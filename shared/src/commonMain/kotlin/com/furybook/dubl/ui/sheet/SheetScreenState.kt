package com.furybook.dubl.ui.sheet

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.furybook.dubl.model.RollResult
import com.furybook.ui.components.FuryRollHistoryEntry

private const val SHEET_HISTORY_LIMIT = 5

/** The one popup the sheet shows at a time. */
sealed interface SheetDialog {
    data class Vital(val vitalId: String) : SheetDialog
    data class Stat(val stat: SheetStatId, val roll: SheetRollSubject?) : SheetDialog
    data object Conditions : SheetDialog
    data object Identity : SheetDialog
    data object Economy : SheetDialog
    data object Resources : SheetDialog
    data class Groups(val kind: SheetGroupKind) : SheetDialog
    data class DevelopmentDetails(val entryId: String) : SheetDialog
}

/**
 * State owned by [SheetScreen]: play/edit mode, the open popup, the current roll setup, its result,
 * the last few totals and whether the roll dialog is open (phone and wide layouts; the docked tray
 * ignores [rollOpen]). [syncCharacter] resets all of it when the active character changes.
 */
class SheetScreenState {
    var request: SheetRollRequest? by mutableStateOf(null)
        private set
    var result: RollResult? by mutableStateOf(null)
        private set
    var history: List<FuryRollHistoryEntry> by mutableStateOf(emptyList())
        private set
    var rollOpen: Boolean by mutableStateOf(false)
        private set
    var editing: Boolean by mutableStateOf(false)
        private set
    var dialog: SheetDialog? by mutableStateOf(null)
        private set
    private var characterId: String? = null

    /** Called with the active character on every composition; a different character starts over in play mode. */
    fun syncCharacter(id: String) {
        if (characterId == id) return
        if (characterId != null) {
            request = null
            result = null
            history = emptyList()
            rollOpen = false
            editing = false
            dialog = null
        }
        characterId = id
    }

    /** Switching between play and edit closes any open popup. */
    fun updateEditing(value: Boolean) {
        if (value == editing) return
        editing = value
        dialog = null
    }

    fun openDialog(next: SheetDialog) {
        dialog = next
    }

    fun closeDialog() {
        dialog = null
    }

    /**
     * Starts a fresh setup for [subject]. [dialog] is false for a tap in the docked-tray layout, so a
     * later shrink of the window does not pop up a dialog nobody asked for.
     */
    fun open(subject: SheetRollSubject, dialog: Boolean = true) {
        request = SheetRollRequest(subject)
        result = null
        rollOpen = dialog
    }

    fun dismiss() {
        rollOpen = false
    }

    /**
     * Any change to the setup drops the shown result, so it is never read against a different setup —
     * except the target: the old desktop dialog let you roll first and type the defence afterwards, and the
     * outcome (and the superiority-die offer) is recomputed against the current target.
     */
    fun updateRequest(next: SheetRollRequest) {
        val current = request
        if (next != current && (current == null || next != current.copy(target = next.target))) result = null
        request = next
    }

    fun roll(host: DublSheetHost, rollDie: () -> Int = ::sheetRollD6) {
        val current = request ?: return
        val rolled = performSheetRoll(host, current, rollDie) ?: return
        result = rolled
        val title = sheetRollOptions(host, current).title
        history = (listOf(FuryRollHistoryEntry(title, rolled.total)) + history).take(SHEET_HISTORY_LIMIT)
    }

    /** Rolls the follow-up die; the newest history row takes the new total. */
    fun followUp(rollDie: () -> Int = ::sheetRollD6) {
        val current = result ?: return
        val next = followUpSheetRoll(current, rollDie)
        result = next
        history.firstOrNull()?.let { head -> history = listOf(head.copy(total = next.total)) + history.drop(1) }
    }
}
