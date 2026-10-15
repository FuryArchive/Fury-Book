package com.furybook.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.furybook.dubl.acquisition.AcquisitionParameterOption

/** All platforms use the same explicit source choice or a player-supplied display name. */
@Composable
fun SkillSpecializationPicker(options: List<AcquisitionParameterOption>, enabled: Boolean,
    onSelect: (name: String, choiceId: String?) -> Unit) {
    var name by remember { mutableStateOf("") }
    var choiceId by remember { mutableStateOf<String?>(null) }
    val selected = options.singleOrNull { it.id == choiceId }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        options.forEach { choice ->
            FilterChip(selected = choiceId == choice.id, onClick = { choiceId = choice.id; name = "" },
                enabled = enabled, label = { Text(choice.label) },
                modifier = Modifier.testTag("acquisition.specialization.option.${choice.id}"))
        }
        OutlinedTextField(name, { name = it; choiceId = null },
            label = { Text(if (options.isEmpty()) "Специализация" else "Другая специализация") },
            singleLine = true, enabled = enabled, modifier = Modifier.testTag("acquisition.specialization.name"))
        TextButton(onClick = { onSelect(selected?.label ?: name.trim(), selected?.id) },
            enabled = enabled && (selected != null || name.isNotBlank()),
            modifier = Modifier.testTag("acquisition.specialization.select")) { Text("Выбрать специализацию") }
    }
}
