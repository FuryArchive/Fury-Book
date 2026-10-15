package com.furybook.dubl.model

import kotlin.math.ceil

/** Source P127 explicitly enumerates the physical attributes. */
private val physicalRollAttributes = setOf(AttributeId.STRENGTH, AttributeId.DEXTERITY, AttributeId.SPEED, AttributeId.CONSTITUTION)

fun DublCharacter.effectiveRollConditions(active: Set<CharacterConditionId>): Set<CharacterConditionId> =
    active + if (enduranceCurrent == 0) setOf(CharacterConditionId.WEAKNESS) else emptySet()

data class ConditionRollModifiers(
    val contributions: List<RollContribution> = emptyList(),
    val hindranceDice: Int = 0,
    val unavailableReason: String? = null,
    val unimplemented: Set<CharacterConditionId> = emptySet(),
    val hindranceSources: List<String> = emptyList(),
) {
    val numericBonus: Int get() = contributions.sumOf { it.value }
    val notice: String get() = listOfNotNull(unavailableReason,
        if (hindranceSources.isNotEmpty()) hindranceSources.joinToString("; ") else null,
        if (unimplemented.isNotEmpty()) "Не все эффекты состояний автоматизированы: ${unimplemented.joinToString { it.title }}" else null)
        .joinToString("\n")
    fun apply(base: Int?): Int? = if (unavailableReason != null) null else base?.plus(numericBonus)
}

/** A visual dependency is declared by the player, never inferred from a skill name. */
fun requiresVisionDeclaration(context: RollContext, attribute: AttributeId?, conditions: Set<CharacterConditionId>): Boolean =
    (CharacterConditionId.BLINDNESS in conditions && context == RollContext.SKILL) ||
        (CharacterConditionId.DAZZLED in conditions && attribute == AttributeId.PERCEPTION &&
            context !in setOf(RollContext.ATTACK, RollContext.FLURRY, RollContext.BREAK_ITEM))

/** Existing call shape stays binary-compatible; unspecified vision is never guessed. */
fun DublCharacter.conditionRollModifiers(
    context: RollContext,
    attribute: AttributeId? = null,
    active: Set<CharacterConditionId> = emptySet(),
    skillId: String? = null,
): ConditionRollModifiers = conditionRollModifiers(context, attribute, active, skillId, requiresVision = null)

/** Pure projection of already-known conditions. No resource expenditure, reset or duration inference. */
fun DublCharacter.conditionRollModifiers(
    context: RollContext,
    attribute: AttributeId? = null,
    active: Set<CharacterConditionId> = emptySet(),
    skillId: String? = null,
    requiresVision: Boolean?,
): ConditionRollModifiers {
    val conditions = effectiveRollConditions(active)
    val effectiveAttribute = attribute ?: when (context) {
        RollContext.GRAPPLE, RollContext.PUSH, RollContext.KNOCKDOWN -> AttributeId.STRENGTH
        RollContext.TRIP -> AttributeId.DEXTERITY
        else -> null
    }
    val physical = effectiveAttribute in physicalRollAttributes
    val attack = context in setOf(RollContext.ATTACK, RollContext.FLURRY, RollContext.BREAK_ITEM)
    val meleeAttack = attack && resolveSkill(skillId ?: "melee_weapon")?.definition?.id in setOf("unarmed", "melee_weapon")
    val entangled = CharacterConditionId.ENTANGLED in conditions
    val blind = CharacterConditionId.BLINDNESS in conditions
    val dazzled = CharacterConditionId.DAZZLED in conditions
    val visionOverlap = blind && dazzled && (attack || effectiveAttribute in setOf(AttributeId.DEXTERITY, AttributeId.PERCEPTION))
    val derivedWeak = context in setOf(RollContext.REFLEXES, RollContext.DODGE, RollContext.INITIATIVE, RollContext.RUN)
    val exhausted = CharacterConditionId.EXHAUSTION in conditions
    val weakPresent = CharacterConditionId.WEAKNESS in conditions
    val contributions = mutableListOf<RollContribution>()
    val factors = mutableListOf<Pair<String, Int>>()
    // Owner decision 2026-10-15: Exhaustion and Weakness stack. The set above
    // already deduplicates explicit Weakness and the zero-endurance projection.
    val weak = weakPresent
    if (weak && derivedWeak) contributions += RollContribution("Слабость", -2)
    if (weak && physical) factors += "Слабость" to 2
    if (exhausted) {
        if (physical) {
            contributions += RollContribution("Истощение", -2)
            factors += "Истощение" to 2
        }
        if (context in setOf(RollContext.REFLEXES, RollContext.DODGE, RollContext.INITIATIVE))
            contributions += RollContribution("Истощение", -3)
        if (context == RollContext.RUN) contributions += RollContribution("Истощение: половина бега",
            ceil(runFull / 2).toInt() - runFull.toInt())
    }
    // P14774: the attack and Dexterity clauses describe the same penalty,
    // not two penalties for a Dexterity-based attack.
    if (entangled && (attack || effectiveAttribute == AttributeId.DEXTERITY)) {
        contributions += RollContribution("Опутанный", -3)
        factors += "Опутанный" to 1
    }
    // P14762: explicit skill-check penalty and two hindrance dice for attacks.
    // Vision dependence and long-term adaptation are not inferred from a skill name.
    if (blind && context == RollContext.SKILL && effectiveAttribute in setOf(AttributeId.PERCEPTION, AttributeId.DEXTERITY))
        contributions += RollContribution("Слепота", -4)
    if (blind && attack && !visionOverlap) factors += "Слепота" to 2
    // P14768's attack/Dexterity clauses count once, not twice on a Dexterity attack.
    if (dazzled && !visionOverlap && (attack || effectiveAttribute == AttributeId.DEXTERITY ||
            (effectiveAttribute == AttributeId.PERCEPTION && requiresVision == true))) factors += "Ослепление" to 1
    if (CharacterConditionId.PRONE in conditions && meleeAttack)
        contributions += RollContribution("Сбит с ног: атака ближнего боя", -2)
    if (CharacterConditionId.POISONED in conditions && effectiveAttribute != null)
        factors += "Отравление" to 1
    if (CharacterConditionId.DEAFNESS in conditions && context == RollContext.INITIATIVE)
        contributions += RollContribution("Глухота", -4)
    if (CharacterConditionId.SHOCK in conditions && context in setOf(RollContext.ATTACK, RollContext.FLURRY,
            RollContext.BREAK_ITEM, RollContext.SKILL, RollContext.ATTRIBUTE, RollContext.REFLEXES,
            RollContext.DODGE, RollContext.INITIATIVE, RollContext.PARRY, RollContext.FEINT,
            RollContext.GRAPPLE, RollContext.DISARM, RollContext.TRIP, RollContext.PUSH, RollContext.KNOCKDOWN)) contributions += RollContribution("Шок", -1)
    return ConditionRollModifiers(contributions, factors.sumOf { it.second },
        when {
            blind && context == RollContext.SKILL && requiresVision == true -> "Умение требует зрения и автоматически проваливается при Слепоте (P14762)."
            requiresVision == null && requiresVisionDeclaration(context, effectiveAttribute, conditions) -> "Укажите, требует ли проверка зрения."
            visionOverlap -> "Совместное действие Слепоты и Ослепления требует отдельного решения; штрафы не складываются автоматически."
            entangled && context == RollContext.RUN -> "Опутанный персонаж не может бежать (P14774)."
            else -> null
        },
        conditions - setOf(CharacterConditionId.WEAKNESS, CharacterConditionId.SHOCK, CharacterConditionId.DEAFNESS,
            CharacterConditionId.POISONED, CharacterConditionId.EXHAUSTION),
        factors.map { (source, count) -> "$source: $count негативных фактора" })
}

data class ConditionDerivedValues(val defense: Int?, val reflexes: Int?, val initiative: Int?, val run: Double?)

fun DublCharacter.conditionDerivedValues(active: Set<CharacterConditionId> = emptySet()): ConditionDerivedValues {
    val conditions = effectiveRollConditions(active)
    val exhausted = CharacterConditionId.EXHAUSTION in conditions
    val weak = CharacterConditionId.WEAKNESS in conditions
    return ConditionDerivedValues(
        defense - (if (exhausted) 3 else 0) - (if (weak) 2 else 0) -
            (if (CharacterConditionId.PRONE in conditions) 2 else 0) -
            (if (CharacterConditionId.STUNNED in conditions) 2 else 0),
        conditionRollModifiers(RollContext.REFLEXES, active = active).apply(reflexes),
        conditionRollModifiers(RollContext.INITIATIVE, active = active).apply(initiative),
        if (conditionRollModifiers(RollContext.RUN, active = active).unavailableReason != null) null else if (exhausted) ceil(runFull / 2) - (if (weak) 2 else 0)
        else runFull + conditionRollModifiers(RollContext.RUN, active = active).numericBonus,
    )
}
