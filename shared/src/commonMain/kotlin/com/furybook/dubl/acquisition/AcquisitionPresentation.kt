package com.furybook.dubl.acquisition

import com.furybook.dubl.model.AttributeId

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
    val price: AcquisitionDisplayedPrice, val isTarget: Boolean, val alreadyOwned: Boolean = false, val excludedTarget: Boolean = false)
data class AcquisitionChoiceView(val id: String, val label: String, val options: List<AcquisitionChoiceOptionView>, val selectedId: String?)
data class AcquisitionChoiceOptionView(val id: String, val label: String, val price: AcquisitionDisplayedPrice)
data class AcquisitionRequirementView(val nodeId: String, val label: String, val status: RequirementStatus,
    val actual: RequirementValue?, val required: RequirementValue?, val external: RequirementNode.ExternalEvidence?,
    val children: List<AcquisitionRequirementView>, val sourceText: String)
data class AcquisitionBlock(val status: RequirementStatus, val message: String, val label: String?, val sourceText: String?)
data class AcquisitionReceiptView(val cost: Amount, val remaining: Amount, val status: ReceiptStatus,
    val replayed: Boolean, val rows: List<AcquisitionDisplayRow>, val currentBalance: Amount? = null,
    val savedOperation: AcquisitionSavedOperation? = null)
data class AcquisitionUiModel(val scope: AcquisitionScope, val key: AcquisitionQuoteKey, val phase: AcquisitionUiPhase,
    val summary: String, val price: AcquisitionDisplayedPrice = AcquisitionDisplayedPrice.Unknown(), val remaining: Amount? = null,
    val rows: List<AcquisitionDisplayRow> = emptyList(), val requirements: List<AcquisitionRequirementView> = emptyList(),
    val choices: List<AcquisitionChoiceView> = emptyList(), val blocks: List<AcquisitionBlock> = emptyList(),
    val canConfirm: Boolean = false, val canRetry: Boolean = false, val canCancel: Boolean = true, val canExport: Boolean = true,
    val receipt: AcquisitionReceiptView? = null, val sync: AcquisitionSyncView = AcquisitionSyncView(AcquisitionSyncStatus.Unavailable),
    val viewRefreshError: String? = null, val funding: NewCharacterFundingReview? = null,
    val request: AcquisitionRequest? = null, val selections: List<AcquisitionSelectionPrompt> = emptyList(),
    val reason: String? = null, val balanceDelta: Amount? = null,
    /** requirements describes the exact plan projection; these are the acknowledged pre-plan facts. */
    val baseRequirements: List<AcquisitionRequirementView> = emptyList())

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
        fun matches(target:AcquisitionTarget,change:AcquisitionChange)=target.definition==change.definition &&
            (target.instanceId==null || target.instanceId==change.instanceId) && target.parameters==change.parameters && target.optionId==change.optionId
        val rows=plan.changes.mapIndexed { index,change -> AcquisitionDisplayRow(change.definition,labels.definition(change.definition),change.fromRank,change.toRank,
            plan.prices.getOrNull(index)?.let(::price) ?: AcquisitionDisplayedPrice.Unknown(),plan.request.targets.any { matches(it,change) }) }.toMutableList()
        for(target in plan.request.targets) if(plan.changes.none { matches(target,it) }) {
            val owned=attained(target,state,catalog)
            val excluded=!plan.request.includeTargets
            rows+=AcquisitionDisplayRow(target.definition,labels.definition(target.definition),null,target.targetRank,
                if(owned || excluded) AcquisitionDisplayedPrice.Known(Amount.ZERO) else AcquisitionDisplayedPrice.Unknown(),true,owned,excluded)
        }
        val context=acquisitionContext(state,catalog,plan.request.externalConfirmations)
        val nodes=plan.request.targets.mapNotNull { catalog.definitions[it.definition]?.requirements }
        val requirements=nodes.map { requirement(it,evaluate(it,plan.projectedContext),labels) }
        val baseRequirements=nodes.map { requirement(it,evaluate(it,context),labels) }
        return AcquisitionUiModel(scope,key,phase,when(phase) {
            AcquisitionUiPhase.Ready -> when {
                !plan.request.includeTargets -> if(plan.changes.isEmpty()) "Требования выполнены; цель не приобретается" else "Проверьте цену требований и подтвердите"
                plan.changes.isEmpty() -> if(rows.isNotEmpty() && rows.all { it.alreadyOwned }) "Уже приобретено" else "Изменений нет"
                else -> "Проверьте цену и подтвердите"
            }
            AcquisitionUiPhase.ChoiceRequired -> "Выберите вариант, экземпляр или параметр"
            else -> "Покупка недоступна"
        },plan.cost?.let { AcquisitionDisplayedPrice.Known(it) } ?: AcquisitionDisplayedPrice.Unknown(),plan.remaining,
            EnvelopeList(rows),EnvelopeList(requirements),EnvelopeList(plan.questions.map { question -> AcquisitionChoiceView(question.id,"Выберите вариант",
                EnvelopeList(question.options.mapIndexed { index,option -> AcquisitionChoiceOptionView(option.id,
                    option.changes.joinToString { labels.definition(it.definition) }.ifBlank { "Вариант ${index+1}" },price(option.quote)) }),question.selectedId) }),
            EnvelopeList(blocks),phase==AcquisitionUiPhase.Ready,sync=sync(state,capabilities),request=plan.request,selections=selectionPrompts(plan.request,state,catalog),baseRequirements=EnvelopeList(baseRequirements))
    }
    /** A canonical ownership fact, independent of executability or an empty plan. */
    private fun attained(target:AcquisitionTarget,state:StoredCharacter,catalog:AcquisitionCatalog):Boolean {
        val definition=catalog.definitions[target.definition] ?: return false
        if(definition.kind==AcquisitionKind.ATTRIBUTE) {
            val attribute=AttributeId.entries.singleOrNull { target.definition==DefinitionRef("dubl-3.69","attribute:${it.name}") } ?: return false
            return target.parameters.isEmpty() && target.optionId==null && (state.character.attributes[attribute]?.base ?: return false)>=target.targetRank
        }
        val owned=if(target.instanceId!=null) state.instances.singleOrNull { it.instanceId==target.instanceId && it.definition==target.definition }
            else if(definition.repeatPolicy==RepeatPolicy.Single) state.instances.singleOrNull { it.definition==target.definition } else null
        return owned!=null && owned.rank>=target.targetRank && owned.parameters==target.parameters && owned.abilityOptionId==target.optionId
    }
    fun receipt(receipt: Receipt, catalog: AcquisitionCatalog, replayed: Boolean, entry: LedgerEntry? = null) = AcquisitionReceiptView(receipt.result.cost,receipt.result.remaining,receipt.status,replayed,
        EnvelopeList(receipt.result.changes.map { AcquisitionDisplayRow(it.definition,AcquisitionLabels(catalog).definition(it.definition),it.fromRank,it.toRank,
            AcquisitionDisplayedPrice.Unknown("Стоимость сохранена в итоге операции"),true) }), savedOperation=savedAcquisitionOperation(receipt,entry))
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
