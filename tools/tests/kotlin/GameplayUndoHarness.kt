package com.furybook.dubl.actions

fun main() {
    var assertions = 0
    fun expect(value: Boolean) { check(value); assertions++ }
    val initial = GameplayState("hero", 0, 1)
    val start = StartOverexertion("start", "hero", 0, PhysicalAttribute.STRENGTH)
    val active = (GameplayActions.start(initial, start) as ActionTransition.Applied).state
    val undo = UndoGameplay("undo", "hero", 1, "start")
    val restored = (GameplayActions.undo(active, undo) as ActionTransition.Applied).state
    expect(restored.endurance == 1 && !restored.weak && restored.pending == null)
    expect(restored.revision == 2L && restored.receipts.size == 2)
    expect((GameplayActions.start(restored, start) as ActionTransition.Replayed).undone)
    expect(GameplayActions.undo(restored, undo) is ActionTransition.Replayed)
    expect(GameplayActions.undo(restored, undo.copy(operationId = "again", expectedRevision = 2)) is ActionTransition.Blocked)
    expect(GameplayActions.undo(restored, undo.copy(operationId = "undo-undo", expectedRevision = 2, targetOperationId = "undo")) is ActionTransition.Blocked)
    expect(GameplayActions.undo(active.copy(endurance = 3), undo) is ActionTransition.Blocked)
    expect(GameplayActions.undo(active.copy(weak = false), undo) is ActionTransition.Blocked)
    expect(GameplayActions.undo(active.copy(resourceConsequencesKnown = false), undo) is ActionTransition.Blocked)
    expect(GameplayActions.undo(active, undo.copy(expectedRevision = 0)) is ActionTransition.Blocked)
    expect(GameplayActions.undo(active, undo.copy(targetOperationId = "unknown")) is ActionTransition.Blocked)
    val finish = ResolvePhysicalCheck("finish", "hero", 1, "start", PhysicalCheckResult(true))
    val complete = (GameplayActions.resolve(active, finish) as ActionTransition.Applied).state
    expect(GameplayActions.undo(complete, undo.copy(expectedRevision = 2)) is ActionTransition.Blocked)
    val reverseResult = UndoGameplay("undo-result", "hero", 2, "finish")
    val reopened = (GameplayActions.undo(complete, reverseResult) as ActionTransition.Applied).state
    expect(reopened.pending == active.pending && reopened.endurance == 0)
    expect((GameplayActions.resolve(reopened, finish) as ActionTransition.Replayed).undone)
    expect(GameplayActions.resolve(reopened, finish.copy(operationId = "new-result", expectedRevision = 3)) is ActionTransition.Applied)
    GameplayJournal.from(restored).validate("hero")
    GameplayJournal.from(reopened).validate("hero")
    expect(runCatching { GameplayJournal.from(restored).copy(receipts = restored.receipts.map {
        if (it.request is UndoGameplay) it.copy(enduranceAfter = 2) else it
    }).validate("hero") }.isFailure)
    println("GameplayUndoHarness: $assertions assertions passed")
}
