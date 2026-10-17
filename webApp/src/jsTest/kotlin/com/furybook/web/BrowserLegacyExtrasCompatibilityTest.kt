package com.furybook.web

import com.furybook.dubl.model.*
import kotlin.test.*

class BrowserLegacyExtrasCompatibilityTest {
    @Test fun old_browser_extras_survive_display_edit_and_store_recreation() {
        clearBrowserSessionData()
        try {
            bindBrowserSessionOwner("legacy-compatibility")
            BrowserCharacterStore().replaceRaw("""{"schema":7,"activeCharacterId":"old","characters":[{"id":"old","name":"Старый лист"}]}""")
            val raw = """{"version":1,"characters":{"old":{"notes":"Старая заметка\nвторая строка","favoriteSkillIds":["athletics"],"oldPanel":{"tab":2}},"other":{"notes":"Другой лист"}}}"""
            val store = BrowserCharacterExtrasStore()
            store.replaceRaw(raw)
            assertEquals("Старая заметка\nвторая строка", store.load("old").notes)
            assertEquals(raw, captureBrowserLegacySource("legacy-compatibility")!!.extrasRaw)
            store.delete("absent")
            assertIs<BrowserLegacySourceState.Present>(browserLegacySourceState("legacy-compatibility"))
            assertFails { decodeBrowserLegacyExtras(raw) }
            store.save("old", store.load("old").copy(portraitUri = "portrait.png"))
            val reopened = BrowserCharacterExtrasStore()
            assertEquals("Старая заметка\nвторая строка", reopened.load("old").notes)
            assertEquals("portrait.png", reopened.load("old").portraitUri)
            assertEquals("Другой лист", reopened.load("other").notes)
            assertTrue(reopened.raw().contains("\"oldPanel\":{\"tab\":2}"))
            assertTrue(reopened.raw().contains("\"favoriteSkillIds\":[\"athletics\"]"))
            assertEquals(raw, captureBrowserLegacySource("legacy-compatibility")!!.extrasRaw)
        } finally { clearBrowserSessionData() }
    }
}
