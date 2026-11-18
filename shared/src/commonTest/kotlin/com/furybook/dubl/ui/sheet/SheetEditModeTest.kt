package com.furybook.dubl.ui.sheet

import com.furybook.dubl.model.AttributeId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SheetEditModeTest {
    @Test
    fun editingResetsOnCharacterChange() {
        val state = SheetScreenState()
        state.syncCharacter("a")
        state.openEditor(forceBasics = false)
        state.openDialog(SheetDialog.Economy)
        state.open(SheetRollSubject.Attribute(AttributeId.STRENGTH))
        state.syncCharacter("a")
        assertTrue(state.editing)
        state.syncCharacter("b")
        assertFalse(state.editing)
        assertNull(state.dialog)
        assertNull(state.request)
    }

    @Test
    fun leavingTheEditorClosesTheOpenDialog() {
        val state = SheetScreenState()
        state.openEditor(forceBasics = false)
        state.openDialog(SheetDialog.Economy)
        state.updateEditing(false)
        assertNull(state.dialog)
    }

    @Test
    fun dialogsOpenAndClose() {
        val state = SheetScreenState()
        state.openDialog(SheetDialog.Vital("health"))
        assertEquals(SheetDialog.Vital("health"), state.dialog)
        state.closeDialog()
        assertNull(state.dialog)
    }
}

class SheetTargetChangeTest {
    @Test
    fun changingOnlyTheTargetKeepsTheResult() {
        val state = SheetScreenState()
        val host = FakeSheetHost(com.furybook.dubl.model.DublCharacter(id = "c1", name = "Тест"))
        state.open(SheetRollSubject.Context(com.furybook.dubl.model.RollContext.ATTACK))
        val dice = sequenceOf(3, 4).iterator()
        state.roll(host) { dice.next() }
        val rolled = state.result
        kotlin.test.assertNotNull(rolled)
        state.updateRequest(state.request!!.copy(target = 15))
        assertEquals(rolled, state.result)
        state.updateRequest(state.request!!.copy(situational = 2))
        assertNull(state.result)
    }
}
