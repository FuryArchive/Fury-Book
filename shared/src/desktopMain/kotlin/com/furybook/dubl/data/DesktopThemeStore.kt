package com.furybook.dubl.data

import com.furybook.ui.theme.FuryThemeId
import com.furybook.ui.theme.FuryThemeStore
import java.nio.file.Files
import java.nio.file.Path

/** The theme key in a one-line text file next to the character data; anything unreadable means the default. */
class DesktopThemeStore(
    private val file: Path = DesktopCharacterStore.defaultDataDirectory().resolve("ui-theme.txt"),
) : FuryThemeStore {
    override fun load(): FuryThemeId =
        runCatching { FuryThemeId.fromKey(Files.readString(file)) }.getOrDefault(FuryThemeId.FURY)

    override fun save(id: FuryThemeId) {
        runCatching {
            file.parent?.let(Files::createDirectories)
            Files.writeString(file, id.key)
        }
    }
}
