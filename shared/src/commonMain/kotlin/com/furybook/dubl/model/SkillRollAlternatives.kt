package com.furybook.dubl.model

/** Explicit, per-roll source condition. Never mutates purchased ranks or defaults. */
data class SkillRollAlternative(
    val id: String,
    val label: String,
    val sourceText: String,
    val skill: ResolvedSkill,
    val attribute: AttributeId,
    val advantageDice: Int = 0,
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
    SkillRollAlternative(effect.id, "${effect.sourceName}: $condition", effect.effectText, replacement, attribute)
}.plus(skillRollPresets(skill, catalog)).distinctBy { it.id }

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
