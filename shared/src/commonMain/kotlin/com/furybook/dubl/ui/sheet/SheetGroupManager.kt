package com.furybook.dubl.ui.sheet

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.furybook.dubl.model.SheetGroup
import com.furybook.dubl.model.SheetGroupingRules
import com.furybook.dubl.model.resolvedSkills
import com.furybook.ui.components.FuryButton
import com.furybook.ui.components.FuryButtonStyle
import com.furybook.ui.components.FuryDialogAction
import com.furybook.ui.components.FuryDialogScaffold
import com.furybook.ui.components.FuryIconButton
import com.furybook.ui.components.FuryTextField
import com.furybook.ui.theme.DublAccent
import com.furybook.ui.theme.DublAccentSoft
import com.furybook.ui.theme.DublMuted
import com.furybook.ui.theme.DublSurfaceRaised
import com.furybook.ui.theme.DublText
import com.furybook.ui.theme.FuryShapes
import com.furybook.ui.theme.FurySpacing
import com.furybook.ui.theme.furyOverlineStyle

enum class SheetGroupKind { SKILLS, DEVELOPMENT }

private const val SKILLS_UNGROUPED = "skills:ungrouped"
private const val DEVELOPMENT_UNGROUPED = "development:ungrouped"

/**
 * Group manager for the sheet's skills or development: drag rows to reorder (long-press anywhere on touch,
 * or press-drag the ≡ handle with a mouse), ↑/↓ buttons for one-step moves, rename, collapse, delete
 * (items go to «Без группы»), create, and «По умолчанию». Dragging a development tree root moves its subtree.
 */
@Composable
fun SheetGroupManager(kind: SheetGroupKind, host: DublSheetEditHost, onDismiss: () -> Unit) {
    val groups = when (kind) {
        SheetGroupKind.SKILLS -> normalizedSkillGroups(host)
        SheetGroupKind.DEVELOPMENT -> normalizedDevelopmentGroups(host)
    }
    val tree = if (kind == SheetGroupKind.DEVELOPMENT) sheetDevelopmentTree(host) else null
    val treeRoots = if (kind == SheetGroupKind.DEVELOPMENT) developmentTreeRootIds(host) else emptySet()
    val labels: Map<String, String> = when (kind) {
        SheetGroupKind.SKILLS -> {
            val hidden = host.active.hiddenSkillIds
            host.active.resolvedSkills(includeHidden = true).associate { it.id to if (it.id in hidden) "${it.name} · скрыто" else it.name }
        }
        SheetGroupKind.DEVELOPMENT -> tree!!.labels
    }
    val save: (List<SheetGroup>) -> Unit = when (kind) {
        SheetGroupKind.SKILLS -> host::setSkillGroups
        SheetGroupKind.DEVELOPMENT -> host::setDevelopmentGroups
    }
    val ungroupedId = if (kind == SheetGroupKind.SKILLS) SKILLS_UNGROUPED else DEVELOPMENT_UNGROUPED
    val rows = groupManagerRows(
        groups,
        labelOf = labels::get,
        depthOf = { id -> tree?.let { t -> SheetGroupingRules.localDepth(id, groups.first { id in it.itemIds }.itemIds, t.parentById) } ?: 0 },
    )

    val tops = remember { mutableStateMapOf<String, Float>() }
    val heights = remember { mutableStateMapOf<String, Float>() }
    var dragged by remember { mutableStateOf<GroupManagerRow?>(null) }
    var dragStartY by remember { mutableStateOf(0f) }
    var dragDelta by remember { mutableStateOf(0f) }
    var renaming by remember { mutableStateOf<String?>(null) }
    var creating by remember { mutableStateOf(false) }

    fun movingIds(row: GroupManagerRow.Item): List<String> {
        val parentById = tree?.parentById ?: return listOf(row.itemId)
        if (row.itemId !in treeRoots) return listOf(row.itemId)
        return SheetGroupingRules.subtreeBlock(row.itemId, parentById, groups.first { it.id == row.groupId }.itemIds)
    }

    fun drop() {
        val row = dragged ?: return
        val slot = dropIndex(rows.map { tops[it.key] ?: 0f }, rows.map { heights[it.key] ?: 0f }, dragStartY + dragDelta)
        if (slot != null) {
            save(applyGroupDrop(groups, rows, row, slot, if (row is GroupManagerRow.Item) movingIds(row) else emptyList()))
        }
        dragged = null
        dragDelta = 0f
    }

    fun dragModifier(row: GroupManagerRow): Modifier = Modifier.pointerInput(row.key, rows.size) {
        detectDragGesturesAfterLongPress(
            onDragStart = { offset -> dragged = row; dragStartY = (tops[row.key] ?: 0f) + offset.y; dragDelta = 0f },
            onDrag = { change, amount -> change.consume(); dragDelta += amount.y },
            onDragEnd = { drop() },
            onDragCancel = { dragged = null; dragDelta = 0f },
        )
    }

    fun handleModifier(row: GroupManagerRow): Modifier = Modifier.pointerInput("handle:${row.key}", rows.size) {
        detectDragGestures(
            onDragStart = { offset -> dragged = row; dragStartY = (tops[row.key] ?: 0f) + offset.y; dragDelta = 0f },
            onDrag = { change, amount -> change.consume(); dragDelta += amount.y },
            onDragEnd = { drop() },
            onDragCancel = { dragged = null; dragDelta = 0f },
        )
    }

    FuryDialogScaffold(
        title = if (kind == SheetGroupKind.SKILLS) "Группы умений" else "Группы навыков",
        subtitle = "Перетащите строку, чтобы поменять порядок",
        onDismiss = onDismiss,
        primary = FuryDialogAction("Готово", onDismiss),
        secondary = FuryDialogAction("Новая группа", { creating = true }),
        destructive = FuryDialogAction("По умолчанию", { save(emptyList()) }),
    ) {
        if (creating) {
            NameEditor("Новая группа", "", onSave = { title -> save(SheetGroupingRules.addGroup(groups, newSheetGroupId(), title)); creating = false }, onCancel = { creating = false })
        }
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(FurySpacing.xs)) {
            rows.forEach { row ->
                val isDragged = dragged?.key == row.key
                Box(
                    Modifier.fillMaxWidth()
                        .onGloballyPositioned { coordinates ->
                            tops[row.key] = coordinates.positionInParent().y
                            heights[row.key] = coordinates.size.height.toFloat()
                        }
                        .graphicsLayer { translationY = if (isDragged) dragDelta else 0f; alpha = if (isDragged) 0.85f else 1f }
                        .then(dragModifier(row)),
                ) {
                    when (row) {
                        is GroupManagerRow.Header -> if (renaming == row.groupId) {
                            NameEditor("Название группы", row.title, onSave = { title -> save(SheetGroupingRules.renameGroup(groups, row.groupId, title)); renaming = null }, onCancel = { renaming = null })
                        } else {
                            HeaderRow(
                                row = row,
                                highlighted = isDragged,
                                handle = handleModifier(row),
                                canDelete = row.groupId != ungroupedId,
                                onToggle = { save(SheetGroupingRules.toggleCollapsed(groups, row.groupId)) },
                                onRename = { renaming = row.groupId },
                                onDelete = { save(SheetGroupingRules.deleteGroup(groups, row.groupId, ungroupedId)) },
                            )
                        }
                        is GroupManagerRow.Item -> ItemRow(
                            row = row,
                            highlighted = isDragged,
                            handle = handleModifier(row),
                            onUp = { save(SheetGroupingRules.moveItemByStep(groups, row.itemId, -1)) },
                            onDown = { save(SheetGroupingRules.moveItemByStep(groups, row.itemId, 1)) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HeaderRow(
    row: GroupManagerRow.Header,
    highlighted: Boolean,
    handle: Modifier,
    canDelete: Boolean,
    onToggle: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clip(FuryShapes.control).background(if (highlighted) DublAccentSoft else DublSurfaceRaised)
            .padding(horizontal = FurySpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(FurySpacing.xs),
    ) {
        DragHandle(handle, "Перетащить группу ${row.title}")
        Text(
            "${row.title.uppercase()} · ${row.count}", Modifier.weight(1f), color = DublText, style = furyOverlineStyle(),
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        FuryButton(if (row.collapsed) "Развернуть" else "Свернуть", onToggle, style = FuryButtonStyle.Ghost)
        FuryIconButton("Переименовать ${row.title}", onRename) { Text("✎", color = DublText) }
        if (canDelete) FuryIconButton("Удалить группу ${row.title}", onDelete) { Text("✕", color = DublText) }
    }
}

@Composable
private fun ItemRow(row: GroupManagerRow.Item, highlighted: Boolean, handle: Modifier, onUp: () -> Unit, onDown: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = (row.depth * 16).dp).clip(FuryShapes.control)
            .then(if (highlighted) Modifier.background(DublAccentSoft).border(1.dp, DublAccent, FuryShapes.control) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(FurySpacing.xs),
    ) {
        DragHandle(handle, "Перетащить ${row.label}")
        Text(row.label, Modifier.weight(1f), color = DublText, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        FuryIconButton("Выше: ${row.label}", onUp) { Text("↑", color = DublText) }
        FuryIconButton("Ниже: ${row.label}", onDown) { Text("↓", color = DublText) }
    }
}

@Composable
private fun DragHandle(modifier: Modifier, label: String) {
    // Decorative glyph; the row's own long-press and the ↑/↓ buttons are the accessible ways to move.
    Box(modifier.size(44.dp), contentAlignment = Alignment.Center) {
        Text("≡", color = DublMuted, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun NameEditor(label: String, initial: String, onSave: (String) -> Unit, onCancel: () -> Unit) {
    var text by remember(initial) { mutableStateOf(initial) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(FurySpacing.sm)) {
        FuryTextField(text, { text = it.take(60) }, label)
        Row(Modifier.fillMaxWidth().height(48.dp), horizontalArrangement = Arrangement.spacedBy(FurySpacing.sm)) {
            FuryButton("Отмена", onCancel, Modifier.weight(1f), style = FuryButtonStyle.Ghost)
            FuryButton("Сохранить", { onSave(text) }, Modifier.weight(1f), enabled = text.isNotBlank())
        }
    }
}
