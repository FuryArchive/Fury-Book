package com.furybook.dubl.data

import com.furybook.dubl.model.CharacterSheetExtras
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

class DesktopCharacterExtrasStore(
    private val file: Path = DesktopCharacterStore.defaultDataDirectory().resolve("sheet-extras.json"),
) : CharacterExtrasStore {
    private var document: LegacyExtrasDocument? = null

    override fun load(characterId: String): CharacterSheetExtras = ensureLoaded().load(characterId)

    override fun save(characterId: String, extras: CharacterSheetExtras) {
        val next = ensureLoaded().withExtras(characterId, extras)
        persist(next)
        document = next
    }

    override fun delete(characterId: String) {
        val next = ensureLoaded().withoutCharacter(characterId)
        persist(next)
        document = next
    }

    private fun ensureLoaded(): LegacyExtrasDocument = document ?: (
        if (Files.notExists(file)) LegacyExtrasDocument.empty()
        else LegacyExtrasDocument.decode(Files.readString(file, StandardCharsets.UTF_8))
    ).also { document = it }

    private fun persist(next: LegacyExtrasDocument) {
        Files.createDirectories(file.parent)
        // Keep exact original bytes as well as retaining opaque values in every updated document.
        val backup = file.resolveSibling("${file.fileName}.pre-compatibility.bak")
        if (Files.exists(file) && Files.notExists(backup)) Files.copy(file, backup)
        val temp = file.resolveSibling("${file.fileName}.tmp")
        Files.writeString(temp, next.encode(), StandardCharsets.UTF_8)
        runCatching {
            Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        }.getOrElse {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    companion object {
        internal fun decodeLegacy(raw: String): Map<String, CharacterSheetExtras> = try {
            LegacyExtrasDocument.decode(raw).forMigration()
        } catch (failure: UnsupportedLegacyExtrasVersion) {
            throw UnsupportedDesktopData(failure.message ?: "Unsupported sheet-extras version")
        }
    }
}
