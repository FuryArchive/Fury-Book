package com.furybook.dubl.acquisition

import com.furybook.dubl.data.*
import com.furybook.dubl.model.*
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.runBlocking
import kotlin.test.*

/** Faults interrupt real filesystem forces; reopening must complete them before receipt replay. */
class DesktopAcquisitionRecoveryTest {
    private fun state() = StoredCharacter(DublCharacter("real"), CharacterSheetExtras(), emptyList(),
        Ledger(Opening("new/real", "test", Amount(100, 10), RequirementNode.CreationPhase.Phase.Creating,
            OpeningProvenance.NewCharacter), emptyList()), emptyList(), emptyList(), SyncMetadata(0), 0, 0,
        RequirementNode.CreationPhase.Phase.Creating)
    private fun service(store: DesktopAcquisitionStore) = AcquisitionService(store, ::desktopStoreTestCatalog) { "instance" }
    private suspend fun command(store: DesktopAcquisitionStore): ConfirmAcquisition {
        val request = AcquisitionRequest("real", listOf(AcquisitionTarget(DefinitionRef("dubl-3.69", "test-store-purchase"), "instance", 1)))
        val plan = service(store).preview(request)
        return ConfirmAcquisition("purchase", "real", request, plan.baseRevision, plan.previewToken)
    }

    @Test fun interrupted_replace_requires_checked_file_and_directory_recovery_before_replay() = runBlocking {
        val dir = Files.createTempDirectory("t12-recovery")
        val normal = DesktopAcquisitionStore(dir)
        assertEquals(CommitResult.Committed, normal.compareAndSet("real", -1, state()))
        val command = command(normal)
        val failed = DesktopAcquisitionStore(dir) { if (it == DesktopAcquisitionWriteStage.AfterReplace) error("replace visible, force pending") }
        assertIs<ApplyResult.StorageFailure>(service(failed).apply(command))
        val visible = Files.readAllBytes(normal.envelopePath("real"))
        for (forceStage in listOf("BeforeRecoveryFileFlush", "BeforeRecoveryDirectoryFlush")) {
            val unavailable = DesktopAcquisitionStore(dir) { if (it.name == forceStage) error("recovery force unavailable: $forceStage") }
            assertFails { unavailable.load("real") }
            assertIs<ApplyResult.StorageFailure>(service(unavailable).apply(command))
            assertContentEquals(visible, Files.readAllBytes(normal.envelopePath("real")))
        }
        val stages = mutableListOf<String>()
        val recovered = DesktopAcquisitionStore(dir) { stages += it.name }
        assertIs<ApplyResult.AlreadyApplied>(service(recovered).apply(command))
        assertTrue("AfterRecoveryDurability" in stages, "Replay needs completed real file/directory force")
        val saved = assertIs<LoadResult.Loaded>(recovered.load("real")).state
        assertEquals(Amount(80, 10), balance(saved.ledger))
        assertEquals("purchase", saved.receipts.single().operationId)
        assertEquals("purchase", saved.outbox.single().operationId)
    }

    @Test fun directory_created_before_parent_force_is_retried_even_when_it_exists() = runBlocking {
        val root = Files.createTempDirectory("t12-ancestor")
        val data = root.resolve("new-parent/new-data")
        val creationFailed = DesktopAcquisitionStore(data) { if (it.name == "AfterDirectoryCreate") error("parent entry unconfirmed") }
        assertIs<CommitResult.StorageFailure>(creationFailed.compareAndSet("real", -1, state()))
        assertTrue(Files.isDirectory(root.resolve("new-parent")))
        var forceCount = 0
        val unconfirmedEntry = root.resolve("new-parent").nameCount
        val retryFailed = DesktopAcquisitionStore(data) {
            if (it.name == "BeforeAncestorDirectoryFlush" && ++forceCount == unconfirmedEntry)
                error("previously unconfirmed parent-entry force unavailable")
        }
        assertIs<CommitResult.StorageFailure>(retryFailed.compareAndSet("real", -1, state()))
        assertEquals(unconfirmedEntry, forceCount)
        assertFalse(Files.exists(retryFailed.envelopePath("real")))
        val stages = mutableListOf<String>()
        val retry = DesktopAcquisitionStore(data) { stages += it.name }
        assertEquals(CommitResult.Committed, retry.compareAndSet("real", -1, state()))
        assertTrue("BeforeAncestorDirectoryFlush" in stages)
        assertEquals(state(), assertIs<LoadResult.Loaded>(DesktopAcquisitionStore(data).load("real")).state)
    }

    @Test fun abrupt_process_exit_after_replace_requires_recovery_before_replay() = runBlocking {
        val dir = Files.createTempDirectory("t12-recovery-process")
        val store = DesktopAcquisitionStore(dir)
        assertEquals(CommitResult.Committed, store.compareAndSet("real", -1, state()))
        val command = command(store)
        val process = ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
            "-cp", desktopStoreTestClasspath(), DesktopAcquisitionProcessProbe::class.java.name,
            dir.toString(), "unused", "halt-after-replace").redirectErrorStream(true).start()
        assertTrue(process.waitFor(30, java.util.concurrent.TimeUnit.SECONDS))
        assertEquals(17, process.exitValue(), process.inputStream.bufferedReader().readText())
        val unavailable = DesktopAcquisitionStore(dir) { if (it.name == "BeforeRecoveryDirectoryFlush") error("directory force unavailable") }
        assertIs<ApplyResult.StorageFailure>(service(unavailable).apply(command))
        val stages = mutableListOf<String>()
        val reopened = DesktopAcquisitionStore(dir) { stages += it.name }
        assertIs<ApplyResult.AlreadyApplied>(service(reopened).apply(command))
        assertTrue("AfterRecoveryDurability" in stages)
        assertEquals(Amount(80, 10), balance(assertIs<LoadResult.Loaded>(reopened.load("real")).state.ledger))
    }
}
