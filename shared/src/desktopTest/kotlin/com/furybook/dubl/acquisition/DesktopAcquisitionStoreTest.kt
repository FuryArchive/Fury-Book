package com.furybook.dubl.acquisition

import com.furybook.dubl.data.*
import com.furybook.dubl.model.*
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread
import kotlin.test.*

/** Real files, service, codec and operating-system locks. No simulated storage implementation. */
class DesktopAcquisitionStoreTest {
    private fun directory(): Path = Files.createTempDirectory("fury-acquisition-test")
    private fun state(id: String = "real") = StoredCharacter(
        DublCharacter(id), CharacterSheetExtras(), emptyList(),
        Ledger(Opening("new/$id", "test-source", Amount(100, 10), RequirementNode.CreationPhase.Phase.Creating,
            OpeningProvenance.NewCharacter), emptyList()), emptyList(), emptyList(), SyncMetadata(0), 0, 0,
        RequirementNode.CreationPhase.Phase.Creating)
    private fun catalog() = desktopStoreTestCatalog()
    private suspend fun buy(store: DesktopAcquisitionStore, operationId: String = "buy"): ApplyResult {
        val service = AcquisitionService(store, ::catalog) { "new-instance" }
        val request = AcquisitionRequest("real", listOf(AcquisitionTarget(DefinitionRef("dubl-3.69", "test-store-purchase"), "new-instance", 1)))
        val preview = service.preview(request)
        return service.apply(ConfirmAcquisition(operationId, "real", request, preview.baseRevision, preview.previewToken))
    }
    @Test fun independent_migrated_copy_reopens_and_writes_without_foreign_legacy_files() = runBlocking {
        val originalRoot=directory()
        val raw=SnapshotCodec.encode(AppSnapshot(listOf(DublCharacter("real")),"real"))
        Files.writeString(originalRoot.resolve("characters.json"),raw)
        val originalStore=DesktopAcquisitionStore(originalRoot)
        val pending=assertIs<MigrationResult.NeedsBalanceConfirmation>(originalStore.prepareMigration("real",listOf("dubl-3.69"),null)).pending
        val original=confirmLegacyBalance(pending,Amount(100,10),"Known test balance")
        assertEquals(CommitResult.Committed,originalStore.compareAndSet("real",-1,original))
        val copied=copyAcquisitionCharacter(original,"independent","copy-operation")
        val targetRoot=directory();val target=DesktopAcquisitionStore(targetRoot)
        assertEquals(CommitResult.Committed,target.compareAndSet("independent",-1,copied))
        val reopened=assertIs<LoadResult.Loaded>(DesktopAcquisitionStore(targetRoot).load("independent")).state
        assertEquals(copied,reopened);assertEquals(original.ledger.opening,reopened.ledger.opening)
        assertEquals(CommitResult.Committed,target.compareAndSet("independent",0,reopened.copy(storageRevision=1)))
        assertFalse(Files.exists(targetRoot.resolve("characters.json")))
        assertFalse(Files.exists(targetRoot.resolve("sheet-extras.json")))
        Files.writeString(originalRoot.resolve("characters.json"),raw+"\n")
        assertIs<LoadResult.LegacyImportConflict>(originalStore.load("real"))
        assertIs<LoadResult.Loaded>(target.load("independent"))
        Unit
    }
    @Test fun temp_fault_before_replace_preserves_100_and_old_bytes() = runBlocking {
        val dir = directory(); val normal = DesktopAcquisitionStore(dir)
        assertEquals(CommitResult.Committed, normal.compareAndSet("real", -1, state()))
        val original = Files.readAllBytes(normal.envelopePath("real"))
        val failed = DesktopAcquisitionStore(dir) { if (it == DesktopAcquisitionWriteStage.BeforeReplace) error("fault") }
        assertIs<ApplyResult.StorageFailure>(buy(failed))
        val reopened = assertIs<LoadResult.Loaded>(DesktopAcquisitionStore(dir).load("real")).state
        assertEquals(Amount(100, 10), balance(reopened.ledger)); assertTrue(reopened.receipts.isEmpty())
        assertContentEquals(original, Files.readAllBytes(normal.envelopePath("real")))
    }
    @Test fun lost_reply_after_replace_reopens_80_receipt_outbox_and_replays_once() = runBlocking {
        val dir = directory(); val normal = DesktopAcquisitionStore(dir)
        normal.compareAndSet("real", -1, state())
        val service = AcquisitionService(DesktopAcquisitionStore(dir) {
            if (it == DesktopAcquisitionWriteStage.AfterReplace) error("lost reply")
        }, ::catalog) { "instance" }
        val request = AcquisitionRequest("real", listOf(AcquisitionTarget(DefinitionRef("dubl-3.69", "test-store-purchase"), "instance", 1)))
        val preview = service.preview(request)
        val command = ConfirmAcquisition("purchase", "real", request, preview.baseRevision, preview.previewToken)
        assertIs<ApplyResult.StorageFailure>(service.apply(command))
        val reopenedStore = DesktopAcquisitionStore(dir)
        val saved = assertIs<LoadResult.Loaded>(reopenedStore.load("real")).state
        assertEquals(Amount(80, 10), balance(saved.ledger)); assertEquals("purchase", saved.receipts.single().operationId)
        assertEquals("purchase", saved.outbox.single().operationId)
        assertIs<ApplyResult.AlreadyApplied>(AcquisitionService(reopenedStore, ::catalog) { "unused" }.apply(command))
        assertEquals(saved, assertIs<LoadResult.Loaded>(reopenedStore.load("real")).state)
    }
    @Test fun corrupt_and_future_envelopes_are_never_reset_or_overwritten() = runBlocking {
        for ((raw, future) in listOf("{broken" to false, "{\"formatVersion\":99}" to true)) {
            val dir = directory(); val store = DesktopAcquisitionStore(dir)
            Files.createDirectories(store.envelopePath("real").parent)
            Files.writeString(store.envelopePath("real"), raw)
            if (future) assertIs<LoadResult.Unsupported>(store.load("real")) else assertIs<LoadResult.Corrupt>(store.load("real"))
            assertIs<CommitResult.StorageFailure>(store.compareAndSet("real", -1, state()))
            assertEquals(raw, Files.readString(store.envelopePath("real")))
        }
    }
    @Test fun separate_instances_commit_only_one_shared_revision() = runBlocking {
        val dir = directory(); DesktopAcquisitionStore(dir).compareAndSet("real", -1, state())
        val barrier = CountDownLatch(1); val results = java.util.Collections.synchronizedList(mutableListOf<CommitResult>())
        val workers = (1..2).map { thread {
            barrier.await(); results += runBlocking { DesktopAcquisitionStore(dir).compareAndSet("real", 0, state().copy(storageRevision = 1)) }
        } }
        barrier.countDown(); workers.forEach { it.join() }
        assertEquals(1, results.count { it == CommitResult.Committed }); assertEquals(1, results.count { it == CommitResult.Conflict })
        assertEquals(1, assertIs<LoadResult.Loaded>(DesktopAcquisitionStore(dir).load("real")).state.storageRevision)
    }
    @Test fun two_processes_share_revision_lock_and_reopen_committed_state() = runBlocking {
        val dir = directory(); DesktopAcquisitionStore(dir).compareAndSet("real", -1, state())
        val javaCommand = Path.of(System.getProperty("java.home"), "bin", "java").toString()
        val classpath = desktopStoreTestClasspath()
        val gate = dir.resolve("go")
        val processes = (1..2).map {
            ProcessBuilder(javaCommand, "-cp", classpath, DesktopAcquisitionProcessProbe::class.java.name, dir.toString(), gate.toString())
                .redirectErrorStream(true).start()
        }
        Files.writeString(gate, "go")
        val outputs = processes.map { process ->
            assertTrue(process.waitFor(30, java.util.concurrent.TimeUnit.SECONDS), "Child did not terminate")
            val output = process.inputStream.bufferedReader().readText()
            assertEquals(0, process.exitValue(), output)
            output.lineSequence().filter { it == "Committed" || it == "Conflict" }.single()
        }
        assertEquals(1, outputs.count { it == "Committed" }, outputs.toString())
        assertEquals(1, outputs.count { it == "Conflict" }, outputs.toString())
        assertEquals(1, assertIs<LoadResult.Loaded>(DesktopAcquisitionStore(dir).load("real")).state.storageRevision)
    }
    @Test fun process_exit_after_durable_commit_reopens_exact_80_receipt_and_outbox() = runBlocking {
        val dir=directory(); DesktopAcquisitionStore(dir).compareAndSet("real",-1,state())
        val process=ProcessBuilder(Path.of(System.getProperty("java.home"),"bin","java").toString(),"-cp",desktopStoreTestClasspath(),
            DesktopAcquisitionProcessProbe::class.java.name,dir.toString(),"unused","halt-after-durable-commit")
            .redirectErrorStream(true).start()
        assertTrue(process.waitFor(30,java.util.concurrent.TimeUnit.SECONDS))
        assertEquals(17,process.exitValue(),process.inputStream.bufferedReader().readText())
        val restored=assertIs<LoadResult.Loaded>(DesktopAcquisitionStore(dir).load("real")).state
        assertEquals(Amount(80,10),balance(restored.ledger))
        assertEquals("purchase",restored.receipts.single().operationId)
        assertEquals("purchase",restored.outbox.single().operationId)
    }
    @Test fun migration_captures_exact_characters_and_all_extras_once_then_detects_either_change() = runBlocking {
        val dir = directory(); val character = DublCharacter("real", creationComplete = false)
        val raw = SnapshotCodec.encode(AppSnapshot(listOf(character), "real"))
        Files.writeString(dir.resolve("characters.json"), raw)
        val extras = CharacterSheetExtras(portraitUri="local:portrait", activeConditions=setOf(CharacterConditionId.BLINDNESS),
            hiddenResourceIds=setOf(CharacterSheetResourceId.MANA), preferredSkillAttributes=mapOf("skill" to AttributeId.DEXTERITY),
            skillGroups=listOf(SheetGroup("skills", "Skills", listOf("skill"))),
            developmentGroups=listOf(SheetGroup("development", "Development", listOf("feat"))),
            conditionOverrides=mapOf(CharacterConditionId.BLINDNESS to ConditionLocalOverride("Dark", "Details")),
            customConditions=listOf(CustomCondition("custom", "Custom", "Description", true)),
            notes="legacy note\nРусский", noteEntries=listOf(CharacterNote("note", "Title", "Body")))
        DesktopCharacterExtrasStore(dir.resolve("sheet-extras.json")).save("real", extras)
        // Reproduce the pre-migration desktop writer's literal separator bytes.
        val extrasRaw = Files.readString(dir.resolve("sheet-extras.json")).replace("\\u001f", "\u001f").replace("\\u001e", "\u001e")
        Files.writeString(dir.resolve("sheet-extras.json"), extrasRaw)
        val store = DesktopAcquisitionStore(dir)
        val pending = assertIs<MigrationResult.NeedsBalanceConfirmation>(store.prepareMigration("real", listOf("dubl-3.69"), null)).pending
        assertIs<LoadResult.MigrationRequired>(store.load("real"))
        val migrated = confirmLegacyBalance(pending, Amount(-5, 3), "Known current balance")
        assertEquals(extras, migrated.extras); assertFalse(migrated.character.creationComplete)
        assertEquals(CommitResult.Committed, store.compareAndSet("real", -1, migrated))
        val reopened = assertIs<LoadResult.Loaded>(DesktopAcquisitionStore(dir).load("real")).state
        assertEquals(migrated, reopened); assertEquals(raw, Files.readString(dir.resolve("characters.json")))
        assertEquals(extrasRaw, Files.readString(dir.resolve("sheet-extras.json")))
        assertEquals(migrated.ledger.opening.migrationId,
            confirmLegacyBalance(pending, Amount(-5, 3), "Known current balance").ledger.opening.migrationId)
        Files.writeString(dir.resolve("sheet-extras.json"), extrasRaw + "\n")
        assertIs<LoadResult.LegacyImportConflict>(store.load("real"))
        assertIs<CommitResult.StorageFailure>(store.compareAndSet("real", 0, migrated.copy(storageRevision=1)))
        Files.writeString(dir.resolve("sheet-extras.json"), extrasRaw)
        Files.writeString(dir.resolve("characters.json"), raw + "\n")
        assertIs<LoadResult.LegacyImportConflict>(store.load("real"))
        assertEquals(migrated, assertIs<LoadResult.Loaded>(decodeEnvelope(Files.readString(store.envelopePath("real")))).state)
    }
    @Test fun changed_source_before_initial_migration_commit_conflicts() = runBlocking {
        val dir=directory(); val raw=SnapshotCodec.encode(AppSnapshot(listOf(DublCharacter("real")),"real"))
        Files.writeString(dir.resolve("characters.json"),raw)
        val store=DesktopAcquisitionStore(dir)
        val pending=assertIs<MigrationResult.NeedsBalanceConfirmation>(store.prepareMigration("real",listOf("dubl-3.69"),null)).pending
        val next=confirmLegacyBalance(pending,Amount(100,10),"Known balance")
        Files.writeString(dir.resolve("sheet-extras.json"),"{\"version\":1,\"characters\":{}}")
        assertEquals(CommitResult.Conflict,store.compareAndSet("real",-1,next))
        assertFalse(Files.exists(store.envelopePath("real")))
    }
    @Test fun unknown_legacy_ownership_blocks_a_duplicate_after_balance_confirmation() = runBlocking {
        val dir=directory()
        val old=DublCharacter("real",development=mapOf("test-store-purchase" to OwnedDevelopment(1)))
        Files.writeString(dir.resolve("characters.json"),SnapshotCodec.encode(AppSnapshot(listOf(old),"real")))
        val store=DesktopAcquisitionStore(dir)
        val pending=assertIs<MigrationResult.NeedsBalanceConfirmation>(store.prepareMigration("real",listOf("dubl-3.69"),null)).pending
        val next=confirmLegacyBalance(pending,Amount(100,10),"Known balance")
        assertEquals(CommitResult.Committed,store.compareAndSet("real",-1,next))
        assertIs<ApplyResult.Blocked>(buy(store))
        assertEquals(next,assertIs<LoadResult.Loaded>(DesktopAcquisitionStore(dir).load("real")).state)
    }
    @Test fun an_orphan_flushed_temp_is_never_promoted_over_the_committed_head() = runBlocking {
        val dir=directory(); val store=DesktopAcquisitionStore(dir)
        store.compareAndSet("real",-1,state())
        Files.writeString(store.envelopePath("real").resolveSibling("orphan.tmp"),"{broken")
        assertEquals(state(),assertIs<LoadResult.Loaded>(DesktopAcquisitionStore(dir).load("real")).state)
    }
    @Test fun migrated_legacy_without_the_extras_source_is_read_only() = runBlocking {
        val dir=directory(); val raw=SnapshotCodec.encode(AppSnapshot(listOf(DublCharacter("real")),"real"))
        Files.writeString(dir.resolve("characters.json"),raw)
        val pending=(prepareLegacy(raw,LegacyOrigin("real",SnapshotCodec.SCHEMA,"old-source",raw,listOf("dubl-3.69")),null) as MigrationResult.NeedsBalanceConfirmation).pending
        val incomplete=confirmLegacyBalance(pending,Amount(100,10),"Known balance")
        val store=DesktopAcquisitionStore(dir)
        Files.createDirectories(store.envelopePath("real").parent)
        Files.writeString(store.envelopePath("real"),encodeEnvelope(incomplete))
        assertIs<LoadResult.Unsupported>(store.load("real"))
        assertIs<CommitResult.StorageFailure>(store.compareAndSet("real",0,incomplete.copy(storageRevision=1)))
        assertEquals(encodeEnvelope(incomplete),Files.readString(store.envelopePath("real")))
    }
    @Test fun invalid_utf8_is_corrupt_and_bytes_are_retained() = runBlocking {
        val dir=directory(); val store=DesktopAcquisitionStore(dir)
        val bytes=byteArrayOf(0xc3.toByte(),0x28)
        Files.createDirectories(store.envelopePath("real").parent)
        Files.write(store.envelopePath("real"),bytes)
        assertIs<LoadResult.Corrupt>(store.load("real"))
        assertIs<CommitResult.StorageFailure>(store.compareAndSet("real",-1,state()))
        assertContentEquals(bytes,Files.readAllBytes(store.envelopePath("real")))
    }
    @Test fun wrong_scope_and_nonincrementing_revisions_cannot_write() = runBlocking {
        val dir=directory(); val store=DesktopAcquisitionStore(dir)
        assertIs<CommitResult.StorageFailure>(store.compareAndSet("real",-1,state("other")))
        assertEquals(CommitResult.Conflict,store.compareAndSet("real",-1,state().copy(storageRevision=1)))
        assertEquals(CommitResult.Committed,store.compareAndSet("real",-1,state()))
        assertEquals(CommitResult.Conflict,store.compareAndSet("real",0,state()))
        assertEquals(state(),assertIs<LoadResult.Loaded>(store.load("real")).state)
    }
    @Test fun corrupt_legacy_and_future_extras_refuse_migration() {
        val dir=directory(); val store=DesktopAcquisitionStore(dir)
        Files.writeString(dir.resolve("characters.json"),"broken")
        assertFails { DesktopCharacterStore(dir.resolve("characters.json")).load() }
        assertIs<MigrationResult.Corrupt>(store.prepareMigration("real",listOf("dubl-3.69"),null))
        Files.writeString(dir.resolve("characters.json"),SnapshotCodec.encode(AppSnapshot(listOf(DublCharacter("real")),"real")))
        Files.writeString(dir.resolve("sheet-extras.json"),"{\"version\":999,\"characters\":{}}")
        assertIs<MigrationResult.Unsupported>(store.prepareMigration("real",listOf("dubl-3.69"),null))
        // Viewing a character is independent of whether the financial migration is supported.
        assertEquals(CharacterSheetExtras(), DesktopCharacterExtrasStore(dir.resolve("sheet-extras.json")).load("real"))
        assertFails { DesktopCharacterExtrasStore(dir.resolve("sheet-extras.json")).save("real", CharacterSheetExtras()) }
    }
}

internal fun desktopStoreTestCatalog(): AcquisitionCatalog {
    val source = RuleSourceRef("test-source", "1", "A confirmed fixture costs 20 XP and has no requirements")
    val ref = DefinitionRef("dubl-3.69", "test-store-purchase")
    val definition = AcquisitionDefinition(ref, AcquisitionKind.DEVELOPMENT, 1, RepeatPolicy.Single,
        RequirementNode.AllOf("none", source, emptyList(), NoRequirementsEvidence(source, "None")),
        PriceRule.Fixed(20, 0, source), SourceStatus.Confirmed, "Store fixture", source)
    return AcquisitionCatalog(mapOf(ref to definition), "test-catalog", "test-resolution")
}

internal fun desktopStoreTestClasspath(): String {
    val urls = generateSequence(DesktopAcquisitionProcessProbe::class.java.classLoader) { it.parent }
        .filterIsInstance<java.net.URLClassLoader>().flatMap { it.getURLs().asSequence() }
        .filter { it.protocol == "file" }.map { Path.of(it.toURI()).toString() }.toList()
    return (urls + System.getProperty("java.class.path").split(java.io.File.pathSeparator))
        .distinct().joinToString(java.io.File.pathSeparator)
}

/** Separate JVM entry point deliberately uses production OS locking and CAS. */
object DesktopAcquisitionProcessProbe {
    @JvmStatic fun main(args: Array<String>) = runBlocking {
        if (args.getOrNull(2) in listOf("halt-after-durable-commit", "halt-after-replace")) {
            val store=DesktopAcquisitionStore(Path.of(args[0])) { stage ->
                if(stage == (if(args[2] == "halt-after-replace") DesktopAcquisitionWriteStage.AfterReplace else DesktopAcquisitionWriteStage.AfterDirectoryFlush)) Runtime.getRuntime().halt(17)
            }
            val service=AcquisitionService(store,::desktopStoreTestCatalog) { "instance" }
            val request=AcquisitionRequest("real",listOf(AcquisitionTarget(DefinitionRef("dubl-3.69","test-store-purchase"),"instance",1)))
            val preview=service.preview(request)
            service.apply(ConfirmAcquisition("purchase","real",request,preview.baseRevision,preview.previewToken))
            error("Expected process termination at durable commit")
        }
        val store = DesktopAcquisitionStore(Path.of(args[0]))
        while (!Files.exists(Path.of(args[1]))) Thread.sleep(5)
        val original = assertIs<LoadResult.Loaded>(store.load("real")).state
        println(store.compareAndSet("real", 0, original.copy(storageRevision=1)))
    }
}
