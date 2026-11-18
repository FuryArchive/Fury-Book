package com.furybook.dubl.ui.editor

import androidx.compose.foundation.background
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

/** Умения: ranks by attribute group, the next rank's price, and adding specializations or custom skills. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun EditorSkillsStep(host: DublEditorHost, state: EditorState, data: EditorFrameData, layout: SheetLayout) {
    var formOpen by remember { mutableStateOf(false) }
    val groups = remember(data, state.skillsRankedOnly, state.skillQuery) {
        editorSkillGroups(host, data.before.character, state.skillsRankedOnly, state.skillQuery)
    }
    val control = editorControl(layout)
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(editorContentPadding(layout)),
        verticalArrangement = Arrangement.spacedBy(FurySpacing.md),
    ) {
        EditorStepTitle("Умения", "Ранг стоит 10 XP × новый ранг. Бонус = характеристика + ранг. Умения сгруппированы по характеристике, как на листе.")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(FurySpacing.sm), verticalArrangement = Arrangement.spacedBy(FurySpacing.sm), itemVerticalAlignment = Alignment.Bottom) {
            FuryTextField(state.skillQuery, { state.skillQuery = it }, "Поиск умения", Modifier.widthIn(min = 200.dp, max = 360.dp))
            EditorOutlineButton(if (state.skillsRankedOnly) "Показать все" else "Только с рангами", { state.skillsRankedOnly = !state.skillsRankedOnly }, control)
            EditorOutlineButton("+ Своё / специализация", { formOpen = true }, control)
        }
        val columns = if (layout == SheetLayout.WIDE_WITH_TRAY) 2 else 1
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
    if (formOpen) {
        EditorSkillFormDialog(host, onAdded = { added ->
            formOpen = false
            state.skillsRankedOnly = false
            state.flash("Добавлено умение: $added")
        }, onDismiss = { formOpen = false })
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
    EditorRow(minHeight = 48.dp) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(row.name, color = if (row.rank > 0) p.text else p.dim, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
