package com.furybook.dubl.model

/**
 * Parses the DUBL 3.69 mana-rank requirement spellings used by the core rulebook.
 *
 * The book mixes Arabic ranks ("Запас маны 3") with Roman ranks
 * ("Запас маны II", "Запас маны (III)") and also uses the
 * "Базовый запас маны" name for the purchasable creation entry.
 */
internal fun manaRequirementRankOrNull(source: String): Int? {
    val text = source.trim(' ', '.', ',', ';')
    val match = Regex(
        "^(?:Базовый\\s+)?Запас\\s+маны(?:\\s*\\(?\\s*([IVX]+|\\d+)\\s*\\)?)?$",
        RegexOption.IGNORE_CASE,
    ).matchEntire(text) ?: return null

    val token = match.groupValues.getOrNull(1).orEmpty()
    if (token.isBlank()) return 1
    token.toIntOrNull()?.let { return it }
    return romanRankOrNull(token)
}

private fun romanRankOrNull(source: String): Int? {
    val roman = source.uppercase()
    if (roman.isBlank() || roman.any { it !in "IVX" }) return null
    val values = mapOf('I' to 1, 'V' to 5, 'X' to 10)
    var total = 0
    var previous = 0
    for (char in roman.reversed()) {
        val value = values[char] ?: return null
        if (value < previous) total -= value else {
            total += value
            previous = value
        }
    }
    return total.takeIf { it > 0 }
}
