package com.furybook.dubl.acquisition

import com.furybook.core.json.*
import com.furybook.dubl.model.*
import kotlin.test.*

class AcquisitionSavedEvidenceTest {
    private val source=RuleSourceRef("historical-source","old-version","Recorded old price",resolutionId="resolved",resolutionRevision="old-rules")
    private val phase=RequirementNode.CreationPhase.Phase.Creating
    private val ref=DefinitionRef("old-pack","old-entry")
    private val change=AcquisitionChange(AcquisitionKind.DEVELOPMENT,ref,"saved-instance",0,1,emptyMap(),null)
    private val receipt=Receipt("paid-id","{}",SavedAcquisitionResult(Amount(20,0),Amount(80,2),listOf(change)),ReceiptStatus.Applied,4,5)
    private fun snapshot(rank:Int)=StoredCharacter(DublCharacter("hero",development=if(rank==0) emptyMap() else mapOf(ref.entryId to OwnedDevelopment(rank))),CharacterSheetExtras(),
        if(rank==0) emptyList() else listOf(AcquisitionInstance("saved-instance",ref,rank,emptyMap(),null,AcquisitionOrigin.New)),
        Ledger(Opening("opening","source",Amount(100,2),phase,OpeningProvenance.NewCharacter),emptyList()),emptyList(),emptyList(),SyncMetadata(0),0,0,phase)
    private fun projection(rank:Int)=JsonReader(acquisitionProjectionCanonical(snapshot(rank))).read()
    private fun edit(raw:String,edit:(JsonValue.Obj)->JsonValue.Obj):String=canonicalJson(jsonStringify(edit(JsonReader(raw).read() as JsonValue.Obj)))
    private fun evidence(cost:String=Amount(20,0).toJson(), schema:String="acquisition-evidence-v1", proof:JsonValue=JsonValue.Arr(emptyList())):String = canonicalJson(jsonStringify(jsonObject(
        "schema" to jsonString(schema),"externalConfirmations" to JsonValue.Arr(emptyList()),"phase" to jsonString(phase.name),
        "undoProjection" to jsonObject("schema" to jsonString("acquisition-projection-v1"),"before" to projection(0),"after" to projection(1)),
        "prices" to JsonValue.Arr(listOf(jsonObject("instanceId" to jsonString("saved-instance"),"sourceRef" to sourceJson(source),
            "quote" to jsonObject("cost" to JsonReader(cost).read(),"sources" to JsonValue.Arr(listOf(sourceJson(source))),"discountEvidence" to proof)))))))
    private fun entry(raw:String=evidence(),id:String="paid-id")=LedgerEntry(id,LedgerEntryType.Purchase,listOf(change),Amount(-20,0),RuleVersions("old-catalog","old-rules"),creationPhase=phase,evidenceCanonical=raw)
    @Test fun reads_historical_source_and_discount_without_any_catalog() {
        val proof=jsonObject("nodeId" to jsonString("old-discount"),"status" to jsonString("Satisfied"),"reason" to jsonString("Saved condition"),"children" to JsonValue.Arr(emptyList()),
            "witnesses" to JsonValue.Arr(listOf(jsonObject("type" to jsonString("OwnedInstance"),"packId" to jsonString("old-pack"),"entryId" to jsonString("old-entry"),"instanceId" to jsonString("old-instance"),"rank" to JsonValue.Num("2")))))
        val view=savedAcquisitionOperation(receipt,entry(evidence(proof=JsonValue.Arr(listOf(proof)))))
        assertEquals("paid-id",view.operationId);assertEquals(4,view.beforeRevision);assertEquals("old-catalog",view.ruleVersions?.catalogRevision)
        val price=assertIs<AcquisitionSavedEvidence.Recorded>(view.evidence).prices.single()
        assertEquals(source,price.source);assertEquals(Amount(20,0),price.cost)
        assertEquals("Saved condition",price.discountProofs.single().reason)
        assertEquals(RequirementWitness.OwnedInstance("old-pack","old-entry","old-instance",2),price.discountProofs.single().witnesses.single())
    }
    @Test fun wrong_operation_never_binds_another_history_entry() {
        val view=savedAcquisitionOperation(receipt,entry(id="someone-else"))
        assertNull(view.type);assertNull(view.ruleVersions);assertIs<AcquisitionSavedEvidence.Unavailable>(view.evidence)
    }
    @Test fun missing_and_unknown_evidence_are_not_zero_or_success() {
        assertIs<AcquisitionSavedEvidence.Unavailable>(savedAcquisitionOperation(receipt,null).evidence)
        assertIs<AcquisitionSavedEvidence.Unsupported>(savedAcquisitionOperation(receipt,entry(evidence(schema="future-v2"))).evidence)
        assertIs<AcquisitionSavedEvidence.Unavailable>(savedAcquisitionOperation(receipt,entry("{}")).evidence)
    }
    @Test fun malformed_truncated_overflow_fractional_and_unknown_witness_are_explicit() {
        for(raw in listOf("{",evidence(cost="{\"xp\":\"9223372036854775808\",\"ap\":\"0\"}"),evidence(cost="{\"xp\":0.5,\"ap\":0}")))
            assertIs<AcquisitionSavedEvidence.Malformed>(savedAcquisitionOperation(receipt,entry(raw)).evidence)
        val future=jsonObject("nodeId" to jsonString("node"),"status" to jsonString("Satisfied"),"reason" to JsonValue.Null,"children" to JsonValue.Arr(emptyList()),"witnesses" to JsonValue.Arr(listOf(jsonObject("type" to jsonString("Future")))))
        assertIs<AcquisitionSavedEvidence.Unsupported>(savedAcquisitionOperation(receipt,entry(evidence(proof=JsonValue.Arr(listOf(future))))).evidence)
    }
    @Test fun inconsistent_paid_amount_transition_instance_count_and_projection_never_record() {
        val raw=evidence()
        val bad=listOf(entry(evidence(cost=Amount(30,0).toJson())),entry(raw.replace("saved-instance","other-instance")),
            entry().copy(changes=emptyList()),entry().copy(changes=listOf(change.copy(toRank=2))),entry().copy(amountDelta=Amount(-21,0)),
            entry(edit(raw) { root -> JsonValue.Obj(root.values+("prices" to JsonValue.Arr(emptyList()))) }),
            entry(edit(raw) { root -> JsonValue.Obj(root.values+("undoProjection" to jsonObject("schema" to jsonString("acquisition-projection-v1"),"before" to jsonObject(),"after" to projection(1)))) }))
        bad.forEach { assertIs<AcquisitionSavedEvidence.Malformed>(savedAcquisitionOperation(receipt,it).evidence) }
    }
    @Test fun repeated_ordered_transitions_and_legitimate_noop_are_retained() {
        val changes=listOf(change,change.copy(fromRank=1,toRank=2))
        val raw=edit(evidence()) { root -> JsonValue.Obj(root.values+("prices" to JsonValue.Arr((root.values.getValue("prices") as JsonValue.Arr).values.let { it+it }))+
            ("undoProjection" to jsonObject("schema" to jsonString("acquisition-projection-v1"),"before" to projection(0),"after" to projection(2)))) }
        val paid=receipt.copy(result=SavedAcquisitionResult(Amount(40,0),Amount(60,2),changes))
        assertEquals(2,assertIs<AcquisitionSavedEvidence.Recorded>(savedAcquisitionOperation(paid,entry(raw).copy(changes=changes,amountDelta=Amount(-40,0))).evidence).prices.size)
        val inert=edit(evidence()) { root -> JsonValue.Obj(root.values+("prices" to JsonValue.Arr(emptyList()))+
            ("undoProjection" to jsonObject("schema" to jsonString("acquisition-projection-v1"),"before" to projection(0),"after" to projection(0)))) }
        val noop=receipt.copy(result=SavedAcquisitionResult(Amount.ZERO,Amount(100,2),emptyList()))
        assertTrue(assertIs<AcquisitionSavedEvidence.Recorded>(savedAcquisitionOperation(noop,entry(inert).copy(type=LedgerEntryType.NonFinancial,changes=emptyList(),amountDelta=Amount.ZERO)).evidence).prices.isEmpty())
        assertIs<AcquisitionSavedEvidence.Malformed>(savedAcquisitionOperation(noop,entry(inert).copy(changes=emptyList(),amountDelta=Amount.ZERO)).evidence)
    }
    @Test fun exact_signed_limits_remain_exact_and_checked_sum_overflow_fails() {
        for(xp in listOf(Long.MAX_VALUE,-Long.MAX_VALUE)) {
            val cost=Amount(xp,0);val paid=receipt.copy(result=receipt.result.copy(cost=cost))
            assertEquals(cost,assertIs<AcquisitionSavedEvidence.Recorded>(savedAcquisitionOperation(paid,entry(evidence(cost=cost.toJson())).copy(amountDelta=Amount(-xp,0))).evidence).prices.single().cost)
        }
        val raw=edit(evidence(cost=Amount(Long.MAX_VALUE,0).toJson())) { root -> JsonValue.Obj(root.values+("prices" to JsonValue.Arr((root.values.getValue("prices") as JsonValue.Arr).values.let { it+it }))) }
        val changes=listOf(change,change.copy(fromRank=1,toRank=2))
        assertIs<AcquisitionSavedEvidence.Malformed>(savedAcquisitionOperation(receipt.copy(result=receipt.result.copy(changes=changes)),entry(raw).copy(changes=changes)).evidence)
    }

}
