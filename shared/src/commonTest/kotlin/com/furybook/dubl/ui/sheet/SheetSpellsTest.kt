package com.furybook.dubl.ui.sheet

import com.furybook.dubl.model.CharacterMagic
import com.furybook.dubl.model.DublCharacter
import com.furybook.dubl.model.KnownSpell
import com.furybook.dubl.model.MagicSchool
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SheetSpellsTest {
    private fun mage(mana: Int, spells: List<KnownSpell>) = DublCharacter(
        id = "m", name = "Вика", manaEnabled = true, manaCurrent = mana,
        magic = CharacterMagic(
            manaRank = 3,
            schools = listOf(MagicSchool("Разрушение", 4), MagicSchool("Ограждение", 4), MagicSchool("Прорицание", 1)),
            spells = spells,
        ),
    )

    private val shield = KnownSpell(uid = "s1", name = "Щит", school = "Ограждение", cost = 1, time = "3 ОД", range = "На себя")
    private val spark = KnownSpell(uid = "s2", name = "Искра", school = "Разрушение", cost = 4, manaText = "4", action = "Воля+Восприятие против Защиты")
    private val vision = KnownSpell(uid = "s3", name = "Видение", school = "Прорицание", cost = 1, learned = false)

    @Test
    fun blockHiddenWithoutMagic() {
        assertNull(sheetSpells(FakeSheetHost(DublCharacter(id = "c", name = "Нет"))))
        val oneSpell = DublCharacter(id = "c", name = "Есть", magic = CharacterMagic(spells = listOf(shield)))
        assertNotNull(sheetSpells(FakeSheetHost(oneSpell)))
    }

    @Test
    fun rowsAreLearnedSpellsBySchoolThenName() {
        val view = sheetSpells(FakeSheetHost(mage(7, listOf(spark, vision, shield))))!!
        assertEquals(listOf("Щит", "Искра"), view.rows.map { it.name })
        assertEquals(7, view.current)
        assertEquals(12, view.maximum)
        assertEquals(1, view.recovery)
        assertEquals("4 маны", view.rows[1].manaLabel)
        assertEquals("1 маны", view.rows[0].manaLabel)
        assertEquals("3 ОД · На себя", view.rows[0].meta)
    }

    @Test
    fun weakSchoolWarnsOnTheRow() {
        val bad = KnownSpell(uid = "s4", name = "Неудача", school = "Прорицание", cost = 6)
        val row = sheetSpells(FakeSheetHost(mage(7, listOf(bad))))!!.rows.single()
        assertEquals("нужна сила школы 6", row.weakText)
        assertNull(sheetSpells(FakeSheetHost(mage(7, listOf(shield))))!!.rows.single().weakText)
    }

    @Test
    fun defaultManaReadsManaText() {
        assertEquals(4, sheetSpellDefaultMana(spark.copy(manaText = "4+")))
        assertEquals(2, sheetSpellDefaultMana(spark.copy(manaText = "", cost = 2)))
        assertEquals(3, sheetSpellDefaultMana(spark.copy(manaText = "от 3 и выше", cost = 9)))
    }

    @Test
    fun castSpendsTheChosenAmount() {
        val host = FakeSheetHost(mage(7, listOf(spark)))
        assertTrue(sheetCastSpell(host, "s2", 3))
        assertEquals(4, host.active.manaCurrent)
        assertTrue(sheetCastSpell(host, "s2", 0))
        assertEquals(4, host.active.manaCurrent)
    }

    @Test
    fun castRefusesMoreThanCurrent() {
        val host = FakeSheetHost(mage(2, listOf(spark)))
        assertFalse(sheetCastSpell(host, "s2", 3))
        assertEquals(2, host.active.manaCurrent)
        assertEquals("Не хватает маны: есть 2, нужно 3", sheetCastBlockedReason(2, 3))
        assertNull(sheetCastBlockedReason(3, 3))
        assertFalse(sheetCastSpell(host, "gone", 1))
    }

    @Test
    fun castAmountParsesLeniently() {
        assertEquals(12, sheetCastAmount("12"))
        assertEquals(0, sheetCastAmount(""))
        assertEquals(3, sheetCastAmount("a3"))
    }
}
