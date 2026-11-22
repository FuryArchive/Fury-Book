package com.furybook.dubl.data

import com.furybook.dubl.master.MasterData
import com.furybook.dubl.master.MasterDataCodec
import com.furybook.dubl.master.MasterStore
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/** Мастер's data in `master.json` next to the characters; written to a temp file and moved into place. */
class DesktopMasterStore(
    private val file: Path = DesktopCharacterStore.defaultDataDirectory().resolve("master.json"),
) : MasterStore {
    override fun load(): MasterData = MasterDataCodec.decode(runCatching { Files.readString(file, StandardCharsets.UTF_8) }.getOrNull())

    override fun save(data: MasterData) {
        runCatching {
            file.parent?.let(Files::createDirectories)
            val temp = file.resolveSibling("${file.fileName}.tmp")
            Files.writeString(temp, MasterDataCodec.encode(data), StandardCharsets.UTF_8)
            try {
                Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING)
            }
        }
    }
}
