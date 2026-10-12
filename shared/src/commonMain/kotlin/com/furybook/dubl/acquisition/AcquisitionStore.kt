package com.furybook.dubl.acquisition

sealed interface LoadResult {
    data class Loaded(val state: StoredCharacter) : LoadResult
    data class Missing(val characterId: String) : LoadResult
    data class MigrationRequired(val raw: String) : LoadResult
    /** Both branches remain available for deliberate import resolution; never a legacy overwrite. */
    data class LegacyImportConflict(val canonicalRaw: String, val legacyRaw: String?, val legacyExtrasRaw: String?, val reason: String) : LoadResult
    data class Unsupported(val raw: String, val reason: String) : LoadResult
    data class Corrupt(val raw: String, val reason: String) : LoadResult
}
sealed interface CommitResult {
    data object Committed : CommitResult
    data object Conflict : CommitResult
    data class StorageFailure(val reason: String) : CommitResult
}

interface AcquisitionStore {
    suspend fun load(characterId: String): LoadResult
    /**
     * Atomically persist the entire envelope before returning Committed. Never replace corrupt or
     * unsupported bytes. expected=-1 requires absence and next.storageRevision=0; otherwise both
     * the existing revision and exactly one increment must match. Metadata-only writes preserve
     * gameplay revision. Platform implementations must serialize competing writers durably.
     */
    suspend fun compareAndSet(characterId: String, expectedStorageRevision: Long, next: StoredCharacter): CommitResult
}
