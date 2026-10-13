package com.furybook.dubl.acquisition

/** View identity differs from the new target when confirming initial funding. No credentials. */
data class AcquisitionScope(val storageScopeId: String, val characterId: String,
    val controllerGeneration: Long, val selectionGeneration: Long, val accountGeneration: Long,
    val viewCharacterId: String = characterId)
data class AcquisitionQuoteKey(val sessionId: String, val generation: Long)
enum class AcquisitionUiPhase { Loading, Ready, ChoiceRequired, Blocked, Applying, Committed, Failed, Stale, Conflict }
sealed interface AcquisitionDisplayedPrice {
    data class Known(val amount: Amount) : AcquisitionDisplayedPrice
    data class Unknown(val message: String = "Цена не определена") : AcquisitionDisplayedPrice
}
enum class AcquisitionSyncStatus { LocalOnlyUnavailable, Pending, Synced, Retryable, Conflict, Unavailable }
data class AcquisitionSyncView(val status: AcquisitionSyncStatus, val pendingCount: Int = 0, val serverRevision: Long? = null)
data class AcquisitionDisplayRow(val definition: DefinitionRef?, val label: String, val fromRank: Int?, val toRank: Int?,
    val price: AcquisitionDisplayedPrice, val isTarget: Boolean, val alreadyOwned: Boolean = false)
data class AcquisitionChoiceView(val id: String, val label: String, val options: List<AcquisitionChoiceOptionView>, val selectedId: String?)
data class AcquisitionChoiceOptionView(val id: String, val label: String, val price: AcquisitionDisplayedPrice)
data class AcquisitionRequirementView(val nodeId: String, val label: String, val status: RequirementStatus,
    val actual: RequirementValue?, val required: RequirementValue?, val external: RequirementNode.ExternalEvidence?,
    val children: List<AcquisitionRequirementView>, val sourceText: String)
data class AcquisitionBlock(val status: RequirementStatus, val message: String, val label: String?, val sourceText: String?)
data class AcquisitionReceiptView(val cost: Amount, val remaining: Amount, val status: ReceiptStatus,
    val replayed: Boolean, val rows: List<AcquisitionDisplayRow>, val currentBalance: Amount? = null)
data class AcquisitionUiModel(val scope: AcquisitionScope, val key: AcquisitionQuoteKey, val phase: AcquisitionUiPhase,
    val summary: String, val price: AcquisitionDisplayedPrice = AcquisitionDisplayedPrice.Unknown(), val remaining: Amount? = null,
    val rows: List<AcquisitionDisplayRow> = emptyList(), val requirements: List<AcquisitionRequirementView> = emptyList(),
    val choices: List<AcquisitionChoiceView> = emptyList(), val blocks: List<AcquisitionBlock> = emptyList(),
    val canConfirm: Boolean = false, val canRetry: Boolean = false, val canCancel: Boolean = true, val canExport: Boolean = true,
    val receipt: AcquisitionReceiptView? = null, val sync: AcquisitionSyncView = AcquisitionSyncView(AcquisitionSyncStatus.Unavailable),
    val viewRefreshError: String? = null, val funding: NewCharacterFundingReview? = null,
    val request: AcquisitionRequest? = null, val selections: List<AcquisitionSelectionPrompt> = emptyList(),
    val reason: String? = null, val balanceDelta: Amount? = null)

/** Missing labels never fall back to stable identifiers. Labels are not rule/identity authority. */
class AcquisitionLabels(private val catalog: AcquisitionCatalog) {
    fun definition(ref: DefinitionRef): String = catalog.definitions[ref]?.name?.takeIf { it.isNotBlank() && it != ref.entryId }
        ?: "Запись недоступного пакета"
    fun requirement(node: RequirementNode): String = when(node) {
        is RequirementNode.AllOf -> "Все условия"
        is RequirementNode.AnyOf -> "Одно из условий"
        is RequirementNode.OwnedRank -> definition(DefinitionRef(node.packId,node.entryId))
        is RequirementNode.Specialization -> definition(DefinitionRef(node.packId,node.entryId))
        is RequirementNode.LearnedSpell -> definition(DefinitionRef(node.packId,node.entryId))
        is RequirementNode.Attribute -> "Характеристика: ${node.attributeId.title}"
        is RequirementNode.SkillFamily -> "Навык выбранной группы"
        is RequirementNode.BranchAccess -> "Доступ к ветке"
        is RequirementNode.DistinctCount -> "Различные приобретения"
        is RequirementNode.CreationPhase -> "Этап создания персонажа"
        is RequirementNode.Exclusive -> "Несовместимое приобретение"
        is RequirementNode.ExternalEvidence -> "Подтверждение внешнего условия"
        is RequirementNode.Unresolved -> "Правило требует уточнения"
    }
}
object AcquisitionPresentation {
    fun price(value: PriceResult): AcquisitionDisplayedPrice = when(value) {
        is PriceResult.Known -> AcquisitionDisplayedPrice.Known(value.cost)
        is PriceResult.Unknown -> AcquisitionDisplayedPrice.Unknown()
    }
    fun present(scope: AcquisitionScope, key: AcquisitionQuoteKey, plan: AcquisitionPlan, state: StoredCharacter,
        catalog: AcquisitionCatalog, capabilities: AcquisitionCapabilities): AcquisitionUiModel {
        val labels=AcquisitionLabels(catalog)
        val phase=when {
            !capabilities.localDurable || !capabilities.formatWritable -> AcquisitionUiPhase.Blocked
            plan.questions.any { !it.resolved } || plan.issues.any { it.status==RequirementStatus.ChoiceRequired } -> AcquisitionUiPhase.ChoiceRequired
            plan.executable -> AcquisitionUiPhase.Ready
            else -> AcquisitionUiPhase.Blocked
        }
        val blocks=plan.issues.map { AcquisitionBlock(it.status, explanation(it.status),it.definition?.let(labels::definition),it.sourceRef?.originalText) }.toMutableList()
        if(!capabilities.localDurable || !capabilities.formatWritable) blocks+=AcquisitionBlock(RequirementStatus.Conflict,"Надёжное локальное сохранение недоступно. Доступны просмотр и экспорт.",null,null)
        val rows=plan.changes.mapIndexed { index,change -> AcquisitionDisplayRow(change.definition,labels.definition(change.definition),change.fromRank,change.toRank,
            plan.prices.getOrNull(index)?.let(::price) ?: AcquisitionDisplayedPrice.Unknown(),plan.request.targets.any { it.definition==change.definition }) }.toMutableList()
        for(target in plan.request.targets) if(rows.none { it.definition==target.definition }) rows+=AcquisitionDisplayRow(target.definition,labels.definition(target.definition),null,target.targetRank,
            if(plan.executable) AcquisitionDisplayedPrice.Known(Amount.ZERO) else AcquisitionDisplayedPrice.Unknown(),true,plan.executable)
        val context=acquisitionContext(state,catalog,plan.request.externalConfirmations)
        val requirements=plan.request.targets.mapNotNull { catalog.definitions[it.definition]?.requirements }.map { requirement(it,evaluate(it,context),labels) }
        return AcquisitionUiModel(scope,key,phase,when(phase) {
            AcquisitionUiPhase.Ready -> if(plan.changes.isEmpty()) "Уже приобретено" else "Проверьте цену и подтвердите"
            AcquisitionUiPhase.ChoiceRequired -> "Выберите вариант, экземпляр или параметр"
            else -> "Покупка недоступна"
        },plan.cost?.let { AcquisitionDisplayedPrice.Known(it) } ?: AcquisitionDisplayedPrice.Unknown(),plan.remaining,
            EnvelopeList(rows),EnvelopeList(requirements),EnvelopeList(plan.questions.map { question -> AcquisitionChoiceView(question.id,"Выберите вариант",
                EnvelopeList(question.options.mapIndexed { index,option -> AcquisitionChoiceOptionView(option.id,
                    option.changes.joinToString { labels.definition(it.definition) }.ifBlank { "Вариант ${index+1}" },price(option.quote)) }),question.selectedId) }),
            EnvelopeList(blocks),phase==AcquisitionUiPhase.Ready,sync=sync(state,capabilities),request=plan.request,selections=selectionPrompts(plan.request,state,catalog))
    }
    fun receipt(receipt: Receipt, catalog: AcquisitionCatalog, replayed: Boolean) = AcquisitionReceiptView(receipt.result.cost,receipt.result.remaining,receipt.status,replayed,
        EnvelopeList(receipt.result.changes.map { AcquisitionDisplayRow(it.definition,AcquisitionLabels(catalog).definition(it.definition),it.fromRank,it.toRank,
            AcquisitionDisplayedPrice.Unknown("Стоимость сохранена в итоге операции"),true) }))
    fun sync(state: StoredCharacter, capabilities: AcquisitionCapabilities) = AcquisitionSyncView(when {
        state.syncMetadata.remoteConflictCanonical!=null || state.syncMetadata.pendingResolution!=null -> AcquisitionSyncStatus.Conflict
        !capabilities.canSync -> AcquisitionSyncStatus.LocalOnlyUnavailable
        state.outbox.isNotEmpty() -> AcquisitionSyncStatus.Pending
        else -> AcquisitionSyncStatus.Synced
    },state.outbox.size,state.syncMetadata.serverRevision)
    private fun explanation(status: RequirementStatus)=when(status) {
        RequirementStatus.SourceUnresolved -> "Правило или цена требуют уточнения источника"
        RequirementStatus.Unsatisfied -> "Условия или доступный баланс не позволяют покупку"
        RequirementStatus.ChoiceRequired -> "Требуется явный выбор"
        RequirementStatus.Conflict -> "Обнаружен конфликт; проверьте сохранённые данные"
        RequirementStatus.Satisfied -> "Условие выполнено"
    }
    private fun requirement(node:RequirementNode,result:RequirementResult,labels:AcquisitionLabels):AcquisitionRequirementView {
        val nodes=when(node) { is RequirementNode.AllOf->node.children;is RequirementNode.AnyOf->node.children;is RequirementNode.Exclusive->listOf(node.conflicting);is RequirementNode.DistinctCount->listOf(node.predicate);else->emptyList() }
        return AcquisitionRequirementView(node.nodeId,labels.requirement(node),result.status,result.actualValue,result.requiredValue,node as? RequirementNode.ExternalEvidence,
            EnvelopeList(nodes.map { child -> requirement(child,result.children.firstOrNull { it.nodeId==child.nodeId } ?: RequirementResult(RequirementStatus.SourceUnresolved,child.nodeId),labels) }),node.sourceRef.originalText)
    }
}
