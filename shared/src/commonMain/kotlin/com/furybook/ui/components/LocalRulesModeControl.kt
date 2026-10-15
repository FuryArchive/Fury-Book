package com.furybook.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Explicit local opt-in. This control never migrates a character or enables cloud writes. */
@Composable
fun LocalRulesModeControl(enabled: Boolean, pending: Boolean? = null, onChange: (Boolean) -> Unit) {
    var reviewing by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(horizontal = 12.dp, vertical = 4.dp)) {
        TextButton(onClick = { reviewing = true }, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(if (enabled) "Правила: локальная автоматизация · облако отключено" else "Включить локальную автоматизацию правил")
        }
        if (pending != null && pending != enabled) Text(
            if (pending) "Закройте и снова откройте приложение: локальная автоматизация будет включена."
            else "После перезапуска локальные данные останутся сохранены; автоматизация будет выключена.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
    if (reviewing) AlertDialog(
        onDismissRequest = { reviewing = false },
        title = { Text(if (enabled) "Выключить локальную автоматизацию?" else "Локальная автоматизация правил") },
        text = { Text("Покупки, расходы и действия будут сохраняться на этом устройстве. Облачная запись нового формата отключена. Перенос существующего персонажа требует отдельного подтверждения и сохраняет исходную копию. На компьютере и Android режим применяется после перезапуска; в Web лист перезагрузится.") },
        confirmButton = { Button(onClick = { onChange(!enabled); reviewing = false }, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(if (enabled) "Выключить" else "Включить локально")
        } },
        dismissButton = { TextButton(onClick = { reviewing = false }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Отмена") } },
    )
}
