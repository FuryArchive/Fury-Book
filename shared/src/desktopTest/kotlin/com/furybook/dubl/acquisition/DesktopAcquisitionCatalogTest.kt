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

    @Test fun undeclared_payload_is_not_loaded_and_bad_payload_is_diagnostic() {
        val base = """{"format":"fury.content-pack","formatVersion":1,"id":"dubl-3.69","name":"Core","version":"3.69","ruleset":{"id":"dubl","version":"3.69","engineApi":1},"modules":[],"entries":[]} """
        val pack = FcpContentPack.load("", FcpTextSource { if (it == "manifest.json") base else "untrusted" })
        val result = loadAcquisitionCatalog(listOf(pack), false)
        assertTrue(result.catalog.definitions.isEmpty())
        assertTrue(result.diagnostics.any { it.kind == "missing-acquisition-payload" })
    }
}
