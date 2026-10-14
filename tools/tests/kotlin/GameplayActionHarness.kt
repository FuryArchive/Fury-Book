package com.furybook.dubl.actions

/** Executes the real pure transition implementation, not a parallel Python model. */
fun main() {
    var assertions = 0
    fun expect(value: Boolean) { check(value); assertions++ }
    val start = StartOverexertion("start-1", "hero", 0, PhysicalAttribute.STRENGTH)
    val initial = GameplayState("hero", 0, 3)
    val preview = GameplayActions.preview(initial, start)
    expect(preview.allowed && preview.enduranceCost == 1 && preview.advantageDice == 1)
    expect(initial.endurance == 3 && initial.pending == null && initial.receipts.isEmpty())
    val begun = GameplayActions.start(initial, start) as ActionTransition.Applied
    expect(begun.state.endurance == 2 && begun.state.revision == 1L)
    expect(begun.state.pending?.ruleVersion == GameplayActions.OVEREXERTION_VERSION)
    expect(GameplayActions.start(begun.state, start) is ActionTransition.Replayed)
    expect(GameplayActions.start(begun.state, start.copy(attribute = PhysicalAttribute.SPEED)) is ActionTransition.Blocked)
    expect(GameplayActions.start(initial.copy(revision = 1), start) is ActionTransition.Blocked)
    expect(GameplayActions.start(initial.copy(endurance = 0), start) is ActionTransition.Blocked)
    expect(GameplayActions.start(initial.copy(weak = true), start) is ActionTransition.Blocked)
    expect(GameplayActions.start(initial.copy(resourceConsequencesKnown = false), start) is ActionTransition.Blocked)
    expect(GameplayActions.start(initial, start.copy(characterId = "other")) is ActionTransition.Blocked)
    expect(GameplayActions.start(begun.state, start.copy(operationId = "start-2", expectedRevision = 1)) is ActionTransition.Blocked)
    expect(GameplayActions.start(initial, start.copy(operationId = "")) is ActionTransition.Blocked)
    val finish = ResolvePhysicalCheck("finish-1", "hero", 1, "start-1", PhysicalCheckResult(success = false))
    val finished = GameplayActions.resolve(begun.state, finish) as ActionTransition.Applied
    expect(finished.state.endurance == 2 && finished.state.pending == null)
    expect(finished.state.revision == 2L && finished.state.receipts.size == 2)
    expect(GameplayActions.resolve(finished.state, finish) is ActionTransition.Replayed)
    expect(GameplayActions.resolve(finished.state, finish.copy(result = PhysicalCheckResult(true))) is ActionTransition.Blocked)
    expect(GameplayActions.resolve(initial, finish.copy(expectedRevision = 0)) is ActionTransition.Blocked)
    expect(GameplayActions.resolve(begun.state, finish.copy(startOperationId = "wrong")) is ActionTransition.Blocked)
    expect(GameplayActions.resolve(begun.state, finish.copy(expectedRevision = 0)) is ActionTransition.Blocked)
    expect(GameplayActions.resolve(begun.state, finish.copy(operationId = "start-1")) is ActionTransition.Blocked)
    val lastPoint = GameplayActions.start(initial.copy(endurance = 1), start) as ActionTransition.Applied
    expect(lastPoint.state.endurance == 0 && lastPoint.state.weak)
    expect(GameplayActions.resolve(lastPoint.state, finish) is ActionTransition.Applied)
    for (attribute in PhysicalAttribute.entries) {
        expect(GameplayActions.start(initial, start.copy(attribute = attribute)) is ActionTransition.Applied)
    }
    expect(PhysicalAttribute.entries.size == 4)
    // Replay is checked before current revision/resource constraints; it never charges twice.
    expect(GameplayActions.start(finished.state.copy(endurance = 0, weak = true), start) is ActionTransition.Replayed)
    expect(GameplayActions.resolve(finished.state.copy(revision = 30), finish) is ActionTransition.Replayed)
    expect(runCatching { initial.copy(endurance = -1) }.isFailure)
    expect(runCatching { initial.copy(revision = -1) }.isFailure)
    println("GameplayActionHarness: $assertions assertions passed")
}
