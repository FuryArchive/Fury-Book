package com.furybook.dubl.ui.sheet

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.furybook.dubl.model.CharacterConditionId
import com.furybook.dubl.model.CustomCondition
import com.furybook.ui.components.FuryButton
import com.furybook.ui.components.FuryButtonStyle
import com.furybook.ui.components.FuryDialogAction
import com.furybook.ui.components.FuryDialogScaffold
import com.furybook.ui.components.FuryTextField
import com.furybook.ui.theme.DublAccent
import com.furybook.ui.theme.DublAccentSoft
import com.furybook.ui.theme.DublMuted
import com.furybook.ui.theme.DublSurfaceRaised
import com.furybook.ui.theme.DublText
import com.furybook.ui.theme.FuryBorderStrong
import com.furybook.ui.theme.FuryMinTouch
import com.furybook.ui.theme.FuryShapes
import com.furybook.ui.theme.FurySpacing
import com.furybook.ui.theme.furyOverlineStyle

private sealed interface ConditionEditor {
    data class Standard(val condition: CharacterConditionId) : ConditionEditor
    data class Custom(val condition: CustomCondition?) : ConditionEditor
}

/**
 * Conditions popup: every rulebook condition with its description and a toggle, a local title/description
 * override with «К рулбуку», and the player's own conditions (add, edit, activate, delete). Слабость that the
 * sheet applies at 0 endurance is shown locked, as the old sheets did.
 */
@Composable
fun SheetConditionsDialog(host: DublSheetEditHost, onDismiss: () -> Unit, onChanged: () -> Unit = {}) {
    var editor by remember { mutableStateOf<ConditionEditor?>(null) }
    val extras = host.extras
    val autoWeakness = host.active.enduranceCurrent == 0 && CharacterConditionId.WEAKNESS !in extras.activeConditions
    when (val current = editor) {
        is ConditionEditor.Standard -> StandardOverrideEditor(host, current.condition, onClose = { editor = null })
        is ConditionEditor.Custom -> CustomConditionEditor(host, current.condition, onClose = { editor = null; onChanged() })
        null -> FuryDialogScaffold(
            title = "Состояния",
            subtitle = "Отметьте действующие состояния",
            onDismiss = onDismiss,
            primary = FuryDialogAction("Готово", onDismiss),
            secondary = FuryDialogAction("Своё состояние", { editor = ConditionEditor.Custom(null) }),
        ) {
            CharacterConditionId.entries.forEach { condition ->
                val locked = condition == CharacterConditionId.WEAKNESS && autoWeakness
                ConditionRow(
                    title = extras.conditionOverrides[condition]?.title ?: condition.title,
                    description = extras.conditionOverrides[condition]?.description ?: host.conditionCatalog.summary(condition),
                    active = condition in extras.activeConditions || locked,
                    locked = locked,
                    onToggle = { host.toggleCondition(condition); onChanged() },
                    onEdit = { editor = ConditionEditor.Standard(condition) },
                )
            }
            if (extras.customConditions.isNotEmpty()) {
                Text("СВОИ СОСТОЯНИЯ", color = DublMuted, style = furyOverlineStyle())
                extras.customConditions.forEach { custom ->
                    ConditionRow(
                        title = custom.title,
                        description = custom.description,
                        active = custom.active,
                        locked = false,
                        onToggle = { host.setCustomConditionActive(custom.id, !custom.active); onChanged() },
                        onEdit = { editor = ConditionEditor.Custom(custom) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ConditionRow(title: String, description: String, active: Boolean, locked: Boolean, onToggle: () -> Unit, onEdit: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(FuryShapes.control)
            .background(if (active) DublAccentSoft else DublSurfaceRaised)
            .border(1.dp, if (active) DublAccent else FuryBorderStrong, FuryShapes.control)
            .then(if (locked) Modifier else Modifier.clickable(role = Role.Checkbox, onClickLabel = if (active) "Снять" else "Отметить") { onToggle() })
            .defaultMinSize(minHeight = FuryMinTouch)
            .padding(horizontal = FurySpacing.md, vertical = FurySpacing.sm),
        horizontalArrangement = Arrangement.spacedBy(FurySpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(if (locked) "$title · авто" else title, color = DublText, style = MaterialTheme.typography.titleSmall)
            if (description.isNotBlank()) Text(description, color = DublMuted, style = MaterialTheme.typography.bodySmall)
        }
        FuryButton("Изменить", onEdit, style = FuryButtonStyle.Ghost)
    }
}

@Composable
private fun StandardOverrideEditor(host: DublSheetEditHost, condition: CharacterConditionId, onClose: () -> Unit) {
    val existing = host.extras.conditionOverrides[condition]
    var title by remember(condition) { mutableStateOf(existing?.title ?: condition.title) }
    var description by remember(condition) { mutableStateOf(existing?.description ?: host.conditionCatalog.summary(condition)) }
    FuryDialogScaffold(
        title = condition.title,
        subtitle = "Своё название и описание для этого персонажа",
        onDismiss = onClose,
        primary = FuryDialogAction("Сохранить", {
            host.setConditionOverride(condition, title.trim().ifBlank { null }, description.trim().ifBlank { null })
            onClose()
        }),
        secondary = FuryDialogAction("Отмена", onClose),
        destructive = existing?.let { FuryDialogAction("К рулбуку", { host.resetConditionOverride(condition); onClose() }) },
    ) {
        FuryTextField(title, { title = it.take(80) }, "Название")
        FuryTextField(description, { description = it.take(2000) }, "Описание", singleLine = false)
    }
}

@Composable
private fun CustomConditionEditor(host: DublSheetEditHost, condition: CustomCondition?, onClose: () -> Unit) {
    var title by remember(condition?.id) { mutableStateOf(condition?.title.orEmpty()) }
    var description by remember(condition?.id) { mutableStateOf(condition?.description.orEmpty()) }
    FuryDialogScaffold(
        title = if (condition == null) "Своё состояние" else condition.title,
        subtitle = "Название и короткое описание",
        onDismiss = onClose,
        primary = FuryDialogAction("Сохранить", {
            if (condition == null) host.addCustomCondition(title.trim(), description.trim(), active = true)
            else host.updateCustomCondition(condition.id, title.trim(), description.trim())
            onClose()
        }, enabled = title.isNotBlank()),
        secondary = FuryDialogAction("Отмена", onClose),
        destructive = condition?.let { FuryDialogAction("Удалить", { host.removeCustomCondition(it.id); onClose() }) },
    ) {
        FuryTextField(title, { title = it.take(80) }, "Название", error = if (title.isBlank()) "Нужно название" else null)
        FuryTextField(description, { description = it.take(2000) }, "Описание", singleLine = false)
    }
}
