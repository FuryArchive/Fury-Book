package com.furybook.dubl.acquisition

import com.furybook.dubl.application.*
import com.furybook.dubl.data.*
import com.furybook.dubl.model.*
import kotlin.coroutines.*
import kotlin.test.*

class AcquisitionReviewDetailsTest {
    private fun run(block:suspend()->Unit) {
        var result:Result<Unit>?=null
        block.startCoroutine(object:Continuation<Unit> { override val context=EmptyCoroutineContext;override fun resumeWith(value:Result<Unit>) { result=value } })
        checkNotNull(result).getOrThrow()
    }
    private val source=RuleSourceRef("fixture","1","Independent display fixture")
    private val ref=DefinitionRef("dubl-3.69","access_17f99cc35c559d82")
    private val none=RequirementNode.AllOf("none",source,emptyList(),NoRequirementsEvidence(source,"None"))
    private fun definition(ref:DefinitionRef,name:String,kind:AcquisitionKind=AcquisitionKind.DEVELOPMENT)=AcquisitionDefinition(ref,kind,1,RepeatPolicy.Single,none,PriceRule.Fixed(20,0,source),SourceStatus.Confirmed,name,source)
    private fun state()=StoredCharacter(DublCharacter("hero"),CharacterSheetExtras(),emptyList(),Ledger(Opening("opening","source",Amount(100,2),RequirementNode.CreationPhase.Phase.Creating,OpeningProvenance.NewCharacter),emptyList()),emptyList(),emptyList(),SyncMetadata(0),0,0,RequirementNode.CreationPhase.Phase.Creating)
    private val scope=AcquisitionScope("store","hero",0,0,0)
    private fun control(store:AcquisitionStore,catalog:AcquisitionCatalog,ids:()->String={error("no ID during reading")},enabled:Boolean=true):AcquisitionController {
        val app=DublApplication(object:CharacterStore { override fun load()=AppSnapshot(listOf(state().character),"hero");override fun save(snapshot:AppSnapshot)=error("legacy write") },
            object:CharacterExtrasStore { override fun load(characterId:String)=CharacterSheetExtras();override fun save(characterId:String,extras:CharacterSheetExtras)=error("legacy extras") },ids,
            acquisitionService=AcquisitionService(store,{catalog},ids))
        return AcquisitionController(ApplicationAcquisitionCommandPort(app,store,{catalog},{AcquisitionCapabilities(enabled,false,true)},{it==scope}),{scope},ids)
    }
    @Test fun ability_payment_choices_are_typed_labeled_and_selected_without_defaulting()=run {
        val catalog=AcquisitionCatalog(mapOf(ref to definition(ref,"Боевые крики").copy(abilityOptionIds=setOf("morale","chi"),priceRule=PriceRule.ByOption(mapOf("morale" to PriceRule.Fixed(20,0,source),"chi" to PriceRule.Fixed(0,1,source)),source))),"catalog","rules")
        val store=InMemoryAcquisitionStore(mapOf("hero" to encodeEnvelope(state())));var ids=0
        val control=control(store,catalog,{"op-${++ids}"})
        val request=AcquisitionRequest("hero",listOf(AcquisitionTarget(ref,"new-instance",1)))
        val view=control.preview(request)
        val details=control.readReviewDetails(view.key)
        assertEquals(view.key,details.key);assertEquals(request.canonical,details.request.canonical);assertEquals(0L,details.baseRevision)
        val selection=details.abilityChoices.single();assertNull(selection.selectedId)
        assertEquals(mapOf("chi" to "Ци","morale" to "Мораль"),selection.options.associate { it.id to it.label })
        assertEquals(Amount(0,1),assertIs<AcquisitionDisplayedPrice.Known>(selection.options.single { it.id=="chi" }.price).amount)
        assertEquals(Amount(20,0),assertIs<AcquisitionDisplayedPrice.Known>(selection.options.single { it.id=="morale" }.price).amount)
        assertEquals(0,ids);assertTrue(assertIs<LoadResult.Loaded>(store.load("hero")).state.receipts.isEmpty())
        val selected=control.selectAbilityOption(view.key,0,"chi")
        assertEquals("new-instance",selected.request?.targets?.single()?.instanceId)
        assertEquals("chi",selected.request?.targets?.single()?.optionId)
        assertEquals(AcquisitionUiPhase.Committed,control.confirm(selected.key).phase)
        assertEquals(1,ids);assertEquals(Amount(100,1),balance(assertIs<LoadResult.Loaded>(store.load("hero")).state.ledger))
    }
    @Test fun readonly_card_details_share_exact_key_scope_request_and_revision_without_session_replacement()=run {
        val catalog=AcquisitionCatalog(mapOf(ref to definition(ref,"Боевые крики").copy(abilityOptionIds=setOf("morale","chi"),priceRule=PriceRule.ByOption(mapOf("morale" to PriceRule.Fixed(20,0,source),"chi" to PriceRule.Fixed(0,1,source)),source))),"catalog","rules")
        val store=InMemoryAcquisitionStore(mapOf("hero" to encodeEnvelope(state())));val control=control(store,catalog,enabled=false)
        val request=AcquisitionRequest("hero",listOf(AcquisitionTarget(ref,"new-instance",1,optionId="morale")))
        val active=control.preview(request)
        val card=control.quoteCardReview(request);val details=assertNotNull(card.details)
        assertEquals(card.model.key,details.key);assertEquals(card.model.scope,details.scope);assertEquals(card.model.request?.canonical,details.request.canonical)
        assertEquals("catalog",details.catalogRevision);assertEquals("rules",details.resolutionRevision);assertEquals(0L,details.baseRevision)
        assertFalse(card.model.canConfirm);assertEquals(2,details.abilityChoices.single().options.size)
        assertEquals(active.key,control.model?.key);assertNull(control.retainedOperationId)
        assertIs<MutationResult.Applied>(CharacterMutationCoordinator(store,{catalog},{"unused"}).mutate(CanonicalMutation("award","hero",0,MutationPayload.Grant(Amount(1,0)),"Award")))
        assertFailsWith<IllegalStateException> { control.readReviewDetails(active.key) }
    }
    @Test fun learned_spell_distinct_cry_and_instance_witnesses_keep_typed_identity_and_readable_labels()=run {
        val spell=DefinitionRef("test","secret-spell");val one=DefinitionRef("test","secret-cry-one");val two=DefinitionRef("test","secret-cry-two")
        val distinct=RequirementNode.DistinctCount("count",source,RequirementNode.AnyOf("cry",source,listOf(
            RequirementNode.OwnedRank("one",source,one.packId,one.entryId,ComparisonOperator.GE,1),RequirementNode.OwnedRank("two",source,two.packId,two.entryId,ComparisonOperator.GE,1))),DistinctScope.Definition,ComparisonOperator.GE,2)
        val requirement=RequirementNode.AllOf("all",source,listOf(RequirementNode.LearnedSpell("learned",source,spell.packId,spell.entryId),distinct))
        val catalog=AcquisitionCatalog(listOf(definition(ref,"Цель").copy(requirements=requirement),definition(spell,"Изученное заклинание",AcquisitionKind.SPELL),definition(one,"Первый крик"),definition(two,"Второй крик")).associateBy { it.ref },"catalog","rules")
        val base=state().copy(character=state().character.copy(magic=CharacterMagic(spells=listOf(KnownSpell("secret-slot",catalogId=spell.entryId)))),instances=listOf(
            AcquisitionInstance("secret-instance-one",one,1,emptyMap(),null,AcquisitionOrigin.New),AcquisitionInstance("secret-instance-two",two,1,emptyMap(),null,AcquisitionOrigin.New)))
        val store=InMemoryAcquisitionStore(mapOf("hero" to encodeEnvelope(base)));val control=control(store,catalog)
        val view=control.preview(AcquisitionRequest("hero",listOf(AcquisitionTarget(ref,"target",1))))
        val root=control.readReviewDetails(view.key).requirements.single().proof
        assertEquals(RequirementStatus.Satisfied,root.status)
        assertEquals(1,root.witnesses.count { it.witness is RequirementWitness.LearnedSpell })
        assertEquals(2,root.witnesses.count { it.witness is RequirementWitness.OwnedInstance })
        assertTrue(root.witnesses.all { !it.label.contains("secret-") })
        assertTrue(root.witnesses.any { it.label.contains("Изученное заклинание") })
        assertEquals(2,root.children.single { it.nodeId=="count" }.witnesses.size)
        val card=control.quoteCardReview(checkNotNull(view.request))
        assertEquals(root,assertNotNull(card.details).requirements.single().proof)
    }
}
