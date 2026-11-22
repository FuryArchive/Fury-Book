package com.furybook.web

import com.furybook.dubl.ui.characters.FuryArchiveStore
import com.furybook.dubl.ui.characters.decodeArchiveIds
import com.furybook.dubl.ui.characters.encodeArchiveIds
import kotlinx.browser.localStorage

/** «Архив» ids under `fury.archive` in the browser's localStorage. */
class WebArchiveStore : FuryArchiveStore {
    override fun load(): Set<String> = decodeArchiveIds(runCatching { localStorage.getItem(KEY) }.getOrNull())

    override fun save(ids: Set<String>) {
        runCatching { localStorage.setItem(KEY, encodeArchiveIds(ids)) }
    }

    private companion object {
        const val KEY = "fury.archive"
    }
}
