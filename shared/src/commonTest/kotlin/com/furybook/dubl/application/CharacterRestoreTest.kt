package com.furybook.dubl.application

import com.furybook.dubl.data.CharacterExtrasStore
import com.furybook.dubl.data.InMemoryCharacterStore
import com.furybook.dubl.model.AppSnapshot
import com.furybook.dubl.model.AttributeId
import com.furybook.dubl.model.CharacterSheetExtras
import com.furybook.dubl.model.DublCharacter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CharacterRestoreTest {
    private class MemoryExtras : CharacterExtrasStore {
        private val values = linkedMapOf<String, CharacterSheetExtras>()
        override fun load(characterId: String): CharacterSheetExtras = values[characterId] ?: CharacterSheetExtras()
        override fun save(characterId: String, extras: CharacterSheetExtras) { values[characterId] = extras }
        override fun delete(characterId: String) { values.remove(characterId) }
    }

    private var nextId = 1
    private val initial = DublCharacter(id = "a", name = "Шершень")
    private val app = DublApplication(
        characterStore = InMemoryCharacterStore(AppSnapshot(listOf(initial), initial.id)),
        extrasStore = MemoryExtras(),
        idFactory = { "id-${nextId++}" },
    )

    private fun dexterity(): Int = app.active.attributes.getValue(AttributeId.DEXTERITY).base

    @Test
    fun restoreBringsCharacterAndExtrasBack() {
        val point = app.restorePoint()
        app.character.changeAttribute(AttributeId.DEXTERITY, 1)
        app.sheet.setPortrait("x")
        assertTrue(app.restore(point))
        assertEquals(point.character, app.active)
        assertEquals(point.extras, app.activeExtras)
    }

    @Test
    fun restoreIsOneUndoStep() {
        val point = app.restorePoint()
        app.character.changeAttribute(AttributeId.DEXTERITY, 1)
        app.sheet.setPortrait("x")
        app.restore(point)
        assertTrue(app.undoLast())
        assertEquals(1, dexterity())
        assertEquals("x", app.activeExtras.portraitUri)
    }

    @Test
    fun restoreRefusesAnotherCharacter() {
        val point = app.restorePoint()
        app.character.createCharacter()
        val other = app.active
        assertFalse(app.restore(point))
        assertEquals(other, app.active)
    }
}
