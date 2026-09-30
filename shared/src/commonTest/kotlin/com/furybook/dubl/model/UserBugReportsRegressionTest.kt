package com.furybook.dubl.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UserBugReportsRegressionTest {
    private fun entry(
        id: String = "mana-gated",
        name: String = "Mana gated",
        requirements: String = "Запас маны (III)",
        cost: Int = 40,
    ) = DevelopmentEntry(
        id = id,
        name = name,
        section = "Навыки",
        category = "Магия",
        cost = cost,
        costType = DevelopmentCostType.XP,
        maxRank = 1,
        requirements = requirements,
        benefit = "",
        notes = "",
        tags = emptyList(),
        accessId = null,
        abilityOptions = emptyList(),
        incomplete = false,
        repeatable = false,
        perfectRoot = false,
        mechanicsConflict = "",
        conflictNote = "",
    )

    @Test
    fun romanManaRequirementAcceptsHigherOwnedRank() {
        val gated = entry()
        val character = DublCharacter(
            id = "mana-4",
            magic = CharacterMagic(manaRank = 4),
        )
        val rules = DevelopmentRules(character, DevelopmentCatalog("test", listOf(gated)), DevelopmentProgress())

        val check = rules.requirements(gated).single()
        assertEquals(RequirementStatus.OK, check.status)
        assertTrue(check.text.contains("4 / 3"))
        assertTrue(rules.availability(gated).canIncrease)
    }

    @Test
    fun manaRequirementSupportsArabicAndRomanSpellings() {
        assertEquals(3, manaRequirementRankOrNull("Запас маны (III)"))
        assertEquals(2, manaRequirementRankOrNull("Запас маны II"))
        assertEquals(4, manaRequirementRankOrNull("Запас маны 4"))
        assertEquals(1, manaRequirementRankOrNull("Базовый запас маны I"))
        assertEquals(null, manaRequirementRankOrNull("Сила магии 3"))
    }

    @Test
    fun plannerCanBuyMissingBaseManaOnlyDuringCreation() {
        val gated = entry()
        val catalog = DevelopmentCatalog("test", listOf(gated))
        val creating = DublCharacter(
            id = "creating",
            experience = 1000,
            creationComplete = false,
            magic = CharacterMagic(manaRank = 1),
        )

        val plan = DevelopmentAcquisitionPlanner(creating, catalog).plan(
            DevelopmentAcquisitionRequest.single(gated.id, includeTarget = true),
        )

        assertTrue(plan.canApply)
        assertEquals(3, plan.projectedCharacter.magic.manaRank)
        assertEquals(240, plan.xpCost)
        assertTrue(plan.steps.any {
            it is DevelopmentAcquisitionStep.ManaRank &&
                it.fromRank == 1 && it.toRank == 3 && it.xpCost == 200
        })

        val completed = creating.copy(creationComplete = true)
        val blocked = DevelopmentAcquisitionPlanner(completed, catalog).plan(
            DevelopmentAcquisitionRequest.single(gated.id, includeTarget = true),
        )
        assertFalse(blocked.canApply)
        assertEquals(1, blocked.projectedCharacter.magic.manaRank)
        assertTrue(blocked.unresolvedRequirements.any { it.contains("только при создании") })
    }

    @Test
    fun learnedMagicSchoolsAppearInSpecialCharacterSheetSection() {
        val character = DublCharacter(
            id = "mage",
            magic = CharacterMagic(
                schools = listOf(
                    MagicSchool("Разрушение", rank = 4),
                    MagicSchool("Ограждение", rank = 2),
                ),
            ),
        )

        val sections = character.characterSheetDevelopmentSections(DevelopmentCatalog("test", emptyList()))
        val special = sections.single { it.type == DevelopmentSheetSectionType.SPECIAL }

        assertEquals(listOf("Ограждение", "Разрушение"), special.items.map { it.entry.name })
        assertEquals(listOf(2, 4), special.items.map { it.rank })
        assertTrue(special.items.all { isMagicSchoolSheetEntryId(it.entry.id) })
    }

    @Test
    fun shortFlurryUsesHalfBonusRoundedUpAndHigherExcessThreshold() {
        val full = FlurryRules.profile(FlurryMode.FULL, FlurryWeaponProfile.LIGHT)
        val short = FlurryRules.profile(FlurryMode.SHORT, FlurryWeaponProfile.LIGHT)

        assertEquals(4, full.attackBonus)
        assertEquals(2, full.excessPerHit)
        assertEquals(2, short.attackBonus)
        assertEquals(3, short.excessPerHit)
        assertFalse(short.reactionAllowed)
        assertEquals(3, FlurryRules.hitCount(6, full))
        assertEquals(2, FlurryRules.hitCount(6, short))
        assertEquals(0, FlurryRules.hitCount(1, full))
    }

    @Test
    fun shortFlurryRoundsOddBonusUpPerGeneralDivisionRule() {
        val short = FlurryRules.profile(FlurryMode.SHORT, FlurryWeaponProfile.ONE_HANDED)
        assertEquals(2, short.attackBonus)
        assertEquals(4, short.excessPerHit)
    }
}
