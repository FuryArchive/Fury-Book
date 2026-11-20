package com.furybook.dubl.ui.sheet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.furybook.dubl.model.CharacterSheetResourceId
import com.furybook.dubl.model.ChiRules
import com.furybook.dubl.ui.editor.DublEditorHost
import com.furybook.ui.components.FuryButton
import com.furybook.ui.components.FuryButtonStyle
import com.furybook.ui.components.FuryDialogAction
import com.furybook.ui.components.FuryDialogScaffold
import com.furybook.ui.components.FuryTextField
import com.furybook.ui.theme.DublGold
import com.furybook.ui.theme.DublMuted
import com.furybook.ui.theme.DublText
import com.furybook.ui.theme.FurySpacing
import com.furybook.ui.theme.furyOverlineStyle

/** One ЦИ technique on the sheet's ЦИ panel; [reason] says why it can't be used now (empty when it can). */
data class SheetChiTechnique(
    val id: String,
    val name: String,
    val school: String,
    val cost: Int,
    val action: String,
    val effect: String,
    val canUse: Boolean,
    val reason: String,
)

data class SheetChiPanel(val current: Int, val maximum: Int, val techniques: List<SheetChiTechnique>)

/** The ЦИ meter's panel: current/max and every technique with what [ChiRules] says about using it now. */
fun sheetChiPanel(host: DublEditorHost): SheetChiPanel {
    val c = host.active
    val rules = ChiRules(c, host.developmentCatalog)
    return SheetChiPanel(
        current = c.chiCurrent,
        maximum = c.chiMaximum,
        techniques = host.chiCatalog.techniques.map { technique ->
            val availability = rules.availability(technique)
            SheetChiTechnique(
                technique.id, technique.name, technique.school, technique.chiCost, technique.action, technique.effect,
                availability.canUse, availability.reason,
            )
        },
    )
}

/** «Использовать»: spends the technique's ЦИ cost; false (nothing spent) when the rules say it can't be used now. */
fun sheetUseChiTechnique(host: DublEditorHost, techniqueId: String): Boolean {
    val technique = host.chiCatalog.techniques.firstOrNull { it.id == techniqueId } ?: return false
    val availability = ChiRules(host.active, host.developmentCatalog).availability(technique)
    if (!availability.canUse) return false
    host.changeResource(CharacterSheetResourceId.CHI, -availability.chiCost)
    return true
}

/**
 * Tap the ЦИ meter: ±1, the «Количество» amount field, «Восстановить» (the pack's restore), then «Приёмы ЦИ»
 * with «Использовать». Replaces the generic resource dialog for ЦИ; [vital] is the meter that was tapped.
 */
@Composable
internal fun SheetChiDialog(vital: SheetVital, host: DublEditorHost, onDismiss: () -> Unit) {
    val panel = sheetChiPanel(host)
    var amountText by remember { mutableStateOf("1") }
    val amount = parseIntField(amountText)?.takeIf { it > 0 }
    val live = vital.copy(current = panel.current, maximum = panel.maximum)
    fun change(delta: Int) { if (delta != 0) host.changeResource(CharacterSheetResourceId.CHI, delta) }
    FuryDialogScaffold(
        title = vital.label,
        subtitle = "Сейчас ${panel.current} из ${panel.maximum}",
        onDismiss = onDismiss,
        primary = FuryDialogAction("Готово", onDismiss),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(FurySpacing.sm)) {
            FuryButton("−1", { change(-1) }, Modifier.weight(1f), enabled = panel.current > 0)
            FuryButton("+1", { change(1) }, Modifier.weight(1f), enabled = panel.current < panel.maximum)
        }
        FuryTextField(
            amountText, { amountText = numericInput(it, allowNegative = false, maxLength = 5) }, "Количество",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            error = if (amount == null) "Введите целое число больше нуля" else null,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(FurySpacing.sm)) {
            FuryButton("Уменьшить", { change(vitalAmountDelta(VitalAction.DAMAGE, amount ?: 1, live).coerceAtLeast(-panel.current)) }, Modifier.weight(1f),
                enabled = amount != null && panel.current > 0)
            FuryButton("Увеличить", { change(vitalAmountDelta(VitalAction.HEAL, amount ?: 1, live)) }, Modifier.weight(1f),
                enabled = amount != null && panel.current < panel.maximum)
        }
        FuryButton("Восстановить", { host.restoreMountedResource(CharacterSheetResourceId.CHI) }, Modifier.fillMaxWidth(),
            style = FuryButtonStyle.Ghost, enabled = panel.current < panel.maximum)
        Text("ПРИЁМЫ ЦИ", color = DublMuted, style = furyOverlineStyle())
        if (panel.techniques.isEmpty()) Text("Приёмов ЦИ в каталоге нет.", color = DublMuted, style = MaterialTheme.typography.bodySmall)
        // Text full width, the button under it: on a phone a side button squeezes long effects into a narrow column.
        panel.techniques.forEach { technique ->
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("${technique.name} · ${technique.school}", color = DublText, style = MaterialTheme.typography.labelLarge)
                Text("${technique.cost} ЦИ · ${technique.action}", color = DublGold, style = MaterialTheme.typography.labelSmall)
                if (technique.effect.isNotBlank()) Text(technique.effect, color = DublText, style = MaterialTheme.typography.bodySmall)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(FurySpacing.sm)) {
                    FuryButton("Использовать", { sheetUseChiTechnique(host, technique.id) }, enabled = technique.canUse)
                    if (!technique.canUse) Text(technique.reason, Modifier.weight(1f), color = DublMuted, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
