package com.furybook.dubl.master

import com.furybook.dubl.model.AttributeId
import com.furybook.dubl.model.ConditionCatalog
import com.furybook.dubl.model.ConditionDefinition
import com.furybook.dubl.model.DevelopmentCatalog
import com.furybook.dubl.model.DevelopmentCostType
import com.furybook.dubl.model.DevelopmentEntry
import com.furybook.dubl.model.GearCatalogEntry
import com.furybook.dubl.model.MagicEquipmentCatalog
import com.furybook.dubl.model.SkillCategory
import com.furybook.dubl.model.SkillDefinition
import com.furybook.dubl.model.SpellCatalogEntry
import com.furybook.dubl.model.UntrainedRule
import kotlin.test.Test
import kotlin.test.assertEquals

class RulesLookupTest {
    private val perk = DevelopmentEntry(
        "p1", "Крепкий орешек", "Общие", "Общие", 20, DevelopmentCostType.entries.first(), 3, "", "+1 к здоровью", "", emptyList(), null,
        emptyList(), false, false, false, "", "",
    )
    private val spell = SpellCatalogEntry("s1", "Огненный шар", "Разрушение", 4, "4", "1 действие", "30 м", "3 м", "Магия", "мгновенно",
        "Цель получает урон и горение.", "", false, "")
    private val gear = GearCatalogEntry("g1", "Кинжал", "Холодное оружие", "Оружие", mapOf("Урон" to "2d6"), "Урон: 2d6\nЛегко спрятать.")
    private val condition = ConditionDefinition("c1", "Горение", "Каждый раунд 1 урона.")
    private val skill = SkillDefinition("k1", "Атлетика", "Бег, прыжки.", SkillCategory.PHYSICAL, AttributeId.entries.first(), UntrainedRule.YES, "", "")
    private val entries = lookupEntries(
        DevelopmentCatalog("t", listOf(perk)),
        MagicEquipmentCatalog("t", listOf(spell), listOf(gear)),
        ConditionCatalog(listOf(condition)),
        listOf(skill),
    )

    @Test
    fun lookupFindsAcrossTypes() {
        val found = searchLookup(entries, "гор", null)
        assertEquals(listOf("Горение", "Огненный шар"), found.map { it.name })
        assertEquals(listOf(LookupType.CONDITION, LookupType.SPELL), found.map { it.type })
        val dagger = searchLookup(entries, "кинжал", null).single()
        assertEquals("Легко спрятать.", dagger.text)
        assertEquals(listOf("Урон" to "2d6"), dagger.facts)
        assertEquals(5, searchLookup(entries, "", null).size)
    }

    @Test
    fun lookupTypeFilter() {
        assertEquals(listOf("Огненный шар"), searchLookup(entries, "гор", LookupType.SPELL).map { it.name })
        assertEquals(listOf("Атлетика"), searchLookup(entries, "", LookupType.SKILL).map { it.name })
    }

    @Test
    fun lookupCapsResults() {
        val many = (1..300).map { LookupEntry("c$it", LookupType.CONDITION, "Состояние $it", "", "") }
        assertEquals(200, searchLookup(many, "состояние", null).size)
    }
}
