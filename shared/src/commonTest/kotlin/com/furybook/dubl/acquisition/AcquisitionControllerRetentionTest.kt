package com.furybook.dubl.acquisition

import com.furybook.dubl.application.*
import com.furybook.dubl.data.*
import com.furybook.dubl.model.*
import kotlinx.coroutines.*
import kotlin.coroutines.*
import kotlin.test.*

class AcquisitionControllerRetentionTest {
    private fun run(block:suspend()->Unit) {
        var result:Result<Unit>?=null
        block.startCoroutine(object:Continuation<Unit> { override val context=EmptyCoroutineContext;override fun resumeWith(value:Result<Unit>) { result=value } })
        checkNotNull(result).getOrThrow()
    }
    private val source=RuleSourceRef("fixture","1","Explicit20")
    private val ref=DefinitionRef("test","target")
    private val catalog=AcquisitionCatalog(mapOf(ref to AcquisitionDefinition(ref,AcquisitionKind.DEVELOPMENT,1,RepeatPolicy.Single,
        RequirementNode.AllOf("none",source,emptyList(),NoRequirementsEvidence(source,"None")),PriceRule.Fixed(20,0,source),SourceStatus.Confirmed,"Приобретение",source)),"catalog","rules")
    private fun state(id:String)=StoredCharacter(DublCharacter(id),CharacterSheetExtras(),emptyList(),Ledger(Opening("opening-$id","source",Amount(100,2),RequirementNode.CreationPhase.Phase.Creating,OpeningProvenance.NewCharacter),emptyList()),emptyList(),emptyList(),SyncMetadata(0),0,0,RequirementNode.CreationPhase.Phase.Creating)
    private fun request(id:String)=AcquisitionRequest(id,listOf(AcquisitionTarget(ref,"holding-$id",1)))
    private fun application(store:AcquisitionStore)=DublApplication(
        object:CharacterStore { override fun load()=AppSnapshot(listOf(state("A").character,state("B").character),"A");override fun save(snapshot:AppSnapshot)=error("legacy write") },
        object:CharacterExtrasStore { override fun load(characterId:String)=CharacterSheetExtras();override fun save(characterId:String,extras:CharacterSheetExtras)=error("legacy extras") },{"unused"},
        acquisitionService=AcquisitionService(store,{catalog},{"unused"}))

    @Test fun pending_A_does_not_block_B_and_return_to_A_retains_exact_command()=run {
        for(committedBeforeLostReply in listOf(false,true)) {
            val backing=InMemoryAcquisitionStore(listOf("A","B").associateWith { encodeEnvelope(state(it)) })
            val entered=CompletableDeferred<Unit>();val release=CompletableDeferred<Unit>();var aWrites=0;var ids=0
            val store=object:AcquisitionStore by backing { override suspend fun compareAndSet(characterId:String,expectedStorageRevision:Long,next:StoredCharacter):CommitResult {
                if(characterId=="A" && ++aWrites==1) {
                    entered.complete(Unit);release.await()
                    if(committedBeforeLostReply) backing.compareAndSet(characterId,expectedStorageRevision,next)
                    return CommitResult.StorageFailure("unknown first result")
                }
                return backing.compareAndSet(characterId,expectedStorageRevision,next)
            }}
            var selected=AcquisitionScope("owner-store","A",0,0,0)
            val notifications=mutableListOf<AcquisitionUiModel?>()
            val control=AcquisitionController(ApplicationAcquisitionCommandPort(application(store),store,{catalog},{AcquisitionCapabilities(true,false,true)},
                { it==selected }),{selected},{"operation-${++ids}"}) { notifications+=it }
            val a=control.preview(request("A"))
            coroutineScope {
                val applying=async(Dispatchers.Unconfined,start=CoroutineStart.UNDISPATCHED){control.confirm(a.key)}
                entered.await()
                try {
                    selected=selected.copy(characterId="B",viewCharacterId="B",selectionGeneration=1)
                    val b=control.preview(request("B"));assertEquals(AcquisitionUiPhase.Ready,b.phase)
                    assertEquals(AcquisitionUiPhase.Committed,control.confirm(b.key).phase)
                    assertEquals("B",control.model?.scope?.characterId)
                } finally { release.complete(Unit) }
                assertEquals(AcquisitionUiPhase.Failed,applying.await().phase)
            }
            assertEquals("B",notifications.last()?.scope?.characterId)
            selected=selected.copy(characterId="A",viewCharacterId="A",selectionGeneration=2)
            assertEquals(a.key,control.reopen()?.key);assertEquals("operation-1",control.retainedOperationId)
            val found=control.reconcilePending()
            if(!committedBeforeLostReply) {
                assertEquals(AcquisitionUiPhase.Failed,found.phase)
                assertEquals(AcquisitionUiPhase.Committed,control.retrySameCommand().phase)
            } else assertEquals(AcquisitionUiPhase.Committed,found.phase)
            assertEquals("operation-1",control.retainedOperationId);assertEquals(2,ids)
            for(id in listOf("A","B")) {
                val saved=assertIs<LoadResult.Loaded>(backing.load(id)).state
                assertEquals(Amount(80,2),balance(saved.ledger));assertEquals(1,saved.receipts.size)
            }
            assertEquals(if(committedBeforeLostReply)1 else 2,aWrites)
        }
    }
    @Test fun wrong_owner_never_exposes_or_rebinds_a_retained_character_command()=run {
        val backing=InMemoryAcquisitionStore(mapOf("A" to encodeEnvelope(state("A"))));var writes=0;var ids=0
        val store=object:AcquisitionStore by backing { override suspend fun compareAndSet(characterId:String,expectedStorageRevision:Long,next:StoredCharacter):CommitResult {
            writes++;if(writes==1)return CommitResult.StorageFailure("not acknowledged")
            return backing.compareAndSet(characterId,expectedStorageRevision,next)
        }}
        var selected=AcquisitionScope("owner-store","A",0,0,0)
        var ownerCurrent=true
        val control=AcquisitionController(ApplicationAcquisitionCommandPort(application(store),store,{catalog},{AcquisitionCapabilities(true,false,true)},{ownerCurrent && it==selected}),{selected},{"operation-${++ids}"})
        val a=control.preview(request("A"));assertEquals(AcquisitionUiPhase.Failed,control.confirm(a.key).phase)
        // The browser store can revoke its captured owner without changing the scope key.
        ownerCurrent=false
        assertNull(control.model);assertNull(control.retainedOperationId)
        assertTrue(runCatching { control.retrySameCommand() }.isFailure)
        assertTrue(runCatching { control.reconcilePending() }.isFailure)
        assertEquals(1,writes);assertEquals(1,ids)
        ownerCurrent=true
        selected=selected.copy(storageScopeId="other-owner",accountGeneration=1,selectionGeneration=1)
        assertNull(control.model);assertNull(control.retainedOperationId)
        assertTrue(runCatching { control.retrySameCommand() }.isFailure)
        assertTrue(runCatching { control.reconcilePending() }.isFailure)
        assertEquals(1,writes);assertEquals(1,ids)
        selected=selected.copy(storageScopeId="owner-store",accountGeneration=0,selectionGeneration=2)
        assertEquals("operation-1",control.retainedOperationId)
        assertEquals(AcquisitionUiPhase.Committed,control.retrySameCommand().phase)
        assertEquals(2,writes);assertEquals(1,ids)
    }
    @Test fun old_quote_keys_never_return_pending_or_committed_models_to_a_revoked_or_different_owner()=run {
        for(committed in listOf(false,true)) for(revoked in listOf(false,true)) {
            val backing=InMemoryAcquisitionStore(mapOf("A" to encodeEnvelope(state("A"))));var writes=0;var ids=0
            val store=object:AcquisitionStore by backing { override suspend fun compareAndSet(characterId:String,expectedStorageRevision:Long,next:StoredCharacter):CommitResult {
                writes++;return if(committed)backing.compareAndSet(characterId,expectedStorageRevision,next) else CommitResult.StorageFailure("pending")
            }}
            var selected=AcquisitionScope("owner-store","A",0,0,0);var ownerCurrent=true
            val control=AcquisitionController(ApplicationAcquisitionCommandPort(application(store),store,{catalog},{AcquisitionCapabilities(true,false,true)},
                {ownerCurrent && it==selected}),{selected},{"operation-${++ids}"})
            val key=control.preview(request("A")).key
            assertEquals(if(committed)AcquisitionUiPhase.Committed else AcquisitionUiPhase.Failed,control.confirm(key).phase)
            if(revoked)ownerCurrent=false else selected=selected.copy(storageScopeId="different-owner",accountGeneration=1)
            assertNull(control.model)
            assertFailsWith<IllegalStateException> { control.confirm(key) }
            assertFailsWith<IllegalStateException> { control.readReviewDetails(key) }
            assertFailsWith<IllegalStateException> { control.refresh(key) }
            assertEquals(1,writes);assertEquals(1,ids)
            ownerCurrent=true;selected=AcquisitionScope("owner-store","A",0,2,0)
            assertEquals("operation-1",control.retainedOperationId)
            assertEquals(if(committed)AcquisitionUiPhase.Committed else AcquisitionUiPhase.Failed,control.confirm(key).phase)
            assertEquals(1,writes);assertEquals(1,ids)
        }
    }

}
