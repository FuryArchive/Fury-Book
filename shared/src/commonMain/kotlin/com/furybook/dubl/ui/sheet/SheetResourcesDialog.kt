package com.furybook.dubl.ui.sheet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.furybook.dubl.model.CharacterSheetResourceId
import com.furybook.ui.components.FuryButton
import com.furybook.ui.components.FuryButtonStyle
import com.furybook.ui.components.FuryChoice
import com.furybook.ui.components.FuryDialogAction
import com.furybook.ui.components.FuryDialogScaffold
import com.furybook.ui.components.FuryMultiChipGroup
import com.furybook.ui.theme.DublMuted
import com.furybook.ui.theme.DublText
import com.furybook.ui.theme.FurySpacing
import com.furybook.ui.theme.furyOverlineStyle

/** One hideable resource in the visibility chips: health, endurance, mana and every available pack meter. */
data class SheetResourceToggle(val resource: CharacterSheetResourceId, val label: String, val hidden: Boolean)

fun sheetResourceVisibility(host: DublSheetEditHost): List<SheetResourceToggle> {
    val hidden = host.extras.hiddenResourceIds
    val builtIn = listOf(
        CharacterSheetResourceId.HEALTH to "Здоровье",
        CharacterSheetResourceId.ENDURANCE to "Выносливость",
        CharacterSheetResourceId.MANA to "Мана",
    ).map { (id, label) -> SheetResourceToggle(id, label, id in hidden) }
    val mounted = host.resourceToggleModels().filter { it.available }.map { model ->
        val presentation = model.presentation
        val label = sheetFcpIconGlyph(presentation.icon)?.let { "$it ${presentation.label}" } ?: presentation.label
        SheetResourceToggle(model.resourceId, label, model.resourceId in hidden)
    }
    return builtIn + mounted
}

/** Show/hide chips (desktop ResourceVisibilityDialog), the custom resources with edit/remove, and an add form. */
@Composable
fun SheetResourcesDialog(host: DublSheetEditHost, onDismiss: () -> Unit) {
    var editing by remember { mutableStateOf<String?>(null) }
    var adding by remember { mutableStateOf(false) }
    val toggles = sheetResourceVisibility(host)
    FuryDialogScaffold(
        title = "Ресурсы",
        subtitle = "Что показывать на листе и свои ресурсы",
        onDismiss = onDismiss,
        primary = FuryDialogAction("Готово", onDismiss),
    ) {
        Text("ПОКАЗЫВАТЬ", color = DublMuted, style = furyOverlineStyle())
        FuryMultiChipGroup(
            choices = toggles.map { FuryChoice(it.resource, it.label) },
            selected = toggles.filterNot { it.hidden }.map { it.resource }.toSet(),
            onToggle = { resource ->
                val toggle = toggles.first { it.resource == resource }
                host.setResourceHidden(resource, !toggle.hidden)
            },
            columns = 2,
        )
        Text("СВОИ РЕСУРСЫ", color = DublMuted, style = furyOverlineStyle())
        val custom = host.active.customResources
        if (custom.isEmpty() && !adding) Text("Своих ресурсов пока нет.", color = DublMuted, style = MaterialTheme.typography.bodyMedium)
        custom.forEach { resource ->
            if (editing == resource.uid) {
                SheetCustomResourceForm(
                    initialName = resource.name, initialMaximum = resource.maximum, initialCurrent = resource.current,
                    saveLabel = "Сохранить ресурс",
                    onSave = { name, maximum, current -> host.updateCustomResource(resource.uid, name, current, maximum); editing = null },
                )
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(FurySpacing.sm), verticalAlignment = Alignment.CenterVertically) {
                    Text("${resource.name} · ${resource.current} / ${resource.maximum}", Modifier.weight(1f), color = DublText, style = MaterialTheme.typography.bodyLarge)
                    FuryButton("Изменить", { editing = resource.uid }, style = FuryButtonStyle.Ghost)
                    FuryButton("Удалить", { host.removeCustomResource(resource.uid) }, style = FuryButtonStyle.Ghost)
                }
            }
        }
        if (adding) {
            SheetCustomResourceForm(
                initialName = "", initialMaximum = 5, initialCurrent = 5,
                saveLabel = "Добавить ресурс",
                onSave = { name, maximum, current -> host.addCustomResource(name, maximum, current); adding = false },
            )
        } else {
            FuryButton("Добавить ресурс", { adding = true }, Modifier.fillMaxWidth())
        }
    }
}
