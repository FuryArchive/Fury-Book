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

    @Test
    fun editorClosesWhenTheHostIsReplaced() {
        val state = SheetScreenState()
        val first = Any()
        state.syncHost(first)
        state.openEditor(forceBasics = false)
        state.syncHost(first)
        kotlin.test.assertTrue(state.editing)
        state.syncHost(Any())
        kotlin.test.assertFalse(state.editing)
    }

    @Test
    fun openingTheEditorTakesOneSnapshotPerVisit() {
        val state = SheetScreenState()
        val opened = mutableListOf<Boolean>()
        state.onEditorOpening = { forceBasics, _, _, _ -> opened += forceBasics }
        state.openEditor(forceBasics = true)
        state.openEditor(forceBasics = false)
        kotlin.test.assertEquals(listOf(true), opened)
        state.updateEditing(false)
        state.openEditor(forceBasics = false)
        kotlin.test.assertEquals(listOf(true, false), opened)
    }

    @Test
    fun editorOpensAtSkillFromRollPanel() {
        val state = SheetScreenState()
        var opened: Pair<Boolean, String?>? = null
        state.onEditorOpening = { forceBasics, skillId, _, _ -> opened = forceBasics to skillId }
        state.open(SheetRollSubject.Skill("stealth"))
        state.openDialog(SheetDialog.SkillInfo("stealth"))
        state.openEditor(forceBasics = false, skillId = "stealth")
        kotlin.test.assertEquals(false to "stealth", opened)
        kotlin.test.assertTrue(state.editing)
        kotlin.test.assertFalse(state.rollOpen)
        kotlin.test.assertEquals(null, state.dialog)
    }

    @Test
    fun editorOpensAtPerkFromSheet() {
        val state = SheetScreenState()
        var opened: Triple<Boolean, String?, String?>? = null
        state.onEditorOpening = { forceBasics, skillId, entryId, _ -> opened = Triple(forceBasics, skillId, entryId) }
        state.openDialog(SheetDialog.DevelopmentDetails("feat_hear"))
        state.openEditor(forceBasics = false, entryId = "feat_hear")
        kotlin.test.assertEquals(Triple(false, null, "feat_hear"), opened)
        kotlin.test.assertTrue(state.editing)
        kotlin.test.assertEquals(null, state.dialog)
    }

    @Test
    fun castAndGearDialogsOpenAndClose() {
        val state = SheetScreenState()
        state.openDialog(SheetDialog.Cast("u"))
        assertEquals(SheetDialog.Cast("u"), state.dialog)
        state.updateEditing(true)
        assertNull(state.dialog)
        state.updateEditing(false)
        state.openDialog(SheetDialog.GearItem("g"))
        state.closeDialog()
        assertNull(state.dialog)
    }

    @Test
    fun editorOpensAtTargetFromSheet() {
        val state = SheetScreenState()
        var target: com.furybook.dubl.ui.editor.EditorOpenTarget? = null
        state.onEditorOpening = { _, _, _, t -> target = t }
        state.openEditor(forceBasics = false, target = com.furybook.dubl.ui.editor.EditorOpenTarget.Gear("u"))
        assertEquals(com.furybook.dubl.ui.editor.EditorOpenTarget.Gear("u"), target)
    }
}
