package com.furybook.dubl.acquisition

import com.furybook.core.cloud.FuryCloudTransport
import com.furybook.dubl.application.*
import com.furybook.dubl.data.*
import com.furybook.dubl.model.*
import kotlin.coroutines.*
import kotlin.test.*

class AcquisitionRolloutTest {
    private fun run(block:suspend()->Unit) {
        var result:Result<Unit>?=null
        block.startCoroutine(object:Continuation<Unit>{override val context=EmptyCoroutineContext
            override fun resumeWith(value:Result<Unit>){result=value}})
        checkNotNull(result).getOrThrow()
    }
    private val source=RuleSourceRef("rollout-fixture","1","Synthetic 20 XP per rank; no requirements")
    private val ref=DefinitionRef("fixture","purchase")
    private val catalog=AcquisitionCatalog(mapOf(ref to AcquisitionDefinition(ref,AcquisitionKind.DEVELOPMENT,3,RepeatPolicy.Single,
        RequirementNode.AllOf("none",source,emptyList(),NoRequirementsEvidence(source,"None")),PriceRule.PerRank(20,0,source),
        SourceStatus.Confirmed,"Purchase",source)),"fixture-catalog","fixture-rules")
    private fun original()=StoredCharacter(DublCharacter("hero"),CharacterSheetExtras(notes="Preserve"),emptyList(),
        Ledger(Opening("original","fixture",Amount(100,0),RequirementNode.CreationPhase.Phase.Creating,OpeningProvenance.NewCharacter),emptyList()),
        emptyList(),emptyList(),SyncMetadata(0),0,0,RequirementNode.CreationPhase.Phase.Creating)
    private suspend fun paid():StoredCharacter {
        val store=InMemoryAcquisitionStore(mapOf("hero" to encodeEnvelope(original())))
        val service=AcquisitionService(store,{catalog}){"unused"}
        val request=AcquisitionRequest("hero",listOf(AcquisitionTarget(ref,"holding",1)))
        val preview=service.preview(request)
        assertIs<ApplyResult.Applied>(service.apply(ConfirmAcquisition("purchase","hero",request,preview.baseRevision,preview.previewToken)))
        return assertIs<LoadResult.Loaded>(store.load("hero")).state.also { assertEquals(Amount(80,0),balance(it.ledger)) }
    }
    @Test fun capability_missing_does_not_migrate_cloud()=run {
        val paid=paid();val raw=encodeEnvelope(paid);val payload=paid.outbox.single().payloadCanonical
        var tuples=0
        for(local in listOf(false,true)) for(cloud in listOf(false,true)) for(format in listOf(false,true)) {
            if(local&&cloud&&format) continue
            tuples++
            for(head in listOf(raw,"{\"formatVersion\":999}")) {
                val backing=InMemoryAcquisitionStore(mapOf("hero" to head));var loads=0;var writes=0;var calls=0;var archives=0;var ids=0
                val store=object:AcquisitionStore by backing {
                    override suspend fun load(characterId:String):LoadResult {loads++;return backing.load(characterId)}
                    override suspend fun compareAndSet(characterId:String,expectedStorageRevision:Long,next:StoredCharacter):CommitResult {writes++;return backing.compareAndSet(characterId,expectedStorageRevision,next)}
                }
                val transport=object:AcquisitionSyncTransport {
                    override suspend fun push(request:AcquisitionPush):AcquisitionPushResult {calls++;error("Disabled push")}
                    override suspend fun resolve(request:AcquisitionResolution):AcquisitionPushResult {calls++;error("Disabled resolution")}
                }
                val archive=object:AcquisitionConflictArchive {override suspend fun preserve(conflict:ArchivedAcquisitionConflict):CommitResult {archives++;error("Disabled archive")}}
                val caps=AcquisitionCapabilities(local,cloud,format)
                val protocol=AcquisitionSyncProtocol(store,transport,archive,caps){ids++;"new"}
                assertEquals(SyncEnvelopeResult.Disabled,protocol.syncPending("hero"))
                assertEquals(SyncEnvelopeResult.Disabled,protocol.resolveConflict("hero",LocalOrServer.Local,0))
                val rpc=AcquisitionRpcTransport(object:FuryCloudTransport{override suspend fun rpc(function:String,jsonBody:String):String {calls++;error("Disabled RPC")}},caps)
                assertFailsWith<IllegalStateException>{rpc.push(AcquisitionPush("hero","purchase",0,payload))}
                assertFailsWith<IllegalStateException>{rpc.resolve(AcquisitionResolution("hero","resolution",0,LocalOrServer.Local,"archive",payload))}
                assertEquals(listOf(0,0,0,0,0),listOf(loads,writes,calls,archives,ids))
                val restored=backing.load("hero")
                if(head==raw) assertEquals(raw,encodeEnvelope(assertIs<LoadResult.Loaded>(restored).state))
                else assertEquals(head,assertIs<LoadResult.Unsupported>(restored).raw)
            }
        }
        assertEquals(7,tuples)
    }
    @Test fun explicit_protected_construction_without_collaborators_fences_old_writers() {
        var writes=0;var ids=0
        val snapshot=AppSnapshot(listOf(original().character),"hero");val extras=original().extras
        val app=DublApplication(object:CharacterStore{override fun load()=snapshot;override fun save(snapshot:AppSnapshot){writes++}},
            object:CharacterExtrasStore{override fun load(characterId:String)=extras;override fun save(characterId:String,extras:CharacterSheetExtras){writes++};override fun delete(characterId:String){writes++}},
            {ids++;"new"},writePolicy=CanonicalWritePolicy.Protected)
        val actions=listOf<()->Any?>(
            {app.character.setName("Changed")},{app.character.changeAttribute(AttributeId.STRENGTH,1)},
            {app.character.setExperience(900)},{app.character.completeCreation()},{app.character.createCharacter()},
            {app.character.deleteActive()},{app.skills.addSpecialized("knowledge_template","Geography")},
            {app.magic.setManaRank(1)},{app.sheet.setPortrait("changed")},{app.sheet.setNotes("changed")},
            {app.transfer.exportActive()},{app.transfer.importCharacter("{}")},{app.undoLast()})
        for(action in actions) {
            assertFailsWith<CanonicalWriteRequired>{action()}
            assertEquals(snapshot,app.snapshot);assertEquals(extras,app.activeExtras)
            assertEquals(0,writes);assertEquals(0,ids)
        }
        assertEquals(13,actions.size)
    }
    @Test fun rollback_read_export_disables_new_writes_without_down_conversion()=run {
        val paid=paid();val raw=encodeEnvelope(paid)
        for(disabled in listOf(AcquisitionCapabilities(false,false,true),AcquisitionCapabilities(true,false,false))) {
            val backing=InMemoryAcquisitionStore(mapOf("hero" to raw));var writes=0;var ids=0;var edits=0
            val store=object:AcquisitionStore by backing {override suspend fun compareAndSet(characterId:String,expectedStorageRevision:Long,next:StoredCharacter):CommitResult{writes++;return backing.compareAndSet(characterId,expectedStorageRevision,next)}}
            val legacy=object:CharacterStore{override fun load()=AppSnapshot(listOf(paid.character),"hero");override fun save(snapshot:AppSnapshot){error("Legacy save")}}
            val extras=object:CharacterExtrasStore{override fun load(characterId:String)=paid.extras;override fun save(characterId:String,extras:CharacterSheetExtras){error("Legacy extras")}}
            val app=DublApplication(legacy,extras,{ids++;"new"},acquisitionService=AcquisitionService(store,{catalog}){"instance"},
                mutationCoordinator=CharacterMutationCoordinator(store,{catalog}){"instance"},acquisitionUndo=AcquisitionUndo(store))
            var caps=AcquisitionCapabilities(true,false,true)
            val scope=AcquisitionScope("store","hero",0,0,0)
            val controller=AcquisitionController(ApplicationAcquisitionCommandPort(app,store,{catalog},{caps},{true}),{scope},{ids++;"op"})
            val request=AcquisitionRequest("hero",listOf(AcquisitionTarget(ref,"holding",2)))
            val ready=controller.preview(request);assertTrue(ready.canConfirm,ready.toString())
            caps=disabled
            assertFalse(controller.confirm(ready.key).canConfirm)
            val reviews=listOf<suspend()->AcquisitionUiModel>(
                {controller.preview(request)},{controller.prepareGrant(Amount(1,0),"Award")},
                {controller.prepareManual(CharacterPatch.fromCanonicalState(paid),Amount(1,0),"Correction",paid.revision)},
                {controller.prepareCompleteCreation()},{controller.prepareNonFinancial{edits++;character.setName("No")}},
                {controller.prepareUndo()})
            for(review in reviews){val view=review();assertFalse(view.canConfirm);assertFalse(controller.confirm(view.key).canConfirm)}
            assertEquals(0,writes);assertEquals(0,ids);assertEquals(0,edits)
            assertEquals(Amount(80,0),controller.readCanonicalView().balance)
            val state=assertIs<LoadResult.Loaded>(backing.load("hero")).state
            assertEquals(raw,encodeEnvelope(state))
            val export=CharacterTransferCodec.encodeEnvelope(state)
            assertEquals(state,assertIs<LoadResult.Loaded>(CharacterTransferCodec.decodeEnvelope(export)).state)
            assertIs<CharacterTransferDecodeResult.Failure>(CharacterTransferCodec.decode(export){error("No legacy ID")})
        }
    }
}
