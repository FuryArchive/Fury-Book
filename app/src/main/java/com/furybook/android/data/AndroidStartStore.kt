package com.furybook.android.data

import android.content.Context
import com.furybook.ui.account.FuryStartMode
import com.furybook.ui.account.FuryStartStore

/** The start mode in the app's «fury_start» preferences. */
class AndroidStartStore(context: Context) : FuryStartStore {
    private val prefs = context.applicationContext.getSharedPreferences("fury_start", Context.MODE_PRIVATE)

    override fun load(): FuryStartMode = FuryStartMode.fromKey(prefs.getString(KEY, null))

    override fun save(mode: FuryStartMode) {
        prefs.edit().putString(KEY, mode.key).apply()
    }

    private companion object {
        const val KEY = "mode"
    }
}
