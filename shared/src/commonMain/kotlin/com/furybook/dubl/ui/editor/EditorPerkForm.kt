package com.furybook.dubl.ui.editor

import com.furybook.dubl.model.AbilityOption
import com.furybook.dubl.model.DevelopmentCostType
import com.furybook.dubl.model.DevelopmentEntry

/** The perk form's fields as typed (the old local-edit dialog's fields); [toEntry] parses them back. */
data class EditorPerkForm(
    val name: String,
    val section: String,
    val category: String,
    val costType: DevelopmentCostType,
    val cost: String,
    val maxRank: String,
    val requirements: String,
    val benefit: String,
    val notes: String,
    val tags: String,
    val accessId: String,
    val abilityOptions: String,
    val mechanicsConflict: String,
    val conflictNote: String,
    val incomplete: Boolean,
    val repeatable: Boolean,
    val perfectRoot: Boolean,
)

fun editorPerkForm(entry: DevelopmentEntry): EditorPerkForm = EditorPerkForm(
    name = entry.name,
    section = entry.section,
    category = entry.category,
    costType = entry.costType,
    cost = entry.cost.toString(),
    maxRank = entry.maxRank.toString(),
    requirements = entry.requirements,
    benefit = entry.benefit,
    notes = entry.notes,
    tags = entry.tags.joinToString(", "),
    accessId = entry.accessId.orEmpty(),
    abilityOptions = entry.abilityOptions.joinToString("; ") { "${it.source}=${it.value}" },
    mechanicsConflict = entry.mechanicsConflict,
    conflictNote = entry.conflictNote,
    incomplete = entry.incomplete,
    repeatable = entry.repeatable,
    perfectRoot = entry.perfectRoot,
)

/** A blank own perk, as the old «Своя запись» dialog started it. */
fun editorNewPerk(): DevelopmentEntry = DevelopmentEntry(
    id = "", name = "", section = "Свои", category = "Домашние правила", cost = 0, costType = DevelopmentCostType.XP,
    maxRank = 1, requirements = "", benefit = "", notes = "", tags = emptyList(), accessId = null, abilityOptions = emptyList(),
    incomplete = false, repeatable = false, perfectRoot = false, mechanicsConflict = "", conflictNote = "",
)

private fun optionParts(text: String): List<String> = text.split(';').map(String::trim).filter(String::isNotEmpty)

private fun parseOption(part: String): AbilityOption? {
    val pieces = part.split('=', limit = 2)
    val source = pieces[0].trim()
    val value = pieces.getOrNull(1)?.trim()?.toIntOrNull()
    return if (source.isEmpty() || value == null || value < 0) null else AbilityOption(source, value)
}

fun EditorPerkForm.errors(): List<String> = buildList {
    if (name.isBlank()) add("Введите название")
    if ((cost.trim().toIntOrNull() ?: -1) < 0) add("Стоимость — целое число от 0")
    if ((maxRank.trim().toIntOrNull() ?: 0) < 1) add("Макс. ранг — целое число от 1")
    optionParts(abilityOptions).filter { parseOption(it) == null }.forEach { add("Варианты ОС: «$it» — нужно источник=цена") }
}

/** [base] with the typed fields; null while [errors] is non-empty. */
fun EditorPerkForm.toEntry(base: DevelopmentEntry): DevelopmentEntry? {
    if (errors().isNotEmpty()) return null
    return base.copy(
        name = name,
        section = section,
        category = category,
        cost = cost.trim().toInt(),
        costType = costType,
        maxRank = maxRank.trim().toInt(),
        requirements = requirements,
        benefit = benefit,
        notes = notes,
        tags = tags.split(',').map(String::trim).filter(String::isNotBlank),
        accessId = accessId.trim().takeIf(String::isNotBlank),
        abilityOptions = optionParts(abilityOptions).mapNotNull(::parseOption),
        incomplete = incomplete,
        repeatable = repeatable,
        perfectRoot = perfectRoot,
        mechanicsConflict = mechanicsConflict,
        conflictNote = conflictNote,
    )
}
