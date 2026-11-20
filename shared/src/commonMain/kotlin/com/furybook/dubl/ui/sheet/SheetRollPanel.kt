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
import com.furybook.ui.theme.FuryColors
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.clickable
import com.furybook.dubl.model.AttributeId
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.furybook.dubl.model.FlurryWeaponProfile
import com.furybook.dubl.model.RollContext
import com.furybook.dubl.model.RollMode
import com.furybook.dubl.model.RollResult
import com.furybook.dubl.model.resolveSkill
import com.furybook.dubl.model.rule
import com.furybook.ui.components.FuryButton
import com.furybook.ui.components.FuryButtonStyle
import com.furybook.ui.components.FuryChipGroup
import com.furybook.ui.components.FuryChoice
import com.furybook.ui.components.FuryIconButton
import com.furybook.ui.components.FuryMultiChipGroup
import com.furybook.ui.components.FuryRollHistoryEntry
import com.furybook.ui.components.FuryRollTray
import com.furybook.ui.components.FuryStepper
import com.furybook.ui.components.FuryTextField
import com.furybook.ui.components.furyStepperState
import com.furybook.ui.theme.DublMuted
import com.furybook.ui.theme.DublSurface
import com.furybook.ui.theme.DublText
import com.furybook.ui.theme.FuryOnAccentFill
import com.furybook.ui.theme.FuryShapes
import com.furybook.ui.theme.FurySpacing
import com.furybook.ui.theme.furyOverlineStyle

private val MODE_CHOICES = listOf(
    FuryChoice(RollMode.NORMAL, "Обычный"),
    FuryChoice(RollMode.ADVANTAGE, "Преим."),
    FuryChoice(RollMode.HINDRANCE, "Помеха"),
)

/** The roll button's label: «Бросить» before the first roll, «Бросить снова» after (as [FuryRollTray] words it). */
internal fun sheetRollButtonLabel(rolled: Boolean): String = if (rolled) "Бросить снова" else "Бросить"

/**
 * The roll tray for one [request]: result, follow-up die, and every input the desktop roll dialogs offer.
 * Stateless apart from the target and modifier text buffers; every change goes through [onRequestChange].
 * With [showRollButton] false the caller shows the roll button itself (the dialog's pinned footer).
 * The setup controls sit on a dark inset so the stage 1 chips, steppers and field keep their contrast.
 */
@Composable
fun SheetRollPanel(
    host: DublSheetHost,
    request: SheetRollRequest,
    onRequestChange: (SheetRollRequest) -> Unit,
    result: RollResult?,
    onRoll: () -> Unit,
    onFollowUp: () -> Unit,
    history: List<FuryRollHistoryEntry>,
    modifier: Modifier = Modifier,
    showRollButton: Boolean = true,
    /** A characteristic picked for a skill roll; the screen saves it as the remembered one when it is allowed. */
    onAttributePicked: (skillId: String, attribute: AttributeId) -> Unit = { _, _ -> },
    /** «Об умении» for a skill roll: the screen opens the read-only rules dialog. */
    onSkillInfo: (skillId: String) -> Unit = {},
) {
    val options = sheetRollOptions(host, request)
    val subject = request.subject
    val context = (subject as? SheetRollSubject.Context)?.context
    val followUp = result?.let { sheetFollowUp(request, it) }
    // Desktop SkillRollDialog always shows the note; ContextRollDialog hides it while a withheld follow-up explains it.
    val note = result?.note?.takeIf { subject is SheetRollSubject.Skill || followUp != null || result.followUp == null }
    FuryRollTray(
        view = result?.let { sheetRollView(options, request, it) },
        history = history,
        onRollAgain = onRoll,
        modifier = modifier,
        emptyText = options.title,
        emptyRollLabel = sheetRollButtonLabel(rolled = false),
        blockedReason = options.blockedReason,
        showRollButton = showRollButton,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(FurySpacing.md)) {
            if (note != null) {
                Text(note, color = FuryOnAccentFill, style = MaterialTheme.typography.bodySmall)
            }
            if (followUp != null) {
                FuryButton(followUp.buttonTitle, onFollowUp, Modifier.fillMaxWidth(), style = FuryButtonStyle.OnAccent)
            }
            Column(
                Modifier.fillMaxWidth().clip(FuryShapes.panel).background(DublSurface).padding(FurySpacing.md),
                verticalArrangement = Arrangement.spacedBy(FurySpacing.md),
            ) {
                (subject as? SheetRollSubject.Skill)?.let { skillSubject ->
                    Text(
                        "Об умении", Modifier.clickable(role = Role.Button) { onSkillInfo(skillSubject.skillId) }.padding(vertical = 6.dp),
                        color = FuryColors.current.goldText, style = MaterialTheme.typography.labelLarge,
                    )
                }
                RollSetup(host, request, options, context, onRequestChange, onAttributePicked)
            }
        }
    }
}

@Composable
private fun RollSetup(
    host: DublSheetHost,
    request: SheetRollRequest,
    options: SheetRollOptions,
    context: RollContext?,
    onRequestChange: (SheetRollRequest) -> Unit,
    onAttributePicked: (skillId: String, attribute: AttributeId) -> Unit,
) {
    // Desktop «Расчёт броска» / Android «Бонус проверки»: what the roll adds before it is made.
    sheetRollFormulaLine(host, request)?.let { Muted(it) }
    if (options.skillChoices.isNotEmpty()) {
        Section("Умение")
        FuryChipGroup(
            choices = options.skillChoices.map { id -> FuryChoice(id, host.active.resolveSkill(id)?.name ?: id) },
            selected = request.skillId?.takeIf { it in options.skillChoices } ?: options.skillChoices.first(),
            onSelect = { onRequestChange(request.copy(skillId = it, attribute = null)) },
            columns = 2,
        )
    }
    if (options.attributeChoices.isNotEmpty()) {
        val subject = request.subject
        val skill = (subject as? SheetRollSubject.Skill)?.let { host.active.resolveSkill(it.skillId) }
        val fallback = skill?.let { sheetRememberedAttribute(it, host.extras) }
        val pick: (AttributeId) -> Unit = { attribute ->
            onRequestChange(request.copy(attribute = attribute))
            if (skill != null) onAttributePicked(skill.id, attribute)
        }
        Section("Характеристика")
        val others = options.otherAttributeChoices
        var showOthers by remember(subject) { mutableStateOf(false) }
        val oneOff = request.attribute != null && request.attribute in others
        FuryChipGroup(
            choices = options.attributeChoices.map { FuryChoice(it, it.shortTitle) },
            selected = request.attribute?.takeIf { it in options.attributeChoices } ?: if (oneOff) null else fallback ?: options.attributeChoices.first(),
            onSelect = pick,
            columns = 4,
        )
        if (others.isNotEmpty()) {
            if (showOthers || oneOff) {
                Muted("Другая характеристика — разово, по решению мастера:")
                FuryChipGroup(
                    choices = others.map { FuryChoice(it, it.shortTitle) },
                    selected = request.attribute?.takeIf { it in others },
                    onSelect = pick,
                    columns = 4,
                )
            } else {
                FuryButton("другая…", { showOthers = true }, style = FuryButtonStyle.Secondary)
            }
        }
    }
    if (context == RollContext.FLURRY) {
        Section("Оружие")
        FuryChipGroup(
            choices = FlurryWeaponProfile.entries.map { FuryChoice(it, it.title) },
            selected = request.flurryProfile,
            onSelect = { onRequestChange(request.copy(flurryProfile = it)) },
            columns = 1,
        )
        FuryMultiChipGroup(
            choices = listOf(FuryChoice(true, "Короткий")),
            selected = if (request.shortFlurry) setOf(true) else emptySet(),
            onToggle = { onRequestChange(request.copy(shortFlurry = !request.shortFlurry)) },
            columns = 1,
        )
        Muted(flurryRuleText(request.flurryProfile, request.shortFlurry))
    }
    Section("Режим")
    FuryChipGroup(MODE_CHOICES, request.mode, { onRequestChange(request.copy(mode = it)) }, columns = 3)
    if (request.mode == RollMode.ADVANTAGE || request.mode == RollMode.HINDRANCE) {
        LabelledStepper("Доп. костей", request.manualEffectCount, 1, 9) { onRequestChange(request.copy(manualEffectCount = it)) }
    }
    SituationalField(request, onRequestChange)
    TargetField(request, context, onRequestChange)
    if (options.automaticEffects.isNotEmpty()) {
        Muted("Автоматически: " + options.automaticEffects.joinToString())
    }
    if (options.effectToggles.isNotEmpty()) {
        Section("Эффекты")
        FuryMultiChipGroup(
            choices = options.effectToggles.map { (id, label) -> FuryChoice(id, label) },
            selected = request.selectedEffectIds,
            onToggle = { id ->
                val ids = request.selectedEffectIds
                onRequestChange(request.copy(selectedEffectIds = if (id in ids) ids - id else ids + id))
            },
            columns = 2,
        )
        options.effectToggles.forEach { (id, label) ->
            options.effectDescriptions[id]?.takeIf { it.isNotBlank() }?.let { Muted("$label: $it") }
        }
    }
    options.reminders.forEach { Muted(it) }
}

/** Keeps a local buffer so "-" and "" can be typed; the request only ever holds a parsed number. */
@Composable
private fun TargetField(request: SheetRollRequest, context: RollContext?, onRequestChange: (SheetRollRequest) -> Unit) {
    var buffer by remember(request.subject) { mutableStateOf(request.target?.toString().orEmpty()) }
    val shown = if (buffer.toIntOrNull() == request.target) buffer else request.target?.toString().orEmpty()
    FuryTextField(
        value = shown,
        onValueChange = { raw ->
            val cleaned = raw.filterIndexed { index, c -> c.isDigit() || (c == '-' && index == 0) }.take(4)
            buffer = cleaned
            onRequestChange(request.copy(target = cleaned.toIntOrNull()))
        },
        label = if (context == RollContext.ATTACK || context == RollContext.FLURRY) "Защита цели" else "Цель / СЛ",
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    )
}

/**
 * The typed modifier: an optional leading "-" and digits, clamped to −99..99 (the text then shows the
 * clamped number). "" and "-" stay as typed and count as 0.
 */
internal fun sheetSituationalInput(raw: String): Pair<String, Int> {
    val sign = if (raw.startsWith("-")) "-" else ""
    val cleaned = sign + raw.filter(Char::isDigit).take(3)
    val parsed = cleaned.toIntOrNull() ?: return cleaned to 0
    val clamped = parsed.coerceIn(-99, 99)
    return (if (clamped == parsed) cleaned else clamped.toString()) to clamped
}

/** The situational modifier: typed like the old dialogs, with −/+ steps beside the field. */
@Composable
private fun SituationalField(request: SheetRollRequest, onRequestChange: (SheetRollRequest) -> Unit) {
    var buffer by remember(request.subject) { mutableStateOf(request.situational.toString()) }
    val shown = if (sheetSituationalInput(buffer).second == request.situational) buffer else request.situational.toString()
    val state = furyStepperState(request.situational, -99, 99)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(FurySpacing.sm), verticalAlignment = Alignment.Bottom) {
        FuryTextField(
            value = shown,
            onValueChange = { raw ->
                val (text, value) = sheetSituationalInput(raw)
                buffer = text
                onRequestChange(request.copy(situational = value))
            },
            label = "Поправка",
            modifier = Modifier.weight(1f),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        FuryIconButton(
            "Уменьшить: Поправка",
            { onRequestChange(request.copy(situational = (request.situational - 1).coerceIn(-99, 99))) },
            Modifier.padding(bottom = 6.dp), // centred on the 56dp field
            enabled = state.canDecrease,
        ) {
            Text("−", color = DublText, style = MaterialTheme.typography.titleLarge)
        }
        FuryIconButton(
            "Увеличить: Поправка",
            { onRequestChange(request.copy(situational = (request.situational + 1).coerceIn(-99, 99))) },
            Modifier.padding(bottom = 6.dp),
            enabled = state.canIncrease,
        ) {
            Text("+", color = DublText, style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
private fun LabelledStepper(label: String, value: Int, min: Int, max: Int, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), color = DublText, style = MaterialTheme.typography.labelLarge)
        FuryStepper(
            value, { onChange(it.coerceIn(min, max)) }, min = min, max = max,
            decreaseLabel = "Уменьшить: $label", increaseLabel = "Увеличить: $label",
        )
    }
}

@Composable
private fun Section(title: String) {
    Text(title.uppercase(), Modifier.padding(top = 2.dp), color = DublMuted, style = furyOverlineStyle())
}

@Composable
private fun Muted(text: String) {
    Text(text, color = DublMuted, style = MaterialTheme.typography.bodySmall)
}

/** Same wording as the desktop flurry dialog. */
private fun flurryRuleText(profile: FlurryWeaponProfile, short: Boolean): String {
    val rule = profile.rule(short)
    return buildString {
        append("Бонус +${rule.attackBonus}. ")
        append("1 попадание за каждые ${rule.excessPerHit} превышения")
        if (rule.shortExcessPenalty > 0) append("; для короткого требуемое превышение +${rule.shortExcessPenalty}")
        append(". Максимум ${rule.maxHits}. Реакцию использовать нельзя.")
        if (rule.alternateWeaponDamage) append(" Урон двух оружий чередуется.")
        if (short) append(" Короткий шквал · ${rule.actionCost}.")
    }
}
