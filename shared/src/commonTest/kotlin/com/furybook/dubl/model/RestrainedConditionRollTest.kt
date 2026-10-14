package com.furybook.dubl.model

import kotlin.test.*

class RestrainedConditionRollTest {
    private val character = DublCharacter("restrained", enduranceCurrent = 3)

    @Test fun entangled_attack_and_dexterity_penalty_counts_once() {
        val active = setOf(CharacterConditionId.ENTANGLED)
        for (context in listOf(RollContext.ATTACK, RollContext.FLURRY, RollContext.BREAK_ITEM)) {
            for (attribute in listOf(AttributeId.STRENGTH, AttributeId.DEXTERITY, AttributeId.PERCEPTION)) {
                val result = character.conditionRollModifiers(context, attribute, active)
                assertEquals(-3, result.numericBonus)
                assertEquals(1, result.hindranceDice)
            }
        }
        val dex = character.conditionRollModifiers(RollContext.SKILL, AttributeId.DEXTERITY, active)
        assertEquals(-3, dex.numericBonus); assertEquals(1, dex.hindranceDice)
        val mental = character.conditionRollModifiers(RollContext.SKILL, AttributeId.INTELLIGENCE, active)
        assertEquals(0, mental.numericBonus); assertEquals(0, mental.hindranceDice)
        assertEquals(-3, character.conditionRollModifiers(RollContext.TRIP, active = active).numericBonus)
    }

    @Test fun entangled_run_is_unavailable_not_zero_distance_or_an_ordinary_check() {
        val active = setOf(CharacterConditionId.ENTANGLED)
        val run = character.conditionRollModifiers(RollContext.RUN, active = active)
        assertNotNull(run.unavailableReason)
        assertNull(run.apply(10))
        assertNull(character.conditionDerivedValues(active).run)
        assertEquals(character.defense, character.conditionDerivedValues(active).defense)
        assertEquals(character, character.copy())
    }

    @Test fun prone_penalty_is_melee_attack_only_and_defense_is_independent() {
        val active = setOf(CharacterConditionId.PRONE)
        for (context in listOf(RollContext.ATTACK, RollContext.FLURRY)) {
            assertEquals(-2, character.conditionRollModifiers(context, active = active, skillId = "melee_weapon").numericBonus)
            assertEquals(-2, character.conditionRollModifiers(context, active = active, skillId = "unarmed").numericBonus)
            assertEquals(0, character.conditionRollModifiers(context, active = active, skillId = "shooting").numericBonus)
            assertEquals(0, character.conditionRollModifiers(context, active = active, skillId = "throwing").numericBonus)
        }
        assertEquals(0, character.conditionRollModifiers(RollContext.PARRY, active = active, skillId = "melee_weapon").numericBonus)
        assertEquals(0, character.conditionRollModifiers(RollContext.SKILL, AttributeId.DEXTERITY, active, "melee_weapon").numericBonus)
        val values = character.conditionDerivedValues(active)
        assertEquals(character.defense - 2, values.defense)
        assertEquals(character.reflexes, values.reflexes)
        assertEquals(character.runFull, values.run)
    }

    @Test fun stun_defense_is_not_a_blanket_penalty_or_automatic_recovery() {
        val active = setOf(CharacterConditionId.STUNNED)
        val before = character.copy()
        val values = character.conditionDerivedValues(active)
        assertEquals(character.defense - 2, values.defense)
        assertEquals(character.reflexes, values.reflexes)
        val result = character.conditionRollModifiers(RollContext.FORTITUDE, active = active)
        assertEquals(0, result.numericBonus)
        assertTrue(result.notice.contains("Оглушение"))
        assertTrue(result.notice.contains("Не все"))
        assertEquals(before, character)
    }
}
