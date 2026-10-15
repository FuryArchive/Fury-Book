package com.furybook.dubl.acquisition

import com.furybook.dubl.model.*
import kotlinx.coroutines.runBlocking
import kotlin.test.*

/** Controlled prices isolate payment identity; these amounts do not establish later Blood-card prices. */
class AcquisitionPaymentOptionsTest {
    private val ref = DefinitionRef("payment-test", "ranked-entry")
    private val source = RuleSourceRef("payment-test", "1", "Each acquisition may be paid from either of the two stated pools.")
    private fun catalog(payment: Boolean = true): AcquisitionCatalog {
        val definition = AcquisitionDefinition(ref, AcquisitionKind.DEVELOPMENT, 3, RepeatPolicy.Single,
            RequirementNode.AllOf("admitted", source, emptyList(), NoRequirementsEvidence(source, "Explicit fixture admission")),
            PriceRule.ByOption(mapOf("xp" to PriceRule.PerRank(10, 0, source), "ap" to PriceRule.PerRank(0, 3, source)), source),
            SourceStatus.Confirmed, "Controlled ranked entry", source, abilityOptionIds = setOf("xp", "ap"),
            classificationSources = if (payment) mapOf("payment:transition-options" to source) else emptyMap())
        return AcquisitionCatalog(mapOf(ref to definition), "payment-catalog", "payment-rules")
    }
    private fun state(instances: List<AcquisitionInstance> = emptyList()): StoredCharacter {
        val phase = RequirementNode.CreationPhase.Phase.Creating
        return StoredCharacter(DublCharacter("payment"), CharacterSheetExtras(), instances,
            Ledger(Opening("opening", "confirmed", Amount(100, 20), phase, OpeningProvenance.NewCharacter), emptyList()),
            emptyList(), emptyList(), SyncMetadata(0), 0, 0, phase)
    }
    private fun request(rank: Int, option: String?) = AcquisitionRequest("payment",
        listOf(AcquisitionTarget(ref, "owned", rank, optionId = option)))
    private suspend fun buy(service: AcquisitionService, operation: String, rank: Int, option: String?): Pair<ConfirmAcquisition, Receipt> {
        val request = request(rank, option)
        val preview = service.preview(request)
        assertTrue(preview.executable, preview.issues.toString())
        val command = ConfirmAcquisition(operation, "payment", request, preview.baseRevision, preview.previewToken)
        return command to assertIs<ApplyResult.Applied>(service.apply(command)).receipt
    }

    @Test fun marked_payment_is_selected_per_transition_and_saved_without_becoming_ownership() = runBlocking<Unit> {
        val rules = catalog()
        val store = InMemoryAcquisitionStore(mapOf("payment" to encodeEnvelope(state())))
        val service = AcquisitionService(store, { rules }, { "generated" })
        val (firstCommand, firstReceipt) = buy(service, "paid-xp", 1, "xp")
        val first = assertIs<LoadResult.Loaded>(store.load("payment")).state
        assertTrue(firstReceipt.result.changes.single().paymentOption)
        assertNull(first.instances.single().abilityOptionId)
        assertEquals("xp", firstReceipt.result.changes.single().optionId)
        assertEquals(Amount(10, 0), firstReceipt.result.cost)
        assertTrue(encodeEnvelope(first).contains("\"paymentOption\":true"))
        assertIs<LoadResult.Corrupt>(decodeEnvelope(encodeEnvelope(first).replace("\"paymentOption\":true", "\"paymentOption\":false")))
        val missing = service.preview(request(2, null))
        assertFalse(missing.executable)
        assertTrue(missing.issues.any { it.status == RequirementStatus.ChoiceRequired })
        val (secondCommand, secondReceipt) = buy(service, "paid-ap", 2, "ap")
        assertEquals(Amount(0, 3), secondReceipt.result.cost)
        assertEquals("ap", secondReceipt.result.changes.single().optionId)
        assertIs<ApplyResult.AlreadyApplied>(service.apply(secondCommand))
        val second = assertIs<LoadResult.Loaded>(store.load("payment")).state
        assertNull(second.instances.single().abilityOptionId)
        assertEquals(Amount(90, 17), balance(second.ledger))
        val reopened = assertIs<LoadResult.Loaded>(decodeEnvelope(encodeEnvelope(second))).state
        assertEquals(second, reopened)
        assertEquals(listOf(Amount(10, 0), Amount(0, 3)), reopened.receipts.map { it.result.cost })
        assertIs<ApplyResult.Applied>(AcquisitionUndo(store).undo("payment", "undo-ap", second.revision))
        assertEquals(acquisitionProjectionCanonical(first), acquisitionProjectionCanonical(
            assertIs<LoadResult.Loaded>(store.load("payment")).state))
        assertEquals(firstReceipt, assertIs<ApplyResult.AlreadyApplied>(service.apply(firstCommand)).receipt)
    }

    @Test fun earlier_stored_payment_is_preserved_on_read_and_does_not_default_the_next_currency() = runBlocking<Unit> {
        val rules = catalog()
        val prior = AcquisitionInstance("owned", ref, 1, emptyMap(), "xp", AcquisitionOrigin.New)
        val before = state(listOf(prior))
        assertNull(instanceViews(before, rules).single().optionId)
        assertEquals("xp", before.instances.single().abilityOptionId)
        val store = InMemoryAcquisitionStore(mapOf("payment" to encodeEnvelope(before)))
        val service = AcquisitionService(store, { rules }, { "generated" })
        assertFalse(service.preview(request(2, null)).executable)
        buy(service, "new-ap", 2, "ap")
        val after = assertIs<LoadResult.Loaded>(store.load("payment")).state
        assertNull(after.instances.single().abilityOptionId)
        assertIs<ApplyResult.Applied>(AcquisitionUndo(store).undo("payment", "undo-new-ap", after.revision))
        assertEquals(acquisitionProjectionCanonical(before), acquisitionProjectionCanonical(
            assertIs<LoadResult.Loaded>(store.load("payment")).state))
    }

    @Test fun unmarked_ability_variants_keep_their_existing_identity_and_old_serialization() = runBlocking<Unit> {
        val rules = catalog(payment = false)
        val store = InMemoryAcquisitionStore(mapOf("payment" to encodeEnvelope(state())))
        val service = AcquisitionService(store, { rules }, { "generated" })
        buy(service, "variant-xp", 1, "xp")
        val saved = assertIs<LoadResult.Loaded>(store.load("payment")).state
        assertEquals("xp", saved.instances.single().abilityOptionId)
        val raw = encodeEnvelope(saved)
        assertFalse(raw.contains("paymentOption"))
        assertEquals(raw, encodeEnvelope(assertIs<LoadResult.Loaded>(decodeEnvelope(raw)).state))
        val other = service.preview(request(2, "ap"))
        assertFalse(other.executable)
        assertTrue(other.issues.any { it.status == RequirementStatus.Conflict })
        assertTrue(service.preview(request(2, "xp")).executable)
    }

    @Test fun payment_identity_effect_requires_the_exact_source_classification_on_replay() {
        val before = state()
        val marked = catalog()
        val change = plan(request(1, "xp"), before, marked).changes.single()
        assertTrue(change.paymentOption)
        assertTrue(source in assertIs<PriceResult.Known>(quote(change, acquisitionContext(before, marked))).componentSources)
        assertTrue(validateAcquisitionProjection(listOf(change.copy(paymentOption = false)), before, marked).issues.isNotEmpty())
        val variant = catalog(payment = false)
        assertIs<PriceResult.Unknown>(quote(change, acquisitionContext(before, variant)))
        assertTrue(validateAcquisitionProjection(listOf(change), before, variant).issues.isNotEmpty())
    }
}
