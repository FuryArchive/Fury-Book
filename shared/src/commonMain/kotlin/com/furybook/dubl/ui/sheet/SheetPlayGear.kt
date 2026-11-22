package com.furybook.dubl.ui.sheet

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.furybook.dubl.ui.editor.DublEditorHost
import com.furybook.dubl.ui.editor.EditorSquareButton
import com.furybook.ui.components.FuryButton
import com.furybook.ui.components.FuryDialogAction
import com.furybook.ui.components.FuryDialogScaffold
import com.furybook.ui.theme.FuryColors
import com.furybook.ui.theme.furyOverlineStyle

private sealed interface GearListRow {
    data class Head(val kind: GearKind, val count: Int) : GearListRow
    data class Item(val row: SheetGearRow) : GearListRow
}

/** Снаряжение: carried gear with load and burden; weapons and armour show their combat stats. */
@Composable
internal fun SheetPlayGear(
    view: SheetGearView,
    onOpen: (String) -> Unit,
    onAdd: () -> Unit,
    compact: Boolean,
    scrollable: Boolean,
    modifier: Modifier = Modifier,
) {
    val p = FuryColors.current
    val rows = view.groups.flatMap { (kind, items) -> listOf<GearListRow>(GearListRow.Head(kind, items.size)) + items.map { GearListRow.Item(it) } }
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(p.panel).padding(start = 12.dp, end = 12.dp, top = 14.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) {
                Text("Снаряжение", color = p.text, style = MaterialTheme.typography.titleLarge)
                val penalty = sheetGearPenaltyText(view.penalty)
                Text(
                    "нагрузка ${view.load}/${view.capacity} — ${view.burden.lowercase()}" + (penalty?.let { " · $it" } ?: ""),
                    color = if (view.penalty < 0) p.accText else p.muted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            HeaderToggle("+ Предмет", onAdd, compact)
        }
        if (rows.isEmpty()) {
            Text("Снаряжения нет", Modifier.fillMaxWidth().padding(16.dp), color = p.dim, style = MaterialTheme.typography.bodyMedium)
        } else if (scrollable) {
            LazyColumn(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(rows, key = { row -> if (row is GearListRow.Item) row.row.uid else "head:${(row as GearListRow.Head).kind}" }) { row -> GearListEntry(row, compact, onOpen) }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) { rows.forEach { row -> GearListEntry(row, compact, onOpen) } }
        }
    }
}

@Composable
private fun GearListEntry(row: GearListRow, compact: Boolean, onOpen: (String) -> Unit) {
    when (row) {
        is GearListRow.Head -> Text(
            "${row.kind.title.uppercase()} · ${row.count}", Modifier.padding(start = 4.dp, top = 4.dp),
            color = FuryColors.current.dim, style = furyOverlineStyle(),
        )
        is GearListRow.Item -> GearRow(row.row, compact) { onOpen(row.row.uid) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GearRow(row: SheetGearRow, compact: Boolean, onOpen: () -> Unit) {
    val p = FuryColors.current
    val shape = RoundedCornerShape(10.dp)
    Column(
        Modifier.fillMaxWidth().heightIn(min = sheetControlMinHeightDp(compact).dp).clip(shape).background(p.panel2)
            .clickable(onClickLabel = "Подробнее: ${row.name}", role = Role.Button, onClick = onOpen)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(row.name, Modifier.weight(1f, fill = false), color = p.text, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (row.quantity > 1) Text("×${row.quantity}", color = p.muted, style = MaterialTheme.typography.labelMedium)
        }
        if (row.line.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                row.line.forEach { stat ->
                    Row(
                        Modifier.clip(RoundedCornerShape(7.dp)).background(p.tile).padding(horizontal = 7.dp, vertical = 3.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(stat.stat.label, color = p.muted, style = MaterialTheme.typography.labelSmall)
                        Text(stat.value, Modifier.widthIn(max = 220.dp), color = p.text, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

/** The item window: every field, the description when it says more than the fields, quantity, «Не нести», editor link. */
@Composable
internal fun SheetGearDialog(host: DublEditorHost, uid: String, onEdit: () -> Unit, onDismiss: () -> Unit) {
    val detail = sheetGearDetail(host.active, uid)
    if (detail == null) {
        com.furybook.ui.components.FuryDismissWhenGone(onDismiss)
        return
    }
    val p = FuryColors.current
    FuryDialogScaffold(
        title = detail.name,
        subtitle = detail.category.takeIf { it.isNotBlank() },
        onDismiss = onDismiss,
        primary = FuryDialogAction("Готово", onDismiss),
    ) {
        if (detail.fields.isNotEmpty()) SheetFacts(detail.fields)
        if (detail.extraDescription.isNotBlank()) Text(detail.extraDescription, color = p.text2, style = MaterialTheme.typography.bodyMedium)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("КОЛИЧЕСТВО", Modifier.weight(1f), color = p.muted, style = furyOverlineStyle())
            EditorSquareButton("−", "Меньше", detail.quantity > 1, 44.dp) { sheetChangeGearQuantity(host, uid, -1) }
            Text(detail.quantity.toString(), Modifier.widthIn(min = 28.dp), color = p.text, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            EditorSquareButton("+", "Больше", true, 44.dp) { sheetChangeGearQuantity(host, uid, 1) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FuryButton("Не нести", { host.setGearItemCarried(uid, false); onDismiss() }, Modifier.weight(1f))
            FuryButton("Изменить в редакторе", onEdit, Modifier.weight(1f))
        }
    }
}
