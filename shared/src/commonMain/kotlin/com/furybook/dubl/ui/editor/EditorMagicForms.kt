package com.furybook.dubl.ui.editor

import com.furybook.dubl.model.KnownSpell

/** The spell form's text fields («+ Своё заклинание», «Изменить»). */
data class EditorSpellForm(
    val name: String,
    val school: String,
    val cost: String,
    val manaText: String,
    val time: String,
    val range: String,
    val area: String,
    val action: String,
    val duration: String,
    val description: String,
    val enhancement: String,
    val xpOverride: String,
    val learned: Boolean,
    val incomplete: Boolean,
)

fun editorSpellForm(spell: KnownSpell?): EditorSpellForm = EditorSpellForm(
    name = spell?.name.orEmpty(),
    school = spell?.school.orEmpty(),
    cost = (spell?.cost ?: 0).toString(),
    manaText = spell?.manaText.orEmpty(),
    time = spell?.time.orEmpty(),
    range = spell?.range.orEmpty(),
    area = spell?.area.orEmpty(),
    action = spell?.action.orEmpty(),
    duration = spell?.duration.orEmpty(),
    description = spell?.description.orEmpty(),
    enhancement = spell?.enhancement.orEmpty(),
    xpOverride = spell?.xpOverride?.toString().orEmpty(),
    learned = spell?.learned ?: true,
    incomplete = spell?.incomplete ?: false,
)

private fun String.wholeNumber(): Int? = trim().takeIf { it.isNotEmpty() && it.all(Char::isDigit) }?.take(6)?.toIntOrNull()

fun EditorSpellForm.errors(): List<String> = buildList {
    if (name.isBlank()) add("Нужно название")
    if (cost.wholeNumber() == null) add("Мана — число")
    if (xpOverride.isNotBlank() && xpOverride.wholeNumber() == null) add("Опыт — целое число")
}

/** The saved spell: [base] keeps its uid and catalog link; a new one is the character's own. Blank mana text takes the cost. */
fun EditorSpellForm.toSpell(base: KnownSpell?): KnownSpell {
    val manaCost = cost.wholeNumber() ?: 0
    return (base ?: KnownSpell(uid = "", custom = true)).copy(
        name = name.trim(),
        school = school.trim(),
        cost = manaCost,
        manaText = manaText.trim().ifBlank { manaCost.toString() },
        time = time.trim(),
        range = range.trim(),
        area = area.trim(),
        action = action.trim(),
        duration = duration.trim(),
        description = description.trim(),
        enhancement = enhancement.trim(),
        xpOverride = xpOverride.wholeNumber(),
        learned = learned,
        incomplete = incomplete,
    )
}

/** «+ Школа» / school detail: the name comes from the rulebook list, so the rules can save and price it. */
data class EditorSchoolForm(val name: String, val power: String, val note: String)

fun EditorSchoolForm.errors(allowed: List<String>): List<String> = buildList {
    if (name !in allowed) add("Выберите школу из списка")
    if (power.wholeNumber() == null) add("Сила — число")
}

fun EditorSchoolForm.powerValue(): Int = power.wholeNumber() ?: 0
