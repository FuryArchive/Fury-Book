package com.furybook.dubl.ui.master

import com.furybook.dubl.master.InMemoryMasterStore
import com.furybook.dubl.master.MasterData
import com.furybook.dubl.master.MasterEnemy
import com.furybook.dubl.master.MasterNote
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MasterStateTest {
    private val goblin = MasterEnemy("g", "Гоблин")

    @Test
    fun everyChangeIsSaved() {
        val store = InMemoryMasterStore()
        val state = MasterState(store)
        state.update({ it.copy(enemies = listOf(goblin)) })
        assertEquals(listOf(goblin), store.load().enemies)
        assertEquals(listOf(goblin), MasterState(store).data.enemies)
    }

    @Test
    fun undoRestoresLastUndoable() {
        val store = InMemoryMasterStore(MasterData(enemies = listOf(goblin)))
        val state = MasterState(store)
        state.update({ it.copy(enemies = emptyList()) }, undoable = "Гоблин удалён")
        assertEquals("Гоблин удалён", state.undoLabel)
        assertTrue(state.undo())
        assertEquals(listOf(goblin), store.load().enemies)
        assertNull(state.undoLabel)
        assertFalse(state.undo())
    }

    @Test
    fun nonUndoableChangesClearUndo() {
        val state = MasterState(InMemoryMasterStore(MasterData(enemies = listOf(goblin))))
        state.update({ it.copy(enemies = emptyList()) }, undoable = "Гоблин удалён")
        state.update({ it.copy(notes = listOf(MasterNote("n", "x"))) })
        assertFalse(state.undo())
        assertEquals(emptyList(), state.data.enemies)
    }
}

class MasterScopeTest {
    @Test
    fun toastLosesVernutWhenUndoIsGone() {
        val scope = MasterScope(MasterState(InMemoryMasterStore(MasterData(enemies = listOf(MasterEnemy("g", "Гоблин"))))), com.furybook.ui.components.FuryToastState())
        scope.change("Гоблин удалён") { it.copy(enemies = emptyList()) }
        assertTrue(scope.toast.undo != null)
        scope.change { it.copy(notes = listOf(MasterNote("n", "x"))) }
        assertNull(scope.toast.undo)
    }
}
