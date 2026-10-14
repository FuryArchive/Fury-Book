package com.furybook.dubl.acquisition

import com.furybook.content.*
import com.furybook.core.json.*
import com.furybook.dubl.model.*
import java.io.File
import kotlin.test.*

/** Read-only companion: no expected-price null is converted into an amount or executable authority. */
class AcquisitionPresentationSourceMatrixTest {
    private val root=generateSequence(File(System.getProperty("user.dir"))) { it.parentFile }.first { File(it,"rulesets/dubl-3.69/acquisition/expected_prices.json").isFile }
    private val folder=File(root,"rulesets/dubl-3.69/acquisition")
    private val scope=AcquisitionScope("source-matrix","oracle",0,0,0)
    private val caps=AcquisitionCapabilities(true,false,true)
    private data class Input(val state:StoredCharacter,val target:AcquisitionTarget,val catalog:AcquisitionCatalog)
    private fun initial(character:DublCharacter,instances:List<AcquisitionInstance>,phase:RequirementNode.CreationPhase.Phase,opening:Amount)=StoredCharacter(
        character,CharacterSheetExtras(),instances,Ledger(Opening("source-opening","independent-input",opening,phase,OpeningProvenance.UserConfirmed,"Source fixture inputs only"),emptyList()),emptyList(),emptyList(),SyncMetadata(0),0,0,phase)
    /** Only input dimensions are decoded; all six nullable expected amounts remain untouched. */
    private fun unresolvedInput(row:JsonValue.Obj,catalog:AcquisitionCatalog):Input {
        fun text(key:String)=(row.values.getValue(key) as JsonValue.Str).value
        fun number(key:String)=(row.values.getValue(key) as JsonValue.Num).raw.toInt()
        val ident=row.values.getValue("definitionRef")
        val ref=if(ident is JsonValue.Obj)DefinitionRef((ident.values.getValue("pack") as JsonValue.Str).value,(ident.values.getValue("id") as JsonValue.Str).value)
            else catalog.definitions.keys.single { it.entryId==(ident as JsonValue.Str).value }
        val definition=catalog.definitions.getValue(ref)
        val phase=if(text("creationPhase")=="CREATION")RequirementNode.CreationPhase.Phase.Creating else RequirementNode.CreationPhase.Phase.Finalized
        val raw=(row.values.getValue("parameters") as JsonValue.Obj).values
        val semantic=(raw["semanticClass"] as? JsonValue.Str)?.value
        val explicit=raw.filterKeys { it!="semanticClass" }.mapValues { (it.value as JsonValue.Str).value }
        val key=when(ref.entryId) { "knowledge_template","performance_template","profession_template","craft_template"->"specialization";"feat_cd965f927a426cc2"->"knowledgeId";"feat_7fc6fc6e63da9688"->"resistanceType";else->null }
        val parameters=if(explicit.isNotEmpty() || key==null)explicit else when(semantic) { "SAME_ID","RENAMED_SAME_ID"->mapOf(key to "spec:a");"OTHER_ID","TWO_DISTINCT"->mapOf(key to "spec:b");else->emptyMap() }
        val attrs=DublCharacter("oracle").attributes.toMutableMap();val instances=mutableListOf<AcquisitionInstance>()
        if(text("discountConditions")=="SELF_TAUGHT") { attrs[AttributeId.INTELLIGENCE]=AttributeValue(6);instances+=AcquisitionInstance("self-taught",DefinitionRef("dubl-3.69","feat_dd0ba74aedebce10"),1,emptyMap(),null,AcquisitionOrigin.New) }
        val attribute=AttributeId.entries.singleOrNull { "attribute:${it.name}"==ref.entryId }
        if(attribute!=null)attrs[attribute]=AttributeValue(number("fromRank"))
        if(text("instance") in setOf("SECOND_DISTINCT","REPEAT_SAME_FORBIDDEN") || semantic=="TWO_DISTINCT") {
            val prior=when { text("instance")=="REPEAT_SAME_FORBIDDEN"->parameters;key=="specialization"->mapOf(key to "spec:a");"resistanceType" in parameters->mapOf("resistanceType" to "fire");"knowledgeId" in parameters->mapOf("knowledgeId" to "anatomy");else->emptyMap() }
            if(prior.isNotEmpty())instances+=AcquisitionInstance("fixture-prior",ref,1,prior,null,AcquisitionOrigin.New)
        }
        if(number("fromRank")>0 && definition.kind!=AcquisitionKind.ATTRIBUTE)instances+=AcquisitionInstance("fixture-target",ref,number("fromRank"),parameters,null,AcquisitionOrigin.New)
        val character=DublCharacter("oracle",attributes=attrs,creationComplete=phase==RequirementNode.CreationPhase.Phase.Finalized)
        val opening=Amount((row.values.getValue("openingXP") as JsonValue.Num).raw.toLong(),(row.values.getValue("openingAP") as JsonValue.Num).raw.toLong())
        return Input(initial(character,instances,phase,opening),AcquisitionTarget(ref,if(attribute==null)"fixture-target" else null,number("toRank"),parameters,text("optionId").takeUnless { it=="DEFAULT" }),catalog)
    }
    @Test fun all_rows_preserve_exact_direct_bulk_intent_and_unverified_source_presentation() {
        val source=FcpTextSource { javaClass.classLoader.getResource(it)?.readText() }
        val catalog=loadAcquisitionCatalog(listOf("dubl-3.69","dubl-chi-3.69").map { FcpContentPack.load("fcp/$it",source) },true).catalog
        val overlay=JsonReader(File(folder,"author_expected_price_updates.json").readText()).read() as JsonValue.Obj
        val patches=(overlay.values.getValue("updates") as JsonValue.Obj).values
        val fixtures=AcquisitionPriceFixtures({error("Exact row only")},catalog)
        var rows=0;var unverified=0;var routes=0;var unresolvedNumeric=0;var knownNumericUnverified=0;var actualBlocked=0;var unsupportedDiscount=0;var missingWitness=0;var symbolicRank=0;var confirmedShown=0;var confirmedBlocked=0;var confirmedCompoundGap=0;var confirmedReceiptEligible=0
        val progress=System.getProperty("furybook.acquisition.sourceMatrix.progress")?.let { File(it).bufferedWriter() }
        try { File(folder,"expected_prices.json").forEachLine { line ->
            if(!line.startsWith("{\"caseId\":"))return@forEachLine
            val row=JsonReader(line.removeSuffix(",")).read() as JsonValue.Obj
            val id=(row.values.getValue("caseId") as JsonValue.Str).value
            val values=row.values.toMutableMap()
            patches[id]?.let { patch -> for(key in listOf("expectedCostXP","expectedCostAP","expectedRemainingXP","expectedRemainingAP","expectedUndoXP","expectedUndoAP"))values[key]=(patch as JsonValue.Obj).values.getValue(key);values["evidenceStatus"]=JsonValue.Str("CONFIRMED") }
            val unresolved=(values.getValue("evidenceStatus") as JsonValue.Str).value!="CONFIRMED"
            if(row.values.getValue("fromRank") !is JsonValue.Num || row.values.getValue("toRank") !is JsonValue.Num) {
                // UNKNOWN is a source marker, not rank0. There is no concrete runtime intent to
                // compare. Exercise the explicit source-status presentation boundary separately.
                assertTrue(unresolved,id);rows++;unverified++;symbolicRank++
                for(field in listOf("expectedCostXP","expectedCostAP","expectedRemainingXP","expectedRemainingAP","expectedUndoXP","expectedUndoAP"))assertEquals(JsonValue.Null,row.values.getValue(field),id)
                assertTrue((row.values.getValue("blockingQuestion") as JsonValue.Arr).values.isNotEmpty(),id)
                val phase=if((row.values.getValue("creationPhase") as JsonValue.Str).value=="CREATION")RequirementNode.CreationPhase.Phase.Creating else RequirementNode.CreationPhase.Phase.Finalized
                val state=initial(DublCharacter("oracle",creationComplete=phase==RequirementNode.CreationPhase.Phase.Finalized),emptyList(),phase,
                    Amount((row.values.getValue("openingXP") as JsonValue.Num).raw.toLong(),(row.values.getValue("openingAP") as JsonValue.Num).raw.toLong()))
                val absentRank=AcquisitionPlan(AcquisitionRequest("oracle",emptyList()),state.revision,catalog.catalogRevision,catalog.resolutionRevision,emptyList(),emptyList(),
                    listOf(AcquisitionIssue(RequirementStatus.SourceUnresolved,"Independent source has no concrete rank transition")),null,null,"source-only:$id",acquisitionContext(state,catalog),emptyList())
                val shown=AcquisitionPresentation.present(scope,AcquisitionQuoteKey(id,0),absentRank,state,catalog,caps)
                assertEquals(AcquisitionUiPhase.Blocked,shown.phase,id);assertFalse(shown.canConfirm,id)
                assertEquals("Цена не определена",assertIs<AcquisitionDisplayedPrice.Unknown>(shown.price).message,id)
                assertTrue(shown.blocks.any { it.status==RequirementStatus.SourceUnresolved },id)
                progress?.apply { write("$rows|$unverified|$routes|symbolic-rank|$id\n");flush() }
                return@forEachLine
            }
            val confirmedFixture=if(unresolved)null else fixtures.decode(jsonStringify(JsonValue.Obj(values)))
            val input=if(unresolved)unresolvedInput(row,catalog) else {
                val f=checkNotNull(confirmedFixture)
                val attrs=DublCharacter("oracle").attributes+f.context.character.attributes
                val attribute=AttributeId.entries.singleOrNull { "attribute:${it.name}"==f.change.definition.entryId }
                val character=f.context.character.copy(attributes=if(attribute==null)attrs else attrs+(attribute to AttributeValue(f.change.fromRank)),creationComplete=f.context.creationPhase==RequirementNode.CreationPhase.Phase.Finalized)
                Input(initial(character,f.context.instances,f.context.creationPhase,f.opening),AcquisitionTarget(f.change.definition,if(attribute==null)f.change.instanceId else null,f.change.toRank,f.change.parameters,f.change.optionId),f.context.catalog)
            }
            rows++;progress?.apply { write("$rows|$unverified|$routes|$id\n");flush() }
            val kind=input.catalog.definitions.getValue(input.target.definition).kind
            val request=AcquisitionRequest("oracle",listOf(input.target))
            val direct=AcquisitionCommandRouting.request(AcquisitionIntent.Target(kind,input.target),scope,input.state,input.catalog)
            val bulk=AcquisitionCommandRouting.request(AcquisitionIntent.BulkTargets(request),scope,input.state,input.catalog)
            assertEquals(direct::class,bulk::class,id)
            var reviewed:AcquisitionPlan?=null
            if(direct is AcquisitionRouteResult.QuoteRequest && bulk is AcquisitionRouteResult.QuoteRequest) {
                assertEquals(request.canonical,direct.request.canonical,id);assertEquals(direct.request.canonical,bulk.request.canonical,id)
                val a=plan(direct.request,input.state,input.catalog);val b=plan(bulk.request,input.state,input.catalog)
                assertEquals(a.previewToken,b.previewToken,id);assertEquals(a.cost,b.cost,id);assertEquals(a.remaining,b.remaining,id);assertEquals(a.executable,b.executable,id);routes++;reviewed=a
            } else assertEquals(direct,bulk,id)
            val actual=reviewed ?: plan(request,input.state,input.catalog)
            val current=AcquisitionPresentation.present(scope,AcquisitionQuoteKey(id,0),actual,input.state,input.catalog,caps)
            assertEquals(actual.cost,(current.price as? AcquisitionDisplayedPrice.Known)?.amount,id)
            assertEquals(actual.remaining,current.remaining,id);assertEquals(request.canonical,current.request?.canonical,id)
            assertEquals(actual.executable,current.canConfirm,id)
            val requirement=input.catalog.definitions.getValue(input.target.definition).requirements
            assertEquals(evaluate(requirement,actual.projectedContext).status,current.requirements.single().status,id)
            assertEquals(evaluate(requirement,acquisitionContext(input.state,input.catalog,request.externalConfirmations)).status,current.baseRequirements.single().status,id)
            assertTrue(current.rows.none { it.label==input.target.definition.entryId },id)
            confirmedFixture?.let { f ->
                val numerical=assertIs<PriceResult.Known>(quote(f.change,f.context),id)
                val expectedQuote=f.knownQuote ?: f.expected.cost
                assertEquals(expectedQuote,numerical.cost,id)
                assertEquals(expectedQuote,assertIs<AcquisitionDisplayedPrice.Known>(AcquisitionPresentation.price(numerical)).amount,id)
                assertEquals(f.expected.remaining,f.opening-f.expected.cost,id)
                assertEquals(f.expected.undoBalance,f.expected.remaining+f.expected.cost,id)
                confirmedShown++
                val outcome=if(!actual.executable) {
                    assertFalse(current.canConfirm,id);confirmedBlocked++;"admission-blocked"
                } else if(actual.cost!=f.expected.cost || actual.changes.any { it.definition!=f.change.definition }) {
                    confirmedCompoundGap++;"compound-fixture-gap"
                } else {
                    assertEquals(f.expected.cost,assertIs<AcquisitionDisplayedPrice.Known>(current.price).amount,id)
                    assertEquals(f.expected.remaining,current.remaining,id);confirmedReceiptEligible++;"receipt-eligible-reused-financial-evidence"
                }
                progress?.apply { write("$rows|$unverified|$routes|classified:$outcome|$id\n");flush() }
            }
            if(unresolved) {
                unverified++
                val questions=(row.values.getValue("blockingQuestion") as JsonValue.Arr).values
                assertTrue(questions.isNotEmpty(),id)
                val discount=(row.values.getValue("discountConditions") as JsonValue.Str).value=="UNRESOLVED_SOURCE_DISCOUNT"
                if(discount)unsupportedDiscount++
                if((row.values.getValue("ownedPrerequisites") as JsonValue.Arr).values.isNotEmpty())missingWitness++
                val change=AcquisitionChange(kind,input.target.definition,"fixture-target",(row.values.getValue("fromRank") as JsonValue.Num).raw.toInt(),input.target.targetRank,input.target.parameters,input.target.optionId)
                val numerical=quote(change,acquisitionContext(input.state,input.catalog))
                val numericDisplay=AcquisitionPresentation.price(numerical)
                when(numerical) { is PriceResult.Unknown->{unresolvedNumeric++;assertEquals("Цена не определена",assertIs<AcquisitionDisplayedPrice.Unknown>(numericDisplay).message,id)};is PriceResult.Known->{knownNumericUnverified++;assertEquals(numerical.cost,assertIs<AcquisitionDisplayedPrice.Known>(numericDisplay).amount,id)} }
                if(!actual.executable)actualBlocked++
                // Source-only dimensions have no production request field. This is an explicit
                // presentation-boundary fixture, not a claim that actual admission models them.
                val sourcePlan=AcquisitionPlan(actual.request,actual.baseRevision,actual.catalogRevision,actual.resolutionRevision,actual.changes,actual.questions,
                    actual.issues+AcquisitionIssue(RequirementStatus.SourceUnresolved,"Independent corpus has unanswered source questions",input.target.definition),actual.cost,actual.remaining,actual.previewToken,actual.projectedContext,actual.prices)
                val shown=AcquisitionPresentation.present(scope,AcquisitionQuoteKey(id,0),sourcePlan,input.state,input.catalog,caps)
                assertFalse(shown.canConfirm,id);assertTrue(shown.blocks.any { it.status==RequirementStatus.SourceUnresolved },id)
                assertEquals(actual.cost,(shown.price as? AcquisitionDisplayedPrice.Known)?.amount,id)
                assertTrue(shown.blocks.none { it.message.contains(input.target.definition.entryId) },id)
            }
        }
        } finally { progress?.close() }
        assertEquals(83818,rows);assertEquals(37745,unverified);assertEquals(unverified,unresolvedNumeric+knownNumericUnverified+symbolicRank);assertEquals(2442,symbolicRank)
        // Source-exact prerequisite grammar moves 16 rows from blocked to compound fixtures.
        // Full native receipt/Undo matrix independently observes 27590 blocked / 2024 compound;
        // the 16459 receipt-eligible cases and immutable money oracle remain unchanged.
        assertEquals(46073,confirmedShown);assertEquals(27590,confirmedBlocked);assertEquals(2024,confirmedCompoundGap);assertEquals(16459,confirmedReceiptEligible)
        println("T17_PRESENTATION_REFRESH confirmedShown=$confirmedShown confirmedAdmissionBlocked=$confirmedBlocked confirmedCompoundFixtureGap=$confirmedCompoundGap confirmedReceiptEligibleReused=$confirmedReceiptEligible")
        println("T17_SOURCE_MATRIX rows=$rows exactDirectBulkPlans=$routes sourceUnverified=$unverified numericUnknown=$unresolvedNumeric numericKnownButUnverified=$knownNumericUnverified symbolicRankUnencodable=$symbolicRank actualAdmissionBlocked=$actualBlocked unsupportedDiscountDimension=$unsupportedDiscount unprojectedPrerequisiteRows=$missingWitness")
    }
}
