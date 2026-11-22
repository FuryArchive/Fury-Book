package com.furybook.dubl.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import com.furybook.dubl.model.DevelopmentCostType
import com.furybook.dubl.model.DevelopmentEntry
import com.furybook.dubl.ui.sheet.SheetLayout
import com.furybook.ui.components.FuryDialogAction
import com.furybook.ui.components.FuryDialogScaffold
import com.furybook.ui.components.FuryTextField
import com.furybook.ui.theme.FuryColors
import com.furybook.ui.theme.FurySpacing

/**
 * The perk form: a new own perk, an own perk's record, or a local edit of a rulebook perk. Explicit
 * «Сохранить» (a multi-field record, not live typing); the button stays off while [errors] lists anything.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun EditorPerkFormDialog(
    host: DublEditorHost,
    state: EditorState,
    base: DevelopmentEntry,
    mode: EditorPerkFormMode,
    layout: SheetLayout,
    onDismiss: () -> Unit,
) {
    val p = FuryColors.current
    val control = editorControl(layout)
    var form by remember(base.id, mode) { mutableStateOf(editorPerkForm(base)) }
    val errors = form.errors()
    // Errors show once the form has been touched; a fresh blank form only keeps «Сохранить» off.
    val shown = if (form != editorPerkForm(base)) errors else emptyList()
    val number = KeyboardOptions(keyboardType = KeyboardType.Number)
    fun digits(text: String) = text.filter(Char::isDigit).take(6)
    FuryDialogScaffold(
        title = mode.title,
        subtitle = if (mode == EditorPerkFormMode.LOCAL_EDIT) "Правка хранится только у этого персонажа; «Сбросить к рулбуку» вернёт запись книги." else null,
        onDismiss = onDismiss,
        primary = FuryDialogAction("Сохранить", { if (editorSavePerk(host, state, form, base, mode)) onDismiss() }, enabled = errors.isEmpty()),
        secondary = FuryDialogAction("Отмена", onDismiss),
    ) {
        FuryTextField(form.name, { form = form.copy(name = it) }, "Название", error = shown.firstOrNull { it == "Введите название" })
        FuryTextField(form.section, { form = form.copy(section = it) }, "Раздел")
        FuryTextField(form.category, { form = form.copy(category = it) }, "Категория")
        EditorOverline("Цена в")
        Row(horizontalArrangement = Arrangement.spacedBy(FurySpacing.sm)) {
            EditorChip("XP", form.costType == DevelopmentCostType.XP, { form = form.copy(costType = DevelopmentCostType.XP) }, control, gold = true, role = Role.RadioButton)
            EditorChip("ОС", form.costType == DevelopmentCostType.ABILITY, { form = form.copy(costType = DevelopmentCostType.ABILITY) }, control, gold = true, role = Role.RadioButton)
        }
        FuryTextField(form.cost, { form = form.copy(cost = digits(it)) }, "Стоимость", keyboardOptions = number,
            error = shown.firstOrNull { it.startsWith("Стоимость") })
        FuryTextField(form.maxRank, { form = form.copy(maxRank = digits(it)) }, "Макс. ранг", keyboardOptions = number,
            error = shown.firstOrNull { it.startsWith("Макс. ранг") })
        FuryTextField(form.requirements, { form = form.copy(requirements = it) }, "Требования", singleLine = false)
        FuryTextField(form.benefit, { form = form.copy(benefit = it) }, "Эффект", singleLine = false)
        FuryTextField(form.notes, { form = form.copy(notes = it) }, "Особое / заметки", singleLine = false)
        FuryTextField(form.tags, { form = form.copy(tags = it) }, "Теги через запятую")
        FuryTextField(form.accessId, { form = form.copy(accessId = it) }, "ID родительской ветки")
        FuryTextField(form.abilityOptions, { form = form.copy(abilityOptions = it) }, "Варианты ОС: источник=цена; …",
            error = shown.filter { it.startsWith("Варианты ОС") }.joinToString("\n").ifEmpty { null })
        FuryTextField(form.mechanicsConflict, { form = form.copy(mechanicsConflict = it) }, "Механический конфликт", singleLine = false)
        FuryTextField(form.conflictNote, { form = form.copy(conflictNote = it) }, "Комментарий конфликта", singleLine = false)
        listOf(
            Triple("Правило неполное / спорное", form.incomplete) { v: Boolean -> form = form.copy(incomplete = v) },
            Triple("Повторяемая запись", form.repeatable) { v: Boolean -> form = form.copy(repeatable = v) },
            Triple("Корень совершенной ветки", form.perfectRoot) { v: Boolean -> form = form.copy(perfectRoot = v) },
        ).forEach { (label, on, set) ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(FurySpacing.sm)) {
                Text(label, Modifier.weight(1f), color = p.text2, style = MaterialTheme.typography.bodyMedium)
                EditorChip(if (on) "Да" else "Нет", on, { set(!on) }, control, role = Role.Switch)
            }
        }
        if (shown.isNotEmpty()) {
            FlowRow { Text(shown.joinToString(" · "), color = p.accText, style = MaterialTheme.typography.bodySmall) }
        }
    }
}
