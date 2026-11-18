package com.furybook.dubl.ui.editor

import com.furybook.dubl.model.DublCharacter
import com.furybook.dubl.ui.sheet.FakeSheetHost
import kotlin.test.Test
import kotlin.test.assertEquals

class EditorIdentityDraftTest {
    private fun host() = FakeSheetHost(DublCharacter(id = "c1", name = "Шершень", concept = "наёмник", creationComplete = true))

    @Test
    fun typedNameIsSaved() {
        val host = host()
        val draft = EditorIdentityDraft(host.active, generation = 0)
        draft.edit(name = "Оса", concept = "наёмник")
        draft.save(host, generation = 0)
        assertEquals("Оса", host.active.name)
    }

    @Test
    fun saveAfterCharacterSwitchLeavesTheOtherCharacterAlone() {
        val host = host()
        val draft = EditorIdentityDraft(host.active, generation = 0)
        draft.edit(name = "Оса", concept = "наёмник")
        host.createCharacter()
        val other = host.active
        draft.save(host, generation = 0)
        assertEquals(other, host.active)
    }

    @Test
    fun untouchedDraftNeverWritesOntoAnotherCharacter() {
        val host = host()
        val draft = EditorIdentityDraft(host.active, generation = 0)
        host.createCharacter()
        val other = host.active
        draft.save(host, generation = 0)
        assertEquals(other, host.active)
    }

    @Test
    fun restoreAfterSavedRenameIsNotUndoneByTheField() {
        val host = host()
        val point = host.restorePoint()
        val draft = EditorIdentityDraft(host.active, generation = 0)
        draft.edit(name = "Оса", concept = "убийца")
        draft.save(host, generation = 0)
        host.restore(point)
        draft.sync(host.active, generation = 1)
        assertEquals("Шершень", draft.name)
        assertEquals("наёмник", draft.concept)
        draft.save(host, generation = 1)
        assertEquals("Шершень", host.active.name)
        assertEquals("наёмник", host.active.concept)
    }

    @Test
    fun pendingTypingIsDroppedByRestore() {
        val host = host()
        val point = host.restorePoint()
        val draft = EditorIdentityDraft(host.active, generation = 0)
        draft.edit(name = "Оса", concept = "наёмник")
        host.restore(point)
        draft.save(host, generation = 1)
        assertEquals("Шершень", host.active.name)
    }

    @Test
    fun outsideRenameShowsUpWhenNothingIsPending() {
        val host = host()
        val draft = EditorIdentityDraft(host.active, generation = 0)
        host.setIdentity("Оса", "наёмник", host.active.size, host.active.legs, host.active.manaEnabled)
        draft.sync(host.active, generation = 0)
        assertEquals("Оса", draft.name)
    }
}
