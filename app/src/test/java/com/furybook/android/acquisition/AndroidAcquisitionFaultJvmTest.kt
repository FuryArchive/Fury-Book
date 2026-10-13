package com.furybook.android.acquisition

import com.furybook.android.data.AndroidAcquisitionFileOps
import com.furybook.android.data.AndroidAcquisitionStore
import com.furybook.android.data.AndroidAcquisitionWriteStage
import com.furybook.android.data.AndroidLegacyAcquisitionSource
import com.furybook.dubl.acquisition.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.IOException
import java.net.URLClassLoader
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption.CREATE_NEW
import java.nio.file.StandardOpenOption.WRITE
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Bounded T13 host tests using genuine JDK files, OS locks, atomic moves and checked force calls.
 * Child JVM halts model process death, not power loss. No Android Os stub, device execution,
 * Android durability guarantee, or production purchase-capability claim is made by this suite.
 */
class AndroidAcquisitionFaultJvmTest {
    @Test fun two_store_instances_have_exactly_one_cas_winner() = runBlocking {
        withFaultRoot { root ->
            val initial = faultStore(root)
            assertEquals(CommitResult.Committed, initial.compareAndSet("real", -1, t13State()))
            val stores = listOf(faultStore(root), faultStore(root))
            val ready = CountDownLatch(2)
            val start = CountDownLatch(1)
            val executor = Executors.newFixedThreadPool(2)
            try {
                val futures = stores.mapIndexed { index, store ->
                    executor.submit(Callable {
                        ready.countDown()
                        check(start.await(30, TimeUnit.SECONDS)) { "CAS start barrier timed out" }
                        runBlocking {
                            store.compareAndSet("real", 0, t13State().copy(
                                storageRevision = 1, syncMetadata = SyncMetadata((index + 1).toLong())))
                        }
                    })
                }
                assertTrue("Both store instances must reach the barrier", ready.await(30, TimeUnit.SECONDS))
                start.countDown()
                val results = futures.map { it.get(30, TimeUnit.SECONDS) }
                assertEquals(results.toString(), 1, results.count { it == CommitResult.Committed })
                assertEquals(results.toString(), 1, results.count { it == CommitResult.Conflict })
                val saved = loaded(faultStore(root))
                assertEquals(1L, saved.storageRevision)
                assertEquals((results.indexOf(CommitResult.Committed) + 1).toLong(), saved.syncMetadata.serverRevision)
                assertEquals(Amount(100, 10), balance(saved.ledger))
            } finally {
                start.countDown()
                executor.shutdownNow()
                assertTrue("CAS workers must terminate", executor.awaitTermination(30, TimeUnit.SECONDS))
            }
        }
    }

    @Test fun two_actual_jvms_race_one_storage_revision_through_os_lock() = runBlocking {
        withFaultRoot { root ->
            assertEquals(CommitResult.Committed, faultStore(root).compareAndSet("real", -1, t13State()))
            val gate = root.resolve("cas-go")
            val processes = listOf(startProbe(root, "cas", "1"), startProbe(root, "cas", "2"))
            try {
                awaitChildBarrier(root, processes)
                Files.writeString(gate, "go", UTF_8, CREATE_NEW, WRITE)
                val results = processes.map { process ->
                    val output = childOutput(process, 0)
                    val resultLines = output.lineSequence().filter { it == "Committed" || it == "Conflict" }.toList()
                    assertEquals(output, 1, resultLines.size)
                    resultLines.single()
                }
                assertEquals(results.toString(), 1, results.count { it == "Committed" })
                assertEquals(results.toString(), 1, results.count { it == "Conflict" })
                val saved = loaded(faultStore(root))
                assertEquals(1L, saved.storageRevision)
                assertEquals((results.indexOf("Committed") + 1).toLong(), saved.syncMetadata.serverRevision)
                assertEquals(Amount(100, 10), balance(saved.ledger))
            } finally {
                processes.forEach(::stopProbe)
            }
        }
    }

    @Test fun halt_after_atomic_replace_reopens_whole_purchase_and_same_id_replays_once() = runBlocking {
        assertHaltedPurchase(AndroidAcquisitionWriteStage.AfterReplace, 71)
    }

    @Test fun halt_after_checked_directory_force_before_ack_replays_once() = runBlocking {
        assertHaltedPurchase(AndroidAcquisitionWriteStage.AfterDirectoryFlush, 72)
    }

    @Test fun recovery_file_force_error_never_acknowledges_same_id_replay_or_cas() = runBlocking {
        withFaultRoot { root ->
            val normal = faultStore(root)
            assertEquals(CommitResult.Committed, normal.compareAndSet("real", -1, t13State()))
            val command = purchaseCommand(normal)
            assertTrue(AcquisitionService(normal, ::t13Catalog) { "instance" }.apply(command) is ApplyResult.Applied)
            val before = Files.readAllBytes(normal.envelopePath("real"))
            var forceAttempts = 0
            val ops = object : AndroidAcquisitionFileOps by JvmFileOps {
                override fun forceFile(path: Path) {
                    forceAttempts++
                    throw IOException("checked recovery file force failed")
                }
            }
            val failing = faultStore(root, ops)
            val replay = AcquisitionService(failing, ::t13Catalog) { "unused" }.apply(command)
            assertTrue(replay.toString(), replay is ApplyResult.StorageFailure)
            assertTrue((replay as ApplyResult.StorageFailure).reason.contains("checked recovery file force failed"))
            assertTrue(failing.compareAndSet("real", 1, t13State().copy(storageRevision = 2)) is CommitResult.StorageFailure)
            assertEquals(2, forceAttempts)
            assertArrayEquals(before, Files.readAllBytes(normal.envelopePath("real")))
            assertPurchase(loaded(faultStore(root)))
        }
    }

    @Test fun recovery_directory_force_error_never_acknowledges_same_id_replay() = runBlocking {
        withFaultRoot { root ->
            val normal = faultStore(root)
            assertEquals(CommitResult.Committed, normal.compareAndSet("real", -1, t13State()))
            val command = purchaseCommand(normal)
            assertTrue(AcquisitionService(normal, ::t13Catalog) { "instance" }.apply(command) is ApplyResult.Applied)
            val head = normal.envelopePath("real")
            val before = Files.readAllBytes(head)
            var rejected = false
            val ops = object : AndroidAcquisitionFileOps by JvmFileOps {
                override fun forceDirectory(path: Path) {
                    if (path == head.parent) {
                        rejected = true
                        throw IOException("checked recovery directory force failed")
                    }
                    JvmFileOps.forceDirectory(path)
                }
            }
            val replay = AcquisitionService(faultStore(root, ops), ::t13Catalog) { "unused" }.apply(command)
            assertTrue(replay.toString(), replay is ApplyResult.StorageFailure)
            assertTrue(rejected)
            assertArrayEquals(before, Files.readAllBytes(head))
            assertPurchase(loaded(faultStore(root)))
        }
    }

    @Test fun post_replace_directory_force_error_returns_failure_then_replay_is_idempotent() = runBlocking {
        withFaultRoot { root ->
            val normal = faultStore(root)
            assertEquals(CommitResult.Committed, normal.compareAndSet("real", -1, t13State()))
            val command = purchaseCommand(normal)
            val head = normal.envelopePath("real")
            var replaced = false
            var rejected = false
            var acknowledged = false
            val ops = object : AndroidAcquisitionFileOps by JvmFileOps {
                override fun forceDirectory(path: Path) {
                    if (replaced && path == head.parent) {
                        rejected = true
                        throw IOException("checked post-replace directory force failed")
                    }
                    JvmFileOps.forceDirectory(path)
                }
            }
            val failing = faultStore(root, ops) { stage ->
                if (stage == AndroidAcquisitionWriteStage.AfterReplace) replaced = true
                if (stage == AndroidAcquisitionWriteStage.BeforeAcknowledge) acknowledged = true
            }
            val result = AcquisitionService(failing, ::t13Catalog) { "instance" }.apply(command)
            assertTrue(result.toString(), result is ApplyResult.StorageFailure)
            assertTrue(replaced)
            assertTrue(rejected)
            assertFalse(acknowledged)
            val reopened = faultStore(root)
            val saved = loaded(reopened)
            assertPurchase(saved)
            assertEquals(saved, (decodeEnvelope(Files.readString(head, UTF_8)) as LoadResult.Loaded).state)
            assertTrue(AcquisitionService(reopened, ::t13Catalog) { "unused" }.apply(command) is ApplyResult.AlreadyApplied)
            assertEquals(saved, loaded(reopened))
        }
    }

    @Test fun ancestor_directory_force_error_cannot_acknowledge_initial_envelope() = runBlocking {
        withFaultRoot { root ->
            var rejected = false
            var acknowledged = false
            val ops = object : AndroidAcquisitionFileOps by JvmFileOps {
                override fun forceDirectory(path: Path) {
                    if (path == root) {
                        rejected = true
                        throw IOException("checked ancestor directory force failed")
                    }
                    JvmFileOps.forceDirectory(path)
                }
            }
            val failing = faultStore(root, ops) { stage ->
                if (stage == AndroidAcquisitionWriteStage.BeforeAcknowledge) acknowledged = true
            }
            assertTrue(failing.compareAndSet("real", -1, t13State()) is CommitResult.StorageFailure)
            assertTrue(rejected)
            assertFalse(acknowledged)
            assertFalse(Files.exists(failing.envelopePath("real")))
            assertEquals(CommitResult.Committed, faultStore(root).compareAndSet("real", -1, t13State()))
            assertEquals(t13State(), loaded(faultStore(root)))
        }
    }

    @Test fun malformed_utf8_and_revision_overflow_are_retained_and_never_reset() = runBlocking {
        val valid = encodeEnvelope(t13State())
        val overflow = valid.replace("\"storageRevision\":\"0\"", "\"storageRevision\":\"9223372036854775808\"")
        assertNotEquals("The overflow fixture must change the decimal string", valid, overflow)
        for (bytes in listOf(valid.toByteArray(UTF_8) + byteArrayOf(0xc3.toByte(), 0x28), overflow.toByteArray(UTF_8))) {
            withFaultRoot { root ->
                val store = faultStore(root)
                val head = store.envelopePath("real")
                Files.createDirectories(head.parent)
                Files.write(head, bytes)
                assertTrue(store.load("real") is LoadResult.Corrupt)
                assertTrue(store.compareAndSet("real", -1, t13State()) is CommitResult.StorageFailure)
                assertArrayEquals(bytes, store.recoveryExport("real").getValue("envelope.json"))
                assertArrayEquals(bytes, Files.readAllBytes(head))
            }
        }
    }

    @Test fun long_max_storage_revision_never_wraps_into_a_committed_cas() = runBlocking {
        withFaultRoot { root ->
            val store = faultStore(root)
            val maximum = t13State().copy(storageRevision = Long.MAX_VALUE)
            val bytes = encodeEnvelope(maximum).toByteArray(UTF_8)
            Files.createDirectories(store.envelopePath("real").parent)
            Files.write(store.envelopePath("real"), bytes)
            assertEquals(maximum, loaded(store))
            assertEquals(CommitResult.Conflict, store.compareAndSet("real", Long.MAX_VALUE, t13State()))
            assertArrayEquals(bytes, Files.readAllBytes(store.envelopePath("real")))
        }
    }

    @Test fun envelope_lock_and_ancestor_symlinks_refuse_without_touching_targets() = runBlocking {
        for (kind in listOf("envelope", "lock", "ancestor")) withFaultRoot { root ->
            val store = faultStore(root)
            val head = store.envelopePath("real")
            val target = root.resolve("retained-target")
            val sentinel = "synthetic retained target".toByteArray(UTF_8)
            val link: Path
            val retained: Path
            if (kind == "ancestor") {
                Files.createDirectory(target)
                retained = target.resolve("sentinel")
                Files.write(retained, sentinel)
                link = root.resolve("files")
            } else {
                Files.createDirectories(head.parent)
                retained = target
                Files.write(retained, sentinel)
                link = if (kind == "envelope") head else head.resolveSibling(head.fileName.toString().removeSuffix(".json") + ".lock")
            }
            Files.createSymbolicLink(link, target)
            val result = store.compareAndSet("real", -1, t13State())
            assertTrue("$kind: $result", result is CommitResult.StorageFailure)
            assertTrue("$kind symlink must remain intact", Files.isSymbolicLink(link))
            assertEquals(target, Files.readSymbolicLink(link))
            assertArrayEquals(sentinel, Files.readAllBytes(retained))
        }
    }

    @Test fun forced_orphan_temp_is_ignored_and_never_promoted_over_head() = runBlocking {
        withFaultRoot { root ->
            val store = faultStore(root)
            assertEquals(CommitResult.Committed, store.compareAndSet("real", -1, t13State()))
            val orphan = store.envelopePath("real").resolveSibling(store.envelopePath("real").fileName.toString() + ".orphan.tmp")
            val orphanBytes = encodeEnvelope(t13State().copy(storageRevision = 99, syncMetadata = SyncMetadata(99))).toByteArray(UTF_8)
            FileChannel.open(orphan, CREATE_NEW, WRITE).use { channel ->
                val buffer = ByteBuffer.wrap(orphanBytes)
                while (buffer.hasRemaining()) channel.write(buffer)
                channel.force(true)
            }
            val reopened = faultStore(root)
            assertEquals(t13State(), loaded(reopened))
            assertEquals(listOf("real"), reopened.characterIds())
            assertFalse(reopened.recoveryExportAll().containsKey(orphan.fileName.toString()))
            assertEquals(CommitResult.Committed, reopened.compareAndSet("real", 0, t13State().copy(storageRevision = 1)))
            assertEquals(1L, loaded(faultStore(root)).storageRevision)
            assertArrayEquals(orphanBytes, Files.readAllBytes(orphan))
        }
    }

    private suspend fun assertHaltedPurchase(stage: AndroidAcquisitionWriteStage, exitCode: Int) {
        withFaultRoot { root ->
            val original = faultStore(root)
            assertEquals(CommitResult.Committed, original.compareAndSet("real", -1, t13State()))
            val command = purchaseCommand(original)
            val process = startProbe(root, "halt", stage.name)
            try {
                val output = childOutput(process, exitCode)
                assertTrue(output, output.lineSequence().any { it == "HALT:${stage.name}" })
                assertFalse("Terminated child cannot send an ACK: $output", output.lineSequence().any { it == "ACK" })
                val reopened = faultStore(root)
                val saved = loaded(reopened)
                assertPurchase(saved)
                assertEquals(saved, (decodeEnvelope(Files.readString(reopened.envelopePath("real"), UTF_8)) as LoadResult.Loaded).state)
                assertTrue(AcquisitionService(reopened, ::t13Catalog) { "unused" }.apply(command) is ApplyResult.AlreadyApplied)
                assertEquals(saved, loaded(reopened))
            } finally {
                stopProbe(process)
            }
        }
    }

    private suspend fun purchaseCommand(store: AndroidAcquisitionStore): ConfirmAcquisition {
        val request = t13Request()
        val preview = AcquisitionService(store, ::t13Catalog) { "instance" }.preview(request)
        return ConfirmAcquisition("purchase", "real", request, preview.baseRevision, preview.previewToken)
    }

    private suspend fun loaded(store: AndroidAcquisitionStore): StoredCharacter = (store.load("real") as LoadResult.Loaded).state

    private fun assertPurchase(saved: StoredCharacter) {
        assertEquals(Amount(80, 10), balance(saved.ledger))
        assertEquals(1L, saved.revision)
        assertEquals(1L, saved.storageRevision)
        assertEquals(1, saved.instances.size)
        assertEquals(1, saved.ledger.entries.size)
        assertEquals("purchase", saved.receipts.single().operationId)
        assertEquals("purchase", saved.outbox.single().operationId)
    }

    private fun startProbe(root: Path, mode: String, parameter: String): Process = ProcessBuilder(
        Path.of(System.getProperty("java.home"), "bin", "java").toString(), "-cp", faultProbeClasspath(),
        AndroidAcquisitionFaultProcessProbe::class.java.name, root.toString(), mode, parameter,
    ).redirectErrorStream(true).start()

    private fun childOutput(process: Process, expectedCode: Int): String {
        if (!process.waitFor(30, TimeUnit.SECONDS)) {
            stopProbe(process)
            fail("Child JVM did not terminate within 30 seconds")
        }
        val output = process.inputStream.bufferedReader().use { it.readText() }
        assertEquals(output, expectedCode, process.exitValue())
        return output
    }

    private fun awaitChildBarrier(root: Path, processes: List<Process>) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30)
        while (!Files.exists(root.resolve("cas-ready-1")) || !Files.exists(root.resolve("cas-ready-2"))) {
            processes.firstOrNull { !it.isAlive }?.let { failed ->
                fail("Child exited before CAS barrier: " + failed.inputStream.bufferedReader().use { it.readText() })
            }
            check(System.nanoTime() < deadline) { "Child CAS readiness barrier timed out" }
            Thread.sleep(10)
        }
    }
}

private fun faultStore(
    root: Path,
    fileOps: AndroidAcquisitionFileOps = JvmFileOps,
    fault: (AndroidAcquisitionWriteStage) -> Unit = {},
) = AndroidAcquisitionStore(root, AndroidLegacyAcquisitionSource(root.resolve("shared_prefs")), fileOps, fault = fault)

private suspend fun <T> withFaultRoot(block: suspend (Path) -> T): T {
    val root = Files.createTempDirectory("t13-host-fault-")
    return try { block(root) } finally {
        // Files.walk does not follow the synthetic symlinks created by this suite.
        Files.walk(root).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) } }
    }
}

private fun stopProbe(process: Process) {
    if (process.isAlive) {
        process.destroyForcibly()
        check(process.waitFor(30, TimeUnit.SECONDS)) { "Child JVM could not be reaped" }
    }
}

/** Gradle's test classes can live on an isolated URLClassLoader, outside java.class.path. */
private fun faultProbeClasspath(): String {
    val urls = generateSequence(AndroidAcquisitionFaultProcessProbe::class.java.classLoader) { it.parent }
        .filterIsInstance<URLClassLoader>().flatMap { it.getURLs().asSequence() }
        .filter { it.protocol == "file" }.map { Path.of(it.toURI()).toString() }.toList()
    return (urls + System.getProperty("java.class.path").orEmpty().split(File.pathSeparator))
        .distinct().joinToString(File.pathSeparator)
}

/** Actual separate host-JVM entry point. All paths are under the parent's synthetic temp root. */
object AndroidAcquisitionFaultProcessProbe {
    @JvmStatic fun main(args: Array<String>) = runBlocking {
        require(args.size == 3)
        val root = Path.of(args[0]).toAbsolutePath().normalize()
        require(Files.isDirectory(root) && root.fileName.toString().startsWith("t13-host-fault-"))
        when (args[1]) {
            "cas" -> {
                val marker = args[2].toLong()
                require(marker == 1L || marker == 2L)
                val store = faultStore(root)
                val original = (store.load("real") as LoadResult.Loaded).state
                Files.writeString(root.resolve("cas-ready-$marker"), "ready", UTF_8, CREATE_NEW, WRITE)
                val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30)
                while (!Files.exists(root.resolve("cas-go"))) {
                    check(System.nanoTime() < deadline) { "Parent CAS release barrier timed out" }
                    Thread.sleep(10)
                }
                println(store.compareAndSet("real", 0, original.copy(storageRevision = 1, syncMetadata = SyncMetadata(marker))))
            }
            "halt" -> {
                val stage = AndroidAcquisitionWriteStage.valueOf(args[2])
                require(stage == AndroidAcquisitionWriteStage.AfterReplace || stage == AndroidAcquisitionWriteStage.AfterDirectoryFlush)
                val store = faultStore(root) { observed ->
                    if (observed == stage) {
                        println("HALT:${stage.name}")
                        System.out.flush()
                        Runtime.getRuntime().halt(if (stage == AndroidAcquisitionWriteStage.AfterReplace) 71 else 72)
                    }
                }
                val service = AcquisitionService(store, ::t13Catalog) { "instance" }
                val request = t13Request()
                val preview = service.preview(request)
                val result = service.apply(ConfirmAcquisition("purchase", "real", request, preview.baseRevision, preview.previewToken))
                println("ACK")
                error("Expected process death before acknowledgment, got $result")
            }
            else -> error("Unknown bounded host probe mode")
        }
    }
}
