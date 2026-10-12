package com.furybook.dubl.acquisition

import com.furybook.core.cloud.FuryCloudTransport
import com.furybook.core.json.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.withLock

/** Already committed state. Transport must never execute requestCanonical/game commands. */
data class AcquisitionPush(val characterId: String, val operationId: String, val parentServerRevision: Long, val payloadCanonical: String)
enum class LocalOrServer { Local, Server }
/** Separate endpoint/DTO: created only by explicit resolveConflict after a durable archive. */
data class AcquisitionResolution(val characterId: String, val operationId: String, val expectedServerRevision: Long,
    val choice: LocalOrServer, val archiveId: String, val selectedCanonical: String)
sealed interface AcquisitionPushResult {
    data class Acknowledged(val operationId: String, val payloadCanonical: String, val serverRevision: Long) : AcquisitionPushResult
    data class Conflict(val serverRevision: Long, val serverCanonical: String) : AcquisitionPushResult
    data class IdentityConflict(val reason: String) : AcquisitionPushResult
}
interface AcquisitionSyncTransport {
    suspend fun push(request: AcquisitionPush): AcquisitionPushResult
    suspend fun resolve(request: AcquisitionResolution): AcquisitionPushResult
}
data class ArchivedAcquisitionConflict(val archiveId: String, val characterId: String, val serverRevision: Long,
    val localCanonical: String, val serverCanonical: String)
interface AcquisitionConflictArchive {
    /** Committed means BOTH immutable branches are durably preserved; same ID must be idempotent. */
    suspend fun preserve(conflict: ArchivedAcquisitionConflict): CommitResult
}
sealed interface SyncEnvelopeResult {
    data class Synced(val serverRevision: Long, val pendingCount: Int) : SyncEnvelopeResult
    data class Conflict(val serverRevision: Long, val localCanonical: String, val serverCanonical: String) : SyncEnvelopeResult
    data class Retryable(val reason: String) : SyncEnvelopeResult
    data class StorageFailure(val reason: String) : SyncEnvelopeResult
    data class IdentityConflict(val reason: String) : SyncEnvelopeResult
    data class Unavailable(val loadResult: LoadResult) : SyncEnvelopeResult
    data object Disabled : SyncEnvelopeResult
    data object LocalChanged : SyncEnvelopeResult
}

/** One whole-branch algorithm for native and Web; CAS protects mutations made during network waits. */
class AcquisitionSyncProtocol(private val store: AcquisitionStore, private val transport: AcquisitionSyncTransport,
    private val archive: AcquisitionConflictArchive, private val capabilities: AcquisitionCapabilities,
    private val idFactory: () -> String) {
    private val locks = MutationLocks()
    suspend fun syncPending(characterId: String): SyncEnvelopeResult = guarded(characterId) {
        val initial = store.load(characterId)
        if (initial !is LoadResult.Loaded) return@guarded SyncEnvelopeResult.Unavailable(initial)
        var latest = initial.state
        latest.syncMetadata.remoteConflictCanonical?.let { return@guarded conflict(latest, it) }
        // Bound to the outbox captured at invocation; purchases racing an ack wait for the next sync.
        val pendingIds=latest.outbox.sortedWith(compareBy<OutboxItem> { decodeSyncEnvelope(it.payloadCanonical).revision }
            .thenBy { item -> latest.receipts.single { it.operationId==item.operationId }.afterRevision }).map { it.operationId }
        for (id in pendingIds) {
            val loaded = store.load(characterId)
            if (loaded !is LoadResult.Loaded) return@guarded SyncEnvelopeResult.Unavailable(loaded)
            latest = loaded.state
            latest.syncMetadata.remoteConflictCanonical?.let { return@guarded conflict(latest, it) }
            val item = latest.outbox.singleOrNull { it.operationId == id } ?: continue
            val request = AcquisitionPush(characterId, id, item.parentServerRevision, item.payloadCanonical)
            when (val response = transport.push(request)) {
                is AcquisitionPushResult.IdentityConflict -> return@guarded SyncEnvelopeResult.IdentityConflict(response.reason)
                is AcquisitionPushResult.Conflict -> return@guarded saveConflict(characterId, response)
                is AcquisitionPushResult.Acknowledged -> {
                    if (response.operationId != id || response.payloadCanonical != item.payloadCanonical || response.serverRevision <= item.parentServerRevision)
                        return@guarded SyncEnvelopeResult.IdentityConflict("Ack does not match committed operation")
                    val result = acknowledge(characterId, item, response.serverRevision)
                    if (result !is SyncEnvelopeResult.Synced) return@guarded result
                }
            }
        }
        val loaded = store.load(characterId)
        if (loaded !is LoadResult.Loaded) SyncEnvelopeResult.Unavailable(loaded)
        else SyncEnvelopeResult.Synced(loaded.state.syncMetadata.serverRevision, loaded.state.outbox.size)
    }
    suspend fun resolveConflict(characterId: String, choice: LocalOrServer, expectedServerRevision: Long): SyncEnvelopeResult = guarded(characterId) {
        val loaded = store.load(characterId)
        if (loaded !is LoadResult.Loaded) return@guarded SyncEnvelopeResult.Unavailable(loaded)
        val local = loaded.state
        val serverCanonical = local.syncMetadata.remoteConflictCanonical ?: return@guarded SyncEnvelopeResult.LocalChanged
        if (expectedServerRevision != local.syncMetadata.serverRevision) return@guarded conflict(local, serverCanonical)
        val localCanonical = encodeSyncEnvelope(local)
        val server = decodeSyncEnvelope(serverCanonical)
        require(server.character.id == characterId) { "Conflict character scope differs" }
        val archiveId = acquisitionArchiveId(characterId, expectedServerRevision, localCanonical, serverCanonical)
        val saved = ArchivedAcquisitionConflict(archiveId, characterId, expectedServerRevision, localCanonical, serverCanonical)
        when (val result = archive.preserve(saved)) {
            CommitResult.Committed -> Unit
            CommitResult.Conflict -> return@guarded SyncEnvelopeResult.StorageFailure("Archive identity conflict")
            is CommitResult.StorageFailure -> return@guarded SyncEnvelopeResult.StorageFailure(result.reason)
        }
        val id = idFactory()
        val selected = if (choice == LocalOrServer.Local) local else server
        val adoption = adoption(selected, local, id, saved, choice)
        val request = AcquisitionResolution(characterId, id, expectedServerRevision, choice, archiveId, encodeSyncEnvelope(adoption))
        when (val response = transport.resolve(request)) {
            is AcquisitionPushResult.IdentityConflict -> SyncEnvelopeResult.IdentityConflict(response.reason)
            is AcquisitionPushResult.Conflict -> saveConflict(characterId, response)
            is AcquisitionPushResult.Acknowledged -> {
                if (response.operationId != id || response.payloadCanonical != request.selectedCanonical || response.serverRevision <= expectedServerRevision)
                    return@guarded SyncEnvelopeResult.IdentityConflict("Resolution ack does not match archived selection")
                repeat(8) {
                    val now = store.load(characterId)
                    if (now !is LoadResult.Loaded) return@guarded SyncEnvelopeResult.Unavailable(now)
                    val current = now.state
                    if (encodeSyncEnvelope(current) != localCanonical || current.syncMetadata.remoteConflictCanonical != serverCanonical)
                        return@guarded saveConflict(characterId, AcquisitionPushResult.Conflict(response.serverRevision, request.selectedCanonical))
                    val next = adoption.copy(storageRevision = increment(current.storageRevision),
                        extras = adoption.extras.copy(portraitUri = current.extras.portraitUri),
                        syncMetadata = SyncMetadata(response.serverRevision), outbox = emptyList())
                    when (val commit = store.compareAndSet(characterId, current.storageRevision, next)) {
                        CommitResult.Committed -> return@guarded SyncEnvelopeResult.Synced(response.serverRevision, 0)
                        CommitResult.Conflict -> Unit
                        is CommitResult.StorageFailure -> return@guarded SyncEnvelopeResult.StorageFailure(commit.reason)
                    }
                }
                SyncEnvelopeResult.StorageFailure("Storage remained busy after resolution")
            }
        }
    }
    private suspend fun acknowledge(characterId: String, item: OutboxItem, revision: Long): SyncEnvelopeResult {
        repeat(8) {
            val loaded = store.load(characterId)
            if (loaded !is LoadResult.Loaded) return SyncEnvelopeResult.Unavailable(loaded)
            val state = loaded.state
            val current = state.outbox.singleOrNull { it.operationId == item.operationId }
                ?: return SyncEnvelopeResult.Synced(state.syncMetadata.serverRevision, state.outbox.size)
            if (current.payloadCanonical != item.payloadCanonical) return SyncEnvelopeResult.IdentityConflict("Queued identity changed during ack")
            val remoteRevision = maxOf(revision, state.syncMetadata.serverRevision)
            val next = state.copy(storageRevision = increment(state.storageRevision),
                outbox = state.outbox.filterNot { it.operationId == item.operationId }.map { pending ->
                    // Rebase transport ancestry only; gameplay payload and receipts remain exact.
                    if (pending.parentServerRevision < remoteRevision) pending.copy(parentServerRevision = remoteRevision) else pending
                }, syncMetadata = state.syncMetadata.copy(serverRevision = remoteRevision))
            when (val result = store.compareAndSet(characterId, state.storageRevision, next)) {
                CommitResult.Committed -> return SyncEnvelopeResult.Synced(remoteRevision, next.outbox.size)
                CommitResult.Conflict -> Unit
                is CommitResult.StorageFailure -> return SyncEnvelopeResult.StorageFailure(result.reason)
            }
        }
        return SyncEnvelopeResult.StorageFailure("Storage remained busy during ack")
    }
    private suspend fun saveConflict(characterId: String, response: AcquisitionPushResult.Conflict): SyncEnvelopeResult {
        require(response.serverRevision >= 0 && decodeSyncEnvelope(response.serverCanonical).character.id == characterId) { "Invalid remote conflict" }
        repeat(8) {
            val loaded = store.load(characterId)
            if (loaded !is LoadResult.Loaded) return SyncEnvelopeResult.Unavailable(loaded)
            val state = loaded.state
            if (response.serverRevision < state.syncMetadata.serverRevision) return SyncEnvelopeResult.LocalChanged
            val next = state.copy(storageRevision = increment(state.storageRevision),
                syncMetadata = SyncMetadata(response.serverRevision, response.serverCanonical))
            when (val result = store.compareAndSet(characterId, state.storageRevision, next)) {
                CommitResult.Committed -> return conflict(next, response.serverCanonical)
                CommitResult.Conflict -> Unit
                is CommitResult.StorageFailure -> return SyncEnvelopeResult.StorageFailure(result.reason)
            }
        }
        return SyncEnvelopeResult.StorageFailure("Storage remained busy during conflict preservation")
    }
    private fun conflict(state: StoredCharacter, remote: String) = SyncEnvelopeResult.Conflict(state.syncMetadata.serverRevision, encodeSyncEnvelope(state), remote)
    private suspend fun guarded(id: String, action: suspend () -> SyncEnvelopeResult): SyncEnvelopeResult {
        if (!capabilities.canSync) return SyncEnvelopeResult.Disabled
        if (id.isBlank()) return SyncEnvelopeResult.IdentityConflict("Blank character identity")
        return locks.forCharacter(id).withLock {
            try { action() } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { SyncEnvelopeResult.Retryable(failure.message ?: "Sync unavailable") }
        }
    }
}
internal fun increment(value: Long): Long { require(value < Long.MAX_VALUE) { "Revision overflow" }; return value + 1 }
internal fun acquisitionArchiveId(id: String, revision: Long, local: String, server: String) = "conflict:" + legacySourceFingerprint(
    listOf(id, revision.toString(), local, server).joinToString("") { "${it.length}:$it" })
private fun adoption(selected: StoredCharacter, local: StoredCharacter, id: String, archive: ArchivedAcquisitionConflict, choice: LocalOrServer): StoredCharacter {
    require(id.isNotBlank() && selected.receipts.none { it.operationId == id }) { "Resolution operation identity collision" }
    val beforeRevision = maxOf(selected.revision, local.revision)
    val afterRevision = increment(beforeRevision)
    val request = canonicalJson(jsonStringify(jsonObject("type" to jsonString("SyncAdoption"), "characterId" to jsonString(local.character.id),
        "expectedRevision" to jsonString(local.revision.toString()), "expectedServerRevision" to jsonString(archive.serverRevision.toString()),
        "choice" to jsonString(choice.name), "archiveId" to jsonString(archive.archiveId))))
    val proof = canonicalJson(jsonStringify(jsonObject("schema" to jsonString("sync-adoption-v1"),
        "selectedCanonical" to jsonString(encodeSyncEnvelope(selected)), "displacedCanonical" to jsonString(archive.localCanonical),
        "serverCanonical" to jsonString(archive.serverCanonical), "expectedServerRevision" to jsonString(archive.serverRevision.toString()),
        "choice" to jsonString(choice.name), "archiveId" to jsonString(archive.archiveId))))
    val entry = LedgerEntry(id, LedgerEntryType.SyncAdoption, emptyList(), Amount.ZERO, RuleVersions("sync-v1", "sync-v1"),
        creationPhase = selected.creationPhase, evidenceCanonical = proof)
    val next = selected.copy(ledger = selected.ledger.copy(entries = selected.ledger.entries + entry),
        receipts = selected.receipts + Receipt(id, request, SavedAcquisitionResult(Amount.ZERO, balance(selected.ledger), emptyList()), ReceiptStatus.Applied, beforeRevision, afterRevision),
        outbox = emptyList(), syncMetadata = SyncMetadata(0), revision = afterRevision, storageRevision = 0)
    encodeEnvelope(next)
    return next
}
/** Exact history in a new character scope. One import receipt registers it; old commands are not queued. */
fun copyAcquisitionCharacter(source: StoredCharacter, newCharacterId: String, importOperationId: String): StoredCharacter {
    encodeEnvelope(source)
    require(newCharacterId.isNotBlank() && newCharacterId != source.character.id && importOperationId.isNotBlank() && source.receipts.none { it.operationId == importOperationId })
    val captured = flatSyncEnvelope(source)
    val proof = canonicalJson(jsonStringify(jsonObject("schema" to jsonString("copy-import-v1"),
        "sourceCharacterId" to jsonString(source.character.id), "sourceFingerprint" to jsonString(legacySourceFingerprint(captured)))))
    val request = canonicalJson(jsonStringify(jsonObject("type" to jsonString("Import"), "characterId" to jsonString(newCharacterId),
        "sourceCharacterId" to jsonString(source.character.id), "sourceFingerprint" to jsonString(legacySourceFingerprint(captured)))))
    val entry = LedgerEntry(importOperationId, LedgerEntryType.Import, emptyList(), Amount.ZERO, RuleVersions("copy-v1", "copy-v1"),
        creationPhase = source.creationPhase, evidenceCanonical = proof)
    val copied = source.copy(character = source.character.copy(id = newCharacterId), revision = increment(source.revision), storageRevision = 0,
        ledger = source.ledger.copy(entries = source.ledger.entries + entry),
        receipts = source.receipts + Receipt(importOperationId, request, SavedAcquisitionResult(Amount.ZERO, balance(source.ledger), emptyList()), ReceiptStatus.Applied, source.revision, increment(source.revision)),
        outbox = emptyList(), syncMetadata = SyncMetadata(0),
        copySources = source.copySources + AcquisitionCopySource(source.character.id, newCharacterId, importOperationId, captured))
    val queued = copied.copy(outbox = listOf(OutboxItem(importOperationId, 0, encodeSyncEnvelope(copied))))
    encodeEnvelope(queued)
    return queued
}

/** Versioned DTO codec used by every adapter. Server support/guards are a separate release gate. */
object AcquisitionSyncWire {
    const val WRITER_VERSION = 2
    fun encodePush(request: AcquisitionPush): String {
        val state=decodeSyncEnvelope(request.payloadCanonical)
        require(request.operationId.isNotBlank() && request.parentServerRevision >= 0 && state.character.id == request.characterId &&
            state.ledger.entries.singleOrNull { it.operationId==request.operationId }?.type?.let { it != LedgerEntryType.SyncAdoption } == true) { "Push must identify a saved non-resolution operation" }
        return canonicalJson(jsonStringify(jsonObject("p_character_id" to jsonString(request.characterId), "p_operation_ids" to jsonArray(listOf(jsonString(request.operationId))),
            "p_expected_revision" to jsonString(request.parentServerRevision.toString()), "p_envelope" to JsonReader(request.payloadCanonical).read(),
            "p_writer_version" to jsonNumber(WRITER_VERSION), "p_resolution" to JsonValue.Null)))
    }
    fun encodeResolution(request: AcquisitionResolution): String {
        require(request.operationId.isNotBlank() && request.archiveId.isNotBlank() && request.expectedServerRevision >= 0 && decodeSyncEnvelope(request.selectedCanonical).character.id == request.characterId)
        return canonicalJson(jsonStringify(jsonObject("p_character_id" to jsonString(request.characterId), "p_operation_ids" to jsonArray(listOf(jsonString(request.operationId))),
            "p_expected_revision" to jsonString(request.expectedServerRevision.toString()), "p_envelope" to JsonReader(request.selectedCanonical).read(),
            "p_writer_version" to jsonNumber(WRITER_VERSION), "p_resolution" to jsonObject("choice" to jsonString(request.choice.name),
                "archiveId" to jsonString(request.archiveId), "expectedServerRevision" to jsonString(request.expectedServerRevision.toString())))))
    }
    fun decodeResult(raw: String): AcquisitionPushResult {
        val root = JsonReader(raw, strict = true).read() as? JsonValue.Obj ?: error("Expected sync result")
        fun text(key: String) = (root.values[key] as? JsonValue.Str)?.value ?: error("Missing $key")
        fun revision(): Long { val value=text("serverRevision"); require(Regex("0|[1-9][0-9]*").matches(value)); return value.toLong() }
        return when (text("status")) {
            "ack" -> { require(root.values.keys == setOf("status","operationId","payloadCanonical","serverRevision"));
                val payload=text("payloadCanonical");decodeSyncEnvelope(payload);AcquisitionPushResult.Acknowledged(text("operationId"),payload,revision()) }
            "conflict" -> { require(root.values.keys == setOf("status","serverCanonical","serverRevision"));
                val payload=text("serverCanonical");decodeSyncEnvelope(payload);AcquisitionPushResult.Conflict(revision(),payload) }
            "identityConflict" -> { require(root.values.keys == setOf("status","reason"));AcquisitionPushResult.IdentityConflict(text("reason")) }
            else -> error("Unknown sync result")
        }
    }
}
class AcquisitionRpcTransport(private val transport: FuryCloudTransport, private val capabilities: AcquisitionCapabilities) : AcquisitionSyncTransport {
    override suspend fun push(request: AcquisitionPush): AcquisitionPushResult {
        check(capabilities.canSync) { "Cloud ledger V2 guards are not enabled" }
        return AcquisitionSyncWire.decodeResult(transport.rpc("acquisition_sync_v2", AcquisitionSyncWire.encodePush(request)))
    }
    override suspend fun resolve(request: AcquisitionResolution): AcquisitionPushResult {
        check(capabilities.canSync) { "Cloud ledger V2 guards are not enabled" }
        return AcquisitionSyncWire.decodeResult(transport.rpc("acquisition_sync_v2", AcquisitionSyncWire.encodeResolution(request)))
    }
}
