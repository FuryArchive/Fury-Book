package com.furybook.dubl.model

import kotlin.test.*

class ConditionRollRulesTest {
    private val healthy = DublCharacter("conditions", enduranceCurrent = 3)
    private val empty = healthy.copy(enduranceCurrent = 0)

    @Test fun zero_endurance_and_explicit_weakness_are_a_single_non_persistent_projection() {
        val original = empty.copy()
        val active = setOf(CharacterConditionId.WEAKNESS)
        assertEquals(setOf(CharacterConditionId.WEAKNESS), empty.effectiveRollConditions(active))
        assertEquals(-2, empty.conditionRollModifiers(RollContext.REFLEXES, active = active).numericBonus)
        assertEquals(original, empty)
        assertTrue(healthy.effectiveRollConditions(emptySet()).isEmpty())
        assertEquals(-2, healthy.conditionRollModifiers(RollContext.REFLEXES, active = active).numericBonus)
    }

    @Test fun weakness_affects_derived_values_and_dodge_exactly_once() {
        val values = empty.conditionDerivedValues()
        assertEquals(empty.defense - 2, values.defense)
        assertEquals(empty.reflexes - 2, values.reflexes)
        assertEquals(empty.initiative - 2, values.initiative)
        assertEquals(empty.runFull - 2, values.run)
        assertEquals(-2, empty.conditionRollModifiers(RollContext.DODGE).numericBonus)
        assertEquals(0, empty.conditionRollModifiers(RollContext.FORTITUDE).numericBonus)
        assertEquals(0, empty.conditionRollModifiers(RollContext.REFLEXES).hindranceDice)
    }

    @Test fun physical_dice_follow_actual_selected_attribute_and_fixed_maneuvers() {
        val physical = setOf(AttributeId.STRENGTH, AttributeId.DEXTERITY, AttributeId.CONSTITUTION, AttributeId.SPEED)
        for (attribute in AttributeId.entries) {
            assertEquals(if (attribute in physical) 2 else 0,
                empty.conditionRollModifiers(RollContext.ATTRIBUTE, attribute).hindranceDice)
            assertEquals(if (attribute in physical) 2 else 0,
                empty.conditionRollModifiers(RollContext.SKILL, attribute).hindranceDice)
        }
        assertEquals(2, empty.conditionRollModifiers(RollContext.GRAPPLE).hindranceDice)
        assertEquals(2, empty.conditionRollModifiers(RollContext.TRIP).hindranceDice)
        assertEquals(0, empty.conditionRollModifiers(RollContext.FEINT, AttributeId.CHARISMA).hindranceDice)
        assertEquals(2, empty.conditionRollModifiers(RollContext.FEINT, AttributeId.DEXTERITY).hindranceDice)
    }

    @Test fun shock_and_deafness_apply_only_to_their_explicit_roll_contexts() {
        val active = setOf(CharacterConditionId.SHOCK, CharacterConditionId.DEAFNESS)
        assertEquals(-5, healthy.conditionRollModifiers(RollContext.INITIATIVE, active = active).numericBonus)
        assertEquals(-1, healthy.conditionRollModifiers(RollContext.SKILL, active = active).numericBonus)
        assertEquals(0, healthy.conditionRollModifiers(RollContext.FORTITUDE, active = active).numericBonus)
        assertEquals(healthy.defense, healthy.conditionDerivedValues(active).defense)
        assertEquals(healthy.runFull, healthy.conditionDerivedValues(active).run)
        assertEquals(-7, empty.conditionRollModifiers(RollContext.INITIATIVE, active = active).numericBonus)
    }

    @Test fun exhaustion_and_weakness_stack_without_counting_zero_endurance_twice() {
        val active = setOf(CharacterConditionId.EXHAUSTION)
        val modifier = empty.conditionRollModifiers(RollContext.ATTRIBUTE, AttributeId.STRENGTH, active)
        assertNull(modifier.unavailableReason)
        assertEquals(8, modifier.apply(10))
        assertEquals(4, modifier.hindranceDice)
        val values = empty.conditionDerivedValues(active)
        assertEquals(empty.defense - 5, values.defense)
        assertEquals(empty.reflexes - 5, values.reflexes)
        assertEquals(empty.initiative - 5, values.initiative)
        assertEquals(kotlin.math.ceil(empty.runFull / 2) - 2, values.run)
        val explicit = active + CharacterConditionId.WEAKNESS
        assertEquals(modifier, empty.conditionRollModifiers(RollContext.ATTRIBUTE, AttributeId.STRENGTH, explicit))
        assertEquals(values, empty.conditionDerivedValues(explicit))
        assertEquals(values, healthy.conditionDerivedValues(explicit))
    }

    @Test fun unsupported_condition_is_visible_not_claimed_as_implemented() {
        val result = healthy.conditionRollModifiers(RollContext.SKILL, AttributeId.INTELLIGENCE,
            setOf(CharacterConditionId.BLINDNESS))
        assertEquals(setOf(CharacterConditionId.BLINDNESS), result.unimplemented)
        assertTrue(result.notice.contains("Слепота"))
    }

    @Test fun exhaustion_alone_uses_its_own_formula_without_guessing_overlap_or_recovery() {
        val active = setOf(CharacterConditionId.EXHAUSTION)
        val c = healthy.copy(attributes = healthy.attributes + (AttributeId.SPEED to AttributeValue(3)))
        val values = c.conditionDerivedValues(active)
        assertEquals(c.defense - 3, values.defense)
        assertEquals(c.reflexes - 3, values.reflexes)
        assertEquals(c.initiative - 3, values.initiative)
        assertEquals(kotlin.math.ceil(c.runFull / 2), values.run)
        val physical = c.conditionRollModifiers(RollContext.SKILL, AttributeId.STRENGTH, active)
        assertEquals(-2, physical.numericBonus); assertEquals(2, physical.hindranceDice)
        assertNull(physical.unavailableReason)
        assertEquals(0, c.conditionRollModifiers(RollContext.SKILL, AttributeId.INTELLIGENCE, active).numericBonus)
        assertEquals(3, c.enduranceCurrent)
    }

    @Test fun poisoned_attribute_factors_and_shock_skill_maneuvers_follow_the_actual_check() {
        val active = setOf(CharacterConditionId.POISONED, CharacterConditionId.SHOCK)
        for (context in listOf(RollContext.PARRY, RollContext.FEINT, RollContext.GRAPPLE, RollContext.TRIP,
            RollContext.DISARM, RollContext.PUSH, RollContext.KNOCKDOWN))
            assertEquals(-1, healthy.conditionRollModifiers(context, active = active).numericBonus)
        assertEquals(1, healthy.conditionRollModifiers(RollContext.SKILL, AttributeId.INTELLIGENCE, active).hindranceDice)
        assertEquals(3, empty.conditionRollModifiers(RollContext.SKILL, AttributeId.STRENGTH, active).hindranceDice)
        assertEquals(0, healthy.conditionRollModifiers(RollContext.INITIATIVE, active = active).hindranceDice)
        assertEquals(0, healthy.conditionRollModifiers(RollContext.FORTITUDE, active = active).numericBonus)
    }
}
