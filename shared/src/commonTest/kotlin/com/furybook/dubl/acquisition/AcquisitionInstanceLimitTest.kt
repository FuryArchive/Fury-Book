package com.furybook.dubl.acquisition

import com.furybook.content.FcpContentPack
import com.furybook.content.FcpTextSource
import com.furybook.dubl.model.*
import kotlin.test.*

class AcquisitionInstanceLimitTest {
    private val ref = DefinitionRef("dubl-3.69", "bounded-copy")
    private fun catalog(independent: Boolean = false): AcquisitionCatalog {
        val source = """{"documentId":"test-source","documentVersion":"1","originalText":"May acquire multiple copies, no more than Constitution divided by two."}"""
        val repeat = if (independent) """{"type":"Independent"}""" else """{"type":"DistinctParameter","parameterKeys":["copy"],"requireUnique":true}"""
        val parameters = if (independent) "[]" else """[{"key":"copy","domain":{"type":"UserDefinedIds"}}]"""
        val definition = """{"ref":{"packId":"dubl-3.69","entryId":"bounded-copy"},"kind":"DEVELOPMENT","name":"bounded-copy","maxRank":1,
          "repeatPolicy":$repeat,"sourceStatus":"Confirmed","sourceRef":$source,"parameterDefinitions":$parameters,
          "abilityOptionIds":["xp","ap"],"requirementsText":"Explicit none",
          "requirements":{"type":"AllOf","nodeId":"none","sourceRef":$source,"children":[],"noRequirementsStatement":"Explicit none"},
          "priceRule":{"type":"ByOption","sourceRef":$source,"options":{
            "xp":{"type":"Fixed","sourceRef":$source,"xp":"30","ap":"0"},
            "ap":{"type":"Fixed","sourceRef":$source,"xp":"0","ap":"30"}}},
          "instanceCountLimit":{"sourceRef":$source,"attributeId":"CON","numerator":1,"denominator":2}}"""
        val manifest = """{"format":"fury.content-pack","formatVersion":1,"id":"dubl-3.69","name":"Core","version":"3.69","ruleset":{"id":"dubl","version":"3.69","engineApi":1},"modules":[],"entries":[{"kind":"dubl.acquisition","path":"content/acquisition.json","modules":[],"order":1}]}"""
        val payload = """{"schemaVersion":1,"catalogRevision":"1","resolutionRevision":"1","definitions":[$definition],"diagnostics":[]}"""
        val files = mapOf("manifest.json" to manifest, "content/acquisition.json" to payload)
        val loaded = loadAcquisitionCatalog(listOf(FcpContentPack.load("", FcpTextSource(files::get))), false)
        assertTrue(loaded.diagnostics.isEmpty(), loaded.diagnostics.toString())
        return loaded.catalog
    }
    private fun state(constitution: Int, copies: Int = 0, independent: Boolean = false): StoredCharacter {
        val phase = RequirementNode.CreationPhase.Phase.Creating
        return StoredCharacter(DublCharacter("bounded", attributes = defaultAttributes() +
            (AttributeId.CONSTITUTION to AttributeValue(base = constitution))), CharacterSheetExtras(),
            (1..copies).map { number -> AcquisitionInstance("copy$number", ref, 1,
                if (independent) emptyMap() else mapOf("copy" to number.toString()), "xp", AcquisitionOrigin.New) },
            Ledger(Opening("opening", "legacy-confirmed-balance", Amount(100, 100), phase, OpeningProvenance.UserConfirmed, "Existing balance"), emptyList()),
            emptyList(), emptyList(), SyncMetadata(0), 0, 0, phase)
    }
    private fun target(number: Int, option: String = "xp", independent: Boolean = false) =
        AcquisitionTarget(ref, "copy$number", 1, if (independent) emptyMap() else mapOf("copy" to number.toString()), option)

    @Test fun odd_attribute_boundary_counts_the_proposed_new_copy_and_both_currency_routes() {
        val rules = catalog()
        for (option in listOf("xp", "ap")) {
            val allowed = plan(AcquisitionRequest("bounded", listOf(target(2, option))), state(5, 1), rules)
            assertTrue(allowed.executable, allowed.issues.toString())
            assertEquals(if (option == "xp") Amount(30, 0) else Amount(0, 30), allowed.cost)
            val blocked = plan(AcquisitionRequest("bounded", listOf(target(3, option))), state(5, 2), rules)
            assertFalse(blocked.executable, "Three copies exceed Constitution5/2")
            assertTrue(blocked.issues.any { it.status == RequirementStatus.Unsatisfied })
        }
    }

    @Test fun siblings_in_one_purchase_share_the_same_proposed_instance_limit() {
        val before = state(3)
        val result = plan(AcquisitionRequest("bounded", listOf(target(1), target(2))), before, catalog())
        assertFalse(result.executable)
        assertTrue(result.issues.any { it.status == RequirementStatus.Unsatisfied })
        assertTrue(before.instances.isEmpty())
    }

    @Test fun existing_over_limit_copies_are_retained_and_attained_rank_is_a_no_op() {
        val before = state(3, 3)
        val noOp = plan(AcquisitionRequest("bounded", listOf(target(1))), before, catalog())
        assertTrue(noOp.executable, noOp.issues.toString())
        assertTrue(noOp.changes.isEmpty())
        assertEquals(Amount.ZERO, noOp.cost)
        assertEquals(3, before.instances.size)
        assertEquals(Amount(100, 100), balance(before.ledger))
        assertFalse(plan(AcquisitionRequest("bounded", listOf(target(4))), before, catalog()).executable)
    }

    @Test fun independent_repeats_keep_separate_ids_without_inventing_a_player_parameter() {
        val rules = catalog(independent = true)
        val result = plan(AcquisitionRequest("bounded", listOf(target(1, independent = true), target(2, "ap", true))),
            state(4, independent = true), rules)
        assertTrue(result.executable, result.issues.toString())
        assertEquals(listOf("copy1", "copy2"), result.changes.map { it.instanceId })
        assertEquals(Amount(30, 30), result.cost)
        assertTrue(result.projectedContext.instances.all { it.parameters.isEmpty() })
    }
}
