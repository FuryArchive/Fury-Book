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

enum class SheetVitalColor { HEALTH, STAMINA, MANA, CUSTOM }

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
)

data class SheetStat(
    val label: String,
    val value: Int,
    val roll: SheetRollSubject?,
    val valueText: String = value.toString(),
)

data class SheetSkillRow(val skillId: String, val name: String, val attributeLabel: String, val rank: Int, val bonus: Int?)

data class SheetSkillGroup(val title: String, val rows: List<SheetSkillRow>)

/** [key] is the [CharacterConditionId] name, or the custom condition id when [custom]. */
data class SheetConditionChip(val key: String, val title: String, val active: Boolean, val custom: Boolean)

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
                    SheetVitalColor.CUSTOM, model.resourceId, null, model.restoreable,
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

/** All eight characteristics in [AttributeId] order, each rollable. */
fun sheetAttributes(character: DublCharacter): List<SheetStat> =
    AttributeId.entries.map { id ->
        SheetStat(id.title, character.attribute(id), SheetRollSubject.Attribute(id))
    }

/** Mirrors desktop HeroMetricsStrip; Бег's [SheetStat.valueText] is the desktop formatNumber output. */
fun sheetMetrics(character: DublCharacter): List<SheetStat> = listOf(
    SheetStat("Защита", character.defense, null),
    SheetStat("Рефлексы", character.reflexes, SheetRollSubject.Context(RollContext.REFLEXES)),
    SheetStat("Инициатива", character.initiative, SheetRollSubject.Context(RollContext.INITIATIVE)),
    SheetStat("Стойкость", character.fortitude, SheetRollSubject.Context(RollContext.FORTITUDE)),
    SheetStat("Бег", character.runFull.roundToInt(), SheetRollSubject.Context(RollContext.RUN), formatSheetNumber(character.runFull)),
    SheetStat("Размер", character.size, null),
)

/**
 * Mirrors desktop SheetSkillsPanel grouping (saved groups normalised against category defaults, hidden
 * skills dropped, empty groups removed), keeping only trained skills (rank above zero).
 */
fun sheetSkillGroups(host: DublSheetHost): List<SheetSkillGroup> {
    val character = host.active
    val allSkills = character.resolvedSkills(includeHidden = true)
    val visibleById = character.resolvedSkills().filter { it.rank > 0 }.associateBy { it.id }
    if (visibleById.isEmpty()) return emptyList()
    val defaults = SkillCategory.entries.mapNotNull { category ->
        allSkills.filter { it.category == category }.map { it.id }.takeIf { it.isNotEmpty() }
            ?.let { SheetGroup("skills:${category.name}", category.title, it) }
    }
    val groups = SheetGroupingRules.normalize(host.extras.skillGroups, defaults, allSkills.map { it.id }, "skills:ungrouped")
    val effectRules = SkillEffectRules(character, host.developmentCatalog, host.skillEffectCatalog)
    return groups.mapNotNull { group ->
        val rows = group.itemIds.mapNotNull(visibleById::get).map { skill -> skillRow(character, effectRules, skill) }
        if (rows.isEmpty()) null else SheetSkillGroup(group.title, rows)
    }
}

private fun skillRow(character: DublCharacter, effectRules: SkillEffectRules, skill: ResolvedSkill): SheetSkillRow {
    val attribute = skill.stockAttribute
    val calculation = character.skillCalculationForRoll(skill, attribute)
    val automatic = effectRules.forSkill(skill).automaticBonus
    return SheetSkillRow(
        skillId = skill.id,
        name = skill.name,
        attributeLabel = attribute.shortTitle,
        rank = skill.rank,
        bonus = calculation.total?.plus(automatic),
    )
}

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
        )
    }
    val custom = extras.customConditions.filter { it.active }.map {
        SheetConditionChip(key = it.id, title = it.title, active = true, custom = true)
    }
    return builtIn + custom
}
