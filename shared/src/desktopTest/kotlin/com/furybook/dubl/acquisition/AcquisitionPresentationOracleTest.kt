package com.furybook.dubl.acquisition

import com.furybook.content.*
import com.furybook.core.json.*
import com.furybook.dubl.model.*
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.runBlocking
import kotlin.test.*

/** The immutable source corpus and bounded author overlay supply expectations, never runtime prices. */
class AcquisitionPresentationOracleTest {
    private val root=generateSequence(File(System.getProperty("user.dir"))) { it.parentFile }.first { File(it,"rulesets/dubl-3.69/acquisition/expected_prices.json").isFile }
    private val folder=File(root,"rulesets/dubl-3.69/acquisition")
    private fun hash(file:File):String { val digest=MessageDigest.getInstance("SHA-256");file.inputStream().use { input -> val buffer=ByteArray(65536);while(true) { val n=input.read(buffer);if(n<0)break;digest.update(buffer,0,n) } };return digest.digest().joinToString("") { "%02x".format(it) } }
    @Test fun complete_effective_price_and_presentation_matrix_with_actual_admission_and_saved_receipts()=runBlocking<Unit> {
        val source=FcpTextSource { javaClass.classLoader.getResource(it)?.readText() }
        val catalog=loadAcquisitionCatalog(listOf("dubl-3.69","dubl-chi-3.69").map { FcpContentPack.load("fcp/$it",source) },true).catalog
        val overlay=JsonReader(File(folder,"author_expected_price_updates.json").readText()).read() as JsonValue.Obj
        val digests=overlay.values.getValue("baseFileSha256") as JsonValue.Obj
        for((name,expected) in digests.values) assertEquals((expected as JsonValue.Str).value,hash(File(folder,name)),name)
        val patches=(overlay.values.getValue("updates") as JsonValue.Obj).values
        assertEquals(3922,patches.size)
        val fixtures=AcquisitionPriceFixtures({error("Use exact row")},catalog)
        val caps=AcquisitionCapabilities(true,false,true)
        var total=0;var confirmed=0;var sourceUnverified=0;var applied=0;var admissionBlocked=0;var compound=0;var patchesSeen=0;var noOpReceipts=0
        val blockedStatuses=linkedMapOf<String,Int>()
        val progress=System.getProperty("furybook.acquisition.matrix.progress")?.let { File(it).bufferedWriter() }
        fun checkpoint(stage:String,id:String) { progress?.apply { write("$total|$confirmed|$applied|$admissionBlocked|$compound|$stage|$id\n");flush() } }
        try {
        File(folder,"expected_prices.json").forEachLine { line ->
            if(!line.startsWith("{\"caseId\":")) return@forEachLine
            val original=JsonReader(line.removeSuffix(",")).read() as JsonValue.Obj
            val id=(original.values.getValue("caseId") as JsonValue.Str).value
            val values=original.values.toMutableMap()
            patches[id]?.let { value ->
                val patch=value as JsonValue.Obj
                for(key in listOf("expectedCostXP","expectedCostAP","expectedRemainingXP","expectedRemainingAP","expectedUndoXP","expectedUndoAP")) values[key]=patch.values.getValue(key)
                values["evidenceStatus"]=JsonValue.Str("CONFIRMED");patchesSeen++
            }
            total++;checkpoint("row",id)
            if((values.getValue("evidenceStatus") as JsonValue.Str).value!="CONFIRMED") { sourceUnverified++;return@forEachLine }
            confirmed++
            val f=fixtures.decode(jsonStringify(JsonValue.Obj(values)))
            val numerical=assertIs<PriceResult.Known>(quote(f.change,f.context),id)
            val expectedQuote=f.knownQuote ?: f.expected.cost
            assertEquals(expectedQuote,numerical.cost,id)
            assertEquals(expectedQuote,assertIs<AcquisitionDisplayedPrice.Known>(AcquisitionPresentation.price(numerical)).amount,id)
            assertEquals(f.expected.remaining,f.opening-f.expected.cost,id)
            assertEquals(f.expected.undoBalance,f.expected.remaining+f.expected.cost,id)
            val attrs=DublCharacter("oracle").attributes+f.context.character.attributes
            val targetAttribute=AttributeId.entries.singleOrNull { "attribute:${it.name}"==f.change.definition.entryId }
            val character=f.context.character.copy(attributes=if(targetAttribute==null)attrs else attrs+(targetAttribute to AttributeValue(f.change.fromRank)),creationComplete=f.context.creationPhase==RequirementNode.CreationPhase.Phase.Finalized)
            val initial=StoredCharacter(character,CharacterSheetExtras(),f.context.instances,
                Ledger(Opening("oracle-opening","independent-fixture",f.opening,f.context.creationPhase,OpeningProvenance.UserConfirmed,"Independent price/admission fixture"),emptyList()),
                emptyList(),emptyList(),SyncMetadata(0),0,0,f.context.creationPhase)
            val request=AcquisitionRequest("oracle",listOf(AcquisitionTarget(f.change.definition,if(targetAttribute!=null)null else f.change.instanceId,f.change.toRank,f.change.parameters,f.change.optionId)))
            checkpoint("plan",id)
            val preview=plan(request,initial,f.context.catalog)
            val ui=AcquisitionPresentation.present(AcquisitionScope("oracle","oracle",0,0,0),AcquisitionQuoteKey(id,0),preview,initial,f.context.catalog,caps)
            assertEquals(preview.cost,when(val price=ui.price) { is AcquisitionDisplayedPrice.Known->price.amount;is AcquisitionDisplayedPrice.Unknown->null },id)
            val direct=AcquisitionCommandRouting.request(AcquisitionIntent.Target(f.change.kind,request.targets.single()),ui.scope,initial,f.context.catalog)
            val bulk=AcquisitionCommandRouting.request(AcquisitionIntent.BulkTargets(request),ui.scope,initial,f.context.catalog)
            assertEquals(direct::class,bulk::class,id)
            if(!preview.executable) {
                assertFalse(ui.canConfirm,id);admissionBlocked++
                preview.issues.forEach { issue -> val key=issue.status.name;blockedStatuses[key]=(blockedStatuses[key] ?: 0)+1 }
            } else if(preview.cost!=f.expected.cost || preview.changes.any { it.definition!=f.change.definition }) {
                // The corpus gives a single-transition price, not independent expected costs for
                // additional prerequisite purchases. These remain explicitly separate fixture gaps.
                compound++
            } else {
                runBlocking {
                    val store=InMemoryAcquisitionStore(mapOf("oracle" to encodeEnvelope(initial)))
                    val service=AcquisitionService(store,{f.context.catalog},{"instance"})
                    val exact=service.preview(request)
                    assertEquals(preview.cost,exact.cost,id)
                    checkpoint("apply",id)
                    val result=assertIs<ApplyResult.Applied>(service.apply(ConfirmAcquisition("purchase","oracle",request,exact.baseRevision,exact.previewToken)),id)
                    assertEquals(f.expected.cost,result.receipt.result.cost,id)
                    assertEquals(f.expected.remaining,result.receipt.result.remaining,id)
                    assertEquals(assertIs<AcquisitionDisplayedPrice.Known>(ui.price).amount,result.receipt.result.cost,id)
                    val saved=assertIs<LoadResult.Loaded>(store.load("oracle")).state
                    assertEquals(f.expected.remaining,balance(saved.ledger),id)
                    checkpoint("undo",id)
                    val undo=AcquisitionUndo(store)
                    val eligible=undo.eligible("oracle")
                    if(exact.changes.isEmpty()) {
                        assertEquals(Amount.ZERO,result.receipt.result.cost,id)
                        assertIs<UndoEligibility.Unavailable>(eligible,id);noOpReceipts++
                    } else {
                        assertIs<UndoEligibility.Available>(eligible,id)
                        assertIs<ApplyResult.Applied>(undo.undo("oracle","undo",saved.revision),id)
                    }
                    assertEquals(f.expected.undoBalance,balance(assertIs<LoadResult.Loaded>(store.load("oracle")).state.ledger),id)
                    applied++
                }
            }
        }
        } finally { progress?.close() }
        assertEquals(83818,total);assertEquals(46073,confirmed);assertEquals(37745,sourceUnverified);assertEquals(3922,patchesSeen)
        assertEquals(confirmed,applied+admissionBlocked+compound)
        assertTrue(applied>0)
        println("T17_EFFECTIVE_MATRIX total=$total confirmed=$confirmed sourceUnverified=$sourceUnverified savedReceiptAndUndo=$applied admissionBlocked=$admissionBlocked compoundFixtureGap=$compound noOpReceipts=$noOpReceipts statuses=$blockedStatuses baseSHA=${hash(File(folder,"expected_prices.json"))} overlaySHA=${hash(File(folder,"author_expected_price_updates.json"))}")
    }
}
