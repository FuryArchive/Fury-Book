package com.furybook.dubl.acquisition

import com.furybook.content.FcpContentPack
import com.furybook.content.FcpTextSource
import com.furybook.dubl.model.*
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class AcquisitionDrawbackSourceTest {
    private val catalog by lazy { loadAcquisitionCatalog(listOf(FcpContentPack.load("fcp/dubl-3.69",
        FcpTextSource { javaClass.classLoader.getResource(it)?.readText() })), false).catalog }
    private fun ref(id: String) = DefinitionRef("dubl-3.69", id)
    private val thin = ref("drawback_912792560e4baf64")
    private val sickly = ref("drawback_cab508624d01d110")
    private val lostArm = ref("drawback_a5a24082b1ceb7c2")
    private fun initial(phase: RequirementNode.CreationPhase.Phase = RequirementNode.CreationPhase.Phase.Creating,
        constitution: Int = 3): StoredCharacter {
        val character = DublCharacter("drawback", creationComplete = phase == RequirementNode.CreationPhase.Phase.Finalized).let { it.copy(attributes = it.attributes +
            (AttributeId.CONSTITUTION to it.attributes.getValue(AttributeId.CONSTITUTION).copy(base = constitution))) }
        return StoredCharacter(character, CharacterSheetExtras(), emptyList(),
            Ledger(Opening("opening", "confirmed-source", Amount(100, 7), phase,
                OpeningProvenance.NewCharacter), emptyList()), emptyList(), emptyList(), SyncMetadata(0), 0, 0, phase)
    }
    private fun request(definition: DefinitionRef, rank: Int = 1,
        confirmations: List<RequirementWitness.External> = emptyList()) =
        AcquisitionRequest("drawback", listOf(AcquisitionTarget(definition, targetRank = rank)),
            externalConfirmations = confirmations)
    private fun externalNodes(node: RequirementNode): List<RequirementNode.ExternalEvidence> = when (node) {
        is RequirementNode.ExternalEvidence -> listOf(node)
        is RequirementNode.AllOf -> node.children.flatMap(::externalNodes)
        is RequirementNode.AnyOf -> node.children.flatMap(::externalNodes)
        else -> emptyList()
    }
    private fun eventConfirmation() = externalNodes(catalog.definitions.getValue(lostArm).requirements).single().let {
        RequirementWitness.External(it.evidenceKey, it.scopeId, "confirmed-story-event", it.sourceRef)
    }

    @Test fun real_creation_reward_is_signed_once_durable_and_exactly_undoable() = runBlocking<Unit> {
        val before = initial()
        val store = InMemoryAcquisitionStore(mapOf("drawback" to encodeEnvelope(before)))
        val service = AcquisitionService(store, { catalog }, { "generated" })
        val request = request(thin, 3)
        val preview = service.preview(request)
        assertTrue(preview.executable, preview.issues.toString())
        assertEquals(Amount(-90, 0), preview.cost)
        assertEquals(Amount(190, 7), preview.remaining)
        val command = ConfirmAcquisition("creation-reward", "drawback", request,
            preview.baseRevision, preview.previewToken)
        val receipt = assertIs<ApplyResult.Applied>(service.apply(command)).receipt
        assertIs<ApplyResult.AlreadyApplied>(service.apply(command))
        assertEquals(Amount.ZERO, service.preview(request).cost)
        val saved = assertIs<LoadResult.Loaded>(store.load("drawback")).state
        assertEquals(Amount(90, 0), saved.ledger.entries.single().amountDelta)
        assertEquals(Amount(190, 7), balance(saved.ledger))
        assertEquals(Amount(-90, 0), receipt.result.cost)
        val recorded = assertIs<AcquisitionSavedEvidence.Recorded>(
            savedAcquisitionOperation(receipt, saved.ledger.entries.single()).evidence)
        assertEquals(Amount(-90, 0), recorded.prices.single().cost)
        assertTrue(recorded.prices.single().componentSources.any { it.originalText.contains("-30") })

        val reopened = assertIs<LoadResult.Loaded>(decodeEnvelope(encodeEnvelope(saved))).state
        assertEquals(saved.receipts, reopened.receipts)
        val reopenedStore = InMemoryAcquisitionStore(mapOf("drawback" to encodeEnvelope(reopened)))
        // A later catalog price cannot reinterpret this saved credit or its inverse.
        val changed = catalog.copy(catalogRevision = catalog.catalogRevision + "/later-price",
            definitions = catalog.definitions + (thin to catalog.definitions.getValue(thin).copy(
                priceRule = PriceRule.Fixed(999, 9, RuleSourceRef("later", "2", "Later price")))))
        assertEquals(receipt, assertIs<ApplyResult.AlreadyApplied>(
            AcquisitionService(reopenedStore, { changed }, { "generated" }).apply(command)).receipt)
        val undo = AcquisitionUndo(reopenedStore)
        assertIs<UndoEligibility.Available>(undo.eligible("drawback"))
        assertIs<ApplyResult.Applied>(undo.undo("drawback", "undo-reward", reopened.revision))
        val restored = assertIs<LoadResult.Loaded>(reopenedStore.load("drawback")).state
        assertEquals(Amount(100, 7), balance(restored.ledger))
        assertEquals(acquisitionProjectionCanonical(before), acquisitionProjectionCanonical(restored))
        assertIs<ApplyResult.AlreadyUndone>(serviceFor(reopenedStore).apply(command))
    }

    private fun serviceFor(store: AcquisitionStore) = AcquisitionService(store, { catalog }, { "generated" })

    @Test fun creation_requirements_are_enforced_but_finalized_drawbacks_have_no_reward() = runBlocking<Unit> {
        val creating = plan(request(sickly), initial(constitution = 5), catalog)
        assertFalse(creating.executable)
        assertTrue(creating.issues.any { it.status == RequirementStatus.Unsatisfied })
        val before = initial(RequirementNode.CreationPhase.Phase.Finalized, constitution = 9)
        val store = InMemoryAcquisitionStore(mapOf("drawback" to encodeEnvelope(before)))
        val service = serviceFor(store)
        val request = request(thin, 3)
        val preview = service.preview(request)
        assertTrue(preview.executable, preview.issues.toString())
        assertEquals(Amount.ZERO, preview.cost)
        val receipt = assertIs<ApplyResult.Applied>(service.apply(ConfirmAcquisition("later-injury", "drawback",
            request, preview.baseRevision, preview.previewToken))).receipt
        assertEquals(Amount.ZERO, receipt.result.cost)
        val saved = assertIs<LoadResult.Loaded>(store.load("drawback")).state
        assertEquals(LedgerEntryType.Purchase, saved.ledger.entries.single().type)
        assertEquals(Amount(100, 7), balance(saved.ledger))
        assertEquals(3, saved.instances.single { it.definition == thin }.rank)
        assertIs<ApplyResult.Applied>(AcquisitionUndo(store).undo("drawback", "undo-later-injury", saved.revision))
        assertEquals(acquisitionProjectionCanonical(before), acquisitionProjectionCanonical(
            assertIs<LoadResult.Loaded>(store.load("drawback")).state))
    }

    @Test fun event_only_drawbacks_require_finalization_and_the_exact_story_confirmation() = runBlocking<Unit> {
        val confirmation = eventConfirmation()
        assertFalse(plan(request(lostArm, confirmations = listOf(confirmation)), initial(), catalog).executable)
        val before = initial(RequirementNode.CreationPhase.Phase.Finalized)
        assertFalse(plan(request(lostArm), before, catalog).executable)
        assertFalse(plan(request(lostArm, confirmations = listOf(confirmation.copy(scopeId = thin.entryId))),
            before, catalog).executable)
        val store = InMemoryAcquisitionStore(mapOf("drawback" to encodeEnvelope(before)))
        val service = serviceFor(store)
        val request = request(lostArm, confirmations = listOf(confirmation))
        val preview = service.preview(request)
        assertTrue(preview.executable, preview.issues.toString())
        assertEquals(Amount.ZERO, preview.cost)
        assertIs<ApplyResult.Applied>(service.apply(ConfirmAcquisition("event-injury", "drawback", request,
            preview.baseRevision, preview.previewToken)))
        val saved = assertIs<LoadResult.Loaded>(decodeEnvelope(encodeEnvelope(
            assertIs<LoadResult.Loaded>(store.load("drawback")).state))).state
        assertEquals(listOf(confirmation), retainedAcquisitionConfirmations(saved))
        assertEquals(Amount(100, 7), balance(saved.ledger))
        assertIs<ApplyResult.Applied>(AcquisitionUndo(store).undo("drawback", "undo-event", saved.revision))
        assertEquals(acquisitionProjectionCanonical(before), acquisitionProjectionCanonical(
            assertIs<LoadResult.Loaded>(store.load("drawback")).state))
    }
}
