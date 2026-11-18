package com.furybook.dubl.ui.editor

import com.furybook.dubl.model.AttributeId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EditorSkillFormsTest {
    @Test
    fun validateSpecializationBlank() {
        assertEquals("Введите название.", validateSpecialization("  "))
        assertNull(validateSpecialization("Монстрология"))
    }

    @Test
    fun validateCustomSkillNeedsAttribute() {
        assertEquals("Выберите хотя бы одну характеристику.", validateCustomSkill("Гадание", emptySet()))
        assertEquals("Введите название.", validateCustomSkill(" ", setOf(AttributeId.WILL)))
        assertNull(validateCustomSkill("Гадание", setOf(AttributeId.WILL)))
    }
}
