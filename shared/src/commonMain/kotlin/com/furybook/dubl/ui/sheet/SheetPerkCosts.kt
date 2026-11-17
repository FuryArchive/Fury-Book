package com.furybook.dubl.ui.sheet

private val COST = Regex("""(\d+)\s*(?:пункт\S*\s*)?(ОД|маны|выносливост\S*)""")
private val COST_LEAD = Regex("За|за|трат|затрач")
private val REACTION = Regex("(тратя|затрачивая|тратить действие) реакци", RegexOption.IGNORE_CASE)

/** Entries the wording heuristic gets wrong, by entry id; these tags win over the parser. */
internal val PERK_COST_OVERRIDES: Map<String, List<String>> = emptyMap()

/**
 * What using a perk costs, read from its rules text: «N ОД», «N маны», «N вын.» or «реакция».
 * Only spending phrases count («За 1 ОД…», «тратите 1 пункт выносливости»), not discounts («на 1 ОД меньше»).
 */
fun perkCostTags(entryId: String, benefit: String): List<String> {
    PERK_COST_OVERRIDES[entryId]?.let { return it }
    val tags = mutableListOf<String>()
    COST.findAll(benefit).forEach { match ->
        val before = benefit.substring((match.range.first - 25).coerceAtLeast(0), match.range.first)
        val after = benefit.substring(match.range.last + 1, (match.range.last + 11).coerceAtMost(benefit.length))
        if (!COST_LEAD.containsMatchIn(before) || "меньше" in after) return@forEach
        val (amount, unit) = match.destructured
        val tag = when (unit) {
            "ОД" -> "$amount ОД"
            "маны" -> "$amount маны"
            else -> "$amount вын."
        }
        if (tag !in tags) tags += tag
    }
    if (tags.isEmpty() && REACTION.containsMatchIn(benefit)) tags += "реакция"
    return tags
}
