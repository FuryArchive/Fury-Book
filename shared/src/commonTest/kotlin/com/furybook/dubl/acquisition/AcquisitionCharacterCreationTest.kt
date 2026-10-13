package com.furybook.dubl.acquisition

import com.furybook.dubl.model.*
import kotlinx.coroutines.*
import kotlin.coroutines.*
import kotlin.test.*

class AcquisitionCharacterCreationTest {
    private val rule = "UR_NEW_CHARACTER_INITIAL_AP_PER_1000_XP"
    private val source = RuleSourceRef("book", "revision", "Source recommends at most one AP per full 1000 XP", "tab", 11903, rule, "r1")
    private fun catalog() = AcquisitionCatalog(emptyMap(), "c1", "r1", creationFundingPolicy = CreationFundingPolicy(rule, 1000, source))
    private val capabilities = AcquisitionCapabilities(true, false, true)
    private fun command(xp: Long = 2500, id: String = "hero") = CreateAcquisitionCharacter("creation:$id", id, xp, "Name", "Concept", "c1", "r1")
    private fun creator(store: AcquisitionStore, catalog: AcquisitionCatalog = catalog(), caps: AcquisitionCapabilities = capabilities) =
        AcquisitionCharacterCreator(store, { catalog }, { caps })
    private fun run(block: suspend () -> Unit) {
        var completion: Result<Unit>? = null
        block.startCoroutine(object : Continuation<Unit> {
            override val context = EmptyCoroutineContext
            override fun resumeWith(result: Result<Unit>) { completion = result }
        })
        checkNotNull(completion) { "Uncompleted test coroutine" }.getOrThrow()
    }
    private suspend fun loaded(store: AcquisitionStore) = assertIs<LoadResult.Loaded>(store.load("hero")).state

    @Test fun literal_review_boundaries_do_not_write() {
        var writes = 0
        val store = object : AcquisitionStore {
            override suspend fun load(characterId: String) = error("Review must not load")
            override suspend fun compareAndSet(characterId: String, expectedStorageRevision: Long, next: StoredCharacter): CommitResult { writes++; error("Review must not write") }
        }
        val creator = creator(store)
        for ((xp, ap) in listOf(0L to 0L, 999L to 0L, 1000L to 1L, 1999L to 1L, 2000L to 2L, 2500L to 2L, Int.MAX_VALUE.toLong() to 2147483L)) {
            val review = creator.review(xp, "Name", "Concept")
            assertTrue(review.executable); assertEquals(ap, review.initialAP)
            assertEquals(xp, review.initialXP); assertEquals(source, review.policySource)
            assertEquals(review.reviewKey, creator.review(xp, "Name", "Concept").reviewKey)
            assertNotEquals(review.reviewKey, creator.review(xp, "Different", "Concept").reviewKey)
        }
        for (xp in listOf(-1L, Int.MAX_VALUE.toLong()+1, Long.MAX_VALUE)) assertFalse(creator.review(xp).executable)
        assertEquals(0, writes)
    }

    @Test fun fresh_creation_has_one_opening_and_no_fabricated_operation() = run {
        val store = InMemoryAcquisitionStore(); val creator = creator(store)
        val result = assertIs<NewCharacterCreationResult.Created>(creator.create(command()))
        val state = loaded(store)
        assertEquals(state, result.state)
        assertEquals(Amount(2500, 2), balance(state.ledger))
        assertEquals(0L, state.revision); assertEquals(0L, state.storageRevision); assertEquals(SyncMetadata(0), state.syncMetadata)
        assertEquals(OpeningProvenance.NewCharacter, state.ledger.opening.provenance)
        assertEquals(RequirementNode.CreationPhase.Phase.Creating, state.creationPhase)
        assertNull(state.ledger.opening.legacyMigration); assertFalse(state.character.creationComplete)
        assertEquals(2500, state.character.experience); assertEquals(2500, state.character.creationExperience)
        assertNull(state.character.abilityPointsOverride)
        assertTrue(state.instances.isEmpty() && state.character.development.isEmpty() && state.character.skills.isEmpty())
        assertTrue(state.ledger.entries.isEmpty() && state.receipts.isEmpty() && state.outbox.isEmpty())
        assertTrue(state.copySources.isEmpty() && state.syncProofs.isEmpty())
        val reopened = InMemoryAcquisitionStore(mapOf("hero" to encodeEnvelope(state)))
        assertEquals(state, assertIs<NewCharacterCreationResult.AlreadyCreated>(creator(reopened).create(command())).state)
    }

    @Test fun read_only_reconcile_never_creates_missing_head_and_ignores_current_write_policy() = run {
        val backing = InMemoryAcquisitionStore()
        val noWrite = object : AcquisitionStore by backing {
            override suspend fun compareAndSet(characterId: String, expectedStorageRevision: Long, next: StoredCharacter): CommitResult = error("Reconcile cannot write")
        }
        val disabled = creator(noWrite, catalog().copy(creationFundingPolicy = null), AcquisitionCapabilities(false, false, false))
        assertIs<LoadResult.Missing>(assertIs<NewCharacterCreationResult.Unavailable>(disabled.reconcile(command())).loadResult)
        assertIs<NewCharacterCreationResult.Created>(creator(backing).create(command()))
        assertIs<NewCharacterCreationResult.AlreadyCreated>(disabled.reconcile(command()))
        assertIs<NewCharacterCreationResult.AlreadyCreated>(disabled.create(command()))
        assertIs<NewCharacterCreationResult.IdentityConflict>(disabled.reconcile(command().copy(initialXP = 2000)))
    }

    @Test fun completion_and_explicit_xp_grants_never_refill_or_allocate_ap() = run {
        val store = InMemoryAcquisitionStore(); val first = assertIs<NewCharacterCreationResult.Created>(creator(store).create(command())).state
        val mutations = CharacterMutationCoordinator(store, ::catalog, { "id" })
        assertIs<MutationResult.Applied>(mutations.mutate(CanonicalMutation("grant", "hero", 0, MutationPayload.Grant(Amount(1000, 0)), "Explicit XP grant")))
        assertIs<MutationResult.Applied>(mutations.mutate(CanonicalMutation("finish", "hero", 1, MutationPayload.CompleteCreation)))
        val current = loaded(store)
        assertEquals(Amount(3500, 2), balance(current.ledger)); assertEquals(first.ledger.opening, current.ledger.opening)
        val changedPolicy = catalog().copy(catalogRevision = "new", resolutionRevision = "new", creationFundingPolicy = null)
        assertEquals(current, assertIs<NewCharacterCreationResult.AlreadyCreated>(creator(store, changedPolicy).create(command())).state)
        assertEquals(current, loaded(store))
    }

    @Test fun lost_reply_replays_same_origin_without_second_allocation() = run {
        val backing = InMemoryAcquisitionStore(); var commits = 0
        val uncertain = object : AcquisitionStore by backing {
            override suspend fun compareAndSet(characterId: String, expectedStorageRevision: Long, next: StoredCharacter): CommitResult {
                commits++; assertEquals(CommitResult.Committed, backing.compareAndSet(characterId, expectedStorageRevision, next))
                error("lost acknowledgement")
            }
        }
        assertIs<NewCharacterCreationResult.StorageFailure>(creator(uncertain).create(command()))
        assertIs<NewCharacterCreationResult.AlreadyCreated>(creator(backing).reconcile(command()))
        assertIs<NewCharacterCreationResult.AlreadyCreated>(creator(uncertain).create(command()))
        assertEquals(1, commits); assertEquals(Amount(2500, 2), balance(loaded(backing).ledger))
    }

    @Test fun changed_intent_existing_legacy_or_corrupt_identity_never_overwritten() = run {
        val backing = InMemoryAcquisitionStore(); creator(backing).create(command())
        for (changed in listOf(command().copy(initialXP=2000), command().copy(name="Other"), command().copy(concept="Other"), command().copy(creationId="other"), command().copy(catalogRevision="other")))
            assertIs<NewCharacterCreationResult.IdentityConflict>(creator(backing).create(changed))
        val old = loaded(backing).let { it.copy(ledger=it.ledger.copy(opening=it.ledger.opening.copy(reason="old NewCharacter opening"))) }
        val oldStore = InMemoryAcquisitionStore(mapOf("hero" to encodeEnvelope(old)))
        assertIs<NewCharacterCreationResult.IdentityConflict>(creator(oldStore).create(command()))
        assertEquals(old, loaded(oldStore))
        for (unavailable in listOf(LoadResult.MigrationRequired("unfinished legacy"), LoadResult.Corrupt("raw", "bad"), LoadResult.Unsupported("future", "future"), LoadResult.LegacyImportConflict("canonical", "legacy", null, "conflict"))) {
            val store = object : AcquisitionStore {
                override suspend fun load(characterId: String) = unavailable
                override suspend fun compareAndSet(characterId: String, expectedStorageRevision: Long, next: StoredCharacter): CommitResult = error("Existing state cannot be funded")
            }
            assertEquals(unavailable, assertIs<NewCharacterCreationResult.Unavailable>(creator(store).create(command())).loadResult)
        }
    }

    @Test fun missing_policy_capability_invalid_budget_and_stale_review_never_commit() = run {
        val store = InMemoryAcquisitionStore()
        assertIs<NewCharacterCreationResult.Unavailable>(creator(store, caps=capabilities.copy(localDurable=false)).create(command()))
        assertIs<NewCharacterCreationResult.Unavailable>(creator(store, catalog().copy(creationFundingPolicy=null)).create(command()))
        assertIs<NewCharacterCreationResult.Unavailable>(creator(store, catalog().copy(creationFundingPolicy=CreationFundingPolicy(rule, 500, source))).create(command()))
        for (xp in listOf(-1L, Int.MAX_VALUE.toLong()+1, Long.MAX_VALUE))
            assertIs<NewCharacterCreationResult.Unavailable>(creator(store).create(command(xp)))
        assertIs<NewCharacterCreationResult.StalePolicy>(creator(store, catalog().copy(catalogRevision="new")).create(command()))
        assertIs<LoadResult.Missing>(store.load("hero"))
    }

    @Test fun competing_creator_cas_conflict_returns_winner_current_state() = run {
        val backing = InMemoryAcquisitionStore(); var attempted = false
        val racing = object : AcquisitionStore by backing {
            override suspend fun compareAndSet(characterId: String, expectedStorageRevision: Long, next: StoredCharacter): CommitResult {
                if (!attempted) { attempted = true; assertIs<NewCharacterCreationResult.Created>(creator(backing).create(command())) }
                return backing.compareAndSet(characterId, expectedStorageRevision, next)
            }
        }
        assertIs<NewCharacterCreationResult.AlreadyCreated>(creator(racing).create(command()))
        assertEquals(Amount(2500, 2), balance(loaded(backing).ledger))
        assertEquals(0L, loaded(backing).storageRevision)
    }
}
