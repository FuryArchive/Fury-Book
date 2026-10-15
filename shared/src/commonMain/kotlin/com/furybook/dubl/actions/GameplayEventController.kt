package com.furybook.dubl.actions

import com.furybook.dubl.acquisition.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface GameBoundaryChoice {
    data object StartOwnTurn : GameBoundaryChoice
    data class StartOtherTurn(val actorId: String) : GameBoundaryChoice
    data object EndTurn : GameBoundaryChoice
    data object EndRound : GameBoundaryChoice
    data object EndScene : GameBoundaryChoice
    data class Rest(val kind: RestKind, val minutes: Int) : GameBoundaryChoice
    data class SpendActions(val count: Int) : GameBoundaryChoice
    data object SpendReaction : GameBoundaryChoice
    data class ReserveDefense(val count: Int) : GameBoundaryChoice
}
data class GameplayEventView(val scope: GameplayScope, val storageRevision: Long, val timeline: GameplayTimeline)
class GameplayEventReview internal constructor(val scope: GameplayScope, val expectedStorageRevision: Long,
    val request: RecordGameBoundary)

/** Captured review and same-ID retry; no resource-refresh or effect-activation policy is inferred. */
class GameplayEventController(private val store: AcquisitionStore, private val scope: () -> GameplayScope,
    private val isCurrent: (GameplayScope) -> Boolean, private val capabilities: () -> GameplayCapabilities,
    private val idFactory: () -> String) {
    private val mutex = Mutex()
    private var retained: GameplayEventReview? = null
    private var attempted = false
    private var uncertain = false
    private var outcome: GameplayEventCommitResult? = null
    val review get() = retained?.takeIf { it.scope == scope() && isCurrent(it.scope) }
    private fun current(captured: GameplayScope) {
        check(captured == scope() && isCurrent(captured)) { "Лист или аккаунт изменился" }
    }
    private suspend fun load(captured: GameplayScope): StoredCharacter {
        current(captured)
        val state = (store.load(captured.characterId) as? LoadResult.Loaded)?.state ?: error("Канонический лист недоступен")
        current(captured)
        check(state.formatVersion == 2 && state.gameplay != null && state.character.id == captured.characterId) { "Нужен локальный формат действий" }
        return state
    }
    suspend fun readView(): GameplayEventView = mutex.withLock {
        val captured = scope(); val state = load(captured)
        GameplayEventView(captured, state.storageRevision, (state.gameplay!!.events ?: GameplayEventLog(captured.characterId)).timeline)
    }
    suspend fun prepare(choice: GameBoundaryChoice, expectedStorageRevision: Long): GameplayEventReview = mutex.withLock {
        check(!attempted || outcome is GameplayEventCommitResult.Applied || outcome is GameplayEventCommitResult.Replayed) { "Сначала выясните исход прежнего события" }
        val captured = scope(); val state = load(captured)
        check(state.storageRevision == expectedStorageRevision) { "Лист изменился; обновите события" }
        check(capabilities().writable) { "Локальные события не включены" }
        val journal = state.gameplay!!; val timeline = (journal.events ?: GameplayEventLog(captured.characterId)).timeline
        val id = idFactory(); require(id.isNotBlank())
        check(state.receipts.none { it.operationId == id } && journal.receipts.none { it.request.operationId == id } && journal.migration?.operationId != id)
        val boundaryId = "$id:boundary"
        val boundary = when (choice) {
            GameBoundaryChoice.StartOwnTurn -> GameBoundary.TurnStarted(boundaryId, captured.characterId)
            is GameBoundaryChoice.StartOtherTurn -> {
                require(choice.actorId.isNotBlank() && choice.actorId != captured.characterId) { "Укажите другого участника" }
                GameBoundary.TurnStarted(boundaryId, choice.actorId)
            }
            GameBoundaryChoice.EndTurn -> checkNotNull(timeline.openTurn) { "Нет открытого хода" }.let {
                GameBoundary.TurnEnded(boundaryId, it.actorId, it.startedBoundaryId)
            }
            GameBoundaryChoice.EndRound -> GameBoundary.RoundEnded(boundaryId)
            GameBoundaryChoice.EndScene -> GameBoundary.SceneEnded(boundaryId)
            is GameBoundaryChoice.Rest -> GameBoundary.RestCompleted(boundaryId, choice.kind, choice.minutes)
            is GameBoundaryChoice.SpendActions -> GameBoundary.ActionPointsSpent(boundaryId, choice.count)
            GameBoundaryChoice.SpendReaction -> GameBoundary.ReactionSpent(boundaryId)
            is GameBoundaryChoice.ReserveDefense -> GameBoundary.DefenseReserved(boundaryId, choice.count)
        }
        val command = RecordGameBoundary(id, captured.characterId, timeline.revision, boundary)
        val transition = GameplayEvents.apply(timeline, command)
        check(transition is BoundaryTransition.Applied) { (transition as? BoundaryTransition.Blocked)?.reason ?: "Событие уже записано" }
        current(captured)
        GameplayEventReview(captured, state.storageRevision, command).also { retained = it; attempted = false; uncertain = false; outcome = null }
    }
    suspend fun confirm(operationId: String): GameplayEventCommitResult = mutex.withLock {
        val selected = checkNotNull(retained) { "Нет подготовленного события" }
        check(operationId == selected.request.operationId) { "Открыто другое событие" }; current(selected.scope)
        outcome?.takeIf { it is GameplayEventCommitResult.Applied || it is GameplayEventCommitResult.Replayed }?.let { return@withLock it }
        val wasUncertain = uncertain; attempted = true; uncertain = true
        val guarded = object : AcquisitionStore {
            override suspend fun load(characterId: String): LoadResult {
                current(selected.scope); val value = store.load(characterId); current(selected.scope); return value
            }
            override suspend fun compareAndSet(characterId: String, expectedStorageRevision: Long, next: StoredCharacter): CommitResult {
                current(selected.scope); return store.compareAndSet(characterId, expectedStorageRevision, next)
            }
        }
        val result = GameplayEventService(guarded) {
            if (scope() == selected.scope && isCurrent(selected.scope)) capabilities() else GameplayCapabilities()
        }.apply(selected.request, selected.expectedStorageRevision)
        outcome = result
        uncertain = when (result) {
            is GameplayEventCommitResult.Applied, is GameplayEventCommitResult.Replayed -> false
            is GameplayEventCommitResult.Blocked -> wasUncertain
            is GameplayEventCommitResult.StorageFailure -> true
        }
        current(selected.scope)
        result
    }
    suspend fun dismiss() = mutex.withLock {
        check(!uncertain) { "Исход события неизвестен; повторите прежний ID" }
        retained = null; attempted = false; outcome = null
    }
}
