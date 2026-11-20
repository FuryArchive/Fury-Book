package com.furybook.dubl.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.furybook.dubl.model.GearCatalogEntry
import com.furybook.dubl.model.GearItem
import com.furybook.dubl.model.MagicEquipmentRules
import com.furybook.dubl.ui.sheet.SheetLayout
import com.furybook.dubl.ui.sheet.SheetSearchField
import com.furybook.dubl.ui.sheet.formatSheetNumber
import com.furybook.dubl.ui.sheet.gearDescriptionExtra
import com.furybook.dubl.ui.sheet.sheetChangeGearQuantity
import com.furybook.dubl.ui.sheet.sheetGearPenaltyText
import com.furybook.ui.components.FuryTextField
import com.furybook.ui.theme.FuryColors
import com.furybook.ui.theme.FurySpacing
import com.furybook.ui.theme.furyOverlineStyle

private sealed interface GearForm {
    data class Item(val base: GearItem?) : GearForm
}

/** Снаряжение: load mode, the whole inventory and the rulebook catalog (the old Снаряжение tab, now in the editor). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun EditorGearStep(host: DublEditorHost, state: EditorState, data: EditorFrameData, layout: SheetLayout) {
    val p = FuryColors.current
    val control = editorControl(layout)
    val compact = layout != SheetLayout.WIDE_WITH_TRAY
    val character = host.active
    val header = remember(data) { editorGearHeader(character) }
    val inventory = remember(data, state.gearQuery) { editorInventory(character, state.gearQuery) }
    var form by remember { mutableStateOf<GearForm?>(null) }
    // The manual load is typed freely and saved once it reads as a number; «Отменить всё» drops the typed text.
    var manualText by remember(state.restoreGeneration, character.id) { mutableStateOf(header.manual) }

    val headerContent: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(FurySpacing.md)) {
            EditorStepTitle("Снаряжение", "Всё, что есть у персонажа. На листе показано то, что он несёт с собой.")
            EditorCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(FurySpacing.sm)) {
                    Column(Modifier.weight(1f)) {
                        EditorOverline("Нагрузка")
                        Text("${header.load} / ${header.capacity}", color = p.text, style = MaterialTheme.typography.titleMedium)
                    }
                    val penalty = sheetGearPenaltyText(header.penalty)
                    Text(
                        header.burden + (penalty?.let { " · $it" } ?: ""), Modifier.widthIn(max = 240.dp),
                        color = if (header.penalty < 0) p.accText else p.muted, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.End,
                    )
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    EditorChip("Считать автоматически", header.automatic, { host.setGearLoadAutomatic(true) }, control, gold = true, role = Role.RadioButton)
                    EditorChip("Вручную", !header.automatic, { host.setGearLoadAutomatic(false) }, control, gold = true, role = Role.RadioButton)
                }
                if (!header.automatic) {
                    FuryTextField(
                        manualText,
                        { text ->
                            manualText = text.filter { it.isDigit() || it == '.' || it == ',' }.take(8)
                            editorManualLoad(manualText)?.let(host::setGearManualLoad)
                        },
                        "Нагрузка вручную",
                        Modifier.width(200.dp),
                        error = if (editorManualLoad(manualText) == null) "Нагрузка — число" else null,
                    )
                }
                Text(header.count, color = p.muted, style = MaterialTheme.typography.bodySmall)
            }
            EditorCard(Modifier.fillMaxWidth()) {
                FlowRow(verticalArrangement = Arrangement.spacedBy(6.dp), horizontalArrangement = Arrangement.spacedBy(FurySpacing.sm)) {
                    Text("Инвентарь", Modifier.padding(top = 8.dp), color = p.text, style = MaterialTheme.typography.titleMedium)
                    EditorOutlineButton("+ Из каталога", { state.gearPane = EditorPane.GearCatalog }, control)
                    EditorOutlineButton("+ Свой предмет", { form = GearForm.Item(null) }, control)
                }
                SheetSearchField(state.gearQuery, { state.gearQuery = it }, "Поиск по снаряжению…", Modifier.fillMaxWidth(), compact)
                if (inventory.isEmpty()) Text(if (character.gear.items.isEmpty()) "Снаряжения нет" else "Ничего не найдено", color = p.dim, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    val list: LazyListScope.() -> Unit = {
        inventory.forEach { (kind, rows) ->
            item(key = "kind:$kind") { Text("${kind.title.uppercase()} · ${rows.size}", Modifier.padding(start = 4.dp), color = p.dim, style = furyOverlineStyle()) }
            items(rows, key = { "item:${it.uid}" }) { row -> InventoryRow(host, state, row, control, stacked = layout == SheetLayout.PHONE) }
        }
    }
    val pane: (@Composable () -> Unit)? = when (val open = state.gearPane) {
        is EditorPane.Item -> character.gear.items.firstOrNull { it.uid == open.uid }?.let { item -> { OwnedItemPane(host, state, item, control) { form = GearForm.Item(item) } } }
        is EditorPane.CatalogItem -> host.magicEquipmentCatalog.gear.firstOrNull { it.id == open.id }?.let { entry -> { CatalogItemPane(host, state, entry, control) } }
        EditorPane.GearCatalog -> { { GearCatalogPane(host, state, control, compact) } }
        else -> null
    }
    EditorListPane(layout, headerContent, list, pane, "Выберите предмет слева — здесь будут все его поля и правка.", onBack = { state.gearPane = null })
    when (val open = form) {
        null -> Unit
        is GearForm.Item -> EditorGearFormDialog(host, state, open.base, layout) { form = null }
    }
}

/** One inventory item; on a phone the controls go under the name so long names stay readable. */
@Composable
private fun InventoryRow(host: DublEditorHost, state: EditorState, row: EditorGearRow, control: Dp, stacked: Boolean) {
    val p = FuryColors.current
    val selected = state.gearPane == EditorPane.Item(row.uid)
    val open = Modifier.clickable(role = Role.Button, onClickLabel = "Открыть ${row.name}") { state.gearPane = EditorPane.Item(row.uid) }
    val title: @Composable (Modifier) -> Unit = { modifier ->
        Column(modifier.padding(vertical = 4.dp)) {
            Text(row.name, color = if (row.carried) p.text else p.muted, style = MaterialTheme.typography.labelLarge, maxLines = if (stacked) 2 else 1, overflow = TextOverflow.Ellipsis)
            Text(row.line, color = p.muted, style = MaterialTheme.typography.bodySmall, maxLines = if (stacked) 2 else 1, overflow = TextOverflow.Ellipsis)
        }
    }
    val controls: @Composable () -> Unit = {
        EditorSquareButton("−", "Меньше: ${row.name}", row.quantity > 1, control) { sheetChangeGearQuantity(host, row.uid, -1) }
        Text(row.quantity.toString(), Modifier.widthIn(min = 20.dp), color = p.text, style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center)
        EditorSquareButton("+", "Больше: ${row.name}", true, control) { sheetChangeGearQuantity(host, row.uid, 1) }
        EditorChip(if (row.carried) "С собой" else "Не с собой", row.carried, { host.setGearItemCarried(row.uid, !row.carried) }, control, gold = true, role = Role.Switch)
    }
    val border = if (selected) Modifier.border(1.dp, p.acc, RoundedCornerShape(10.dp)) else Modifier
    if (stacked) {
        Column(
            border.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(p.panel2).then(open).padding(horizontal = 10.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            title(Modifier.fillMaxWidth())
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(FurySpacing.sm)) { controls() }
        }
    } else {
        EditorRow(border.then(open), minHeight = control + 8.dp) {
            title(Modifier.weight(1f))
            controls()
        }
    }
}

@Composable
private fun FieldList(fields: List<Pair<String, String>>) {
    val p = FuryColors.current
    fields.forEach { (key, value) ->
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(key, Modifier.widthIn(min = 110.dp), color = p.muted, style = MaterialTheme.typography.bodySmall)
            Text(value, Modifier.weight(1f), color = p.text, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OwnedItemPane(host: DublEditorHost, state: EditorState, item: GearItem, control: Dp, onEdit: () -> Unit) {
    val p = FuryColors.current
    EditorCard(Modifier.fillMaxWidth()) {
        Text(item.name, color = p.text, style = MaterialTheme.typography.titleLarge)
        Text("${item.category} · нагрузка ${formatSheetNumber(item.load)}", color = p.muted, style = MaterialTheme.typography.bodySmall)
        FieldList(item.fields.filterValues { it.isNotBlank() }.toList())
        gearDescriptionExtra(item.description, item.fields).takeIf { it.isNotBlank() }?.let { Text(it, color = p.text2, style = MaterialTheme.typography.bodyMedium) }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(FurySpacing.sm)) {
            EditorOverline("Количество", Modifier.weight(1f))
            EditorSquareButton("−", "Меньше", item.quantity > 1, control) { sheetChangeGearQuantity(host, item.uid, -1) }
            Text(item.quantity.toString(), Modifier.widthIn(min = 24.dp), color = p.text, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            EditorSquareButton("+", "Больше", true, control) { sheetChangeGearQuantity(host, item.uid, 1) }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(FurySpacing.sm), verticalArrangement = Arrangement.spacedBy(FurySpacing.sm)) {
            EditorChip(if (item.carried) "С собой" else "Не с собой", item.carried, { host.setGearItemCarried(item.uid, !item.carried) }, control, gold = true, role = Role.Switch)
            EditorOutlineButton("Изменить", onEdit, control)
            EditorOutlineButton("Удалить", { editorRemoveGear(host, state, item.uid) }, control, accent = true)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CatalogItemPane(host: DublEditorHost, state: EditorState, entry: GearCatalogEntry, control: Dp) {
    val p = FuryColors.current
    EditorCard(Modifier.fillMaxWidth()) {
        Text(entry.name, color = p.text, style = MaterialTheme.typography.titleLarge)
        Text("${entry.category} · нагрузка ${formatSheetNumber(MagicEquipmentRules.catalogGearLoad(entry))}", color = p.muted, style = MaterialTheme.typography.bodySmall)
        FieldList(entry.fields.filterValues { it.isNotBlank() }.toList())
        gearDescriptionExtra(entry.description, entry.fields).takeIf { it.isNotBlank() }?.let { Text(it, color = p.text2, style = MaterialTheme.typography.bodyMedium) }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(FurySpacing.sm), verticalArrangement = Arrangement.spacedBy(FurySpacing.sm)) {
            EditorPrimaryButton("Добавить", { editorAddCatalogGear(host, state, entry) }, control)
            EditorOutlineButton("К каталогу", { state.gearPane = EditorPane.GearCatalog }, control)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GearCatalogPane(host: DublEditorHost, state: EditorState, control: Dp, compact: Boolean) {
    val p = FuryColors.current
    val categories = remember(host.magicEquipmentCatalog) { editorGearCategories(host.magicEquipmentCatalog.gear) }
    val rows = editorGearCatalog(host, state.gearCatalogQuery, state.gearCatalogCategory)
    EditorCard(Modifier.fillMaxWidth()) {
        Text("Каталог снаряжения", color = p.text, style = MaterialTheme.typography.titleMedium)
        SheetSearchField(state.gearCatalogQuery, { state.gearCatalogQuery = it }, "Поиск по каталогу…", Modifier.fillMaxWidth(), compact)
        // The catalog has ~25 categories: one scrolling row keeps the list itself in view.
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            EditorChip("Всё", state.gearCatalogCategory == null, { state.gearCatalogCategory = null }, control)
            categories.forEach { (key, label) -> EditorChip(label, state.gearCatalogCategory == key, { state.gearCatalogCategory = key }, control) }
        }
        if (rows.isEmpty()) Text(if (host.magicEquipmentCatalog.gear.isEmpty()) "Каталог снаряжения пуст" else "Ничего не найдено", color = p.dim, style = MaterialTheme.typography.bodySmall)
        rows.forEach { row ->
            EditorRow(Modifier.clickable(role = Role.Button, onClickLabel = "Подробнее: ${row.name}") { state.gearPane = EditorPane.CatalogItem(row.id) }, minHeight = control + 8.dp) {
                Column(Modifier.weight(1f).padding(vertical = 4.dp)) {
                    Text(row.name, color = p.text, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(row.line, color = p.muted, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (row.owned > 0) Text("есть ×${row.owned}", Modifier.padding(end = 6.dp), color = p.muted, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
