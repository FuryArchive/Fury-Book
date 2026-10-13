package com.furybook.dubl.acquisition

import com.furybook.content.*
import com.furybook.dubl.model.*
import kotlinx.coroutines.runBlocking
import kotlin.test.*

/** Actual manifest-loaded rules and canonical execution, not platform renderer evidence. */
class AcquisitionAuthorFoundationTest {
    private val source = FcpTextSource { javaClass.classLoader.getResource(it)?.readText() }
    private fun catalog(chi: Boolean = false) = loadAcquisitionCatalog(listOf("dubl-3.69", "dubl-chi-3.69").map {
        FcpContentPack.load("fcp/$it", source)
    }, chi).catalog
    private fun ref(id: String) = DefinitionRef("dubl-3.69", id)
    private fun context(catalog: AcquisitionCatalog, cha: Int, phase: RequirementNode.CreationPhase.Phase) = AcquisitionContext(
        DublCharacter("hero", attributes=AttributeId.entries.associateWith { AttributeValue(if(it == AttributeId.CHARISMA) cha else 0) }),
        emptyList(), emptySet(), phase, emptyList(), catalog)

    @Test fun actual_core_policy_and_both_pack_authority_are_loaded() {
        for (chi in listOf(false, true)) {
            val catalog = catalog(chi)
            val policy = assertNotNull(catalog.creationFundingPolicy)
            assertEquals(1000L, policy.xpPerAP)
            assertEquals("UR_NEW_CHARACTER_INITIAL_AP_PER_1000_XP", policy.ruleId)
            assertEquals(11903, policy.sourceRef.paragraph)
            assertEquals("t.z1o9nhkkrzeb", policy.sourceRef.sheet)
            assertTrue(policy.sourceRef.originalText.contains("полные 1000 опыта"))
            assertTrue(catalog.sourceRuleRevision.isNotBlank())
            val rule = assertIs<PriceRule.PerRank>(catalog.definitions.getValue(ref("feat_7fc6fc6e63da9688")).priceRule)
            assertEquals("UR_FIXED_DEVELOPMENT_PRICE_PER_RANK", rule.sourceRef.resolutionId)
            assertEquals(policy.sourceRef.resolutionRevision, rule.sourceRef.resolutionRevision)
        }
    }

    @Test fun numeric_per_rank_price_does_not_override_creation_only_source_condition() = runBlocking {
        val catalog = catalog(); val beauty = catalog.definitions.getValue(ref("feat_65e4315227fdc855"))
        val creating = RequirementNode.CreationPhase.Phase.Creating
        val finalized = RequirementNode.CreationPhase.Phase.Finalized
        assertEquals(RequirementStatus.Satisfied, evaluate(beauty.requirements, context(catalog, 4, creating)).status)
        assertEquals(RequirementStatus.Unsatisfied, evaluate(beauty.requirements, context(catalog, 3, creating)).status)
        val after = context(catalog, 4, finalized)
        assertEquals(RequirementStatus.Unsatisfied, evaluate(beauty.requirements, after).status)
        assertNull(beauty.requirements.sourceRef.resolutionId)
        for ((from, to, xp) in listOf(Triple(0, 1, 30L), Triple(1, 2, 30L), Triple(0, 2, 60L))) {
            val change = AcquisitionChange(AcquisitionKind.DEVELOPMENT, beauty.ref, "beauty", from, to, emptyMap(), null)
            assertEquals(Amount(xp, 0), assertIs<PriceResult.Known>(quote(change, after)).cost)
        }
        val original = StoredCharacter(after.character.copy(creationComplete=true), CharacterSheetExtras(),
            listOf(AcquisitionInstance("beauty", beauty.ref, 1, emptyMap(), null, AcquisitionOrigin.New)),
            Ledger(Opening("manual-compatibility-fixture", "not-a-source-valid-build", Amount(100, 7), finalized, OpeningProvenance.UserConfirmed, "Explicit negative compatibility scenario"), emptyList()),
            emptyList(), emptyList(), SyncMetadata(0), 0, 0, finalized)
        val store = InMemoryAcquisitionStore(mapOf("hero" to encodeEnvelope(original)))
        val service = AcquisitionService(store, { catalog }, { "op" })
        val request = AcquisitionRequest("hero", listOf(AcquisitionTarget(beauty.ref, "beauty", 2)))
        val preview = service.preview(request)
        assertFalse(preview.executable)
        assertTrue(preview.issues.any { it.status == RequirementStatus.Unsatisfied })
        assertIs<ApplyResult.Blocked>(service.apply(ConfirmAcquisition("blocked", "hero", request, preview.baseRevision, preview.previewToken)))
        assertEquals(original, assertIs<LoadResult.Loaded>(store.load("hero")).state)
    }

    @Test fun actual_new_funding_creation_purchase_carryover_restart_postcreation_ap_receipt_and_undo() = runBlocking {
        val catalog = catalog(); var store = InMemoryAcquisitionStore()
        val command = CreateAcquisitionCharacter("creation", "hero", 2500, "Hero", "", catalog.catalogRevision, catalog.resolutionRevision)
        val creator = AcquisitionCharacterCreator(store, { catalog }, { AcquisitionCapabilities(true, false, true) })
        val opening = assertIs<NewCharacterCreationResult.Created>(creator.create(command)).state.ledger.opening
        suspend fun buy(entry: String, rank: Int, operation: String, expected: Amount, option: String? = null): ConfirmAcquisition {
            val service = AcquisitionService(store, { catalog }, { operation })
            val request = AcquisitionRequest("hero", listOf(AcquisitionTarget(ref(entry), if (entry.startsWith("attribute:")) null else "owned/$entry", rank, optionId=option)))
            val preview = service.preview(request)
            assertTrue(preview.executable, preview.issues.toString()); assertEquals(expected, preview.cost)
            val confirm = ConfirmAcquisition(operation, "hero", request, preview.baseRevision, preview.previewToken)
            assertIs<ApplyResult.Applied>(service.apply(confirm))
            return confirm
        }
        // Independently retained source literals: 0->4 attribute cumulative250XP each;
        // Commander Voice25XP; athletics0->1 creation10XP. No current quote generates expectations.
        buy("attribute:CHARISMA", 4, "charisma", Amount(250, 0))
        buy("attribute:CONSTITUTION", 4, "constitution", Amount(250, 0))
        buy("feat_a700b29906107b2b", 1, "voice", Amount(25, 0))
        buy("athletics", 1, "athletics", Amount(10, 0))
        var current = assertIs<LoadResult.Loaded>(store.load("hero")).state
        assertEquals(Amount(1965, 2), balance(current.ledger))
        val mutation = CharacterMutationCoordinator(store, { catalog }, { "mutation" })
        assertIs<MutationResult.Applied>(mutation.mutate(CanonicalMutation("complete", "hero", current.revision, MutationPayload.CompleteCreation)))
        current = assertIs<LoadResult.Loaded>(store.load("hero")).state
        assertEquals(Amount(1965, 2), balance(current.ledger)); assertEquals(opening, current.ledger.opening)
        store = InMemoryAcquisitionStore(mapOf("hero" to encodeEnvelope(current)))
        val access = buy("access_17f99cc35c559d82", 1, "morale", Amount(0, 1), "morale")
        val service = AcquisitionService(store, { catalog }, { "retry" })
        assertIs<ApplyResult.AlreadyApplied>(service.apply(access))
        current = assertIs<LoadResult.Loaded>(store.load("hero")).state
        assertEquals(Amount(1965, 1), balance(current.ledger)); assertEquals(Amount(0, 1), current.receipts.last().result.cost)
        assertEquals(Amount(0, -1), current.ledger.entries.last().amountDelta)
        store = InMemoryAcquisitionStore(mapOf("hero" to encodeEnvelope(current)))
        assertIs<ApplyResult.Applied>(AcquisitionUndo(store).undo("hero", "undo-morale", current.revision))
        val undone = assertIs<LoadResult.Loaded>(store.load("hero")).state
        assertEquals(Amount(1965, 2), balance(undone.ledger)); assertEquals(opening, undone.ledger.opening)
        assertEquals(ReceiptStatus.Undone, undone.receipts.single { it.operationId == "morale" }.status)
        val reopened = InMemoryAcquisitionStore(mapOf("hero" to encodeEnvelope(undone)))
        val disabled = AcquisitionCharacterCreator(reopened, { catalog.copy(creationFundingPolicy=null) }, { AcquisitionCapabilities(false,false,false) })
        assertEquals(undone, assertIs<NewCharacterCreationResult.AlreadyCreated>(disabled.reconcile(command)).state)
    }
}
