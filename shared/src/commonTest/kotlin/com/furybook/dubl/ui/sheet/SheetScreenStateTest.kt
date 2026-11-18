package com.furybook.dubl.ui.sheet

import com.furybook.dubl.model.AttributeId
import com.furybook.dubl.model.AttributeValue
import com.furybook.dubl.model.DublCharacter
import com.furybook.dubl.model.RollMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SheetScreenStateTest {
    private val host = FakeSheetHost(
        DublCharacter(id = "hornet", name = "Шершень", attributes = AttributeId.entries.associateWith { AttributeValue(base = 3) }),
    )
    private val dexterity = SheetRollSubject.Attribute(AttributeId.DEXTERITY)

    private fun dice(vararg faces: Int): () -> Int {
        val iterator = faces.iterator()
        return { iterator.next() }
    }

    @Test
    fun openingSetsRequestAndClearsResult() {
        val state = SheetScreenState()
        state.open(dexterity)
        state.roll(host, dice(3, 4))
        assertNotNull(state.result)
        state.open(SheetRollSubject.Attribute(AttributeId.STRENGTH))
        assertEquals(SheetRollRequest(SheetRollSubject.Attribute(AttributeId.STRENGTH)), state.request)
        assertNull(state.result)
        assertEquals(true, state.rollOpen)
    }

    @Test
    fun changingSetupClearsResultButKeepsHistory() {
        val state = SheetScreenState()
        state.open(dexterity)
        state.roll(host, dice(3, 4))
        state.updateRequest(state.request!!.copy(mode = RollMode.ADVANTAGE))
        assertNull(state.result)
        assertEquals(1, state.history.size)
    }

    @Test
    fun unchangedRequestKeepsResult() {
        val state = SheetScreenState()
        state.open(dexterity)
        state.roll(host, dice(3, 4))
        state.updateRequest(state.request!!.copy())
        assertNotNull(state.result)
    }

    @Test
    fun historyKeepsLastFiveNewestFirst() {
        val state = SheetScreenState()
        state.open(dexterity)
        repeat(7) { index -> state.roll(host, dice(1, index % 6 + 1)) }
        assertEquals(5, state.history.size)
        assertEquals(state.result!!.total, state.history.first().total)
    }

    @Test
    fun trayTapDoesNotOpenDialog() {
        val state = SheetScreenState()
        state.open(dexterity, dialog = false)
        assertEquals(SheetRollRequest(dexterity), state.request)
        assertEquals(false, state.rollOpen)
        state.open(dexterity)
        assertEquals(true, state.rollOpen)
        state.open(dexterity, dialog = false)
        assertEquals(false, state.rollOpen)
    }

    @Test
    fun rollWithoutRequestDoesNothing() {
        val state = SheetScreenState()
        state.roll(host, dice(3, 4))
        assertNull(state.result)
        assertEquals(0, state.history.size)
    }

    @Test
    fun editorOpensAtBasicsWhenRequested() {
        val state = SheetScreenState()
        state.openEditor(forceBasics = true)
        kotlin.test.assertTrue(state.editing)
        kotlin.test.assertTrue(state.editorForceBasics)
        state.updateEditing(false)
        kotlin.test.assertFalse(state.editing)
        kotlin.test.assertFalse(state.editorForceBasics)
        state.openEditor(forceBasics = false)
        kotlin.test.assertFalse(state.editorForceBasics)
    }
}
