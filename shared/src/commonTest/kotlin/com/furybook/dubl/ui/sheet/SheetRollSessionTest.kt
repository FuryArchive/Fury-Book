package com.furybook.dubl.ui.sheet

import com.furybook.dubl.model.AttributeId
import com.furybook.dubl.model.AttributeValue
import com.furybook.dubl.model.CharacterSkill
import com.furybook.dubl.model.DublCharacter
import com.furybook.dubl.model.FlurryWeaponProfile
import com.furybook.dubl.model.OwnedDevelopment
import com.furybook.dubl.model.RollContext
import com.furybook.dubl.model.RollFollowUp
import com.furybook.dubl.model.RollMode
import com.furybook.dubl.model.SkillEffectCatalog
import com.furybook.dubl.model.SkillEffectDefinition
import com.furybook.dubl.model.SkillEffectMode
import com.furybook.dubl.model.resolve
import com.furybook.dubl.model.resolveAttack
import com.furybook.dubl.model.resolveSkill
import com.furybook.dubl.model.rollPreset
import com.furybook.dubl.model.rule
import com.furybook.dubl.model.summary
import com.furybook.ui.components.FuryRollTerm
import com.furybook.ui.components.furyRollBreakdown
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SheetRollSessionTest {
    private val hornet = DublCharacter(
        id = "hornet",
        name = "Шершень",
        attributes = AttributeId.entries.associateWith { AttributeValue(base = 3) } +
            (AttributeId.DEXTERITY to AttributeValue(base = 8)),
        skills = mapOf(
            "melee_weapon" to CharacterSkill(id = "melee_weapon", definitionId = "melee_weapon", rank = 4),
            "eloquence" to CharacterSkill(id = "eloquence", definitionId = "eloquence", rank = 2),
        ),
        development = mapOf("clumsy" to OwnedDevelopment(1)),
    )

    private fun dice(vararg faces: Int): () -> Int {
        val iterator = faces.iterator()
        return { iterator.next() }
    }

    /** FakeSheetHost with a skill-effect catalog holding one hindrance toggle for Красноречие. */
    private class EffectHost(
        base: DublSheetHost,
        override val skillEffectCatalog: SkillEffectCatalog,
    ) : DublSheetHost by base

    private val hindranceEffects = SkillEffectCatalog(
        "test",
        listOf(
            SkillEffectDefinition(
                "clumsy-talk", 1, "Косноязычие", "Помеха на Красноречие", "", "", "",
                SkillEffectMode.TOGGLE_HINDRANCE, RollContext.SKILL, listOf("Красноречие"), 0, false,
                "Косноязычие", developmentId = "clumsy",
            ),
        ),
    )

    @Test
    fun attributeRollUsesPresetBonus() {
        val host = FakeSheetHost(hornet)
        val request = SheetRollRequest(SheetRollSubject.Attribute(AttributeId.DEXTERITY))
        val options = sheetRollOptions(host, request)
        val bonus = host.active.rollPreset(RollContext.ATTRIBUTE, attribute = AttributeId.DEXTERITY).bonus!!

        val result = assertNotNull(performSheetRoll(host, request, dice(4, 5)))
        val view = sheetRollView(options, request, result)

        assertEquals(9 + bonus, result.total)
        assertEquals(9 + bonus, view.total)
        assertTrue(FuryRollTerm("Ловкость", 8) in view.terms)
        assertEquals("Ловкость", view.title)
        assertNull(options.blockedReason)
    }

    @Test
    fun advantageWithTwoEffectsRollsFourDiceAndKeepsBestTwo() {
        val host = FakeSheetHost(hornet)
        val request = SheetRollRequest(
            SheetRollSubject.Attribute(AttributeId.DEXTERITY),
            mode = RollMode.ADVANTAGE,
            manualEffectCount = 2,
            situational = -1,
        )

        val result = assertNotNull(performSheetRoll(host, request, dice(1, 6, 2, 5)))
        val view = sheetRollView(sheetRollOptions(host, request), request, result)

        assertEquals(listOf(1, 6, 2, 5), view.dice)
        assertEquals(setOf(1, 3), view.keptDice)
        assertEquals(11 + 8 - 1, view.total)
        assertEquals("11 + 8 Ловкость − 1 Поправка", furyRollBreakdown(view))
    }

    @Test
    fun advantageAndHindranceTogetherIsBlocked() {
        val host = EffectHost(FakeSheetHost(hornet), hindranceEffects)
        val base = SheetRollRequest(SheetRollSubject.Skill("eloquence"), selectedEffectIds = setOf("clumsy-talk"))

        val hindranceOnly = sheetRollOptions(host, base)
        assertEquals(listOf("clumsy-talk" to "Косноязычие"), hindranceOnly.effectToggles)
        assertNull(hindranceOnly.blockedReason)
        val hindered = assertNotNull(performSheetRoll(host, base, dice(2, 5, 4)))
        assertEquals(RollMode.HINDRANCE, hindered.mode)
        assertEquals(setOf(0, 2), hindered.chosenIndices)

        val both = base.copy(mode = RollMode.ADVANTAGE)
        assertNotNull(sheetRollOptions(host, both).blockedReason)
        assertNull(performSheetRoll(host, both, dice(1, 2, 3, 4)))
    }

    @Test
    fun attackAgainstDefenceReportsOutcome() {
        val host = FakeSheetHost(hornet)
        val request = SheetRollRequest(
            SheetRollSubject.Context(RollContext.ATTACK),
            skillId = "melee_weapon",
            target = 20,
        )
        val options = sheetRollOptions(host, request)
        assertEquals(listOf("unarmed", "melee_weapon", "shooting", "throwing"), options.skillChoices)
        assertEquals(listOf(AttributeId.DEXTERITY, AttributeId.STRENGTH), options.attributeChoices)

        val result = assertNotNull(performSheetRoll(host, request, dice(3, 4)))
        val view = sheetRollView(options, request, result)
        val attack = resolveAttack(result, 20)

        assertEquals(7 + 8 + 4, result.total)
        assertEquals("${attack.outcome.title} (${if (attack.margin >= 0) "+" else ""}${attack.margin})", view.outcome)
    }

    @Test
    fun flurryUsesProfileRule() {
        val host = FakeSheetHost(hornet)
        val request = SheetRollRequest(
            SheetRollSubject.Context(RollContext.FLURRY),
            skillId = "melee_weapon",
            target = 15,
            flurryProfile = FlurryWeaponProfile.LIGHT,
        )
        val options = sheetRollOptions(host, request)

        val result = assertNotNull(performSheetRoll(host, request, dice(3, 4)))
        val view = sheetRollView(options, request, result)

        assertEquals(7 + 8 + 4 + FlurryWeaponProfile.LIGHT.rule(false).attackBonus, result.total)
        assertEquals(FlurryWeaponProfile.LIGHT.rule(false).resolve(result, 15).summary, view.outcome)
        assertTrue(FuryRollTerm("Шквал атак", 4) in view.terms)
    }

    @Test
    fun followUpAddsDie() {
        val host = FakeSheetHost(hornet)
        val request = SheetRollRequest(SheetRollSubject.Attribute(AttributeId.DEXTERITY))
        val result = assertNotNull(performSheetRoll(host, request, dice(3, 3)))
        assertEquals(RollFollowUp.ADVANTAGE_DIE, sheetFollowUp(request, result))

        val followed = followUpSheetRoll(result, dice(5))

        assertEquals(3, followed.dice.size)
        assertEquals(5 + 3 + 8, followed.total)
        assertNull(sheetFollowUp(request, followed))
    }

    @Test
    fun superiorityDieIsWithheldWhenNaturalSixesAlreadyHit() {
        val host = FakeSheetHost(hornet)
        val request = SheetRollRequest(SheetRollSubject.Context(RollContext.ATTACK), skillId = "melee_weapon", target = 20)
        val result = assertNotNull(performSheetRoll(host, request, dice(6, 6)))

        assertNull(sheetFollowUp(request, result))
        assertEquals(RollFollowUp.SUPERIORITY_DIE, sheetFollowUp(request.copy(target = 30), result))
    }

    @Test
    fun superiorityDieShowsAsTermAndSpecialResult() {
        val host = FakeSheetHost(hornet)
        val request = SheetRollRequest(SheetRollSubject.Skill("eloquence"), attribute = AttributeId.CHARISMA)
        val options = sheetRollOptions(host, request)
        val result = followUpSheetRoll(assertNotNull(performSheetRoll(host, request, dice(6, 6))), dice(4))

        val view = sheetRollView(options, request, result)

        assertEquals(12 + 3 + 2 + 4, view.total)
        assertTrue(FuryRollTerm("Кость превосходства", 4) in view.terms)
        assertEquals(result.specialResult?.title, view.specialResult)
        assertEquals("Красноречие + Харизма", result.checkBonusLabel)
    }

    @Test
    fun skillAndContextRemindersSurfaceLikeDesktop() {
        val catalog = SkillEffectCatalog(
            "test",
            listOf(
                SkillEffectDefinition(
                    "talk-reminder", 2, "Косноязычие", "Можно перебросить 1", "", "", "",
                    SkillEffectMode.REMINDER, RollContext.SKILL, listOf("Красноречие"), 0, false,
                    "", developmentId = "clumsy",
                ),
                SkillEffectDefinition(
                    "attack-reminder", 3, "Косноязычие", "Враг отвлечён", "", "", "",
                    SkillEffectMode.REMINDER, RollContext.ATTACK, emptyList(), 0, false,
                    "", developmentId = "clumsy",
                ),
            ),
        )
        val host = EffectHost(FakeSheetHost(hornet), catalog)

        val skill = sheetRollOptions(host, SheetRollRequest(SheetRollSubject.Skill("eloquence")))
        val flurry = sheetRollOptions(host, SheetRollRequest(SheetRollSubject.Context(RollContext.FLURRY), skillId = "melee_weapon"))
        val dodge = sheetRollOptions(host, SheetRollRequest(SheetRollSubject.Context(RollContext.DODGE)))

        assertEquals(listOf("Косноязычие: Можно перебросить 1"), skill.reminders)
        assertEquals(listOf("Косноязычие: Враг отвлечён"), flurry.reminders)
        assertEquals(emptyList<String>(), dodge.reminders)
    }

    @Test
    fun skillOptionsCarryEffectDescriptionsAndAutomaticEffects() {
        val catalog = SkillEffectCatalog(
            "test",
            listOf(
                SkillEffectDefinition(
                    "talk-auto", 4, "Косноязычие", "Всегда +2", "", "", "",
                    SkillEffectMode.AUTO_BONUS, RollContext.SKILL, listOf("Красноречие"), 2, false,
                    "", developmentId = "clumsy",
                ),
            ) + hindranceEffects.effects,
        )
        val host = EffectHost(FakeSheetHost(hornet), catalog)

        val skill = sheetRollOptions(host, SheetRollRequest(SheetRollSubject.Skill("eloquence")))
        val context = sheetRollOptions(host, SheetRollRequest(SheetRollSubject.Context(RollContext.DODGE)))

        assertEquals(mapOf("clumsy-talk" to "Помеха на Красноречие"), skill.effectDescriptions)
        assertEquals(listOf("Косноязычие +2"), skill.automaticEffects)
        assertEquals(emptyMap<String, String>(), context.effectDescriptions)
        assertEquals(emptyList<String>(), context.automaticEffects)
    }

    @Test
    fun formulaLineShowsPresetBonusAndFormulaBeforeRolling() {
        val host = FakeSheetHost(hornet)
        val request = SheetRollRequest(SheetRollSubject.Attribute(AttributeId.DEXTERITY))
        val preset = host.active.rollPreset(RollContext.ATTRIBUTE, attribute = AttributeId.DEXTERITY)

        assertEquals("Бонус проверки +${preset.bonus} · ${preset.formulaText}", sheetRollFormulaLine(host, request))
    }

    @Test
    fun formulaLineFollowsFlurryProfileAndAttributeChoices() {
        val host = FakeSheetHost(hornet)
        val light = SheetRollRequest(SheetRollSubject.Context(RollContext.FLURRY), skillId = "melee_weapon")
        val heavy = light.copy(flurryProfile = FlurryWeaponProfile.entries.last())
        val lightBonus = sheetRollOptions(host, light).preset.bonus!!
        val heavyBonus = sheetRollOptions(host, heavy).preset.bonus!!

        val lightLine = assertNotNull(sheetRollFormulaLine(host, light))
        assertTrue(lightLine.startsWith("Бонус проверки +$lightBonus · "), lightLine)
        assertTrue(lightLine.endsWith(" + Шквал атак"), lightLine)
        assertTrue(assertNotNull(sheetRollFormulaLine(host, heavy)).startsWith("Бонус проверки +$heavyBonus"))
        assertTrue(assertNotNull(sheetRollFormulaLine(host, light.copy(shortFlurry = true))).endsWith(" + Короткий шквал"))

        val skill = SheetRollRequest(SheetRollSubject.Skill("eloquence"))
        val withDexterity = assertNotNull(sheetRollFormulaLine(host, skill.copy(attribute = AttributeId.DEXTERITY)))
        assertTrue(withDexterity != sheetRollFormulaLine(host, skill), withDexterity)
    }

    @Test
    fun formulaLineAddsToggledEffectBonus() {
        val catalog = SkillEffectCatalog(
            "test",
            listOf(
                SkillEffectDefinition(
                    "talk-bonus", 5, "Косноязычие", "+3 по желанию", "", "", "",
                    SkillEffectMode.TOGGLE_BONUS, RollContext.SKILL, listOf("Красноречие"), 3, false,
                    "Подготовка", developmentId = "clumsy",
                ),
            ),
        )
        val host = EffectHost(FakeSheetHost(hornet), catalog)
        val request = SheetRollRequest(SheetRollSubject.Skill("eloquence"))
        val bonus = sheetRollOptions(host, request).preset.bonus!!

        val plain = assertNotNull(sheetRollFormulaLine(host, request))
        val toggled = assertNotNull(sheetRollFormulaLine(host, request.copy(selectedEffectIds = setOf("talk-bonus"))))

        assertTrue(plain.startsWith("Бонус проверки ${if (bonus >= 0) "+" else ""}$bonus · "), plain)
        assertTrue(toggled.startsWith("Бонус проверки ${if (bonus + 3 >= 0) "+" else ""}${bonus + 3} · "), toggled)
        assertTrue(toggled.endsWith(" + Эффекты (+3)"), toggled)
    }

    @Test
    fun formulaLineIsAbsentWithoutABonus() {
        val host = FakeSheetHost(hornet)
        assertNull(sheetRollFormulaLine(host, SheetRollRequest(SheetRollSubject.Skill("no-such-skill"))))
    }

    @Test
    fun situationalInputIsTypeableAndClamped() {
        assertEquals("" to 0, sheetSituationalInput(""))
        assertEquals("-" to 0, sheetSituationalInput("-"))
        assertEquals("-7" to -7, sheetSituationalInput("-7"))
        assertEquals("99" to 99, sheetSituationalInput("150"))
        assertEquals("-99" to -99, sheetSituationalInput("-999"))
        assertEquals("12" to 12, sheetSituationalInput("1a2"))
        assertEquals("5" to 5, sheetSituationalInput("5-"))
    }

    @Test
    fun rollButtonLabelMatchesTray() {
        assertEquals("Бросить", sheetRollButtonLabel(rolled = false))
        assertEquals("Бросить снова", sheetRollButtonLabel(rolled = true))
    }

    private val sneaky = hornet.copy(
        skills = hornet.skills + ("stealth" to CharacterSkill(id = "stealth", definitionId = "stealth", rank = 3, attributes = listOf(AttributeId.DEXTERITY, AttributeId.PERCEPTION))),
    )

    @Test
    fun skillRollOffersAllowedThenOthers() {
        val options = sheetRollOptions(FakeSheetHost(sneaky), SheetRollRequest(SheetRollSubject.Skill("stealth")))
        assertEquals(listOf(AttributeId.DEXTERITY, AttributeId.PERCEPTION), options.attributeChoices)
        assertEquals(AttributeId.entries - setOf(AttributeId.DEXTERITY, AttributeId.PERCEPTION), options.otherAttributeChoices)
    }

    @Test
    fun rollStartsOnAllowedPreferredOrStock() {
        val skill = sneaky.resolveSkill("stealth")!!
        val preferred = com.furybook.dubl.model.CharacterSheetExtras(preferredSkillAttributes = mapOf("stealth" to AttributeId.PERCEPTION))
        assertEquals(AttributeId.PERCEPTION, sheetRememberedAttribute(skill, preferred))
        val stale = com.furybook.dubl.model.CharacterSheetExtras(preferredSkillAttributes = mapOf("stealth" to AttributeId.STRENGTH))
        assertEquals(skill.stockAttribute, sheetRememberedAttribute(skill, stale))
        val host = FakeSheetHost(sneaky, preferred)
        assertTrue(sheetRollFormulaLine(host, SheetRollRequest(SheetRollSubject.Skill("stealth")))!!.contains("Восприятие"))
    }

    @Test
    fun onlyAllowedPicksAreRemembered() {
        val skill = sneaky.resolveSkill("stealth")!!
        assertTrue(sheetRemembersAttribute(skill, AttributeId.PERCEPTION))
        assertEquals(false, sheetRemembersAttribute(skill, AttributeId.STRENGTH))
    }

    @Test
    fun oneOffAttributeStillRolls() {
        val line = sheetRollFormulaLine(FakeSheetHost(sneaky), SheetRollRequest(SheetRollSubject.Skill("stealth"), attribute = AttributeId.STRENGTH))!!
        assertTrue("Сила" in line, line)
    }
}
