package com.furybook.dubl.acquisition

import com.furybook.content.FcpContentPack
import com.furybook.content.FcpTextSource
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
        assertEquals(45L, assertIs<PriceRule.Fixed>(eagle.priceRule).xp)
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
            else -> emptyList()
        }
        val knowledge = assertIs<RequirementNode.SkillFamily>(entry("feat_cd965f927a426cc2").requirements)
        assertEquals("knowledge", knowledge.familyId)
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

    @Test fun undeclared_payload_is_not_loaded_and_bad_payload_is_diagnostic() {
        val base = """{"format":"fury.content-pack","formatVersion":1,"id":"dubl-3.69","name":"Core","version":"3.69","ruleset":{"id":"dubl","version":"3.69","engineApi":1},"modules":[],"entries":[]} """
        val pack = FcpContentPack.load("", FcpTextSource { if (it == "manifest.json") base else "untrusted" })
        val result = loadAcquisitionCatalog(listOf(pack), false)
        assertTrue(result.catalog.definitions.isEmpty())
        assertTrue(result.diagnostics.any { it.kind == "missing-acquisition-payload" })
    }
}
