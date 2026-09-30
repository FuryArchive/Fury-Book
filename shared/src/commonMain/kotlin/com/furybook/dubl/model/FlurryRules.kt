package com.furybook.dubl.model

enum class FlurryMode(val title: String) {
    FULL("Полный шквал"),
    SHORT("Короткий шквал"),
}

enum class FlurryWeaponProfile(
    val title: String,
    val fullBonus: Int,
    val excessPerHit: Int,
    val maximumHits: Int,
) {
    LIGHT("Одно лёгкое оружие", 4, 2, 3),
    ONE_HANDED("Одноручное оружие", 3, 3, 3),
    TWO_HANDED("Двуручное оружие", 2, 4, 3),
    TWO_WEAPONS_LIGHT("Два оружия · тяжелейшее лёгкое", 2, 2, 6),
    TWO_WEAPONS_ONE_HANDED("Два оружия · тяжелейшее одноручное", 2, 3, 6),
    TWO_WEAPONS_TWO_HANDED("Два оружия · тяжелейшее двуручное", 2, 4, 6),
}

data class FlurryProfile(
    val mode: FlurryMode,
    val weapon: FlurryWeaponProfile,
    val attackBonus: Int,
    val excessPerHit: Int,
    val maximumHits: Int,
    val actionText: String,
    val reactionAllowed: Boolean,
)

object FlurryRules {
    fun profile(
        mode: FlurryMode,
        weapon: FlurryWeaponProfile,
    ): FlurryProfile {
        val short = mode == FlurryMode.SHORT
        return FlurryProfile(
            mode = mode,
            weapon = weapon,
            attackBonus = if (short) (weapon.fullBonus + 1) / 2 else weapon.fullBonus,
            excessPerHit = weapon.excessPerHit + if (short) 1 else 0,
            maximumHits = weapon.maximumHits,
            actionText = if (short) "2 ОД" else "весь раунд",
            reactionAllowed = false,
        )
    }

    /**
     * The rulebook grants one hit for every full excess interval, up to the
     * weapon profile's cap. Short flurry raises that interval by one.
     */
    fun hitCount(excess: Int, profile: FlurryProfile): Int =
        if (excess <= 0) 0
        else (excess / profile.excessPerHit.coerceAtLeast(1)).coerceAtMost(profile.maximumHits)
}
