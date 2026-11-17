package com.furybook.dubl.ui.sheet

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.furybook.ui.components.FuryButton
import com.furybook.ui.components.FuryButtonStyle
import com.furybook.ui.theme.DublMuted
import com.furybook.ui.theme.DublSurfaceRaised
import com.furybook.ui.theme.DublText
import com.furybook.ui.theme.FuryAccentFill
import com.furybook.ui.theme.FuryMinTouch
import com.furybook.ui.theme.FuryOnAccentFill
import com.furybook.ui.theme.FuryShapes
import com.furybook.ui.theme.FurySpacing
import com.furybook.ui.theme.furyOverlineStyle

/** Trained skills by group; tapping a row rolls it. A null bonus (no usable attribute) shows "—". */
@Composable
fun SheetSkills(
    groups: List<SheetSkillGroup>,
    columns: Int,
    onRoll: (String) -> Unit,
    onOpenAll: () -> Unit,
    modifier: Modifier = Modifier,
    /** Tapping a group title collapses it (persisted, as on the old desktop sheet). */
    onToggleCollapsed: ((String) -> Unit)? = null,
    onManageGroups: (() -> Unit)? = null,
) {
    val perRow = columns.coerceAtLeast(1)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(FurySpacing.md)) {
        if (groups.isEmpty()) {
            Text("Нет изученных умений", color = DublMuted, style = MaterialTheme.typography.bodyMedium)
        }
        groups.forEach { group ->
            Column(verticalArrangement = Arrangement.spacedBy(FurySpacing.sm)) {
                Row(
                    Modifier.fillMaxWidth().defaultMinSize(minHeight = FuryMinTouch).then(
                        if (onToggleCollapsed != null && group.id.isNotEmpty()) {
                            Modifier.clickable(role = Role.Button, onClickLabel = if (group.collapsed) "Развернуть" else "Свернуть") { onToggleCollapsed(group.id) }
                        } else Modifier,
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(group.title.uppercase(), Modifier.weight(1f), color = DublMuted, style = furyOverlineStyle())
                    if (onToggleCollapsed != null) Text(if (group.collapsed) "▾ ${group.rows.size}" else "▴", color = DublMuted)
                }
                if (!group.collapsed) group.rows.chunked(perRow).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(FurySpacing.sm)) {
                        row.forEach { skill -> SkillRow(skill, onRoll, Modifier.weight(1f)) }
                        repeat(perRow - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(FurySpacing.sm)) {
            onManageGroups?.let { FuryButton("Настроить группы", it, style = FuryButtonStyle.Ghost) }
            FuryButton("Все умения", onOpenAll, style = FuryButtonStyle.Ghost)
        }
    }
}

@Composable
private fun SkillRow(skill: SheetSkillRow, onRoll: (String) -> Unit, modifier: Modifier) {
    Row(
        modifier.defaultMinSize(minHeight = 56.dp).clip(FuryShapes.panel).background(DublSurfaceRaised)
            .clickable(role = Role.Button, onClickLabel = "Бросок") { onRoll(skill.skillId) }
            .padding(horizontal = FurySpacing.md, vertical = FurySpacing.sm),
        horizontalArrangement = Arrangement.spacedBy(FurySpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                skill.name, color = DublText, style = MaterialTheme.typography.titleSmall,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${skill.attributeLabel} · ранг ${skill.rank}", color = DublMuted, style = MaterialTheme.typography.bodySmall,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Box(
            Modifier.defaultMinSize(minWidth = FuryMinTouch, minHeight = 40.dp).clip(FuryShapes.control)
                .background(FuryAccentFill).padding(horizontal = FurySpacing.sm),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                skill.bonus?.let { if (it >= 0) "+$it" else "−${-it}" } ?: "—",
                color = FuryOnAccentFill,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
            )
        }
    }
}
