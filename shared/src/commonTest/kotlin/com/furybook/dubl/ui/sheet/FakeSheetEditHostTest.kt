package com.furybook.dubl.ui.sheet

import com.furybook.dubl.model.AttributeId
import com.furybook.dubl.model.DublCharacter
import com.furybook.dubl.model.displayNotes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FakeSheetEditHostTest {
    private fun host(): DublSheetEditHost = FakeSheetHost(DublCharacter(id = "c1", name = "Тест"))

    @Test
    fun blankNameFollowsTheApplicationRule() {
        val host = host()
        host.setIdentity("  ", "", 5, 2, false)
        assertEquals("Новый персонаж", host.active.name)
    }

    @Test
    fun changeAttributeRaisesItAndEnablesUndo() {
        val host = host()
        val before = host.active.attributes.getValue(AttributeId.STRENGTH).base
        host.changeAttribute(AttributeId.STRENGTH, 1)
        assertEquals(before + 1, host.active.attributes.getValue(AttributeId.STRENGTH).base)
        assertTrue(host.canUndo)
    }

    @Test
    fun addedCustomResourceIsListedOnTheCharacter() {
        val host = host()
        val uid = assertNotNull(host.addCustomResource("Ярость", 5, 5))
        assertTrue(host.active.customResources.any { it.uid == uid && it.name == "Ярость" })
    }

    @Test
    fun addedNoteAppearsInDisplayNotes() {
        val host = host()
        host.addNote("A", "b")
        assertTrue(host.extras.displayNotes().any { it.title == "A" && it.body == "b" })
    }
}
