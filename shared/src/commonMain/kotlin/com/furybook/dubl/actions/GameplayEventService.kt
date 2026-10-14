package com.furybook.dubl.actions

import com.furybook.dubl.acquisition.*
import kotlinx.coroutines.CancellationException

sealed interface GameplayEventCommitResult {
    data class Applied(val receipt: GameBoundaryReceipt) : GameplayEventCommitResult
    data class Replayed(val receipt: GameBoundaryReceipt) : GameplayEventCommitResult
    data class Blocked(val reason: String) : GameplayEventCommitResult
    data class StorageFailure(val reason: String) : GameplayEventCommitResult
}

/** Persist explicit boundaries in the same whole-character CAS, without refreshing any resource. */
class GameplayEventService(private val store: AcquisitionStore,
    private val capabilities: () -> GameplayCapabilities = { GameplayCapabilities() }) {
    suspend fun apply(request: RecordGameBoundary, expectedStorageRevision: Long): GameplayEventCommitResult = try {
        applyOnce(request, expectedStorageRevision)
    } catch (cancelled: CancellationException) { throw cancelled }
    catch (failure: Exception) {
        reconcile(request) ?: GameplayEventCommitResult.StorageFailure(failure.message ?: "Event outcome unknown; retain the same ID")
    }
    private suspend fun applyOnce(request: RecordGameBoundary, expectedStorageRevision: Long): GameplayEventCommitResult {
        val state = (store.load(request.characterId) as? LoadResult.Loaded)?.state
            ?: return GameplayEventCommitResult.Blocked("Canonical character unavailable")
        replay(state, request)?.let { return it }
        if (!capabilities().writable) return GameplayEventCommitResult.Blocked("Local durable events are not enabled")
        val journal = state.gameplay
        if (state.formatVersion != 2 || journal == null || state.character.id != request.characterId)
            return GameplayEventCommitResult.Blocked("Explicit local gameplay migration is required")
        if (state.storageRevision != expectedStorageRevision || expectedStorageRevision == Long.MAX_VALUE)
            return GameplayEventCommitResult.Blocked("Character changed; review the event again")
        if (state.receipts.any { it.operationId == request.operationId } || journal.receipts.any { it.request.operationId == request.operationId } ||
            journal.migration?.operationId == request.operationId) return GameplayEventCommitResult.Blocked("Operation ID belongs to another history")
        val log = journal.events ?: GameplayEventLog(request.characterId)
        val transition = GameplayEvents.apply(log.timeline, request)
        if (transition is BoundaryTransition.Blocked) return GameplayEventCommitResult.Blocked(transition.reason)
        if (transition is BoundaryTransition.Replayed) return GameplayEventCommitResult.Replayed(transition.receipt)
        transition as BoundaryTransition.Applied
        val nextLog = GameplayEventLog(request.characterId, log.commands + request)
        val next = state.copy(gameplay = journal.copy(events = nextLog), storageRevision = state.storageRevision + 1)
        // The action marker deliberately stays unchanged: an intervening boundary blocks action Undo.
        encodeEnvelope(next)
        if (!capabilities().writable) return GameplayEventCommitResult.Blocked("Event permission changed before commit")
        return when (val result = store.compareAndSet(request.characterId, expectedStorageRevision, next)) {
            CommitResult.Committed -> GameplayEventCommitResult.Applied(transition.receipt)
            CommitResult.Conflict -> reconcile(request) ?: GameplayEventCommitResult.Blocked("Concurrent change; review again")
            is CommitResult.StorageFailure -> reconcile(request) ?: GameplayEventCommitResult.StorageFailure(result.reason)
        }
    }
    private fun replay(state: StoredCharacter, request: RecordGameBoundary): GameplayEventCommitResult? {
        if (state.character.id != request.characterId) return null
        val receipt = state.gameplay?.events?.timeline?.receipts?.find { it.request.operationId == request.operationId } ?: return null
        return if (receipt.request == request) GameplayEventCommitResult.Replayed(receipt)
        else GameplayEventCommitResult.Blocked("Event ID reused with different input")
    }
    private suspend fun reconcile(request: RecordGameBoundary): GameplayEventCommitResult? = try {
        (store.load(request.characterId) as? LoadResult.Loaded)?.state?.let { replay(it, request) }
    } catch (cancelled: CancellationException) { throw cancelled }
    catch (_: Exception) { null }
}
