package com.furybook.dubl.acquisition

import com.furybook.content.FcpContentPack
import com.furybook.content.FcpTextSource
import com.furybook.dubl.model.*
import kotlinx.coroutines.runBlocking
import kotlin.test.*

/** Current source-bound selections must be the exact paid/owned instances, never similarly named rows. */
class AcquisitionSubjectParametersTest {
    private val catalog by lazy {
        loadAcquisitionCatalog(listOf(FcpContentPack.load("fcp/dubl-3.69",
            FcpTextSource { javaClass.classLoader.getResource(it)?.readText() })), false).catalog
    }
    private fun ref(id: String) = DefinitionRef("dubl-3.69", id)
    private val deep = ref("feat_cd965f927a426cc2")
    private val contacts = ref("feat_3acde4a62c69d01d")
    private val access = ref("access_b832538f3a803048")
    private fun initial(instances: List<AcquisitionInstance>): StoredCharacter = StoredCharacter(DublCharacter("subject"), CharacterSheetExtras(), instances,
        Ledger(Opening("opening", "source-selection-regression", Amount(1000, 10), RequirementNode.CreationPhase.Phase.Creating,
            OpeningProvenance.NewCharacter), emptyList()), emptyList(), emptyList(), SyncMetadata(0), 0, 0, RequirementNode.CreationPhase.Phase.Creating)
    private fun owned(id: String, definition: DefinitionRef, rank: Int, parameters: Map<String, String>) =
        AcquisitionInstance(id, definition, rank, parameters, null, AcquisitionOrigin.New)
    private fun store(state: StoredCharacter) = InMemoryAcquisitionStore(mapOf("subject" to encodeEnvelope(state)))
    private suspend fun saved(store: AcquisitionStore) = assertIs<LoadResult.Loaded>(store.load("subject")).state
    private fun service(store: AcquisitionStore) = AcquisitionService(store, { catalog }, { "operation" })
    private suspend fun buy(service: AcquisitionService, request: AcquisitionRequest, operation: String): Receipt {
        val preview = service.preview(request)
        assertTrue(preview.executable, preview.issues.toString())
        return assertIs<ApplyResult.Applied>(service.apply(ConfirmAcquisition(operation, "subject", request, preview.baseRevision, preview.previewToken))).receipt
    }

    @Test fun deep_study_raises_the_selected_knowledge_only_and_preserves_other_rank_four() = runBlocking {
        val selected = owned("selected-knowledge", ref("knowledge_template"), 3,
            mapOf("specialization" to "history-local", "specializationName" to "История города"))
        val unrelated = owned("other-knowledge", ref("knowledge_academic"), 4, emptyMap())
        val original = initial(listOf(selected, unrelated))
        val request = AcquisitionRequest("subject", listOf(AcquisitionTarget(deep, "deep-selected", 1,
            mapOf("knowledgeInstanceId" to selected.instanceId))))
        val preview = plan(request, original, catalog)
        assertTrue(preview.executable, preview.issues.toString())
        assertEquals(listOf(selected.instanceId, "deep-selected"), preview.changes.map { it.instanceId })
        assertEquals(3, preview.changes.first().fromRank)
        assertEquals(4, preview.changes.first().toRank)
        assertEquals(selected.parameters, preview.changes.first().parameters)
        assertEquals(Amount(90, 0), preview.cost) // Current skill 3→4=40 XP, selected one-rank development=50 XP.
        val backing = store(original)
        assertEquals(Amount(90, 0), buy(service(backing), request, "deep-purchase").result.cost)
        val after = saved(backing)
        assertEquals(4, after.instances.single { it.instanceId == selected.instanceId }.rank)
        assertEquals(unrelated, after.instances.single { it.instanceId == unrelated.instanceId })
        val duplicate = AcquisitionRequest("subject", listOf(AcquisitionTarget(deep, "duplicate-deep", 1,
            mapOf("knowledgeInstanceId" to selected.instanceId))))
        assertFalse(plan(duplicate, after, catalog).executable)
        val missing = AcquisitionRequest("subject", listOf(AcquisitionTarget(deep, "missing-deep", 1,
            mapOf("knowledgeInstanceId" to "not-an-owned-instance"))))
        assertFalse(plan(missing, original, catalog).executable)
        assertEquals(encodeEnvelope(after), encodeEnvelope(saved(backing)))
    }

    @Test fun contacts_access_is_bought_for_the_same_organization_and_sibling_targets_do_not_leak() = runBlocking {
        val accessA = owned("access-a", access, 1, mapOf("organizationId" to "organization:a", "organizationName" to "Организация А"))
        val original = initial(listOf(accessA))
        fun target(organization: String) = AcquisitionTarget(contacts, "contacts-$organization", 1,
            mapOf("organizationId" to "organization:$organization", "organizationName" to if(organization=="a") "Организация А" else "Организация Б"))
        val a = target("a")
        val b = target("b")
        val forA = plan(AcquisitionRequest("subject", listOf(a)), original, catalog)
        assertTrue(forA.executable, forA.issues.toString())
        assertEquals(Amount(50, 0), forA.cost)
        val forB = plan(AcquisitionRequest("subject", listOf(b)), original, catalog)
        assertTrue(forB.executable, forB.issues.toString())
        assertEquals(listOf(access, contacts), forB.changes.map { it.definition })
        assertEquals(mapOf("organizationId" to "organization:b", "organizationName" to "Организация Б"), forB.changes.first().parameters)
        assertEquals(Amount(50, 1), forB.cost)
        val both = AcquisitionRequest("subject", listOf(a, b))
        val combined = plan(both, original, catalog)
        assertTrue(combined.executable, combined.issues.toString())
        assertEquals(Amount(100, 1), combined.cost)
        val backing = store(original)
        buy(service(backing), both, "two-organizations")
        val after = saved(backing)
        assertEquals(accessA, after.instances.single { it.instanceId == accessA.instanceId })
        assertEquals(setOf("organization:a", "organization:b"), after.instances.filter { it.definition == access }.map { it.parameters.getValue("organizationId") }.toSet())
        assertEquals(Amount(900, 9), balance(after.ledger))
        assertEquals(encodeEnvelope(after), encodeEnvelope(saved(store(after))))
        assertIs<ApplyResult.Applied>(AcquisitionUndo(backing).undo("subject", "undo-organizations", after.revision))
        assertEquals(acquisitionProjectionCanonical(original), acquisitionProjectionCanonical(saved(backing)))
    }

    @Test fun the_only_selected_knowledge_is_raised_without_an_unrelated_family_choice() {
        val selected = owned("only-knowledge", ref("knowledge_template"), 3,
            mapOf("specialization" to "local-history", "specializationName" to "Местная история"))
        val request = AcquisitionRequest("subject", listOf(AcquisitionTarget(deep, "deep-only", 1,
            mapOf("knowledgeInstanceId" to selected.instanceId))))
        val preview = plan(request, initial(listOf(selected)), catalog)
        assertTrue(preview.executable, preview.issues.toString())
        assertTrue(preview.questions.isEmpty())
        assertEquals(listOf(selected.instanceId, "deep-only"), preview.changes.map { it.instanceId })
        assertEquals(Amount(90, 0), preview.cost)
    }

    @Test fun recursive_owned_proofs_use_their_own_organization_and_bindings_are_captured() {
        val accessA = owned("access-a", access, 1, mapOf("organizationId" to "organization:a", "organizationName" to "Организация А"))
        val contactsB = owned("contacts-b", contacts, 1, mapOf("organizationId" to "organization:b", "organizationName" to "Организация Б"))
        val state = initial(listOf(accessA, contactsB))
        val context = acquisitionContext(state, catalog)
        val source = catalog.definitions.getValue(contacts).sourceRef
        val ownB = RequirementNode.OwnedRank("specific-contacts-b", source, contacts.packId, contacts.entryId,
            ComparisonOperator.GE, 1, contactsB.instanceId)
        assertEquals(RequirementStatus.Unsatisfied, evaluate(ownB, context, mapOf("organizationId" to "organization:a")).status)
        val mutableBindings = mutableMapOf("organizationId" to "organizationId")
        val bound = RequirementNode.BranchAccess("same-organization", source, access.packId, access.entryId,
            parameterBindings = mutableBindings)
        mutableBindings["organizationId"] = "unrelated-key"
        assertEquals(RequirementStatus.Satisfied, evaluate(bound, context, mapOf("organizationId" to "organization:a")).status)
        assertEquals(RequirementStatus.Unsatisfied, evaluate(bound, context, mapOf("organizationId" to "organization:b")).status)
        assertEquals(RequirementStatus.Satisfied, evaluate(bound, context, mapOf("organizationId" to "organization:a")).status)
    }

    @Test fun explicit_matching_contacts_access_reuses_its_id_in_both_bundle_orders_and_when_already_owned() {
        val parameters = mapOf("organizationId" to "organization:b", "organizationName" to "Организация Б")
        val contact = AcquisitionTarget(contacts, "contacts-b", 1, parameters)
        val explicitAccess = AcquisitionTarget(access, "explicit-access-b", 1, parameters)
        for (targets in listOf(listOf(contact, explicitAccess), listOf(explicitAccess, contact))) {
            val result = plan(AcquisitionRequest("subject", targets), initial(emptyList()), catalog)
            assertTrue(result.executable, result.issues.toString())
            assertEquals(Amount(50, 1), result.cost)
            assertEquals(listOf("explicit-access-b", "contacts-b"), result.changes.map { it.instanceId })
            assertEquals(2, result.changes.size)
            val prior = owned("explicit-access-b", access, 1, parameters)
            val already = plan(AcquisitionRequest("subject", targets), initial(listOf(prior)), catalog)
            assertTrue(already.executable, already.issues.toString())
            assertEquals(Amount(50, 0), already.cost)
            assertEquals(listOf("contacts-b"), already.changes.map { it.instanceId })
            assertEquals(prior, already.projectedContext.instances.single { it.definition == access })
        }
    }
}
