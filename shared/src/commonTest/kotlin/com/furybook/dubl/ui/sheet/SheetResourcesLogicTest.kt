package com.furybook.dubl.ui.sheet

import com.furybook.dubl.model.CharacterSheetResourceId
import com.furybook.dubl.model.DublCharacter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SheetResourcesLogicTest {
    @Test
    fun hidingHealthRemovesItFromVitals() {
        val host = FakeSheetHost(DublCharacter(id = "c1", name = "Тест"))
        assertTrue(sheetVitals(host).any { it.id == "health" })
        host.setResourceHidden(CharacterSheetResourceId.HEALTH, true)
        assertFalse(sheetVitals(host).any { it.id == "health" })
        assertTrue(sheetResourceVisibility(host).first { it.resource == CharacterSheetResourceId.HEALTH }.hidden)
    }

    @Test
    fun customCurrentClampedToMaximumInForm() {
        assertEquals(5, customResourceCurrent(9, 5))
        assertEquals(0, customResourceCurrent(-2, 5))
    }
}
