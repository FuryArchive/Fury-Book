package com.furybook.dubl.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ContextualRetryEffectsTest {
    private val barter = "feat_dfb7c30574749cae"
    private val lock = "feat_fe40de7bd9514242"
    private val archive = "feat_fde4317b9b9ab7cd"
    private val pet = "feat_72094d2dcfe2baa4"
    private val pack = "feat_410d23864293c612"
    private val sprint = "feat_7321f6d55d019f93"
    private val ids = listOf(barter, lock, archive, pet, pack, sprint)
    private val effects = SkillEffectCatalog("test", ids.mapIndexed { i, id ->
        val targets = when (id) {
            barter -> listOf("Бартер")
            lock -> listOf("Взлом")
            archive -> listOf("Знание")
            else -> emptyList()
        }
        SkillEffectDefinition("effect-$id", i, id, "Source condition", "", "", "",
            SkillEffectMode.REMINDER, if (targets.isNotEmpty()) RollContext.SKILL else null,
            targets, 0, false, "", id)
    })
    private val character = DublCharacter("retry", skills = mapOf(
        "renamed" to CharacterSkill(id = "renamed", definitionId = "barter", name = "Другое имя", rank = 6),
        "lock" to CharacterSkill(id = "lock", definitionId = "lockpicking", rank = 5),
        "knowledge" to CharacterSkill(id = "knowledge", definitionId = "knowledge_academic", rank = 4),
        "medicine" to CharacterSkill(id = "medicine", definitionId = "medicine", rank = 4),
    ), development = ids.associateWith { OwnedDevelopment(if (it == sprint) 2 else 1) })
    private fun rules(c: DublCharacter = character) = SkillEffectRules(c, DevelopmentCatalog("test", emptyList()), effects)

    @Test fun retry_choices_are_scoped_and_opt_in() {
        val cases = listOf("renamed" to SkillRollEffectTotals(-2, 0, 0),
            "lock" to SkillRollEffectTotals(0, 0, 1), "knowledge" to SkillRollEffectTotals(0, 1, 0))
        cases.forEach { (skill, expected) ->
            val result = rules().forSkill(character.resolveSkill(skill)!!)
            assertEquals(0, result.automaticBonus)
            assertEquals(1, result.options.size)
            assertEquals(SkillRollEffectTotals(), result.options.selectedTotals(emptySet()))
            assertEquals(expected, result.options.selectedTotals(result.options.map { it.id }.toSet()))
        }
        assertTrue(rules().forSkill(character.resolveSkill("medicine")!!).options.isEmpty())
        assertEquals(6, character.skills.getValue("renamed").rank)
        assertTrue(rules().contextOptions(RollContext.ATTRIBUTE, AttributeId.INTELLIGENCE).isEmpty())
    }

    @Test fun no_effect_without_ownership_or_when_disabled() {
        listOf(character.copy(development = emptyMap()),
            character.copy(disabledSkillEffectIds = effects.effects.map { it.id }.toSet())).forEach { c ->
            assertTrue(rules(c).forSkill(c.resolveSkill("renamed")!!).options.isEmpty())
            assertTrue(rules(c).forSkill(c.resolveSkill("lock")!!).options.isEmpty())
            assertTrue(rules(c).forSkill(c.resolveSkill("knowledge")!!).options.isEmpty())
            assertTrue(rules(c).contextOptions(RollContext.ATTACK).isEmpty())
            assertTrue(rules(c).contextOptions(RollContext.RUN).isEmpty())
        }
    }

    @Test fun pet_and_pack_are_attack_only_and_sprint_is_run_only() {
        listOf(RollContext.ATTACK, RollContext.FLURRY).forEach { context ->
            val choices = rules().contextOptions(context)
            assertEquals(setOf("effect-$pet", "effect-$pack"), choices.map { it.id }.toSet())
            assertEquals(SkillRollEffectTotals(1, 1, 0), choices.selectedTotals(choices.map { it.id }.toSet()))
            assertEquals(SkillRollEffectTotals(), choices.selectedTotals(emptySet()))
        }
        val run = rules().contextOptions(RollContext.RUN).single()
        assertEquals("effect-$sprint", run.id)
        assertEquals(2, run.numericBonus)
        assertEquals(0, run.advantageDice)
        assertTrue(run.label.contains("спринт"))
        assertTrue(rules().contextOptions(RollContext.BREAK_ITEM).isEmpty())
        assertTrue(rules().contextOptions(RollContext.PARRY).isEmpty())
        assertTrue(rules().contextOptions(RollContext.SKILL).isEmpty())
    }
}
