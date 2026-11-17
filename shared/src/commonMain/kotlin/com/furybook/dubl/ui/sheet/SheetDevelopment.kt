package com.furybook.dubl.ui.sheet

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import com.furybook.dubl.model.DevelopmentProgress
import com.furybook.dubl.model.DevelopmentRules
import com.furybook.dubl.model.RequirementStatus
import com.furybook.ui.components.FuryButton
import com.furybook.ui.components.FuryButtonStyle
import com.furybook.ui.components.FuryDialogAction
import com.furybook.ui.components.FuryDialogScaffold
import com.furybook.ui.theme.DublAccentStrong
import com.furybook.ui.theme.DublGold
import com.furybook.ui.theme.DublMuted
import com.furybook.ui.theme.DublSurfaceRaised
import com.furybook.ui.theme.DublText
import com.furybook.ui.theme.FuryMinTouch
import com.furybook.ui.theme.FuryShapes
import com.furybook.ui.theme.FurySpacing
import com.furybook.ui.theme.furyOverlineStyle

/**
 * «Взятые навыки»: owned development grouped as on the old sheets, children indented under their parent.
 * Rows with unmet requirements are marked with «!» and crimson text (not colour alone).
 */
@Composable
fun SheetDevelopment(
    groups: List<SheetDevelopmentGroup>,
    onOpenDetails: (String) -> Unit,
    onOpenAll: () -> Unit,
    onManageGroups: (() -> Unit)?,
    onToggleCollapsed: ((String) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(FurySpacing.sm)) {
        if (groups.isEmpty()) Text("Нет взятых навыков", color = DublMuted, style = MaterialTheme.typography.bodyMedium)
        groups.forEach { group ->
            Row(
                Modifier.fillMaxWidth().defaultMinSize(minHeight = FuryMinTouch).then(
                    onToggleCollapsed?.let { toggle -> Modifier.clickable(role = Role.Button, onClickLabel = if (group.collapsed) "Развернуть" else "Свернуть") { toggle(group.id) } } ?: Modifier,
                ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("${group.title.uppercase()} · ${group.rows.size}", Modifier.weight(1f), color = DublMuted, style = furyOverlineStyle())
                if (onToggleCollapsed != null) Text(if (group.collapsed) "▾" else "▴", color = DublMuted)
            }
            if (!group.collapsed) group.rows.forEach { row -> DevelopmentRow(row, onOpenDetails) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(FurySpacing.sm)) {
            onManageGroups?.let { FuryButton("Настроить группы", it, style = FuryButtonStyle.Ghost) }
            FuryButton("Все навыки", onOpenAll, style = FuryButtonStyle.Ghost)
        }
    }
}

@Composable
private fun DevelopmentRow(row: SheetDevelopmentRow, onOpenDetails: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = (row.depth * 18).dp).clip(FuryShapes.control).background(DublSurfaceRaised)
            .then(if (row.opensDetails) Modifier.clickable(role = Role.Button, onClickLabel = "Подробнее") { onOpenDetails(row.entryId) } else Modifier)
            .defaultMinSize(minHeight = FuryMinTouch).padding(horizontal = FurySpacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(FurySpacing.sm),
    ) {
        if (row.requirementsUnmet) Text("!", color = DublAccentStrong, style = MaterialTheme.typography.titleMedium)
        Text(
            row.name, Modifier.weight(1f), color = if (row.requirementsUnmet) DublAccentStrong else DublText,
            style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        Text(row.rankLabel, color = DublGold, style = MaterialTheme.typography.titleSmall)
    }
}

/**
 * Read-only details of an owned entry (Android OwnedDevelopmentDetailSheet): effect, notes, requirement
 * checks (✓ / ✕ / ?), cost and book conflict. Desktop and web keep their richer dialog until stage 4.
 */
@Composable
fun SheetDevelopmentDetailsDialog(host: DublSheetHost, entryId: String, onDismiss: () -> Unit) {
    val character = host.active
    val entry = host.developmentCatalog.byId(entryId) ?: run { onDismiss(); return }
    val progress = DevelopmentProgress(character.development)
    val rules = DevelopmentRules(character, host.developmentCatalog, progress)
    FuryDialogScaffold(
        title = entry.name,
        subtitle = "${entry.section} · ${entry.category} · ранг ${progress.rank(entry.id)}/${entry.maxRank}",
        onDismiss = onDismiss,
        primary = FuryDialogAction("Закрыть", onDismiss),
    ) {
        if (entry.tags.isNotEmpty()) Text(entry.tags.joinToString(" · "), color = DublGold, style = MaterialTheme.typography.labelMedium)
        Section("Эффект", entry.benefit)
        Section("Особое", entry.notes)
        Text("ТРЕБОВАНИЯ", color = DublMuted, style = furyOverlineStyle())
        rules.requirements(entry).forEach { check ->
            val (mark, color) = when (check.status) {
                RequirementStatus.OK -> "✓" to DublText
                RequirementStatus.FAIL -> "✕" to DublAccentStrong
                RequirementStatus.MANUAL -> "?" to DublGold
            }
            Text("$mark ${check.text}", color = color, style = MaterialTheme.typography.bodySmall)
        }
        Text(
            if (entry.isAbility) "Стоимость доступа: ${rules.abilityCost(entry, progress.optionIndex(entry.id))} ОС" else "Стоимость ранга: ${entry.cost} XP",
            color = DublGold, style = MaterialTheme.typography.labelLarge,
        )
        Section("Расхождение книги", entry.conflictNote)
    }
}

@Composable
private fun Section(title: String, text: String) {
    if (text.isBlank()) return
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title.uppercase(), color = DublMuted, style = furyOverlineStyle())
        Text(text, color = DublText, style = MaterialTheme.typography.bodyMedium)
    }
}
