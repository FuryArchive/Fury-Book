package com.furybook.dubl.data

import com.furybook.core.json.*
import com.furybook.dubl.acquisition.canonicalJson
import com.furybook.dubl.acquisition.legacySourceFingerprint
import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.WinDef.DWORD
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardCopyOption.*
import java.nio.file.StandardOpenOption.*
import java.util.UUID
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/** POSIX directory fsync is unavailable on Windows. Windows publishes both files and NEW
 * directories with checked MoveFileExW WRITE_THROUGH, without cross-volume copy fallback.
 * A serialized, durable init journal fences a visible but unfinished directory publication.
 * https://learn.microsoft.com/en-us/windows/win32/fileio/moving-directories
 */
internal object DesktopAcquisitionFiles {
    val isWindows: Boolean = System.getProperty("os.name").startsWith("Windows",ignoreCase=true)
    private const val INIT_PREFIX = ".furybook-acquisition-init-"
    private const val TEMP_PREFIX = ".furybook-directory-"
    private val initLock = ReentrantLock()

    fun replace(temp: Path,target: Path) {
        require(temp.toAbsolutePath().normalize().parent == target.toAbsolutePath().normalize().parent) { "Replacement must stay in one directory" }
        if (isWindows) moveWindows(temp,target,replace=true)
        else Files.move(temp,target,ATOMIC_MOVE,REPLACE_EXISTING)
    }

    fun forceDirectory(path: Path) {
        if (!isWindows) FileChannel.open(path,READ).use { it.force(true) }
        // Windows publication was already checked by initializeDirectory/replace; no ignored fsync failure.
        else require(Files.isDirectory(path,NOFOLLOW_LINKS)) { "Published directory is unavailable" }
    }

    /** Complete durability of a validated visible head before a receipt can be acknowledged. */
    fun recoverDirectoryEntry(path: Path,raw: String) {
        if (!isWindows) forceDirectory(path.parent)
        else writeThrough(path,raw) // Identical UTF-8 bytes, under the store's stable per-character OS lock.
    }

    fun initializeDirectory(path: Path,afterCreate: () -> Unit,beforeAncestorBarrier: () -> Unit) {
        check(isWindows)
        val home = Path.of(System.getProperty("user.home")).toRealPath()
        require(Files.isDirectory(home,NOFOLLOW_LINKS)) { "Existing user directory is required for initialization lock" }
        // One per-user lock also serializes overlapping/nested storage roots. Never delete/replace it.
        initLock.withLock {
            FileChannel.open(home.resolve("${INIT_PREFIX}global.lock"),CREATE,WRITE).use { channel -> channel.lock().use {
                val scope = prospectiveRealPath(path)
                Files.list(home).use { entries -> entries.filter { it.fileName.toString().startsWith(INIT_PREFIX) &&
                    it.fileName.toString().endsWith(".json") }.sorted().forEach { recoverPending(it,home,scope) } }
                val journal = home.resolve(INIT_PREFIX+fingerprint(scope)+".json")
                fun ensure(directory: Path) {
                    directory.parent?.let(::ensure)
                    if (!Files.isDirectory(directory,NOFOLLOW_LINKS)) {
                        require(!Files.exists(directory,NOFOLLOW_LINKS)) { "Storage path is not a directory" }
                        val temp = checkNotNull(directory.parent).resolve(TEMP_PREFIX+UUID.randomUUID()+".tmpdir")
                        writeThrough(journal,canonicalJson(jsonStringify(jsonObject(
                            "schema" to jsonString("furybook-windows-directory-v1"),
                            "scope" to jsonString(scope.toString()),"target" to jsonString(directory.toString()),"temp" to jsonString(temp.toString())))))
                        Files.createDirectory(temp)
                        moveWindows(temp,directory,replace=false)
                        afterCreate() // Pending remains until the completed native publication is recorded.
                        writeThrough(journal,"{}")
                    }
                    if (directory.parent != null) beforeAncestorBarrier()
                }
                ensure(scope)
            } }
        }
    }

    private fun recoverPending(journal: Path,home: Path,currentScope: Path) {
        require(Files.isRegularFile(journal,NOFOLLOW_LINKS)) { "Invalid initialization journal; preserve it for recovery" }
        val raw = Files.readString(journal,UTF_8)
        val root = JsonReader(raw,strict=true).read() as? JsonValue.Obj ?: error("Invalid initialization journal")
        if (root.values.isEmpty()) return
        require(root.values.keys == setOf("schema","scope","target","temp")) { "Unknown initialization journal shape" }
        fun text(key: String) = (root.values[key] as? JsonValue.Str)?.value ?: error("Invalid initialization journal field")
        require(text("schema") == "furybook-windows-directory-v1") { "Unknown initialization journal version" }
        fun storedPath(key: String): Path = Path.of(text(key)).also {
            require(it.isAbsolute && it.normalize() == it) { "Invalid initialization journal path" }
        }
        val scope = storedPath("scope"); val target = storedPath("target"); val temp = storedPath("temp")
        require(journal.fileName.toString() == INIT_PREFIX+fingerprint(scope)+".json" && scope.startsWith(target) &&
            target.parent != null && temp.parent == target.parent && !home.startsWith(target)) { "Initialization journal scope differs" }
        // An interrupted, disjoint store must not block or mutate this healthy root.
        if (!currentScope.startsWith(target) && !target.startsWith(currentScope)) return
        val name = temp.fileName.toString()
        require(name.startsWith(TEMP_PREFIX) && name.endsWith(".tmpdir")) { "Unknown initialization temporary path" }
        UUID.fromString(name.removePrefix(TEMP_PREFIX).removeSuffix(".tmpdir"))
        fun empty(directory: Path) {
            require(Files.isDirectory(directory,NOFOLLOW_LINKS) && Files.list(directory).use { !it.findAny().isPresent }) {
                "Interrupted directory contains unknown data; preserve it for recovery"
            }
        }
        // No writer may use a pending directory. Only these registered, empty directories can move.
        if (Files.exists(target,NOFOLLOW_LINKS)) {
            empty(target)
            if (Files.exists(temp,NOFOLLOW_LINKS)) { empty(temp); Files.delete(temp) }
            moveWindows(target,temp,replace=false)
        } else if (!Files.exists(temp,NOFOLLOW_LINKS)) Files.createDirectory(temp)
        empty(temp)
        moveWindows(temp,target,replace=false)
        writeThrough(journal,"{}") // Clearing is durable replacement, never an unflushed deletion.
    }

    private fun prospectiveRealPath(path: Path): Path {
        val absolute = path.toAbsolutePath().normalize()
        if (Files.exists(absolute,NOFOLLOW_LINKS)) return absolute.toRealPath()
        return checkNotNull(absolute.parent).let(::prospectiveRealPath).resolve(absolute.fileName)
    }
    private fun fingerprint(path: Path) = legacySourceFingerprint(path.toString().lowercase()).substringAfter(':')

    private fun writeThrough(path: Path,raw: String) {
        val temp = path.resolveSibling("${path.fileName}.${UUID.randomUUID()}.tmp")
        try {
            FileChannel.open(temp,CREATE_NEW,WRITE).use { channel ->
                val data = ByteBuffer.wrap(raw.toByteArray(UTF_8))
                while (data.hasRemaining()) channel.write(data)
                channel.force(true)
            }
            moveWindows(temp,path,replace=true)
        } finally { Files.deleteIfExists(temp) }
    }
    private fun moveWindows(source: Path,target: Path,replace: Boolean) {
        require(source.toAbsolutePath().normalize().parent == target.toAbsolutePath().normalize().parent) { "Native move must stay in one directory" }
        try {
            // Kernel32's Unicode mapper calls MoveFileExW. All data handles are closed before rename.
            val kernel = Kernel32.INSTANCE
            if (!kernel.MoveFileEx(nativePath(source),nativePath(target),
                    DWORD((0x8 or if (replace) 0x1 else 0).toLong()))) {
                val code = kernel.GetLastError()
                throw IOException("Windows write-through publication failed (error $code)")
            }
        } catch (failure: LinkageError) {
            throw IOException("Windows durable storage native bridge is unavailable",failure)
        }
    }
    private fun nativePath(path: Path): String {
        val absolute = path.toAbsolutePath().normalize().toString()
        return when {
            absolute.startsWith("\\\\?\\") -> absolute
            absolute.startsWith("\\\\") -> "\\\\?\\UNC\\"+absolute.removePrefix("\\\\")
            else -> "\\\\?\\"+absolute
        }
    }
}
