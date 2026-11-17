package com.furybook.dubl.ui.sheet

private const val LETTER = "А-Яа-яЁё"

/** A spending verb or «за» (as a word) right before the amount: «За 1 ОД», «тратите 1 пункт выносливости». */
private val SPEND = Regex("""(?<![$LETTER])(?:[Зз]а|[Тт]рат\S*|[Зз]атрачивая|[Пп]отрат\S*)\s+""")
private val AMOUNT = Regex("""(\d+)\s*(?:пункт\S*\s*)?(ОД|маны|выносливост\S*)""")
private val AND_MORE = Regex("""^\s+и\s+""")
private val NOT_OURS = Regex("""(Цель|цель|противник|Противник)\s+может""")
private val NOT_A_COST_AFTER = Regex("""^\s*(и\s+больше|больше|меньше)""")
private val REACTION = Regex(
    """(?<![$LETTER])(?:(?:по)?трат\S*|за|затрачивая)\s+(?:действие\s+)?реакци|реакцией|действием реакции|как реакци""",
    RegexOption.IGNORE_CASE,
)

/** Entries the wording heuristic gets wrong, by entry id; these tags win over the parser. */
internal val PERK_COST_OVERRIDES: Map<String, List<String>> = emptyMap()

/**
 * What using a perk costs, read from its rules text: «N ОД», «N маны», «N вын.» or «реакция».
 * Only the owner spending counts («За 1 ОД…», «тратите 1 пункт выносливости», «за реакцию»): not discounts or
 * extra time («на 1 ОД меньше/больше»), thresholds («тратите 2 ОД и больше»), or what a target may pay.
 */
fun perkCostTags(entryId: String, benefit: String): List<String> {
    PERK_COST_OVERRIDES[entryId]?.let { return it }
    val tags = mutableListOf<String>()
    SPEND.findAll(benefit).forEach { spend ->
        // «Цель может потратить … или потратить 2 ОД»: the whole sentence is about what someone else pays.
        val sentenceStart = maxOf(benefit.lastIndexOf('.', spend.range.first), benefit.lastIndexOf('\n', spend.range.first)) + 1
        if (NOT_OURS.containsMatchIn(benefit.substring(sentenceStart, spend.range.first))) return@forEach
        var at = spend.range.last + 1
        while (true) {
            val amount = AMOUNT.matchAt(benefit, at) ?: break
            val after = benefit.substring(amount.range.last + 1)
            if (NOT_A_COST_AFTER.containsMatchIn(after)) break
            val (count, unit) = amount.destructured
            val tag = when (unit) {
                "ОД" -> "$count ОД"
                "маны" -> "$count маны"
                else -> "$count вын."
            }
            if (tag !in tags) tags += tag
            val joiner = AND_MORE.find(after) ?: break
            at = amount.range.last + 1 + joiner.range.last + 1
        }
    }
    if (REACTION.containsMatchIn(benefit) && "реакция" !in tags) tags += "реакция"
    return tags
}
