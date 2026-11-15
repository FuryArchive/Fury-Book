package com.furybook.dubl.data

import com.furybook.core.json.jsonString
import com.furybook.core.json.jsonStringify
import com.furybook.dubl.model.AppSnapshot
import com.furybook.dubl.model.DublCharacter

/**
 * Single-owner serialization cache. Callers must serialize access (Android's
 * repository does). The canonical codec still defines every field and migration.
 * Only the latest roster is retained; removed characters do not accumulate.
 * This does not introduce deferred saves or change the persisted schema.
 */
class CachedSnapshotCodec(private val idFactory: () -> String) {
    private data class CharacterEncoding(
        val source: DublCharacter,
        val json: String,
        val decoded: DublCharacter,
    )

    private var characters = emptyMap<String, CharacterEncoding>()
    private var encodedInput: AppSnapshot? = null
    private var lastRaw: String? = null
    private var lastDecoded: AppSnapshot? = null

    fun decode(raw: String): AppSnapshot {
        if (raw == lastRaw) lastDecoded?.let { return it }
        val decoded = SnapshotCodec.decode(raw, idFactory)
        // An external write must invalidate the whole-snapshot fast path.
        encodedInput = null
        lastRaw = raw
        lastDecoded = decoded
        return decoded
    }

    fun encode(snapshot: AppSnapshot): String {
        if (snapshot == encodedInput) lastRaw?.let { return it }
        val next = linkedMapOf<String, CharacterEncoding>()
        val encoded = snapshot.characters.map { character ->
            val previous = characters[character.id]
            val entry = if (previous != null && previous.source == character) previous else {
                val json = SnapshotCodec.encodeCharacter(character)
                CharacterEncoding(
                    source = character,
                    json = jsonStringify(json),
                    decoded = SnapshotCodec.decodeCharacter(json, SnapshotCodec.SCHEMA, idFactory),
                )
            }
            next[character.id] = entry
            entry
        }
        // Exactly the same field order, escaping and separators as SnapshotCodec.encode.
        val raw = buildString {
            append("{\"schema\":").append(SnapshotCodec.SCHEMA)
            append(",\"activeCharacterId\":").append(jsonStringify(jsonString(snapshot.activeCharacterId)))
            append(",\"characters\":[")
            encoded.forEachIndexed { index, character ->
                if (index > 0) append(',')
                append(character.json)
            }
            append("]}")
        }
        val decodedCharacters = encoded.map { it.decoded }
        val decoded = if (decodedCharacters.isEmpty()) SnapshotCodec.fresh(idFactory) else {
            val activeId = decodedCharacters.firstOrNull { it.id == snapshot.activeCharacterId }?.id
                ?: decodedCharacters.first().id
            AppSnapshot(decodedCharacters, activeId)
        }
        // Publish cache updates only after the complete encoding succeeds.
        characters = next
        encodedInput = snapshot
        lastRaw = raw
        lastDecoded = decoded
        return raw
    }
}
