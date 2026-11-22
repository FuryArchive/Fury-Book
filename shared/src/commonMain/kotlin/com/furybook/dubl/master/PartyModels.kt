package com.furybook.dubl.master

import com.furybook.core.cloud.CampaignDashboard
import com.furybook.core.cloud.CampaignMember
import com.furybook.dubl.model.AttributeId
import com.furybook.dubl.model.CharacterConditionId
import com.furybook.dubl.model.CharacterSheetExtras
import com.furybook.dubl.model.ConditionCatalog
import com.furybook.dubl.model.DevelopmentCatalog
import com.furybook.dubl.model.DublCharacter
import com.furybook.dubl.model.resolvedSkills
import com.furybook.dubl.ui.sheet.sheetMetrics
import com.furybook.dubl.ui.sheet.sheetSpellManaLabel

/** One player in the Партия table. */
data class PartyRow(
    val userId: String,
    val nickname: String,
    val characterName: String,
    val hp: Pair<Int, Int>,
    val endurance: Pair<Int, Int>,
    val mana: Pair<Int, Int>?,
    val defense: Int,
    val reflexes: Int,
    val initiative: Int,
    val conditions: List<String>,
    val updatedAt: String,
)

/** Active conditions as the sheet shows them: chosen ones (with the player's renames), automatic Слабость, custom ones. */
private fun conditionTitles(character: DublCharacter, extras: CharacterSheetExtras): List<String> {
    val builtIn = buildSet {
        addAll(extras.activeConditions)
        if (character.enduranceCurrent == 0) add(CharacterConditionId.WEAKNESS)
    }
    return builtIn.map { extras.conditionOverrides[it]?.title ?: it.title } + extras.customConditions.filter { it.active }.map { it.title }
}

/** The party table, from the same character getters the sheet uses. */
@Suppress("UNUSED_PARAMETER")
fun partyRows(dashboard: CampaignDashboard, conditions: ConditionCatalog): List<PartyRow> = dashboard.players.map { member ->
    val c = member.character
    PartyRow(
        userId = member.userId,
        nickname = member.nickname,
        characterName = c.name,
        hp = c.hpCurrent to c.healthMaximum,
        endurance = c.enduranceCurrent to c.enduranceMaximum,
        mana = if (c.manaEnabled) c.manaCurrent to c.effectiveManaMaximum else null,
        defense = c.defense,
        reflexes = c.reflexes,
        initiative = c.initiative,
        conditions = conditionTitles(c, member.extras),
        updatedAt = member.updatedAt,
    )
}

/** A player's character for the GM to read: the sheet's numbers and lists, nothing to press. */
data class ReadOnlyView(
    val name: String,
    val concept: String,
    val nickname: String,
    val updatedAt: String,
    val vitals: List<Pair<String, String>>,
    val derived: List<Pair<String, String>>,
    val attributes: List<Pair<String, String>>,
    val conditions: List<String>,
    val skills: List<Pair<String, String>>,
    val perks: List<String>,
    val schools: List<Pair<String, String>>,
    val spells: List<Pair<String, String>>,
    val gear: List<Pair<String, String>>,
)

/**
 * Built from the sheet's models. A perk the GM's device doesn't know (a pack it lacks) shows as «<id> · нет пакета»
 * instead of failing.
 */
fun readOnlyView(member: CampaignMember, development: DevelopmentCatalog): ReadOnlyView {
    val c = member.character
    val perks = c.development.filterValues { it.rank > 0 }.map { (id, owned) ->
        val name = development.byId(id)?.name ?: c.customDevelopmentEntries.firstOrNull { it.id == id }?.name
        when {
            name == null -> "$id · нет пакета"
            owned.rank > 1 -> "$name · ранг ${owned.rank}"
            else -> name
        }
    } + c.customDevelopmentEntries.filter { custom -> c.development[custom.id] == null }.map { it.name }
    return ReadOnlyView(
        name = c.name,
        concept = c.concept,
        nickname = member.nickname,
        updatedAt = member.updatedAt,
        vitals = buildList {
            add("Здоровье" to "${c.hpCurrent}/${c.healthMaximum}")
            add("Выносливость" to "${c.enduranceCurrent}/${c.enduranceMaximum}")
            if (c.manaEnabled) add("Мана" to "${c.manaCurrent}/${c.effectiveManaMaximum}")
            add("XP" to c.experience.toString())
        },
        derived = sheetMetrics(c).map { it.label to it.valueText } + ("Внимательность" to c.perception.toString()),
        attributes = AttributeId.entries.map { it.shortTitle to c.attribute(it).toString() },
        conditions = conditionTitles(c, member.extras),
        skills = c.resolvedSkills(includeHidden = true).filter { it.rank > 0 }.map { it.name.ifBlank { it.id } to "ранг ${it.rank}" },
        perks = perks,
        schools = c.magic.schools.map { it.name to it.rank.toString() },
        spells = c.magic.spells.filter { it.learned }.map { it.name to listOf(it.school, sheetSpellManaLabel(it)).filter(String::isNotBlank).joinToString(" · ") },
        gear = c.gear.items.map { it.name to "×${it.quantity}${if (it.carried) "" else " · не несёт"}" },
    )
}
