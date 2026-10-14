package com.furybook.dubl.actions

fun main() {
    var assertions = 0
    fun expect(value: Boolean) { check(value); assertions++ }
    val original = GameplayState("hero", 0, 3)
    val start = StartOverexertion("begin", "hero", 0, PhysicalAttribute.DEXTERITY)
    val active = (GameplayActions.start(original, start) as ActionTransition.Applied).state
    val pending = GameplayJournal.from(active)
    pending.validate("hero")
    expect(pending.pending == active.pending)
    expect(runCatching { pending.validate("other") }.isFailure)
    expect(runCatching { pending.copy(revision = 2).validate("hero") }.isFailure)
    expect(runCatching { pending.copy(pending = null).validate("hero") }.isFailure)
    val r = pending.receipts.single()
    expect(runCatching { pending.copy(receipts = listOf(r.copy(enduranceAfter = 3))).validate("hero") }.isFailure)
    expect(runCatching { pending.copy(receipts = listOf(r.copy(weakBefore = true))).validate("hero") }.isFailure)
    expect(runCatching { pending.copy(receipts = listOf(r.copy(resourceConsequencesKnownBefore = false))).validate("hero") }.isFailure)
    expect(runCatching { pending.copy(receipts = listOf(r.copy(afterRevision = 5))).validate("hero") }.isFailure)
    val finished = (GameplayActions.resolve(active, ResolvePhysicalCheck("result", "hero", 1,
        "begin", PhysicalCheckResult(true))) as ActionTransition.Applied).state
    val history = GameplayJournal.from(finished)
    history.validate("hero")
    expect(history.pending == null && history.revision == 2L)
    expect(runCatching { history.copy(receipts = history.receipts.reversed()).validate("hero") }.isFailure)
    expect(runCatching { history.copy(receipts = listOf(r, r)).validate("hero") }.isFailure)
    val mutable = history.receipts.toMutableList()
    val detached = history.copy(receipts = mutable)
    mutable.clear()
    expect(detached == history)
    val exposed = detached.receipts
    (exposed as? MutableList<GameplayReceipt>)?.clear()
    expect(detached == history)
    GameplayJournal().validate("hero")
    expect(runCatching { GameplayJournal(pending = active.pending).validate("hero") }.isFailure)
    println("GameplayJournalHarness: $assertions assertions passed")
}
