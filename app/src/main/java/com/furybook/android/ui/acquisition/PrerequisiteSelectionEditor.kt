package com.furybook.android.ui.acquisition

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.furybook.dubl.acquisition.*
import java.util.UUID

/** Explicit user input, not inferred eligibility. Shared checks the complete selected targets. */
@Composable
internal fun PrerequisiteSelectionEditor(model: AcquisitionUiModel, questionId: String,
    view: AcquisitionCharacterView, onReview: (List<AcquisitionTarget>) -> Unit, onDismiss: () -> Unit) {
    var selected by remember(model.key, questionId) { mutableStateOf(model.request?.prerequisiteSelections?.get(questionId).orEmpty()) }
    var query by remember { mutableStateOf("") }
    var definition by remember { mutableStateOf<AcquisitionDefinition?>(null) }
    var rank by remember { mutableStateOf("1") }
    var parameters by remember(definition) { mutableStateOf(emptyMap<String, String>()) }
    var option by remember(definition) { mutableStateOf<String?>(null) }
    val labels = remember(view.catalog) { AcquisitionLabels(view.catalog) }
    val targetRank = rank.toIntOrNull()?.takeIf { it > 0 }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Явный выбор требований") }, text = {
        Column(Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Выберите точные экземпляры и требуемые ранги. Это только запрос: условия, цену и допустимость проверит общий расчёт.")
            OutlinedTextField(rank, { rank = it }, label = { Text("Целевой ранг выбранного экземпляра") }, modifier = Modifier.testTag("acquisition.prerequisite.rank"))
            view.instances.forEach { instance ->
                OutlinedButton(onClick = {
                    selected = selected.filterNot { it.instanceId == instance.instanceId } + AcquisitionTarget(instance.definition,
                        instance.instanceId, checkNotNull(targetRank), instance.parameters, instance.optionId)
                }, enabled = targetRank != null, modifier = Modifier.testTag("acquisition.choice")) { Text("${instance.label} · сейчас ${instance.rank}") }
            }
            OutlinedTextField(query, { query = it }, label = { Text("Поиск новой записи") }, modifier = Modifier.testTag("acquisition.prerequisite.search"))
            if (query.isNotBlank()) view.catalog.definitions.values.filter { labels.definition(it.ref).contains(query, true) }
                .sortedWith(compareBy({ it.name }, { it.ref.packId }, { it.ref.entryId })).take(50).forEach { candidate ->
                    TextButton(onClick = { definition = candidate }) { Text(labels.definition(candidate.ref)) }
                }
            definition?.let { value ->
                Text(labels.definition(value.ref), style = MaterialTheme.typography.titleSmall)
                value.parameterDefinitions.forEachIndexed { index, field ->
                    Text("Параметр ${index + 1}")
                    when (val domain = field.domain) {
                        is ParameterDomain.StableIds -> domain.valueIds.sorted().forEachIndexed { number, id ->
                            FilterChip(selected = parameters[field.key] == id, onClick = { parameters = parameters + (field.key to id) },
                                label = { Text(view.instances.firstOrNull { it.instanceId == id }?.label ?: "Вариант ${number + 1}") })
                        }
                        ParameterDomain.UserDefinedIds -> OutlinedTextField(parameters[field.key].orEmpty(),
                            { parameters = parameters + (field.key to it) }, label = { Text("Явный параметр специализации") })
                        is ParameterDomain.Unresolved -> Text("Допустимые параметры не подтверждены")
                    }
                }
                value.abilityOptionIds?.sorted()?.forEachIndexed { number, id ->
                    FilterChip(selected = option == id, onClick = { option = id },
                        label = { Text(when (id) { "morale" -> "Мораль"; "chi" -> "Ци"; else -> "Вариант ${number + 1}" }) })
                }
                val abilityOptions = value.abilityOptionIds
                val paramsValid = value.parameterDefinitions.all { it.domain !is ParameterDomain.Unresolved && !parameters[it.key].isNullOrBlank() }
                TextButton(onClick = {
                    selected = selected + AcquisitionTarget(value.ref, "ui-choice:${UUID.randomUUID()}", checkNotNull(targetRank), parameters, option)
                    definition = null; query = ""
                }, enabled = targetRank != null && paramsValid && abilityOptions != null &&
                    (abilityOptions.isEmpty() || option in abilityOptions) && value.repeatPolicy !is RepeatPolicy.Unresolved) { Text("Добавить новый экземпляр в запрос") }
            }
            Text("Выбрано: ${selected.size}")
            selected.forEachIndexed { index, target -> Row {
                Text("${labels.definition(target.definition)} · ранг ${target.targetRank}", Modifier.weight(1f))
                TextButton(onClick = { selected = selected.filterIndexed { i, _ -> i != index } }) { Text("Убрать") }
            } }
        }
    }, confirmButton = { TextButton(onClick = { onReview(selected) }, enabled = selected.isNotEmpty(),
        modifier = Modifier.testTag("acquisition.prerequisite.review")) { Text("Пересчитать выбранный план") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } })
}
