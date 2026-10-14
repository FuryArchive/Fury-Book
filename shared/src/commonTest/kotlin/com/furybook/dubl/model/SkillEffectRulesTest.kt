package com.furybook.dubl.model

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.Test

class SkillEffectRulesTest {
    private fun development(id: String, name: String, maxRank: Int = 3) = DevelopmentEntry(
        id = id, name = name, section = "Навыки", category = "Тест", cost = 10,
        costType = DevelopmentCostType.XP, maxRank = maxRank, requirements = "-", benefit = "",
        notes = "", tags = emptyList(), accessId = null, abilityOptions = emptyList(), incomplete = false,
        repeatable = false, perfectRoot = false, mechanicsConflict = "", conflictNote = "",
    )

    @Test
    fun autoAndSituationalEffectsResolveFromOwnedDevelopmentRanks() {
        val athletics = development("athletic", "Атлетичность", 2)
        val diplomat = development("diplomat", "Дипломат", 3)
        val catalog = DevelopmentCatalog("test", listOf(athletics, diplomat))
        val character = DublCharacter(
            id = "effects",
            skills = mapOf(
                "athletics" to CharacterSkill(id = "athletics", definitionId = "athletics", rank = 4),
                "eloquence" to CharacterSkill(id = "eloquence", definitionId = "eloquence", rank = 4),
            ),
            development = mapOf(
                athletics.id to OwnedDevelopment(2),
                diplomat.id to OwnedDevelopment(3),
            ),
        )
        val effects = SkillEffectCatalog(
            "test",
            listOf(
                SkillEffectDefinition("e1", 4, "Атлетичность", "", "", "0.5 · EXISTING", "", SkillEffectMode.AUTO_BONUS, RollContext.SKILL, listOf("Атлетика"), 1, true, "Атлетичность"),
                SkillEffectDefinition("e2", 74, "Дипломат", "", "", "0.5 · EXISTING", "", SkillEffectMode.TOGGLE_BONUS, RollContext.SKILL, listOf("Красноречие"), 1, true, "Дипломат — пытаюсь договориться"),
            ),
        )
        val rules = SkillEffectRules(character, catalog, effects)

        val athleticsResult = rules.forSkill(character.resolveSkill("athletics")!!)
        assertEquals(2, athleticsResult.automaticBonus)
        assertTrue(athleticsResult.automaticContributions.any { it.label == "Атлетичность" && it.value == 2 })

        val eloquenceResult = rules.forSkill(character.resolveSkill("eloquence")!!)
        assertEquals(0, eloquenceResult.automaticBonus)
        assertEquals(1, eloquenceResult.options.size)
        assertEquals(3, eloquenceResult.options.single().numericBonus)
        assertEquals("Дипломат — пытаюсь договориться", eloquenceResult.options.single().label)
    }

    @Test
    fun advantageToggleAddsDiceEffectNotNumericBonus() {
        val perk = development("sweet", "Сладкие речи")
        val catalog = DevelopmentCatalog("test", listOf(perk))
        val character = DublCharacter(
            id = "adv",
            skills = mapOf("eloquence" to CharacterSkill(id = "eloquence", definitionId = "eloquence", rank = 4)),
            development = mapOf(perk.id to OwnedDevelopment(1)),
        )
        val effectCatalog = SkillEffectCatalog(
            "test",
            listOf(SkillEffectDefinition("e", 77, "Сладкие речи", "", "", "0.5 · EXISTING", "", SkillEffectMode.TOGGLE_ADVANTAGE, RollContext.SKILL, listOf("Красноречие"), 0, false, "Вру")),
        )
        val option = SkillEffectRules(character, catalog, effectCatalog).forSkill(character.resolveSkill("eloquence")!!).options.single()
        assertEquals(1, option.advantageDice)
        assertEquals(0, option.numericBonus)
    }

    @Test fun strength_conditions_are_attribute_scoped_and_do_not_leak_after_switching() {
        val strong = "feat_c35ba86389f61a11"; val hauler = "feat_a144256fd7c09aeb"
        val effects = SkillEffectCatalog("test", listOf(strong, hauler).mapIndexed { i, id ->
            SkillEffectDefinition("strength-$i", i, "Source", "Condition", "", "", "", SkillEffectMode.REMINDER,
                RollContext.ATTRIBUTE, emptyList(), 0, false, "", id)
        })
        val c = DublCharacter("strength", development = mapOf(strong to OwnedDevelopment(1), hauler to OwnedDevelopment(2)))
        val rules = SkillEffectRules(c, DevelopmentCatalog("test", emptyList()), effects)
        val choices = rules.contextOptions(RollContext.ATTRIBUTE, AttributeId.STRENGTH)
        assertEquals(2, choices.size)
        assertEquals(SkillRollEffectTotals(2, 1, 0), choices.selectedTotals(choices.map { it.id }.toSet()))
        assertEquals(SkillRollEffectTotals(), choices.selectedTotals(emptySet()))
        assertTrue(rules.contextOptions(RollContext.ATTRIBUTE, AttributeId.DEXTERITY).isEmpty())
        assertTrue(rules.contextOptions(RollContext.ATTRIBUTE).isEmpty())
        assertTrue(rules.contextOptions(RollContext.ATTACK, AttributeId.STRENGTH).isEmpty())
        assertTrue(rules.contextOptions(RollContext.BREAK_ITEM, AttributeId.DEXTERITY).isEmpty())
        assertEquals("strength-0", rules.contextOptions(RollContext.BREAK_ITEM, AttributeId.STRENGTH).single().id)
        val disabled = SkillEffectRules(c.copy(disabledSkillEffectIds = setOf("strength-0", "strength-1")),
            DevelopmentCatalog("test", emptyList()), effects)
        assertTrue(disabled.contextOptions(RollContext.ATTRIBUTE, AttributeId.STRENGTH).isEmpty())
    }

    @Test fun mental_resistance_and_repeat_are_opt_in_not_automatic_actions() {
        val ids = listOf("feat_5a681a34327a203e", "feat_8cd438317819c497")
        val effects = SkillEffectCatalog("test", ids.mapIndexed { i, id ->
            SkillEffectDefinition("context-$i", i, "Source", "Condition", "", "", "", SkillEffectMode.REMINDER,
                null, emptyList(), 0, false, "", id)
        })
        val c = DublCharacter("context", development = ids.associateWith { OwnedDevelopment(1) })
        val rules = SkillEffectRules(c, DevelopmentCatalog("test", emptyList()), effects)
        val options = rules.forSkill(c.resolveSkill("awareness")!!).options
        assertEquals(2, options.size)
        assertEquals(SkillRollEffectTotals(), options.selectedTotals(emptySet()))
        assertEquals(1, options.selectedTotals(setOf("context-1")).advantageDice)
        assertTrue(options.last().label.contains("не даёт новой попытки"))
        assertEquals(options, rules.contextOptions(RollContext.FORTITUDE))
        assertTrue(SkillEffectRules(c.copy(development = emptyMap()), DevelopmentCatalog("test", emptyList()), effects)
            .contextOptions(RollContext.FORTITUDE).isEmpty())
    }
}
