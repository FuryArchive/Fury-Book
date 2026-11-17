package com.furybook.desktop.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.furybook.desktop.DesktopAppState
import com.furybook.dubl.model.DevelopmentEntry

/**
 * The old sheet's development details flow for the shared sheet: details with linked entries, local edit,
 * reset and delete-custom, wired exactly as CharacterSheetScreen did. Replaced when stage 4 rebuilds Навыки.
 */
@Composable
fun DesktopSheetDevelopmentDetails(state: DesktopAppState, entryId: String, onDismiss: () -> Unit) {
    var shown by remember(entryId) { mutableStateOf(state.developmentCatalog.byId(entryId)) }
    var editing by remember(entryId) { mutableStateOf<DevelopmentEntry?>(null) }
    val character = state.activeCharacter
    editing?.let { entry ->
        val isCustom = character.customDevelopmentEntries.any { it.id == entry.id }
        DevelopmentLocalEditDialog(
            initial = entry,
            title = if (isCustom) "Редактировать свою запись" else "Локальная правка",
            onSave = { updated ->
                if (isCustom) state.updateCustomDevelopment(updated) else state.setDevelopmentOverride(updated)
                onDismiss()
            },
            onDismiss = onDismiss,
        )
        return
    }
    val entry = shown ?: run { onDismiss(); return }
    DevelopmentDetailsDialog(
        state = state,
        entry = entry,
        onOpenEntry = { targetId -> state.developmentCatalog.byId(targetId)?.let { shown = it } },
        onEditLocal = { editing = entry },
        onResetLocal = { state.resetDevelopmentOverride(entry.id); onDismiss() },
        onDeleteCustom = { state.removeCustomDevelopment(entry.id); onDismiss() },
        hasLocalOverride = character.developmentOverrides.containsKey(entry.id),
        isCustom = character.customDevelopmentEntries.any { it.id == entry.id },
        onDismiss = onDismiss,
    )
}
