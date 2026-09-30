package com.furybook.dubl.model

enum class RollContext(val title: String) {
    ATTRIBUTE("Проверка характеристики"),
    SKILL("Проверка умения"),
    FORTITUDE("Стойкость"),
    REFLEXES("Рефлексы"),
    INITIATIVE("Инициатива"),
    RUN("Бег"),
    DODGE("Уворачивание"),
    ATTACK("Атака"),
    FLURRY("Шквал атак"),
    PARRY("Парирование"),
    FEINT("Финт"),
    GRAPPLE("Захват"),
    DISARM("Обезоруживание"),
    TRIP("Подсечка"),
    PUSH("Толкание"),
    KNOCKDOWN("Сбивание"),
    BREAK_ITEM("Поломка предмета"),
}

enum class FlurryWeaponProfile(
    val title: String,
    val attackBonus: Int,
    val excessPerHit: Int,
    val maxHits: Int,
    val twoWeapons: Boolean = false,
) {
    LIGHT("Одно лёгкое оружие", 4, 2, 3),
    ONE_HANDED("Одноручное оружие", 3, 3, 3),
    TWO_HANDED("Двуручное оружие", 2, 4, 3),
    TWO_WEAPONS_LIGHT("Два оружия · самое тяжёлое лёгкое", 2, 2, 6, true),
    TWO_WEAPONS_ONE_HANDED("Два оружия · самое тяжёлое одноручное", 2, 3, 6, true),
    TWO_WEAPONS_TWO_HANDED("Два оружия · самое тяжёлое двуручное", 2, 4, 6, true),
}

data class FlurryAttackRule(
    val attackBonus: Int,
    val excessPerHit: Int,
    val maxHits: Int,
    val shortExcessPenalty: Int,
    val actionCost: String,
    val reactionAllowed: Boolean,
    val alternateWeaponDamage: Boolean,
)

fun FlurryWeaponProfile.rule(short: Boolean): FlurryAttackRule = FlurryAttackRule(
    // Core 3.69 uses the general division rule: fractions are rounded up.
    attackBonus = if (short) (attackBonus + 1) / 2 else attackBonus,
    excessPerHit = excessPerHit,
    maxHits = maxHits,
    shortExcessPenalty = if (short) 1 else 0,
    actionCost = if (short) "2 ОД" else "полный раунд",
    reactionAllowed = false,
    alternateWeaponDamage = twoWeapons,
)

fun RollContext.allowedSkillIds(): List<String> = when (this) {
    RollContext.ATTACK, RollContext.FLURRY, RollContext.BREAK_ITEM -> listOf("unarmed", "melee_weapon", "shooting", "throwing")
    RollContext.PARRY, RollContext.DISARM -> listOf("unarmed", "melee_weapon")
    RollContext.FEINT -> listOf("eloquence", "unarmed", "melee_weapon")
    else -> emptyList()
}

fun RollContext.allowedAttributes(skillId: String?): List<AttributeId> = when (this) {
    RollContext.ATTACK, RollContext.FLURRY, RollContext.BREAK_ITEM -> when (skillId) {
        "shooting" -> listOf(AttributeId.PERCEPTION, AttributeId.DEXTERITY)
        "throwing", "unarmed", "melee_weapon" -> listOf(AttributeId.DEXTERITY, AttributeId.STRENGTH)
        else -> emptyList()
    }
    RollContext.PARRY, RollContext.DISARM -> listOf(AttributeId.DEXTERITY, AttributeId.STRENGTH)
    RollContext.FEINT -> listOf(AttributeId.CHARISMA)
    else -> emptyList()
}

data class RollContribution(
    val label: String,
    val value: Int,
)

data class CharacterRollPreset(
    val context: RollContext,
    val title: String,
    val bonus: Int?,
    val contributions: List<RollContribution>,
    val formulaText: String,
    val unavailableReason: String = "",
) {
    val available: Boolean get() = bonus != null
}

fun DublCharacter.rollPreset(
    context: RollContext,
    skillId: String? = null,
    attribute: AttributeId? = null,
): CharacterRollPreset {
    return when (context) {
        RollContext.ATTRIBUTE -> attributePreset(attribute ?: AttributeId.STRENGTH)
        RollContext.SKILL -> skillPreset(skillId ?: return unavailablePreset(context, "Не выбрано умение"), attribute)
        RollContext.FORTITUDE -> fixedPreset(
            context = context,
            contributions = listOf(
                RollContribution("Телосложение", constitution),
                RollContribution("Воля", will),
                RollContribution("Стойкий", developmentRank(DevelopmentEffectIds.STALWART)),
                RollContribution("Неподвижная гора", stillMountainBonus),
            ).filter { it.value != 0 },
        )
        RollContext.REFLEXES -> fixedPreset(
            context = context,
            contributions = listOf(
                RollContribution("Скорость", speed),
                RollContribution("Ловкость", dexterity),
                RollContribution("Нагрузка", equipmentLoadPenalty),
                RollContribution("Быстрые рефлексы", developmentRank(DevelopmentEffectIds.QUICK_REFLEXES)),
            ).filter { it.value != 0 },
        )
        RollContext.INITIATIVE -> fixedPreset(
            context = context,
            contributions = listOf(
                RollContribution("Скорость", speed),
                RollContribution("Восприятие", perception),
                RollContribution("Улучшенная инициатива", developmentRank(DevelopmentEffectIds.IMPROVED_INITIATIVE)),
                RollContribution("Владыка бури", stormLordBonus),
            ).filter { it.value != 0 },
        )
        RollContext.RUN -> fixedPreset(
            context = context,
            contributions = listOf(RollContribution("Бег", runFull.toInt())),
        )
        RollContext.DODGE -> fixedPreset(
            context = context,
            contributions = listOf(RollContribution("Рефлексы", reflexes)),
            formulaOverride = "2d6 + Рефлексы + ситуационные бонусы защиты",
        )
        RollContext.ATTACK -> attackLikePreset(context, skillId ?: "melee_weapon", attribute)
        RollContext.FLURRY -> attackLikePreset(context, skillId ?: "melee_weapon", attribute)
        RollContext.PARRY -> {
            val base = attackLikePreset(context, skillId ?: "melee_weapon", attribute)
            base.withDevelopmentBonus("Фехтовальщик", developmentRank(DevelopmentEffectIds.FENCER))
        }
        RollContext.FEINT -> {
            val chosenSkill = skillId ?: "eloquence"
            val chosenAttribute = attribute ?: AttributeId.CHARISMA
            val base = skillBasedPreset(context, chosenSkill, chosenAttribute)
            base.withDevelopmentBonus("Финтовальщик", developmentRank(DevelopmentEffectIds.FEINTER))
        }
        RollContext.GRAPPLE -> skillBasedPreset(context, "unarmed", AttributeId.STRENGTH)
        RollContext.DISARM -> skillBasedPreset(context, skillId ?: "melee_weapon", attribute ?: AttributeId.DEXTERITY)
        RollContext.TRIP -> skillBasedPreset(context, "unarmed", AttributeId.DEXTERITY)
        RollContext.PUSH -> skillBasedPreset(context, "unarmed", AttributeId.STRENGTH)
        RollContext.KNOCKDOWN -> skillBasedPreset(context, "unarmed", AttributeId.STRENGTH)
        RollContext.BREAK_ITEM -> attackLikePreset(context, skillId ?: "melee_weapon", attribute)
    }
}

private fun DublCharacter.attributePreset(attribute: AttributeId): CharacterRollPreset {
    val contributions = mutableListOf(RollContribution(attribute.title, attribute(attribute)))
    val loadPenalty = rollLoadPenalty(attribute)
    if (loadPenalty != 0) contributions += RollContribution("Нагрузка", loadPenalty)
    return CharacterRollPreset(
        context = RollContext.ATTRIBUTE,
        title = attribute.title,
        bonus = contributions.sumOf { it.value },
        contributions = contributions,
        formulaText = "2d6 + " + contributions.joinToString(" + ") { it.label },
    )
}

private fun DublCharacter.skillPreset(skillId: String, attribute: AttributeId?): CharacterRollPreset {
    val skill = resolveSkill(skillId) ?: return unavailablePreset(RollContext.SKILL, "Умение не найдено")
    val calculation = skillCalculation(skill, attribute)
    return CharacterRollPreset(
        context = RollContext.SKILL,
        title = skill.name,
        bonus = calculation.total,
        contributions = calculation.contributions.map { RollContribution(it.label, it.value) },
        formulaText = calculation.formulaText(skill),
        unavailableReason = calculation.unavailableReason,
    )
}

private fun DublCharacter.skillBasedPreset(
    context: RollContext,
    skillId: String,
    attribute: AttributeId,
    attack: Boolean = false,
): CharacterRollPreset {
    val skill = resolveSkill(skillId) ?: return unavailablePreset(context, "Умение не найдено")
    val contributions = mutableListOf(
        RollContribution(attribute.title, attribute(attribute)),
        RollContribution("Ранг", skill.rank),
    )
    val loadPenalty = rollLoadPenalty(attribute, attack = attack)
    if (loadPenalty != 0) {
        contributions += RollContribution("Нагрузка", loadPenalty)
    }
    if (skill.modifier != 0) {
        contributions += RollContribution("Поправка умения", skill.modifier)
    }
    if (skill.rank == 0 && !skill.untrained.usable) {
        return CharacterRollPreset(
            context = context,
            title = context.title,
            bonus = null,
            contributions = contributions,
            formulaText = "2d6 + ${attribute.title} + ${skill.name}",
            unavailableReason = skill.untrained.unavailableReason(),
        )
    }
    val untrainedPenalty = if (skill.rank == 0) skill.untrained.penalty ?: 0 else 0
    if (untrainedPenalty != 0) {
        contributions += RollContribution("Без обучения", untrainedPenalty)
    }
    return CharacterRollPreset(
        context = context,
        title = context.title,
        bonus = contributions.sumOf { it.value },
        contributions = contributions,
        formulaText = "2d6 + ${attribute.title} + ${skill.name}",
    )
}

private fun DublCharacter.attackLikePreset(
    context: RollContext,
    skillId: String,
    attribute: AttributeId?,
): CharacterRollPreset {
    val defaultAttribute = when (skillId) {
        "shooting" -> AttributeId.PERCEPTION
        "throwing", "unarmed", "melee_weapon" -> AttributeId.DEXTERITY
        else -> AttributeId.DEXTERITY
    }
    return skillBasedPreset(context, skillId, attribute ?: defaultAttribute, attack = true)
}

private fun fixedPreset(
    context: RollContext,
    contributions: List<RollContribution>,
    formulaOverride: String? = null,
): CharacterRollPreset {
    val total = contributions.sumOf { it.value }
    return CharacterRollPreset(
        context = context,
        title = context.title,
        bonus = total,
        contributions = contributions,
        formulaText = formulaOverride ?: "2d6 + " + contributions.joinToString(" + ") { it.label },
    )
}

private fun unavailablePreset(context: RollContext, reason: String): CharacterRollPreset = CharacterRollPreset(
    context = context,
    title = context.title,
    bonus = null,
    contributions = emptyList(),
    formulaText = reason,
    unavailableReason = reason,
)

private fun CharacterRollPreset.withDevelopmentBonus(label: String, value: Int): CharacterRollPreset {
    if (value == 0 || bonus == null) return this
    return copy(
        bonus = bonus + value,
        contributions = contributions + RollContribution(label, value),
        formulaText = "$formulaText + $label",
    )
}
