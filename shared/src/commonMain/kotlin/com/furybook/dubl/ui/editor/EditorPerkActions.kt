package com.furybook.dubl.ui.editor

import com.furybook.dubl.model.DevelopmentEntry
import com.furybook.dubl.model.DevelopmentEntryKind
import com.furybook.dubl.model.normalizedLocalCopy

/** Runs a perk action and confirms it in the editor toast («Взято: …» or the rules' refusal message). */
internal fun applyPerkAction(
    host: DublEditorHost, state: EditorState, entryId: String, name: String, action: EditorPerkAction, chain: Boolean,
    /** Replaces «Взято: …» on success (bulk ranks, «Добрать требования»). */
    doneText: String? = null,
) {
    when (action) {
        is EditorPerkAction.Acquire -> {
            val result = host.acquireDevelopment(action.request)
            state.flash(if (result.applied) doneText ?: ((if (chain) "Взято цепочкой: " else "Взято: ") + name) else result.message)
        }
        is EditorPerkAction.SetRank -> {
            val before = host.active.development[entryId]?.rank ?: 0
            host.setDevelopmentRank(entryId, action.rank, action.optionIndex)
            state.flash(if (action.rank > before) "Взято: $name" else "Убрано: $name")
        }
    }
}

/** Which kind tab lists [entryId]; owned entries outside the catalog fall back to «Взятые». */
internal fun EditorPerkContext.kindOf(entryId: String): EditorPerkKind = when (index.indexed(entryId)?.kind) {
    DevelopmentEntryKind.REGULAR -> EditorPerkKind.REGULAR
    DevelopmentEntryKind.SPECIAL -> EditorPerkKind.SPECIAL
    DevelopmentEntryKind.MARTIAL -> EditorPerkKind.MARTIAL
    DevelopmentEntryKind.CHI -> EditorPerkKind.CHI
    null -> EditorPerkKind.OWNED
}

/** Opens [entryId] in the Навыки step with its detail shown. */
internal fun EditorState.openPerk(context: EditorPerkContext, entryId: String) {
    step = EditorStep.PERKS
    perkKind = context.kindOf(entryId)
    perkFilter = EditorPerkFilter.ALL
    perkQuery = ""
    selectChoices(entryId)
}

/** «Удалить свою запись»: drops the record and its ranks; «Вернуть» brings both back. Closes the detail and the plan entry. */
internal fun editorRemoveOwnPerk(host: DublEditorHost, state: EditorState, entryId: String, name: String) {
    val before = host.restorePoint()
    host.removeCustomDevelopment(entryId)
    state.selectChoices(null)
    state.dropPlanned(entryId)
    state.flash("Удалено: $name", undoableAt = host.restorePoint(), undoTo = before)
}

/** «Сбросить к рулбуку»: drops the local edit; the detail stays open on the rulebook record. */
internal fun editorResetPerk(host: DublEditorHost, state: EditorState, entryId: String, name: String) {
    val before = host.restorePoint()
    host.resetDevelopmentOverride(entryId)
    state.flash("Сброшено к рулбуку: $name", undoableAt = host.restorePoint(), undoTo = before)
}

enum class EditorPerkFormMode(val title: String) {
    NEW("Своя запись"),
    EDIT_OWN("Редактировать свою запись"),
    LOCAL_EDIT("Локальная правка"),
}

/** Saves the perk form; false (nothing written) while the form has errors or the application refuses it. */
internal fun editorSavePerk(host: DublEditorHost, state: EditorState, form: EditorPerkForm, base: DevelopmentEntry, mode: EditorPerkFormMode): Boolean {
    val entry = form.toEntry(base) ?: return false
    when (mode) {
        EditorPerkFormMode.NEW -> {
            val id = host.addCustomDevelopment(entry) ?: return false
            state.openPerk(EditorPerkContext(host), id)
        }
        EditorPerkFormMode.EDIT_OWN -> if (!host.updateCustomDevelopment(entry)) return false
        // A local edit that matches the rulebook record again is no edit: drop it rather than store a copy.
        EditorPerkFormMode.LOCAL_EDIT ->
            if (entry.normalizedLocalCopy(entry.id) == host.canonicalDevelopmentCatalog.byId(entry.id)?.normalizedLocalCopy(entry.id)) {
                host.resetDevelopmentOverride(entry.id)
            } else {
                host.setDevelopmentOverride(entry)
            }
    }
    state.flash("Сохранено: ${entry.name.trim()}")
    return true
}
