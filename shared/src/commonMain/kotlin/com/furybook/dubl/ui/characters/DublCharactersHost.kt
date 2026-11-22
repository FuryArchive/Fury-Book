package com.furybook.dubl.ui.characters

import com.furybook.dubl.application.CharacterTransferImportResult
import com.furybook.dubl.model.DublCharacter

/** Which characters the player moved to «Архив» on this device. Local only; never synced. */
interface FuryArchiveStore {
    fun load(): Set<String>
    fun save(ids: Set<String>)
}

class InMemoryFuryArchiveStore(private var ids: Set<String> = emptySet()) : FuryArchiveStore {
    override fun load(): Set<String> = ids
    override fun save(ids: Set<String>) { this.ids = ids }
}

/** What the Персонажи screen needs from a platform: the roster and the actions on it. */
interface DublCharactersHost {
    val characters: List<DublCharacter>
    val activeId: String
    fun portraitOf(id: String): String?
    fun select(id: String)
    fun create()
    fun delete(id: String)
    fun duplicate(id: String): String?
    fun exportCharacter(id: String): String
    fun importCharacter(raw: String): CharacterTransferImportResult
    val archive: FuryArchiveStore
}
