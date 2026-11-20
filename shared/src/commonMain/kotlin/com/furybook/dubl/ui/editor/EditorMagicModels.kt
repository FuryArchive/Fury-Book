package com.furybook.dubl.ui.editor

import com.furybook.dubl.model.CharacterEconomy
import com.furybook.dubl.model.DublCharacter
import com.furybook.dubl.model.KnownSpell
import com.furybook.dubl.model.MagicEquipmentRules
import com.furybook.dubl.model.MagicSchoolCatalog
import com.furybook.dubl.model.SpellCatalogEntry
import com.furybook.dubl.ui.sheet.sheetChiEconomyMounted
import com.furybook.dubl.ui.sheet.sheetSpellManaLabel
import com.furybook.dubl.ui.sheet.sheetSpellWeakText

/** The Магия header: mana rank (creation-only in the rules), pool, recovery and the magic XP split. */
data class EditorMagicHeader(
    val manaRank: Int,
    val rankXp: Int,
    val rankLocked: Boolean,
    val manaMaximum: Int,
    val recovery: Int,
    val schoolXp: Int,
    val spellXp: Int,
    val zeroReason: String?,
)

const val EDITOR_MANA_RANK_MAX = 5

fun editorMagicHeader(host: DublEditorHost): EditorMagicHeader {
    val character = host.active
    val economy = CharacterEconomy.breakdown(character, host.developmentCatalog, includeChi = sheetChiEconomyMounted(host))
    // What the sheet shows: a manual maximum or a stored one from older builds counts.
    val maximum = character.effectiveManaMaximum
    return EditorMagicHeader(
        manaRank = character.magic.manaRank,
        rankXp = economy.manaXp,
        rankLocked = character.creationComplete,
        manaMaximum = maximum,
        recovery = MagicEquipmentRules.manaRecoveryPerRound(character),
        schoolXp = economy.magicSchoolXp,
        spellXp = economy.spellXp,
        zeroReason = if (maximum > 0) null else "Мана появится, когда ранг маны больше 0 и есть школа с силой",
    )
}

data class EditorSchoolRow(val index: Int, val name: String, val power: Int, val xp: Int, val note: String)

/** The character's schools in rulebook order; [EditorSchoolRow.index] is the stored position the application edits by. */
fun editorSchoolRows(character: DublCharacter): List<EditorSchoolRow> =
    character.magic.schools
        .mapIndexed { index, school -> EditorSchoolRow(index, school.name, school.rank, MagicEquipmentRules.magicSchoolRankXp(school.rank), school.note) }
        .sortedBy { MagicSchoolCatalog.sortIndex(it.name) }

/** Rulebook schools the character doesn't have yet (the rules only save and price these names). */
fun editorAddableSchools(character: DublCharacter): List<String> {
    val owned = character.magic.schools.mapNotNull { MagicSchoolCatalog.canonicalizeOrNull(it.name) }.toSet()
    return MagicSchoolCatalog.schools.filterNot { it in owned }
}

/** Names the school form may pick: the addable ones plus, in a school's detail ([index]), its own (rename). */
fun editorSchoolChoices(character: DublCharacter, index: Int?): List<String> {
    val own = index?.let { character.magic.schools.getOrNull(it) }?.let { MagicSchoolCatalog.canonicalizeOrNull(it.name) }
    return MagicSchoolCatalog.schools.filter { it == own || it in editorAddableSchools(character) }
}

data class EditorSpellRow(val uid: String, val name: String, val manaLabel: String, val learned: Boolean, val learnXp: Int?, val weakText: String?)

data class EditorSpellGroup(val school: String, val power: Int, val rows: List<EditorSpellRow>)

private fun matches(query: String, vararg texts: String): Boolean {
    val q = query.trim().lowercase().replace('ё', 'е')
    return q.isEmpty() || texts.any { it.lowercase().replace('ё', 'е').contains(q) }
}

/** The spell's school for grouping: its first rulebook school, else its text, else «Без школы». */
private fun groupSchool(spell: String): String = MagicSchoolCatalog.parseSchools(spell).firstOrNull() ?: spell.trim().ifBlank { "Без школы" }

/**
 * What learning [spell] costs, read from the rule itself (`learnedSpellXp` with and without it), so «неполное»
 * spells show the 0 the economy charges. Null when the rules give it no price («без цены»).
 */
fun editorSpellLearnXp(character: DublCharacter, spell: KnownSpell): Int? {
    if (spell.xpOverride == null && !spell.incomplete && MagicEquipmentRules.learnXpCost(spell.cost) == null) return null
    val others = character.magic.spells.filterNot { it.uid == spell.uid }
    fun xp(spells: List<KnownSpell>) = MagicEquipmentRules.learnedSpellXp(character.copy(magic = character.magic.copy(spells = spells)))
    return xp(others + spell.copy(learned = true)) - xp(others)
}

internal fun SpellCatalogEntry.asKnownSpell(): KnownSpell =
    KnownSpell(uid = "catalog:$id", catalogId = id, name = name, school = school, cost = cost, manaText = manaText, incomplete = incomplete)

/** The spellbook grouped by school (rulebook order), filtered by learned state, a school chip and a search over name and text. */
fun editorSpellGroups(character: DublCharacter, filter: EditorSpellFilter, school: String?, query: String): List<EditorSpellGroup> =
    character.magic.spells
        .filter { spell ->
            when (filter) {
                EditorSpellFilter.ALL -> true
                EditorSpellFilter.LEARNED -> spell.learned
                EditorSpellFilter.UNLEARNED -> !spell.learned
            }
        }
        .filter { school == null || school in MagicSchoolCatalog.parseSchools(it.school) || it.school.trim() == school }
        .filter { matches(query, it.name, it.description, it.enhancement) }
        .groupBy { groupSchool(it.school) }
        .toList()
        .sortedBy { (name, _) -> MagicSchoolCatalog.sortIndex(name) }
        .map { (name, spells) ->
            EditorSpellGroup(
                school = name,
                power = MagicEquipmentRules.schoolPower(character, name),
                rows = spells.sortedBy { it.name.lowercase() }.map { spell ->
                    EditorSpellRow(spell.uid, spell.name, sheetSpellManaLabel(spell), spell.learned, editorSpellLearnXp(character, spell), sheetSpellWeakText(character, spell))
                },
            )
        }

data class EditorCatalogSpellRow(
    val id: String,
    val name: String,
    val school: String,
    val manaLabel: String,
    val known: Boolean,
    val usable: Boolean,
    val requiredPower: Int,
)

/** The rulebook spells: search over name, school and text, a school chip, and «только доступные по силе школы». */
fun editorSpellCatalog(host: DublEditorHost, query: String, school: String?, usableOnly: Boolean): List<EditorCatalogSpellRow> {
    val character = host.active
    val known = character.magic.spells.mapNotNull { it.catalogId }.toSet()
    return host.magicEquipmentCatalog.spells
        .filter { school == null || school in MagicSchoolCatalog.parseSchools(it.school) }
        .filter { matches(query, it.name, it.school, it.description) }
        .map { entry ->
            val usability = MagicEquipmentRules.spellUsability(character, entry)
            EditorCatalogSpellRow(
                id = entry.id,
                name = entry.name,
                school = entry.school,
                manaLabel = "${entry.manaText.trim().ifBlank { entry.cost.toString() }} маны",
                known = entry.id in known,
                usable = usability.usable,
                requiredPower = usability.requiredPower,
            )
        }
        .filter { !usableOnly || it.usable }
        .sortedWith(compareBy<EditorCatalogSpellRow>({ MagicSchoolCatalog.sortIndex(it.school) }, { it.name.lowercase() }))
}

data class EditorSpellDetail(
    val title: String,
    val subtitle: String,
    val facts: List<Pair<String, String>>,
    val description: String,
    val enhancement: String,
    val conflictNote: String,
    val weakText: String?,
    val incomplete: Boolean,
)

private fun facts(time: String, range: String, area: String, action: String, duration: String): List<Pair<String, String>> =
    listOf("Время" to time, "Дистанция" to range, "Область" to area, "Проверка" to action, "Длительность" to duration).filter { it.second.isNotBlank() }

private fun xpText(xp: Int?): String = xp?.let { "изучить $it XP" } ?: "без цены"

fun editorSpellDetail(character: DublCharacter, spell: KnownSpell): EditorSpellDetail = EditorSpellDetail(
    title = spell.name,
    subtitle = listOf(spell.school, sheetSpellManaLabel(spell), xpText(editorSpellLearnXp(character, spell))).filter { it.isNotBlank() }.joinToString(" · "),
    facts = facts(spell.time, spell.range, spell.area, spell.action, spell.duration),
    description = spell.description,
    enhancement = spell.enhancement,
    conflictNote = spell.conflictNote,
    weakText = sheetSpellWeakText(character, spell),
    incomplete = spell.incomplete,
)

fun editorSpellDetail(character: DublCharacter, entry: SpellCatalogEntry): EditorSpellDetail {
    val usability = MagicEquipmentRules.spellUsability(character, entry)
    return EditorSpellDetail(
        title = entry.name,
        subtitle = listOf(entry.school, "${entry.manaText.trim().ifBlank { entry.cost.toString() }} маны", xpText(editorSpellLearnXp(character, entry.asKnownSpell())))
            .filter { it.isNotBlank() }.joinToString(" · "),
        facts = facts(entry.time, entry.range, entry.area, entry.action, entry.duration),
        description = entry.description,
        enhancement = entry.enhancement,
        conflictNote = entry.conflictNote,
        weakText = if (usability.usable) null else "нужна сила школы ${usability.requiredPower}",
        incomplete = entry.incomplete,
    )
}
