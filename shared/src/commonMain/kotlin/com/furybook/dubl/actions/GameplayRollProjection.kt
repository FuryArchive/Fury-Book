package com.furybook.dubl.actions

import com.furybook.dubl.acquisition.StoredCharacter
import com.furybook.dubl.model.*

/** Calculation aid only. The player submits the same typed success/failure as with physical dice. */
data class GameplayRollProjection(val startOperationId: String, val title: String, val bonus: Int?,
    val contributions: List<RollContribution>, val advantageDice: Int, val hindranceDice: Int,
    val notices: List<String>, val automaticDiceBlockedReason: String?) {
    fun roll(rollDie: () -> Int): RollResult {
        check(automaticDiceBlockedReason == null) { automaticDiceBlockedReason.orEmpty() }
        return rollCheck(RollMode.ADVANTAGE, advantageDice, checkNotNull(bonus), title, 0, rollDie)
    }
}

fun pendingGameplayRoll(state: StoredCharacter): GameplayRollProjection? {
    val pending = state.gameplay?.pending ?: return null
    val attribute = when (pending.attribute) {
        PhysicalAttribute.STRENGTH -> AttributeId.STRENGTH
        PhysicalAttribute.DEXTERITY -> AttributeId.DEXTERITY
        PhysicalAttribute.SPEED -> AttributeId.SPEED
        PhysicalAttribute.CONSTITUTION -> AttributeId.CONSTITUTION
    }
    val base = state.character.rollPreset(RollContext.ATTRIBUTE, attribute = attribute)
    val conditions = state.character.conditionRollModifiers(RollContext.ATTRIBUTE, attribute, state.extras.activeConditions)
    val blocked = when {
        pending.ruleVersion != GameplayActions.OVEREXERTION_VERSION -> "Версия правила не поддерживается"
        !base.available -> base.unavailableReason
        conditions.unavailableReason != null -> conditions.unavailableReason
        conditions.hindranceDice > 0 -> "Преимущество и негативные факторы требуют явного решения; автоматическое взаимопогашение не применяется"
        conditions.unimplemented.isNotEmpty() -> "Есть неавтоматизированные последствия состояний; используйте физическую проверку"
        else -> null
    }
    return GameplayRollProjection(pending.startOperationId, base.title, conditions.apply(base.bonus),
        base.contributions + conditions.contributions, pending.advantageDice, conditions.hindranceDice,
        listOf(conditions.notice).filter { it.isNotBlank() }, blocked)
}
