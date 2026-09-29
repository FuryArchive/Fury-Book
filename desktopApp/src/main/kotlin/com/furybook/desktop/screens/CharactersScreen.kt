package com.furybook.desktop.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.furybook.dubl.application.CharacterTransferImportResult
import com.furybook.dubl.data.CharacterTransferRejectReason
import com.furybook.ui.theme.DublFocus
import com.furybook.ui.theme.DublMuted
import com.furybook.desktop.DesktopAppState
import com.furybook.desktop.cloud.DesktopCloudController
import com.furybook.cloud.NativeCloudStatus
import java.awt.FileDialog
import java.awt.Frame
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.launch

@Composable
fun CharactersScreen(state: DesktopAppState, cloudController: DesktopCloudController, modifier: Modifier = Modifier) {
    var confirmDelete by remember { mutableStateOf(false) }
    var transferStatus by remember { mutableStateOf<String?>(null) }
    var contentPackStatus by remember { mutableStateOf<String?>(null) }
    var cloudRegistering by remember { mutableStateOf(false) }
    var cloudNickname by remember { mutableStateOf("") }
    var cloudEmail by remember { mutableStateOf("") }
    var cloudPassword by remember { mutableStateOf("") }
    var cloudAuthBusy by remember { mutableStateOf(false) }
    var cloudLoginMessage by remember { mutableStateOf<String?>(null) }
    val cloudScope = rememberCoroutineScope()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("Персонажи", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text("${state.snapshot.characters.size} персонаж(а/ей)", color = DublMuted)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = {
                        val file = pickTransferFile()
                        if (file != null) {
                            val raw = runCatching { Files.readString(file, StandardCharsets.UTF_8) }.getOrNull()
                            transferStatus = if (raw == null) {
                                "Не удалось прочитать файл персонажа."
                            } else {
                                importStatus(state.importCharacter(raw))
                            }
                        }
                    }) { Text("Импорт") }
                    OutlinedButton(onClick = {
                        val file = saveTransferFile(state.activeCharacter.name)
                        if (file != null) {
                            transferStatus = if (runCatching {
                                    Files.writeString(file, state.exportActiveCharacter(), StandardCharsets.UTF_8)
                                }.isSuccess
                            ) {
                                "Персонаж экспортирован."
                            } else {
                                "Не удалось сохранить файл персонажа."
                            }
                        }
                    }) { Text("Экспорт") }
                    Button(onClick = { state.createCharacter() }) { Text("+ Новый персонаж") }
                }
            }
        }
        transferStatus?.let { status ->
            item {
                Text(
                    status,
                    color = DublMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        item {
            SectionCard(title = "Fury Cloud") {
                val session = cloudController.session
                if (session == null) {
                    Text(
                        "Локальные персонажи останутся на компьютере. При первом входе они безопасно объединятся с облаком — существующие данные не перезаписываются.",
                        color = DublMuted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            modifier = Modifier.weight(1f),
                            enabled = !cloudAuthBusy,
                            onClick = {
                                cloudRegistering = false
                                cloudLoginMessage = null
                            },
                        ) { Text("Войти") }
                        OutlinedButton(
                            modifier = Modifier.weight(1f),
                            enabled = !cloudAuthBusy,
                            onClick = {
                                cloudRegistering = true
                                cloudLoginMessage = null
                            },
                        ) { Text("Создать аккаунт") }
                    }
                    if (cloudRegistering) {
                        OutlinedTextField(
                            value = cloudNickname,
                            onValueChange = { cloudNickname = it.take(32); cloudLoginMessage = null },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text("Никнейм") },
                            supportingText = { Text("От 2 до 32 символов.") },
                        )
                    }
                    OutlinedTextField(
                        value = cloudEmail,
                        onValueChange = { cloudEmail = it; cloudLoginMessage = null },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Email") },
                        supportingText = {
                            if (cloudRegistering) Text("На этот адрес придёт письмо для подтверждения аккаунта.")
                        },
                    )
                    OutlinedTextField(
                        value = cloudPassword,
                        onValueChange = { cloudPassword = it; cloudLoginMessage = null },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        label = { Text("Пароль") },
                        supportingText = {
                            if (cloudRegistering) Text("Минимум 8 символов.")
                        },
                    )
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !cloudAuthBusy,
                        onClick = {
                            val email = cloudEmail.trim()
                            val nickname = cloudNickname.trim()
                            val validation = when {
                                cloudRegistering && nickname.length !in 2..32 -> "Никнейм должен содержать от 2 до 32 символов."
                                email.isBlank() -> "Введите email."
                                !email.contains("@") || !email.substringAfter("@", "").contains(".") -> "Введите корректный email."
                                cloudPassword.isBlank() -> "Введите пароль."
                                cloudRegistering && cloudPassword.length < 8 -> "Пароль должен содержать минимум 8 символов."
                                else -> null
                            }
                            if (validation != null) {
                                cloudLoginMessage = validation
                            } else {
                                cloudAuthBusy = true
                                cloudLoginMessage = null
                                cloudScope.launch {
                                    if (cloudRegistering) {
                                        cloudController.signUp(email, cloudPassword, nickname)
                                            .onSuccess { signedIn ->
                                                cloudPassword = ""
                                                if (signedIn) {
                                                    cloudLoginMessage = "Аккаунт создан. Локальные и облачные персонажи безопасно объединены."
                                                } else {
                                                    cloudRegistering = false
                                                    cloudLoginMessage = "Аккаунт создан. Подтвердите email из письма, затем войдите."
                                                }
                                            }
                                            .onFailure { cloudLoginMessage = it.message ?: "Не удалось создать аккаунт." }
                                    } else {
                                        cloudController.signIn(email, cloudPassword)
                                            .onSuccess {
                                                cloudPassword = ""
                                                cloudLoginMessage = "Вход выполнен. Локальные и облачные персонажи безопасно объединены."
                                            }
                                            .onFailure { cloudLoginMessage = it.message ?: "Не удалось войти." }
                                    }
                                    cloudAuthBusy = false
                                }
                            }
                        },
                    ) {
                        Text(
                            when {
                                cloudAuthBusy -> "Подождите…"
                                cloudRegistering -> "Создать аккаунт"
                                else -> "Войти и синхронизировать"
                            },
                        )
                    }
                    if (cloudRegistering) {
                        Text(
                            "После регистрации откройте письмо, подтвердите email и вернитесь в Fury Book. Локальные персонажи до входа никуда не исчезнут.",
                            color = DublMuted,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                } else {
                    Text(
                        cloudController.nickname.ifBlank { session.email.substringBefore('@') },
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(session.email, color = DublMuted, style = MaterialTheme.typography.bodySmall)
                    Text(
                        desktopCloudStatusText(cloudController.status),
                        color = desktopCloudStatusColor(cloudController.status),
                        fontWeight = FontWeight.SemiBold,
                    )
                    cloudController.statusMessage?.let { message ->
                        Text(message, color = DublMuted, style = MaterialTheme.typography.bodySmall)
                    }
                    cloudController.lastSyncedAt?.let { value ->
                        Text("Последняя синхронизация: $value", color = DublMuted, style = MaterialTheme.typography.bodySmall)
                    }
                    cloudController.lastSyncedBy?.let { value ->
                        Text(value, color = DublMuted, style = MaterialTheme.typography.bodySmall)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            modifier = Modifier.weight(1f),
                            onClick = cloudController::requestSync,
                        ) { Text("Синхронизировать") }
                        TextButton(
                            modifier = Modifier.weight(1f),
                            onClick = {
                                cloudScope.launch {
                                    cloudController.signOut()
                                    cloudLoginMessage = "Аккаунт отключён. Локальные персонажи сохранены."
                                }
                            },
                        ) { Text("Выйти") }
                    }
                }
                cloudLoginMessage?.let { message ->
                    Text(message, color = DublMuted, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item {
            SectionCard(title = "Fury Content Packs") {
                Text(
                    "Активные FCP определяют не только правила и каталоги, но и подключаемые части интерфейса.",
                    color = DublMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedButton(onClick = {
                    val file = pickFcpFile()
                    if (file != null) {
                        contentPackStatus = runCatching {
                            val manifest = state.installContentPack(file)
                            "Установлен FCP: ${manifest.name} v${manifest.version}."
                        }.getOrElse { error ->
                            "Ошибка импорта FCP: ${error.message ?: "неизвестная ошибка"}"
                        }
                    }
                }) { Text("Импортировать .fcp") }
                val composition = state.contentPackComposition
                composition.available.forEach { manifest ->
                    val required = manifest.id in composition.requiredPackIds
                    val canActivate = state.canActivateContentPack(manifest)
                    ContentPackRow(
                        name = manifest.name,
                        version = manifest.version,
                        enabled = composition.isActive(manifest.id),
                        toggleEnabled = !required && canActivate,
                        subtitle = if (required) {
                            "Основной ruleset · обязателен"
                        } else if (!canActivate) {
                            "Установлен · adapter support пока отсутствует"
                        } else if (manifest.id != state.corePackManifest.id && manifest.id != state.chiPackManifest.id) {
                            "DUBL data-only · дополнительное развитие"
                        } else if (manifest.dependencies.isEmpty()) {
                            "Опциональный FCP"
                        } else {
                            "Опциональный FCP · зависит от ${manifest.dependencies.joinToString { it.id }}"
                        },
                        onToggle = { enabled ->
                            contentPackStatus = runCatching {
                                state.setContentPackActive(manifest.id, enabled)
                                if (enabled) "${manifest.name} включён." else "${manifest.name} выключен."
                            }.getOrElse { error ->
                                "Ошибка FCP: ${error.message ?: "неизвестная ошибка"}"
                            }
                        },
                    )
                }
                contentPackStatus?.let { status ->
                    Text(status, color = DublMuted, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        items(state.snapshot.characters, key = { it.id }) { character ->
            val active = character.id == state.snapshot.activeCharacterId
            SectionCard(
                title = character.name,
                modifier = Modifier.clickable { state.selectCharacter(character.id) },
                action = {
                    if (active && state.snapshot.characters.size > 1) {
                        TextButton(onClick = { confirmDelete = true }) { Text("Удалить") }
                    } else if (!active) {
                        OutlinedButton(onClick = { state.selectCharacter(character.id) }) { Text("Открыть") }
                    }
                },
            ) {
                Text(character.concept.ifBlank { "Без концепта" }, color = DublMuted)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("XP ${character.experience}", color = DublFocus)
                    Text(if (active) "Активный" else "", color = DublFocus, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }

    cloudController.conflicts.firstOrNull()?.let { conflict ->
        FuryDialog(
            onDismissRequest = {},
            title = { Text("Персонаж изменён на другом устройстве") },
            text = {
                Text(
                    "Fury Book ничего не перезаписал. Облачная версия: revision \${conflict.serverRevision}" +
                        (conflict.updatedBy?.let { " · $it" } ?: "") +
                        ". Выберите, какую версию оставить.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    cloudScope.launch { cloudController.useCloudVersion() }
                }) { Text("Загрузить облачную") }
            },
            dismissButton = {
                TextButton(onClick = {
                    cloudScope.launch { cloudController.keepLocalVersion(conflict) }
                }) { Text(if (conflict.deleteRequested) "Удалить всё равно" else "Сохранить локальную") }
            },
        )
    }

    if (confirmDelete) {
        FuryDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Удалить персонажа?") },
            text = { Text("${state.activeCharacter.name} будет удалён вместе с desktop-настройками листа. Это действие нельзя отменить.") },
            confirmButton = {
                TextButton(onClick = {
                    state.deleteActive()
                    confirmDelete = false
                }) { Text("Удалить") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Отмена") } },
        )
    }
}

@Composable
private fun ContentPackRow(
    name: String,
    version: String,
    enabled: Boolean,
    toggleEnabled: Boolean,
    subtitle: String,
    onToggle: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(name, fontWeight = FontWeight.SemiBold)
            Text("$subtitle · v$version", color = DublMuted, style = MaterialTheme.typography.bodySmall)
        }
        Switch(
            checked = enabled,
            enabled = toggleEnabled,
            onCheckedChange = onToggle,
        )
    }
}

private fun pickFcpFile(): Path? {
    val dialog = FileDialog(null as Frame?, "Импорт Fury Content Pack", FileDialog.LOAD)
    dialog.file = "*.fcp"
    dialog.isVisible = true
    val file = dialog.file ?: return null
    return Path.of(dialog.directory, file)
}

private fun pickTransferFile(): Path? {
    val dialog = FileDialog(null as Frame?, "Импорт персонажа DUBL", FileDialog.LOAD)
    dialog.isVisible = true
    val file = dialog.file ?: return null
    return Path.of(dialog.directory, file)
}

private fun saveTransferFile(characterName: String): Path? {
    val dialog = FileDialog(null as Frame?, "Экспорт персонажа DUBL", FileDialog.SAVE)
    dialog.file = "${safeTransferFileName(characterName)}.dubl"
    dialog.isVisible = true
    val file = dialog.file ?: return null
    val chosen = Path.of(dialog.directory, file)
    return if (chosen.fileName.toString().endsWith(".dubl", ignoreCase = true)) {
        chosen
    } else {
        chosen.resolveSibling("${chosen.fileName}.dubl")
    }
}

private fun importStatus(result: CharacterTransferImportResult): String = when (result) {
    is CharacterTransferImportResult.Imported -> "Импортирован персонаж: ${result.name}."
    is CharacterTransferImportResult.Rejected -> when (result.reason) {
        CharacterTransferRejectReason.INVALID_FILE -> "Это не поддерживаемый файл персонажа DUBL."
        CharacterTransferRejectReason.UNSUPPORTED_FORMAT_VERSION -> "Версия файла персонажа пока не поддерживается."
        CharacterTransferRejectReason.UNSUPPORTED_RULESET -> "Этот файл создан для другого рулбука или версии правил."
    }
}

private fun safeTransferFileName(name: String): String = name
    .trim()
    .ifBlank { "character" }
    .replace(Regex("[\\\\/:*?\"<>|]+"), "_")
    .take(80)


@Composable
private fun desktopCloudStatusColor(status: NativeCloudStatus) = when (status) {
    NativeCloudStatus.SYNCED -> DublFocus
    NativeCloudStatus.CONFLICT, NativeCloudStatus.ERROR -> MaterialTheme.colorScheme.error
    else -> DublMuted
}

private fun desktopCloudStatusText(status: NativeCloudStatus): String = when (status) {
    NativeCloudStatus.SIGNED_OUT -> "Не подключено"
    NativeCloudStatus.CONNECTING -> "Подключение…"
    NativeCloudStatus.SYNCING -> "Синхронизация…"
    NativeCloudStatus.SYNCED -> "Синхронизировано"
    NativeCloudStatus.OFFLINE -> "Офлайн · изменения в очереди"
    NativeCloudStatus.CONFLICT -> "Конфликт изменений"
    NativeCloudStatus.ERROR -> "Ошибка синхронизации"
}
