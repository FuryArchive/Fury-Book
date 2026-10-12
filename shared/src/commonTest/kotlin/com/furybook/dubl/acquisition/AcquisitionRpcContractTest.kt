package com.furybook.dubl.acquisition

import com.furybook.core.cloud.FuryCloudTransport
import com.furybook.core.json.*
import com.furybook.dubl.model.*
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class AcquisitionRpcContractTest {
    @Test fun ordinary_push_uses_exact_single_v2_rpc_with_null_resolution() = runBlocking {
        val state=StoredCharacter(DublCharacter("hero"),CharacterSheetExtras(),emptyList(),
            Ledger(Opening("new","funding",Amount(100,0),RequirementNode.CreationPhase.Phase.Creating,OpeningProvenance.NewCharacter),emptyList()),
            emptyList(),emptyList(),SyncMetadata(0),0,0,RequirementNode.CreationPhase.Phase.Creating)
        val store=InMemoryAcquisitionStore(mapOf("hero" to encodeEnvelope(state)))
        val mutations=CharacterMutationCoordinator(store,{AcquisitionCatalog(emptyMap(),"fixture","fixture")},{"id"})
        assertIs<MutationResult.Applied>(mutations.mutate(CanonicalMutation("op1","hero",0,MutationPayload.Grant(Amount.ZERO),"Fixture")))
        val payload=assertIs<LoadResult.Loaded>(store.load("hero")).state.outbox.single().payloadCanonical
        var name="";var body=""
        val rpc=object:FuryCloudTransport { override suspend fun rpc(function:String,jsonBody:String):String {
            name=function;body=jsonBody
            return jsonStringify(jsonObject("status" to jsonString("ack"),"operationId" to jsonString("op1"),
                "payloadCanonical" to jsonString(payload),"serverRevision" to jsonString("1")))
        } }
        AcquisitionRpcTransport(rpc,AcquisitionCapabilities(true,true,true)).push(AcquisitionPush("hero","op1",0,payload))
        assertEquals("acquisition_sync_v2",name)
        val root=JsonReader(body,strict=true).read() as JsonValue.Obj
        assertEquals(setOf("p_character_id","p_expected_revision","p_operation_ids","p_envelope","p_writer_version","p_resolution"),root.values.keys)
        assertEquals(jsonString("0"),root.values["p_expected_revision"])
        assertEquals(jsonArray(listOf(jsonString("op1"))),root.values["p_operation_ids"])
        assertEquals(jsonNumber(2),root.values["p_writer_version"])
        assertEquals(JsonValue.Null,root.values["p_resolution"])
    }
}
