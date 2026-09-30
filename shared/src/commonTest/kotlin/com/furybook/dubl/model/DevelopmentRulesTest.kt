package com.furybook.dubl.model

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.Test

class DevelopmentRulesTest {
    private val access = DevelopmentEntry(
        id = "access_assassin",
        name = "Ассасин",
        section = "Особые способности",
        category = "Особые способности",
        cost = 0,
        costType = DevelopmentCostType.ABILITY,
        maxRank = 1,
        requirements = "Скрытность 4",
        benefit = "Открывает ветку",
        notes = "",
        tags = emptyList(),
        accessId = null,
        abilityOptions = listOf(AbilityOption("Мастерство", 1)),
        incomplete = false,
        repeatable = false,
        perfectRoot = false,
        mechanicsConflict = "",
        conflictNote = "",
    )

    private val child = DevelopmentEntry(
        id = "feat_sneak_attack",
        name = "Подлая атака",
        section = "Ветки способностей",
        category = "Ассасин",
        cost = 40,
        costType = DevelopmentCostType.XP,
        maxRank = 2,
        requirements = "Ассасин, Ловкость 4",
        benefit = "Тест",
        notes = "",
        tags = listOf("Ассасин"),
        accessId = access.id,
        abilityOptions = emptyList(),
        incomplete = false,
        repeatable = false,
        perfectRoot = false,
        mechanicsConflict = "",
        conflictNote = "",
    )

    private val catalog = DevelopmentCatalog("test", listOf(access, child))

    private fun character(stealthRank: Int = 0, dexterity: Int = 0, xp: Int = 1000): DublCharacter {
        val skills = if (stealthRank > 0) {
            mapOf("stealth" to CharacterSkill(id = "stealth", definitionId = "stealth", rank = stealthRank))
        } else emptyMap()
        return DublCharacter(
            id = "test",
            experience = xp,
            attributes = defaultAttributes() + (
                AttributeId.DEXTERITY to AttributeValue(base = dexterity)
            ),
            skills = skills,
        )
    }

    @Test
    fun abilityUsesExperienceBudgetAndRequirements() {
        val rules = DevelopmentRules(character(stealthRank = 4), catalog, DevelopmentProgress())
        assertEquals(1, rules.abilityPointsBudget())
        assertEquals(1, rules.abilityPointsAvailable())
        assertTrue(rules.availability(access).canIncrease)
    }

    @Test
    fun childRequiresBranchAndAttribute() {
        val before = DevelopmentRules(character(stealthRank = 4, dexterity = 4), catalog, DevelopmentProgress())
        assertFalse(before.availability(child).canIncrease)
        assertTrue(before.requirements(child).any { it.text.contains("Доступ к ветке") && it.status == RequirementStatus.FAIL })

        val ownedAccess = DevelopmentProgress().withRank(access, 1, 0)
        val after = DevelopmentRules(character(stealthRank = 4, dexterity = 4), catalog, ownedAccess)
        assertTrue(after.availability(child).canIncrease)
    }

    @Test
    fun alreadyOwnedEntryIsNotDeletedWhenRequirementLaterFails() {
        var progress = DevelopmentProgress().withRank(access, 1, 0)
        progress = progress.withRank(child, 1)
        val rules = DevelopmentRules(character(stealthRank = 4, dexterity = 2), catalog, progress)
        assertEquals(1, progress.rank(child.id))
        assertTrue(rules.requirements(child).any { it.status == RequirementStatus.FAIL })
    }

    @Test
    fun abilityPointsReturnWhenAbilityIsRemoved() {
        val owned = DevelopmentProgress().withRank(access, 1, 0)
        val spent = DevelopmentRules(character(stealthRank = 4), catalog, owned)
        assertEquals(1, spent.abilityPointsSpent())
        assertEquals(0, spent.abilityPointsAvailable())

        val removed = owned.withRank(access, 0)
        val restored = DevelopmentRules(character(stealthRank = 4), catalog, removed)
        assertEquals(0, restored.abilityPointsSpent())
        assertEquals(1, restored.abilityPointsAvailable())
    }
    @Test
    fun creationOnlyRequirementClosesAfterCreation() {
        val creationOnly = child.copy(
            id = "creation-only",
            name = "Природная красота",
            accessId = null,
            requirements = "Ловкость 4, только при создании",
        )
        val localCatalog = DevelopmentCatalog("test", listOf(creationOnly))

        val duringCreation = character(dexterity = 4).copy(creationComplete = false)
        val openRules = DevelopmentRules(duringCreation, localCatalog, DevelopmentProgress())
        assertTrue(openRules.availability(creationOnly).canIncrease)

        val completed = duringCreation.copy(creationComplete = true, creationExperience = duringCreation.experience)
        val closedRules = DevelopmentRules(completed, localCatalog, DevelopmentProgress())
        assertFalse(closedRules.availability(creationOnly).canIncrease)
        assertTrue(closedRules.requirements(creationOnly).any { it.text.contains("создание уже завершено") })

        val ownedProgress = DevelopmentProgress().withRank(creationOnly, 1)
        val ownedRules = DevelopmentRules(completed, localCatalog, ownedProgress)
        assertTrue(ownedRules.requirements(creationOnly).all { it.status == RequirementStatus.OK })
    }

    @Test
    fun abilityPointOverspendIsWarningStateNotHardPurchaseBlock() {
        val expensive = access.copy(abilityOptions = listOf(AbilityOption("Мастерство", 2)))
        val localCatalog = DevelopmentCatalog("test", listOf(expensive))
        val onePointCharacter = character(stealthRank = 4, xp = 1000)
        val localRules = DevelopmentRules(onePointCharacter, localCatalog, DevelopmentProgress())

        assertEquals(1, localRules.abilityPointsBudget())
        assertTrue(localRules.availability(expensive).canIncrease)

        val overspent = DevelopmentProgress().withRank(expensive, 1, 0)
        val spentRules = DevelopmentRules(onePointCharacter, localCatalog, overspent)
        assertEquals(-1, spentRules.abilityPointsAvailable())
    }

    @Test
    fun developmentEntriesSeparateRegularAndSpecialBranches() {
        val regular = child.copy(
            id = "regular",
            name = "Крепкий хват",
            section = "Навыки",
            category = "Общие",
            accessId = null,
        )
        val perfect = child.copy(
            id = "perfect",
            name = "Ассасин",
            section = "Ветки способностей",
            category = "Ассасин",
            accessId = null,
            perfectRoot = true,
        )

        assertTrue(regular.isRegularDevelopment)
        assertFalse(regular.isSpecialDevelopment)
        assertTrue(access.isSpecialDevelopment)
        assertTrue(child.isSpecialDevelopment)
        assertTrue(perfect.isSpecialDevelopment)
        assertFalse(perfect.isRegularDevelopment)
    }

    @Test
    fun orRequirementAcceptsBareCombatSkillShorthand() {
        val either = child.copy(
            id = "either-combat-skill",
            name = "Тест ИЛИ",
            accessId = null,
            requirements = "Рукопашный ИЛИ Холодное",
        )
        val localCatalog = DevelopmentCatalog("test", listOf(either))

        val unarmedCharacter = character().copy(
            skills = mapOf(
                "unarmed" to CharacterSkill(id = "unarmed", definitionId = "unarmed", rank = 1),
            ),
        )
        val unarmedRules = DevelopmentRules(unarmedCharacter, localCatalog, DevelopmentProgress())
        assertTrue(unarmedRules.availability(either).canIncrease)
        assertTrue(unarmedRules.requirements(either).all { it.status == RequirementStatus.OK })

        val meleeCharacter = character().copy(
            skills = mapOf(
                "melee_weapon" to CharacterSkill(id = "melee_weapon", definitionId = "melee_weapon", rank = 1),
            ),
        )
        val meleeRules = DevelopmentRules(meleeCharacter, localCatalog, DevelopmentProgress())
        assertTrue(meleeRules.availability(either).canIncrease)
        assertTrue(meleeRules.requirements(either).all { it.status == RequirementStatus.OK })

        val missingRules = DevelopmentRules(character(), localCatalog, DevelopmentProgress())
        assertFalse(missingRules.availability(either).canIncrease)
        assertTrue(missingRules.requirements(either).all { it.status != RequirementStatus.MANUAL })
    }

    @Test
    fun orRequirementPropagatesTrailingRankToEveryAlternative() {
        val either = child.copy(
            id = "either-ranked-combat-skill",
            name = "Тест ИЛИ ранга",
            accessId = null,
            requirements = "Холодное оружие или Рукопашный бой 4",
        )
        val localCatalog = DevelopmentCatalog("test", listOf(either))
        val unarmedCharacter = character().copy(
            skills = mapOf(
                "unarmed" to CharacterSkill(id = "unarmed", definitionId = "unarmed", rank = 4),
            ),
        )
        val rules = DevelopmentRules(unarmedCharacter, localCatalog, DevelopmentProgress())
        assertTrue(rules.availability(either).canIncrease)
        assertTrue(rules.requirements(either).all { it.status == RequirementStatus.OK })
    }

    @Test
    fun unmetRequirementsCanBeForcePurchasedButRemainInvalid() {
        val locked = child.copy(
            id = "forceable",
            name = "Принудительная покупка",
            accessId = null,
            requirements = "Ловкость 8",
        )
        val localCatalog = DevelopmentCatalog("test", listOf(locked))
        val baseCharacter = character(dexterity = 2)
        val before = DevelopmentRules(baseCharacter, localCatalog, DevelopmentProgress())

        assertFalse(before.availability(locked).canIncrease)
        assertTrue(before.availability(locked).canForceIncrease)

        val forcedProgress = DevelopmentProgress().withRank(locked, 1)
        val after = DevelopmentRules(baseCharacter, localCatalog, forcedProgress)
        assertEquals(1, forcedProgress.rank(locked.id))
        assertTrue(after.requirements(locked).any { it.status == RequirementStatus.FAIL })
        assertFalse(after.availability(locked).canIncrease)
        assertTrue(after.availability(locked).canForceIncrease)
    }

    @Test
    fun ownedSheetOrderSeparatesRegularAndSpecialAndNestsByFirstRequirement() {
        val parry = child.copy(
            id = "parry",
            name = "Парирование",
            section = "Навыки",
            category = "Защита",
            accessId = null,
            requirements = "-",
        )
        val riposte = child.copy(
            id = "riposte",
            name = "Рипост",
            section = "Навыки",
            category = "Защита",
            accessId = null,
            requirements = "Парирование",
        )
        val grip = child.copy(
            id = "grip",
            name = "Крепкий хват",
            section = "Навыки",
            category = "Общие",
            accessId = null,
            requirements = "-",
        )
        val complex = child.copy(
            id = "complex",
            name = "Сложная техника",
            section = "Навыки",
            category = "Защита",
            accessId = null,
            requirements = "Парирование, Крепкий хват",
        )
        val localCatalog = DevelopmentCatalog("test", listOf(parry, riposte, grip, complex, access, child))
        val progress = DevelopmentProgress(
            mapOf(
                parry.id to OwnedDevelopment(rank = 1),
                riposte.id to OwnedDevelopment(rank = 1),
                grip.id to OwnedDevelopment(rank = 1),
                complex.id to OwnedDevelopment(rank = 1),
                access.id to OwnedDevelopment(rank = 1),
                child.id to OwnedDevelopment(rank = 1),
            )
        )
        val rules = DevelopmentRules(character(stealthRank = 4, dexterity = 4), localCatalog, progress)
        val sections = rules.ownedSheetSections()

        assertEquals(listOf(DevelopmentSheetSectionType.REGULAR, DevelopmentSheetSectionType.SPECIAL), sections.map { it.type })
        val regular = sections.first().items
        assertEquals(4, regular.size)
        assertEquals(4, regular.map { it.entry.id }.distinct().size)
        assertTrue(regular.indexOfFirst { it.entry.id == parry.id } < regular.indexOfFirst { it.entry.id == riposte.id })
        assertEquals(1, regular.first { it.entry.id == riposte.id }.depth)
        assertEquals(1, regular.first { it.entry.id == complex.id }.depth)
        assertEquals(parry.id, regular.first { it.entry.id == complex.id }.parentId)

        val special = sections.last().items
        assertEquals(access.id, special.first().entry.id)
        assertEquals(child.id, special[1].entry.id)
        assertEquals(1, special[1].depth)
    }

    @Test
    fun martialArtsAreASeparateDevelopmentDomain() {
        val martial = child.copy(
            id = "martial-aikido",
            name = "Айкидо",
            section = "Боевые искусства",
            category = "Рукопашные",
            costType = DevelopmentCostType.XP,
            accessId = null,
            tags = listOf("Боевые искусства", "Боевой стиль"),
        )

        assertTrue(martial.isMartialArt)
        assertFalse(martial.isRegularDevelopment)
        assertFalse(martial.isSpecialDevelopment)
    }

    @Test
    fun martialArtAlternativeListRequiresAnyOneListedStyle() {
        val aikido = child.copy(
            id = "martial-aikido",
            name = "Айкидо",
            section = "Боевые искусства",
            category = "Рукопашные",
            costType = DevelopmentCostType.XP,
            accessId = null,
            requirements = "-",
            tags = listOf("Боевые искусства", "Боевой стиль"),
        )
        val hapkido = aikido.copy(id = "martial-hapkido", name = "Хапкидо")
        val monkey = aikido.copy(id = "martial-monkey", name = "Обезьяна")
        val technique = child.copy(
            id = "martial-throw",
            name = "Импульсный бросок",
            section = "Боевые искусства",
            category = "Общие приёмы",
            costType = DevelopmentCostType.XP,
            accessId = null,
            requirements = "Боевые искусства: Айкидо, Хапкидо, Обезьяна или Ниндзюцу",
            tags = listOf("Боевые искусства", "Приём"),
        )
        val localCatalog = DevelopmentCatalog("test", listOf(aikido, hapkido, monkey, technique))
        val progress = DevelopmentProgress(mapOf(hapkido.id to OwnedDevelopment(rank = 1)))
        val rules = DevelopmentRules(character(), localCatalog, progress)

        assertTrue(rules.availability(technique).canIncrease)
        assertTrue(rules.requirements(technique).all { it.status == RequirementStatus.OK })
    }

    @Test
    fun anyMartialArtRequirementAcceptsAnyOwnedStyle() {
        val boxing = child.copy(
            id = "martial-boxing",
            name = "Бокс",
            section = "Боевые искусства",
            category = "Рукопашные",
            costType = DevelopmentCostType.XP,
            accessId = null,
            requirements = "-",
            tags = listOf("Боевые искусства", "Боевой стиль"),
        )
        val escape = child.copy(
            id = "martial-escape",
            name = "Ускользание",
            section = "Боевые искусства",
            category = "Общие приёмы",
            costType = DevelopmentCostType.XP,
            accessId = null,
            requirements = "Боевые искусства (Любое)",
            tags = listOf("Боевые искусства", "Приём"),
        )
        val localCatalog = DevelopmentCatalog("test", listOf(boxing, escape))
        val progress = DevelopmentProgress(mapOf(boxing.id to OwnedDevelopment(rank = 1)))
        val rules = DevelopmentRules(character(), localCatalog, progress)

        assertTrue(rules.availability(escape).canIncrease)
        assertTrue(rules.requirements(escape).all { it.status == RequirementStatus.OK })
    }

    @Test
    fun martialArtAlternativeListDoesNotSwallowTrailingFeatRequirement() {
        val boxing = child.copy(
            id = "martial-boxing",
            name = "Бокс",
            section = "Боевые искусства",
            category = "Рукопашные",
            costType = DevelopmentCostType.XP,
            accessId = null,
            requirements = "-",
            tags = listOf("Боевые искусства", "Боевой стиль"),
        )
        val tiger = boxing.copy(id = "martial-tiger", name = "Тигр")
        val powerStrike = child.copy(
            id = "power-strike",
            name = "Мощный удар",
            section = "Навыки",
            category = "Бой",
            accessId = null,
            requirements = "-",
        )
        val technique = child.copy(
            id = "martial-body-blow",
            name = "Удары в корпус",
            section = "Боевые искусства",
            category = "Общие приёмы",
            costType = DevelopmentCostType.XP,
            accessId = null,
            requirements = "Боевые искусства: Бокс или Тигр, Мощный удар",
            tags = listOf("Боевые искусства", "Приём"),
        )
        val localCatalog = DevelopmentCatalog("test", listOf(boxing, tiger, powerStrike, technique))
        val missingFeat = DevelopmentRules(
            character(),
            localCatalog,
            DevelopmentProgress(mapOf(boxing.id to OwnedDevelopment(rank = 1))),
        )
        assertFalse(missingFeat.availability(technique).canIncrease)

        val complete = DevelopmentRules(
            character(),
            localCatalog,
            DevelopmentProgress(
                mapOf(
                    boxing.id to OwnedDevelopment(rank = 1),
                    powerStrike.id to OwnedDevelopment(rank = 1),
                )
            ),
        )
        assertTrue(complete.availability(technique).canIncrease)
    }


    @Test
    fun ownedMartialTechniqueIsNestedUnderOwnedStyle() {
        val boxing = child.copy(
            id = "martial-boxing-tree",
            name = "Бокс",
            section = "Боевые искусства",
            category = "Рукопашные",
            costType = DevelopmentCostType.XP,
            accessId = null,
            requirements = "-",
            tags = listOf("Боевые искусства", "Боевой стиль"),
        )
        val hook = child.copy(
            id = "martial-hook-tree",
            name = "Хук",
            section = "Боевые искусства",
            category = "Общие приёмы",
            costType = DevelopmentCostType.XP,
            accessId = null,
            requirements = "Боевые искусства: Бокс",
            tags = listOf("Боевые искусства", "Приём"),
        )
        val localCatalog = DevelopmentCatalog("test", listOf(boxing, hook))
        val progress = DevelopmentProgress(
            mapOf(
                boxing.id to OwnedDevelopment(rank = 1),
                hook.id to OwnedDevelopment(rank = 1),
            )
        )
        val section = DevelopmentRules(character(), localCatalog, progress)
            .ownedSheetSections()
            .single { it.type == DevelopmentSheetSectionType.MARTIAL_ARTS }

        assertEquals(listOf(boxing.id, hook.id), section.items.map { it.entry.id })
        assertEquals(boxing.id, section.items[1].parentId)
        assertEquals(1, section.items[1].depth)
    }


    @Test
    fun manaRequirementsAcceptRomanArabicAndHigherRanks() {
        val roman = child.copy(
            id = "mana-roman",
            name = "Великанская магия",
            accessId = null,
            requirements = "Запас маны (III)",
        )
        val baseArabic = child.copy(
            id = "mana-arabic",
            name = "Медитация",
            accessId = null,
            requirements = "Базовый запас маны 3",
        )
        val localCatalog = DevelopmentCatalog("test", listOf(roman, baseArabic))

        val rankFour = character().copy(magic = CharacterMagic(manaRank = 4))
        val rules = DevelopmentRules(rankFour, localCatalog, DevelopmentProgress())
        assertTrue(rules.requirements(roman).all { it.status == RequirementStatus.OK })
        assertTrue(rules.requirements(baseArabic).all { it.status == RequirementStatus.OK })
        assertEquals(4, rules.featureRank("Запас маны"))
        assertEquals(4, rules.featureRank("Базовый запас маны"))

        val rankTwo = DevelopmentRules(
            character().copy(magic = CharacterMagic(manaRank = 2)),
            localCatalog,
            DevelopmentProgress(),
        )
        assertTrue(rankTwo.requirements(roman).any { it.status == RequirementStatus.FAIL })
    }

    @Test
    fun acquisitionPlannerTreatsOwnedHigherManaRankAsSatisfied() {
        val giantMagic = child.copy(
            id = "giant-magic",
            name = "Великанская магия",
            accessId = null,
            requirements = "Запас маны (III)",
        )
        val localCatalog = DevelopmentCatalog("test", listOf(giantMagic))
        val rankFour = character().copy(magic = CharacterMagic(manaRank = 4))

        val plan = DevelopmentAcquisitionPlanner(rankFour, localCatalog).plan(
            DevelopmentAcquisitionRequest.single(
                entryId = giantMagic.id,
                includeTarget = false,
                enforceBudget = false,
            ),
        )

        assertTrue(plan.unresolvedRequirements.isEmpty())
        assertTrue(plan.steps.isEmpty())
    }

    @Test
    fun learnedMagicSchoolsAppearInSpecialSkillsSection() {
        val mage = character().copy(
            magic = CharacterMagic(
                schools = listOf(
                    MagicSchool(name = "Разрушение", rank = 5),
                    MagicSchool(name = "Ограждение", rank = 3),
                ),
            ),
        )
        val sections = DevelopmentRules(mage, DevelopmentCatalog("test", emptyList()), DevelopmentProgress())
            .ownedSheetSections()
        val special = sections.single { it.type == DevelopmentSheetSectionType.SPECIAL }

        assertEquals(listOf("Ограждение", "Разрушение"), special.items.map { it.entry.name })
        assertEquals(listOf(3, 5), special.items.map { it.rank })
        assertTrue(special.items.all { it.source == DevelopmentSheetItemSource.MAGIC_SCHOOL })
    }

}
