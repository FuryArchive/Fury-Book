package com.furybook.dubl.acquisition

import com.furybook.dubl.application.DublApplication
import com.furybook.dubl.data.*
import com.furybook.dubl.model.*
import kotlinx.coroutines.*
import kotlin.coroutines.*
import kotlin.test.*

/** Real serialized-store interleaving; no native/disk durability claim. */
class AcquisitionManualBaseRevisionTest {
    private val catalog = AcquisitionCatalog(emptyMap(), "manual-fixture", "manual-rules")
    private val phase = RequirementNode.CreationPhase.Phase.Creating
    private fun initial() = StoredCharacter(DublCharacter("hero", name = "original", size = 5),
        CharacterSheetExtras(), emptyList(), Ledger(Opening("opening", "manual-fixture", Amount(100, 2),
            phase, OpeningProvenance.NewCharacter), emptyList()), emptyList(), emptyList(), SyncMetadata(0), 0, 0, phase)
    private fun run(block: suspend () -> Unit) {
        var result: Result<Unit>? = null
        block.startCoroutine(object : Continuation<Unit> {
            override val context = EmptyCoroutineContext
            override fun resumeWith(value: Result<Unit>) { result = value }
        })
        checkNotNull(result).getOrThrow()
    }
    private fun application(store: AcquisitionStore) = DublApplication(
        object : CharacterStore {
            override fun load() = AppSnapshot(listOf(initial().character), "hero")
            override fun save(snapshot: AppSnapshot) = error("Legacy writes are forbidden")
        }, object : CharacterExtrasStore {
            override fun load(characterId: String) = CharacterSheetExtras()
            override fun save(characterId: String, extras: CharacterSheetExtras) = error("Legacy extras are forbidden")
        }, { "unused" }, acquisitionService = AcquisitionService(store, { catalog }, { "unused" }),
        mutationCoordinator = CharacterMutationCoordinator(store, { catalog }, { "unused" }))
    private suspend fun saved(store: AcquisitionStore) = assertIs<LoadResult.Loaded>(store.load("hero")).state

    @Test fun actual_preparation_load_rejects_old_manual_draft_after_competing_edit() = run {
        val backing = InMemoryAcquisitionStore(mapOf("hero" to encodeEnvelope(initial())))
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        var pauseNextLoad = false; var writes = 0; var ids = 0
        val store = object : AcquisitionStore by backing {
            override suspend fun load(characterId: String): LoadResult {
                if (pauseNextLoad) { pauseNextLoad = false; entered.complete(Unit); release.await() }
                return backing.load(characterId) // Authoritative read is AFTER the competing commit.
            }
            override suspend fun compareAndSet(characterId: String, expectedStorageRevision: Long,
                next: StoredCharacter): CommitResult {
                writes++
                return backing.compareAndSet(characterId, expectedStorageRevision, next)
            }
        }
        val scope = AcquisitionScope("manual-owner", "hero", 0, 0, 0)
        val control = AcquisitionController(ApplicationAcquisitionCommandPort(application(store), store, { catalog },
            { AcquisitionCapabilities(true, false, true) }, { it == scope }), { scope }, { "manual-${++ids}" })
        val draft = control.readCanonicalView()
        val patch = CharacterPatch.fromCanonicalState(draft.state, draft.state.character.copy(size = 6))
        // The UI's preliminary read can still acknowledge R0 immediately before Shared prepares.
        assertEquals(draft.state.revision, control.readCanonicalView().state.revision)
        coroutineScope {
            pauseNextLoad = true
            val preparing = async(Dispatchers.Unconfined, start = CoroutineStart.UNDISPATCHED) {
                control.prepareManual(patch, Amount.ZERO, "Explicit size correction", expectedBaseRevision = draft.state.revision)
            }
            try {
                entered.await()
                val competing = CharacterPatch.fromCanonicalState(draft.state,
                    draft.state.character.copy(name = "competing name"))
                assertIs<MutationResult.Applied>(CharacterMutationCoordinator(store, { catalog }, { "unused" }).mutate(
                    CanonicalMutation("competing", "hero", draft.state.revision,
                        MutationPayload.NonFinancial(competing))))
                val afterCompeting = saved(backing); val bytes = encodeEnvelope(afterCompeting)
                assertEquals(draft.state.revision + 1, afterCompeting.revision)
                release.complete(Unit)
                val stale = preparing.await()
                assertEquals(AcquisitionUiPhase.Stale, stale.phase)
                assertFalse(stale.canConfirm); assertFalse(stale.canRetry); assertNull(stale.request)
                assertNull(control.retainedOperationId); assertEquals(0, ids); assertEquals(1, writes)
                assertEquals(AcquisitionUiPhase.Stale, control.confirm(stale.key).phase)
                assertFailsWith<IllegalStateException> { control.retrySameCommand() }
                assertFailsWith<IllegalStateException> { control.refresh(stale.key) }
                assertEquals(bytes, encodeEnvelope(saved(backing)))
                assertNull(control.retainedOperationId); assertEquals(0, ids); assertEquals(1, writes)

                // Only an explicitly NEW draft captured at R1 may be reviewed and confirmed.
                val fresh = control.readCanonicalView()
                val freshPatch = CharacterPatch.fromCanonicalState(fresh.state, fresh.state.character.copy(size = 6))
                val ready = control.prepareManual(freshPatch, Amount.ZERO, "New explicit size correction", expectedBaseRevision = fresh.state.revision)
                assertEquals(AcquisitionUiPhase.Ready, ready.phase)
                assertEquals(AcquisitionUiPhase.Committed, control.confirm(ready.key).phase)
                val final = saved(backing)
                assertEquals("competing name", final.character.name); assertEquals(6, final.character.size)
                assertEquals(Amount(100, 2), balance(final.ledger)); assertEquals(2, writes); assertEquals(1, ids)
            } finally { release.complete(Unit) }
        }
    }

    @Test fun existing_three_argument_manual_call_remains_source_compatible() = run {
        val backing = InMemoryAcquisitionStore(mapOf("hero" to encodeEnvelope(initial())))
        val scope = AcquisitionScope("manual-owner", "hero", 0, 0, 0)
        val control = AcquisitionController(ApplicationAcquisitionCommandPort(application(backing), backing, { catalog },
            { AcquisitionCapabilities(true, false, true) }, { it == scope }), { scope }, { "manual-compatible" })
        val base = control.readCanonicalView()
        val ready = control.prepareManual(CharacterPatch.fromCanonicalState(base.state,
            base.state.character.copy(size = 6)), Amount.ZERO, "Existing caller")
        assertEquals(AcquisitionUiPhase.Ready, ready.phase)
        assertEquals(AcquisitionUiPhase.Committed, control.confirm(ready.key).phase)
        assertEquals(6, saved(backing).character.size)
    }
}
