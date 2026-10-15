package com.furybook.dubl.actions

import com.furybook.core.json.*

internal fun encodeGameEvents(log: GameplayEventLog): JsonValue = jsonArray(log.commands.map { command ->
    val event = command.boundary
    val common = jsonObject("operationId" to jsonString(command.operationId), "characterId" to jsonString(command.characterId),
        "expectedRevision" to jsonString(command.expectedRevision.toString()), "boundaryId" to jsonString(event.boundaryId)).values
    val details = when (event) {
        is GameBoundary.TurnStarted -> jsonObject("type" to jsonString("TurnStarted"), "actorId" to jsonString(event.actorId))
        is GameBoundary.TurnEnded -> jsonObject("type" to jsonString("TurnEnded"), "actorId" to jsonString(event.actorId), "startedBoundaryId" to jsonString(event.startedBoundaryId))
        is GameBoundary.RoundEnded -> jsonObject("type" to jsonString("RoundEnded"))
        is GameBoundary.SceneEnded -> jsonObject("type" to jsonString("SceneEnded"))
        is GameBoundary.RestCompleted -> jsonObject("type" to jsonString("RestCompleted"), "kind" to jsonString(event.kind.name), "minutes" to jsonNumber(event.minutes))
        is GameBoundary.ActionPointsSpent -> jsonObject("type" to jsonString("ActionPointsSpent"), "count" to jsonNumber(event.count))
        is GameBoundary.ReactionSpent -> jsonObject("type" to jsonString("ReactionSpent"))
        is GameBoundary.DefenseReserved -> jsonObject("type" to jsonString("DefenseReserved"), "count" to jsonNumber(event.count))
    }
    JsonValue.Obj(common + details.values)
})
internal fun decodeGameEvents(value: JsonValue, characterId: String): GameplayEventLog {
    val values = (value as? JsonValue.Arr)?.values ?: error("Events must be an array")
    return GameplayEventLog(characterId, values.map { value ->
        val root = value as? JsonValue.Obj ?: error("Event must be an object")
        fun text(key: String) = (root.values[key] as? JsonValue.Str)?.value ?: error("Event $key must be text")
        val type = text("type")
        val extra = when (type) {
            "TurnStarted" -> setOf("actorId")
            "TurnEnded" -> setOf("actorId", "startedBoundaryId")
            "RoundEnded", "SceneEnded", "ReactionSpent" -> emptySet()
            "ActionPointsSpent", "DefenseReserved" -> setOf("count")
            "RestCompleted" -> setOf("kind", "minutes")
            else -> error("Unsupported event type")
        }
        require(root.values.keys == setOf("operationId", "characterId", "expectedRevision", "boundaryId", "type") + extra) { "Unknown or missing event fields" }
        val revisionText = text("expectedRevision")
        val revision = revisionText.toLong().also { require(it >= 0 && it.toString() == revisionText) }
        fun positiveInt(key: String): Int {
            val raw = (root.values[key] as? JsonValue.Num)?.raw ?: error("Event $key must be numeric")
            return raw.toInt().also { require(it > 0 && it.toString() == raw) }
        }
        val boundaryId = text("boundaryId")
        val event = when (type) {
            "TurnStarted" -> GameBoundary.TurnStarted(boundaryId, text("actorId"))
            "TurnEnded" -> GameBoundary.TurnEnded(boundaryId, text("actorId"), text("startedBoundaryId"))
            "RoundEnded" -> GameBoundary.RoundEnded(boundaryId)
            "SceneEnded" -> GameBoundary.SceneEnded(boundaryId)
            "ActionPointsSpent" -> GameBoundary.ActionPointsSpent(boundaryId, positiveInt("count"))
            "ReactionSpent" -> GameBoundary.ReactionSpent(boundaryId)
            "DefenseReserved" -> GameBoundary.DefenseReserved(boundaryId, positiveInt("count"))
            "RestCompleted" -> {
                val raw = (root.values["minutes"] as? JsonValue.Num)?.raw ?: error("Rest minutes must be numeric")
                val minutes = raw.toInt().also { require(it > 0 && it.toString() == raw) }
                GameBoundary.RestCompleted(boundaryId, RestKind.valueOf(text("kind")), minutes)
            }
            else -> error("Unsupported event type")
        }
        RecordGameBoundary(text("operationId"), text("characterId"), revision, event)
    })
}
