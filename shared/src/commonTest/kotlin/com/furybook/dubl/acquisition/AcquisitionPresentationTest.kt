package com.furybook.dubl.acquisition

import com.furybook.dubl.application.*
import com.furybook.dubl.data.*
import com.furybook.dubl.model.*
import kotlinx.coroutines.*
import kotlin.test.*
import kotlin.coroutines.*

class AcquisitionPresentationTest {
    private fun run(block:suspend()->Unit) {
        var completed:Result<Unit>?=null
        block.startCoroutine(object:Continuation<Unit> { override val context=EmptyCoroutineContext;override fun resumeWith(result:Result<Unit>) { completed=result } })
        checkNotNull(completed) { "Test coroutine did not complete" }.getOrThrow()
    }
    private val source = RuleSourceRef("fixture", "1", "Explicit price and admission")
    private val ref = DefinitionRef("test", "internal-id")
    private fun catalog(unknown: Boolean = false) = AcquisitionCatalog(mapOf(ref to AcquisitionDefinition(ref,
        AcquisitionKind.DEVELOPMENT, 3, RepeatPolicy.Single,
        RequirementNode.AllOf("none", source, emptyList(), NoRequirementsEvidence(source,"No requirements")),
        if(unknown) PriceRule.Unknown("Unreviewed source",source) else PriceRule.PerRank(20,0,source),
        SourceStatus.Confirmed,"Сопротивление",source)),"catalog","rules")
    private fun state() = StoredCharacter(DublCharacter("hero"),CharacterSheetExtras(),emptyList(),
        Ledger(Opening("opening","original",Amount(100,2),RequirementNode.CreationPhase.Phase.Creating,OpeningProvenance.NewCharacter),emptyList()),
        emptyList(),emptyList(),SyncMetadata(0),0,0,RequirementNode.CreationPhase.Phase.Creating)
    private fun store() = InMemoryAcquisitionStore(mapOf("hero" to encodeEnvelope(state())))
    private val scope = AcquisitionScope("store","hero",0,0,0)
    private fun request() = AcquisitionRequest("hero",listOf(AcquisitionTarget(ref,"holding",1)))
    private fun app(store: AcquisitionStore, catalog: AcquisitionCatalog, id:()->String = { "id" }):DublApplication = DublApplication(
        object:CharacterStore { override fun load()=AppSnapshot(listOf(state().character),"hero");override fun save(snapshot:AppSnapshot)=error("legacy write") },
        object:CharacterExtrasStore { override fun load(characterId:String)=CharacterSheetExtras();override fun save(characterId:String,extras:CharacterSheetExtras)=error("legacy extras") },id,
        acquisitionService=AcquisitionService(store,{catalog},id),mutationCoordinator=CharacterMutationCoordinator(store,{catalog},id),acquisitionUndo=AcquisitionUndo(store),characterCreator=AcquisitionCharacterCreator(store,{catalog},{AcquisitionCapabilities(true,false,true)}))
    private fun session(store:AcquisitionStore, catalog:AcquisitionCatalog=catalog(), id:()->String={"id"}, current:(AcquisitionScope)->Boolean={true},publish:suspend(AcquisitionScope,StoredCharacter)->Unit={_,_->}) =
        AcquisitionPresentationSession(ApplicationAcquisitionCommandPort(app(store,catalog,id),store,{catalog},{AcquisitionCapabilities(true,false,true)},current,publish),id)

    @Test fun quote_confirm_reopen_and_duplicate_tap_use_one_saved_price_and_id() = run {
        val store=store();var ids=0;val session=session(store,id={"op-${++ids}"})
        val ready=session.open(scope,request());assertEquals(AcquisitionUiPhase.Ready,ready.phase)
        assertEquals(Amount(20,0),assertIs<AcquisitionDisplayedPrice.Known>(ready.price).amount)
        assertEquals(0,ids)
        val committed=session.confirm(ready.key);assertEquals(AcquisitionUiPhase.Committed,committed.phase)
        session.confirm(ready.key);session.dismiss();session.reopen()
        assertEquals(1,ids);assertEquals(Amount(20,0),committed.receipt?.cost)
        assertEquals(1,assertIs<LoadResult.Loaded>(store.load("hero")).state.receipts.size)
        assertFalse(ready.rows.any { it.label.contains("internal-id") })
    }
    @Test fun unknown_is_not_free_and_cancel_does_not_allocate() = run {
        var ids=0;val s=session(store(),catalog(true),{ids++;"op"})
        val model=s.open(scope,request());assertEquals(AcquisitionUiPhase.Blocked,model.phase)
        assertEquals("Цена не определена",assertIs<AcquisitionDisplayedPrice.Unknown>(model.price).message)
        s.dismiss();assertEquals(0,ids)
    }
    @Test fun lost_reply_reconciliation_retains_the_same_confirmed_intent() = run {
        val backing=store();var writes=0;var ids=0
        val faulty=object:AcquisitionStore by backing { override suspend fun compareAndSet(characterId:String,expectedStorageRevision:Long,next:StoredCharacter):CommitResult {
            writes++;backing.compareAndSet(characterId,expectedStorageRevision,next);return CommitResult.StorageFailure("lost reply")
        }}
        val s=session(faulty,id={"op-${++ids}"});val key=s.open(scope,request()).key
        assertEquals(AcquisitionUiPhase.Failed,s.confirm(key).phase)
        s.dismiss();s.reopen();assertEquals(AcquisitionUiPhase.Committed,s.reconcilePending().phase)
        assertEquals(1,writes);assertEquals(1,ids)
    }
    @Test fun acknowledged_reload_failure_is_committed_not_a_second_purchase() = run {
        val store=store();val s=session(store,publish={_,_->error("view unavailable")})
        val model=s.confirm(s.open(scope,request()).key)
        assertEquals(AcquisitionUiPhase.Committed,model.phase);assertNotNull(model.receipt)
        assertNotNull(model.viewRefreshError);s.retrySameCommand()
        assertEquals(1,assertIs<LoadResult.Loaded>(store.load("hero")).state.receipts.size)
    }
    @Test fun stale_scope_cannot_redirect_the_captured_character() = run {
        val store=store();var current=true;val s=session(store,current={current})
        val key=s.open(scope,request()).key;current=false
        assertEquals(AcquisitionUiPhase.Stale,s.confirm(key).phase)
        assertTrue(assertIs<LoadResult.Loaded>(store.load("hero")).state.receipts.isEmpty())
    }
    @Test fun manual_reason_is_required_and_grant_completion_and_undo_are_commands() = run {
        val store=store();var ids=0;val s=session(store,id={"op-${++ids}"})
        assertEquals(AcquisitionUiPhase.Blocked,s.prepareGrant(scope,Amount(5,0)," ").phase)
        var review=s.prepareGrant(scope,Amount(5,0),"Session award")
        assertEquals(AcquisitionUiPhase.Committed,s.confirm(review.key).phase)
        review=s.prepareCompleteCreation(scope)
        assertEquals(AcquisitionUiPhase.Committed,s.confirm(review.key).phase)
        assertEquals(Amount(105,2),balance(assertIs<LoadResult.Loaded>(store.load("hero")).state.ledger))
        review=s.prepareUndo(scope);assertEquals(AcquisitionUiPhase.Ready,review.phase)
        assertEquals(AcquisitionUiPhase.Committed,s.confirm(review.key).phase)
    }
    @Test fun injected_canonical_application_blocks_legacy_writes_before_memory_and_ids() = run {
        var ids=0;val a=app(store(),catalog()){ids++;"new"};val before=a.snapshot
        for(action in listOf<()->Unit>({a.character.setName("changed")},{a.character.createCharacter()},
            {a.skills.addSpecialized("knowledge_template","Geography")},{a.sheet.setPortrait("photo")},
            {a.character.changeAttribute(AttributeId.STRENGTH,1)},{a.magic.setManaRank(1)},{a.undoLast()})) {
            assertFailsWith<CanonicalWriteRequired>{action()};assertEquals(before,a.snapshot);assertEquals(0,ids)
        }
        val command=a.prepareNonFinancial("edit","hero",0){character.setName("Allowed")}
        assertIs<MutationResult.Applied>(a.mutateCanonical(command))
    }
    @Test fun every_direct_kind_routes_only_to_quote_and_decrease_needs_manual() {
        val base=state();val catalog=catalog()
        assertIs<AcquisitionRouteResult.QuoteRequest>(AcquisitionCommandRouting.request(AcquisitionIntent.Target(AcquisitionKind.DEVELOPMENT,AcquisitionTarget(ref,"new",1)),scope,base,catalog))
        assertIs<AcquisitionRouteResult.Blocked>(AcquisitionCommandRouting.request(AcquisitionIntent.Target(AcquisitionKind.SPELL,AcquisitionTarget(ref,"new",1)),scope,base,catalog))
        assertIs<AcquisitionRouteResult.ManualReviewRequired>(AcquisitionCommandRouting.request(AcquisitionIntent.Target(AcquisitionKind.DEVELOPMENT,AcquisitionTarget(ref,"old",-1)),scope,base,catalog))
    }
    @Test fun readonly_cards_and_restart_history_do_not_replace_retained_session() = run {
        val store=store();val c=catalog();var ids=0
        val control=AcquisitionController(ApplicationAcquisitionCommandPort(app(store,c),store,{c},{AcquisitionCapabilities(true,false,true)},{true}),{scope},{"op-${++ids}"})
        val ready=control.preview(request());control.confirm(ready.key)
        val savedKey=control.model!!.key;val savedId=control.retainedOperationId
        val card=control.quoteCard(request())
        assertEquals(savedKey,control.model!!.key);assertEquals(savedId,control.retainedOperationId)
        assertNotEquals(savedKey,card.key)
        val read=control.readCanonicalView()
        assertEquals(Amount(80,2),read.balance);assertEquals(1,read.history.size);assertEquals(1,read.instances.size)
        val disabled=AcquisitionController(ApplicationAcquisitionCommandPort(app(store,c),store,{c},{AcquisitionCapabilities(false,false,true)},{true}),{scope},{"never"})
        val view=disabled.quoteCard(request())
        assertIs<AcquisitionDisplayedPrice.Known>(view.price);assertFalse(view.canConfirm)
    }
    @Test fun instance_choice_preserves_request_and_readable_actionable_candidates() = run {
        val base=state().copy(instances=listOf(AcquisitionInstance("secret-a",ref,1,emptyMap(),null,AcquisitionOrigin.New),AcquisitionInstance("secret-b",ref,1,emptyMap(),null,AcquisitionOrigin.New)))
        val store=InMemoryAcquisitionStore(mapOf("hero" to encodeEnvelope(base)));val c=catalog()
        val control=AcquisitionController(ApplicationAcquisitionCommandPort(app(store,c),store,{c},{AcquisitionCapabilities(true,false,true)},{true}),{scope},{"new-id"})
        val view=control.preview(AcquisitionIntent.development(AcquisitionTarget(ref,targetRank=2)))
        assertEquals(AcquisitionUiPhase.ChoiceRequired,view.phase);assertNotNull(view.request)
        assertEquals(setOf("secret-a","secret-b"),view.selections.single().instances.mapNotNull { it.instanceId }.toSet())
        assertTrue(view.selections.single().instances.all { !it.label.contains("secret-") })
    }
    @Test fun concurrent_confirm_and_selection_change_preserve_acknowledged_original_operation() = run {
        val backing=store();val entered=CompletableDeferred<Unit>();val release=CompletableDeferred<Unit>();var writes=0;var ids=0;var current=true;var publications=0
        val delayed=object:AcquisitionStore by backing { override suspend fun compareAndSet(characterId:String,expectedStorageRevision:Long,next:StoredCharacter):CommitResult {
            writes++;entered.complete(Unit);release.await();return backing.compareAndSet(characterId,expectedStorageRevision,next)
        }}
        val s=session(delayed,id={"op-${++ids}"},current={current},publish={_,_->publications++})
        val ready=s.open(scope,request())
        coroutineScope {
            val one=async(Dispatchers.Unconfined,start=CoroutineStart.UNDISPATCHED){s.confirm(ready.key)}
            entered.await()
            assertEquals(AcquisitionUiPhase.Applying,s.model?.phase)
            val two=async(Dispatchers.Unconfined,start=CoroutineStart.UNDISPATCHED){s.confirm(ready.key)}
            current=false;release.complete(Unit)
            assertEquals(AcquisitionUiPhase.Committed,one.await().phase)
            assertEquals(AcquisitionUiPhase.Committed,two.await().phase)
        }
        assertEquals(1,writes);assertEquals(1,ids);assertEquals(0,publications)
        assertNotNull(s.model?.viewRefreshError)
        assertEquals("hero",s.model?.scope?.characterId)
    }
    @Test fun stale_review_requires_refresh_and_a_new_confirmation_identity()=run {
        val backing=store();var ids=0;val c=catalog();val s=session(backing,c,id={"op-${++ids}"})
        val first=s.open(scope,request())
        val award=CanonicalMutation("award","hero",0,MutationPayload.Grant(Amount(5,0)),"Award")
        assertIs<MutationResult.Applied>(CharacterMutationCoordinator(backing,{c},{"unused"}).mutate(award))
        val stale=s.confirm(first.key);assertEquals(AcquisitionUiPhase.Stale,stale.phase)
        assertEquals(1,ids);assertNotEquals(first.key,stale.key)
        s.confirm(first.key);assertEquals(1,ids)
        val refreshed=s.refresh(stale.key)
        assertEquals(AcquisitionUiPhase.Committed,s.confirm(refreshed.key).phase)
        assertEquals(2,ids);assertEquals(Amount(85,2),balance(assertIs<LoadResult.Loaded>(backing.load("hero")).state.ledger))
    }
    @Test fun nonfinancial_callback_is_staged_once_and_financial_edits_never_fall_back_to_manual()=run {
        val backing=store();var calls=0;var ids=0
        val uncertain=object:AcquisitionStore by backing { override suspend fun compareAndSet(characterId:String,expectedStorageRevision:Long,next:StoredCharacter):CommitResult {
            backing.compareAndSet(characterId,expectedStorageRevision,next);return CommitResult.StorageFailure("lost reply")
        }}
        val s=session(uncertain,id={"op-${++ids}"})
        val edit=s.prepareNonFinancial(scope){calls++;character.setName("Renamed")}
        assertEquals(AcquisitionUiPhase.Failed,s.confirm(edit.key).phase)
        assertEquals(AcquisitionUiPhase.Committed,s.reconcilePending().phase)
        assertEquals(1,calls);assertEquals(1,ids)
        val unsafe=s.prepareNonFinancial(scope){character.changeAttribute(AttributeId.STRENGTH,1)}
        assertEquals(AcquisitionUiPhase.Blocked,s.confirm(unsafe.key).phase)
        val saved=assertIs<LoadResult.Loaded>(backing.load("hero")).state
        assertEquals("Renamed",saved.character.name);assertEquals(0,saved.character.attributes.getValue(AttributeId.STRENGTH).base)
        assertEquals(1,saved.receipts.size)
    }
    @Test fun creation_facade_allocates_once_and_binds_new_target_to_original_view()=run {
        val backing=store();var ids=0;var published:AcquisitionScope?=null
        val policySource=RuleSourceRef("policy","1","Once only funding",resolutionId="UR_NEW_CHARACTER_INITIAL_AP_PER_1000_XP",resolutionRevision="rules")
        val c=catalog().copy(creationFundingPolicy=CreationFundingPolicy("UR_NEW_CHARACTER_INITIAL_AP_PER_1000_XP",1000,policySource))
        val a=app(backing,c){"new-${++ids}"}
        val control=AcquisitionController(ApplicationAcquisitionCommandPort(a,backing,{c},{AcquisitionCapabilities(true,false,true)},{it.viewCharacterId=="hero"},{value,_->published=value}),{scope},{"unexpected"})
        val review=control.reviewNewCharacterFunding(2500)
        assertEquals(0,ids);assertEquals(2L,review.funding?.initialAP)
        assertEquals(AcquisitionUiPhase.Committed,control.confirm(review.key).phase)
        control.confirm(review.key);control.dismiss();control.reopen();control.reconcilePending()
        assertEquals(2,ids);assertEquals("hero",published?.viewCharacterId);assertEquals("new-2",published?.characterId)
        val saved=assertIs<LoadResult.Loaded>(backing.load("new-2")).state
        assertEquals(Amount(2500,2),balance(saved.ledger));assertTrue(saved.receipts.isEmpty());assertTrue(saved.outbox.isEmpty())
    }
    @Test fun response_for_another_operation_is_conflict_not_success()=run {
        val backing=store();val c=catalog()
        val actual=ApplicationAcquisitionCommandPort(app(backing,c),backing,{c},{AcquisitionCapabilities(true,false,true)},{true})
        val foreign=object:AcquisitionCommandPort by actual {
            override suspend fun apply(command:ConfirmAcquisition)=ApplyResult.AlreadyApplied(Receipt("other","foreign",SavedAcquisitionResult(Amount(20,0),Amount(80,2),emptyList()),ReceiptStatus.Applied,0,1))
        }
        val s=AcquisitionPresentationSession(foreign,{"ours"})
        assertEquals(AcquisitionUiPhase.Conflict,s.confirm(s.open(scope,request()).key).phase)
        assertTrue(assertIs<LoadResult.Loaded>(backing.load("hero")).state.receipts.isEmpty())
    }
    @Test fun later_read_failure_cannot_downgrade_a_known_commit()=run {
        val backing=store();var failed=false
        val late=object:AcquisitionStore by backing {
            override suspend fun load(characterId:String):LoadResult { if(failed) error("temporarily unreadable");return backing.load(characterId) }
            override suspend fun compareAndSet(characterId:String,expectedStorageRevision:Long,next:StoredCharacter):CommitResult {
                val result=backing.compareAndSet(characterId,expectedStorageRevision,next);failed=true;return result
            }
        }
        val s=session(late);val first=s.confirm(s.open(scope,request()).key)
        assertEquals(AcquisitionUiPhase.Committed,first.phase);assertNotNull(first.receipt)
        val checked=s.reconcilePending();assertEquals(AcquisitionUiPhase.Committed,checked.phase);assertEquals(first.receipt,checked.receipt)
        assertNotNull(checked.viewRefreshError)
    }
    @Test fun catalog_change_between_preview_and_presentation_requires_new_review()=run {
        val backing=store();val c=catalog();var shown=c
        val actual=ApplicationAcquisitionCommandPort(app(backing,c),backing,{c},{AcquisitionCapabilities(true,false,true)},{true})
        val drift=object:AcquisitionCommandPort by actual {
            override val catalog get()=shown
            override suspend fun preview(request:AcquisitionRequest):AcquisitionPlan { val result=actual.preview(request);shown=c.copy(catalogRevision="later");return result }
        }
        val s=AcquisitionPresentationSession(drift,{"op"})
        assertEquals(AcquisitionUiPhase.Stale,s.open(scope,request()).phase)
        assertTrue(assertIs<LoadResult.Loaded>(backing.load("hero")).state.receipts.isEmpty())
    }
    @Test fun late_route_load_cannot_replace_a_newer_review()=run {
        val backing=store();val c=catalog();val entered=CompletableDeferred<Unit>();val release=CompletableDeferred<Unit>();var first=true
        val actual=ApplicationAcquisitionCommandPort(app(backing,c),backing,{c},{AcquisitionCapabilities(true,false,true)},{true})
        val delayed=object:AcquisitionCommandPort by actual {
            override suspend fun load(characterId:String):LoadResult {
                if(first) { first=false;entered.complete(Unit);release.await() }
                return actual.load(characterId)
            }
        }
        val s=AcquisitionPresentationSession(delayed,{"op"})
        coroutineScope {
            val old=async(Dispatchers.Unconfined,start=CoroutineStart.UNDISPATCHED) { s.route(scope,AcquisitionIntent.attribute(AttributeId.STRENGTH,1)) }
            entered.await();val newest=s.prepareGrant(scope,Amount(5,0),"Award")
            release.complete(Unit);assertEquals(newest,old.await());assertEquals(newest,s.model)
        }
    }
    @Test fun cancellation_before_commit_and_after_ack_keep_exact_identity_and_known_receipt()=run {
        for(afterAck in listOf(false,true)) {
            val backing=store();val c=catalog();var ids=0;var first=true
            val actual=ApplicationAcquisitionCommandPort(app(backing,c),backing,{c},{AcquisitionCapabilities(true,false,true)},{true},
                {_,_->if(afterAck && first) { first=false;throw CancellationException("view cancelled") }})
            val port=object:AcquisitionCommandPort by actual {
                override suspend fun apply(command:ConfirmAcquisition):ApplyResult {
                    if(!afterAck && first) { first=false;throw CancellationException("before commit") }
                    return actual.apply(command)
                }
            }
            val s=AcquisitionPresentationSession(port,{"op-${++ids}"})
            val key=s.open(scope,request()).key
            assertFailsWith<CancellationException> { s.confirm(key) }
            assertEquals(if(afterAck)AcquisitionUiPhase.Committed else AcquisitionUiPhase.Failed,s.model?.phase)
            if(afterAck)assertNotNull(s.model?.receipt)
            assertEquals(AcquisitionUiPhase.Committed,s.retrySameCommand().phase)
            assertEquals(1,ids);assertEquals(1,assertIs<LoadResult.Loaded>(backing.load("hero")).state.receipts.size)
        }
    }

    @Test fun late_missing_load_cannot_replace_newer_quote_or_mutation_review()=run {
        for(entry in listOf("quote","grant","nonfinancial")) {
            val backing=store();val c=catalog();val entered=CompletableDeferred<Unit>();val release=CompletableDeferred<Unit>();var first=true
            val actual=ApplicationAcquisitionCommandPort(app(backing,c),backing,{c},{AcquisitionCapabilities(true,false,true)},{true})
            val delayed=object:AcquisitionCommandPort by actual {
                override suspend fun load(characterId:String):LoadResult {
                    if(first) { first=false;entered.complete(Unit);release.await();return LoadResult.Missing(characterId) }
                    return actual.load(characterId)
                }
            }
            val s=AcquisitionPresentationSession(delayed,{"unused"})
            coroutineScope {
                val old=async(Dispatchers.Unconfined,start=CoroutineStart.UNDISPATCHED) { when(entry) {
                    "quote"->s.open(scope,request())
                    "grant"->s.prepareGrant(scope,Amount(1,0),"Old")
                    else->s.prepareNonFinancial(scope){character.setName("Old")}
                } }
                entered.await();val newest=s.prepareGrant(scope,Amount(5,0),"Newest")
                release.complete(Unit);assertEquals(newest,old.await(),entry);assertEquals(newest,s.model,entry)
            }
        }
    }

    @Test fun card_and_session_never_mix_a_preview_with_an_older_read_snapshot()=run {
        for(card in listOf(false,true)) {
            val backing=store();val c=catalog()
            val actual=ApplicationAcquisitionCommandPort(app(backing,c),backing,{c},{AcquisitionCapabilities(true,false,true)},{true})
            val changed=object:AcquisitionCommandPort by actual {
                override suspend fun preview(request:AcquisitionRequest):AcquisitionPlan {
                    assertIs<MutationResult.Applied>(CharacterMutationCoordinator(backing,{c},{"unused"}).mutate(CanonicalMutation("external","hero",0,MutationPayload.Grant(Amount(5,0)),"Concurrent award")))
                    return actual.preview(request)
                }
            }
            val control=AcquisitionController(changed,{scope},{"never"})
            val view=if(card)control.quoteCard(request()) else control.preview(request())
            assertEquals(AcquisitionUiPhase.Stale,view.phase);assertFalse(view.canConfirm)
            assertEquals(listOf("external"),assertIs<LoadResult.Loaded>(backing.load("hero")).state.receipts.map { it.operationId })
        }
    }

    @Test fun nonfinancial_review_and_cancel_do_not_allocate_confirmed_operation_identity()=run {
        val backing=store();var ids=0;var callbacks=0
        val s=session(backing,id={"op-${++ids}"})
        val view=s.prepareNonFinancial(scope){callbacks++;character.setName("Reviewed")}
        assertEquals(0,ids);assertNull(s.retainedOperationId)
        s.dismiss();s.reopen();assertEquals(0,ids)
        assertTrue(assertIs<LoadResult.Loaded>(backing.load("hero")).state.receipts.isEmpty())
        assertEquals(AcquisitionUiPhase.Committed,s.confirm(view.key).phase)
        assertEquals(1,ids);assertEquals(1,callbacks);assertEquals("op-1",s.retainedOperationId)
    }

}
