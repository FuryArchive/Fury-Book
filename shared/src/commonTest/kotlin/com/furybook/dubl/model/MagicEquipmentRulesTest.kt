package com.furybook.dubl.model

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.Test

class MagicEquipmentRulesTest {
    @Test
    fun manaUsesDesktopTableAndDevelopmentBonuses() {
        val character = DublCharacter(
            id = "magic",
            magic = CharacterMagic(manaRank = 2, power = 10),
            development = mapOf(
                MagicEquipmentRules.INCREASED_MANA_ENTRY_ID to OwnedDevelopment(rank = 3),
                MagicEquipmentRules.MEDITATION_ENTRY_ID to OwnedDevelopment(rank = 2),
            ),
        )
        assertEquals(31, MagicEquipmentRules.manaMaximum(character))
        assertEquals(3, MagicEquipmentRules.manaRecoveryPerRound(character))
    }

    @Test
    fun equipmentLoadAndBurdenMatchDesktopRules() {
        val attrs = defaultAttributes() + mapOf(
            AttributeId.STRENGTH to AttributeValue(base = 4),
            AttributeId.CONSTITUTION to AttributeValue(base = 3),
        )
        val character = DublCharacter(
            id = "gear",
            attributes = attrs,
            gear = CharacterGear(
                items = listOf(
                    GearItem(uid = "a", quantity = 2, load = 4.0, carried = true),
                    GearItem(uid = "b", quantity = 1, load = 99.0, carried = false),
                ),
            ),
        )
        assertEquals(8.0, MagicEquipmentRules.equipmentLoad(character), 0.001)
        assertEquals(7, MagicEquipmentRules.equipmentCapacity(character))
        assertEquals("Лёгкая нагрузка", MagicEquipmentRules.burden(character).title)
        assertEquals(-1, MagicEquipmentRules.burden(character).penalty)
    }

    @Test
    fun developmentRequirementsCanReadMagicState() {
        val entry = DevelopmentEntry(
            id = "magic-gated",
            name = "Магический тест",
            section = "Тест",
            category = "Тест",
            cost = 10,
            costType = DevelopmentCostType.XP,
            maxRank = 1,
            requirements = "Базовый запас маны 3, Сила магии 2, Знать 1 заклинание",
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
        val character = DublCharacter(
            id = "magic-req",
            magic = CharacterMagic(
                manaRank = 3,
                power = 2,
                spells = listOf(KnownSpell(uid = "spell", name = "Искра", learned = true)),
            ),
        )
        val rules = DevelopmentRules(character, DevelopmentCatalog("test", listOf(entry)), DevelopmentProgress())
        assertTrue(rules.requirements(entry).all { it.status == RequirementStatus.OK })
        assertTrue(rules.availability(entry).canIncrease)
    }
    @Test
    fun developmentRequirementsUseStrongestConfiguredSchoolPower() {
        val entry = DevelopmentEntry(
            id = "school-magic-gated",
            name = "Школьная магия",
            section = "Тест",
            category = "Тест",
            cost = 10,
            costType = DevelopmentCostType.XP,
            maxRank = 1,
            requirements = "Сила магии 4",
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
        val character = DublCharacter(
            id = "school-magic-req",
            magic = CharacterMagic(
                power = 99,
                schools = listOf(
                    MagicSchool("Ограждение", 2),
                    MagicSchool("Разрушение", 4),
                ),
            ),
        ).normalized()

        val rules = DevelopmentRules(character, DevelopmentCatalog("test", listOf(entry)), DevelopmentProgress())
        assertTrue(rules.requirements(entry).all { it.status == RequirementStatus.OK })
    }

    @Test
    fun catalogGearLoadUsesStrengthRequirementInsteadOfWeight() {
        val entry = GearCatalogEntry(
            id = "bow",
            name = "Лук",
            category = "Снаряжение",
            section = "Оружие",
            fields = mapOf("Треб." to "4", "Вес" to "0,6 кг"),
            description = "",
        )
        assertEquals(4.0, MagicEquipmentRules.catalogGearLoad(entry), 0.001)
        assertEquals(0.6, MagicEquipmentRules.legacyCatalogGearWeight(entry), 0.001)
    }

    @Test
    fun catalogGearLoadParsesRequirementVariantsAndMissingRequirement() {
        fun entry(key: String? = null, value: String = "") = GearCatalogEntry(
            id = value,
            name = "Тест",
            category = "Снаряжение",
            section = "Тест",
            fields = if (key == null) mapOf("Вес" to "19 кг") else mapOf(key to value, "Вес" to "19 кг"),
            description = "",
        )

        assertEquals(3.0, MagicEquipmentRules.catalogGearLoad(entry("Треб.", "3(4*)")), 0.001)
        assertEquals(2.0, MagicEquipmentRules.catalogGearLoad(entry("Треб.", "2*")), 0.001)
        assertEquals(8.0, MagicEquipmentRules.catalogGearLoad(entry("Требование", "8")), 0.001)
        assertEquals(0.0, MagicEquipmentRules.catalogGearLoad(entry("Треб", "-")), 0.001)
        assertEquals(0.0, MagicEquipmentRules.catalogGearLoad(entry()), 0.001)
    }

    @Test
    fun longswordAndChainShirtGiveMediumLoadForStrengthTwoConstitutionTwo() {
        fun gear(name: String, requirement: String, weight: String) =
            GearCatalogEntry(name, name, "Снаряжение", "Тест", mapOf("Треб." to requirement, "Вес" to weight), "")
        val character = DublCharacter(
            id = "load",
            attributes = defaultAttributes() +
                (AttributeId.STRENGTH to AttributeValue(2)) +
                (AttributeId.CONSTITUTION to AttributeValue(2)),
            gear = CharacterGear(
                items = listOf(gear("Длинный меч", "3", "1.5 кг"), gear("Кольчужный", "4", "19 кг")).map {
                    GearItem(uid = it.id, catalogId = it.id, load = MagicEquipmentRules.catalogGearLoad(it))
                },
            ),
        )
        assertEquals(7.0, MagicEquipmentRules.equipmentLoad(character), 0.001)
        assertEquals(-2, MagicEquipmentRules.burden(character).penalty)
    }


    @Test
    fun schoolPowerCosts25XpPerLevelAndHighestSchoolDrivesManaTable() {
        val character = DublCharacter(
            id = "school-power",
            magic = CharacterMagic(
                manaRank = 2,
                power = 99,
                schools = listOf(
                    MagicSchool("Разрушение", 6),
                    MagicSchool("Ограждение", 3),
                ),
            ),
        )

        assertEquals(225, MagicEquipmentRules.magicSchoolPowerXp(character))
        assertEquals(6, MagicEquipmentRules.highestMagicPower(character))
        assertEquals(13, MagicEquipmentRules.manaMaximum(character))
    }

    @Test
    fun spellCanBeLearnedButUsabilityDependsOnAnyMatchingSchoolPower() {
        val character = DublCharacter(
            id = "spell-school",
            magic = CharacterMagic(
                schools = listOf(
                    MagicSchool("Разрушение", 5),
                    MagicSchool("Воплощение", 6),
                ),
            ),
        )
        val spell = KnownSpell(
            uid = "spell",
            name = "Тест",
            school = "Разрушение / Воплощение",
            cost = 6,
            learned = true,
        )

        val usable = MagicEquipmentRules.spellUsability(character, spell)
        assertTrue(usable.usable)
        assertEquals(listOf("Воплощение", "Разрушение"), usable.schools)
        assertEquals("Воплощение", usable.qualifyingSchool)

        val weak = character.copy(
            magic = character.magic.copy(schools = listOf(MagicSchool("Разрушение", 5))),
        )
        val blocked = MagicEquipmentRules.spellUsability(weak, spell)
        assertFalse(blocked.usable)
        assertEquals(6, blocked.requiredPower)
    }

    @Test
    fun schoolNamesFromRulebookAreCanonicalizedForFilteringAndRequirements() {
        assertEquals("Ограждение", MagicSchoolCatalog.canonicalize("Ограждения"))
        assertEquals("Молитва", MagicSchoolCatalog.canonicalize("Молитвы"))
        assertEquals("Некромантия", MagicSchoolCatalog.canonicalize("Некромант"))
        assertEquals(
            listOf("Магия крови", "Некромантия"),
            MagicSchoolCatalog.parseSchools("Некромантия (Магия крови)"),
        )
    }

    @Test
    fun unknownLegacySchoolDoesNotAffectSchoolPowerEconomy() {
        val character = DublCharacter(
            id = "legacy-school",
            magic = CharacterMagic(
                manaRank = 1,
                power = 2,
                schools = listOf(MagicSchool("Старая кастомная школа", 20)),
            ),
        )

        assertEquals(0, MagicEquipmentRules.magicSchoolPowerXp(character))
        assertEquals(2, MagicEquipmentRules.highestMagicPower(character))
        assertEquals(2, MagicEquipmentRules.manaMaximum(character))
    }

    @Test
    fun visibleMagicSchoolsCanHideUnlearnedSchools() {
        val character = DublCharacter(
            id = "visible-schools",
            magic = CharacterMagic(
                schools = listOf(
                    MagicSchool("Разрушение", 4),
                    MagicSchool("Ограждение", 0),
                )
            )
        )

        assertEquals(MagicSchoolCatalog.schools, MagicEquipmentRules.visibleMagicSchools(character, hideUnlearned = false))
        assertEquals(listOf("Разрушение"), MagicEquipmentRules.visibleMagicSchools(character, hideUnlearned = true))
    }

}
