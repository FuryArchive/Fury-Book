package com.furybook.desktop.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.furybook.dubl.model.AttributeId
import com.furybook.dubl.model.DevelopmentCatalog
import com.furybook.dubl.model.DublCharacter
import com.furybook.dubl.model.FlurryWeaponProfile
import com.furybook.dubl.model.ResolvedSkill
import com.furybook.dubl.model.RollMode
import com.furybook.dubl.model.RollResult
import com.furybook.dubl.model.RollContext
import com.furybook.dubl.model.SkillEffectCatalog
import com.furybook.dubl.model.SkillEffectRules
import com.furybook.dubl.model.allowedAttributes
import com.furybook.dubl.model.allowedSkillIds
import com.furybook.dubl.model.availableFollowUp
import com.furybook.dubl.model.compareRollToTarget
import com.furybook.dubl.model.resolve
import com.furybook.dubl.model.resolveAttack
import com.furybook.dubl.model.summary
import com.furybook.dubl.model.developmentNormalize
import com.furybook.dubl.model.resolveSkill
import com.furybook.dubl.model.rollCheck
import com.furybook.dubl.model.rollFollowUp
import com.furybook.dubl.model.rollPreset
import com.furybook.dubl.model.rule
import com.furybook.dubl.model.skillCalculationForRoll
import com.furybook.dubl.model.selectedTotals
import com.furybook.ui.theme.DublMuted

@Composable
fun SkillRollDialog(
    character: DublCharacter,
    skill: ResolvedSkill,
    preferredAttribute: AttributeId?,
    initialAttribute: AttributeId? = null,
    developmentCatalog: DevelopmentCatalog,
    effectCatalog: SkillEffectCatalog,
    onPreferredAttribute: (AttributeId) -> Unit,
    onDismiss: () -> Unit,
) {
    val configuredAttributes = skill.attributes.ifEmpty { listOf(skill.stockAttribute) }
    val attributes = initialAttribute?.let { listOf(it) } ?: configuredAttributes
    val startingAttribute = initialAttribute ?: preferredAttribute?.takeIf { it in configuredAttributes } ?: configuredAttributes.first()
    var attribute by remember(skill.id, initialAttribute, preferredAttribute) { mutableStateOf(startingAttribute) }
    var mode by remember(skill.id) { mutableStateOf(RollMode.NORMAL) }
    var effectCountText by remember(skill.id) { mutableStateOf("1") }
    var situationalText by remember(skill.id) { mutableStateOf("0") }
    var targetText by remember(skill.id) { mutableStateOf("") }
    var result by remember(skill.id) { mutableStateOf<RollResult?>(null) }
    var selectedEffects by remember(skill.id) { mutableStateOf(emptySet<String>()) }
    var attrMenu by remember { mutableStateOf(false) }

    val effects = SkillEffectRules(character, developmentCatalog, effectCatalog).forSkill(skill)
    val calculation = character.skillCalculationForRoll(skill, attribute)

    FuryDialog(
        onDismissRequest = onDismiss,
        title = { Text("Бросок: ${skill.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { attrMenu = true }) { Text("Характеристика: ${attribute.title}") }
                DropdownMenu(expanded = attrMenu, onDismissRequest = { attrMenu = false }) {
                    attributes.forEach { option ->
                        DropdownMenuItem(text = { Text(option.title) }, onClick = { attribute = option; attrMenu = false })
                    }
                }
                FurySegmentedControl(
                    options = RollMode.entries.map { it.title },
                    selectedIndex = RollMode.entries.indexOf(mode),
                    onSelected = { index -> mode = RollMode.entries[index] },
                )
                if (mode != RollMode.NORMAL) {
                    OutlinedTextField(
                        effectCountText,
                        { effectCountText = it.filter(Char::isDigit).take(2) },
                        label = { Text("Доп. костей") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        situationalText,
                        { situationalText = it.filter { c -> c.isDigit() || c == '-' }.take(4) },
                        label = { Text("Ситуативная поправка") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        targetText,
                        { targetText = it.filter { c -> c.isDigit() || c == '-' }.take(4) },
                        label = { Text("СЛ / результат") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                }

                if (effects.automaticContributions.isNotEmpty()) {
                    Text("Автоматически: " + effects.automaticContributions.joinToString { "${it.label} ${signed(it.value)}" })
                }
                effects.options.forEach { option ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = option.id in selectedEffects,
                            onCheckedChange = { checked -> selectedEffects = if (checked) selectedEffects + option.id else selectedEffects - option.id },
                        )
                        Column {
                            Text(option.label)
                            Text(option.description, color = DublMuted)
                        }
                    }
                }
                effects.reminders.forEach { reminder ->
                    Text("${reminder.sourceName}: ${reminder.effectText}", color = DublMuted)
                }
                androidx.compose.material3.Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(9.dp),
                    color = DesktopSurfaceInset.copy(alpha = .58f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, DesktopBorder.copy(alpha = .56f)),
                ) {
                    Column(Modifier.padding(horizontal = 11.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Расчёт броска", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                        Text(calculation.formulaText(skill), color = DublMuted, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                    }
                }
                result?.let { roll ->
                    val chosen = roll.chosenIndices.sorted().joinToString { roll.dice[it].toString() }
                    Text("Кости: ${roll.dice.joinToString()} · выбрано: $chosen")
                    Text("ИТОГ: ${roll.total}")
                    targetText.toIntOrNull()?.let { target ->
                        val comparison = compareRollToTarget(roll, target)
                        Text("СЛ $target: ${comparison.outcome} (${signed(comparison.margin)})")
                    }
                    roll.specialResult?.let { Text(it.title) }
                    roll.note?.let { Text(it, color = DublMuted) }
                    roll.followUp?.let { followUp ->
                        OutlinedButton(onClick = { result = rollFollowUp(roll) }) { Text(followUp.buttonTitle) }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = calculation.total != null,
                onClick = {
                    val base = calculation.total ?: return@TextButton
                    onPreferredAttribute(attribute)
                    val selectedTotals = effects.options.selectedTotals(selectedEffects)
                    val autoBonus = effects.automaticBonus
                    val toggledBonus = selectedTotals.numericBonus
                    val selectedAdvantage = selectedTotals.advantageDice
                    val selectedHindrance = selectedTotals.hindranceDice
                    val manual = effectCountText.toIntOrNull()?.coerceIn(1, 9) ?: 1
                    val manualAdvantage = if (mode == RollMode.ADVANTAGE) manual else 0
                    val manualHindrance = if (mode == RollMode.HINDRANCE) manual else 0
                    val adv = selectedAdvantage + manualAdvantage
                    val hind = selectedHindrance + manualHindrance
                    val effectiveMode = when {
                        adv > 0 && hind > 0 -> return@TextButton
                        adv > 0 -> RollMode.ADVANTAGE
                        hind > 0 -> RollMode.HINDRANCE
                        else -> RollMode.NORMAL
                    }
                    result = rollCheck(
                        mode = effectiveMode,
                        effectCount = when (effectiveMode) { RollMode.ADVANTAGE -> adv; RollMode.HINDRANCE -> hind; RollMode.NORMAL -> 0 },
                        checkBonus = base + autoBonus + toggledBonus,
                        checkBonusLabel = "${skill.name} + ${attribute.title}",
                        situationalBonus = situationalText.toIntOrNull()?.coerceIn(-99, 99) ?: 0,
                    )
                },
            ) { Text(if (result == null) "Бросить" else "Бросить снова") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } },
    )
}
