package com.furybook.dubl.acquisition

import com.furybook.content.FcpTextSource
import com.furybook.dubl.content.DublFcp
import com.furybook.dubl.model.*
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class AcquisitionDrawbackAttributesTest {
    private val catalog by lazy { DublFcp.open(FcpTextSource { javaClass.classLoader.getResource(it)?.readText() }).loadAcquisition().catalog }
    private fun ref(id:String)=DefinitionRef("dubl-3.69",id)
    private val clumsy=ref("drawback_a0f942e069cc9556")
    private val acrobatics=ref("feat_4c85aa747f319702")
    private fun initial():StoredCharacter {
        val phase=RequirementNode.CreationPhase.Phase.Creating
        val character=DublCharacter("drawback-attribute",attributes=DublCharacter("drawback-attribute").attributes+
            (AttributeId.DEXTERITY to AttributeValue(4))+(AttributeId.STRENGTH to AttributeValue(1)),
            skills=mapOf("athletics" to CharacterSkill("athletics",definitionId="athletics",rank=4)))
        return StoredCharacter(character,CharacterSheetExtras(),listOf(AcquisitionInstance("athletics",ref("athletics"),4,emptyMap(),null,AcquisitionOrigin.New)),
            Ledger(Opening("opening","confirmed-existing-balance",Amount(1000,10),phase,OpeningProvenance.UserConfirmed,"Existing attributes and Athletics stay paid"),emptyList()),
            emptyList(),emptyList(),SyncMetadata(0),0,0,phase)
    }
    private suspend fun saved(store:AcquisitionStore)=assertIs<LoadResult.Loaded>(store.load("drawback-attribute")).state
    private suspend fun buy(store:AcquisitionStore,targets:List<AcquisitionTarget>):Receipt {
        val service=AcquisitionService(store,{catalog},{"generated"})
        val request=AcquisitionRequest("drawback-attribute",targets)
        val preview=service.preview(request)
        assertTrue(preview.executable,preview.issues.toString())
        return assertIs<ApplyResult.Applied>(service.apply(ConfirmAcquisition("buy","drawback-attribute",request,preview.baseRevision,preview.previewToken))).receipt
    }
    @Test fun actual_attribute_drawback_changes_effective_value_without_overwriting_paid_base()=runBlocking {
        val before=initial();val store=InMemoryAcquisitionStore(mapOf(before.character.id to encodeEnvelope(before)))
        val request=AcquisitionRequest(before.character.id,listOf(AcquisitionTarget(clumsy,targetRank=1)))
        val preview=plan(request,before,catalog)
        assertTrue(preview.executable,preview.issues.toString())
        assertEquals(3,preview.projectedContext.character.dexterity)
        assertEquals(Amount(-50,0),buy(store,request.targets).result.cost)
        val after=saved(store)
        assertEquals(4,after.character.attributes.getValue(AttributeId.DEXTERITY).base)
        assertEquals(3,after.character.dexterity)
        assertEquals(Amount(1050,10),balance(after.ledger))
        assertIs<ApplyResult.Applied>(AcquisitionUndo(store).undo(before.character.id,"undo",after.revision))
        assertEquals(acquisitionProjectionCanonical(before),acquisitionProjectionCanonical(saved(store)))
    }
    @Test fun actual_bulk_purchase_repairs_effective_dexterity_once_in_either_target_order()=runBlocking {
        val first=AcquisitionTarget(acrobatics,targetRank=1);val second=AcquisitionTarget(clumsy,targetRank=1)
        for(targets in listOf(listOf(first,second),listOf(second,first))) {
            val before=initial();val store=InMemoryAcquisitionStore(mapOf(before.character.id to encodeEnvelope(before)))
            val receipt=buy(store,targets)
            assertEquals(Amount(75,1),receipt.result.cost) // -50 credit, 4→5 base75, Acrobatics50, access1.
            assertEquals(1,receipt.result.changes.count { it.definition==ref("attribute:DEXTERITY") })
            val after=saved(store)
            assertEquals(5,after.character.attributes.getValue(AttributeId.DEXTERITY).base)
            assertEquals(4,after.character.dexterity)
            assertEquals(Amount(925,9),balance(after.ledger))
            assertEquals(Amount(30,0),plan(AcquisitionRequest(before.character.id,listOf(AcquisitionTarget(ref("feat_7eece523ffdc8431"),targetRank=1))),after,catalog).cost)
            assertIs<ApplyResult.Applied>(AcquisitionUndo(store).undo(before.character.id,"undo",after.revision))
            assertEquals(acquisitionProjectionCanonical(before),acquisitionProjectionCanonical(saved(store)))
        }
    }
}
