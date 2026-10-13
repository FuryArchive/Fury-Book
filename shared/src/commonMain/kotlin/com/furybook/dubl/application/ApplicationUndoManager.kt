package com.furybook.dubl.application

/** Legacy-session one-step Undo only. Canonical operations use persisted AcquisitionUndo;
 * this manager must never receive a canonical snapshot or publish a canonical restoration. */
internal class ApplicationUndoManager(private val beforeWrite: () -> Unit = {}) {
    private var pending: (() -> Unit)? = null

    val canUndo: Boolean get() = pending != null

    fun record(action: () -> Unit) {
        beforeWrite()
        pending = action
    }

    fun undoLast(): Boolean {
        beforeWrite()
        val action = pending ?: return false
        pending = null
        action()
        return true
    }
}
