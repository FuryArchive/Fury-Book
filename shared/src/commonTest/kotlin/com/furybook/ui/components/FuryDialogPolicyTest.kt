package com.furybook.ui.components

import com.furybook.ui.layout.DublLayoutClass
import kotlin.test.Test
import kotlin.test.assertEquals

class FuryDialogPolicyTest {
    @Test
    fun phoneWidthUsesBottomSheet() =
        assertEquals(FuryDialogPresentation.BottomSheet, furyDialogPresentation(DublLayoutClass.COMPACT, 390))

    @Test
    fun narrowDesktopWindowStaysCentered() =
        assertEquals(FuryDialogPresentation.Centered, furyDialogPresentation(DublLayoutClass.COMPACT, 720))

    @Test
    fun wideUsesCentered() =
        assertEquals(FuryDialogPresentation.Centered, furyDialogPresentation(DublLayoutClass.WIDE, 1400))
}
