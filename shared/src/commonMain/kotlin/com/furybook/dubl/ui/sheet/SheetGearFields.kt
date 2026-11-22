package com.furybook.dubl.ui.sheet

/** A gear stat the sheet and the editor show; [label] is the short name on a line. */
enum class GearStat(val label: String) {
    DAMAGE("Урон"),
    DAMAGE_TYPE("Тип урона"),
    ARMOUR("Броня"),
    REQUIREMENT("Треб"),
    RANGE("Дальность"),
    SPEED("Скор"),
    MAGAZINE("Магазин"),
    CALIBER("Калибр"),
    PROPERTIES("Свойства"),
    WEIGHT("Вес"),
}

enum class GearKind(val title: String) { WEAPON("Оружие"), ARMOUR("Броня"), OTHER("Прочее") }

data class GearStatValue(val stat: GearStat, val value: String)

private fun normalKey(key: String): String = key.trim().lowercase().replace('ё', 'е')

/** The catalog spells field names several ways; each stat lists every spelling seen in it. */
private val SPELLINGS: Map<GearStat, Set<String>> = mapOf(
    GearStat.DAMAGE to setOf("Урон"),
    GearStat.DAMAGE_TYPE to setOf("Тип урона"),
    GearStat.ARMOUR to setOf("Броня"),
    GearStat.REQUIREMENT to setOf("Треб.", "Треб", "Требование"),
    GearStat.RANGE to setOf("Дальность", "Дальность (Баз-Макс)"),
    GearStat.SPEED to setOf("Скор-ть", "Скор-ть²"),
    GearStat.MAGAZINE to setOf("Магазин"),
    GearStat.CALIBER to setOf("Калибр"),
    GearStat.PROPERTIES to setOf("Спец. свойства", "Спец Свойства", "Специальные свойства", "Спецсвойства"),
    GearStat.WEIGHT to setOf("Вес", "Вес, кг"),
).mapValues { (_, keys) -> keys.mapTo(mutableSetOf(), ::normalKey) }

/** The stat's value under any of its spellings; null when missing or blank. */
fun gearStat(fields: Map<String, String>, stat: GearStat): String? {
    val keys = SPELLINGS.getValue(stat)
    return fields.entries.firstOrNull { normalKey(it.key) in keys && it.value.isNotBlank() }?.value?.trim()
}

fun gearKind(fields: Map<String, String>): GearKind = when {
    gearStat(fields, GearStat.DAMAGE) != null -> GearKind.WEAPON
    gearStat(fields, GearStat.ARMOUR) != null -> GearKind.ARMOUR
    else -> GearKind.OTHER
}

private val WEAPON_LINE = listOf(
    GearStat.DAMAGE, GearStat.DAMAGE_TYPE, GearStat.REQUIREMENT, GearStat.RANGE, GearStat.SPEED, GearStat.MAGAZINE, GearStat.PROPERTIES,
)
private val ARMOUR_LINE = listOf(GearStat.ARMOUR, GearStat.REQUIREMENT, GearStat.PROPERTIES)

/** The combat stats worth a glance in a fight, in reading order, at most [max]. Other items have none. */
fun gearLine(fields: Map<String, String>, max: Int): List<GearStatValue> {
    val order = when (gearKind(fields)) {
        GearKind.WEAPON -> WEAPON_LINE
        GearKind.ARMOUR -> ARMOUR_LINE
        GearKind.OTHER -> emptyList()
    }
    return order.mapNotNull { stat -> gearStat(fields, stat)?.let { GearStatValue(stat, it) } }.take(max.coerceAtLeast(0))
}

private fun compact(text: String): String = text.lowercase().replace('ё', 'е').filterNot { it.isWhitespace() }

/** Catalog descriptions often just repeat the fields as «Ключ: значение» lines; only what is left is worth showing. */
fun gearDescriptionExtra(description: String, fields: Map<String, String>): String {
    val repeated = fields.entries.mapTo(mutableSetOf()) { compact("${it.key}:${it.value}") }
    return description.lines().filterNot { compact(it) in repeated }.joinToString("\n").trim()
}

/** One key for the catalog's duplicate category spellings («Пистолеты/Револьверы» = «Пистолеты и револьверы»). */
fun gearCategoryKey(category: String): String =
    category.lowercase().replace('ё', 'е').replace("/", " и ").split(Regex("\\s+")).filter { it.isNotBlank() }.joinToString(" ")
