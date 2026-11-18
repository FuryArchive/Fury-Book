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
        if (name != current.localName) host.setSkillNameOverride(skillId, name)
        if (description != current.localDescription) host.setSkillDescriptionOverride(skillId, description)
        if (auto6 != current.auto6 || auto12 != current.auto12) host.setSkillAutoOverrides(skillId, auto6, auto12)
    }

    /** Shows values changed from outside (restore, undo, «К рулбуку»). A restore also drops pending typing. */
    fun sync(detail: EditorSkillDetail, generation: Int) {
        if (detail.skillId != skillId) return
        if (generation != typedAt) {
            typedAt = generation
            dirty = false
        }
        if (dirty) return
        note = detail.formulaNote
        name = detail.localName
        description = detail.localDescription
        auto6 = detail.auto6
        auto12 = detail.auto12
    }
}
