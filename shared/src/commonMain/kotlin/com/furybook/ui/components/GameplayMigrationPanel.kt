package com.furybook.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import com.furybook.dubl.actions.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** A separate explicit confirmation, not an automatic upgrade on read. */
@Composable
internal fun GameplayMigrationPanel(controller: GameplayMigrationController, scope: GameplayScope,
    onCommitted: suspend () -> Unit) {
    var review by remember(controller, scope) { mutableStateOf(controller.review) }
    var busy by remember(controller, scope) { mutableStateOf(false) }
    var message by remember(controller, scope) { mutableStateOf<String?>(null) }
    val jobs = rememberCoroutineScope()
    fun run(action: suspend () -> Unit) {
        if (busy) return
        busy = true
        jobs.launch {
            try { message = null; action(); review = controller.review?.takeIf { it.scope == scope } }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { message = failure.message ?: "Переход не завершён" }
            finally { busy = false }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        message?.let { Text(it) }
        val shown = review
        if (shown == null) {
            OutlinedButton(enabled = !busy, onClick = { run { controller.prepare() } }) {
                Text("Подготовить локальные действия")
            }
        } else {
            Text("Лист: ${shown.characterName}. Выносливость: ${shown.endurance}.")
            Text("Перед переходом будет сохранена точная исходная копия. Новый формат нельзя открыть для записи в старой версии. Облачная синхронизация этого формата не поддерживается.")
            Text("Ресурсы и приобретения при переходе не меняются. Это не обратимый переключатель интерфейса.")
            Button(enabled = !busy, onClick = { run {
                when (val result = controller.confirm(shown.request.operationId)) {
                    is GameplayMigrationResult.Migrated, is GameplayMigrationResult.AlreadyMigrated -> {
                        onCommitted(); message = "Переход сохранён вместе с исходной копией"
                    }
                    is GameplayMigrationResult.Blocked -> message = result.reason
                    is GameplayMigrationResult.StorageFailure -> message = "Исход неизвестен. Повторите этот же переход. ${result.reason}"
                }
            } }) { Text("Создать копию и перейти / повторить") }
            TextButton(enabled = !busy, onClick = { run { controller.dismiss() } }) { Text("Закрыть") }
        }
    }
}
