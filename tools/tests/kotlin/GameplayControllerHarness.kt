package com.furybook.dubl.actions

import com.furybook.dubl.acquisition.*
import com.furybook.dubl.model.*
import kotlinx.coroutines.runBlocking

private class ControllerStore(var state: StoredCharacter): AcquisitionStore {
    var writes = 0
    var failure = false
    var afterLoad: (() -> Unit)? = null
    var afterCommit: (() -> Unit)? = null
    override suspend fun load(characterId: String): LoadResult = LoadResult.Loaded(state).also { afterLoad?.invoke() }
    override suspend fun compareAndSet(characterId: String, expectedStorageRevision: Long, next: StoredCharacter): CommitResult {
        if (failure) return CommitResult.StorageFailure("uncertain fixture")
        if (state.storageRevision != expectedStorageRevision) return CommitResult.Conflict
        state = (decodeEnvelope(encodeEnvelope(next), true) as LoadResult.Loaded).state; writes++; afterCommit?.invoke()
        return CommitResult.Committed
    }
}
fun main() = runBlocking {
    var count = 0
    fun expect(value: Boolean) { check(value); count++ }
    fun initial() = StoredCharacter(DublCharacter("hero"), CharacterSheetExtras(), emptyList(),
        Ledger(Opening("opening", "source", Amount.ZERO, RequirementNode.CreationPhase.Phase.Creating,
            OpeningProvenance.NewCharacter), emptyList()), emptyList(), emptyList(), SyncMetadata(0), 0, 0,
        RequirementNode.CreationPhase.Phase.Creating, formatVersion = 2, gameplay = GameplayJournal())
    val store = ControllerStore(initial())
    var current = GameplayScope("fixture", "owner", "hero", 0)
    var enabled = true
    var ids = 0
    fun controller() = GameplayController(store, { current }, { it == current }, { GameplayCapabilities(enabled, true) }, { "id-${++ids}" })
    var c = controller()
    val first = c.prepareStart(PhysicalAttribute.STRENGTH)
    expect(first.enduranceBefore == 3 && first.enduranceAfter == 2 && store.writes == 0)
    c.dismiss(); expect(store.writes == 0)
    val start = c.prepareStart(PhysicalAttribute.DEXTERITY)
    expect(runCatching { c.confirm(first.key) }.isFailure && store.writes == 0)
    expect(c.confirm(start.key) is GameplayCommitResult.Applied)
    expect(c.confirm(start.key) is GameplayCommitResult.Applied && store.writes == 1)
    c = controller()
    expect(c.readSheet().pending?.startOperationId == start.request.operationId)
    expect(runCatching { c.prepareResult(PhysicalCheckResult(true), "wrong-start", store.state.storageRevision) }.isFailure)
    expect(runCatching { c.prepareResult(PhysicalCheckResult(true), start.request.operationId, 999) }.isFailure)
    val result = c.prepareResult(PhysicalCheckResult(false), store.state.gameplay!!.pending!!.startOperationId, store.state.storageRevision)
    expect(result.enduranceBefore == 2 && result.enduranceAfter == 2)
    expect(c.confirm(result.key) is GameplayCommitResult.Applied && store.writes == 2)
    val undo = c.prepareUndo()
    expect(c.confirm(undo.key) is GameplayCommitResult.Applied && store.state.gameplay?.pending != null)
    c = controller()
    val ready = c.prepareResult(PhysicalCheckResult(true), store.state.gameplay!!.pending!!.startOperationId, store.state.storageRevision)
    current = current.copy(generation = 1)
    expect(c.review == null && runCatching { c.confirm(ready.key) }.isFailure && store.writes == 3)
    current = current.copy(generation = 0)
    store.state = store.state.copy(storageRevision = store.state.storageRevision + 1)
    expect(c.confirm(ready.key) is GameplayCommitResult.Blocked && store.writes == 3)
    c.dismiss()
    val uncertain = c.prepareResult(PhysicalCheckResult(true), store.state.gameplay!!.pending!!.startOperationId, store.state.storageRevision)
    store.failure = true
    expect(c.confirm(uncertain.key) is GameplayCommitResult.StorageFailure)
    expect(runCatching { c.dismiss() }.isFailure)
    expect(runCatching { c.prepareResult(PhysicalCheckResult(false), store.state.gameplay!!.pending!!.startOperationId, store.state.storageRevision) }.isFailure)
    enabled = false
    expect(c.confirm(uncertain.key) is GameplayCommitResult.Blocked)
    expect(runCatching { c.dismiss() }.isFailure) // Permission loss cannot erase uncertainty.
    enabled = true; store.failure = false
    expect(c.confirm(uncertain.key) is GameplayCommitResult.Applied && store.writes == 4)
    c.dismiss(); expect(c.review == null)
    c = controller()
    store.afterLoad = { current = current.copy(generation = current.generation + 1) }
    expect(runCatching { c.prepareStart(PhysicalAttribute.SPEED) }.isFailure && store.writes == 4)
    store.afterLoad = null
    c = controller()
    val switching = c.prepareStart(PhysicalAttribute.CONSTITUTION)
    val oldScope = current
    store.afterCommit = { current = current.copy(generation = current.generation + 1) }
    expect(runCatching { c.confirm(switching.key) }.isFailure && store.writes == 5 && c.review == null)
    current = oldScope; store.afterCommit = null
    expect(c.confirm(switching.key) is GameplayCommitResult.Applied && store.writes == 5)
    println("GameplayControllerHarness: $count assertions passed; simulated storage only")
}
