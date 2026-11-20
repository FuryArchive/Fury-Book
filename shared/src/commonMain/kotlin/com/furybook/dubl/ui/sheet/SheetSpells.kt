package com.furybook.dubl.ui.sheet

import com.furybook.dubl.model.CharacterSheetResourceId
import com.furybook.dubl.model.DublCharacter
import com.furybook.dubl.model.KnownSpell
import com.furybook.dubl.model.MagicEquipmentRules
import com.furybook.dubl.model.MagicSchoolCatalog
import com.furybook.dubl.ui.editor.DublEditorHost

/** One known spell on the sheet. [weakText] is set when no school of the spell is strong enough (a warning, not a block). */
data class SheetSpellRow(val uid: String, val name: String, val manaLabel: String, val meta: String, val weakText: String?)

data class SheetSpellsView(val current: Int, val maximum: Int, val recovery: Int, val rows: List<SheetSpellRow>)

/** The block shows for a character who uses magic: mana switched on, a mana pool, or any learned spell. */
fun sheetShowsSpells(character: DublCharacter): Boolean =
    character.manaEnabled || character.effectiveManaMaximum > 0 || character.magic.spells.any { it.learned }

internal fun sheetSpellManaLabel(spell: KnownSpell): String = "${spell.manaText.trim().ifBlank { spell.cost.toString() }} маны"

internal fun sheetSpellWeakText(character: DublCharacter, spell: KnownSpell): String? {
    val usability = MagicEquipmentRules.spellUsability(character, spell)
    return if (usability.usable) null else "нужна сила школы ${usability.requiredPower}"
}

/** Learned spells by school (rulebook order), then name; null when the character doesn't use magic. */
fun sheetSpells(host: DublSheetHost): SheetSpellsView? {
    val character = host.active
    if (!sheetShowsSpells(character)) return null
    val rows = character.magic.spells
        .filter { it.learned }
        .sortedWith(compareBy<KnownSpell>({ MagicSchoolCatalog.sortIndex(it.school) }, { it.name.lowercase() }))
        .map { spell ->
            SheetSpellRow(
                uid = spell.uid,
                name = spell.name,
                manaLabel = sheetSpellManaLabel(spell),
                meta = listOf(spell.time, spell.range, spell.action).filter { it.isNotBlank() }.joinToString(" · "),
                weakText = sheetSpellWeakText(character, spell),
            )
        }
    return SheetSpellsView(
        current = character.manaCurrent,
        maximum = character.effectiveManaMaximum,
        recovery = MagicEquipmentRules.manaRecoveryPerRound(character),
        rows = rows,
    )
}

/** What the cast window starts at: the first number in the spell's mana text («4+», «от 3»), else its cost. */
fun sheetSpellDefaultMana(spell: KnownSpell): Int =
    (Regex("\\d+").find(spell.manaText)?.value?.toIntOrNull() ?: spell.cost).coerceAtLeast(0)

/** The typed amount: digits only, blank reads as 0. */
fun sheetCastAmount(text: String): Int = text.filter(Char::isDigit).take(6).toIntOrNull() ?: 0

fun sheetCastBlockedReason(current: Int, amount: Int): String? =
    if (amount > current) "Не хватает маны: есть $current, нужно $amount" else null

/** Spends [amount] mana for the spell; false and nothing spent when the spell is gone or there isn't enough mana. */
fun sheetCastSpell(host: DublEditorHost, uid: String, amount: Int): Boolean {
    if (host.active.magic.spells.none { it.uid == uid }) return false
    if (amount < 0 || sheetCastBlockedReason(host.active.manaCurrent, amount) != null) return false
    if (amount > 0) host.changeResource(CharacterSheetResourceId.MANA, -amount)
    return true
}
