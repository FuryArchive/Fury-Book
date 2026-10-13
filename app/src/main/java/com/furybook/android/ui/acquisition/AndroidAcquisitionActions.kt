package com.furybook.android.ui.acquisition

import androidx.compose.runtime.*
import com.furybook.android.state.CharacterController
import com.furybook.dubl.acquisition.*
import com.furybook.dubl.application.DublApplication
import com.furybook.dubl.model.*
import kotlinx.coroutines.*

/** Root-owned rendering callbacks only. The Shared controller owns every retained command. */
class AndroidAcquisitionActions(val controller: CharacterController, private val scope: CoroutineScope) {
    var visible by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    var notice by mutableStateOf<String?>(null); private set
    var prerequisite by mutableStateOf<Pair<AcquisitionUiModel, String>?>(null); private set
    var confirmRole by mutableStateOf<Pair<AcquisitionQuoteKey, String>?>(null); private set
    var reviewLabel by mutableStateOf<Pair<AcquisitionQuoteKey, String>?>(null); private set
    var view by mutableStateOf<AcquisitionCharacterView?>(null); private set
    var readUnavailable by mutableStateOf(false); private set
    var details by mutableStateOf<AcquisitionReviewDetails?>(null); private set
    var manual by mutableStateOf<AndroidManualDraft?>(null); private set
    var fundingEditor by mutableStateOf(false); private set
    var historyVisible by mutableStateOf(false); private set
    var picker by mutableStateOf<AcquisitionKind?>(null); private set
    private var pickerRank = 1
    val canonical get() = controller.isCanonicalProtected

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
        val character = controller.active.id
        view = null; readUnavailable = false
        val loaded = try { controller.acquisition.readCanonicalView() } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { null }
        if (generation == readGeneration && character == controller.active.id) { view = loaded; readUnavailable = loaded == null }
    }
    fun showError(message: String) { error = message }
    fun clearError() { error = null }
    fun clearNotice() { notice = null }
    fun choosePrerequisite(model: AcquisitionUiModel, question: String) { prerequisite = model to question }
    fun closePrerequisite() { prerequisite = null }
    fun selectPrerequisites(model: AcquisitionUiModel, question: String, targets: List<AcquisitionTarget>) {
        val request = model.request ?: return
        action {
            controller.acquisition.replaceSelections(model.key, AcquisitionRequest(request.characterId, request.targets, request.choiceSelections, request.includeTargets, request.prerequisiteSelections + (question to targets), request.externalConfirmations))
            prerequisite = null
        }
    }
    fun hideForSelection() { visible = false; details = null; manual = null; picker = null; error = null; historyVisible = false; fundingEditor = false; prerequisite = null; reviewLabel = null; confirmRole = null }
    fun dismiss() { controller.acquisition.dismiss(); visible = false; details = null }
    fun reopen() { controller.acquisition.reopen(); visible = true }
    fun open(request: AcquisitionRequest) = action { visible = true; details = null; controller.acquisition.preview(request) }
    fun confirm(key: AcquisitionQuoteKey) = action {
        val result = controller.acquisition.confirm(key)
        if (result.funding != null && result.phase == AcquisitionUiPhase.Committed) {
            visible = false; notice = "Новый персонаж создан. Начальный баланс сохранён однократно."
        }
        reload()
    }
    fun refresh(key: AcquisitionQuoteKey) = action { controller.acquisition.refresh(key) }
    fun retry() = action { controller.acquisition.retrySameCommand(); reload() }
    fun reconcile() = action { controller.acquisition.reconcilePending(); reload() }
    fun undo() = action { visible = true; val review = controller.acquisition.prepareUndo(); confirmRole = review.key to "acquisition.undo.confirm" }
    fun completeCreation() = action { visible = true; controller.acquisition.prepareCompleteCreation() }
    fun nonFinancial(edit: DublApplication.() -> Unit) = action {
        visible = true; controller.acquisition.prepareNonFinancial(edit)
    }
    fun selectChoice(model: AcquisitionUiModel, id: String, option: String) {
        val request = model.request ?: return
        val next = AcquisitionRequest(request.characterId, request.targets, request.choiceSelections + (id to option),
            request.includeTargets, request.prerequisiteSelections, request.externalConfirmations)
        action { controller.acquisition.replaceSelections(model.key, next) }
    }
    fun selectInstance(model: AcquisitionUiModel, prompt: AcquisitionSelectionPrompt, instance: AcquisitionInstanceView) = action {
        controller.acquisition.selectTarget(model.key, prompt.targetIndex, instance.instanceId,
            parameters = instance.parameters, optionId = instance.optionId)
    }
    fun selectNew(model: AcquisitionUiModel, prompt: AcquisitionSelectionPrompt, parameters: Map<String, String>) = action {
        controller.acquisition.selectTarget(model.key, prompt.targetIndex, null, newInstance = true, parameters = parameters)
    }
    fun selectAbility(model: AcquisitionUiModel, index: Int, option: String) = action {
        controller.acquisition.selectAbilityOption(model.key, index, option)
    }
    fun confirmExternal(model: AcquisitionUiModel, node: RequirementNode.ExternalEvidence, checked: Boolean) {
        val request = model.request ?: return
        val previous = request.externalConfirmations.filterNot { it.evidenceKey == node.evidenceKey && it.scopeId == node.scopeId }
        val confirmations = if (checked) previous + RequirementWitness.External(node.evidenceKey, node.scopeId,
            java.util.UUID.randomUUID().toString(), node.sourceRef) else previous
        action { controller.acquisition.replaceSelections(model.key, AcquisitionRequest(request.characterId, request.targets,
            request.choiceSelections, request.includeTargets, request.prerequisiteSelections, confirmations)) }
    }
    suspend fun loadDetails(model: AcquisitionUiModel) {
        details = null
        if (model.request == null || model.phase == AcquisitionUiPhase.Applying || model.phase == AcquisitionUiPhase.Committed) return
        val loaded = try { controller.acquisition.readReviewDetails(model.key) } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { null }
        if (controller.acquisitionUiModel?.key == model.key) details = loaded
    }
    fun target(kind: AcquisitionKind, id: String, rank: Int): AcquisitionTarget? {
        val current = view?.takeIf { it.scope.viewCharacterId == controller.active.id } ?: return null
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
        open(AcquisitionRequest(controller.active.id, listOf(target), includeTargets = includeTarget))
    }
    fun openBulk(ids: Collection<String>, includeTargets: Boolean = true) {
        val targets = ids.sorted().map { id -> target(AcquisitionKind.DEVELOPMENT, id,
            (view?.state?.instances?.filter { it.definition.entryId == id }?.maxOfOrNull { it.rank } ?: 0) + 1) }
        if (targets.any { it == null }) { error = "Одна из целей не имеет однозначной записи каталога"; return }
        open(AcquisitionRequest(controller.active.id, targets.filterNotNull(), includeTargets = includeTargets))
    }
    fun chooseDefinition(kind: AcquisitionKind, rank: Int = 1) { pickerRank = rank; picker = kind }
    fun dismissPicker() { picker = null }
    fun pickDefinition(definition: AcquisitionDefinition) { picker = null; open(AcquisitionRequest(controller.active.id,
        listOf(AcquisitionTarget(definition.ref, targetRank = pickerRank)))) }
    fun showHistory() { historyVisible = true; action { reload() } }
    fun closeHistory() { historyVisible = false }
    fun newCharacter() { fundingEditor = true }
    fun closeFunding() { fundingEditor = false }
    fun reviewFunding(xp: Long, name: String) {
        action { controller.acquisition.reviewNewCharacterFunding(xp, name); fundingEditor = false; visible = true }
    }
    fun openManual(label: String = "Исправление баланса", character: DublCharacter? = null,
        instances: List<AcquisitionInstance>? = null, grant: Boolean = false) {
        val current = view ?: run { error = "Сначала откройте сохранённый канонический лист"; return }
        manual = AndroidManualDraft(current, label, CharacterPatch.fromCanonicalState(current.state,
            character ?: current.state.character, instances), grant)
    }
    /** Narrow edits always start from the acknowledged base, never the runtime-filtered sheet. */
    fun editManual(label: String, change: (DublCharacter) -> DublCharacter) {
        val current = view ?: run { error = "Сначала откройте сохранённый канонический лист"; return }
        openManual(label, change(current.state.character))
    }
    fun closeManual() { manual = null }
    fun reviewManual(draft: AndroidManualDraft, delta: Amount, reason: String) = action {
        if (draft.base.scope.viewCharacterId != controller.active.id) error("Character changed")
        val current = controller.acquisition.readCanonicalView()
        if (current.scope != draft.base.scope || current.state.revision != draft.base.state.revision) error("Review changed")
        val review = if (draft.grant) controller.acquisition.prepareGrant(delta, reason)
        else controller.acquisition.prepareManual(draft.patch, delta, reason)
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

data class AndroidManualDraft(val base: AcquisitionCharacterView, val label: String, val patch: CharacterPatch, val grant: Boolean)
val LocalAndroidAcquisition = staticCompositionLocalOf<AndroidAcquisitionActions?> { null }
