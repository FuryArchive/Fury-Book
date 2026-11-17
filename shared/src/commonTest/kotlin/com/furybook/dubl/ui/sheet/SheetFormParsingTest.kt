package com.furybook.dubl.ui.sheet

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SheetFormParsingTest {
    @Test
    fun blankOverrideIsAuto() {
        assertEquals(SheetEconomyForm(500, 300, 0, null), sheetEconomyForm("500", "300", "0", ""))
    }

    @Test
    fun junkIsInvalid() {
        assertNull(sheetEconomyForm("5оо", "300", "0", ""))
        assertNull(sheetEconomyForm("500", "300", "0", "x"))
        assertNull(sheetEconomyForm("", "300", "0", ""))
    }

    @Test
    fun negativeAllowedWhereOldDialogAllowedIt() {
        assertEquals(-40, sheetEconomyForm("500", "300", "-40", "")?.adjustment)
        assertNull(sheetEconomyForm("-1", "300", "0", ""))
    }
}
