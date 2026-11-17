package com.furybook.dubl.ui.sheet

import com.furybook.dubl.model.DublCharacter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SheetStatBreakdownTest {
    private val character = DublCharacter(id = "c1", name = "Тест")

    @Test
    fun statBreakdownRunListsBaseMultiplierAndRunner() {
        val run = sheetStatBreakdown(character, SheetStatId.RUN)
        assertEquals("${formatSheetNumber(character.runFull)} м", run.value)
        assertTrue(run.lines.contains("Базовый бег: ${formatSheetNumber(character.runBase)} м"))
        assertTrue(run.lines.contains("Множитель: ${formatSheetNumber(character.runMultiplier)}"))
        assertTrue(run.lines.any { it.startsWith("Бегун: ") })
        assertTrue(run.note!!.contains("Размера (${character.size})"))
    }

    @Test
    fun statBreakdownDefenseIncludesLoadPenalty() {
        val defense = sheetStatBreakdown(character, SheetStatId.DEFENSE)
        assertEquals("10 − Размер + Скорость + Ловкость + штраф нагрузки", defense.formula)
        assertTrue(defense.lines.any { it.startsWith("Штраф нагрузки: ") })
        assertEquals("Итог: ${character.defense}", defense.lines.last())
    }
}
