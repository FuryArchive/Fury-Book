package com.furybook.android.data

import android.content.Context
import com.furybook.dubl.master.MasterData
import com.furybook.dubl.master.MasterDataCodec
import com.furybook.dubl.master.MasterStore
import java.io.File

/** Мастер's data in the app's `files/master.json`; written to a temp file and renamed into place. */
class AndroidMasterStore(context: Context) : MasterStore {
    private val file = File(context.applicationContext.filesDir, "master.json")

    override fun load(): MasterData = MasterDataCodec.decode(runCatching { file.readText(Charsets.UTF_8) }.getOrNull())

    override fun save(data: MasterData) {
        runCatching {
            val temp = File(file.parentFile, "master.json.tmp")
            temp.writeText(MasterDataCodec.encode(data), Charsets.UTF_8)
            if (!temp.renameTo(file)) {
                file.writeText(MasterDataCodec.encode(data), Charsets.UTF_8)
                temp.delete()
            }
        }
    }
}
