package com.furybook.dubl.acquisition

import com.furybook.content.FcpContentPack
import com.furybook.content.FcpTextSource
import com.furybook.dubl.application.DublApplication
import com.furybook.dubl.data.CharacterExtrasStore
import com.furybook.dubl.data.CharacterStore
import com.furybook.dubl.model.*
import kotlinx.coroutines.runBlocking
import kotlin.test.*

/** Real skill definitions, real catalog prices, and the ordinary persisted purchase path. */
class AcquisitionSpecializedSkillsTest {
    private val catalog by lazy {
        loadAcquisitionCatalog(listOf(FcpContentPack.load("fcp/dubl-3.69",
            FcpTextSource { javaClass.classLoader.getResource(it)?.readText() })), false).catalog
    }
    private fun ref(id: String) = DefinitionRef("dubl-3.69", id)
    private fun state(creating: Boolean = true): StoredCharacter {
        val phase = if (creating) RequirementNode.CreationPhase.Phase.Creating else RequirementNode.CreationPhase.Phase.Finalized
        return StoredCharacter(DublCharacter("specialist", creationComplete = !creating), CharacterSheetExtras(), emptyList(),
            Ledger(Opening("opening", "current-corebook", Amount(1000, 10), phase, OpeningProvenance.NewCharacter), emptyList()),
            emptyList(), emptyList(), SyncMetadata(0), 0, 0, phase)
    }
    private fun target(template: String, instance: String, specialty: String, name: String, rank: Int = 1) =
        AcquisitionTarget(ref(template), instance, rank, mapOf("specialization" to specialty, "specializationName" to name))

    @Test fun source_confirmed_specialized_skills_have_explicit_independent_identity() {
        for ((template, name) in listOf("knowledge_template" to "Военное дело (СССР)",
            "performance_template" to "Танцы", "profession_template" to "Повар")) {
            val definition = catalog.definitions.getValue(ref(template))
            assertEquals(setOf("specialization"), assertIs<RepeatPolicy.DistinctParameter>(definition.repeatPolicy).parameterKeys)
            assertTrue(definition.parameterDefinitions.all { it.domain !is ParameterDomain.Unresolved })
            val specialty = when (template) { "performance_template" -> "dance"; "profession_template" -> "cook"; else -> "custom/$template" }
            val request = AcquisitionRequest("specialist", listOf(target(template, "new/$template", specialty, name)))
            val preview = plan(request, state(), catalog)
            assertTrue(preview.executable, "$template: ${preview.issues}")
        }
    }

    @Test fun current_general_craft_is_one_ordinary_skill_separate_from_professions() {
        val definition = catalog.definitions.getValue(ref("craft_template"))
        assertEquals(RepeatPolicy.Single, definition.repeatPolicy)
        assertTrue(definition.parameterDefinitions.isEmpty())
        assertFalse(AcquisitionSkillSpecializations.supported(definition))
        val request = AcquisitionRequest("specialist", listOf(AcquisitionTarget(definition.ref, targetRank = 1)))
        val preview = plan(request, state(), catalog)
        assertTrue(preview.executable, preview.issues.toString())
        assertEquals(Amount(10, 0), preview.cost)
        assertEquals(definition.ref, preview.changes.single().definition)
    }

    @Test fun named_academic_and_vocal_skills_cannot_be_bought_again_under_custom_template_aliases() {
        for ((template, name) in listOf("knowledge_template" to "АКАДЕМИЧЕСКОЕ", "performance_template" to "вокал")) {
            val definition = catalog.definitions.getValue(ref(template))
            val duplicate = AcquisitionSkillSpecializations.newTarget(definition, "custom/$template", name, 4)
            val preview = plan(AcquisitionRequest("specialist", listOf(duplicate)), state(), catalog)
            assertFalse(preview.executable, "$template must use the existing named skill purchase path")
            assertTrue(preview.changes.isEmpty())
            assertTrue(preview.issues.any { it.status == RequirementStatus.Conflict })
        }
        val source=catalog.definitions.getValue(ref("knowledge_template")).sourceRef
        val count=RequirementNode.DistinctCount("knowledge/two",source,
            RequirementNode.SkillFamily("knowledge/one",source,"dubl-3.69","knowledge",ComparisonOperator.GE,4),
            DistinctScope.Instance,ComparisonOperator.GE,2)
        val owned=listOf(AcquisitionInstance("academic",ref("knowledge_academic"),4,emptyMap(),null,AcquisitionOrigin.New),
            AcquisitionInstance("old-custom-alias",ref("knowledge_template"),4,
                mapOf("specialization" to "custom-academic", "specializationName" to "АКАДЕМИЧЕСКОЕ"),null,AcquisitionOrigin.New))
        assertEquals(RequirementStatus.Unsatisfied,evaluate(count,acquisitionContext(state().copy(instances=owned),catalog)).status)
    }

    @Test fun profession_specializations_keep_independent_ranks_and_one_price_across_review_receipt_and_reopen() = runBlocking {
        for (creating in listOf(true, false)) {
            val initial = state(creating)
            val store = InMemoryAcquisitionStore(mapOf("specialist" to encodeEnvelope(initial)))
            val service = AcquisitionService(store, { catalog }, { "generated" })
            val cook = target("profession_template", "cook-instance", "cook", "Повар", 2)
            val carpenter = target("profession_template", "carpenter-instance", "carpenter-specialty", "Плотник", 1)
            // The same chosen predecessor/target is reused once within the combined purchase.
            val request = AcquisitionRequest("specialist", listOf(cook, carpenter, cook))
            val preview = service.preview(request)
            assertTrue(preview.executable, preview.issues.toString())
            assertEquals(2, preview.changes.size)
            // Current skill price table: 0→1=10, 1→2=20, with no special-point charge.
            assertEquals(Amount(40, 0), preview.cost)
            val scope = AcquisitionScope("store", "specialist", 0, 0, 0)
            val card = AcquisitionPresentation.present(scope, AcquisitionQuoteKey("card", 0), preview, initial, catalog, AcquisitionCapabilities(true, false, true))
            assertEquals(setOf("Профессия (Повар)", "Профессия (Плотник)"), card.rows.map { it.label }.toSet())
            val command = ConfirmAcquisition("purchase", "specialist", request, preview.baseRevision, preview.previewToken)
            val receipt = assertIs<ApplyResult.Applied>(service.apply(command)).receipt
            assertEquals(assertIs<AcquisitionDisplayedPrice.Known>(card.price).amount, receipt.result.cost)
            assertEquals(card.rows.map { it.label }, AcquisitionPresentation.receipt(receipt, catalog, false).rows.map { it.label })
            val saved = assertIs<LoadResult.Loaded>(decodeEnvelope(encodeEnvelope(assertIs<LoadResult.Loaded>(store.load("specialist")).state))).state
            assertEquals(setOf("Профессия (Повар)", "Профессия (Плотник)"), saved.character.skills.values.map { it.name }.toSet())
            assertEquals(mapOf("cook-instance" to 2, "carpenter-instance" to 1), saved.instances.associate { it.instanceId to it.rank })
            assertEquals(card.rows.map { it.label }.toSet(), instanceViews(saved, catalog).map { it.label }.toSet())
            assertIs<ApplyResult.AlreadyApplied>(service.apply(command))
            val raise = AcquisitionRequest("specialist", listOf(cook.copy(targetRank = 3)))
            val raisedPreview = service.preview(raise)
            assertTrue(raisedPreview.executable, raisedPreview.issues.toString())
            assertEquals(Amount(30, 0), raisedPreview.cost)
            assertEquals(2, raisedPreview.changes.single().fromRank)
            assertEquals(3, raisedPreview.changes.single().toRank)
            val raised = assertIs<ApplyResult.Applied>(service.apply(ConfirmAcquisition("raise", "specialist", raise,
                raisedPreview.baseRevision, raisedPreview.previewToken))).receipt
            assertEquals(raisedPreview.cost, raised.result.cost)
            val after = assertIs<LoadResult.Loaded>(store.load("specialist")).state
            assertEquals(mapOf("cook-instance" to 3, "carpenter-instance" to 1), after.instances.associate { it.instanceId to it.rank })
            assertEquals(initial.ledger.opening.balance.ap, balance(after.ledger).ap)
            val duplicate = plan(AcquisitionRequest("specialist", listOf(cook.copy(instanceId = "duplicate", targetRank = 1))), after, catalog)
            assertFalse(duplicate.executable)
            assertTrue(duplicate.issues.any { it.status == RequirementStatus.Conflict })
            val renamed = plan(AcquisitionRequest("specialist", listOf(cook.copy(targetRank = 4,
                parameters = cook.parameters + ("specializationName" to "Иное имя")))), after, catalog)
            assertFalse(renamed.executable)
            assertTrue(renamed.issues.any { it.status == RequirementStatus.Conflict })
        }
    }

    @Test fun actual_skill_choice_captures_name_and_identity_once_without_exposing_id_entry() = runBlocking {
        val initial = state()
        val store = InMemoryAcquisitionStore(mapOf("specialist" to encodeEnvelope(initial)))
        var ids = 0
        val idFactory = { "captured-${++ids}" }
        val service = AcquisitionService(store, { catalog }, idFactory)
        val app = DublApplication(object : CharacterStore {
            override fun load() = AppSnapshot(listOf(initial.character), "specialist")
            override fun save(snapshot: AppSnapshot) = error("Legacy write")
        }, object : CharacterExtrasStore {
            override fun load(characterId: String) = CharacterSheetExtras()
            override fun save(characterId: String, extras: CharacterSheetExtras) = error("Legacy write")
        }, idFactory, acquisitionService = service)
        val scope = AcquisitionScope("store", "specialist", 0, 0, 0)
        val control = AcquisitionController(ApplicationAcquisitionCommandPort(app, store, { catalog },
            { AcquisitionCapabilities(true, false, true) }, { true }), { scope }, idFactory)
        val choose = control.preview(AcquisitionIntent.skill(AcquisitionTarget(ref("performance_template"), targetRank = 1)))
        assertEquals(AcquisitionUiPhase.ChoiceRequired, choose.phase)
        val prompt = choose.selections.single()
        assertTrue(prompt.specializedSkill)
        assertEquals("Специализация", prompt.parameters.single().label)
        assertEquals("Танцы", prompt.parameters.single().options.single { it.id == "dance" }.label)
        assertEquals(0, ids)
        val ready = control.selectNewSpecialization(choose.key, 0, "", choiceId = "dance")
        assertEquals(AcquisitionUiPhase.Ready, ready.phase)
        assertEquals(1, ids)
        val selected = checkNotNull(ready.request).targets.single()
        assertEquals("dance", selected.parameters["specialization"])
        assertEquals("Танцы", selected.parameters["specializationName"])
        assertEquals("Исполнение (Танцы)", ready.rows.single().label)
        repeat(2) { assertEquals(Amount(10, 0), assertIs<AcquisitionDisplayedPrice.Known>(control.quoteCard(checkNotNull(ready.request)).price).amount) }
        assertEquals(1, ids)
        val committed = control.confirm(ready.key)
        assertEquals(AcquisitionUiPhase.Committed, committed.phase)
        assertEquals(2, ids)
        assertEquals(Amount(10, 0), committed.receipt?.cost)
        val view = control.readCanonicalView()
        assertEquals("Исполнение (Танцы)", view.instances.single().label)
        assertEquals("Исполнение (Танцы)", view.history.single().rows.single().label)
        // Entering the same visible name does not infer the source-backed dance identity.
        val counterfeit = AcquisitionSkillSpecializations.newTarget(catalog.definitions.getValue(ref("performance_template")), "custom-dance", "Танцы")
        assertNotEquals("dance", counterfeit.parameters["specialization"])
        assertFalse(plan(AcquisitionRequest("specialist", listOf(counterfeit)), initial, catalog).executable)
        val rankTwo = control.preview(AcquisitionRequest("specialist", listOf(selected.copy(targetRank = 2))))
        assertEquals(AcquisitionUiPhase.Committed, control.confirm(rankTwo.key).phase)
        val existingChoice = control.preview(AcquisitionIntent.skill(AcquisitionTarget(ref("performance_template"), targetRank = 1)))
        val existing = existingChoice.selections.single().instances.single()
        assertEquals(2, existing.rank)
        val rankThree = control.selectTarget(existingChoice.key, 0, existing.instanceId, parameters = existing.parameters,
            optionId = existing.optionId, targetRank = existing.rank + 1)
        assertEquals(AcquisitionUiPhase.Ready, rankThree.phase)
        assertEquals(Amount(30, 0), assertIs<AcquisitionDisplayedPrice.Known>(rankThree.price).amount)
        assertEquals(2, rankThree.rows.single().fromRank)
        assertEquals(3, rankThree.rows.single().toRank)
        assertEquals(2, control.readCanonicalView().instances.single().rank, "Selecting a rank opens a quote only")
    }
}
