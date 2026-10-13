package com.furybook.dubl.model

object DevelopmentEffectIds {
    const val INCREDIBLE_HEALTH = "feat_c8e0271c9bd8bb91"
    const val STALWART = "feat_b4154deb78a0bc28"
    const val ENDURING = "feat_600fc03329750988"
    const val QUICK_REFLEXES = "feat_a5fd203a26fdf5f5"
    const val IMPROVED_INITIATIVE = "feat_8df5e1aefda2a020"
    const val RUNNER = "feat_2d6d89e4371fda60"
    const val HAULER = "feat_a144256fd7c09aeb"
    const val SELF_TAUGHT = "feat_dd0ba74aedebce10"
    const val VIGILANCE = "feat_0be07734fc944da9"
    const val TOUGH_MUSCULATURE = "feat_3219b2220a1a0926" // resolved by name fallback until catalog migration
    const val FENCER = "feat_1c923fa61f82ded6" // resolved by name fallback until catalog migration
    const val FEINTER = "feat_ed8b4da6b63c16cb" // resolved by name fallback until catalog migration
    const val INTERNAL_CHI = "chi_development_6b44546d9d5fa11b"
    const val MASTER_CHI = "chi_development_d516094876012090"
    const val AWAKENED_CHI = "chi_development_df819473c66bcb28"
    const val STILL_MOUNTAIN_SCHOOL = "chi_development_b0cb7f68cfffa0af"
    const val STORM_LORD_SCHOOL = "chi_development_a9a363b5d9403f94"
}

fun DublCharacter.developmentRank(entryId: String): Int =
    development[entryId]?.rank?.coerceAtLeast(0) ?: 0

fun incredibleHealthPerRank(size: Int): Int = when {
    size <= 5 -> 1
    size >= 15 -> 1024
    else -> 1 shl (size - 5)
}

/** Core source: passive Awareness is learned Awareness + Perception, plus 2/rank Vigilance. */
val DublCharacter.passiveAwareness: Int
    get() = (resolveSkill("awareness")?.rank ?: 0) + perception +
        if ("skill_effect_022_d4dfae50b28b" in disabledSkillEffectIds) 0
        else 2 * developmentRank(DevelopmentEffectIds.VIGILANCE)

/** Bonus only: never substitute this for armor supplied by equipment or other rules. */
val DublCharacter.naturalArmorDevelopmentBonus: Int
    get() = if ("skill_effect_213_cae701fac661" in disabledSkillEffectIds) 0
        else developmentRank(DevelopmentEffectIds.TOUGH_MUSCULATURE)
