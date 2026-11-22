package com.furybook.dubl.ui.editor

import com.furybook.dubl.model.KnownSpell
import com.furybook.dubl.model.SpellCatalogEntry

/**
 * Магия and Снаряжение actions with feedback. Deletes keep no undo step in the application, so each takes a
 * restore point first and the toast's «Вернуть» goes back to it (as own perks do in Навыки).
 */
internal fun editorRemoveSpell(host: DublEditorHost, state: EditorState, uid: String) {
    val spell = host.active.magic.spells.firstOrNull { it.uid == uid } ?: return
    val before = host.restorePoint()
    host.removeSpell(uid)
    if (state.magicPane == EditorPane.Spell(uid)) state.magicPane = null
    state.flash("Удалено: ${spell.name}", undoableAt = host.restorePoint(), undoTo = before)
}

internal fun editorRemoveSchool(host: DublEditorHost, state: EditorState, index: Int) {
    val school = host.active.magic.schools.getOrNull(index) ?: return
    val before = host.restorePoint()
    host.removeMagicSchool(index)
    if (state.magicPane is EditorPane.School) state.magicPane = null
    state.flash("Удалено: ${school.name}", undoableAt = host.restorePoint(), undoTo = before)
}

/** «Добавить» in the catalog: learns the spell and opens it; returns its uid (null when the application refused). */
internal fun editorAddCatalogSpell(host: DublEditorHost, state: EditorState, entry: SpellCatalogEntry): String? {
    // The rules refuse a second spell with the same catalog id or name: open the one already in the book instead.
    host.active.magic.spells.firstOrNull { it.catalogId == entry.id || it.name.equals(entry.name, ignoreCase = true) }?.let { known ->
        state.magicPane = EditorPane.Spell(known.uid)
        state.flash("Уже в книге заклинаний: ${known.name}")
        return known.uid
    }
    if (!host.addCatalogSpell(entry)) {
        state.flash("Не удалось добавить: ${entry.name}")
        return null
    }
    val uid = host.active.magic.spells.lastOrNull { it.catalogId == entry.id }?.uid ?: return null
    state.magicPane = EditorPane.Spell(uid)
    state.flash("Изучено: ${entry.name}")
    return uid
}

/** Saves the spell form and opens the saved spell; false (nothing written) while the form has errors. */
internal fun editorSaveSpell(host: DublEditorHost, state: EditorState, form: EditorSpellForm, base: KnownSpell?): Boolean {
    if (form.errors().isNotEmpty()) return false
    val spell = form.toSpell(base)
    val uid = if (base == null) host.addCustomSpell(spell) else spell.uid.also { host.updateSpell(spell) }
    state.magicPane = EditorPane.Spell(uid)
    return true
}

/** «+ Школа» ([index] null) or the school detail's «Сохранить»; a refusal says so instead of failing silently. */
internal fun editorSaveSchool(host: DublEditorHost, state: EditorState, index: Int?, form: EditorSchoolForm): Boolean {
    val character = host.active
    val current = index?.let { character.magic.schools.getOrNull(it)?.name }
    val allowed = editorSchoolChoices(character, index.takeIf { current != null })
    val saved = form.errors(allowed).isEmpty() && if (index == null) {
        host.addMagicSchool(form.name, form.powerValue(), form.note.trim())
    } else {
        host.updateMagicSchool(index, form.name, form.powerValue(), form.note.trim())
    }
    if (!saved) state.flash("Не удалось сохранить школу")
    return saved
}

internal fun editorRemoveGear(host: DublEditorHost, state: EditorState, uid: String) {
    val item = host.active.gear.items.firstOrNull { it.uid == uid } ?: return
    val before = host.restorePoint()
    host.removeGearItem(uid)
    if (state.gearPane == EditorPane.Item(uid)) state.gearPane = null
    state.flash("Удалено: ${item.name}", undoableAt = host.restorePoint(), undoTo = before)
}

/** «Добавить» in the gear catalog (a second copy raises the quantity) and opens the item. */
internal fun editorAddCatalogGear(host: DublEditorHost, state: EditorState, entry: com.furybook.dubl.model.GearCatalogEntry) {
    val uid = host.addCatalogGear(entry)
    state.gearPane = EditorPane.Item(uid)
    // A catalog item already carried gets one more, not a second row: say so.
    val quantity = host.active.gear.items.firstOrNull { it.uid == uid }?.quantity ?: 1
    state.flash(if (quantity > 1) "${entry.name}: теперь ×$quantity" else "Добавлено: ${entry.name}")
}

/** Saves the item form and opens the saved item; false (nothing written) while the form has errors. */
internal fun editorSaveGear(host: DublEditorHost, state: EditorState, form: EditorGearForm, base: com.furybook.dubl.model.GearItem?): Boolean {
    if (form.errors().isNotEmpty()) return false
    val item = form.toItem(base)
    val uid = if (base == null) host.addCustomGear(item) else item.uid.also { host.updateGearItem(item) }
    state.gearPane = EditorPane.Item(uid)
    return true
}

/**
 * A school's −/+ keeps its note (the rules' power setter rebuilds the school without one). Stepping below 1
 * removes the school, so it goes through [editorRemoveSchool] and offers «Вернуть».
 */
internal fun editorStepSchoolPower(host: DublEditorHost, state: EditorState, index: Int, delta: Int) {
    val school = host.active.magic.schools.getOrNull(index) ?: return
    val next = school.rank + delta
    if (next < 1) editorRemoveSchool(host, state, index) else host.updateMagicSchool(index, school.name, next, school.note)
}
