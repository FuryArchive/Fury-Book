package com.furybook.dubl.model

/** Source P127 explicitly enumerates the physical attributes. */
private val physicalRollAttributes = setOf(AttributeId.STRENGTH, AttributeId.DEXTERITY, AttributeId.SPEED, AttributeId.CONSTITUTION)

fun DublCharacter.effectiveRollConditions(active: Set<CharacterConditionId>): Set<CharacterConditionId> =
    active + if (enduranceCurrent == 0) setOf(CharacterConditionId.WEAKNESS) else emptySet()

data class ConditionRollModifiers(
    val contributions: List<RollContribution> = emptyList(),
    val hindranceDice: Int = 0,
    val unavailableReason: String? = null,
    val unimplemented: Set<CharacterConditionId> = emptySet(),
) {
    val numericBonus: Int get() = contributions.sumOf { it.value }
    val notice: String get() = listOfNotNull(unavailableReason,
        if (hindranceDice > 0) "Слабость: $hindranceDice негативных фактора на физическую проверку" else null,
        if (unimplemented.isNotEmpty()) "Эффекты состояний пока не рассчитаны: ${unimplemented.joinToString { it.title }}" else null)
        .joinToString("\n")
    fun apply(base: Int?): Int? = if (unavailableReason != null) null else base?.plus(numericBonus)
}

/** Pure projection of already-known conditions. No resource expenditure, reset or duration inference. */
fun DublCharacter.conditionRollModifiers(
    context: RollContext,
    attribute: AttributeId? = null,
    active: Set<CharacterConditionId> = emptySet(),
): ConditionRollModifiers {
    val conditions = effectiveRollConditions(active)
    val effectiveAttribute = attribute ?: when (context) {
        RollContext.GRAPPLE, RollContext.PUSH, RollContext.KNOCKDOWN -> AttributeId.STRENGTH
        RollContext.TRIP -> AttributeId.DEXTERITY
        else -> null
    }
    val physical = effectiveAttribute in physicalRollAttributes
    val derivedWeak = context in setOf(RollContext.REFLEXES, RollContext.DODGE, RollContext.INITIATIVE, RollContext.RUN)
    val exhaustionUnresolved = CharacterConditionId.EXHAUSTION in conditions && (physical || derivedWeak)
    val contributions = mutableListOf<RollContribution>()
    val weak = CharacterConditionId.WEAKNESS in conditions && !exhaustionUnresolved
    if (weak && derivedWeak) contributions += RollContribution("Слабость", -2)
    if (CharacterConditionId.DEAFNESS in conditions && context == RollContext.INITIATIVE)
        contributions += RollContribution("Глухота", -4)
    if (CharacterConditionId.SHOCK in conditions && context in setOf(RollContext.ATTACK, RollContext.FLURRY,
            RollContext.BREAK_ITEM, RollContext.SKILL, RollContext.ATTRIBUTE, RollContext.REFLEXES,
            RollContext.DODGE, RollContext.INITIATIVE)) contributions += RollContribution("Шок", -1)
    return ConditionRollModifiers(contributions, if (weak && physical) 2 else 0,
        if (exhaustionUnresolved) "Формула Истощения и её сочетание со Слабостью пока не автоматизированы." else null,
        conditions - setOf(CharacterConditionId.WEAKNESS, CharacterConditionId.SHOCK, CharacterConditionId.DEAFNESS))
}

data class ConditionDerivedValues(val defense: Int?, val reflexes: Int?, val initiative: Int?, val run: Double?)

fun DublCharacter.conditionDerivedValues(active: Set<CharacterConditionId> = emptySet()): ConditionDerivedValues {
    val conditions = effectiveRollConditions(active)
    val exhausted = CharacterConditionId.EXHAUSTION in conditions
    val weak = CharacterConditionId.WEAKNESS in conditions
    return ConditionDerivedValues(
        if (exhausted) null else defense - if (weak) 2 else 0,
        conditionRollModifiers(RollContext.REFLEXES, active = active).apply(reflexes),
        conditionRollModifiers(RollContext.INITIATIVE, active = active).apply(initiative),
        if (exhausted) null else runFull + conditionRollModifiers(RollContext.RUN, active = active).numericBonus,
    )
}
