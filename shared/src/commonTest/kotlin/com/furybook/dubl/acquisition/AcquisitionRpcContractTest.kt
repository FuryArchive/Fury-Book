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
        assertEquals(jsonObject("payloadCanonical" to jsonString(payload)),root.values["p_envelope"])
    }

    @Test fun raw_wire_wrapper_preserves_lexemes_unicode_and_lone_surrogates() = runBlocking {
        val state=StoredCharacter(DublCharacter("lexical",name="Lone \uD800; pair 😀",
            gear=CharacterGear(loadAutomatic=false,loadManual=-0.0,items=listOf(GearItem("gear",name="tiny",load=1.0E-10)))),
            CharacterSheetExtras(),emptyList(),Ledger(Opening("new","funding",Amount(100,0),RequirementNode.CreationPhase.Phase.Creating,OpeningProvenance.NewCharacter),emptyList()),
            emptyList(),emptyList(),SyncMetadata(0),0,0,RequirementNode.CreationPhase.Phase.Creating)
        // Preserve the genuine historical token expectation independently of fresh rendering.
        val historicalRaw=encodeSyncEnvelope(state).replace(Regex("\"load\":-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?"),"\"load\":1.0E-10")
        val historical=decodeSyncEnvelope(historicalRaw)
        val store=InMemoryAcquisitionStore(mapOf("lexical" to encodeEnvelope(historical)))
        val mutations=CharacterMutationCoordinator(store,{AcquisitionCatalog(emptyMap(),"fixture","fixture")},{"id"})
        assertIs<MutationResult.Applied>(mutations.mutate(CanonicalMutation("op","lexical",0,MutationPayload.Grant(Amount.ZERO),"Fixture")))
        val payload=assertIs<LoadResult.Loaded>(store.load("lexical")).state.outbox.single().payloadCanonical
        assertTrue(payload.contains("-0.0")); assertTrue(payload.contains("1.0E-10")); assertTrue(payload.contains("\\uD800"))
        val root=JsonReader(AcquisitionSyncWire.encodePush(AcquisitionPush("lexical","op",0,payload)),strict=true).read() as JsonValue.Obj
        val wrapper=root.values.getValue("p_envelope") as JsonValue.Obj
        assertEquals(setOf("payloadCanonical"),wrapper.values.keys)
        assertEquals(jsonString(payload),wrapper.values.getValue("payloadCanonical"))
    }
}
