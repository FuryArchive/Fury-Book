package com.furybook.dubl.actions

import com.furybook.dubl.acquisition.*
import com.furybook.dubl.model.CharacterConditionId
import com.furybook.dubl.model.effectiveRollConditions
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Storage, authenticated owner and selected view must all still match at dispatch. */
data class GameplayScope(val storageId: String, val ownerId: String, val characterId: String, val generation: Long)
data class GameplaySheet(val scope: GameplayScope, val storageRevision: Long, val gameplayRevision: Long,
    val endurance: Int, val pending: PendingPhysicalCheck?, val lastReceipt: GameplayReceipt?, val roll: GameplayRollProjection?)
class GameplayReview internal constructor(val key: String, val scope: GameplayScope,
    val request: GameplayRequest, val expectedStorageRevision: Long, val enduranceBefore: Int,
    val enduranceAfter: Int, val pendingAfter: PendingPhysicalCheck?, val ruleVersion: String?)

/**
 * Retained confirmation state shared by all front ends. Preparing/dismissing never writes.
 * An uncertain dispatch retains the exact operation ID and input; it cannot be edited or replaced.
 * This controller does not migrate data, enable storage capabilities, roll dice or infer game time.
 */
class GameplayController(private val store: AcquisitionStore, private val scope: () -> GameplayScope,
    private val isCurrent: (GameplayScope) -> Boolean, private val capabilities: () -> GameplayCapabilities,
    private val idFactory: () -> String) {
    private val mutex = Mutex()
    private var retained: GameplayReview? = null
    private var attempted = false
    private var uncertain = false
    private var outcome: GameplayCommitResult? = null
    val review: GameplayReview? get() = retained?.takeIf { scope() == it.scope && isCurrent(it.scope) }
    val lastOutcome: GameplayCommitResult? get() = if (review != null) outcome else null

    private fun current(captured: GameplayScope) {
        check(captured == scope() && isCurrent(captured)) { "Аккаунт, хранилище или выбранный лист изменился" }
    }
    private suspend fun load(captured: GameplayScope): StoredCharacter {
        current(captured)
        val state = (store.load(captured.characterId) as? LoadResult.Loaded)?.state
            ?: error("Канонический лист недоступен")
        current(captured)
        check(state.character.id == captured.characterId && state.formatVersion == 2 && state.gameplay != null) {
            "Нужен явный переход на локальный формат действий с резервной копией"
        }
        return state
    }
    suspend fun readSheet(): GameplaySheet = mutex.withLock {
        val captured = scope(); val state = load(captured); val journal = state.gameplay!!
        GameplaySheet(captured, state.storageRevision, journal.revision, state.character.enduranceCurrent,
            journal.pending, journal.receipts.lastOrNull(), pendingGameplayRoll(state))
    }
    private fun projection(state: StoredCharacter): GameplayState {
        val journal = state.gameplay!!
        val conditions = state.character.effectiveRollConditions(state.extras.activeConditions)
        return GameplayState(state.character.id, journal.revision, state.character.enduranceCurrent,
            CharacterConditionId.WEAKNESS in conditions,
            CharacterConditionId.TIRED !in conditions && CharacterConditionId.EXHAUSTION !in conditions,
            journal.pending, journal.receipts)
    }
    private suspend fun prepare(make: (String, StoredCharacter) -> GameplayRequest): GameplayReview = mutex.withLock {
        check(!attempted || outcome is GameplayCommitResult.Applied || outcome is GameplayCommitResult.Replayed) {
            "Сначала выясните исход предыдущей операции с прежним ID"
        }
        val captured = scope(); val state = load(captured)
        check(capabilities().writable) { "Локальные сохранённые действия не включены" }
        val id = idFactory(); require(id.isNotBlank())
        check(state.receipts.none { it.operationId == id } && state.gameplay!!.migration?.operationId != id)
        val request = make(id, state); val input = projection(state)
        val transition = when (request) {
            is StartOverexertion -> GameplayActions.start(input, request)
            is ResolvePhysicalCheck -> GameplayActions.resolve(input, request)
            is UndoGameplay -> {
                check(state.gameplay!!.lastCommittedStorageRevision == state.storageRevision) { "После действия лист уже менялся" }
                GameplayActions.undo(input, request)
            }
        }
        check(transition is ActionTransition.Applied) {
            (transition as? ActionTransition.Blocked)?.reason ?: "ID операции уже использован"
        }
        current(captured)
        GameplayReview(id, captured, request, state.storageRevision, input.endurance, transition.state.endurance,
            transition.state.pending, transition.state.pending?.ruleVersion ?: input.pending?.ruleVersion).also {
            retained = it; attempted = false; uncertain = false; outcome = null
        }
    }
    suspend fun prepareStart(attribute: PhysicalAttribute) = prepare { id, state ->
        StartOverexertion(id, state.character.id, state.gameplay!!.revision, attribute)
    }
    suspend fun prepareResult(result: PhysicalCheckResult, expectedStartOperationId: String,
        expectedStorageRevision: Long) = prepare { id, state ->
        check(state.storageRevision == expectedStorageRevision && state.gameplay?.pending?.startOperationId == expectedStartOperationId) {
            "Лист или ожидающая проверка изменились; сначала обновите её данные"
        }
        ResolvePhysicalCheck(id, state.character.id, state.gameplay!!.revision,
            checkNotNull(state.gameplay!!.pending) { "Нет ожидающей проверки" }.startOperationId, result)
    }
    suspend fun prepareUndo() = prepare { id, state ->
        UndoGameplay(id, state.character.id, state.gameplay!!.revision,
            checkNotNull(state.gameplay!!.receipts.lastOrNull()) { "Нет сохранённого действия" }.request.operationId)
    }
    suspend fun confirm(key: String): GameplayCommitResult = mutex.withLock {
        val selected = checkNotNull(retained) { "Нет подтверждаемого действия" }
        check(selected.key == key) { "Открыт другой preview" }; current(selected.scope)
        // Terminal outcomes are display-only. Repeated clicks never allocate another command.
        outcome?.takeIf { it is GameplayCommitResult.Applied || it is GameplayCommitResult.Replayed }?.let { return@withLock it }
        val wasUncertain = uncertain
        attempted = true; uncertain = true
        val guardedStore = object : AcquisitionStore {
            override suspend fun load(characterId: String): LoadResult {
                current(selected.scope); val result = store.load(characterId); current(selected.scope); return result
            }
            override suspend fun compareAndSet(characterId: String, expectedStorageRevision: Long, next: StoredCharacter): CommitResult {
                current(selected.scope); return store.compareAndSet(characterId, expectedStorageRevision, next)
            }
        }
        val result = GameplayService(guardedStore) {
            if (scope() == selected.scope && isCurrent(selected.scope)) capabilities() else GameplayCapabilities()
        }.apply(selected.request, selected.expectedStorageRevision)
        outcome = result
        uncertain = when (result) {
            is GameplayCommitResult.Applied, is GameplayCommitResult.Replayed -> false
            is GameplayCommitResult.Blocked -> wasUncertain
            is GameplayCommitResult.StorageFailure -> true
        }
        current(selected.scope)
        result
    }
    /** A known refusal permits a new explicit preview. Uncertain storage failures do not. */
    suspend fun dismiss() = mutex.withLock {
        check(!uncertain && (!attempted || outcome is GameplayCommitResult.Blocked || outcome is GameplayCommitResult.Applied || outcome is GameplayCommitResult.Replayed)) {
            "Исход сохранения неизвестен; повторите прежнюю операцию"
        }
        retained = null; attempted = false; uncertain = false; outcome = null
    }
}
