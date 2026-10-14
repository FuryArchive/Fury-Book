package com.furybook.dubl.actions

import com.furybook.core.json.*

fun main() {
    var assertions = 0
    fun expect(value: Boolean) { check(value); assertions++ }
    fun decode(raw: String) = decodeGameplayJournal(JsonReader(raw, strict = true).read(), "hero")
    val start = StartOverexertion("begin", "hero", 0, PhysicalAttribute.CONSTITUTION)
    val active = (GameplayActions.start(GameplayState("hero", 0, 3), start) as ActionTransition.Applied).state
    val journal = GameplayJournal.from(active)
    val raw = jsonStringify(encodeGameplayJournal(journal, "hero"))
    expect(decode(raw) == journal)
    expect(jsonStringify(encodeGameplayJournal(decode(raw), "hero")) == raw)
    expect(decode(jsonStringify(encodeGameplayJournal(GameplayJournal(), "hero"))) == GameplayJournal())
    for ((old, replacement) in listOf(
        "\"revision\":\"1\"" to "\"revision\":1",
        "\"revision\":\"1\"" to "\"revision\":\"01\"",
        "\"revision\":\"1\"" to "\"revision\":\"9223372036854775808\"",
        "\"revision\":\"1\"" to "\"revision\":\"1\",\"extra\":true",
        "\"revision\":\"1\"" to "\"revision\":\"1\",\"revision\":\"1\"",
        "StartOverexertion" to "FutureCommand",
        "CONSTITUTION" to "INTELLIGENCE",
        "\"endurancePaid\":1" to "\"endurancePaid\":0",
        "\"enduranceAfter\":2" to "\"enduranceAfter\":3",
        "\"weakBefore\":false" to "\"weakBefore\":true",
        "\"enduranceBefore\":3" to "\"enduranceBefore\":-1",
        "\"characterId\":\"hero\"" to "\"characterId\":\"other\"",
    )) {
        expect(raw.contains(old))
        expect(runCatching { decode(raw.replace(old, replacement)) }.isFailure)
    }
    val finished = (GameplayActions.resolve(active, ResolvePhysicalCheck("finish", "hero", 1,
        "begin", PhysicalCheckResult(false))) as ActionTransition.Applied).state
    val complete = GameplayJournal.from(finished)
    expect(decode(jsonStringify(encodeGameplayJournal(complete, "hero"))) == complete)
    expect(runCatching { decode(raw + "garbage") }.isFailure)
    println("GameplayJournalCodecHarness: $assertions assertions passed")
}
