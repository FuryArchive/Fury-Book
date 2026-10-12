package com.furybook.dubl.acquisition

import com.furybook.dubl.model.*
import com.furybook.dubl.data.SnapshotCodec
import kotlin.coroutines.*
import kotlin.test.*

class AcquisitionMutationTest {
    private val phase = RequirementNode.CreationPhase.Phase.Creating
    private val source = RuleSourceRef("mutation-fixture", "1", "Explicit price")
    private fun ref(id: String = "target") = DefinitionRef("test", id)
    private fun definition(id: String = "target", kind: AcquisitionKind = AcquisitionKind.DEVELOPMENT) = AcquisitionDefinition(ref(id),kind,3,
        RepeatPolicy.Single,RequirementNode.AllOf("none",source,emptyList(),NoRequirementsEvidence(source,"No prerequisites")),
        PriceRule.PerRank(20,0,source),SourceStatus.Confirmed,id,source)
    private fun catalog() = AcquisitionCatalog(listOf(definition(), definition("spell",AcquisitionKind.SPELL)).associateBy { it.ref },"c1","r1")
    private fun state() = StoredCharacter(DublCharacter("hero",hpCurrent=2,healthMaximumOverride=10),CharacterSheetExtras(),emptyList(),
        Ledger(Opening("new","funding",Amount(100,0),phase,OpeningProvenance.NewCharacter),emptyList()),emptyList(),emptyList(),SyncMetadata(0),0,0,phase)
    private fun store(state: StoredCharacter = state()) = InMemoryAcquisitionStore(mapOf("hero" to encodeEnvelope(state)))
    private fun coordinator(store: AcquisitionStore) = CharacterMutationCoordinator(store,::catalog,{"id"})
    private suspend fun load(store: AcquisitionStore) = assertIs<LoadResult.Loaded>(store.load("hero")).state
    private suspend fun buy(store: AcquisitionStore, targets: List<AcquisitionTarget> = listOf(AcquisitionTarget(ref(),targetRank=1))): ConfirmAcquisition {
        val service=AcquisitionService(store,::catalog,{"id"}); val request=AcquisitionRequest("hero",targets); val p=service.preview(request)
        val command=ConfirmAcquisition("op1","hero",request,p.baseRevision,p.previewToken)
        assertIs<ApplyResult.Applied>(service.apply(command));return command
    }
    private fun run(block:suspend ()->Unit) { var completion:Result<Unit>?=null; block.startCoroutine(object:Continuation<Unit>{
        override val context=EmptyCoroutineContext;override fun resumeWith(result:Result<Unit>){completion=result}});checkNotNull(completion).getOrThrow() }
    @Test fun undo_restart_exact_and_operation_identity() = run {
        val backing=store();val command=buy(backing);assertEquals(Amount(80,0),balance(load(backing).ledger))
        val restarted=store(load(backing));assertIs<ApplyResult.Applied>(AcquisitionUndo(restarted).undo("hero","u1",1))
        val saved=load(restarted);assertEquals(Amount(100,0),balance(saved.ledger));assertEquals(state().character,saved.character)
        assertIs<ApplyResult.AlreadyApplied>(AcquisitionUndo(restarted).undo("hero","u1",1))
        assertIs<ApplyResult.IdentityConflict>(AcquisitionUndo(restarted).undo("hero","u1",2))
        assertIs<ApplyResult.AlreadyUndone>(AcquisitionService(restarted,::catalog,{"x"}).apply(command))
        assertEquals(ReceiptStatus.Undone,saved.receipts.first().status);assertEquals(2,saved.outbox.size)
    }
    @Test fun undo_after_hp_change_is_blocked() = run {
        val backing=store();buy(backing)
        assertIs<MutationResult.Applied>(coordinator(backing).mutate(CanonicalMutation("hp","hero",1,
            MutationPayload.NonFinancial(CharacterPatch(load(backing).character.copy(hpCurrent=1))))))
        assertIs<ApplyResult.Blocked>(AcquisitionUndo(backing).undo("hero","u1",2))
        assertEquals(Amount(80,0),balance(load(backing).ledger));assertEquals(1,load(backing).character.hpCurrent)
    }
    @Test fun undo_after_sync_same_tip_allowed() = run {
        val backing=store();buy(backing);val old=load(backing)
        assertEquals(CommitResult.Committed,backing.compareAndSet("hero",old.storageRevision,old.copy(storageRevision=2,outbox=emptyList(),syncMetadata=SyncMetadata(9))))
        assertIs<ApplyResult.Applied>(AcquisitionUndo(backing).undo("hero","u1",1))
        assertEquals(9L,load(backing).outbox.single().parentServerRevision);assertEquals(3L,load(backing).storageRevision)
    }
    @Test fun grant_repeat_once_and_explicit_signed_amount() = run {
        val backing=store();val cmd=CanonicalMutation("grant","hero",0,MutationPayload.Grant(Amount(1000,0)),"award")
        assertIs<MutationResult.Applied>(coordinator(backing).mutate(cmd));assertIs<MutationResult.AlreadyApplied>(coordinator(backing).mutate(cmd))
        assertIs<MutationResult.IdentityConflict>(coordinator(backing).mutate(cmd.copy(reason="different")))
        assertEquals(Amount(1100,0),balance(load(backing).ledger));assertEquals(0,load(backing).character.experience)
        assertIs<ApplyResult.Applied>(AcquisitionUndo(backing).undo("hero","undo",1));assertEquals(Amount(100,0),balance(load(backing).ledger))
    }
    @Test fun manual_legacy_refund_requires_amount_and_reason() = run {
        val backing=store();val patch=CharacterPatch(state().character.copy(experience=50))
        assertIs<MutationResult.Blocked>(coordinator(backing).mutate(CanonicalMutation("bad","hero",0,MutationPayload.ManualCorrection(patch,Amount(10,0)))))
        assertIs<MutationResult.Applied>(coordinator(backing).mutate(CanonicalMutation("ok","hero",0,MutationPayload.ManualCorrection(patch,Amount(10,0)),"explicit refund")))
        assertEquals(Amount(110,0),balance(load(backing).ledger))
        assertIs<ApplyResult.Applied>(AcquisitionUndo(backing).undo("hero","u",1));assertEquals(state().character,load(backing).character)
    }
    @Test fun complete_creation_no_reprice_and_manual_reopen_retains_finalized_access() = run {
        val backing=store();val c=coordinator(backing);val cmd=CanonicalMutation("complete","hero",0,MutationPayload.CompleteCreation)
        assertIs<MutationResult.Applied>(c.mutate(cmd));assertIs<MutationResult.AlreadyApplied>(c.mutate(cmd))
        assertEquals(Amount(100,0),balance(load(backing).ledger));assertTrue(load(backing).character.creationComplete)
        assertIs<MutationResult.Applied>(c.mutate(CanonicalMutation("again","hero",1,MutationPayload.CompleteCreation)))
        assertEquals(Amount(100,0),balance(load(backing).ledger))
        assertIs<MutationResult.Blocked>(c.mutate(CanonicalMutation("reopen","hero",2,MutationPayload.ManualCorrection(
            CharacterPatch(load(backing).character.copy(creationComplete=false)),Amount.ZERO),"manual reopen")))
    }
    @Test fun nonfinancial_patch_cannot_alter_rank_xp_attributes_spell_or_instances() = run {
        val original=state();val changed=listOf(original.character.copy(experience=100),original.character.copy(development=mapOf("target" to OwnedDevelopment(1))),
            original.character.copy(attributes=original.character.attributes + (AttributeId.STRENGTH to AttributeValue(1))),
            original.character.copy(magic=CharacterMagic(spells=listOf(KnownSpell("s","spell",learned=true)))))
        for(character in changed) {val backing=store();assertIs<MutationResult.Blocked>(coordinator(backing).mutate(CanonicalMutation("x","hero",0,MutationPayload.NonFinancial(CharacterPatch(character)))))
            assertEquals(original,load(backing))}
        val backing=store();val instance=AcquisitionInstance("new",ref(),1,emptyMap(),null,AcquisitionOrigin.New)
        assertIs<MutationResult.Blocked>(coordinator(backing).mutate(CanonicalMutation("x","hero",0,MutationPayload.NonFinancial(CharacterPatch(original.character,listOf(instance))))))
    }
    @Test fun exact_absent_vs_unlearned_and_rank_zero_restoration() = run {
        for(existing in listOf(false,true)) {
            val old=state().copy(character=state().character.copy(development=if(existing) mapOf("target" to OwnedDevelopment(0)) else emptyMap(),
                magic=CharacterMagic(spells=if(existing) listOf(KnownSpell("old","spell",learned=false,description="retained")) else emptyList())))
            val backing=store(old);buy(backing,listOf(AcquisitionTarget(ref(),targetRank=1),AcquisitionTarget(ref("spell"),targetRank=1)))
            assertIs<ApplyResult.Applied>(AcquisitionUndo(backing).undo("hero","undo",1));assertEquals(old.character,load(backing).character);assertEquals(old.instances,load(backing).instances)
        }
    }
    @Test fun failed_and_lost_reply_mutations_remain_atomic() = run {
        val backing=store();val cmd=CanonicalMutation("g","hero",0,MutationPayload.Grant(Amount(2,0)),"award")
        val fail=object:AcquisitionStore by backing {override suspend fun compareAndSet(characterId:String,expectedStorageRevision:Long,next:StoredCharacter)=CommitResult.StorageFailure("full")}
        assertIs<MutationResult.StorageFailure>(coordinator(fail).mutate(cmd));assertEquals(state(),load(backing))
        val lost=object:AcquisitionStore by backing {override suspend fun compareAndSet(characterId:String,expectedStorageRevision:Long,next:StoredCharacter):CommitResult{
            backing.compareAndSet(characterId,expectedStorageRevision,next);error("lost reply")}}
        assertIs<MutationResult.StorageFailure>(coordinator(lost).mutate(cmd));assertIs<MutationResult.AlreadyApplied>(coordinator(backing).mutate(cmd));assertEquals(Amount(102,0),balance(load(backing).ledger))
    }

    @Test fun complete_creation_eligible_undo_restores_exact_phase_without_repricing() = run {
        val backing=store();assertIs<MutationResult.Applied>(coordinator(backing).mutate(CanonicalMutation("complete","hero",0,MutationPayload.CompleteCreation)))
        assertIs<ApplyResult.Applied>(AcquisitionUndo(backing).undo("hero","u",1))
        assertEquals(state().character,load(backing).character);assertEquals(phase,load(backing).creationPhase);assertEquals(Amount(100,0),balance(load(backing).ledger))
    }
    @Test fun detached_staging_captures_retryable_command_and_rejects_hidden_mutations() = run {
        val backing=store();val c=coordinator(backing)
        val cmd=c.prepareNonFinancial("hp","hero",0) { character.changeHp(-1) }
        assertEquals(state(),load(backing))
        assertIs<MutationResult.Applied>(c.mutate(cmd));assertIs<MutationResult.AlreadyApplied>(coordinator(backing).mutate(cmd))
        assertEquals(1,load(backing).character.hpCurrent)
        for(edit in listOf<com.furybook.dubl.application.DublApplication.()->Unit>(
            { character.setExperience(900) }, { character.changeAttribute(AttributeId.STRENGTH,1) }, { character.completeCreation() },
            { development.setRank("target",1) }, { magic.setManaRank(1) })) {
            val clean=store();val coordinator=coordinator(clean)
            assertIs<MutationResult.Blocked>(coordinator.stageNonFinancial("hidden","hero",0,edit));assertEquals(state(),load(clean))
        }
    }
    @Test fun nonfinancial_cannot_redefine_skill_attribute_identity() = run {
        val before=state().copy(character=state().character.copy(skills=mapOf("skill" to CharacterSkill("skill","skill",rank=1))))
        val backing=store(before);val patch=CharacterPatch(before.character.copy(skills=mapOf("skill" to before.character.skills.getValue("skill").copy(attributes=listOf(AttributeId.INTELLIGENCE)))))
        assertIs<MutationResult.Blocked>(coordinator(backing).mutate(CanonicalMutation("hidden","hero",0,MutationPayload.NonFinancial(patch))))
    }
    @Test fun immutable_patch_and_new_specialization_manual_guard() = run {
        val rows=mutableMapOf("target" to OwnedDevelopment(1));val patch=CharacterPatch(state().character.copy(development=rows))
        rows["target"]=OwnedDevelopment(3)
        val backing=store();assertIs<MutationResult.Applied>(coordinator(backing).mutate(CanonicalMutation("manual","hero",0,MutationPayload.ManualCorrection(patch,Amount.ZERO),"manual value")))
        assertEquals(1,load(backing).character.development.getValue("target").rank)
        val instance=AcquisitionInstance("specialization",ref(),1,emptyMap(),null,AcquisitionOrigin.New)
        assertIs<MutationResult.Blocked>(coordinator(backing).mutate(CanonicalMutation("new","hero",1,MutationPayload.ManualCorrection(CharacterPatch(load(backing).character,listOf(instance)),Amount.ZERO),"new specialization")))
    }
    private fun legacy():StoredCharacter {
        val character=DublCharacter("hero",development=mapOf("target" to OwnedDevelopment(2)))
        val raw=SnapshotCodec.encode(AppSnapshot(listOf(character),"hero"))
        val pending=assertIs<MigrationResult.NeedsBalanceConfirmation>(prepareLegacy(raw,LegacyOrigin("hero",11,"old-revision",raw,listOf("dubl-3.69")),null)).pending
        return confirmLegacyBalance(pending,Amount(100,0),"old balance")
    }
    private fun resolution(state:StoredCharacter,rank:Int=2,key:String="development:target",definition:DefinitionRef=ref()):MutationPayload.ConfirmLegacyParameter {
        val origin=state.ledger.opening.legacyMigration!!.origin
        val instanceOrigin=AcquisitionOrigin.Legacy(origin.characterId,origin.schema.toString(),origin.sourceRevision,origin.sourceFingerprint,key)
        return MutationPayload.ConfirmLegacyParameter(legacyInstanceId(definition,instanceOrigin),emptyMap(),LegacyOwnershipResolution(key,definition,rank))
    }
    @Test fun resolve_original_rank_no_charge_then_remove_reacquire_and_undo_marker() = run {
        val original=legacy();val backing=store(original);val c=coordinator(backing)
        assertEquals(1,unresolvedLegacyOwnership(original,catalog()).size)
        assertIs<MutationResult.Applied>(c.mutate(CanonicalMutation("resolve","hero",0,resolution(original),"confirmed identity")))
        val mapped=load(backing);assertEquals(2,mapped.instances.single().rank);assertEquals(Amount(100,0),balance(mapped.ledger));assertTrue(unresolvedLegacyOwnership(mapped,catalog()).isEmpty())
        assertEquals(original.ledger.opening,mapped.ledger.opening)
        val undoStore=store(mapped);assertIs<ApplyResult.Applied>(AcquisitionUndo(undoStore).undo("hero","u-resolve",1));assertEquals(1,unresolvedLegacyOwnership(load(undoStore),catalog()).size)
        assertIs<MutationResult.Applied>(c.mutate(CanonicalMutation("remove","hero",1,MutationPayload.ManualCorrection(
            CharacterPatch(mapped.character.copy(development=emptyMap()),emptyList()),Amount(7,0)),"explicit legacy refund")))
        val removed=load(backing);assertEquals(Amount(107,0),balance(removed.ledger));assertTrue(unresolvedLegacyOwnership(removed,catalog()).isEmpty())
        val reacquire=AcquisitionService(store(removed),::catalog,{"id"});assertTrue(reacquire.preview(AcquisitionRequest("hero",listOf(AcquisitionTarget(ref(),targetRank=1)))).executable)
        assertIs<ApplyResult.Applied>(AcquisitionUndo(backing).undo("hero","u-remove",2));assertEquals(mapped.instances,load(backing).instances);assertEquals(mapped.character,load(backing).character)
        assertEquals(Amount(100,0),balance(load(backing).ledger));assertTrue(activeLegacyRemovalKeys(load(backing)).isEmpty())
    }
    @Test fun legacy_resolution_rejects_rank_increase_wrong_key_candidate_scope_and_blank_reason() = run {
        val original=legacy()
        val bad=listOf(resolution(original,3),resolution(original,1),resolution(original,key="development:other"),resolution(original,definition=ref("spell")),
            MutationPayload.ConfirmLegacyParameter("wrong-id",emptyMap(),LegacyOwnershipResolution("development:target",ref(),2)))
        for(payload in bad) {val backing=store(original);assertIs<MutationResult.Blocked>(coordinator(backing).mutate(CanonicalMutation("bad","hero",0,payload,"confirm")));assertEquals(original,load(backing))}
        assertIs<MutationResult.Blocked>(coordinator(store(original)).mutate(CanonicalMutation("bad","hero",0,resolution(original))))
    }
    @Test fun forged_or_noop_removal_evidence_never_resolves_original_ownership() = run {
        val original=legacy();val backing=store(original);val c=coordinator(backing)
        assertIs<MutationResult.Applied>(c.mutate(CanonicalMutation("manual","hero",0,MutationPayload.ManualCorrection(CharacterPatch(original.character),Amount.ZERO),"not a removal")))
        val noop=load(backing);assertEquals(1,unresolvedLegacyOwnership(noop,catalog()).size)
        val forged=noop.copy(ledger=noop.ledger.copy(entries=listOf(noop.ledger.entries.single().copy(evidenceCanonical=canonicalJson("{\"removedLegacyKeys\":[\"development:target\"]}")))),outbox=emptyList())
        assertEquals(1,unresolvedLegacyOwnership(store(forged).let { load(it) },catalog()).size)
    }
    @Test fun forged_inverse_unrelated_hp_change_is_blocked_and_does_not_refund() = run {
        val backing=store();buy(backing);val saved=load(backing)
        val entry=saved.ledger.entries.single();val root=com.furybook.core.json.JsonReader(entry.evidenceCanonical).read() as com.furybook.core.json.JsonValue.Obj
        val proof=root.values.getValue("undoProjection") as com.furybook.core.json.JsonValue.Obj
        val forgedBefore=com.furybook.core.json.JsonReader(acquisitionProjectionCanonical(state().copy(character=state().character.copy(hpCurrent=99)))).read()
        val changed=com.furybook.core.json.JsonValue.Obj(root.values+("undoProjection" to com.furybook.core.json.JsonValue.Obj(proof.values+("before" to forgedBefore))))
        val forged=saved.copy(ledger=saved.ledger.copy(entries=listOf(entry.copy(evidenceCanonical=canonicalJson(com.furybook.core.json.jsonStringify(changed))))),outbox=emptyList())
        val restarted=store(forged);assertIs<ApplyResult.Blocked>(AcquisitionUndo(restarted).undo("hero","u",1));assertEquals(forged,load(restarted))
    }
    @Test fun undo_storage_failure_and_reply_loss_have_no_double_refund() = run {
        val backing=store();buy(backing);val before=load(backing)
        val fail=object:AcquisitionStore by backing {override suspend fun compareAndSet(characterId:String,expectedStorageRevision:Long,next:StoredCharacter)=CommitResult.StorageFailure("full")}
        assertIs<ApplyResult.StorageFailure>(AcquisitionUndo(fail).undo("hero","u",1));assertEquals(before,load(backing))
        val lost=object:AcquisitionStore by backing {override suspend fun compareAndSet(characterId:String,expectedStorageRevision:Long,next:StoredCharacter):CommitResult { backing.compareAndSet(characterId,expectedStorageRevision,next);error("lost") }}
        assertIs<ApplyResult.StorageFailure>(AcquisitionUndo(lost).undo("hero","u",1));assertIs<ApplyResult.AlreadyApplied>(AcquisitionUndo(backing).undo("hero","u",1));assertEquals(Amount(100,0),balance(load(backing).ledger))
    }
    @Test fun metadata_race_retries_but_gameplay_race_is_stale() = run {
        for(gameplay in listOf(false,true)) {
            val backing=store();var race=true
            val competing=object:AcquisitionStore by backing {override suspend fun compareAndSet(characterId:String,expectedStorageRevision:Long,next:StoredCharacter):CommitResult {
                if(race) {race=false;val before=load(backing)
                    if(gameplay) coordinator(backing).mutate(CanonicalMutation("other","hero",0,MutationPayload.Grant(Amount(1,0)),"other award"))
                    else backing.compareAndSet("hero",0,before.copy(storageRevision=1,syncMetadata=SyncMetadata(8))) }
                return backing.compareAndSet(characterId,expectedStorageRevision,next)
            }}
            val result=coordinator(competing).mutate(CanonicalMutation("g","hero",0,MutationPayload.Grant(Amount(5,0)),"award"))
            if(gameplay) {assertIs<MutationResult.Stale>(result);assertEquals(Amount(101,0),balance(load(backing).ledger))}
            else {assertIs<MutationResult.Applied>(result);assertEquals(8L,load(backing).outbox.single().parentServerRevision)}
        }
    }

    @Test fun unmapped_school_resolves_only_at_original_rank_and_undo_restores_unresolved() = run {
        val character=DublCharacter("hero",magic=CharacterMagic(schools=listOf(MagicSchool("Ограждение",3))))
        val raw=SnapshotCodec.encode(AppSnapshot(listOf(character),"hero"));val origin=LegacyOrigin("hero",11,"school-old",raw,listOf("dubl-3.69"))
        val pending=assertIs<MigrationResult.NeedsBalanceConfirmation>(prepareLegacy(raw,origin,null)).pending
        val before=confirmLegacyBalance(pending,Amount(100,0),"balance")
        val schoolRef=DefinitionRef("dubl-3.69","magic-school:ограждение")
        val school=definition("school",AcquisitionKind.SCHOOL).copy(ref=schoolRef)
        val cat=AcquisitionCatalog(mapOf(schoolRef to school),"c","r");val key=legacySchoolOwnershipKey("Ограждение")
        val legacyOrigin=AcquisitionOrigin.Legacy("hero","11","school-old",origin.sourceFingerprint,key)
        val payload=MutationPayload.ConfirmLegacyParameter(legacyInstanceId(schoolRef,legacyOrigin),emptyMap(),LegacyOwnershipResolution(key,schoolRef,3))
        val backing=store(before);assertIs<MutationResult.Applied>(CharacterMutationCoordinator(backing,{cat},{"id"}).mutate(CanonicalMutation("map","hero",0,payload,"mapping")))
        assertEquals(3,load(backing).instances.single().rank);assertEquals(Amount(100,0),balance(load(backing).ledger));assertTrue(unresolvedLegacyOwnership(load(backing),cat).isEmpty())
        assertIs<ApplyResult.Applied>(AcquisitionUndo(backing).undo("hero","u",1));assertEquals(before.character,load(backing).character);assertEquals(1,unresolvedLegacyOwnership(load(backing),cat).size)
    }
    @Test fun legacy_unknown_parameter_confirmation_is_validated_and_undo_is_exact() = run {
        val original=legacy();val key="knowledge"
        val d=definition().copy(repeatPolicy=RepeatPolicy.DistinctParameter(setOf(key)),parameterDefinitions=listOf(ParameterDefinition(key,ParameterDomain.StableIds(setOf("lore","history")))))
        val cat=AcquisitionCatalog(mapOf(ref() to d),"c","r");val backing=store(original);val c=CharacterMutationCoordinator(backing,{cat},{"id"})
        val base=resolution(original)
        assertIs<MutationResult.Blocked>(c.mutate(CanonicalMutation("bad","hero",0,MutationPayload.ConfirmLegacyParameter(base.instanceId,mapOf(key to "unknown"),base.resolution),"confirm")))
        assertIs<MutationResult.Applied>(c.mutate(CanonicalMutation("map","hero",0,MutationPayload.ConfirmLegacyParameter(base.instanceId,mapOf(key to "lore"),base.resolution),"confirm")))
        assertEquals(mapOf(key to "lore"),load(backing).instances.single().parameters)
        assertIs<MutationResult.Blocked>(c.mutate(CanonicalMutation("rewrite","hero",1,MutationPayload.ConfirmLegacyParameter(base.instanceId,mapOf(key to "history")),"rewrite")))
        assertIs<ApplyResult.Applied>(AcquisitionUndo(backing).undo("hero","u",1));assertEquals(original.instances,load(backing).instances)
    }
    @Test fun ordered_purchase_undo_preserves_unrelated_manual_values() = run {
        val before=state().copy(character=state().character.copy(hpCurrent=77,attributes=state().character.attributes+(AttributeId.STRENGTH to AttributeValue(15))))
        val backing=store(before);buy(backing,listOf(AcquisitionTarget(ref(),targetRank=1),AcquisitionTarget(ref(),targetRank=2)))
        assertEquals(Amount(60,0),balance(load(backing).ledger));assertIs<ApplyResult.Applied>(AcquisitionUndo(backing).undo("hero","u",1))
        assertEquals(before.character,load(backing).character);assertEquals(Amount(100,0),balance(load(backing).ledger))
    }

    @Test fun pure_acquisition_noop_does_not_block_previous_purchase_undo() = run {
        val backing=store();val purchase=buy(backing);val service=AcquisitionService(backing,::catalog,{"id"})
        val p=service.preview(purchase.request);val noop=ConfirmAcquisition("noop","hero",purchase.request,1,p.previewToken)
        assertIs<ApplyResult.Applied>(service.apply(noop));assertEquals(Amount(80,0),balance(load(backing).ledger))
        assertIs<ApplyResult.Applied>(AcquisitionUndo(backing).undo("hero","u",load(backing).revision))
        assertEquals(Amount(100,0),balance(load(backing).ledger));assertEquals(state().character,load(backing).character)
        assertIs<ApplyResult.AlreadyUndone>(service.apply(purchase));assertIs<ApplyResult.AlreadyApplied>(service.apply(noop))
    }
    @Test fun detached_resource_edits_keep_bounds_without_normalizing_unrelated_holdings() = run {
        val before=state().copy(character=state().character.copy(attributes=state().character.attributes+(AttributeId.STRENGTH to AttributeValue(15)),
            development=mapOf("untouched-zero" to OwnedDevelopment(0)),manaMaximumOverride=5,manaEnabled=true,chiEnabled=true))
        val backing=store(before)
        assertIs<MutationResult.Applied>(coordinator(backing).stageNonFinancial("resources","hero",0) {
            character.changeHp(100);character.changeEndurance(100);magic.changeMana(100);development.changeChi(100)
        })
        val after=load(backing).character
        assertEquals(10,after.hpCurrent);assertEquals(before.character.enduranceMaximum,after.enduranceCurrent)
        assertEquals(5,after.manaCurrent);assertEquals(before.character.chiMaximum,after.chiCurrent)
        assertEquals(before.character.attributes,after.attributes);assertEquals(before.character.development,after.development)
    }

    @Test fun multiple_acquisition_noops_restart_ack_and_undo_keep_all_operation_identities() = run {
        val backing=store();val purchase=buy(backing);val service=AcquisitionService(backing,::catalog,{"id"})
        val noops=(1..2).map { index -> val p=service.preview(purchase.request)
            ConfirmAcquisition("noop-$index","hero",purchase.request,p.baseRevision,p.previewToken).also { assertIs<ApplyResult.Applied>(service.apply(it)) } }
        val restarted=store(load(backing));val tip=load(restarted)
        assertEquals(CommitResult.Committed,restarted.compareAndSet("hero",tip.storageRevision,tip.copy(outbox=emptyList(),storageRevision=tip.storageRevision+1,syncMetadata=SyncMetadata(10))))
        assertIs<ApplyResult.Applied>(AcquisitionUndo(restarted).undo("hero","u",3));val saved=load(restarted)
        assertEquals(Amount(100,0),balance(saved.ledger));assertEquals(4,saved.ledger.entries.size);assertEquals(4L,saved.revision);assertEquals(5L,saved.storageRevision)
        assertEquals(10L,saved.outbox.single().parentServerRevision);assertEquals(listOf("op1","noop-1","noop-2","u"),saved.receipts.map { it.operationId })
        assertIs<ApplyResult.AlreadyApplied>(AcquisitionUndo(restarted).undo("hero","u",3))
        noops.forEach { assertIs<ApplyResult.AlreadyApplied>(AcquisitionService(restarted,::catalog,{"id"}).apply(it)) }
        assertIs<ApplyResult.AlreadyUndone>(AcquisitionService(restarted,::catalog,{"id"}).apply(purchase))
    }
    @Test fun nonacquisition_zero_delta_and_malformed_noop_are_undo_barriers() = run {
        for(malformed in listOf(false,true)) {
            val backing=store();val purchase=buy(backing)
            if(!malformed) assertIs<MutationResult.Applied>(coordinator(backing).mutate(CanonicalMutation("noop","hero",1,
                MutationPayload.NonFinancial(CharacterPatch(load(backing).character)))))
            else { val service=AcquisitionService(backing,::catalog,{"id"});val p=service.preview(purchase.request)
                assertIs<ApplyResult.Applied>(service.apply(ConfirmAcquisition("noop","hero",purchase.request,1,p.previewToken))) }
            val saved=load(backing)
            val candidate=if(malformed) saved.copy(ledger=saved.ledger.copy(entries=saved.ledger.entries.dropLast(1)+saved.ledger.entries.last().copy(evidenceCanonical="{}")),outbox=emptyList()) else saved
            val restarted=store(candidate)
            assertIs<ApplyResult.Blocked>(AcquisitionUndo(restarted).undo("hero","u",2));assertEquals(candidate,load(restarted))
        }
    }
    @Test fun canonical_facade_staging_and_undo_never_write_legacy_session() = run {
        val backing=store();var writes=0
        val old=com.furybook.dubl.data.InMemoryCharacterStore(AppSnapshot(listOf(DublCharacter("legacy")),"legacy"))
        val legacyStore=object:com.furybook.dubl.data.CharacterStore by old { override fun save(snapshot:AppSnapshot) { writes++;old.save(snapshot) } }
        val extras=object:com.furybook.dubl.data.CharacterExtrasStore {
            override fun load(characterId:String)=CharacterSheetExtras()
            override fun save(characterId:String,extras:CharacterSheetExtras) { writes++ }
        }
        val app=com.furybook.dubl.application.DublApplication(legacyStore,extras,{"new-row"},mutationCoordinator=coordinator(backing),acquisitionUndo=AcquisitionUndo(backing))
        assertIs<MutationResult.Applied>(app.mutateCanonical(CanonicalMutation("g","hero",0,MutationPayload.Grant(Amount(2,0)),"award")))
        assertIs<ApplyResult.Applied>(app.undoCanonical("hero","u",1))
        val prepared=app.prepareNonFinancial("note","hero",2) { sheet.addNote("kept") }
        assertIs<MutationResult.Applied>(app.mutateCanonical(prepared));assertEquals("kept",load(backing).extras.noteEntries.single().title)
        assertEquals(0,writes);assertEquals("legacy",app.active.id);assertFalse(app.canUndo)
    }

    @Test fun shared_undo_candidate_crosses_noops_and_reports_current_revision_without_writes() = run {
        val backing=store();val purchase=buy(backing);val service=AcquisitionService(backing,::catalog,{"id"})
        repeat(2) { index -> val p=service.preview(purchase.request);assertIs<ApplyResult.Applied>(service.apply(ConfirmAcquisition("noop-$index","hero",purchase.request,p.baseRevision,p.previewToken))) }
        val before=load(backing);val available=assertIs<UndoEligibility.Available>(AcquisitionUndo(backing).eligible("hero"))
        assertEquals("op1",available.operationId);assertEquals(3L,available.expectedRevision);assertEquals(Amount(20,0),available.amountDelta);assertEquals(LedgerEntryType.Purchase,available.type)
        assertEquals(before,load(backing))
        assertIs<MutationResult.Applied>(coordinator(backing).mutate(CanonicalMutation("hp","hero",3,MutationPayload.NonFinancial(CharacterPatch(before.character.copy(hpCurrent=1))))))
        assertIs<UndoEligibility.Unavailable>(AcquisitionUndo(backing).eligible("hero"))
    }
    @Test fun signed_grant_debt_and_xp_ap_purchase_undo_use_actual_recorded_amounts() = run {
        val backing=store();assertIs<MutationResult.Applied>(coordinator(backing).mutate(CanonicalMutation("debt","hero",0,MutationPayload.Grant(Amount(-110,0)),"explicit deduction")))
        assertEquals(Amount(-10,0),balance(load(backing).ledger));assertIs<ApplyResult.Applied>(AcquisitionUndo(backing).undo("hero","u",1));assertEquals(Amount(100,0),balance(load(backing).ledger))
        val initial=state().copy(ledger=state().ledger.copy(opening=state().ledger.opening.copy(balance=Amount(100,5))))
        val paid=store(initial);val cat=AcquisitionCatalog(mapOf(ref() to definition().copy(priceRule=PriceRule.Fixed(20,2,source))),"cost-old","r")
        val service=AcquisitionService(paid,{cat},{"id"});val request=AcquisitionRequest("hero",listOf(AcquisitionTarget(ref(),targetRank=1)));val plan=service.preview(request)
        assertIs<ApplyResult.Applied>(service.apply(ConfirmAcquisition("purchase","hero",request,0,plan.previewToken)));assertEquals(Amount(80,3),balance(load(paid).ledger))
        assertIs<ApplyResult.Applied>(AcquisitionUndo(paid).undo("hero","u",1));assertEquals(Amount(100,5),balance(load(paid).ledger))
    }

    @Test fun detached_staging_cannot_alias_cached_character_or_extras_collections() = run {
        val cached=state().copy(character=state().character.copy(attributes=state().character.attributes.toMutableMap()),
            extras=CharacterSheetExtras(preferredSkillAttributes=mutableMapOf("skill" to AttributeId.STRENGTH)))
        val original=encodeEnvelope(cached)
        val backing=object:AcquisitionStore {
            override suspend fun load(characterId:String)=LoadResult.Loaded(cached)
            override suspend fun compareAndSet(characterId:String,expectedStorageRevision:Long,next:StoredCharacter):CommitResult = error("Hidden acquisition reached storage")
        }
        val c=coordinator(backing)
        val command=c.prepareNonFinancial("alias","hero",0) {
            // A codec may return immutable singleton maps. Either rejecting the edit or mutating
            // only the detached copy is safe; the cached source must remain byte-identical.
            try { (active.attributes as MutableMap<AttributeId,AttributeValue>)[AttributeId.STRENGTH]=AttributeValue(4) }
            catch (_: UnsupportedOperationException) { }
            try { (activeExtras.preferredSkillAttributes as MutableMap<String,AttributeId>)["skill"]=AttributeId.INTELLIGENCE }
            catch (_: UnsupportedOperationException) { }
        }
        assertEquals(original,encodeEnvelope(cached))
        val staged=(command.payload as MutationPayload.NonFinancial).characterPatch.apply(cached)
        if(staged.character.attributes!=cached.character.attributes) assertIs<MutationResult.Blocked>(c.mutate(command))
        assertEquals(original,encodeEnvelope(cached))
    }
    @Test fun detached_generated_ids_are_captured_once_for_retry() = run {
        val backing=store();var ids=0;val c=CharacterMutationCoordinator(backing,::catalog,{"generated-${++ids}"})
        val command=c.prepareNonFinancial("condition","hero",0) { sheet.addCustomCondition("test condition") }
        assertEquals(1,ids);assertIs<MutationResult.Applied>(c.mutate(command));assertIs<MutationResult.AlreadyApplied>(c.mutate(command))
        assertEquals(1,ids);assertEquals("generated-1",load(backing).extras.customConditions.single().id)
    }

    @Test fun wrong_origin_in_manual_removal_evidence_does_not_resolve_legacy_ownership() = run {
        val before=legacy();val backing=store(before);val c=coordinator(backing)
        assertIs<MutationResult.Applied>(c.mutate(CanonicalMutation("map","hero",0,resolution(before),"mapping")))
        val mapped=load(backing)
        assertIs<MutationResult.Applied>(c.mutate(CanonicalMutation("remove","hero",1,MutationPayload.ManualCorrection(CharacterPatch(mapped.character.copy(development=emptyMap()),emptyList()),Amount.ZERO),"remove")))
        val removed=load(backing);val entry=removed.ledger.entries.last()
        val root=com.furybook.core.json.JsonReader(entry.evidenceCanonical).read() as com.furybook.core.json.JsonValue.Obj
        val projection=root.values.getValue("before") as com.furybook.core.json.JsonValue.Obj
        val instance=(projection.values.getValue("instances") as com.furybook.core.json.JsonValue.Arr).values.single() as com.furybook.core.json.JsonValue.Obj
        val origin=instance.values.getValue("origin") as com.furybook.core.json.JsonValue.Obj
        val wrongOrigin=com.furybook.core.json.JsonValue.Obj(origin.values+("sourceFingerprint" to com.furybook.core.json.jsonString("wrong-source")))
        val wrongInstance=com.furybook.core.json.JsonValue.Obj(instance.values+("origin" to wrongOrigin))
        val wrongProjection=com.furybook.core.json.JsonValue.Obj(projection.values+("instances" to com.furybook.core.json.JsonValue.Arr(listOf(wrongInstance))))
        val wrongEvidence=canonicalJson(com.furybook.core.json.jsonStringify(com.furybook.core.json.JsonValue.Obj(root.values+("before" to wrongProjection))))
        val forged=removed.copy(ledger=removed.ledger.copy(entries=removed.ledger.entries.dropLast(1)+entry.copy(evidenceCanonical=wrongEvidence)),outbox=emptyList())
        assertEquals(1,unresolvedLegacyOwnership(forged,catalog()).size)
        assertIs<UndoEligibility.Unavailable>(AcquisitionUndo(store(forged)).eligible("hero"))
    }
    @Test fun undo_candidate_preserves_unavailable_load_outcomes_without_writes() = run {
        val outcomes=listOf<LoadResult>(LoadResult.Missing("hero"),LoadResult.MigrationRequired("original"),LoadResult.Corrupt("original","bad"),LoadResult.Unsupported("original","newer"))
        for(outcome in outcomes) {
            val backing=object:AcquisitionStore {
                override suspend fun load(characterId:String)=outcome
                override suspend fun compareAndSet(characterId:String,expectedStorageRevision:Long,next:StoredCharacter):CommitResult=error("Unexpected write")
            }
            assertEquals(outcome,assertIs<UndoEligibility.Unavailable>(AcquisitionUndo(backing).eligible("hero")).loadResult)
        }
    }
}
