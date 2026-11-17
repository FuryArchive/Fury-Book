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
}
