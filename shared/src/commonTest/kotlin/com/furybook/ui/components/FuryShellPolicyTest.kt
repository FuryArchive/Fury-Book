package com.furybook.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals

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
}
