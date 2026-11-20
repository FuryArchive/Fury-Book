package com.furybook.dubl.ui.editor

import com.furybook.dubl.model.AbilityOption
import com.furybook.dubl.model.AttributeId
import com.furybook.dubl.model.AttributeValue
import com.furybook.dubl.model.CharacterSkill
import com.furybook.dubl.model.DevelopmentCatalog
import com.furybook.dubl.model.DevelopmentCostType
import com.furybook.dubl.model.DevelopmentEntry
import com.furybook.dubl.model.DublCharacter
import com.furybook.dubl.model.OwnedDevelopment

/** Small catalog and a Шершень-like character for the editor model tests. Real catalog wording. */
internal object EditorFixtures {
    fun entry(
        id: String, name: String, category: String = "Общие", cost: Int = 30, maxRank: Int = 1,
        requirements: String = "", benefit: String = "", section: String = "Навыки", accessId: String? = null,
    ) = DevelopmentEntry(
        id = id, name = name, section = section, category = category, cost = cost, costType = DevelopmentCostType.XP,
        maxRank = maxRank, requirements = requirements, benefit = benefit, notes = "", tags = emptyList(), accessId = accessId,
        abilityOptions = emptyList(), incomplete = false, repeatable = false, perfectRoot = false, mechanicsConflict = "", conflictNote = "",
    )

    val keenHearing = entry("feat_hear", "Острый слух", "Внимательность", 30, 2, "Восприятие 2, Внимательность 2",
        "+1 за ранг на проверки Внимательности при использовании слуха.")
    val athleticism = entry("feat_ath", "Атлетичность", "Атлетика", 40, 2, "Сила 4, Ловкость 4, Телосложение 4, Атлетика 4",
        "За каждый ранг Вы получаете +1 на проверки атлетики.")
    val ranger = entry("feat_ranger", "Рейнджер", "Выживание", 40, 2, "Выживание 3", "Вы получаете +1 на проверки выживания за каждый ранг.")
    val quickAim = entry("feat_aim", "Быстрое прицеливание", "Стрелковое и метательное оружие", 50, 1, "Стрельба 5 или Метание 5",
        "В первый раунд снайперской стрельбы Вы игнорируете два НФ вместо одного.")
    val beauty = entry("feat_beauty", "Природная красота", "Общие", 30, 2, "Харизма 4, только при создании", "Вы от рождения обладаете…")
    val lockpick = entry("feat_lock", "Взломщик", "Взлом", 40, 1, "Взлом 6, Интеллект 6", "")
    val access = entry("access_van", "Авангард", "Особые способности", 0, 1, "", "", section = "Особые способности").copy(
        costType = DevelopmentCostType.ABILITY, abilityOptions = listOf(AbilityOption("Мастерство", 1)),
    )
    val branch = entry("feat_van", "Разметать", "Авангард", 40, 1, "", "Шквал атак приёмом Рассечение.", section = "Ветки способностей", accessId = "access_van")

    val catalog = DevelopmentCatalog("test", listOf(keenHearing, athleticism, ranger, quickAim, beauty, lockpick, access, branch))

    private fun skill(id: String, rank: Int) = id to CharacterSkill(id = id, definitionId = id, rank = rank)

    /** СИЛ 3, ЛОВ 8, ТЕЛ 6, ВОС 6, ХАР 1; a few ranked skills; owns Атлетичность 1 and the Авангард branch. */
    val hornet = DublCharacter(
        id = "c1", name = "Шершень", experience = 4580, creationExperience = 3500, creationComplete = true,
        attributes = AttributeId.entries.associateWith { AttributeValue() } + mapOf(
            AttributeId.STRENGTH to AttributeValue(base = 3), AttributeId.DEXTERITY to AttributeValue(base = 8),
            AttributeId.CONSTITUTION to AttributeValue(base = 6), AttributeId.PERCEPTION to AttributeValue(base = 6),
            AttributeId.CHARISMA to AttributeValue(base = 1),
        ),
        skills = mapOf(
            skill("melee_weapon", 7), skill("stealth", 5), skill("athletics", 4), skill("awareness", 3),
            skill("survival", 2), skill("shooting", 4), skill("lockpicking", 4),
        ),
        development = mapOf(
            athleticism.id to OwnedDevelopment(rank = 1), access.id to OwnedDevelopment(rank = 1), branch.id to OwnedDevelopment(rank = 1),
        ),
    )
}
