package com.furybook.ui.text

/** The Russian form for [n]: [one] (1, 21), [few] (2–4, 22–24), [many] (0, 5–20, 25–30…). */
fun ruPlural(n: Int, one: String, few: String, many: String): String {
    val abs = kotlin.math.abs(n)
    val lastTwo = abs % 100
    val last = abs % 10
    return when {
        lastTwo in 11..14 -> many
        last == 1 -> one
        last in 2..4 -> few
        else -> many
    }
}

/** «5 заклинаний», «1 заклинание», «2 заклинания». */
fun ruCount(n: Int, one: String, few: String, many: String): String = "$n ${ruPlural(n, one, few, many)}"
