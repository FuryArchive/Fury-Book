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
