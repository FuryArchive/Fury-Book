package com.furybook.dubl.model

import kotlin.test.*

class VisionConditionRollTest {
    private val character = DublCharacter("hero", enduranceCurrent=3)
    @Test fun dazzled_attack_and_dexterity_clauses_count_once() {
        val active = setOf(CharacterConditionId.DAZZLED)
        for (context in listOf(RollContext.ATTACK, RollContext.FLURRY, RollContext.BREAK_ITEM)) {
            for (attribute in listOf(AttributeId.STRENGTH, AttributeId.DEXTERITY, AttributeId.PERCEPTION))
                assertEquals(1, character.conditionRollModifiers(context, attribute, active).hindranceDice)
        }
        assertEquals(1, character.conditionRollModifiers(RollContext.ATTRIBUTE, AttributeId.DEXTERITY, active).hindranceDice)
        assertEquals(1, character.conditionRollModifiers(RollContext.TRIP, active=active).hindranceDice)
        assertEquals(0, character.conditionRollModifiers(RollContext.SKILL, AttributeId.INTELLIGENCE, active).hindranceDice)
    }
    @Test fun perception_requires_explicit_visual_context_not_a_guessed_skill_name() {
        val active = setOf(CharacterConditionId.DAZZLED)
        assertNotNull(character.conditionRollModifiers(RollContext.SKILL, AttributeId.PERCEPTION, active).unavailableReason)
        assertEquals(0, character.conditionRollModifiers(RollContext.SKILL, AttributeId.PERCEPTION, active).hindranceDice)
        assertEquals(0, character.conditionRollModifiers(RollContext.SKILL, AttributeId.PERCEPTION, active, requiresVision=false).hindranceDice)
        assertEquals(1, character.conditionRollModifiers(RollContext.SKILL, AttributeId.PERCEPTION, active, requiresVision=true).hindranceDice)
        assertTrue(CharacterConditionId.DAZZLED in character.conditionRollModifiers(RollContext.SKILL, AttributeId.PERCEPTION, active).unimplemented)
    }
    @Test fun blindness_skill_penalty_and_attack_factors_are_separate_source_clauses() {
        val active = setOf(CharacterConditionId.BLINDNESS)
        for (attribute in listOf(AttributeId.DEXTERITY, AttributeId.PERCEPTION))
            assertEquals(-4, character.conditionRollModifiers(RollContext.SKILL, attribute, active).numericBonus)
        assertEquals(0, character.conditionRollModifiers(RollContext.ATTRIBUTE, AttributeId.DEXTERITY, active).numericBonus)
        assertEquals(0, character.conditionRollModifiers(RollContext.SKILL, AttributeId.INTELLIGENCE, active).numericBonus)
        for (context in listOf(RollContext.ATTACK, RollContext.FLURRY, RollContext.BREAK_ITEM))
            assertEquals(2, character.conditionRollModifiers(context, AttributeId.DEXTERITY, active).hindranceDice)
    }
    @Test fun explicitly_visual_skill_has_no_roll_total_when_blind() {
        val result = character.conditionRollModifiers(RollContext.SKILL, AttributeId.INTELLIGENCE,
            setOf(CharacterConditionId.BLINDNESS), requiresVision=true)
        assertNotNull(result.unavailableReason); assertNull(result.apply(8))
        assertTrue(result.unavailableReason.contains("автоматически проваливается"))
        assertTrue(CharacterConditionId.BLINDNESS in result.unimplemented)
    }
    @Test fun unresolved_blind_and_dazzled_overlap_is_not_silently_stacked() {
        val active = setOf(CharacterConditionId.BLINDNESS, CharacterConditionId.DAZZLED)
        val result = character.conditionRollModifiers(RollContext.ATTACK, AttributeId.DEXTERITY, active)
        assertNotNull(result.unavailableReason); assertNull(result.apply(7)); assertEquals(0, result.hindranceDice)
        val unrelated = character.conditionRollModifiers(RollContext.SKILL, AttributeId.INTELLIGENCE, active, requiresVision=false)
        assertNull(unrelated.unavailableReason); assertEquals(0, unrelated.numericBonus)
    }
    @Test fun missing_visual_declaration_blocks_instead_of_silently_rolling() {
        for (attribute in AttributeId.entries) {
            val result = character.conditionRollModifiers(RollContext.SKILL, attribute, setOf(CharacterConditionId.BLINDNESS))
            assertNotNull(result.unavailableReason); assertNull(result.apply(10))
            assertNull(character.conditionRollModifiers(RollContext.SKILL, attribute,
                setOf(CharacterConditionId.BLINDNESS), requiresVision=false).unavailableReason)
        }
        assertTrue(requiresVisionDeclaration(RollContext.ATTRIBUTE, AttributeId.PERCEPTION, setOf(CharacterConditionId.DAZZLED)))
        assertFalse(requiresVisionDeclaration(RollContext.ATTACK, AttributeId.PERCEPTION, setOf(CharacterConditionId.DAZZLED)))
        assertFalse(requiresVisionDeclaration(RollContext.SKILL, AttributeId.PERCEPTION, emptySet()))
    }
    @Test fun unrelated_factors_remain_attributed_and_no_resource_or_condition_is_changed() {
        val active = setOf(CharacterConditionId.DAZZLED, CharacterConditionId.POISONED)
        val before = character.copy()
        val result = character.conditionRollModifiers(RollContext.ATTRIBUTE, AttributeId.DEXTERITY, active)
        assertEquals(2, result.hindranceDice); assertEquals(2, result.hindranceSources.size)
        assertEquals(before, character); assertEquals(3, character.enduranceCurrent)
    }
}
