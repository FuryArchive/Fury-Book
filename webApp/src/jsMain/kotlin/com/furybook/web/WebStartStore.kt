package com.furybook.web

import com.furybook.ui.account.FuryStartMode
import com.furybook.ui.account.FuryStartStore
import kotlinx.browser.localStorage

/** The start mode under `fury.start` in the browser's localStorage; unreadable storage means «not chosen». */
class WebStartStore : FuryStartStore {
    override fun load(): FuryStartMode =
        runCatching { FuryStartMode.fromKey(localStorage.getItem(KEY)) }.getOrDefault(FuryStartMode.NONE)

    override fun save(mode: FuryStartMode) {
        runCatching { localStorage.setItem(KEY, mode.key) }
    }

    private companion object {
        const val KEY = "fury.start"
    }
}
