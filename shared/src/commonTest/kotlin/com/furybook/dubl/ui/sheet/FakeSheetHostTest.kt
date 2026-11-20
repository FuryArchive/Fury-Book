package com.furybook.dubl.ui.sheet

import com.furybook.dubl.model.CharacterSheetResourceId
import com.furybook.dubl.model.DublCharacter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FakeSheetHostTest {
    private fun host() = FakeSheetHost(DublCharacter(id = "c1", name = "Тест", hpCurrent = 10, healthMaximumOverride = 20))

    @Test
    fun changeResourceLowersHpAndEnablesUndo() {
        val host = host()
        val before = host.active.hpCurrent
        assertFalse(host.canUndo)

        host.changeResource(CharacterSheetResourceId.HEALTH, -3)

        assertEquals(before - 3, host.active.hpCurrent)
        assertTrue(host.canUndo)
    }

    @Test
    fun undoRestoresHp() {
        val host = host()
        val before = host.active.hpCurrent
        host.changeResource(CharacterSheetResourceId.HEALTH, -3)

        assertTrue(host.undoLast())

        assertEquals(before, host.active.hpCurrent)
    }
}
