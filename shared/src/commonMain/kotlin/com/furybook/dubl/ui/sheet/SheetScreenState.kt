package com.furybook.dubl.ui.sheet

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.furybook.dubl.model.RollResult
import com.furybook.ui.components.FuryRollHistoryEntry

private const val SHEET_HISTORY_LIMIT = 5

/**
 * Roll state owned by [SheetScreen]: the current setup, its result, the last few totals and whether
 * the roll dialog is open (phone and wide layouts; the docked tray ignores [rollOpen]).
 * Keep one instance per character: the screen remembers it keyed on the character id.
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

    /** Starts a fresh setup for [subject] and opens the roll dialog. */
    fun open(subject: SheetRollSubject) {
        request = SheetRollRequest(subject)
        result = null
        rollOpen = true
    }

    fun dismiss() {
        rollOpen = false
    }

    /** Any change to the setup drops the shown result, so it is never read against a different setup. */
    fun updateRequest(next: SheetRollRequest) {
        if (next != request) result = null
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
