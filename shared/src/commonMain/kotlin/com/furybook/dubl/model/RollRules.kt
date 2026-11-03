package com.furybook.dubl.model

import kotlin.random.Random

enum class RollMode(val title: String) {
    NORMAL("Обычный"),
    ADVANTAGE("Преимущество"),
    HINDRANCE("Помеха"),
}

enum class RollFollowUp(val buttonTitle: String) {
    ADVANTAGE_DIE("Бросить кость преимущества"),
    CRITICAL_FAILURE_CONFIRMATION("Бросить кость подтверждения"),
    SUPERIORITY_DIE("Бросить кость превосходства"),
}

enum class RollSpecialResult(val title: String) {
    CRITICAL_FAILURE("Критический провал"),
    CONFIRMED_CRITICAL_FAILURE("Подтверждённый критический провал"),
    CRITICAL_SUCCESS("Критический успех"),
    CONFIRMED_CRITICAL_SUCCESS("Подтверждённый критический успех"),
}

data class RollResult(
    val mode: RollMode,
    val effectCount: Int,
    val dice: List<Int>,
    val chosenIndices: Set<Int>,
    val checkBonus: Int,
    val checkBonusLabel: String,
    val situationalBonus: Int,
    val total: Int,
    val note: String? = null,
    val followUp: RollFollowUp? = null,
    val followUpDie: Int? = null,
    val resolvedFollowUp: RollFollowUp? = null,
    val specialResult: RollSpecialResult? = null,
    val grantedAdvantageDie: Boolean = false,
)

fun rollCheck(
    mode: RollMode,
    effectCount: Int,
    checkBonus: Int,
    checkBonusLabel: String,
    situationalBonus: Int,
    rollDie: () -> Int = ::rollD6,
): RollResult {
    val extraDice = effectCount.coerceAtLeast(0)
    val dice = List(2 + extraDice) { rollDie().coerceIn(1, 6) }
    val chosenIndices = chooseDiceIndices(mode, dice)
    val chosenValues = chosenIndices.map { dice[it] }
    val pair = chosenValues.sorted()
    val hasExtraDice = dice.size > 2
    val ambiguitySuffix = if (hasExtraDice) {
        " При дополнительных костях книга не закрепляет, определяется ли дубль до или после выбора двух костей."
    } else {
        ""
    }
    val (followUp, note) = when {
        pair == listOf(1, 1) -> RollFollowUp.CRITICAL_FAILURE_CONFIRMATION to
            "Критический провал. Дополнительная кость определяет, будет ли он подтверждён.$ambiguitySuffix"
        pair == listOf(6, 6) -> RollFollowUp.SUPERIORITY_DIE to
            "Критический успех. Кость превосходства бросается дополнительно и прибавляется к результату.$ambiguitySuffix"
        pair.size == 2 && pair[0] == pair[1] -> RollFollowUp.ADVANTAGE_DIE to
            "Дубль ${pair[0]}–${pair[1]}. По правилам доступна дополнительная кость преимущества.$ambiguitySuffix"
        else -> null to null
    }
    val specialResult = when (pair) {
        listOf(1, 1) -> RollSpecialResult.CRITICAL_FAILURE
        listOf(6, 6) -> RollSpecialResult.CRITICAL_SUCCESS
        else -> null
    }
    return RollResult(
        mode = mode,
        effectCount = extraDice,
        dice = dice,
        chosenIndices = chosenIndices,
        checkBonus = checkBonus,
        checkBonusLabel = checkBonusLabel,
        situationalBonus = situationalBonus,
        total = chosenValues.sum() + checkBonus + situationalBonus,
        note = note,
        followUp = followUp,
        specialResult = specialResult,
    )
}

fun rollFollowUp(
    roll: RollResult,
    rollDie: () -> Int = ::rollD6,
): RollResult {
    val action = roll.followUp ?: return roll
    val die = rollDie().coerceIn(1, 6)
    return when (action) {
        RollFollowUp.ADVANTAGE_DIE -> {
            val newDice = roll.dice + die
            // The double's advantage die competes with the two dice already kept,
            // so it can only improve the result, even under Hindrance.
            val newChosen = (roll.chosenIndices + newDice.lastIndex)
                .sortedByDescending { newDice[it] }
                .take(2)
                .toSet()
            val newTotal = newChosen.sumOf { newDice[it] } + roll.checkBonus + roll.situationalBonus
            roll.copy(
                dice = newDice,
                chosenIndices = newChosen,
                total = newTotal,
                note = "Кость преимущества за дубль: $die. Результат пересчитан по правилам текущего броска.",
                followUp = null,
                followUpDie = die,
                resolvedFollowUp = action,
                grantedAdvantageDie = true,
            )
        }

        RollFollowUp.CRITICAL_FAILURE_CONFIRMATION -> roll.copy(
            note = if (die == 1) {
                "Выпала ещё одна 1 — критический провал подтверждён."
            } else {
                "Дополнительная кость показала $die. Критический провал остаётся, но не подтверждается."
            },
            followUp = null,
            followUpDie = die,
            resolvedFollowUp = action,
            specialResult = if (die == 1) {
                RollSpecialResult.CONFIRMED_CRITICAL_FAILURE
            } else {
                RollSpecialResult.CRITICAL_FAILURE
            },
        )

        RollFollowUp.SUPERIORITY_DIE -> roll.copy(
            total = roll.total + die,
            note = if (die == 6) {
                "Кость превосходства показала 6 — критический успех подтверждён."
            } else {
                "Кость превосходства показала $die и добавлена к итоговому результату."
            },
            followUp = null,
            followUpDie = die,
            resolvedFollowUp = action,
            specialResult = if (die == 6) {
                RollSpecialResult.CONFIRMED_CRITICAL_SUCCESS
            } else {
                RollSpecialResult.CRITICAL_SUCCESS
            },
        )
    }
}

fun chooseDiceIndices(
    mode: RollMode,
    dice: List<Int>,
): Set<Int> = when {
    mode == RollMode.HINDRANCE -> dice.indices.sortedBy { dice[it] }.take(2).toSet()
    mode == RollMode.ADVANTAGE ->
        dice.indices.sortedByDescending { dice[it] }.take(2).toSet()
    else -> dice.indices.take(2).toSet()
}

private fun rollD6(): Int = Random.nextInt(1, 7)

enum class RollTargetOutcome {
    SUCCESS,
    TIE,
    FAILURE,
}

data class RollTargetComparison(
    val target: Int,
    val margin: Int,
    val outcome: RollTargetOutcome,
)

fun compareRollToTarget(total: Int, target: Int): RollTargetComparison {
    val margin = total - target
    return RollTargetComparison(
        target = target,
        margin = margin,
        outcome = when {
            margin > 0 -> RollTargetOutcome.SUCCESS
            margin < 0 -> RollTargetOutcome.FAILURE
            else -> RollTargetOutcome.TIE
        },
    )
}


fun compareRollToTarget(roll: RollResult, target: Int): RollTargetComparison {
    val comparison = compareRollToTarget(total = roll.total, target = target)
    return if (roll.specialResult == RollSpecialResult.CRITICAL_FAILURE ||
        roll.specialResult == RollSpecialResult.CONFIRMED_CRITICAL_FAILURE
    ) {
        comparison.copy(outcome = RollTargetOutcome.FAILURE)
    } else {
        comparison
    }
}

enum class AttackOutcome(val title: String) {
    CRITICAL_MISS("Критический провал · промах с негативными последствиями"),
    MISS("Промах · урона нет"),
    PARTIAL_HIT("Частичное попадание · ½ урона (с округлением вверх)"),
    FULL_HIT("Полное попадание · полный урон"),
    CRITICAL_HIT("Критическое попадание · основной урон ×1,5 и/или последствие"),
}

data class AttackResolution(
    val defense: Int,
    val margin: Int,
    val outcome: AttackOutcome,
)

private val RollResult.naturalDoubleSix: Boolean
    get() = specialResult == RollSpecialResult.CRITICAL_SUCCESS ||
        specialResult == RollSpecialResult.CONFIRMED_CRITICAL_SUCCESS

private val RollResult.totalBeforeSuperiorityDie: Int
    get() = total - if (resolvedFollowUp == RollFollowUp.SUPERIORITY_DIE) followUpDie ?: 0 else 0

/**
 * Core 3.69 "Проверки атаки": below Defense misses, equal is a partial hit, above
 * is a full hit. A natural 6-6 is critical only when it is enough to hit; a 6-6
 * that falls short earns the superiority die instead and then resolves normally.
 */
fun resolveAttack(roll: RollResult, defense: Int): AttackResolution {
    val margin = roll.total - defense
    val outcome = when {
        roll.specialResult == RollSpecialResult.CRITICAL_FAILURE ||
            roll.specialResult == RollSpecialResult.CONFIRMED_CRITICAL_FAILURE -> AttackOutcome.CRITICAL_MISS
        roll.naturalDoubleSix && roll.totalBeforeSuperiorityDie >= defense -> AttackOutcome.CRITICAL_HIT
        margin < 0 -> AttackOutcome.MISS
        margin == 0 -> AttackOutcome.PARTIAL_HIT
        else -> AttackOutcome.FULL_HIT
    }
    return AttackResolution(defense = defense, margin = margin, outcome = outcome)
}

/**
 * Follow-up the player may still roll. For an attack against a known Defense the
 * superiority die is withheld when the natural 6-6 already hits (it is a critical hit).
 */
fun RollResult.availableFollowUp(attackDefense: Int? = null): RollFollowUp? {
    val action = followUp ?: return null
    if (action == RollFollowUp.SUPERIORITY_DIE && attackDefense != null && total >= attackDefense) return null
    return action
}
