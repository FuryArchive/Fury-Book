package com.furybook.dubl.data

import com.furybook.dubl.ui.characters.FuryArchiveStore
import com.furybook.dubl.ui.characters.decodeArchiveIds
import com.furybook.dubl.ui.characters.encodeArchiveIds
import java.nio.file.Files
import java.nio.file.Path

/** «Архив» ids in `archive.json` next to the character data. */
class DesktopArchiveStore(
    private val file: Path = DesktopCharacterStore.defaultDataDirectory().resolve("archive.json"),
) : FuryArchiveStore {
    override fun load(): Set<String> = decodeArchiveIds(runCatching { Files.readString(file) }.getOrNull())

    override fun save(ids: Set<String>) {
        runCatching {
            file.parent?.let(Files::createDirectories)
            Files.writeString(file, encodeArchiveIds(ids))
        }
    }
}
