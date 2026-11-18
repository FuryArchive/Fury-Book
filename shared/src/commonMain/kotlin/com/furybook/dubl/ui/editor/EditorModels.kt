package com.furybook.dubl.ui.editor

import com.furybook.dubl.model.AttributeId
import com.furybook.dubl.model.resolveSkill
import com.furybook.dubl.model.SkillCategory
import com.furybook.dubl.model.CharacterEconomy
import com.furybook.dubl.model.DublCharacter
import com.furybook.dubl.model.MagicEquipmentRules
import com.furybook.dubl.model.RequirementStatus
import com.furybook.dubl.model.developmentNormalize
import com.furybook.dubl.model.skillNextRankCost
import com.furybook.dubl.ui.sheet.derivedShortLabel
import com.furybook.dubl.ui.sheet.sheetMetrics
import com.furybook.dubl.ui.sheet.sheetSkillsByAttribute

private const val DEFAULT_NAME = "Новый персонаж"

data class EditorAttributeCard(
    val id: AttributeId,
    val short: String,
    val title: String,
    val value: Int,
    val delta: Int,
    val incText: String,
    val decText: String,
    val canInc: Boolean,
    val canDec: Boolean,
)

private fun DublCharacter.base(id: AttributeId): Int = attributes[id]?.base ?: 0

fun editorAttributeCards(character: DublCharacter, before: DublCharacter): List<EditorAttributeCard> = AttributeId.entries.map { id ->
    val value = character.base(id)
    val next = CharacterEconomy.nextAttributeCost(value)
    val refund = CharacterEconomy.previousAttributeRefund(value)
    EditorAttributeCard(
        id = id,
        short = id.shortTitle,
        title = id.title,
        value = value,
        delta = value - before.base(id),
        incText = next?.let { "+1: $it XP" } ?: "максимум",
        decText = refund?.let { "−1: вернёт $it" } ?: "минимум",
        canInc = next != null,
        canDec = refund != null,
    )
}

data class EditorDerived(val label: String, val value: String, val delta: Int)

/** The play sheet's six metrics, with the change since the editor opened. */
fun editorDerived(after: DublCharacter, before: DublCharacter): List<EditorDerived> {
    val was = sheetMetrics(before).associate { it.label to it.value }
    return sheetMetrics(after).map { stat -> EditorDerived(derivedShortLabel(stat.label), stat.valueText, stat.value - (was[stat.label] ?: stat.value)) }
}

data class EditorSkillRow(
    val skillId: String,
    val name: String,
    val rank: Int,
    val gainedFrom: Int,
    val nextCostText: String,
    val bonus: Int?,
    val canInc: Boolean,
    val canDec: Boolean,
)

data class EditorSkillGroup(val attribute: AttributeId, val short: String, val title: String, val value: Int, val rows: List<EditorSkillRow>)

/**
 * Skills by default attribute, as on the play sheet. With [rankedOnly], a skill ranked when the editor
 * opened stays listed at rank 0 so its last rank can be put back.
 */
/**
 * Skills by attribute for the Умения step. Search matches names and descriptions; [category] narrows to one
 * category; [keep] (the open skill) stays listed whatever the filters say.
 */
fun editorSkillGroups(
    host: DublEditorHost,
    before: DublCharacter,
    rankedOnly: Boolean,
    query: String,
    category: SkillCategory? = null,
    keep: String? = null,
): List<EditorSkillGroup> {
    val character = host.active
    val needle = developmentNormalize(query)
    return sheetSkillsByAttribute(host, rankedOnly = false).map { group ->
        val rows = group.rows
            .filter { row ->
                val skill = character.resolveSkill(row.skillId)
                row.skillId == keep || (
                    (!rankedOnly || row.rank > 0 || (before.skills[row.skillId]?.rank ?: 0) > 0) &&
                        (category == null || skill?.category == category) &&
                        (needle.isBlank() || needle in developmentNormalize(row.name) || needle in developmentNormalize(skill?.description.orEmpty()))
                    )
            }
            // Ordered by the rank at the start of the visit, so a row never moves under the cursor while you click it.
            .sortedWith(compareByDescending<com.furybook.dubl.ui.sheet.SheetSkillRow> { before.skills[it.skillId]?.rank ?: 0 }.thenBy { developmentNormalize(it.name) })
            .map { row ->
                val next = character.skillNextRankCost(row.rank)
                EditorSkillRow(
                    skillId = row.skillId,
                    name = row.name,
                    rank = row.rank,
                    gainedFrom = before.skills[row.skillId]?.rank ?: 0,
                    nextCostText = next?.let { "+$it XP" } ?: "макс.",
                    bonus = row.bonus,
                    canInc = next != null,
                    canDec = row.rank > 0,
                )
            }
        EditorSkillGroup(group.attribute, group.attribute.shortTitle, group.attribute.title, group.value, rows)
    }
}

/** [affordable] false: the row stays (lists never shift mid-visit) but its button is off — no XP left, or maxed. */
data class EditorDevSkill(val skillId: String, val name: String, val attrShort: String, val from: Int, val cost: Int, val bonusAfter: Int, val affordable: Boolean = true)

data class EditorDevAttr(val id: AttributeId, val short: String, val from: Int, val cost: Int, val affordable: Boolean = true)

/** Which rows Развитие shows, in order; fixed for the rest of the visit once first computed. */
data class EditorDevelopmentPins(val skills: List<String>, val attributes: List<AttributeId>, val perks: List<String>, val almost: List<String>)

data class EditorDevelopmentView(
    val skills: List<EditorDevSkill>,
    val attributes: List<EditorDevAttr>,
    val perks: List<EditorPerkCard>,
    val almost: List<EditorPerkCard>,
) {
    val pins: EditorDevelopmentPins
        get() = EditorDevelopmentPins(skills.map { it.skillId }, attributes.map { it.id }, perks.map { it.entryId }, almost.map { it.entryId })
}

/**
 * The Развитие landing: what fits in free XP right now, cheapest or strongest first. With [pins] (from the first
 * view of this visit) the same rows come back in the same order with their current state, so nothing moves while
 * the player taps; new options show on the next visit to the step.
 */
fun editorDevelopmentView(host: DublEditorHost, pins: EditorDevelopmentPins? = null): EditorDevelopmentView =
    EditorPerkContext(host).developmentView(host, pins)

fun EditorPerkContext.developmentView(host: DublEditorHost, pins: EditorDevelopmentPins? = null): EditorDevelopmentView {
    val free = economy.remainingXp
    val skillRows = sheetSkillsByAttribute(host, rankedOnly = true).flatMap { group -> group.rows.map { group.attribute to it } }
    fun devSkill(attribute: AttributeId, row: com.furybook.dubl.ui.sheet.SheetSkillRow): EditorDevSkill {
        val cost = character.skillNextRankCost(row.rank)
        return EditorDevSkill(row.skillId, row.name, attribute.shortTitle, row.rank, cost ?: 0, (row.bonus ?: 0) + 1, cost != null && cost <= free)
    }
    fun devAttr(id: AttributeId): EditorDevAttr {
        val from = character.base(id)
        val cost = CharacterEconomy.nextAttributeCost(from)
        return EditorDevAttr(id, id.shortTitle, from, cost ?: 0, cost != null && cost <= free)
    }
    val cards = allCards()
    if (pins != null) {
        val byId = cards.associateBy { it.entryId }
        val rowsById = skillRows.associateBy { it.second.skillId }
        return EditorDevelopmentView(
            skills = pins.skills.mapNotNull { id -> rowsById[id]?.let { (attribute, row) -> devSkill(attribute, row) } },
            attributes = pins.attributes.map(::devAttr),
            perks = pins.perks.mapNotNull(byId::get),
            almost = pins.almost.mapNotNull(byId::get),
        )
    }
    val skills = skillRows.map { (attribute, row) -> devSkill(attribute, row) }.filter { it.affordable }
        .sortedByDescending { it.bonusAfter }
        .take(6)
    val attributes = AttributeId.entries.map(::devAttr).filter { it.affordable }.sortedBy { it.cost }.take(8)
    val fitting = cards.filter { it.nextCost <= free }
    val perks = fitting
        .filter { it.status == EditorPerkStatus.AVAILABLE || it.status == EditorPerkStatus.UPGRADABLE }
        .filter { catalog.byId(it.entryId)?.isAbility != true }
        .sortedBy { it.nextCost }
        .take(6)
    val almost = fitting.filter { it.status == EditorPerkStatus.ALMOST }.sortedBy { it.nextCost }.take(4)
    return EditorDevelopmentView(skills, attributes, perks, almost)
}

/** One Итог remark; [entryId] names the perk to open when the fix is on one entry. */
data class EditorProblem(val text: String, val step: EditorStep, val entryId: String? = null)

fun editorProblems(host: DublEditorHost): List<EditorProblem> = EditorPerkContext(host).problems()

fun EditorPerkContext.problems(): List<EditorProblem> = buildList {
    if (economy.overspentXp) add(EditorProblem("Перерасход опыта: ${-economy.remainingXp} XP", EditorStep.BASICS))
    if (economy.overspentAbilityPoints) {
        add(EditorProblem("Очков способностей потрачено больше бюджета: ${economy.abilityPointsSpent} из ${economy.abilityPointsBudget}", EditorStep.BASICS))
    }
    if (character.name.isBlank() || (!character.creationComplete && character.name == DEFAULT_NAME)) {
        add(EditorProblem("У персонажа нет имени", EditorStep.BASICS))
    }
    character.development.keys.filter { it != MagicEquipmentRules.BASE_MANA_ENTRY_ID }.forEach { id ->
        val entry = catalog.byId(id) ?: return@forEach
        val unmet = rules.requirements(entry).any { it.status == RequirementStatus.FAIL && CREATION_CLOSED_MARK !in it.text }
        if (unmet) add(EditorProblem("${entry.name}: требования больше не выполнены", EditorStep.PERKS, entry.id))
    }
    if (economy.unpricedLearnedSpells > 0) add(EditorProblem("Заклинания без цены: ${economy.unpricedLearnedSpells}", EditorStep.SUMMARY))
}

/** A character still in creation with the placeholder name gets an empty name field (blank saves as the placeholder). */
fun editorNameFieldInitial(character: DublCharacter): String =
    if (!character.creationComplete && character.name == DEFAULT_NAME) "" else character.name

/** Share of the XP bar that is spent; empty with no XP at all, full once spending passes the total. */
fun editorSpentFraction(spent: Int, total: Int): Float = when {
    total <= 0 -> if (spent > 0) 1f else 0f
    else -> (spent.toFloat() / total).coerceIn(0f, 1f)
}

/**
 * Splits skill groups (given as row counts, in attribute order) into [columns] independent columns, each group
 * going to the currently shorter column. Order is kept within a column; an empty group still takes one line.
 */
fun editorBalancedColumns(rowCounts: List<Int>, columns: Int): List<List<Int>> {
    val result = List(columns.coerceAtLeast(1)) { mutableListOf<Int>() }
    val heights = IntArray(result.size)
    rowCounts.forEachIndexed { index, rows ->
        val target = heights.indices.minBy { heights[it] }
        result[target] += index
        heights[target] += rows.coerceAtLeast(1) + 1
    }
    return result
}

/** Развитие's subtitle; an overspent budget is said in words, never as "-5 ОС". */
fun editorDevelopmentSubtitle(freeXp: Int, freeAbilityPoints: Int): String {
    val tail = " Что можно взять прямо сейчас, одним нажатием."
    return when {
        freeXp < 0 -> "Перерасход опыта: ${-freeXp} XP. Верните часть опыта или добавьте XP на «Основе»."
        freeAbilityPoints < 0 -> "Свободно $freeXp XP; очков способностей потрачено на ${-freeAbilityPoints} больше бюджета.$tail"
        else -> "Свободно $freeXp XP и $freeAbilityPoints ОС.$tail"
    }
}
