package com.furybook.web

import com.furybook.content.*
import com.furybook.dubl.acquisition.*
import com.furybook.dubl.model.*
import kotlinx.coroutines.*
import kotlin.js.Promise
import kotlin.test.*

/** Actual bundled source rules and real IndexedDB, with no cloud-write permission. */
class CurrentBookPurchaseTest {
    @Test fun mana_spell_and_named_knowledge_keep_exact_prices_after_database_reopen() : Promise<Unit> = MainScope().promise {
        val resources=loadBundledFcpTexts()
        val catalog=loadAcquisitionCatalog(listOf(FcpContentPack.load("fcp/dubl-3.69",FcpTextSource { resources[it] })),false).catalog
        val phase=RequirementNode.CreationPhase.Phase.Creating
        val initial=StoredCharacter(DublCharacter("native"),CharacterSheetExtras(),emptyList(),
            Ledger(Opening("opening","current-book-confirmed-balance",Amount(1000,10),phase,OpeningProvenance.NewCharacter),emptyList()),
            emptyList(),emptyList(),SyncMetadata(0),0,0,phase)
        val owner=BrowserOwnerScope.fromVerifiedSession(WebAuthSession("native-book-test","native@example.test")) { "native-book-test" }
        val database="furybook-current-book-"+webUuid()
        var store=BrowserAcquisitionStore(owner,database,localWritesEnabled=true,legacySource={null})
        assertTrue(store.initialize())
        try {
            assertEquals(CommitResult.Committed,store.compareAndSet("native",-1,initial))
            val service=AcquisitionService(store,{ catalog },{ webUuid() })
            suspend fun purchase(request:AcquisitionRequest,id:String):Receipt {
                val preview=service.preview(request)
                assertTrue(preview.executable,preview.issues.toString())
                return assertIs<ApplyResult.Applied>(service.apply(ConfirmAcquisition(id,"native",request,preview.baseRevision,preview.previewToken))).receipt
            }
            val spell=DefinitionRef("dubl-3.69","spell_b2de2ad02456564c")
            val first=purchase(AcquisitionRequest("native",listOf(AcquisitionTarget(spell,targetRank=1))),"learn-with-reserve")
            assertEquals(Amount(110,0),first.result.cost)
            val afterMana=assertIs<LoadResult.Loaded>(store.load("native")).state
            assertEquals(1,afterMana.character.magic.power)
            assertEquals(1,afterMana.character.magic.manaRank)
            val knowledge=catalog.definitions.getValue(DefinitionRef("dubl-3.69","knowledge_template"))
            val selected=AcquisitionSkillSpecializations.newTarget(knowledge,"city-history","История города",targetRank=2)
            assertEquals(Amount(30,0),purchase(AcquisitionRequest("native",listOf(selected)),"learn-history").result.cost)
            val saved=assertIs<LoadResult.Loaded>(store.load("native")).state
            val bytes=store.rawForExport("native")
            store.close()
            store=BrowserAcquisitionStore(owner,database,localWritesEnabled=true,legacySource={null})
            assertTrue(store.initialize())
            assertEquals(bytes,store.rawForExport("native"))
            val reopened=assertIs<LoadResult.Loaded>(store.load("native")).state
            assertEquals(Amount(860,10),balance(reopened.ledger))
            assertEquals("Знание (История города)",reopened.character.skills.getValue("city-history").name)
            assertEquals(2,reopened.instances.single { it.instanceId=="city-history" }.rank)
            val entry=reopened.ledger.entries.single { it.operationId=="learn-with-reserve" }
            val receipt=AcquisitionPresentation.receipt(first,catalog,true,entry)
            assertEquals(listOf(Amount(100,0),Amount(10,0)),receipt.rows.map { assertIs<AcquisitionDisplayedPrice.Known>(it.price).amount })
            assertFalse(store.capabilities.cloudLedgerV2)
            assertIs<ApplyResult.Applied>(AcquisitionUndo(store).undo("native","undo-history",saved.revision))
            assertEquals(acquisitionProjectionCanonical(afterMana),acquisitionProjectionCanonical(assertIs<LoadResult.Loaded>(store.load("native")).state))
        } finally { store.close() }
    }
}
