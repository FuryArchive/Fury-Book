package com.furybook.dubl.ui.sheet

import kotlin.test.Test
import kotlin.test.assertEquals

// Benefit texts copied from the DUBL 3.69 catalogs.
class SheetPerkCostsTest {
    @Test
    fun manaShieldCostsOneAction() =
        assertEquals(listOf("1 ОД"), perkCostTags("x", "За 1 ОД Вы можете активировать щит маны, использующий Вашу ману как барьер."))

    @Test
    fun healingTouchCostsManaAndActions() =
        assertEquals(listOf("1 маны", "2 ОД"), perkCostTags("x", "За 1 маны и 2 ОД Вы можете касанием исцелить 1d3 здоровья живому существу."))

    @Test
    fun perfectThrowCostsEndurance() = assertEquals(
        listOf("1 вын."),
        perkCostTags("x", "Затрачивая 1 пункт выносливости Вы игнорируете любые штрафы и помехи на следующую атаку метательным оружием и уменьшаете разброс до 1 метра."),
    )

    @Test
    fun unbreakableSpiritCostsThreeActions() = assertEquals(
        listOf("3 ОД"),
        perkCostTags("x", "За 3 ОД Вы издаёте рев, наполняющий союзников невероятной стойкостью. Все дружественные существа в радиусе 8 метров в течении 3 раундов получают следующие моральные бонусы::\n+2 стойкость и броня."),
    )

    @Test
    fun aDiscountIsNotACost() =
        assertEquals(emptyList(), perkCostTags("x", "Разоружение и финт не провоцирует атаки, а разоружение занимает на 1 ОД меньше."))

    @Test
    fun spendingAReactionIsACost() = assertEquals(
        listOf("реакция"),
        perkCostTags("x", "Затрачивая реакцию или отложенное защитное действие Вы не провоцируете атаки при сотворении заклинаний."),
    )

    @Test
    fun passiveBonusHasNoCost() =
        assertEquals(emptyList(), perkCostTags("x", "+1 за ранг на проверки красноречия при попытке обмануть."))

    @Test
    fun emptyBenefitHasNoCost() = assertEquals(emptyList(), perkCostTags("x", ""))

    // Review findings: wording that used to be misread, from the real catalog.
    @Test
    fun extraTimeIsNotACost() =
        assertEquals(emptyList(), perkCostTags("x", "Удар разворота оглушает противника, даже если Вы не нанесли достаточно урона, но занимает на 1 ОД больше."))

    @Test
    fun anAttackLimitIsNotACost() = assertEquals(
        emptyList(),
        perkCostTags("x", "Если атака противника должна Вас вырубить или убить, Вы можете провести любую атаку оружием, занимающую не более 1 ОД и только после этого теряете сознание или умираете."),
    )

    @Test
    fun quickCastingCostsTheReactionNotTheSavedAction() = assertEquals(
        listOf("реакция"),
        perkCostTags("x", "Вы можете тратить действие реакции чтобы уменьшить время произношения заклинания на 1 ОД."),
    )

    @Test
    fun whatTheTargetPaysIsNotOurCost() {
        assertEquals(emptyList(), perkCostTags("x", "Когда кто-то во время боя оказывается в 10 метрах от Вас, то терпит штраф -1 на все проверки. Цель может потратить 2 ОД и пройти проверку воля+интеллект против запугивание+харизма."))
        assertEquals(emptyList(), perkCostTags("x", "Каждая успешная рукопашная атака уменьшает его защиту.\nЦель может потратить 1 пункт выносливости (сверхусилие) или потратить 2 ОД чтобы отдышаться."))
    }

    @Test
    fun passiveThresholdsAreNotCosts() {
        assertEquals(emptyList(), perkCostTags("x", "Ваша защита против стрелковых атак вырастает на 1. Когда Вы тратите 2 ОД и больше на передвижение этот бонус вырастает ещё на 1."))
        assertEquals(emptyList(), perkCostTags("x", "Когда Ваше здоровье опускается ниже 0 Вы можете начать борьбу за жизнь. Пока активна борьба за жизнь:\nУ вас 2 ОД."))
    }

    @Test
    fun otherReactionWordingsAreCosts() {
        assertEquals(listOf("реакция"), perkCostTags("x", "Успешная атака нунчаками позволяет провести ещё одну атаку за реакцию."))
        assertEquals(listOf("реакция"), perkCostTags("x", "Вы получаете +2 к защите против одной атаки. Можно использовать как реакцию."))
        assertEquals(listOf("реакция"), perkCostTags("x", "Вы можете проводить осмотр обстановки вокруг и проверки внимательности действием реакции."))
        assertEquals(listOf("реакция"), perkCostTags("x", "Когда противник попадает по Вашему двойнику, Вы можете реакцией заставить его атаковать другую цель."))
    }
}
