package com.furybook.dubl.ui.sheet

import com.furybook.dubl.model.CharacterConditionId
import com.furybook.dubl.model.CharacterSheetResourceId
import com.furybook.dubl.model.DublCharacter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SheetChangeReportingHostTest {
    private fun host(changes: MutableList<String>, endurance: Int = 2): DublSheetHost {
        val base = DublCharacter(id = "c1", name = "Тест", enduranceCurrent = endurance)
        return SheetChangeReportingHost(FakeSheetHost(base)) { changes += it.text }
    }

    @Test
    fun appliedResourceChangeIsReportedAndUndoable() {
        val changes = mutableListOf<String>()
        val host = host(changes)
        host.changeResource(CharacterSheetResourceId.ENDURANCE, -1)
        assertEquals(listOf("Выносливость -1"), changes)
        assertTrue(host.canUndo)
    }

    @Test
    fun resourceChangeCarriesTheResourceAndAppliedStep() {
        val reported = mutableListOf<SheetChange>()
        val host = SheetChangeReportingHost(FakeSheetHost(DublCharacter(id = "c1", enduranceCurrent = 2))) { reported += it }
        host.changeResource(CharacterSheetResourceId.ENDURANCE, -1)
        assertEquals(listOf<SheetChange>(SheetChange.Resource(CharacterSheetResourceId.ENDURANCE, -1)), reported)
    }

    @Test
    fun resourceChangeThatAppliesNothingIsNotReported() {
        val changes = mutableListOf<String>()
        val host = host(changes, endurance = 0)
        host.changeResource(CharacterSheetResourceId.ENDURANCE, -1)
        assertEquals(emptyList(), changes)
    }

    @Test
    fun conditionToggleIsReported() {
        val changes = mutableListOf<String>()
        val host = host(changes)
        val condition = CharacterConditionId.entries.first()
        host.toggleCondition(condition)
        host.toggleCondition(condition)
        assertEquals(listOf("Добавлено состояние: ${condition.title}", "Убрано состояние: ${condition.title}"), changes)
    }
}

class SheetChangeReportingEditTest {
    @kotlin.test.Test
    fun attributeChangeReportsUndoToast() {
        val changes = mutableListOf<SheetChange>()
        val host = SheetChangeReportingHost(FakeSheetHost(com.furybook.dubl.model.DublCharacter(id = "c1", name = "Тест"))) { changes += it }
        host.changeAttribute(com.furybook.dubl.model.AttributeId.STRENGTH, 1)
        kotlin.test.assertEquals(listOf("Сила +1"), changes.map { it.text })
    }

    @kotlin.test.Test
    fun identityChangeReportsUndoToast() {
        val changes = mutableListOf<SheetChange>()
        val host = SheetChangeReportingHost(FakeSheetHost(com.furybook.dubl.model.DublCharacter(id = "c1", name = "Тест"))) { changes += it }
        host.setIdentity("Шершень", "", 5, 2, false)
        kotlin.test.assertEquals(listOf("Персонаж изменён"), changes.map { it.text })
    }
}
