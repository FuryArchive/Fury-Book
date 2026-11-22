package com.furybook.dubl.data

import com.furybook.ui.account.FuryStartMode
import com.furybook.ui.account.FuryStartStore
import java.nio.file.Files
import java.nio.file.Path

/** The start mode in a one-line text file next to the character data; anything unreadable means «not chosen». */
class DesktopStartStore(
    private val file: Path = DesktopCharacterStore.defaultDataDirectory().resolve("start-mode.txt"),
) : FuryStartStore {
    override fun load(): FuryStartMode =
        runCatching { FuryStartMode.fromKey(Files.readString(file)) }.getOrDefault(FuryStartMode.NONE)

    override fun save(mode: FuryStartMode) {
        runCatching {
            file.parent?.let(Files::createDirectories)
            Files.writeString(file, mode.key)
        }
    }
}
