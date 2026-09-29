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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.furybook.dubl.application.CharacterTransferImportResult
import com.furybook.dubl.data.CharacterTransferRejectReason
import com.furybook.ui.theme.DublFocus
import com.furybook.ui.theme.DublMuted
import com.furybook.desktop.DesktopAppState
import com.furybook.web.downloadTextFile
import com.furybook.web.pickTextFile

@Composable
fun CharactersScreen(state: DesktopAppState, modifier: Modifier = Modifier) {
    var confirmDelete by remember { mutableStateOf(false) }
    var transferStatus by remember { mutableStateOf<String?>(null) }
    var contentPackStatus by remember { mutableStateOf<String?>(null) }

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
                        pickTextFile(".dubl,application/json,text/plain") { raw ->
                            transferStatus = importStatus(state.importCharacter(raw))
                        }
                    }) { Text("Импорт") }
                    OutlinedButton(onClick = {
                        downloadTextFile(
                            "${safeTransferFileName(state.activeCharacter.name)}.dubl",
                            state.exportActiveCharacter(),
                        )
                        transferStatus = "Персонаж экспортирован."
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
            SectionCard(title = "Fury Content Packs") {
                Text(
                    "Активные FCP определяют не только правила и каталоги, но и подключаемые части интерфейса.",
                    color = DublMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    "В web-версии сейчас доступны встроенные FCP; импорт внешних .fcp будет добавлен следующим слоем.",
                    color = DublMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
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

    if (confirmDelete) {
        FuryDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Удалить персонажа?") },
            text = { Text("${state.activeCharacter.name} будет удалён вместе с настройками листа. Это действие нельзя отменить.") },
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
