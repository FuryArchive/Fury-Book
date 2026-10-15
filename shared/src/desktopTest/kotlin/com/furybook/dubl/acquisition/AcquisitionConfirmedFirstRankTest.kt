package com.furybook.dubl.acquisition

import com.furybook.content.FcpTextSource
import com.furybook.dubl.content.DublFcp
import com.furybook.dubl.model.*
import kotlinx.coroutines.runBlocking
import kotlin.test.*

/** A missing source maximum must neither erase a confirmed first price nor invent later prices. */
class AcquisitionConfirmedFirstRankTest {
    private val catalog by lazy { DublFcp.open(FcpTextSource { javaClass.classLoader.getResource(it)?.readText() }).loadAcquisition().catalog }
    private fun ref(id:String) = DefinitionRef("dubl-3.69",id)
    private val dodge=ref("feat_7eece523ffdc8431")
    private val bounce=ref("feat_e8bda6bbcde463dc")
    private val base=ref("feat_4c85aa747f319702")
    private val access=ref("access_45edee695a349bf7")
    private fun initial():StoredCharacter {
        val phase=RequirementNode.CreationPhase.Phase.Creating
        val character=DublCharacter("acrobat",attributes=DublCharacter("acrobat").attributes+
            (AttributeId.DEXTERITY to AttributeValue(4))+(AttributeId.STRENGTH to AttributeValue(1)),
            skills=mapOf("athletics" to CharacterSkill("athletics",definitionId="athletics",rank=4)))
        return StoredCharacter(character,CharacterSheetExtras(),listOf(AcquisitionInstance("athletics",ref("athletics"),4,
            emptyMap(),null,AcquisitionOrigin.New)),Ledger(Opening("opening","confirmed-existing-balance",Amount(1000,10),phase,
            OpeningProvenance.UserConfirmed,"Existing attributes and Athletics remain paid"),emptyList()),
            emptyList(),emptyList(),SyncMetadata(0),0,0,phase)
    }
    @Test fun actual_two_acrobatics_cards_share_one_access_and_one_predecessor_and_survive_reopen() = runBlocking {
        for(target in listOf(dodge,bounce)) {
            assertNull(catalog.definitions.getValue(target).maxRank)
            assertEquals(SourceStatus.NeedsResolution,catalog.definitions.getValue(target).sourceStatus)
        }
        val before=initial()
        val request=AcquisitionRequest("acrobat",listOf(AcquisitionTarget(dodge,targetRank=1),AcquisitionTarget(bounce,targetRank=1)))
        val store=InMemoryAcquisitionStore(mapOf("acrobat" to encodeEnvelope(before)))
        val service=AcquisitionService(store,{ catalog },{ "generated" })
        val preview=service.preview(request)
        assertTrue(preview.executable,preview.issues.toString())
        assertEquals(Amount(110,1),preview.cost)
        assertEquals(setOf(access,base,dodge,bounce),preview.changes.map { it.definition }.toSet())
        assertEquals(4,preview.changes.size)
        assertIs<ApplyResult.Applied>(service.apply(ConfirmAcquisition("both-cards","acrobat",request,preview.baseRevision,preview.previewToken)))
        val after=assertIs<LoadResult.Loaded>(store.load("acrobat")).state
        val reopened=assertIs<LoadResult.Loaded>(decodeEnvelope(encodeEnvelope(after))).state
        assertEquals(Amount(890,9),balance(reopened.ledger))
        assertEquals(4,reopened.character.skills.getValue("athletics").rank)
        assertEquals(Amount.ZERO,plan(request,reopened,catalog).cost)
        val later=plan(AcquisitionRequest("acrobat",listOf(AcquisitionTarget(dodge,targetRank=2))),reopened,catalog)
        assertFalse(later.executable)
        assertIs<ApplyResult.Applied>(AcquisitionUndo(store).undo("acrobat","undo-both",after.revision))
        assertEquals(acquisitionProjectionCanonical(before),acquisitionProjectionCanonical(assertIs<LoadResult.Loaded>(store.load("acrobat")).state))
    }
}
