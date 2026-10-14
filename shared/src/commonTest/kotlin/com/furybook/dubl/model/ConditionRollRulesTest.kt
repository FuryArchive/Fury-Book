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

    @Test fun unresolved_exhaustion_does_not_silently_add_weakness_or_publish_a_total() {
        val active = setOf(CharacterConditionId.EXHAUSTION)
        val modifier = empty.conditionRollModifiers(RollContext.ATTRIBUTE, AttributeId.STRENGTH, active)
        assertNotNull(modifier.unavailableReason)
        assertNull(modifier.apply(10))
        assertEquals(0, modifier.hindranceDice)
        assertNull(empty.conditionDerivedValues(active).defense)
        assertNull(empty.conditionDerivedValues(active).reflexes)
        assertNull(empty.conditionDerivedValues(active).run)
        assertTrue(modifier.notice.contains("Истощения"))
    }

    @Test fun unsupported_condition_is_visible_not_claimed_as_implemented() {
        val result = healthy.conditionRollModifiers(RollContext.SKILL, AttributeId.INTELLIGENCE,
            setOf(CharacterConditionId.BLINDNESS))
        assertEquals(setOf(CharacterConditionId.BLINDNESS), result.unimplemented)
        assertTrue(result.notice.contains("Слепота"))
    }
}
