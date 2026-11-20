package com.furybook.dubl.ui.sheet

import com.furybook.dubl.model.DublCharacter
import com.furybook.dubl.model.GearItem
import com.furybook.dubl.model.MagicEquipmentRules
import com.furybook.dubl.ui.editor.DublEditorHost

/** One carried item on the sheet: its combat line (weapons, armour) or just its name and quantity. */
data class SheetGearRow(val uid: String, val name: String, val quantity: Int, val kind: GearKind, val line: List<GearStatValue>)

data class SheetGearView(
    val load: String,
    val capacity: Int,
    val burden: String,
    val penalty: Int,
    val groups: List<Pair<GearKind, List<SheetGearRow>>>,
)

/** Groups [items] by kind (weapons, armour, other — empty groups dropped), by name inside each group. */
internal fun <T> gearGroups(items: List<GearItem>, row: (GearItem) -> T): List<Pair<GearKind, List<T>>> {
    val byKind = items.groupBy { gearKind(it.fields) }
    return GearKind.entries.mapNotNull { kind ->
        byKind[kind]?.sortedBy { it.name.lowercase() }?.map(row)?.let { kind to it }
    }
}

/** Carried gear with load, capacity and burden from the rules. */
fun sheetGear(character: DublCharacter): SheetGearView {
    val burden = MagicEquipmentRules.burden(character)
    return SheetGearView(
        load = formatSheetNumber(MagicEquipmentRules.equipmentLoad(character)),
        capacity = MagicEquipmentRules.equipmentCapacity(character),
        burden = burden.title,
        penalty = burden.penalty,
        groups = gearGroups(character.gear.items.filter { it.carried }) { item ->
            SheetGearRow(item.uid, item.name, item.quantity, gearKind(item.fields), gearLine(item.fields, 5))
        },
    )
}

fun sheetGearPenaltyText(penalty: Int): String? = if (penalty < 0) "−${-penalty} к атакам и защите" else null

data class SheetGearDetail(
    val uid: String,
    val name: String,
    val category: String,
    val fields: List<Pair<String, String>>,
    val extraDescription: String,
    val quantity: Int,
    val carried: Boolean,
)

fun sheetGearDetail(character: DublCharacter, uid: String): SheetGearDetail? {
    val item = character.gear.items.firstOrNull { it.uid == uid } ?: return null
    return SheetGearDetail(
        uid = item.uid,
        name = item.name,
        category = item.category,
        fields = item.fields.filterValues { it.isNotBlank() }.toList(),
        extraDescription = gearDescriptionExtra(item.description, item.fields),
        quantity = item.quantity,
        carried = item.carried,
    )
}

/** Quantity ± from the sheet or the editor; never below 1 (removing is «Удалить» in the editor). */
fun sheetChangeGearQuantity(host: DublEditorHost, uid: String, delta: Int) {
    val item = host.active.gear.items.firstOrNull { it.uid == uid } ?: return
    val next = (item.quantity + delta).coerceAtLeast(1)
    if (next != item.quantity) host.setGearItemQuantity(uid, next)
}
