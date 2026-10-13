package com.furybook.desktop.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.furybook.dubl.acquisition.*
import com.furybook.dubl.acquisition.RequirementStatus
import com.furybook.dubl.application.DublApplication
import com.furybook.dubl.model.*
import java.util.UUID
import kotlinx.coroutines.*
import com.furybook.desktop.DesktopAppState



data class DesktopAcquisitionViewBinding(
    val price: String,
    val remaining: String?,
    val status: String,
    val sync: String,
    val canConfirm: Boolean,
    val receiptPrice: String?,
    val receiptRemaining: String?,
    val currentBalance: String?,
)

fun acquisitionAmount(value: Amount): String = "${value.xp} XP / ${value.ap} ОСп"
fun acquisitionPrice(value: AcquisitionDisplayedPrice): String = when (value) {
    is AcquisitionDisplayedPrice.Known -> acquisitionAmount(value.amount)
    is AcquisitionDisplayedPrice.Unknown -> value.message
}

fun bindDesktopAcquisition(model: AcquisitionUiModel) = DesktopAcquisitionViewBinding(
    acquisitionPrice(model.price), model.remaining?.let(::acquisitionAmount), model.summary,
    when (model.sync.status) {
        AcquisitionSyncStatus.LocalOnlyUnavailable -> "Облачная синхронизация недоступна; локальный результат отдельный"
        AcquisitionSyncStatus.Pending -> "Ожидает синхронизации: ${model.sync.pendingCount}"
        AcquisitionSyncStatus.Synced -> "Синхронизировано"
        AcquisitionSyncStatus.Retryable -> "Нужен повтор синхронизации"
        AcquisitionSyncStatus.Conflict -> "Конфликт облачных версий; автоматического объединения нет"
        AcquisitionSyncStatus.Unavailable -> "Статус синхронизации недоступен"
    }, model.canConfirm && model.phase == AcquisitionUiPhase.Ready,
    model.receipt?.cost?.let(::acquisitionAmount), model.receipt?.remaining?.let(::acquisitionAmount),
    model.receipt?.currentBalance?.let(::acquisitionAmount))

fun acquisitionRowLabel(row: AcquisitionDisplayRow): String = row.label + when {
    row.excludedTarget -> " · не приобретается"
    row.alreadyOwned -> " · уже приобретено"
    row.fromRank != null && row.toRank != null -> " · с ${row.fromRank} на ${row.toRank}"
    else -> ""
}

/** Both signed amounts must be entered explicitly; blank is never an implied zero. */
fun parseManualAmount(xp: String, ap: String, reason: String): Amount? {
    if (reason.isBlank()) return null
    fun integer(value: String): Long? = value.trim().takeIf { it.matches(Regex("[+-]?[0-9]+")) }?.toLongOrNull()
    return Amount(integer(xp) ?: return null, integer(ap) ?: return null)
}


/** Typed display only. Missing evidence is never rendered as false or numeric zero. */
fun acquisitionRequirementValue(value: RequirementValue?, label: String): String = when (value) {
    null -> "не установлено"
    is RequirementValue.Number -> value.value.toString()
    is RequirementValue.Flag -> if (value.value) "да" else "нет"
    is RequirementValue.Phase -> when (value.value) {
        RequirementNode.CreationPhase.Phase.Creating -> "создание персонажа"
        RequirementNode.CreationPhase.Phase.Finalized -> "после создания"
    }
    is RequirementValue.StableId -> label // Exact model-bound readable label, never a new catalog lookup or raw ID.
}


/** Root-owned rendering callbacks only. The Shared state owns every retained command. */
class DesktopAcquisitionActions(val state: DesktopAppState, private val scope: CoroutineScope) {
    var visible by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    var notice by mutableStateOf<String?>(null); private set
    var prerequisite by mutableStateOf<Pair<AcquisitionUiModel, String>?>(null); private set
    var confirmRole by mutableStateOf<Pair<AcquisitionQuoteKey, String>?>(null); private set
    var reviewLabel by mutableStateOf<Pair<AcquisitionQuoteKey, String>?>(null); private set
    var view by mutableStateOf<AcquisitionCharacterView?>(null); private set
    var readUnavailable by mutableStateOf(false); private set
    var details by mutableStateOf<AcquisitionReviewDetails?>(null); private set
    var manual by mutableStateOf<DesktopManualDraft?>(null); private set
    var fundingEditor by mutableStateOf(false); private set
    var historyVisible by mutableStateOf(false); private set
    var picker by mutableStateOf<AcquisitionKind?>(null); private set
    private var pickerRank = 1
    val canonical get() = state.canonicalMode

    fun action(block: suspend () -> Unit) {
        // Start inline: capture view, request and key before the first suspension.
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try { error = null; block() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { error = "Действие недоступно или просмотр изменился. Сохранённый запрос не заменён." }
        }
    }
    private var readGeneration = 0L
    suspend fun reload() {
        val generation = ++readGeneration
        val character = state.activeCharacter.id
        view = null; readUnavailable = false
        val loaded = try { state.acquisition.readCanonicalView() } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { null }
        if (generation == readGeneration && character == state.activeCharacter.id) { view = loaded; readUnavailable = loaded == null }
    }
    fun showError(message: String) { error = message }
    fun clearError() { error = null }
    fun clearNotice() { notice = null }
    fun choosePrerequisite(model: AcquisitionUiModel, question: String) { prerequisite = model to question }
    fun closePrerequisite() { prerequisite = null }
    fun selectPrerequisites(model: AcquisitionUiModel, question: String, targets: List<AcquisitionTarget>) {
        val request = model.request ?: return
        action {
            state.acquisition.replaceSelections(model.key, AcquisitionRequest(request.characterId, request.targets, request.choiceSelections, request.includeTargets, request.prerequisiteSelections + (question to targets), request.externalConfirmations))
            prerequisite = null
        }
    }
    fun hideForSelection() { visible = false; details = null; manual = null; picker = null; error = null; historyVisible = false; fundingEditor = false; prerequisite = null; reviewLabel = null; confirmRole = null }
    fun dismiss() { state.acquisition.dismiss(); visible = false; details = null }
    fun reopen() { state.acquisition.reopen(); visible = true }
    fun open(request: AcquisitionRequest) = action { visible = true; details = null; state.acquisition.preview(request) }
    fun confirm(key: AcquisitionQuoteKey) = action {
        val result = state.acquisition.confirm(key)
        if (result.funding != null && result.phase == AcquisitionUiPhase.Committed) {
            visible = false; notice = "Новый персонаж создан. Начальный баланс сохранён однократно."
        }
        reload()
    }
    fun refresh(key: AcquisitionQuoteKey) = action { state.acquisition.refresh(key) }
    fun retry() = action { state.acquisition.retrySameCommand(); reload() }
    fun reconcile() = action { state.acquisition.reconcilePending(); reload() }
    fun undo() = action { historyVisible = false; visible = true; val review = state.acquisition.prepareUndo(); confirmRole = review.key to "acquisition.undo.confirm" }
    fun completeCreation() = action { visible = true; state.acquisition.prepareCompleteCreation() }
    fun nonFinancial(edit: DublApplication.() -> Unit) = action {
        visible = true; state.acquisition.prepareNonFinancial(edit)
    }
    fun selectChoice(model: AcquisitionUiModel, id: String, option: String) {
        val request = model.request ?: return
        val next = AcquisitionRequest(request.characterId, request.targets, request.choiceSelections + (id to option),
            request.includeTargets, request.prerequisiteSelections, request.externalConfirmations)
        action { state.acquisition.replaceSelections(model.key, next) }
    }
    fun selectInstance(model: AcquisitionUiModel, prompt: AcquisitionSelectionPrompt, instance: AcquisitionInstanceView) = action {
        state.acquisition.selectTarget(model.key, prompt.targetIndex, instance.instanceId,
            parameters = instance.parameters, optionId = instance.optionId)
    }
    fun selectNew(model: AcquisitionUiModel, prompt: AcquisitionSelectionPrompt, parameters: Map<String, String>) = action {
        state.acquisition.selectTarget(model.key, prompt.targetIndex, null, newInstance = true, parameters = parameters)
    }
    fun selectAbility(model: AcquisitionUiModel, index: Int, option: String) = action {
        state.acquisition.selectAbilityOption(model.key, index, option)
    }
    fun confirmExternal(model: AcquisitionUiModel, node: RequirementNode.ExternalEvidence, checked: Boolean) {
        val request = model.request ?: return
        val previous = request.externalConfirmations.filterNot { it.evidenceKey == node.evidenceKey && it.scopeId == node.scopeId }
        val confirmations = if (checked) previous + RequirementWitness.External(node.evidenceKey, node.scopeId,
            java.util.UUID.randomUUID().toString(), node.sourceRef) else previous
        action { state.acquisition.replaceSelections(model.key, AcquisitionRequest(request.characterId, request.targets,
            request.choiceSelections, request.includeTargets, request.prerequisiteSelections, confirmations)) }
    }
    suspend fun loadDetails(model: AcquisitionUiModel) {
        details = null
        if (model.request == null || model.phase == AcquisitionUiPhase.Applying || model.phase == AcquisitionUiPhase.Committed) return
        val loaded = try { withContext(Dispatchers.Default) { state.acquisition.readReviewDetails(model.key) } } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { null }
        if (state.acquisitionUiModel?.key == model.key && state.snapshot.activeCharacterId == model.scope.viewCharacterId) details = loaded
    }
    fun target(kind: AcquisitionKind, id: String, rank: Int): AcquisitionTarget? {
        val current = view?.takeIf { it.scope.viewCharacterId == state.activeCharacter.id } ?: return null
        val holding = current.state.instances.singleOrNull { it.instanceId == id }
        val definition = if (holding != null) current.catalog.definitions[holding.definition]?.takeIf { it.kind == kind }
            else current.catalog.definitions.values.singleOrNull { it.kind == kind && it.ref.entryId == id }
        if (definition == null) return null
        val owned = holding ?: current.state.instances.singleOrNull { it.definition == definition.ref }
        return AcquisitionTarget(definition.ref, owned?.instanceId, rank, owned?.parameters.orEmpty(), owned?.abilityOptionId)
    }
    fun openTarget(kind: AcquisitionKind, id: String, rank: Int, includeTarget: Boolean = true) {
        val target = target(kind, id, rank)
        if (target == null) { error = "Нет однозначной записи или экземпляра. Выберите запись каталога; название не подтверждает её личность."; return }
        open(AcquisitionRequest(state.activeCharacter.id, listOf(target), includeTargets = includeTarget))
    }
    fun openBulk(ids: Collection<String>, includeTargets: Boolean = true) {
        val targets = ids.sorted().map { id -> target(AcquisitionKind.DEVELOPMENT, id,
            (view?.state?.instances?.filter { it.definition.entryId == id }?.maxOfOrNull { it.rank } ?: 0) + 1) }
        if (targets.any { it == null }) { error = "Одна из целей не имеет однозначной записи каталога"; return }
        open(AcquisitionRequest(state.activeCharacter.id, targets.filterNotNull(), includeTargets = includeTargets))
    }
    fun chooseDefinition(kind: AcquisitionKind, rank: Int = 1) { pickerRank = rank; picker = kind }
    fun dismissPicker() { picker = null }
    fun pickDefinition(definition: AcquisitionDefinition) { picker = null; open(AcquisitionRequest(state.activeCharacter.id,
        listOf(AcquisitionTarget(definition.ref, targetRank = pickerRank)))) }
    fun showHistory() { historyVisible = true; action { reload() } }
    fun closeHistory() { historyVisible = false }
    fun newCharacter() { fundingEditor = true }
    fun closeFunding() { fundingEditor = false }
    fun reviewFunding(xp: Long, name: String) {
        action { state.acquisition.reviewNewCharacterFunding(xp, name); fundingEditor = false; visible = true }
    }
    fun openManual(label: String = "Исправление баланса", character: DublCharacter? = null,
        instances: List<AcquisitionInstance>? = null, grant: Boolean = false) {
        val current = view ?: run { error = "Сначала откройте сохранённый канонический лист"; return }
        manual = DesktopManualDraft(current, label, CharacterPatch.fromCanonicalState(current.state,
            character ?: current.state.character, instances), grant)
    }
    /** Narrow edits always start from the acknowledged base, never the runtime-filtered sheet. */
    fun editManual(label: String, change: (DublCharacter) -> DublCharacter) {
        val current = view ?: run { error = "Сначала откройте сохранённый канонический лист"; return }
        openManual(label, change(current.state.character))
    }
    fun closeManual() { manual = null }
    fun reviewManual(draft: DesktopManualDraft, delta: Amount, reason: String) = action {
        if (draft.base.scope.viewCharacterId != state.activeCharacter.id) error("Character changed")
        val current = state.acquisition.readCanonicalView()
        if (current.scope != draft.base.scope || current.state.revision != draft.base.state.revision) error("Review changed")
        val review = if (draft.grant) state.acquisition.prepareGrant(delta, reason)
        else state.acquisition.prepareManual(draft.patch, delta, reason, expectedBaseRevision = draft.base.state.revision)
        reviewLabel = review.key to draft.label
        confirmRole = review.key to "acquisition.manual.confirm"
        manual = null; visible = true
    }
    fun manualSkill(id: String, label: String, transform: (CharacterSkill) -> CharacterSkill) {
        val current = view ?: return
        val old = current.state.character.skills[id] ?: CharacterSkill(id, definitionId = id)
        openManual(label, current.state.character.copy(skills = current.state.character.skills + (id to transform(old))))
    }
    fun lowerAttribute(id: AttributeId, rank: Int) {
        val state = view?.state ?: return
        val old = state.character.attributes[id] ?: return
        openManual("${id.title}: ${old.base} → $rank", state.character.copy(attributes = state.character.attributes + (id to old.copy(base = rank))))
    }
    fun lowerHolding(kind: AcquisitionKind, id: String, rank: Int) {
        val current = view ?: return
        val target = target(kind, id, rank) ?: run { error = "Нужен точный существующий экземпляр"; return }
        val owned = current.state.instances.singleOrNull { it.instanceId == target.instanceId } ?: run { error = "Старое приобретение требует отдельного подтверждения личности"; return }
        val instances = current.state.instances.mapNotNull { if (it.instanceId != owned.instanceId) it else if (rank > 0) it.copy(rank = rank) else null }
        val c = current.state.character
        val changed = when (kind) {
            AcquisitionKind.DEVELOPMENT -> c.copy(development = if (rank > 0) c.development + (owned.definition.entryId to (c.development[owned.definition.entryId] ?: OwnedDevelopment()).copy(rank = rank)) else c.development - owned.definition.entryId)
            AcquisitionKind.SKILL -> c.copy(skills = c.skills + (owned.instanceId to (c.skills[owned.instanceId] ?: CharacterSkill(owned.instanceId, definitionId = owned.definition.entryId)).copy(rank = rank)))
            AcquisitionKind.MANA -> c.copy(magic = c.magic.copy(manaRank = rank))
            AcquisitionKind.CHI_BONUS -> c.copy(chiBonusRanks = rank)
            AcquisitionKind.SCHOOL -> c // Exact school ownership is held by canonical instances, never a name alias.
            else -> { error = "Эта правка требует точного выбора записи в ручном редакторе"; return }
        }
        openManual("${AcquisitionLabels(current.catalog).definition(owned.definition)}: ${owned.rank} → $rank", changed, instances)
    }
}

data class DesktopManualDraft(val base: AcquisitionCharacterView, val label: String, val patch: CharacterPatch, val grant: Boolean)
val LocalDesktopAcquisition = staticCompositionLocalOf<DesktopAcquisitionActions?> { null }


val AcquisitionDefinitionKey = SemanticsPropertyKey<String>("AcquisitionDefinition")
var SemanticsPropertyReceiver.acquisitionDefinition by AcquisitionDefinitionKey
val AcquisitionPhaseKey = SemanticsPropertyKey<String>("AcquisitionPhase")
var SemanticsPropertyReceiver.acquisitionPhase by AcquisitionPhaseKey

@Composable
fun AcquisitionCardSummary(model: AcquisitionUiModel, modifier: Modifier = Modifier) {
    val binding = bindDesktopAcquisition(model)
    Column(modifier.testTag("acquisition.card").semantics { acquisitionPhase = model.phase.name },
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(binding.price, Modifier.testTag("acquisition.price"), style = MaterialTheme.typography.labelLarge)
        binding.remaining?.let { Text("После: $it", style = MaterialTheme.typography.bodySmall) }
        Text(binding.status, Modifier.testTag("acquisition.status"), style = MaterialTheme.typography.bodySmall)
    }
}

/** Quotes never replace the root's active or uncertain command. */
@Composable
fun AcquisitionEntrySummary(kind: AcquisitionKind, id: String, rank: Int, modifier: Modifier = Modifier) {
    val actions = LocalDesktopAcquisition.current ?: return
    if (!actions.canonical) return
    val snapshot = actions.view
    var review by remember(snapshot, kind, id, rank) { mutableStateOf<AcquisitionReview?>(null) }
    LaunchedEffect(snapshot, kind, id, rank) {
        val target = actions.target(kind, id, rank) ?: return@LaunchedEffect
        val request = AcquisitionRequest(actions.state.activeCharacter.id, listOf(target))
        review = try { withContext(Dispatchers.Default) { actions.state.acquisition.quoteCardReview(request) } }
            catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled } catch (_: Exception) { null }
    }
    val shown = review
    Column(modifier.testTag("acquisition.definition.$id").semantics { acquisitionDefinition = "$kind:$id" }) {
        if (shown == null) Text("Цена уточняется или запись недоступна", Modifier.testTag("acquisition.status"))
        else AcquisitionCardSummary(shown.model)
        TextButton(onClick = { shown?.model?.request?.let(actions::open) }, enabled = shown?.model?.request != null, modifier = Modifier.testTag("acquisition.open")) { Text("Просмотреть покупку") }
    }
}

@Composable
fun AcquisitionBalanceCard() {
    val actions = LocalDesktopAcquisition.current ?: return
    if (!actions.canonical) return
    SectionCard("Канонический журнал", Modifier.fillMaxWidth()) {
        Text(actions.view?.balance?.let(::acquisitionAmount) ?: "Баланс недоступен", Modifier.testTag("acquisition.balance"))
        Text("Покупка всегда требует отдельного просмотра и подтверждения.", style = MaterialTheme.typography.bodySmall)
        if (!actions.state.localAcquisitionEnabled) Text("Режим только чтения. Новые записи и покупки выключены.", style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = actions::showHistory, Modifier.testTag("acquisition.history.open")) { Text("История") }
            TextButton(onClick = { actions.openManual() }, Modifier.testTag("acquisition.manual.open")) { Text("Исправление") }
            TextButton(onClick = { actions.openManual("Выдать XP / ОСп", grant = true) }) { Text("Выдать") }
        }
        TextButton(onClick = actions::completeCreation, enabled = actions.view?.state?.character?.creationComplete == false) { Text("Завершить создание") }
        if (actions.state.acquisitionUiModel != null)
            TextButton(onClick = actions::reopen, Modifier.testTag("acquisition.reopen")) { Text("Последняя операция") }
    }
}

@Composable
fun AcquisitionReceiptSummary(receipt: AcquisitionReceiptView, onUndo: (() -> Unit)? = null) {
    Column(Modifier.testTag("acquisition.receipt"), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(if (receipt.status == ReceiptStatus.Undone) "Операция отменена" else "Сохранено на устройстве")
        Text(acquisitionAmount(receipt.cost), Modifier.testTag("acquisition.price"))
        Text("Остаток тогда: ${acquisitionAmount(receipt.remaining)}")
        receipt.currentBalance?.let { Text("Остаток сейчас: ${acquisitionAmount(it)}") }
        receipt.rows.forEach { Text(acquisitionRowLabel(it)) }
        receipt.savedOperation?.let { saved ->
            Text("Операция: ${saved.operationId}",Modifier.testTag("acquisition.receipt.operation"),style=MaterialTheme.typography.labelSmall)
            Text("Тип: ${saved.type?.let(::savedOperationType) ?: "не сохранён"} · Этап: ${saved.phase?.let { acquisitionRequirementValue(RequirementValue.Phase(it),"") } ?: "не сохранён"}",style=MaterialTheme.typography.bodySmall)
            Text("Ревизии листа: ${saved.beforeRevision} / ${saved.afterRevision}",style=MaterialTheme.typography.labelSmall)
            saved.ruleVersions?.let { Text("Сохранённые версии: ${it.catalogRevision} / ${it.resolutionRevision}",style=MaterialTheme.typography.labelSmall) }
            when(val evidence=saved.evidence) {
                is AcquisitionSavedEvidence.Recorded -> {
                    if(evidence.prices.isEmpty()) Text("Сохранённых ценовых переходов нет",style=MaterialTheme.typography.bodySmall)
                    evidence.prices.forEachIndexed { index,price ->
                        Text("Переход ${index+1}: ${acquisitionAmount(price.cost)}",style=MaterialTheme.typography.bodySmall)
                        (listOf(price.source)+price.componentSources).distinct().forEach { source ->
                            Text("Сохранённый источник: ${source.documentId} · ${source.documentVersion}",style=MaterialTheme.typography.labelSmall)
                            Text(source.originalText,style=MaterialTheme.typography.bodySmall)
                            source.resolutionId?.let { Text("Разрешение: $it · ${source.resolutionRevision ?: "версия не сохранена"}",style=MaterialTheme.typography.labelSmall) }
                        }
                        if(price.discountProofs.isEmpty()) Text("Условия скидки не записаны",style=MaterialTheme.typography.labelSmall)
                        price.discountProofs.forEach { SavedDiscountProof(it) }
                    }
                }
                is AcquisitionSavedEvidence.Unavailable -> Text(evidence.explanation)
                is AcquisitionSavedEvidence.Unsupported -> Text(evidence.explanation)
                is AcquisitionSavedEvidence.Malformed -> Text(evidence.explanation)
            }
        }
        if (receipt.replayed) Text("Показан сохранённый результат, без повторной оплаты", style = MaterialTheme.typography.bodySmall)
        if (onUndo != null) TextButton(onClick = onUndo, Modifier.testTag("acquisition.undo")) { Text("Проверить отмену") }
    }
}

private fun savedOperationType(type:LedgerEntryType):String=when(type) {
    LedgerEntryType.Purchase -> "покупка"
    LedgerEntryType.Undo -> "отмена"
    LedgerEntryType.Grant -> "награда"
    LedgerEntryType.ManualCorrection -> "ручная поправка"
    LedgerEntryType.NonFinancial -> "нефинансовая правка"
    LedgerEntryType.CompleteCreation -> "завершение создания"
    LedgerEntryType.ConfirmLegacyParameter -> "уточнение прежнего параметра"
    LedgerEntryType.Import -> "импорт"
    LedgerEntryType.SyncAdoption -> "принятая синхронизация"
}
@Composable
private fun SavedDiscountProof(proof:RequirementResult,depth:Int=0) {
    Column(Modifier.padding(start=(depth.coerceAtMost(3)*8).dp)) {
        Text("Сохранённое условие скидки: ${statusText(proof.status)}",style=MaterialTheme.typography.bodySmall)
        proof.reason?.let { Text(it,style=MaterialTheme.typography.labelSmall) }
        proof.witnesses.forEach { witness -> Text(when(witness) {
            is RequirementWitness.OwnedInstance -> "Записан экземпляр ранга ${witness.rank}"
            is RequirementWitness.Branch -> "Записан выбранный доступ к ветке"
            is RequirementWitness.LearnedSpell -> "Записано изученное заклинание"
            is RequirementWitness.External -> "Записанное внешнее подтверждение: ${witness.sourceRef.originalText}"
        },style=MaterialTheme.typography.labelSmall) }
        proof.children.forEach { SavedDiscountProof(it,depth+1) }
    }
}

@Composable
private fun RequirementTree(value: AcquisitionRequirementView, model: AcquisitionUiModel, actions: DesktopAcquisitionActions?, depth: Int = 0, allowConfirmation: Boolean = true) {
    Column(Modifier.padding(start = (depth.coerceAtMost(3) * 8).dp)) {
        Text("${statusText(value.status)} · ${value.label}", style = MaterialTheme.typography.bodySmall)
        if (value.actual != null || value.required != null) Text(
            "Факт: ${acquisitionRequirementValue(value.actual, value.label)} · Требуется: ${acquisitionRequirementValue(value.required, value.label)}",
            modifier = Modifier.testTag("acquisition.requirement.values"), style = MaterialTheme.typography.labelSmall)
        if (value.sourceText.isNotBlank()) Text(value.sourceText, style = MaterialTheme.typography.labelSmall)
        value.external?.takeIf { allowConfirmation && actions != null }?.let { external ->
            val checked = model.request?.externalConfirmations?.any { it.evidenceKey == external.evidenceKey && it.scopeId == external.scopeId } == true
            Row {
                Checkbox(checked, { actions?.confirmExternal(model, external, it) }, enabled = model.phase in setOf(AcquisitionUiPhase.Ready, AcquisitionUiPhase.ChoiceRequired, AcquisitionUiPhase.Blocked, AcquisitionUiPhase.Stale))
                Text("Подтверждаю именно это внешнее условие", style = MaterialTheme.typography.bodySmall)
            }
        }
        value.children.forEach { RequirementTree(it, model, actions, depth + 1, allowConfirmation) }
    }
}
private fun statusText(status: RequirementStatus) = when(status) {
    RequirementStatus.Satisfied -> "Выполнено"
    RequirementStatus.Unsatisfied -> "Не выполнено"
    RequirementStatus.ChoiceRequired -> "Нужен выбор"
    RequirementStatus.SourceUnresolved -> "Источник не уточнён"
    RequirementStatus.Conflict -> "Конфликт"
}
@Composable
private fun WitnessTree(value: AcquisitionWitnessProof) {
    value.witnesses.forEach { Text(it.label, style = MaterialTheme.typography.bodySmall) }
    value.children.forEach { WitnessTree(it) }
}

@Composable
fun AcquisitionPurchaseSurface(model: AcquisitionUiModel, actions: DesktopAcquisitionActions) {
    val binding = bindDesktopAcquisition(model)
    val focus=remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val mutable = model.phase in setOf(AcquisitionUiPhase.Ready, AcquisitionUiPhase.ChoiceRequired, AcquisitionUiPhase.Blocked, AcquisitionUiPhase.Stale)
    FuryDialog(onDismissRequest = actions::dismiss,
        modifier = Modifier.testTag("acquisition.preview").semantics { acquisitionPhase = model.phase.name }
            .onPreviewKeyEvent { event -> if(event.key==Key.Escape && event.type==KeyEventType.KeyDown) { actions.dismiss();true } else false }
            .focusRequester(focus).focusable(),
        title = { Text("Просмотр операции") },
        text = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                actions.reviewLabel?.takeIf { it.first == model.key }?.let { Text(it.second) }
                AcquisitionPlanContent(model, actions)
                actions.details?.takeIf { it.key == model.key }?.let { details ->
                    details.requirements.forEach { proof ->
                        Text("Подтверждённые факты до операции", style = MaterialTheme.typography.labelLarge); WitnessTree(proof.baseProof)
                        Text("Факты выбранного плана", style = MaterialTheme.typography.labelLarge); WitnessTree(proof.proof)
                    }
                    details.abilityChoices.forEach { choice ->
                        Text(choice.label)
                        if (choice.unresolved) Text("Варианты доступа не подтверждены источником")
                        choice.options.forEach { option -> OutlinedButton(
                            onClick = { actions.selectAbility(model, choice.targetIndex, option.id) }, enabled = mutable,
                            modifier = Modifier.testTag("acquisition.choice")) {
                            Text("${if (choice.selectedId == option.id) "✓ " else ""}${option.label}: ${acquisitionPrice(option.price)}")
                        } }
                    }
                }
                model.choices.forEach { choice ->
                    Text(choice.label)
                    choice.options.forEach { option -> OutlinedButton(onClick = { actions.selectChoice(model, choice.id, option.id) },
                        enabled = mutable, modifier = Modifier.testTag("acquisition.choice")) {
                        Text("${if (choice.selectedId == option.id) "✓ " else ""}${option.label}: ${acquisitionPrice(option.price)}")
                    } }
                    if (choice.options.isEmpty()) {
                        Text("Нужен точный выбор экземпляра или параметра; автоматический выбор невозможен")
                        TextButton(onClick = { actions.choosePrerequisite(model, choice.id) }, enabled = mutable,
                            modifier = Modifier.testTag("acquisition.prerequisite.open")) { Text("Выбрать требования явно") }
                    }
                }
                model.selections.forEach { prompt -> SelectionEditor(model, prompt, actions, mutable) }
                model.blocks.forEach { block -> Text(listOfNotNull(block.label, block.message, block.sourceText).joinToString("\n")) }
                model.receipt?.let { AcquisitionReceiptSummary(it, actions::undo) }
                model.viewRefreshError?.let { Text(it) }
                Text(binding.sync, Modifier.testTag("acquisition.sync.status"), style = MaterialTheme.typography.bodySmall)
                if (model.phase == AcquisitionUiPhase.Stale && model.request != null)
                    TextButton(onClick = { actions.refresh(model.key) }, Modifier.testTag("acquisition.refresh")) { Text("Обновить цену и проверить заново") }
                if (model.canRetry) TextButton(onClick = actions::retry, Modifier.testTag("acquisition.retry")) { Text("Повторить тот же запрос") }
                if (actions.state.acquisition.retainedOperationId != null)
                    TextButton(onClick = actions::reconcile, Modifier.testTag("acquisition.reconcile")) { Text("Проверить сохранённый результат") }
            }
        },
        confirmButton = { TextButton(onClick = { actions.confirm(model.key) }, enabled = binding.canConfirm,
            modifier = Modifier.testTag("acquisition.confirm")) { Text("Подтвердить") } },
        dismissButton = { TextButton(onClick = actions::dismiss, Modifier.testTag("acquisition.cancel")) {
            Text(if (model.phase == AcquisitionUiPhase.Applying) "Скрыть, не отменяя запись" else "Закрыть")
        } })
}

@Composable
private fun SelectionEditor(model: AcquisitionUiModel, prompt: AcquisitionSelectionPrompt, actions: DesktopAcquisitionActions, enabled: Boolean) {
    if (prompt.instances.size <= 1 && prompt.parameters.isEmpty() && !prompt.canCreateNew) return
    var parameters by remember(model.scope, prompt.definition) { mutableStateOf(emptyMap<String, String>()) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Экземпляр: ${prompt.label}")
        prompt.instances.forEach { item -> OutlinedButton(onClick = { actions.selectInstance(model, prompt, item) }, enabled = enabled,
            modifier = Modifier.testTag("acquisition.choice")) { Text("${item.label} · ранг ${item.rank}") } }
        prompt.parameters.forEach { field ->
            Text(field.label)
            if (field.unresolved) Text("Допустимые параметры не подтверждены источником")
            field.options.forEach { option -> FilterChip(selected = parameters[field.key] == option.id,
                onClick = { parameters = parameters + (field.key to option.id) }, enabled = enabled,
                label = { Text(option.label) }, modifier = Modifier.testTag("acquisition.choice")) }
            if (field.userDefined) OutlinedTextField(parameters[field.key].orEmpty(), { parameters = parameters + (field.key to it) },
                label = { Text("Явный параметр специализации") }, enabled = enabled, modifier = Modifier.testTag("acquisition.choice"))
        }
        if (prompt.canCreateNew) TextButton(onClick = { actions.selectNew(model, prompt, parameters) },
            enabled = enabled && prompt.parameters.all { !it.unresolved && !parameters[it.key].isNullOrBlank() },
            modifier = Modifier.testTag("acquisition.choice")) { Text("Новый экземпляр с этими параметрами") }
    }
}

@Composable
fun AcquisitionManualEditor(draft: DesktopManualDraft, actions: DesktopAcquisitionActions) {
    var xp by remember(draft) { mutableStateOf("") }; var ap by remember(draft) { mutableStateOf("") }
    var reason by remember(draft) { mutableStateOf("") }
    val delta = parseManualAmount(xp, ap, reason)
    FuryDialog(onDismissRequest = actions::closeManual, title = { Text(if (draft.grant) "Выдача XP / ОСп" else "Ручное отклонение") },
        text = { Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(draft.label); Text("Суммы укажите явно, включая 0. Возврат по сегодняшним ценам не вычисляется.")
            OutlinedTextField(xp, { xp = it }, label = { Text("Изменение XP со знаком") }, modifier = Modifier.testTag("acquisition.manual.xp"))
            OutlinedTextField(ap, { ap = it }, label = { Text("Изменение ОСп со знаком") }, modifier = Modifier.testTag("acquisition.manual.ap"))
            OutlinedTextField(reason, { reason = it }, label = { Text("Причина") }, modifier = Modifier.testTag("acquisition.manual.reason"))
            Text("Ручная коррекция не подтверждает источник и не возвращает права этапа создания.")
        } }, confirmButton = { TextButton(onClick = { actions.reviewManual(draft, checkNotNull(delta), reason) }, enabled = delta != null,
            modifier = Modifier.testTag("acquisition.manual.review")) { Text("Просмотреть") } },
        dismissButton = { TextButton(onClick = actions::closeManual) { Text("Отмена") } })
}

@Composable
fun DesktopAcquisitionRoot(state: DesktopAppState, content: @Composable () -> Unit) {
    val scope = rememberCoroutineScope()
    val actions = remember(state) { DesktopAcquisitionActions(state, scope) }
    val character = state.snapshot.activeCharacterId
    val model = state.acquisitionUiModel
    LaunchedEffect(state, character) { actions.hideForSelection(); actions.reload() }
    val canonicalLoad = state.localStoredCharacter()
    val canonicalRevision = (canonicalLoad as? LoadResult.Loaded)?.state?.let { it.revision to it.storageRevision }
    // A successful migration/import may preserve the character snapshot. Refresh the read view on
    // the acknowledged outcome/revision, not actions.view, so equivalent reads cannot self-trigger.
    LaunchedEffect(state.snapshot, state.contentRevision, canonicalLoad::class, canonicalRevision) { actions.reload() }
    LaunchedEffect(model?.key, model?.phase) { if (model != null) actions.loadDetails(model) }
    DisposableEffect(actions) {
        state.onCanonicalNonFinancialReview = actions::nonFinancial
        onDispose { state.onCanonicalNonFinancialReview = null }
    }
    CompositionLocalProvider(LocalDesktopAcquisition provides actions) {
        content()
        if (actions.visible && model != null) AcquisitionPurchaseSurface(model, actions)
        actions.manual?.let { AcquisitionManualEditor(it, actions) }
        actions.error?.let { message -> FuryDialog(onDismissRequest = actions::clearError, title = { Text("Операция не выполнена") },
            text = { Text(message) }, confirmButton = { TextButton(onClick = actions::clearError) { Text("Понятно") } }) }
        if (actions.historyVisible) FuryDialog(onDismissRequest = actions::closeHistory, title = { Text("История операций") },
            text = { Column(Modifier.fillMaxWidth().testTag("acquisition.history"),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(actions.view?.balance?.let(::acquisitionAmount) ?: "Баланс недоступен", Modifier.testTag("acquisition.balance"))
                AcquisitionHistoryContent(actions.view?.history.orEmpty())
                if (actions.view?.history?.isEmpty() == true) Text("Открывающий баланс сохранён без выдуманной истории")
            } }, confirmButton = { TextButton(onClick = { actions.closeHistory(); actions.undo() }, Modifier.testTag("acquisition.undo")) { Text("Проверить отмену последней операции") } },
            dismissButton = { TextButton(onClick = actions::closeHistory) { Text("Закрыть") } })
        if (actions.fundingEditor) FundingEditor(actions)
        actions.notice?.let { message -> FuryDialog(onDismissRequest = actions::clearNotice, title = { Text("Результат операции") },
            text = { Text(message) }, confirmButton = { TextButton(onClick = actions::clearNotice) { Text("Понятно") } }) }
        actions.prerequisite?.let { (review, question) -> actions.view?.let { current ->
            PrerequisiteSelectionEditor(review, question, current, { actions.selectPrerequisites(review, question, it) }, actions::closePrerequisite)
        } }
        actions.picker?.let { kind -> FuryDialog(onDismissRequest = actions::dismissPicker, title = { Text("Выберите запись каталога") },
            text = { Column(Modifier.fillMaxWidth()) {
                actions.view?.catalog?.definitions?.values?.filter { it.kind == kind }?.sortedBy { it.name }?.forEach { definition ->
                    TextButton(onClick = { actions.pickDefinition(definition) }, Modifier.testTag("acquisition.open").semantics {
                        acquisitionDefinition = "${definition.ref.packId}:${definition.ref.entryId}"
                    }) { Text(AcquisitionLabels(actions.view!!.catalog).definition(definition.ref)) }
                }
            } }, confirmButton = { TextButton(onClick = actions::dismissPicker) { Text("Отмена") } }) }
    }
}

@Composable
private fun FundingEditor(actions: DesktopAcquisitionActions) {
    var xp by remember { mutableStateOf("") }; var name by remember { mutableStateOf("Новый персонаж") }
    val amount = xp.toLongOrNull()?.takeIf { it in 0..Int.MAX_VALUE.toLong() }
    FuryDialog(onDismissRequest = actions::closeFunding, title = { Text("Новый персонаж") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(name, { name = it }, label = { Text("Имя") })
            OutlinedTextField(xp, { xp = it }, label = { Text("Начальный XP") }, modifier = Modifier.testTag("acquisition.funding.xp"))
            Text("ОСп рассчитает подтверждённое правило в следующем просмотре. Существующие персонажи не меняются.")
        }
    }, confirmButton = { TextButton(onClick = { actions.reviewFunding(checkNotNull(amount), name) }, enabled = amount != null,
        modifier = Modifier.testTag("acquisition.funding.review")) { Text("Просмотреть баланс") } },
        dismissButton = { TextButton(onClick = actions::closeFunding) { Text("Отмена") } })
}


/** Explicit user input, not inferred eligibility. Shared checks the complete selected targets. */
@Composable
internal fun PrerequisiteSelectionEditor(model: AcquisitionUiModel, questionId: String,
    view: AcquisitionCharacterView, onReview: (List<AcquisitionTarget>) -> Unit, onDismiss: () -> Unit) {
    var selected by remember(model.key, questionId) { mutableStateOf(model.request?.prerequisiteSelections?.get(questionId).orEmpty()) }
    var query by remember { mutableStateOf("") }
    var definition by remember { mutableStateOf<AcquisitionDefinition?>(null) }
    var rank by remember { mutableStateOf("1") }
    var parameters by remember(definition) { mutableStateOf(emptyMap<String, String>()) }
    var option by remember(definition) { mutableStateOf<String?>(null) }
    val labels = remember(view.catalog) { AcquisitionLabels(view.catalog) }
    val targetRank = rank.toIntOrNull()?.takeIf { it > 0 }
    FuryDialog(onDismissRequest = onDismiss, title = { Text("Явный выбор требований") }, text = {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Выберите точные экземпляры и требуемые ранги. Это только запрос: условия, цену и допустимость проверит общий расчёт.")
            OutlinedTextField(rank, { rank = it }, label = { Text("Целевой ранг выбранного экземпляра") }, modifier = Modifier.testTag("acquisition.prerequisite.rank"))
            view.instances.forEach { instance ->
                OutlinedButton(onClick = {
                    selected = selected.filterNot { it.instanceId == instance.instanceId } + AcquisitionTarget(instance.definition,
                        instance.instanceId, checkNotNull(targetRank), instance.parameters, instance.optionId)
                }, enabled = targetRank != null, modifier = Modifier.testTag("acquisition.choice")) { Text("${instance.label} · сейчас ${instance.rank}") }
            }
            OutlinedTextField(query, { query = it }, label = { Text("Поиск новой записи") }, modifier = Modifier.testTag("acquisition.prerequisite.search"))
            if (query.isNotBlank()) view.catalog.definitions.values.filter { labels.definition(it.ref).contains(query, true) }
                .sortedWith(compareBy({ it.name }, { it.ref.packId }, { it.ref.entryId })).take(50).forEach { candidate ->
                    TextButton(onClick = { definition = candidate }) { Text(labels.definition(candidate.ref)) }
                }
            definition?.let { value ->
                Text(labels.definition(value.ref), style = MaterialTheme.typography.titleSmall)
                value.parameterDefinitions.forEachIndexed { index, field ->
                    Text("Параметр ${index + 1}")
                    when (val domain = field.domain) {
                        is ParameterDomain.StableIds -> domain.valueIds.sorted().forEachIndexed { number, id ->
                            FilterChip(selected = parameters[field.key] == id, onClick = { parameters = parameters + (field.key to id) },
                                label = { Text(view.instances.firstOrNull { it.instanceId == id }?.label ?: "Вариант ${number + 1}") })
                        }
                        ParameterDomain.UserDefinedIds -> OutlinedTextField(parameters[field.key].orEmpty(),
                            { parameters = parameters + (field.key to it) }, label = { Text("Явный параметр специализации") })
                        is ParameterDomain.Unresolved -> Text("Допустимые параметры не подтверждены")
                    }
                }
                value.abilityOptionIds?.sorted()?.forEachIndexed { number, id ->
                    FilterChip(selected = option == id, onClick = { option = id },
                        label = { Text(when (id) { "morale" -> "Мораль"; "chi" -> "Ци"; else -> "Вариант ${number + 1}" }) })
                }
                val abilityOptions = value.abilityOptionIds
                val paramsValid = value.parameterDefinitions.all { it.domain !is ParameterDomain.Unresolved && !parameters[it.key].isNullOrBlank() }
                TextButton(onClick = {
                    selected = selected + AcquisitionTarget(value.ref, "ui-choice:${UUID.randomUUID()}", checkNotNull(targetRank), parameters, option)
                    definition = null; query = ""
                }, enabled = targetRank != null && paramsValid && abilityOptions != null &&
                    (abilityOptions.isEmpty() || option in abilityOptions) && value.repeatPolicy !is RepeatPolicy.Unresolved) { Text("Добавить новый экземпляр в запрос") }
            }
            Text("Выбрано: ${selected.size}")
            selected.forEachIndexed { index, target -> Row {
                Text("${labels.definition(target.definition)} · ранг ${target.targetRank}", Modifier.weight(1f))
                TextButton(onClick = { selected = selected.filterIndexed { i, _ -> i != index } }) { Text("Убрать") }
            } }
        }
    }, confirmButton = { TextButton(onClick = { onReview(selected) }, enabled = selected.isNotEmpty(),
        modifier = Modifier.testTag("acquisition.prerequisite.review")) { Text("Пересчитать выбранный план") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } })
}

/** The mutable Shared cache has one serialized preparation lane per root. */
internal class DesktopDependencyHintCache {
    private val cache=AcquisitionDependencyHintCache()
    @Synchronized fun get(catalog:AcquisitionCatalog)=cache.get(catalog)
}
@Composable
internal fun AcquisitionRequirementRows(model:AcquisitionUiModel, actions:DesktopAcquisitionActions) {
    if(model.baseRequirements.isNotEmpty()) { Text("До операции");model.baseRequirements.forEach { RequirementTree(it,model,actions,allowConfirmation=false) } }
    if(model.requirements.isNotEmpty()) { Text("После выбранного плана");model.requirements.forEach { RequirementTree(it,model,actions) } }
}
@Composable
internal fun AcquisitionWitnessRows(details:AcquisitionReviewDetails?) {
    details?.requirements?.forEach { Text("Факты до операции");WitnessTree(it.baseProof);Text("Факты выбранного плана");WitnessTree(it.proof) }
}

@Composable
internal fun AcquisitionPlanContent(model:AcquisitionUiModel, actions:DesktopAcquisitionActions? = null) {
    val binding=bindDesktopAcquisition(model)
    Column(Modifier.testTag("acquisition.plan.content"),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text(binding.status, Modifier.testTag("acquisition.status"))
                model.funding?.let {
                    Text("Начальный XP: ${it.initialXP}")
                    Text("Начальный ОСп: ${it.initialAP?.toString() ?: "не определён"}")
                    Text("Только однократное создание нового персонажа. Это не обмен валют и не выдача за последующий XP.")
                    it.blockReason?.let { Text("Создание пока недоступно") }
                }
                Text("Итого выбранного плана (цель и включённые требования)",style=MaterialTheme.typography.labelSmall)
                Text(binding.price, Modifier.testTag("acquisition.price"))
                binding.remaining?.let { Text("После: $it", Modifier.testTag("acquisition.balance")) }
                model.balanceDelta?.let { Text("Изменение баланса: ${acquisitionAmount(it)}") }
                model.reason?.let { Text("Причина: $it") }
                model.rows.forEach { row -> Column(Modifier.semantics {
                    acquisitionDefinition = row.definition?.let { "${it.packId}:${it.entryId}" }.orEmpty()
                }) {
                    Text(acquisitionRowLabel(row))
                    Text(when { row.excludedTarget -> "Цель исключена; не новая покупка"; row.alreadyOwned -> "Уже имеется; не новая покупка";
                        row.isTarget -> "Переход цели"; else -> "Переход требования" },style=MaterialTheme.typography.labelSmall)
                    Text(acquisitionPrice(row.price),Modifier.testTag("acquisition.row.price"),style=MaterialTheme.typography.bodySmall)
                } }
                if (model.baseRequirements.isNotEmpty()) {
                    Text("До операции", style = MaterialTheme.typography.titleSmall)
                    model.baseRequirements.forEach { RequirementTree(it, model, actions, allowConfirmation = false) }
                }
                if (model.requirements.isNotEmpty()) {
                    Text("После выбранного плана", style = MaterialTheme.typography.titleSmall)
                    model.requirements.forEach { RequirementTree(it, model, actions) }
                }
    }
}
@Composable
internal fun AcquisitionHistoryContent(history:List<AcquisitionReceiptView>, current:Amount? = null) {
    Column(Modifier.testTag("acquisition.history.content"),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        current?.let { Text(acquisitionAmount(it),Modifier.testTag("acquisition.history.balance")) }
        if(history.isEmpty()) Text("Нет подтверждённой покупки",Modifier.testTag("acquisition.history.empty"))
        history.asReversed().forEach { AcquisitionReceiptSummary(it) }
    }
}
