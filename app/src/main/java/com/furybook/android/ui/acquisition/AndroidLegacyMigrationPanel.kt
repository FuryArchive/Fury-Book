package com.furybook.android.ui.acquisition

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.furybook.android.data.AndroidDublFcp
import com.furybook.android.state.CharacterController
import com.furybook.dubl.acquisition.*
import com.furybook.dubl.content.DublFcp
import com.furybook.dubl.content.DublChiFcp
import kotlinx.coroutines.*

/** Existing migration service, now reachable from the actual Android application. */
@Composable
internal fun AndroidLegacyMigrationPanel(controller: CharacterController, onNewCharacter: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val jobs = rememberCoroutineScope()
    var chi by remember(controller) { mutableStateOf(false) }
    var additionalPacks by remember(controller) { mutableStateOf("") }
    var result by remember(controller, controller.snapshot.activeCharacterId) { mutableStateOf<MigrationResult?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var xp by remember { mutableStateOf("") }
    var ap by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("") }
    fun run(action: suspend () -> Unit) {
        if (busy) return
        busy = true
        jobs.launch {
            try { message = null; action() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { message = failure.message ?: "Перенос не подтверждён; исходные данные сохранены" }
            finally { busy = false }
        }
    }
    fun commitResult(value: CommitResult) {
        message = when (value) {
            CommitResult.Committed -> "Персонаж перенесён."
            CommitResult.Conflict -> "Лист изменился. Подготовьте перенос заново или перезапустите приложение, чтобы прочитать сохранённую копию."
            is CommitResult.StorageFailure -> "Сохранение не подтверждено. Исходные данные сохранены. ${value.reason}"
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Перейти на локальные правила", style = MaterialTheme.typography.headlineSmall)
        Text("Существующий персонаж сохраняет свои значения и остаток опыта. История старых покупок не придумывается; облачная запись отключена.")
        Text("Подтвердите книги и модули, с которыми был создан исходный персонаж.")
        Text("Основная книга DUBL 3.69")
        Row { Checkbox(chi, { chi = it; result = null }, enabled = !busy); Text("В исходном листе использовался модуль ЦИ") }
        OutlinedTextField(additionalPacks, { additionalPacks = it; result = null }, enabled = !busy,
            label = { Text("Другие исходные пакеты, если использовались") }, modifier = Modifier.fillMaxWidth())
        Button(enabled = !busy && controller.acquisitionCapabilities.localDurable, modifier = Modifier.heightIn(min = 48.dp), onClick = { run {
            val ids = listOf(DublFcp.PACK_ID) + (if (chi) listOf(DublChiFcp.PACK_ID) else emptyList()) +
                additionalPacks.split(',').map(String::trim).filter(String::isNotBlank)
            val old = withContext(Dispatchers.IO) { runCatching { loadLegacyCatalogSnapshot(AndroidDublFcp.loader(context).pack, ids) }.getOrNull() }
            result = controller.prepareLocalMigration(ids, old)
        } }) { Text("Проверить исходный лист") }
        when (val prepared = result) {
            is MigrationResult.Ready -> {
                val opening = balance(prepared.state.ledger)
                Text("Сохранится остаток: ${opening.xp} XP / ${opening.ap} ОСп")
                Button(enabled = !busy, modifier = Modifier.heightIn(min = 48.dp), onClick = { run {
                    commitResult(controller.commitPreparedLocalMigration())
                } }) { Text("Подтверждаю состав и перенос") }
            }
            is MigrationResult.NeedsBalanceConfirmation -> {
                Text("Точный старый расчёт недоступен. Укажите известный вам остаток; он будет сохранён с вашим подтверждением.")
                OutlinedTextField(xp, { xp = it }, enabled = !busy, label = { Text("Известный остаток XP") })
                OutlinedTextField(ap, { ap = it }, enabled = !busy, label = { Text("Известный остаток ОСп") })
                OutlinedTextField(reason, { reason = it }, enabled = !busy, label = { Text("Основание подтверждения") })
                Button(enabled = !busy && xp.toLongOrNull() != null && ap.toLongOrNull() != null && reason.isNotBlank(),
                    modifier = Modifier.heightIn(min = 48.dp), onClick = { run {
                        commitResult(controller.confirmLocalOpeningBalance(Amount(checkNotNull(xp.toLongOrNull()), checkNotNull(ap.toLongOrNull())), reason))
                    } }) { Text("Подтверждаю известный остаток и перенос") }
            }
            is MigrationResult.Unsupported -> Text(prepared.reason)
            is MigrationResult.Corrupt -> Text(prepared.reason)
            null -> Unit
        }
        message?.let { Text(it) }
        OutlinedButton(enabled = !busy, onClick = onNewCharacter, modifier = Modifier.heightIn(min = 48.dp)) { Text("Создать отдельного нового персонажа") }
    }
}
