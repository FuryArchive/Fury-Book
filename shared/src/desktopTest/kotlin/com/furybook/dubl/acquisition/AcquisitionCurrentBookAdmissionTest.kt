package com.furybook.dubl.acquisition

import com.furybook.content.FcpTextSource
import com.furybook.dubl.content.DublFcp
import com.furybook.dubl.model.*
import kotlinx.coroutines.runBlocking
import kotlin.test.*

/** Real current-book definitions; neither requirements nor prices are replaced by test rules. */
class AcquisitionCurrentBookAdmissionTest {
    private val catalog by lazy { DublFcp.open(FcpTextSource { javaClass.classLoader.getResource(it)?.readText() }).loadAcquisition().catalog }
    private val phase = RequirementNode.CreationPhase.Phase.Creating
    private fun ref(id: String) = DefinitionRef("dubl-3.69",id)
    private fun owned(id: String,rank: Int=1,parameters: Map<String,String> = emptyMap()) =
        AcquisitionInstance("owned/$id",ref(id),rank,parameters,null,AcquisitionOrigin.New)
    private fun state(holdings: List<AcquisitionInstance> = emptyList(),spells: List<KnownSpell> = emptyList()) =
        StoredCharacter(DublCharacter("book",attributes=DublCharacter("book").attributes.mapValues { AttributeValue(5) },
            magic=CharacterMagic(manaRank=1,power=4,spells=spells)),CharacterSheetExtras(),holdings,
            Ledger(Opening("opening","already-paid-holdings",Amount(2000,10),phase,OpeningProvenance.UserConfirmed,
                "Existing ranks remain paid"),emptyList()),emptyList(),emptyList(),SyncMetadata(0),0,0,phase)
    private fun nodes(root: RequirementNode): List<RequirementNode> = listOf(root)+when(root) {
        is RequirementNode.AllOf -> root.children.flatMap(::nodes)
        is RequirementNode.AnyOf -> root.children.flatMap(::nodes)
        is RequirementNode.DistinctCount -> nodes(root.predicate)
        else -> emptyList()
    }
    private fun present(plan: AcquisitionPlan,state: StoredCharacter) = AcquisitionPresentation.present(
        AcquisitionScope("local","book",0,0,0),AcquisitionQuoteKey("book",0),plan,state,catalog,AcquisitionCapabilities(true,false,true))
    private fun views(value: AcquisitionRequirementView): List<AcquisitionRequirementView> = listOf(value)+value.children.flatMap(::views)

    @Test fun two_trap_cards_buy_one_access_and_never_rebill_it_after_reopening() = runBlocking {
        val before=state(listOf(owned("stealth",5)))
        val targets=listOf("feat_ff2b2532be3cc900","feat_b802afc2806c5b17").map { AcquisitionTarget(ref(it),targetRank=1) }
        val request=AcquisitionRequest("book",targets)
        val store=InMemoryAcquisitionStore(mapOf("book" to encodeEnvelope(before)))
        val service=AcquisitionService(store,{catalog},{"id"})
        val preview=service.preview(request)
        assertTrue(preview.executable,preview.issues.toString())
        assertEquals(Amount(70,2),preview.cost)
        assertEquals(1,preview.changes.count { it.definition==ref("access_d0fdba646d5da299") })
        val receipt=assertIs<ApplyResult.Applied>(service.apply(ConfirmAcquisition("traps","book",request,preview.baseRevision,preview.previewToken))).receipt
        val after=assertIs<LoadResult.Loaded>(decodeEnvelope(encodeEnvelope(assertIs<LoadResult.Loaded>(store.load("book")).state))).state
        assertEquals(Amount(1930,8),balance(after.ledger))
        assertEquals(1,after.instances.single { it.definition==ref("access_d0fdba646d5da299") }.rank)
        assertEquals(listOf(Amount(0,2),Amount(30,0),Amount(40,0)),
            (savedAcquisitionOperation(receipt,after.ledger.entries.last()).evidence as AcquisitionSavedEvidence.Recorded).prices.map { it.cost })
        assertEquals(Amount.ZERO,plan(request,after,catalog).cost)
        assertIs<ApplyResult.Applied>(AcquisitionUndo(store).undo("book","undo-traps",after.revision))
        assertEquals(acquisitionProjectionCanonical(before),acquisitionProjectionCanonical(assertIs<LoadResult.Loaded>(store.load("book")).state))
    }

    @Test fun hunter_accepts_a_real_named_knowledge_and_preserves_each_rank_choice() = runBlocking {
        val knowledge=owned("knowledge_template",4,mapOf("specialization" to "wildlife","specializationName" to "Животный мир"))
        val before=state(listOf(owned("survival",4),owned("medicine",4),knowledge,owned("access_597f3218972b15b7")))
        val store=InMemoryAcquisitionStore(mapOf("book" to encodeEnvelope(before)))
        val service=AcquisitionService(store,{catalog},{"id"})
        val target=AcquisitionTarget(ref("feat_dee6d91d0a987242"),"hunter",1,mapOf("creatureType1" to "animal"))
        for((operation,rank,parameters) in listOf(Triple("hunter-one",1,target.parameters),Triple("hunter-two",2,target.parameters+("creatureType2" to "undead")))) {
            val request=AcquisitionRequest("book",listOf(target.copy(targetRank=rank,parameters=parameters)))
            val preview=service.preview(request)
            assertTrue(preview.executable,preview.issues.toString())
            assertEquals(Amount(50,0),preview.cost)
            assertEquals(listOf(target.definition),preview.changes.map { it.definition })
            assertIs<ApplyResult.Applied>(service.apply(ConfirmAcquisition(operation,"book",request,preview.baseRevision,preview.previewToken)))
        }
        val after=assertIs<LoadResult.Loaded>(store.load("book")).state
        assertEquals(mapOf("creatureType1" to "animal","creatureType2" to "undead"),after.instances.single { it.instanceId=="hunter" }.parameters)
        assertEquals(4,after.instances.single { it.instanceId==knowledge.instanceId }.rank)
        assertEquals(Amount(1900,10),balance(after.ledger))
    }

    @Test fun referenced_school_rank_admits_blood_purchase_without_inventing_school_price() {
        val before=state(listOf(owned("magic-school:некромантия",2)))
        val request=AcquisitionRequest("book",listOf(AcquisitionTarget(ref("feat_430bb8e0abcdd492"),targetRank=1,optionId="xp")))
        val purchase=plan(request,before,catalog)
        assertTrue(purchase.executable,purchase.issues.toString())
        assertEquals(Amount(40,0),purchase.cost)
        assertEquals(listOf(ref("feat_430bb8e0abcdd492")),purchase.changes.map { it.definition })
        assertFalse(plan(request,state(),catalog).executable)
        assertFalse(plan(AcquisitionRequest("book",listOf(AcquisitionTarget(ref("magic-school:некромантия"),targetRank=2))),state(),catalog).executable)
    }

    @Test fun current_weapon_alternatives_remain_or_and_have_readable_rank_labels() {
        for(id in listOf("feat_e63dcbbf9b7dda01","feat_48b024e36a3ddd21")) {
            val node=nodes(catalog.definitions.getValue(ref(id)).requirements).filterIsInstance<RequirementNode.AnyOf>().single()
            val before=state(listOf(owned("unarmed",5)))
            assertEquals(RequirementStatus.Satisfied,evaluate(node,acquisitionContext(before,catalog)).status)
            assertTrue(AcquisitionLabels(catalog).condition(node).contains("Рукопашный бой ≥ 5"))
            assertEquals(RequirementStatus.Unsatisfied,evaluate(node,acquisitionContext(state(listOf(owned("unarmed",4))),catalog)).status)
        }
    }

    @Test fun side_specialist_requires_the_selected_rank_four_skill_and_explicit_applicability() {
        val definition=catalog.definitions.getValue(ref("feat_95b19d58202e83b8"))
        val before=state(listOf(owned("medicine",4)))
        val request=AcquisitionRequest("book",listOf(AcquisitionTarget(definition.ref,targetRank=1)))
        assertFalse(plan(request,before,catalog).executable)
        val branch=nodes(definition.requirements).filterIsInstance<RequirementNode.AllOf>().single { n ->
            n.children.any { it is RequirementNode.SkillFamily && it.familyId=="medicine" } }
        val condition=branch.children.filterIsInstance<RequirementNode.ExternalEvidence>().single()
        val confirmed=RequirementWitness.External(condition.evidenceKey,condition.scopeId,"medicine-synergy-confirmed",condition.sourceRef)
        val choice=nodes(definition.requirements).filterIsInstance<RequirementNode.AnyOf>().single()
        val selected=AcquisitionRequest("book",request.targets,mapOf(acquisitionChoiceId(definition.ref,choice.nodeId) to branch.nodeId),
            externalConfirmations=listOf(confirmed))
        val preview=plan(selected,before,catalog)
        assertTrue(preview.executable,preview.issues.toString())
        assertEquals(Amount(20,0),preview.cost)
        assertEquals(listOf(definition.ref),preview.changes.map { it.definition })
        assertTrue(present(preview,before).choices.all { it.options.any { option -> option.label.contains("Медицина ≥ 4") } })
    }

    @Test fun martial_effect_disagreement_does_not_block_the_agreed_purchase_or_erase_its_style() {
        val uppercut=catalog.definitions.getValue(ref("martial_technique_175d9254b640d8a4"))
        assertTrue(nodes(uppercut.requirements).none { it is RequirementNode.Unresolved })
        assertTrue(nodes(uppercut.requirements).any { it is RequirementNode.OwnedRank && it.entryId.startsWith("martial_style_") })
        val gates=ref("martial_technique_66324cb638a0cbd6")
        val before=state(listOf(owned("martial_style_8f76adeae5ab87d1"),owned("feat_555de96ef1d0ca7c"),owned("feat_195750d39f11cfba"),owned("unarmed",5)))
        val preview=plan(AcquisitionRequest("book",listOf(AcquisitionTarget(gates,targetRank=1))),before,catalog)
        assertTrue(preview.executable,preview.issues.toString())
        assertEquals(Amount(40,0),preview.cost)
        assertTrue(present(preview,before).effectNotices.any { it.contains("описание игрового эффекта не заполнено") })
    }

    @Test fun elemental_admission_counts_three_distinct_learned_spells_and_retains_scoped_confirmations() = runBlocking {
        for((ability,key,spells) in listOf(
            Triple("feat_ca778ede19771422","spell-descriptor:огонь",listOf("spell_ff0f2470d3958645","spell_0dd92d6f7100cee1","spell_a7d134030c3d6036")),
            Triple("feat_22eb6769decb881c","spell-descriptor:холод",listOf("spell_6e22b50cb054eb71","spell_58f5e95b4153e838","spell_198066545cafb36e")))) {
            val definition=catalog.definitions.getValue(ref(ability))
            val count=nodes(definition.requirements).filterIsInstance<RequirementNode.DistinctCount>().single()
            val confirmations=nodes(count).filterIsInstance<RequirementNode.ExternalEvidence>().filter { it.scopeId in spells }.map {
                RequirementWitness.External(key,it.scopeId,"confirmed:${it.scopeId}",it.sourceRef) }
            val learned=spells.map { KnownSpell("learned/$it",it,catalog.definitions.getValue(ref(it)).name,learned=true) }
            val before=state(listOf(owned("mana_rank")),learned)
            val request=AcquisitionRequest("book",listOf(AcquisitionTarget(definition.ref,targetRank=1)),externalConfirmations=confirmations)
            val unconfirmed=plan(AcquisitionRequest("book",request.targets),before,catalog)
            assertFalse(unconfirmed.executable)
            val views=present(unconfirmed,before).requirements.flatMap(::views)
            assertEquals(3,views.count { it.external?.evidenceKey==key },"Only learned spell descriptor checkboxes should be displayed")
            val preview=plan(request,before,catalog)
            assertTrue(preview.executable,preview.issues.toString())
            assertEquals(Amount(100,1),preview.cost)
            assertEquals(2,preview.changes.size)
            assertEquals(RequirementStatus.Unsatisfied,evaluate(count,acquisitionContext(before.copy(character=before.character.copy(
                magic=before.character.magic.copy(spells=learned.take(2)+learned.first().copy(uid="duplicate")))),catalog,confirmations)).status)
            val stale=confirmations.map { it.copy(sourceRef=it.sourceRef.copy(documentVersion="old-book")) }
            assertNotEquals(RequirementStatus.Satisfied,evaluate(count,acquisitionContext(before,catalog,stale)).status)
            val store=InMemoryAcquisitionStore(mapOf("book" to encodeEnvelope(before)))
            val service=AcquisitionService(store,{catalog},{"id"})
            val receipt=assertIs<ApplyResult.Applied>(service.apply(ConfirmAcquisition("buy-element","book",request,preview.baseRevision,preview.previewToken))).receipt
            val after=assertIs<LoadResult.Loaded>(decodeEnvelope(encodeEnvelope(assertIs<LoadResult.Loaded>(store.load("book")).state))).state
            assertEquals(Amount(1900,9),balance(after.ledger))
            assertEquals(RequirementStatus.Satisfied,evaluate(count,acquisitionContext(after,catalog)).status)
            assertEquals(3,retainedAcquisitionConfirmations(after).size)
            val checkView=views.first { it.external?.evidenceKey==key }.copy(status=RequirementStatus.Satisfied)
            assertEquals(AcquisitionExternalConfirmationView(true,true),acquisitionExternalConfirmation(checkView,AcquisitionRequest("book",request.targets)))
            assertEquals(Amount(100,1),receipt.result.cost)
            assertIs<ApplyResult.Applied>(AcquisitionUndo(store).undo("book","undo-element",after.revision))
            assertTrue(retainedAcquisitionConfirmations(assertIs<LoadResult.Loaded>(store.load("book")).state).isEmpty())
        }
    }
}
