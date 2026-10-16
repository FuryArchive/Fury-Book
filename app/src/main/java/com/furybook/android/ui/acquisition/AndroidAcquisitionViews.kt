package com.furybook.android.ui.acquisition

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.furybook.android.state.CharacterController
import com.furybook.dubl.acquisition.*
import com.furybook.ui.components.SkillSpecializationPicker
import com.furybook.ui.components.AcquisitionParameterPicker
import com.furybook.ui.components.DublCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

val AcquisitionDefinitionKey = SemanticsPropertyKey<String>("AcquisitionDefinition")
var SemanticsPropertyReceiver.acquisitionDefinition by AcquisitionDefinitionKey
val AcquisitionPhaseKey = SemanticsPropertyKey<String>("AcquisitionPhase")
var SemanticsPropertyReceiver.acquisitionPhase by AcquisitionPhaseKey

@Composable
fun AcquisitionCardSummary(model: AcquisitionUiModel, modifier: Modifier = Modifier) {
    val binding = bindAndroidAcquisition(model)
    Column(modifier.testTag("acquisition.card").semantics { acquisitionPhase = model.phase.name },
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(binding.price, Modifier.testTag("acquisition.price"), style = MaterialTheme.typography.labelLarge)
        binding.remaining?.let { Text("После: $it", style = MaterialTheme.typography.bodySmall) }
        Text(binding.status, Modifier.testTag("acquisition.status"), style = MaterialTheme.typography.bodySmall)
        model.effectNotices.forEach { Text(it, Modifier.testTag("acquisition.effect.notice"), style = MaterialTheme.typography.bodySmall) }
    }
}

/** Quotes never replace the root's active or uncertain command. */
@Composable
fun AcquisitionEntrySummary(kind: AcquisitionKind, id: String, rank: Int, modifier: Modifier = Modifier) {
    val actions = LocalAndroidAcquisition.current ?: return
    if (!actions.canonical) return
    val snapshot = actions.view
    var review by remember(snapshot, kind, id, rank) { mutableStateOf<AcquisitionReview?>(null) }
    LaunchedEffect(snapshot, kind, id, rank) {
        val target = actions.target(kind, id, rank) ?: return@LaunchedEffect
        val request = AcquisitionRequest(actions.controller.active.id, listOf(target))
        review = try { withContext(Dispatchers.Default) { actions.controller.acquisition.quoteCardReview(request) } }
            catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled } catch (_: Exception) { null }
    }
    val shown = review
    Column(modifier.semantics { acquisitionDefinition = "$kind:$id" }) {
        if (shown == null) Text("Цена уточняется или запись недоступна", Modifier.testTag("acquisition.status"))
        else AcquisitionCardSummary(shown.model)
        TextButton(onClick = { shown?.model?.request?.let(actions::open) ?: actions.openTarget(kind, id, rank) }, Modifier.testTag("acquisition.open")) { Text("Просмотреть покупку") }
    }
}

@Composable
fun AcquisitionBalanceCard() {
    val actions = LocalAndroidAcquisition.current ?: return
    if (!actions.canonical) return
    DublCard(Modifier.fillMaxWidth()) {
        Text("Канонический журнал", style = MaterialTheme.typography.titleSmall)
        Text(actions.view?.balance?.let(::acquisitionAmount) ?: "Баланс недоступен", Modifier.testTag("acquisition.balance"))
        Text("Покупка всегда требует отдельного просмотра и подтверждения.", style = MaterialTheme.typography.bodySmall)
        if (!actions.controller.acquisitionCapabilities.localDurable) Text("Android: проверка надёжного сохранения на устройстве не завершена. Рабочие покупки пока выключены.", style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = actions::showHistory, Modifier.testTag("acquisition.history.open")) { Text("История") }
            TextButton(onClick = { actions.openManual() }, Modifier.testTag("acquisition.manual.open")) { Text("Исправление") }
            TextButton(onClick = { actions.openManual("Выдать XP / ОСп", grant = true) }) { Text("Выдать") }
        }
        TextButton(onClick = actions::completeCreation, enabled = actions.view?.state?.character?.creationComplete == false) { Text("Завершить создание") }
        if (actions.controller.acquisitionUiModel != null)
            TextButton(onClick = actions::reopen) { Text("Последняя операция") }
    }
}

@Composable
fun AcquisitionReceiptSummary(receipt: AcquisitionReceiptView, onUndo: (() -> Unit)? = null) {
    Column(Modifier.testTag("acquisition.receipt"), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(if (receipt.status == ReceiptStatus.Undone) "Операция отменена" else "Сохранённая операция")
        Text(acquisitionAmount(receipt.cost), Modifier.testTag("acquisition.price"))
        Text("Остаток тогда: ${acquisitionAmount(receipt.remaining)}")
        receipt.currentBalance?.let { Text("Остаток сейчас: ${acquisitionAmount(it)}") }
        receipt.rows.forEach { Text(acquisitionRowLabel(it) + " · " + acquisitionPrice(it.price)) }
        if (receipt.replayed) Text("Показан сохранённый результат, без повторной оплаты", style = MaterialTheme.typography.bodySmall)
        if (onUndo != null) TextButton(onClick = onUndo, Modifier.testTag("acquisition.undo")) { Text("Проверить отмену") }
    }
}

@Composable
private fun RequirementTree(value: AcquisitionRequirementView, model: AcquisitionUiModel, actions: AndroidAcquisitionActions, depth: Int = 0, allowConfirmation: Boolean = true) {
    Column(Modifier.padding(start = (depth.coerceAtMost(3) * 8).dp)) {
        Text("${statusText(value.status)} · ${value.label}", style = MaterialTheme.typography.bodySmall)
        if (value.actual != null || value.required != null) Text(
            "Факт: ${acquisitionRequirementValue(value.actual, value.label)} · Требуется: ${acquisitionRequirementValue(value.required, value.label)}",
            modifier = Modifier.testTag("acquisition.requirement.values"), style = MaterialTheme.typography.labelSmall)
        if (value.sourceText.isNotBlank()) Text(value.sourceText, style = MaterialTheme.typography.labelSmall)
        value.external?.takeIf { allowConfirmation }?.let { external ->
            val confirmation = acquisitionExternalConfirmation(value, model.request)
            Row {
                Checkbox(confirmation.checked, { actions.confirmExternal(model, external, it) }, enabled = !confirmation.retained && model.phase in setOf(AcquisitionUiPhase.Ready, AcquisitionUiPhase.ChoiceRequired, AcquisitionUiPhase.Blocked, AcquisitionUiPhase.Stale))
                Text(if (confirmation.retained) "Подтверждение сохранено с покупкой" else "Подтверждаю именно это внешнее условие", style = MaterialTheme.typography.bodySmall)
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
fun AcquisitionPurchaseSurface(model: AcquisitionUiModel, actions: AndroidAcquisitionActions) {
    val binding = bindAndroidAcquisition(model)
    val mutable = model.phase in setOf(AcquisitionUiPhase.Ready, AcquisitionUiPhase.ChoiceRequired, AcquisitionUiPhase.Blocked, AcquisitionUiPhase.Stale)
    AlertDialog(onDismissRequest = actions::dismiss,
        modifier = Modifier.testTag("acquisition.preview").semantics { acquisitionPhase = model.phase.name },
        title = { Text("Просмотр операции") },
        text = {
            Column(Modifier.heightIn(max = 620.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(binding.status, Modifier.testTag("acquisition.status"))
                model.effectNotices.forEach { Text(it, Modifier.testTag("acquisition.effect.notice")) }
                actions.reviewLabel?.takeIf { it.first == model.key }?.let { Text(it.second) }
                model.funding?.let {
                    Text("Начальный XP: ${it.initialXP}")
                    Text("Начальный ОСп: ${it.initialAP?.toString() ?: "не определён"}")
                    Text("Только однократное создание нового персонажа. Это не обмен валют и не выдача за последующий XP.")
                    it.blockReason?.let { Text("Создание пока недоступно") }
                }
                Text(binding.price, Modifier.testTag("acquisition.price"))
                binding.remaining?.let { Text("После: $it", Modifier.testTag("acquisition.balance")) }
                model.balanceDelta?.let { Text("Изменение баланса: ${acquisitionAmount(it)}") }
                model.reason?.let { Text("Причина: $it") }
                model.rows.forEach { row -> Text(acquisitionRowLabel(row), Modifier.semantics {
                    acquisitionDefinition = row.definition?.let { "${it.packId}:${it.entryId}" }.orEmpty()
                }) }
                if (model.baseRequirements.isNotEmpty()) {
                    Text("До операции", style = MaterialTheme.typography.titleSmall)
                    model.baseRequirements.forEach { RequirementTree(it, model, actions, allowConfirmation = false) }
                }
                if (model.requirements.isNotEmpty()) {
                    Text("После выбранного плана", style = MaterialTheme.typography.titleSmall)
                    model.requirements.forEach { RequirementTree(it, model, actions) }
                }
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
                if (actions.controller.acquisition.retainedOperationId != null)
                    TextButton(onClick = actions::reconcile, Modifier.testTag("acquisition.reconcile")) { Text("Проверить сохранённый результат") }
            }
        },
        confirmButton = { TextButton(onClick = { actions.confirm(model.key) }, enabled = binding.canConfirm,
            modifier = Modifier.testTag(actions.confirmRole?.takeIf { it.first == model.key }?.second ?: "acquisition.confirm")) { Text("Подтвердить") } },
        dismissButton = { TextButton(onClick = actions::dismiss, Modifier.testTag("acquisition.cancel")) {
            Text(if (model.phase == AcquisitionUiPhase.Applying) "Скрыть, не отменяя запись" else "Закрыть")
        } })
}

@Composable
private fun SelectionEditor(model: AcquisitionUiModel, prompt: AcquisitionSelectionPrompt, actions: AndroidAcquisitionActions, enabled: Boolean) {
    if (prompt.instances.size <= 1 && prompt.parameters.isEmpty() && !prompt.canCreateNew) return
    var parameters by remember(model.scope, prompt.definition, prompt.targetIndex, prompt.targetRank, prompt.selectedInstanceId) { mutableStateOf(prompt.selectedParameters) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Экземпляр: ${prompt.label}")
        val maximum = actions.view?.catalog?.definitions?.get(prompt.definition)?.maxRank
        prompt.instances.forEach { item -> OutlinedButton(onClick = { actions.selectInstance(model, prompt, item) },
            enabled = enabled && maximum != null && item.rank < maximum,
            modifier = Modifier.testTag("acquisition.choice")) {
            Text(if (maximum != null && item.rank >= maximum) "${item.label} · ранг ${item.rank} (максимум)"
                else "${item.label} · ранг ${item.rank} → ${item.rank + 1}")
        } }
        if (prompt.specializedSkill) {
            key(model.scope, prompt.definition, prompt.targetIndex) {
                SkillSpecializationPicker(prompt.parameters.flatMap { it.options }, enabled && prompt.canCreateNew) { name, choice ->
                    actions.selectNewSpecialization(model, prompt, name, choice)
                }
            }
        } else {
        AcquisitionParameterPicker(prompt.parameters, parameters, enabled, { java.util.UUID.randomUUID().toString() }, { parameters = it })
        if (prompt.canEditSelectedParameters) TextButton(onClick = { actions.selectParameters(model, prompt, parameters) },
            enabled = enabled && AcquisitionNamedParameters.complete(prompt.parameters, parameters),
            modifier = Modifier.testTag("acquisition.parameters.select")) { Text("Применить выбор") }
        if (prompt.canCreateNew) TextButton(onClick = { actions.selectNew(model, prompt, parameters) },
            enabled = enabled && AcquisitionNamedParameters.complete(prompt.parameters, parameters),
            modifier = Modifier.testTag("acquisition.choice")) { Text("Новая специализация с этими параметрами") }
        }
    }
}

@Composable
fun AcquisitionManualEditor(draft: AndroidManualDraft, actions: AndroidAcquisitionActions) {
    var xp by remember(draft) { mutableStateOf("") }; var ap by remember(draft) { mutableStateOf("") }
    var reason by remember(draft) { mutableStateOf("") }
    val delta = parseManualAmount(xp, ap, reason)
    AlertDialog(onDismissRequest = actions::closeManual, title = { Text(if (draft.grant) "Выдача XP / ОСп" else "Ручное отклонение") },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
fun AndroidAcquisitionRoot(controller: CharacterController, content: @Composable () -> Unit) {
    val scope = rememberCoroutineScope()
    val actions = remember(controller) { AndroidAcquisitionActions(controller, scope) }
    val character = controller.snapshot.activeCharacterId
    val model = controller.acquisitionUiModel
    LaunchedEffect(controller, character, controller.hasCanonicalActive) {
        actions.hideForSelection()
        if (controller.hasCanonicalActive) actions.reload()
    }
    LaunchedEffect(controller.snapshot, LocalAndroidAcquisitionContent.current) { if (controller.hasCanonicalActive) actions.reload() }
    LaunchedEffect(model?.key, model?.phase) { if (model != null) actions.loadDetails(model) }
    DisposableEffect(actions) {
        controller.onCanonicalNonFinancialReview = actions::nonFinancial
        onDispose { controller.onCanonicalNonFinancialReview = null }
    }
    CompositionLocalProvider(LocalAndroidAcquisition provides actions) {
        if (controller.acquisitionCapabilities.localDurable && !controller.hasCanonicalActive)
            AndroidLegacyMigrationPanel(controller, actions::newCharacter)
        else if (controller.gameplay != null && controller.activeStoredFormat == 1) {
            val captured = controller.gameplayScope()
            com.furybook.ui.components.LocalRulesPreparationPanel(captured,
                checkNotNull(controller.gameplayMigration), controller.exportActiveCharacter()) { controller.refreshAfterGameplay(captured) }
        } else content()
        if (actions.visible && model != null) AcquisitionPurchaseSurface(model, actions)
        actions.manual?.let { AcquisitionManualEditor(it, actions) }
        actions.error?.let { message -> AlertDialog(onDismissRequest = actions::clearError, title = { Text("Операция не выполнена") },
            text = { Text(message) }, confirmButton = { TextButton(onClick = actions::clearError) { Text("Понятно") } }) }
        if (actions.historyVisible) AlertDialog(onDismissRequest = actions::closeHistory, title = { Text("История операций") },
            text = { Column(Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()).testTag("acquisition.history"),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(actions.view?.balance?.let(::acquisitionAmount) ?: "Баланс недоступен", Modifier.testTag("acquisition.balance"))
                actions.view?.history?.asReversed()?.forEach { AcquisitionReceiptSummary(it) }
                if (actions.view?.history?.isEmpty() == true) Text("Открывающий баланс сохранён без выдуманной истории")
            } }, confirmButton = { TextButton(onClick = { actions.closeHistory(); actions.undo() }, Modifier.testTag("acquisition.undo")) { Text("Проверить отмену последней операции") } },
            dismissButton = { TextButton(onClick = actions::closeHistory) { Text("Закрыть") } })
        if (actions.fundingEditor) FundingEditor(actions)
        actions.notice?.let { message -> AlertDialog(onDismissRequest = actions::clearNotice, title = { Text("Результат операции") },
            text = { Text(message) }, confirmButton = { TextButton(onClick = actions::clearNotice) { Text("Понятно") } }) }
        actions.prerequisite?.let { (review, question) -> actions.view?.let { current ->
            PrerequisiteSelectionEditor(review, question, current, { actions.selectPrerequisites(review, question, it) }, actions::closePrerequisite)
        } }
        actions.picker?.let { kind -> AlertDialog(onDismissRequest = actions::dismissPicker, title = { Text("Выберите запись каталога") },
            text = { Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState())) {
                actions.view?.catalog?.definitions?.values?.filter { it.kind == kind }?.sortedBy { it.name }?.forEach { definition ->
                    TextButton(onClick = { actions.pickDefinition(definition) }, Modifier.testTag("acquisition.open").semantics {
                        acquisitionDefinition = "${definition.ref.packId}:${definition.ref.entryId}"
                    }) { Text(AcquisitionLabels(actions.view!!.catalog).definition(definition.ref)) }
                }
            } }, confirmButton = { TextButton(onClick = actions::dismissPicker) { Text("Отмена") } }) }
    }
}

@Composable
private fun FundingEditor(actions: AndroidAcquisitionActions) {
    var xp by remember { mutableStateOf("") }; var name by remember { mutableStateOf("Новый персонаж") }
    val amount = xp.toLongOrNull()?.takeIf { it in 0..Int.MAX_VALUE.toLong() }
    AlertDialog(onDismissRequest = actions::closeFunding, title = { Text("Новый персонаж") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(name, { name = it }, label = { Text("Имя") })
            OutlinedTextField(xp, { xp = it }, label = { Text("Начальный XP") }, modifier = Modifier.testTag("acquisition.funding.xp"))
            Text("ОСп рассчитает подтверждённое правило в следующем просмотре. Существующие персонажи не меняются.")
        }
    }, confirmButton = { TextButton(onClick = { actions.reviewFunding(checkNotNull(amount), name) }, enabled = amount != null,
        modifier = Modifier.testTag("acquisition.funding.review")) { Text("Просмотреть баланс") } },
        dismissButton = { TextButton(onClick = actions::closeFunding) { Text("Отмена") } })
}
