package com.furybook.dubl.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import com.furybook.dubl.model.SkillCategory
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.furybook.dubl.ui.sheet.SheetLayout
import com.furybook.ui.components.FuryTextField
import com.furybook.ui.theme.FuryColors
import com.furybook.ui.theme.FurySpacing

private val SKILL_PANEL_WIDTH = 380.dp

/** Two group columns only when each keeps room for names next to the ±/bonus controls (~380dp each). */
internal fun editorSkillColumns(contentWidthDp: Int): Int = if (contentWidthDp >= 760) 2 else 1

/**
 * Умения: ranks by attribute group, the next rank's price, adding specializations or custom skills — and, since
 * the Умения tab is gone, each skill's settings in a panel (click the name), plus the hidden list.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun EditorSkillsStep(host: DublEditorHost, state: EditorState, data: EditorFrameData, layout: SheetLayout) {
    var formOpen by remember { mutableStateOf(false) }
    var hiddenOpen by remember { mutableStateOf(false) }
    val groups = remember(data, state.skillsRankedOnly, state.skillQuery, state.skillCategory, state.skillPanelId) {
        editorSkillGroups(host, data.before.character, state.skillsRankedOnly, state.skillQuery, state.skillCategory, keep = state.skillPanelId)
    }
    val detail = remember(data, state.skillPanelId) { state.skillPanelId?.let { editorSkillDetail(host, it) } }
    if (state.skillPanelId != null && detail == null) state.skillPanelId = null
    val padding = editorContentPadding(layout)
    val wide = layout == SheetLayout.WIDE_WITH_TRAY
    val hiddenCount = host.active.hiddenSkillIds.size
    val list: @Composable (columns: Int) -> Unit = { columns ->
        Column(verticalArrangement = Arrangement.spacedBy(FurySpacing.md)) {
            SkillsHeader(state, layout, hiddenCount, onAdd = { formOpen = true }, onHidden = { hiddenOpen = true })
            if (groups.all { it.rows.isEmpty() }) {
                SkillsEmpty(state, layout, hiddenCount) { hiddenOpen = true }
            } else {
                // Independent columns, balanced by size, so a one-skill group is never stretched to a big neighbour's height.
                val split = editorBalancedColumns(groups.map { it.rows.size }, columns)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(FurySpacing.sm), verticalAlignment = Alignment.Top) {
                    split.forEach { column ->
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(FurySpacing.sm)) {
                            column.forEach { index -> SkillGroup(host, state, groups[index], layout, Modifier.fillMaxWidth().height(IntrinsicSize.Min)) }
                        }
                    }
                }
            }
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val contentWidth = maxWidth.value.toInt()
        val split = wide && editorPerksSplit(contentWidth)
        when {
            split && detail != null -> Row(Modifier.fillMaxSize().padding(start = padding, end = padding, top = padding), horizontalArrangement = Arrangement.spacedBy(FurySpacing.md)) {
                Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(bottom = padding)) { list(1) }
                Column(Modifier.width(SKILL_PANEL_WIDTH).fillMaxHeight().verticalScroll(rememberScrollState()).padding(bottom = padding)) {
                    EditorSkillPanel(host, state, detail, layout, showBack = false)
                }
            }
            detail != null -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(padding)) {
                EditorSkillPanel(host, state, detail, layout, showBack = true)
            }
            else -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(padding)) {
                list(editorSkillColumns(contentWidth - 2 * padding.value.toInt()))
            }
        }
    }
    if (formOpen) {
        EditorSkillFormDialog(host, onAdded = { added ->
            formOpen = false
            state.skillsRankedOnly = false
            state.flash("Добавлено умение: $added")
        }, onDismiss = { formOpen = false })
    }
    if (hiddenOpen) EditorHiddenSkillsDialog(host, layout) { hiddenOpen = false }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SkillsHeader(state: EditorState, layout: SheetLayout, hiddenCount: Int, onAdd: () -> Unit, onHidden: () -> Unit) {
    val control = editorControl(layout)
    Column(verticalArrangement = Arrangement.spacedBy(FurySpacing.md)) {
        EditorStepTitle("Умения", "Ранг стоит 10 XP × новый ранг. Бонус = характеристика + ранг. Нажмите на умение — откроются его правила и настройки.")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(FurySpacing.sm), verticalArrangement = Arrangement.spacedBy(FurySpacing.sm), itemVerticalAlignment = Alignment.Bottom) {
            FuryTextField(state.skillQuery, { state.skillQuery = it }, "Поиск умения", Modifier.widthIn(min = 200.dp, max = 360.dp))
            EditorOutlineButton(if (state.skillsRankedOnly) "Показать все" else "Только с рангами", { state.skillsRankedOnly = !state.skillsRankedOnly }, control)
            EditorOutlineButton("+ Своё / специализация", onAdd, control)
            if (hiddenCount > 0) EditorOutlineButton("Скрытые · $hiddenCount", onHidden, control)
        }
        val chips: @Composable () -> Unit = {
            EditorChip("Все", state.skillCategory == null, { state.skillCategory = null }, control, gold = true)
            SkillCategory.entries.forEach { category ->
                EditorChip(category.title, state.skillCategory == category, {
                    state.skillCategory = if (state.skillCategory == category) null else category
                }, control, gold = true)
            }
        }
        // On a phone eight chips would take three rows; one scrolling row (the cut-off chip shows there is more).
        if (layout == SheetLayout.PHONE) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) { chips() }
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { chips() }
        }
    }
}

@Composable
private fun SkillsEmpty(state: EditorState, layout: SheetLayout, hiddenCount: Int, onHidden: () -> Unit) {
    val control = editorControl(layout)
    EditorCard(Modifier.editorReadableWidth()) {
        Text("Ничего не найдено", color = FuryColors.current.text, style = MaterialTheme.typography.titleMedium)
        Text("Сбросьте поиск и фильтры или верните скрытые умения.", color = FuryColors.current.muted, style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(FurySpacing.sm)) {
            EditorOutlineButton("Сбросить фильтры", {
                state.skillQuery = ""
                state.skillCategory = null
                state.skillsRankedOnly = false
            }, control)
            if (hiddenCount > 0) EditorOutlineButton("Открыть скрытые", onHidden, control)
        }
    }
}

@Composable
private fun SkillGroup(host: DublEditorHost, state: EditorState, group: EditorSkillGroup, layout: SheetLayout, modifier: Modifier) {
    val p = FuryColors.current
    val phone = layout == SheetLayout.PHONE
    val empty = group.rows.isEmpty()
    val shape = RoundedCornerShape(14.dp)
    val tile: @Composable (Modifier) -> Unit = { m ->
        val tileShape = RoundedCornerShape(10.dp)
        if (phone) {
            Row(
                m.fillMaxWidth().heightIn(min = 30.dp).clip(tileShape).background(if (empty) p.sunk else p.tile).border(1.dp, p.line, tileShape)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                EditorOverline(group.short)
                Text(group.value.toString(), color = p.text, style = MaterialTheme.typography.titleMedium.merge(EditorTabular))
                Text(group.title, color = p.muted, style = MaterialTheme.typography.labelMedium)
            }
        } else {
            Column(
                m.width(52.dp).heightIn(min = 44.dp).clip(tileShape).background(if (empty) p.sunk else p.tile).border(1.dp, p.line, tileShape),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
            ) {
                EditorOverline(group.short)
                Text(group.value.toString(), color = p.text, style = MaterialTheme.typography.titleMedium.merge(EditorTabular).copy(fontSize = 17.sp))
            }
        }
    }
    val rows: @Composable (Modifier) -> Unit = { m ->
        Column(m, verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically)) {
            if (empty) {
                Text(
                    when {
                        state.skillQuery.isNotBlank() -> "нет совпадений"
                        state.skillsRankedOnly -> "нет умений с рангами"
                        else -> "нет умений"
                    },
                    Modifier.padding(start = 4.dp), color = p.muted, style = MaterialTheme.typography.labelSmall,
                )
            }
            group.rows.forEach { row -> SkillRow(host, state, row, layout) }
        }
    }
    if (phone) {
        Column(modifier.clip(shape).background(p.panel).border(1.dp, p.line, shape).padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            tile(Modifier)
            rows(Modifier.fillMaxWidth())
        }
    } else {
        Row(modifier.clip(shape).background(p.panel).border(1.dp, p.line, shape).padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            tile(Modifier.fillMaxHeight())
            rows(Modifier.weight(1f))
        }
    }
}

@Composable
private fun SkillRow(host: DublEditorHost, state: EditorState, row: EditorSkillRow, layout: SheetLayout) {
    val p = FuryColors.current
    val control = editorControl(layout)
    val bonus = row.bonus
    val top = bonus != null && bonus >= 12
    val open = state.skillPanelId == row.skillId
    EditorRow(minHeight = 48.dp) {
        Column(
            Modifier.weight(1f).clickable(role = Role.Button, onClickLabel = "Правила и настройки «${row.name}»") { state.skillPanelId = row.skillId },
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(row.name, color = if (open) p.accText else if (row.rank > 0) p.text else p.dim, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EditorPips(row.rank, 10, if (top) p.acc else p.dim, Modifier.widthIn(min = 40.dp, max = 70.dp).weight(1f, fill = false), gainedFrom = row.gainedFrom)
                Text(row.nextCostText, color = p.goldText, style = MaterialTheme.typography.labelSmall.merge(EditorTabular), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        EditorSquareButton("−", "Понизить ${row.name}", row.canDec, control) { host.changeSkillRank(row.skillId, -1) }
        Text(row.rank.toString(), Modifier.width(20.dp), color = p.text, textAlign = TextAlign.Center, style = MaterialTheme.typography.labelLarge.merge(EditorTabular))
        EditorSquareButton("+", "Повысить ${row.name}", row.canInc, control) { host.changeSkillRank(row.skillId, 1) }
        Box(
            Modifier.widthIn(min = 44.dp).heightIn(min = 26.dp).clip(RoundedCornerShape(7.dp)).background(if (top) p.accSoft else p.hover).padding(horizontal = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                bonus?.let { if (it >= 0) "+$it" else it.toString() } ?: "—",
                color = when { top -> p.accText; row.rank > 0 -> p.text; else -> p.dim }, maxLines = 1, softWrap = false,
                style = MaterialTheme.typography.labelLarge.merge(EditorTabular),
            )
        }
    }
}
