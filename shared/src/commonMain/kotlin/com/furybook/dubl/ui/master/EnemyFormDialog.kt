package com.furybook.dubl.ui.master

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import com.furybook.dubl.master.EnemyForm
import com.furybook.dubl.master.MasterEnemy
import com.furybook.ui.components.FuryDialogAction
import com.furybook.ui.components.FuryDialogScaffold
import com.furybook.ui.components.FuryTextField
import com.furybook.ui.theme.DublDanger
import com.furybook.ui.theme.FurySpacing

/** New or edited enemy template: typed numbers only, no derivation from attributes. */
@Composable
internal fun EnemyFormDialog(initial: MasterEnemy?, onDismiss: () -> Unit, onSave: (MasterEnemy) -> Unit) {
    var form by remember(initial) { mutableStateOf(initial?.let(EnemyForm::from) ?: EnemyForm()) }
    var tried by remember { mutableStateOf(false) }
    val errors = form.errors()
    val number = KeyboardOptions(keyboardType = KeyboardType.Number)
    FuryDialogScaffold(
        title = if (initial == null) "Новый враг" else "Изменить врага",
        onDismiss = onDismiss,
        primary = FuryDialogAction("Сохранить", {
            tried = true
            if (errors.isEmpty()) onSave(form.toEnemy(initial ?: MasterEnemy(id = masterId(), name = "")))
        }),
        secondary = FuryDialogAction("Отмена", onDismiss),
    ) {
        FuryTextField(form.name, { form = form.copy(name = it) }, "Имя")
        Row(horizontalArrangement = Arrangement.spacedBy(FurySpacing.sm)) {
            FuryTextField(form.defense, { form = form.copy(defense = it) }, "Защита", Modifier.weight(1f), keyboardOptions = number)
            FuryTextField(form.reflexes, { form = form.copy(reflexes = it) }, "Рефлексы", Modifier.weight(1f), keyboardOptions = number)
            FuryTextField(form.initiative, { form = form.copy(initiative = it) }, "Инициатива", Modifier.weight(1f), keyboardOptions = number)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(FurySpacing.sm)) {
            FuryTextField(form.health, { form = form.copy(health = it) }, "Здоровье", Modifier.weight(1f), keyboardOptions = number)
            FuryTextField(form.endurance, { form = form.copy(endurance = it) }, "Выносливость", Modifier.weight(1f), keyboardOptions = number)
        }
        FuryTextField(form.attacks, { form = form.copy(attacks = it) }, "Атаки и способности", singleLine = false)
        FuryTextField(form.notes, { form = form.copy(notes = it) }, "Заметки", singleLine = false)
        if (tried && errors.isNotEmpty()) Text(errors.joinToString(" · "), color = DublDanger, style = MaterialTheme.typography.bodySmall)
    }
}
