package com.furybook.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FuryShellPolicyTest {
    private val androidSections = listOf(
        FuryShellSection("OVERVIEW", "Лист", primary = true),
        FuryShellSection("SKILLS", "Умения", primary = true),
        FuryShellSection("FEATS", "Навыки", primary = true),
        FuryShellSection("MAGIC", "Магия", primary = true),
        FuryShellSection("INVENTORY", "Вещи", primary = true),
        FuryShellSection("CAMPAIGN", "Кампания", primary = false),
        FuryShellSection("MORE", "Ещё", primary = false),
    )

    @Test
    fun fivePrimarySectionsBecomeBottomTabsInOrder() =
        assertEquals(listOf("Лист", "Умения", "Навыки", "Магия", "Вещи"), furyBottomTabs(androidSections).map { it.label })

    @Test
    fun theRestGoBehindTheMenu() =
        assertEquals(listOf("Кампания", "Ещё"), furyMenuSections(androidSections).map { it.label })

    @Test
    fun aSixthPrimarySectionStaysReachableThroughTheMenu() {
        val sections = androidSections.take(5) + FuryShellSection("EXTRA", "Ещё одна", primary = true) + androidSections.drop(5)
        assertEquals(5, furyBottomTabs(sections).size)
        assertEquals(listOf("Ещё одна", "Кампания", "Ещё"), furyMenuSections(sections).map { it.label })
    }

    @Test
    fun phoneTabsUseTheShortLabelWideTabsTheFullOne() {
        val equipment = FuryShellSection("EQUIPMENT", "Снаряжение", primary = true, phoneLabel = "Вещи")
        val sections = androidSections.take(4) + equipment + androidSections.drop(5)
        assertEquals(listOf("Лист", "Умения", "Навыки", "Магия", "Вещи"), furyBottomTabs(sections).map { it.bottomTabLabel })
        assertEquals("Снаряжение", furyBottomTabs(sections).last().label)
        assertEquals("Кампания", furyMenuSections(sections).first().bottomTabLabel)
    }

    @Test
    fun phoneShellBelow600dp() {
        assertEquals(true, furyShellIsPhone(599))
        assertEquals(false, furyShellIsPhone(600))
    }

    @Test
    fun tabsGetTheirOwnRowOnlyWhenTheyDoNotFitBesideTheTitle() {
        // Natural tab width vs the room left beside the wordmark and the character button (px).
        assertTrue(furyShellTabsOnOwnRow(tabsWidth = 680, room = 420))
        assertFalse(furyShellTabsOnOwnRow(tabsWidth = 680, room = 700))
        assertFalse(furyShellTabsOnOwnRow(tabsWidth = 0, room = 420))
    }

    @Test
    fun noPrimarySectionsMeansNoBottomTabs() {
        val sections = listOf(
            FuryShellSection("SHEET", "Лист", primary = false),
            FuryShellSection("CAMPAIGN", "Кампания", primary = false),
            FuryShellSection("CHARACTERS", "Персонажи", primary = false),
        )
        assertTrue(furyBottomTabs(sections).isEmpty())
        assertEquals(listOf("Лист", "Кампания", "Персонажи"), furyMenuSections(sections).map { it.label })
    }
}
