package com.furybook.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.furybook.dubl.actions.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Opt-in host component. The host must supply the current scope and a gated controller. */
@Composable
fun GameplayActionPanel(controller: GameplayController, scope: GameplayScope,
    modifier: Modifier = Modifier, onCommitted: suspend () -> Unit = {}) {
    var sheet by remember(controller, scope) { mutableStateOf<GameplaySheet?>(null) }
    var review by remember(controller, scope) { mutableStateOf<GameplayReview?>(null) }
    var busy by remember(controller, scope) { mutableStateOf(false) }
    var message by remember(controller, scope) { mutableStateOf<String?>(null) }
    val jobs = rememberCoroutineScope()
    // Changing the presentation scope only reloads. It never submits a game event or restores resources.
    LaunchedEffect(controller, scope) {
        try { sheet = controller.readSheet().takeIf { it.scope == scope }; review = controller.review }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { message = failure.message ?: "Лист недоступен" }
    }
    fun run(action: suspend () -> Unit) {
        if (busy) return
        busy = true
        jobs.launch {
            try {
                message = null; action()
                sheet = controller.readSheet().takeIf { it.scope == scope }
                review = controller.review?.takeIf { it.scope == scope }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { message = failure.message ?: "Операция не завершена" }
            finally { busy = false }
        }
    }
    DublCard(modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Сохранённые действия", style = MaterialTheme.typography.titleMedium)
            sheet?.let { Text("Выносливость: ${it.endurance}") }
            message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            val shown = review
            if (shown != null) {
                Text(when (val command = shown.request) {
                    is StartOverexertion -> "Сверхусилие: ${command.attribute.label()}, +1 кость преимущества"
                    is ResolvePhysicalCheck -> if (command.result.success) "Сохранить успех проверки" else "Сохранить провал проверки"
                    is UndoGameplay -> "Отменить последнее независимое действие"
                })
                Text("Выносливость: ${shown.enduranceBefore} → ${shown.enduranceAfter}")
                shown.ruleVersion?.let { Text("Источник: $it", style = MaterialTheme.typography.bodySmall) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(enabled = !busy, onClick = { run {
                        when (val result = controller.confirm(shown.key)) {
                            is GameplayCommitResult.Applied -> { onCommitted(); message = "Сохранено" }
                            is GameplayCommitResult.Replayed -> { onCommitted(); message = if (result.undone) "Это действие уже отменено" else "Уже сохранено; повторного расхода нет" }
                            is GameplayCommitResult.Blocked -> message = result.reason
                            is GameplayCommitResult.StorageFailure -> message = "Исход сохранения неизвестен. Повторите эту же операцию. ${result.reason}"
                        }
                    } }) { Text("Подтвердить / повторить") }
                    TextButton(enabled = !busy, onClick = { run { controller.dismiss() } }) { Text("Закрыть") }
                }
            } else {
                val pending = sheet?.pending
                if (pending != null) {
                    Text("Ожидается проверка: ${pending.attribute.label()}")
                    Text("Уже потрачено: ${pending.endurancePaid}. Преимущество: ${pending.advantageDice} кость. Провал не возвращает расход.")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(enabled = !busy, onClick = { run { controller.prepareResult(PhysicalCheckResult(true)) } }) { Text("Успех") }
                        OutlinedButton(enabled = !busy, onClick = { run { controller.prepareResult(PhysicalCheckResult(false)) } }) { Text("Провал") }
                    }
                } else if (sheet != null) {
                    Text("Сверхусилие: 1 выносливость, +1 кость преимущества. Только проверка характеристики, не атака или умение.")
                    for (attribute in PhysicalAttribute.entries) {
                        OutlinedButton(enabled = !busy, onClick = { run { controller.prepareStart(attribute) } }) { Text(attribute.label()) }
                    }
                }
                if (sheet?.lastReceipt != null) {
                    TextButton(enabled = !busy, onClick = { run { controller.prepareUndo() } }) { Text("Просмотреть отмену последнего действия") }
                }
            }
        }
    }
}
private fun PhysicalAttribute.label() = when (this) {
    PhysicalAttribute.STRENGTH -> "Сила"
    PhysicalAttribute.DEXTERITY -> "Ловкость"
    PhysicalAttribute.SPEED -> "Скорость"
    PhysicalAttribute.CONSTITUTION -> "Телосложение"
}
