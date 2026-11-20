package com.furybook.dubl.ui.editor

import com.furybook.dubl.model.KnownSpell
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EditorMagicFormsTest {
    @Test
    fun spellFormRejectsBadInput() {
        assertTrue("Нужно название" in editorSpellForm(null).copy(name = " ").errors())
        assertTrue("Мана — число" in editorSpellForm(null).copy(name = "Искра", cost = "x").errors())
        val good = editorSpellForm(null).copy(name = " Искра ", cost = "3", school = "Разрушение")
        assertEquals(emptyList(), good.errors())
        val spell = good.toSpell(null)
        assertTrue(spell.custom)
        assertEquals("Искра", spell.name)
        assertEquals("3", spell.manaText)
        assertEquals(3, spell.cost)
    }

    @Test
    fun spellFormKeepsTheBaseSpell() {
        val base = KnownSpell(uid = "u", catalogId = "c", name = "Щит", cost = 1, manaText = "1+", xpOverride = 5)
        val form = editorSpellForm(base)
        assertEquals("1+", form.manaText)
        assertEquals("5", form.xpOverride)
        val saved = form.copy(xpOverride = "").toSpell(base)
        assertEquals("u", saved.uid)
        assertEquals("c", saved.catalogId)
        assertEquals(null, saved.xpOverride)
        assertEquals(false, saved.custom)
    }

    @Test
    fun schoolFormNeedsAListedSchool() {
        val allowed = listOf("Разрушение", "Ограждение")
        assertTrue("Выберите школу из списка" in EditorSchoolForm("Огонь", "1", "").errors(allowed))
        assertTrue("Сила — число" in EditorSchoolForm("Разрушение", "a", "").errors(allowed))
        assertEquals(emptyList(), EditorSchoolForm("Разрушение", "2", "").errors(allowed))
    }
}
