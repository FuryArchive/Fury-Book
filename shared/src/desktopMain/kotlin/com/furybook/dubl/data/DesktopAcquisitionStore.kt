package com.furybook.dubl.data

import com.furybook.core.json.*
import com.furybook.dubl.acquisition.*
import com.furybook.dubl.actions.*
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption.*
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

enum class DesktopAcquisitionWriteStage {
    AfterTempFlush, BeforeReplace, AfterReplace, AfterDirectoryFlush,
    AfterDirectoryCreate, BeforeAncestorDirectoryFlush,
    BeforeRecoveryFileFlush, BeforeRecoveryDirectoryFlush, AfterRecoveryDurability,
    AfterGameplayBackup, BeforeGameplayHeadReplace,
}

/**
 * Separate versioned container. Atomic replace is mandatory; unsupported filesystems fail closed.
 * File + directory force precede acknowledgement. Orphan temps are never promoted. A corrupt/future
 * head is retained for export, not silently recovered/reset. Legacy files are immutable import inputs.
 * Stable lock files are never removed; an OS lock serializes other JVMs and a JVM lock prevents
 * overlapping FileChannel locks between instances. Character IDs never become path components.
 */
class DesktopAcquisitionStore(
    private val dataDirectory: Path = DesktopCharacterStore.defaultDataDirectory(),
    private val fault: (DesktopAcquisitionWriteStage) -> Unit = {},
    private val gameplayFormatEnabled: Boolean = false,
) : AcquisitionStore {
    /** Preserve existing trailing-lambda fault-injection callers. */
    constructor(dataDirectory: Path, fault: (DesktopAcquisitionWriteStage) -> Unit) :
        this(dataDirectory, fault, false)
    private val container = dataDirectory.toAbsolutePath().normalize().resolve("acquisition-v1")
    private val legacyCharacters = dataDirectory.resolve("characters.json")
    private val legacyExtras = dataDirectory.resolve("sheet-extras.json")

    fun envelopePath(characterId: String): Path = container.resolve("${key(characterId)}.json")
    val storageIdentity: String get() = container.toString()

    /** Only an explicitly reviewed isolated-local request reaches this path; ordinary CAS cannot upgrade. */
    suspend fun migrateGameplay(request: GameplayMigrationRequest): GameplayMigrationResult {
        if (!gameplayFormatEnabled) return GameplayMigrationResult.Blocked("Gameplay migration is not enabled")
        return try {
            locked(request.characterId) {
                require(request.operationId.isNotBlank()) { "Missing migration ID" }
                val backupDirectory = container.resolve("gameplay-migration-backups")
                val backup = backupDirectory.resolve("${key(request.characterId + "\u0000" + request.operationId)}.json")
                val current = loadLocked(request.characterId)
                val state = (current as? LoadResult.Loaded)?.state
                    ?: return@locked GameplayMigrationResult.Blocked("Canonical source is unavailable; preserve existing bytes")
                if (state.formatVersion == 2) {
                    val proof = state.gameplay?.migration
                    if (proof?.operationId != request.operationId || proof.sourceStorageRevision != request.expectedStorageRevision ||
                        canonicalJson(proof.sourceRaw) != request.expectedSourceCanonical)
                        return@locked GameplayMigrationResult.Blocked("Different or already completed migration")
                    require(readIfExists(backup) == proof.sourceRaw) { "Migration backup is missing or differs" }
                    FileChannel.open(backup, WRITE).use { it.force(true) }
                    forceDirectory(backupDirectory)
                    return@locked GameplayMigrationResult.AlreadyMigrated(backup.toString())
                }
                val sourceRaw = checkNotNull(readIfExists(envelopePath(request.characterId))) { "Missing canonical source" }
                val next = prepareGameplayMigration(state, sourceRaw, request)
                createDirectoriesDurably(backupDirectory)
                val saved = readIfExists(backup)
                if (saved != null) {
                    require(saved == sourceRaw) { "Migration ID already has a different backup" }
                    FileChannel.open(backup, WRITE).use { it.force(true) }
                    forceDirectory(backupDirectory)
                } else durableReplace(backup, sourceRaw)
                require(readIfExists(backup) == sourceRaw) { "Backup verification failed" }
                fault(DesktopAcquisitionWriteStage.AfterGameplayBackup)
                fault(DesktopAcquisitionWriteStage.BeforeGameplayHeadReplace)
                state.localLegacySourceFence()?.let { require(sourceUnchanged(it)) { "Legacy source changed during gameplay migration" } }
                durableReplace(envelopePath(request.characterId), encodeEnvelope(next))
                GameplayMigrationResult.Migrated(backup.toString())
            }
        } catch (failure: IllegalArgumentException) {
            GameplayMigrationResult.Blocked(failure.message ?: "Invalid migration review")
        } catch (failure: Exception) {
            GameplayMigrationResult.StorageFailure(failure.message ?: "Migration outcome uncertain; retain the same operation ID")
        }
    }

    /** Presence/uncertainty is protection, never write capability or an inferred migration. */
    fun hasProtectedHeads(): Boolean = try {
        if (Files.notExists(container)) false
        else if (!Files.isDirectory(container, java.nio.file.LinkOption.NOFOLLOW_LINKS)) true
        else Files.list(container).use { paths -> paths.anyMatch { it.fileName.toString().endsWith(".json") } }
    } catch (_: Exception) { true }

    /** Heads are the roster authority. Never silently skip future/corrupt/misbound data. */
    suspend fun characterIds(): List<String> {
        if (!Files.exists(container)) return emptyList()
        val ids = linkedSetOf<String>()
        Files.list(container).use { paths ->
            paths.filter { it.fileName.toString().endsWith(".json") }.sorted().forEach { path ->
                require(Files.isRegularFile(path, java.nio.file.LinkOption.NOFOLLOW_LINKS)) { "Invalid canonical head; preserve for recovery" }
                val raw = readIfExists(path) ?: error("Canonical head disappeared; reload")
                val loaded = decodeEnvelope(raw, allowGameplayFormat = gameplayFormatEnabled)
                val state = (loaded as? LoadResult.Loaded)?.state ?: throw AcquisitionPreviewUnavailable(loaded)
                require(path == envelopePath(state.character.id) && ids.add(state.character.id)) { "Canonical head identity differs from its filename" }
            }
        }
        for (id in ids) when (val loaded = load(id)) {
            is LoadResult.Loaded -> require(loaded.state.character.id == id)
            else -> throw AcquisitionPreviewUnavailable(loaded)
        }
        return ids.sorted()
    }


    override suspend fun load(characterId: String): LoadResult = locked(characterId) { loadLocked(characterId) }

    /** Preparation does not write. A caller must explicitly confirm when the frozen catalog is absent. */
    fun prepareMigration(characterId: String, activePackIds: List<String>, catalog: LegacyCatalogSnapshot?): MigrationResult =
        locked(characterId) {
            when (val loaded = loadLocked(characterId)) {
                is LoadResult.Loaded -> MigrationResult.Ready(loaded.state)
                is LoadResult.Unsupported -> MigrationResult.Unsupported(loaded.raw, loaded.reason)
                is LoadResult.Corrupt -> MigrationResult.Corrupt(loaded.raw, loaded.reason)
                is LoadResult.LegacyImportConflict -> MigrationResult.Corrupt(loaded.canonicalRaw, loaded.reason)
                is LoadResult.Missing -> MigrationResult.Corrupt("", "Legacy character is missing")
                is LoadResult.MigrationRequired -> try {
                    val rawExtras = readIfExists(legacyExtras)
                    val extras = rawExtras?.let { DesktopCharacterExtrasStore.decodeLegacy(it)[characterId] }
                        ?: com.furybook.dubl.model.CharacterSheetExtras()
                    val root = JsonReader(loaded.raw, strict = true).read() as JsonValue.Obj
                    val source = LegacyOrigin(characterId, root.int("schema"),
                        legacySourceFingerprint(loaded.raw), loaded.raw, activePackIds, extrasSource = LegacyExtrasSource(rawExtras))
                    prepareLegacy(loaded.raw, source, catalog, extras)
                } catch (failure: UnsupportedDesktopData) {
                    MigrationResult.Unsupported(loaded.raw, failure.message ?: "Unsupported extras")
                } catch (failure: Exception) {
                    MigrationResult.Corrupt(loaded.raw, failure.message ?: "Invalid legacy source set")
                }
            }
        }

    override suspend fun compareAndSet(characterId: String, expectedStorageRevision: Long, next: StoredCharacter): CommitResult = try {
        // Capture and validate before touching a file. No caller-owned mutable collection survives.
        val encoded = encodeEnvelope(next)
        val captured = (decodeEnvelope(encoded, allowGameplayFormat = gameplayFormatEnabled) as LoadResult.Loaded).state
        require(captured.character.id == characterId) { "Character scope differs" }
        locked(characterId) {
            when (val current = loadLocked(characterId)) {
                is LoadResult.Loaded -> {
                    if (current.state.formatVersion != captured.formatVersion)
                        return@locked CommitResult.StorageFailure("Explicit backed-up canonical format migration is required")
                    if (current.state.storageRevision != expectedStorageRevision || expectedStorageRevision == Long.MAX_VALUE ||
                        captured.storageRevision != expectedStorageRevision + 1) return@locked CommitResult.Conflict
                }
                is LoadResult.Missing -> if (expectedStorageRevision != -1L || captured.storageRevision != 0L)
                    return@locked CommitResult.Conflict
                is LoadResult.MigrationRequired -> {
                    if (expectedStorageRevision != -1L || captured.storageRevision != 0L) return@locked CommitResult.Conflict
                    val origin = captured.localLegacySourceFence()
                        ?: return@locked CommitResult.StorageFailure("Legacy source requires an explicit migration")
                    if (origin.raw != current.raw || origin.extrasSource == null || !sourceUnchanged(origin))
                        return@locked CommitResult.Conflict
                    val originalExtras = origin.extrasSource.raw?.let { DesktopCharacterExtrasStore.decodeLegacy(it)[characterId] }
                        ?: com.furybook.dubl.model.CharacterSheetExtras()
                    require(captured.extras == originalExtras) { "Initial migration must preserve every legacy extras value" }
                }
                is LoadResult.LegacyImportConflict -> return@locked CommitResult.StorageFailure(current.reason)
                is LoadResult.Corrupt -> return@locked CommitResult.StorageFailure("Corrupt envelope/source: ${current.reason}")
                is LoadResult.Unsupported -> return@locked CommitResult.StorageFailure("Unsupported envelope/source: ${current.reason}")
            }
            captured.localLegacySourceFence()?.let {
                if (!sourceUnchanged(it)) return@locked CommitResult.Conflict
            }
            durableReplace(envelopePath(characterId), encoded)
            CommitResult.Committed
        }
    } catch (failure: Exception) { CommitResult.StorageFailure(failure.message ?: "Desktop storage unavailable") }

    private fun loadLocked(characterId: String): LoadResult {
        val path = envelopePath(characterId)
        val canonicalRaw = try { readIfExists(path) } catch (_: java.nio.charset.CharacterCodingException) {
            return LoadResult.Corrupt("", "Invalid envelope UTF-8; original bytes retained in the container")
        }
        if (canonicalRaw != null) {
            val raw = canonicalRaw
            val loaded = decodeEnvelope(raw, allowGameplayFormat = gameplayFormatEnabled)
            if (loaded is LoadResult.Loaded) {
                if (loaded.state.character.id != characterId) return LoadResult.Corrupt(raw, "Envelope character scope differs")
                loaded.state.localLegacySourceFence()?.let { origin ->
                    if (origin.extrasSource == null) return LoadResult.Unsupported(raw,
                        "Legacy migration lacks the exact sheet-extras source; preserve/export it for review")
                    if (!sourceUnchanged(origin)) return LoadResult.LegacyImportConflict(raw,
                        readIfExists(legacyCharacters), readIfExists(legacyExtras),
                        "Legacy source changed after migration; choose an import branch before writing")
                }
                // A visible rename may be an interrupted commit, including one from another JVM.
                // Never acknowledge/replay its receipt until the head and every directory entry
                // have checked durability. An unavailable recovery force propagates as an error.
                fault(DesktopAcquisitionWriteStage.BeforeRecoveryFileFlush)
                FileChannel.open(path, WRITE).use { it.force(true) }
                createDirectoriesDurably(container) // Under the same per-character OS lock.
                fault(DesktopAcquisitionWriteStage.BeforeRecoveryDirectoryFlush)
                forceDirectory(container)
                fault(DesktopAcquisitionWriteStage.AfterRecoveryDurability)
            }
            return loaded
        }
        val raw = try { readIfExists(legacyCharacters) } catch (_: java.nio.charset.CharacterCodingException) {
            return LoadResult.Corrupt("", "Invalid legacy UTF-8; original bytes retained")
        } ?: return LoadResult.Missing(characterId)
        return when (val legacy = decodeEnvelope(raw)) {
            is LoadResult.MigrationRequired -> try {
                val root = JsonReader(raw, strict=true).read() as JsonValue.Obj
                val ids = root.array("characters").map { (it as JsonValue.Obj).string("id") }
                require(ids.all(String::isNotBlank) && ids.distinct().size == ids.size) { "Invalid legacy character identities" }
                if (characterId in ids) legacy else LoadResult.Missing(characterId)
            } catch (failure: Exception) { LoadResult.Corrupt(raw, failure.message ?: "Invalid legacy snapshot") }
            is LoadResult.Loaded -> LoadResult.Corrupt(raw, "Acquisition envelope found in legacy path")
            else -> legacy
        }
    }

    private fun sourceUnchanged(origin: LegacyOrigin): Boolean = readIfExists(legacyCharacters) == origin.raw &&
        (origin.extrasSource == null || readIfExists(legacyExtras) == origin.extrasSource.raw)

    private fun durableReplace(path: Path, raw: String) {
        val temp = path.resolveSibling("${path.fileName}.${UUID.randomUUID()}.tmp")
        try {
            FileChannel.open(temp, CREATE_NEW, WRITE).use { channel ->
                val buffer = ByteBuffer.wrap(raw.toByteArray(StandardCharsets.UTF_8))
                while (buffer.hasRemaining()) channel.write(buffer)
                channel.force(true)
            }
            fault(DesktopAcquisitionWriteStage.AfterTempFlush)
            fault(DesktopAcquisitionWriteStage.BeforeReplace)
            Files.move(temp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            fault(DesktopAcquisitionWriteStage.AfterReplace)
            // Head files live in container; migration backups have their own directory entry.
            forceDirectory(path.parent)
            fault(DesktopAcquisitionWriteStage.AfterDirectoryFlush)
        } finally { Files.deleteIfExists(temp) }
    }

    private fun <T> locked(characterId: String, action: () -> T): T {
        require(characterId.isNotBlank()) { "Blank character ID" }
        createDirectoriesDurably(container)
        val lockPath = container.resolve("${key(characterId)}.lock")
        return locks.computeIfAbsent(lockPath) { ReentrantLock() }.withLock {
            FileChannel.open(lockPath, CREATE, WRITE).use { channel -> channel.lock().use { action() } }
        }
    }

    private fun readIfExists(path: Path): String? = try { read(path) }
        catch (_: java.nio.file.NoSuchFileException) { null }

    private fun createDirectoriesDurably(path: Path) {
        val parent = path.parent
        if (parent != null) createDirectoriesDurably(parent)
        if (!Files.isDirectory(path)) {
            try {
                Files.createDirectory(path)
                fault(DesktopAcquisitionWriteStage.AfterDirectoryCreate)
            } catch (_: java.nio.file.FileAlreadyExistsException) {
                require(Files.isDirectory(path)) { "Storage path is not a directory" }
            }
        }
        // Existence is not proof: another writer or an earlier failed attempt may have created
        // this entry without flushing its parent. Retry the full ancestor chain unconditionally.
        if (parent != null) {
            fault(DesktopAcquisitionWriteStage.BeforeAncestorDirectoryFlush)
            forceDirectory(parent)
        }
    }
    private fun read(path: Path): String = StandardCharsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(Files.readAllBytes(path))).toString()
    private fun key(id: String): String = legacySourceFingerprint(id).substringAfter(':')
    private fun forceDirectory(path: Path) { FileChannel.open(path, READ).use { it.force(true) } }
    companion object { private val locks = ConcurrentHashMap<Path, ReentrantLock>() }
}

class UnsupportedDesktopData(message: String) : IllegalStateException(message)
