package com.furybook.dubl.ui.sheet

import com.furybook.content.FcpOrderedUiItem
import com.furybook.content.FcpUiHostOrder
import com.furybook.content.orderedUiItems
import com.furybook.dubl.model.AttributeId
import com.furybook.dubl.model.CharacterConditionId
import com.furybook.dubl.model.CharacterSheetResourceId
import com.furybook.dubl.model.DublCharacter
import com.furybook.dubl.model.ResolvedSkill
import com.furybook.dubl.model.RollContext
import com.furybook.dubl.model.SheetGroup
import com.furybook.dubl.model.SheetGroupingRules
import com.furybook.dubl.model.SkillCategory
import com.furybook.dubl.model.SkillEffectRules
import com.furybook.dubl.model.resolvedSkills
import com.furybook.dubl.model.skillCalculationForRoll
import kotlin.math.roundToInt

/** [MOUNTED] = a content-pack meter (Ци); its accent comes from the pack presentation, which maps to the crimson accent today. */
enum class SheetVitalColor { HEALTH, STAMINA, MANA, CUSTOM, MOUNTED }

/** One resource tile. [resource] is set for built-in and mounted meters, [customUid] for custom resources. */
data class SheetVital(
    val id: String,
    val label: String,
    val current: Int,
    val maximum: Int,
    val color: SheetVitalColor,
    val resource: CharacterSheetResourceId?,
    val customUid: String?,
    val restoreable: Boolean,
    /** Pack presentation tokens of a mounted meter (FcpUiIconToken / FcpUiAccentToken); null otherwise. */
    val iconToken: String? = null,
    val accentToken: String? = null,
)

data class SheetStat(
    val label: String,
    val value: Int,
    val roll: SheetRollSubject?,
    val valueText: String = value.toString(),
    /** Set for metrics: tapping the tile opens the formula popup (which has the roll button) instead of rolling. */
    val info: SheetStatId? = null,
)

/**
 * [perkMark] is «+N» when perks add N automatically, «★» when they only offer roll options or reminders;
 * [perkTip] names those perks. [breakdown] explains the bonus («… 2d6 + 15 (Ловкость 8 + ранг 7)»).
 */
data class SheetSkillRow(
    val skillId: String,
    val name: String,
    val attributeLabel: String,
    val rank: Int,
    val bonus: Int?,
    val perkMark: String? = null,
    val perkTip: String? = null,
    val breakdown: String = "",
)

/**
 * [key] is the [CharacterConditionId] name, or the custom condition id when [custom]. [automatic] chips
 * (Слабость at 0 endurance that the player did not set) are shown but cannot be toggled.
 */
data class SheetConditionChip(
    val key: String,
    val title: String,
    val active: Boolean,
    val custom: Boolean,
    val automatic: Boolean = false,
    /** What the condition does: the player's override, the catalog text, or the custom condition's own text. */
    val description: String = "",
    val kind: SheetConditionKind = SheetConditionKind.HARMFUL,
)

/** Chip colour family: built-in conditions are harmful except the few that help; custom ones are their own family. */
enum class SheetConditionKind { HARMFUL, HELPFUL, CUSTOM }

private val HELPFUL_CONDITIONS = setOf(CharacterConditionId.INSPIRED, CharacterConditionId.INVISIBILITY)

/** Same quick checks, same order, as the desktop «Боевые действия» panel. */
val SHEET_COMBAT_CONTEXTS: List<RollContext> = listOf(
    RollContext.DODGE,
    RollContext.ATTACK,
    RollContext.FLURRY,
    RollContext.PARRY,
    RollContext.FEINT,
    RollContext.GRAPPLE,
    RollContext.DISARM,
    RollContext.TRIP,
    RollContext.PUSH,
    RollContext.KNOCKDOWN,
    RollContext.BREAK_ITEM,
)

/** Mirrors desktop HeroResources: health, endurance, mana, mounted meters, custom; ordered like its tiles. */
fun sheetVitals(host: DublSheetHost): List<SheetVital> {
    val character = host.active
    val hidden = host.extras.hiddenResourceIds
    val items = mutableListOf<FcpOrderedUiItem<SheetVital>>()
    if (CharacterSheetResourceId.HEALTH !in hidden) {
        items += FcpOrderedUiItem(
            FcpUiHostOrder.PRIMARY, "health",
            SheetVital(
                "health", "Здоровье", character.hpCurrent, character.healthMaximum,
                SheetVitalColor.HEALTH, CharacterSheetResourceId.HEALTH, null, restoreable = false,
            ),
        )
    }
    if (CharacterSheetResourceId.ENDURANCE !in hidden) {
        items += FcpOrderedUiItem(
            FcpUiHostOrder.SECONDARY, "endurance",
            SheetVital(
                "endurance", "Выносливость", character.enduranceCurrent, character.enduranceMaximum,
                SheetVitalColor.STAMINA, CharacterSheetResourceId.ENDURANCE, null, restoreable = false,
            ),
        )
    }
    if ((character.manaEnabled || character.effectiveManaMaximum > 0) && CharacterSheetResourceId.MANA !in hidden) {
        items += FcpOrderedUiItem(
            FcpUiHostOrder.TERTIARY, "mana",
            SheetVital(
                "mana", "Мана", character.manaCurrent, character.effectiveManaMaximum,
                SheetVitalColor.MANA, CharacterSheetResourceId.MANA, null, restoreable = false,
            ),
        )
    }
    host.resourceMeters()
        .filter { it.available && it.resourceId !in hidden }
        .forEach { model ->
            items += FcpOrderedUiItem(
                model.presentation.order, "fcp:${model.resourceId.name}",
                SheetVital(
                    "fcp:${model.resourceId.name}", model.presentation.label, model.current, model.maximum,
                    SheetVitalColor.MOUNTED, model.resourceId, null, model.restoreable,
                    iconToken = model.presentation.icon, accentToken = model.presentation.accent,
                ),
            )
        }
    character.customResources.forEachIndexed { index, resource ->
        items += FcpOrderedUiItem(
            FcpUiHostOrder.CUSTOM + index, "custom:${resource.uid}",
            SheetVital(
                "custom:${resource.uid}", resource.name, resource.current, resource.maximum,
                SheetVitalColor.CUSTOM, null, resource.uid, restoreable = false,
            ),
        )
    }
    return orderedUiItems(items)
}

/** Desktop formatNumber ("%.2f", trailing zeros trimmed) without String.format, which common code lacks. */
internal fun formatSheetNumber(value: Double): String {
    if (value % 1.0 == 0.0) return value.toInt().toString()
    val hundredths = (value * 100).roundToInt()
    val whole = hundredths / 100
    val fraction = (hundredths % 100).toString().padStart(2, '0').trimEnd('0')
    return if (fraction.isEmpty()) whole.toString() else "$whole.$fraction"
}

/** Mirrors desktop HeroMetricsStrip; Бег's [SheetStat.valueText] is the desktop formatNumber output. */
fun sheetMetrics(character: DublCharacter): List<SheetStat> = listOf(
    SheetStat("Защита", character.defense, null, info = SheetStatId.DEFENSE),
    SheetStat("Рефлексы", character.reflexes, SheetRollSubject.Context(RollContext.REFLEXES), info = SheetStatId.REFLEXES),
    SheetStat("Инициатива", character.initiative, SheetRollSubject.Context(RollContext.INITIATIVE), info = SheetStatId.INITIATIVE),
    SheetStat("Стойкость", character.fortitude, SheetRollSubject.Context(RollContext.FORTITUDE), info = SheetStatId.FORTITUDE),
    SheetStat(
        "Бег", character.runFull.roundToInt(), SheetRollSubject.Context(RollContext.RUN), formatSheetNumber(character.runFull),
        info = SheetStatId.RUN,
    ),
    SheetStat("Размер", character.size, null, info = SheetStatId.SIZE),
)

/** Mirrors desktop HeroConditions: active conditions (plus automatic Слабость at 0 endurance), then active custom ones. */
fun sheetActiveConditions(host: DublSheetHost): List<SheetConditionChip> {
    val extras = host.extras
    val effective = buildSet {
        addAll(extras.activeConditions)
        if (host.active.enduranceCurrent == 0) add(CharacterConditionId.WEAKNESS)
    }
    val builtIn = effective.map { condition ->
        SheetConditionChip(
            key = condition.name,
            title = extras.conditionOverrides[condition]?.title ?: condition.title,
            active = true,
            custom = false,
            // Desktop HeroConditions: only the endurance-driven Слабость the player did not set is automatic.
            automatic = condition == CharacterConditionId.WEAKNESS && condition !in extras.activeConditions,
            description = extras.conditionOverrides[condition]?.description?.takeIf { it.isNotBlank() }
                ?: host.conditionCatalog.summary(condition),
            kind = if (condition in HELPFUL_CONDITIONS) SheetConditionKind.HELPFUL else SheetConditionKind.HARMFUL,
        )
    }
    val custom = extras.customConditions.filter { it.active }.map {
        SheetConditionChip(
            key = it.id, title = it.title, active = true, custom = true,
            description = it.description, kind = SheetConditionKind.CUSTOM,
        )
    }
    return builtIn + custom
}
