package com.furybook.dubl.acquisition

import com.furybook.content.FcpContentPack
import com.furybook.content.FcpTextSource
import com.furybook.dubl.model.*
import kotlin.test.*

/** Exact current Жила identity/currency/limit facts; this does not waive its source prerequisite chain. */
class AcquisitionBloodRepeatSourceTest {
    private val catalog by lazy { loadAcquisitionCatalog(listOf(FcpContentPack.load("fcp/dubl-3.69",
        FcpTextSource { javaClass.classLoader.getResource(it)?.readText() })), false).catalog }
    private val ref = DefinitionRef("dubl-3.69", "feat_0f2944736b20e934")
    private fun state(copies: Int): StoredCharacter {
        val phase = RequirementNode.CreationPhase.Phase.Creating
        return StoredCharacter(DublCharacter("blood", attributes = defaultAttributes() +
            (AttributeId.CONSTITUTION to AttributeValue(3))), CharacterSheetExtras(),
            (1..copies).map { AcquisitionInstance("old/$it", ref, 1, emptyMap(), "xp", AcquisitionOrigin.New) },
            Ledger(Opening("opening", "preserved-old-balance", Amount(100, 100), phase,
                OpeningProvenance.UserConfirmed, "Exact existing balance"), emptyList()), emptyList(), emptyList(), SyncMetadata(0), 0, 0, phase)
    }

    @Test fun actual_current_card_has_explicit_currency_alternatives_and_independent_copies() {
        val definition = catalog.definitions.getValue(ref)
        assertEquals("Жила", definition.name)
        assertEquals(RepeatPolicy.Independent, definition.repeatPolicy)
        assertTrue(definition.parameterDefinitions.isEmpty())
        assertEquals(setOf("xp", "ap"), definition.abilityOptionIds)
        assertTrue(definition.hasTransitionPaymentOptions)
        val limit = checkNotNull(definition.instanceCountLimit)
        assertEquals(AttributeId.CONSTITUTION, limit.attributeId)
        assertEquals(1, limit.numerator)
        assertEquals(2, limit.denominator)
        assertTrue(limit.sourceRef.originalText.contains("не более Телосложения/2"))
        val context = acquisitionContext(state(0), catalog)
        for ((option, expected) in listOf("xp" to Amount(30, 0), "ap" to Amount(0, 30))) {
            val change = AcquisitionChange(AcquisitionKind.DEVELOPMENT, ref, "new", 0, 1, emptyMap(), option)
            assertEquals(expected, assertIs<PriceResult.Known>(quote(change, context)).cost)
        }
        assertIs<PriceResult.Unknown>(quote(AcquisitionChange(AcquisitionKind.DEVELOPMENT, ref, "new", 0, 1, emptyMap(), null), context))
        assertEquals("Опыт", acquisitionAbilityOptionLabel("xp"))
        assertEquals("Очки способностей", acquisitionAbilityOptionLabel("ap"))
    }

    @Test fun source_limit_blocks_additional_copies_and_preserves_old_over_limit_holdings() {
        val before = state(3)
        val noOp = plan(AcquisitionRequest("blood", listOf(AcquisitionTarget(ref, "old/1", 1, optionId = "xp"))), before, catalog)
        assertTrue(noOp.executable, noOp.issues.toString())
        assertTrue(noOp.changes.isEmpty())
        assertEquals(Amount.ZERO, noOp.cost)
        val blocked = plan(AcquisitionRequest("blood", listOf(AcquisitionTarget(ref, "new", 1, optionId = "ap"))), before, catalog)
        assertFalse(blocked.executable)
        assertTrue(blocked.issues.any { it.status == RequirementStatus.Unsatisfied && it.nodeId?.endsWith("/instance-count-limit") == true })
        assertEquals(3, before.instances.size)
        assertEquals(Amount(100, 100), balance(before.ledger))
    }

    @Test fun actual_ranked_blood_currency_is_not_identity_but_unconfirmed_later_prices_stay_unknown() {
        val ranked = DefinitionRef("dubl-3.69", "feat_430bb8e0abcdd492")
        val definition = catalog.definitions.getValue(ranked)
        assertTrue(definition.hasTransitionPaymentOptions)
        val old = AcquisitionInstance("old-blood", ranked, 1, emptyMap(), "xp", AcquisitionOrigin.New)
        assertIs<IdentityValidation.ExistingInstance>(validateAcquisitionTarget(ranked, checkNotNull(definition.maxRank),
            definition.parameterDefinitions, definition.repeatPolicy, old.instanceId, 2, emptyMap(), "ap",
            listOf(old), definition.abilityOptionIds, catalog))
        val before = state(0).copy(instances = listOf(old))
        assertIs<PriceResult.Unknown>(quote(AcquisitionChange(AcquisitionKind.DEVELOPMENT, ranked, old.instanceId,
            1, 2, emptyMap(), "ap", paymentOption = true), acquisitionContext(before, catalog)))
        assertEquals("xp", before.instances.single().abilityOptionId)
    }
}
