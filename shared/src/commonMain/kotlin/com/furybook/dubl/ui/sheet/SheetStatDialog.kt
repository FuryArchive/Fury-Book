package com.furybook.dubl.ui.sheet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.furybook.ui.components.FuryDialogAction
import com.furybook.ui.components.FuryDialogScaffold
import com.furybook.ui.components.FuryStepper
import com.furybook.ui.theme.DublMuted
import com.furybook.ui.theme.DublText
import com.furybook.ui.theme.FurySpacing
import com.furybook.ui.theme.furyOverlineStyle

/**
 * Formula popup for a metric (Android StatInfoSheet). [onRoll] adds the roll as the primary action;
 * [sizeLegsEditor] adds Размер (1..10) and ног (≥ 2) steppers for the Размер popup.
 */
@Composable
fun SheetStatDialog(
    breakdown: SheetStatBreakdown,
    onRoll: (() -> Unit)?,
    sizeLegsEditor: Boolean,
    host: DublSheetEditHost,
    onDismiss: () -> Unit,
) {
    FuryDialogScaffold(
        title = breakdown.title,
        subtitle = breakdown.formula,
        onDismiss = onDismiss,
        primary = onRoll?.let { roll -> FuryDialogAction("Бросить", { onDismiss(); roll() }) },
        secondary = FuryDialogAction("Закрыть", onDismiss),
    ) {
        Text(breakdown.value, color = DublText, style = MaterialTheme.typography.displaySmall)
        Column(verticalArrangement = Arrangement.spacedBy(FurySpacing.xs)) {
            breakdown.lines.forEach { line -> Text(line, color = DublText, style = MaterialTheme.typography.bodyMedium) }
        }
        breakdown.note?.let { Text(it, color = DublMuted, style = MaterialTheme.typography.bodySmall) }
        if (sizeLegsEditor) {
            val character = host.active
            fun save(size: Int, legs: Int) = host.setIdentity(character.name, character.concept, size, legs, character.manaEnabled)
            StepperRow("Размер") {
                FuryStepper(character.size, { save(it, character.legs) }, min = 1, max = 10, decreaseLabel = "Уменьшить размер", increaseLabel = "Увеличить размер")
            }
            StepperRow("Ног") {
                FuryStepper(character.legs, { save(character.size, it) }, min = 2, decreaseLabel = "Меньше ног", increaseLabel = "Больше ног")
            }
        }
    }
}

@Composable
private fun StepperRow(label: String, stepper: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label.uppercase(), Modifier.weight(1f), color = DublMuted, style = furyOverlineStyle())
        stepper()
    }
}
