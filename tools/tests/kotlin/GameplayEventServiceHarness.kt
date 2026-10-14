package com.furybook.dubl.actions

import com.furybook.dubl.acquisition.*
import com.furybook.dubl.model.*
import kotlinx.coroutines.runBlocking

private class EventStore(var raw: String): AcquisitionStore {
    var writes = 0; var fault: String? = null
    override suspend fun load(characterId: String) = decodeEnvelope(raw, true)
    override suspend fun compareAndSet(characterId: String, expectedStorageRevision: Long, next: StoredCharacter): CommitResult {
        val old = (load(characterId) as LoadResult.Loaded).state
        if (expectedStorageRevision != old.storageRevision || fault == "conflict") return CommitResult.Conflict
        if (fault == "before") return CommitResult.StorageFailure("before")
        raw = encodeEnvelope(next); writes++
        if (fault == "after") return CommitResult.StorageFailure("after")
        return CommitResult.Committed
    }
    suspend fun state() = (load("hero") as LoadResult.Loaded).state
}
fun main() = runBlocking {
    var count = 0
    fun expect(value: Boolean) { check(value); count++ }
    val original = StoredCharacter(DublCharacter("hero"), CharacterSheetExtras(notes="keep"), emptyList(),
        Ledger(Opening("opening", "source", Amount.ZERO, RequirementNode.CreationPhase.Phase.Creating,
            OpeningProvenance.NewCharacter), emptyList()), emptyList(), emptyList(), SyncMetadata(0), 0, 0,
        RequirementNode.CreationPhase.Phase.Creating, formatVersion = 2, gameplay = GameplayJournal())
    val raw = encodeEnvelope(original)
    val store = EventStore(raw)
    val enabled = { GameplayCapabilities(true, true) }
    val service = GameplayEventService(store, enabled)
    val start = RecordGameBoundary("turn-start", "hero", 0, GameBoundary.TurnStarted("turn-1", "hero"))
    expect(GameplayEventService(store).apply(start, 0) is GameplayEventCommitResult.Blocked && store.writes == 0)
    expect(service.apply(start, 999) is GameplayEventCommitResult.Blocked && store.writes == 0)
    expect(service.apply(start, 0) is GameplayEventCommitResult.Applied && store.writes == 1)
    val saved = store.state()
    expect(saved.character == original.character && saved.extras == original.extras && saved.ledger == original.ledger && saved.revision == original.revision)
    expect(saved.gameplay!!.events!!.timeline.ownTurnOrdinal == 1L)
    expect((decodeEnvelope(store.raw, true) as LoadResult.Loaded).state == saved)
    expect((decodeEnvelope(store.raw) as LoadResult.Unsupported).raw == store.raw)
    expect(runCatching { encodeSyncEnvelope(saved) }.isFailure)
    expect(GameplayEventService(store).apply(start, 0) is GameplayEventCommitResult.Replayed && store.writes == 1)
    expect(service.apply(start.copy(operationId="duplicate-boundary", expectedRevision=1), 1) is GameplayEventCommitResult.Blocked)
    expect(service.apply(start.copy(boundary=GameBoundary.SceneEnded("changed")), 1) is GameplayEventCommitResult.Blocked)
    val action = StartOverexertion("effort", "hero", 0, PhysicalAttribute.STRENGTH)
    val actions = GameplayService(store, enabled)
    expect(actions.apply(action, 1) is GameplayCommitResult.Applied)
    expect(store.state().gameplay!!.events == saved.gameplay!!.events)
    val end = RecordGameBoundary("turn-end", "hero", 1, GameBoundary.TurnEnded("end-1", "hero", "turn-1"))
    expect(service.apply(end, 2) is GameplayEventCommitResult.Applied)
    expect(store.state().character.enduranceCurrent == 2 && store.state().gameplay?.pending?.startOperationId == "effort")
    expect(actions.apply(UndoGameplay("undo", "hero", 1, "effort"), 3) is GameplayCommitResult.Blocked)
    expect(actions.apply(StartOverexertion("turn-start", "hero", 1, PhysicalAttribute.SPEED), 3) is GameplayCommitResult.Blocked)
    expect(service.apply(RecordGameBoundary("effort", "hero", 2, GameBoundary.SceneEnded("scene")), 3) is GameplayEventCommitResult.Blocked)
    val rest = RecordGameBoundary("rest", "hero", 2, GameBoundary.RestCompleted("rest-1", RestKind.FULL, 480))
    expect(service.apply(rest, 3) is GameplayEventCommitResult.Applied)
    expect(store.state().character.enduranceCurrent == 2 && store.state().gameplay?.pending != null)
    val wire = store.raw
    expect(decodeEnvelope(wire.replace("\"minutes\":480", "\"minutes\":0"), true) !is LoadResult.Loaded)
    expect(decodeEnvelope(wire.replace("\"type\":\"RestCompleted\"", "\"type\":\"Unknown\""), true) !is LoadResult.Loaded)
    expect(decodeEnvelope(wire.replace("\"kind\":\"FULL\"", "\"kind\":\"FULL\",\"heal\":99"), true) !is LoadResult.Loaded)
    for (fault in listOf("before", "after", "conflict")) {
        val broken = EventStore(raw); broken.fault = fault
        val outcome = GameplayEventService(broken, enabled).apply(start, 0)
        expect(if (fault == "after") outcome is GameplayEventCommitResult.Replayed && broken.writes == 1 else outcome !is GameplayEventCommitResult.Applied && broken.writes == 0)
        broken.fault = null
        expect(GameplayEventService(broken, enabled).apply(start, 0).let { it is GameplayEventCommitResult.Applied || it is GameplayEventCommitResult.Replayed } && broken.writes == 1)
    }
    expect(runCatching { encodeEnvelope(saved.copy(storageRevision=0)) }.isFailure)
    println("GameplayEventServiceHarness: $count assertions passed; simulated storage only")
}
