package com.furybook.dubl.actions

import com.furybook.core.json.*

/** Strict embedded codec. It does not create a new independently persisted sidecar. */
internal fun encodeGameplayJournal(value: GameplayJournal, characterId: String): JsonValue.Obj {
    value.validate(characterId)
    return jsonObject("revision" to decimal(value.revision), "pending" to encodePending(value.pending),
        "lastCommittedStorageRevision" to (value.lastCommittedStorageRevision?.let(::decimal) ?: JsonValue.Null),
        "migration" to (value.migration?.let { jsonObject("operationId" to jsonString(it.operationId),
            "sourceRaw" to jsonString(it.sourceRaw), "sourceStorageRevision" to decimal(it.sourceStorageRevision)) } ?: JsonValue.Null),
        "receipts" to jsonArray(value.receipts.map { r -> jsonObject(
            "request" to encodeRequest(r.request), "beforeRevision" to decimal(r.beforeRevision),
            "afterRevision" to decimal(r.afterRevision), "enduranceBefore" to jsonNumber(r.enduranceBefore),
            "enduranceAfter" to jsonNumber(r.enduranceAfter), "pendingBefore" to encodePending(r.pendingBefore),
            "pendingAfter" to encodePending(r.pendingAfter), "weakBefore" to jsonBoolean(r.weakBefore),
            "weakAfter" to jsonBoolean(r.weakAfter),
            "resourceConsequencesKnownBefore" to jsonBoolean(r.resourceConsequencesKnownBefore)) })).let { root ->
                value.events?.let { JsonValue.Obj(root.values + ("events" to encodeGameEvents(it))) } ?: root
            }
}

internal fun decodeGameplayJournal(value: JsonValue, characterId: String): GameplayJournal {
    val fields = arrayOf("revision", "pending", "receipts", "lastCommittedStorageRevision", "migration")
    val hasEvents = (value as? JsonValue.Obj)?.values?.containsKey("events") == true
    val root = value.objectWith(*(if (hasEvents) fields + "events" else fields))
    val receipts = (root.field("receipts") as? JsonValue.Arr)?.values ?: error("Gameplay receipts must be an array")
    return GameplayJournal(root.long("revision"), decodePending(root.field("pending")), receipts.map { value ->
        val r = value.objectWith("request", "beforeRevision", "afterRevision", "enduranceBefore", "enduranceAfter",
            "pendingBefore", "pendingAfter", "weakBefore", "weakAfter", "resourceConsequencesKnownBefore")
        GameplayReceipt(decodeRequest(r.field("request")), r.long("beforeRevision"), r.long("afterRevision"),
            r.int("enduranceBefore"), r.int("enduranceAfter"), decodePending(r.field("pendingBefore")),
            decodePending(r.field("pendingAfter")), r.bool("weakBefore"), r.bool("weakAfter"),
            r.bool("resourceConsequencesKnownBefore"))
    }, if (root.field("lastCommittedStorageRevision") == JsonValue.Null) null else root.long("lastCommittedStorageRevision"),
        if (root.field("migration") == JsonValue.Null) null else root.field("migration")
            .objectWith("operationId", "sourceRaw", "sourceStorageRevision").let {
                GameplayMigrationProof(it.text("operationId"), it.text("sourceRaw"), it.long("sourceStorageRevision"))
            }, if (hasEvents) decodeGameEvents(root.field("events"), characterId) else null)
        .also { it.validate(characterId) }
}

private fun encodeRequest(value: GameplayRequest): JsonValue.Obj {
    val common = jsonObject("operationId" to jsonString(value.operationId), "characterId" to jsonString(value.characterId),
        "expectedRevision" to decimal(value.expectedRevision)).values
    val details = when (value) {
        is StartOverexertion -> jsonObject("type" to jsonString("StartOverexertion"), "attribute" to jsonString(value.attribute.name))
        is ResolvePhysicalCheck -> jsonObject("type" to jsonString("ResolvePhysicalCheck"),
            "startOperationId" to jsonString(value.startOperationId), "success" to jsonBoolean(value.result.success))
        is UndoGameplay -> jsonObject("type" to jsonString("UndoGameplay"),
            "targetOperationId" to jsonString(value.targetOperationId))
    }
    return JsonValue.Obj(common + details.values)
}

private fun decodeRequest(value: JsonValue): GameplayRequest {
    val root = value as? JsonValue.Obj ?: error("Gameplay request must be an object")
    val common = arrayOf("operationId", "characterId", "expectedRevision", "type")
    return when (root.text("type")) {
        "StartOverexertion" -> {
            root.objectWith(*common, "attribute")
            StartOverexertion(root.text("operationId"), root.text("characterId"), root.long("expectedRevision"),
                PhysicalAttribute.valueOf(root.text("attribute")))
        }
        "ResolvePhysicalCheck" -> {
            root.objectWith(*common, "startOperationId", "success")
            ResolvePhysicalCheck(root.text("operationId"), root.text("characterId"), root.long("expectedRevision"),
                root.text("startOperationId"), PhysicalCheckResult(root.bool("success")))
        }
        "UndoGameplay" -> {
            root.objectWith(*common, "targetOperationId")
            UndoGameplay(root.text("operationId"), root.text("characterId"), root.long("expectedRevision"),
                root.text("targetOperationId"))
        }
        else -> error("Unsupported gameplay request type")
    }
}

private fun encodePending(value: PendingPhysicalCheck?): JsonValue = value?.let { jsonObject(
    "startOperationId" to jsonString(it.startOperationId), "attribute" to jsonString(it.attribute.name),
    "ruleVersion" to jsonString(it.ruleVersion), "endurancePaid" to jsonNumber(it.endurancePaid),
    "advantageDice" to jsonNumber(it.advantageDice)) } ?: JsonValue.Null

private fun decodePending(value: JsonValue): PendingPhysicalCheck? {
    if (value == JsonValue.Null) return null
    val root = value.objectWith("startOperationId", "attribute", "ruleVersion", "endurancePaid", "advantageDice")
    return PendingPhysicalCheck(root.text("startOperationId"), PhysicalAttribute.valueOf(root.text("attribute")),
        root.text("ruleVersion"), root.int("endurancePaid"), root.int("advantageDice"))
}

private fun decimal(value: Long) = jsonString(value.toString())
private fun JsonValue.objectWith(vararg fields: String): JsonValue.Obj {
    val root = this as? JsonValue.Obj ?: error("Gameplay field must be an object")
    require(root.values.keys == fields.toSet()) { "Unknown or missing gameplay fields" }
    return root
}
private fun JsonValue.Obj.field(key: String) = values[key] ?: error("Missing $key")
private fun JsonValue.Obj.text(key: String) = (field(key) as? JsonValue.Str)?.value ?: error("$key must be text")
private fun JsonValue.Obj.bool(key: String) = (field(key) as? JsonValue.Bool)?.value ?: error("$key must be boolean")
private fun JsonValue.Obj.long(key: String): Long = text(key).let { raw ->
    raw.toLong().also { require(it >= 0 && it.toString() == raw) { "Invalid canonical revision" } }
}
private fun JsonValue.Obj.int(key: String): Int {
    val raw = (field(key) as? JsonValue.Num)?.raw ?: error("$key must be numeric")
    return raw.toInt().also { require(it >= 0 && it.toString() == raw) { "Invalid resource or modifier count" } }
}
