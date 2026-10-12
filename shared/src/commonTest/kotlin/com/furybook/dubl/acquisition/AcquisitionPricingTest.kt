package com.furybook.dubl.acquisition

import com.furybook.dubl.model.DublCharacter
import kotlin.test.*

class AcquisitionPricingTest {
    private val source = RuleSourceRef("test-book", "1", "Explicit test price")
    private val ref = DefinitionRef("test", "entry")
    private fun context(rule: PriceRule, status: SourceStatus = SourceStatus.NeedsResolution,
        phase: RequirementNode.CreationPhase.Phase = RequirementNode.CreationPhase.Phase.Finalized): AcquisitionContext {
        val d = AcquisitionDefinition(ref, AcquisitionKind.DEVELOPMENT, 10, RepeatPolicy.Single,
            RequirementNode.Unresolved("admission", source, "Independent admission question"), rule, status, "entry", source)
        return AcquisitionContext(DublCharacter("test"), emptyList(), emptySet(), phase, emptyList(),
            AcquisitionCatalog(mapOf(ref to d), "1", "1"))
    }
    private fun change(from: Int = 0, to: Int = 1, option: String? = null) =
        AcquisitionChange(AcquisitionKind.DEVELOPMENT, ref, "instance", from, to, emptyMap(), option)
    private fun known(result: PriceResult) = assertIs<PriceResult.Known>(result).cost

    @Test fun price_is_independent_of_admission_and_unknown_is_not_free() {
        assertEquals(Amount(45, 0), known(quote(change(), context(PriceRule.Fixed(45, 0, source)))))
        assertEquals(Amount(0, 0), known(quote(change(), context(PriceRule.Fixed(0, 0, source)))))
        assertIs<PriceResult.Unknown>(quote(change(), context(PriceRule.Unknown("missing", source))))
        assertIs<PriceResult.Unknown>(quote(change(), context(PriceRule.Fixed(0, 0, source), SourceStatus.Unavailable)))
    }

    @Test fun marginal_steps_are_summed_without_repricing_owned_ranks() {
        val table = PriceRule.TransitionTable(listOf(
            PriceRule.Transition(0, 1, 10, 0, source), PriceRule.Transition(1, 2, 20, 0, source),
            PriceRule.Transition(2, 3, 30, 0, source)), source)
        assertEquals(Amount(50, 0), known(quote(change(1, 3), context(table))))
        assertEquals(Amount(0, 0), known(quote(change(2, 2), context(table))))
        assertIs<PriceResult.Unknown>(quote(change(1, 2), context(PriceRule.Fixed(20, 0, source))))
        assertIs<PriceResult.Unknown>(quote(change(2, 4), context(table)))
    }

    @Test fun explicit_access_payment_options_and_stage_grants() {
        val options = PriceRule.ByOption(mapOf("xp" to PriceRule.Fixed(100, 0, source),
            "ap" to PriceRule.Fixed(0, 1, source)), source)
        assertEquals(Amount(0, 1), known(quote(change(option="ap"), context(options))))
        assertEquals(Amount(100, 0), known(quote(change(option="xp"), context(options))))
        assertIs<PriceResult.Unknown>(quote(change(), context(options)))
        val grant = PriceRule.Conditional(RequirementNode.CreationPhase("phase", source,
            RequirementNode.CreationPhase.Phase.Creating), PriceRule.PerRank(-20, 0, source), PriceRule.PerRank(0, 0, source), source)
        assertEquals(Amount(-60, 0), known(quote(change(to=3), context(grant, phase=RequirementNode.CreationPhase.Phase.Creating))))
        assertEquals(Amount(0, 0), known(quote(change(to=3), context(grant))))
        val uncertain = PriceRule.Conditional(RequirementNode.Unresolved("q", source, "missing condition"),
            PriceRule.Fixed(0, 0, source), PriceRule.Fixed(10, 0, source), source)
        assertIs<PriceResult.Unknown>(quote(change(), context(uncertain)))
    }

    @Test fun overflow_rejected() {
        assertFailsWith<ArithmeticException> { Amount(Long.MAX_VALUE, 0) + Amount(1, 0) }
        assertFailsWith<ArithmeticException> { Amount(0, Long.MIN_VALUE) - Amount(0, 1) }
        assertEquals(Amount(-1, 0), Amount(Long.MIN_VALUE, 0) - Amount(-Long.MAX_VALUE, 0))
        val result = quote(change(to=2), context(PriceRule.PerRank(Long.MAX_VALUE, 0, source)))
        assertTrue(assertIs<PriceResult.Unknown>(result).reason.contains("overflow", ignoreCase=true))
    }

    @Test fun amounts_use_exact_decimal_strings() {
        val amount = Amount(9007199254740993, Long.MIN_VALUE)
        assertEquals("{\"xp\":\"9007199254740993\",\"ap\":\"-9223372036854775808\"}", amount.toJson())
        assertEquals(amount, Amount.fromJson(amount.toJson()))
        for (bad in listOf("{\"xp\":9007199254740993,\"ap\":\"0\"}", "{\"xp\":\"01\",\"ap\":\"0\"}",
            "{\"xp\":\"9223372036854775808\",\"ap\":\"0\"}")) assertFailsWith<IllegalArgumentException> { Amount.fromJson(bad) }
    }

    @Test fun unrelated_unknown_discount_condition_does_not_hide_an_identical_quote() {
        val same = PriceRule.Conditional(RequirementNode.Unresolved("discount", source, "Unknown ownership"),
            PriceRule.Fixed(30, 0, source), PriceRule.Fixed(30, 0, source), source)
        assertEquals(Amount(30, 0), known(quote(change(), context(same))))
    }

    @Test fun acquisition_change_snapshots_parameters() {
        val parameters = mutableMapOf("knowledgeId" to "anatomy")
        val change = AcquisitionChange(AcquisitionKind.DEVELOPMENT, ref, "new", 0, 1, parameters, null)
        parameters["knowledgeId"] = "history"
        assertEquals(mapOf("knowledgeId" to "anatomy"), change.parameters)
        assertEquals(change, change.copy())
    }

    @Test fun parameter_snapshot_obeys_map_entry_and_hash_key_contracts() {
        val original = mutableMapOf("knowledgeId" to "anatomy", "schoolId" to "history")
        val ordinary = original.toMap()
        val snapshot = AcquisitionChange(AcquisitionKind.DEVELOPMENT, ref, "new", 0, 1, original, null).parameters
        assertEquals(ordinary, snapshot)
        assertEquals(snapshot, ordinary)
        assertEquals(ordinary.hashCode(), snapshot.hashCode())
        for (entry in ordinary.entries) {
            val captured = snapshot.entries.single { it.key == entry.key }
            assertEquals(entry, captured)
            assertEquals(captured, entry)
            assertEquals(entry.hashCode(), captured.hashCode())
        }
        assertEquals("ordinary", hashMapOf(ordinary to "ordinary")[snapshot])
        assertEquals("snapshot", hashMapOf(snapshot to "snapshot")[ordinary])
        val ordinaryEntry = ordinary.entries.first()
        val capturedEntry = snapshot.entries.single { it.key == ordinaryEntry.key }
        assertEquals("ordinary-entry", hashMapOf(ordinaryEntry to "ordinary-entry")[capturedEntry])
        assertEquals("snapshot-entry", hashMapOf(capturedEntry to "snapshot-entry")[ordinaryEntry])
        original.clear()
        original["knowledgeId"] = "different"
        assertEquals(ordinary, snapshot)
        assertEquals(ordinary.hashCode(), snapshot.hashCode())
        assertEquals("retained", hashMapOf(ordinary to "retained")[snapshot])
        assertFalse(snapshot is MutableMap<*, *>)
        assertFalse(capturedEntry is MutableMap.MutableEntry<*, *>)
    }
}
