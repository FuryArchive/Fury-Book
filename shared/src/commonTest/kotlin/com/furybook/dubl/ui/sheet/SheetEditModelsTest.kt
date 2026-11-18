package com.furybook.dubl.ui.sheet

import com.furybook.dubl.model.AbilityOption
import com.furybook.dubl.model.CharacterEconomy
import com.furybook.dubl.model.DevelopmentCatalog
import com.furybook.dubl.model.DevelopmentCostType
import com.furybook.dubl.model.DevelopmentEntry
import com.furybook.dubl.model.DublCharacter
import com.furybook.dubl.model.OwnedDevelopment
import com.furybook.dubl.model.SheetGroupingRules
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SheetEditModelsTest {
    private val access = DevelopmentEntry(
        id = "access_assassin", name = "Ассасин", section = "Особые способности", category = "Особые способности",
        cost = 0, costType = DevelopmentCostType.ABILITY, maxRank = 1, requirements = "Скрытность 4",
        benefit = "Открывает ветку", notes = "", tags = emptyList(), accessId = null,
        abilityOptions = listOf(AbilityOption("Мастерство", 1)), incomplete = false, repeatable = false,
        perfectRoot = false, mechanicsConflict = "", conflictNote = "",
    )
    private val child = access.copy(
        id = "feat_sneak_attack", name = "Подлая атака", section = "Ветки способностей", category = "Ассасин",
        cost = 40, costType = DevelopmentCostType.XP, maxRank = 2, requirements = "Ассасин, Ловкость 4",
        tags = listOf("Ассасин"), accessId = "access_assassin", abilityOptions = emptyList(),
    )
    private val catalog = DevelopmentCatalog("test", listOf(access, child))

    private fun devHost() = FakeSheetHost(
        DublCharacter(
            id = "c1", name = "Тест", experience = 1000,
            development = mapOf(access.id to OwnedDevelopment(rank = 1), child.id to OwnedDevelopment(rank = 1)),
        ),
        developmentCatalogOverride = catalog,
    )

    @Test
    fun economyIncludesChiOnlyWhenPackOn() {
        val character = DublCharacter(id = "c1", name = "Тест", experience = 500)
        val off = sheetEconomyView(FakeSheetHost(character, chiPackEnabled = false))
        val on = sheetEconomyView(FakeSheetHost(character, chiPackEnabled = true))
        assertFalse(off.lines.any { it.first == "ЦИ" })
        assertTrue(on.lines.any { it.first == "ЦИ" })
    }

    @Test
    fun recommendedAbilityPointsMatchesEconomy() {
        val host = FakeSheetHost(DublCharacter(id = "c1", name = "Тест", experience = 500))
        val expected = CharacterEconomy.breakdown(host.active, host.developmentCatalog, includeChi = false).recommendedAbilityPoints
        assertEquals(expected, sheetEconomyView(host).recommendedAbilityPoints)
    }

}

class SheetChiMountTest {
    @Test
    fun chiXpFollowsTheFcpEconomyMountNotAFlag() {
        val character = DublCharacter(id = "c1", name = "Тест", experience = 500)
        assertFalse(sheetChiEconomyMounted(FakeSheetHost(character, chiPackEnabled = false)))
        assertTrue(sheetChiEconomyMounted(FakeSheetHost(character, chiPackEnabled = true)))
    }
}
