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

    @Test
    fun removeGearOffersReturn() {
        val host = FakeSheetHost(hornet)
        val uid = host.addCustomGear(com.furybook.dubl.model.GearItem(uid = "", name = "Верёвка", quantity = 2))
        val state = opened(host)
        state.gearPane = EditorPane.Item(uid)
        editorRemoveGear(host, state, uid)
        assertTrue(host.active.gear.items.isEmpty())
        assertNull(state.gearPane)
        assertTrue(state.toast!!.startsWith("Удалено"))
        assertTrue(state.undoToast(host))
        assertEquals(2, host.active.gear.items.single().quantity)
    }

    @Test
    fun savedGearFormOpensTheItem() {
        val host = FakeSheetHost(hornet)
        val state = opened(host)
        assertFalse(editorSaveGear(host, state, editorGearForm(null), null))
        assertTrue(editorSaveGear(host, state, editorGearForm(null).copy(name = "Фляга"), null))
        val item = host.active.gear.items.single()
        assertEquals(EditorPane.Item(item.uid), state.gearPane)
    }

    @Test
    fun schoolPowerStepKeepsTheNote() {
        val host = FakeSheetHost(mage.copy(magic = mage.magic.copy(schools = listOf(MagicSchool("Ограждение", 1, "от учителя")))))
        val state = opened(host)
        editorStepSchoolPower(host, state, 0, +1)
        assertEquals(MagicSchool("Ограждение", 2, "от учителя"), host.active.magic.schools.single())
        editorStepSchoolPower(host, state, 0, -1)
        assertEquals("от учителя", host.active.magic.schools.single().note)
        editorStepSchoolPower(host, state, 0, -1)
        assertTrue(host.active.magic.schools.isEmpty())
        assertTrue(state.toast!!.startsWith("Удалено"))
        assertTrue(state.undoToast(host))
        assertEquals("от учителя", host.active.magic.schools.single().note)
    }
}
