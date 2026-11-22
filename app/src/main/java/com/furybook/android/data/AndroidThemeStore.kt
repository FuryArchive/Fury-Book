package com.furybook.android.data

import android.content.Context
import com.furybook.ui.theme.FuryThemeId
import com.furybook.ui.theme.FuryThemeStore

/** The theme key in the app's «furybook_ui» preferences. */
class AndroidThemeStore(context: Context) : FuryThemeStore {
    private val prefs = context.applicationContext.getSharedPreferences("furybook_ui", Context.MODE_PRIVATE)

    override fun load(): FuryThemeId = FuryThemeId.fromKey(prefs.getString(KEY, null))

    override fun save(id: FuryThemeId) {
        prefs.edit().putString(KEY, id.key).apply()
    }

    private companion object {
        const val KEY = "theme"
    }
}
