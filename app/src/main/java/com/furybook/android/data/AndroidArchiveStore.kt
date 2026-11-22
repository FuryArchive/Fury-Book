package com.furybook.android.data

import android.content.Context
import com.furybook.dubl.ui.characters.FuryArchiveStore
import com.furybook.dubl.ui.characters.decodeArchiveIds
import com.furybook.dubl.ui.characters.encodeArchiveIds

/** «Архив» ids in the app's «fury_archive» preferences. */
class AndroidArchiveStore(context: Context) : FuryArchiveStore {
    private val prefs = context.applicationContext.getSharedPreferences("fury_archive", Context.MODE_PRIVATE)

    override fun load(): Set<String> = decodeArchiveIds(prefs.getString(KEY, null))

    override fun save(ids: Set<String>) {
        prefs.edit().putString(KEY, encodeArchiveIds(ids)).apply()
    }

    private companion object {
        const val KEY = "ids"
    }
}
