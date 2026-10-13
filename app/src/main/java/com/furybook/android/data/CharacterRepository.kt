package com.furybook.android.data

import android.content.Context
import com.furybook.dubl.data.CharacterStore
import com.furybook.dubl.data.SnapshotCodec
import com.furybook.dubl.model.AppSnapshot
import com.furybook.dubl.model.DublCharacter
import java.util.UUID

/** Legacy compatibility only. Canonical acquisition data never enters state_v1. */
class CharacterRepository(
    context: Context,
    internal val source: AndroidLegacyAcquisitionSource = AndroidLegacyAcquisitionSource(context.applicationContext.dataDir.toPath().resolve("shared_prefs")),
) : CharacterStore {
    private val appContext = context.applicationContext
    private val preferences by lazy { appContext.getSharedPreferences("dubl_native_state", Context.MODE_PRIVATE) }
    @Volatile private var cached: AppSnapshot? = null

    /** Call from IO before constructing a display application; malformed/existing data never fresh-falls-back. */
    internal fun initialize(captured: AndroidLegacyCapture = source.capture(), canonicalFallback: AppSnapshot? = null): AppSnapshot {
        val snapshot = captured.snapshotRaw?.let { SnapshotCodec.decode(it, ::newId) } ?: canonicalFallback ?: SnapshotCodec.fresh(::newId)
        check(typedPreferences(preferences.all) == captured.stateEntries) { "Legacy preference memory/disk disagreement" }
        cached = snapshot
        return snapshot
    }
    override fun load(): AppSnapshot = checkNotNull(cached) { "Legacy repository must be bootstrapped on IO before use" }
    override fun save(snapshot: AppSnapshot) = source.gate.legacyWrite {
        // This remains the isolated old writer and supplies no acquisition durability acknowledgment.
        preferences.edit().putString(KEY_STATE, SnapshotCodec.encode(snapshot)).apply()
        cached = snapshot
    }
    internal fun memoryValues(): Map<String, *> = preferences.all
    fun newCharacter(): DublCharacter = DublCharacter(id = newId(), name = "Новый персонаж")
    private fun newId(): String = UUID.randomUUID().toString()
    private companion object { const val KEY_STATE = "state_v1" }
}
