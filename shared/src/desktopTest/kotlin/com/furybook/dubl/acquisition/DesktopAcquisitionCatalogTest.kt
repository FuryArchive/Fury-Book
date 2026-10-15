package com.furybook.dubl.acquisition

import com.furybook.content.FcpContentPack
import com.furybook.content.FcpTextSource
import com.furybook.dubl.model.DublCharacter
import kotlin.test.*

class DesktopAcquisitionCatalogTest {
    private val source = FcpTextSource { path -> javaClass.classLoader.getResource(path)?.readText() }
    private fun packs() = listOf("dubl-3.69", "dubl-chi-3.69").map { FcpContentPack.load("fcp/$it", source) }

    @Test fun loads_typed_source_corrections_from_manifest() {
        val result = loadAcquisitionCatalog(packs(), false)
        val block = result.catalog.definitions.getValue(DefinitionRef("dubl-3.69", "feat_fcdd74622d21435d"))
        val all = assertIs<RequirementNode.AllOf>(block.requirements)
        assertEquals(listOf(2, 2), all.children.map { assertIs<RequirementNode.AnyOf>(it).children.size })
        assertEquals(11012, all.sourceRef.paragraph)
        val eagle = result.catalog.definitions.getValue(DefinitionRef("dubl-3.69", "feat_9012c539238a1e6f"))
        assertEquals(45L, assertIs<PriceRule.PerRank>(eagle.priceRule).xp)
        assertTrue(result.catalog.catalogRevision.isNotBlank())
        assertTrue(result.diagnostics.any { it.kind == "rank-out-of-range" && it.definition?.entryId == "feat_1beadb2cd06442f9" })
    }

    @Test fun optional_chi_preserves_existing_claims_and_pack_order() {
        val off = loadAcquisitionCatalog(packs(), false).catalog
        val on = loadAcquisitionCatalog(packs().reversed(), true).catalog
        val claimed = "martial_technique_08d81ce45b880c24"
        assertFalse(off.definitions.keys.any { it.entryId == claimed })
        assertTrue(on.definitions.keys.any { it.entryId == claimed })
        assertFalse(off.definitions.keys.any { it.packId == "dubl-chi-3.69" })
        assertEquals("dubl-3.69", on.definitions.keys.first().packId)
    }

    @Test fun loads_explicit_family_specialization_and_distinct_count_source_mappings() {
        val catalog = loadAcquisitionCatalog(packs(), false).catalog
        fun entry(id: String) = catalog.definitions.getValue(DefinitionRef("dubl-3.69", id))
        fun nodes(n: RequirementNode): List<RequirementNode> = listOf(n) + when (n) {
            is RequirementNode.AllOf -> n.children.flatMap(::nodes)
            is RequirementNode.AnyOf -> n.children.flatMap(::nodes)
            is RequirementNode.DistinctCount -> nodes(n.predicate)
            is RequirementNode.SelectedInstance -> nodes(n.predicate)
            else -> emptyList()
        }
        val selectedKnowledge = nodes(entry("feat_cd965f927a426cc2").requirements)
            .filterIsInstance<RequirementNode.SelectedInstance>().single()
        assertEquals("knowledgeInstanceId", selectedKnowledge.parameterKey)
        val knowledge = assertIs<RequirementNode.SkillFamily>(selectedKnowledge.predicate)
        assertEquals("knowledge", knowledge.familyId)
        assertEquals(ComparisonOperator.GE, knowledge.operator)
        assertEquals(4, knowledge.operand)
        assertTrue("knowledge" in entry("knowledge_template").familyIds)
        assertEquals(278, entry("knowledge_template").classificationSources.getValue("family:knowledge").paragraph)
        val specialization = nodes(entry("feat_a834e752f2e3132b").requirements).filterIsInstance<RequirementNode.Specialization>().single()
        assertEquals(ParameterMatch("weaponTypeId", "melee"), specialization.qualifier)
        for ((id, count) in listOf("feat_8b6f3261d7c5c8ea" to 2, "feat_127f2d478d76e66d" to 3)) {
            val distinct = nodes(entry(id).requirements).filterIsInstance<RequirementNode.DistinctCount>().single()
            assertEquals(count, distinct.operand)
            assertEquals(DistinctScope.Definition, distinct.uniqueness)
            assertEquals(11, assertIs<RequirementNode.AnyOf>(distinct.predicate).children.size)
        }
    }

    @Test fun actual_loaded_perfect_roots_conflict_despite_invalid_rival_prerequisites() {
        val loaded = loadAcquisitionCatalog(packs(), false)
        val catalog = loaded.catalog
        val roots = setOf("feat_1bacaebcd614c5c5", "feat_7c06b44361f99e86", "feat_07a5933de10322e2")
        fun ref(id: String) = DefinitionRef("dubl-3.69", id)
        fun exclusive(node: RequirementNode): List<RequirementNode.Exclusive> = when (node) {
            is RequirementNode.Exclusive -> listOf(node)
            is RequirementNode.AllOf -> node.children.flatMap(::exclusive)
            is RequirementNode.AnyOf -> node.children.flatMap(::exclusive)
            else -> emptyList()
        }
        assertEquals(roots, catalog.definitions.values.filter { "perfect-root" in it.categoryIds }.map { it.ref.entryId }.toSet())
        for (target in roots) {
            val definition = catalog.definitions.getValue(ref(target))
            val exclusion = exclusive(definition.requirements).single()
            assertEquals(13618, exclusion.sourceRef.paragraph)
            assertEquals("t.hwzq55hph1xp", exclusion.sourceRef.sheet)
            assertTrue(exclusion.sourceRef.originalText.contains("не более одной"))
            for (rival in roots - target) {
                val old = AcquisitionInstance("old/$rival", ref(rival), 1, emptyMap(), null, AcquisitionOrigin.New)
                val context = AcquisitionContext(DublCharacter("actual"), listOf(old), emptySet(),
                    RequirementNode.CreationPhase.Phase.Finalized, emptyList(), catalog)
                // Rival's actual source tree has unresolved/unsatisfied prerequisites; ownership still excludes target.
                assertNotEquals(RequirementStatus.Satisfied, evaluate(catalog.definitions.getValue(ref(rival)).requirements, context).status)
                val proof = evaluate(definition.requirements, context)
                assertEquals(RequirementStatus.Conflict, proof.status)
                assertTrue(proof.witnesses.any { it is RequirementWitness.OwnedInstance && it.entryId == rival })
                assertEquals(listOf(old), context.instances)
                // The unrelated source-confirmed skill's eligibility is independent of the legacy deviation.
                assertEquals(RequirementStatus.Satisfied, evaluate(catalog.definitions.getValue(ref("athletics")).requirements, context).status)
            }
            val own = AcquisitionInstance("old/$target", ref(target), 1, emptyMap(), null, AcquisitionOrigin.New)
            val context = AcquisitionContext(DublCharacter("actual"), listOf(own), emptySet(),
                RequirementNode.CreationPhase.Phase.Finalized, emptyList(), catalog)
            assertEquals(RequirementStatus.Satisfied, evaluate(exclusion, context).status)
        }
        assertFalse(loaded.diagnostics.any { it.kind == "dependency-cycle" && it.definition?.entryId in roots })
    }

    @Test fun actual_loaded_knowledge_four_condition_uses_stable_family() {
        val catalog = loadAcquisitionCatalog(packs(), false).catalog
        fun ref(id: String) = DefinitionRef("dubl-3.69", id)
        val condition = catalog.definitions.getValue(ref("feat_cd965f927a426cc2")).requirements
        assertEquals(10530, condition.sourceRef.paragraph)
        val knowledge = AcquisitionInstance("knowledge/academic", ref("knowledge_academic"), 4, emptyMap(), null, AcquisitionOrigin.New)
        val context = AcquisitionContext(DublCharacter("actual"), listOf(knowledge), emptySet(),
            RequirementNode.CreationPhase.Phase.Creating, emptyList(), catalog)
        assertEquals(RequirementStatus.ChoiceRequired, evaluate(condition, context).status)
        assertEquals(RequirementStatus.Satisfied,
            evaluate(condition, context, mapOf("knowledgeInstanceId" to knowledge.instanceId)).status)
        assertEquals(RequirementStatus.Unsatisfied,
            evaluate(condition, context, mapOf("knowledgeInstanceId" to "knowledge/missing")).status)
    }

    @Test fun actual_any_performance_prerequisites_accept_vocal_not_unrelated_high_rank() {
        val catalog = loadAcquisitionCatalog(packs(), false).catalog
        fun ref(id: String) = DefinitionRef("dubl-3.69", id)
        val talented = catalog.definitions.getValue(ref("feat_f33c232811919ee5")).requirements
        val versatile = catalog.definitions.getValue(ref("feat_0bf4bbf1c46717a4")).requirements
        val any = assertIs<RequirementNode.AnyOf>(talented)
        assertEquals(setOf("performance_vocal", "performance_template"),
            any.children.map { assertIs<RequirementNode.SkillFamily>(it).familyId }.toSet())
        assertEquals(10542, talented.sourceRef.paragraph)
        fun instance(id: String, rank: Int) = AcquisitionInstance("owned/$id", ref(id), rank, emptyMap(), null, AcquisitionOrigin.New)
        fun context(vararg instances: AcquisitionInstance) = AcquisitionContext(DublCharacter("performance"),
            instances.toList(), emptySet(), RequirementNode.CreationPhase.Phase.Creating, emptyList(), catalog)
        assertEquals(RequirementStatus.Satisfied, evaluate(talented, context(instance("performance_vocal", 4))).status)
        assertNotEquals(RequirementStatus.Satisfied, evaluate(talented, context(instance("performance_vocal", 3))).status)
        assertNotEquals(RequirementStatus.Satisfied, evaluate(talented, context(instance("athletics", 10))).status)
        assertEquals(RequirementStatus.Satisfied, evaluate(versatile,
            context(instance("performance_vocal", 5), instance("feat_f33c232811919ee5", 1))).status)
        assertNotEquals(RequirementStatus.Satisfied, evaluate(versatile,
            context(instance("performance_vocal", 4), instance("feat_f33c232811919ee5", 1))).status)
        assertNotEquals(RequirementStatus.Satisfied, evaluate(versatile, context(instance("performance_vocal", 5))).status)
    }

    @Test fun undeclared_payload_is_not_loaded_and_bad_payload_is_diagnostic() {
        val base = """{"format":"fury.content-pack","formatVersion":1,"id":"dubl-3.69","name":"Core","version":"3.69","ruleset":{"id":"dubl","version":"3.69","engineApi":1},"modules":[],"entries":[]} """
        val pack = FcpContentPack.load("", FcpTextSource { if (it == "manifest.json") base else "untrusted" })
        val result = loadAcquisitionCatalog(listOf(pack), false)
        assertTrue(result.catalog.definitions.isEmpty())
        assertTrue(result.diagnostics.any { it.kind == "missing-acquisition-payload" })
    }
}
