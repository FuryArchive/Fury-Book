package com.furybook.dubl.application

/** Legacy-session one-step Undo only. Canonical operations use persisted AcquisitionUndo;
 * this manager must never receive a canonical snapshot or publish a canonical restoration. */
internal class ApplicationUndoManager {
    private var pending: (() -> Unit)? = null

    val canUndo: Boolean get() = pending != null

    fun record(action: () -> Unit) {
        pending = action
    }

    fun undoLast(): Boolean {
        val action = pending ?: return false
        pending = null
        action()
        return true
    }
}
