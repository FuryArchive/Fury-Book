package com.furybook.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals

class FuryBarLogicTest {
    @Test
    fun zeroMaximumIsEmptyAndContinuous() = assertEquals(FuryBarModel(0f, 0, 0), furyBarModel(5, 0))

    @Test
    fun currentAboveMaximumClamps() = assertEquals(FuryBarModel(1f, 3, 3), furyBarModel(9, 3))

    @Test
    fun negativeCurrentClamps() = assertEquals(FuryBarModel(0f, 3, 0), furyBarModel(-2, 3))

    @Test
    fun largeMaximumIsContinuous() = assertEquals(FuryBarModel(0.5f, 0, 0), furyBarModel(16, 32))

    @Test
    fun smallMaximumIsSegmented() = assertEquals(FuryBarModel(2f / 3f, 3, 2), furyBarModel(2, 3))
}
