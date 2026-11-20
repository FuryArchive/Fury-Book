package com.furybook.dubl.ui.sheet

import com.furybook.content.FcpUiComponent
import com.furybook.content.FcpUiSurface
import com.furybook.dubl.content.DublUiFeature
import com.furybook.dubl.content.forFeature
import com.furybook.dubl.model.CharacterEconomy
import com.furybook.dubl.model.DevelopmentProgress
import com.furybook.dubl.model.DevelopmentRules
import com.furybook.dubl.model.DevelopmentSheetItemSource
import com.furybook.dubl.model.DevelopmentSheetSectionType
import com.furybook.dubl.model.RequirementStatus
import com.furybook.dubl.model.SheetGroup
import com.furybook.dubl.model.SheetGroupingRules

/** Everything the XP dialog shows; numbers come straight from [CharacterEconomy.breakdown]. */
data class SheetEconomyView(
    val total: Int,
    val creation: Int,
    val adjustment: Int,
    val abilityPointsOverride: Int?,
    val lines: List<Pair<String, Int>>,
    val spent: Int,
    val remaining: Int,
    val abilityPointsSpent: Int,
    val abilityPointsBudget: Int,
    val abilityPointsRemaining: Int,
    val recommendedAbilityPoints: Int,
    val selfTaughtNote: String?,
    val unpricedSpellsWarning: String?,
    val creationComplete: Boolean,
)

/**
 * Whether chi XP counts: a pack mounts the chi feature on the economy XP line (the old sheets' FCP check,
 * `uiMounts(CHARACTER_ECONOMY, XP_LINE).forFeature(CHI)`), not a hard-coded pack flag.
 */
fun sheetChiEconomyMounted(host: DublSheetHost): Boolean =
    host.uiMounts(FcpUiSurface.CHARACTER_ECONOMY, FcpUiComponent.XP_LINE).forFeature(DublUiFeature.CHI) != null

fun sheetEconomyView(host: DublSheetHost): SheetEconomyView {
    val character = host.active
    val showChi = sheetChiEconomyMounted(host)
    val economy = CharacterEconomy.breakdown(character, host.developmentCatalog, includeChi = showChi)
    val lines = buildList {
        add("Характеристики" to economy.attributeXp)
        add("Умения" to economy.skillXp)
        add("Навыки" to economy.developmentXp)
        add("Мана" to economy.manaXp)
        if (showChi) add("ЦИ" to economy.chiXp)
        add("Школы" to economy.magicSchoolXp)
        add("Заклинания" to economy.spellXp)
    }
    val hasSelfTaught = host.developmentCatalog.matchingName("Самоучка").any { entry ->
        (character.development[entry.id]?.rank ?: 0) > 0
    }
    return SheetEconomyView(
        total = character.experience,
        creation = character.creationExperience,
        adjustment = character.xpAdjustment,
        abilityPointsOverride = character.abilityPointsOverride,
        lines = lines,
        spent = economy.spentXp,
        remaining = economy.remainingXp,
        abilityPointsSpent = economy.abilityPointsSpent,
        abilityPointsBudget = economy.abilityPointsBudget,
        abilityPointsRemaining = economy.abilityPointsRemaining,
        recommendedAbilityPoints = economy.recommendedAbilityPoints,
        selfTaughtNote = if (!hasSelfTaught) null else if (character.creationComplete) {
            "Самоучка: скидка на первые два ранга умений действует только после создания. История покупок в старых сейвах не хранится, поэтому скидка не применяется ретроактивно автоматически — используйте ручную поправку XP, если она положена."
        } else {
            "Самоучка не работает во время создания персонажа; его скидка начнёт иметь смысл только после завершения создания."
        },
        unpricedSpellsWarning = economy.unpricedLearnedSpells.takeIf { it > 0 }?.let { "Заклинаний без цены опыта: $it" },
        creationComplete = character.creationComplete,
    )
}

/** Persisted development groups normalised against the default section groups, as desktop SheetDevelopmentPanel did. */
fun normalizedDevelopmentGroups(host: DublSheetHost): List<SheetGroup> {
    val rules = developmentRules(host)
    val items = rules.ownedSheetSections().flatMap { it.items }.distinctBy { it.entry.id }
    val defaults = rules.ownedSheetSections().map { section ->
        SheetGroup("development:${section.type.name}", developmentSectionTitle(section.type), section.items.map { it.entry.id }.distinct())
    }
    return SheetGroupingRules.normalize(host.extras.developmentGroups, defaults, items.map { it.entry.id }, "development:ungrouped")
}

/** Desktop/Android critical-health styling level: 0 normal, 1 ≤ 50 %, 2 ≤ 25 %, 3 at or below zero. */
fun healthCriticalLevel(current: Int, maximum: Int): Int {
    if (current <= 0) return 3
    if (maximum <= 0) return 0
    val ratio = current.toDouble() / maximum.toDouble()
    if (ratio <= 0.25) return 2
    if (ratio <= 0.50) return 1
    return 0
}

internal fun developmentRules(host: DublSheetHost): DevelopmentRules {
    val character = host.active
    return DevelopmentRules(character, host.developmentCatalog, DevelopmentProgress(character.development))
}

private fun developmentSectionTitle(type: DevelopmentSheetSectionType): String = when (type) {
    DevelopmentSheetSectionType.REGULAR -> "Обычные навыки"
    DevelopmentSheetSectionType.SPECIAL -> "Спец. навыки"
    DevelopmentSheetSectionType.MARTIAL_ARTS -> "Боевые искусства"
    DevelopmentSheetSectionType.CHI -> "ЦИ"
}
