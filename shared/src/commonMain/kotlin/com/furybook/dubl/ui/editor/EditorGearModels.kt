package com.furybook.dubl.ui.editor

import com.furybook.dubl.model.DublCharacter
import com.furybook.dubl.model.GearCatalogEntry
import com.furybook.dubl.model.GearItem
import com.furybook.dubl.model.MagicEquipmentRules
import com.furybook.dubl.ui.sheet.GearKind
import com.furybook.dubl.ui.sheet.formatSheetNumber
import com.furybook.dubl.ui.sheet.gearCategoryKey
import com.furybook.dubl.ui.sheet.gearGroups
import com.furybook.dubl.ui.sheet.gearLine

/** The Снаряжение header: load and burden from the rules, the load mode, and «N шт. · M позиций». */
data class EditorGearHeader(
    val load: String,
    val capacity: Int,
    val burden: String,
    val penalty: Int,
    val automatic: Boolean,
    val manual: String,
    val count: String,
)

fun editorGearHeader(character: DublCharacter): EditorGearHeader {
    val burden = MagicEquipmentRules.burden(character)
    val items = character.gear.items
    return EditorGearHeader(
        load = formatSheetNumber(MagicEquipmentRules.equipmentLoad(character)),
        capacity = MagicEquipmentRules.equipmentCapacity(character),
        burden = burden.title,
        penalty = burden.penalty,
        automatic = character.gear.loadAutomatic,
        manual = formatSheetNumber(character.gear.loadManual),
        count = "${items.sumOf { it.quantity }} шт. · ${items.size} позиций",
    )
}

data class EditorGearRow(val uid: String, val name: String, val line: String, val quantity: Int, val carried: Boolean)

internal fun gearLineText(fields: Map<String, String>, max: Int): String = gearLine(fields, max).joinToString(" · ") { "${it.stat.label} ${it.value}" }

private fun matches(query: String, vararg texts: String): Boolean {
    val q = query.trim().lowercase().replace('ё', 'е')
    return q.isEmpty() || texts.any { it.lowercase().replace('ё', 'е').contains(q) }
}

/** Every item, grouped like the sheet; the search covers name, description and field values (the old screens' search). */
fun editorInventory(character: DublCharacter, query: String): List<Pair<GearKind, List<EditorGearRow>>> =
    gearGroups(character.gear.items.filter { matches(query, it.name, it.description, it.fields.values.joinToString(" ")) }) { item ->
        EditorGearRow(item.uid, item.name, gearLineText(item.fields, 4).ifBlank { item.category }, item.quantity, item.carried)
    }

data class EditorCatalogGearRow(val id: String, val name: String, val line: String, val owned: Int)

/** Category chips: one per [gearCategoryKey] (the catalog spells some categories two ways), labelled as first seen. */
fun editorGearCategories(entries: List<GearCatalogEntry>): List<Pair<String, String>> =
    entries.groupBy { gearCategoryKey(it.category) }.map { (key, group) -> key to group.first().category }

fun editorGearCatalog(host: DublEditorHost, query: String, categoryKey: String?): List<EditorCatalogGearRow> {
    val owned = host.active.gear.items.filter { it.catalogId != null }.groupBy { it.catalogId }.mapValues { (_, items) -> items.sumOf { it.quantity } }
    return host.magicEquipmentCatalog.gear
        .filter { categoryKey == null || gearCategoryKey(it.category) == categoryKey }
        .filter { matches(query, it.name, it.category, it.description, it.fields.values.joinToString(" ")) }
        .map { entry -> EditorCatalogGearRow(entry.id, entry.name, gearLineText(entry.fields, 3).ifBlank { entry.category }, owned[entry.id] ?: 0) }
}

/** The item form: fields edit as «Название: значение» lines. */
data class EditorGearForm(
    val name: String,
    val quantity: String,
    val load: String,
    val category: String,
    val section: String,
    val description: String,
    val fields: String,
    val carried: Boolean,
)

fun editorGearForm(item: GearItem?): EditorGearForm = EditorGearForm(
    name = item?.name.orEmpty(),
    quantity = (item?.quantity ?: 1).toString(),
    load = formatSheetNumber(item?.load ?: 0.0),
    category = item?.category ?: "Снаряжение",
    section = item?.section ?: "Предметы",
    description = item?.description.orEmpty(),
    fields = item?.fields?.entries?.joinToString("\n") { "${it.key}: ${it.value}" }.orEmpty(),
    carried = item?.carried ?: true,
)

/** A load typed with «.» or «,»; null when it isn't a non-negative number. */
fun editorManualLoad(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it >= 0.0 }

fun EditorGearForm.errors(): List<String> = buildList {
    if (name.isBlank()) add("Нужно название")
    val qty = quantity.trim().takeIf { it.all(Char::isDigit) }?.toIntOrNull()
    if (qty == null || qty < 1) add("Количество — целое число от 1")
    if (editorManualLoad(load) == null) add("Нагрузка — число")
}

private fun parseFields(text: String): Map<String, String> = text.lines().mapNotNull { line ->
    val at = line.indexOf(':')
    if (at <= 0) return@mapNotNull null
    val key = line.substring(0, at).trim()
    val value = line.substring(at + 1).trim()
    if (key.isEmpty()) null else key to value
}.toMap(LinkedHashMap())

/** The saved item: [base] keeps its uid and catalog link; a new one is the character's own. */
fun EditorGearForm.toItem(base: GearItem?): GearItem = (base ?: GearItem(uid = "", custom = true)).copy(
    name = name.trim(),
    quantity = quantity.trim().toIntOrNull()?.coerceAtLeast(1) ?: 1,
    load = editorManualLoad(load) ?: 0.0,
    category = category.trim(),
    section = section.trim(),
    description = description.trim(),
    fields = parseFields(fields),
    carried = carried,
)
