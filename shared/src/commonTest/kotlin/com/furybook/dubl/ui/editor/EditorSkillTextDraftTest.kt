package com.furybook.dubl.ui.editor

import com.furybook.dubl.ui.editor.EditorFixtures.catalog
import com.furybook.dubl.ui.editor.EditorFixtures.hornet
import com.furybook.dubl.ui.sheet.FakeSheetHost
import kotlin.test.Test
import kotlin.test.assertEquals

class EditorSkillTextDraftTest {
    private fun host() = FakeSheetHost(hornet, developmentCatalogOverride = catalog)
    private fun draft(host: FakeSheetHost, generation: Int = 0) =
        EditorSkillTextDraft(host.active.id, editorSkillDetail(host, "stealth")!!, generation)

    @Test
    fun typedNoteIsSaved() {
        val host = host()
        val draft = draft(host)
        draft.edit(note = "+2 в темноте", auto6 = "В тени")
        draft.save(host, generation = 0)
        assertEquals("+2 в темноте", host.active.skills.getValue("stealth").formulaNote)
        assertEquals("В тени", editorSkillDetail(host, "stealth")!!.auto6)
    }

    @Test
    fun skillTextDraftNeverWritesToAnotherCharacter() {
        val host = host()
        val draft = draft(host)
        draft.edit(note = "+2 в темноте")
        host.createCharacter()
        val other = host.active
        draft.save(host, generation = 0)
        assertEquals(other, host.active)
    }

    @Test
    fun skillTextDraftDropsTypingAfterRestore() {
        val host = host()
        val point = host.restorePoint()
        val draft = draft(host)
        draft.edit(note = "+2 в темноте")
        host.restore(point)
        draft.save(host, generation = 1)
        assertEquals("", host.active.skills.getValue("stealth").formulaNote)
    }

    @Test
    fun outsideChangeShowsWhenNothingPending() {
        val host = host()
        val draft = draft(host)
        host.setSkillFormulaNote("stealth", "снаружи")
        draft.sync(editorSkillDetail(host, "stealth")!!, generation = 0)
        assertEquals("снаружи", draft.note)
    }

    @Test
    fun ownSaveDoesNotEatTheTrailingSpace() {
        // The note setter trims; the field must not snap to the trimmed value while the player is mid-sentence.
        val host = host()
        val draft = draft(host)
        draft.edit(note = "+2 в ")
        draft.save(host, generation = 0)
        draft.sync(editorSkillDetail(host, "stealth")!!, generation = 0)
        assertEquals("+2 в ", draft.note)
        host.resetSkillDefinitionOverrides("stealth")
        host.setSkillFormulaNote("stealth", "снаружи")
        draft.sync(editorSkillDetail(host, "stealth")!!, generation = 0)
        assertEquals("снаружи", draft.note)
    }

    @Test
    fun customSkillNameIsNeverSavedBlank() {
        val host = host()
        val id = host.addCustomSkill("Гадание", "", listOf(com.furybook.dubl.model.AttributeId.INTELLIGENCE), com.furybook.dubl.model.UntrainedRule.YES)!!
        val draft = EditorSkillTextDraft(host.active.id, editorSkillDetail(host, id)!!, 0)
        draft.edit(name = "  ")
        draft.save(host, generation = 0)
        assertEquals("Гадание", editorSkillDetail(host, id)!!.name)
    }
}
