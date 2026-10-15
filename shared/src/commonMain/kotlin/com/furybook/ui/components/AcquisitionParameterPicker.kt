package com.furybook.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.furybook.dubl.acquisition.AcquisitionNamedParameters
import com.furybook.dubl.acquisition.AcquisitionParameterField
import com.furybook.dubl.acquisition.AcquisitionOrderedSelections

/** One source-backed choice editor for main targets and prerequisite targets on every platform. */
@Composable
fun AcquisitionParameterPicker(fields:List<AcquisitionParameterField>,parameters:Map<String,String>,enabled:Boolean,
    newId:()->String,onParametersChange:(Map<String,String>)->Unit,modifier:Modifier=Modifier) {
    val bound=fields.flatMap { field -> field.options.firstOrNull { it.id==parameters[field.key] }?.parameterValues?.entries.orEmpty() }
        .associate { it.key to it.value }
    Column(modifier,verticalArrangement=Arrangement.spacedBy(8.dp)) {
        fields.forEach { field -> key(field.key) {
            if(field.sequenceLength!=null) OrderedChoiceField(field,parameters,enabled,onParametersChange)
            else ParameterChoiceField(field,parameters,enabled && field.key !in bound,newId,onParametersChange)
        } }
    }
}

@Composable
private fun OrderedChoiceField(field:AcquisitionParameterField,parameters:Map<String,String>,enabled:Boolean,
    onParametersChange:(Map<String,String>)->Unit) {
    val selected=AcquisitionOrderedSelections.decode(parameters[field.key]).orEmpty()
    val names=AcquisitionOrderedSelections.decode(field.displayNameKey?.let { parameters[it] }).orEmpty()
    var activeSlot by remember(field.key,field.sequenceReadOnlyPrefix) { mutableStateOf(field.sequenceReadOnlyPrefix) }
    var query by remember(field.key) { mutableStateOf("") }
    val length=checkNotNull(field.sequenceLength)
    Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
        Text(field.label)
        repeat(length) { index ->
            val label=field.options.firstOrNull { it.id==selected.getOrNull(index) }?.label ?: names.getOrNull(index) ?: "Выберите приём"
            TextButton(onClick={ activeSlot=index },enabled=enabled && !field.readOnly && index>=field.sequenceReadOnlyPrefix && index<=selected.size,
                modifier=Modifier.testTag("acquisition.parameter.${field.key}.slot.$index")) {
                Text("${index+1}. $label")
            }
        }
        if(!field.readOnly && activeSlot<length) {
            if(field.options.size>8) OutlinedTextField(query,{ query=it },label={ Text("Найти приём") },singleLine=true,enabled=enabled)
            val visible=field.options.filter { query.isBlank() || it.label.contains(query,ignoreCase=true) }
            LazyColumn(Modifier.heightIn(max=240.dp)) {
                items(visible,key={ it.id }) { option ->
                    FilterChip(selected=selected.getOrNull(activeSlot)==option.id,
                        onClick={
                            onParametersChange(AcquisitionOrderedSelections.select(field,activeSlot,option.id,parameters))
                            if(activeSlot==selected.size && activeSlot+1<length) activeSlot++
                        },enabled=enabled && !field.unresolved,label={ Text(option.label) },
                        modifier=Modifier.testTag("acquisition.parameter.${field.key}.option.${option.id}"))
                }
            }
        }
    }
}

@Composable
private fun ParameterChoiceField(field:AcquisitionParameterField,parameters:Map<String,String>,enabled:Boolean,
    newId:()->String,onParametersChange:(Map<String,String>)->Unit) {
    var query by remember(field.key) { mutableStateOf("") }
    var customName by remember(field.key) { mutableStateOf("") }
    val editable=enabled && !field.readOnly && !field.unresolved
    val selected=AcquisitionNamedParameters.selectedLabel(field,parameters)
    val visible=field.options.filter { query.isBlank() || it.label.contains(query,ignoreCase=true) }
    Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
        Text(field.label)
        selected?.let { Text("Выбрано: $it",modifier=Modifier.testTag("acquisition.parameter.${field.key}.selected")) }
        if(field.unresolved) Text("Правило выбора требует уточнения источника")
        if(!field.readOnly && field.options.size>8) OutlinedTextField(query,{ query=it },
            label={ Text("Найти вариант") },singleLine=true,enabled=editable,
            modifier=Modifier.testTag("acquisition.parameter.${field.key}.search"))
        if(!field.readOnly) {
            val choice:@Composable (com.furybook.dubl.acquisition.AcquisitionParameterOption)->Unit = { option ->
                FilterChip(selected=parameters[field.key]==option.id,
                    onClick={ onParametersChange(AcquisitionNamedParameters.selectOption(field,option.id,parameters));customName="" },
                    enabled=editable,label={ Text(option.label) },
                    modifier=Modifier.testTag("acquisition.parameter.${field.key}.option.${option.id}"))
            }
            if(field.options.size>8) LazyColumn(Modifier.heightIn(max=240.dp)) { items(visible,key={ it.id }) { choice(it) } }
            else visible.forEach { choice(it) }
        }
        if(field.userDefined && field.displayNameKey!=null && !field.readOnly) {
            OutlinedTextField(customName,{ customName=it },label={ Text(if(field.options.isEmpty())"Название" else "Другое название") },
                singleLine=true,enabled=editable,modifier=Modifier.testTag("acquisition.parameter.${field.key}.name"))
            TextButton(onClick={ onParametersChange(AcquisitionNamedParameters.selectCustom(field,customName,parameters,newId));customName="" },
                enabled=editable && customName.isNotBlank() && field.options.none { it.sourceChoice && it.label.equals(customName.trim(),ignoreCase=true) },
                modifier=Modifier.testTag("acquisition.parameter.${field.key}.custom")) { Text("Выбрать название") }
        }
    }
}
