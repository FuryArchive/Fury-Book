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

    @Test
    fun skillColumnsFollowTheRoomNotTheWindowClass() {
        // 1440 window: ~896dp of content → two columns; 1100 window: ~556dp → one, or the names vanish.
        assertEquals(2, editorSkillColumns(896))
        assertEquals(1, editorSkillColumns(556))
        assertEquals(1, editorSkillColumns(360))
    }

    @Test
    fun openClearsThePlan() {
        val host = FakeSheetHost(EditorFixtures.hornet, developmentCatalogOverride = EditorFixtures.catalog)
        val state = EditorState()
        state.open(host, forceBasics = false)
        state.togglePlanned("feat_hear")
        assertEquals(setOf("feat_hear"), state.plannedIds)
        state.togglePlanned("feat_hear")
        assertTrue(state.plannedIds.isEmpty())
        state.togglePlanned("feat_hear")
        state.open(host, forceBasics = false)
        assertTrue(state.plannedIds.isEmpty())
    }

    private fun hornetHost() = FakeSheetHost(EditorFixtures.hornet, developmentCatalogOverride = EditorFixtures.catalog)

    @Test
    fun removeOwnPerkOffersReturn() {
        val host = hornetHost()
        val state = EditorState()
        state.open(host, forceBasics = false)
        val id = host.addCustomDevelopment(EditorFixtures.entry("", "Гадание", cost = 25))!!
        host.setDevelopmentRank(id, 1)
        state.togglePlanned(id)
        state.selectChoices(id)
        editorRemoveOwnPerk(host, state, id, "Гадание")
        assertTrue(host.active.customDevelopmentEntries.none { it.id == id })
        assertEquals(null, state.selectedEntryId)
        assertTrue(id !in state.plannedIds)
        assertTrue(state.toastUndoable)
        assertTrue(state.undoToast(host))
        assertEquals(1, host.active.development.getValue(id).rank)
        assertTrue(host.active.customDevelopmentEntries.any { it.id == id })
    }

    @Test
    fun resetPerkOffersReturn() {
        val host = hornetHost()
        val state = EditorState()
        state.open(host, forceBasics = false)
        host.setDevelopmentOverride(EditorFixtures.keenHearing.copy(cost = 10))
        state.selectChoices("feat_hear")
        editorResetPerk(host, state, "feat_hear", "Острый слух")
        assertEquals(30, host.developmentCatalog.byId("feat_hear")!!.cost)
        assertEquals("feat_hear", state.selectedEntryId)
        assertTrue(state.undoToast(host))
        assertEquals(10, host.developmentCatalog.byId("feat_hear")!!.cost)
    }

    @Test
    fun newPerkOpensInDetail() {
        val host = hornetHost()
        val state = EditorState()
        state.open(host, forceBasics = false)
        val form = editorPerkForm(editorNewPerk()).copy(name = "Гадание")
        assertTrue(editorSavePerk(host, state, form, editorNewPerk(), EditorPerkFormMode.NEW))
        val id = host.active.customDevelopmentEntries.single().id
        assertEquals(EditorStep.PERKS, state.step)
        assertEquals(id, state.selectedEntryId)
        assertFalse(editorSavePerk(host, state, form.copy(name = ""), editorNewPerk(), EditorPerkFormMode.NEW))
        val edited = editorPerkForm(EditorFixtures.keenHearing).copy(cost = "12")
        assertTrue(editorSavePerk(host, state, edited, EditorFixtures.keenHearing, EditorPerkFormMode.LOCAL_EDIT))
        assertEquals(12, host.developmentCatalog.byId("feat_hear")!!.cost)
        val own = host.developmentCatalog.byId(id)!!
        assertTrue(editorSavePerk(host, state, editorPerkForm(own).copy(name = "Ворожба"), own, EditorPerkFormMode.EDIT_OWN))
        assertEquals("Ворожба", host.developmentCatalog.byId(id)!!.name)
    }

    @Test
    fun unchangedLocalEditStoresNothing() {
        val host = hornetHost()
        val state = EditorState()
        state.open(host, forceBasics = false)
        assertTrue(editorSavePerk(host, state, editorPerkForm(EditorFixtures.keenHearing), EditorFixtures.keenHearing, EditorPerkFormMode.LOCAL_EDIT))
        assertTrue(host.active.developmentOverrides.isEmpty())
        host.setDevelopmentOverride(EditorFixtures.keenHearing.copy(cost = 10))
        val edited = host.developmentCatalog.byId("feat_hear")!!
        assertTrue(editorSavePerk(host, state, editorPerkForm(edited).copy(cost = "30"), edited, EditorPerkFormMode.LOCAL_EDIT))
        assertTrue(host.active.developmentOverrides.isEmpty())
    }
}
