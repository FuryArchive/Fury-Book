package com.furybook.dubl.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import com.furybook.dubl.model.AttributeId
import com.furybook.dubl.model.SkillCatalog
import com.furybook.dubl.model.UntrainedRule
import com.furybook.ui.components.FuryChipGroup
import com.furybook.ui.components.FuryChoice
import com.furybook.ui.components.FuryDialogAction
import com.furybook.ui.components.FuryDialogScaffold
import com.furybook.ui.components.FuryMultiChipGroup
import com.furybook.ui.components.FuryTextField
import com.furybook.ui.theme.FuryColors

internal fun validateSpecialization(text: String): String? = if (text.isBlank()) "Введите название." else null

internal fun validateCustomSkill(name: String, attributes: Set<AttributeId>): String? = when {
    name.isBlank() -> "Введите название."
    attributes.isEmpty() -> "Выберите хотя бы одну характеристику."
    else -> null
}

private enum class SkillFormTab(val title: String) { SPECIALIZATION("Специализация"), CUSTOM("Своё") }

/** Adds a specialization of a template skill or a custom skill; the same fields as the old Skills screens. */
@Composable
internal fun EditorSkillFormDialog(host: DublEditorHost, onAdded: (String) -> Unit, onDismiss: () -> Unit) {
    var tab by remember { mutableStateOf(SkillFormTab.SPECIALIZATION) }
    var template by remember { mutableStateOf(SkillCatalog.templates.firstOrNull()?.id) }
    var specialization by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var attributes by remember { mutableStateOf(setOf(AttributeId.INTELLIGENCE)) }
    var untrained by remember { mutableStateOf(UntrainedRule.YES) }
    var error by remember { mutableStateOf<String?>(null) }
    fun submit() {
        error = when (tab) {
            SkillFormTab.SPECIALIZATION -> validateSpecialization(specialization) ?: run {
                val id = template ?: return@run "Нет шаблонов для специализации."
                val added = host.addSpecializedSkill(id, specialization.trim())
                if (added == null) "Такое умение уже есть." else { onAdded(specialization.trim()); null }
            }
            SkillFormTab.CUSTOM -> validateCustomSkill(name, attributes) ?: run {
                val added = host.addCustomSkill(name.trim(), description.trim(), AttributeId.entries.filter { it in attributes }, untrained)
                if (added == null) "Такое умение уже есть." else { onAdded(name.trim()); null }
            }
        }
    }
    FuryDialogScaffold(
        title = "Новое умение",
        onDismiss = onDismiss,
        subtitle = "Специализация шаблонного умения или своё умение",
        primary = FuryDialogAction("Добавить", ::submit),
        secondary = FuryDialogAction("Отмена", onDismiss),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SkillFormTab.entries.forEach { option -> EditorChip(option.title, tab == option, { tab = option; error = null }, 44.dp) }
        }
        when (tab) {
            SkillFormTab.SPECIALIZATION -> {
                FuryChipGroup(SkillCatalog.templates.map { FuryChoice(it.id, it.name) }, template, { template = it }, columns = 2)
                FuryTextField(specialization, { specialization = it.take(80); error = null }, "Специализация", error = error)
            }
            SkillFormTab.CUSTOM -> {
                FuryTextField(name, { name = it.take(80); error = null }, "Название", error = error.takeIf { attributes.isNotEmpty() })
                FuryTextField(description, { description = it.take(500) }, "Описание", singleLine = false)
                EditorOverline("Характеристики")
                FuryMultiChipGroup(AttributeId.entries.map { FuryChoice(it, it.shortTitle) }, attributes, { attr ->
                    attributes = if (attr in attributes) attributes - attr else attributes + attr
                    error = null
                }, columns = 4)
                EditorOverline("Без обучения")
                FuryChipGroup(UntrainedRule.entries.map { FuryChoice(it, it.label) }, untrained, { untrained = it }, columns = 2)
            }
        }
        if (error != null && tab == SkillFormTab.CUSTOM && attributes.isEmpty()) {
            Text(error!!, color = FuryColors.current.accText, style = MaterialTheme.typography.bodySmall)
        }
    }
}
