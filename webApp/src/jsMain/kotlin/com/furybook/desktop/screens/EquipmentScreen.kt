package com.furybook.desktop.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.furybook.dubl.model.GearCatalogEntry
import com.furybook.dubl.model.GearItem
import com.furybook.dubl.model.MagicEquipmentRules
import com.furybook.ui.theme.DublFocus
import com.furybook.ui.theme.DublGold
import com.furybook.ui.theme.DublMuted
import com.furybook.desktop.DesktopAppState

private enum class EquipmentTab(val title: String) { INVENTORY("Инвентарь"), CATALOG("Каталог") }

@Composable
fun EquipmentScreen(state: DesktopAppState, modifier: Modifier = Modifier) {
    val character = state.activeCharacter
    var tab by remember(character.id) { mutableStateOf(EquipmentTab.INVENTORY) }
    var search by remember(character.id) { mutableStateOf("") }
    var editItem by remember(character.id) { mutableStateOf<GearItem?>(null) }
    var addCustom by remember(character.id) { mutableStateOf(false) }
    var catalogDetails by remember(character.id) { mutableStateOf<GearCatalogEntry?>(null) }
    var manualLoad by remember(character.id) { mutableStateOf(character.gear.loadManual.toString()) }
    var pendingDeleteUid by remember(character.id) { mutableStateOf<String?>(null) }

    val load = MagicEquipmentRules.equipmentLoad(character)
    val capacity = MagicEquipmentRules.equipmentCapacity(character)
    val burden = MagicEquipmentRules.burden(character)

    LazyColumn(modifier = modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            SectionCard("Снаряжение") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    EquipmentTab.entries.forEach { item -> FilterChip(selected = tab == item, onClick = { tab = item }, label = { Text(item.title) }) }
                    OutlinedTextField(search, { search = it }, label = { Text("Поиск") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Нагрузка ${formatNumber(load)} / $capacity", color = DublFocus, fontWeight = FontWeight.Bold)
                    Text("${burden.title}${if (burden.penalty != 0) " (${signed(burden.penalty)})" else ""}", color = DublGold)
                    Text("Автоматически")
                    Switch(checked = character.gear.loadAutomatic, onCheckedChange = { state.setGearLoadAutomatic(it) })
                }
                if (!character.gear.loadAutomatic) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(manualLoad, { manualLoad = it.filter { c -> c.isDigit() || c == '.' || c == ',' } }, label = { Text("Ручная нагрузка") }, singleLine = true)
                        Button(onClick = { state.setGearManualLoad(manualLoad.replace(',', '.').toDoubleOrNull() ?: 0.0) }) { Text("Применить") }
                    }
                }
            }
        }

        if (tab == EquipmentTab.INVENTORY) {
            item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { Button(onClick = { addCustom = true }) { Text("+ Свой предмет") } } }
            val itemsFiltered = character.gear.items.filter { item ->
                search.isBlank() || listOf(
                    item.name,
                    item.category,
                    item.section,
                    item.description,
                    item.fields.values.joinToString(" "),
                ).any { it.contains(search, true) }
            }.sortedBy { it.name.lowercase() }
            items(itemsFiltered, key = { it.uid }) { item ->
                SectionCard(item.name, action = { OutlinedButton(onClick = { editItem = item }) { Text("Редактировать") } }) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("×${item.quantity}", fontWeight = FontWeight.Bold)
                        Text("${formatNumber(item.load)} ед./шт", color = DublMuted)
                        Text(if (item.carried) "Несётся" else "Не несётся", color = if (item.carried) DublFocus else DublMuted)
                        Text(item.category, color = DublGold)
                    }
                    if (item.description.isNotBlank()) Text(item.description, color = DublMuted)
                    if (item.fields.isNotEmpty()) Text(item.fields.entries.joinToString(" · ") { "${it.key}: ${it.value}" }, color = DublMuted)
                }
            }
        } else {
            val entries = state.magicEquipmentCatalog.gear.filter { entry ->
                search.isBlank() || listOf(
                    entry.name,
                    entry.category,
                    entry.section,
                    entry.description,
                    entry.fields.values.joinToString(" "),
                ).any { it.contains(search, true) }
            }.sortedBy { it.name.lowercase() }
            items(entries, key = { it.id }) { entry ->
                SectionCard(entry.name, action = {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TextButton(onClick = { catalogDetails = entry }) { Text("Подробнее") }
                        Button(onClick = { state.addCatalogGear(entry) }) { Text("Добавить") }
                    }
                }) {
                    Text("${entry.category} · ${entry.section} · вес ${formatNumber(MagicEquipmentRules.catalogGearLoad(entry))}", color = DublMuted)
                    if (entry.description.isNotBlank()) Text(entry.description, color = DublMuted)
                }
            }
        }
    }

    editItem?.let { item -> GearDialog(state, item, onDelete = { uid -> editItem = null; pendingDeleteUid = uid }, onDismiss = { editItem = null }) }
    if (addCustom) GearDialog(state, null, onDelete = {}, onDismiss = { addCustom = false })
    catalogDetails?.let { entry -> CatalogGearDialog(state, entry, onDismiss = { catalogDetails = null }) }

    pendingDeleteUid?.let { uid ->
        val itemName = state.activeCharacter.gear.items.firstOrNull { it.uid == uid }?.name ?: "предмет"
        FuryDialog(
            onDismissRequest = { pendingDeleteUid = null },
            title = { Text("Удалить предмет?") },
            text = { Text("«$itemName» будет удалён из инвентаря персонажа.") },
            confirmButton = { TextButton(onClick = { state.removeGearItem(uid); pendingDeleteUid = null }) { Text("Удалить") } },
            dismissButton = { TextButton(onClick = { pendingDeleteUid = null }) { Text("Отмена") } },
        )
    }
}

@Composable
private fun GearDialog(state: DesktopAppState, item: GearItem?, onDelete: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember(item?.uid) { mutableStateOf(item?.name.orEmpty()) }
    var quantity by remember(item?.uid) { mutableStateOf((item?.quantity ?: 1).toString()) }
    var load by remember(item?.uid) { mutableStateOf((item?.load ?: 0.0).toString()) }
    var carried by remember(item?.uid) { mutableStateOf(item?.carried ?: true) }
    var category by remember(item?.uid) { mutableStateOf(item?.category ?: "Снаряжение") }
    var section by remember(item?.uid) { mutableStateOf(item?.section ?: "Предметы") }
    var description by remember(item?.uid) { mutableStateOf(item?.description.orEmpty()) }
    FuryDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (item == null) "Свой предмет" else item.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Название") })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(quantity, { quantity = it.filter(Char::isDigit) }, label = { Text("Количество") }, modifier = Modifier.weight(1f), singleLine = true)
                    OutlinedTextField(load, { load = it.filter { c -> c.isDigit() || c == '.' || c == ',' } }, label = { Text("Вес / нагрузка") }, modifier = Modifier.weight(1f), singleLine = true)
                }
                OutlinedTextField(category, { category = it }, label = { Text("Категория") })
                OutlinedTextField(section, { section = it }, label = { Text("Раздел") })
                OutlinedTextField(description, { description = it }, label = { Text("Описание") })
                Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(carried, { carried = it }); Text("Несётся персонажем") }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = {
                val updated = (item ?: GearItem(uid = "", custom = true)).copy(
                    name = name.trim(), quantity = quantity.toIntOrNull()?.coerceAtLeast(1) ?: 1,
                    load = load.replace(',', '.').toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0,
                    carried = carried, category = category.trim(), section = section.trim(), description = description.trim(),
                )
                if (item == null) state.addCustomGear(updated) else state.updateGearItem(updated)
                onDismiss()
            }) { Text("Сохранить") }
        },
        dismissButton = {
            Row {
                if (item != null) TextButton(onClick = { onDelete(item.uid) }) { Text("Удалить") }
                TextButton(onClick = onDismiss) { Text("Отмена") }
            }
        },
    )
}

@Composable
private fun CatalogGearDialog(state: DesktopAppState, entry: GearCatalogEntry, onDismiss: () -> Unit) {
    FuryDialog(
        onDismissRequest = onDismiss,
        title = { Text(entry.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                KeyValue("Категория", entry.category)
                KeyValue("Раздел", entry.section)
                KeyValue("Вес", formatNumber(MagicEquipmentRules.catalogGearLoad(entry)))
                entry.fields.forEach { (key, value) -> KeyValue(key, value) }
                if (entry.description.isNotBlank()) Text(entry.description)
            }
        },
        confirmButton = { TextButton(onClick = { state.addCatalogGear(entry); onDismiss() }) { Text("Добавить") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } },
    )
}
