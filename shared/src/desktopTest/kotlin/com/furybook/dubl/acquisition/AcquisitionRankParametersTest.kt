package com.furybook.dubl.acquisition

import com.furybook.content.FcpContentPack
import com.furybook.content.FcpTextSource
import com.furybook.dubl.model.*
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class AcquisitionRankParametersTest {
    private val catalog by lazy { loadAcquisitionCatalog(listOf(FcpContentPack.load("fcp/dubl-3.69",
        FcpTextSource { javaClass.classLoader.getResource(it)?.readText() })), false).catalog }

    @Test fun actual_scout_and_hunter_activate_one_immutable_selection_per_owned_rank() {
        for ((id, prefix) in listOf("feat_e15a39ad2d1a7aaa" to "terrain", "feat_dee6d91d0a987242" to "creatureType")) {
            val definition = catalog.definitions.getValue(DefinitionRef("dubl-3.69", id))
            val first = definition.parameterDefinitions.first().let { (it.domain as ParameterDomain.StableIds).valueIds.first() }
            val parameters = mapOf("${prefix}1" to first)
            fun validate(rank: Int, selected: Map<String, String>, owned: List<AcquisitionInstance> = emptyList()) =
                validateAcquisitionTarget(definition.ref, checkNotNull(definition.maxRank), definition.parameterDefinitions,
                    definition.repeatPolicy, "owned", rank, selected, null, owned)
            assertEquals(IdentityValidation.NewInstance, validate(1, parameters), id)
            val old = AcquisitionInstance("owned", definition.ref, 1, parameters, null, AcquisitionOrigin.New)
            // Both current source cards permit the same choice at another rank; do not invent uniqueness.
            val added = parameters + ("${prefix}2" to first)
            assertIs<IdentityValidation.ExistingInstance>(validate(2, added, listOf(old)), id)
            assertEquals(RequirementStatus.ChoiceRequired, assertIs<IdentityValidation.Blocked>(validate(2, parameters, listOf(old))).status)
            assertIs<IdentityValidation.Blocked>(validate(1, added, listOf(old)))
            assertIs<IdentityValidation.Blocked>(validate(1, added))
            val other = (definition.parameterDefinitions.first().domain as ParameterDomain.StableIds).valueIds.last()
            assertEquals(RequirementStatus.Conflict, assertIs<IdentityValidation.Blocked>(validate(2,
                added + ("${prefix}1" to other), listOf(old))).status)
            assertEquals(parameters, old.parameters)
        }
    }

    @Test fun actual_scout_rank_raise_pays_only_new_rank_and_undo_restores_previous_parameter_prefix() = runBlocking {
        fun ref(id: String) = DefinitionRef("dubl-3.69", id)
        fun owned(id: String, rank: Int, params: Map<String, String> = emptyMap()) =
            AcquisitionInstance("owned/$id", ref(id), rank, params, null, AcquisitionOrigin.New)
        val scout = ref("feat_e15a39ad2d1a7aaa")
        val holdings = listOf(owned("survival", 5), owned("stealth", 3), owned("feat_d69c0d77fdf1d016", 2),
            owned("feat_c69b491a98862c61", 1), owned("feat_fcdea7663f0b9383", 1), owned("feat_098eff5ce64b5b87", 1),
            owned("knowledge_template", 5, mapOf("specialization" to "nature", "specializationName" to "Природа")),
            owned("access_bd7119a29fbad30f", 1))
        val initial = StoredCharacter(DublCharacter("scout"), CharacterSheetExtras(), holdings,
            Ledger(Opening("opening", "current-native-scout", Amount(1000, 3), RequirementNode.CreationPhase.Phase.Creating,
                OpeningProvenance.NewCharacter), emptyList()), emptyList(), emptyList(), SyncMetadata(0), 0, 0,
            RequirementNode.CreationPhase.Phase.Creating)
        val store = InMemoryAcquisitionStore(mapOf("scout" to encodeEnvelope(initial)))
        val service = AcquisitionService(store, { catalog }, { "id" })
        suspend fun purchase(operation: String, rank: Int, params: Map<String, String>): Receipt {
            val request = AcquisitionRequest("scout", listOf(AcquisitionTarget(scout, "scout-instance", rank, params)))
            val preview = service.preview(request)
            assertTrue(preview.executable, preview.issues.toString())
            assertEquals(listOf(scout), preview.changes.map { it.definition }, "Owned shared predecessors are never charged again")
            assertEquals(Amount(50, 0), preview.cost)
            return assertIs<ApplyResult.Applied>(service.apply(ConfirmAcquisition(operation, "scout", request,
                preview.baseRevision, preview.previewToken))).receipt
        }
        purchase("first", 1, mapOf("terrain1" to "forest"))
        val beforeRaise = assertIs<LoadResult.Loaded>(store.load("scout")).state
        val missing = AcquisitionRequest("scout", listOf(AcquisitionTarget(scout, targetRank = 2)))
        val prompt = selectionPrompts(missing, beforeRaise, catalog).single()
        assertEquals("scout-instance", prompt.selectedInstanceId)
        assertEquals(mapOf("terrain1" to "forest"), prompt.selectedParameters)
        assertTrue(prompt.canEditSelectedParameters)
        assertEquals(listOf("terrain1", "terrain2"), prompt.parameters.map { it.key })
        assertEquals(listOf(true, false), prompt.parameters.map { it.readOnly })
        val second=purchase("second", 2, mapOf("terrain1" to "forest", "terrain2" to "desert"))
        assertEquals("Скаут (Лес, Пустыня)",AcquisitionPresentation.receipt(second,catalog,false).rows.single().label)
        val after = assertIs<LoadResult.Loaded>(decodeEnvelope(encodeEnvelope(assertIs<LoadResult.Loaded>(store.load("scout")).state))).state
        assertEquals(Amount(900, 3), balance(after.ledger))
        assertEquals(2, after.instances.single { it.definition == scout }.rank)
        val undo = AcquisitionUndo(store)
        assertIs<UndoEligibility.Available>(undo.eligible("scout"))
        assertIs<ApplyResult.Applied>(undo.undo("scout", "undo-second", after.revision))
        val restored = assertIs<LoadResult.Loaded>(store.load("scout")).state
        assertEquals(1, restored.instances.single { it.definition == scout }.rank)
        assertEquals(mapOf("terrain1" to "forest"), restored.instances.single { it.definition == scout }.parameters)
        assertEquals(Amount(950, 3), balance(restored.ledger))
        assertEquals(holdings, restored.instances.filterNot { it.definition == scout })
    }
}
