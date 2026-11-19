package com.furybook.dubl.ui.editor

import com.furybook.dubl.model.DevelopmentEntryKind

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
