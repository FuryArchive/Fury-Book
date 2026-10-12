package com.furybook.dubl.acquisition

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Atomic test store; serialized bytes prevent caller mutation after commit. No disk durability. */
class InMemoryAcquisitionStore(initial: Map<String, String> = emptyMap()) : AcquisitionStore {
    private val records = initial.toMutableMap()
    private val mutex = Mutex()
    override suspend fun load(characterId: String): LoadResult = mutex.withLock { read(characterId) }
    private fun read(characterId: String): LoadResult {
        val raw = records[characterId] ?: return LoadResult.Missing(characterId)
        val loaded = decodeEnvelope(raw)
        return if (loaded is LoadResult.Loaded && loaded.state.character.id != characterId)
            LoadResult.Corrupt(raw, "Storage key and character scope differ") else loaded
    }
    override suspend fun compareAndSet(characterId: String, expectedStorageRevision: Long, next: StoredCharacter): CommitResult = mutex.withLock {
        val previous = read(characterId)
        if (previous !is LoadResult.Missing && previous !is LoadResult.Loaded)
            return@withLock CommitResult.StorageFailure("Existing bytes are not writable")
        val current = (previous as? LoadResult.Loaded)?.state
        if ((current?.storageRevision ?: -1) != expectedStorageRevision) return@withLock CommitResult.Conflict
        if (characterId != next.character.id || expectedStorageRevision < -1 || expectedStorageRevision == Long.MAX_VALUE ||
            next.storageRevision != expectedStorageRevision + 1) return@withLock CommitResult.StorageFailure("Invalid identity or storage revision")
        try {
            val encoded = encodeEnvelope(next)
            records[characterId] = encoded
            CommitResult.Committed
        } catch (failure: IllegalArgumentException) { CommitResult.StorageFailure(failure.message ?: "Invalid envelope") }
        catch (failure: ArithmeticException) { CommitResult.StorageFailure(failure.message ?: "Invalid amount") }
    }
}
