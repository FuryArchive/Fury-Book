package com.furybook.dubl.model

import kotlin.test.assertEquals
import kotlin.test.Test

class CharacterRollContextsTest {
    private val character = DublCharacter(
        id = "roll-context",
        attributes = defaultAttributes() + mapOf(
            AttributeId.DEXTERITY to AttributeValue(base = 4),
            AttributeId.STRENGTH to AttributeValue(base = 3),
            AttributeId.CHARISMA to AttributeValue(base = 5),
        ),
        skills = mapOf(
            "melee_weapon" to CharacterSkill(id = "melee_weapon", definitionId = "melee_weapon", rank = 6),
            "unarmed" to CharacterSkill(id = "unarmed", definitionId = "unarmed", rank = 4),
            "eloquence" to CharacterSkill(id = "eloquence", definitionId = "eloquence", rank = 3),
        ),
        development = mapOf(
            DevelopmentEffectIds.FENCER to OwnedDevelopment(3),
            DevelopmentEffectIds.FEINTER to OwnedDevelopment(2),
        ),
    )

    @Test
    fun fencerAppliesOnlyToParry() {
        val parry = character.rollPreset(RollContext.PARRY, "melee_weapon", AttributeId.DEXTERITY)
        val attack = character.rollPreset(RollContext.ATTACK, "melee_weapon", AttributeId.DEXTERITY)

        assertEquals(13, parry.bonus)
        assertEquals(10, attack.bonus)
    }

    @Test
    fun feinterAppliesOnlyToFeint() {
        val feint = character.rollPreset(RollContext.FEINT, "eloquence", AttributeId.CHARISMA)
        val ordinaryEloquence = character.rollPreset(RollContext.SKILL, "eloquence", AttributeId.CHARISMA)

        assertEquals(10, feint.bonus)
        assertEquals(8, ordinaryEloquence.bonus)
    }

    @Test
    fun simpleCombatManeuversUseRulebookSkillAttributePairs() {
        assertEquals(8, character.rollPreset(RollContext.TRIP).bonus)
        assertEquals(7, character.rollPreset(RollContext.GRAPPLE).bonus)
        assertEquals(7, character.rollPreset(RollContext.PUSH).bonus)
        assertEquals(7, character.rollPreset(RollContext.KNOCKDOWN).bonus)
    }

    @Test
    fun flurryUsesAttackSkillsAndCoreWeaponTable() {
        assertEquals(
            listOf("unarmed", "melee_weapon", "shooting", "throwing"),
            RollContext.FLURRY.allowedSkillIds(),
        )
        assertEquals(
            listOf(AttributeId.DEXTERITY, AttributeId.STRENGTH),
            RollContext.FLURRY.allowedAttributes("melee_weapon"),
        )

        val full = FlurryWeaponProfile.LIGHT.rule(short = false)
        assertEquals(4, full.attackBonus)
        assertEquals(2, full.excessPerHit)
        assertEquals(3, full.maxHits)
        assertEquals(0, full.shortExcessPenalty)
        assertEquals("полный раунд", full.actionCost)
        assertEquals(false, full.reactionAllowed)

        val shortOneHanded = FlurryWeaponProfile.ONE_HANDED.rule(short = true)
        assertEquals(2, shortOneHanded.attackBonus)
        assertEquals(3, shortOneHanded.excessPerHit)
        assertEquals(1, shortOneHanded.shortExcessPenalty)
        assertEquals("2 ОД", shortOneHanded.actionCost)

        val twoWeapons = FlurryWeaponProfile.TWO_WEAPONS_TWO_HANDED.rule(short = false)
        assertEquals(2, twoWeapons.attackBonus)
        assertEquals(4, twoWeapons.excessPerHit)
        assertEquals(6, twoWeapons.maxHits)
        assertEquals(true, twoWeapons.alternateWeaponDamage)
    }

    @Test
    fun shortFlurryRoundsHalfBonusUp() {
        assertEquals(2, FlurryWeaponProfile.LIGHT.rule(short = true).attackBonus)
        assertEquals(2, FlurryWeaponProfile.ONE_HANDED.rule(short = true).attackBonus)
        assertEquals(1, FlurryWeaponProfile.TWO_HANDED.rule(short = true).attackBonus)
    }

}
