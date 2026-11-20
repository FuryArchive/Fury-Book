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
import androidx.compose.ui.unit.dp
import com.furybook.dubl.model.KnownSpell
import com.furybook.dubl.model.MagicSchoolCatalog
import com.furybook.dubl.ui.sheet.SheetLayout
import com.furybook.ui.components.FuryDialogAction
import com.furybook.ui.components.FuryDialogScaffold
import com.furybook.ui.components.FuryTextField
import com.furybook.ui.theme.FuryColors
import com.furybook.ui.theme.FurySpacing

/** «+ Своё заклинание» / «Изменить»: every field of the spell, explicit «Сохранить». */
@Composable
internal fun EditorSpellFormDialog(host: DublEditorHost, state: EditorState, base: KnownSpell?, layout: SheetLayout, onDismiss: () -> Unit) {
    val p = FuryColors.current
    val control = editorControl(layout)
    var form by remember(base?.uid) { mutableStateOf(editorSpellForm(base)) }
    val errors = form.errors()
    val shown = if (form != editorSpellForm(base)) errors else emptyList()
    val number = KeyboardOptions(keyboardType = KeyboardType.Number)
    FuryDialogScaffold(
        title = if (base == null) "Своё заклинание" else base.name,
        onDismiss = onDismiss,
        primary = FuryDialogAction("Сохранить", { if (editorSaveSpell(host, state, form, base)) onDismiss() }, enabled = errors.isEmpty()),
        secondary = FuryDialogAction("Отмена", onDismiss),
    ) {
        FuryTextField(form.name, { form = form.copy(name = it) }, "Название", error = shown.firstOrNull { it == "Нужно название" })
        FuryTextField(form.school, { form = form.copy(school = it) }, "Школа")
        Row(horizontalArrangement = Arrangement.spacedBy(FurySpacing.sm)) {
            FuryTextField(form.cost, { form = form.copy(cost = it.filter(Char::isDigit).take(6)) }, "Мана", Modifier.weight(1f), keyboardOptions = number,
                error = shown.firstOrNull { it.startsWith("Мана") })
            FuryTextField(form.manaText, { form = form.copy(manaText = it) }, "Текст маны", Modifier.weight(1f))
        }
        FuryTextField(form.time, { form = form.copy(time = it) }, "Время")
        FuryTextField(form.range, { form = form.copy(range = it) }, "Дистанция")
        FuryTextField(form.area, { form = form.copy(area = it) }, "Область")
        FuryTextField(form.action, { form = form.copy(action = it) }, "Проверка")
        FuryTextField(form.duration, { form = form.copy(duration = it) }, "Длительность")
        FuryTextField(form.description, { form = form.copy(description = it) }, "Описание", singleLine = false)
        FuryTextField(form.enhancement, { form = form.copy(enhancement = it) }, "Усиление", singleLine = false)
        FuryTextField(form.xpOverride, { form = form.copy(xpOverride = it.filter(Char::isDigit).take(6)) }, "Опыт вручную (пусто — по мане)", keyboardOptions = number,
            error = shown.firstOrNull { it.startsWith("Опыт") })
        listOf(
            Triple("Изучено", form.learned) { v: Boolean -> form = form.copy(learned = v) },
            Triple("Правило неполное", form.incomplete) { v: Boolean -> form = form.copy(incomplete = v) },
        ).forEach { (label, on, set) ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(FurySpacing.sm)) {
                Text(label, Modifier.weight(1f), color = p.text2, style = MaterialTheme.typography.bodyMedium)
                EditorChip(if (on) "Да" else "Нет", on, { set(!on) }, control, role = Role.Switch)
            }
        }
    }
}

/** «+ Школа» (pick from the rulebook list) or a school's detail (power, note, «Удалить»). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun EditorSchoolDialog(host: DublEditorHost, state: EditorState, index: Int?, layout: SheetLayout, onDismiss: () -> Unit) {
    val p = FuryColors.current
    val control = editorControl(layout)
    val existing = index?.let { host.active.magic.schools.getOrNull(it) }
    val addable = remember(index) { editorSchoolChoices(host.active, index) }
    var form by remember(index) {
        mutableStateOf(
            EditorSchoolForm(
                name = existing?.let { MagicSchoolCatalog.canonicalize(it.name) } ?: addable.firstOrNull().orEmpty(),
                power = (existing?.rank ?: 1).toString(),
                note = existing?.note.orEmpty(),
            ),
        )
    }
    FuryDialogScaffold(
        title = existing?.name ?: "Школа",
        subtitle = if (existing == null) "Школы берутся из книги правил — только их правила сохраняют и оценивают." else null,
        onDismiss = onDismiss,
        primary = FuryDialogAction("Сохранить", { if (editorSaveSchool(host, state, index, form)) onDismiss() }, enabled = form.name.isNotBlank()),
        secondary = FuryDialogAction("Отмена", onDismiss),
        destructive = if (index != null) FuryDialogAction("Удалить", { editorRemoveSchool(host, state, index); onDismiss() }) else null,
    ) {
        if (addable.isEmpty()) Text("Все школы книги уже добавлены.", color = p.muted, style = MaterialTheme.typography.bodySmall)
        // In a school's detail the list also offers the other free schools: that is how a school is renamed.
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            addable.forEach { school -> EditorChip(school, form.name == school, { form = form.copy(name = school) }, control, role = Role.RadioButton) }
        }
        FuryTextField(form.power, { form = form.copy(power = it.filter(Char::isDigit).take(6)) }, "Сила школы",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        FuryTextField(form.note, { form = form.copy(note = it) }, "Примечание", singleLine = false)
    }
}


/** «+ Свой предмет» / «Изменить»: name, quantity, load, category, section, description and fields as «Название: значение» lines. */
@Composable
internal fun EditorGearFormDialog(host: DublEditorHost, state: EditorState, base: com.furybook.dubl.model.GearItem?, layout: SheetLayout, onDismiss: () -> Unit) {
    val p = FuryColors.current
    val control = editorControl(layout)
    var form by remember(base?.uid) { mutableStateOf(editorGearForm(base)) }
    val errors = form.errors()
    val shown = if (form != editorGearForm(base)) errors else emptyList()
    FuryDialogScaffold(
        title = if (base == null) "Свой предмет" else base.name,
        onDismiss = onDismiss,
        primary = FuryDialogAction("Сохранить", { if (editorSaveGear(host, state, form, base)) onDismiss() }, enabled = errors.isEmpty()),
        secondary = FuryDialogAction("Отмена", onDismiss),
    ) {
        FuryTextField(form.name, { form = form.copy(name = it) }, "Название", error = shown.firstOrNull { it == "Нужно название" })
        Row(horizontalArrangement = Arrangement.spacedBy(FurySpacing.sm)) {
            FuryTextField(form.quantity, { form = form.copy(quantity = it.filter(Char::isDigit).take(6)) }, "Количество", Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), error = shown.firstOrNull { it.startsWith("Количество") })
            FuryTextField(form.load, { form = form.copy(load = it.filter { c -> c.isDigit() || c == '.' || c == ',' }) }, "Нагрузка (треб. силы)", Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), error = shown.firstOrNull { it.startsWith("Нагрузка") })
        }
        FuryTextField(form.category, { form = form.copy(category = it) }, "Категория")
        FuryTextField(form.section, { form = form.copy(section = it) }, "Раздел")
        FuryTextField(form.fields, { form = form.copy(fields = it) }, "Поля: «Название: значение», по одному в строке", singleLine = false)
        FuryTextField(form.description, { form = form.copy(description = it) }, "Описание", singleLine = false)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(FurySpacing.sm)) {
            Text("С собой", Modifier.weight(1f), color = p.text2, style = MaterialTheme.typography.bodyMedium)
            EditorChip(if (form.carried) "Да" else "Нет", form.carried, { form = form.copy(carried = !form.carried) }, control, role = Role.Switch)
        }
    }
}
