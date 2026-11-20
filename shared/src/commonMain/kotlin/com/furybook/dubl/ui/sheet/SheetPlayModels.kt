package com.furybook.dubl.ui.sheet

import com.furybook.dubl.model.AttributeId
import com.furybook.dubl.model.CharacterNote
import com.furybook.dubl.model.DevelopmentSheetItemSource
import com.furybook.dubl.model.DublCharacter
import com.furybook.dubl.model.RequirementStatus
import com.furybook.dubl.model.ResolvedSkill
import com.furybook.dubl.model.SheetGroupingRules
import com.furybook.dubl.model.SkillEffectRules
import com.furybook.dubl.model.resolvedSkills
import com.furybook.dubl.model.skillCalculationForRoll

const val PERK_SEARCH_THRESHOLD = 10
const val NOTE_SEARCH_THRESHOLD = 6
const val CONDITION_INLINE_CAP = 5

/** One attribute of the play sheet's Умения column, with the skills it drives. */
data class SheetAttributeSkills(val attribute: AttributeId, val label: String, val value: Int, val rows: List<SheetSkillRow>)

/**
 * All eight attributes in sheet order, each with its visible skills (by stock attribute), strongest first.
 * [rankedOnly] keeps skills with a rank; an attribute with none still appears, with no rows.
 */
fun sheetSkillsByAttribute(host: DublSheetHost, rankedOnly: Boolean): List<SheetAttributeSkills> {
    val character = host.active
    val effects = SkillEffectRules(character, host.developmentCatalog, host.skillEffectCatalog)
    val skills = character.resolvedSkills().filter { !rankedOnly || it.rank > 0 }
    return AttributeId.entries.map { attribute ->
        val rows = skills.filter { it.stockAttribute == attribute }
            .map { playSkillRow(character, effects, it) }
            .sortedWith(compareByDescending<SheetSkillRow> { it.bonus ?: Int.MIN_VALUE }.thenBy { it.name })
        SheetAttributeSkills(attribute, attribute.shortTitle, character.attribute(attribute), rows)
    }
}

/** (skills with a rank, all visible skills) for the «N из M» header. */
fun sheetSkillTotals(host: DublSheetHost): Pair<Int, Int> {
    val skills = host.active.resolvedSkills()
    return skills.count { it.rank > 0 } to skills.size
}

private fun playSkillRow(character: DublCharacter, effects: SkillEffectRules, skill: ResolvedSkill): SheetSkillRow {
    val attribute = skill.stockAttribute
    val resolution = effects.forSkill(skill)
    val automatic = resolution.automaticBonus
    val bonus = character.skillCalculationForRoll(skill, attribute).total?.plus(automatic)
    val sources = (resolution.automaticContributions.map { it.label } + resolution.options.map { it.sourceName } +
        resolution.reminders.map { it.sourceName }).filter { it.isNotBlank() }.distinct()
    val mark = when {
        automatic != 0 -> if (automatic > 0) "+$automatic" else automatic.toString()
        sources.isNotEmpty() -> "★"
        else -> null
    }
    val breakdown = if (bonus == null) skill.name else buildString {
        append("${skill.name}: 2d6 + $bonus (${attribute.title} ${character.attribute(attribute)} + ранг ${skill.rank}")
        if (automatic != 0) append(" + $automatic навыки")
        append(")")
    }
    return SheetSkillRow(
        skillId = skill.id,
        name = skill.name,
        attributeLabel = attribute.shortTitle,
        rank = skill.rank,
        bonus = bonus,
        perkMark = mark,
        perkTip = sources.takeIf { it.isNotEmpty() }?.joinToString(", "),
        breakdown = breakdown,
    )
}

/** One owned perk on the play sheet: the rules text verbatim plus what it costs to use. */
data class SheetPerkCard(
    val entryId: String,
    val name: String,
    val category: String,
    val rank: Int,
    val maxRank: Int,
    val benefit: String,
    val requirements: String,
    val costTags: List<String>,
    val requirementsUnmet: Boolean,
    val depth: Int,
    val opensDetails: Boolean,
    val isAccessRoot: Boolean,
)

data class SheetPerkGroup(val id: String, val title: String, val cards: List<SheetPerkCard>)

enum class SheetPerkFilter { ALL, ACTIVE, PASSIVE }

/** Owned perks in the player's groups (or the default sections), parents before children, as the 2b sheet orders them. */
fun sheetPerkGroups(host: DublSheetHost): List<SheetPerkGroup> {
    val rules = developmentRules(host)
    val items = rules.ownedSheetSections().flatMap { it.items }.distinctBy { it.entry.id }
    if (items.isEmpty()) return emptyList()
    val byId = items.associateBy { it.entry.id }
    val parentById = items.associate { it.entry.id to it.parentId }
    return normalizedDevelopmentGroups(host).mapNotNull { group ->
        val ordered = SheetGroupingRules.hierarchicalOrder(group.itemIds, parentById).mapNotNull(byId::get)
        if (ordered.isEmpty()) return@mapNotNull null
        val groupIds = ordered.map { it.entry.id }
        SheetPerkGroup(
            id = group.id,
            title = group.title,
            cards = ordered.map { item ->
                val entry = item.entry
                SheetPerkCard(
                    entryId = entry.id,
                    name = entry.name,
                    category = entry.category,
                    rank = item.rank,
                    maxRank = entry.maxRank.coerceAtLeast(1),
                    benefit = entry.benefit,
                    requirements = entry.requirements,
                    costTags = perkCostTags(entry.id, entry.benefit),
                    requirementsUnmet = rules.requirements(entry).any { it.status != RequirementStatus.OK },
                    depth = SheetGroupingRules.localDepth(entry.id, groupIds, parentById),
                    opensDetails = item.source == DevelopmentSheetItemSource.DEVELOPMENT,
                    isAccessRoot = entry.id.startsWith("access_"),
                )
            },
        )
    }
}

/** Active = has a cost tag. The query matches name, rules text or category, ignoring case; empty groups go. */
fun filterPerkGroups(groups: List<SheetPerkGroup>, filter: SheetPerkFilter, query: String): List<SheetPerkGroup> {
    val q = query.trim().lowercase()
    return groups.mapNotNull { group ->
        val cards = group.cards.filter { card ->
            val kept = when (filter) {
                SheetPerkFilter.ALL -> true
                SheetPerkFilter.ACTIVE -> card.costTags.isNotEmpty()
                SheetPerkFilter.PASSIVE -> card.costTags.isEmpty()
            }
            kept && (q.isEmpty() || "${card.name}\n${card.benefit}\n${card.category}".lowercase().contains(q))
        }
        if (cards.isEmpty()) null else group.copy(cards = cards)
    }
}

/** Notes whose title or text contain the query (ignoring case), in their stored order. */
fun filterNotes(notes: List<CharacterNote>, query: String): List<CharacterNote> {
    val q = query.trim().lowercase()
    if (q.isEmpty()) return notes
    return notes.filter { "${it.title}\n${it.body}".lowercase().contains(q) }
}
