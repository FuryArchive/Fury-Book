package com.furybook.web

import com.furybook.content.FcpTextSource
import com.furybook.content.FcpContentPack
import com.furybook.desktop.DesktopAppState
import com.furybook.desktop.screens.*
import com.furybook.dubl.acquisition.*
import com.furybook.dubl.data.SnapshotCodec
import com.furybook.dubl.model.*
import kotlinx.browser.window
import kotlinx.coroutines.*
import kotlin.coroutines.resume
import kotlin.js.Promise
import kotlin.test.*

/** Prepared real IndexedDB + production callback tests. NOT Compose pixels, DOM input or offline Main bootstrap. */
class AcquisitionUiTest {
    private var funding:CreationFundingPolicy?=null
    private val ref=DefinitionRef("browser-synthetic","target")
    private fun catalog():AcquisitionCatalog {
        val source=RuleSourceRef("SYNTHETIC_RULE","web-reconstruction","Synthetic fixed20, not DUBL source acceptance")
        val definition=AcquisitionDefinition(ref,AcquisitionKind.DEVELOPMENT,1,RepeatPolicy.Single,
            RequirementNode.AllOf("none",source,emptyList(),NoRequirementsEvidence(source,"Synthetic no requirements")),
            PriceRule.Fixed(20,0,source),SourceStatus.Confirmed,"Synthetic target",source)
        return AcquisitionCatalog(mapOf(ref to definition),"synthetic","synthetic",creationFundingPolicy=funding)
    }
    private fun initial(id:String="hero",chi:Boolean=false)=StoredCharacter(DublCharacter(id,name="Original",chiEnabled=chi),
        CharacterSheetExtras(),emptyList(),Ledger(Opening("opening:$id","SYNTHETIC_RULE",Amount(100,2),
            RequirementNode.CreationPhase.Phase.Creating,OpeningProvenance.NewCharacter),emptyList()),
        emptyList(),emptyList(),SyncMetadata(0),0,0,RequirementNode.CreationPhase.Phase.Creating)
    private fun test(body:suspend CoroutineScope.()->Unit):Promise<Unit> = MainScope().promise { withTimeout(8000) { body() } }
    private suspend fun settled(state:DesktopAppState,phase:AcquisitionUiPhase) = withTimeout(8000) {
        while(state.acquisitionUiModel?.phase!=phase) delay(1)
        checkNotNull(state.acquisitionUiModel)
    }
    private suspend fun fixture(writable:Boolean=true,chi:Boolean=false):Pair<BrowserAcquisitionStore,DesktopAppState> {
        clearBrowserSessionData();BrowserPackState.chiEnabled=false
        val owner=BrowserOwnerScope.fromVerifiedSession(WebAuthSession("web-ui-fixture","synthetic@example.test")) { "web-ui-fixture" }
        val store=BrowserAcquisitionStore(owner,"furybook-web-ui-"+webUuid(),localWritesEnabled=true)
        assertTrue(store.initialize());val saved=initial(chi=chi)
        assertEquals(CommitResult.Committed,store.compareAndSet("hero",-1,saved))
        val resources=loadBundledFcpTexts()
        val source=FcpTextSource { resources[it] }
        funding=loadAcquisitionCatalog(listOf(FcpContentPack.load("fcp/dubl-3.69",source)),false).catalog.creationFundingPolicy
        val state=DesktopAppState(resources,acquisitionStore=store,initialCanonicalStates=mapOf("hero" to saved),
            localAcquisitionEnabled=writable,acquisitionCatalogProvider=::catalog)
        return store to state
    }
    @Test fun card_preview_cancel_reentry_and_review_replacement_allocate_no_financial_id()=test {
        val (store,state)=fixture()
        try {
            val actions=WebAcquisitionActions(state,this);actions.reload()
            val request=AcquisitionRequest("hero",listOf(AcquisitionTarget(ref,targetRank=1)))
            val card=state.acquisition.quoteCardReview(request)
            assertEquals(Amount(20,0),assertIs<AcquisitionDisplayedPrice.Known>(card.model.price).amount)
            actions.open(checkNotNull(card.model.request));val first=settled(state,AcquisitionUiPhase.Ready)
            actions.dismiss();actions.reopen()
            assertTrue(actions.visible);assertEquals(first.key,state.acquisitionUiModel?.key)
            actions.dismiss();actions.open(request);val second=settled(state,AcquisitionUiPhase.Ready)
            assertNotEquals(first.key,second.key);assertNull(state.acquisition.retainedOperationId)
            assertEquals(100L,state.canonicalBalance("hero")?.xp)
            assertTrue(assertIs<LoadResult.Loaded>(store.load("hero")).state.receipts.isEmpty())
        } finally { state.dispose();store.close() }
    }
    @Test fun production_callback_synthetic_purchase_saved_evidence_reopen_and_undo()=test {
        val (store,state)=fixture()
        try {
            val actions=WebAcquisitionActions(state,this);actions.reload()
            actions.open(AcquisitionRequest("hero",listOf(AcquisitionTarget(ref,targetRank=1))))
            val ready=settled(state,AcquisitionUiPhase.Ready);actions.confirm(ready.key)
            val paid=settled(state,AcquisitionUiPhase.Committed)
            assertEquals(Amount(80,2),paid.receipt?.remaining)
            val operation=checkNotNull(paid.receipt?.savedOperation)
            assertIs<AcquisitionSavedEvidence.Recorded>(operation.evidence)
            assertEquals(1,assertIs<LoadResult.Loaded>(store.load("hero")).state.receipts.size)
            actions.dismiss();actions.reopen();assertEquals(paid.key,state.acquisitionUiModel?.key)
            actions.reload();assertEquals(1,actions.view?.history?.size)
            actions.undo();val inverse=settled(state,AcquisitionUiPhase.Ready);actions.confirm(inverse.key)
            settled(state,AcquisitionUiPhase.Committed)
            assertEquals(Amount(100,2),balance(assertIs<LoadResult.Loaded>(store.load("hero")).state.ledger))
        } finally { state.dispose();store.close() }
    }
    @Test fun manual_transform_preserves_exact_canonical_fields_not_runtime_suppressed_copy()=test {
        val (store,state)=fixture(chi=true)
        try {
            val actions=WebAcquisitionActions(state,this);actions.reload()
            assertTrue(actions.view!!.state.character.chiEnabled)
            actions.editManual("Size") { base -> base.copy(size=6) }
            val draft=checkNotNull(actions.manual);assertTrue(draft.base.state.character.chiEnabled)
            actions.reviewManual(draft,Amount.ZERO,"Explicit size correction")
            val ready=settled(state,AcquisitionUiPhase.Ready);actions.confirm(ready.key);settled(state,AcquisitionUiPhase.Committed)
            val saved=assertIs<LoadResult.Loaded>(store.load("hero")).state
            assertEquals(6,saved.character.size);assertTrue(saved.character.chiEnabled)
            assertEquals(Amount(100,2),balance(saved.ledger))
        } finally { state.dispose();store.close() }
    }
    @Test fun readonly_canonical_manual_and_purchase_never_fall_back_to_legacy_writes()=test {
        val (store,state)=fixture(writable=false)
        try {
            val before=store.rawForExport("hero");val actions=WebAcquisitionActions(state,this);actions.reload()
            state.setProfile("Changed","",999,6,2,false)
            actions.open(AcquisitionRequest("hero",listOf(AcquisitionTarget(ref,targetRank=1))))
            val blocked=settled(state,AcquisitionUiPhase.Blocked)
            assertFalse(blocked.canConfirm);assertNull(state.acquisition.retainedOperationId)
            assertEquals(before,store.rawForExport("hero"));assertTrue(state.readOnlyCanonicalMode)
        } finally { state.dispose();store.close() }
    }
    @Test fun pending_and_failed_initial_discovery_refuse_legacy_write_and_preserve_bytes()=test {
        clearBrowserSessionData();val original=SnapshotCodec.encode(AppSnapshot(listOf(DublCharacter("legacy",name="Before")),"legacy"))
        BrowserCharacterStore().replaceRaw(original,migrationSource=false)
        val extras=BrowserCharacterExtrasStore().raw()
        val owner=BrowserOwnerScope.fromVerifiedSession(WebAuthSession("not-initialized","synthetic@example.test")) { "not-initialized" }
        val store=BrowserAcquisitionStore(owner,"furybook-web-pending-"+webUuid())
        val resources=loadBundledFcpTexts()
        val source=FcpTextSource { resources[it] }
        funding=loadAcquisitionCatalog(listOf(FcpContentPack.load("fcp/dubl-3.69",source)),false).catalog.creationFundingPolicy
        val state=DesktopAppState(resources,acquisitionStore=store)
        try {
            assertTrue(state.canonicalMode);assertFalse(state.canonicalReadState.navigationAvailable)
            state.setName("Pending write");assertEquals(original,BrowserCharacterStore().raw())
            assertFailsWith<IllegalStateException> { state.initializeCanonicalView() }
            assertEquals(WebCanonicalReadPhase.Unavailable,state.canonicalReadState.phase)
            state.setName("Failed write");state.setSkillGroups(emptyList())
            assertEquals(original,BrowserCharacterStore().raw());assertEquals(extras,BrowserCharacterExtrasStore().raw())
        } finally { state.dispose();store.close() }
    }
    @Test fun stale_manual_draft_requires_new_capture_and_disposal_refuses_old_owner_callbacks()=test {
        val (store,state)=fixture()
        try {
            val actions=WebAcquisitionActions(state,this);actions.reload();actions.editManual("Size") { it.copy(size=6) }
            val draft=checkNotNull(actions.manual)
            val old=draft.base.state
            assertIs<MutationResult.Applied>(state.mutateCanonical(CanonicalMutation("competing","hero",old.revision,
                MutationPayload.NonFinancial(CharacterPatch.fromCanonicalState(old,old.character.copy(name="Competing"))))))
            val bytes=store.rawForExport("hero")
            actions.reviewManual(draft,Amount.ZERO,"Old draft");withTimeout(8000) { while(actions.error==null) delay(1) }
            assertNotNull(actions.error);assertNull(state.acquisition.retainedOperationId)
            assertEquals(bytes,store.rawForExport("hero"))
            actions.reload();actions.editManual("New size") { it.copy(size=6) }
            actions.reviewManual(checkNotNull(actions.manual),Amount.ZERO,"New draft")
            val ready=settled(state,AcquisitionUiPhase.Ready);state.dispose();actions.confirm(ready.key);yield()
            assertEquals(bytes,store.rawForExport("hero"))
        } finally { state.dispose();store.close() }
    }
    @Test fun new_funding_uses_shared_initial_ap_and_does_not_refill_existing_character()=test {
        val (store,state)=fixture()
        try {
            val before=store.rawForExport("hero");val actions=WebAcquisitionActions(state,this)
            actions.reviewFunding(2500,"New")
            val ready=settled(state,AcquisitionUiPhase.Ready)
            assertEquals(2L,ready.funding?.initialAP);assertNull(state.acquisition.retainedOperationId)
            actions.confirm(ready.key);settled(state,AcquisitionUiPhase.Committed)
            assertEquals(before,store.rawForExport("hero"))
            val newId=store.characterIds().single { it!="hero" }
            val created=assertIs<LoadResult.Loaded>(store.load(newId)).state
            assertEquals(Amount(2500,2),balance(created.ledger));assertTrue(created.receipts.isEmpty())
        } finally { state.dispose();store.close() }
    }

    private suspend fun nativeDatabase(name:String):dynamic = suspendCancellableCoroutine { c ->
        val request=window.asDynamic().indexedDB.open(name,1)
        request.onsuccess={ _:dynamic -> c.resume(request.result) }
        request.onerror={ _:dynamic -> c.resumeWith(Result.failure(IllegalStateException("Native fixture open failed"))) }
    }
    private suspend fun seedRaw(name:String,ownerId:String,raw:String) {
        val db=nativeDatabase(name)
        try { suspendCancellableCoroutine<Unit> { c ->
            val tx=db.transaction(arrayOf("envelopes"),"readwrite",js("({durability:'strict'})"))
            tx.oncomplete={ _:dynamic -> c.resume(Unit) }
            tx.onabort={ _:dynamic -> c.resumeWith(Result.failure(IllegalStateException("Native fixture aborted"))) }
            tx.objectStore("envelopes").put(raw,arrayOf(ownerId,"hero"))
        } } finally { db.close() }
    }
    @Test fun delayed_actual_first_native_read_cannot_write_legacy_before_protected_classification()=test {
        clearBrowserSessionData()
        val original=SnapshotCodec.encode(AppSnapshot(listOf(DublCharacter("hero",name="Legacy")),"hero"))
        BrowserCharacterStore().replaceRaw(original,migrationSource=false)
        val name="furybook-web-delayed-"+webUuid();val ownerId="delayed-owner"
        val owner=BrowserOwnerScope.fromVerifiedSession(WebAuthSession(ownerId,"synthetic@example.test")) { ownerId }
        val store=BrowserAcquisitionStore(owner,name,localWritesEnabled=true)
        assertTrue(store.initialize());assertEquals(CommitResult.Committed,store.compareAndSet("hero",-1,initial()))
        val state=DesktopAppState(loadBundledFcpTexts(),acquisitionStore=store)
        val db=nativeDatabase(name);val entered=CompletableDeferred<Unit>();val completed=CompletableDeferred<Unit>();var released=false
        val tx=db.transaction(arrayOf("envelopes"),"readwrite",js("({durability:'strict'})"))
        tx.oncomplete={ _:dynamic -> completed.complete(Unit) }
        tx.onabort={ _:dynamic -> completed.completeExceptionally(IllegalStateException("Native read barrier aborted")) }
        fun keepAlive() {
            val request=tx.objectStore("envelopes").get(arrayOf(ownerId,"hero"))
            request.onsuccess={ _:dynamic -> if(!entered.isCompleted) entered.complete(Unit);if(!released) keepAlive() }
        }
        keepAlive()
        try {
            entered.await()
            val reading=async { state.initializeCanonicalView() };yield()
            assertFalse(reading.isCompleted);assertFalse(state.canonicalReadState.navigationAvailable)
            state.setName("Must not write");assertEquals(original,BrowserCharacterStore().raw())
            released=true;completed.await();reading.await()
            assertTrue(state.canonicalMode);assertTrue(state.canonicalReadState.navigationAvailable)
            assertEquals(original,BrowserCharacterStore().raw());assertEquals(Amount(100,2),state.canonicalBalance("hero"))
        } finally { released=true;state.dispose();store.close();db.close() }
    }
    @Test fun corrupt_actual_first_native_load_never_falls_back_to_legacy()=test {
        clearBrowserSessionData()
        val original=SnapshotCodec.encode(AppSnapshot(listOf(DublCharacter("hero",name="Legacy")),"hero"))
        BrowserCharacterStore().replaceRaw(original,migrationSource=false)
        val name="furybook-web-failed-"+webUuid();val ownerId="failed-owner"
        val owner=BrowserOwnerScope.fromVerifiedSession(WebAuthSession(ownerId,"synthetic@example.test")) { ownerId }
        val store=BrowserAcquisitionStore(owner,name)
        assertTrue(store.initialize());seedRaw(name,ownerId,"{broken-json")
        val state=DesktopAppState(loadBundledFcpTexts(),acquisitionStore=store)
        try {
            assertFailsWith<AcquisitionPreviewUnavailable> { state.initializeCanonicalView() }
            assertEquals(WebCanonicalReadPhase.Unavailable,state.canonicalReadState.phase)
            assertTrue(state.canonicalMode);assertFalse(state.canonicalReadState.navigationAvailable)
            state.setName("Must not write");assertEquals(original,BrowserCharacterStore().raw())
        } finally { state.dispose();store.close() }
    }

}
