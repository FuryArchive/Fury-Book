package com.furybook.dubl.actions

/** Canonical typed history. Only implemented source-specific commands can activate an effect. */
class GameplayEventLog(val characterId: String, commands: List<RecordGameBoundary> = emptyList()) {
    private val saved = commands.toList()
    val commands get() = saved.toList()
    val timeline: GameplayTimeline
    init {
        var replay = GameplayTimeline(characterId)
        for (command in saved) {
            val result = GameplayEvents.apply(replay, command)
            require(result is BoundaryTransition.Applied) { "Invalid or duplicate boundary history" }
            replay = result.timeline
        }
        timeline = replay
    }
    override fun equals(other: Any?) = other is GameplayEventLog && characterId == other.characterId && saved == other.saved
    override fun hashCode() = 31 * characterId.hashCode() + saved.hashCode()
}
