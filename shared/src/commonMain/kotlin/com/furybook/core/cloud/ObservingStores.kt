package com.furybook.core.cloud

import com.furybook.dubl.data.CharacterExtrasStore
import com.furybook.dubl.data.CharacterStore
import com.furybook.dubl.model.AppSnapshot
import com.furybook.dubl.model.CharacterSheetExtras

class ObservingCharacterStore(
    private val delegate: CharacterStore,
) : CharacterStore {
    var onSaved: ((before: AppSnapshot, after: AppSnapshot) -> Unit)? = null

    override fun load(): AppSnapshot = delegate.load()

    override fun save(snapshot: AppSnapshot) {
        val before = delegate.load()
        delegate.save(snapshot)
        if (before != snapshot) onSaved?.invoke(before, snapshot)
    }
}

class ObservingCharacterExtrasStore(
    private val delegate: CharacterExtrasStore,
) : CharacterExtrasStore {
    var onSaved: ((characterId: String) -> Unit)? = null
    var onDeleted: ((characterId: String) -> Unit)? = null

    override fun load(characterId: String): CharacterSheetExtras = delegate.load(characterId)

    override fun save(characterId: String, extras: CharacterSheetExtras) {
        val before = delegate.load(characterId)
        delegate.save(characterId, extras)
        if (before != extras) onSaved?.invoke(characterId)
    }

    override fun delete(characterId: String) {
        delegate.delete(characterId)
        onDeleted?.invoke(characterId)
    }
}
