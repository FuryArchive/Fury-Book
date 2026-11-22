package com.furybook.dubl.ui.sheet

import com.furybook.dubl.model.DublCharacter

enum class SheetStatId { DEFENSE, REFLEXES, INITIATIVE, FORTITUDE, RUN, SIZE }

/** Formula popup content; a port of the Android sheet's statInfo(), reading the shared bonus properties. */
data class SheetStatBreakdown(
    val title: String,
    val value: String,
    val formula: String,
    val lines: List<String>,
    val note: String? = null,
)

fun sheetStatBreakdown(character: DublCharacter, stat: SheetStatId): SheetStatBreakdown = with(character) {
    when (stat) {
        SheetStatId.DEFENSE -> SheetStatBreakdown(
            "Защита", defense.toString(), "10 − Размер + Скорость + Ловкость + штраф нагрузки",
            listOf("10 − $size + $speed + $dexterity", "Штраф нагрузки: ${sgn(equipmentLoadPenalty)}", "Итог: $defense"),
        )
        SheetStatId.REFLEXES -> SheetStatBreakdown(
            "Рефлексы", sgn(reflexes), "Скорость + Ловкость + нагрузка + Быстрые рефлексы",
            listOf(
                "$speed + $dexterity", "Штраф нагрузки: ${sgn(equipmentLoadPenalty)}",
                "Быстрые рефлексы: ${sgn(quickReflexesBonus)}", "Итог: ${sgn(reflexes)}",
            ),
        )
        SheetStatId.INITIATIVE -> SheetStatBreakdown(
            "Инициатива", sgn(initiative), "Скорость + Восприятие + Улучшенная инициатива + Повелитель Бури",
            listOf(
                "$speed + $perception", "Улучшенная инициатива: ${sgn(improvedInitiativeBonus)}",
                "Повелитель Бури: ${sgn(stormLordBonus)}", "Итог: ${sgn(initiative)}",
            ),
        )
        SheetStatId.FORTITUDE -> SheetStatBreakdown(
            "Стойкость", sgn(fortitude), "Телосложение + Воля + Стойкий + Неподвижная Гора",
            listOf(
                "$constitution + $will", "Стойкий: ${sgn(stalwartBonus)}",
                "Неподвижная Гора: ${sgn(stillMountainBonus)}", "Итог: ${sgn(fortitude)}",
            ),
        )
        SheetStatId.RUN -> SheetStatBreakdown(
            "Бег", "${formatSheetNumber(runFull)} м", "Базовый бег + (Скорость + Повелитель Бури) × множитель + нагрузка + Бегун",
            listOf(
                "Базовый бег: ${formatSheetNumber(runBase)} м", "Скорость: $speed",
                "Повелитель Бури: ${sgn(runStormSpeedBonus)}", "Множитель: ${formatSheetNumber(runMultiplier)}",
                "Штраф нагрузки: ${sgn(equipmentLoadPenalty)}", "Бегун: ${sgn(runRunnerBonus)}",
                "Итог: ${formatSheetNumber(runFull)} м",
            ),
            note = "Множитель зависит от Размера ($size) и количества ног ($legs).",
        )
        SheetStatId.SIZE -> SheetStatBreakdown(
            "Размер", size.toString(), "Задаётся напрямую",
            listOf(
                "Размер: $size", "Модификатор Силы: ${sgn(strengthSizeModifier)}",
                "Модификатор Скорости: ${sgn(speedSizeModifier)}",
            ),
            note = "Размер влияет на Силу, Скорость, Защиту, здоровье и Бег.",
        )
    }
}

private fun sgn(value: Int): String = if (value >= 0) "+$value" else value.toString()
