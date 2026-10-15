package com.furybook.dubl.actions

import com.furybook.dubl.acquisition.*
import com.furybook.dubl.model.*
import kotlin.coroutines.*
import kotlin.test.*

class GameplayAcquisitionInteropTest {
    private val source = RuleSourceRef("interop-fixture", "1", "Explicit twenty-XP fixture")
    private val definition = AcquisitionDefinition(DefinitionRef("test", "target"), AcquisitionKind.DEVELOPMENT, 1,
        RepeatPolicy.Single, RequirementNode.AllOf("none", source, emptyList(), NoRequirementsEvidence(source, "None")),
        PriceRule.Fixed(20, 0, source), SourceStatus.Confirmed, "target", source)
    private val catalog = AcquisitionCatalog(mapOf(definition.ref to definition), "c1", "r1")
    private class LocalStore : AcquisitionStore {
        var raw = encodeEnvelope(StoredCharacter(DublCharacter("hero"), CharacterSheetExtras(), emptyList(),
            Ledger(Opening("opening", "source", Amount(100, 0), RequirementNode.CreationPhase.Phase.Creating,
                OpeningProvenance.NewCharacter), emptyList()), emptyList(), emptyList(), SyncMetadata(0), 0, 0,
            RequirementNode.CreationPhase.Phase.Creating, formatVersion = 2, gameplay = GameplayJournal()))
        override suspend fun load(characterId: String) = decodeEnvelope(raw, true)
        override suspend fun compareAndSet(characterId: String, expectedStorageRevision: Long, next: StoredCharacter): CommitResult {
            if (state().storageRevision != expectedStorageRevision) return CommitResult.Conflict
            check(next.storageRevision == expectedStorageRevision + 1)
            raw = encodeEnvelope(next)
            return CommitResult.Committed
        }
        fun state() = (decodeEnvelope(raw, true) as LoadResult.Loaded).state
    }
    private fun run(block: suspend () -> Unit) {
        var completed: Result<Unit>? = null
        block.startCoroutine(object : Continuation<Unit> {
            override val context = EmptyCoroutineContext
            override fun resumeWith(result: Result<Unit>) { completed = result }
        })
        checkNotNull(completed).getOrThrow()
    }
    private suspend fun buy(store: LocalStore) {
        val service = AcquisitionService(store, { catalog }, { "instance" })
        val request = AcquisitionRequest("hero", listOf(AcquisitionTarget(definition.ref, targetRank = 1)))
        val preview = service.preview(request)
        assertTrue(preview.executable)
        assertIs<ApplyResult.Applied>(service.apply(ConfirmAcquisition("buy", "hero", request, preview.baseRevision, preview.previewToken)))
    }
    private suspend fun startTurn(store: LocalStore) {
        val saved = store.state()
        assertIs<GameplayEventCommitResult.Applied>(GameplayEventService(store) { GameplayCapabilities(true, true) }.apply(
            RecordGameBoundary("turn", "hero", saved.gameplay?.events?.timeline?.revision ?: 0,
                GameBoundary.TurnStarted("turn", "hero")), saved.storageRevision))
    }
    @Test fun local_gameplay_character_can_buy_and_undo_without_creating_cloud_payloads() = run {
        val store = LocalStore()
        startTurn(store)
        val events = store.state().gameplay!!.events
        buy(store)
        val saved = store.state()
        assertEquals(Amount(80, 0), balance(saved.ledger))
        assertEquals(events, saved.gameplay!!.events)
        assertTrue(saved.outbox.isEmpty())
        assertFailsWith<IllegalArgumentException> { encodeSyncEnvelope(saved) }
        assertIs<UndoEligibility.Available>(AcquisitionUndo(store).eligible("hero"))
        assertIs<ApplyResult.Applied>(AcquisitionUndo(store).undo("hero", "undo-buy", saved.revision))
        assertEquals(Amount(100, 0), balance(store.state().ledger))
        assertEquals(events, store.state().gameplay!!.events)
        assertTrue(store.state().outbox.isEmpty())
    }
    @Test fun later_game_event_blocks_acquisition_undo_even_when_character_numbers_are_unchanged() = run {
        val store = LocalStore()
        buy(store)
        val before = store.state().character
        startTurn(store)
        assertEquals(before, store.state().character)
        assertIs<UndoEligibility.Unavailable>(AcquisitionUndo(store).eligible("hero"))
        assertIs<ApplyResult.Blocked>(AcquisitionUndo(store).undo("hero", "stale-undo", store.state().revision))
        assertEquals(Amount(80, 0), balance(store.state().ledger))
    }
    @Test fun inert_purchase_confirmation_does_not_reenable_undo_across_a_game_event() = run {
        val store = LocalStore()
        buy(store)
        startTurn(store)
        val service = AcquisitionService(store, { catalog }, { "unused" })
        val request = AcquisitionRequest("hero", listOf(AcquisitionTarget(definition.ref, targetRank = 1)))
        val preview = service.preview(request)
        assertTrue(preview.changes.isEmpty())
        assertIs<ApplyResult.Applied>(service.apply(ConfirmAcquisition("noop", "hero", request, preview.baseRevision, preview.previewToken)))
        assertIs<UndoEligibility.Unavailable>(AcquisitionUndo(store).eligible("hero"))
        assertEquals(Amount(80, 0), balance(store.state().ledger))
    }
    @Test fun buying_does_not_allow_undo_of_an_earlier_resource_action() = run {
        val store = LocalStore()
        val actions = GameplayService(store) { GameplayCapabilities(true, true) }
        assertIs<GameplayCommitResult.Applied>(actions.apply(StartOverexertion("effort", "hero", 0, PhysicalAttribute.STRENGTH), 0))
        buy(store)
        val saved = store.state()
        assertEquals(2, saved.character.enduranceCurrent)
        assertEquals("effort", saved.gameplay!!.pending?.startOperationId)
        assertIs<GameplayCommitResult.Blocked>(actions.apply(UndoGameplay("undo-effort", "hero", 1, "effort"), saved.storageRevision))
        assertEquals(saved, store.state())
    }
}
