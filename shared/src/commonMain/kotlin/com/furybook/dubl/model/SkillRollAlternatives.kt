package com.furybook.dubl.model

/** Explicit, per-roll source condition. Never mutates purchased ranks or defaults. */
data class SkillRollAlternative(
    val id: String,
    val label: String,
    val sourceText: String,
    val skill: ResolvedSkill,
    val attribute: AttributeId,
    val advantageDice: Int = 0,
    val synergyPartner: ResolvedSkill? = null,
    val synergyContributions: List<SkillContribution> = emptyList(),
)

fun DublCharacter.skillRollAlternatives(
    skill: ResolvedSkill,
    catalog: SkillEffectCatalog,
): List<SkillRollAlternative> = catalog.effects.mapNotNull { effect ->
    if (effect.mode != SkillEffectMode.ALTERNATIVE || effect.id in disabledSkillEffectIds ||
        developmentRank(effect.developmentId) <= 0) return@mapNotNull null
    val originalId = skill.definition?.id ?: return@mapNotNull null
    var replacement = skill
    var attribute = skill.stockAttribute
    var synergy: ResolvedSkill? = null
    val condition = when (effect.developmentId) {
        "feat_ef01c79bad95bc11" -> {
            if (originalId != "riding") return@mapNotNull null
            val driving = resolveSkill("driving") ?: return@mapNotNull null
            replacement = skill.copy(state = skill.state.copy(rank = driving.rank))
            "Мотоцикл: использовать ранги Вождения для Верховой езды"
        }
        "feat_48d67bcbe8fc28c8" -> {
            if (originalId != "medicine") return@mapNotNull null
            replacement = resolveSkill("survival") ?: return@mapNotNull null
            attribute = AttributeId.INTELLIGENCE
            "Медицинский уход с природными ресурсами: Выживание + Интеллект"
        }
        "feat_098eff5ce64b5b87" -> {
            if (originalId != "stealth") return@mapNotNull null
            replacement = resolveSkill("survival") ?: return@mapNotNull null
            attribute = replacement.stockAttribute
            "Прячусь в дикой местности: Выживание вместо Скрытности"
        }
        "feat_a5d3b291d6db6600", "feat_cfa9aa3e8740f026" -> {
            if (originalId !in setOf("survival", "search")) return@mapNotNull null
            replacement = resolveSkill("search") ?: return@mapNotNull null
            attribute = if (effect.developmentId == "feat_a5d3b291d6db6600") AttributeId.PERCEPTION else AttributeId.INTELLIGENCE
            if (developmentRank(effect.developmentId) >= 2) synergy = resolveSkill("survival")
            "Читаю следы: Поиск + ${attribute.title}" + if (synergy != null) "; синергия с Выживанием" else ""
        }
        "feat_80cb914af7c6df13" -> {
            if (originalId != "eloquence") return@mapNotNull null
            attribute = AttributeId.INTELLIGENCE
            "Убеждение или обман: Интеллект вместо Харизмы"
        }
        "feat_b4c4cb83cb4afccd" -> {
            if (originalId != "leadership") return@mapNotNull null
            replacement = resolveSkill("intimidation") ?: return@mapNotNull null
            attribute = replacement.stockAttribute
            "Командую отрядом: Запугивание вместо Лидерства"
        }
        "feat_5da49e15b0b6c3dc" -> {
            if (originalId != "medicine") return@mapNotNull null
            attribute = AttributeId.DEXTERITY
            "Базовая сложность медицины не выше 15: Ловкость вместо Интеллекта"
        }
        "feat_1e4f2d8d4bab0107" -> {
            if (originalId != "stealth") return@mapNotNull null
            attribute = AttributeId.CHARISMA
            "Скрываюсь в толпе: Харизма вместо Ловкости"
        }
        else -> return@mapNotNull null
    }
    SkillRollAlternative(effect.id, "${effect.sourceName}: $condition", effect.effectText, replacement, attribute,
        synergyPartner = synergy, synergyContributions = if (synergy != null) synergyBonuses(replacement, synergy, catalog) else emptyList())
}.plus(skillRollPresets(skill, catalog)).plus(skillSynergyPresets(skill, catalog)).distinctBy { it.id }

/** Bounded, no-resource presets with explicit source conditions. */
private fun DublCharacter.skillRollPresets(
    skill: ResolvedSkill,
    catalog: SkillEffectCatalog,
): List<SkillRollAlternative> = catalog.effects.flatMap { effect ->
    if (effect.mode != SkillEffectMode.PRESET || effect.id in disabledSkillEffectIds ||
        developmentRank(effect.developmentId) <= 0) return@flatMap emptyList()
    when (effect.developmentId) {
        "feat_b6173be4e251982d" -> if (skill.definition?.id == "awareness") listOf(
            SkillRollAlternative(effect.id, "Чтение по губам: Внимательность + Интеллект",
                effect.effectText, skill, AttributeId.INTELLIGENCE)
        ) else emptyList()
        "feat_e63bcf4c87dd83e1" -> if (skill.definition?.id == "search") listOf(
            "Область не более 3×3 м" to 1,
            "Область 1,5×1,5 м" to 2,
            "Конкретный предмет" to 3,
        ).map { (area, dice) ->
            SkillRollAlternative("${effect.id}:$dice", "Целенаправленный поиск: $area (+$dice ПФ)",
                effect.effectText, skill, AttributeId.INTELLIGENCE, advantageDice = dice)
        } else emptyList()
        else -> emptyList()
    }
}

/** Uses actual learned ranks for eligibility/effects; synergy never edits them. */
fun SkillRollAlternative.calculation(character: DublCharacter): SkillCalculation {
    val base = character.skillCalculationForRoll(skill, attribute)
    val partner = synergyPartner ?: return base
    if (base.total == null) return base
    val rankBonus = SkillCheckRules.synergyCombinedRank(skill.rank, partner.rank) - skill.rank
    val contributions = listOf(SkillContribution("Синергия: ${skill.name} + ${partner.name}", rankBonus)) + synergyContributions
    return base.copy(total = base.total + contributions.sumOf { it.value }, contributions = base.contributions + contributions)
}

private fun DublCharacter.synergyBonuses(first: ResolvedSkill, second: ResolvedSkill, catalog: SkillEffectCatalog): List<SkillContribution> =
    catalog.effects.filter { it.id !in disabledSkillEffectIds && developmentRank(it.developmentId) > 0 }.mapNotNull { effect ->
        val value = when (effect.developmentId) {
            "feat_95b19d58202e83b8" -> 1
            "feat_d2afd80ad8e35bd6" -> when {
                minOf(first.rank, second.rank) >= 10 -> 2
                minOf(first.rank, second.rank) >= 5 -> 1
                else -> 0
            }
            else -> 0
        }
        if (value == 0) null else SkillContribution(effect.sourceName, value)
    }

/** Explicit source-defined pairs, resolved by stable skill identity, never by display name. */
private fun DublCharacter.skillSynergyPresets(skill: ResolvedSkill, catalog: SkillEffectCatalog): List<SkillRollAlternative> =
    catalog.effects.mapNotNull { effect ->
        if (effect.id in disabledSkillEffectIds || developmentRank(effect.developmentId) <= 0) return@mapNotNull null
        val pair = when (effect.developmentId) {
            "feat_a1b2117d80ed94e3" -> Triple("barter", "eloquence", "Договариваюсь об условиях выхода из конфликта")
            "feat_b28ef4152e26995a" -> Triple("engineering_repair", "computers", "Ремонтирую компьютер, гаджет или цифровое устройство")
            else -> return@mapNotNull null
        }
        if (skill.id != pair.first && skill.id != pair.second) return@mapNotNull null
        val primary = resolveSkill(pair.first) ?: return@mapNotNull null
        val partner = resolveSkill(pair.second) ?: return@mapNotNull null
        // A synergy does not grant permission to use a skill forbidden without training.
        if (skillCalculationForRoll(primary, primary.stockAttribute).total == null ||
            skillCalculationForRoll(partner, partner.stockAttribute).total == null) return@mapNotNull null
        SkillRollAlternative(effect.id, "${effect.sourceName}: ${pair.third}; ${primary.name} + ${partner.name}",
            effect.effectText, primary, primary.stockAttribute, synergyPartner = partner,
            synergyContributions = synergyBonuses(primary, partner, catalog))
    }
