package com.furybook.dubl.ui.sheet

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import com.furybook.ui.components.FuryButton
import com.furybook.ui.components.FuryButtonStyle
import com.furybook.ui.components.FuryDialogAction
import com.furybook.ui.components.FuryDialogScaffold
import com.furybook.ui.components.FuryTextField
import com.furybook.ui.theme.DublAccentStrong
import com.furybook.ui.theme.DublGold
import com.furybook.ui.theme.DublMuted
import com.furybook.ui.theme.DublSurfaceRaised
import com.furybook.ui.theme.DublText
import com.furybook.ui.theme.FuryShapes
import com.furybook.ui.theme.FurySpacing
import com.furybook.ui.theme.furyOverlineStyle

/**
 * XP and ability points: total, creation XP, adjustment (may be negative) and the ability-points override
 * (blank = book recommendation), the spend breakdown, warnings, and finishing / reopening creation.
 */
@Composable
fun SheetEconomyDialog(view: SheetEconomyView, host: DublSheetEditHost, onDismiss: () -> Unit, onSaved: (previousTotal: Int) -> Unit = {}) {
    var total by remember(view.total) { mutableStateOf(view.total.toString()) }
    var creation by remember(view.creation) { mutableStateOf(view.creation.toString()) }
    var adjustment by remember(view.adjustment) { mutableStateOf(view.adjustment.toString()) }
    var abilityPoints by remember(view.abilityPointsOverride) { mutableStateOf(view.abilityPointsOverride?.toString().orEmpty()) }
    val form = sheetEconomyForm(total, creation, adjustment, abilityPoints)
    val number = KeyboardOptions(keyboardType = KeyboardType.Number)
    FuryDialogScaffold(
        title = "Опыт",
        subtitle = "Потрачено ${view.spent} · осталось ${view.remaining}",
        onDismiss = onDismiss,
        primary = FuryDialogAction("Сохранить", {
            form?.let {
                host.setEconomy(it.total, it.creation, it.adjustment, it.abilityPointsOverride)
                onSaved(view.total)
            }
            onDismiss()
        }, enabled = form != null),
        secondary = FuryDialogAction("Отмена", onDismiss),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(FurySpacing.sm)) {
            FuryTextField(total, { total = numericInput(it, allowNegative = false) }, "Всего опыта", Modifier.weight(1f), keyboardOptions = number)
            FuryTextField(creation, { creation = numericInput(it, allowNegative = false) }, "На создание", Modifier.weight(1f), keyboardOptions = number)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(FurySpacing.sm)) {
            FuryTextField(adjustment, { adjustment = numericInput(it) }, "Поправка", Modifier.weight(1f), keyboardOptions = number)
            FuryTextField(abilityPoints, { abilityPoints = numericInput(it, allowNegative = false) }, "ОС вручную", Modifier.weight(1f), keyboardOptions = number)
        }
        if (form == null) Text("Проверьте числа: опыт и ОС — целые, не меньше нуля.", color = DublAccentStrong, style = MaterialTheme.typography.bodySmall)
        Text("Рекомендация книги: ${view.recommendedAbilityPoints} ОС по стартовому XP. Пустое поле ОС — по рекомендации.", color = DublMuted, style = MaterialTheme.typography.bodySmall)
        Column(
            Modifier.fillMaxWidth().clip(FuryShapes.panel).background(DublSurfaceRaised).padding(FurySpacing.md),
            verticalArrangement = Arrangement.spacedBy(FurySpacing.xs),
        ) {
            Text("РАСПРЕДЕЛЕНИЕ ОПЫТА", color = DublMuted, style = furyOverlineStyle())
            view.lines.forEach { (label, xp) ->
                Row(Modifier.fillMaxWidth()) {
                    Text(label, Modifier.weight(1f), color = DublText, style = MaterialTheme.typography.bodyMedium)
                    Text(xp.toString(), color = DublText, style = MaterialTheme.typography.bodyMedium)
                }
            }
            Text(
                "Опыт: потрачено ${view.spent}, осталось ${view.remaining}",
                color = if (view.remaining < 0) DublAccentStrong else DublText, style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "ОС: потрачено ${view.abilityPointsSpent} из ${view.abilityPointsBudget}, осталось ${view.abilityPointsRemaining}",
                color = if (view.abilityPointsRemaining < 0) DublAccentStrong else DublText, style = MaterialTheme.typography.bodyMedium,
            )
        }
        view.unpricedSpellsWarning?.let { Text(it, color = DublGold, style = MaterialTheme.typography.bodySmall) }
        view.selfTaughtNote?.let { Text(it, color = DublMuted, style = MaterialTheme.typography.bodySmall) }
        if (view.creationComplete) {
            FuryButton("Вернуться в создание", { host.reopenCreation() }, Modifier.fillMaxWidth(), style = FuryButtonStyle.Ghost)
        } else {
            FuryButton("Завершить создание", { host.completeCreation() }, Modifier.fillMaxWidth(), style = FuryButtonStyle.Ghost)
        }
    }
}
