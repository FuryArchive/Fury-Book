package com.furybook.dubl.acquisition

import com.furybook.content.FcpTextSource
import com.furybook.dubl.content.DublFcp
import com.furybook.dubl.data.*
import com.furybook.dubl.model.*
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import kotlin.test.*

/** Executes native Windows paths on the Windows packaging runner; POSIX regression stays unchanged. */
class AcquisitionWindowsPublicationTest {
    private fun initial() = StoredCharacter(DublCharacter("windows",attributes=DublCharacter("windows").attributes+
        (AttributeId.DEXTERITY to AttributeValue(4))),CharacterSheetExtras(),emptyList(),
        Ledger(Opening("opening","confirmed-existing-balance",Amount(1000,5),RequirementNode.CreationPhase.Phase.Creating,
            OpeningProvenance.UserConfirmed,"Existing balance"),emptyList()),emptyList(),emptyList(),SyncMetadata(0),0,0,
        RequirementNode.CreationPhase.Phase.Creating)

    @Test fun real_trap_purchase_reopens_and_undoes_in_a_long_unicode_directory() = runBlocking {
        if (!DesktopAcquisitionFiles.isWindows) return@runBlocking
        var directory=Files.createTempDirectory("furybook-окно-")
        repeat(5) { directory=directory.resolve("Персонаж-"+"x".repeat(48)+it) }
        val store=DesktopAcquisitionStore(directory)
        val before=initial()
        assertEquals(CommitResult.Committed,store.compareAndSet("windows",-1,before))
        assertTrue(store.envelopePath("windows").toString().length>260)
        val catalog=DublFcp.open(FcpTextSource { javaClass.classLoader.getResource(it)?.readText() }).loadAcquisition().catalog
        val service=AcquisitionService(store,{catalog},{"id"})
        val request=AcquisitionRequest("windows",listOf(AcquisitionTarget(DefinitionRef("dubl-3.69","feat_ff2b2532be3cc900"),targetRank=1)))
        val preview=service.preview(request)
        assertTrue(preview.executable,preview.issues.toString())
        assertEquals(Amount(30,2),preview.cost)
        assertIs<ApplyResult.Applied>(service.apply(ConfirmAcquisition("buy-trap","windows",request,preview.baseRevision,preview.previewToken)))
        val reopened=DesktopAcquisitionStore(directory)
        val paid=assertIs<LoadResult.Loaded>(reopened.load("windows")).state
        assertEquals(Amount(970,3),balance(paid.ledger))
        assertEquals(1,paid.instances.count { it.definition.entryId=="access_d0fdba646d5da299" })
        assertIs<ApplyResult.Applied>(AcquisitionUndo(reopened).undo("windows","undo-trap",paid.revision))
        assertEquals(acquisitionProjectionCanonical(before),acquisitionProjectionCanonical(assertIs<LoadResult.Loaded>(reopened.load("windows")).state))
    }

    @Test fun unknown_pending_directory_data_is_preserved_without_blocking_a_disjoint_store() = runBlocking {
        if (!DesktopAcquisitionFiles.isWindows) return@runBlocking
        val root=Files.createTempDirectory("furybook-init-isolation-")
        val interrupted=root.resolve("pending/character-data")
        val failed=DesktopAcquisitionStore(interrupted) { if(it==DesktopAcquisitionWriteStage.AfterDirectoryCreate) error("interrupted initialization") }
        assertIs<CommitResult.StorageFailure>(failed.compareAndSet("windows",-1,initial()))
        val sentinel=root.resolve("pending/preserve.txt")
        Files.writeString(sentinel,"unknown user data")
        val healthy=DesktopAcquisitionStore(root.resolve("healthy"))
        assertEquals(CommitResult.Committed,healthy.compareAndSet("windows",-1,initial()))
        assertEquals("unknown user data",Files.readString(sentinel))
        assertIs<CommitResult.StorageFailure>(DesktopAcquisitionStore(interrupted).compareAndSet("windows",-1,initial()))
        assertEquals("unknown user data",Files.readString(sentinel))
        Files.delete(sentinel) // Remove only the bytes introduced by this test, then exercise native recovery.
        assertEquals(CommitResult.Committed,DesktopAcquisitionStore(interrupted).compareAndSet("windows",-1,initial()))
        assertEquals(initial(),assertIs<LoadResult.Loaded>(DesktopAcquisitionStore(interrupted).load("windows")).state)
        assertEquals(initial(),assertIs<LoadResult.Loaded>(healthy.load("windows")).state)
    }
}
