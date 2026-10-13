package com.furybook.dubl.application

import com.furybook.dubl.data.CharacterTransferCodec
import com.furybook.dubl.data.CharacterTransferDecodeResult
import com.furybook.dubl.data.CharacterTransferRejectReason
import com.furybook.dubl.state.CharacterExtrasSession
import com.furybook.dubl.state.CharacterSession

sealed interface CharacterTransferImportResult {
    data class Imported(
        val characterId: String,
        val name: String,
    ) : CharacterTransferImportResult

    data class Rejected(
        val reason: CharacterTransferRejectReason,
    ) : CharacterTransferImportResult
}

class CharacterTransferApplication internal constructor(
    private val characters: CharacterSession,
    private val extras: CharacterExtrasSession,
    private val beforeLegacyTransfer: () -> Unit = {},
) {
    fun exportActive(): String {
        beforeLegacyTransfer()
        return CharacterTransferCodec.encode(
        character = characters.active,
        extras = extras.load(characters.active.id),
    )
    }

    fun importCharacter(raw: String): CharacterTransferImportResult {
        beforeLegacyTransfer()
        val decoded = CharacterTransferCodec.decode(raw, characters::newIdForTransfer)
        if (decoded is CharacterTransferDecodeResult.Failure) {
            return CharacterTransferImportResult.Rejected(decoded.reason)
        }
        decoded as CharacterTransferDecodeResult.Success

        val importedId = characters.importTransferredCharacter(decoded.payload.character)
        extras.replace(importedId, decoded.payload.extras)
        return CharacterTransferImportResult.Imported(
            characterId = importedId,
            name = characters.active.name,
        )
    }
}
