package com.furybook.dubl.model

enum class SkillEffectMode {
    AUTO_BONUS,
    TOGGLE_BONUS,
    TOGGLE_ADVANTAGE,
    TOGGLE_HINDRANCE,
    TOGGLE_RULE,
    ALTERNATIVE,
    PRESET,
    REROLL,
    FORMULA,
    REMINDER,
}

data class SkillEffectDefinition(
    val id: String,
    val reviewIndex: Int,
    val sourceName: String,
    val effectText: String,
    val targetText: String,
    val status: String,
    val plan: String,
    val mode: SkillEffectMode,
    val rollContext: RollContext?,
    val targetSkills: List<String>,
    val value: Int,
    val perRank: Boolean,
    val toggleLabel: String,
    val developmentId: String = "",
    val sourceRefs: List<String> = emptyList(),
)

data class SkillEffectCatalog(
    val version: String,
    val effects: List<SkillEffectDefinition>,
)

data class SkillRollEffectOption(
    val id: String,
    val label: String,
    val sourceName: String,
    val description: String,
    val numericBonus: Int = 0,
    val advantageDice: Int = 0,
    val hindranceDice: Int = 0,
)

data class SkillRollEffectTotals(
    val numericBonus: Int = 0,
    val advantageDice: Int = 0,
    val hindranceDice: Int = 0,
)

fun List<SkillRollEffectOption>.selectedTotals(selectedIds: Set<String>): SkillRollEffectTotals {
    val selected = filter { it.id in selectedIds }
    return SkillRollEffectTotals(
        numericBonus = selected.sumOf { it.numericBonus },
        advantageDice = selected.sumOf { it.advantageDice },
        hindranceDice = selected.sumOf { it.hindranceDice },
    )
}

data class SkillRollEffectResolution(
    val automaticContributions: List<RollContribution> = emptyList(),
    val options: List<SkillRollEffectOption> = emptyList(),
    val reminders: List<SkillEffectDefinition> = emptyList(),
) {
    val automaticBonus: Int get() = automaticContributions.sumOf { it.value }
}

class SkillEffectRules(
    private val character: DublCharacter,
    private val developmentCatalog: DevelopmentCatalog,
    private val effectCatalog: SkillEffectCatalog,
) {
    fun configuredForSkill(skill: ResolvedSkill): List<SkillEffectDefinition> = effectCatalog.effects.filter { effect ->
        effect.rollContext == RollContext.SKILL && effect.matchesSkill(skill) && ownedRank(effect) > 0
    }

    fun forSkill(skill: ResolvedSkill): SkillRollEffectResolution {
        val relevant = configuredForSkill(skill).filter { effect ->
            effect.id !in character.disabledSkillEffectIds && effect.skillConditionsHold(skill)
        }
        val automatic = relevant.mapNotNull { effect ->
            if (effect.mode != SkillEffectMode.AUTO_BONUS) return@mapNotNull null
            val value = effect.scaledValue(ownedRank(effect))
            if (value == 0) null else RollContribution(effect.sourceName, value)
        }
        val options = relevant.mapNotNull { effect ->
            val rank = ownedRank(effect)
            when (effect.mode) {
                SkillEffectMode.TOGGLE_BONUS -> SkillRollEffectOption(
                    id = effect.id,
                    label = effect.toggleLabel,
                    sourceName = effect.sourceName,
                    description = effect.effectText,
                    numericBonus = effect.scaledValue(rank),
                )
                SkillEffectMode.TOGGLE_ADVANTAGE -> SkillRollEffectOption(
                    id = effect.id,
                    label = effect.toggleLabel,
                    sourceName = effect.sourceName,
                    description = effect.effectText,
                    advantageDice = if (effect.perRank) rank.coerceAtLeast(1) else 1,
                )
                SkillEffectMode.TOGGLE_HINDRANCE -> SkillRollEffectOption(
                    id = effect.id,
                    label = effect.toggleLabel,
                    sourceName = effect.sourceName,
                    description = effect.effectText,
                    hindranceDice = when (effect.developmentId) {
                        // This feature removes only vision-related penalties. It
                        // must never ADD a hindrance or erase unrelated ones.
                        "feat_3502559c63707e6a" -> 0
                        "feat_dad6c142bda7893e" -> if (rank >= 2) 0 else 1
                        else -> if (effect.perRank) rank.coerceAtLeast(1) else 1
                    },
                )
                SkillEffectMode.TOGGLE_RULE -> SkillRollEffectOption(
                    id = effect.id,
                    label = effect.toggleLabel,
                    sourceName = effect.sourceName,
                    description = effect.effectText,
                )
                else -> null
            }
        }
        val reminders = relevant.filter { effect ->
            effect.mode in setOf(
                SkillEffectMode.ALTERNATIVE,
                SkillEffectMode.PRESET,
                SkillEffectMode.REROLL,
                SkillEffectMode.REMINDER,
            )
        }
        return SkillRollEffectResolution(
            automaticContributions = automatic,
            options = options + contextOptions(RollContext.SKILL),
            reminders = reminders,
        )
    }

    fun forContext(context: RollContext): List<SkillEffectDefinition> = effectCatalog.effects.filter { effect ->
        effect.id !in character.disabledSkillEffectIds && effect.rollContext == context && ownedRank(effect) > 0
    }

    /** Zero-cost situational effects. Selection confirms the stated external
     * condition for THIS roll only; it does not invent encounter state. */
    fun contextOptions(context: RollContext): List<SkillRollEffectOption> = effectCatalog.effects.mapNotNull { effect ->
        if (effect.id in character.disabledSkillEffectIds || ownedRank(effect) <= 0) return@mapNotNull null
        val condition = when (effect.developmentId) {
            "feat_255d30b26489a033" -> if (context in setOf(RollContext.ATTACK, RollContext.FLURRY))
                "Налёт: мультидействие атака + движение не менее 5 м" else null
            "feat_2f6cc2a6bb5fbef5" -> if (context == RollContext.DODGE)
                "Сфокусированное уклонение: уворачиваюсь от выбранной цели" else null
            "feat_108ac1d6e40fa059" -> if (context == RollContext.FEINT)
                "Соколиные перья: финт минимум за 2 ОД с плащом" else null
            "feat_e51286dcfb04695d" -> if (context !in setOf(RollContext.ATTACK, RollContext.FLURRY, RollContext.BREAK_ITEM,
                RollContext.FORTITUDE, RollContext.REFLEXES, RollContext.DODGE))
                "Командный игрок: союзник не далее 3 м" else null
            else -> null
        } ?: return@mapNotNull null
        val numeric = if (effect.developmentId == "feat_e51286dcfb04695d") 1 else 0
        SkillRollEffectOption(effect.id,condition,effect.sourceName,effect.effectText,
            numericBonus=numeric,advantageDice=if(numeric==0) 1 else 0)
    }.distinctBy { it.id }

    /** Source-qualified restrictions on the effect, not acquisition eligibility. */
    private fun SkillEffectDefinition.skillConditionsHold(skill: ResolvedSkill): Boolean = when (developmentId) {
        "feat_3233f2da5c693e34", // Ас: driving/piloting with at least four ranks.
        "feat_f33c232811919ee5" -> skill.rank >= 4 // Талантливый исполнитель.
        "feat_1a9f2f0b9135c3d8" -> skill.rank >= 5 // Creature-identification bonus.
        "feat_4e8ea7dbf3fb9784" -> skill.rank >= 1 && !skill.isSecretKnowledge()
        "feat_0bf4bbf1c46717a4" -> skill.rank >= 1
        else -> true
    }

    private fun ownedRank(effect: SkillEffectDefinition): Int {
        if (effect.developmentId.isNotBlank()) {
            return character.development[effect.developmentId]?.rank ?: 0
        }
        return developmentCatalog.matchingName(effect.sourceName)
            .maxOfOrNull { entry -> character.development[entry.id]?.rank ?: 0 }
            ?: 0
    }

    private fun SkillEffectDefinition.scaledValue(rank: Int): Int = if (perRank) value * rank else value

    private fun SkillEffectDefinition.matchesSkill(skill: ResolvedSkill): Boolean {
        if (targetSkills.isEmpty()) return false
        val skillName = developmentNormalize(skill.name)
        return targetSkills.any { target ->
            val normalized = developmentNormalize(target)
            when (normalized) {
                "знание" -> skill.category == SkillCategory.KNOWLEDGE && skillName.startsWith("знание")
                "исполнение" -> skillName.startsWith("исполнение")
                "ремесло" -> skillName.startsWith("ремесло")
                else -> skillName == normalized
            }
        }
    }
}
