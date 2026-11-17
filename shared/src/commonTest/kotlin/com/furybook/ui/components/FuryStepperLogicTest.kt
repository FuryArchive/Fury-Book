package com.furybook.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals

class FuryStepperLogicTest {
    @Test
    fun atMinimumCannotDecrease() = assertEquals(FuryStepperState(false, true), furyStepperState(0, 0, 5))

    @Test
    fun atMaximumCannotIncrease() = assertEquals(FuryStepperState(true, false), furyStepperState(5, 0, 5))

    @Test
    fun disabledBlocksBoth() = assertEquals(FuryStepperState(false, false), furyStepperState(2, 0, 5, enabled = false))

    @Test
    fun valueOutsideRangeStillAllowsMovingBackIn() = assertEquals(FuryStepperState(true, false), furyStepperState(9, 0, 5))
}
