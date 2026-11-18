package com.furybook.dubl.ui.editor

import com.furybook.dubl.model.AttributeId
import com.furybook.dubl.model.AttributeValue
import com.furybook.dubl.model.DublCharacter
import com.furybook.dubl.model.skillNextRankCost
import com.furybook.dubl.ui.editor.EditorFixtures.catalog
import com.furybook.dubl.ui.editor.EditorFixtures.hornet
import com.furybook.dubl.ui.sheet.FakeSheetHost
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EditorModelsTest {
    private fun host(character: DublCharacter = hornet) = FakeSheetHost(character, developmentCatalogOverride = catalog)

    @Test
    fun attributeCardsCostsAndDelta() {
        val after = hornet.copy(attributes = hornet.attributes + (AttributeId.STRENGTH to AttributeValue(base = 4)))
        val cards = editorAttributeCards(after, before = hornet)
        val dex = cards.first { it.id == AttributeId.DEXTERITY }
        assertEquals("+1: 95 XP", dex.incText)
        assertEquals("−1: вернёт 90", dex.decText)
        assertEquals(0, dex.delta)
        assertEquals(1, cards.first { it.id == AttributeId.STRENGTH }.delta)
        assertEquals(listOf("СИЛ", "ЛОВ", "ТЕЛ", "СКР", "ИНТ", "ВОС", "ВОЛ", "ХАР"), cards.map { it.short })
    }

    @Test
    fun attributeLimits() {
        val maxed = hornet.copy(attributes = hornet.attributes + (AttributeId.DEXTERITY to AttributeValue(base = 10)))
        val card = editorAttributeCards(maxed, before = maxed).first { it.id == AttributeId.DEXTERITY }
        assertEquals("максимум", card.incText)
        assertEquals(false, card.canInc)
    }

    @Test
    fun derivedShowsDelta() {
        val after = hornet.copy(attributes = hornet.attributes + (AttributeId.DEXTERITY to AttributeValue(base = 9)))
        val defense = editorDerived(after, before = hornet).first { it.label == "ЗАЩ" }
        assertEquals(1, defense.delta)
    }

    @Test
    fun skillGroupsKeepRankedFromBefore() {
        val after = hornet.copy(skills = hornet.skills + ("survival" to hornet.skills.getValue("survival").copy(rank = 0)))
        val host = host(after)
        val rows = editorSkillGroups(host, before = hornet, rankedOnly = true, query = "").flatMap { it.rows }
        val survival = rows.first { it.skillId == "survival" }
        assertEquals(0, survival.rank)
        assertEquals("+${after.skillNextRankCost(0)} XP", survival.nextCostText)
        assertTrue(rows.none { it.rank == 0 && it.skillId != "survival" })
        assertEquals(8, editorSkillGroups(host, hornet, rankedOnly = false, query = "").size)
    }

    @Test
    fun skillSearchFilters() {
        val rows = editorSkillGroups(host(), hornet, rankedOnly = false, query = "скрыт").flatMap { it.rows }
        assertEquals(listOf("stealth"), rows.map { it.skillId })
    }

    @Test
    fun developmentViewFitsFreeXp() {
        // Spent 2525 of 2570: 45 XP free.
        val view = editorDevelopmentView(host(hornet.copy(experience = 2570)))
        assertTrue(view.skills.isNotEmpty())
        assertTrue(view.skills.all { it.cost <= 45 }, view.skills.toString())
        assertTrue(view.attributes.all { it.cost <= 45 })
        assertTrue(view.perks.all { it.nextCost <= 45 })
        assertEquals(view.perks.map { it.nextCost }.sorted(), view.perks.map { it.nextCost })
        assertTrue(view.skills.size <= 6 && view.perks.size <= 6 && view.almost.size <= 4)
    }

    @Test
    fun problemsListOverspentAndNoName() {
        val fresh = DublCharacter(id = "n", name = "Новый персонаж", attributes = AttributeId.entries.associateWith { AttributeValue() } +
            (AttributeId.DEXTERITY to AttributeValue(base = 1)))
        val texts = editorProblems(host(fresh)).map { it.text }
        assertTrue("Перерасход опыта: 55 XP" in texts, texts.toString())
        assertTrue("У персонажа нет имени" in texts, texts.toString())
    }

    @Test
    fun problemsListUnmetOwnedPerk() {
        val problems = editorProblems(host())
        val line = problems.first { it.text.startsWith("Атлетичность") }
        assertEquals("Атлетичность: требования больше не выполнены", line.text)
        assertEquals(EditorStep.PERKS, line.step)
        assertEquals(EditorFixtures.athleticism.id, line.entryId)
    }

    @Test
    fun removingPrerequisitePerkKeepsDependentAndFlagsIt() {
        val host = host()
        host.setDevelopmentRank(EditorFixtures.access.id, 0)
        assertEquals(1, host.active.development.getValue(EditorFixtures.branch.id).rank)
        val line = editorProblems(host).single { it.entryId == EditorFixtures.branch.id }
        assertEquals("Разметать: требования больше не выполнены", line.text)
    }

    @Test
    fun newCharacterNameFieldStartsEmpty() {
        assertEquals("", editorNameFieldInitial(DublCharacter(id = "n", name = "Новый персонаж")))
        assertEquals("Шершень", editorNameFieldInitial(hornet))
        assertEquals("Новый персонаж", editorNameFieldInitial(DublCharacter(id = "n", name = "Новый персонаж", creationComplete = true)))
    }

    @Test
    fun spentBarIsEmptyWithNoExperience() {
        assertEquals(0f, editorSpentFraction(spent = 0, total = 0))
        assertEquals(1f, editorSpentFraction(spent = 55, total = 0))
        assertEquals(0.5f, editorSpentFraction(spent = 50, total = 100))
        assertEquals(1f, editorSpentFraction(spent = 150, total = 100))
    }

    @Test
    fun skillGroupsSplitIntoBalancedColumnsKeepingOrder() {
        // СИЛ 1, ЛОВ 8, ТЕЛ 0, СКР 0, ИНТ 3, ВОС 5, ВОЛ 0, ХАР 2 rows; an empty group still takes a line.
        val columns = editorBalancedColumns(listOf(1, 8, 0, 0, 3, 5, 0, 2), columns = 2)
        assertEquals(listOf(listOf(0, 2, 3, 4, 6, 7), listOf(1, 5)), columns)
        assertEquals(listOf(listOf(0, 1, 2)), editorBalancedColumns(listOf(4, 1, 2), columns = 1))
    }

    @Test
    fun skillOrderStaysPutWhileRanksChange() {
        // Clicking + twice on the same row must hit the same skill: rows never jump during a visit.
        val host = host()
        val before = host.active
        fun order() = editorSkillGroups(host, before, rankedOnly = false, query = "").flatMap { g -> g.rows.map { it.skillId } }
        val initial = order()
        // ВОС group: Внимательность 3, Выживание 2 ranked; lift an unranked ВОС skill above both.
        val group = editorSkillGroups(host, before, rankedOnly = false, query = "").first { it.short == "ВОС" }
        val unranked = group.rows.last { it.rank == 0 }.skillId
        repeat(4) { host.changeSkillRank(unranked, +1) }
        assertEquals(4, host.active.skills.getValue(unranked).rank)
        assertEquals(initial, order())
    }

    @Test
    fun developmentListsStayPutWhileYouClick() {
        // Rows must not reorder or vanish under the cursor: the first view of a visit pins membership and order.
        val single = EditorFixtures.entry("feat_once", "Разовый", "Общие", 10, 1, "", "Один ранг.")
        val host = FakeSheetHost(hornet, developmentCatalogOverride = com.furybook.dubl.model.DevelopmentCatalog("test", catalog.entries + single))
        val first = editorDevelopmentView(host)
        val pins = first.pins
        assertTrue(single.id in first.perks.map { it.entryId })
        val lowest = first.skills.last()
        repeat(3) { host.changeSkillRank(lowest.skillId, +1) }
        val take = editorPerkDetail(host, single.id, emptyMap(), null)!!.action as EditorPerkAction.Acquire
        assertTrue(host.acquireDevelopment(take.request).applied)
        val again = editorDevelopmentView(host, pins)
        assertEquals(first.skills.map { it.skillId }, again.skills.map { it.skillId })
        assertEquals(first.attributes.map { it.id }, again.attributes.map { it.id })
        assertEquals(first.perks.map { it.entryId }, again.perks.map { it.entryId })
        assertEquals(lowest.from + 3, again.skills.last().from)
        assertEquals(EditorPerkStatus.OWNED, again.perks.single { it.entryId == single.id }.status)
    }

    @Test
    fun developmentSubtitleNeverShowsNegativePoints() {
        assertEquals("Свободно 2450 XP и 3 ОС. Что можно взять прямо сейчас, одним нажатием.", editorDevelopmentSubtitle(2450, 3))
        assertEquals("Свободно 3735 XP; очков способностей потрачено на 5 больше бюджета. Что можно взять прямо сейчас, одним нажатием.", editorDevelopmentSubtitle(3735, -5))
        assertEquals("Перерасход опыта: 40 XP. Верните часть опыта или добавьте XP на «Основе».", editorDevelopmentSubtitle(-40, 0))
    }
}
