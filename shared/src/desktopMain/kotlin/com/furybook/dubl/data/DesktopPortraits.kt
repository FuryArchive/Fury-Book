package com.furybook.dubl.data

import java.nio.file.Files
import java.nio.file.Path

/** Portrait images the desktop copies into `portraits/` as «<characterId>-<uuid>.<ext>». */
object DesktopPortraits {
    fun directory(): Path = DesktopCharacterStore.defaultDataDirectory().resolve("portraits")

    /** Removes the portraits of [characterIds] (the account's characters leaving the device on «Выйти»). */
    fun deleteFor(characterIds: Set<String>, dir: Path = directory()) {
        if (characterIds.isEmpty() || !Files.isDirectory(dir)) return
        runCatching {
            Files.list(dir).use { files ->
                files.filter { file -> characterIds.any { id -> file.fileName.toString().startsWith("$id-") } }
                    .forEach { runCatching { Files.deleteIfExists(it) } }
            }
        }
    }
}
