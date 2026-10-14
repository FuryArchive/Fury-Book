package com.furybook.dubl.actions

import com.furybook.dubl.acquisition.*
import java.io.File

/** Compile and run against the pre-format-2 shared jar, not the new decoder. */
fun main(args: Array<String>) {
    val old = File(args[0], "format1.json").readText()
    val future = File(args[0], "format2.json").readText()
    val oldRead = decodeEnvelope(old) as LoadResult.Loaded
    check(encodeEnvelope(oldRead.state) == old)
    val blocked = decodeEnvelope(future) as LoadResult.Unsupported
    check(blocked.raw == future)
    println("OLD_READER_PRESERVES_UNSUPPORTED_FORMAT_2")
}
