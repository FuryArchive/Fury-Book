package com.furybook.dubl.acquisition

import com.furybook.core.json.*
import com.furybook.dubl.application.DublApplication
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Dispatch is platform-owned; application/store/account are bound once, never selected after await. */
interface AcquisitionExecutor {
    suspend fun <T> run(block: suspend () -> T): T
    suspend fun <T> read(block: suspend () -> T): T = run(block)
}
object DirectAcquisitionExecutor : AcquisitionExecutor { override suspend fun <T> run(block:suspend()->T):T=block() }
interface AcquisitionCommandPort {
    suspend fun <T> executeCaptured(scope:AcquisitionScope,operationId:String,block:suspend()->T):T=block()
    val catalog: AcquisitionCatalog
    val capabilities: AcquisitionCapabilities
    fun isCurrent(scope: AcquisitionScope): Boolean
    suspend fun load(characterId: String): LoadResult
    suspend fun preview(request: AcquisitionRequest): AcquisitionPlan
    suspend fun apply(command: ConfirmAcquisition): ApplyResult
    suspend fun mutate(command: CanonicalMutation): MutationResult
    suspend fun prepareNonFinancial(operationId:String,characterId:String,revision:Long,edit:DublApplication.()->Unit):CanonicalMutation
    suspend fun eligibleUndo(characterId:String):UndoEligibility
    suspend fun undo(characterId:String,operationId:String,revision:Long):ApplyResult
    fun reviewCreation(initialXP:Long,name:String,concept:String):NewCharacterFundingReview
    fun captureCreation(review:NewCharacterFundingReview):CreateAcquisitionCharacter
    suspend fun create(command:CreateAcquisitionCharacter):NewCharacterCreationResult
    suspend fun reconcileCreation(command:CreateAcquisitionCharacter):NewCharacterCreationResult
    suspend fun publish(scope:AcquisitionScope,state:StoredCharacter)
}
class ApplicationAcquisitionCommandPort(
    private val application:DublApplication,private val store:AcquisitionStore,
    private val catalogProvider:()->AcquisitionCatalog,private val capabilitiesProvider:()->AcquisitionCapabilities,
    private val current:(AcquisitionScope)->Boolean,private val publisher:suspend(AcquisitionScope,StoredCharacter)->Unit={_,_->},
    private val executor:AcquisitionExecutor=DirectAcquisitionExecutor,
):AcquisitionCommandPort {
    override suspend fun <T> executeCaptured(scope:AcquisitionScope,operationId:String,block:suspend()->T):T =
        withContext(AcquisitionDispatchContext(scope,operationId)) { block() }
    override val catalog get()=catalogProvider()
    override val capabilities get()=capabilitiesProvider()
    override fun isCurrent(scope:AcquisitionScope)=current(scope)
    override suspend fun load(characterId:String)=executor.read { store.load(characterId) }
    override suspend fun preview(request:AcquisitionRequest)=executor.read { application.development.previewAcquisition(request) }
    override suspend fun apply(command:ConfirmAcquisition)=executor.run { application.development.confirmAcquisition(command) }
    override suspend fun mutate(command:CanonicalMutation)=executor.run { application.mutateCanonical(command) }
    override suspend fun prepareNonFinancial(operationId:String,characterId:String,revision:Long,edit:DublApplication.()->Unit)=executor.run { application.prepareNonFinancial(operationId,characterId,revision,edit) }
    override suspend fun eligibleUndo(characterId:String)=executor.read { application.eligibleCanonicalUndo(characterId) }
    override suspend fun undo(characterId:String,operationId:String,revision:Long)=executor.run { application.undoCanonical(characterId,operationId,revision) }
    override fun reviewCreation(initialXP:Long,name:String,concept:String)=application.reviewNewCharacterFunding(initialXP,name,concept)
    override fun captureCreation(review:NewCharacterFundingReview)=application.captureCanonicalCharacterCreation(review)
    override suspend fun create(command:CreateAcquisitionCharacter)=executor.run { application.createCanonicalCharacter(command) }
    override suspend fun reconcileCreation(command:CreateAcquisitionCharacter)=executor.read { application.reconcileCanonicalCharacterCreation(command) }
    override suspend fun publish(scope:AcquisitionScope,state:StoredCharacter)=publisher(scope,state)
}

/** One live reviewed intent. Dismiss is presentation-only; uncertain commands cannot be replaced.
 * No durable uncommitted journal is claimed. A cold start discovers committed canonical heads.
 */
class AcquisitionPresentationSession(private val port:AcquisitionCommandPort,private val idFactory:()->String,
    private val changed:(AcquisitionUiModel)->Unit={}) {
    private val confirmation=Mutex()
    private val sessionId = "view:${kotlin.random.Random.nextLong()}:${kotlin.random.Random.nextLong()}"
    private var generation=0L
    private var plan:AcquisitionPlan?=null
    private var reviewCatalog:AcquisitionCatalog?=null
    private var reviewMutation:CanonicalMutation?=null
    private var reviewPayload:MutationPayload?=null
    private var reason:String?=null
    private var revision=0L
    private var undoReview:UndoEligibility.Available?=null
    private var pending:Pending?=null
    var model:AcquisitionUiModel?=null;private set
    var visible:Boolean=false;private set
    val retainedOperationId:String? get()=pending?.operationId
    private sealed interface Pending {
        val operationId:String
        val characterId:String
        val canonical:String
        data class Purchase(val command:ConfirmAcquisition):Pending {
            override val operationId get()=command.operationId;override val characterId get()=command.characterId;override val canonical get()=command.canonical
        }
        data class Mutation(val command:CanonicalMutation):Pending {
            override val operationId get()=command.operationId;override val characterId get()=command.characterId;override val canonical get()=command.canonical
        }
        data class Undo(override val operationId:String,override val characterId:String,val revision:Long):Pending {
            override val canonical get()=canonicalJson(jsonStringify(jsonObject("type" to jsonString("Undo"),"characterId" to jsonString(characterId),"expectedRevision" to jsonString(revision.toString()))))
        }
        data class Creation(val command:CreateAcquisitionCharacter):Pending {
            override val operationId get()=command.creationId;override val characterId get()=command.characterId;override val canonical get()=command.canonical
        }
    }
    /** Only the view epoch can reactivate; command target and original owner binding never change. */
    internal fun activate(scope:AcquisitionScope) {
        val view=model ?: return
        val old=view.scope
        require(old.storageScopeId==scope.storageScopeId && old.controllerGeneration==scope.controllerGeneration &&
            old.accountGeneration==scope.accountGeneration && old.viewCharacterId==scope.viewCharacterId)
        if(old.selectionGeneration!=scope.selectionGeneration) set(view.copy(scope=old.copy(selectionGeneration=scope.selectionGeneration)))
    }
    suspend fun readReviewDetails(key:AcquisitionQuoteKey):AcquisitionReviewDetails {
        val view=checkNotNull(model);require(view.key==key);check(current()) { "Captured view changed" }
        val request=checkNotNull(view.request)
        val expectedRevision=plan?.baseRevision ?: revision
        val catalog=checkNotNull(reviewCatalog)
        val state=(port.load(view.scope.characterId) as? LoadResult.Loaded)?.state ?: error("Canonical view unavailable")
        check(model?.key==key && current() && state.revision==expectedRevision) { "Review changed; quote again" }
        val activeCatalog=port.catalog
        check(activeCatalog.catalogRevision==catalog.catalogRevision && activeCatalog.resolutionRevision==catalog.resolutionRevision) { "Catalog changed" }
        return acquisitionReviewDetails(checkNotNull(model).scope,key,request,state,catalog,plan)
    }
    suspend fun selectAbilityOption(key:AcquisitionQuoteKey,index:Int,optionId:String):AcquisitionUiModel {
        require(key==model?.key && pending==null);check(current())
        val request=checkNotNull(model?.request);val target=request.targets[index]
        require(reviewCatalog?.definitions?.get(target.definition)?.abilityOptionIds?.contains(optionId)==true)
        return replaceSelections(key,request.withTarget(index,target.copy(optionId=optionId)))
    }
    private fun set(value:AcquisitionUiModel):AcquisitionUiModel { model=value;changed(value);return value }
    private fun start(scope:AcquisitionScope,summary:String):AcquisitionUiModel {
        check(model?.phase!=AcquisitionUiPhase.Applying && (pending==null || model?.phase==AcquisitionUiPhase.Committed)) { "Resolve the retained command before replacing it" }
        pending=null;plan=null;reviewCatalog=null;reviewMutation=null;reviewPayload=null;reason=null;undoReview=null;visible=true
        return set(AcquisitionUiModel(scope,AcquisitionQuoteKey(sessionId,++generation),AcquisitionUiPhase.Loading,summary,canCancel=true))
    }
    private fun blocked(message:String,phase:AcquisitionUiPhase=AcquisitionUiPhase.Blocked):AcquisitionUiModel = set(checkNotNull(model).copy(
        phase=phase,summary=message,canConfirm=false,canRetry=phase==AcquisitionUiPhase.Failed,
        blocks=EnvelopeList(listOf(AcquisitionBlock(RequirementStatus.Conflict,message,null,null)))))
    private fun current():Boolean=port.isCurrent(checkNotNull(model).scope)
    private fun canWrite():Boolean=port.capabilities.localDurable && port.capabilities.formatWritable
    suspend fun open(scope:AcquisitionScope,request:AcquisitionRequest):AcquisitionUiModel {
        val opening=start(scope,"Расчёт цены");val key=opening.key
        if(request.characterId!=scope.characterId) return blocked("Запрос относится к другому персонажу",AcquisitionUiPhase.Conflict)
        return try {
            val loaded=port.load(scope.characterId)
            if(model?.key!=key) return checkNotNull(model)
            val state=(loaded as? LoadResult.Loaded)?.state ?: return blocked("Канонический лист недоступен. Сохраните экспорт или подготовьте миграцию.")
            val preview=port.preview(request)
            if(model?.key!=key) return checkNotNull(model)
            if(!current()) return blocked("Выбор персонажа или аккаунта изменился",AcquisitionUiPhase.Stale)
            plan=preview
            if(state.revision!=preview.baseRevision) return blocked("Лист изменился. Обновите цену",AcquisitionUiPhase.Stale)
            val catalog=port.catalog
            if(catalog.catalogRevision!=preview.catalogRevision || catalog.resolutionRevision!=preview.resolutionRevision)
                return blocked("Каталог изменился. Обновите цену",AcquisitionUiPhase.Stale)
            reviewCatalog=catalog
            set(AcquisitionPresentation.present(checkNotNull(model).scope,key,preview,state,catalog,port.capabilities))
        } catch(cancelled:CancellationException) { if(model?.key==key) blocked("Расчёт отменён");throw cancelled }
          catch(failure:Exception) { if(model?.key!=key) checkNotNull(model) else blocked("Не удалось прочитать лист или рассчитать цену") }
    }
    suspend fun route(scope:AcquisitionScope,intent:AcquisitionIntent):AcquisitionUiModel {
        val key=start(scope,"Подготовка покупки").key
        val state=try { port.load(scope.characterId) } catch(cancelled:CancellationException) { throw cancelled } catch(failure:Exception) { return if(model?.key!=key) checkNotNull(model) else blocked("Канонический лист недоступен") }
        if(model?.key!=key) return checkNotNull(model)
        if(!current()) return blocked("Выбор изменился",AcquisitionUiPhase.Stale)
        if(state !is LoadResult.Loaded) { start(scope,"Покупка");return blocked("Канонический лист недоступен") }
        return when(val route=AcquisitionCommandRouting.request(intent,scope,state.state,port.catalog)) {
            is AcquisitionRouteResult.QuoteRequest->open(checkNotNull(model).scope,route.request)
            is AcquisitionRouteResult.Blocked->{start(scope,"Покупка");blocked(route.message)}
            is AcquisitionRouteResult.ManualReviewRequired->{start(scope,"Коррекция");blocked(route.message)}
            is AcquisitionRouteResult.NeedsExplicitSelection->{
                val request=when(intent) { is AcquisitionIntent.Target->AcquisitionRequest(scope.characterId,listOf(intent.target));is AcquisitionIntent.BulkTargets->intent.request }
                reviewCatalog=port.catalog;revision=state.state.revision
                blocked(route.message,AcquisitionUiPhase.ChoiceRequired)
                set(checkNotNull(model).copy(request=request,selections=selectionPrompts(request,state.state,port.catalog)))
            }
        }
    }
    suspend fun replaceSelections(key:AcquisitionQuoteKey,request:AcquisitionRequest):AcquisitionUiModel {
        require(key==model?.key && pending==null);return open(checkNotNull(model).scope,request)
    }
    suspend fun selectTarget(key:AcquisitionQuoteKey,targetIndex:Int,instanceId:String?,newInstance:Boolean=false,
        parameters:Map<String,String> = emptyMap(),optionId:String?=null):AcquisitionUiModel {
        require(key==model?.key && pending==null)
        val view=checkNotNull(model);val request=checkNotNull(view.request)
        val selection=view.selections.single { it.targetIndex==targetIndex }
        require(!newInstance || selection.canCreateNew)
        require(newInstance || instanceId==null || selection.instances.any { it.instanceId==instanceId })
        val target=request.targets[targetIndex].copy(instanceId=if(newInstance)newId() else instanceId,parameters=parameters,optionId=optionId)
        return replaceSelections(key,AcquisitionRequest(request.characterId,request.targets.mapIndexed { index,old -> if(index==targetIndex)target else old },
            request.choiceSelections,request.includeTargets,request.prerequisiteSelections,request.externalConfirmations))
    }
    suspend fun refresh(key:AcquisitionQuoteKey):AcquisitionUiModel {
        require(key==model?.key && pending==null);return open(checkNotNull(model).scope,checkNotNull(plan).request)
    }
    fun dismiss() { visible=false }
    fun reopen():AcquisitionUiModel? { visible=true;return model }
    suspend fun prepareGrant(scope:AcquisitionScope,delta:Amount,reason:String)=prepareMutation(scope,MutationPayload.Grant(delta),reason,"Выдача XP / ОСп")
    suspend fun prepareManual(scope:AcquisitionScope,patch:CharacterPatch,delta:Amount,reason:String)=prepareMutation(scope,MutationPayload.ManualCorrection(patch,delta),reason,"Ручная коррекция")
    suspend fun prepareCompleteCreation(scope:AcquisitionScope)=prepareMutation(scope,MutationPayload.CompleteCreation,null,"Завершить создание без пересчёта баланса")
    suspend fun prepareLegacyParameter(scope:AcquisitionScope,payload:MutationPayload.ConfirmLegacyParameter,reason:String)=prepareMutation(scope,payload,reason,"Подтверждение старого приобретения")
    private suspend fun prepareMutation(scope:AcquisitionScope,payload:MutationPayload,reason:String?,summary:String):AcquisitionUiModel {
        val key=start(scope,summary).key
        if(payload!=MutationPayload.CompleteCreation && reason.isNullOrBlank()) return blocked("Укажите причину операции")
        return try {
            val loaded=port.load(scope.characterId)
            if(model?.key!=key) return checkNotNull(model)
            val state=(loaded as? LoadResult.Loaded)?.state ?: return blocked("Канонический лист недоступен")
            if(model?.key!=key) return checkNotNull(model)
            reviewCatalog=port.catalog;revision=state.revision;reviewPayload=payload;this.reason=reason
            if(!current()) return blocked("Выбор изменился",AcquisitionUiPhase.Stale)
            if(!canWrite()) return blocked("Надёжное локальное сохранение недоступно")
            val delta=when(payload) { is MutationPayload.Grant->payload.delta;is MutationPayload.ManualCorrection->payload.delta;else->Amount.ZERO }
            set(checkNotNull(model).copy(phase=AcquisitionUiPhase.Ready,canConfirm=true,price=AcquisitionDisplayedPrice.Known(Amount.ZERO-delta),remaining=balance(state.ledger)+delta,reason=reason,balanceDelta=delta))
        } catch(cancelled:CancellationException) { throw cancelled } catch(failure:Exception) { if(model?.key!=key) checkNotNull(model) else blocked("Не удалось подготовить операцию") }
    }
    suspend fun prepareNonFinancial(scope:AcquisitionScope,edit:DublApplication.()->Unit):AcquisitionUiModel {
        val key=start(scope,"Изменение листа").key
        return try {
            val loaded=port.load(scope.characterId)
            if(model?.key!=key) return checkNotNull(model)
            val state=(loaded as? LoadResult.Loaded)?.state ?: return blocked("Канонический лист недоступен")
            if(!current()) return blocked("Выбор изменился",AcquisitionUiPhase.Stale)
            if(!canWrite()) return blocked("Надёжное локальное сохранение недоступно")
            if(model?.key!=key) return checkNotNull(model)
            val catalog=port.catalog
            // This identity only stages the frozen payload; it is never submitted to the store.
            val stageId="stage:${key.sessionId}:${key.generation}"
            val prepared=port.executeCaptured(checkNotNull(model).scope,stageId) { port.prepareNonFinancial(stageId,scope.characterId,state.revision,edit) }
            if(model?.key!=key) return checkNotNull(model)
            reviewCatalog=catalog;reviewMutation=prepared
            if(!current()) return blocked("Выбор изменился",AcquisitionUiPhase.Stale)
            set(checkNotNull(model).copy(phase=AcquisitionUiPhase.Ready,canConfirm=true,price=AcquisitionDisplayedPrice.Known(Amount.ZERO),remaining=balance(state.ledger)))
        } catch(cancelled:CancellationException) { throw cancelled } catch(failure:Exception) { if(model?.key!=key) checkNotNull(model) else blocked("Изменение требует отдельной подтверждённой операции") }
    }
    suspend fun prepareUndo(scope:AcquisitionScope):AcquisitionUiModel {
        val key=start(scope,"Отмена сохранённой операции").key
        val catalog=port.catalog
        val value=port.eligibleUndo(scope.characterId)
        if(model?.key!=key) return checkNotNull(model)
        reviewCatalog=catalog
        return when(value) {
            is UndoEligibility.Available->{undoReview=value;if(!current()) blocked("Выбор изменился",AcquisitionUiPhase.Stale)
                else if(!canWrite()) blocked("Надёжное локальное сохранение недоступно")
                else set(checkNotNull(model).copy(phase=AcquisitionUiPhase.Ready,canConfirm=true,price=AcquisitionDisplayedPrice.Known(Amount.ZERO-value.amountDelta)))}
            else->blocked("Эту операцию сейчас нельзя отменить")
        }
    }
    fun reviewNewCharacterFunding(scope:AcquisitionScope,initialXP:Long,name:String="Новый персонаж",concept:String=""):AcquisitionUiModel {
        start(scope,"Начальный баланс нового персонажа")
        return try {
            val review=port.reviewCreation(initialXP,name,concept)
            set(checkNotNull(model).copy(phase=if(review.executable&&current())AcquisitionUiPhase.Ready else AcquisitionUiPhase.Blocked,
                canConfirm=review.executable&&current(),funding=review,remaining=review.initialAP?.let { Amount(initialXP,it) },
                summary=if(review.executable)"Подтвердите однократный начальный баланс" else "Начальное финансирование недоступно"))
        } catch(failure:Exception) { blocked("Создание канонического персонажа недоступно") }
    }
    suspend fun confirm(key:AcquisitionQuoteKey):AcquisitionUiModel=confirmation.withLock {
        val view=checkNotNull(model)
        if(key!=view.key) return@withLock view
        if(pending!=null) return@withLock view // A second tap is never a second application.
        if(!view.canConfirm || view.phase!=AcquisitionUiPhase.Ready) return@withLock view
        if(!current()) return@withLock blocked("Выбор персонажа или аккаунта изменился",AcquisitionUiPhase.Stale)
        if(!canWrite()) return@withLock blocked("Надёжное локальное сохранение недоступно")
        pending=when {
            view.funding!=null->{
                val command=try { port.captureCreation(view.funding) } catch(failure:Exception) { return@withLock blocked("Начальные условия изменились. Проверьте их заново",AcquisitionUiPhase.Stale) }
                set(view.copy(scope=view.scope.copy(characterId=command.characterId)))
                Pending.Creation(command)
            }
            reviewMutation!=null->Pending.Mutation(checkNotNull(reviewMutation).copy(operationId=newId()))
            reviewPayload!=null->Pending.Mutation(CanonicalMutation(newId(),view.scope.characterId,revision,checkNotNull(reviewPayload),reason))
            undoReview!=null->Pending.Undo(newId(),view.scope.characterId,checkNotNull(undoReview).expectedRevision)
            else->{val quote=checkNotNull(plan);Pending.Purchase(ConfirmAcquisition(newId(),quote.request.characterId,quote.request,quote.baseRevision,quote.previewToken))}
        }
        execute()
    }
    suspend fun retrySameCommand():AcquisitionUiModel=confirmation.withLock {
        if(model?.phase==AcquisitionUiPhase.Committed) return@withLock reconcile()
        checkNotNull(pending) { "No retained command" }
        if(!current()) return@withLock blocked("Вернитесь к исходному персонажу и аккаунту",AcquisitionUiPhase.Stale)
        if(!canWrite()) return@withLock blocked("Сохранение недоступно; проверьте результат без повторной записи",AcquisitionUiPhase.Failed)
        execute()
    }
    suspend fun reconcilePending():AcquisitionUiModel=confirmation.withLock { reconcile() }
    private fun newId()=idFactory().also { require(it.isNotBlank()) }
    private suspend fun execute():AcquisitionUiModel {
        val command=checkNotNull(pending)
        set(checkNotNull(model).copy(phase=AcquisitionUiPhase.Applying,canConfirm=false,canRetry=false,summary="Сохранение операции"))
        return try { port.executeCaptured(checkNotNull(model).scope,command.operationId) { when(command) {
            is Pending.Purchase->applied(port.apply(command.command))
            is Pending.Mutation->mutated(port.mutate(command.command))
            is Pending.Undo->applied(port.undo(command.characterId,command.operationId,command.revision))
            is Pending.Creation->created(port.create(command.command))
        }}} catch(cancelled:CancellationException) { if(model?.phase!=AcquisitionUiPhase.Committed) blocked("Результат неизвестен. Сохранён исходный запрос",AcquisitionUiPhase.Failed);throw cancelled }
          catch(failure:Exception) { blocked("Результат неизвестен. Повторите тот же запрос или проверьте сохранение",AcquisitionUiPhase.Failed) }
    }
    private suspend fun applied(result:ApplyResult):AcquisitionUiModel=when(result) {
        is ApplyResult.Applied->acknowledge(result.receipt,false)
        is ApplyResult.AlreadyApplied->acknowledge(result.receipt,true)
        is ApplyResult.AlreadyUndone->acknowledge(result.receipt,true)
        is ApplyResult.Stale->{pending=null;plan=result.newPlan;set(checkNotNull(model).copy(key=AcquisitionQuoteKey(sessionId,++generation)));blocked("Цена или лист изменились. Обновите и подтвердите заново",AcquisitionUiPhase.Stale)}
        is ApplyResult.IdentityConflict->blocked("Идентификатор операции относится к другому запросу",AcquisitionUiPhase.Conflict)
        is ApplyResult.Blocked->{pending=null;blocked("Условия покупки не выполнены")}
        is ApplyResult.StorageFailure->blocked("Результат сохранения неизвестен; сохранён исходный запрос",AcquisitionUiPhase.Failed)
    }
    private suspend fun mutated(result:MutationResult):AcquisitionUiModel=when(result) {
        is MutationResult.Applied->acknowledge(result.receipt,false)
        is MutationResult.AlreadyApplied->acknowledge(result.receipt,true)
        is MutationResult.AlreadyUndone->acknowledge(result.receipt,true)
        is MutationResult.Stale->{pending=null;blocked("Лист изменился. Подготовьте операцию заново",AcquisitionUiPhase.Stale)}
        is MutationResult.IdentityConflict->blocked("Конфликт идентификатора операции",AcquisitionUiPhase.Conflict)
        is MutationResult.Blocked->{pending=null;blocked("Изменение не разрешено каноническими правилами")}
        is MutationResult.StorageFailure->blocked("Результат сохранения неизвестен; сохранён исходный запрос",AcquisitionUiPhase.Failed)
    }
    private suspend fun acknowledge(receipt:Receipt,replayed:Boolean,state:StoredCharacter?=null):AcquisitionUiModel {
        val command=checkNotNull(pending)
        if(receipt.operationId!=command.operationId || receipt.requestCanonical!=command.canonical) return blocked("Ответ относится к другой операции",AcquisitionUiPhase.Conflict)
        set(checkNotNull(model).copy(phase=AcquisitionUiPhase.Committed,summary=if(receipt.status==ReceiptStatus.Undone)"Операция уже отменена" else "Операция сохранена",
            canConfirm=false,canRetry=false,receipt=AcquisitionPresentation.receipt(receipt,checkNotNull(reviewCatalog) { "Original review catalog unavailable" },replayed),viewRefreshError=null))
        return publishAcknowledged(state)
    }
    private suspend fun created(result:NewCharacterCreationResult):AcquisitionUiModel=when(result) {
        is NewCharacterCreationResult.Created->creationAcknowledged(result.state)
        is NewCharacterCreationResult.AlreadyCreated->creationAcknowledged(result.state)
        is NewCharacterCreationResult.IdentityConflict->blocked("Персонаж уже имеет другое финансирование",AcquisitionUiPhase.Conflict)
        is NewCharacterCreationResult.StalePolicy->{pending=null;blocked("Начальные условия изменились. Проверьте их заново",AcquisitionUiPhase.Stale)}
        is NewCharacterCreationResult.Unavailable->reconciliationFailure("Начальное финансирование недоступно; сохранён исходный запрос")
        is NewCharacterCreationResult.StorageFailure->reconciliationFailure("Результат создания неизвестен; сохранены исходные идентификаторы")
    }
    private suspend fun creationAcknowledged(state:StoredCharacter):AcquisitionUiModel {
        if(state.character.id!=pending?.characterId) return blocked("Ответ относится к другому персонажу",AcquisitionUiPhase.Conflict)
        set(checkNotNull(model).copy(phase=AcquisitionUiPhase.Committed,summary="Начальный баланс сохранён однократно",canConfirm=false,canRetry=false,remaining=balance(state.ledger)))
        return publishAcknowledged(state)
    }
    private suspend fun publishAcknowledged(known:StoredCharacter?=null):AcquisitionUiModel {
        return try {
            val state=known ?: (port.load(checkNotNull(pending).characterId) as? LoadResult.Loaded)?.state ?: error("No acknowledged head")
            require(state.character.id==pending?.characterId)
            val view=checkNotNull(model)
            set(view.copy(sync=AcquisitionPresentation.sync(state,port.capabilities),receipt=view.receipt?.copy(currentBalance=balance(state.ledger))))
            if(!current()) return set(checkNotNull(model).copy(viewRefreshError="Сохранено для исходного персонажа; текущий выбор изменился"))
            port.publish(checkNotNull(model).scope,state)
            set(checkNotNull(model).copy(viewRefreshError=null))
        } catch(cancelled:CancellationException) { set(checkNotNull(model).copy(viewRefreshError="Операция сохранена, обновление просмотра отменено"));throw cancelled }
          catch(failure:Exception) { set(checkNotNull(model).copy(viewRefreshError="Операция сохранена, но просмотр не обновился. Проверьте сохранённый результат.")) }
    }
    private fun reconciliationFailure(message:String):AcquisitionUiModel = if(model?.phase==AcquisitionUiPhase.Committed)
        set(checkNotNull(model).copy(viewRefreshError=message)) else blocked(message,AcquisitionUiPhase.Failed)
    private suspend fun reconcile():AcquisitionUiModel {
        val command=checkNotNull(pending)
        if(!current()) return reconciliationFailure("Вернитесь к исходному персонажу и аккаунту")
        // The bound store's own owner guard remains authoritative. Never rebind to the current owner.
        return try {
            if(command is Pending.Creation) return created(port.reconcileCreation(command.command))
            val state=(port.load(command.characterId) as? LoadResult.Loaded)?.state ?: return reconciliationFailure("Сохранённый результат недоступен")
            if(state.character.id!=command.characterId) return blocked("Другой персонаж",AcquisitionUiPhase.Conflict)
            val receipt=state.receipts.singleOrNull { it.operationId==command.operationId }
                ?: return reconciliationFailure("Операция не найдена. Можно повторить только исходный запрос")
            acknowledge(receipt,true,state)
        } catch(cancelled:CancellationException) { throw cancelled } catch(failure:Exception) { reconciliationFailure("Не удалось проверить результат") }
    }
}
