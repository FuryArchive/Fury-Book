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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import com.furybook.dubl.model.CharacterSheetResourceId
import com.furybook.ui.components.FuryButton
import com.furybook.ui.components.FuryButtonStyle
import com.furybook.ui.components.FuryDialogAction
import com.furybook.ui.components.FuryDialogScaffold
import com.furybook.ui.components.FuryTextField
import com.furybook.ui.theme.DublMuted
import com.furybook.ui.theme.FurySpacing
import com.furybook.ui.theme.furyOverlineStyle

enum class VitalAction { DAMAGE, HEAL, RESTORE_ALL }

/**
 * The step to apply for [action]. Damage is never clamped (health may go negative); healing stops at the
 * maximum; «Восстановить всё» brings the resource to its maximum — the old Android HealthControlSheet maths.
 */
fun vitalAmountDelta(action: VitalAction, amount: Int, vital: SheetVital): Int = when (action) {
    VitalAction.DAMAGE -> -amount
    VitalAction.HEAL -> (vital.current + amount).coerceAtMost(vital.maximum) - vital.current
    VitalAction.RESTORE_ALL -> vital.maximum - vital.current
}

/**
 * Tap-a-vital popup: amount with damage/heal (decrease/increase for other resources), restore all,
 * the maximum override for health/endurance/mana (blank = formula), restore for pack meters, and
 * name/maximum/current editing plus removal for custom resources. [onApplied] reports a resource step
 * so the platform undo toast can show it.
 */
@Composable
fun SheetVitalDialog(
    vital: SheetVital,
    host: DublSheetEditHost,
    onDismiss: () -> Unit,
    onApplied: (SheetVital, Int) -> Unit = { _, _ -> },
) {
    var amountText by remember(vital.id) { mutableStateOf("1") }
    val amount = parseIntField(amountText)?.takeIf { it > 0 }
    val isHealth = vital.resource == CharacterSheetResourceId.HEALTH
    val custom = vital.customUid
    fun apply(action: VitalAction) {
        val delta = vitalAmountDelta(action, amount ?: 1, vital)
        if (delta == 0) return
        if (custom != null) host.changeCustomResource(custom, delta) else vital.resource?.let { host.changeResource(it, delta) }
        onApplied(vital, delta)
    }
    FuryDialogScaffold(
        title = vital.label,
        subtitle = "Сейчас ${vital.current} из ${vital.maximum}",
        onDismiss = onDismiss,
        primary = FuryDialogAction("Готово", onDismiss),
        destructive = custom?.let { uid -> FuryDialogAction("Удалить", { host.removeCustomResource(uid); onDismiss() }) },
    ) {
        FuryTextField(
            amountText, { amountText = numericInput(it, allowNegative = false, maxLength = 5) }, "Количество",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            error = if (amount == null) "Введите целое число больше нуля" else null,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(FurySpacing.sm)) {
            FuryButton(
                if (isHealth) "Получить урон" else "Уменьшить", { apply(VitalAction.DAMAGE) }, Modifier.weight(1f),
                enabled = amount != null && vitalCanDecrease(vital),
            )
            FuryButton(
                if (isHealth) "Лечение" else "Увеличить", { apply(VitalAction.HEAL) }, Modifier.weight(1f),
                enabled = amount != null && vital.current < vital.maximum,
            )
        }
        FuryButton(
            if (isHealth) "Восстановить всё здоровье" else "Восстановить всё", { apply(VitalAction.RESTORE_ALL) },
            Modifier.fillMaxWidth(), style = FuryButtonStyle.Ghost, enabled = vital.current != vital.maximum,
        )
        if (vital.restoreable && vital.resource != null) {
            FuryButton("Восстановить по правилам пакета", { host.restoreMountedResource(vital.resource) }, Modifier.fillMaxWidth(), style = FuryButtonStyle.Ghost)
        }
        when {
            custom != null -> SheetCustomResourceForm(
                initialName = vital.label, initialMaximum = vital.maximum, initialCurrent = vital.current,
                saveLabel = "Сохранить ресурс",
                onSave = { name, maximum, current -> host.updateCustomResource(custom, name, current, maximum) },
            )
            vital.resource in MAXIMUM_EDITABLE -> MaximumOverrideField(vital, host)
        }
    }
}

private val MAXIMUM_EDITABLE = setOf(CharacterSheetResourceId.HEALTH, CharacterSheetResourceId.ENDURANCE, CharacterSheetResourceId.MANA)

@Composable
private fun MaximumOverrideField(vital: SheetVital, host: DublSheetEditHost) {
    val override = when (vital.resource) {
        CharacterSheetResourceId.HEALTH -> host.active.healthMaximumOverride
        CharacterSheetResourceId.ENDURANCE -> host.active.enduranceMaximumOverride
        else -> host.active.manaMaximumOverride
    }
    var text by remember(vital.id, override) { mutableStateOf(override?.toString().orEmpty()) }
    val parsed = parseOptionalIntField(text)
    fun save(value: Int?) = when (vital.resource) {
        CharacterSheetResourceId.HEALTH -> host.setHealthMaximumOverride(value)
        CharacterSheetResourceId.ENDURANCE -> host.setEnduranceMaximumOverride(value)
        else -> host.setManaMaximumOverride(value)
    }
    Column(verticalArrangement = Arrangement.spacedBy(FurySpacing.sm)) {
        Text("МАКСИМУМ", color = DublMuted, style = furyOverlineStyle())
        FuryTextField(
            text, { text = numericInput(it, allowNegative = false) }, "Свой максимум (пусто — по формуле)",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            error = if (parsed == OptionalIntField.Invalid) "Только целое число" else null,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(FurySpacing.sm)) {
            FuryButton("По формуле", { text = ""; save(null) }, Modifier.weight(1f), style = FuryButtonStyle.Ghost, enabled = override != null)
            FuryButton(
                "Сохранить максимум",
                { when (parsed) { is OptionalIntField.Value -> save(parsed.value); OptionalIntField.Blank -> save(null); else -> Unit } },
                Modifier.weight(1f),
                enabled = parsed != OptionalIntField.Invalid,
            )
        }
    }
}

/** Name, maximum and current of a custom resource; current is kept within 0..maximum. Shared with the resources popup. */
@Composable
fun SheetCustomResourceForm(
    initialName: String,
    initialMaximum: Int,
    initialCurrent: Int,
    saveLabel: String,
    onSave: (name: String, maximum: Int, current: Int) -> Unit,
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    var maximumText by remember(initialMaximum) { mutableStateOf(initialMaximum.toString()) }
    var currentText by remember(initialCurrent) { mutableStateOf(initialCurrent.toString()) }
    val maximum = parseIntField(maximumText)?.takeIf { it > 0 }
    val current = parseIntField(currentText)?.takeIf { it >= 0 }
    Column(verticalArrangement = Arrangement.spacedBy(FurySpacing.sm)) {
        FuryTextField(name, { name = it.take(60) }, "Название", error = if (name.isBlank()) "Нужно название" else null)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(FurySpacing.sm)) {
            FuryTextField(
                maximumText, { maximumText = numericInput(it, allowNegative = false) }, "Максимум", Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                error = if (maximum == null) "Больше нуля" else null,
            )
            FuryTextField(
                currentText, { currentText = numericInput(it, allowNegative = false) }, "Сейчас", Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                error = if (current == null) "Ноль или больше" else null,
            )
        }
        FuryButton(
            saveLabel,
            { if (maximum != null && current != null) onSave(name.trim(), maximum, customResourceCurrent(current, maximum)) },
            Modifier.fillMaxWidth(),
            enabled = name.isNotBlank() && maximum != null && current != null,
        )
        Text("Текущее значение не больше максимума.", color = DublMuted, style = MaterialTheme.typography.bodySmall)
    }
}

/** The current value saved by the custom-resource form: clamped to 0..maximum. */
fun customResourceCurrent(current: Int, maximum: Int): Int = current.coerceIn(0, maximum.coerceAtLeast(0))
