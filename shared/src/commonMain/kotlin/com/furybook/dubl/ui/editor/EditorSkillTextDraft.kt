package com.furybook.dubl.ui.editor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * The skill panel's text fields (rule note, local name/description, Auto 6/12), saved with a delay. Same
 * guarantees as [EditorIdentityDraft]: only typing marks it dirty; a save writes only to the character and
 * skill it was made for, and never after a restore («Отменить всё», «Вернуть») bumped the generation.
 */
class EditorSkillTextDraft(private val characterId: String, detail: EditorSkillDetail, generation: Int) {
    private val skillId = detail.skillId
    private var typedAt = generation
    private var dirty = false
    // What this draft last wrote and what the store kept: a setter may trim or normalise, and echoing that
    // back into the field mid-sentence would eat the space the player just typed.
    private var echo: Map<String, Pair<String, String>> = emptyMap()

    var note: String by mutableStateOf(detail.formulaNote)
        private set
    var name: String by mutableStateOf(detail.localName)
        private set
    var description: String by mutableStateOf(detail.localDescription)
        private set
    var auto6: String by mutableStateOf(detail.auto6)
        private set
    var auto12: String by mutableStateOf(detail.auto12)
        private set

    fun edit(
        note: String = this.note,
        name: String = this.name,
        description: String = this.description,
        auto6: String = this.auto6,
        auto12: String = this.auto12,
    ) {
        this.note = note
        this.name = name
        this.description = description
        this.auto6 = auto6
        this.auto12 = auto12
        dirty = true
    }

    fun save(host: DublEditorHost, generation: Int) {
        if (!dirty) return
        dirty = false
        if (host.active.id != characterId || generation != typedAt) return
        val current = editorSkillDetail(host, skillId) ?: return
        if (note != current.formulaNote) host.setSkillFormulaNote(skillId, note)
        // A skill the player added has no rulebook name to fall back to: never save it blank.
        if (name != current.localName && (name.isNotBlank() || current.canResetToBook)) host.setSkillNameOverride(skillId, name)
        if (description != current.localDescription) host.setSkillDescriptionOverride(skillId, description)
        if (auto6 != current.auto6 || auto12 != current.auto12) host.setSkillAutoOverrides(skillId, auto6, auto12)
        val stored = editorSkillDetail(host, skillId) ?: return
        echo = mapOf(
            "note" to (note to stored.formulaNote),
            "name" to (name to stored.localName),
            "description" to (description to stored.localDescription),
            "auto6" to (auto6 to stored.auto6),
            "auto12" to (auto12 to stored.auto12),
        )
    }

    /** The store's value, unless it is just the echo of this draft's own last save (then the typed text stays). */
    private fun incoming(field: String, typed: String, stored: String): String {
        val (savedTyped, savedStored) = echo[field] ?: return stored
        return if (typed == savedTyped && stored == savedStored) typed else stored
    }

    /** Shows values changed from outside (restore, undo, «К рулбуку»). A restore also drops pending typing. */
    fun sync(detail: EditorSkillDetail, generation: Int) {
        if (detail.skillId != skillId) return
        if (generation != typedAt) {
            typedAt = generation
            dirty = false
            echo = emptyMap()
        }
        if (dirty) return
        note = incoming("note", note, detail.formulaNote)
        name = incoming("name", name, detail.localName)
        description = incoming("description", description, detail.localDescription)
        auto6 = incoming("auto6", auto6, detail.auto6)
        auto12 = incoming("auto12", auto12, detail.auto12)
    }
}
