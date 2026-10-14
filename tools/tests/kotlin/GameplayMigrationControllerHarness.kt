package com.furybook.dubl.actions

import com.furybook.dubl.acquisition.*
import com.furybook.dubl.model.*
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    var assertions = 0
    fun expect(value: Boolean) { check(value); assertions++ }
    var state = StoredCharacter(DublCharacter("hero", name="Original"), CharacterSheetExtras(), emptyList(),
        Ledger(Opening("opening", "source", Amount.ZERO, RequirementNode.CreationPhase.Phase.Creating,
            OpeningProvenance.NewCharacter), emptyList()), emptyList(), emptyList(), SyncMetadata(0), 0, 0,
        RequirementNode.CreationPhase.Phase.Creating)
    val original = state
    var current = GameplayScope("fixture", "owner", "hero", 0)
    var allowed = true; var count = 0; var writes = 0; var fail = false
    val store = object: AcquisitionStore {
        override suspend fun load(characterId: String) = LoadResult.Loaded(state)
        override suspend fun compareAndSet(characterId: String, expectedStorageRevision: Long, next: StoredCharacter) = error("Use explicit migration")
    }
    val c = GameplayMigrationController(store, { current }, { it == current }, { GameplayCapabilities(allowed, true) }, { "migration-${++count}" }) { command ->
        if (fail) GameplayMigrationResult.StorageFailure("fixture uncertain")
        else { state = prepareGameplayMigration(state, encodeEnvelope(state), command); writes++; GameplayMigrationResult.Migrated("fixture-original") }
    }
    val abandoned = c.prepare()
    expect(abandoned.characterName == "Original" && state == original && writes == 0)
    c.dismiss(); expect(state == original)
    val ready = c.prepare()
    expect(runCatching { c.confirm(abandoned.request.operationId) }.isFailure && writes == 0)
    current = current.copy(generation = 1)
    expect(c.review == null && runCatching { c.confirm(ready.request.operationId) }.isFailure)
    current = current.copy(generation = 0); fail = true
    expect(c.confirm(ready.request.operationId) is GameplayMigrationResult.StorageFailure)
    expect(runCatching { c.dismiss() }.isFailure && runCatching { c.prepare() }.isFailure)
    allowed = false
    expect(c.confirm(ready.request.operationId) is GameplayMigrationResult.Blocked)
    expect(runCatching { c.dismiss() }.isFailure)
    allowed = true; fail = false
    expect(c.confirm(ready.request.operationId) is GameplayMigrationResult.Migrated && writes == 1)
    expect(c.confirm(ready.request.operationId) is GameplayMigrationResult.Migrated && writes == 1)
    expect(state.gameplay?.migration?.sourceRaw == encodeEnvelope(original))
    c.dismiss(); expect(runCatching { c.prepare() }.isFailure && writes == 1)
    println("GameplayMigrationControllerHarness: $assertions assertions passed; simulated migration only")
}
