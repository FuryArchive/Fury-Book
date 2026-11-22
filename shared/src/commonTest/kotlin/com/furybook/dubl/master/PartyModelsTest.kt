package com.furybook.dubl.master

import com.furybook.core.cloud.CampaignDashboard
import com.furybook.core.cloud.CampaignMember
import com.furybook.dubl.model.CharacterConditionId
import com.furybook.dubl.model.CharacterGear
import com.furybook.dubl.model.CharacterMagic
import com.furybook.dubl.model.CharacterSheetExtras
import com.furybook.dubl.model.ConditionCatalog
import com.furybook.dubl.model.DevelopmentCatalog
import com.furybook.dubl.model.DublCharacter
import com.furybook.dubl.model.GearItem
import com.furybook.dubl.model.KnownSpell
import com.furybook.dubl.model.MagicSchool
import com.furybook.dubl.model.OwnedDevelopment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PartyModelsTest {
    private fun member(character: DublCharacter, extras: CharacterSheetExtras = CharacterSheetExtras()) =
        CampaignMember("u1", "artem", character.id, character, extras, 3, "2026-11-21T10:00:00Z", "web", "")

    @Test
    fun partyRowsReadVitalsAndConditions() {
        val c = DublCharacter(id = "c1", name = "Вика", hpCurrent = 4, enduranceCurrent = 0, manaEnabled = true, manaCurrent = 2)
        val dashboard = CampaignDashboard("k1", "Дорога", "gm", "", listOf(member(c, CharacterSheetExtras(activeConditions = setOf(CharacterConditionId.entries.first())))))
        val row = partyRows(dashboard, ConditionCatalog(emptyList())).single()
        assertEquals("artem", row.nickname)
        assertEquals("Вика", row.characterName)
        assertEquals(4 to c.healthMaximum, row.hp)
        assertEquals(0 to c.enduranceMaximum, row.endurance)
        assertEquals(2 to c.effectiveManaMaximum, row.mana)
        assertEquals(c.defense, row.defense)
        assertEquals(c.initiative, row.initiative)
        // The chosen condition, plus the automatic Слабость at 0 endurance, as the sheet shows them.
        assertTrue(CharacterConditionId.entries.first().title in row.conditions)
        assertTrue(CharacterConditionId.WEAKNESS.title in row.conditions)
        assertEquals(null, partyRows(dashboard.copy(players = listOf(member(c.copy(manaEnabled = false)))), ConditionCatalog(emptyList())).single().mana)
    }

    @Test
    fun readOnlyViewToleratesMissingPack() {
        val c = DublCharacter(id = "c1", name = "Шершень", development = mapOf("chi.missing" to OwnedDevelopment(rank = 2)))
        val view = readOnlyView(member(c), DevelopmentCatalog("t", emptyList()))
        assertEquals(listOf("chi.missing · нет пакета"), view.perks)
    }

    @Test
    fun readOnlyViewListsLearnedSpellsAndCarriedGear() {
        val c = DublCharacter(
            id = "c1", name = "Вика",
            magic = CharacterMagic(
                schools = listOf(MagicSchool("Разрушение", 4)),
                spells = listOf(KnownSpell("s1", name = "Огненный шар", school = "Разрушение", cost = 4), KnownSpell("s2", name = "Щит", learned = false)),
            ),
            gear = CharacterGear(items = listOf(GearItem("g1", name = "Кинжал", quantity = 2), GearItem("g2", name = "Палатка", carried = false))),
        )
        val view = readOnlyView(member(c), DevelopmentCatalog("t", emptyList()))
        assertEquals(listOf("Огненный шар"), view.spells.map { it.first })
        assertEquals(listOf("Разрушение" to "4"), view.schools)
        assertEquals(listOf("Кинжал" to "×2", "Палатка" to "×1 · не несёт"), view.gear)
        assertTrue(view.vitals.any { it.first == "Здоровье" })
        assertTrue(view.derived.any { it.first == "Внимательность" })
    }
}

class PartyTimeTest {
    @Test
    fun timeIsShortened() {
        assertEquals("21.11 10:05", com.furybook.dubl.ui.master.partyTime("2026-11-21T10:05:33.123Z"))
        assertEquals("вчера", com.furybook.dubl.ui.master.partyTime("вчера"))
    }
}
