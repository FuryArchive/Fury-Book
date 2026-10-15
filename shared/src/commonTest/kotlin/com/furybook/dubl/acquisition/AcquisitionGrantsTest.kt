package com.furybook.dubl.acquisition

import com.furybook.dubl.model.*
import kotlin.test.*

/** Isolated grant protocol cases; real-source integration is exercised in the desktop FCP test. */
class AcquisitionGrantsTest {
    private val owner = DefinitionRef("test", "owner")
    private val gift = DefinitionRef("test", "gift")
    private val source = RuleSourceRef("test-book", "1", "The selected entry is included in this newly acquired rank.")
    private fun definition(ref: DefinitionRef, price: Long) = AcquisitionDefinition(ref, AcquisitionKind.DEVELOPMENT, 1,
        RepeatPolicy.Single, RequirementNode.AllOf(ref.entryId, source, emptyList(), NoRequirementsEvidence(source, "Explicit test rule permits purchase")),
        PriceRule.Fixed(price, 0, source), SourceStatus.Confirmed, ref.entryId, source)
    private fun catalog(newOnly: Boolean = false): AcquisitionCatalog {
        val grantor = definition(owner, 20).copy(parameterDefinitions = listOf(ParameterDefinition("gift", ParameterDomain.StableIds(setOf("chosen")))),
            includedGrants = listOf(IncludedGrant(source, "gift", 1, mapOf("chosen" to gift), requireNewOwnership = newOnly)))
        return AcquisitionCatalog(mapOf(owner to grantor, gift to definition(gift, 10)), "catalog", "source")
    }
    private fun state(instances: List<AcquisitionInstance> = emptyList()): StoredCharacter {
        val phase = RequirementNode.CreationPhase.Phase.Creating
        return StoredCharacter(DublCharacter("grant"), CharacterSheetExtras(), instances,
            Ledger(Opening("opening", "source", Amount(100, 0), phase, OpeningProvenance.NewCharacter), emptyList()),
            emptyList(), emptyList(), SyncMetadata(0), 0, 0, phase)
    }
    private fun target() = AcquisitionTarget(owner, "owner-instance", 1, mapOf("gift" to "chosen"))

    @Test fun one_operation_includes_selected_target_once_even_when_the_bulk_target_comes_first() {
        val rules = catalog()
        val request = AcquisitionRequest("grant", listOf(AcquisitionTarget(gift, "captured-gift", 1), target(), target()))
        val result = plan(request, state(), rules)
        assertTrue(result.executable, result.issues.toString())
        assertEquals(listOf(owner, gift), result.changes.map { it.definition })
        assertEquals("captured-gift", result.changes.last().instanceId)
        assertEquals(listOf(Amount(20, 0), Amount.ZERO), result.prices.map { it.cost })
        assertEquals(Amount(80, 0), result.remaining)
        assertTrue(source in result.prices.last().componentSources)
        val replay = validateAcquisitionProjection(result.changes, state(), rules)
        assertTrue(replay.issues.isEmpty(), replay.issues.toString())
        assertEquals(result.prices.map { it.cost }, replay.prices.map { it.cost })
        assertTrue(validateAcquisitionProjection(result.changes.take(1), state(), rules).issues.isNotEmpty(),
            "Replaying only the charged owner cannot silently discard its included choice")
    }

    @Test fun a_previous_owner_neither_grants_on_read_nor_discounts_a_future_purchase() {
        val previous = AcquisitionInstance("owner-instance", owner, 1, mapOf("gift" to "chosen"), null, AcquisitionOrigin.New)
        val before = state(listOf(previous))
        val result = plan(AcquisitionRequest("grant", listOf(AcquisitionTarget(gift, targetRank = 1))), before, catalog())
        assertTrue(result.executable, result.issues.toString())
        assertEquals(Amount(10, 0), result.cost)
        assertEquals(listOf(gift), result.changes.map { it.definition })
        val noOp = plan(AcquisitionRequest("grant", listOf(target())), before, catalog())
        assertTrue(noOp.executable, noOp.issues.toString())
        assertTrue(noOp.changes.isEmpty())
        assertEquals(listOf(previous), acquisitionContext(before, catalog()).instances)
    }

    @Test fun source_new_only_rejects_a_preowned_choice_without_repricing_or_refunding_it() {
        val prior = AcquisitionInstance("old-gift", gift, 1, emptyMap(), null, AcquisitionOrigin.New)
        val before = state(listOf(prior))
        val blocked = plan(AcquisitionRequest("grant", listOf(target())), before, catalog(newOnly = true))
        assertFalse(blocked.executable)
        assertTrue(blocked.issues.any { it.status == RequirementStatus.Conflict })
        assertEquals(listOf(prior), before.instances)
        val permitted = plan(AcquisitionRequest("grant", listOf(target())), before, catalog())
        assertTrue(permitted.executable, permitted.issues.toString())
        assertEquals(Amount(20, 0), permitted.cost)
        assertEquals(listOf(owner), permitted.changes.map { it.definition })
    }

    @Test fun included_target_still_needs_its_source_eligibility() {
        val rules = catalog()
        val unavailable = rules.definitions.getValue(gift).copy(requirements =
            RequirementNode.Unresolved("gift/source", source, "A required source interpretation is unresolved"))
        val result = plan(AcquisitionRequest("grant", listOf(target())), state(),
            rules.copy(definitions = rules.definitions + (gift to unavailable)))
        assertFalse(result.executable)
        assertTrue(result.issues.any { it.status == RequirementStatus.SourceUnresolved })
        assertTrue(result.changes.none { it.definition == gift })
    }

    @Test fun a_rank_raise_includes_only_newly_activated_choices() {
        val second = DefinitionRef("test", "second-gift")
        val third = DefinitionRef("test", "third-gift")
        val mappings = mapOf("first" to gift, "second" to second, "third" to third)
        val grantor = definition(owner, 20).copy(maxRank = 3, priceRule = PriceRule.PerRank(20, 0, source),
            parameterDefinitions = (1..3).map { rank -> ParameterDefinition("gift$rank",
                ParameterDomain.StableIds(mappings.keys), requiredAtRank = rank) },
            includedGrants = (1..3).map { rank -> IncludedGrant(source, "gift$rank", rank, mappings) })
        val rules = catalog().copy(definitions = mapOf(owner to grantor, gift to definition(gift, 10),
            second to definition(second, 10), third to definition(third, 10)))
        val before = state(listOf(AcquisitionInstance("owner-instance", owner, 1, mapOf("gift1" to "first"), null, AcquisitionOrigin.New),
            AcquisitionInstance("first-gift", gift, 1, emptyMap(), null, AcquisitionOrigin.New)))
        val request = AcquisitionRequest("grant", listOf(AcquisitionTarget(owner, "owner-instance", 3,
            mapOf("gift1" to "first", "gift2" to "second", "gift3" to "third"))))
        val result = plan(request, before, rules)
        assertTrue(result.executable, result.issues.toString())
        assertEquals(listOf(owner, second, third), result.changes.map { it.definition })
        assertEquals(listOf(Amount(40, 0), Amount.ZERO, Amount.ZERO), result.prices.map { it.cost })
        assertEquals(Amount(40, 0), result.cost)
        assertEquals(before.instances.last(), result.projectedContext.instances.single { it.definition == gift })
        assertTrue(validateAcquisitionProjection(result.changes, before, rules).issues.isEmpty())
    }

    @Test fun grant_rule_and_operation_context_capture_mutable_inputs() {
        val targets = mutableMapOf("chosen" to gift)
        val rule = IncludedGrant(source, "gift", 1, targets)
        targets.clear()
        assertEquals(gift, rule.targetByValue["chosen"])
        val changes = mutableListOf(AcquisitionChange(AcquisitionKind.DEVELOPMENT, owner, "owner-instance", 0, 1,
            mapOf("gift" to "chosen"), null))
        val before = state()
        val rules = catalog().let { it.copy(definitions = it.definitions + (owner to it.definitions.getValue(owner).copy(includedGrants = listOf(rule)))) }
        val context = AcquisitionContext(before.character, before.instances, emptySet(), before.creationPhase, emptyList(), rules, changes)
        changes.clear()
        assertEquals(Amount.ZERO, assertIs<PriceResult.Known>(quote(AcquisitionChange(AcquisitionKind.DEVELOPMENT, gift,
            "new-gift", 0, 1, emptyMap(), null), context)).cost)
        assertEquals(Amount(10, 0), assertIs<PriceResult.Known>(quote(AcquisitionChange(AcquisitionKind.DEVELOPMENT, gift,
            "new-gift", 0, 1, emptyMap(), null), acquisitionContext(before, rules))).cost)
    }
}
