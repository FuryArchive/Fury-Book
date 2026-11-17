package com.furybook.dubl.ui.sheet

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.furybook.ui.components.FuryChipGroup
import com.furybook.ui.components.FuryChoice
import com.furybook.ui.components.FuryDialogAction
import com.furybook.ui.components.FuryDialogScaffold
import com.furybook.ui.components.FuryStepper
import com.furybook.ui.components.FuryTextField
import com.furybook.ui.theme.DublMuted
import com.furybook.ui.theme.furyOverlineStyle

/** Name, concept, size (1..10), legs (≥ 2) and mana. A blank name saves as «Новый персонаж» (application rule). */
@Composable
fun SheetIdentityDialog(host: DublSheetEditHost, onDismiss: () -> Unit, onSaved: () -> Unit = {}) {
    val character = host.active
    var name by remember(character.id) { mutableStateOf(character.name) }
    var concept by remember(character.id) { mutableStateOf(character.concept) }
    var size by remember(character.id) { mutableStateOf(character.size) }
    var legs by remember(character.id) { mutableStateOf(character.legs) }
    var mana by remember(character.id) { mutableStateOf(character.manaEnabled) }
    FuryDialogScaffold(
        title = "Персонаж",
        subtitle = "Имя, концепт и тело",
        onDismiss = onDismiss,
        primary = FuryDialogAction("Сохранить", {
            host.setIdentity(name, concept, size, legs, mana)
            onSaved()
            onDismiss()
        }),
        secondary = FuryDialogAction("Отмена", onDismiss),
    ) {
        FuryTextField(name, { name = it.take(120) }, "Имя")
        FuryTextField(concept, { concept = it.take(200) }, "Концепт")
        LabeledRow("Размер") { FuryStepper(size, { size = it }, min = 1, max = 10, decreaseLabel = "Уменьшить размер", increaseLabel = "Увеличить размер") }
        LabeledRow("Ног") { FuryStepper(legs, { legs = it }, min = 2, max = 99, decreaseLabel = "Меньше ног", increaseLabel = "Больше ног") }
        Text("МАНА", color = DublMuted, style = furyOverlineStyle())
        FuryChipGroup(
            choices = listOf(FuryChoice(true, "Есть"), FuryChoice(false, "Нет")),
            selected = mana,
            onSelect = { mana = it },
            columns = 2,
        )
    }
}

@Composable
internal fun LabeledRow(label: String, control: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label.uppercase(), Modifier.weight(1f), color = DublMuted, style = furyOverlineStyle())
        control()
    }
}
