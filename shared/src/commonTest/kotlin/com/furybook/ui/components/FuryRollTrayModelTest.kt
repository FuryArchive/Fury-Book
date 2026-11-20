package com.furybook.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals

class FuryRollTrayModelTest {
    private val base = FuryRollView(
        title = "Холодное оружие",
        dice = listOf(4, 5),
        terms = listOf(FuryRollTerm("Ловкость", 8), FuryRollTerm("ранг", 7)),
        total = 24,
    )

    @Test
    fun listsDiceSumThenTerms() = assertEquals("9 + 8 Ловкость + 7 ранг", furyRollBreakdown(base))

    @Test
    fun negativeTermUsesMinusSign() = assertEquals(
        "9 + 8 Ловкость − 2 Поправка",
        furyRollBreakdown(base.copy(terms = listOf(FuryRollTerm("Ловкость", 8), FuryRollTerm("Поправка", -2)))),
    )

    @Test
    fun zeroTermsAreOmitted() = assertEquals(
        "9 + 8 Ловкость",
        furyRollBreakdown(base.copy(terms = listOf(FuryRollTerm("Ловкость", 8), FuryRollTerm("ранг", 0)))),
    )

    @Test
    fun noDiceShowsTermsOnly() = assertEquals(
        "0 + 8 Ловкость",
        furyRollBreakdown(base.copy(dice = emptyList(), terms = listOf(FuryRollTerm("Ловкость", 8)))),
    )

    @Test
    fun droppedDiceAreLeftOutOfTheSum() = assertEquals(
        "11 + 8 Ловкость + 7 ранг",
        furyRollBreakdown(base.copy(dice = listOf(1, 6, 2, 5), keptDice = setOf(1, 3))),
    )

    @Test
    fun allDiceAreKeptByDefault() = assertEquals(setOf(0, 1), base.keptDice)
}
