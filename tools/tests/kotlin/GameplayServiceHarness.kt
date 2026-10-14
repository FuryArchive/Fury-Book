package com.furybook.dubl.actions

import com.furybook.dubl.acquisition.*
import com.furybook.dubl.model.*
import kotlinx.coroutines.runBlocking

private class Backing(var raw: String, var writes: Int = 0, var failure: String? = null)
private class TestGameplayStore(private val backing: Backing) : AcquisitionStore {
    override suspend fun load(characterId: String) = decodeEnvelope(backing.raw, allowGameplayFormat = true)
    override suspend fun compareAndSet(characterId: String, expectedStorageRevision: Long, next: StoredCharacter): CommitResult {
        val old = (load(characterId) as LoadResult.Loaded).state
        if (old.storageRevision != expectedStorageRevision) return CommitResult.Conflict
        if (backing.failure == "before") return CommitResult.StorageFailure("before commit")
        if (backing.failure == "conflict") return CommitResult.Conflict
        check(next.storageRevision == expectedStorageRevision + 1)
        backing.raw = encodeEnvelope(next)
        backing.writes++
        if (backing.failure == "throw-after") error("after commit")
        if (backing.failure == "after") return CommitResult.StorageFailure("after commit")
        return CommitResult.Committed
    }
}

fun main() = runBlocking {
    var assertions = 0
    fun expect(value: Boolean) { check(value); assertions++ }
    val base = StoredCharacter(DublCharacter("hero"), CharacterSheetExtras(notes = "preserve"), emptyList(),
        Ledger(Opening("opening", "source", Amount.ZERO, RequirementNode.CreationPhase.Phase.Creating,
            OpeningProvenance.NewCharacter), emptyList()), emptyList(), emptyList(), SyncMetadata(0), 0, 0,
        RequirementNode.CreationPhase.Phase.Creating, formatVersion = 2, gameplay = GameplayJournal())
    val request = StartOverexertion("start", "hero", 0, PhysicalAttribute.STRENGTH)
    val backing = Backing(encodeEnvelope(base))
    val store = TestGameplayStore(backing)
    val enabled = { GameplayCapabilities(durableAtomic = true, isolatedLocal = true) }
    val service = GameplayService(store, enabled)
    expect(GameplayService(store).apply(request, 0) is GameplayCommitResult.Blocked && backing.writes == 0)
    expect(service.apply(request, 5) is GameplayCommitResult.Blocked && backing.writes == 0)
    expect(service.apply(request, 0) is GameplayCommitResult.Applied && backing.writes == 1)
    val saved = (store.load("hero") as LoadResult.Loaded).state
    expect(saved.character.enduranceCurrent == 2 && saved.gameplay?.pending != null)
    expect(saved.revision == 0L && saved.storageRevision == 1L && saved.ledger == base.ledger && saved.extras == base.extras)
    // Independent service/store objects observe the same durable-unit simulation.
    expect(GameplayService(TestGameplayStore(backing), enabled).apply(request, 0) is GameplayCommitResult.Replayed)
    expect(GameplayService(store).apply(request, 0) is GameplayCommitResult.Replayed)
    expect(service.apply(request.copy(attribute = PhysicalAttribute.DEXTERITY), 0) is GameplayCommitResult.Blocked)
    expect(backing.writes == 1)
    expect(service.apply(ResolvePhysicalCheck("finish", "hero", 1, "start", PhysicalCheckResult(false)), 1) is GameplayCommitResult.Applied)
    val completed = (store.load("hero") as LoadResult.Loaded).state
    expect(completed.character.enduranceCurrent == 2 && completed.gameplay?.pending == null && backing.writes == 2)
    val undo = UndoGameplay("undo-finish", "hero", 2, "finish")
    val intervening = Backing(encodeEnvelope(completed.copy(storageRevision = 3,
        extras = completed.extras.copy(notes = "another saved operation"))))
    expect(GameplayService(TestGameplayStore(intervening), enabled).apply(undo, 3) is GameplayCommitResult.Blocked && intervening.writes == 0)
    expect(service.apply(undo, 2) is GameplayCommitResult.Applied)
    val reopened = (store.load("hero") as LoadResult.Loaded).state
    expect(reopened.gameplay?.pending?.startOperationId == "start" && reopened.character.enduranceCurrent == 2)
    expect((service.apply(ResolvePhysicalCheck("finish", "hero", 1, "start", PhysicalCheckResult(false)), 1) as GameplayCommitResult.Replayed).undone)
    expect(service.apply(undo, 2) is GameplayCommitResult.Replayed && backing.writes == 3)
    for (failure in listOf("before", "after", "throw-after", "conflict")) {
        val fault = Backing(encodeEnvelope(base), failure = failure)
        val outcome = GameplayService(TestGameplayStore(fault), enabled).apply(request, 0)
        if (failure.endsWith("after")) {
            expect(outcome is GameplayCommitResult.Replayed && fault.writes == 1)
            expect((decodeEnvelope(fault.raw, true) as LoadResult.Loaded).state.character.enduranceCurrent == 2)
        } else expect(outcome !is GameplayCommitResult.Applied && fault.writes == 0 && fault.raw == encodeEnvelope(base))
    }
    val revoke = Backing(encodeEnvelope(base))
    var calls = 0
    val revoked = GameplayService(TestGameplayStore(revoke)) { calls++; if (calls == 1) enabled() else GameplayCapabilities() }
    expect(revoked.apply(request, 0) is GameplayCommitResult.Blocked && revoke.writes == 0)
    for (condition in listOf(CharacterConditionId.WEAKNESS, CharacterConditionId.TIRED, CharacterConditionId.EXHAUSTION)) {
        val conditional = Backing(encodeEnvelope(base.copy(extras = base.extras.copy(activeConditions = setOf(condition)))))
        expect(GameplayService(TestGameplayStore(conditional), enabled).apply(request, 0) is GameplayCommitResult.Blocked && conditional.writes == 0)
    }
    val legacy = Backing(encodeEnvelope(base.copy(formatVersion = 1, gameplay = null)))
    expect(GameplayService(TestGameplayStore(legacy), enabled).apply(request, 0) is GameplayCommitResult.Blocked && legacy.writes == 0)
    println("GameplayServiceHarness: $assertions assertions passed; simulated storage only")
}
