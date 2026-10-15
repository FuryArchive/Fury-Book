package com.furybook.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.furybook.dubl.actions.*

/** Purchases must not create a format-1 cloud queue before explicit local-format migration. */
@Composable
fun LocalRulesPreparationPanel(controller: GameplayController, scope: GameplayScope,
    migration: GameplayMigrationController, exportedCharacter: String, onCommitted: suspend () -> Unit) {
    var showExport by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Подготовка локальных правил", style = MaterialTheme.typography.headlineSmall)
        Text("Перед покупками и действиями подготовьте локальный формат с резервной копией. Баланс и сам персонаж сохранятся.")
        Text("Если у персонажа есть облачная очередь или история синхронизации, переход будет заблокирован: эти данные нельзя сбрасывать.")
        GameplayActionPanel(controller, scope, migration = migration, onCommitted = onCommitted)
        TextButton(onClick = { showExport = true }) { Text("Показать экспорт персонажа") }
    }
    if (showExport) AlertDialog(onDismissRequest = { showExport = false }, title = { Text("Экспорт персонажа") },
        text = { SelectionContainer { Text(exportedCharacter, Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) } },
        confirmButton = { TextButton(onClick = { showExport = false }) { Text("Закрыть") } })
}
