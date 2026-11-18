package com.furybook.dubl.ui.sheet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.furybook.dubl.ui.editor.DublEditorHost
import com.furybook.dubl.ui.editor.editorSkillDetail
import com.furybook.ui.components.FuryDialogAction
import com.furybook.ui.components.FuryDialogScaffold
import com.furybook.ui.theme.FuryColors

/**
 * «Об умении» from the roll panel: description, bonus per allowed characteristic, rule note, «без обучения»
 * and auto-successes, read-only. Changing anything is one tap away: «Настроить в редакторе».
 */
@Composable
internal fun SheetSkillInfoDialog(host: DublEditorHost, skillId: String, onConfigure: () -> Unit, onDismiss: () -> Unit) {
    val detail = editorSkillDetail(host, skillId) ?: run { onDismiss(); return }
    val p = FuryColors.current
    FuryDialogScaffold(
        title = detail.name,
        onDismiss = onDismiss,
        subtitle = listOfNotNull(detail.origin, detail.headline).joinToString(" · "),
        primary = FuryDialogAction("Настроить в редакторе", onConfigure),
        secondary = FuryDialogAction("Закрыть", onDismiss),
    ) {
        if (detail.description.isNotBlank()) Text(detail.description, color = p.text2, style = MaterialTheme.typography.bodyMedium)
        Label("Бонус проверки")
        detail.bonus.forEach { line ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(line.title, color = p.text, style = MaterialTheme.typography.labelLarge)
                    Text(line.unavailableReason.ifBlank { line.breakdown }, color = if (line.total == null) p.accText else p.muted, style = MaterialTheme.typography.labelSmall)
                }
                Text(line.total?.let { if (it >= 0) "+$it" else it.toString() } ?: "—", color = if (line.total == null) p.dim else p.goldText, style = MaterialTheme.typography.titleMedium)
            }
        }
        if (detail.formulaNote.isNotBlank()) {
            Label("Особое правило / условный бонус")
            Text(detail.formulaNote, color = p.text2, style = MaterialTheme.typography.bodySmall)
        }
        if (detail.modifier != 0) Text("Постоянная поправка: ${if (detail.modifier > 0) "+" else ""}${detail.modifier}", color = p.text2, style = MaterialTheme.typography.bodySmall)
        Label("Правила")
        Text("Без обучения: ${detail.untrained.label}", color = p.text2, style = MaterialTheme.typography.bodySmall)
        Text("Автоуспех 6: ${detail.auto6.ifBlank { "—" }} · Автоуспех 12: ${detail.auto12.ifBlank { "—" }}", color = p.text2, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun Label(text: String) {
    Text(text.uppercase(), color = FuryColors.current.muted, style = MaterialTheme.typography.labelSmall)
}
