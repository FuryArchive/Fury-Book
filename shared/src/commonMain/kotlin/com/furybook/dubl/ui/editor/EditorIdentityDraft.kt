package com.furybook.dubl.ui.editor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.furybook.dubl.model.DublCharacter

/**
 * What the Основа name/concept fields show, saved with a delay. Only typing marks the draft dirty, and a save
 * writes only to the character the draft was made for and only if no restore happened since the typing
 * ([generation] is bumped by «Отменить всё» and «Вернуть»). So switching character or undoing never gets
 * overwritten by stale field text.
 */
class EditorIdentityDraft(character: DublCharacter, generation: Int) {
    private val characterId = character.id
    private var typedAt = generation
    private var dirty = false
    // Typed text vs what the store kept after this draft's own save: setIdentity trims, and echoing that back
    // mid-typing would eat the space the player just typed.
    private var echo: Pair<Pair<String, String>, Pair<String, String>>? = null

    var name: String by mutableStateOf(editorNameFieldInitial(character))
        private set
    var concept: String by mutableStateOf(character.concept)
        private set

    fun edit(name: String, concept: String) {
        this.name = name
        this.concept = concept
        dirty = true
    }

    fun save(host: DublEditorHost, generation: Int) {
        if (!dirty) return
        dirty = false
        val c = host.active
        if (c.id != characterId || generation != typedAt) return
        val newName = if (name == editorNameFieldInitial(c)) c.name else name
        if (newName != c.name || concept != c.concept) host.setIdentity(newName, concept, c.size, c.legs, c.manaEnabled)
        val stored = host.active
        echo = (name to concept) to (editorNameFieldInitial(stored) to stored.concept)
    }

    /** Shows a name/concept changed from outside (restore, undo). A restore also drops pending typing. */
    fun sync(character: DublCharacter, generation: Int) {
        if (character.id != characterId) return
        if (generation != typedAt) {
            typedAt = generation
            dirty = false
            echo = null
        }
        if (dirty) return
        val storedName = editorNameFieldInitial(character)
        val own = echo
        val ownEcho = own != null && own.first == (name to concept) && own.second == (storedName to character.concept)
        if (ownEcho) return
        name = storedName
        concept = character.concept
    }
}
