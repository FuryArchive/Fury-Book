package com.furybook.dubl.actions

import com.furybook.dubl.acquisition.*

data class GameplayMigrationRequest(
    val operationId: String,
    val characterId: String,
    val expectedStorageRevision: Long,
    val expectedSourceCanonical: String,
)

/** Exact prior bytes remain independently restorable, not just a success flag or hash. */
data class GameplayMigrationProof(val operationId: String, val sourceRaw: String, val sourceStorageRevision: Long)

sealed interface GameplayMigrationResult {
    data class Migrated(val backupPath: String) : GameplayMigrationResult
    data class AlreadyMigrated(val backupPath: String) : GameplayMigrationResult
    data class Blocked(val reason: String) : GameplayMigrationResult
    data class StorageFailure(val reason: String) : GameplayMigrationResult
}

/** Preparation only: the platform must durably save sourceRaw before replacing the head. */
fun prepareGameplayMigration(source: StoredCharacter, sourceRaw: String, request: GameplayMigrationRequest): StoredCharacter {
    require(request.operationId.isNotBlank() && request.characterId == source.character.id) { "Invalid migration identity" }
    require(source.formatVersion == 1 && source.gameplay == null) { "Only canonical format 1 can be migrated" }
    require(source.storageRevision == request.expectedStorageRevision && source.storageRevision < Long.MAX_VALUE) { "Stale migration review" }
    require(canonicalJson(sourceRaw) == request.expectedSourceCanonical && encodeEnvelope(source) == request.expectedSourceCanonical) {
        "Character changed since migration review"
    }
    require(source.outbox.isEmpty() && source.syncMetadata == SyncMetadata(0) && source.syncProofs.isEmpty()) {
        "Only isolated local characters can migrate; cloud history must not be reset"
    }
    require(source.receipts.none { it.operationId == request.operationId }) { "Migration ID belongs to an existing operation" }
    val next = source.copy(formatVersion = 2, storageRevision = source.storageRevision + 1,
        gameplay = GameplayJournal(lastCommittedStorageRevision = source.storageRevision + 1,
            migration = GameplayMigrationProof(request.operationId, sourceRaw, source.storageRevision)))
    encodeEnvelope(next)
    return next
}
