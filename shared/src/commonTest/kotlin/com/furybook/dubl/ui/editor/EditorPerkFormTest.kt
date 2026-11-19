package com.furybook.dubl.ui.editor

import com.furybook.dubl.model.AbilityOption
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EditorPerkFormTest {
    private val entry = EditorFixtures.entry("x", "Ворожба", maxRank = 2).copy(
        tags = listOf("ЦИ", "Приём"),
        abilityOptions = listOf(AbilityOption("Мастерство", 1), AbilityOption("Магия", 2)),
        accessId = "access_van",
    )

    @Test
    fun formRoundTripsAnEntry() {
        val form = editorPerkForm(entry)
        assertEquals("Мастерство=1; Магия=2", form.abilityOptions)
        assertEquals("ЦИ, Приём", form.tags)
        assertEquals(entry, form.toEntry(entry))
    }

    @Test
    fun formRejectsBadInput() {
        val form = editorPerkForm(entry)
        val blank = form.copy(name = "  ")
        assertTrue("Введите название" in blank.errors())
        assertNull(blank.toEntry(entry))
        val zero = form.copy(maxRank = "0")
        assertTrue(zero.errors().any { "Макс. ранг" in it })
        assertNull(zero.toEntry(entry))
        val junk = form.copy(abilityOptions = "Мастерство=1; мусор")
        assertTrue(junk.errors().any { "мусор" in it })
        assertNull(junk.toEntry(entry))
        val noCost = form.copy(cost = "")
        assertTrue(noCost.errors().any { "Стоимость" in it })
    }

    @Test
    fun blankOptionPartsAreSkipped() {
        val parsed = editorPerkForm(entry).copy(abilityOptions = "Мастерство=1; ;", accessId = " ").toEntry(entry)!!
        assertEquals(listOf(AbilityOption("Мастерство", 1)), parsed.abilityOptions)
        assertNull(parsed.accessId)
    }

    @Test
    fun newPerkMatchesTheOldDefaults() {
        val fresh = editorNewPerk()
        assertEquals("", fresh.id)
        assertEquals("Свои", fresh.section)
        assertEquals("Домашние правила", fresh.category)
        assertEquals(1, fresh.maxRank)
    }
}
