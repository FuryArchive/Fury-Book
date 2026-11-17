package com.furybook.dubl.ui.sheet

import com.furybook.dubl.model.AttributeId
import com.furybook.dubl.model.CharacterConditionId
import com.furybook.dubl.model.CharacterSheetExtras
import com.furybook.dubl.model.CharacterSheetResourceId
import com.furybook.dubl.model.CharacterSkill
import com.furybook.dubl.model.CustomCondition
import com.furybook.dubl.model.CustomResource
import com.furybook.dubl.model.DublCharacter
import com.furybook.dubl.model.RollContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SheetModelsTest {
    private val plain = DublCharacter(id = "c", name = "Тест", hpCurrent = 5, enduranceCurrent = 5)

    @Test
    fun noTrainedSkillsGivesNoGroups() {
        assertTrue(sheetSkillGroups(FakeSheetHost(plain)).isEmpty())
    }

    @Test
    fun trainedSkillsAreGroupedWithRankAndBonus() {
        val trained = plain.copy(
            skills = mapOf("melee_weapon" to CharacterSkill(id = "melee_weapon", definitionId = "melee_weapon", rank = 3)),
        )
        val groups = sheetSkillGroups(FakeSheetHost(trained))
        val row = groups.flatMap { it.rows }.single()
        assertEquals("melee_weapon", row.skillId)
        assertEquals(3, row.rank)
    }

    @Test
    fun manaHiddenWhenDisabled() {
        val ids = sheetVitals(FakeSheetHost(plain)).map { it.id }
        assertEquals(listOf("health", "endurance"), ids)
    }

    @Test
    fun manaShownWhenEnabledAndCustomResourcesFollow() {
        val withMana = plain.copy(
            manaEnabled = true,
            customResources = listOf(CustomResource("u1", "Ярость", 2, 4)),
        )
        val vitals = sheetVitals(FakeSheetHost(withMana))
        assertEquals(listOf("health", "endurance", "mana", "custom:u1"), vitals.map { it.id })
        val custom = vitals.last()
        assertEquals("u1", custom.customUid)
        assertNull(custom.resource)
        assertEquals(SheetVitalColor.CUSTOM, custom.color)
        assertEquals(SheetVitalColor.MANA, vitals[2].color)
    }

    @Test
    fun hiddenResourceIsOmitted() {
        val extras = CharacterSheetExtras(hiddenResourceIds = setOf(CharacterSheetResourceId.HEALTH))
        val ids = sheetVitals(FakeSheetHost(plain, extras)).map { it.id }
        assertEquals(listOf("endurance"), ids)
    }

    @Test
    fun attributesInSheetOrder() {
        val stats = sheetAttributes(plain)
        assertEquals(
            listOf("Сила", "Ловкость", "Телосложение", "Скорость", "Интеллект", "Восприятие", "Воля", "Харизма"),
            stats.map { it.label },
        )
        stats.forEachIndexed { index, stat ->
            assertEquals(SheetRollSubject.Attribute(AttributeId.entries[index]), stat.roll)
        }
    }

    @Test
    fun metricsMarkRollableOnes() {
        val metrics = sheetMetrics(plain).associateBy { it.label }
        assertEquals(listOf("Защита", "Рефлексы", "Инициатива", "Стойкость", "Бег", "Размер"), sheetMetrics(plain).map { it.label })
        assertNull(metrics.getValue("Защита").roll)
        assertNull(metrics.getValue("Размер").roll)
        assertEquals(SheetRollSubject.Context(RollContext.REFLEXES), metrics.getValue("Рефлексы").roll)
        assertEquals(SheetRollSubject.Context(RollContext.INITIATIVE), metrics.getValue("Инициатива").roll)
        assertEquals(SheetRollSubject.Context(RollContext.FORTITUDE), metrics.getValue("Стойкость").roll)
        assertEquals(SheetRollSubject.Context(RollContext.RUN), metrics.getValue("Бег").roll)
        assertEquals(plain.defense, metrics.getValue("Защита").value)
    }

    @Test
    fun combatContextsAreInPanelOrder() {
        assertEquals(
            listOf(
                RollContext.DODGE, RollContext.ATTACK, RollContext.FLURRY, RollContext.PARRY, RollContext.FEINT,
                RollContext.GRAPPLE, RollContext.DISARM, RollContext.TRIP, RollContext.PUSH, RollContext.KNOCKDOWN,
                RollContext.BREAK_ITEM,
            ),
            SHEET_COMBAT_CONTEXTS,
        )
    }

    @Test
    fun activeConditionsIncludeCustom() {
        val extras = CharacterSheetExtras(
            activeConditions = setOf(CharacterConditionId.PRONE),
            customConditions = listOf(
                CustomCondition("k1", "Благословлён", active = true),
                CustomCondition("k2", "Спрятан", active = false),
            ),
        )
        val chips = sheetActiveConditions(FakeSheetHost(plain, extras))
        assertEquals(listOf("Сбит с ног", "Благословлён"), chips.map { it.title })
        assertTrue(chips.all { it.active })
        assertEquals(listOf(false, true), chips.map { it.custom })
        assertNotNull(chips.first { it.custom }.key)
    }

    @Test
    fun zeroEnduranceAddsAutomaticWeakness() {
        val drained = plain.copy(enduranceCurrent = 0)
        val chips = sheetActiveConditions(FakeSheetHost(drained))
        assertEquals(listOf("Слабость"), chips.map { it.title })
    }

    @Test
    fun runTextUsesDesktopNumberFormat() {
        assertEquals("7", formatSheetNumber(7.0))
        assertEquals("7.5", formatSheetNumber(7.5))
        assertEquals("7.25", formatSheetNumber(7.25))
        assertEquals("0.25", formatSheetNumber(0.25))
        assertEquals("1.05", formatSheetNumber(1.05))
        val run = sheetMetrics(plain).single { it.label == "Бег" }
        assertEquals(formatSheetNumber(plain.runFull), run.valueText)
        assertEquals("5", sheetMetrics(plain).single { it.label == "Размер" }.valueText)
    }
}
