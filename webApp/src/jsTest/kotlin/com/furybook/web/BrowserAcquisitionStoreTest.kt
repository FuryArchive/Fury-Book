package com.furybook.web

import com.furybook.dubl.acquisition.*
import com.furybook.dubl.model.*
import com.furybook.dubl.data.SnapshotCodec
import kotlinx.browser.window
import kotlinx.browser.document
import org.w3c.dom.HTMLIFrameElement
import kotlin.coroutines.resume
import kotlinx.coroutines.*
import kotlin.js.Promise
import kotlin.test.*

class BrowserAcquisitionStoreTest {
    private fun owner(id:String="synthetic-owner")=BrowserOwnerScope.fromVerifiedSession(WebAuthSession(id,"synthetic@example.test")) { id }
    private fun state(id:String="hero",storage:Long=0)=StoredCharacter(DublCharacter(id),CharacterSheetExtras(),emptyList(),
        Ledger(Opening("opening","source",Amount(100,0),RequirementNode.CreationPhase.Phase.Creating,OpeningProvenance.NewCharacter),emptyList()),
        emptyList(),emptyList(),SyncMetadata(0),0,storage,RequirementNode.CreationPhase.Phase.Creating)
    private fun test(body:suspend CoroutineScope.()->Unit):Promise<Unit> = MainScope().promise { body() }
    @Test fun independent_migrated_copy_needs_no_foreign_backup()=test {
        val raw=SnapshotCodec.encode(AppSnapshot(listOf(DublCharacter("hero")),"hero"))
        val origin=LegacyOrigin("hero",SnapshotCodec.SCHEMA,legacySourceFingerprint(raw),raw,listOf("dubl-3.69"),extrasSource=LegacyExtrasSource(null))
        val pending=assertIs<MigrationResult.NeedsBalanceConfirmation>(prepareLegacy(raw,origin,null)).pending
        val original=confirmLegacyBalance(pending,Amount(100,0),"Known synthetic balance")
        val copied=copyAcquisitionCharacter(original,"independent","copy-operation")
        val name="f1-copy-"+webUuid()
        val target=BrowserAcquisitionStore(owner(),name,localWritesEnabled=true,legacySource={null})
        assertTrue(target.initialize())
        assertEquals(CommitResult.Committed,target.compareAndSet("independent",-1,copied));target.close()
        val reopened=BrowserAcquisitionStore(owner(),name,localWritesEnabled=true,legacySource={null})
        assertTrue(reopened.initialize())
        val saved=assertIs<LoadResult.Loaded>(reopened.load("independent")).state
        assertEquals(copied,saved);assertEquals(original.ledger.opening,saved.ledger.opening)
        assertEquals(CommitResult.Committed,reopened.compareAndSet("independent",0,saved.copy(storageRevision=1)))
        reopened.close()
    }
    @Test fun real_idb_atomic_cas_whole_envelope_and_exact_long()=test {
        val name="furybook-test-cas-"+webUuid()
        val a=BrowserAcquisitionStore(owner(),name,localWritesEnabled=true)
        val b=BrowserAcquisitionStore(owner(),name,localWritesEnabled=true)
        assertTrue(a.initialize());assertTrue(b.initialize())
        val start=state(storage=0).copy(ledger=state().ledger.copy(opening=state().ledger.opening.copy(balance=Amount(9_007_199_254_740_993L,0))))
        assertEquals(CommitResult.Committed,a.compareAndSet("hero",-1,start))
        val next=start.copy(storageRevision=1)
        val results=awaitAll(async { a.compareAndSet("hero",0,next) },async { b.compareAndSet("hero",0,next) })
        assertEquals(1,results.count { it==CommitResult.Committed });assertEquals(1,results.count { it==CommitResult.Conflict })
        assertEquals(9_007_199_254_740_993L,balance((b.load("hero") as LoadResult.Loaded).state.ledger).xp)
        assertEquals(encodeEnvelope(next),b.rawForExport("hero"))
        assertEquals("strict",a.observedDurability)
        val large=next.copy(storageRevision=9_007_199_254_740_993L)
        seedRaw(name,"hero",encodeEnvelope(large))
        assertEquals(large.storageRevision,(b.load("hero") as LoadResult.Loaded).state.storageRevision)
        assertEquals(CommitResult.Committed,a.compareAndSet("hero",large.storageRevision,large.copy(storageRevision=large.storageRevision+1)))
        assertEquals(9_007_199_254_740_994L,(b.load("hero") as LoadResult.Loaded).state.storageRevision)
        a.close();b.close()
    }
    @Test fun real_abort_after_successful_put_keeps_old_head()=test {
        val name="furybook-test-abort-"+webUuid()
        val good=BrowserAcquisitionStore(owner(),name,localWritesEnabled=true)
        assertTrue(good.initialize());assertEquals(CommitResult.Committed,good.compareAndSet("hero",-1,state()))
        var paid=state()
        val staging=object:AcquisitionStore {
            override suspend fun load(characterId:String)=LoadResult.Loaded(paid)
            override suspend fun compareAndSet(characterId:String,expectedStorageRevision:Long,next:StoredCharacter):CommitResult {
                check(expectedStorageRevision==paid.storageRevision);encodeEnvelope(next);paid=next;return CommitResult.Committed
            }
        }
        buy(staging)
        assertEquals(80L,balance(paid.ledger).xp);assertEquals(1,paid.receipts.size);assertEquals(1,paid.outbox.size)
        val abort=BrowserAcquisitionStore(owner(),name,localWritesEnabled=true,fault={ stage -> if(stage==BrowserAcquisitionWriteStage.AfterPutSucceeded) error("synthetic abort after successful put") })
        assertTrue(abort.initialize())
        assertIs<CommitResult.StorageFailure>(abort.compareAndSet("hero",0,paid))
        assertEquals(encodeEnvelope(state()),good.rawForExport("hero"))
        val unchanged=(good.load("hero") as LoadResult.Loaded).state
        assertEquals(100L,balance(unchanged.ledger).xp);assertTrue(unchanged.receipts.isEmpty());assertTrue(unchanged.outbox.isEmpty())
        println("ACTUAL_PAID_ABORT|successful-put80-with-receipt-outbox|rollback100-empty-receipt-outbox")
        good.close();abort.close()
    }
    @Test fun owners_and_default_off_are_isolated()=test {
        val name="furybook-test-owner-"+webUuid()
        val a=BrowserAcquisitionStore(owner("a"),name,localWritesEnabled=true)
        val b=BrowserAcquisitionStore(owner("b"),name,localWritesEnabled=true)
        val off=BrowserAcquisitionStore(owner("a"),name)
        assertTrue(a.initialize());assertTrue(b.initialize());assertTrue(off.initialize())
        assertEquals(CommitResult.Committed,a.compareAndSet("hero",-1,state()))
        assertIs<LoadResult.Missing>(b.load("hero"));assertTrue(b.characterIds().isEmpty())
        assertIs<CommitResult.StorageFailure>(off.compareAndSet("hero",0,state(storage=1)))
        assertFalse(off.capabilities.localDurable);assertFalse(a.capabilities.cloudLedgerV2)
        var current:String?="a"
        val stale=BrowserAcquisitionStore(BrowserOwnerScope.fromVerifiedSession(WebAuthSession("a","synthetic@example.test")) { current },name)
        current="b"
        assertFalse(stale.initialize());assertFails { stale.characterIds() }
        a.close();b.close();off.close();stale.close()
    }
    @Test fun absent_memory_is_not_migration_and_present_source_is_once_only()=test {
        val name="furybook-test-migration-"+webUuid()
        var source:BrowserLegacySource?=null
        val store=BrowserAcquisitionStore(owner(),name,localWritesEnabled=true,legacySource={source})
        assertTrue(store.initialize());assertIs<LoadResult.Missing>(store.load("hero"))
        val raw=SnapshotCodec.encode(AppSnapshot(listOf(DublCharacter("hero",experience=100)),"hero"))
        source=BrowserLegacySource(raw,"{\"version\":1,\"characters\":{}}")
        assertIs<LoadResult.MigrationRequired>(store.load("hero"))
        val pending=assertIs<MigrationResult.NeedsBalanceConfirmation>(store.prepareMigration("hero",listOf("dubl-3.69"),null))
        val migrated=confirmLegacyBalance(pending.pending,Amount(100,0),"synthetic known balance")
        assertEquals(CommitResult.Committed,store.compareAndSet("hero",-1,migrated))
        assertEquals(CommitResult.Conflict,store.compareAndSet("hero",-1,migrated))
        source=source!!.copy(snapshotRaw=raw.replace("100","101"))
        assertIs<LoadResult.LegacyImportConflict>(store.load("hero"))
        assertIs<CommitResult.StorageFailure>(store.compareAndSet("hero",0,migrated.copy(storageRevision=1)))
        source=null
        val reopened=BrowserAcquisitionStore(owner(),name,localWritesEnabled=true)
        assertTrue(reopened.initialize());assertEquals(100L,balance((reopened.load("hero") as LoadResult.Loaded).state.ledger).xp)
        store.close();reopened.close()
    }
    private suspend fun frame(sandbox:Boolean=false):HTMLIFrameElement = suspendCancellableCoroutine { continuation ->
        val frame=document.createElement("iframe") as HTMLIFrameElement
        if(sandbox) frame.setAttribute("sandbox","allow-scripts")
        frame.onload={ continuation.resume(frame) }
        frame.srcdoc="<!doctype html><title>Synthetic IDB context</title>"
        document.body!!.appendChild(frame)
    }
    private fun catalog():AcquisitionCatalog {
        val source=RuleSourceRef("browser-synthetic","1","Test-only fixed20")
        val ref=DefinitionRef("browser-synthetic","target")
        val d=AcquisitionDefinition(ref,AcquisitionKind.DEVELOPMENT,1,RepeatPolicy.Single,
            RequirementNode.AllOf("none",source,emptyList(),NoRequirementsEvidence(source,"Synthetic no prerequisites")),
            PriceRule.Fixed(20,0,source),SourceStatus.Confirmed,"Synthetic target",source)
        return AcquisitionCatalog(mapOf(ref to d),"browser-synthetic","browser-synthetic")
    }
    private suspend fun buy(store:AcquisitionStore,id:String="buy"):ConfirmAcquisition {
        val service=AcquisitionService(store,::catalog,::webUuid)
        val request=AcquisitionRequest("hero",listOf(AcquisitionTarget(DefinitionRef("browser-synthetic","target"),targetRank=1)))
        val preview=service.preview(request)
        val command=ConfirmAcquisition(id,"hero",request,preview.baseRevision,preview.previewToken)
        assertIs<ApplyResult.Applied>(service.apply(command));return command
    }
    @Test fun lost_reply_after_native_commit_replays_exact_operation_without_second_debit()=test {
        val name="furybook-test-lost-reply-"+webUuid()
        val native=BrowserAcquisitionStore(owner(),name,localWritesEnabled=true)
        assertTrue(native.initialize());assertEquals(CommitResult.Committed,native.compareAndSet("hero",-1,state()))
        var replyLost=false;var committedCalls=0;var catalogCalls=0
        // Test-only reply loss happens after the real adapter has awaited native oncomplete.
        val uncertain=object:AcquisitionStore {
            override suspend fun load(characterId:String)=native.load(characterId)
            override suspend fun compareAndSet(characterId:String,expectedStorageRevision:Long,next:StoredCharacter):CommitResult {
                val result=native.compareAndSet(characterId,expectedStorageRevision,next)
                if(result==CommitResult.Committed) {
                    committedCalls++;replyLost=true
                    return CommitResult.StorageFailure("Fault-injected lost reply after native transaction complete")
                }
                return result
            }
        }
        val service=AcquisitionService(uncertain,{ catalogCalls++;check(!replyLost);catalog() },{error("Frozen operation must not get a new ID")})
        val request=AcquisitionRequest("hero",listOf(AcquisitionTarget(DefinitionRef("browser-synthetic","target"),targetRank=1)))
        val preview=service.preview(request)
        val frozen=ConfirmAcquisition("lost-reply-buy","hero",request,preview.baseRevision,preview.previewToken)
        val frozenIntent=frozen.canonical
        assertIs<ApplyResult.StorageFailure>(service.apply(frozen));assertTrue(replyLost);assertEquals(1,committedCalls)
        val saved=assertNotNull(native.rawForExport("hero"));native.close()
        val reopened=BrowserAcquisitionStore(owner(),name,localWritesEnabled=true)
        assertTrue(reopened.initialize())
        val retry=AcquisitionService(reopened,{error("Receipt replay must precede catalogue access")},{error("No replacement operation ID")})
        val callsBefore=catalogCalls
        val replay=assertIs<ApplyResult.AlreadyApplied>(retry.apply(frozen))
        assertEquals(frozenIntent,frozen.canonical);assertEquals(callsBefore,catalogCalls)
        assertEquals(saved,reopened.rawForExport("hero"))
        val head=(reopened.load("hero") as LoadResult.Loaded).state
        assertEquals(80L,balance(head.ledger).xp);assertEquals(1,head.ledger.entries.size)
        assertEquals(1,head.receipts.size);assertEquals(1,head.outbox.size)
        assertEquals("lost-reply-buy",head.receipts.single().operationId)
        assertEquals(replay.receipt,head.receipts.single());assertEquals(encodeSyncEnvelope(head),head.outbox.single().payloadCanonical)
        println("ACTUAL_NATIVE_LOST_REPLY|fault-after-oncomplete|frozen-id-retry-AlreadyApplied|one20-debit-receipt-outbox|exact-head-unchanged|no-catalogue-on-replay")
        reopened.close()
    }
    @Test fun native_factories_from_two_contexts_race_one_paid_projection()=test {
        val frame=frame()
        try {
            val childFactory=frame.contentWindow!!.asDynamic().indexedDB
            assertNotSame(window.asDynamic().indexedDB,childFactory)
            val name="furybook-context-cas-"+webUuid()
            val a=BrowserAcquisitionStore(owner(),name,localWritesEnabled=true)
            val b=BrowserAcquisitionStore(owner(),name,localWritesEnabled=true,indexedDb={childFactory})
            assertTrue(a.initialize());assertTrue(b.initialize())
            assertEquals(CommitResult.Committed,a.compareAndSet("hero",-1,state()))
            var current=(a.load("hero") as LoadResult.Loaded).state
            repeat(7) { assertEquals(CommitResult.Committed,a.compareAndSet("hero",current.storageRevision,current.copy(storageRevision=current.storageRevision+1)));current=(a.load("hero") as LoadResult.Loaded).state }
            // Prepare a real paid Shared transition in a detached test store, then race actual IDB CAS.
            var paid=current
            val detached=object:AcquisitionStore {
                override suspend fun load(characterId:String)=LoadResult.Loaded(paid)
                override suspend fun compareAndSet(characterId:String,expectedStorageRevision:Long,next:StoredCharacter):CommitResult { paid=next;return CommitResult.Committed }
            }
            buy(detached)
            assertEquals(80L,balance(paid.ledger).xp);assertEquals(8L,paid.storageRevision)
            val results=awaitAll(async { a.compareAndSet("hero",7,paid) },async { b.compareAndSet("hero",7,paid) })
            assertEquals(1,results.count { it==CommitResult.Committed });assertEquals(1,results.count { it==CommitResult.Conflict })
            val saved=(b.load("hero") as LoadResult.Loaded).state
            assertEquals(80L,balance(saved.ledger).xp);assertEquals(1,saved.receipts.size);assertEquals(1,saved.outbox.size)
            println("ACTUAL_TWO_CONTEXT_CAS|revision7-to8|balance100-to80|one-receipt-outbox|strict=${a.observedDurability}/${b.observedDurability}")
            a.close();b.close()
        } finally { frame.remove() }
    }
    private suspend fun seedRaw(name:String,id:String,raw:String,ownerId:String="synthetic-owner") {
        val db=suspendCancellableCoroutine<dynamic> { c ->
            val request=window.asDynamic().indexedDB.open(name,1)
            request.onsuccess={ _:dynamic -> c.resume(request.result) }
            request.onerror={ _:dynamic -> c.resumeWith(Result.failure(IllegalStateException("Native test seed open failed"))) }
        }
        try {
            suspendCancellableCoroutine<Unit> { c ->
                val tx=db.transaction(arrayOf("envelopes"),"readwrite",js("({durability:'strict'})"))
                tx.oncomplete={ _:dynamic -> c.resume(Unit) }
                tx.onabort={ _:dynamic -> c.resumeWith(Result.failure(IllegalStateException("Native test seed aborted"))) }
                tx.objectStore("envelopes").put(raw,arrayOf(ownerId,id))
            }
        } finally { db.close() }
    }
    @Test fun corrupt_and_future_heads_are_retained_and_never_freshened()=test {
        val name="furybook-refusals-"+webUuid();val store=BrowserAcquisitionStore(owner(),name,localWritesEnabled=true)
        assertTrue(store.initialize())
        val corrupt="{invalid";val future=encodeEnvelope(state("future")).replace("\"formatVersion\":1","\"formatVersion\":2")
        seedRaw(name,"hero",corrupt);seedRaw(name,"future",future)
        assertIs<LoadResult.Corrupt>(store.load("hero"));assertIs<LoadResult.Unsupported>(store.load("future"))
        assertIs<CommitResult.StorageFailure>(store.compareAndSet("hero",-1,state()))
        assertIs<CommitResult.StorageFailure>(store.compareAndSet("future",-1,state("future")))
        assertEquals(corrupt,store.rawForExport("hero"));assertEquals(future,store.rawForExport("future"));store.close()
    }
    @Test fun quota_failure_is_explicitly_fault_injected_and_disables_write_gate()=test {
        val store=BrowserAcquisitionStore(owner(),"furybook-quota-map-"+webUuid(),localWritesEnabled=true,
            fault={ if(it==BrowserAcquisitionWriteStage.BeforePut) error("QuotaExceededError (fault injected)") })
        assertTrue(store.initialize());assertIs<CommitResult.StorageFailure>(store.compareAndSet("hero",-1,state()))
        assertFalse(store.capabilities.localDurable);assertIs<LoadResult.Missing>(store.load("hero"));store.close()
    }
    @Test fun real_restricted_browser_context_fails_closed_without_override()=test {
        val opaque=frame(sandbox=true)
        try {
            val store=BrowserAcquisitionStore(owner(),"furybook-denied-"+webUuid(),localWritesEnabled=true,indexedDb={opaque.contentWindow!!.asDynamic().indexedDB})
            assertFalse(store.initialize());assertFalse(store.capabilities.localDurable)
            assertNotNull(store.storageError)
            val originalDenial=store.storageError!!
            assertTrue(originalDenial.contains("Permission denied") || originalDenial.contains("SecurityError"),originalDenial)
            assertIs<CommitResult.StorageFailure>(store.compareAndSet("hero",-1,state()))
            println("ACTUAL_RESTRICTED_CONTEXT_STORAGE_FAILURE|"+originalDenial)
            store.close()
        } finally { opaque.remove() }
    }
    @Test fun separate_process_profile_reopens_exact_paid_envelope_and_outbox()=test {
        val phase=window.asDynamic().__karma__.config.furybookIdbPhase as? String ?: "normal"
        if(phase=="normal") return@test
        val store=BrowserAcquisitionStore(owner(),"furybook-product-process-reopen-v1",localWritesEnabled=true)
        assertTrue(store.initialize())
        if(phase=="write") {
            assertIs<LoadResult.Missing>(store.load("hero"));assertEquals(CommitResult.Committed,store.compareAndSet("hero",-1,state()))
            buy(store,"process-buy")
            val raw=store.rawForExport("hero")!!
            println("ACTUAL_PRODUCT_PROCESS_RAW_HEX|"+raw.map { it.code.toString(16).padStart(4,'0') }.joinToString(""))
        } else require(phase=="reopen")
        val saved=(store.load("hero") as LoadResult.Loaded).state
        assertEquals(80L,balance(saved.ledger).xp);assertEquals("process-buy",saved.receipts.single().operationId)
        assertEquals(encodeSyncEnvelope(saved),saved.outbox.single().payloadCanonical)
        val raw=store.rawForExport("hero")!!
        if(phase=="reopen") println("ACTUAL_PRODUCT_PROCESS_RAW_HEX|"+raw.map { it.code.toString(16).padStart(4,'0') }.joinToString(""))
        println("ACTUAL_PRODUCT_PROCESS_REOPEN|$phase|balance80|one-receipt-outbox|strict=${store.observedDurability}")
        store.close()
    }

    @Test fun verified_startup_enumerates_only_its_owner_and_refuses_unavailable_heads()=test {
        val name="furybook-startup-"+webUuid()
        val seed=BrowserAcquisitionStore(owner("a"),name,localWritesEnabled=true)
        assertTrue(seed.initialize());assertEquals(CommitResult.Committed,seed.compareAndSet("hero",-1,state()))
        var current:String?="a"
        val startup=BrowserAcquisitionSession.open(WebAuthSession("a","synthetic@example.test"),{current},name) { it }
        assertEquals(setOf("hero"),startup.states.keys);assertFalse(startup.store.capabilities.localDurable)
        val other=BrowserAcquisitionSession.open(WebAuthSession("b","synthetic@example.test"),{"b"},name) { it }
        assertTrue(other.states.isEmpty());other.store.close()
        current="b"
        assertFails { BrowserAcquisitionSession.open(WebAuthSession("a","synthetic@example.test"),{current},name) { it } }
        current="a"
        seedRaw(name,"bad","{bad",ownerId="a")
        assertFails { BrowserAcquisitionSession.open(WebAuthSession("a","synthetic@example.test"),{current},name) { it } }
        startup.store.close();seed.close()
    }


    private fun bootstrapReply(characters:List<DublCharacter>,extras:dynamic=js("({})")):dynamic {
        val snapshot=js("JSON.parse")(SnapshotCodec.encode(AppSnapshot(characters,characters.firstOrNull()?.id.orEmpty())))
        val reply=js("({profile:{nickname:'Synthetic'},state:{activeCharacterId:'hero',revision:1,packState:{}},history:[],characters:[]})")
        for(c in snapshot.characters as Array<dynamic>) {
            val row=js("({revision:2})");row.id=c.id;row.data=c;row.extras=extras
            reply.characters.push(row)
        }
        return reply
    }
    private suspend fun <T> syntheticPull(store:BrowserAcquisitionStore,id:String,reply:dynamic,
        action:suspend (WebCloudSync,WebAuthSession)->T):T {
        val client=js("({auth:{}})");val sdkSession=js("({error:null,data:{session:{user:{}}}})")
        sdkSession.data.session.access_token="synthetic-test-token-never-sent"
        sdkSession.data.session.user.id=id;sdkSession.data.session.user.email="synthetic@example.test"
        val getSession:()->Promise<dynamic> = {Promise.resolve(sdkSession)}
        val getUser:()->Promise<dynamic> = {Promise.resolve(authReply(id))}
        client.auth.getSession=getSession;client.auth.getUser=getUser
        val originalFetch=window.asDynamic().fetch
        val fetch:(dynamic,dynamic)->Promise<dynamic> = {url:dynamic,_:dynamic ->
            check((url as String).endsWith("/rest/v1/rpc/get_web_bootstrap")) { "Unexpected synthetic request" }
            val response=js("({ok:true})")
            response.text={Promise.resolve(js("JSON.stringify")(reply) as String)};Promise.resolve(response)
        }
        window.asDynamic().fetch=fetch
        try {
            val cloud=WebCloudSync(store,{store.knownCharacterIds},client)
            return action(cloud,assertNotNull(cloud.restoreSession()))
        } finally { window.asDynamic().fetch=originalFetch }
    }
    @Test fun actual_pull_and_main_ingress_refuse_hidden_changed_source_and_stale_other_migration()=test {
        clearBrowserSessionData()
        val id="ingress-owner";var current:String?=id;val name="furybook-ingress-"+webUuid()
        val writer=BrowserAcquisitionStore(BrowserOwnerScope.fromVerifiedSession(WebAuthSession(id,"synthetic@example.test")) {current},name,localWritesEnabled=true)
        assertTrue(writer.initialize())
        val old=listOf(DublCharacter("hero",name="old hero",experience=100),DublCharacter("other",name="old other",experience=100))
        try {
            var ingress:BrowserAcquisitionSession?=null
            syntheticPull(writer,id,bootstrapReply(old)) { cloud,session ->
                ingress=BrowserAcquisitionSession.open(session,{current},name,verifyOwner=cloud::verifyOwner)
                val original=cloud.pull();ingress!!.ingestBootstrap(original)
                assertEquals(original.originalLegacySource,writer.originalLegacySourceForExport())
            }
            val staleOther=confirmLegacyBalance(assertIs<MigrationResult.NeedsBalanceConfirmation>(writer.prepareMigration("other",listOf("dubl-3.69"),null)).pending,Amount(100,0),"Synthetic known100")
            val receivedBeforeEdit=assertNotNull(writer.originalLegacySourceForExport())
            val legacyWriter=BrowserCharacterStore();val legacySnapshot=legacyWriter.load()
            legacyWriter.save(legacySnapshot.copy(characters=legacySnapshot.characters.map { if(it.id=="other") it.copy(experience=250) else it }))
            assertIs<LoadResult.LegacyImportConflict>(writer.load("other"))
            assertIs<MigrationResult.Corrupt>(writer.prepareMigration("other",listOf("dubl-3.69"),null))
            assertIs<CommitResult.StorageFailure>(writer.compareAndSet("other",-1,staleOther))
            assertEquals(receivedBeforeEdit,writer.originalLegacySourceForExport());assertNull(writer.rawForExport("other"))
            legacyWriter.save(legacySnapshot)
            assertIs<LoadResult.LegacyImportConflict>(writer.load("other"))
            assertFails { syntheticPull(writer,id,bootstrapReply(old)) { cloud,_ -> cloud.pull() } }
            // Existing fresh-tab/sign-in lifecycle clears volatile display before a fresh received bootstrap.
            clearBrowserSessionData()
            syntheticPull(writer,id,bootstrapReply(old)) { cloud,_ -> ingress!!.ingestBootstrap(cloud.pull()) }
            val hero=confirmLegacyBalance(assertIs<MigrationResult.NeedsBalanceConfirmation>(writer.prepareMigration("hero",listOf("dubl-3.69"),null)).pending,Amount(100,0),"Synthetic known100")
            assertEquals(CommitResult.Committed,writer.compareAndSet("hero",-1,hero))
            val retained=writer.rawForExport("hero")
            clearBrowserSessionData()
            assertIs<BrowserLegacySourceState.NotLoaded>(browserLegacySourceState(id))
            assertIs<LoadResult.Loaded>(writer.load("hero"))
            assertTrue(assertIs<MigrationResult.Corrupt>(writer.prepareMigration("other",listOf("dubl-3.69"),null)).reason.contains("not loaded"))
            assertIs<CommitResult.StorageFailure>(writer.compareAndSet("other",-1,staleOther))
            val changed=listOf(old[0].copy(name="remote hero",experience=170),old[1].copy(name="remote other",experience=250))
            syntheticPull(writer,id,bootstrapReply(changed)) { cloud,_ ->
                val received=cloud.pull()
                assertEquals(250,SnapshotCodec.decode(received.snapshot,{error("No generated identity")}).characters.single {it.id=="other"}.experience)
                assertFails { ingress!!.ingestBootstrap(received) }
                assertEquals(received.originalLegacySource,writer.originalLegacySourceForExport())
                assertIs<LoadResult.LegacyImportConflict>(writer.load("hero"))
                assertIs<MigrationResult.Corrupt>(writer.prepareMigration("other",listOf("dubl-3.69"),null))
                assertIs<CommitResult.StorageFailure>(writer.compareAndSet("other",-1,staleOther))
                assertNull(writer.rawForExport("other"));assertEquals(retained,writer.rawForExport("hero"))
                current="another-owner"
                assertFails { ingress!!.ingestBootstrap(received) }
                assertNull(captureBrowserLegacySource("another-owner"));current=id
            }
            clearBrowserSessionData()
            syntheticPull(writer,id,bootstrapReply(emptyList())) { cloud,_ ->
                val absent=cloud.pull();assertNull(absent.originalLegacySource)
                assertFails { ingress!!.ingestBootstrap(absent) }
                assertIs<BrowserLegacySourceState.Absent>(browserLegacySourceState(id))
                assertIs<LoadResult.LegacyImportConflict>(writer.load("hero"))
                assertIs<MigrationResult.Corrupt>(writer.prepareMigration("other",listOf("dubl-3.69"),null))
                assertIs<CommitResult.StorageFailure>(writer.compareAndSet("other",-1,staleOther))
                assertEquals(retained,writer.rawForExport("hero"))
            }
            ingress!!.store.close()
            val otherOwner=BrowserAcquisitionStore(owner("another-owner"),name)
            assertTrue(otherOwner.initialize());assertIs<LoadResult.Missing>(otherOwner.load("hero"));otherOwner.close()
            println("ACTUAL_MAIN_ORIGINAL_INGRESS|pull-original-before-overlay|changed-source-conflict|other250-not-backup100|NotLoaded-Absent-refuse-migration|owners-head-retained|PASS")
        } finally { writer.close();clearBrowserSessionData() }
    }
    @Test fun actual_pull_main_ingress_preserves_original_extras_strict_refusal_and_exact_backup()=test {
        val id="extras-ingress-owner"
        val character=DublCharacter("hero",experience=100,creationComplete=false,gear=CharacterGear(loadManual=-0.5),
            magic=CharacterMagic(schools=listOf(MagicSchool("Unknown school",1))))
        val known=CharacterSheetExtras("synthetic://portrait",setOf(CharacterConditionId.TIRED),setOf(CharacterSheetResourceId.MANA),
            mapOf("skill" to AttributeId.WILL),listOf(SheetGroup("s","Skills",listOf("skill"),true)),listOf(SheetGroup("d","Developments",listOf("dev"))),
            mapOf(CharacterConditionId.TIRED to ConditionLocalOverride("Title","Details")),listOf(CustomCondition("c","Custom","Details",true)),
            "Notes 😀",listOf(CharacterNote("n","Title","Body")))
        val knownRow=js("JSON.parse")(com.furybook.core.cloud.CloudExtrasProjection.encode(known))
        val invalidRows=listOf(js("({notes:'keep',futureNote:'valuable original',conditions:['TIRED','UNKNOWN_CONDITION']})"),
            js("({notes:'keep',conditions:'malformed-array'})"),"{malformed original extras")
        var validCandidate:StoredCharacter?=null
        for(index in 0..invalidRows.size) {
            clearBrowserSessionData()
            val name="furybook-original-extras-"+webUuid();val writer=BrowserAcquisitionStore(owner(id),name,localWritesEnabled=true)
            assertTrue(writer.initialize())
            var ingress:BrowserAcquisitionSession?=null
            try {
                val row=if(index==0) knownRow else invalidRows[index-1]
                syntheticPull(writer,id,bootstrapReply(listOf(character),row)) { cloud,session ->
                    ingress=BrowserAcquisitionSession.open(session,{id},name,verifyOwner=cloud::verifyOwner)
                    val received=cloud.pull();val original=assertNotNull(received.originalLegacySource)
                    ingress!!.ingestBootstrap(received)
                    assertEquals(original,captureBrowserLegacySource(id));assertEquals(original,writer.originalLegacySourceForExport())
                    if(index>0) {
                        assertIs<MigrationResult.Corrupt>(writer.prepareMigration("hero",listOf("dubl-3.69"),null))
                        assertIs<CommitResult.StorageFailure>(writer.compareAndSet("hero",-1,assertNotNull(validCandidate)))
                        assertNull(writer.rawForExport("hero"));assertEquals(original,writer.originalLegacySourceForExport())
                    } else {
                        val pending=assertIs<MigrationResult.NeedsBalanceConfirmation>(writer.prepareMigration("hero",listOf("dubl-3.69"),null))
                        assertEquals(known,pending.pending.extras);assertFalse(pending.pending.character.creationComplete)
                        assertEquals(0.0,pending.pending.character.gear.loadManual);assertTrue(pending.pending.unresolvedOwnershipKeys.isNotEmpty())
                        val migrated=confirmLegacyBalance(pending.pending,Amount(-10,0),"Synthetic original balance")
                        validCandidate=migrated
                        val extrasWriter=BrowserCharacterExtrasStore()
                        extrasWriter.save("hero",known.copy(notes="Reachable local edit"))
                        assertIs<LoadResult.LegacyImportConflict>(writer.load("hero"))
                        assertIs<MigrationResult.Corrupt>(writer.prepareMigration("hero",listOf("dubl-3.69"),null))
                        assertIs<CommitResult.StorageFailure>(writer.compareAndSet("hero",-1,migrated))
                        assertEquals(original,writer.originalLegacySourceForExport());assertNull(writer.rawForExport("hero"))
                        extrasWriter.save("hero",known)
                        assertIs<LoadResult.LegacyImportConflict>(writer.load("hero"))
                        clearBrowserSessionData()
                        syntheticPull(writer,id,bootstrapReply(listOf(character),knownRow)) { fresh,_ -> ingress!!.ingestBootstrap(fresh.pull()) }
                        val changed=known.copy(notes="Changed after preparation")
                        syntheticPull(writer,id,bootstrapReply(listOf(character),js("JSON.parse")(com.furybook.core.cloud.CloudExtrasProjection.encode(changed)))) { next,_ ->
                            ingress!!.ingestBootstrap(next.pull())
                        }
                        assertEquals(CommitResult.Conflict,writer.compareAndSet("hero",-1,migrated));assertNull(writer.rawForExport("hero"))
                        ingress!!.ingestBootstrap(received)
                        assertEquals(CommitResult.Committed,writer.compareAndSet("hero",-1,migrated))
                        val saved=(writer.load("hero") as LoadResult.Loaded).state
                        assertEquals(known,saved.extras);assertEquals(-10L,balance(saved.ledger).xp)
                        assertEquals(original.snapshotRaw,saved.ledger.opening.legacyMigration!!.origin.raw)
                        assertEquals(original.extrasRaw,saved.ledger.opening.legacyMigration!!.origin.extrasSource!!.raw)
                        val retained=writer.rawForExport("hero")
                        val changedSource=original.copy(extrasRaw=original.extrasRaw!!.replace("Notes","Concurrent source"))
                        val changeDuringCas=BrowserAcquisitionStore(owner(id),name,localWritesEnabled=true,fault={stage ->
                            if(stage==BrowserAcquisitionWriteStage.AfterPutSucceeded) receiveBrowserLegacySource(id,changedSource)
                        })
                        assertTrue(changeDuringCas.initialize())
                        assertIs<CommitResult.StorageFailure>(changeDuringCas.compareAndSet("hero",0,saved.copy(storageRevision=1)))
                        assertEquals(retained,writer.rawForExport("hero"));changeDuringCas.close()
                        receiveBrowserLegacySource(id,original)
                        var job:Job?=null;var putSucceeded=false;var acknowledged=false
                        val cancel=BrowserAcquisitionStore(owner(id),name,localWritesEnabled=true,fault={stage ->
                            if(stage==BrowserAcquisitionWriteStage.AfterPutSucceeded) {putSucceeded=true;job!!.cancel()}
                        })
                        assertTrue(cancel.initialize())
                        job=launch(start=CoroutineStart.LAZY) {cancel.compareAndSet("hero",0,saved.copy(storageRevision=1));acknowledged=true}
                        job!!.start();job!!.join();assertTrue(putSucceeded && job!!.isCancelled && !acknowledged)
                        assertEquals(retained,writer.rawForExport("hero"));cancel.close()
                    }
                }
            } finally { ingress?.store?.close();writer.close();clearBrowserSessionData() }
        }
        println("ACTUAL_MAIN_ORIGINAL_EXTRAS|unknown-field-enum-malformed-refused-export-exact|all10-known-fields-backup|changed-preparation-CAS-abort|native-cancellation|PASS")
    }

    @Test fun actual_pull_main_ingress_preserves_supported_own_extras_key()=test {
        val id="own-extras-key-owner";val characterId="__proto__"
        val character=DublCharacter(characterId,experience=100)
        val known=CharacterSheetExtras(activeConditions=setOf(CharacterConditionId.TIRED),notes="valuable original")
        val rows=listOf(js("({notes:'valuable original',futureNote:'unsupported original',conditions:['UNKNOWN_CONDITION']})"),
            js("JSON.parse")(com.furybook.core.cloud.CloudExtrasProjection.encode(known)))
        for(index in rows.indices) {
            clearBrowserSessionData()
            val name="furybook-own-extras-key-"+webUuid();val writer=BrowserAcquisitionStore(owner(id),name,localWritesEnabled=true)
            assertTrue(writer.initialize());var ingress:BrowserAcquisitionSession?=null
            try {
                val row=rows[index]
                val expectedExtras="{\"version\":1,\"characters\":{\"__proto__\":"+(js("JSON.stringify")(row) as String)+"}}"
                if(index==0) {
                    // The genuine original key is supported; strict refusal is about its unknown content.
                    bindBrowserSessionOwner(id)
                    BrowserCharacterStore().replaceRaw(SnapshotCodec.encode(AppSnapshot(listOf(character),characterId)))
                    BrowserCharacterExtrasStore().replaceRaw(expectedExtras)
                    assertIs<MigrationResult.Corrupt>(writer.prepareMigration(characterId,listOf("dubl-3.69"),null))
                    clearBrowserSessionData()
                }
                val reply=bootstrapReply(listOf(character),row);reply.state.activeCharacterId=characterId
                syntheticPull(writer,id,reply) { cloud,session ->
                    ingress=BrowserAcquisitionSession.open(session,{id},name,verifyOwner=cloud::verifyOwner)
                    val received=cloud.pull();ingress!!.ingestBootstrap(received)
                    val original=assertNotNull(received.originalLegacySource)
                    assertEquals(expectedExtras,original.extrasRaw)
                    val originalMap=js("JSON.parse")(original.extrasRaw).characters
                    val displayMap=js("JSON.parse")(received.extras).characters
                    assertTrue(js("Object.prototype.hasOwnProperty.call")(originalMap,characterId) as Boolean)
                    assertTrue(js("Object.prototype.hasOwnProperty.call")(displayMap,characterId) as Boolean)
                    assertEquals(original,writer.originalLegacySourceForExport())
                    if(index==0) {
                        assertIs<MigrationResult.Corrupt>(writer.prepareMigration(characterId,listOf("dubl-3.69"),null))
                        assertIs<CommitResult.StorageFailure>(writer.compareAndSet(characterId,-1,state(characterId)))
                        assertNull(writer.rawForExport(characterId));assertEquals(original,writer.originalLegacySourceForExport())
                    } else {
                        assertEquals(known,BrowserCharacterExtrasStore().load(characterId))
                        val pending=assertIs<MigrationResult.NeedsBalanceConfirmation>(writer.prepareMigration(characterId,listOf("dubl-3.69"),null))
                        assertEquals(known,pending.pending.extras)
                        val migrated=confirmLegacyBalance(pending.pending,Amount(100,0),"Synthetic known100")
                        assertEquals(CommitResult.Committed,writer.compareAndSet(characterId,-1,migrated))
                        val saved=(writer.load(characterId) as LoadResult.Loaded).state
                        assertEquals(known,saved.extras)
                        assertEquals(expectedExtras,saved.ledger.opening.legacyMigration!!.origin.extrasSource!!.raw)
                        assertEquals(original.snapshotRaw,saved.ledger.opening.legacyMigration!!.origin.raw)
                    }
                }
            } finally { ingress?.store?.close();writer.close();clearBrowserSessionData() }
        }
        println("ACTUAL_MAIN_OWN_EXTRAS_KEY|__proto__|unknown-field-enum-refused|known-extras-backup-exact|PASS")
    }

    private fun authReply(id:String?="a",failure:Boolean=false):dynamic {
        val reply=js("({})");reply.error=if(failure) js("({message:'Synthetic Auth failure'})") else null
        reply.data=js("({})")
        val user=if(id==null) null else js("({})")
        if(user!=null) { user.id=id;user.email="synthetic@example.test" }
        reply.data.user=user;return reply
    }
    @Test fun server_principal_failures_and_account_change_precede_native_storage_access()=test {
        var current:String?="a";var nativeCalls=0;var verified=false
        val captured=WebAuthSession("a","synthetic@example.test")
        suspend fun open(server:suspend ()->dynamic,sdk:suspend ()->String?={current}):BrowserAcquisitionSession =
            BrowserAcquisitionSession.open(captured,{current},"furybook-auth-order-"+webUuid(),indexedDb={
                assertTrue(verified);nativeCalls++;window.asDynamic().indexedDB
            }) { pending -> verifyBrowserServerOwner(pending,{current},server,sdk).also { verified=true } }
        assertFails { open({authReply(failure=true)}) };assertEquals(0,nativeCalls)
        assertFails { open({authReply(null)}) };assertEquals(0,nativeCalls)
        assertFails { open({authReply(" ")}) };assertEquals(0,nativeCalls)
        assertFails { open({authReply("b")}) };assertEquals(0,nativeCalls)
        assertFails { open({authReply("a")},{"b"}) };assertEquals(0,nativeCalls)
        assertFails { open({current="b";authReply("a")},{"a"}) };assertEquals(0,nativeCalls)
        current="a"
        val online=open({authReply("a")});assertEquals(1,nativeCalls);assertTrue(online.states.isEmpty());online.store.close()
        println("ACTUAL_ROOT_OWNER_ORDER|synthetic-server-error-null-blank-mismatch-SDK-change-account-change|all-before-native-IDB")
    }
    @Test fun cold_offline_bootstrap_refuses_unverified_owner_but_retains_committed_paid_bytes()=test {
        val name="furybook-offline-owner-"+webUuid();val captured=WebAuthSession("synthetic-owner","synthetic@example.test")
        val online=BrowserAcquisitionSession.open(captured,{captured.userId},name) { pending ->
            verifyBrowserServerOwner(pending,{captured.userId},{authReply(captured.userId)},{captured.userId})
        }
        val trustedLiveScope=owner();val purchase=BrowserAcquisitionStore(trustedLiveScope,name,localWritesEnabled=true)
        assertTrue(purchase.initialize());assertEquals(CommitResult.Committed,purchase.compareAndSet("hero",-1,state()))
        buy(purchase,"offline-regression-buy");val before=purchase.rawForExport("hero")!!
        online.store.close();purchase.close()
        var offlineNativeCalls=0
        assertFails {
            BrowserAcquisitionSession.open(captured,{captured.userId},name,indexedDb={offlineNativeCalls++;window.asDynamic().indexedDB}) { pending ->
                verifyBrowserServerOwner(pending,{captured.userId},{error("Synthetic offline Auth unavailable")},{captured.userId})
            }
        }
        assertEquals(0,offlineNativeCalls)
        // A trusted scope retained in this live test process does not re-authenticate per load.
        val retained=BrowserAcquisitionStore(trustedLiveScope,name,localWritesEnabled=true)
        assertTrue(retained.initialize());assertEquals(before,retained.rawForExport("hero"))
        val saved=(retained.load("hero") as LoadResult.Loaded).state
        assertEquals(80L,balance(saved.ledger).xp);assertEquals(1,saved.receipts.size);assertEquals(1,saved.outbox.size)
        assertTrue(retained.capabilities.localDurable)
        assertEquals(CommitResult.Committed,retained.compareAndSet("hero",saved.storageRevision,saved.copy(storageRevision=saved.storageRevision+1)))
        val metadata=(retained.load("hero") as LoadResult.Loaded).state
        assertEquals(saved.receipts,metadata.receipts);assertEquals(saved.outbox,metadata.outbox);assertEquals(80L,balance(metadata.ledger).xp)
        assertFalse(retained.capabilities.cloudLedgerV2);retained.close()
        println("ACTUAL_ROOT_COLD_OFFLINE_GATE|previously-verified-paid80-bytes-retained|Auth-unavailable-before-IDB|cold-offline-publication-disabled")
    }

}
