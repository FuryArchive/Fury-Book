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

    @Test
    fun takenPerkStaysListedWhileItIsOpen() {
        // Taking an open perk under «Доступно» must not make the row vanish and the list slide under the cursor.
        val single = entry("feat_once", "Разовый", "Общие", 10, 1, "", "Один ранг.")
        val host = host(devCatalog = DevelopmentCatalog("test", catalog.entries + single))
        val detail = editorPerkDetail(host, single.id, emptyMap(), null)!!
        assertTrue(host.acquireDevelopment((detail.action as EditorPerkAction.Acquire).request).applied)
        fun listed(keep: String?) = editorPerkGroups(host, EditorPerkKind.REGULAR, EditorPerkFilter.AVAILABLE, "", keep).flatMap { g -> g.cards.map { it.entryId } }
        assertTrue(single.id in listed(keep = single.id))
        assertFalse(single.id in listed(keep = null))
    }

    @Test
    fun allRanksOfferedOnlyWithTwoLeft() {
        val host = host()
        val fresh = editorPerkDetail(host, keenHearing.id, emptyMap(), null)!!
        assertTrue(fresh.allRanksLabel!!.startsWith("Взять все оставшиеся ранги · "), fresh.allRanksLabel)
        assertEquals(2, fresh.allRanksAction!!.request.targets.single().targetRank)
        assertTrue(host.acquireDevelopment(fresh.allRanksAction!!.request).applied)
        assertEquals(2, host.active.development.getValue(keenHearing.id).rank)
        val oneLeft = host(hornet.copy(development = hornet.development + (keenHearing.id to com.furybook.dubl.model.OwnedDevelopment(rank = 1))))
        assertNull(editorPerkDetail(oneLeft, keenHearing.id, emptyMap(), null)!!.allRanksAction)
    }

    @Test
    fun prerequisitesOnlyWhenChainExists() {
        val host = host()
        val chained = editorPerkDetail(host, ranger.id, emptyMap(), null)!!
        val request = chained.prerequisitesAction!!.request
        assertTrue(chained.prerequisitesLabel!!.startsWith("Добрать требования · "), chained.prerequisitesLabel)
        assertTrue(host.acquireDevelopment(request).applied)
        assertEquals(3, host.active.skills.getValue("survival").rank)
        assertNull(host.active.development[ranger.id])
        assertNull(editorPerkDetail(host(), keenHearing.id, emptyMap(), null)!!.prerequisitesAction)
    }

    @Test
    fun linksCarryTargets() {
        val host = host()
        val branch = editorPerkDetail(host, EditorFixtures.branch.id, emptyMap(), null)!!
        assertTrue(branch.requirements.any { it.targetId == EditorFixtures.access.id }, branch.requirements.toString())
        val access = editorPerkDetail(host, EditorFixtures.access.id, emptyMap(), null)!!
        assertTrue(EditorPerkLink(EditorFixtures.branch.id, "Разметать") in access.unlocks, access.unlocks.toString())
    }

    @Test
    fun originMarksCustomAndEdited() {
        val host = host()
        host.setDevelopmentOverride(keenHearing.copy(cost = 10))
        val custom = host.addCustomDevelopment(entry("", "Гадание"))!!
        assertEquals(EditorPerkOrigin.BOOK_EDITED, editorPerkDetail(host, keenHearing.id, emptyMap(), null)!!.origin)
        assertEquals(EditorPerkOrigin.CUSTOM, editorPerkDetail(host, custom, emptyMap(), null)!!.origin)
        assertEquals(EditorPerkOrigin.BOOK, editorPerkDetail(host, ranger.id, emptyMap(), null)!!.origin)
    }
}
