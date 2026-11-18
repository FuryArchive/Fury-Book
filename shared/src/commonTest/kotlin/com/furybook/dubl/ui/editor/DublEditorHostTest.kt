package com.furybook.dubl.ui.editor

import com.furybook.dubl.model.CharacterSkill
import com.furybook.dubl.model.DevelopmentAcquisitionRequest
import com.furybook.dubl.model.DublCharacter
import com.furybook.dubl.ui.sheet.FakeSheetHost
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DublEditorHostTest {
    private val character = DublCharacter(
        id = "c1", name = "Шершень", experience = 100,
        skills = mapOf("stealth" to CharacterSkill(id = "stealth", definitionId = "stealth", rank = 0)),
    )

    @Test
    fun fakeHostDelegatesEditorCalls() {
        val host: DublEditorHost = FakeSheetHost(character)
        val point = host.restorePoint()
        host.changeSkillRank("stealth", 1)
        assertEquals(1, host.active.skills.getValue("stealth").rank)
        val result = host.acquireDevelopment(DevelopmentAcquisitionRequest.single("a"))
        assertTrue(result.applied, result.message)
        assertEquals(1, host.active.development.getValue("a").rank)
        assertTrue(host.restore(point))
        assertEquals(0, host.active.skills.getValue("stealth").rank)
    }
}
