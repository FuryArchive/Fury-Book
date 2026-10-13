package com.furybook.android.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.furybook.android.ui.acquisition.LocalAndroidAcquisitionContent
import com.furybook.android.ui.acquisition.LocalAndroidAcquisition
import com.furybook.dubl.model.GearCatalogEntry
import com.furybook.dubl.model.GearItem
import com.furybook.dubl.model.MagicEquipmentRules
import com.furybook.android.state.CharacterController
import com.furybook.android.ui.components.containSheetOverscroll
import com.furybook.ui.components.DublCard
import com.furybook.android.ui.components.DublScreenHeader
import com.furybook.android.ui.components.DublSwitch
import com.furybook.ui.theme.DublDanger
import com.furybook.ui.theme.DublFocus
import com.furybook.ui.theme.DublGold
import com.furybook.ui.theme.DublMuted
import com.furybook.ui.theme.DublStamina
import java.text.DecimalFormat
import java.util.UUID

@Composable
fun EquipmentScreen(controller: CharacterController) {
    val character = controller.active
    val acquisition = LocalAndroidAcquisition.current
    val context = LocalContext.current.applicationContext
    val largeText = LocalDensity.current.fontScale >= 1.2f
    val catalog = LocalAndroidAcquisitionContent.current.magicEquipment
    var query by remember(character.id) { mutableStateOf("") }
    var selectedUid by remember(character.id) { mutableStateOf<String?>(null) }
    var showCatalog by remember(character.id) { mutableStateOf(false) }
    var editItem by remember(character.id) { mutableStateOf<GearItem?>(null) }
    var createItem by remember(character.id) { mutableStateOf(false) }
    var pendingDeleteUid by remember(character.id) { mutableStateOf<String?>(null) }

    val load = MagicEquipmentRules.equipmentLoad(character)
    val capacity = MagicEquipmentRules.equipmentCapacity(character)
    val burden = MagicEquipmentRules.burden(character)
    LaunchedEffect(character.id, catalog.version) {
        if (!controller.isCanonicalProtected) controller.syncCatalogGearLoads(catalog.gear)
    }

    val filtered = remember(character.gear.items, query) {
        val needle = query.trim().lowercase()
        character.gear.items.filter { item ->
            needle.isBlank() || listOf(item.name, item.category, item.section, item.description, item.fields.values.joinToString(" "))
                .joinToString(" ").lowercase().contains(needle)
        }.sortedBy { it.name.lowercase() }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 26.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            DublScreenHeader(
                title = "Снаряжение",
                subtitle = "Предметы, количество и нагрузка",
            )
        }

        item {
            LoadCard(
                automatic = character.gear.loadAutomatic,
                manualLoad = character.gear.loadManual,
                load = load,
                capacity = capacity,
                burdenTitle = burden.title,
                burdenPenalty = burden.penalty,
                onAutomatic = controller::setGearLoadAutomatic,
                onManualLoad = controller::setGearManualLoad,
            )
        }

        item {
            if (largeText) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column {
                        Text("Предметы", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("${character.gear.items.sumOf { it.quantity }} шт. · ${character.gear.items.size} позиций", style = MaterialTheme.typography.bodySmall, color = DublMuted)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(onClick = { createItem = true }, modifier = Modifier.weight(1f)) { Text("Свой") }
                        Button(onClick = { showCatalog = true }, modifier = Modifier.weight(1f)) { Text("Из книги") }
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Column {
                        Text("Предметы", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("${character.gear.items.sumOf { it.quantity }} шт. · ${character.gear.items.size} позиций", style = MaterialTheme.typography.bodySmall, color = DublMuted)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(onClick = { createItem = true }) { Text("Свой") }
                        Button(onClick = { showCatalog = true }) { Text("Из книги") }
                    }
                }
            }
        }

        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Поиск по снаряжению") },
                singleLine = true,
            )
        }

        if (filtered.isEmpty()) {
            item {
                DublCard(Modifier.fillMaxWidth()) {
                    Text(
                        if (character.gear.items.isEmpty()) "Снаряжение не добавлено" else "Ничего не найдено",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        if (character.gear.items.isEmpty()) "Добавьте оружие, доспехи или личные вещи из каталога." else "Измените поисковый запрос.",
                        color = DublMuted,
                    )
                    Spacer(Modifier.height(10.dp))
                    if (character.gear.items.isEmpty()) {
                        Button(onClick = { showCatalog = true }, modifier = Modifier.fillMaxWidth()) {
                            Text("Открыть каталог")
                        }
                        OutlinedButton(onClick = { createItem = true }, modifier = Modifier.fillMaxWidth()) {
                            Text("Создать свой предмет")
                        }
                    } else {
                        OutlinedButton(onClick = { query = "" }, modifier = Modifier.fillMaxWidth()) {
                            Text("Сбросить поиск")
                        }
                    }
                }
            }
        } else {
            items(filtered, key = { it.uid }) { item ->
                GearRow(item = item, onClick = { selectedUid = item.uid })
            }
        }
    }

    if (showCatalog) {
        GearCatalogSheet(
            entries = catalog.gear,
            ownedQuantities = character.gear.items
                .mapNotNull { item -> item.catalogId?.let { it to item.quantity } }
                .toMap(),
            onAdd = { entry -> if (acquisition?.canonical == true) acquisition.nonFinancial { equipment.addCatalog(entry) } else { controller.addCatalogGear(entry); Unit } },
            onDismiss = { showCatalog = false },
        )
    }

    selectedUid?.let { uid ->
        character.gear.items.firstOrNull { it.uid == uid }?.let { item ->
            GearDetailSheet(
                item = item,
                onEdit = { editItem = item; selectedUid = null },
                onRemove = { selectedUid = null; pendingDeleteUid = uid },
                onToggleCarried = { carried -> controller.setGearItemCarried(uid, carried) },
                onQuantity = { qty -> controller.setGearItemQuantity(uid, qty) },
                onDismiss = { selectedUid = null },
            )
        } ?: run { selectedUid = null }
    }

    if (createItem) {
        GearEditDialog(
            initial = GearItem(uid = UUID.randomUUID().toString(), custom = true),
            title = "Свой предмет",
            onSave = { item -> if (acquisition?.canonical == true) acquisition.nonFinancial { equipment.addCustom(item) } else controller.addCustomGear(item); createItem = false },
            onDismiss = { createItem = false },
        )
    }

    editItem?.let { item ->
        GearEditDialog(
            initial = item,
            title = item.name,
            onSave = { updated -> controller.updateGearItem(updated); editItem = null },
            onDismiss = { editItem = null },
        )
    }

    pendingDeleteUid?.let { uid ->
        val item = character.gear.items.firstOrNull { it.uid == uid }
        if (item != null) {
            AlertDialog(
                onDismissRequest = { pendingDeleteUid = null },
                title = { Text("Удалить предмет?") },
                text = { Text("«${item.name}» будет удалён из снаряжения персонажа.") },
                confirmButton = {
                    Button(onClick = {
                        controller.removeGearItem(uid)
                        pendingDeleteUid = null
                    }) { Text("Удалить") }
                },
                dismissButton = {
                    TextButton(onClick = { pendingDeleteUid = null }) { Text("Отмена") }
                },
            )
        } else {
            pendingDeleteUid = null
        }
    }
}

@Composable
private fun LoadCard(
    automatic: Boolean,
    manualLoad: Double,
    load: Double,
    capacity: Int,
    burdenTitle: String,
    burdenPenalty: Int,
    onAutomatic: (Boolean) -> Unit,
    onManualLoad: (Double) -> Unit,
) {
    var manualText by remember(manualLoad, automatic) { mutableStateOf(formatNumber(manualLoad)) }
    DublCard(Modifier.fillMaxWidth()) {
        val largeText = LocalDensity.current.fontScale >= 1.2f
        if (largeText) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Нагрузка", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("${formatNumber(load)} / $capacity", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = DublStamina)
                Text(burdenTitle, style = MaterialTheme.typography.labelLarge, color = if (burdenPenalty < 0) DublDanger else DublFocus)
                if (burdenPenalty != 0) Text("штраф $burdenPenalty", style = MaterialTheme.typography.bodySmall, color = DublDanger)
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("Нагрузка", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("${formatNumber(load)} / $capacity", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = DublStamina)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(burdenTitle, style = MaterialTheme.typography.labelLarge, color = if (burdenPenalty < 0) DublDanger else DublFocus)
                    if (burdenPenalty != 0) Text("штраф $burdenPenalty", style = MaterialTheme.typography.bodySmall, color = DublDanger)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Считать по предметам", fontWeight = FontWeight.Medium)
                Text("Вместимость = Сила + Телосложение", style = MaterialTheme.typography.bodySmall, color = DublMuted)
            }
            DublSwitch(checked = automatic, onCheckedChange = onAutomatic)
        }
        if (!automatic) {
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = manualText,
                onValueChange = { raw ->
                    manualText = raw.filter { it.isDigit() || it == '.' || it == ',' }.replace(',', '.')
                    manualText.toDoubleOrNull()?.let(onManualLoad)
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Нагрузка вручную") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
            )
        }
    }
}

@Composable
private fun GearRow(item: GearItem, onClick: () -> Unit) {
    DublCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        contentPadding = PaddingValues(horizontal = 13.dp, vertical = 10.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        item.name,
                        modifier = Modifier.weight(1f, fill = false),
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (item.custom) {
                        Spacer(Modifier.width(6.dp))
                        Text("СВОЁ", style = MaterialTheme.typography.labelSmall, color = DublGold)
                    }
                }
                Text(
                    listOf(item.category, if (item.carried) "на персонаже" else "оставлено")
                        .filter { it.isNotBlank() }
                        .joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = DublMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("×${item.quantity}", fontWeight = FontWeight.Bold)
                Text("нагр. ${formatNumber(item.load)}", style = MaterialTheme.typography.bodySmall, color = DublMuted)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GearCatalogSheet(
    entries: List<GearCatalogEntry>,
    ownedQuantities: Map<String, Int>,
    onAdd: (GearCatalogEntry) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(query, entries) {
        val needle = query.trim().lowercase()
        entries.filter { entry ->
            needle.isBlank() || listOf(entry.name, entry.category, entry.section, entry.description, entry.fields.values.joinToString(" "))
                .joinToString(" ").lowercase().contains(needle)
        }.sortedBy { it.name.lowercase() }
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 720.dp)
                .containSheetOverscroll()
                .padding(horizontal = 16.dp),
        ) {
            Text("Каталог снаряжения", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("${entries.size} предметов в каталоге", color = DublMuted)
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), label = { Text("Поиск") }, singleLine = true)
            Spacer(Modifier.height(8.dp))
            LazyColumn(
                modifier = Modifier.weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                if (filtered.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text("Ничего не найдено", fontWeight = FontWeight.SemiBold)
                            Text(
                                "Попробуйте другой запрос.",
                                style = MaterialTheme.typography.bodySmall,
                                color = DublMuted,
                            )
                            if (query.isNotBlank()) {
                                TextButton(onClick = { query = "" }) { Text("Сбросить поиск") }
                            }
                        }
                    }
                }
                items(filtered, key = { it.id }) { entry ->
                    val ownedQuantity = ownedQuantities[entry.id] ?: 0
                    Surface(
                        onClick = { onAdd(entry) },
                        shape = RoundedCornerShape(13.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)),
                    ) {
                        Row(
                            modifier = Modifier.padding(11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    entry.name,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    buildString {
                                        append(entry.fields.entries.take(2).joinToString(" · ") { "${it.key}: ${it.value}" }.ifBlank { entry.category })
                                        if (ownedQuantity > 0) append(" · у вас ×$ownedQuantity")
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = DublMuted,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Text(
                                if (ownedQuantity > 0) "+1" else "Добавить",
                                style = MaterialTheme.typography.labelMedium,
                                color = DublGold,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GearDetailSheet(
    item: GearItem,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
    onToggleCarried: (Boolean) -> Unit,
    onQuantity: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .containSheetOverscroll()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(item.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(item.category, color = DublGold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Количество", modifier = Modifier.weight(1f))
                TextButton(onClick = { onQuantity((item.quantity - 1).coerceAtLeast(1)) }, enabled = item.quantity > 1) { Text("−") }
                Text(item.quantity.toString(), fontWeight = FontWeight.Bold)
                TextButton(onClick = { onQuantity(item.quantity + 1) }) { Text("+") }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("На персонаже")
                    Text("Учитывается в автоматической нагрузке", style = MaterialTheme.typography.bodySmall, color = DublMuted)
                }
                DublSwitch(item.carried, onToggleCarried)
            }
            Text("Нагрузка одного: ${formatNumber(item.load)}", color = DublMuted)
            if (item.fields.isNotEmpty()) {
                HorizontalDivider()
                item.fields.forEach { (key, value) ->
                    if (LocalDensity.current.fontScale >= 1.2f) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(key, style = MaterialTheme.typography.labelMedium, color = DublMuted)
                            Text(value)
                        }
                    } else {
                        Row(Modifier.fillMaxWidth()) {
                            Text(key, modifier = Modifier.width(112.dp), style = MaterialTheme.typography.labelMedium, color = DublMuted)
                            Text(value, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
            if (item.description.isNotBlank()) {
                HorizontalDivider()
                Text("Описание", fontWeight = FontWeight.Bold)
                Text(item.description)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onRemove, modifier = Modifier.weight(1f)) { Text("Удалить") }
                Button(onClick = onEdit, modifier = Modifier.weight(1f)) { Text("Изменить") }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun GearEditDialog(
    initial: GearItem,
    title: String,
    onSave: (GearItem) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember(initial.uid) { mutableStateOf(initial.name.takeUnless { it == "Предмет" } ?: "") }
    var qtyText by remember(initial.uid) { mutableStateOf(initial.quantity.toString()) }
    var loadText by remember(initial.uid) { mutableStateOf(formatNumber(initial.load)) }
    var carried by remember(initial.uid) { mutableStateOf(initial.carried) }
    var description by remember(initial.uid) { mutableStateOf(initial.description) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(name, { name = it }, label = { Text("Название") }, singleLine = true)
                OutlinedTextField(qtyText, { qtyText = it.filter(Char::isDigit) }, label = { Text("Количество") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                OutlinedTextField(loadText, { loadText = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.replace(',', '.') }, label = { Text("Нагрузка одного предмета") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("На персонаже", modifier = Modifier.weight(1f))
                    DublSwitch(carried, { carried = it })
                }
                OutlinedTextField(description, { description = it }, label = { Text("Свойства и описание") }, minLines = 4)
            }
        },
        confirmButton = {
            Button(onClick = {
                if (name.isBlank()) return@Button
                onSave(
                    initial.copy(
                        name = name.trim(),
                        quantity = qtyText.toIntOrNull()?.coerceAtLeast(1) ?: 1,
                        load = loadText.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0,
                        carried = carried,
                        description = description.trim(),
                    )
                )
            }) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

private fun formatNumber(value: Double): String = DecimalFormat("0.##").format(value)
