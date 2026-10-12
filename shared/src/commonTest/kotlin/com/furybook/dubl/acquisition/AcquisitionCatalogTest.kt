package com.furybook.dubl.acquisition

import com.furybook.content.FcpContentPack
import com.furybook.content.FcpTextSource
import kotlin.test.*

class AcquisitionCatalogTest {
    private val src = """{"documentId":"book","documentVersion":"r1","paragraph":42,"sheet":"tab","originalText":"Source wording"}"""
    private fun unresolved(id: String = "q") = """{"type":"Unresolved","nodeId":"$id","sourceRef":$src,"reason":"source question"}"""
    private fun owned(id: String, target: String, rank: Int = 1) = """{"type":"OwnedRank","nodeId":"$id","sourceRef":$src,"packId":"dubl-3.69","entryId":"$target","operator":"GE","operand":$rank}"""
    private fun fixed(xp: String = "\"45\"") = """{"type":"Fixed","sourceRef":$src,"xp":$xp,"ap":"0"}"""
    private fun definition(id: String, requirement: String = unresolved(), price: String = fixed(), status: String = "NeedsResolution", max: String = "2", kind: String = "DEVELOPMENT") = """{
      "ref":{"packId":"dubl-3.69","entryId":"$id"},"kind":"$kind","name":"$id","maxRank":$max,
      "repeatPolicy":{"type":"Single"},"sourceStatus":"$status","sourceRef":$src,
      "parameterDefinitions":[],"abilityOptionIds":[],"requirementsText":"Source wording",
      "requirements":$requirement,"priceRule":$price} """
    private fun load(vararg defs: String): CatalogLoadResult {
        val manifest = """{"format":"fury.content-pack","formatVersion":1,"id":"dubl-3.69","name":"Core","version":"3.69","ruleset":{"id":"dubl","version":"3.69","engineApi":1},"modules":[],"entries":[{"kind":"dubl.acquisition","path":"content/acquisition.json","modules":[],"order":1}]}"""
        val payload = """{"schemaVersion":1,"catalogRevision":"c1","resolutionRevision":"r1","definitions":[${defs.joinToString()}],"diagnostics":[]}"""
        val files = mapOf("manifest.json" to manifest, "content/acquisition.json" to payload)
        return loadAcquisitionCatalog(listOf(FcpContentPack.load("", FcpTextSource(files::get))), false)
    }
    private fun resultDefinition(result: CatalogLoadResult, id: String = "a") = result.catalog.definitions.getValue(DefinitionRef("dubl-3.69", id))
    private fun allNodes(n: RequirementNode): List<RequirementNode> = listOf(n) + when(n) {
        is RequirementNode.AllOf -> n.children.flatMap(::allNodes)
        is RequirementNode.AnyOf -> n.children.flatMap(::allNodes)
        else -> emptyList()
    }

    @Test fun unavailable_source_cannot_be_bypassed_by_free_price_or_satisfied_tree() {
        val empty = """{"type":"AllOf","nodeId":"empty","sourceRef":$src,"children":[],"noRequirementsStatement":"Explicit none"}"""
        val d = resultDefinition(load(definition("a", empty, fixed("\"0\""), "Unavailable")))
        assertIs<PriceRule.Unknown>(d.priceRule)
        assertTrue(allNodes(d.requirements).any { it is RequirementNode.Unresolved && it.reason == "source-unverified" })
    }

    @Test fun unsafe_money_numbers_overflow_and_missing_fields_are_not_zero() {
        for (amount in listOf("9007199254740993", "\"9223372036854775808\"", "\"01\"")) {
            val result = load(definition("a", price = fixed(amount)))
            assertTrue(result.catalog.definitions.isEmpty())
            assertTrue(result.diagnostics.any { it.kind == "invalid-acquisition-payload" })
        }
        val exact = resultDefinition(load(definition("a", price = fixed("\"9007199254740993\""))))
        assertEquals(9007199254740993L, assertIs<PriceRule.Fixed>(exact.priceRule).xp)
    }

    @Test fun missing_duplicate_rank_and_cycle_references_become_blockers() {
        val result = load(definition("a", owned("a/b", "b")), definition("b", owned("b/a", "a")),
            definition("missing", owned("m/z", "z")), definition("badRank", owned("r/a", "a", 3)), definition("a"))
        assertTrue(result.diagnostics.map { it.kind }.containsAll(listOf("duplicate-definition", "missing-reference", "rank-out-of-range", "dependency-cycle")))
        for (id in listOf("a", "missing", "badRank"))
            assertTrue(allNodes(resultDefinition(result, id).requirements).any { it is RequirementNode.Unresolved })
    }

    @Test fun spell_reference_is_typed_and_kind_mismatch_is_blocked() {
        val spell = """{"type":"LearnedSpell","nodeId":"spell","sourceRef":$src,"packId":"dubl-3.69","entryId":"target","operator":"EQ","operand":true}"""
        val good = load(definition("a", spell), definition("target", kind="SPELL"))
        assertIs<RequirementNode.LearnedSpell>(resultDefinition(good).requirements)
        assertFalse(good.diagnostics.any { it.kind == "reference-kind-mismatch" })
        assertTrue(load(definition("a", spell), definition("target")).diagnostics.any { it.kind == "reference-kind-mismatch" })
    }

    @Test fun all_price_variants_retain_source_and_partial_transition_table() {
        val table = """{"type":"TransitionTable","sourceRef":$src,"transitions":[{"fromRank":0,"toRank":1,"xp":"20","ap":"0","sourceRef":$src}]}"""
        val t = assertIs<PriceRule.TransitionTable>(resultDefinition(load(definition("a", price=table))).priceRule)
        assertEquals(1, t.transitions.size)
        assertEquals(42, t.transitions.single().sourceRef.paragraph)
        val conditional = """{"type":"Conditional","sourceRef":$src,"condition":${unresolved()},"whenTrue":${fixed()},"whenFalse":{"type":"Unknown","sourceRef":$src,"reason":"Unreviewed transition"}}"""
        assertIs<PriceRule.Conditional>(resultDefinition(load(definition("a", price=conditional))).priceRule)
        val perRank = fixed().replace("Fixed", "PerRank")
        assertIs<PriceRule.PerRank>(resultDefinition(load(definition("a", price=perRank))).priceRule)
    }

    @Test fun unknown_rank_is_not_unlimited_and_unknown_options_are_blocked() {
        val result = load(definition("a", owned("a/b", "b")), definition("b", max="null"))
        assertTrue(result.diagnostics.any { it.kind == "unresolved-reference-rank" })
        val price = """{"type":"ByOption","sourceRef":$src,"options":{"unknown":${fixed()}}}"""
        assertTrue(load(definition("a", price=price)).diagnostics.any { it.kind == "price-option-out-of-range" })
    }

    @Test fun price_condition_references_are_validated_too() {
        val conditional = """{"type":"Conditional","sourceRef":$src,"condition":${owned("price/ref", "missing")},"whenTrue":${fixed()},"whenFalse":${fixed()}}"""
        val result = load(definition("a", price=conditional))
        assertTrue(result.diagnostics.any { it.kind == "missing-reference" && it.nodeId == "price/ref" })
    }

    private fun any(vararg children: String) = """{"type":"AnyOf","nodeId":"or","sourceRef":$src,"children":[${children.joinToString()}]}"""
    private val strength = """{"type":"Attribute","nodeId":"str","sourceRef":$src,"attributeId":"STR","operator":"GE","operand":1}"""

    @Test fun invalid_or_sibling_does_not_add_mandatory_root_condition() {
        val d = resultDefinition(load(definition("a", any(strength, owned("missing", "z")))))
        val tree = assertIs<RequirementNode.AnyOf>(d.requirements)
        assertIs<RequirementNode.Attribute>(tree.children[0])
        assertIs<RequirementNode.Unresolved>(tree.children[1])
    }

    @Test fun static_scc_preserves_valid_alternative_and_dependency() {
        val result = load(definition("a", any(strength, owned("a/b", "b"))), definition("b", owned("b/a", "a")))
        assertIs<RequirementNode.AnyOf>(resultDefinition(result).requirements)
        assertIs<RequirementNode.OwnedRank>(resultDefinition(result, "b").requirements)
        assertTrue(result.diagnostics.any { it.kind == "dependency-cycle" })
    }

    @Test fun invalid_price_branches_and_rows_leave_other_prices_and_requirements_usable() {
        val table = """{"type":"TransitionTable","sourceRef":$src,"transitions":[{"fromRank":0,"toRank":1,"xp":"20","ap":"0","sourceRef":$src},{"fromRank":2,"toRank":3,"xp":"40","ap":"0","sourceRef":$src}]}"""
        val options = """{"type":"ByOption","sourceRef":$src,"options":{"valid":$table,"unknown":${fixed()}}}"""
        val conditional = """{"type":"Conditional","sourceRef":$src,"condition":${owned("price-condition", "absent")},"whenTrue":${fixed()},"whenFalse":$options}"""
        val input = definition("a", strength, conditional).replace("\"abilityOptionIds\":[]", "\"abilityOptionIds\":[\"valid\"]")
        val result = load(input)
        val d = resultDefinition(result)
        assertIs<RequirementNode.Attribute>(d.requirements)
        val price = assertIs<PriceRule.Conditional>(d.priceRule)
        assertIs<RequirementNode.Unresolved>(price.condition)
        assertIs<PriceRule.Fixed>(price.whenTrue)
        val byOption = assertIs<PriceRule.ByOption>(price.whenFalse)
        assertIs<PriceRule.Unknown>(byOption.options.getValue("unknown"))
        val rows = assertIs<PriceRule.TransitionTable>(byOption.options.getValue("valid")).transitions
        assertEquals(listOf(1), rows.map { it.toRank })
        assertTrue(result.diagnostics.any { it.kind == "price-rank-out-of-range" })
        assertTrue(result.diagnostics.any { it.kind == "price-option-out-of-range" })
    }

    @Test fun canonical_phase_type_and_no_empty_conjunction_fallback() {
        val phase = """{"type":"CreationPhase","nodeId":"phase","sourceRef":$src,"operator":"EQ","operand":"Creating"}"""
        assertEquals(RequirementNode.CreationPhase.Phase.Creating, assertIs<RequirementNode.CreationPhase>(resultDefinition(load(definition("a", phase))).requirements).operand)
        val empty = """{"type":"AllOf","nodeId":"empty","sourceRef":$src,"children":[]}"""
        assertTrue(load(definition("a", empty)).diagnostics.any { it.kind == "invalid-acquisition-payload" })
    }
}
