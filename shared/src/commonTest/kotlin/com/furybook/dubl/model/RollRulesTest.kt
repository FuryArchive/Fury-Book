package com.furybook.dubl.model

import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.Test

class RollRulesTest {
    private fun dice(vararg values: Int): () -> Int {
        val iterator = values.iterator()
        return { iterator.nextInt() }
    }

    @Test
    fun normalDoubleGrantsAdvantageDieAndRecalculatesUsingTwoHighest() {
        val initial = rollCheck(
            mode = RollMode.NORMAL,
            effectCount = 0,
            checkBonus = 3,
            checkBonusLabel = "Бонус",
            situationalBonus = 0,
            rollDie = dice(4, 4),
        )
        assertEquals(RollFollowUp.ADVANTAGE_DIE, initial.followUp)

        val resolved = rollFollowUp(initial, dice(6))
        assertEquals(listOf(4, 4, 6), resolved.dice)
        assertEquals(setOf(0, 2), resolved.chosenIndices)
        assertEquals(13, resolved.total)
        assertNull(resolved.followUp)
    }

    @Test
    fun criticalFailureStaysFailedAndCanBeConfirmed() {
        val initial = rollCheck(
            mode = RollMode.NORMAL,
            effectCount = 0,
            checkBonus = 5,
            checkBonusLabel = "Бонус",
            situationalBonus = 0,
            rollDie = dice(1, 1),
        )
        assertEquals(RollSpecialResult.CRITICAL_FAILURE, initial.specialResult)
        assertEquals(RollFollowUp.CRITICAL_FAILURE_CONFIRMATION, initial.followUp)
        assertEquals(7, initial.total)

        val confirmed = rollFollowUp(initial, dice(1))
        assertEquals(RollSpecialResult.CONFIRMED_CRITICAL_FAILURE, confirmed.specialResult)
        assertEquals(7, confirmed.total)
        assertEquals(1, confirmed.followUpDie)
        assertNull(confirmed.followUp)
    }

    @Test
    fun criticalFailureConfirmationDoesNotChangeNumericTotalWhenNotConfirmed() {
        val initial = rollCheck(
            mode = RollMode.NORMAL,
            effectCount = 0,
            checkBonus = 2,
            checkBonusLabel = "Бонус",
            situationalBonus = -1,
            rollDie = dice(1, 1),
        )
        val resolved = rollFollowUp(initial, dice(5))
        assertEquals(RollSpecialResult.CRITICAL_FAILURE, resolved.specialResult)
        assertEquals(initial.total, resolved.total)
        assertEquals(5, resolved.followUpDie)
    }

    @Test
    fun superiorityDieAddsToCriticalSuccessAndThirdSixConfirmsIt() {
        val initial = rollCheck(
            mode = RollMode.NORMAL,
            effectCount = 0,
            checkBonus = 2,
            checkBonusLabel = "Бонус",
            situationalBonus = 1,
            rollDie = dice(6, 6),
        )
        assertEquals(RollSpecialResult.CRITICAL_SUCCESS, initial.specialResult)
        assertEquals(RollFollowUp.SUPERIORITY_DIE, initial.followUp)
        assertEquals(15, initial.total)

        val confirmed = rollFollowUp(initial, dice(6))
        assertEquals(RollSpecialResult.CONFIRMED_CRITICAL_SUCCESS, confirmed.specialResult)
        assertEquals(21, confirmed.total)
        assertEquals(6, confirmed.followUpDie)
        assertNull(confirmed.followUp)
    }

    @Test
    fun hindranceDoubleAdvantageDieNeverLowersResult() {
        val initial = rollCheck(
            mode = RollMode.HINDRANCE,
            effectCount = 1,
            checkBonus = 0,
            checkBonusLabel = "Бонус",
            situationalBonus = 0,
            rollDie = dice(2, 2, 6),
        )
        assertEquals(RollFollowUp.ADVANTAGE_DIE, initial.followUp)

        val low = rollFollowUp(initial, dice(1))
        assertEquals(setOf(0, 1), low.chosenIndices)
        assertEquals(4, low.total)
    }

    @Test
    fun hindranceDoubleAdvantageDieImprovesKeptPair() {
        val initial = rollCheck(
            mode = RollMode.HINDRANCE,
            effectCount = 1,
            checkBonus = 0,
            checkBonusLabel = "Бонус",
            situationalBonus = 0,
            rollDie = dice(3, 3, 5),
        )
        val resolved = rollFollowUp(initial, dice(6))
        assertEquals(setOf(0, 3), resolved.chosenIndices)
        assertEquals(9, resolved.total)
    }
    @Test
    fun comparesRollAgainstOptionalTargetWithoutChangingRollTotal() {
        val success = compareRollToTarget(total = 14, target = 11)
        assertEquals(3, success.margin)
        assertEquals(RollTargetOutcome.SUCCESS, success.outcome)

        val tie = compareRollToTarget(total = 11, target = 11)
        assertEquals(0, tie.margin)
        assertEquals(RollTargetOutcome.TIE, tie.outcome)

        val failure = compareRollToTarget(total = 8, target = 11)
        assertEquals(-3, failure.margin)
        assertEquals(RollTargetOutcome.FAILURE, failure.outcome)
    }

    @Test
    fun criticalFailureCannotBeReportedAsTargetSuccessEvenWithLargeBonus() {
        val roll = rollCheck(
            mode = RollMode.NORMAL,
            effectCount = 0,
            checkBonus = 20,
            checkBonusLabel = "Бонус",
            situationalBonus = 0,
            rollDie = dice(1, 1),
        )

        val comparison = compareRollToTarget(roll, target = 10)

        assertEquals(12, comparison.margin)
        assertEquals(RollTargetOutcome.FAILURE, comparison.outcome)
    }

    private fun attack(bonus: Int, vararg rolled: Int) = rollCheck(
        mode = RollMode.NORMAL,
        effectCount = 0,
        checkBonus = bonus,
        checkBonusLabel = "Атака",
        situationalBonus = 0,
        rollDie = dice(*rolled),
    )

    @Test
    fun attackBelowEqualAndAboveDefenseMissPartialAndFullHit() {
        // Rulebook example: Defense 6, attack +3; rolling 2 and 1 totals 6 → partial hit.
        assertEquals(AttackOutcome.PARTIAL_HIT, resolveAttack(attack(3, 2, 1), 6).outcome)
        assertEquals(AttackOutcome.FULL_HIT, resolveAttack(attack(3, 3, 1), 6).outcome)
        assertEquals(AttackOutcome.MISS, resolveAttack(attack(3, 1, 2), 7).outcome)
    }

    @Test
    fun doubleSixThatHitsIsCriticalWithoutSuperiorityDie() {
        val roll = attack(0, 6, 6)
        assertEquals(AttackOutcome.CRITICAL_HIT, resolveAttack(roll, 12).outcome)
        assertNull(roll.availableFollowUp(attackDefense = 12))
        assertEquals(RollFollowUp.SUPERIORITY_DIE, roll.availableFollowUp())
    }

    @Test
    fun doubleSixThatMissesEarnsSuperiorityDieAndIsNotCritical() {
        val roll = attack(0, 6, 6)
        assertEquals(AttackOutcome.MISS, resolveAttack(roll, 15).outcome)
        assertEquals(RollFollowUp.SUPERIORITY_DIE, roll.availableFollowUp(attackDefense = 15))

        val withDie = rollFollowUp(roll, dice(4))
        assertEquals(16, withDie.total)
        assertEquals(AttackOutcome.FULL_HIT, resolveAttack(withDie, 15).outcome)
        assertEquals(AttackOutcome.MISS, resolveAttack(rollFollowUp(roll, dice(2)), 15).outcome)
    }

    @Test
    fun doubleOneAttackIsCriticalMissEvenAboveDefense() {
        assertEquals(AttackOutcome.CRITICAL_MISS, resolveAttack(attack(20, 1, 1), 6).outcome)
    }

    @Test
    fun flurryScoresOneHitPerRequiredExcessUpToWeaponMaximum() {
        val light = FlurryWeaponProfile.LIGHT.rule(short = false)
        assertEquals(0, light.resolve(attack(0, 4, 5), 9).hits)
        assertEquals(0, light.resolve(attack(0, 4, 6), 9).hits)
        assertEquals(1, light.resolve(attack(0, 5, 6), 9).hits)
        assertEquals(2, light.resolve(attack(2, 5, 6), 9).hits)
        assertEquals(3, light.resolve(attack(20, 5, 6), 9).hits)

        val twoWeapons = FlurryWeaponProfile.TWO_WEAPONS_LIGHT.rule(short = false)
        assertEquals(6, twoWeapons.resolve(attack(20, 5, 6), 9).hits)
    }

    @Test
    fun shortFlurryNeedsOneMoreExcessPerHit() {
        val shortTwoHanded = FlurryWeaponProfile.TWO_HANDED.rule(short = true)
        val resolution = shortTwoHanded.resolve(attack(10, 4, 5), 9)
        assertEquals(5, resolution.excessPerHit)
        assertEquals(2, resolution.hits)
    }

    @Test
    fun flurryCriticalFailureScoresNoHits() {
        assertEquals(0, FlurryWeaponProfile.LIGHT.rule(short = false).resolve(attack(30, 1, 1), 6).hits)
    }
}
