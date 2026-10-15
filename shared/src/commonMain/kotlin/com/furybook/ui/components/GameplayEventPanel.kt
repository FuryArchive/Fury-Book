package com.furybook.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.FilterChip
import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import com.furybook.dubl.actions.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun GameplayEventPanel(controller: GameplayEventController, scope: GameplayScope,
    storageRevision: Long, onCommitted: suspend () -> Unit) {
    var view by remember(controller, scope) { mutableStateOf<GameplayEventView?>(null) }
    var review by remember(controller, scope) { mutableStateOf(controller.review) }
    var busy by remember(controller, scope) { mutableStateOf(false) }
    var message by remember(controller, scope) { mutableStateOf<String?>(null) }
    var otherActor by remember(controller, scope) { mutableStateOf("") }
    var restMinutes by remember(controller, scope) { mutableStateOf("") }
    var restKind by remember(controller, scope) { mutableStateOf(RestKind.BRIEF) }
    var expanded by remember(controller, scope) { mutableStateOf(false) }
    var otherEvents by remember(controller, scope) { mutableStateOf(false) }
    var cost by remember(controller, scope) { mutableStateOf(1) }
    val jobs = rememberCoroutineScope()
    LaunchedEffect(controller, scope, storageRevision) {
        try { view = controller.readView().takeIf { it.scope == scope }; review = controller.review }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { message = failure.message ?: "События недоступны" }
    }
    fun run(action: suspend () -> Unit) {
        if (busy) return
        busy = true
        jobs.launch {
            try {
                message = null; action()
                view = controller.readView().takeIf { it.scope == scope }
                review = controller.review?.takeIf { it.scope == scope }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { message = failure.message ?: "Событие не завершено" }
            finally { busy = false }
        }
    }
    fun prepare(choice: GameBoundaryChoice) = run {
        controller.prepare(choice, checkNotNull(view) { "Обновите журнал событий" }.storageRevision)
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val timeline = view?.timeline
        if (timeline != null) {
            Text("ОД: ${timeline.combat.actionPoints} / 3 · Реакция: ${timeline.combat.reactions} / 1", style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
            if (timeline.combat.defenseBonus > 0) Text("Защита +${timeline.combat.defenseBonus} до следующего своего хода (P14265)")
        }
        TextButton(enabled = view != null, onClick = { expanded = !expanded }, modifier = Modifier.heightIn(min = 48.dp)) { Text(if (expanded) "Свернуть действия" else "Ходы и действия") }
        if (expanded) {
        Text("В начале своего хода: 3 ОД, 1 реакция. Здоровье и выносливость автоматически не восстанавливаются.")
        message?.let { Text(it) }
        if (timeline != null) {
            Text("Начато своих ходов: ${timeline.ownTurnOrdinal}. Завершено раундов: ${timeline.roundOrdinal}.")
            Text(timeline.openTurn?.let { if (it.actorId == scope.characterId) "Сейчас ваш ход" else "Сейчас ход: ${it.actorId}" } ?: "Открытого хода нет")
        }
        val shown = review
        if (shown != null) {
            Text(when (val event = shown.request.boundary) {
                is GameBoundary.TurnStarted -> if (event.actorId == scope.characterId) "Начать свой ход" else "Начать ход: ${event.actorId}"
                is GameBoundary.TurnEnded -> if (event.actorId == scope.characterId) "Завершить свой ход" else "Завершить ход: ${event.actorId}"
                is GameBoundary.RoundEnded -> "Завершить раунд"
                is GameBoundary.SceneEnded -> "Завершить сцену"
                is GameBoundary.RestCompleted -> "Записать отдых: ${event.kind.label()}, ${event.minutes} мин."
                is GameBoundary.ActionPointsSpent -> "Потратить ${event.count} ОД"
                is GameBoundary.ReactionSpent -> "Потратить реакцию"
                is GameBoundary.DefenseReserved -> "Отложить ${event.count} ОД: +${event.count} к Защите до начала своего следующего хода"
            })
            Text("ОД: ${shown.beforeCombat.actionPoints} → ${shown.afterCombat.actionPoints} · Реакция: ${shown.beforeCombat.reactions} → ${shown.afterCombat.reactions}")
            if (shown.beforeCombat.defenseBonus != shown.afterCombat.defenseBonus)
                Text("Временный бонус Защиты: +${shown.beforeCombat.defenseBonus} → +${shown.afterCombat.defenseBonus}")
            Text("Отмена записанных событий пока не поддерживается. Событие также закроет отмену предшествующего действия.")
            Button(enabled = !busy, onClick = { run {
                when (val result = controller.confirm(shown.request.operationId)) {
                    is GameplayEventCommitResult.Applied -> { onCommitted(); message = "Событие записано" }
                    is GameplayEventCommitResult.Replayed -> { onCommitted(); message = "Уже записано; повтор не применён" }
                    is GameplayEventCommitResult.Blocked -> message = result.reason
                    is GameplayEventCommitResult.StorageFailure -> message = "Исход неизвестен; повторите это же событие. ${result.reason}"
                }
            } }) { Text("Записать / повторить") }
            TextButton(enabled = !busy, onClick = { run { controller.dismiss() } }) { Text("Закрыть") }
        } else if (timeline != null) {
            OutlinedButton(enabled = !busy && timeline.openTurn == null, onClick = { prepare(GameBoundaryChoice.StartOwnTurn) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Начать свой ход") }
            OutlinedButton(enabled = !busy && timeline.openTurn != null, onClick = { prepare(GameBoundaryChoice.EndTurn) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Завершить текущий ход") }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                for (count in 1..3) FilterChip(selected = cost == count, onClick = { cost = count },
                    label = { Text("$count ОД") }, modifier = Modifier.heightIn(min = 48.dp))
            }
                OutlinedButton(enabled = !busy && timeline.openTurn?.actorId == scope.characterId && timeline.combat.actionPoints >= cost,
                    onClick = { prepare(GameBoundaryChoice.SpendActions(cost)) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Потратить $cost ОД") }
                OutlinedButton(enabled = !busy && timeline.openTurn?.actorId == scope.characterId && timeline.combat.actionPoints >= cost,
                    onClick = { prepare(GameBoundaryChoice.ReserveDefense(cost)) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Отложить $cost ОД для защиты") }
            OutlinedButton(enabled = !busy && timeline.combat.reactions > 0,
                onClick = { prepare(GameBoundaryChoice.SpendReaction) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Потратить реакцию") }
            TextButton(onClick = { otherEvents = !otherEvents }) { Text(if (otherEvents) "Свернуть другие события" else "Другие участники, раунды и отдых") }
            if (otherEvents) {
            OutlinedTextField(value = otherActor, onValueChange = { otherActor = it }, label = { Text("Другой участник") }, singleLine = true)
            OutlinedButton(enabled = !busy && timeline.openTurn == null && otherActor.isNotBlank(),
                onClick = { prepare(GameBoundaryChoice.StartOtherTurn(otherActor.trim())) }) { Text("Начать чужой ход") }
            OutlinedButton(enabled = !busy, onClick = { prepare(GameBoundaryChoice.EndRound) }) { Text("Завершить раунд") }
            OutlinedButton(enabled = !busy, onClick = { prepare(GameBoundaryChoice.EndScene) }) { Text("Завершить сцену") }
            for (kind in RestKind.entries) TextButton(enabled = !busy, onClick = { restKind = kind }) { Text((if (restKind == kind) "• " else "") + kind.label()) }
            OutlinedTextField(value = restMinutes, onValueChange = { restMinutes = it }, label = { Text("Длительность отдыха, минуты") }, singleLine = true)
            OutlinedButton(enabled = !busy && (restMinutes.toIntOrNull() ?: 0) > 0,
                onClick = { prepare(GameBoundaryChoice.Rest(restKind, checkNotNull(restMinutes.toIntOrNull()))) }) { Text("Записать отдых") }
            }
        }
        TextButton(enabled = !busy, onClick = { run {} }) { Text("Обновить события") }
        }
    }
}
private fun RestKind.label() = when (this) { RestKind.BRIEF -> "Короткий отдых"; RestKind.FULL -> "Полный отдых"; RestKind.OTHER -> "Другой отдых" }
