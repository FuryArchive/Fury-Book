package com.furybook.android.data

import android.system.Os
import android.system.OsConstants
import java.nio.channels.FileChannel
import java.nio.file.Path
import java.nio.file.StandardOpenOption

/** Checked platform boundary. No AtomicFile/log-only or non-atomic fallback acknowledgment. */
interface AndroidAcquisitionFileOps {
    fun atomicReplace(temp: Path, head: Path)
    fun forceFile(path: Path)
    fun forceDirectory(path: Path)
}

internal object AndroidOsAcquisitionFileOps : AndroidAcquisitionFileOps {
    override fun atomicReplace(temp: Path, head: Path) = Os.rename(temp.toString(), head.toString())
    override fun forceFile(path: Path) {
        FileChannel.open(path, StandardOpenOption.WRITE).use { it.force(true) }
    }
    override fun forceDirectory(path: Path) {
        val descriptor = Os.open(path.toString(), OsConstants.O_RDONLY, 0)
        try { check(OsConstants.S_ISDIR(Os.fstat(descriptor).st_mode)) { "Expected directory descriptor" }; Os.fsync(descriptor) } finally { Os.close(descriptor) }
    }
}
