package com.furybook.dubl.master

/** «Дублировать» in Враги: same numbers, a new id, «<имя> (копия)». */
fun duplicateEnemy(e: MasterEnemy, newId: String): MasterEnemy = e.copy(id = newId, name = "${e.name} (копия)")

/** The enemy editor's fields as typed. Blank numbers count as 0; anything else must be a whole number. */
data class EnemyForm(
    val name: String = "",
    val defense: String = "",
    val reflexes: String = "",
    val initiative: String = "",
    val health: String = "",
    val endurance: String = "",
    val attacks: String = "",
    val notes: String = "",
) {
    private val numbers: List<Pair<String, String>>
        get() = listOf("Защита" to defense, "Рефлексы" to reflexes, "Инициатива" to initiative, "Здоровье" to health, "Выносливость" to endurance)

    fun errors(): List<String> = buildList {
        if (name.isBlank()) add("Нужно имя")
        numbers.forEach { (label, value) -> if (value.isNotBlank() && value.trim().toIntOrNull() == null) add("«$label» — целое число") }
    }

    fun toEnemy(base: MasterEnemy): MasterEnemy = base.copy(
        name = name.trim(),
        defense = defense.number(), reflexes = reflexes.number(), initiative = initiative.number(),
        health = health.number(), endurance = endurance.number(),
        attacks = attacks.trim(), notes = notes.trim(),
    )

    companion object {
        fun from(e: MasterEnemy) = EnemyForm(
            e.name, e.defense.toString(), e.reflexes.toString(), e.initiative.toString(), e.health.toString(), e.endurance.toString(), e.attacks, e.notes,
        )
    }
}

private fun String.number(): Int = trim().toIntOrNull() ?: 0

internal fun lookupKey(text: String): String = text.lowercase().replace('ё', 'е')

/** Notes whose title or text contain [query] (case and ё ignored), in their stored order: newest first. */
fun searchNotes(notes: List<MasterNote>, query: String): List<MasterNote> {
    val key = lookupKey(query.trim())
    if (key.isEmpty()) return notes
    return notes.filter { key in lookupKey(it.title) || key in lookupKey(it.text) }
}

/** «Новая заметка»: empty, dated [today] (the GM can change the date). */
fun newNote(id: String, today: String): MasterNote = MasterNote(id = id, title = "", text = "", date = today)
