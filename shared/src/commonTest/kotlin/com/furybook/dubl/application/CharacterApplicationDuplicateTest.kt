package com.furybook.dubl.application

import com.furybook.dubl.data.CharacterExtrasStore
import com.furybook.dubl.data.InMemoryCharacterStore
import com.furybook.dubl.model.AppSnapshot
import com.furybook.dubl.model.CharacterSheetExtras
import com.furybook.dubl.model.DublCharacter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class CharacterApplicationDuplicateTest {
    private class MemoryExtras : CharacterExtrasStore {
        val values = linkedMapOf<String, CharacterSheetExtras>()
        override fun load(characterId: String): CharacterSheetExtras = values[characterId] ?: CharacterSheetExtras()
        override fun save(characterId: String, extras: CharacterSheetExtras) { values[characterId] = extras }
        override fun delete(characterId: String) { values.remove(characterId) }
    }

    private var nextId = 1
    private val first = DublCharacter(id = "a", name = "Шершень", concept = "Наёмник", experience = 40)
    private val second = DublCharacter(id = "b", name = "Вика Ястреб")
    private val extras = MemoryExtras().also { it.save("a", CharacterSheetExtras(portraitUri = "data:p")) }
    private val app = DublApplication(
        characterStore = InMemoryCharacterStore(AppSnapshot(listOf(first, second), "b")),
        extrasStore = extras,
        idFactory = { "id-${nextId++}" },
    )

    @Test
    fun duplicateCopiesWithNewIdAndName() {
        val copyId = app.character.duplicate("a")!!
        assertNotEquals("a", copyId)
        val ids = app.snapshot.characters.map { it.id }
        assertEquals(listOf("a", copyId, "b"), ids)
        val copy = app.snapshot.characters[1]
        assertEquals("Шершень (копия)", copy.name)
        assertEquals(first.copy(id = copyId, name = "Шершень (копия)"), copy)
        assertEquals("data:p", extras.load(copyId).portraitUri)
        // The player stays where they were.
        assertEquals("b", app.snapshot.activeCharacterId)
    }

    @Test
    fun exportTakesAnyCharacterNotOnlyTheActive() {
        val raw = app.transfer.export("a")!!
        assertEquals(true, raw.contains("Шершень"))
        assertNull(app.transfer.export("zzz"))
        assertEquals("b", app.snapshot.activeCharacterId)
    }

    @Test
    fun duplicateOfUnknownIdDoesNothing() {
        assertNull(app.character.duplicate("zzz"))
        assertEquals(2, app.snapshot.characters.size)
    }
}
