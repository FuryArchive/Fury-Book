package com.furybook.dubl.acquisition

import com.furybook.dubl.data.*
import com.furybook.dubl.model.*
import kotlinx.coroutines.runBlocking
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.*
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.test.*

/** Raw-file characterization, not a complete product backup API or power-loss proof. */
class DesktopAcquisitionRolloutTest {
    private data class Fixture(val root: Path, val state: StoredCharacter, val command: ConfirmAcquisition,
        val files: Map<Path, ByteArray>)
    private suspend fun fixture(): Fixture {
        val root=Files.createTempDirectory("fury-rollout-original")
        val character=DublCharacter("real",name="Исходный персонаж",creationComplete=false)
        Files.writeString(root.resolve("characters.json"),SnapshotCodec.encode(AppSnapshot(listOf(character),"real")))
        DesktopCharacterExtrasStore(root.resolve("sheet-extras.json")).save("real",CharacterSheetExtras(
            notes="Заметка до миграции\nВторая строка",noteEntries=listOf(CharacterNote("note","Название","Текст"))))
        val store=DesktopAcquisitionStore(root)
        val pending=assertIs<MigrationResult.NeedsBalanceConfirmation>(store.prepareMigration("real",listOf("dubl-3.69"),null)).pending
        val migrated=confirmLegacyBalance(pending,Amount(100,10),"Explicit synthetic opening")
        assertEquals(CommitResult.Committed,store.compareAndSet("real",-1,migrated))
        val service=AcquisitionService(store,::desktopStoreTestCatalog){"unused"}
        val request=AcquisitionRequest("real",listOf(AcquisitionTarget(DefinitionRef("dubl-3.69","test-store-purchase"),"holding",1)))
        val preview=service.preview(request)
        val command=ConfirmAcquisition("purchase","real",request,preview.baseRevision,preview.previewToken)
        assertIs<ApplyResult.Applied>(service.apply(command))
        val state=assertIs<LoadResult.Loaded>(store.load("real")).state
        assertEquals(Amount(80,10),balance(state.ledger));assertEquals(Amount(-20,0),state.ledger.entries.single().amountDelta)
        val paths=listOf(Path.of("characters.json"),Path.of("sheet-extras.json"),root.relativize(store.envelopePath("real")))
        return Fixture(root,state,command,paths.associateWith { Files.readAllBytes(root.resolve(it)) })
    }
    /** Publish only to an absent sibling destination; no overwrite or arbitrary source traversal. */
    private fun copyRaw(files: Map<Path,ByteArray>, destination: Path) {
        require(Files.notExists(destination,LinkOption.NOFOLLOW_LINKS))
        val stage=Files.createTempDirectory(destination.parent,"staged-")
        for((relative,bytes) in files) {
            require(!relative.isAbsolute && relative.normalize()==relative && !relative.startsWith(".."))
            val target=stage.resolve(relative).normalize();require(target.startsWith(stage))
            Files.createDirectories(target.parent)
            FileChannel.open(target,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE).use { channel ->
                val buffer=ByteBuffer.wrap(bytes);while(buffer.hasRemaining()) channel.write(buffer);channel.force(true)
            }
            assertContentEquals(bytes,Files.readAllBytes(target))
            assertContentEquals(MessageDigest.getInstance("SHA-256").digest(bytes),MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(target)))
        }
        // POSIX directory fsync is unavailable on Windows. Native store durability is covered
        // by AcquisitionWindowsPublicationTest; this helper only characterizes exact-byte copies.
        if (!DesktopAcquisitionFiles.isWindows) Files.walk(stage).use { paths -> paths.filter { Files.isDirectory(it,LinkOption.NOFOLLOW_LINKS) }
            .sorted(Comparator.reverseOrder()).forEach { FileChannel.open(it,StandardOpenOption.READ).use { c -> c.force(true) } } }
        Files.move(stage,destination,StandardCopyOption.ATOMIC_MOVE)
        if (!DesktopAcquisitionFiles.isWindows) FileChannel.open(destination.parent,StandardOpenOption.READ).use { it.force(true) }
        verify(files,destination)
    }
    private fun verify(files: Map<Path,ByteArray>,root: Path) {
        files.forEach { (relative,bytes) -> assertContentEquals(bytes,Files.readAllBytes(root.resolve(relative))) }
    }
    private fun restored(f: Fixture): Path {
        val parent=Files.createTempDirectory("fury-rollout-backup")
        val backup=parent.resolve("backup");copyRaw(f.files,backup)
        val restored=parent.resolve("restored")
        copyRaw(f.files.keys.associateWith { Files.readAllBytes(backup.resolve(it)) },restored)
        return restored
    }
    @Test fun backup_restore_retains_receipts() = runBlocking {
        val f=fixture();val root=restored(f);verify(f.files,root)
        val process=ProcessBuilder(Path.of(System.getProperty("java.home"),"bin","java").toString(),"-cp",desktopStoreTestClasspath(),
            DesktopAcquisitionRestoreProbe::class.java.name,root.toString(),ProcessHandle.current().pid().toString())
            .redirectErrorStream(true).start()
        val stopped=process.waitFor(30,TimeUnit.SECONDS)
        if(!stopped) process.destroyForcibly()
        assertTrue(stopped,"Restore child timed out")
        val output=process.inputStream.bufferedReader().readText();assertEquals(0,process.exitValue(),output)
        assertTrue(output.contains("FRESH_RESTORE_OK"),output)
        val store=DesktopAcquisitionStore(root);val state=assertIs<LoadResult.Loaded>(store.load("real")).state
        assertEquals(f.state,state)
        // Replay must use its persisted receipt even if today's provider would return a different price.
        var catalogReads=0;var ids=0
        val replay=AcquisitionService(store,{catalogReads++;val c=desktopStoreTestCatalog();c.copy(definitions=c.definitions.mapValues { (_,d)->d.copy(priceRule=PriceRule.Fixed(30,0,d.sourceRef)) })}){ids++;"new"}
        assertIs<ApplyResult.AlreadyApplied>(replay.apply(f.command))
        assertEquals(0,catalogReads);assertEquals(0,ids)
        assertEquals(state,assertIs<LoadResult.Loaded>(store.load("real")).state)
        verify(f.files,root);verify(f.files,f.root)
    }
    @Test fun backup_missing_or_changed_legacy_sources_refuse_write_and_retain_paid_head() = runBlocking {
        val f=fixture()
        for(name in listOf("characters.json","sheet-extras.json")) for(missing in listOf(true,false)) {
            val root=restored(f);val file=root.resolve(name)
            if(missing) Files.delete(file) else Files.writeString(file,Files.readString(file)+"\n")
            val store=DesktopAcquisitionStore(root)
            assertIs<LoadResult.LegacyImportConflict>(store.load("real"))
            assertIs<CommitResult.StorageFailure>(store.compareAndSet("real",f.state.storageRevision,f.state.copy(storageRevision=f.state.storageRevision+1)))
            val relative=f.root.relativize(DesktopAcquisitionStore(f.root).envelopePath("real"))
            assertContentEquals(f.files.getValue(relative),Files.readAllBytes(store.envelopePath("real")))
        }
        verify(f.files,f.root)
    }
    @Test fun rollback_old_client_cannot_overwrite_new_canonical_head() = runBlocking {
        val f=fixture();val root=restored(f)
        val old=DesktopCharacterStore(root.resolve("characters.json"));val snapshot=old.load()
        old.save(snapshot.copy(characters=snapshot.characters.map { it.copy(name="Old writer change") }))
        val store=DesktopAcquisitionStore(root)
        assertIs<LoadResult.LegacyImportConflict>(store.load("real"))
        assertIs<CommitResult.StorageFailure>(store.compareAndSet("real",f.state.storageRevision,f.state.copy(storageRevision=f.state.storageRevision+1)))
        val relative=f.root.relativize(DesktopAcquisitionStore(f.root).envelopePath("real"))
        assertContentEquals(f.files.getValue(relative),Files.readAllBytes(store.envelopePath("real")))
        assertContentEquals(f.files.getValue(Path.of("sheet-extras.json")),Files.readAllBytes(root.resolve("sheet-extras.json")))
        verify(f.files,f.root)
    }
}

object DesktopAcquisitionRestoreProbe {
    @JvmStatic fun main(args: Array<String>) = runBlocking {
        check(ProcessHandle.current().pid()!=args[1].toLong())
        val state=assertIs<LoadResult.Loaded>(DesktopAcquisitionStore(Path.of(args[0])).load("real")).state
        assertEquals(Amount(80,10),balance(state.ledger))
        assertEquals("purchase",state.receipts.single().operationId)
        assertEquals("purchase",state.outbox.single().operationId)
        assertNotNull(state.ledger.opening.legacyMigration)
        assertEquals("Заметка до миграции\nВторая строка",state.extras.notes)
        println("FRESH_RESTORE_OK pid=${ProcessHandle.current().pid()}")
    }
}
