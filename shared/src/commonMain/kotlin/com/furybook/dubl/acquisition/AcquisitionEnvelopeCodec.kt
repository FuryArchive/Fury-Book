package com.furybook.dubl.acquisition

import com.furybook.core.json.*
import com.furybook.dubl.data.CharacterTransferCodec
import com.furybook.dubl.data.SnapshotCodec
import com.furybook.dubl.data.validateLegacySnapshot
import com.furybook.dubl.model.*

/**
 * Stable UTF-8 JSON text: recursive key ordering by Kotlin String (UTF-16 code units), standard
 * escaping, array order retained. Number lexemes are retained exactly, never routed through Double.
 * Monetary/revision Longs in this format are canonical decimal strings. Numerically equivalent but
 * differently spelled JSON numbers are deliberately distinct tokens. Duplicate keys are rejected.
 */
fun canonicalJson(raw: String): String = canonicalText(JsonReader(raw, strict = true).read())
private fun canonicalText(value: JsonValue): String = buildString {
    // Escaping UTF-16 surrogate code units also preserves lone surrogates through UTF-8 encoding.
    jsonStringify(canonical(value)).forEach { ch ->
        if (ch.code in 0xD800..0xDFFF) append("\\u").append(ch.code.toString(16).uppercase()) else append(ch)
    }
}
private fun canonical(value: JsonValue): JsonValue = when (value) {
    is JsonValue.Obj -> JsonValue.Obj(value.values.keys.sorted().associateWith { canonical(value.values.getValue(it)) })
    is JsonValue.Arr -> JsonValue.Arr(value.values.map(::canonical))
    else -> value
}

fun encodeEnvelope(value: StoredCharacter): String {
    validate(value)
    val encoded = encodeState(value, local = true)
    require(value == decodeState(encoded)) { "Envelope serialization would lose stored values" }
    return canonicalText(encoded)
}

/** Wire projection has no recursive outbox, device URI, local CAS or sync/conflict bookkeeping. */
fun encodeSyncEnvelope(state: StoredCharacter): String {
    validate(state)
    require(state == decodeState(encodeState(state, local = true))) { "Envelope serialization would lose stored values" }
    return canonicalText(encodeState(state, local = false))
}

fun decodeEnvelope(raw: String): LoadResult = try {
    val root = JsonReader(raw, strict = true).read().obj()
    if (!root.has("formatVersion")) {
        if (root.has("schema")) {
            val schema = root.number("schema")
            if (schema !in 1..SnapshotCodec.SCHEMA) LoadResult.Unsupported(raw, "Unsupported legacy schema")
            else {
                validateLegacySnapshot(root, schema)
                LoadResult.MigrationRequired(raw)
            }
        } else LoadResult.Corrupt(raw, "Missing format version")
    } else if (root.number("formatVersion") != 1) LoadResult.Unsupported(raw, "Unsupported acquisition format")
    else {
        val state = decodeState(root)
        validate(state)
        LoadResult.Loaded(state)
    }
} catch (failure: UnsupportedEnvelope) {
    LoadResult.Unsupported(raw, failure.message ?: "Unknown required fields")
} catch (failure: Exception) {
    LoadResult.Corrupt(raw, failure.message ?: "Invalid acquisition envelope")
}

private fun encodeState(state: StoredCharacter, local: Boolean): JsonValue.Obj = jsonObject(
    "formatVersion" to jsonNumber(state.formatVersion),
    "characterId" to jsonString(state.character.id),
    "characterSnapshot" to parseRoot(SnapshotCodec.encode(AppSnapshot(listOf(state.character), state.character.id))),
    "extras" to encodeExtras(state.extras, local),
    "instances" to jsonArray(state.instances.map(::encodeInstance)),
    "ledger" to jsonObject("opening" to encodeOpening(state.ledger.opening), "entries" to jsonArray(state.ledger.entries.map(::encodeEntry))),
    "receipts" to jsonArray(state.receipts.map(::encodeReceipt)),
    "revision" to decimal(state.revision),
    "creationPhase" to jsonString(state.creationPhase.name),
    "storageRevision" to if (local) decimal(state.storageRevision) else null,
    "outbox" to if (local) jsonArray(state.outbox.map { item -> jsonObject(
        "operationId" to jsonString(item.operationId), "parentServerRevision" to decimal(item.parentServerRevision),
        "payloadCanonical" to jsonString(item.payloadCanonical)) }) else null,
    "syncMetadata" to if (local) jsonObject("serverRevision" to decimal(state.syncMetadata.serverRevision),
        "remoteConflictCanonical" to nullable(state.syncMetadata.remoteConflictCanonical)) else null,
)

private fun decodeState(root: JsonValue.Obj): StoredCharacter {
    root.keys("formatVersion", "characterId", "characterSnapshot", "extras", "instances", "ledger", "receipts",
        "revision", "creationPhase", "storageRevision", "outbox", "syncMetadata")
    val snapshot = root.objectRequired("characterSnapshot")
    if (snapshot.number("schema") != SnapshotCodec.SCHEMA) throw UnsupportedEnvelope("Embedded character schema")
    require(snapshot.list("characters").size == 1) { "Envelope requires exactly one character" }
    val character = SnapshotCodec.decodeEnvelopeProjection(snapshot)
    require(character.id == root.text("characterId") && snapshot.text("activeCharacterId") == character.id) { "Character scope mismatch" }
    // Parsing must preserve the entire projection, including manual deviations; never drop fields.
    require(canonicalJson(jsonStringify(snapshot)) == canonicalJson(SnapshotCodec.encode(AppSnapshot(listOf(character), character.id)))) {
        "Character projection would lose or normalize persisted fields"
    }
    val ledger = root.objectRequired("ledger").also { it.keys("opening", "entries") }
    val sync = root.objectRequired("syncMetadata").also { it.keys("serverRevision", "remoteConflictCanonical") }
    return StoredCharacter(character, decodeExtras(root.objectRequired("extras")), root.list("instances").map { decodeInstance(it.obj()) },
        Ledger(decodeOpening(ledger.objectRequired("opening")), ledger.list("entries").map { decodeEntry(it.obj()) }),
        root.list("receipts").map { decodeReceipt(it.obj()) }, root.list("outbox").map { value ->
            val item = value.obj().also { it.keys("operationId", "parentServerRevision", "payloadCanonical") }
            OutboxItem(item.text("operationId"), item.long("parentServerRevision"), item.text("payloadCanonical"))
        }, SyncMetadata(sync.long("serverRevision"), sync.optionalText("remoteConflictCanonical")),
        root.long("revision"), root.long("storageRevision"), root.phase("creationPhase"), root.number("formatVersion"))
}

private fun encodeOpening(opening: Opening): JsonValue.Obj = JsonValue.Obj(jsonObject(
    "migrationId" to jsonString(opening.migrationId), "sourceFingerprint" to jsonString(opening.sourceFingerprint),
    "balance" to amount(opening.balance), "legacyStage" to jsonString(opening.legacyStage.name),
    "provenance" to jsonString(opening.provenance.name), "reason" to nullable(opening.reason)).values +
    // Absence must remain absence for pre-T09 format1 outbox payload equality.
    (opening.legacyMigration?.let { mapOf("legacyMigration" to encodeLegacyMigration(it)) } ?: emptyMap()))
private fun decodeOpening(value: JsonValue.Obj): Opening {
    val required = arrayOf("migrationId", "sourceFingerprint", "balance", "legacyStage", "provenance", "reason")
    value.keys(*(if(value.has("legacyMigration")) required + "legacyMigration" else required))
    return Opening(value.text("migrationId"), value.text("sourceFingerprint"), value.amount("balance"),
        value.phase("legacyStage"), OpeningProvenance.valueOf(value.text("provenance")), value.optionalText("reason"),
        if(value.has("legacyMigration")) decodeLegacyMigration(value.objectRequired("legacyMigration")) else null)
}
private fun encodeLegacyMigration(value:LegacyMigrationProvenance):JsonValue.Obj = jsonObject(
    "origin" to jsonObject("characterId" to jsonString(value.origin.characterId), "schema" to jsonNumber(value.origin.schema),
        "sourceRevision" to jsonString(value.origin.sourceRevision), "raw" to jsonString(value.origin.raw),
        "activePackIds" to jsonArray(value.origin.activePackIds.map(::jsonString)),
        "ownershipMappings" to JsonValue.Obj(value.origin.ownershipMappings.mapValues { encodeDefinition(it.value) })).let { origin ->
            JsonValue.Obj(origin.values + (value.origin.extrasSource?.let { mapOf("extrasSource" to jsonObject("raw" to nullable(it.raw))) } ?: emptyMap()))
        },
    "calculatorBaseline" to jsonString(value.calculatorBaseline), "catalogIdentity" to nullable(value.catalogIdentity),
    "historyUnknown" to jsonBoolean(value.historyUnknown),
    "ownershipRecords" to jsonArray(value.ownershipRecords.map { record -> jsonObject(
        "ownershipKey" to jsonString(record.ownershipKey), "kind" to jsonString(record.kind.name), "rank" to jsonNumber(record.rank),
        "candidateEntryId" to nullable(record.candidateEntryId), "custom" to jsonBoolean(record.custom)) }))
private fun decodeLegacyMigration(value:JsonValue.Obj):LegacyMigrationProvenance {
    value.keys("origin","calculatorBaseline","catalogIdentity","historyUnknown","ownershipRecords")
    val origin=value.objectRequired("origin").also { it.keys(*(listOf("characterId","schema","sourceRevision","raw","activePackIds","ownershipMappings") + if(it.has("extrasSource")) listOf("extrasSource") else emptyList()).toTypedArray()) }
    return LegacyMigrationProvenance(LegacyOrigin(origin.text("characterId"),origin.number("schema"),origin.text("sourceRevision"),
        origin.text("raw"),origin.list("activePackIds").map { (it as? JsonValue.Str)?.value ?: error("Expected pack ID") },
        origin.objectRequired("ownershipMappings").values.mapValues { decodeDefinition(it.value.obj()) },
        if (origin.has("extrasSource")) LegacyExtrasSource(origin.objectRequired("extrasSource").also { it.keys("raw") }.optionalText("raw")) else null),
        value.text("calculatorBaseline"),value.optionalText("catalogIdentity"),
        (value.values["historyUnknown"] as? JsonValue.Bool)?.value ?: error("Expected history marker"),
        value.list("ownershipRecords").map { item ->
            val row=item.obj().also { it.keys("ownershipKey","kind","rank","candidateEntryId","custom") }
            LegacyOwnershipRecord(row.text("ownershipKey"),AcquisitionKind.valueOf(row.text("kind")),row.number("rank"),row.optionalText("candidateEntryId"),
                (row.values["custom"] as? JsonValue.Bool)?.value ?: error("Expected custom marker"))
        })
}
private fun encodeEntry(entry: LedgerEntry) = jsonObject(
    "operationId" to jsonString(entry.operationId), "type" to jsonString(entry.type.name),
    "changes" to jsonArray(entry.changes.map(::encodeChange)), "amountDelta" to amount(entry.amountDelta),
    "ruleVersions" to jsonObject("catalogRevision" to jsonString(entry.ruleVersions.catalogRevision),
        "resolutionRevision" to jsonString(entry.ruleVersions.resolutionRevision)),
    "reversesId" to nullable(entry.reversesId), "creationPhase" to jsonString(entry.creationPhase.name),
    "evidenceCanonical" to jsonString(entry.evidenceCanonical))
private fun decodeEntry(value: JsonValue.Obj): LedgerEntry {
    value.keys("operationId", "type", "changes", "amountDelta", "ruleVersions", "reversesId", "creationPhase", "evidenceCanonical")
    val versions = value.objectRequired("ruleVersions").also { it.keys("catalogRevision", "resolutionRevision") }
    return LedgerEntry(value.text("operationId"), LedgerEntryType.valueOf(value.text("type")),
        value.list("changes").map { decodeChange(it.obj()) }, value.amount("amountDelta"),
        RuleVersions(versions.text("catalogRevision"), versions.text("resolutionRevision")), value.optionalText("reversesId"),
        value.phase("creationPhase"), value.text("evidenceCanonical"))
}
private fun encodeChange(change: AcquisitionChange) = jsonObject(
    "kind" to jsonString(change.kind.name), "definition" to encodeDefinition(change.definition),
    "instanceId" to jsonString(change.instanceId), "fromRank" to jsonNumber(change.fromRank), "toRank" to jsonNumber(change.toRank),
    "parameters" to stringMap(change.parameters), "optionId" to nullable(change.optionId))
private fun decodeChange(value: JsonValue.Obj): AcquisitionChange {
    value.keys("kind", "definition", "instanceId", "fromRank", "toRank", "parameters", "optionId")
    return AcquisitionChange(AcquisitionKind.valueOf(value.text("kind")), decodeDefinition(value.objectRequired("definition")),
        value.text("instanceId"), value.number("fromRank"), value.number("toRank"), value.strings("parameters"), value.optionalText("optionId"))
}
private fun encodeDefinition(value: DefinitionRef) = jsonObject("packId" to jsonString(value.packId), "entryId" to jsonString(value.entryId))
private fun decodeDefinition(value: JsonValue.Obj): DefinitionRef {
    value.keys("packId", "entryId"); return DefinitionRef(value.text("packId"), value.text("entryId"))
}
private fun encodeInstance(value: AcquisitionInstance) = jsonObject(
    "instanceId" to jsonString(value.instanceId), "definition" to encodeDefinition(value.definition), "rank" to jsonNumber(value.rank),
    "parameters" to stringMap(value.parameters), "abilityOptionId" to nullable(value.abilityOptionId),
    "origin" to when (val origin = value.origin) {
        AcquisitionOrigin.New -> jsonObject("type" to jsonString("New"))
        is AcquisitionOrigin.Legacy -> jsonObject("type" to jsonString("Legacy"), "characterId" to jsonString(origin.characterId),
            "schema" to jsonString(origin.schema), "sourceRevision" to jsonString(origin.sourceRevision),
            "sourceFingerprint" to jsonString(origin.sourceFingerprint), "ownershipKey" to jsonString(origin.ownershipKey))
    })
private fun decodeInstance(value: JsonValue.Obj): AcquisitionInstance {
    value.keys("instanceId", "definition", "rank", "parameters", "abilityOptionId", "origin")
    val origin = value.objectRequired("origin")
    val decodedOrigin = when (origin.text("type")) {
        "New" -> { origin.keys("type"); AcquisitionOrigin.New }
        "Legacy" -> {
            origin.keys("type", "characterId", "schema", "sourceRevision", "sourceFingerprint", "ownershipKey")
            AcquisitionOrigin.Legacy(origin.text("characterId"), origin.text("schema"), origin.text("sourceRevision"),
                origin.text("sourceFingerprint"), origin.text("ownershipKey"))
        }
        else -> throw UnsupportedEnvelope("Unknown acquisition origin")
    }
    return AcquisitionInstance(value.text("instanceId"), decodeDefinition(value.objectRequired("definition")), value.number("rank"),
        value.strings("parameters"), value.optionalText("abilityOptionId"), decodedOrigin)
}
private fun encodeReceipt(value: Receipt) = jsonObject(
    "operationId" to jsonString(value.operationId), "requestCanonical" to jsonString(value.requestCanonical),
    "result" to jsonObject("cost" to amount(value.result.cost), "remaining" to amount(value.result.remaining),
        "changes" to jsonArray(value.result.changes.map(::encodeChange))), "status" to jsonString(value.status.name),
    "beforeRevision" to decimal(value.beforeRevision), "afterRevision" to decimal(value.afterRevision))
private fun decodeReceipt(value: JsonValue.Obj): Receipt {
    value.keys("operationId", "requestCanonical", "result", "status", "beforeRevision", "afterRevision")
    val result = value.objectRequired("result").also { it.keys("cost", "remaining", "changes") }
    return Receipt(value.text("operationId"), value.text("requestCanonical"), SavedAcquisitionResult(result.amount("cost"),
        result.amount("remaining"), result.list("changes").map { decodeChange(it.obj()) }), ReceiptStatus.valueOf(value.text("status")),
        value.long("beforeRevision"), value.long("afterRevision"))
}

private fun encodeExtras(value: CharacterSheetExtras, local: Boolean): JsonValue.Obj = JsonValue.Obj(
    CharacterTransferCodec.encodeExtras(value).values + mapOf(
        "noteEntries" to jsonArray(value.noteEntries.map { jsonObject("id" to jsonString(it.id), "title" to jsonString(it.title), "body" to jsonString(it.body)) }),
    ) + if (local) mapOf("portraitUri" to nullable(value.portraitUri)) else emptyMap())
private fun decodeExtras(value: JsonValue.Obj): CharacterSheetExtras {
    val notes = value.list("noteEntries").map { item ->
        val note = item.obj().also { it.keys("id", "title", "body") }
        CharacterNote(note.text("id"), note.text("title"), note.text("body"))
    }
    val extras = CharacterTransferCodec.decodeExtras(value).copy(portraitUri = value.optionalText("portraitUri"), noteEntries = notes)
    require(canonicalJson(jsonStringify(value)) == canonicalJson(jsonStringify(encodeExtras(extras, local = true)))) {
        "Extras would lose or normalize persisted fields"
    }
    return extras
}

private fun validate(state: StoredCharacter) {
    require(state.formatVersion == 1) { "Unsupported format" }
    require(state.character.id.isNotBlank() && state.character.ruleset == DublRuleset.reference) { "Invalid character scope/ruleset" }
    require(state.revision >= 0 && state.storageRevision >= 0 && state.syncMetadata.serverRevision >= 0) { "Invalid revisions" }
    require(state.character.creationComplete == (state.creationPhase == RequirementNode.CreationPhase.Phase.Finalized)) { "Creation phase mismatch" }
    require(state.ledger.opening.migrationId.isNotBlank() && state.ledger.opening.sourceFingerprint.isNotBlank()) { "Opening requires provenance" }
    require(state.ledger.opening.provenance != OpeningProvenance.UserConfirmed || !state.ledger.opening.reason.isNullOrBlank()) { "Confirmed funding requires reason" }
    state.ledger.opening.legacyMigration?.let { provenance ->
        val opening=state.ledger.opening
        val original=validateLegacyOrigin(provenance.origin)
        require(provenance.ownershipRecords==legacyOwnershipRecords(original)) { "Legacy ownership evidence differs from original backup" }
        require(original.creationComplete==(opening.legacyStage==RequirementNode.CreationPhase.Phase.Finalized)) { "Legacy stage mismatch" }
        require(provenance.origin.characterId==state.character.id && provenance.origin.sourceFingerprint==opening.sourceFingerprint) { "Legacy backup scope/fingerprint mismatch" }
        require(legacyMigrationId(provenance.origin)==opening.migrationId) { "Legacy migration identity mismatch" }
        require(provenance.historyUnknown && provenance.calculatorBaseline==LEGACY_ECONOMY_BASELINE) { "Unknown legacy history/baseline" }
        require(opening.provenance!=OpeningProvenance.NewCharacter) { "New character cannot have legacy provenance" }
        if (opening.provenance==OpeningProvenance.LegacyCalculated) {
            val expected=legacyCatalogIdentity(provenance.origin.activePackIds)
            require(expected!=null && provenance.catalogIdentity==expected) { "Calculated migration needs its pinned catalog identity" }
        } else require(provenance.catalogIdentity==null) { "Confirmed migration cannot claim a calculated catalog" }
        state.instances.filter { it.origin is AcquisitionOrigin.Legacy }.forEach { instance ->
            val origin=instance.origin as AcquisitionOrigin.Legacy
            require(origin.characterId==provenance.origin.characterId && origin.schema==provenance.origin.schema.toString() &&
                origin.sourceRevision==provenance.origin.sourceRevision && origin.sourceFingerprint==opening.sourceFingerprint) { "Legacy instance provenance mismatch" }
            val record=provenance.ownershipRecords.singleOrNull { it.ownershipKey==origin.ownershipKey }
                ?: error("Legacy instance lacks original ownership evidence")
            require(record.candidateEntryId==null || record.candidateEntryId==instance.definition.entryId) { "Legacy instance differs from original candidate ID" }
            if(record.kind==AcquisitionKind.SCHOOL) require(isLegacySchoolRef(instance.definition)) { "Invalid resolved school identity" }
            provenance.origin.ownershipMappings[origin.ownershipKey]?.let { require(instance.definition==it) { "Legacy mapping differs from its instance" } }
            require(instance.instanceId==legacyInstanceId(instance.definition,origin)) { "Legacy instance identity mismatch" }
        }
    }
    require(state.instances.map { it.instanceId }.distinct().size == state.instances.size) { "Duplicate instance ID" }
    state.instances.forEach { require(it.origin !is AcquisitionOrigin.Legacy || it.origin.characterId == state.character.id) { "Legacy instance scope mismatch" } }
    val entries = state.ledger.entries
    require(entries.map { it.operationId }.distinct().size == entries.size && entries.all { it.operationId.isNotBlank() }) { "Duplicate/blank ledger operation ID" }
    require(state.receipts.map { it.operationId }.distinct().size == state.receipts.size) { "Duplicate receipt ID" }
    require(state.receipts.map { it.operationId }.toSet() == entries.map { it.operationId }.toSet()) { "Ledger/receipt mismatch" }
    val receipts = state.receipts.associateBy { it.operationId }
    var revision = 0L
    var remaining = state.ledger.opening.balance
    val reversed = mutableSetOf<String>()
    val seen = mutableMapOf<String, LedgerEntry>()
    for (entry in entries) {
        val receipt = receipts.getValue(entry.operationId)
        require(revision < Long.MAX_VALUE && receipt.beforeRevision == revision && receipt.afterRevision == revision + 1) { "Broken receipt revision chain" }
        revision = receipt.afterRevision
        requireCanonical(entry.evidenceCanonical)
        require(entry.ruleVersions.catalogRevision.isNotBlank() && entry.ruleVersions.resolutionRevision.isNotBlank()) { "Missing rule versions" }
        val request = requireCanonical(receipt.requestCanonical).obj()
        require(request.text("characterId") == state.character.id) { "Receipt character scope mismatch" }
        remaining += entry.amountDelta
        require(receipt.result.cost == Amount.ZERO - entry.amountDelta && receipt.result.remaining == remaining && receipt.result.changes == entry.changes) { "Receipt financial result mismatch" }
        entry.changes.forEach { change ->
            require(change.instanceId.isNotBlank() && change.parameters.all { it.key.isNotBlank() && it.value.isNotBlank() } &&
                (change.optionId == null || change.optionId.isNotBlank())) { "Invalid change identity" }
        }
        if (entry.type == LedgerEntryType.Undo) {
            val original = seen[entry.reversesId] ?: throw IllegalArgumentException("Undo must reference a preceding entry")
            require(original.type != LedgerEntryType.Undo && reversed.add(original.operationId)) { "Duplicate or chained reversal" }
            require(entry.amountDelta == Amount.ZERO - original.amountDelta && receipts.getValue(original.operationId).status == ReceiptStatus.Undone) { "Invalid reversal amount/status" }
        } else require(entry.reversesId == null) { "Only Undo can reverse an entry" }
        seen[entry.operationId] = entry
    }
    require(revision == state.revision) { "Truncated or future receipt chain" }
    state.receipts.forEach { require((it.status == ReceiptStatus.Undone) == (it.operationId in reversed)) { "Undone receipt lacks reversal" } }
    require(state.outbox.map { it.operationId }.distinct().size == state.outbox.size) { "Duplicate outbox ID" }
    state.outbox.forEach { item ->
        require(item.operationId in receipts && item.parentServerRevision >= 0) { "Invalid outbox operation" }
        val payload = validateSyncPayload(item.payloadCanonical, state.character.id)
        require(payload.long("revision") <= state.revision) { "Future outbox revision" }
        if (payload.long("revision") == state.revision) {
            require(item.payloadCanonical == canonicalText(encodeState(state, local = false))) {
                "Current-revision outbox differs from committed projection"
            }
        }
        val payloadReceipts = payload.list("receipts").map { decodeReceipt(it.obj()) }
        require(payloadReceipts.any { it.operationId == item.operationId }) { "Outbox payload lacks its operation" }
        val payloadLedger = payload.objectRequired("ledger")
        require(canonicalText(payloadLedger.values.getValue("opening")) == canonicalText(encodeOpening(state.ledger.opening))) { "Outbox opening differs" }
        val payloadEntries = payloadLedger.list("entries").map { decodeEntry(it.obj()) }
        require(payloadEntries.size <= entries.size && payloadEntries.indices.all {
            canonicalText(encodeEntry(payloadEntries[it])) == canonicalText(encodeEntry(entries[it]))
        }) { "Outbox ledger is not a saved prefix" }
        payloadReceipts.forEach { saved ->
            val current = receipts.getValue(saved.operationId)
            require(canonicalText(encodeReceipt(saved.copy(status = current.status))) == canonicalText(encodeReceipt(current))) {
                "Operation ID reused with different payload"
            }
            require(saved.status != ReceiptStatus.Undone || current.status == ReceiptStatus.Undone) { "Outbox Undo status regressed" }
        }
    }
    state.syncMetadata.remoteConflictCanonical?.let { validateSyncPayload(it, state.character.id) }
}

/** Validate nested wire structure without recursively accepting local bookkeeping. */
private fun validateSyncPayload(raw: String, characterId: String): JsonValue.Obj {
    val root = requireCanonical(raw).obj()
    require(root.text("characterId") == characterId) { "Sync payload character scope mismatch" }
    require(!root.has("outbox") && !root.has("storageRevision") && !root.has("syncMetadata")) { "Local metadata in sync payload" }
    val extras = root.objectRequired("extras")
    require(!extras.has("portraitUri")) { "Device metadata in sync payload" }
    val local = JsonValue.Obj(root.values + mapOf("outbox" to jsonArray(emptyList()), "storageRevision" to decimal(root.long("revision")),
        "syncMetadata" to jsonObject("serverRevision" to decimal(0), "remoteConflictCanonical" to JsonValue.Null),
        "extras" to JsonValue.Obj(extras.values + ("portraitUri" to JsonValue.Null))))
    validate(decodeState(local))
    return root
}
private fun requireCanonical(raw: String): JsonValue {
    require(raw == canonicalJson(raw)) { "Expected canonical JSON" }
    return JsonReader(raw, strict = true).read()
}
private class UnsupportedEnvelope(message: String) : IllegalArgumentException(message)
private fun decimal(value: Long) = jsonString(value.toString())
private fun nullable(value: String?): JsonValue = value?.let(::jsonString) ?: JsonValue.Null
private fun amount(value: Amount) = parseRoot(value.toJson())
private fun stringMap(value: Map<String, String>) = JsonValue.Obj(value.mapValues { jsonString(it.value) })
private fun JsonValue.obj() = this as? JsonValue.Obj ?: throw IllegalArgumentException("Expected object")
private fun JsonValue.Obj.keys(vararg expected: String) {
    if (values.keys.any { it !in expected }) throw UnsupportedEnvelope("Unknown required fields")
    require(values.keys.containsAll(expected.toList())) { "Missing required fields" }
}
private fun JsonValue.Obj.text(key: String): String = (values[key] as? JsonValue.Str)?.value ?: throw IllegalArgumentException("Expected string: $key")
private fun JsonValue.Obj.optionalText(key: String): String? = if (values[key] == JsonValue.Null) null else text(key)
private fun JsonValue.Obj.objectRequired(key: String) = values[key]?.obj() ?: throw IllegalArgumentException("Missing object: $key")
private fun JsonValue.Obj.list(key: String) = (values[key] as? JsonValue.Arr)?.values ?: throw IllegalArgumentException("Expected array: $key")
private fun JsonValue.Obj.number(key: String): Int = (values[key] as? JsonValue.Num)?.raw?.toIntOrNull() ?: throw IllegalArgumentException("Expected integer: $key")
private fun JsonValue.Obj.long(key: String): Long {
    val raw = text(key)
    require(Regex("0|[1-9][0-9]*").matches(raw)) { "Invalid unsigned decimal: $key" }
    return raw.toLongOrNull() ?: throw IllegalArgumentException("Revision overflow: $key")
}
private fun JsonValue.Obj.amount(key: String): Amount {
    val value = objectRequired(key).also { it.keys("xp", "ap") }
    return Amount.fromJson(jsonStringify(value))
}
private fun JsonValue.Obj.phase(key: String) = RequirementNode.CreationPhase.Phase.valueOf(text(key))
private fun JsonValue.Obj.strings(key: String): Map<String, String> = objectRequired(key).values.mapValues { (_, value) ->
    (value as? JsonValue.Str)?.value ?: throw IllegalArgumentException("Expected string parameter")
}
