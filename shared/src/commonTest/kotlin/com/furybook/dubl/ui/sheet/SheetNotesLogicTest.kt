package com.furybook.dubl.ui.sheet

import kotlin.test.Test
import kotlin.test.assertEquals

class SheetNotesLogicTest {
    @Test
    fun noteLimitsEnforced() {
        assertEquals(120, noteTitleInput("я".repeat(200)).length)
        assertEquals(12000, noteBodyInput("ж".repeat(13000)).length)
        assertEquals("Коротко", noteTitleInput("Коротко"))
    }
}
