package com.furybook.dubl.actions

import com.furybook.dubl.acquisition.*
import com.furybook.dubl.model.*
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    var count = 0
    fun expect(value: Boolean) { check(value); count++ }
    var state = StoredCharacter(DublCharacter("hero"), CharacterSheetExtras(), emptyList(),
        Ledger(Opening("opening", "source", Amount.ZERO, RequirementNode.CreationPhase.Phase.Creating,
            OpeningProvenance.NewCharacter), emptyList()), emptyList(), emptyList(), SyncMetadata(0), 0, 0,
        RequirementNode.CreationPhase.Phase.Creating, formatVersion=2, gameplay=GameplayJournal())
    var writes = 0; var fail = false; var enabled = true; var ids = 0
    var scope = GameplayScope("fixture", "owner", "hero", 0)
    val store = object: AcquisitionStore {
        override suspend fun load(characterId: String) = LoadResult.Loaded(state)
        override suspend fun compareAndSet(characterId: String, expectedStorageRevision: Long, next: StoredCharacter): CommitResult {
            if (state.storageRevision != expectedStorageRevision) return CommitResult.Conflict
            if (fail) return CommitResult.StorageFailure("fixture uncertain")
            state = (decodeEnvelope(encodeEnvelope(next), true) as LoadResult.Loaded).state; writes++
            return CommitResult.Committed
        }
    }
    val c = GameplayEventController(store, { scope }, { it == scope }, { GameplayCapabilities(enabled, true) }, { "event-${++ids}" })
    expect(c.readView().timeline.revision == 0L)
    val cancelled = c.prepare(GameBoundaryChoice.StartOwnTurn, 0)
    expect(writes == 0); c.dismiss(); expect(c.review == null)
    val start = c.prepare(GameBoundaryChoice.StartOwnTurn, 0)
    expect(runCatching { c.confirm(cancelled.request.operationId) }.isFailure)
    expect(c.confirm(start.request.operationId) is GameplayEventCommitResult.Applied && writes == 1)
    expect(c.confirm(start.request.operationId) is GameplayEventCommitResult.Applied && writes == 1)
    expect(c.readView().timeline.openTurn?.actorId == "hero")
    expect(runCatching { c.prepare(GameBoundaryChoice.StartOwnTurn, 1) }.isFailure && writes == 1)
    expect(runCatching { c.prepare(GameBoundaryChoice.EndTurn, 0) }.isFailure)
    val end = c.prepare(GameBoundaryChoice.EndTurn, 1)
    expect((end.request.boundary as GameBoundary.TurnEnded).startedBoundaryId == start.request.boundary.boundaryId)
    scope = scope.copy(generation=1)
    expect(c.review == null && runCatching { c.confirm(end.request.operationId) }.isFailure && writes == 1)
    scope = scope.copy(generation=0); fail = true
    expect(c.confirm(end.request.operationId) is GameplayEventCommitResult.StorageFailure)
    expect(runCatching { c.dismiss() }.isFailure && runCatching { c.prepare(GameBoundaryChoice.EndScene, 1) }.isFailure)
    enabled = false
    expect(c.confirm(end.request.operationId) is GameplayEventCommitResult.Blocked)
    expect(runCatching { c.dismiss() }.isFailure)
    enabled = true; fail = false
    expect(c.confirm(end.request.operationId) is GameplayEventCommitResult.Applied && writes == 2)
    expect(c.readView().timeline.openTurn == null)
    expect(runCatching { c.prepare(GameBoundaryChoice.Rest(RestKind.FULL, 0), 2) }.isFailure)
    expect(runCatching { c.prepare(GameBoundaryChoice.StartOtherTurn("hero"), 2) }.isFailure)
    val other = c.prepare(GameBoundaryChoice.StartOtherTurn("other"), 2)
    expect(c.confirm(other.request.operationId) is GameplayEventCommitResult.Applied)
    expect(c.readView().timeline.ownTurnOrdinal == 1L && c.readView().timeline.openTurn?.actorId == "other")
    expect(state.character.enduranceCurrent == 3)
    println("GameplayEventControllerHarness: $count assertions passed; simulated storage only")
}
