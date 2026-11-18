package com.furybook.dubl.ui.editor

import com.furybook.dubl.model.DevelopmentCatalog
import com.furybook.dubl.model.DublCharacter
import com.furybook.dubl.model.MagicEquipmentRules
import com.furybook.dubl.ui.editor.EditorFixtures.athleticism
import com.furybook.dubl.ui.editor.EditorFixtures.beauty
import com.furybook.dubl.ui.editor.EditorFixtures.catalog
import com.furybook.dubl.ui.editor.EditorFixtures.entry
import com.furybook.dubl.ui.editor.EditorFixtures.hornet
import com.furybook.dubl.ui.editor.EditorFixtures.keenHearing
import com.furybook.dubl.ui.editor.EditorFixtures.lockpick
import com.furybook.dubl.ui.editor.EditorFixtures.quickAim
import com.furybook.dubl.ui.editor.EditorFixtures.ranger
import com.furybook.dubl.ui.sheet.FakeSheetHost
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EditorPerkModelsTest {
    private fun host(character: DublCharacter = hornet, devCatalog: DevelopmentCatalog = catalog) =
        FakeSheetHost(character, developmentCatalogOverride = devCatalog)

    private fun status(host: FakeSheetHost, id: String) = editorPerkStatus(host, host.developmentCatalog.byId(id)!!)

    @Test
    fun statusAvailableAlmostRequirements() {
        val host = host()
        assertEquals(EditorPerkStatus.AVAILABLE, status(host, keenHearing.id))
        assertEquals(EditorPerkStatus.ALMOST, status(host, ranger.id))
        assertEquals(EditorPerkStatus.REQUIREMENTS, status(host, lockpick.id))
        val card = editorPerkGroups(host, EditorPerkKind.REGULAR, EditorPerkFilter.ALL, "").flatMap { it.cards }.first { it.entryId == athleticism.id }
        assertEquals("Взят · 1/2", card.pill)
        assertTrue(card.warn)
    }

    @Test
    fun creationOnlyAfterComplete() {
        assertEquals(EditorPerkStatus.CREATION_ONLY, status(host(), beauty.id))
        assertNotEquals(EditorPerkStatus.CREATION_ONLY, status(host(hornet.copy(creationComplete = false)), beauty.id))
    }

    @Test
    fun chainDetail() {
        val host = host()
        val detail = editorPerkDetail(host, ranger.id, emptyMap(), null)!!
        assertEquals(2, detail.chain.size)
        assertTrue(detail.actionLabel!!.startsWith("Взять с цепочкой · "), detail.actionLabel)
        val action = detail.action as EditorPerkAction.Acquire
        assertTrue(host.acquireDevelopment(action.request).applied)
        assertEquals(3, host.active.skills.getValue("survival").rank)
        assertEquals(1, host.active.development.getValue(ranger.id).rank)
    }

    @Test
    fun choiceSelectionChangesChain() {
        val host = host()
        val first = editorPerkDetail(host, quickAim.id, emptyMap(), null)!!
        val choice = first.choices.single()
        assertEquals(2, choice.options.size)
        val second = editorPerkDetail(host, quickAim.id, mapOf(choice.id to 1 - choice.selected), null)!!
        assertNotEquals(first.chain.first().label, second.chain.first().label)
        assertNotEquals(first.chainCost, second.chainCost)
        val throwing = choice.options.indexOfFirst { it.startsWith("Метание") }
        val picked = editorPerkDetail(host, quickAim.id, mapOf(choice.id to throwing), null)!!
        assertTrue(host.acquireDevelopment((picked.action as EditorPerkAction.Acquire).request).applied)
        assertEquals(5, host.active.skills.values.single { it.definitionId == "throwing" || it.id == "throwing" }.rank)
        assertEquals(4, host.active.skills.getValue("shooting").rank)
    }

    @Test
    fun chainMarksWhichStepIsThePerkItself() {
        val chain = editorPerkDetail(host(), ranger.id, emptyMap(), null)!!.chain
        assertEquals(listOf(false, true), chain.map { it.isTarget })
    }

    @Test
    fun overspentBlocksTakeWithReason() {
        val host = host(hornet.copy(experience = 2000))
        val detail = editorPerkDetail(host, keenHearing.id, emptyMap(), null)!!
        assertNull(detail.action)
        assertTrue(detail.blockReason!!.startsWith("Перерасход опыта: свободно -"), detail.blockReason)
        assertNotNull(detail.forceAction)
        assertEquals(EditorPerkStatus.LOW_XP, status(host, keenHearing.id))
    }

    @Test
    fun ownedPerkCanBeRemoved() {
        val detail = editorPerkDetail(host(), athleticism.id, emptyMap(), null)!!
        assertEquals("Убрать (+40 XP)", detail.removeLabel)
        assertEquals(EditorPerkAction.SetRank(0, 0), detail.removeAction)
    }

    @Test
    fun emptyBenefitHasPlaceholder() {
        val detail = editorPerkDetail(host(), lockpick.id, emptyMap(), null)!!
        assertTrue(detail.benefitMissing)
        assertEquals("Нет описания в правилах — см. требования.", detail.benefit)
    }

    @Test
    fun chiTabOnlyWhenMounted() {
        assertFalse(editorShowsChi(host()))
    }

    @Test
    fun baseManaNeverListed() {
        val mana = entry(MagicEquipmentRules.BASE_MANA_ENTRY_ID, "Базовый запас маны", "Магические навыки", 100, 5)
        val host = host(devCatalog = DevelopmentCatalog("test", catalog.entries + mana))
        val ids = EditorPerkKind.entries.flatMap { kind -> editorPerkGroups(host, kind, EditorPerkFilter.ALL, "").flatMap { it.cards } }.map { it.entryId }
        assertFalse(MagicEquipmentRules.BASE_MANA_ENTRY_ID in ids)
    }

    @Test
    fun filtersAndCounts() {
        val host = host()
        val counts = editorPerkCounts(host, EditorPerkKind.REGULAR)
        val available = editorPerkGroups(host, EditorPerkKind.REGULAR, EditorPerkFilter.AVAILABLE, "").flatMap { it.cards }
        assertEquals(counts.getValue(EditorPerkFilter.AVAILABLE), available.size)
        assertTrue(available.all { it.status == EditorPerkStatus.AVAILABLE || it.status == EditorPerkStatus.UPGRADABLE })
        val found = editorPerkGroups(host, EditorPerkKind.REGULAR, EditorPerkFilter.ALL, "слух").flatMap { it.cards }
        assertEquals(listOf(keenHearing.id), found.map { it.entryId })
    }

    @Test
    fun overspentAbilityPointsDoNotBlockXpPerks() {
        // The rules treat the ОС budget as a recommendation; a build already over it can still buy XP-only perks.
        val host = host(hornet.copy(abilityPointsOverride = 0))
        assertEquals(EditorPerkStatus.AVAILABLE, status(host, keenHearing.id))
        val detail = editorPerkDetail(host, keenHearing.id, emptyMap(), null)!!
        val action = detail.action as EditorPerkAction.Acquire
        assertTrue(host.acquireDevelopment(action.request).applied)
        val chain = editorPerkDetail(host, ranger.id, emptyMap(), null)!!.action as EditorPerkAction.Acquire
        assertTrue(host.acquireDevelopment(chain.request).applied)
    }
}
