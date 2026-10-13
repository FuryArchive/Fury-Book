package com.furybook.desktop

import com.furybook.dubl.acquisition.*
import com.furybook.dubl.data.*
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.test.*

class DesktopAcquisitionRoutingTest {
    @Test fun empty_bootstrap_explicit_funding_and_restart_use_only_canonical_heads()=runBlocking<Unit> {
        val directory=Files.createTempDirectory("t17-desktop")
        fun app()=DesktopAppState(DesktopCharacterStore(directory.resolve("characters.json")),DesktopCharacterExtrasStore(directory.resolve("sheet-extras.json")),true,DesktopAcquisitionStore(directory))
        val state=app();state.initializeCanonicalView();assertTrue(state.unfundedPlaceholder)
        val before=state.snapshot
        state.changeAttribute(com.furybook.dubl.model.AttributeId.STRENGTH,1);assertEquals(before,state.snapshot)
        val review=state.reviewNewCharacterFunding(2500)
        assertEquals(2L,review.funding?.initialAP)
        assertTrue(DesktopAcquisitionStore(directory).characterIds().isEmpty())
        val committed=state.confirmNewCharacterFunding(review.key)
        assertEquals(AcquisitionUiPhase.Committed,committed.phase);assertFalse(state.unfundedPlaceholder)
        val id=state.snapshot.activeCharacterId
        state.confirmNewCharacterFunding(review.key)
        assertEquals(listOf(id),DesktopAcquisitionStore(directory).characterIds())
        val reopened=app();reopened.initializeCanonicalView()
        assertEquals(id,reopened.snapshot.activeCharacterId)
        assertEquals(Amount(2500,2),balance((reopened.localStoredCharacter() as LoadResult.Loaded).state.ledger))
        assertFalse(Files.exists(directory.resolve("characters.json")));assertFalse(Files.exists(directory.resolve("sheet-extras.json")))
        val quoted=state.acquisition.preview(AcquisitionIntent.attribute(com.furybook.dubl.model.AttributeId.STRENGTH,1))
        assertEquals(AcquisitionUiPhase.Ready,quoted.phase)
        assertEquals(AcquisitionUiPhase.Committed,state.acquisition.confirm(quoted.key).phase)
        assertEquals(1,(DesktopAcquisitionStore(directory).load(id) as LoadResult.Loaded).state.receipts.size)
        assertFalse(state.cloudLedgerV2)
    }
    @Test fun default_mode_does_not_enable_durable_purchases() {
        assertFalse(DesktopAppState().localAcquisitionEnabled)
    }
    @Test fun default_false_restart_of_migrated_head_is_readonly_before_legacy_transform()=runBlocking<Unit> {
        val directory=Files.createTempDirectory("t17-readonly-restart")
        val file=directory.resolve("characters.json")
        val legacy=SnapshotCodec.encode(com.furybook.dubl.model.AppSnapshot(listOf(com.furybook.dubl.model.DublCharacter("legacy")),"legacy"))
        Files.writeString(file,legacy)
        val store=DesktopAcquisitionStore(directory)
        val pending=assertIs<MigrationResult.NeedsBalanceConfirmation>(store.prepareMigration("legacy",listOf("dubl-3.69"),null)).pending
        val migrated=confirmLegacyBalance(pending,Amount(77,2),"Independent opening")
        assertEquals(CommitResult.Committed,store.compareAndSet("legacy",-1,migrated))
        val bytes=Files.readString(store.envelopePath("legacy"))
        val state=DesktopAppState(DesktopCharacterStore(file),DesktopCharacterExtrasStore(directory.resolve("sheet-extras.json")),false,store)
        var legacyConstructions=0
        assertNull(legacyCloudForStorage(store,false) { legacyConstructions++;error("Must never construct or bind a legacy sender") })
        assertEquals(0,legacyConstructions)
        assertTrue(state.canonicalMode);assertTrue(state.readOnlyCanonicalMode);assertFalse(state.canonicalViewReady)
        val before=state.snapshot
        state.setName("Not saved");state.changeAttribute(com.furybook.dubl.model.AttributeId.STRENGTH,1)
        assertEquals(before,state.snapshot);assertEquals(legacy,Files.readString(file));assertEquals(bytes,Files.readString(store.envelopePath("legacy")))
        state.initializeCanonicalView()
        assertEquals(Amount(77,2),state.acquisition.readCanonicalView().balance)
        assertFalse(state.reviewNewCharacterFunding(2500).canConfirm)
        assertEquals(bytes,encodeEnvelope(assertIs<LoadResult.Loaded>(CharacterTransferCodec.decodeEnvelope(state.exportActiveCharacter())).state));assertFalse(state.cloudLedgerV2)
    }
    @Test fun corrupt_head_presence_never_allows_default_legacy_fallback()=runBlocking<Unit> {
        val directory=Files.createTempDirectory("t17-unavailable-head")
        val store=DesktopAcquisitionStore(directory);val head=store.envelopePath("protected")
        Files.createDirectories(head.parent);Files.writeString(head,"{not-valid}")
        val state=DesktopAppState(DesktopCharacterStore(directory.resolve("characters.json")),DesktopCharacterExtrasStore(directory.resolve("sheet-extras.json")),false,store)
        assertNull(legacyCloudForStorage(store,false) { error("Corrupt heads cannot construct a legacy sender") })
        assertTrue(state.canonicalMode);val before=state.snapshot
        state.setName("Not saved");assertEquals(before,state.snapshot)
        assertFails { state.initializeCanonicalView() }
        assertTrue(state.readOnlyCanonicalMode);assertFalse(state.canonicalViewReady)
        assertEquals("{not-valid}",Files.readString(head));assertFalse(Files.exists(directory.resolve("characters.json")))
    }

}
