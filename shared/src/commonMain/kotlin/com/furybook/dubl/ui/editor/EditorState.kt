package com.furybook.dubl.ui.editor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.furybook.dubl.application.CharacterRestorePoint

enum class EditorStep(val title: String) {
    DEVELOPMENT("Развитие"),
    BASICS("Основа"),
    ATTRIBUTES("Характеристики"),
    SKILLS("Умения"),
    PERKS("Навыки"),
    SUMMARY("Итог"),
}

enum class EditorPerkKind { REGULAR, SPECIAL, MARTIAL, CHI, OWNED }

enum class EditorPerkFilter { ALL, AVAILABLE, ALMOST }

private val CREATION_STEPS = listOf(EditorStep.BASICS, EditorStep.ATTRIBUTES, EditorStep.SKILLS, EditorStep.PERKS, EditorStep.SUMMARY)

/** A character in creation walks the five steps; a finished one also gets the Развитие landing, first. */
fun editorSteps(creationComplete: Boolean): List<EditorStep> =
    if (creationComplete) listOf(EditorStep.DEVELOPMENT) + CREATION_STEPS else CREATION_STEPS

fun editorStartStep(creationComplete: Boolean, forceBasics: Boolean): EditorStep =
    if (!creationComplete || forceBasics) EditorStep.BASICS else EditorStep.DEVELOPMENT

/** Editor UI state. The snapshot taken on [open] is what «Отменить всё» restores and the changes list compares against. */
class EditorState {
    var step: EditorStep by mutableStateOf(EditorStep.BASICS)
    var perkKind: EditorPerkKind by mutableStateOf(EditorPerkKind.REGULAR)
    var perkFilter: EditorPerkFilter by mutableStateOf(EditorPerkFilter.ALL)
    var perkQuery: String by mutableStateOf("")
    var skillQuery: String by mutableStateOf("")
    var skillsRankedOnly: Boolean by mutableStateOf(true)
    var selectedEntryId: String? by mutableStateOf(null)
    var choiceSelections: Map<String, Int> by mutableStateOf(emptyMap())
    var optionIndex: Int? by mutableStateOf(null)
    var changesOpen: Boolean by mutableStateOf(false)
    var snapshot: CharacterRestorePoint? by mutableStateOf(null)
        private set

    fun open(host: DublEditorHost, forceBasics: Boolean) {
        val complete = host.active.creationComplete
        snapshot = host.restorePoint()
        step = editorStartStep(complete, forceBasics)
        perkKind = EditorPerkKind.REGULAR
        perkFilter = EditorPerkFilter.ALL
        perkQuery = ""
        skillQuery = ""
        skillsRankedOnly = complete
        selectChoices(null)
        changesOpen = false
    }

    /** Another character became active (picker, cloud pull): start over, so no snapshot ever crosses characters. */
    fun syncCharacter(host: DublEditorHost) {
        if (host.active.id != snapshot?.character?.id) open(host, forceBasics = false)
    }

    /** Selects a perk and forgets the choices made for the previous one. */
    fun selectChoices(entryId: String?) {
        selectedEntryId = entryId
        choiceSelections = emptyMap()
        optionIndex = null
    }

    fun next(host: DublEditorHost) = move(host, +1)

    fun back(host: DublEditorHost) = move(host, -1)

    private fun move(host: DublEditorHost, delta: Int) {
        val steps = editorSteps(host.active.creationComplete)
        val index = steps.indexOf(step).coerceAtLeast(0)
        step = steps[(index + delta).coerceIn(0, steps.lastIndex)]
    }
}
