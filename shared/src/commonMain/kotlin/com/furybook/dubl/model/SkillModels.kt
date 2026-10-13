package com.furybook.dubl.model

/** UI-only grouping. Categories do not change DUBL rules. */
enum class SkillCategory(val title: String) {
    COMBAT("Бой"),
    PHYSICAL("Физические"),
    FIELD("Полевые"),
    SOCIAL("Социальные"),
    KNOWLEDGE("Знания"),
    TECHNICAL("Технические"),
    CUSTOM("Свои"),
}

enum class UntrainedRule(val label: String, val penalty: Int?, val usable: Boolean) {
    YES("Да", 0, true),
    YES_MINUS_2("Да (−2)", -2, true),
    NO("Нет", null, false),
    UNSPECIFIED("Не указано", null, false),
}

fun UntrainedRule.unavailableReason(): String = when (this) {
    UntrainedRule.NO -> "Нельзя использовать без обучения"
    UntrainedRule.UNSPECIFIED -> "Правило нетренированного использования не определено в рулбуке"
    else -> ""
}

data class SkillDefinition(
    val id: String,
    val name: String,
    val description: String,
    val category: SkillCategory,
    val defaultAttribute: AttributeId,
    val untrained: UntrainedRule,
    val auto6: String,
    val auto12: String,
    val template: Boolean = false,
)

data class CharacterSkill(
    val id: String,
    val definitionId: String? = null,
    val name: String = "",
    val description: String = "",
    val rank: Int = 0,
    val attributes: List<AttributeId> = emptyList(),
    val modifier: Int = 0,
    val formulaNote: String = "",
    val categoryOverride: SkillCategory? = null,
    val untrainedOverride: UntrainedRule? = null,
    val auto6Override: String? = null,
    val auto12Override: String? = null,
)

data class ResolvedSkill(
    val id: String,
    val definition: SkillDefinition?,
    val state: CharacterSkill,
) {
    val name: String
        get() = state.name.ifBlank { definition?.name.orEmpty() }
    val description: String
        get() = state.description.ifBlank { definition?.description.orEmpty() }
    val category: SkillCategory
        get() = state.categoryOverride ?: definition?.category ?: SkillCategory.CUSTOM
    val rank: Int get() = state.rank
    val modifier: Int get() = state.modifier
    val formulaNote: String get() = state.formulaNote
    val untrained: UntrainedRule
        get() = state.untrainedOverride ?: definition?.untrained ?: UntrainedRule.YES
    val auto6: String
        get() = state.auto6Override ?: definition?.auto6.orEmpty()
    val auto12: String
        get() = state.auto12Override ?: definition?.auto12.orEmpty()
    val attributes: List<AttributeId>
        get() = state.attributes.distinct().ifEmpty {
            listOf(definition?.defaultAttribute ?: AttributeId.INTELLIGENCE)
        }
    val isBuiltIn: Boolean
        get() = definition != null && !definition.template && id == definition.id
    val isDynamic: Boolean get() = !isBuiltIn
    val stockAttribute: AttributeId
        get() = definition?.defaultAttribute ?: attributes.first()
}

data class SkillContribution(val label: String, val value: Int)

data class SkillCalculation(
    val total: Int?,
    val contributions: List<SkillContribution>,
    val unavailableReason: String = "",
    val selectedAttribute: AttributeId? = null,
) {
    fun formulaText(skill: ResolvedSkill, showConfiguredOptions: Boolean = true): String = buildString {
        if (showConfiguredOptions && skill.attributes.size > 1) {
            append("Характеристика: ")
            append(selectedAttribute?.title ?: skill.attributes.first().title)
            append('\n').append("Варианты: ")
            append(skill.attributes.joinToString(" / ") { it.title })
        } else {
            append("Характеристика: ")
            append(selectedAttribute?.title ?: skill.attributes.first().title)
        }
        if (unavailableReason.isNotBlank()) {
            append('\n').append(unavailableReason)
        }
        if (contributions.isNotEmpty()) {
            append('\n').append(if (total == null) "Состав: " else "Расчёт: ")
            append(contributions.joinToString("; ") { item ->
                val sign = if (item.value >= 0) "+" else "−"
                "${item.label} $sign${kotlin.math.abs(item.value)}"
            })
            if (total != null) append(" = ${if (total >= 0) "+" else ""}$total")
        }
        if (skill.formulaNote.isNotBlank()) {
            append('\n').append("Особое правило: ").append(skill.formulaNote)
        }
    }
}

object SkillCatalog {
    val rankCosts: List<Int> = GeneratedSkillCatalog.rankCosts
    val definitions: List<SkillDefinition> = GeneratedSkillCatalog.definitions

    val builtIns: List<SkillDefinition> = definitions.filterNot { it.template }
    val templates: List<SkillDefinition> = definitions.filter { it.template }
    private val byId = definitions.associateBy { it.id }

    fun definition(id: String?): SkillDefinition? = id?.let(byId::get)

    fun costForRank(rank: Int): Int = rankCosts[rank.coerceIn(0, 10)]

    fun nextRankCost(rank: Int): Int? {
        val current = rank.coerceIn(0, 10)
        if (current >= 10) return null
        return rankCosts[current + 1] - rankCosts[current]
    }
}

fun DublCharacter.resolvedSkills(includeHidden: Boolean = false): List<ResolvedSkill> {
    val builtIns = SkillCatalog.builtIns.map { definition ->
        val state = skills[definition.id] ?: CharacterSkill(
            id = definition.id,
            definitionId = definition.id,
        )
        ResolvedSkill(definition.id, definition, state)
    }
    val dynamic = skills.values
        .filter { state -> SkillCatalog.builtIns.none { it.id == state.id } }
        .map { state -> ResolvedSkill(state.id, SkillCatalog.definition(state.definitionId), state) }

    return (builtIns + dynamic)
        .filter { includeHidden || it.id !in hiddenSkillIds }
        .sortedWith(compareBy<ResolvedSkill>({ it.category.ordinal }, { it.name.lowercase() }))
}

fun DublCharacter.resolveSkill(skillId: String): ResolvedSkill? {
    val builtIn = SkillCatalog.builtIns.firstOrNull { it.id == skillId }
    if (builtIn != null) {
        return ResolvedSkill(
            id = builtIn.id,
            definition = builtIn,
            state = skills[builtIn.id] ?: CharacterSkill(id = builtIn.id, definitionId = builtIn.id),
        )
    }
    val state = skills[skillId] ?: return null
    return ResolvedSkill(skillId, SkillCatalog.definition(state.definitionId), state)
}

fun DublCharacter.skillCalculation(
    skill: ResolvedSkill,
    attribute: AttributeId? = null,
): SkillCalculation {
    val selectedAttribute = attribute
        ?.takeIf { it in skill.attributes }
        ?: skill.attributes.first()
    return skillCalculationWithSelectedAttribute(skill, selectedAttribute)
}

/**
 * Per-roll override used by the character sheet. DUBL checks can call for a
 * different characteristic than the skill's configured/default one, so this
 * path intentionally accepts any core characteristic for this single roll.
 */
fun DublCharacter.skillCalculationForRoll(
    skill: ResolvedSkill,
    attribute: AttributeId,
): SkillCalculation = skillCalculationWithSelectedAttribute(skill, attribute)

private fun DublCharacter.skillCalculationWithSelectedAttribute(
    skill: ResolvedSkill,
    selectedAttribute: AttributeId,
): SkillCalculation {
    val contributions = mutableListOf<SkillContribution>()
    var total = attribute(selectedAttribute)
    contributions += SkillContribution(selectedAttribute.title, total)

    val loadPenalty = rollLoadPenalty(selectedAttribute)
    if (loadPenalty != 0) {
        total += loadPenalty
        contributions += SkillContribution("Нагрузка", loadPenalty)
    }

    total += skill.rank
    contributions += SkillContribution("Ранг", skill.rank)
    val sharedPerformanceRank = effectivePerformanceRank(skill)
    if (sharedPerformanceRank > skill.rank) {
        val bonus = sharedPerformanceRank - skill.rank
        total += bonus
        contributions += SkillContribution("Разносторонний исполнитель", bonus)
    }

    if (skill.modifier != 0) {
        total += skill.modifier
        contributions += SkillContribution("Поправка умения", skill.modifier)
    }

    if (skill.rank == 0) {
        if (!skill.untrained.usable && !eruditionAllowsUntrained(skill)) {
            return SkillCalculation(
                total = null,
                contributions = contributions,
                unavailableReason = skill.untrained.unavailableReason(),
                selectedAttribute = selectedAttribute,
            )
        }
        val penalty = skill.untrained.penalty ?: 0
        if (penalty != 0) {
            total += penalty
            contributions += SkillContribution("Без обучения", penalty)
        }
    }

    return SkillCalculation(
        total = total,
        contributions = contributions,
        selectedAttribute = selectedAttribute,
    )
}

fun DublCharacter.skillCalculationOptions(skill: ResolvedSkill): List<Pair<AttributeId, SkillCalculation>> =
    skill.attributes.map { attribute ->
        attribute to skillCalculation(skill, attribute)
    }

fun DublCharacter.skillXpSpent(): Int = resolvedSkills(includeHidden = true)
    .sumOf { skillXpCostForRank(it.rank) }

fun DublCharacter.skillXpCostForRank(rank: Int): Int {
    val normalized = rank.coerceIn(0, 10)
    val base = SkillCatalog.costForRank(normalized)
    if (!creationComplete || developmentRank(DevelopmentEffectIds.SELF_TAUGHT) <= 0) return base
    val discountedPart = SkillCatalog.costForRank(normalized.coerceAtMost(2))
    return base - discountedPart / 2
}

fun DublCharacter.skillNextRankCost(currentRank: Int): Int? {
    val current = currentRank.coerceIn(0, 10)
    if (current >= 10) return null
    return skillXpCostForRank(current + 1) - skillXpCostForRank(current)
}

/** Source effect: highest learned performance rank, without changing saved ranks. */
fun DublCharacter.effectivePerformanceRank(skill: ResolvedSkill): Int {
    if (skill.rank < 1 || skill.definition?.id !in setOf("performance_vocal", "performance_template") ||
        developmentRank("feat_0bf4bbf1c46717a4") < 1 ||
        "skill_effect_051_e8064c1e7ba7" in disabledSkillEffectIds) return skill.rank
    return resolvedSkills(includeHidden = true)
        .filter { it.definition?.id in setOf("performance_vocal", "performance_template") }
        .maxOfOrNull { it.rank }?.coerceAtLeast(skill.rank) ?: skill.rank
}

fun ResolvedSkill.isSecretKnowledge(): Boolean =
    developmentNormalize(name).contains("тайн")

fun DublCharacter.eruditionAllowsUntrained(skill: ResolvedSkill): Boolean =
    skill.definition?.id in setOf("knowledge_academic", "knowledge_template") &&
        !skill.isSecretKnowledge() && developmentRank("feat_4e8ea7dbf3fb9784") > 0 &&
        "skill_effect_046_77fd048a901f" !in disabledSkillEffectIds
