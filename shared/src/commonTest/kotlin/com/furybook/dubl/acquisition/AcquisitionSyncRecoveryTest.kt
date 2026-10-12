package com.furybook.dubl.acquisition

import com.furybook.dubl.model.*
import com.furybook.dubl.data.SnapshotCodec
import com.furybook.core.json.*
import kotlinx.coroutines.runBlocking
import kotlin.test.*

/** Real strict codecs/CAS, with only the remote envelope receipt store represented by a fixture. */
class AcquisitionSyncRecoveryTest {
    private fun runTest(block:suspend ()->Unit)=runBlocking { block() }
    private val caps=AcquisitionCapabilities(true,true,true)
    private val catalog=AcquisitionCatalog(emptyMap(),"fixture","fixture")
    private fun initial()=StoredCharacter(DublCharacter("hero"),CharacterSheetExtras(),emptyList(),
        Ledger(Opening("new","funding",Amount(100,0),RequirementNode.CreationPhase.Phase.Creating,OpeningProvenance.NewCharacter),emptyList()),
        emptyList(),emptyList(),SyncMetadata(0),0,0,RequirementNode.CreationPhase.Phase.Creating)
    private fun store(state:StoredCharacter=initial())=InMemoryAcquisitionStore(mapOf(state.character.id to encodeEnvelope(state)))
    private suspend fun load(store:AcquisitionStore,id:String="hero")=assertIs<LoadResult.Loaded>(store.load(id)).state
    private suspend fun grant(store:AcquisitionStore,id:String,delta:Long=0)=assertIs<MutationResult.Applied>(
        CharacterMutationCoordinator(store,{catalog},{"unused"}).mutate(CanonicalMutation(id,"hero",load(store).revision,MutationPayload.Grant(Amount(delta,0)),"Explicit fixture")))
    private class Archive:AcquisitionConflictArchive {
        val records=linkedMapOf<String,ArchivedAcquisitionConflict>()
        override suspend fun preserve(conflict:ArchivedAcquisitionConflict):CommitResult {
            records[conflict.archiveId]?.let { assertEquals(it,conflict) }
            records[conflict.archiveId]=conflict;return CommitResult.Committed
        }
    }
    private class Server(var canonical:String,var revision:Long=0):AcquisitionSyncTransport {
        val receipts=linkedMapOf<String,AcquisitionPushResult.Acknowledged>()
        val resolutions=mutableListOf<AcquisitionResolution>()
        var loseResolution=false
        var afterResolution:(suspend ()->Unit)?=null
        override suspend fun push(request:AcquisitionPush):AcquisitionPushResult=accept(request.operationId,request.payloadCanonical,request.parentServerRevision)
        private fun accept(id:String,wire:String,parent:Long):AcquisitionPushResult {
            receipts[id]?.let { return if(it.payloadCanonical==wire) it else AcquisitionPushResult.IdentityConflict("different") }
            if(parent!=revision)return AcquisitionPushResult.Conflict(revision,canonical)
            canonical=wire;revision++
            return AcquisitionPushResult.Acknowledged(id,wire,revision).also { receipts[id]=it }
        }
        override suspend fun resolve(request:AcquisitionResolution):AcquisitionPushResult {
            resolutions+=request
            val response=accept(request.operationId,request.selectedCanonical,request.expectedServerRevision)
            afterResolution?.also { afterResolution=null }?.invoke()
            if(loseResolution){loseResolution=false;error("lost accepted resolution response")}
            return response
        }
    }
    private fun protocol(store:AcquisitionStore,server:AcquisitionSyncTransport,archive:Archive=Archive(),ids:()->String={"resolution"})=
        AcquisitionSyncProtocol(store,server,archive,caps,ids)
    private suspend fun conflicted():Triple<InMemoryAcquisitionStore,Server,Archive> {
        val local=store();val remote=store();grant(local,"local",-80);grant(remote,"remote",-70)
        val server=Server(encodeSyncEnvelope(load(remote)),1);val archive=Archive()
        assertIs<SyncEnvelopeResult.Conflict>(protocol(local,server,archive).syncPending("hero"))
        return Triple(local,server,archive)
    }
    @Test fun inherited_copy_holding_rejects_unpaid_rank_parameter_and_option_changes()=runTest {
        val character=DublCharacter("hero",development=mapOf("old" to OwnedDevelopment(1)))
        val raw=SnapshotCodec.encode(AppSnapshot(listOf(character),"hero"));val origin=LegacyOrigin("hero",11,"7",raw,listOf("dubl-3.69"))
        val base=confirmLegacyBalance(assertIs<MigrationResult.NeedsBalanceConfirmation>(prepareLegacy(raw,origin,null)).pending,Amount(100,0),"Known balance")
        val ref=DefinitionRef("test","old");val legacy=AcquisitionOrigin.Legacy("hero","11","7",origin.sourceFingerprint,"development:old")
        val id=legacyInstanceId(ref,legacy);val source=RuleSourceRef("fixture","1","Explicit fixture")
        val proven=AcquisitionCatalog(mapOf(ref to AcquisitionDefinition(ref,AcquisitionKind.DEVELOPMENT,3,RepeatPolicy.Single,
            RequirementNode.AllOf("none",source,emptyList(),NoRequirementsEvidence(source,"None")),PriceRule.PerRank(20,0,source),SourceStatus.Confirmed,"Old",source,
            parameterDefinitions=listOf(ParameterDefinition("element",ParameterDomain.StableIds(setOf("fire","water")))))),"fixture","fixture")
        val firstStore=store(copyAcquisitionCharacter(base,"copy1","import1"))
        assertIs<MutationResult.Applied>(CharacterMutationCoordinator(firstStore,{proven},{"unused"}).mutate(CanonicalMutation("confirm1","copy1",1,
            MutationPayload.ConfirmLegacyParameter(id,mapOf("element" to "fire"),LegacyOwnershipResolution("development:old",ref,1)),"Original holding")))
        val confirmedFirst=load(firstStore,"copy1")
        assertFailsWith<IllegalArgumentException> { encodeEnvelope(confirmedFirst.copy(instances=emptyList(),outbox=emptyList())) }
        val second=copyAcquisitionCharacter(confirmedFirst,"copy2","import2")
        for(forged in listOf(second.instances.single().copy(rank=2),second.instances.single().copy(parameters=mapOf("element" to "water")),second.instances.single().copy(abilityOptionId="forged"))) {
            assertFailsWith<IllegalArgumentException> { encodeEnvelope(second.copy(instances=listOf(forged),outbox=emptyList())) }
            assertFailsWith<IllegalArgumentException> { encodeSyncEnvelope(second.copy(instances=listOf(forged),outbox=emptyList())) }
        }
        assertFailsWith<IllegalArgumentException> { encodeEnvelope(second.copy(instances=emptyList(),outbox=emptyList())) }
        val target=store(second);val service=AcquisitionService(target,{proven},{"unused"})
        val request=AcquisitionRequest("copy2",listOf(AcquisitionTarget(ref,id,2,mapOf("element" to "fire"))))
        val preview=service.preview(request)
        assertIs<ApplyResult.Applied>(service.apply(ConfirmAcquisition("paid","copy2",request,preview.baseRevision,preview.previewToken)))
        val paid=load(target,"copy2");assertEquals(Amount(80,0),balance(paid.ledger));assertEquals(2,paid.instances.single().rank)
        assertEquals(paid,assertIs<LoadResult.Loaded>(decodeEnvelope(encodeEnvelope(paid))).state)
        assertIs<ApplyResult.Applied>(AcquisitionUndo(target).undo("copy2","undo-paid",paid.revision))
        assertEquals(1,load(target,"copy2").instances.single().rank)
        assertEquals(Amount(100,0),balance(load(target,"copy2").ledger))
        val current=load(target,"copy2")
        val counterfeit=current.copy(instances=listOf(current.instances.single().copy(rank=2)),outbox=emptyList())
        assertFailsWith<IllegalArgumentException> { CharacterPatch.fromCanonicalState(counterfeit) }
        assertFailsWith<IllegalArgumentException> { CharacterPatch(current.character,instances=current.instances) }
        val proofless=current.copy(instances=listOf(current.instances.single().copy(parameters=mapOf("element" to "water"))),outbox=emptyList(),storageRevision=current.storageRevision+1)
        assertIs<CommitResult.StorageFailure>(target.compareAndSet("copy2",current.storageRevision,proofless))
        val correction=CharacterPatch.fromCanonicalState(current,instances=listOf(current.instances.single().copy(parameters=mapOf("element" to "water"))))
        assertIs<MutationResult.Applied>(CharacterMutationCoordinator(target,{proven},{"unused"}).mutate(CanonicalMutation("correct","copy2",current.revision,
            MutationPayload.ManualCorrection(correction,Amount.ZERO),"Explicit corrected parameter")))
        val corrected=load(target,"copy2");assertEquals("water",corrected.instances.single().parameters["element"])
        assertEquals(corrected,assertIs<LoadResult.Loaded>(decodeEnvelope(encodeEnvelope(corrected))).state)
        val changedRequest=corrected.receipts.last().requestCanonical.replace("water","fire")
        val evidence=JsonReader(corrected.ledger.entries.last().evidenceCanonical).read() as JsonValue.Obj
        val forgedEvidence=canonicalJson(jsonStringify(JsonValue.Obj(evidence.values+("command" to JsonReader(changedRequest).read()))))
        val mismatched=corrected.copy(outbox=emptyList(),receipts=corrected.receipts.dropLast(1)+corrected.receipts.last().copy(requestCanonical=changedRequest),
            ledger=corrected.ledger.copy(entries=corrected.ledger.entries.dropLast(1)+corrected.ledger.entries.last().copy(evidenceCanonical=forgedEvidence)))
        assertFailsWith<IllegalArgumentException> { encodeEnvelope(mismatched) }
        assertIs<ApplyResult.Applied>(AcquisitionUndo(target).undo("copy2","undo-correct",corrected.revision))
        val restored=load(target,"copy2");assertEquals("fire",restored.instances.single().parameters["element"])
        assertIs<MutationResult.Applied>(CharacterMutationCoordinator(target,{proven},{"unused"}).mutate(CanonicalMutation("zero","copy2",restored.revision,MutationPayload.Grant(Amount.ZERO),"Explicit zero")))
        val afterZero=load(target,"copy2")
        val stalePatch=CharacterPatch.fromCanonicalState(restored,instances=listOf(restored.instances.single().copy(parameters=mapOf("element" to "water"))))
        assertIs<MutationResult.Stale>(CharacterMutationCoordinator(target,{proven},{"unused"}).mutate(CanonicalMutation("stale-correction","copy2",restored.revision,
            MutationPayload.ManualCorrection(stalePatch,Amount.ZERO),"Explicit but stale correction")))
        assertEquals(afterZero,load(target,"copy2"))
        val removePatch=CharacterPatch.fromCanonicalState(afterZero,instances=emptyList())
        assertIs<MutationResult.Applied>(CharacterMutationCoordinator(target,{proven},{"unused"}).mutate(CanonicalMutation("remove","copy2",afterZero.revision,
            MutationPayload.ManualCorrection(removePatch,Amount.ZERO),"Explicit holding removal")))
        val removed=load(target,"copy2");assertTrue(removed.instances.isEmpty())
        assertEquals(removed,assertIs<LoadResult.Loaded>(decodeEnvelope(encodeEnvelope(removed))).state)
        assertIs<ApplyResult.Applied>(AcquisitionUndo(target).undo("copy2","undo-remove",removed.revision))
        assertEquals(afterZero.instances,load(target,"copy2").instances)
        assertFailsWith<IllegalArgumentException> { encodeEnvelope(afterZero.copy(instances=listOf(afterZero.instances.single().copy(rank=2)),outbox=emptyList())) }
    }
    @Test fun four_successive_selections_have_bounded_growth_and_strict_reopen()=runTest {
        var base=initial();val server=Server(encodeSyncEnvelope(base));var seq=0;var previous=0
        for(round in 1..4) {
            val local=store(base);val remote=store(base);grant(local,"local$round");grant(remote,"remote$round")
            assertIs<SyncEnvelopeResult.Synced>(protocol(remote,server).syncPending("hero"))
            val archive=Archive();val p=protocol(local,server,archive,{"resolution${++seq}"})
            assertIs<SyncEnvelopeResult.Conflict>(p.syncPending("hero"))
            assertIs<SyncEnvelopeResult.Synced>(p.resolveConflict("hero",if(round%2==0)LocalOrServer.Server else LocalOrServer.Local,server.revision))
            base=load(local);val wire=encodeSyncEnvelope(base)
            if(previous>0)assertTrue(wire.length<previous*4,"Recursive amplification round $round: ${wire.length} from $previous")
            assertTrue(wire.length<500_000,"Four small selections must remain bounded: ${wire.length}")
            assertEquals(base,assertIs<LoadResult.Loaded>(decodeEnvelope(encodeEnvelope(base))).state)
            assertEquals(base.character,decodeSyncEnvelope(wire).character);assertEquals(1,archive.records.size)
            previous=wire.length
        }
    }
    @Test fun resolution_reply_loss_restarts_with_identical_dedicated_request()=runTest {
        val (local,server,archive)=conflicted();server.loseResolution=true;var seq=0
        assertIs<SyncEnvelopeResult.Retryable>(protocol(local,server,archive,{"resolution${++seq}"}).resolveConflict("hero",LocalOrServer.Server,1))
        val restarted=store(load(local))
        assertIs<SyncEnvelopeResult.Synced>(protocol(restarted,server,archive,{"different-after-restart"}).resolveConflict("hero",LocalOrServer.Server,1))
        assertEquals(server.resolutions[0],server.resolutions[1]);assertEquals(1,archive.records.size)
        assertEquals(Amount(30,0),balance(load(restarted).ledger));assertEquals(2L,server.revision)
    }
    @Test fun resolution_local_storage_failure_restarts_same_request_without_overwriting_newer_branch()=runTest {
        val (local,server,archive)=conflicted();var fail=false
        val failing=object:AcquisitionStore {
            override suspend fun load(characterId:String)=local.load(characterId)
            override suspend fun compareAndSet(characterId:String,expectedStorageRevision:Long,next:StoredCharacter)=
                if(fail){fail=false;CommitResult.StorageFailure("after server accepted")}else local.compareAndSet(characterId,expectedStorageRevision,next)
        }
        server.afterResolution={fail=true}
        assertIs<SyncEnvelopeResult.StorageFailure>(protocol(failing,server,archive).resolveConflict("hero",LocalOrServer.Server,1))
        val restarted=store(load(local));grant(restarted,"new-local",10)
        assertIs<SyncEnvelopeResult.Conflict>(protocol(restarted,server,archive,{"different"}).resolveConflict("hero",LocalOrServer.Server,1))
        assertEquals(server.resolutions[0],server.resolutions[1]);assertEquals(Amount(30,0),balance(load(restarted).ledger))
        assertTrue(load(restarted).ledger.entries.any { it.operationId=="new-local" });assertNotNull(load(restarted).syncMetadata.remoteConflictCanonical)
    }
    @Test fun ordinary_tip_and_resolution_proof_are_bound_in_public_codec()=runTest {
        val (local,server,archive)=conflicted()
        assertIs<SyncEnvelopeResult.Synced>(protocol(local,server,archive).resolveConflict("hero",LocalOrServer.Server,1))
        val adopted=load(local);val wire=encodeSyncEnvelope(adopted)
        assertFailsWith<IllegalArgumentException> { AcquisitionSyncWire.encodePush(AcquisitionPush("hero","remote",2,wire)) }
        assertFailsWith<IllegalArgumentException> { encodeEnvelope(adopted.copy(outbox=listOf(OutboxItem("remote",2,wire)))) }
        assertFailsWith<IllegalArgumentException> { AcquisitionSyncWire.encodeResolution(AcquisitionResolution("hero","invented",0,LocalOrServer.Server,"invented",encodeSyncEnvelope(initial()))) }
        val good=server.resolutions.single()
        for(bad in listOf(good.copy(operationId="remote"),good.copy(choice=LocalOrServer.Local),good.copy(archiveId="forged"),good.copy(expectedServerRevision=0)))
            assertFailsWith<IllegalArgumentException> { AcquisitionSyncWire.encodeResolution(bad) }
        grant(local,"fresh")
        val next=load(local);val pending=next.outbox.single()
        AcquisitionSyncWire.encodePush(AcquisitionPush("hero","fresh",pending.parentServerRevision,pending.payloadCanonical))
    }
    @Test fun pooled_proofs_refuse_forged_missing_recursive_and_cyclic_records_and_support_copy()=runTest {
        val (local,server,archive)=conflicted()
        assertIs<SyncEnvelopeResult.Synced>(protocol(local,server,archive).resolveConflict("hero",LocalOrServer.Server,1))
        val saved=load(local);assertTrue(saved.syncProofs.isNotEmpty())
        val first=saved.syncProofs.first()
        val altered=first.copy(payloadCanonical=first.payloadCanonical.replace("funding","forged"))
        val cyclic=first.copy(proofIds=listOf(first.proofId))
        val root=JsonReader(first.payloadCanonical).read() as JsonValue.Obj
        val recursive=canonicalJson(jsonStringify(JsonValue.Obj(root.values+("syncProofs" to jsonArray(emptyList())))))
        val nested=first.copy(proofId=acquisitionSyncProofId(recursive,first.proofIds),payloadCanonical=recursive)
        for(records in listOf(saved.syncProofs.drop(1),listOf(altered)+saved.syncProofs.drop(1),listOf(cyclic)+saved.syncProofs.drop(1),
            (listOf(nested)+saved.syncProofs.drop(1)).sortedBy { it.proofId },saved.syncProofs+first)) {
            assertFailsWith<IllegalArgumentException> { encodeEnvelope(saved.copy(syncProofs=records,outbox=emptyList())) }
            assertFailsWith<IllegalArgumentException> { encodeSyncEnvelope(saved.copy(syncProofs=records,outbox=emptyList())) }
        }
        val copied=copyAcquisitionCharacter(saved,"copy","import")
        assertEquals(saved.ledger.entries,copied.ledger.entries.dropLast(1));assertEquals(saved.syncProofs,copied.syncProofs)
        assertEquals(copied,assertIs<LoadResult.Loaded>(decodeEnvelope(encodeEnvelope(copied))).state)
        val second=copyAcquisitionCharacter(copied,"copy2","import2")
        assertEquals(second,assertIs<LoadResult.Loaded>(decodeEnvelope(encodeEnvelope(second))).state)
        assertEquals(second.ledger,decodeSyncEnvelope(encodeSyncEnvelope(second)).ledger)
    }
    @Test fun copied_branch_can_be_selected_then_publish_a_fresh_ordinary_operation()=runTest {
        val (source,originalServer,sourceArchive)=conflicted()
        assertIs<SyncEnvelopeResult.Synced>(protocol(source,originalServer,sourceArchive).resolveConflict("hero",LocalOrServer.Server,1))
        val copied=copyAcquisitionCharacter(load(source),"copy","import")
        val local=store(copied);val remote=store(copied)
        for((target,id) in listOf(local to "copy-local",remote to "copy-remote"))
            assertIs<MutationResult.Applied>(CharacterMutationCoordinator(target,{catalog},{"unused"}).mutate(CanonicalMutation(id,"copy",copied.revision,MutationPayload.Grant(Amount.ZERO),"Explicit fixture")))
        val server=Server(encodeSyncEnvelope(load(remote,"copy")),1);val archive=Archive();val p=protocol(local,server,archive,{"copy-resolution"})
        assertIs<SyncEnvelopeResult.Conflict>(p.syncPending("copy"))
        assertIs<SyncEnvelopeResult.Synced>(p.resolveConflict("copy",LocalOrServer.Server,1))
        val chosen=load(local,"copy");assertEquals(Amount(30,0),balance(chosen.ledger))
        assertEquals(chosen,assertIs<LoadResult.Loaded>(decodeEnvelope(encodeEnvelope(chosen))).state)
        assertIs<MutationResult.Applied>(CharacterMutationCoordinator(local,{catalog},{"unused"}).mutate(CanonicalMutation("copy-fresh","copy",chosen.revision,MutationPayload.Grant(Amount.ZERO),"Explicit fixture")))
        val pending=load(local,"copy").outbox.single()
        AcquisitionSyncWire.encodePush(AcquisitionPush("copy","copy-fresh",pending.parentServerRevision,pending.payloadCanonical))
        assertIs<SyncEnvelopeResult.Synced>(p.syncPending("copy"))
        assertTrue(load(local,"copy").outbox.isEmpty());assertEquals(Amount(30,0),balance(load(source).ledger))
    }
    @Test fun old_valid_inline_v1_adoption_remains_readable_without_rewriting_evidence()=runTest {
        val (local,server,archive)=conflicted()
        assertIs<SyncEnvelopeResult.Synced>(protocol(local,server,archive).resolveConflict("hero",LocalOrServer.Server,1))
        val saved=load(local);val captured=archive.records.values.single()
        val proof=canonicalJson(jsonStringify(jsonObject("schema" to jsonString("sync-adoption-v1"),"selectedCanonical" to jsonString(captured.serverCanonical),
            "displacedCanonical" to jsonString(captured.localCanonical),"serverCanonical" to jsonString(captured.serverCanonical),"expectedServerRevision" to jsonString("1"),
            "choice" to jsonString("Server"),"archiveId" to jsonString(captured.archiveId))))
        val old=saved.copy(ledger=saved.ledger.copy(entries=saved.ledger.entries.dropLast(1)+saved.ledger.entries.last().copy(evidenceCanonical=proof)),syncProofs=emptyList())
        val bytes=encodeEnvelope(old);assertEquals(old,assertIs<LoadResult.Loaded>(decodeEnvelope(bytes)).state)
        assertEquals(bytes,encodeEnvelope(assertIs<LoadResult.Loaded>(decodeEnvelope(bytes)).state))
        assertEquals(proof,decodeSyncEnvelope(encodeSyncEnvelope(old)).ledger.entries.last().evidenceCanonical)
    }
    @Test fun delayed_ack_reports_newer_persisted_conflict()=runTest {
        val local=store();val remote=store();grant(local,"op1",-20);grant(remote,"remote",-70)
        val remoteWire=encodeSyncEnvelope(load(remote))
        val transport=object:AcquisitionSyncTransport {
            override suspend fun push(request:AcquisitionPush):AcquisitionPushResult {
                grant(local,"op2",-10);val state=load(local)
                assertEquals(CommitResult.Committed,local.compareAndSet("hero",state.storageRevision,state.copy(storageRevision=state.storageRevision+1,syncMetadata=SyncMetadata(2,remoteWire))))
                return AcquisitionPushResult.Acknowledged(request.operationId,request.payloadCanonical,1)
            }
            override suspend fun resolve(request:AcquisitionResolution):AcquisitionPushResult=error("unexpected")
        }
        assertIs<SyncEnvelopeResult.Conflict>(protocol(local,transport).syncPending("hero"))
        val saved=load(local);assertEquals(listOf("op2"),saved.outbox.map { it.operationId });assertEquals(Amount(70,0),balance(saved.ledger))
    }
}
