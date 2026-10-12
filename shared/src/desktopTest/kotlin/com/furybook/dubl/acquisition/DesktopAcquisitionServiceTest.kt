package com.furybook.dubl.acquisition

import com.furybook.content.FcpContentPack
import com.furybook.content.FcpTextSource
import com.furybook.dubl.model.*
import kotlinx.coroutines.runBlocking
import kotlin.test.*

/** Actual manifest catalog + real service/store; literal source anchors, no quote-derived oracle. */
class DesktopAcquisitionServiceTest {
    private val catalog by lazy {
        loadAcquisitionCatalog(listOf("dubl-3.69","dubl-chi-3.69").map { id ->
            FcpContentPack.load("fcp/$id", FcpTextSource { javaClass.classLoader.getResource(it)?.readText() }) },false).catalog
    }
    private fun ref(id:String)=DefinitionRef("dubl-3.69",id)
    private fun owned(id:String,rank:Int=1,option:String?=null)=AcquisitionInstance("owned/$id",ref(id),rank,emptyMap(),option,AcquisitionOrigin.New)
    private fun state(instances:List<AcquisitionInstance> = emptyList(),creating:Boolean=true):StoredCharacter {
        val phase=if(creating) RequirementNode.CreationPhase.Phase.Creating else RequirementNode.CreationPhase.Phase.Finalized
        return StoredCharacter(DublCharacter("real",creationComplete=!creating,attributes=AttributeId.entries.associateWith { AttributeValue(6) }),
            CharacterSheetExtras(),instances,Ledger(Opening("new","literal-source-anchors",Amount(100,10),phase,OpeningProvenance.NewCharacter),emptyList()),
            emptyList(),emptyList(),SyncMetadata(0),0,0,phase)
    }
    private suspend fun execute(original:StoredCharacter,id:String,option:String?=null):Pair<ApplyResult,StoredCharacter> {
        val store=InMemoryAcquisitionStore(mapOf("real" to encodeEnvelope(original)))
        val service=AcquisitionService(store,{ catalog },{ "generated" })
        val request=AcquisitionRequest("real",listOf(AcquisitionTarget(ref(id),"new/$id",1,optionId=option)))
        val preview=service.preview(request)
        val result=service.apply(ConfirmAcquisition("purchase","real",request,preview.baseRevision,preview.previewToken))
        return result to assertIs<LoadResult.Loaded>(store.load("real")).state
    }

    @Test fun actual_combat_and_tails_never_publish_or_spend() = runBlocking {
        for(id in listOf("martial_technique_880f59e730436e3f","martial_technique_9bfa154b252a4237","martial_technique_e1110cd9a24338fd")) {
            val original=state(); val (result,saved)=execute(original,id)
            assertIs<ApplyResult.Blocked>(result,id); assertEquals(original,saved,id)
        }
    }

    @Test fun actual_two_and_three_owned_cries_apply_only_target_for_30xp() = runBlocking {
        // Commander Voice CHA4/CON4; Unbreakable Spirit CON4/WIL4;
        // Thunder Warning intimidation5; Rally eloquence4/leadership4.
        val base=listOf(owned("access_17f99cc35c559d82",option="morale"),owned("feat_a700b29906107b2b"),
            owned("feat_9813ba6aff7624ad"),owned("feat_ff626e2585de2dc7"),owned("intimidation",5),
            owned("feat_e000383baa5b3afa"),owned("eloquence",4),owned("leadership",4))
        for((target,count) in listOf("feat_8b6f3261d7c5c8ea" to 2,"feat_127f2d478d76e66d" to 3)) {
            val initial=if(count==2) base.filterNot { it.definition.entryId=="feat_e000383baa5b3afa" } else base
            val (result,saved)=execute(state(initial,creating=false),target)
            val receipt=assertIs<ApplyResult.Applied>(result).receipt
            assertEquals(Amount(30,0),receipt.result.cost); assertEquals(Amount(70,10),balance(saved.ledger))
            assertEquals(listOf(ref(target)),receipt.result.changes.map { it.definition })
            assertEquals(initial.size+1,saved.instances.size); assertEquals(1,saved.receipts.size)
        }
    }

    @Test fun actual_access_payment_options_persist_stable_identity_and_phase() = runBlocking {
        for((option,ap) in listOf("morale" to 1L,"chi" to 2L)) {
            val initial=state(listOf(owned("feat_a700b29906107b2b")))
            val (result,saved)=execute(initial,"access_17f99cc35c559d82",option)
            assertEquals(Amount(0,ap),assertIs<ApplyResult.Applied>(result).receipt.result.cost)
            assertEquals(Amount(100,10-ap),balance(saved.ledger))
            assertEquals(option,saved.instances.single { it.definition.entryId=="access_17f99cc35c559d82" }.abilityOptionId)
            assertFalse(saved.character.development.containsKey("access_17f99cc35c559d82"),"No invented numeric option")
            val finalized=state(initial.instances,creating=false)
            val (blocked,unchanged)=execute(finalized,"access_17f99cc35c559d82",option)
            assertIs<ApplyResult.Blocked>(blocked); assertEquals(finalized,unchanged)
        }
    }
}
