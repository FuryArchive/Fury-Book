package com.furybook.ui.theme

import kotlin.test.Test
import kotlin.test.assertEquals

class FuryThemeStoreTest {
    @Test
    fun inMemoryStoreRoundTrips() {
        val store = InMemoryFuryThemeStore()
        assertEquals(FuryThemeId.FURY, store.load())
        store.save(FuryThemeId.PARCHMENT)
        assertEquals(FuryThemeId.PARCHMENT, store.load())
    }
}
