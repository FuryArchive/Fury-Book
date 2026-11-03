package com.furybook.dubl.model

import kotlin.test.Test
import kotlin.test.assertEquals

class RulebookRequirementParsingTest {
    private fun entry(
        id: String,
        name: String,
        requirements: String = "-",
        category: String = "Особые навыки",
    ) = DevelopmentEntry(
        id = id,
        name = name,
        section = "Тест",
        category = category,
        cost = 10,
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

    private fun statuses(character: DublCharacter, catalog: DevelopmentCatalog, entry: DevelopmentEntry) =
        DevelopmentRules(character, catalog, DevelopmentProgress(character.development))
            .requirements(entry)
            .map { it.status }

    private fun spell(uid: String, name: String, description: String = "") =
        KnownSpell(uid = uid, name = name, school = "Разрушение", cost = 1, description = description)

    @Test
    fun spellDescriptorRequirementLooksAtSpellText() {
        val pyromancer = entry("pyro", "Пиромант", "Знать 3 заклинания с дескриптором “огонь”.")
        val catalog = DevelopmentCatalog("test", listOf(pyromancer))
        fun mage(vararg spells: KnownSpell) = DublCharacter(id = "m", magic = CharacterMagic(spells = spells.toList()))

        val fire = mage(
            spell("a", "Огненный шар"),
            spell("b", "Огненная стена"),
            spell("c", "Вспышка", "Наносит урон огнем и вызывает горение."),
        )
        assertEquals(listOf(RequirementStatus.OK), statuses(fire, catalog, pyromancer))

        val mind = mage(spell("a", "Внушение"), spell("b", "Страх"), spell("c", "Сон"))
        assertEquals(listOf(RequirementStatus.MANUAL), statuses(mind, catalog, pyromancer))

        val tooFew = mage(spell("a", "Огненный шар"), spell("b", "Огненная стена"))
        assertEquals(listOf(RequirementStatus.FAIL), statuses(tooFew, catalog, pyromancer))
    }

    @Test
    fun specialisedSkillRequirementsAcceptRulebookSpellings() {
        val repairman = entry("repair", "Сервисный работник", "Ремонт 3")
        val initiate = entry("initiate", "Посвященный", "Знание: Магия 3")
        val catalog = DevelopmentCatalog("test", listOf(repairman, initiate))
        val character = DublCharacter(
            id = "t",
            skills = mapOf(
                "engineering_repair" to CharacterSkill(id = "engineering_repair", definitionId = "engineering_repair", rank = 3),
                "k1" to CharacterSkill(id = "k1", definitionId = "knowledge_template", name = "Знание (Магия)", rank = 3),
            ),
        )

        assertEquals(listOf(RequirementStatus.OK), statuses(character, catalog, repairman))
        assertEquals(listOf(RequirementStatus.OK), statuses(character, catalog, initiate))
    }

    @Test
    fun onlyOneChiSchoolCanBeLearned() {
        val water = entry("water", "Школа Текущей Воды", category = "Школы ЦИ")
        val mountain = entry("mountain", "Школа Неподвижной Горы", category = "Школы ЦИ")
        val heron = entry("heron", "Полёт цапли", category = "Основы ЦИ")
        val catalog = DevelopmentCatalog("test", listOf(water, mountain, heron))
        val student = DublCharacter(id = "c", development = mapOf("water" to OwnedDevelopment(1)))

        assertEquals(listOf(RequirementStatus.OK), statuses(student, catalog, water))
        assertEquals(listOf(RequirementStatus.FAIL), statuses(student, catalog, mountain))
        assertEquals(listOf(RequirementStatus.OK), statuses(student, catalog, heron))

        val plan = DevelopmentAcquisitionPlanner(student, catalog).plan(DevelopmentAcquisitionRequest.single("mountain"))
        assertEquals(false, plan.canApply)
    }
}
