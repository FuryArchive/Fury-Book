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
        val host = FakeSheetHost(DublCharacter(id = "c1", name = "Шершень"))
        val state = EditorState()
        state.flash("Взято: Острый слух")
        assertFalse(state.toastUndoable)
        state.flash("Персонаж возвращён", undoableAt = host.restorePoint())
        assertTrue(state.toastUndoable)
        state.flash("Ещё что-то")
        assertFalse(state.toastUndoable)
    }

    @Test
    fun returnIsWithdrawnOnceAnythingChangesAfterTheRestore() {
        val host = FakeSheetHost(DublCharacter(id = "c1", name = "Шершень"))
        val state = EditorState()
        state.flash("Персонаж возвращён", undoableAt = host.restorePoint())
        state.dropStaleUndo(host.restorePoint())
        assertTrue(state.toastUndoable)
        host.setIdentity("Оса", "", host.active.size, host.active.legs, host.active.manaEnabled)
        state.dropStaleUndo(host.restorePoint())
        assertFalse(state.toastUndoable)
        assertEquals("Персонаж возвращён", state.toast)
    }

    @Test
    fun perkDetailSitsBesideTheListOnlyWhenTheListKeepsRoom() {
        // 1440 window: 896dp of content; 1100 window: 556dp, too narrow for a 360dp pane plus a readable list.
        assertTrue(editorPerksSplit(contentWidthDp = 896))
        assertFalse(editorPerksSplit(contentWidthDp = 556))
    }

    @Test
    fun stepTabsScrollToCentreTheCurrentTab() {
        // 360dp strip, 700dp of tabs: the current tab is centred, clamped to the scroll range.
        assertEquals(0, editorTabScrollTarget(tabStart = 12, tabWidth = 100, viewport = 360, maxScroll = 340))
        assertEquals(190, editorTabScrollTarget(tabStart = 320, tabWidth = 100, viewport = 360, maxScroll = 340))
        assertEquals(340, editorTabScrollTarget(tabStart = 600, tabWidth = 100, viewport = 360, maxScroll = 340))
    }

    @Test
    fun openAtSkill() {
        val host = FakeSheetHost(DublCharacter(id = "c1", name = "Шершень", creationComplete = true))
        val state = EditorState()
        state.open(host, forceBasics = false, skillId = "stealth")
        assertEquals(EditorStep.SKILLS, state.step)
        assertEquals("stealth", state.skillPanelId)
        assertFalse(state.skillsRankedOnly)
        state.skillCategory = com.furybook.dubl.model.SkillCategory.COMBAT
        state.open(host, forceBasics = false)
        assertEquals(null, state.skillPanelId)
        assertEquals(null, state.skillCategory)
    }

    @Test
    fun returnUndoesHideAndDelete() {
        val host = FakeSheetHost(DublCharacter(id = "c1", name = "Шершень", experience = 500))
        val state = EditorState()
        state.open(host, forceBasics = false)
        val id = host.addCustomSkill("Гадание", "", listOf(com.furybook.dubl.model.AttributeId.INTELLIGENCE), com.furybook.dubl.model.UntrainedRule.YES)!!
        host.changeSkillRank(id, 1)
        host.changeSkillRank(id, 1)
        val beforeDelete = host.restorePoint()
        host.deleteDynamicSkill(id)
        state.flash("Удалено: Гадание", undoableAt = host.restorePoint(), undoTo = beforeDelete)
        assertTrue(state.undoToast(host))
        assertEquals(2, host.active.skills.getValue(id).rank)
        val beforeHide = host.restorePoint()
        host.hideSkill(id)
        state.flash("Скрыто: Гадание", undoableAt = host.restorePoint(), undoTo = beforeHide)
        assertTrue(state.undoToast(host))
        assertTrue(id !in host.active.hiddenSkillIds)
    }

    @Test
    fun undoToastsStayLongEnoughToReach() {
        assertTrue(editorToastMs(undoable = true) >= 5000)
        assertTrue(editorToastMs(undoable = false) < editorToastMs(undoable = true))
    }
}
