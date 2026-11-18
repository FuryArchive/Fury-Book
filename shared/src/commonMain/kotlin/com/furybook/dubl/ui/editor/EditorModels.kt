package com.furybook.dubl.ui.editor

import com.furybook.dubl.model.AttributeId
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
fun editorSkillGroups(host: DublEditorHost, before: DublCharacter, rankedOnly: Boolean, query: String): List<EditorSkillGroup> {
    val character = host.active
    val needle = developmentNormalize(query)
    return sheetSkillsByAttribute(host, rankedOnly = false).map { group ->
        val rows = group.rows
            .filter { row -> !rankedOnly || row.rank > 0 || (before.skills[row.skillId]?.rank ?: 0) > 0 }
            .filter { row -> needle.isBlank() || needle in developmentNormalize(row.name) }
            .sortedWith(compareByDescending<com.furybook.dubl.ui.sheet.SheetSkillRow> { it.rank }.thenBy { developmentNormalize(it.name) })
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

data class EditorDevSkill(val skillId: String, val name: String, val attrShort: String, val from: Int, val cost: Int, val bonusAfter: Int)

data class EditorDevAttr(val id: AttributeId, val short: String, val from: Int, val cost: Int)

data class EditorDevelopmentView(
    val skills: List<EditorDevSkill>,
    val attributes: List<EditorDevAttr>,
    val perks: List<EditorPerkCard>,
    val almost: List<EditorPerkCard>,
)

/** The Развитие landing: what fits in free XP right now, cheapest or strongest first. */
fun editorDevelopmentView(host: DublEditorHost): EditorDevelopmentView = EditorPerkContext(host).developmentView(host)

fun EditorPerkContext.developmentView(host: DublEditorHost): EditorDevelopmentView {
    val free = economy.remainingXp
    val skills = sheetSkillsByAttribute(host, rankedOnly = true).flatMap { group -> group.rows.map { group.attribute to it } }
        .mapNotNull { (attribute, row) ->
            val cost = character.skillNextRankCost(row.rank) ?: return@mapNotNull null
            if (cost > free) return@mapNotNull null
            EditorDevSkill(row.skillId, row.name, attribute.shortTitle, row.rank, cost, (row.bonus ?: 0) + 1)
        }
        .sortedByDescending { it.bonusAfter }
        .take(6)
    val attributes = AttributeId.entries.mapNotNull { id ->
        val from = character.base(id)
        val cost = CharacterEconomy.nextAttributeCost(from) ?: return@mapNotNull null
        if (cost > free) null else EditorDevAttr(id, id.shortTitle, from, cost)
    }.sortedBy { it.cost }.take(8)
    val cards = allCards().filter { it.nextCost <= free }
    val perks = cards
        .filter { it.status == EditorPerkStatus.AVAILABLE || it.status == EditorPerkStatus.UPGRADABLE }
        .filter { catalog.byId(it.entryId)?.isAbility != true }
        .sortedBy { it.nextCost }
        .take(6)
    val almost = cards.filter { it.status == EditorPerkStatus.ALMOST }.sortedBy { it.nextCost }.take(4)
    return EditorDevelopmentView(skills, attributes, perks, almost)
}

data class EditorProblem(val text: String, val step: EditorStep)

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
        if (unmet) add(EditorProblem("${entry.name}: требования больше не выполнены", EditorStep.PERKS))
    }
    if (economy.unpricedLearnedSpells > 0) add(EditorProblem("Заклинания без цены: ${economy.unpricedLearnedSpells}", EditorStep.SUMMARY))
}
