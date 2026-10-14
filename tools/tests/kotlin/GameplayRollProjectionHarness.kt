package com.furybook.dubl.actions

import com.furybook.dubl.acquisition.*
import com.furybook.dubl.model.*

fun main() {
    var count = 0
    fun expect(value: Boolean) { check(value); count++ }
    val character = DublCharacter("hero", attributes = mapOf(AttributeId.STRENGTH to AttributeValue(base = 4)))
    val base = StoredCharacter(character, CharacterSheetExtras(), emptyList(),
        Ledger(Opening("opening", "source", Amount.ZERO, RequirementNode.CreationPhase.Phase.Creating,
            OpeningProvenance.NewCharacter), emptyList()), emptyList(), emptyList(), SyncMetadata(0), 0, 0,
        RequirementNode.CreationPhase.Phase.Creating, formatVersion = 2, gameplay = GameplayJournal())
    expect(pendingGameplayRoll(base) == null)
    val transition = GameplayActions.start(GameplayState("hero", 0, 3), StartOverexertion("start", "hero", 0, PhysicalAttribute.STRENGTH)) as ActionTransition.Applied
    val saved = base.copy(character = character.copy(enduranceCurrent = 2), gameplay = GameplayJournal.from(transition.state, 1), storageRevision = 1)
    val projection = checkNotNull(pendingGameplayRoll(saved))
    expect(projection.bonus == character.rollPreset(RollContext.ATTRIBUTE, attribute = AttributeId.STRENGTH).bonus)
    expect(projection.advantageDice == 1 && projection.hindranceDice == 0 && projection.automaticDiceBlockedReason == null)
    val faces = listOf(2, 4, 5); var index = 0
    val result = projection.roll { faces[index++] }
    expect(result.dice == faces && result.total == 9 + checkNotNull(projection.bonus) && index == 3)
    expect(saved.character.enduranceCurrent == 2 && saved.gameplay!!.pending != null)
    val weak = pendingGameplayRoll(saved.copy(character = saved.character.copy(enduranceCurrent = 0)))!!
    expect(weak.hindranceDice == 2 && weak.advantageDice == 1 && weak.automaticDiceBlockedReason != null)
    expect(runCatching { weak.roll { error("Must not generate") } }.isFailure)
    val shock = pendingGameplayRoll(saved.copy(extras = saved.extras.copy(activeConditions = setOf(CharacterConditionId.SHOCK))))!!
    expect(shock.bonus == projection.bonus!! - 1 && shock.contributions.any { it.label == "Шок" && it.value == -1 })
    val poisoned = pendingGameplayRoll(saved.copy(extras = saved.extras.copy(activeConditions = setOf(CharacterConditionId.POISONED))))!!
    expect(poisoned.hindranceDice == 1 && poisoned.automaticDiceBlockedReason != null)
    println("GameplayRollProjectionHarness: $count assertions passed")
}
