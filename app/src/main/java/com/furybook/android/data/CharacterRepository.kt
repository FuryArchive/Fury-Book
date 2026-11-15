package com.furybook.android.data

import com.furybook.dubl.data.CachedSnapshotCodec
import com.furybook.dubl.data.CharacterStore
import com.furybook.dubl.data.SnapshotCodec
import android.content.Context
import com.furybook.dubl.model.AppSnapshot
import com.furybook.dubl.model.DublCharacter
import java.util.UUID

class CharacterRepository(context: Context) : CharacterStore {
    private val preferences = context.getSharedPreferences("dubl_native_state", Context.MODE_PRIVATE)

    // All access to the single-owner codec is serialized; there is no background
    // queue that could overwrite a newer cloud/local save or delay durability.
    private val codec = CachedSnapshotCodec(::newId)

    @Synchronized
    override fun load(): AppSnapshot {
        val raw = preferences.getString(KEY_STATE, null) ?: return SnapshotCodec.fresh(::newId)
        return runCatching { codec.decode(raw) }
            .getOrElse { SnapshotCodec.fresh(::newId) }
    }

    @Synchronized
    override fun save(snapshot: AppSnapshot) {
        val raw = codec.encode(snapshot)
        if (preferences.getString(KEY_STATE, null) != raw) {
            preferences.edit().putString(KEY_STATE, raw).apply()
        }
    }

    fun newCharacter(): DublCharacter = DublCharacter(id = newId(), name = "Новый персонаж")

    private fun newId(): String = UUID.randomUUID().toString()

    private companion object {
        const val KEY_STATE = "state_v1"
    }
}
