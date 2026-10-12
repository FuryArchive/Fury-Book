package com.furybook.dubl.acquisition

import com.furybook.dubl.model.*
import com.furybook.dubl.data.DesktopAcquisitionStore
import com.furybook.dubl.data.DesktopAcquisitionWriteStage
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.test.*

/** Real durable reopen; remote receipt fixture never executes character commands. */
class DesktopAcquisitionSyncRetryTest {
    private fun runTest(block:suspend ()->Unit)=runBlocking { block() }
    @Test fun frozen_03d6bab_durable_outbox_and_inline_adoption_bytes_remain_readable() {
        for(name in listOf("old-pending.json","old-adoption-envelope.json","old-adoption-followup-pending.json")) {
            val raw=requireNotNull(javaClass.getResourceAsStream("/acquisition-sync-03d6bab/$name")).bufferedReader().use { it.readText() }
            val state=assertIs<LoadResult.Loaded>(decodeEnvelope(raw)).state
            assertEquals(raw,encodeEnvelope(state));assertTrue(state.syncProofs.isEmpty())
            state.outbox.forEach { decodeSyncEnvelope(it.payloadCanonical) }
            if(name!="old-adoption-envelope.json")assertTrue(state.outbox.isNotEmpty())
        }
        val wire=requireNotNull(javaClass.getResourceAsStream("/acquisition-sync-03d6bab/old-adoption-wire.json")).bufferedReader().use { it.readText() }
        assertEquals(wire,encodeSyncEnvelope(decodeSyncEnvelope(wire)))
        assertTrue(decodeSyncEnvelope(wire).ledger.entries.last().evidenceCanonical.contains("sync-adoption-v1"))
    }
    @Test fun accepted_resolution_timeout_and_local_replace_failure_reopen_identical_dedicated_request()=runTest {
        for(mode in listOf("reply","storage","pending-write")) {
            val directory=Files.createTempDirectory("fury-sync-retry")
            val normal=DesktopAcquisitionStore(directory)
            val initial=StoredCharacter(DublCharacter("hero"),CharacterSheetExtras(),emptyList(),
                Ledger(Opening("new","funding",Amount(100,0),RequirementNode.CreationPhase.Phase.Creating,OpeningProvenance.NewCharacter),emptyList()),
                emptyList(),emptyList(),SyncMetadata(0),0,0,RequirementNode.CreationPhase.Phase.Creating)
            assertEquals(CommitResult.Committed,normal.compareAndSet("hero",-1,initial))
            val remote=InMemoryAcquisitionStore(mapOf("hero" to encodeEnvelope(initial)))
            val catalog=AcquisitionCatalog(emptyMap(),"fixture","fixture")
            for((target,id,delta) in listOf(Triple(normal as AcquisitionStore,"local",-80L),Triple(remote as AcquisitionStore,"remote",-70L)))
                assertIs<MutationResult.Applied>(CharacterMutationCoordinator(target,{catalog},{"unused"}).mutate(CanonicalMutation(id,"hero",0,MutationPayload.Grant(Amount(delta,0)),"Explicit fixture")))
            var canonical=encodeSyncEnvelope(assertIs<LoadResult.Loaded>(remote.load("hero")).state)
            var revision=1L;var lose=mode=="reply";var fail=false
            val requests=mutableListOf<AcquisitionResolution>();var ack:AcquisitionPushResult.Acknowledged?=null
            val transport=object:AcquisitionSyncTransport {
                override suspend fun push(request:AcquisitionPush):AcquisitionPushResult=AcquisitionPushResult.Conflict(revision,canonical)
                override suspend fun resolve(request:AcquisitionResolution):AcquisitionPushResult {
                    val durable=assertIs<LoadResult.Loaded>(DesktopAcquisitionStore(directory).load("hero")).state
                    assertEquals(request,assertNotNull(durable.syncMetadata.pendingResolution).request)
                    requests+=request
                    ack?.let { assertEquals(it.payloadCanonical,request.selectedCanonical);return it }
                    assertEquals(1L,revision);canonical=request.selectedCanonical;revision++
                    val accepted=AcquisitionPushResult.Acknowledged(request.operationId,canonical,revision);ack=accepted
                    if(lose){lose=false;error("lost accepted reply")}
                    if(mode=="storage")fail=true
                    return accepted
                }
            }
            val records=linkedMapOf<String,ArchivedAcquisitionConflict>()
            val archive=object:AcquisitionConflictArchive {
                override suspend fun preserve(conflict:ArchivedAcquisitionConflict):CommitResult {
                    records[conflict.archiveId]?.let { assertEquals(it,conflict) };records[conflict.archiveId]=conflict;return CommitResult.Committed
                }
            }
            val caps=AcquisitionCapabilities(true,true,true)
            assertIs<SyncEnvelopeResult.Conflict>(AcquisitionSyncProtocol(normal,transport,archive,caps,{"unused"}).syncPending("hero"))
            val faulty=DesktopAcquisitionStore(directory) { stage ->
                if(mode=="pending-write" && stage==DesktopAcquisitionWriteStage.AfterReplace && !fail) {fail=true;error("lost local pending commit reply")}
                if(mode=="storage" && fail && stage==DesktopAcquisitionWriteStage.BeforeReplace) {fail=false;error("post-acceptance replace failure")}
            }
            val first=AcquisitionSyncProtocol(faulty,transport,archive,caps,{"frozen-resolution"}).resolveConflict("hero",LocalOrServer.Server,1)
            if(mode=="reply")assertIs<SyncEnvelopeResult.Retryable>(first)else assertIs<SyncEnvelopeResult.StorageFailure>(first)
            val reopen=DesktopAcquisitionStore(directory)
            val frozen=assertNotNull(assertIs<LoadResult.Loaded>(reopen.load("hero")).state.syncMetadata.pendingResolution).request
            assertEquals("frozen-resolution",frozen.operationId)
            assertIs<SyncEnvelopeResult.Synced>(AcquisitionSyncProtocol(reopen,transport,archive,caps,{"wrong-new-id"}).resolveConflict("hero",LocalOrServer.Server,1))
            assertTrue(requests.all { it==frozen });assertEquals(1,records.size);assertEquals(2L,revision)
            val saved=assertIs<LoadResult.Loaded>(DesktopAcquisitionStore(directory).load("hero")).state
            assertEquals(Amount(30,0),balance(saved.ledger));assertNull(saved.syncMetadata.pendingResolution);assertTrue(saved.outbox.isEmpty())
        }
    }
}
