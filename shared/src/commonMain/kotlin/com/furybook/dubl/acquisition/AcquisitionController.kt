package com.furybook.dubl.acquisition

import com.furybook.dubl.application.DublApplication

/** One retained session per original owner/controller/view, never one global pending command. */
class AcquisitionController(private val port:AcquisitionCommandPort,private val scope:()->AcquisitionScope,private val idFactory:()->String,
    private val changed:(AcquisitionUiModel?)->Unit={}) {
    private data class SessionScope(val storage:String,val controller:Long,val account:Long,val viewCharacter:String)
    private fun binding(value:AcquisitionScope)=SessionScope(value.storageScopeId,value.controllerGeneration,value.accountGeneration,value.viewCharacterId)
    private val sessions=linkedMapOf<SessionScope,AcquisitionPresentationSession>()
    private fun selected(create:Boolean):AcquisitionPresentationSession? {
        val captured=scope()
        if(!port.isCurrent(captured)) return null
        val key=binding(captured)
        val value=sessions[key] ?: if(create) AcquisitionPresentationSession(port,idFactory) { model ->
            if(port.isCurrent(model.scope)) changed(model)
        }.also { sessions[key]=it } else null
        value?.activate(captured)
        return value
    }
    private fun current()=checkNotNull(selected(false)) { "No retained review for this owner and character" }
    private fun forKey(key:AcquisitionQuoteKey,completedCreationReceipt:Boolean=false):AcquisitionPresentationSession {
        val value=checkNotNull(sessions.values.firstOrNull { it.model?.key==key }) { "Review is no longer available" }
        val captured=scope()
        val view=checkNotNull(value.model);val origin=view.scope
        check(port.isCurrent(captured)) { "Captured owner is no longer valid" }
        if(binding(origin)!=binding(captured)) {
            // Creation may select its acknowledged target. Return only that saved result;
            // never reactivate/rebind the originating session or dispatch another command.
            check(completedCreationReceipt && view.phase==AcquisitionUiPhase.Committed && view.funding!=null &&
                origin.characterId!=origin.viewCharacterId && captured.characterId==origin.characterId && captured.viewCharacterId==origin.characterId &&
                origin.storageScopeId==captured.storageScopeId && origin.controllerGeneration==captured.controllerGeneration && origin.accountGeneration==captured.accountGeneration) {
                "Review is unavailable for the current owner and view"
            }
        } else {
            value.activate(captured)
            check(port.isCurrent(checkNotNull(value.model).scope)) { "Captured review changed" }
        }
        return value
    }
    val model get()=selected(false)?.model
    val retainedOperationId get()=selected(false)?.retainedOperationId
    /** Selection notification only: no load, quote, write, owner rebind or modal opening. */
    fun activateCurrentView():AcquisitionUiModel?=model.also(changed)
    suspend fun readCanonicalView():AcquisitionCharacterView {
        val captured=scope()
        val loaded=port.load(captured.characterId)
        val state=(loaded as? LoadResult.Loaded)?.state ?: throw AcquisitionPreviewUnavailable(loaded)
        check(port.isCurrent(captured)) { "Captured view changed" }
        return canonicalView(captured,state,port.catalog,port.capabilities)
    }
    /** Card model and details share one exact request/base/catalog snapshot; no retained session changes. */
    suspend fun quoteCardReview(request:AcquisitionRequest):AcquisitionReview {
        val captured=scope();require(captured.characterId==request.characterId)
        val key=AcquisitionQuoteKey("card:${kotlin.random.Random.nextLong()}:${kotlin.random.Random.nextLong()}",0)
        fun unavailable(phase:AcquisitionUiPhase,text:String)=AcquisitionReview(AcquisitionUiModel(captured,key,phase,text,request=request),null)
        val loaded=port.load(captured.characterId)
        if(loaded !is LoadResult.Loaded) return unavailable(AcquisitionUiPhase.Blocked,"Канонический лист недоступен")
        val plan=port.preview(request)
        if(!port.isCurrent(captured)) return unavailable(AcquisitionUiPhase.Stale,"Выбор изменился")
        if(loaded.state.revision!=plan.baseRevision) return unavailable(AcquisitionUiPhase.Stale,"Лист изменился")
        val catalog=port.catalog
        if(catalog.catalogRevision!=plan.catalogRevision || catalog.resolutionRevision!=plan.resolutionRevision)
            return unavailable(AcquisitionUiPhase.Stale,"Каталог изменился")
        return AcquisitionReview(AcquisitionPresentation.present(captured,key,plan,loaded.state,catalog,port.capabilities).copy(canConfirm=false),
            acquisitionReviewDetails(captured,key,request,loaded.state,catalog,plan))
    }
    suspend fun quoteCard(request:AcquisitionRequest)=quoteCardReview(request).model
    suspend fun readReviewDetails(key:AcquisitionQuoteKey)=forKey(key).readReviewDetails(key)
    suspend fun selectAbilityOption(key:AcquisitionQuoteKey,index:Int,optionId:String)=forKey(key).selectAbilityOption(key,index,optionId)
    suspend fun selectTarget(key:AcquisitionQuoteKey,index:Int,instanceId:String?,newInstance:Boolean=false,parameters:Map<String,String> = emptyMap(),optionId:String?=null)=
        forKey(key).selectTarget(key,index,instanceId,newInstance,parameters,optionId)
    suspend fun preview(intent:AcquisitionIntent)=checkNotNull(selected(true)).route(scope(),intent)
    suspend fun preview(request:AcquisitionRequest)=checkNotNull(selected(true)).open(scope(),request)
    suspend fun replaceSelections(key:AcquisitionQuoteKey,request:AcquisitionRequest)=forKey(key).replaceSelections(key,request)
    suspend fun refresh(key:AcquisitionQuoteKey)=forKey(key).refresh(key)
    suspend fun confirm(key:AcquisitionQuoteKey):AcquisitionUiModel {
        val session=forKey(key,completedCreationReceipt=true);val view=checkNotNull(session.model)
        if(view.phase==AcquisitionUiPhase.Committed && view.funding!=null) return view
        return session.confirm(key)
    }
    suspend fun retrySameCommand()=current().retrySameCommand()
    suspend fun reconcilePending()=current().reconcilePending()
    fun dismiss() { selected(false)?.dismiss() }
    fun reopen()=selected(false)?.reopen()
    suspend fun prepareGrant(delta:Amount,reason:String)=checkNotNull(selected(true)).prepareGrant(scope(),delta,reason)
    suspend fun prepareManual(patch:CharacterPatch,delta:Amount,reason:String)=checkNotNull(selected(true)).prepareManual(scope(),patch,delta,reason)
    suspend fun prepareCompleteCreation()=checkNotNull(selected(true)).prepareCompleteCreation(scope())
    suspend fun prepareLegacyParameter(payload:MutationPayload.ConfirmLegacyParameter,reason:String)=checkNotNull(selected(true)).prepareLegacyParameter(scope(),payload,reason)
    suspend fun prepareNonFinancial(edit:DublApplication.()->Unit)=checkNotNull(selected(true)).prepareNonFinancial(scope(),edit)
    suspend fun prepareUndo()=checkNotNull(selected(true)).prepareUndo(scope())
    fun reviewNewCharacterFunding(initialXP:Long,name:String="Новый персонаж",concept:String="")=checkNotNull(selected(true)).reviewNewCharacterFunding(scope(),initialXP,name,concept)
}
