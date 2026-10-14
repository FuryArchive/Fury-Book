package com.furybook.dubl.actions

fun main() {
    var count = 0
    fun expect(value: Boolean) { check(value); count++ }
    fun effect(id: String, expiry: EffectExpiry) = TimedEffectInstance(id, "fixture-policy:$id", 0, expiry)
    val originalEffects = mutableListOf(
        effect("next-start", EffectExpiry.NextOwnTurnStart(0)),
        effect("next-end", EffectExpiry.OwnTurnEnd(1)),
        effect("two-rounds", EffectExpiry.RoundEnds(0, 2)),
        effect("scene", EffectExpiry.SceneEnd),
        effect("indefinite", EffectExpiry.Indefinite),
        effect("unknown", EffectExpiry.Unresolved("P126", "Sprint anchor is unresolved")))
    var state = GameplayTimeline("hero", effects = originalEffects)
    originalEffects.clear(); expect(state.effects.size == 6)
    fun request(event: GameBoundary) = RecordGameBoundary("op:${event.boundaryId}", "hero", state.revision, event)
    fun apply(event: GameBoundary): BoundaryTransition.Applied {
        val outcome = GameplayEvents.apply(state, request(event)) as BoundaryTransition.Applied
        state = outcome.timeline; return outcome
    }
    expect(GameplayEvents.apply(state, request(GameBoundary.RestCompleted("invalid", RestKind.BRIEF, 0))) is BoundaryTransition.Blocked)
    expect(state.revision == 0L && state.effects.size == 6)
    val other = apply(GameBoundary.TurnStarted("other-1", "other"))
    expect(state.ownTurnOrdinal == 0L && other.receipt.expiredEffectIds.isEmpty())
    expect(other.receipt.blockedEffectIds == listOf("unknown"))
    expect(GameplayEvents.apply(state, request(GameBoundary.TurnStarted("hero-too-soon", "hero"))) is BoundaryTransition.Blocked)
    expect(GameplayEvents.apply(state, request(GameBoundary.TurnEnded("bad-end", "hero", "other-1"))) is BoundaryTransition.Blocked)
    apply(GameBoundary.TurnEnded("other-end", "other", "other-1"))
    val ownCommand = request(GameBoundary.TurnStarted("own-1", "hero"))
    val own = GameplayEvents.apply(state, ownCommand) as BoundaryTransition.Applied; state = own.timeline
    expect(own.receipt.expiredEffectIds == listOf("next-start") && state.ownTurnOrdinal == 1L)
    expect(state.effects.any { it.instanceId == "next-end" })
    expect(GameplayEvents.apply(state, ownCommand) is BoundaryTransition.Replayed)
    expect(GameplayEvents.apply(state, ownCommand.copy(operationId = "different-id", expectedRevision = state.revision)) is BoundaryTransition.Blocked)
    expect(GameplayEvents.apply(state, ownCommand.copy(boundary = GameBoundary.TurnStarted("different-boundary", "hero"))) is BoundaryTransition.Blocked)
    val laterEffect = TimedEffectInstance("later", "fixture-next-start", state.revision, EffectExpiry.NextOwnTurnStart(1))
    state = GameplayTimeline(state.characterId, state.revision, state.ownTurnOrdinal, state.roundOrdinal, state.openTurn, state.effects + laterEffect, state.receipts)
    expect(GameplayEvents.apply(state, ownCommand) is BoundaryTransition.Replayed && state.effects.contains(laterEffect))
    val end = apply(GameBoundary.TurnEnded("own-end", "hero", "own-1"))
    expect(end.receipt.expiredEffectIds == listOf("next-end") && state.effects.contains(laterEffect))
    val rest = apply(GameBoundary.RestCompleted("rest", RestKind.FULL, 480))
    expect(rest.receipt.expiredEffectIds.isEmpty() && state.ownTurnOrdinal == 1L)
    val round = apply(GameBoundary.RoundEnded("round-1"))
    expect(round.receipt.expiredEffectIds.isEmpty() && state.roundOrdinal == 1L)
    val second = apply(GameBoundary.RoundEnded("round-2"))
    expect(second.receipt.expiredEffectIds == listOf("two-rounds") && state.roundOrdinal == 2L)
    val scene = apply(GameBoundary.SceneEnded("scene-end"))
    expect(scene.receipt.expiredEffectIds == listOf("scene"))
    expect(state.effects.map { it.instanceId }.toSet() == setOf("indefinite", "unknown", "later"))
    val next = apply(GameBoundary.TurnStarted("own-2", "hero"))
    expect(next.receipt.expiredEffectIds == listOf("later") && state.ownTurnOrdinal == 2L)
    expect(state.effects.map { it.instanceId }.toSet() == setOf("indefinite", "unknown"))
    expect(GameplayEvents.apply(state, request(GameBoundary.SceneEnded("stale")).copy(expectedRevision = 0)) is BoundaryTransition.Blocked)
    expect(GameplayEvents.apply(state, request(GameBoundary.SceneEnded("wrong-owner")).copy(characterId = "other")) is BoundaryTransition.Blocked)
    expect(GameplayEvents.apply(state, request(GameBoundary.SceneEnded("empty-id")).copy(operationId = "")) is BoundaryTransition.Blocked)
    val receiptCopy = state.receipts.last().expiredEffectIds
    runCatching { (receiptCopy as? MutableList<String>)?.clear() }
    expect(state.receipts.last().expiredEffectIds == listOf("later"))
    expect(runCatching { GameplayTimeline("hero", ownTurnOrdinal = 1) }.isFailure)
    expect(runCatching { GameplayTimeline(state.characterId, state.revision, state.ownTurnOrdinal, state.roundOrdinal,
        state.openTurn, state.effects + laterEffect, state.receipts) }.isFailure)
    expect(runCatching { GameplayTimeline(state.characterId, state.revision, state.ownTurnOrdinal, state.roundOrdinal,
        state.openTurn, state.effects + TimedEffectInstance("retroactive", "fixture", state.revision, EffectExpiry.NextOwnTurnStart(0)), state.receipts) }.isFailure)
    println("GameplayEventsHarness: $count assertions passed; pure lifecycle only")
}
