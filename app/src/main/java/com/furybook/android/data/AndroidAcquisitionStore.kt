package com.furybook.android.data

import com.furybook.dubl.acquisition.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardOpenOption.*
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

enum class AndroidAcquisitionWriteStage { AfterDirectoryCreate, BeforeAncestorDirectoryFlush, AfterTempFlush, BeforeReplace,
    AfterReplace, AfterDirectoryFlush, BeforeAcknowledge, BeforeRecoveryFileFlush, BeforeRecoveryDirectoryFlush, AfterRecoveryDurability }

/** Mechanical adapter. Its existence does not enable production Android purchase capability. */
class AndroidAcquisitionStore(
    appDataDirectory: Path,
    val legacySource: AndroidLegacyAcquisitionSource,
    private val fileOps: AndroidAcquisitionFileOps = AndroidOsAcquisitionFileOps,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val fault: (AndroidAcquisitionWriteStage) -> Unit = {},
) : AcquisitionStore {
    private val root = appDataDirectory.toAbsolutePath().normalize()
    private val container = root.resolve("files/acquisition-v1/device-local")
    val storageIdentity: String get() = root.toString() + "/files/acquisition-v1/device-local"
    fun envelopePath(characterId: String): Path { require(characterId.isNotBlank()); return container.resolve("${key(characterId)}.json") }

    override suspend fun load(characterId: String): LoadResult = withContext(io) {
        locked(characterId) { loadLocked(characterId) }
    }
    override suspend fun compareAndSet(characterId: String, expectedStorageRevision: Long, next: StoredCharacter): CommitResult = withContext(io) {
        currentCoroutineContext().ensureActive()
        try {
            val encoded = encodeEnvelope(next)
            val captured = (decodeEnvelope(encoded) as LoadResult.Loaded).state
            require(captured.character.id == characterId) { "Character scope differs" }
            locked(characterId) {
                when (val existing = loadLocked(characterId)) {
                    is LoadResult.Loaded -> if (expectedStorageRevision < 0 || expectedStorageRevision == Long.MAX_VALUE ||
                        existing.state.storageRevision != expectedStorageRevision || captured.storageRevision != expectedStorageRevision + 1) return@locked CommitResult.Conflict
                    is LoadResult.Missing -> if (expectedStorageRevision != -1L || captured.storageRevision != 0L) return@locked CommitResult.Conflict
                    is LoadResult.MigrationRequired -> {
                        if (expectedStorageRevision != -1L || captured.storageRevision != 0L) return@locked CommitResult.Conflict
                        val origin = captured.localLegacySourceFence() ?: return@locked CommitResult.StorageFailure("Explicit legacy migration is required")
                        if (!legacySource.unchanged(origin)) return@locked CommitResult.Conflict
                        require(captured.extras == legacySource.capturedOrigin(origin).extras(characterId)) { "Initial migration must preserve legacy extras" }
                    }
                    else -> return@locked CommitResult.StorageFailure("Existing source/container requires recovery: ${existing::class.simpleName}")
                }
                val origin = captured.localLegacySourceFence()
                if (origin != null && !legacySource.unchanged(origin)) return@locked CommitResult.Conflict
                durableReplace(envelopePath(characterId), encoded) {
                    if (origin != null) require(legacySource.unchanged(origin)) { "Legacy source changed before replace" }
                }
                legacySource.gate.protect()
                fault(AndroidAcquisitionWriteStage.BeforeAcknowledge)
                CommitResult.Committed
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { CommitResult.StorageFailure(failure.message ?: "Android storage unavailable") }
    }

    suspend fun prepareMigration(characterId: String, declaredPackIds: List<String>, catalog: LegacyCatalogSnapshot?): MigrationResult = withContext(io) {
        locked(characterId) {
            when (val loaded = loadLocked(characterId)) {
                is LoadResult.Loaded -> MigrationResult.Ready(loaded.state)
                is LoadResult.Unsupported -> MigrationResult.Unsupported(loaded.raw, loaded.reason)
                is LoadResult.Corrupt -> MigrationResult.Corrupt(loaded.raw, loaded.reason)
                is LoadResult.LegacyImportConflict -> MigrationResult.Corrupt(loaded.canonicalRaw, loaded.reason)
                is LoadResult.Missing -> MigrationResult.Corrupt("", "Legacy character is missing")
                is LoadResult.MigrationRequired -> try {
                    val captured = legacySource.capture()
                    val origin = captured.origin(characterId, declaredPackIds)
                    prepareLegacy(origin.raw, origin, catalog, captured.extras(characterId))
                } catch (failure: AndroidSourceUnavailable) { if (failure.unsupported) MigrationResult.Unsupported(loaded.raw, failure.message.orEmpty()) else MigrationResult.Corrupt(loaded.raw, failure.message.orEmpty()) }
                catch (failure: Exception) { MigrationResult.Unsupported(loaded.raw, failure.message ?: "Legacy extras cannot be represented") }
            }
        }
    }

    suspend fun hasProtectedLocalData(): Boolean = withContext(io) {
        if (Files.notExists(container, NOFOLLOW_LINKS)) false else {
            checkDirectory(container)
            Files.newDirectoryStream(container).use { paths -> paths.any { it.fileName.toString().endsWith(".json") } }.also { if (it) legacySource.gate.protect() }
        }
    }
    suspend fun characterIds(): List<String> = withContext(io) {
        if (Files.notExists(container, NOFOLLOW_LINKS)) emptyList() else {
            checkDirectory(container)
            Files.newDirectoryStream(container, "*.json").use { paths -> paths.map { path ->
                checkRegular(path)
                val raw=try { decodeUtf8(readBytes(path)) } catch (_:Exception) { throw AcquisitionPreviewUnavailable(LoadResult.Corrupt("","Invalid UTF-8; exact bytes retained")) }
                val loaded=decodeEnvelope(raw)
                val state=(loaded as? LoadResult.Loaded)?.state ?: throw AcquisitionPreviewUnavailable(loaded)
                require(path==envelopePath(state.character.id)) { "Container filename/character scope differs" }
                state.character.id
            }.toList() }
        }
    }
    suspend fun recoveryExportAll(): Map<String, ByteArray> = withContext(io) {
        legacySource.gate.captured {
            val files=legacySource.rawFiles().toMutableMap()
            if(Files.exists(container,NOFOLLOW_LINKS)) {
                checkDirectory(container)
                Files.newDirectoryStream(container,"*.json").use { paths -> paths.forEach { path -> checkRegular(path);files[path.fileName.toString()]=readBytes(path) } }
            }
            files.toMap()
        }
    }
    suspend fun recoveryExport(characterId: String): Map<String, ByteArray> = withContext(io) {
        legacySource.gate.captured {
            val result = legacySource.rawFiles().toMutableMap()
            val path = envelopePath(characterId)
            if (Files.exists(path, NOFOLLOW_LINKS)) { checkRegular(path); result["envelope.json"] = readBytes(path) }
            result.toMap()
        }
    }

    private fun loadLocked(characterId: String): LoadResult {
        val path = envelopePath(characterId)
        if (Files.exists(path, NOFOLLOW_LINKS)) {
            checkRegular(path); legacySource.gate.protect()
            val bytes = readBytes(path)
            val raw = try { decodeUtf8(bytes) } catch (_: Exception) { return LoadResult.Corrupt("", "Invalid UTF-8; original bytes retained for export") }
            val loaded = decodeEnvelope(raw)
            if (loaded is LoadResult.MigrationRequired) return LoadResult.Corrupt(raw, "Legacy data in versioned container")
            if (loaded is LoadResult.Loaded) {
                if (loaded.state.character.id != characterId) return LoadResult.Corrupt(raw, "Wrong character scope")
                val origin = loaded.state.localLegacySourceFence()
                if (origin != null) try {
                    if (origin.extrasSource?.raw == null) return LoadResult.Unsupported(raw, "Exact Android preference backup is missing")
                    if (!legacySource.unchanged(origin)) {
                        val present = legacySource.capture()
                        return LoadResult.LegacyImportConflict(raw, present.snapshotRaw, present.origin(characterId, origin.activePackIds).extrasSource?.raw, "Legacy source changed; choose an import branch before writing")
                    }
                } catch (failure: Exception) {
                    return LoadResult.LegacyImportConflict(raw, null, rawSourceBundle(), "Legacy source is unavailable or changed: ${failure.message}")
                }
                fault(AndroidAcquisitionWriteStage.BeforeRecoveryFileFlush); fileOps.forceFile(path)
                createDirectoriesDurably(container)
                fault(AndroidAcquisitionWriteStage.BeforeRecoveryDirectoryFlush); fileOps.forceDirectory(container)
                fault(AndroidAcquisitionWriteStage.AfterRecoveryDurability)
            }
            return loaded
        }
        return try {
            val source = legacySource.capture()
            val raw = source.snapshotRaw ?: return LoadResult.Missing(characterId)
            val json = AndroidRawJson.fields(raw)
            val ids = AndroidRawJson.array(json.getValue("characters")).map { AndroidRawJson.string(AndroidRawJson.fields(it).getValue("id")) }
            require(ids.all(String::isNotBlank) && ids.distinct().size == ids.size)
            if (characterId in ids) LoadResult.MigrationRequired(raw) else LoadResult.Missing(characterId)
        } catch (failure: AndroidSourceUnavailable) {
            if (failure.unsupported) LoadResult.Unsupported(rawSourceBundle(), failure.message.orEmpty()) else LoadResult.Corrupt(rawSourceBundle(), failure.message.orEmpty())
        }
    }

    private fun rawSourceBundle(): String = AndroidRawJson.obj(legacySource.rawFiles().mapValues { AndroidRawJson.quote(java.util.Base64.getEncoder().encodeToString(it.value)) })
    private fun durableReplace(path: Path, raw: String, admit: () -> Unit) {
        val temp = path.resolveSibling("${path.fileName}.${UUID.randomUUID()}.tmp")
        try {
            FileChannel.open(temp, CREATE_NEW, WRITE).use { channel ->
                val buffer = ByteBuffer.wrap(raw.toByteArray(StandardCharsets.UTF_8))
                while (buffer.hasRemaining()) channel.write(buffer)
                channel.force(true)
            }
            fault(AndroidAcquisitionWriteStage.AfterTempFlush); admit(); fault(AndroidAcquisitionWriteStage.BeforeReplace)
            fileOps.atomicReplace(temp, path)
            // No suspension or cancellation boundary from replace to checked durability.
            fault(AndroidAcquisitionWriteStage.AfterReplace); fileOps.forceDirectory(container)
            fault(AndroidAcquisitionWriteStage.AfterDirectoryFlush)
        } finally { Files.deleteIfExists(temp) }
    }
    private fun <T> locked(characterId: String, action: () -> T): T = legacySource.gate.captured {
        require(characterId.isNotBlank())
        require(Files.isDirectory(root, NOFOLLOW_LINKS) && !Files.isSymbolicLink(root)) { "App data directory is unavailable" }
        createDirectoriesDurably(container)
        val lockPath = container.resolve("${key(characterId)}.lock")
        locks.computeIfAbsent(root.toRealPath().resolve(root.relativize(lockPath))) { ReentrantLock() }.withLock {
            if (Files.exists(lockPath, NOFOLLOW_LINKS)) checkRegular(lockPath)
            FileChannel.open(lockPath, CREATE, WRITE).use { channel -> channel.lock().use { action() } }
        }
    }
    private fun createDirectoriesDurably(path: Path) {
        if (path == root) { checkDirectory(path); return }
        val parent = requireNotNull(path.parent)
        require(path.startsWith(root)); createDirectoriesDurably(parent)
        if (Files.notExists(path, NOFOLLOW_LINKS)) {
            try { Files.createDirectory(path); fault(AndroidAcquisitionWriteStage.AfterDirectoryCreate) }
            catch (_: java.nio.file.FileAlreadyExistsException) { checkDirectory(path) }
        }
        checkDirectory(path); fault(AndroidAcquisitionWriteStage.BeforeAncestorDirectoryFlush); fileOps.forceDirectory(parent)
    }
    private fun checkDirectory(path: Path) { require(!Files.isSymbolicLink(path) && Files.isDirectory(path, NOFOLLOW_LINKS)) { "Unsupported directory path" } }
    private fun checkRegular(path: Path) { require(!Files.isSymbolicLink(path) && Files.isRegularFile(path, NOFOLLOW_LINKS)) { "Unsupported file path" } }
    private fun readBytes(path: Path): ByteArray { require(Files.size(path) <= 64L * 1024 * 1024) { "Envelope too large" }; return Files.readAllBytes(path) }
    private fun decodeUtf8(bytes: ByteArray): String = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
    private fun key(id: String) = legacySourceFingerprint(id).substringAfter(':')
    companion object { private val locks = ConcurrentHashMap<Path, ReentrantLock>() }
}
