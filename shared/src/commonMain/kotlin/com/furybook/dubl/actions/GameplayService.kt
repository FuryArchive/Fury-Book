package com.furybook.dubl.actions

import com.furybook.dubl.acquisition.*
import com.furybook.dubl.model.CharacterConditionId
import com.furybook.dubl.model.effectiveRollConditions
import kotlinx.coroutines.CancellationException

/** No caller in production enables this gate yet. Cloud format 2 is never implied. */
data class GameplayCapabilities(val durableAtomic: Boolean = false, val isolatedLocal: Boolean = false) {
    val writable: Boolean get() = durableAtomic && isolatedLocal
}

sealed interface GameplayCommitResult {
    data class Applied(val receipt: GameplayReceipt) : GameplayCommitResult
    data class Replayed(val receipt: GameplayReceipt, val undone: Boolean = false) : GameplayCommitResult
    data class Blocked(val reason: String) : GameplayCommitResult
    data class StorageFailure(val reason: String) : GameplayCommitResult
}

/** One whole-character CAS. No sidecar resource write, implicit migration or retry with a new ID. */
class GameplayService(
    private val store: AcquisitionStore,
    private val capabilities: () -> GameplayCapabilities = { GameplayCapabilities() },
) {
    suspend fun apply(request: GameplayRequest, expectedStorageRevision: Long): GameplayCommitResult = try {
        applyOnce(request, expectedStorageRevision)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        reconcile(request) ?: GameplayCommitResult.StorageFailure(failure.message ?: "Gameplay storage unavailable; retain the same operation ID")
    }

    private suspend fun applyOnce(request: GameplayRequest, expectedStorageRevision: Long): GameplayCommitResult {
        val loaded = store.load(request.characterId)
        val state = (loaded as? LoadResult.Loaded)?.state
            ?: return GameplayCommitResult.Blocked("Canonical character unavailable; no fallback or automatic migration")
        if (state.character.id != request.characterId)
            return GameplayCommitResult.Blocked("Stored character scope differs")
        replay(state, request)?.let { return it }
        if (!capabilities().writable) return GameplayCommitResult.Blocked("Local durable gameplay is not enabled")
        if (state.formatVersion != 2 || state.gameplay == null)
            return GameplayCommitResult.Blocked("Explicit backed-up format migration is required")
        if (state.storageRevision != expectedStorageRevision || expectedStorageRevision == Long.MAX_VALUE)
            return GameplayCommitResult.Blocked("Character changed; confirm a fresh preview")
        if (state.receipts.any { it.operationId == request.operationId })
            return GameplayCommitResult.Blocked("Operation ID belongs to acquisition history")
        if (state.gameplay.migration?.operationId == request.operationId)
            return GameplayCommitResult.Blocked("Operation ID belongs to migration")
        encodeEnvelope(state)
        val journal = state.gameplay
        if (request is UndoGameplay && journal.lastCommittedStorageRevision != state.storageRevision)
            return GameplayCommitResult.Blocked("Another saved operation followed this action; Undo is blocked")
        val conditions = state.character.effectiveRollConditions(state.extras.activeConditions)
        val input = GameplayState(state.character.id, journal.revision, state.character.enduranceCurrent,
            CharacterConditionId.WEAKNESS in conditions,
            resourceConsequencesKnown = CharacterConditionId.TIRED !in conditions && CharacterConditionId.EXHAUSTION !in conditions,
            pending = journal.pending, receipts = journal.receipts)
        val transition = when (request) {
            is StartOverexertion -> GameplayActions.start(input, request)
            is ResolvePhysicalCheck -> GameplayActions.resolve(input, request)
            is UndoGameplay -> GameplayActions.undo(input, request)
        }
        if (transition is ActionTransition.Blocked) return GameplayCommitResult.Blocked(transition.reason)
        if (transition is ActionTransition.Replayed) return GameplayCommitResult.Replayed(transition.receipt, transition.undone)
        transition as ActionTransition.Applied
        val next = state.copy(character = state.character.copy(enduranceCurrent = transition.state.endurance),
            gameplay = GameplayJournal.from(transition.state, state.storageRevision + 1).copy(migration = journal.migration),
            storageRevision = state.storageRevision + 1)
        encodeEnvelope(next)
        if (!capabilities().writable) return GameplayCommitResult.Blocked("Local gameplay permission changed before commit")
        return when (val result = store.compareAndSet(request.characterId, expectedStorageRevision, next)) {
            CommitResult.Committed -> GameplayCommitResult.Applied(transition.receipt)
            CommitResult.Conflict -> reconcile(request)
                ?: GameplayCommitResult.Blocked("Concurrent change; confirm a fresh preview")
            is CommitResult.StorageFailure -> reconcile(request) ?: GameplayCommitResult.StorageFailure(result.reason)
        }
    }

    private fun replay(state: StoredCharacter, request: GameplayRequest): GameplayCommitResult? {
        val receipt = state.gameplay?.receipts?.find { it.request.operationId == request.operationId } ?: return null
        return if (receipt.request == request) GameplayCommitResult.Replayed(receipt,
            state.gameplay.receipts.any { (it.request as? UndoGameplay)?.targetOperationId == request.operationId })
        else GameplayCommitResult.Blocked("Operation ID reused with different input")
    }

    private suspend fun reconcile(request: GameplayRequest): GameplayCommitResult? = try {
        val state = (store.load(request.characterId) as? LoadResult.Loaded)?.state
        if (state?.character?.id == request.characterId) replay(state, request) else null
    } catch (cancelled: CancellationException) { throw cancelled }
    catch (_: Exception) { null }
}
