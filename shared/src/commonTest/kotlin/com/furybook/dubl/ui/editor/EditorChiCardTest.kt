package com.furybook.dubl.ui.editor

import com.furybook.dubl.model.ChiCatalog
import com.furybook.dubl.model.ChiTechnique
import com.furybook.dubl.model.CharacterEconomy
import com.furybook.dubl.model.DevelopmentEffectIds
import com.furybook.dubl.model.OwnedDevelopment
import com.furybook.dubl.ui.sheet.FakeSheetHost
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EditorChiCardTest {
    private val technique = ChiTechnique("t1", "Удар ладонью", "Школа ветра", chiCost = 2, action = "1 ОД", effect = "Отбрасывает цель.", requirements = "")
    private fun host(character: com.furybook.dubl.model.DublCharacter = EditorFixtures.hornet, pack: Boolean = true) = FakeSheetHost(
        character, chiPackEnabled = pack, developmentCatalogOverride = EditorFixtures.catalog,
        chiCatalogOverride = ChiCatalog("t", emptyList(), listOf(technique)),
    )

    @Test
    fun chiCardHiddenWithoutPack() {
        assertNull(editorChiCard(host(pack = false)))
    }

    @Test
    fun chiSwitchLockedByInternalChi() {
        val owner = EditorFixtures.hornet.copy(development = EditorFixtures.hornet.development + (DevelopmentEffectIds.INTERNAL_CHI to OwnedDevelopment(rank = 1)))
        val card = editorChiCard(host(owner))!!
        assertTrue(card.locked)
        assertTrue(card.active)
        assertNotNull(card.lockReason)
        assertFalse(editorChiCard(host())!!.locked)
    }

    @Test
    fun chiCardShowsMaximumAndTechniques() {
        val h = host(EditorFixtures.hornet.copy(chiEnabled = true, chiBonusRanks = 2, chiCurrent = 0))
        val c = h.active
        val card = editorChiCard(h)!!
        assertEquals("Максимум: база ${c.chiBaseMaximum} + запас 2 + развитие 0 = ${c.chiMaximum}", card.maximumText)
        assertEquals(CharacterEconomy.CHI_BONUS_RANK_XP, card.rankXp)
        assertEquals(2, card.bonusRanks)
        val line = card.techniques.single()
        assertEquals("Удар ладонью", line.name)
        assertEquals("Недостаточно ЦИ: нужно 2", line.status)
    }

    @Test
    fun techniquesFollowSearchAndAvailableFilter() {
        // «Доступно» keeps techniques whose requirements are met (the old tab's `unlocked`), even when ЦИ is short right now.
        val locked = ChiTechnique("t2", "Шаг ветра", "Базовые приёмы", chiCost = 1, action = "1 ОД", effect = "Бег удваивается.", requirements = "Сила 20")
        val h = FakeSheetHost(
            EditorFixtures.hornet.copy(chiEnabled = true, chiCurrent = 1), chiPackEnabled = true, developmentCatalogOverride = EditorFixtures.catalog,
            chiCatalogOverride = ChiCatalog("t", emptyList(), listOf(technique, locked)),
        )
        assertEquals(listOf("Шаг ветра"), editorChiCard(h, query = "шаг")!!.techniques.map { it.name })
        assertEquals(listOf("Удар ладонью"), editorChiCard(h, query = "отбрасывает")!!.techniques.map { it.name })
        assertEquals(listOf("Удар ладонью"), editorChiCard(h, availableOnly = true)!!.techniques.map { it.name })
        assertEquals(2, editorChiCard(h)!!.techniques.size)
    }
}
