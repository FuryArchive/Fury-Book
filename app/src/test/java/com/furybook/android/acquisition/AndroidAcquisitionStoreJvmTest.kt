package com.furybook.android.acquisition

import com.furybook.android.data.*
import com.furybook.dubl.acquisition.*
import com.furybook.dubl.model.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption
import java.nio.file.StandardCopyOption

/** Actual host files/locks and Shared services. This is not an Android/device test. */
class AndroidAcquisitionStoreJvmTest {
    private fun store(root:Path, fault:(AndroidAcquisitionWriteStage)->Unit = {}) = AndroidAcquisitionStore(
        root, AndroidLegacyAcquisitionSource(root.resolve("shared_prefs")), JvmFileOps, fault=fault)
    @Test fun independent_migrated_copy_retains_provenance_without_foreign_preferences() = runBlocking {
        val raw=com.furybook.dubl.data.SnapshotCodec.encode(AppSnapshot(listOf(DublCharacter("real")),"real"))
        val origin=LegacyOrigin("real",com.furybook.dubl.data.SnapshotCodec.SCHEMA,legacySourceFingerprint(raw),raw,listOf("dubl-3.69"),extrasSource=LegacyExtrasSource(null))
        val pending=(prepareLegacy(raw,origin,null) as MigrationResult.NeedsBalanceConfirmation).pending
        val original=confirmLegacyBalance(pending,Amount(100,10),"Known test balance")
        val copied=copyAcquisitionCharacter(original,"independent","copy-operation")
        val root=Files.createTempDirectory("f1-independent");val target=store(root)
        assertEquals(CommitResult.Committed,target.compareAndSet("independent",-1,copied))
        val reopened=(store(root).load("independent") as LoadResult.Loaded).state
        assertEquals(copied,reopened);assertEquals(original.ledger.opening,reopened.ledger.opening)
        assertEquals(CommitResult.Committed,target.compareAndSet("independent",0,reopened.copy(storageRevision=1)))
        assertFalse(Files.exists(root.resolve("shared_prefs")))
    }
    @Test fun reopen_after_ack_keeps_receipt() = runBlocking {
        val root=Files.createTempDirectory("t13-host")
        val original=store(root)
        assertEquals(CommitResult.Committed,original.compareAndSet("real",-1,t13State()))
        val service=AcquisitionService(original,::t13Catalog) { "instance" }
        val request=t13Request();val p=service.preview(request)
        val command=ConfirmAcquisition("purchase","real",request,p.baseRevision,p.previewToken)
        assertTrue(service.apply(command) is ApplyResult.Applied)
        val reopened=store(root);val saved=(reopened.load("real") as LoadResult.Loaded).state
        assertEquals(Amount(80,10),balance(saved.ledger));assertEquals(1,saved.receipts.size);assertEquals(1,saved.outbox.size)
        assertTrue(AcquisitionService(reopened,::t13Catalog) { "unused" }.apply(command) is ApplyResult.AlreadyApplied)
        assertEquals(1,(reopened.load("real") as LoadResult.Loaded).state.receipts.size)
    }
    @Test fun storage_failure_before_replace_retains_old_bytes() = runBlocking {
        val root=Files.createTempDirectory("t13-host");val normal=store(root)
        normal.compareAndSet("real",-1,t13State());val bytes=Files.readAllBytes(normal.envelopePath("real"))
        val failed=store(root) { if(it==AndroidAcquisitionWriteStage.BeforeReplace) error("fault") }
        assertTrue(failed.compareAndSet("real",0,t13State().copy(storageRevision=1)) is CommitResult.StorageFailure)
        assertArrayEquals(bytes,Files.readAllBytes(normal.envelopePath("real")))
    }
    @Test fun corrupt_and_future_never_reset() = runBlocking {
        for(raw in listOf("{broken","{\"formatVersion\":99}")) {
            val root=Files.createTempDirectory("t13-host");val s=store(root)
            Files.createDirectories(s.envelopePath("real").parent);Files.write(s.envelopePath("real"),raw.toByteArray())
            assertFalse(s.load("real") is LoadResult.Loaded)
            assertTrue(s.compareAndSet("real",-1,t13State()) is CommitResult.StorageFailure)
            assertEquals(raw,String(s.recoveryExport("real").getValue("envelope.json")))
        }
    }
}
internal object JvmFileOps:AndroidAcquisitionFileOps {
    override fun atomicReplace(temp:Path,head:Path) { Files.move(temp,head,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING) }
    override fun forceFile(path:Path) { FileChannel.open(path,StandardOpenOption.WRITE).use { it.force(true) } }
    override fun forceDirectory(path:Path) { FileChannel.open(path,StandardOpenOption.READ).use { it.force(true) } }
}
internal fun t13State() = StoredCharacter(DublCharacter("real"),CharacterSheetExtras(),emptyList(),
    Ledger(Opening("new/real","synthetic",Amount(100,10),RequirementNode.CreationPhase.Phase.Creating,OpeningProvenance.NewCharacter),emptyList()),
    emptyList(),emptyList(),SyncMetadata(0),0,0,RequirementNode.CreationPhase.Phase.Creating)
internal fun t13Catalog():AcquisitionCatalog {
    val ref=DefinitionRef("dubl-3.69","synthetic-storage")
    val source=RuleSourceRef("test-source","1","Synthetic storage fixture")
    return AcquisitionCatalog(mapOf(ref to AcquisitionDefinition(ref,AcquisitionKind.DEVELOPMENT,1,RepeatPolicy.Single,
        RequirementNode.AllOf("none",source,emptyList(),NoRequirementsEvidence(source,"None")),PriceRule.Fixed(20,0,source),SourceStatus.Confirmed,"Synthetic storage purchase",source)),"test","test")
}
internal fun t13Request()=AcquisitionRequest("real",listOf(AcquisitionTarget(DefinitionRef("dubl-3.69","synthetic-storage"),"instance",1)))
