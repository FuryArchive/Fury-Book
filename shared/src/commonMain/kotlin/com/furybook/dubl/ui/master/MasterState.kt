package com.furybook.dubl.ui.master

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.furybook.dubl.master.LookupType
import com.furybook.dubl.master.MasterData
import com.furybook.dubl.master.MasterStore

enum class MasterTab(val label: String) { FIGHT("Бой"), ENEMIES("Враги"), NOTES("Заметки"), LOOKUP("Справочник"), PARTY("Партия") }

/**
 * Мастер's working state: the data (loaded once, saved on every change), the open tab and searches, and one
 * undo step for the toast's «Вернуть» after a delete, a removal or «Новый бой».
 */
class MasterState(private val store: MasterStore) {
    var data: MasterData by mutableStateOf(store.load())
        private set
    var tab: MasterTab by mutableStateOf(MasterTab.FIGHT)
    var noteQuery: String by mutableStateOf("")
    var lookupQuery: String by mutableStateOf("")
    var lookupType: LookupType? by mutableStateOf(null)
    var lookupPick: String? by mutableStateOf(null)
    var partyCampaignId: String? by mutableStateOf(null)

    private var undoData: MasterData? = null
    var undoLabel: String? by mutableStateOf(null)
        private set

    /** Applies [change] and saves. With [undoable] set, the previous data can come back once through [undo]. */
    fun update(change: (MasterData) -> MasterData, undoable: String? = null) {
        val before = data
        val next = change(before)
        if (next == before) return
        undoData = if (undoable != null) before else null
        undoLabel = undoable
        data = next
        store.save(next)
    }

    fun undo(): Boolean {
        val previous = undoData ?: return false
        undoData = null
        undoLabel = null
        data = previous
        store.save(previous)
        return true
    }
}
