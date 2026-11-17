package com.furybook.dubl.ui.sheet

import com.furybook.dubl.model.AttributeId
import com.furybook.dubl.model.CharacterRollPreset
import com.furybook.dubl.model.FlurryWeaponProfile
import com.furybook.dubl.model.RollContext
import com.furybook.dubl.model.RollContribution
import com.furybook.dubl.model.RollFollowUp
import com.furybook.dubl.model.RollMode
import com.furybook.dubl.model.RollResult
import com.furybook.dubl.model.RollTargetOutcome
import com.furybook.dubl.model.SkillEffectRules
import com.furybook.dubl.model.SkillRollEffectResolution
import com.furybook.dubl.model.allowedAttributes
import com.furybook.dubl.model.allowedSkillIds
import com.furybook.dubl.model.availableFollowUp
import com.furybook.dubl.model.compareRollToTarget
import com.furybook.dubl.model.developmentNormalize
import com.furybook.dubl.model.resolve
import com.furybook.dubl.model.resolveAttack
import com.furybook.dubl.model.resolveSkill
import com.furybook.dubl.model.rollCheck
import com.furybook.dubl.model.rollFollowUp
import com.furybook.dubl.model.rollPreset
import com.furybook.dubl.model.rule
import com.furybook.dubl.model.selectedTotals
import com.furybook.dubl.model.skillCalculationForRoll
import com.furybook.dubl.model.summary
import com.furybook.ui.components.FuryRollTerm
import com.furybook.ui.components.FuryRollView
import kotlin.random.Random

/** What a sheet roll is about: a characteristic, a skill, or one of the fixed roll contexts. */
sealed interface SheetRollSubject {
    data class Attribute(val id: AttributeId) : SheetRollSubject
    data class Skill(val skillId: String) : SheetRollSubject
    data class Context(val context: RollContext) : SheetRollSubject // FORTITUDE…BREAK_ITEM
}

/** Everything the player chose in the roll tray. Plain data; the rules live in the shared model. */
data class SheetRollRequest(
    val subject: SheetRollSubject,
    val skillId: String? = null, // chosen skill for contexts that allow several
    val attribute: AttributeId? = null, // chosen attribute when the skill/context allows several
    val mode: RollMode = RollMode.NORMAL,
    val manualEffectCount: Int = 1, // 1..9, used only when mode != NORMAL
    val selectedEffectIds: Set<String> = emptySet(),
    val situational: Int = 0, // -99..99
    val target: Int? = null, // СЛ, or defence for ATTACK/FLURRY
    val flurryProfile: FlurryWeaponProfile = FlurryWeaponProfile.LIGHT,
    val shortFlurry: Boolean = false,
)

/** What the tray may offer for a request, and whether it can be rolled right now. */
data class SheetRollOptions(
    val title: String,
    val skillChoices: List<String>, // empty when fixed
    val attributeChoices: List<AttributeId>, // empty when fixed
    val effectToggles: List<Pair<String, String>>, // id to label, from SkillEffectRules
    val preset: CharacterRollPreset,
    val blockedReason: String?, // e.g. preset unavailable, or both advantage and hindrance
    val reminders: List<String> = emptyList(), // "Источник: текст", as the desktop dialogs list them
    val effectDescriptions: Map<String, String> = emptyMap(), // toggle id to its rule text
    val automaticEffects: List<String> = emptyList(), // "Источник +N", applied without a toggle
)

private const val BOTH_MODES_REASON = "Нельзя бросать с преимуществом и помехой одновременно"
private const val UNAVAILABLE_REASON = "Бросок недоступен"

internal fun sheetRollD6(): Int = Random.nextInt(1, 7)

/** Fully resolved inputs for one roll: what [rollCheck] needs plus what the view shows. */
private class ResolvedSheetRoll(
    val options: SheetRollOptions,
    val checkBonusLabel: String,
    val effectTerm: Int,
    val mode: RollMode,
    val effectCount: Int,
)

private fun resolveSheetRoll(host: DublSheetHost, request: SheetRollRequest): ResolvedSheetRoll =
    when (val subject = request.subject) {
        is SheetRollSubject.Skill -> resolveSkillRoll(host, request, subject.skillId)
        is SheetRollSubject.Attribute -> resolveContextRoll(host, request, RollContext.ATTRIBUTE, fixedAttribute = subject.id)
        is SheetRollSubject.Context -> resolveContextRoll(host, request, subject.context, fixedAttribute = null)
    }

/** Mirrors desktop SkillRollDialog: any characteristic, skill effects, and adv/hind from mode + toggles. */
private fun resolveSkillRoll(host: DublSheetHost, request: SheetRollRequest, skillId: String): ResolvedSheetRoll {
    val character = host.active
    val skill = character.resolveSkill(skillId)
    if (skill == null) {
        val preset = character.rollPreset(RollContext.SKILL, skillId, request.attribute)
        val options = SheetRollOptions(
            title = preset.title,
            skillChoices = emptyList(),
            attributeChoices = emptyList(),
            effectToggles = emptyList(),
            preset = preset,
            blockedReason = preset.unavailableReason.ifBlank { UNAVAILABLE_REASON },
        )
        return ResolvedSheetRoll(options, preset.title, 0, RollMode.NORMAL, 0)
    }
    // The sheet lets the player pick any characteristic for a single skill roll (desktop
    // SkillAttributeChoiceDialog), starting from the skill's stock one.
    val attribute = request.attribute ?: skill.stockAttribute
    val calculation = character.skillCalculationForRoll(skill, attribute)
    val preset = CharacterRollPreset(
        context = RollContext.SKILL,
        title = skill.name,
        bonus = calculation.total,
        contributions = calculation.contributions.map { RollContribution(it.label, it.value) },
        formulaText = calculation.formulaText(skill),
        unavailableReason = calculation.unavailableReason,
    )
    val effects: SkillRollEffectResolution =
        SkillEffectRules(character, host.developmentCatalog, host.skillEffectCatalog).forSkill(skill)
    val selectedTotals = effects.options.selectedTotals(request.selectedEffectIds)
    val autoBonus = effects.automaticBonus
    val toggledBonus = selectedTotals.numericBonus
    val manual = request.manualEffectCount.coerceIn(1, 9)
    val manualAdvantage = if (request.mode == RollMode.ADVANTAGE) manual else 0
    val manualHindrance = if (request.mode == RollMode.HINDRANCE) manual else 0
    val adv = selectedTotals.advantageDice + manualAdvantage
    val hind = selectedTotals.hindranceDice + manualHindrance
    val bothModes = adv > 0 && hind > 0
    val effectiveMode = when {
        bothModes -> RollMode.NORMAL
        adv > 0 -> RollMode.ADVANTAGE
        hind > 0 -> RollMode.HINDRANCE
        else -> RollMode.NORMAL
    }
    val blockedReason = when {
        !preset.available -> preset.unavailableReason.ifBlank { UNAVAILABLE_REASON }
        bothModes -> BOTH_MODES_REASON
        else -> null
    }
    val options = SheetRollOptions(
        title = skill.name,
        skillChoices = emptyList(),
        attributeChoices = AttributeId.entries.toList(),
        effectToggles = effects.options.map { it.id to it.label },
        effectDescriptions = effects.options.associate { it.id to it.description },
        automaticEffects = effects.automaticContributions.map { "${it.label} ${signed(it.value)}" },
        preset = preset,
        blockedReason = blockedReason,
        reminders = effects.reminders.map { "${it.sourceName}: ${it.effectText}" },
    )
    return ResolvedSheetRoll(
        options = options,
        checkBonusLabel = "${skill.name} + ${attribute.title}",
        effectTerm = autoBonus + toggledBonus,
        mode = effectiveMode,
        effectCount = when (effectiveMode) {
            RollMode.ADVANTAGE -> adv
            RollMode.HINDRANCE -> hind
            RollMode.NORMAL -> 0
        },
    )
}

/** Mirrors desktop ContextRollDialog: preset bonus (+ flurry rule), no skill effects applied. */
private fun resolveContextRoll(
    host: DublSheetHost,
    request: SheetRollRequest,
    context: RollContext,
    fixedAttribute: AttributeId?,
): ResolvedSheetRoll {
    val character = host.active
    val skills = context.allowedSkillIds().filter { character.resolveSkill(it) != null }
    val skillId = request.skillId?.takeIf { it in skills } ?: skills.firstOrNull()
    val attrOptions = if (context == RollContext.ATTRIBUTE) AttributeId.entries.toList() else context.allowedAttributes(skillId)
    val selectedAttribute = fixedAttribute?.takeIf { it in attrOptions }
        ?: request.attribute?.takeIf { it in attrOptions }
        ?: attrOptions.firstOrNull()
    val basePreset = character.rollPreset(context, skillId, selectedAttribute)
    val flurryRule = if (context == RollContext.FLURRY) request.flurryProfile.rule(request.shortFlurry) else null
    val baseBonus = basePreset.bonus
    val preset = if (flurryRule != null && baseBonus != null) {
        val label = if (request.shortFlurry) "Короткий шквал" else "Шквал атак"
        basePreset.copy(
            bonus = baseBonus + flurryRule.attackBonus,
            contributions = basePreset.contributions + RollContribution(label, flurryRule.attackBonus),
            formulaText = "${basePreset.formulaText} + $label",
        )
    } else {
        basePreset
    }
    // Desktop ContextRollDialog: flurry uses the attack reminders; effects already in the preset are skipped.
    val alreadyAppliedLabels = preset.contributions.map { developmentNormalize(it.label) }.toSet()
    val reminders = SkillEffectRules(character, host.developmentCatalog, host.skillEffectCatalog)
        .forContext(if (context == RollContext.FLURRY) RollContext.ATTACK else context)
        .filterNot { developmentNormalize(it.sourceName) in alreadyAppliedLabels }
    val manual = request.manualEffectCount.coerceIn(1, 9)
    val adv = if (request.mode == RollMode.ADVANTAGE) manual else 0
    val hind = if (request.mode == RollMode.HINDRANCE) manual else 0
    val mode = when {
        adv > 0 -> RollMode.ADVANTAGE
        hind > 0 -> RollMode.HINDRANCE
        else -> RollMode.NORMAL
    }
    val options = SheetRollOptions(
        title = preset.title,
        skillChoices = skills.takeIf { it.size > 1 }.orEmpty(),
        attributeChoices = if (fixedAttribute != null) emptyList() else attrOptions.takeIf { it.size > 1 }.orEmpty(),
        effectToggles = emptyList(),
        preset = preset,
        blockedReason = if (preset.available) null else preset.unavailableReason.ifBlank { UNAVAILABLE_REASON },
        reminders = reminders.map { "${it.sourceName}: ${it.effectText}" },
    )
    return ResolvedSheetRoll(
        options = options,
        checkBonusLabel = preset.title,
        effectTerm = 0,
        mode = mode,
        effectCount = if (mode == RollMode.ADVANTAGE) adv else if (mode == RollMode.HINDRANCE) hind else 0,
    )
}

fun sheetRollOptions(host: DublSheetHost, request: SheetRollRequest): SheetRollOptions =
    resolveSheetRoll(host, request).options

/**
 * The pre-roll line: "Бонус проверки +7 · 2d6 + Ловкость + Атлетика", with toggled/automatic skill
 * effects added as a term. Null while the roll has no bonus (its blocked reason explains why).
 */
fun sheetRollFormulaLine(host: DublSheetHost, request: SheetRollRequest): String? {
    val resolved = resolveSheetRoll(host, request)
    val preset = resolved.options.preset
    val bonus = preset.bonus ?: return null
    val effects = resolved.effectTerm
    val formula = preset.formulaText.ifBlank { null }?.let { text ->
        if (effects != 0) "$text + Эффекты (${signed(effects)})" else text
    }
    return buildString {
        append("Бонус проверки ").append(signed(bonus + effects))
        if (formula != null) append(" · ").append(formula)
    }
}

/** Rolls the request through the shared engine, or returns null when [SheetRollOptions.blockedReason] is set. */
fun performSheetRoll(
    host: DublSheetHost,
    request: SheetRollRequest,
    rollDie: () -> Int = ::sheetRollD6,
): RollResult? {
    val resolved = resolveSheetRoll(host, request)
    if (resolved.options.blockedReason != null) return null
    val bonus = resolved.options.preset.bonus ?: return null
    return rollCheck(
        mode = resolved.mode,
        effectCount = resolved.effectCount,
        checkBonus = bonus + resolved.effectTerm,
        checkBonusLabel = resolved.checkBonusLabel,
        situationalBonus = request.situational.coerceIn(-99, 99),
        rollDie = rollDie,
    )
}

fun followUpSheetRoll(result: RollResult, rollDie: () -> Int = ::sheetRollD6): RollResult =
    rollFollowUp(result, rollDie)

/**
 * The follow-up die the player may still roll, as the desktop dialogs offer it: an attack against
 * a known defence withholds the superiority die when the natural 6-6 already hits.
 */
fun sheetFollowUp(request: SheetRollRequest, result: RollResult): RollFollowUp? {
    val subject = request.subject
    val isAttack = subject is SheetRollSubject.Context && subject.context == RollContext.ATTACK
    return result.availableFollowUp(attackDefense = request.target.takeIf { isAttack })
}

fun sheetRollView(options: SheetRollOptions, request: SheetRollRequest, result: RollResult): FuryRollView {
    val context = (request.subject as? SheetRollSubject.Context)?.context
    val terms = buildList {
        options.preset.contributions.forEach { add(FuryRollTerm(it.label, it.value)) }
        val effectTerm = result.checkBonus - (options.preset.bonus ?: result.checkBonus)
        if (effectTerm != 0) add(FuryRollTerm("Эффекты", effectTerm))
        if (result.situationalBonus != 0) add(FuryRollTerm("Поправка", result.situationalBonus))
        val superiorityDie = result.followUpDie.takeIf { result.resolvedFollowUp == RollFollowUp.SUPERIORITY_DIE }
        if (superiorityDie != null) add(FuryRollTerm("Кость превосходства", superiorityDie))
    }
    val target = request.target
    val outcome = when {
        target == null -> null
        context == RollContext.ATTACK -> resolveAttack(result, target).let { "${it.outcome.title} (${signed(it.margin)})" }
        context == RollContext.FLURRY -> request.flurryProfile.rule(request.shortFlurry).resolve(result, target).summary
        else -> compareRollToTarget(result, target).let { comparison ->
            val text = when (comparison.outcome) {
                RollTargetOutcome.SUCCESS -> "Выше цели на ${comparison.margin}"
                RollTargetOutcome.TIE -> "Равно цели · разница 0"
                RollTargetOutcome.FAILURE -> "Ниже цели на ${kotlin.math.abs(comparison.margin)}"
            }
            "СЛ $target: $text"
        }
    }
    return FuryRollView(
        title = options.title,
        dice = result.dice,
        terms = terms,
        total = result.total,
        outcome = outcome,
        keptDice = result.chosenIndices,
        specialResult = result.specialResult?.title,
    )
}

private fun signed(value: Int): String = if (value >= 0) "+$value" else value.toString()
