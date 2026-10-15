package com.furybook.dubl.acquisition

/** Labels for the closed, source-reviewed access-table vocabulary. No pricing or default selection. */
fun acquisitionAbilityOptionLabel(id: String, index: Int = 0): String = when (id) {
    "xp" -> "Опыт"
    "ap" -> "Очки способностей"
    "morale" -> "Мораль"
    "chi" -> "Ци"
    "mastery" -> "Мастерство"
    "mastery-morale" -> "Мастерство и Мораль"
    "mastery-tactics" -> "Мастерство и Тактика"
    "mastery-technique" -> "Мастерство / Техника"
    "mystical-force" -> "Мистическая сила"
    "augmentation" -> "Аугментация / Импланты"
    "extraordinary" -> "Экстраординарное"
    "social" -> "Социальное"
    else -> "Вариант ${index + 1}"
}

/** The exact source table caption takes precedence over the closed fallback vocabulary. */
fun acquisitionAbilityOptionLabel(definition: AcquisitionDefinition?, id: String, index: Int = 0): String =
    definition?.classificationSources?.get("option:$id")?.originalText?.takeIf { it.isNotBlank() }
        ?: acquisitionAbilityOptionLabel(id, index)
