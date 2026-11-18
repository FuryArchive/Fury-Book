package com.furybook.dubl.ui.editor

import com.furybook.dubl.model.DublCharacter
import com.furybook.dubl.ui.sheet.FakeSheetHost
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EditorStateTest {
    private val creating = listOf(EditorStep.BASICS, EditorStep.ATTRIBUTES, EditorStep.SKILLS, EditorStep.PERKS, EditorStep.SUMMARY)

    @Test
    fun stepsForNewAndExisting() {
        assertEquals(creating, editorSteps(creationComplete = false))
        assertEquals(listOf(EditorStep.DEVELOPMENT) + creating, editorSteps(creationComplete = true))
    }

    @Test
    fun startStep() {
        assertEquals(EditorStep.BASICS, editorStartStep(creationComplete = false, forceBasics = false))
        assertEquals(EditorStep.DEVELOPMENT, editorStartStep(creationComplete = true, forceBasics = false))
        assertEquals(EditorStep.BASICS, editorStartStep(creationComplete = true, forceBasics = true))
    }

    @Test
    fun nextAndBackClamp() {
        val host = FakeSheetHost(DublCharacter(id = "c1", name = "Шершень"))
        val state = EditorState()
        state.open(host, forceBasics = false)
        state.back(host)
        assertEquals(EditorStep.BASICS, state.step)
        repeat(10) { state.next(host) }
        assertEquals(EditorStep.SUMMARY, state.step)
        state.back(host)
        assertEquals(EditorStep.PERKS, state.step)
    }

    @Test
    fun openTakesSnapshotAndRankedDefault() {
        val host = FakeSheetHost(DublCharacter(id = "c1", name = "Шершень", creationComplete = true))
        val state = EditorState()
        state.open(host, forceBasics = false)
        assertEquals(host.active, state.snapshot?.character)
        assertEquals(EditorStep.DEVELOPMENT, state.step)
        assertTrue(state.skillsRankedOnly)
        val fresh = EditorState().apply { open(FakeSheetHost(DublCharacter(id = "n", name = "Новый персонаж")), forceBasics = false) }
        assertFalse(fresh.skillsRankedOnly)
    }

    @Test
    fun stateResetsOnCharacterChange() {
        val host = FakeSheetHost(DublCharacter(id = "c1", name = "Шершень", creationComplete = true))
        val state = EditorState()
        state.open(host, forceBasics = false)
        state.step = EditorStep.SKILLS
        state.selectedEntryId = "a"
        host.createCharacter()
        state.syncCharacter(host)
        assertEquals(host.active.id, state.snapshot?.character?.id)
        assertEquals(EditorStep.BASICS, state.step)
        assertEquals(null, state.selectedEntryId)
    }

    @Test
    fun undoableToastOffersReturnOnlyAfterRestore() {
        val state = EditorState()
        state.flash("Взято: Острый слух")
        assertFalse(state.toastUndoable)
        state.flash("Персонаж возвращён", undoable = true)
        assertTrue(state.toastUndoable)
        state.flash("Ещё что-то")
        assertFalse(state.toastUndoable)
    }
}
