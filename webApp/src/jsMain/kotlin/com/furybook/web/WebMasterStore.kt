package com.furybook.web

import com.furybook.dubl.master.MasterData
import com.furybook.dubl.master.MasterDataCodec
import com.furybook.dubl.master.MasterStore
import kotlinx.browser.localStorage

/** Мастер's data under `fury.master` in the browser's localStorage, signed in or not. */
class WebMasterStore : MasterStore {
    override fun load(): MasterData = MasterDataCodec.decode(runCatching { localStorage.getItem(KEY) }.getOrNull())

    override fun save(data: MasterData) {
        BrowserGuestStorage.write(KEY, MasterDataCodec.encode(data))
    }

    private companion object {
        const val KEY = "fury.master"
    }
}
