package com.furybook.dubl.ui.editor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.furybook.dubl.application.CharacterRestorePoint
import com.furybook.dubl.model.SkillCategory

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
    /** Short confirmation shown at the bottom of the editor («Взято: …»); the screen clears it after a moment. */
    var toast: String? by mutableStateOf(null)
    var snapshot: CharacterRestorePoint? by mutableStateOf(null)
        private set
    /** Bumped by «Отменить всё» and «Вернуть», so typed-but-unsaved field text is dropped instead of written back. */
    var restoreGeneration: Int by mutableStateOf(0)
        private set
    /** Rows Развитие pinned on its first view of this visit; cleared when the visit or the step changes. */
    var developmentPins: EditorDevelopmentPins? = null

    fun markRestored() { restoreGeneration++ }

    /** Skill whose panel is open on the Умения step; [skillCategory] the category chip filter. */
    var skillPanelId: String? by mutableStateOf(null)
    var skillCategory: SkillCategory? by mutableStateOf(null)

    /** [skillId]: opened from «Настроить в редакторе» — straight to Умения with that skill's panel. */
    fun open(host: DublEditorHost, forceBasics: Boolean, skillId: String? = null) {
        val complete = host.active.creationComplete
        snapshot = host.restorePoint()
        step = if (skillId != null) EditorStep.SKILLS else editorStartStep(complete, forceBasics)
        perkKind = EditorPerkKind.REGULAR
        perkFilter = EditorPerkFilter.ALL
        perkQuery = ""
        skillQuery = ""
        skillsRankedOnly = complete && skillId == null
        skillPanelId = skillId
        skillCategory = null
        selectChoices(null)
        changesOpen = false
        developmentPins = null
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

    /**
     * The toast offers «Вернуть» (one-step undo) only right after «Отменить всё», which the application records
     * as one undo step, and only while the character is still exactly as the restore left it: any later change
     * takes that undo slot, so «Вернуть» would undo the wrong thing.
     */
    var toastUndoable: Boolean by mutableStateOf(false)
        private set
    private var undoableAt: CharacterRestorePoint? = null

    fun flash(text: String, undoableAt: CharacterRestorePoint? = null) {
        toast = text
        this.undoableAt = undoableAt
        toastUndoable = undoableAt != null
    }

    fun dropStaleUndo(current: CharacterRestorePoint) {
        if (toastUndoable && current != undoableAt) {
            toastUndoable = false
            undoableAt = null
        }
    }

    fun next(host: DublEditorHost) = move(host, +1)

    fun back(host: DublEditorHost) = move(host, -1)

    private fun move(host: DublEditorHost, delta: Int) {
        val steps = editorSteps(host.active.creationComplete)
        val index = steps.indexOf(step).coerceAtLeast(0)
        step = steps[(index + delta).coerceIn(0, steps.lastIndex)]
    }
}

/** Scroll position (px) that centres the current step tab in the tab strip, clamped to the scroll range. */
fun editorTabScrollTarget(tabStart: Int, tabWidth: Int, viewport: Int, maxScroll: Int): Int =
    (tabStart + tabWidth / 2 - viewport / 2).coerceIn(0, maxScroll.coerceAtLeast(0))
