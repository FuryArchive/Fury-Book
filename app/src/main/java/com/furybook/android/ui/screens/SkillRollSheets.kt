package com.furybook.android.ui.screens

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.furybook.android.data.AndroidContentPackState
import com.furybook.android.data.ConditionCatalogRepository
import com.furybook.android.data.DevelopmentCatalogRepository
import com.furybook.android.data.SkillEffectCatalogRepository
import com.furybook.content.FcpOrderedUiItem
import com.furybook.content.FcpUiAccentToken
import com.furybook.content.FcpUiComponent
import com.furybook.content.FcpUiHostOrder
import com.furybook.content.FcpUiIconToken
import com.furybook.content.FcpUiSurface
import com.furybook.content.orderedUiItems
import com.furybook.dubl.content.DublResourceMeterModel
import com.furybook.dubl.content.DublResourceToggleModel
import com.furybook.dubl.content.DublUiFeature
import com.furybook.dubl.content.forFeature
import com.furybook.dubl.content.resourceCurrent
import com.furybook.dubl.model.AttributeId
import com.furybook.dubl.model.CharacterConditionId
import com.furybook.dubl.model.ConditionLocalOverride
import com.furybook.dubl.model.CharacterEconomy
import com.furybook.dubl.model.CharacterEconomyBreakdown
import com.furybook.dubl.model.CharacterSheetResourceId
import com.furybook.dubl.model.DublCharacter
import com.furybook.dubl.model.FlurryWeaponProfile
import com.furybook.dubl.model.CustomCondition
import com.furybook.dubl.model.CustomResource
import com.furybook.dubl.model.DevelopmentCatalog
import com.furybook.dubl.model.DevelopmentEntry
import com.furybook.dubl.model.DevelopmentSheetItem
import com.furybook.dubl.model.DevelopmentSheetItemSource
import com.furybook.dubl.model.DevelopmentSheetSection
import com.furybook.dubl.model.DevelopmentSheetSectionType
import com.furybook.dubl.model.DevelopmentProgress
import com.furybook.dubl.model.DevelopmentRules
import com.furybook.dubl.model.RequirementStatus
import com.furybook.dubl.model.MagicEquipmentRules
import com.furybook.dubl.model.ResolvedSkill
import com.furybook.dubl.model.RollContribution
import com.furybook.dubl.model.RollContext
import com.furybook.dubl.model.RollFollowUp
import com.furybook.dubl.model.RollMode
import com.furybook.dubl.model.RollResult
import com.furybook.dubl.model.RollTargetOutcome
import com.furybook.dubl.model.SheetGroup
import com.furybook.dubl.model.SheetGroupingRules
import com.furybook.dubl.model.SkillCategory
import com.furybook.dubl.model.SkillEffectDefinition
import com.furybook.dubl.model.SkillEffectRules
import com.furybook.dubl.model.SkillRollEffectOption
import com.furybook.dubl.model.allowedAttributes
import com.furybook.dubl.model.allowedSkillIds
import com.furybook.dubl.model.AttackOutcome
import com.furybook.dubl.model.availableFollowUp
import com.furybook.dubl.model.compareRollToTarget
import com.furybook.dubl.model.resolve
import com.furybook.dubl.model.resolveAttack
import com.furybook.dubl.model.rule
import com.furybook.dubl.model.summary
import com.furybook.dubl.model.developmentNormalize
import com.furybook.dubl.model.rollPreset
import com.furybook.dubl.model.rollCheck
import com.furybook.dubl.model.rollFollowUp
import com.furybook.dubl.model.resolveSkill
import com.furybook.dubl.model.resolvedSkills
import com.furybook.dubl.model.skillCalculation
import com.furybook.dubl.model.skillCalculationForRoll
import com.furybook.dubl.model.selectedTotals
import com.furybook.android.state.CharacterController
import com.furybook.android.ui.components.containSheetOverscroll
import com.furybook.ui.components.DublCard
import com.furybook.android.ui.components.DublSwitch
import com.furybook.ui.theme.FuryAccentFill
import com.furybook.ui.theme.FuryOnAccentFill
import com.furybook.ui.theme.DublAccent
import com.furybook.ui.theme.DublAccentSoft
import com.furybook.ui.theme.DublDanger
import com.furybook.ui.theme.DublGold
import com.furybook.ui.theme.DublHealth
import com.furybook.ui.theme.DublMana
import com.furybook.ui.theme.DublStamina
import java.text.DecimalFormat
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Skill roll sheets used by SkillsScreen; moved unchanged from the removed OverviewScreen.kt (stage 2b).

@Composable
internal fun DublSkillRollSheet(
    character: DublCharacter,
    skill: ResolvedSkill,
    initialAttribute: AttributeId,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val developmentCatalog = remember(context.applicationContext) {
        DevelopmentCatalogRepository(context.applicationContext).load()
    }
    val effectCatalog = remember(context.applicationContext) {
        SkillEffectCatalogRepository(context.applicationContext).load()
    }
    val calculation = character.skillCalculationForRoll(skill, initialAttribute)
    val effects = remember(character, skill.id, developmentCatalog, effectCatalog) {
        SkillEffectRules(character, developmentCatalog, effectCatalog).forSkill(skill)
    }
    CheckRollSheet(
        title = skill.name,
        bonusTitle = "Бонус умения · ${initialAttribute.shortTitle}",
        checkBonus = calculation.total?.plus(effects.automaticBonus),
        formulaText = calculation.formulaText(skill, showConfiguredOptions = false),
        rememberKey = "${skill.id}:${initialAttribute.name}",
        selectedAttribute = initialAttribute,
        automaticContributions = effects.automaticContributions,
        effectOptions = effects.options,
        effectReminders = effects.reminders,
        onDismiss = onDismiss,
    )
}

/** Compatibility path for the dedicated Skills screen, where configured skill
 * attributes are still edited/remembered in that screen's existing flow. */
@Composable
internal fun DublSkillRollSheet(
    character: DublCharacter,
    skill: ResolvedSkill,
    preferredAttribute: AttributeId? = null,
    onPreferredAttribute: ((AttributeId) -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val developmentCatalog = remember(context.applicationContext) {
        DevelopmentCatalogRepository(context.applicationContext).load()
    }
    val effectCatalog = remember(context.applicationContext) {
        SkillEffectCatalogRepository(context.applicationContext).load()
    }
    var selectedAttribute by remember(skill.id, skill.attributes, preferredAttribute) {
        mutableStateOf(preferredAttribute?.takeIf { it in skill.attributes } ?: skill.attributes.first())
    }
    val calculation = character.skillCalculation(skill, selectedAttribute)
    val effects = remember(character, skill.id, developmentCatalog, effectCatalog) {
        SkillEffectRules(character, developmentCatalog, effectCatalog).forSkill(skill)
    }
    CheckRollSheet(
        title = skill.name,
        bonusTitle = "Бонус умения",
        checkBonus = calculation.total?.plus(effects.automaticBonus),
        formulaText = calculation.formulaText(skill),
        rememberKey = "skills-screen:${skill.id}",
        attributeOptions = skill.attributes,
        selectedAttribute = selectedAttribute,
        automaticContributions = effects.automaticContributions,
        effectOptions = effects.options,
        effectReminders = effects.reminders,
        onAttributeSelected = {
            selectedAttribute = it
            onPreferredAttribute?.invoke(it)
        },
        onDismiss = onDismiss,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CheckRollSheet(
    title: String,
    bonusTitle: String,
    checkBonus: Int?,
    formulaText: String,
    rememberKey: Any,
    skillOptions: List<ResolvedSkill> = emptyList(),
    selectedSkillId: String? = null,
    onSkillSelected: ((String) -> Unit)? = null,
    attributeOptions: List<AttributeId> = emptyList(),
    selectedAttribute: AttributeId? = null,
    automaticContributions: List<RollContribution> = emptyList(),
    effectOptions: List<SkillRollEffectOption> = emptyList(),
    effectReminders: List<SkillEffectDefinition> = emptyList(),
    onAttributeSelected: ((AttributeId) -> Unit)? = null,
    flurryProfile: FlurryWeaponProfile? = null,
    shortFlurry: Boolean = false,
    onFlurryProfile: ((FlurryWeaponProfile) -> Unit)? = null,
    onShortFlurry: ((Boolean) -> Unit)? = null,
    attackResolution: Boolean = false,
    onDismiss: () -> Unit,
) {
    var advantageCount by remember(rememberKey) { mutableStateOf(0) }
    var hindranceCount by remember(rememberKey) { mutableStateOf(0) }
    var situationalText by remember(rememberKey) { mutableStateOf("0") }
    var targetText by remember(rememberKey) { mutableStateOf("") }
    var selectedEffectIds by remember(rememberKey) { mutableStateOf(setOf<String>()) }
    var result by remember(rememberKey) { mutableStateOf<RollResult?>(null) }
    val scrollState = rememberScrollState()

    val situationalBonus = situationalText.toIntOrNull()?.coerceIn(-99, 99) ?: 0
    val targetValue = targetText.toIntOrNull()?.coerceIn(-999, 999)
    val selectedEffectTotals = effectOptions.selectedTotals(selectedEffectIds)
    val effectiveCheckBonus = checkBonus?.plus(selectedEffectTotals.numericBonus)
    val totalAdvantage = advantageCount + selectedEffectTotals.advantageDice
    val totalHindrance = hindranceCount + selectedEffectTotals.hindranceDice
    val mode = when {
        totalAdvantage > 0 -> RollMode.ADVANTAGE
        totalHindrance > 0 -> RollMode.HINDRANCE
        else -> RollMode.NORMAL
    }
    val effectCount = when (mode) {
        RollMode.ADVANTAGE -> totalAdvantage
        RollMode.HINDRANCE -> totalHindrance
        RollMode.NORMAL -> 0
    }

    fun invalidateResult() {
        result = null
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .containSheetOverscroll()
                .verticalScroll(scrollState)
                .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(
                text = effectiveCheckBonus?.let { "$bonusTitle: ${signed(it)}" } ?: "Проверка недоступна",
                style = MaterialTheme.typography.titleMedium,
                color = if (effectiveCheckBonus != null) DublGold else DublAccent,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                formulaText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (automaticContributions.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text("Автоматические эффекты", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = DublAccent)
                automaticContributions.forEach { contribution ->
                    Text(
                        "${contribution.label}: ${signed(contribution.value)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (effectOptions.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text("Ситуационные эффекты навыков", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(5.dp))
                effectOptions.forEach { option ->
                    val selected = option.id in selectedEffectIds
                    FilterChip(
                        selected = selected,
                        onClick = {
                            val next = selectedEffectIds.toMutableSet()
                            if (!next.add(option.id)) {
                                next.remove(option.id)
                            } else if (option.advantageDice > 0) {
                                effectOptions.filter { it.hindranceDice > 0 }.forEach { next.remove(it.id) }
                                hindranceCount = 0
                            } else if (option.hindranceDice > 0) {
                                effectOptions.filter { it.advantageDice > 0 }.forEach { next.remove(it.id) }
                                advantageCount = 0
                            }
                            selectedEffectIds = next
                            invalidateResult()
                        },
                        label = {
                            val suffix = when {
                                option.numericBonus != 0 -> " (${signed(option.numericBonus)})"
                                option.advantageDice > 0 -> " (+${option.advantageDice} преимущество)"
                                option.hindranceDice > 0 -> " (+${option.hindranceDice} помеха)"
                                else -> ""
                            }
                            Text(option.label + suffix)
                        },
                    )
                    if (selected && option.description.isNotBlank()) {
                        Text(
                            option.description,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 8.dp, bottom = 4.dp),
                        )
                    }
                }
            }
            if (effectReminders.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    color = DublGold.copy(alpha = 0.035f),
                    border = BorderStroke(1.dp, DublGold.copy(alpha = 0.20f)),
                ) {
                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("Связанные правила", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = DublGold)
                        effectReminders.forEach { effect ->
                            Text(
                                "${effect.sourceName}: ${effect.effectText}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            if (skillOptions.size > 1 && onSkillSelected != null) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "Умение для проверки",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    items(skillOptions, key = { it.id }) { option ->
                        FilterChip(
                            selected = selectedSkillId == option.id,
                            onClick = {
                                onSkillSelected(option.id)
                                invalidateResult()
                            },
                            label = { Text(option.name) },
                        )
                    }
                }
            }

            if (attributeOptions.size > 1 && onAttributeSelected != null) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "Характеристика для броска",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    items(attributeOptions, key = { it.name }) { attribute ->
                        FilterChip(
                            selected = selectedAttribute == attribute,
                            onClick = {
                                onAttributeSelected(attribute)
                                invalidateResult()
                            },
                            label = { Text(attribute.title) },
                        )
                    }
                }
            }

            if (flurryProfile != null && onFlurryProfile != null && onShortFlurry != null) {
                val rule = flurryProfile.rule(shortFlurry)
                Spacer(Modifier.height(12.dp))
                Text("Параметры шквала", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    items(FlurryWeaponProfile.entries, key = { it.name }) { profile ->
                        FilterChip(
                            selected = flurryProfile == profile,
                            onClick = {
                                onFlurryProfile(profile)
                                invalidateResult()
                            },
                            label = { Text(profile.title) },
                        )
                    }
                }
                Spacer(Modifier.height(7.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    FilterChip(
                        selected = !shortFlurry,
                        onClick = {
                            onShortFlurry(false)
                            invalidateResult()
                        },
                        label = { Text("Полный шквал") },
                    )
                    FilterChip(
                        selected = shortFlurry,
                        onClick = {
                            onShortFlurry(true)
                            invalidateResult()
                        },
                        label = { Text("Короткий · 2 ОД") },
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    buildString {
                        append("Бонус +${rule.attackBonus}. ")
                        append("1 попадание за каждые ${rule.excessPerHit} превышения")
                        if (rule.shortExcessPenalty > 0) append("; для короткого требуемое превышение +${rule.shortExcessPenalty}")
                        append(". Максимум ${rule.maxHits}. ")
                        append("Реакцию использовать нельзя.")
                        if (rule.alternateWeaponDamage) append(" Урон двух оружий чередуется.")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (checkBonus != null) {
                Spacer(Modifier.height(16.dp))
                Text(
                    "Условия броска",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(8.dp))

                RollCounter(
                    title = "Преимущества",
                    count = advantageCount,
                    onMinus = {
                        advantageCount = (advantageCount - 1).coerceAtLeast(0)
                        invalidateResult()
                    },
                    onPlus = {
                        advantageCount += 1
                        hindranceCount = 0
                        selectedEffectIds = selectedEffectIds - effectOptions.filter { it.hindranceDice > 0 }.map { it.id }.toSet()
                        invalidateResult()
                    },
                )
                Spacer(Modifier.height(7.dp))
                RollCounter(
                    title = "Помехи",
                    count = hindranceCount,
                    onMinus = {
                        hindranceCount = (hindranceCount - 1).coerceAtLeast(0)
                        invalidateResult()
                    },
                    onPlus = {
                        hindranceCount += 1
                        advantageCount = 0
                        selectedEffectIds = selectedEffectIds - effectOptions.filter { it.advantageDice > 0 }.map { it.id }.toSet()
                        invalidateResult()
                    },
                )

                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = situationalText,
                    onValueChange = { raw ->
                        situationalText = sanitizeSignedBonus(raw)
                        invalidateResult()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Ситуационный бонус / штраф") },
                    supportingText = { Text("Например: +2 за инструменты или −3 за условия") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    trailingIcon = {
                        TextButton(
                            onClick = {
                                val value = situationalText.toIntOrNull() ?: 0
                                situationalText = when {
                                    value > 0 -> (-value).toString()
                                    value < 0 -> abs(value).toString()
                                    else -> "0"
                                }
                                invalidateResult()
                            },
                        ) { Text("±") }
                    },
                    singleLine = true,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = targetText,
                    onValueChange = { raw ->
                        targetText = raw.filter { it.isDigit() || it == '-' }.take(4)
                        invalidateResult()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(if (attackResolution || flurryProfile != null) "Защита цели (необязательно)" else "СЛ / результат противника (необязательно)") },
                    supportingText = { Text("Нужен только для автоматического сравнения результата") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    text = "Преимущества и помехи не смешиваются автоматически: выбор одного типа сбрасывает другой, потому что книга не описывает их совместное разрешение.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(9.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
                ) {
                    Text(
                        text = rollSetupText(mode, effectCount, bonusTitle, effectiveCheckBonus ?: 0, situationalBonus),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        result = rollCheck(
                            mode = mode,
                            effectCount = effectCount,
                            checkBonus = effectiveCheckBonus ?: 0,
                            checkBonusLabel = bonusTitle,
                            situationalBonus = situationalBonus,
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 12.dp),
                ) {
                    Text("Бросить")
                }
            }

            result?.let { roll ->
                Spacer(Modifier.height(18.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(13.dp),
                    color = DublGold.copy(alpha = 0.06f),
                    border = BorderStroke(1.dp, DublGold.copy(alpha = 0.42f)),
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = roll.total.toString(),
                            fontSize = 42.sp,
                            lineHeight = 46.sp,
                            fontWeight = FontWeight.Bold,
                            color = DublGold,
                        )
                        val attackDefense = targetValue?.takeIf { attackResolution }
                        val availableFollowUp = roll.availableFollowUp(attackDefense)
                        attackDefense?.let { defense ->
                            val attack = resolveAttack(roll, defense)
                            Text(
                                text = "${attack.outcome.title} · ${signed(attack.margin)}",
                                style = MaterialTheme.typography.labelLarge,
                                color = when (attack.outcome) {
                                    AttackOutcome.CRITICAL_HIT, AttackOutcome.FULL_HIT -> DublAccent
                                    AttackOutcome.PARTIAL_HIT -> DublGold
                                    AttackOutcome.MISS, AttackOutcome.CRITICAL_MISS -> DublDanger
                                },
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                            )
                        }
                        val flurryResolution = flurryProfile?.let { profile ->
                            targetValue?.let { profile.rule(shortFlurry).resolve(roll, it) }
                        }
                        flurryResolution?.let { flurry ->
                            Text(
                                text = flurry.summary,
                                style = MaterialTheme.typography.labelLarge,
                                color = if (flurry.hits > 0) DublAccent else DublDanger,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                            )
                        }
                        targetValue?.takeIf { !attackResolution && flurryProfile == null }?.let { target ->
                            val comparison = compareRollToTarget(roll, target)
                            Text(
                                text = when (comparison.outcome) {
                                    RollTargetOutcome.SUCCESS -> "Выше цели на ${comparison.margin}"
                                    RollTargetOutcome.TIE -> "Равно цели · разница 0"
                                    RollTargetOutcome.FAILURE -> "Ниже цели на ${kotlin.math.abs(comparison.margin)}"
                                },
                                style = MaterialTheme.typography.labelLarge,
                                color = when (comparison.outcome) {
                                    RollTargetOutcome.SUCCESS -> DublAccent
                                    RollTargetOutcome.TIE -> DublGold
                                    RollTargetOutcome.FAILURE -> DublDanger
                                },
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Text(
                            text = rollResultCaption(roll),
                            style = MaterialTheme.typography.labelLarge,
                            color = roll.specialResult?.let { DublAccent } ?: MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (roll.specialResult != null) FontWeight.Bold else FontWeight.Normal,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(5.dp))
                        Text(
                            text = rollModeDescription(roll),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(10.dp))
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(horizontal = 2.dp),
                        ) {
                            itemsIndexed(roll.dice) { index, die ->
                                DiceChip(value = die, selected = index in roll.chosenIndices)
                            }
                        }
                        availableFollowUp?.let { followUp ->
                            Spacer(Modifier.height(12.dp))
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(11.dp),
                                color = DublAccent.copy(alpha = 0.08f),
                                border = BorderStroke(1.dp, DublAccent.copy(alpha = 0.45f)),
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Text(
                                        text = followUpPrompt(followUp),
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = DublAccent,
                                        textAlign = TextAlign.Center,
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Button(
                                        onClick = { result = rollFollowUp(roll) },
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Text(followUp.buttonTitle)
                                    }
                                }
                            }
                        }
                        roll.followUpDie?.let { die ->
                            Spacer(Modifier.height(10.dp))
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(11.dp),
                                color = DublGold.copy(alpha = 0.07f),
                                border = BorderStroke(1.dp, DublGold.copy(alpha = 0.38f)),
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    DiceChip(value = die, selected = true)
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = followUpDieLabel(roll, die),
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = DublGold,
                                        )
                                        roll.specialResult?.let { special ->
                                            Text(
                                                text = special.title,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = DublAccent,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = buildRollBreakdown(roll),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                        roll.note?.takeIf { availableFollowUp != null || roll.followUp == null }?.let { note ->
                            Spacer(Modifier.height(7.dp))
                            Text(
                                text = note,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = DublAccent,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RollCounter(
    title: String,
    count: Int,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
            )
            OutlinedButton(
                onClick = onMinus,
                enabled = count > 0,
                contentPadding = PaddingValues(horizontal = 13.dp, vertical = 7.dp),
            ) { Text("−") }
            Text(
                text = count.toString(),
                modifier = Modifier.width(28.dp),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            OutlinedButton(
                onClick = onPlus,
                contentPadding = PaddingValues(horizontal = 13.dp, vertical = 7.dp),
            ) { Text("+") }
        }
    }
}

@Composable
private fun DiceChip(value: Int, selected: Boolean) {
    Surface(
        shape = RoundedCornerShape(9.dp),
        color = if (selected) DublGold.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            if (selected) 1.5.dp else 1.dp,
            if (selected) DublGold else MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
        ),
    ) {
        Text(
            text = value.toString(),
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 8.dp),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = if (selected) DublGold else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun rollSetupText(
    mode: RollMode,
    effectCount: Int,
    bonusTitle: String,
    checkBonus: Int,
    situationalBonus: Int,
): String {
    val diceText = when (mode) {
        RollMode.NORMAL -> "2d6"
        RollMode.ADVANTAGE -> "${2 + effectCount}d6, оставить 2 наибольших"
        RollMode.HINDRANCE -> "${2 + effectCount}d6, оставить 2 наименьших"
    }
    return buildString {
        append(diceText)
        append(" · ").append(bonusTitle.lowercase()).append(" ").append(signed(checkBonus))
        if (situationalBonus != 0) {
            append(" · ситуация ").append(signed(situationalBonus))
        }
    }
}

private fun rollModeDescription(roll: RollResult): String = buildString {
    when (roll.mode) {
        RollMode.NORMAL -> append("Обычный бросок · 2d6")
        RollMode.ADVANTAGE -> append("Преимущества ×${roll.effectCount} · ${2 + roll.effectCount}d6 · выбрать 2 наибольших")
        RollMode.HINDRANCE -> append("Помехи ×${roll.effectCount} · ${2 + roll.effectCount}d6 · выбрать 2 наименьших")
    }
    if (roll.grantedAdvantageDie) append(" · +1 кость за дубль")
}

private fun followUpPrompt(followUp: RollFollowUp): String = when (followUp) {
    RollFollowUp.ADVANTAGE_DIE -> "Выпал дубль — доступен дополнительный куб."
    RollFollowUp.CRITICAL_FAILURE_CONFIRMATION -> "Критический провал — можно бросить кость подтверждения."
    RollFollowUp.SUPERIORITY_DIE -> "Критический успех — доступна кость превосходства."
}

private fun rollResultCaption(roll: RollResult): String = roll.specialResult?.title ?: "Итог проверки"

private fun followUpDieLabel(roll: RollResult, die: Int): String = when (roll.resolvedFollowUp) {
    RollFollowUp.ADVANTAGE_DIE -> "Кость преимущества: $die"
    RollFollowUp.CRITICAL_FAILURE_CONFIRMATION -> "Кость подтверждения: $die"
    RollFollowUp.SUPERIORITY_DIE -> "Кость превосходства: $die"
    null -> "Дополнительная кость: $die"
}

private fun buildRollBreakdown(roll: RollResult): String = buildString {
    append("Кости ")
    append(roll.chosenIndices.sorted().joinToString(" + ") { roll.dice[it].toString() })
    append(" · ").append(roll.checkBonusLabel.lowercase()).append(" ").append(signed(roll.checkBonus))
    if (roll.situationalBonus != 0) {
        append(" · ситуация ").append(signed(roll.situationalBonus))
    }
    if (roll.followUpDie != null && roll.resolvedFollowUp == RollFollowUp.SUPERIORITY_DIE) {
        append(" · превосходство +").append(roll.followUpDie)
    }
}

private fun sanitizeSignedBonus(raw: String): String {
    if (raw.isBlank()) return ""
    val negative = raw.trimStart().startsWith('-')
    val digits = raw.filter(Char::isDigit).take(2)
    if (digits.isEmpty()) return if (negative) "-" else ""
    return (if (negative) "-" else "") + digits
}

private fun signed(value: Int): String = if (value >= 0) "+$value" else value.toString()
