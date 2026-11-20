package com.furybook.dubl.ui.editor

import com.furybook.dubl.model.CharacterMagic
import com.furybook.dubl.model.KnownSpell
import com.furybook.dubl.model.MagicEquipmentCatalog
import com.furybook.dubl.model.MagicSchool
import com.furybook.dubl.model.SpellCatalogEntry
import com.furybook.dubl.ui.editor.EditorFixtures.hornet
import com.furybook.dubl.ui.sheet.FakeSheetHost
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EditorMagicActionsTest {
    private val shield = KnownSpell(uid = "s1", name = "Щит", school = "Ограждение", cost = 4)
    private val mage = hornet.copy(
        creationComplete = false,
        magic = CharacterMagic(manaRank = 1, schools = listOf(MagicSchool("Ограждение", 4)), spells = listOf(shield)),
    )

    private fun opened(host: FakeSheetHost) = EditorState().also { it.open(host, forceBasics = false) }

    @Test
    fun removeSpellOffersReturn() {
        val host = FakeSheetHost(mage)
        val state = opened(host)
        state.magicPane = EditorPane.Spell("s1")
        editorRemoveSpell(host, state, "s1")
        assertTrue(host.active.magic.spells.isEmpty())
        assertNull(state.magicPane)
        assertTrue(state.toast!!.startsWith("Удалено"))
        assertTrue(state.undoToast(host))
        assertTrue(host.active.magic.spells.single().learned)
    }

    @Test
    fun removeSchoolOffersReturn() {
        val host = FakeSheetHost(mage)
        val state = opened(host)
        editorRemoveSchool(host, state, 0)
        assertTrue(host.active.magic.schools.isEmpty())
        assertTrue(state.undoToast(host))
        assertEquals(4, host.active.magic.schools.single().rank)
    }

    @Test
    fun incompleteCatalogSpellIsAddable() {
        val entry = SpellCatalogEntry("c9", "Обрывок", "Разум", 2, "2", "", "", "", "", "", "", "", true, "")
        val host = FakeSheetHost(mage, magicEquipmentCatalogOverride = MagicEquipmentCatalog("t", listOf(entry), emptyList()))
        val state = opened(host)
        val uid = editorAddCatalogSpell(host, state, entry)
        assertNotNull(uid)
        assertTrue(host.active.magic.spells.single { it.uid == uid }.incomplete)
        assertEquals(EditorPane.Spell(uid), state.magicPane)
    }

    @Test
    fun refusedSchoolSaveSaysSo() {
        val host = FakeSheetHost(mage)
        val state = opened(host)
        assertFalse(editorSaveSchool(host, state, null, EditorSchoolForm("Ограждение", "2", "")))
        assertEquals("Не удалось сохранить школу", state.toast)
        assertTrue(editorSaveSchool(host, state, null, EditorSchoolForm("Разрушение", "2", "")))
        assertEquals(2, host.active.magic.schools.size)
        assertTrue(editorSaveSchool(host, state, 0, EditorSchoolForm("Ограждение", "5", "заметка")))
        assertEquals(5, host.active.magic.schools[0].rank)
    }
}
