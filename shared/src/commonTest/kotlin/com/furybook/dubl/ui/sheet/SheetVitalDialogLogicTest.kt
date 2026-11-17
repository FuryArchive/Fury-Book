package com.furybook.dubl.ui.sheet

import com.furybook.dubl.model.CharacterSheetResourceId
import com.furybook.dubl.model.DublCharacter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SheetVitalDialogLogicTest {
    private fun hp(current: Int, maximum: Int) = SheetVital(
        "health", "Здоровье", current, maximum, SheetVitalColor.HEALTH, CharacterSheetResourceId.HEALTH, null, false,
    )

    @Test
    fun damageBelowZeroKeepsNegative() {
        assertEquals(-20, vitalAmountDelta(VitalAction.DAMAGE, 20, hp(12, 30)))
        val host = FakeSheetHost(DublCharacter(id = "c1", name = "Тест", hpCurrent = 12, healthMaximumOverride = 30))
        host.changeResource(CharacterSheetResourceId.HEALTH, vitalAmountDelta(VitalAction.DAMAGE, 20, hp(12, 30)))
        assertEquals(-8, host.active.hpCurrent)
    }

    @Test
    fun healStopsAtMaximum() {
        assertEquals(3, vitalAmountDelta(VitalAction.HEAL, 10, hp(27, 30)))
    }

    @Test
    fun restoreAllIsMaxMinusCurrent() {
        assertEquals(38, vitalAmountDelta(VitalAction.RESTORE_ALL, 1, hp(-8, 30)))
    }

    @Test
    fun junkAmountDisablesActions() {
        assertNull(parseIntField("abc"))
        assertNull(parseIntField("-"))
        assertNull(parseIntField(""))
        assertEquals(15, parseIntField(" 15 "))
    }

    @Test
    fun blankOptionalFieldMeansFormula() {
        assertEquals(OptionalIntField.Blank, parseOptionalIntField("  "))
        assertEquals(OptionalIntField.Value(12), parseOptionalIntField("12"))
        assertEquals(OptionalIntField.Invalid, parseOptionalIntField("1x"))
    }
}
