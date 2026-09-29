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
import com.furybook.dubl.model.ResolvedSkill
import com.furybook.dubl.model.RollMode
import com.furybook.dubl.model.RollResult
import com.furybook.dubl.model.RollContext
import com.furybook.dubl.model.SkillEffectCatalog
import com.furybook.dubl.model.SkillEffectRules
import com.furybook.dubl.model.allowedAttributes
import com.furybook.dubl.model.allowedSkillIds
import com.furybook.dubl.model.compareRollToTarget
import com.furybook.dubl.model.developmentNormalize
import com.furybook.dubl.model.resolveSkill
import com.furybook.dubl.model.rollCheck
import com.furybook.dubl.model.rollFollowUp
import com.furybook.dubl.model.rollPreset
import com.furybook.dubl.model.skillCalculationForRoll
import com.furybook.dubl.model.selectedTotals
import com.furybook.ui.theme.DublMuted

@Composable
fun SkillAttributeChoiceDialog(
    character: DublCharacter,
    skill: ResolvedSkill,
    onConfirm: (AttributeId) -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedAttribute by remember(skill.id) { mutableStateOf(skill.stockAttribute) }
    val calculation = character.skillCalculationForRoll(skill, selectedAttribute)

    FuryDialog(
        onDismissRequest = onDismiss,
        title = { Text("${skill.name}: характеристика") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Выберите характеристику для этого броска. Стоковая характеристика уже выбрана.",
                    color = DublMuted,
                )
                androidx.compose.material3.Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(9.dp),
                    color = DesktopSurfaceInset.copy(alpha = .62f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, DesktopBorder.copy(alpha = .62f)),
                ) {
                    Column(Modifier.padding(horizontal = 11.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(selectedAttribute.title, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                        Text(
                            calculation.formulaText(skill, showConfiguredOptions = false),
                            color = DublMuted,
                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                AttributeId.entries.chunked(2).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { option ->
                            FuryChoiceButton(
                                label = option.title,
                                meta = signed(character.attribute(option)),
                                selected = selectedAttribute == option,
                                onClick = { selectedAttribute = option },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = calculation.total != null,
                onClick = { onConfirm(selectedAttribute) },
            ) { Text("Бросить ${calculation.total?.let(::signed) ?: "—"}") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

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

@Composable
fun ContextRollDialog(
    character: DublCharacter,
    context: RollContext,
    developmentCatalog: DevelopmentCatalog,
    effectCatalog: SkillEffectCatalog,
    initialAttribute: AttributeId? = null,
    onDismiss: () -> Unit,
) {
    val allowedSkillIds = context.allowedSkillIds()
    val skills = allowedSkillIds.mapNotNull(character::resolveSkill)
    var skill by remember(context) { mutableStateOf(skills.firstOrNull()) }
    var attribute by remember(context, skill?.id, initialAttribute) { mutableStateOf<AttributeId?>(initialAttribute) }
    var advantageText by remember(context) { mutableStateOf("0") }
    var hindranceText by remember(context) { mutableStateOf("0") }
    var situationalText by remember(context) { mutableStateOf("0") }
    var targetText by remember(context) { mutableStateOf("") }
    var result by remember(context) { mutableStateOf<RollResult?>(null) }
    var skillMenu by remember { mutableStateOf(false) }
    var attributeMenu by remember { mutableStateOf(false) }

    val attrOptions = if (context == RollContext.ATTRIBUTE) AttributeId.entries else context.allowedAttributes(skill?.id)
    val selectedAttribute = initialAttribute?.takeIf { it in attrOptions } ?: attribute?.takeIf { it in attrOptions } ?: attrOptions.firstOrNull()
    val preset = character.rollPreset(context, skill?.id, selectedAttribute)
    val alreadyAppliedLabels = preset.contributions.map { developmentNormalize(it.label) }.toSet()
    val reminders = SkillEffectRules(character, developmentCatalog, effectCatalog)
        .forContext(context)
        .filterNot { developmentNormalize(it.sourceName) in alreadyAppliedLabels }

    FuryDialog(
        onDismissRequest = onDismiss,
        title = { Text(context.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (skills.isNotEmpty()) {
                    OutlinedButton(onClick = { skillMenu = true }) { Text("Умение: ${skill?.name ?: "—"}") }
                    DropdownMenu(expanded = skillMenu, onDismissRequest = { skillMenu = false }) {
                        skills.forEach { option -> DropdownMenuItem(text = { Text(option.name) }, onClick = { skill = option; attribute = null; skillMenu = false }) }
                    }
                }
                if (attrOptions.isNotEmpty()) {
                    OutlinedButton(onClick = { attributeMenu = true }) { Text("Характеристика: ${(selectedAttribute ?: attrOptions.first()).title}") }
                    DropdownMenu(expanded = attributeMenu, onDismissRequest = { attributeMenu = false }) {
                        attrOptions.forEach { option -> DropdownMenuItem(text = { Text(option.title) }, onClick = { attribute = option; attributeMenu = false }) }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(advantageText, { advantageText = it.filter(Char::isDigit).take(1) }, label = { Text("Преим.") }, modifier = Modifier.weight(1f), singleLine = true)
                    OutlinedTextField(hindranceText, { hindranceText = it.filter(Char::isDigit).take(1) }, label = { Text("Помех.") }, modifier = Modifier.weight(1f), singleLine = true)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(situationalText, { situationalText = it.take(4) }, label = { Text("Поправка") }, modifier = Modifier.weight(1f), singleLine = true)
                    OutlinedTextField(targetText, { targetText = it.take(4) }, label = { Text("Цель / СЛ") }, modifier = Modifier.weight(1f), singleLine = true)
                }
                preset.contributions.forEach { contribution -> Text("${contribution.label}: ${signed(contribution.value)}", color = DublMuted) }
                reminders.forEach { reminder -> Text("${reminder.sourceName}: ${reminder.effectText}", color = DublMuted) }
                Text(preset.formulaText, color = DublMuted)
                result?.let { roll ->
                    Text("Кости: ${roll.dice.joinToString()} · итог ${roll.total}")
                    targetText.toIntOrNull()?.let { target ->
                        val comparison = compareRollToTarget(roll, target)
                        Text("${comparison.outcome} (${signed(comparison.margin)})")
                    }
                    roll.specialResult?.let { Text(it.title) }
                    roll.note?.let { Text(it, color = DublMuted) }
                    roll.followUp?.let { follow -> OutlinedButton(onClick = { result = rollFollowUp(roll) }) { Text(follow.buttonTitle) } }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = preset.available, onClick = {
                val bonus = preset.bonus ?: return@TextButton
                val adv = advantageText.toIntOrNull()?.coerceIn(0, 9) ?: 0
                val hind = hindranceText.toIntOrNull()?.coerceIn(0, 9) ?: 0
                if (adv > 0 && hind > 0) return@TextButton
                val mode = when { adv > 0 -> RollMode.ADVANTAGE; hind > 0 -> RollMode.HINDRANCE; else -> RollMode.NORMAL }
                result = rollCheck(
                    mode = mode,
                    effectCount = if (mode == RollMode.ADVANTAGE) adv else if (mode == RollMode.HINDRANCE) hind else 0,
                    checkBonus = bonus,
                    checkBonusLabel = preset.title,
                    situationalBonus = situationalText.toIntOrNull()?.coerceIn(-99, 99) ?: 0,
                )
            }) { Text(if (result == null) "Бросить" else "Бросить снова") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } },
    )
}
