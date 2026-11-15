package com.furybook.dubl.data

import com.furybook.dubl.model.*
import kotlin.test.*

class CachedSnapshotCodecTest {
    private val first = DublCharacter("one", name = "Тест \"漢字\"\n\\\t").normalized()
    private val second = DublCharacter("two", name = "Другой", experience = 4000).normalized()
    private fun snapshot() = AppSnapshot(listOf(first, second), first.id)

    @Test fun byteForByteCanonicalParityAndCachedReads() {
        val codec = CachedSnapshotCodec { "fresh" }
        val snapshot = snapshot()
        val raw = codec.encode(snapshot)
        assertEquals(SnapshotCodec.encode(snapshot), raw)
        assertEquals(SnapshotCodec.decode(raw) { "fresh" }, codec.decode(raw))
        assertSame(codec.decode(raw), codec.decode(raw))
        assertSame(raw, codec.encode(snapshot.copy()))
    }

    @Test fun changingOneCharacterRetainsUnchangedDecodedCharacter() {
        val codec = CachedSnapshotCodec { "fresh" }
        val source = snapshot()
        val previous = codec.decode(codec.encode(source))
        val updated = source.copy(characters = listOf(first.copy(name = "new"), second))
        val raw = codec.encode(updated)
        val decoded = codec.decode(raw)
        assertEquals(SnapshotCodec.encode(updated), raw)
        assertSame(previous.characters[1], decoded.characters[1])
        assertNotSame(previous.characters[0], decoded.characters[0])
    }

    @Test fun reorderDeleteUndoAndActiveSelectionRemainCanonical() {
        val codec = CachedSnapshotCodec { "fresh" }
        val source = snapshot()
        val revisions = listOf(source, AppSnapshot(listOf(second, first), second.id), AppSnapshot(listOf(first), "missing"), source)
        for (revision in revisions) {
            val raw = codec.encode(revision)
            assertEquals(SnapshotCodec.encode(revision), raw)
            assertEquals(SnapshotCodec.decode(raw) { "fresh" }, codec.decode(raw))
        }
    }

    @Test fun externalPayloadInvalidatesEncodeAndDecodeFastPaths() {
        val codec = CachedSnapshotCodec { "fresh" }
        val original = snapshot()
        codec.encode(original)
        val cloud = original.copy(characters = listOf(first.copy(name = "cloud")))
        assertEquals("cloud", codec.decode(SnapshotCodec.encode(cloud)).activeCharacter.name)
        val restored = codec.encode(original)
        assertEquals(SnapshotCodec.encode(original), restored)
        assertEquals(original, codec.decode(restored))
    }

    @Test fun normalizationIsIdenticalToDiskReload() {
        val codec = CachedSnapshotCodec { "fresh" }
        val dirty = first.copy(size = 100, experience = -20, skills = mapOf("x" to CharacterSkill("x", name = "test", rank = 99)))
        val source = AppSnapshot(listOf(dirty), "missing")
        val raw = codec.encode(source)
        assertEquals(SnapshotCodec.decode(raw) { "fresh" }, codec.decode(raw))
    }

    @Test fun emptyRosterUsesCanonicalFreshCharacter() {
        val codec = CachedSnapshotCodec { "fresh" }
        val source = AppSnapshot(emptyList(), "none")
        val raw = codec.encode(source)
        assertEquals(SnapshotCodec.encode(source), raw)
        assertEquals(SnapshotCodec.decode(raw) { "fresh" }, codec.decode(raw))
    }

    @Test fun failedExternalDecodeDoesNotPoisonPreviousState() {
        val codec = CachedSnapshotCodec { "fresh" }
        val raw = codec.encode(snapshot())
        val previous = codec.decode(raw)
        assertFails { codec.decode("{broken") }
        assertSame(previous, codec.decode(raw))
    }
}
