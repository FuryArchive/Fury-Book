package com.furybook.web

import com.furybook.ui.theme.FuryThemeId
import com.furybook.ui.theme.FuryThemeStore
import kotlinx.browser.localStorage

/** The theme key in the browser's localStorage; unreadable storage means the default theme. */
class WebThemeStore : FuryThemeStore {
    override fun load(): FuryThemeId =
        runCatching { FuryThemeId.fromKey(localStorage.getItem(KEY)) }.getOrDefault(FuryThemeId.FURY)

    override fun save(id: FuryThemeId) {
        runCatching { localStorage.setItem(KEY, id.key) }
    }

    private companion object {
        const val KEY = "furybook.theme"
    }
}
