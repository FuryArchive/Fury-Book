package com.furybook.desktop.screens

import com.furybook.dubl.model.CharacterConditionId
import com.furybook.dubl.model.requiresVisionDeclaration
import com.furybook.dubl.model.conditionRollModifiers
import com.furybook.dubl.model.SkillContribution
import com.furybook.dubl.model.allowedRollAttributes

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
import com.furybook.dubl.model.compareRollToTarget
import com.furybook.dubl.model.developmentNormalize
import com.furybook.dubl.model.resolveSkill
import com.furybook.dubl.model.rollCheck
import com.furybook.dubl.model.rollFollowUp
import com.furybook.dubl.model.rollPreset
import com.furybook.dubl.model.rule
import com.furybook.dubl.model.skillCalculationForRoll
import com.furybook.dubl.model.calculation
import com.furybook.dubl.model.skillRollAlternatives
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
    activeConditions: Set<CharacterConditionId> = emptySet(),
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
    var result by remember(skill.id, character, activeConditions) { mutableStateOf<RollResult?>(null) }
    var selectedEffects by remember(skill.id) { mutableStateOf(emptySet<String>()) }
    var attrMenu by remember { mutableStateOf(false) }

    var alternativeId by remember(skill.id) { mutableStateOf<String?>(null) }
    val alternatives = character.skillRollAlternatives(skill, effectCatalog)
    val alternative = alternatives.firstOrNull { it.id == alternativeId }
    val effectiveSkill = alternative?.skill ?: skill
    val effectiveAttribute = alternative?.attribute ?: attribute
    val effects = SkillEffectRules(character, developmentCatalog, effectCatalog).forSkill(effectiveSkill)
    var requiresVision by remember(character.id, effectiveSkill.id, effectiveAttribute, activeConditions) { mutableStateOf<Boolean?>(null) }
    val visionDeclarationNeeded = requiresVisionDeclaration(RollContext.SKILL, effectiveAttribute, activeConditions)
    val conditionModifiers = character.conditionRollModifiers(RollContext.SKILL, effectiveAttribute, activeConditions, requiresVision = requiresVision)
    val rawCalculation = alternative?.calculation(character) ?: character.skillCalculationForRoll(effectiveSkill, effectiveAttribute)
    val calculation = rawCalculation.copy(total = conditionModifiers.apply(rawCalculation.total),
        contributions = rawCalculation.contributions + conditionModifiers.contributions.map { SkillContribution(it.label, it.value) },
        unavailableReason = conditionModifiers.unavailableReason ?: rawCalculation.unavailableReason)

    val selectedTotals = effects.options.selectedTotals(selectedEffects)
    val manualDice = effectCountText.toIntOrNull()?.coerceIn(1, 9) ?: 1
    val totalAdvantage = selectedTotals.advantageDice + (alternative?.advantageDice ?: 0) +
        if (mode == RollMode.ADVANTAGE) manualDice else 0
    val totalHindrance = selectedTotals.hindranceDice + conditionModifiers.hindranceDice +
        if (mode == RollMode.HINDRANCE) manualDice else 0
    val mixedFactors = totalAdvantage > 0 && totalHindrance > 0

    FuryDialog(
        onDismissRequest = onDismiss,
        title = { Text("Бросок: ${skill.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (alternatives.isNotEmpty()) {
                    Text("Формула только для этого броска; выбор подтверждает указанное условие", color = DublMuted)
                    TextButton(onClick = { alternativeId = null; selectedEffects = emptySet(); result = null }) {
                        Text(if (alternative == null) "✓ Обычная проверка" else "Обычная проверка")
                    }
                    alternatives.forEach { option ->
                        TextButton(onClick = { alternativeId = option.id; selectedEffects = emptySet(); result = null }) {
                            Text((if (alternative?.id == option.id) "✓ " else "") + option.label)
                        }
                    }
                    alternative?.let { Text(it.sourceText, color = DublMuted) }
                }
                OutlinedButton(enabled = alternative == null, onClick = { attrMenu = true }) { Text("Характеристика: ${effectiveAttribute.title}") }
                DropdownMenu(expanded = attrMenu, onDismissRequest = { attrMenu = false }) {
                    attributes.forEach { option ->
                        DropdownMenuItem(text = { Text(option.title) }, onClick = { attribute = option; attrMenu = false; result = null })
                    }
                }
                FurySegmentedControl(
                    options = RollMode.entries.map { it.title },
                    selectedIndex = RollMode.entries.indexOf(mode),
                    onSelected = { index -> mode = RollMode.entries[index]; result = null },
                )
                if (mode != RollMode.NORMAL) {
                    OutlinedTextField(
                        effectCountText,
                        { effectCountText = it.filter(Char::isDigit).take(2); result = null },
                        label = { Text("Доп. костей") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        situationalText,
                        { situationalText = it.filter { c -> c.isDigit() || c == '-' }.take(4); result = null },
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

                if (visionDeclarationNeeded) {
                    Text("Проверка требует зрения?")
                    listOf(null to "Не указано", true to "Да", false to "Нет").forEach { (choice, label) ->
                        TextButton(onClick = { requiresVision = choice; result = null }) {
                            Text((if (requiresVision == choice) "✓ " else "") + label)
                        }
                    }
                }
                if (conditionModifiers.notice.isNotBlank()) Text(conditionModifiers.notice, color = DublMuted)
                if (effects.automaticContributions.isNotEmpty()) {
                    Text("Автоматически: " + effects.automaticContributions.joinToString { "${it.label} ${signed(it.value)}" })
                }
                effects.options.forEach { option ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = option.id in selectedEffects,
                            onCheckedChange = { checked -> selectedEffects = if (checked) selectedEffects + option.id else selectedEffects - option.id; result = null },
                        )
                        Column {
                            Text(option.label)
                            Text(option.description, color = DublMuted)
                        }
                    }
                }
                if (mixedFactors) {
                    Text("Уберите помехи или выберите обычную проверку: совместное разрешение ПФ и помех не определено.", color = DublMuted)
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
                        Text(calculation.formulaText(effectiveSkill), color = DublMuted, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
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
                enabled = calculation.total != null && !mixedFactors,
                onClick = {
                    val base = calculation.total ?: return@TextButton
                    onPreferredAttribute(attribute)
                    val autoBonus = effects.automaticBonus
                    val toggledBonus = selectedTotals.numericBonus
                    val adv = totalAdvantage
                    val hind = totalHindrance
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
                        checkBonusLabel = "${effectiveSkill.name} + ${effectiveAttribute.title}",
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
    activeConditions: Set<CharacterConditionId> = emptySet(),
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
    var result by remember(context, character, activeConditions) { mutableStateOf<RollResult?>(null) }
    var skillMenu by remember { mutableStateOf(false) }
    var attributeMenu by remember { mutableStateOf(false) }
    var flurryProfile by remember(context) { mutableStateOf(FlurryWeaponProfile.LIGHT) }
    var selectedContextEffects by remember(context, character.id) { mutableStateOf(emptySet<String>()) }
    var shortFlurry by remember(context) { mutableStateOf(false) }
    var flurryProfileMenu by remember { mutableStateOf(false) }

    val attrOptions = if (context == RollContext.ATTRIBUTE) AttributeId.entries else character.allowedRollAttributes(context, skill?.id)
    val selectedAttribute = initialAttribute?.takeIf { it in attrOptions } ?: attribute?.takeIf { it in attrOptions } ?: attrOptions.firstOrNull()
    var requiresVision by remember(character.id, context, skill?.id, selectedAttribute, activeConditions) { mutableStateOf<Boolean?>(null) }
    val visionDeclarationNeeded = requiresVisionDeclaration(context, selectedAttribute, activeConditions)
    val conditionModifiers = character.conditionRollModifiers(context, selectedAttribute, activeConditions, skill?.id, requiresVision = requiresVision)
    val basePreset = character.rollPreset(context, skill?.id, selectedAttribute).let { base ->
        base.copy(bonus = conditionModifiers.apply(base.bonus), contributions = base.contributions + conditionModifiers.contributions,
            unavailableReason = conditionModifiers.unavailableReason ?: base.unavailableReason)
    }
    val flurryRule = if (context == RollContext.FLURRY) flurryProfile.rule(shortFlurry) else null
    val baseBonus = basePreset.bonus
    val preset = if (flurryRule != null && baseBonus != null) {
        val label = if (shortFlurry) "Короткий шквал" else "Шквал атак"
        basePreset.copy(
            bonus = baseBonus + flurryRule.attackBonus,
            contributions = basePreset.contributions + com.furybook.dubl.model.RollContribution(label, flurryRule.attackBonus),
            formulaText = "${basePreset.formulaText} + $label",
        )
    } else basePreset
    val contextOptions = SkillEffectRules(character, developmentCatalog, effectCatalog).contextOptions(context, selectedAttribute)
    val contextTotals = contextOptions.selectedTotals(selectedContextEffects)
    val totalAdvantage = (advantageText.toIntOrNull()?.coerceIn(0, 9) ?: 0) + contextTotals.advantageDice
    val totalHindrance = (hindranceText.toIntOrNull()?.coerceIn(0, 9) ?: 0) + contextTotals.hindranceDice + conditionModifiers.hindranceDice
    val mixedFactors = totalAdvantage > 0 && totalHindrance > 0
    val alreadyAppliedLabels = preset.contributions.map { developmentNormalize(it.label) }.toSet()
    val reminders = SkillEffectRules(character, developmentCatalog, effectCatalog).let { rules ->
        if (context == RollContext.FLURRY) {
            rules.forContext(RollContext.ATTACK)
        } else {
            rules.forContext(context)
        }
    }.filterNot { developmentNormalize(it.sourceName) in alreadyAppliedLabels || it.id in contextOptions.map { option -> option.id } }

    FuryDialog(
        onDismissRequest = onDismiss,
        title = { Text(context.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (skills.isNotEmpty()) {
                    OutlinedButton(onClick = { skillMenu = true }) { Text("Умение: ${skill?.name ?: "—"}") }
                    DropdownMenu(expanded = skillMenu, onDismissRequest = { skillMenu = false }) {
                        skills.forEach { option -> DropdownMenuItem(text = { Text(option.name) }, onClick = { skill = option; attribute = null; skillMenu = false; result = null }) }
                    }
                }
                if (attrOptions.isNotEmpty()) {
                    OutlinedButton(onClick = { attributeMenu = true }) { Text("Характеристика: ${(selectedAttribute ?: attrOptions.first()).title}") }
                    DropdownMenu(expanded = attributeMenu, onDismissRequest = { attributeMenu = false }) {
                        attrOptions.forEach { option -> DropdownMenuItem(text = { Text(option.title) }, onClick = { attribute = option; attributeMenu = false; result = null }) }
                    }
                }
                if (context == RollContext.FLURRY) {
                    val rule = flurryProfile.rule(shortFlurry)
                    OutlinedButton(onClick = { flurryProfileMenu = true }) { Text("Оружие: ${flurryProfile.title}") }
                    DropdownMenu(expanded = flurryProfileMenu, onDismissRequest = { flurryProfileMenu = false }) {
                        FlurryWeaponProfile.entries.forEach { profile ->
                            DropdownMenuItem(
                                text = { Text(profile.title) },
                                onClick = {
                                    flurryProfile = profile
                                    flurryProfileMenu = false
                                    result = null
                                },
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = shortFlurry,
                            onCheckedChange = {
                                shortFlurry = it
                                result = null
                            },
                        )
                        Text("Короткий шквал · 2 ОД")
                    }
                    Text(
                        buildString {
                            append("Бонус +${rule.attackBonus}. ")
                            append("1 попадание за каждые ${rule.excessPerHit} превышения")
                            if (rule.shortExcessPenalty > 0) append("; для короткого требуемое превышение +${rule.shortExcessPenalty}")
                            append(". Максимум ${rule.maxHits}. Реакцию использовать нельзя.")
                            if (rule.alternateWeaponDamage) append(" Урон двух оружий чередуется.")
                        },
                        color = DublMuted,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(advantageText, { advantageText = it.filter(Char::isDigit).take(1); result = null }, label = { Text("Преим.") }, modifier = Modifier.weight(1f), singleLine = true)
                    OutlinedTextField(hindranceText, { hindranceText = it.filter(Char::isDigit).take(1); result = null }, label = { Text("Помех.") }, modifier = Modifier.weight(1f), singleLine = true)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(situationalText, { situationalText = it.take(4); result = null }, label = { Text("Поправка") }, modifier = Modifier.weight(1f), singleLine = true)
                    OutlinedTextField(targetText, { targetText = it.take(4) }, label = { Text("Цель / СЛ") }, modifier = Modifier.weight(1f), singleLine = true)
                }
                if (visionDeclarationNeeded) {
                    Text("Проверка требует зрения?")
                    listOf(null to "Не указано", true to "Да", false to "Нет").forEach { (choice, label) ->
                        TextButton(onClick = { requiresVision = choice; result = null }) {
                            Text((if (requiresVision == choice) "✓ " else "") + label)
                        }
                    }
                }
                if (conditionModifiers.notice.isNotBlank()) Text(conditionModifiers.notice, color = DublMuted)
                if (mixedFactors) Text("Одновременно есть преимущества и помехи. Совместное разрешение не определено; встроенный бросок недоступен.", color = DublMuted)
                preset.contributions.forEach { contribution -> Text("${contribution.label}: ${signed(contribution.value)}", color = DublMuted) }
                contextOptions.forEach { option ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = option.id in selectedContextEffects, onCheckedChange = { selected ->
                            selectedContextEffects = if (selected) selectedContextEffects + option.id else selectedContextEffects - option.id
                            result = null
                        })
                        Text(option.label)
                    }
                }
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
            TextButton(enabled = preset.available && !mixedFactors, onClick = {
                val bonus = (preset.bonus ?: return@TextButton) + contextTotals.numericBonus
                val adv = totalAdvantage
                val hind = totalHindrance
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
