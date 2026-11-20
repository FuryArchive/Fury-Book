package com.furybook.dubl.ui.sheet

import com.furybook.dubl.model.AbilityOption
import com.furybook.dubl.model.AttributeId
import com.furybook.dubl.model.AttributeValue
import com.furybook.dubl.model.CharacterConditionId
import com.furybook.dubl.model.CharacterNote
import com.furybook.dubl.model.CharacterSheetExtras
import com.furybook.dubl.model.CharacterSkill
import com.furybook.dubl.model.CustomCondition
import com.furybook.dubl.model.DevelopmentCatalog
import com.furybook.dubl.model.DevelopmentCostType
import com.furybook.dubl.model.DevelopmentEntry
import com.furybook.dubl.model.DublCharacter
import com.furybook.dubl.model.OwnedDevelopment
import com.furybook.dubl.model.RollContext
import com.furybook.dubl.model.SheetGroup
import com.furybook.dubl.model.SkillEffectCatalog
import com.furybook.dubl.model.SkillEffectDefinition
import com.furybook.dubl.model.SkillEffectMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SheetPlayModelsTest {
    private fun entry(id: String, name: String, category: String, benefit: String, maxRank: Int = 1, accessId: String? = null, requirements: String = "") =
        DevelopmentEntry(
            id = id, name = name, section = "Ветки способностей", category = category, cost = 10, costType = DevelopmentCostType.XP,
            maxRank = maxRank, requirements = requirements, benefit = benefit, notes = "", tags = emptyList(), accessId = accessId,
            abilityOptions = emptyList(), incomplete = false, repeatable = false, perfectRoot = false, mechanicsConflict = "", conflictNote = "",
        )

    private val athleticism = entry("feat_ath", "Атлетичность", "Атлетика", "За каждый ранг Вы получаете +1 на проверки атлетики.", maxRank = 2,
        requirements = "Сила 4")
    private val shield = entry("feat_shield", "Щит маны", "Магические навыки", "За 1 ОД Вы можете активировать щит маны, использующий Вашу ману как барьер.")
    private val access = entry("access_van", "Авангард", "Особые способности", "", ).copy(
        section = "Особые способности", costType = DevelopmentCostType.ABILITY, abilityOptions = listOf(AbilityOption("Мастерство", 1)),
    )
    private val branch = entry("feat_van", "Разметать", "Авангард", "Шквал атак приёмом Рассечение.", accessId = "access_van")
    private val catalog = DevelopmentCatalog("test", listOf(athleticism, shield, access, branch))

    private val effects = SkillEffectCatalog(
        "1",
        listOf(
            SkillEffectDefinition(
                id = "e1", reviewIndex = 1, sourceName = "Атлетичность", effectText = "", targetText = "", status = "", plan = "",
                mode = SkillEffectMode.AUTO_BONUS, rollContext = RollContext.SKILL, targetSkills = listOf("Атлетика"),
                value = 1, perRank = true, toggleLabel = "", developmentId = "feat_ath",
            ),
        ),
    )

    private val hornet = DublCharacter(
        id = "c1", name = "Шершень", hpCurrent = 20, enduranceCurrent = 3,
        attributes = AttributeId.entries.associateWith { AttributeValue() } +
            (AttributeId.DEXTERITY to AttributeValue(base = 8)) + (AttributeId.STRENGTH to AttributeValue(base = 2)),
        skills = mapOf(
            "melee_weapon" to CharacterSkill(id = "melee_weapon", definitionId = "melee_weapon", rank = 7),
            "stealth" to CharacterSkill(id = "stealth", definitionId = "stealth", rank = 5),
            "athletics" to CharacterSkill(id = "athletics", definitionId = "athletics", rank = 4),
            "driving" to CharacterSkill(id = "driving", definitionId = "driving", rank = 0),
        ),
        development = mapOf(
            athleticism.id to OwnedDevelopment(rank = 1), shield.id to OwnedDevelopment(rank = 1),
            access.id to OwnedDevelopment(rank = 1), branch.id to OwnedDevelopment(rank = 1),
        ),
    )

    private fun host(extras: CharacterSheetExtras = CharacterSheetExtras()) =
        FakeSheetHost(hornet, extras, developmentCatalogOverride = catalog, skillEffectCatalogOverride = effects)

    @Test
    fun skillsByAttributeHasEightGroupsInOrder() {
        val groups = sheetSkillsByAttribute(host(), rankedOnly = true)
        assertEquals(AttributeId.entries, groups.map { it.attribute })
        assertEquals(listOf("СИЛ", "ЛОВ", "ТЕЛ", "СКР", "ИНТ", "ВОС", "ВОЛ", "ХАР"), groups.map { it.label })
        assertEquals(8, groups.first { it.attribute == AttributeId.DEXTERITY }.value)
        assertTrue(groups.first { it.attribute == AttributeId.CONSTITUTION }.rows.isEmpty())
    }

    @Test
    fun rowsSortedByBonusDescending() {
        val dex = sheetSkillsByAttribute(host(), rankedOnly = true).first { it.attribute == AttributeId.DEXTERITY }
        assertEquals(listOf("melee_weapon", "stealth"), dex.rows.map { it.skillId })
    }

    @Test
    fun rankedOnlyDropsRankZero() {
        val ranked = sheetSkillsByAttribute(host(), rankedOnly = true).flatMap { it.rows }.map { it.skillId }
        val all = sheetSkillsByAttribute(host(), rankedOnly = false).flatMap { it.rows }.map { it.skillId }
        assertTrue("driving" !in ranked)
        assertTrue("driving" in all)
        val (shown, total) = sheetSkillTotals(host())
        assertEquals(ranked.size, shown)
        assertEquals(all.size, total)
    }

    @Test
    fun athleticsCarriesPerkMarkAndTip() {
        val rows = sheetSkillsByAttribute(host(), rankedOnly = true).flatMap { it.rows }
        val athletics = rows.first { it.skillId == "athletics" }
        assertEquals("+1", athletics.perkMark)
        assertEquals("Атлетичность", athletics.perkTip)
        assertNull(rows.first { it.skillId == "melee_weapon" }.perkMark)
    }

    @Test
    fun breakdownText() {
        val melee = sheetSkillsByAttribute(host(), rankedOnly = true).flatMap { it.rows }.first { it.skillId == "melee_weapon" }
        assertEquals("Холодное оружие: 2d6 + 15 (Ловкость 8 + ранг 7)", melee.breakdown)
        val athletics = sheetSkillsByAttribute(host(), rankedOnly = true).flatMap { it.rows }.first { it.skillId == "athletics" }
        assertEquals("Атлетика: 2d6 + 7 (Сила 2 + ранг 4 + 1 навыки)", athletics.breakdown)
    }

    @Test
    fun perkCardsKeepCustomGroupOrder() {
        val custom = listOf(SheetGroup("mine", "Моё", listOf(shield.id, athleticism.id)))
        val groups = sheetPerkGroups(host(CharacterSheetExtras(developmentGroups = custom)))
        assertEquals("Моё", groups.first().title)
        assertEquals(listOf(shield.id, athleticism.id), groups.first().cards.map { it.entryId })
    }

    @Test
    fun perkCardsCarryBenefitAndTags() {
        val cards = sheetPerkGroups(host()).flatMap { it.cards }
        val card = cards.first { it.entryId == shield.id }
        assertEquals(shield.benefit, card.benefit)
        assertEquals(listOf("1 ОД"), card.costTags)
        assertEquals(1, card.rank)
        val ath = cards.first { it.entryId == athleticism.id }
        assertEquals(2, ath.maxRank)
        assertTrue(ath.requirementsUnmet)
        assertEquals("Сила 4", ath.requirements)
        assertTrue(cards.first { it.entryId == access.id }.isAccessRoot)
        assertEquals(1, cards.first { it.entryId == branch.id }.depth)
    }

    @Test
    fun activeFilterKeepsTaggedOnly() {
        val groups = sheetPerkGroups(host())
        assertEquals(listOf(shield.id), filterPerkGroups(groups, SheetPerkFilter.ACTIVE, "").flatMap { it.cards }.map { it.entryId })
        assertTrue(shield.id !in filterPerkGroups(groups, SheetPerkFilter.PASSIVE, "").flatMap { it.cards }.map { it.entryId })
    }

    @Test
    fun queryMatchesBenefitText() {
        val found = filterPerkGroups(sheetPerkGroups(host()), SheetPerkFilter.ALL, "  РАССЕЧЕНИЕ ").flatMap { it.cards }
        assertEquals(listOf(branch.id), found.map { it.entryId })
    }

    @Test
    fun emptyGroupsDropped() {
        val groups = filterPerkGroups(sheetPerkGroups(host()), SheetPerkFilter.ALL, "щит")
        assertTrue(groups.all { it.cards.isNotEmpty() })
        assertEquals(1, groups.size)
    }

    @Test
    fun conditionChipsCarryDescriptionAndKind() {
        val extras = CharacterSheetExtras(
            activeConditions = setOf(CharacterConditionId.DAZED, CharacterConditionId.INSPIRED),
            customConditions = listOf(CustomCondition("k1", "Отравлен", "−1 ко всем проверкам", active = true)),
        )
        val h = host(extras)
        val chips = sheetActiveConditions(h).associateBy { it.key }
        assertEquals(SheetConditionKind.HARMFUL, chips.getValue("DAZED").kind)
        assertEquals(h.conditionCatalog.summary(CharacterConditionId.DAZED), chips.getValue("DAZED").description)
        assertEquals(SheetConditionKind.HELPFUL, chips.getValue("INSPIRED").kind)
        assertEquals(SheetConditionKind.CUSTOM, chips.getValue("k1").kind)
        assertEquals("−1 ко всем проверкам", chips.getValue("k1").description)
    }

    @Test
    fun filterNotesMatchesBody() {
        val notes = listOf(CharacterNote("1", "Долги", "300 крон Гансу"), CharacterNote("2", "Контакты", "Мира в порту"))
        assertEquals(listOf("2"), filterNotes(notes, "порт").map { it.id })
        assertEquals(notes, filterNotes(notes, " "))
    }
}
