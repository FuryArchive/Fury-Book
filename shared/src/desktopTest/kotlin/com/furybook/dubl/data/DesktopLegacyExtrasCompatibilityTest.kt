package com.furybook.dubl.data

import com.furybook.dubl.acquisition.*
import com.furybook.dubl.model.*
import java.nio.file.Files
import kotlin.test.*

class DesktopLegacyExtrasCompatibilityTest {
    @Test fun old_extras_open_edit_and_restart_without_erasing_extensions_or_original_backup() {
        val root = Files.createTempDirectory("fury-old-extras")
        val path = root.resolve("sheet-extras.json")
        val raw = """{"version":1,"characters":{"old":{"notes":"Лист до обновления","favorites":["reflexes"],"legacyLayout":{"columns":2}},"other":{"notes":"Чужая заметка","opaque":123}}}"""
        Files.writeString(path, raw)
        val store = DesktopCharacterExtrasStore(path)
        assertEquals("Лист до обновления", store.load("old").notes)
        assertEquals(raw, Files.readString(path))
        store.save("old", store.load("old").copy(notes = "После обновления"))
        assertEquals("После обновления", DesktopCharacterExtrasStore(path).load("old").notes)
        val saved = Files.readString(path)
        assertTrue(saved.contains("\"favorites\":[\"reflexes\"]"))
        assertTrue(saved.contains("\"legacyLayout\":{\"columns\":2}"))
        assertTrue(saved.contains("\"other\":{\"notes\":\"Чужая заметка\",\"opaque\":123}"))
        val backup = path.resolveSibling("sheet-extras.json.pre-compatibility.bak")
        assertEquals(raw, Files.readString(backup))
        store.save("old", store.load("old").copy(portraitUri = "portrait"))
        assertEquals(raw, Files.readString(backup))
        assertFails { DesktopCharacterExtrasStore.decodeLegacy(saved) }
    }

    @Test fun readable_future_extras_do_not_allow_financial_migration_or_destructive_save() {
        val root = Files.createTempDirectory("fury-future-extras")
        val path = root.resolve("sheet-extras.json")
        val raw = """{"version":999,"characters":{"old":{"notes":"Сохранить"}}}"""
        Files.writeString(path, raw)
        val store = DesktopCharacterExtrasStore(path)
        assertEquals("Сохранить", store.load("old").notes)
        assertFails { store.save("old", CharacterSheetExtras()) }
        assertFailsWith<UnsupportedDesktopData> { DesktopCharacterExtrasStore.decodeLegacy(raw) }
        assertEquals(raw, Files.readString(path))
    }
}
