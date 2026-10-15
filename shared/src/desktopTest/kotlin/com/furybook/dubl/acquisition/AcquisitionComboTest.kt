package com.furybook.dubl.acquisition

import com.furybook.content.FcpContentPack
import com.furybook.content.FcpTextSource
import com.furybook.dubl.model.*
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class AcquisitionComboTest {
    private val catalog by lazy { loadAcquisitionCatalog(listOf(FcpContentPack.load("fcp/dubl-3.69",
        FcpTextSource { javaClass.classLoader.getResource(it)?.readText() })), false).catalog }

    @Test fun actual_combo_requires_a_source_eligible_owned_ordered_sequence() {
        val combo=catalog.definitions.getValue(DefinitionRef("dubl-3.69","martial_technique_c4972b9f356e9d3d"))
        assertEquals(3,combo.maxRank)
        assertEquals(listOf("comboSequence"),combo.parameterDefinitions.map { it.key })
        assertIs<RepeatPolicy.DistinctParameter>(combo.repeatPolicy)
        val parameter=combo.parameterDefinitions.single()
        val source=assertIs<ParameterSelectionSource.OrderedOwnedInstances>(parameter.selectionSource)
        assertEquals(401,source.definitions.size)
        assertEquals("comboSequenceNames",parameter.displayNameKey)
        val techniques=listOf("feat_7767abfc9b7aed0c","feat_f7202f148aa21fe3").mapIndexed { index,id ->
            AcquisitionInstance("technique-$index",DefinitionRef("dubl-3.69",id),1,emptyMap(),null,AcquisitionOrigin.New)
        }
        val initial=state(techniques)
        val field=acquisitionParameterFields(combo,initial,catalog,1).single()
        assertFalse(field.userDefined)
        assertEquals(2,field.sequenceLength)
        assertEquals(techniques.map { it.instanceId },field.options.map { it.id })
        var chosen=AcquisitionOrderedSelections.select(field,0,techniques[0].instanceId,emptyMap())
        assertFalse(AcquisitionNamedParameters.complete(listOf(field),chosen))
        chosen=AcquisitionOrderedSelections.select(field,1,techniques[1].instanceId,chosen)
        assertTrue(AcquisitionNamedParameters.complete(listOf(field),chosen))
        fun validate(rank:Int,params:Map<String,String>,owned:List<AcquisitionInstance> = techniques,id:String="combo")=
            validateAcquisitionTarget(combo.ref,3,combo.parameterDefinitions,combo.repeatPolicy,id,rank,params,null,owned,catalog=catalog)
        assertEquals(IdentityValidation.NewInstance,validate(1,chosen))
        assertIs<IdentityValidation.Blocked>(validate(1,chosen+("comboSequence" to "[\"not-owned\",\"technique-1\"]")))
        assertIs<IdentityValidation.Blocked>(validate(1,chosen+("comboSequence" to "[ \"technique-0\",\"technique-1\"]")))
        assertIs<IdentityValidation.Blocked>(validate(1,chosen+("comboSequenceNames" to "[\"Другое имя\",\"Сокрушительный удар\"]")))
        val old=AcquisitionInstance("combo",combo.ref,1,chosen,null,AcquisitionOrigin.New)
        assertEquals(RequirementStatus.Conflict,assertIs<IdentityValidation.Blocked>(validate(1,chosen,techniques+old,"duplicate")).status)
        val reverse=mapOf("comboSequence" to AcquisitionOrderedSelections.encode(techniques.reversed().map { it.instanceId }),
            "comboSequenceNames" to AcquisitionOrderedSelections.encode(techniques.reversed().map { AcquisitionLabels(catalog).acquisition(it.definition,it.parameters) }))
        assertEquals(IdentityValidation.NewInstance,validate(1,reverse,techniques+old,"reverse"))
        // The source does not require distinct techniques inside one sequence.
        val repeated=AcquisitionOrderedSelections.select(field,1,techniques[0].instanceId,chosen)
        assertEquals(IdentityValidation.NewInstance,validate(1,repeated))
        val next=acquisitionParameterFields(combo,state(techniques+old),catalog,2,chosen,chosen.keys,chosen).single()
        assertEquals(3,next.sequenceLength)
        assertEquals(2,next.sequenceReadOnlyPrefix)
        assertFalse(next.readOnly)
        val appended=AcquisitionOrderedSelections.select(next,2,techniques[0].instanceId,chosen)
        assertIs<IdentityValidation.ExistingInstance>(validate(2,appended,techniques+old))
        assertIs<IdentityValidation.Blocked>(validate(2,appended,techniques+old.copy(parameters=mapOf("comboSequence" to "[]","comboSequenceNames" to "[]"))))
        assertFailsWith<IllegalArgumentException> { AcquisitionOrderedSelections.select(next,0,techniques[1].instanceId,chosen) }
        assertIs<IdentityValidation.Blocked>(validate(2,appended+("comboSequence" to AcquisitionOrderedSelections.encode(listOf("technique-1","technique-0","technique-0"))),techniques+old))
        assertEquals(chosen,old.parameters)
    }

    @Test fun actual_combo_purchase_receipt_reopen_and_undo_keep_the_selected_sequence() = runBlocking {
        fun holding(id:String,rank:Int=1)=AcquisitionInstance("owned-$id",DefinitionRef("dubl-3.69",id),rank,emptyMap(),null,AcquisitionOrigin.New)
        val techniques=listOf(holding("feat_7767abfc9b7aed0c"),holding("feat_f7202f148aa21fe3"))
        val initial=state(techniques+listOf(holding("martial_style_6f736b57bce826e7"),holding("feat_9a0dbfed2efa56b7"),
            holding("feat_555de96ef1d0ca7c"),holding("unarmed",5),holding("athletics",4))).copy(character=DublCharacter("combo",
            attributes=AttributeId.entries.associateWith { AttributeValue(when(it) { AttributeId.STRENGTH->5;AttributeId.WILL->3;else->0 }) }))
        val combo=catalog.definitions.getValue(DefinitionRef("dubl-3.69","martial_technique_c4972b9f356e9d3d"))
        val params=mapOf("comboSequence" to AcquisitionOrderedSelections.encode(techniques.map { it.instanceId }),
            "comboSequenceNames" to AcquisitionOrderedSelections.encode(techniques.map { AcquisitionLabels(catalog).acquisition(it.definition,it.parameters) }))
        val store=InMemoryAcquisitionStore(mapOf("combo" to encodeEnvelope(initial)))
        val service=AcquisitionService(store,{ catalog },{ "id" })
        val request=AcquisitionRequest("combo",listOf(AcquisitionTarget(combo.ref,"my-combo",1,params)))
        val plan=service.preview(request)
        assertTrue(plan.executable,plan.issues.toString())
        assertEquals(Amount(10,0),plan.cost)
        assertEquals(listOf(combo.ref),plan.changes.map { it.definition })
        val receipt=assertIs<ApplyResult.Applied>(service.apply(ConfirmAcquisition("combo-purchase","combo",request,plan.baseRevision,plan.previewToken))).receipt
        assertEquals("Комбо (Таран → Сокрушительный удар)",AcquisitionPresentation.receipt(receipt,catalog,false).rows.single().label)
        val after=assertIs<LoadResult.Loaded>(decodeEnvelope(encodeEnvelope(assertIs<LoadResult.Loaded>(store.load("combo")).state))).state
        assertEquals(params,after.instances.single { it.instanceId=="my-combo" }.parameters)
        assertEquals(Amount(90,3),balance(after.ledger))
        val view=canonicalView(AcquisitionScope("scope","combo",0,0,0),after,catalog,AcquisitionCapabilities(true,false,true))
        assertEquals("Комбо (Таран → Сокрушительный удар)",view.ownedDevelopmentInstances(true).single { it.instanceId=="my-combo" }.label)
        assertFalse(after.character.development.containsKey(combo.ref.entryId))
        val undo=AcquisitionUndo(store)
        assertIs<UndoEligibility.Available>(undo.eligible("combo"))
        assertIs<ApplyResult.Applied>(undo.undo("combo","undo-combo",after.revision))
        assertEquals(initial.instances,assertIs<LoadResult.Loaded>(store.load("combo")).state.instances)
    }

    @Test fun actual_combo_cannot_repeat_the_same_technique_order_through_duplicate_single_instances() {
        val combo=catalog.definitions.getValue(DefinitionRef("dubl-3.69","martial_technique_c4972b9f356e9d3d"))
        val source=assertIs<ParameterSelectionSource.OrderedOwnedInstances>(combo.parameterDefinitions.single().selectionSource)
        assertTrue(source.definitions.all { catalog.definitions.getValue(it).repeatPolicy==RepeatPolicy.Single })
        fun holding(instance:String,id:String,rank:Int=1)=AcquisitionInstance(instance,DefinitionRef("dubl-3.69",id),rank,emptyMap(),null,AcquisitionOrigin.New)
        val first=holding("taran-a","feat_7767abfc9b7aed0c")
        val duplicate=first.copy(instanceId="taran-b")
        val strike=holding("strike","feat_f7202f148aa21fe3")
        fun parameters(technique:AcquisitionInstance)=mapOf("comboSequence" to AcquisitionOrderedSelections.encode(listOf(technique.instanceId,strike.instanceId)),
            "comboSequenceNames" to AcquisitionOrderedSelections.encode(listOf(technique,strike).map { AcquisitionLabels(catalog).acquisition(it.definition,it.parameters) }))
        val old=AcquisitionInstance("combo-first",combo.ref,1,parameters(first),null,AcquisitionOrigin.New)
        val owned=listOf(first,duplicate,strike,old,holding("style","martial_style_6f736b57bce826e7"),holding("unarmed","unarmed",5),
            holding("athletics","athletics",4),holding("power","feat_9a0dbfed2efa56b7"),holding("specialist","feat_555de96ef1d0ca7c"))
        val initial=state(owned).copy(character=DublCharacter("combo",attributes=AttributeId.entries.associateWith { AttributeValue(6) }))
        val request=AcquisitionRequest("combo",listOf(AcquisitionTarget(combo.ref,"combo-second",1,parameters(duplicate))))
        val rejected=plan(request,initial,catalog)
        assertFalse(rejected.executable,"Duplicate legacy copies of the same technique must not form another combination")
        assertTrue(rejected.changes.isEmpty())
        assertEquals(RequirementStatus.Conflict,assertIs<IdentityValidation.Blocked>(validateAcquisitionTarget(combo.ref,3,
            combo.parameterDefinitions,combo.repeatPolicy,"combo-second",1,parameters(duplicate),null,owned,catalog=catalog)).status)
        assertEquals(Amount(100,3),balance(initial.ledger))
        assertEquals(owned,initial.instances)
    }

    @Test fun ordered_repeat_identity_preserves_declared_parameters_and_ability_variants() {
        val combo=catalog.definitions.getValue(DefinitionRef("dubl-3.69","martial_technique_c4972b9f356e9d3d"))
        val technique=catalog.definitions.getValue(DefinitionRef("dubl-3.69","feat_7767abfc9b7aed0c"))
        val strike=AcquisitionInstance("strike",DefinitionRef("dubl-3.69","feat_f7202f148aa21fe3"),1,emptyMap(),null,AcquisitionOrigin.New)
        fun checkVariant(definition:AcquisitionDefinition,first:AcquisitionInstance,second:AcquisitionInstance,expected:RequirementStatus?) {
            val scoped=catalog.copy(definitions=catalog.definitions+(definition.ref to definition))
            fun params(owned:AcquisitionInstance)=mapOf("comboSequence" to AcquisitionOrderedSelections.encode(listOf(owned.instanceId,strike.instanceId)),
                "comboSequenceNames" to AcquisitionOrderedSelections.encode(listOf(owned,strike).map { AcquisitionLabels(scoped).acquisition(it.definition,it.parameters) }))
            val old=AcquisitionInstance("combo-first",combo.ref,1,params(first),null,AcquisitionOrigin.New)
            val result=validateAcquisitionTarget(combo.ref,3,combo.parameterDefinitions,combo.repeatPolicy,"combo-second",1,params(second),null,
                listOf(first,second,strike,old),catalog=scoped)
            if(expected==null) assertEquals(IdentityValidation.NewInstance,result)
            else assertEquals(expected,assertIs<IdentityValidation.Blocked>(result).status)
        }
        // Current source techniques have no variant schema; these explicit schemas guard future typed contracts.
        val parameterized=technique.copy(repeatPolicy=RepeatPolicy.DistinctParameter(setOf("variantId")),parameterDefinitions=listOf(
            ParameterDefinition("variantId",ParameterDomain.StableIds(setOf("low","high")),displayNameKey="variantName")))
        val low=AcquisitionInstance("low",technique.ref,1,mapOf("variantId" to "low","variantName" to "Нижний"),null,AcquisitionOrigin.New)
        checkVariant(parameterized,low,low.copy(instanceId="high",parameters=mapOf("variantId" to "high","variantName" to "Верхний")),null)
        checkVariant(parameterized,low,low.copy(instanceId="same-low",parameters=low.parameters+("variantName" to "Другое отображаемое имя")),RequirementStatus.Conflict)
        val withOptions=technique.copy(repeatPolicy=RepeatPolicy.Independent,abilityOptionIds=setOf("first","second"))
        val first=low.copy(parameters=emptyMap(),abilityOptionId="first")
        checkVariant(withOptions,first,first.copy(instanceId="second",abilityOptionId="second"),null)
        // Stray legacy options cannot invent an option dimension absent from the source definition.
        checkVariant(technique,first.copy(abilityOptionId=null),first.copy(instanceId="stray",abilityOptionId="unverified"),RequirementStatus.Conflict)
    }

    @Test fun actual_combo_count_is_independent_of_unrelated_knowledge_and_keeps_uncertain_candidates_closed() {
        fun holding(id:String,rank:Int=1,parameters:Map<String,String> = emptyMap()) = AcquisitionInstance("owned-$id",
            DefinitionRef("dubl-3.69",id),rank,parameters,null,AcquisitionOrigin.New)
        val combo=catalog.definitions.getValue(DefinitionRef("dubl-3.69","martial_technique_c4972b9f356e9d3d"))
        val techniques=listOf(holding("feat_7767abfc9b7aed0c"),holding("feat_f7202f148aa21fe3"))
        val valid=techniques+listOf(holding("martial_style_6f736b57bce826e7"),holding("feat_9a0dbfed2efa56b7"),
            holding("feat_555de96ef1d0ca7c"),holding("unarmed",5),holding("athletics",4))
        val params=mapOf("comboSequence" to AcquisitionOrderedSelections.encode(techniques.map { it.instanceId }),
            "comboSequenceNames" to AcquisitionOrderedSelections.encode(techniques.map { AcquisitionLabels(catalog).acquisition(it.definition,it.parameters) }))
        val knowledge=(0 until 45).map { index -> AcquisitionInstance("unrelated-$index",DefinitionRef("dubl-3.69","knowledge_template"),10,
            mapOf("specialization" to "custom:unrelated-$index","specializationName" to "Знание $index"),null,AcquisitionOrigin.New) }
        fun context(owned:List<AcquisitionInstance>,rules:AcquisitionCatalog=catalog)=acquisitionContext(state(owned).copy(
            character=DublCharacter("combo",attributes=AttributeId.entries.associateWith { AttributeValue(6) })),rules)
        assertEquals(RequirementStatus.Satisfied,evaluate(combo.requirements,context(valid),params).status)
        for(owned in listOf(knowledge+valid,valid+knowledge,(knowledge+valid).reversed()))
            assertEquals(RequirementStatus.Satisfied,evaluate(combo.requirements,context(owned),params).status)
        fun counts(node:RequirementNode):List<RequirementNode.DistinctCount> = when(node) {
            is RequirementNode.DistinctCount -> listOf(node)
            is RequirementNode.AllOf -> node.children.flatMap(::counts)
            is RequirementNode.AnyOf -> node.children.flatMap(::counts)
            else -> emptyList()
        }
        val count=counts(combo.requirements).single()
        val migi=holding("martial_technique_23fd4936857b5c0a")
        val onlyOne=listOf(migi,holding("martial_style_2cb08b5fe00825ab"),holding("melee_weapon",4),
            holding("weapon_proficiency_template",parameters=mapOf("groupId" to "polearms","groupName" to "Древковое")))
        for(owned in listOf(knowledge+onlyOne,onlyOne+knowledge)) {
            val rejected=evaluate(count,context(owned))
            assertEquals(RequirementStatus.Unsatisfied,rejected.status)
            assertEquals(RequirementValue.Number(1),rejected.actualValue)
        }
        val missing=catalog.copy(definitions=catalog.definitions-techniques.first().definition)
        assertEquals(RequirementStatus.SourceUnresolved,evaluate(count,context(knowledge+onlyOne+techniques.first(),missing)).status)
    }

    private fun state(instances:List<AcquisitionInstance>)=StoredCharacter(DublCharacter("combo"),CharacterSheetExtras(),instances,
        Ledger(Opening("opening","native-combo",Amount(100,3),RequirementNode.CreationPhase.Phase.Creating,OpeningProvenance.NewCharacter),emptyList()),
        emptyList(),emptyList(),SyncMetadata(0),0,0,RequirementNode.CreationPhase.Phase.Creating)
}
