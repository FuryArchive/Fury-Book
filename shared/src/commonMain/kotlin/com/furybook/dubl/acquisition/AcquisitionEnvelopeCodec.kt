package com.furybook.dubl.acquisition

import com.furybook.core.json.*
import com.furybook.dubl.data.CharacterTransferCodec
import com.furybook.dubl.data.SnapshotCodec
import com.furybook.dubl.data.validateLegacySnapshot
import com.furybook.dubl.model.*
import com.furybook.dubl.actions.*

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

/** Internal finite staging only. Public durable/wire codecs still validate the committed tip. */
internal fun encodeProjectionEnvelope(state: StoredCharacter): String {
    require(state.outbox.isEmpty()) { "Staging must omit pending transport payloads" }
    validate(state,committedTip=false)
    val encoded=encodeState(state,local=true)
    require(state==decodeState(encoded)) { "Projection would lose stored values" }
    return canonicalText(encoded)
}
internal fun decodeProjectionEnvelope(raw: String): StoredCharacter {
    val state=decodeState(JsonReader(raw,strict=true).read().obj())
    require(state.outbox.isEmpty()) { "Staging must omit pending transport payloads" }
    validate(state,committedTip=false)
    return state
}

/** Wire projection has no recursive outbox, device URI, local CAS or sync/conflict bookkeeping. */
fun encodeSyncEnvelope(state: StoredCharacter): String {
    require(state.formatVersion == 1 && state.gameplay == null) { "Gameplay format is local-only; cloud writer is not enabled" }
    validate(state)
    require(state == decodeState(encodeState(state, local = true))) { "Envelope serialization would lose stored values" }
    return canonicalText(encodeState(state, local = false))
}

fun decodeEnvelope(raw: String, allowGameplayFormat: Boolean = false): LoadResult = try {
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
    } else if (root.number("formatVersion") !in 1..2 || (root.number("formatVersion") == 2 && !allowGameplayFormat))
        LoadResult.Unsupported(raw, "Unsupported or disabled canonical format")
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

private fun encodeState(state: StoredCharacter, local: Boolean): JsonValue.Obj = JsonValue.Obj(jsonObject(
    "formatVersion" to jsonNumber(state.formatVersion),
    "characterId" to jsonString(state.character.id),
    "characterSnapshot" to SnapshotCodec.encodeAcquisitionProjection(state.character),
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
        "remoteConflictCanonical" to nullable(state.syncMetadata.remoteConflictCanonical)).let { metadata -> JsonValue.Obj(metadata.values +
            (state.syncMetadata.pendingResolution?.let { mapOf("pendingResolution" to encodePendingResolution(it)) } ?: emptyMap())) } else null,
 ).values + (if (state.copySources.isEmpty()) emptyMap() else mapOf("copySources" to jsonArray(state.copySources.map { source ->
    jsonObject("sourceCharacterId" to jsonString(source.sourceCharacterId), "destinationCharacterId" to jsonString(source.destinationCharacterId),
        "importOperationId" to jsonString(source.importOperationId), "sourceCanonical" to jsonString(source.sourceCanonical))
}))) + (if(state.syncProofs.isEmpty()) emptyMap() else mapOf("syncProofs" to jsonArray(state.syncProofs.map(::encodeSyncProof)))) +
    (state.gameplay?.let { mapOf("gameplay" to encodeGameplayJournal(it, state.character.id)) } ?: emptyMap()))

private fun decodeState(root: JsonValue.Obj): StoredCharacter {
    root.keys("formatVersion", "characterId", "characterSnapshot", "extras", "instances", "ledger", "receipts",
        "revision", "creationPhase", "storageRevision", "outbox", "syncMetadata", *((if(root.has("copySources")) listOf("copySources") else emptyList()) + (if(root.has("syncProofs")) listOf("syncProofs") else emptyList()) +
            (if(root.number("formatVersion") == 2) listOf("gameplay") else emptyList())).toTypedArray())
    val snapshot = root.objectRequired("characterSnapshot")
    if (snapshot.number("schema") != SnapshotCodec.SCHEMA) throw UnsupportedEnvelope("Embedded character schema")
    require(snapshot.list("characters").size == 1) { "Envelope requires exactly one character" }
    val character = SnapshotCodec.decodeEnvelopeProjection(snapshot)
    require(character.id == root.text("characterId") && snapshot.text("activeCharacterId") == character.id) { "Character scope mismatch" }
    // Parsing must preserve the entire projection, including manual deviations; never drop fields.
    require(canonicalJson(jsonStringify(snapshot)) == canonicalJson(jsonStringify(SnapshotCodec.encodeAcquisitionProjection(character)))) {
        "Character projection would lose or normalize persisted fields"
    }
    val ledger = root.objectRequired("ledger").also { it.keys("opening", "entries") }
    val sync = root.objectRequired("syncMetadata").also { it.keys("serverRevision", "remoteConflictCanonical", *if(it.has("pendingResolution")) arrayOf("pendingResolution") else emptyArray()) }
    return StoredCharacter(character, decodeExtras(root.objectRequired("extras")), root.list("instances").map { decodeInstance(it.obj()) },
        Ledger(decodeOpening(ledger.objectRequired("opening")), ledger.list("entries").map { decodeEntry(it.obj()) }),
        root.list("receipts").map { decodeReceipt(it.obj()) }, root.list("outbox").map { value ->
            val item = value.obj().also { it.keys("operationId", "parentServerRevision", "payloadCanonical") }
            OutboxItem(item.text("operationId"), item.long("parentServerRevision"), item.text("payloadCanonical"))
        }, SyncMetadata(sync.long("serverRevision"), sync.optionalText("remoteConflictCanonical"),if(sync.has("pendingResolution")) decodePendingResolution(sync.objectRequired("pendingResolution")) else null),
        root.long("revision"), root.long("storageRevision"), root.phase("creationPhase"), root.number("formatVersion"),
        if(root.has("copySources")) root.list("copySources").map { value ->
            val source=value.obj().also { it.keys("sourceCharacterId","destinationCharacterId","importOperationId","sourceCanonical") }
            AcquisitionCopySource(source.text("sourceCharacterId"),source.text("destinationCharacterId"),source.text("importOperationId"),source.text("sourceCanonical"))
        } else emptyList(),if(root.has("syncProofs")) root.list("syncProofs").map { decodeSyncProof(it.obj()) } else emptyList(),
        if (root.number("formatVersion") == 2) decodeGameplayJournal(root.objectRequired("gameplay"), character.id) else null)
}

private fun encodeSyncProof(value:AcquisitionSyncProof)=jsonObject("proofId" to jsonString(value.proofId),
    "payloadCanonical" to jsonString(value.payloadCanonical),"proofIds" to jsonArray(value.proofIds.map(::jsonString)))
private fun decodeSyncProof(value:JsonValue.Obj):AcquisitionSyncProof {
    value.keys("proofId","payloadCanonical","proofIds")
    return AcquisitionSyncProof(value.text("proofId"),value.text("payloadCanonical"),value.list("proofIds").map { (it as? JsonValue.Str)?.value ?: error("Expected proof ID") })
}
private fun encodePendingResolution(value:PendingAcquisitionResolution)=jsonObject("localCanonical" to jsonString(value.localCanonical),
    "serverCanonical" to jsonString(value.serverCanonical),"request" to jsonObject("characterId" to jsonString(value.request.characterId),
        "operationId" to jsonString(value.request.operationId),"expectedServerRevision" to decimal(value.request.expectedServerRevision),
        "choice" to jsonString(value.request.choice.name),"archiveId" to jsonString(value.request.archiveId),"selectedCanonical" to jsonString(value.request.selectedCanonical)))
private fun decodePendingResolution(value:JsonValue.Obj):PendingAcquisitionResolution {
    value.keys("localCanonical","serverCanonical","request")
    val request=value.objectRequired("request").also { it.keys("characterId","operationId","expectedServerRevision","choice","archiveId","selectedCanonical") }
    return PendingAcquisitionResolution(AcquisitionResolution(request.text("characterId"),request.text("operationId"),request.long("expectedServerRevision"),
        LocalOrServer.valueOf(request.text("choice")),request.text("archiveId"),request.text("selectedCanonical")),value.text("localCanonical"),value.text("serverCanonical"))
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

private fun validate(state: StoredCharacter, committedTip: Boolean = true, sharedProofs:SyncProofContext?=null) {
    val proofs=sharedProofs ?: SyncProofContext(state.syncProofs)
    proofs.verifyMembership(state.syncProofs)
    var historical: StoredCharacter? = null
    var previousDestination: String? = null
    var importId: String? = null
    state.copySources.forEach { source ->
        require(source.sourceCharacterId.isNotBlank() && source.destinationCharacterId.isNotBlank() && source.importOperationId.isNotBlank() &&
            source.sourceCharacterId != source.destinationCharacterId) { "Invalid copy identity" }
        require(previousDestination == null || source.sourceCharacterId == previousDestination) { "Broken copy ancestry" }
        val root=wireRoot(source.sourceCanonical,source.sourceCharacterId)
        require(!root.has("copySources") && !root.has("syncProofs")) { "Nested copy ancestry/proof pool" }
        val original=decodeWireState(root).copy(syncProofs=state.syncProofs)
        validateState(original,historical,importId,true,proofs)
        historical=original
        previousDestination=source.destinationCharacterId
        importId=source.importOperationId
    }
    require(previousDestination == null || previousDestination == state.character.id) { "Copy destination scope mismatch" }
    validateState(state,historical,importId,committedTip,proofs)
}
private fun validateState(state: StoredCharacter, historical: StoredCharacter?, importId: String?, committedTip: Boolean, proofs:SyncProofContext) {
    if(historical!=null) {
        require(state.ledger.opening==historical.ledger.opening && state.ledger.entries.size>historical.ledger.entries.size &&
            state.ledger.entries.take(historical.ledger.entries.size)==historical.ledger.entries) { "Copied historical ledger differs" }
        require(state.receipts.take(historical.receipts.size).map { old -> old.copy(status=historical.receipts.single { it.operationId==old.operationId }.status) } == historical.receipts) { "Copied historical receipts differ" }
        val boundary=state.ledger.entries[historical.ledger.entries.size]
        require(boundary.operationId==importId && boundary.type==LedgerEntryType.Import && boundary.amountDelta==Amount.ZERO && boundary.changes.isEmpty()) { "Copy requires separate import operation" }
        val proof=requireCanonical(boundary.evidenceCanonical).obj()
        val receipt=state.receipts.single { it.operationId==boundary.operationId }
        val fingerprint=legacySourceFingerprint(canonicalText(JsonValue.Obj(encodeState(historical,false).values - "copySources" - "syncProofs")))
        val importRequest=canonicalText(jsonObject("type" to jsonString("Import"),"characterId" to jsonString(state.character.id),
            "sourceCharacterId" to jsonString(historical.character.id),"sourceFingerprint" to jsonString(fingerprint)))
        require(receipt.requestCanonical==importRequest) { "Import receipt scope/provenance differs" }
        proof.keys("schema","sourceCharacterId","sourceFingerprint")
        require(proof.text("schema")=="copy-import-v1" && proof.text("sourceCharacterId")==historical.character.id &&
            proof.text("sourceFingerprint")==fingerprint) { "Import provenance differs" }
    }
    require(state.formatVersion in 1..2) { "Unsupported format" }
    if (state.formatVersion == 1) require(state.gameplay == null) { "Gameplay requires explicit format migration" }
    else {
        checkNotNull(state.gameplay) { "Format 2 requires gameplay history" }.validate(state.character.id)
        val gameplayOperationIds = state.gameplay.receipts.map { it.request.operationId }.toSet() +
            state.gameplay.events?.commands.orEmpty().map { it.operationId } + listOfNotNull(state.gameplay.migration?.operationId)
        require(state.receipts.none { it.operationId in gameplayOperationIds }) { "Acquisition and gameplay operation IDs collide" }
        require(state.gameplay.revision <= state.storageRevision) { "Action history exceeds storage revision" }
        require(state.gameplay.events?.commands?.size?.toLong()?.let { it <= state.storageRevision - state.gameplay.revision } != false) { "Combined gameplay history exceeds storage revision" }
        require(state.gameplay.lastCommittedStorageRevision?.let { it <= state.storageRevision } != false) {
            "Gameplay history claims a future storage commit"
        }
        require(state.outbox.isEmpty() && state.syncMetadata == SyncMetadata(0) && state.syncProofs.isEmpty()) {
            "Gameplay format has no enabled cloud transport"
        }
    }
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
        require((provenance.origin.characterId==state.character.id || historical?.ledger?.opening==opening) && provenance.origin.sourceFingerprint==opening.sourceFingerprint) { "Legacy backup scope/fingerprint mismatch" }
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
    state.instances.forEach { require(it.origin !is AcquisitionOrigin.Legacy || (it.origin.characterId == state.character.id || inheritedCopiedHolding(state,historical,it,committedTip) ||
        copiedLegacyAdmission(state,historical,it,committedTip))) { "Legacy instance scope mismatch" } }
    if(historical!=null) {
        // A fresh captured confirmation is also a baseline once recorded, even if now omitted.
        state.ledger.entries.drop(historical.ledger.entries.size+1).filter { it.type==LedgerEntryType.ConfirmLegacyParameter }.forEach { entry ->
            val proof=requireCanonical(entry.evidenceCanonical).obj()
            val prior=proof.objectRequired("before").list("instances").map { decodeInstance(it.obj()) }
            val added=proof.objectRequired("after").list("instances").map { decodeInstance(it.obj()) }.filter { next ->
                next.origin is AcquisitionOrigin.Legacy && next.origin.characterId!=state.character.id && prior.none { it.instanceId==next.instanceId }
            }
            added.forEach { captured ->
                val current=state.instances.singleOrNull { it.instanceId==captured.instanceId }
                require(copiedLegacyAdmission(state,historical,current ?: captured,committedTip,missing=current==null)) { "Confirmed copied holding omitted without recorded inverse" }
            }
        }
    }
    historical?.instances?.filter { it.origin is AcquisitionOrigin.Legacy }?.forEach { captured ->
        val current=state.instances.singleOrNull { it.instanceId==captured.instanceId }
        require(inheritedCopiedHolding(state,historical,current ?: captured,committedTip,missing=current==null)) { "Copied holding removed without exact recorded proof" }
    }
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
        val inherited=historical?.ledger?.entries?.any { it == entry } == true
        if(entry.type==LedgerEntryType.Import) require(inherited || (historical!=null && entry.operationId==importId)) { "Import lacks captured source provenance" }
        if(entry.type==LedgerEntryType.SyncAdoption && inherited) {
            require(receipt.beforeRevision>=revision) { "Copied adoption revision regressed" }
        } else if(entry.type==LedgerEntryType.SyncAdoption) {
            val proof=requireCanonical(entry.evidenceCanonical).obj()
            val branches=adoptionBranches(proof,proofs,state.syncProofs.map { it.proofId }.toSet())
            val selected=branches.selected
            val displaced=branches.displaced
            val server=branches.server
            val choice=LocalOrServer.valueOf(proof.text("choice"))
            val selectedCanonical=if(choice==LocalOrServer.Local) branches.displacedCanonical else branches.serverCanonical
            val expectedServerRevision=proof.long("expectedServerRevision")
            require(branches.selectedCanonical==selectedCanonical && displaced.character.id==state.character.id && server.character.id==state.character.id &&
                proof.text("archiveId")==acquisitionArchiveId(state.character.id,expectedServerRevision,branches.displacedCanonical,branches.serverCanonical) &&
                selected.character.id==state.character.id && selected.revision==revision &&
                selected.ledger.opening==state.ledger.opening && selected.ledger.entries==seen.values.toList() &&
                selected.receipts==state.receipts.take(seen.size).map { current -> current.copy(status=selected.receipts.single { it.operationId==current.operationId }.status) } &&
                entry.amountDelta==Amount.ZERO && entry.changes.isEmpty() &&
                receipt.beforeRevision==maxOf(revision,displaced.revision)) { "Invalid sync adoption boundary" }
            require(receipt.requestCanonical==canonicalText(jsonObject("type" to jsonString("SyncAdoption"),
                "characterId" to jsonString(state.character.id),"expectedRevision" to decimal(displaced.revision),
                "expectedServerRevision" to decimal(expectedServerRevision),"choice" to jsonString(choice.name),
                "archiveId" to jsonString(proof.text("archiveId"))))) { "Adoption receipt differs from explicit selection" }
        } else require(receipt.beforeRevision == revision) { "Broken receipt revision chain" }
        require(receipt.beforeRevision < Long.MAX_VALUE && receipt.afterRevision == receipt.beforeRevision + 1) { "Broken receipt revision chain" }
        revision = receipt.afterRevision
        requireCanonical(entry.evidenceCanonical)
        require(entry.ruleVersions.catalogRevision.isNotBlank() && entry.ruleVersions.resolutionRevision.isNotBlank()) { "Missing rule versions" }
        val request = requireCanonical(receipt.requestCanonical).obj()
        require(request.text("characterId") == state.character.id || historical?.receipts?.any { old ->
            old.operationId==receipt.operationId && old.requestCanonical==receipt.requestCanonical } == true) { "Receipt character scope mismatch" }
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
    val tip=entries.lastOrNull()
    if(committedTip && tip?.type==LedgerEntryType.SyncAdoption && historical?.ledger?.entries?.any { it==tip } != true) {
        val proof=requireCanonical(tip.evidenceCanonical).obj()
        val selected=adoptionBranches(proof,proofs,state.syncProofs.map { it.proofId }.toSet()).selected
        require(state.character==selected.character && state.instances==selected.instances &&
            state.extras.copy(portraitUri=null)==selected.extras.copy(portraitUri=null) && state.creationPhase==selected.creationPhase) { "Adoption projection differs from selected whole branch" }
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
        require(payloadReceipts.lastOrNull()?.operationId==item.operationId && payloadReceipts.last().afterRevision==payload.long("revision")) { "Outbox must identify its captured tip" }
        val payloadLedger = payload.objectRequired("ledger")
        require(canonicalText(payloadLedger.values.getValue("opening")) == canonicalText(encodeOpening(state.ledger.opening))) { "Outbox opening differs" }
        val payloadEntries = payloadLedger.list("entries").map { decodeEntry(it.obj()) }
        require(payloadEntries.lastOrNull()?.operationId==item.operationId && payloadEntries.last().type!=LedgerEntryType.SyncAdoption) { "Ordinary outbox cannot represent resolution" }
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
    state.syncMetadata.pendingResolution?.let { pending ->
        validateResolutionRequest(pending.request)
        require(pending.request.characterId==state.character.id && decodeSyncEnvelope(pending.localCanonical).character.id==state.character.id &&
            decodeSyncEnvelope(pending.serverCanonical).character.id==state.character.id && pending.request.archiveId==acquisitionArchiveId(state.character.id,
                pending.request.expectedServerRevision,pending.localCanonical,pending.serverCanonical)) { "Pending resolution scope/archive differs" }
        val frozen=decodeSyncEnvelope(pending.request.selectedCanonical)
        val proof=requireCanonical(frozen.ledger.entries.last().evidenceCanonical).obj()
        val branches=adoptionBranches(proof,SyncProofContext(frozen.syncProofs))
        require(branches.displacedCanonical==pending.localCanonical && branches.serverCanonical==pending.serverCanonical) { "Pending resolution branch differs" }
    }
}

/** A missing mapped instance in a copy is admitted only by a fresh, exact typed confirmation.
 * The captured original opening/ownership backup is still independently validated above. */
private fun copiedLegacyAdmission(state:StoredCharacter,historical:StoredCharacter?,instance:AcquisitionInstance,committedTip:Boolean,missing:Boolean=false):Boolean {
    val source=historical ?: return false
    val provenance=source.ledger.opening.legacyMigration ?: return false
    val origin=instance.origin as? AcquisitionOrigin.Legacy ?: return false
    if(state.ledger.opening!=source.ledger.opening || origin.characterId!=provenance.origin.characterId ||
        origin.schema!=provenance.origin.schema.toString() || origin.sourceRevision!=provenance.origin.sourceRevision ||
        origin.sourceFingerprint!=provenance.origin.sourceFingerprint || instance.instanceId!=legacyInstanceId(instance.definition,origin)) return false
    val record=provenance.ownershipRecords.singleOrNull { !it.custom && it.ownershipKey==origin.ownershipKey } ?: return false
    if(record.candidateEntryId!=null && record.candidateEntryId!=instance.definition.entryId) return false
    // Only internal, empty-outbox staging may represent the pending confirmation before its receipt.
    if(!committedTip && instance.rank==record.rank) return true
    return state.ledger.entries.drop(source.ledger.entries.size+1).any { entry ->
        if(entry.type!=LedgerEntryType.ConfirmLegacyParameter || entry.amountDelta!=Amount.ZERO || entry.changes.isNotEmpty()) return@any false
        try {
            val receipt=state.receipts.single { it.operationId==entry.operationId }
            val request=requireCanonical(receipt.requestCanonical).obj()
            request.keys("type","characterId","expectedRevision","reason","payload")
            require(request.text("type")=="CanonicalMutation" && request.text("characterId")==state.character.id &&
                request.long("expectedRevision")==receipt.beforeRevision && request.text("reason").isNotBlank())
            val payload=request.objectRequired("payload").also { it.keys("kind","instanceId","parameters","resolution") }
            require(payload.text("kind")=="ConfirmLegacyParameter" && payload.text("instanceId")==instance.instanceId)
            val resolution=payload.objectRequired("resolution").also { it.keys("ownershipKey","packId","entryId","rank") }
            require(resolution.text("ownershipKey")==record.ownershipKey && resolution.number("rank")==record.rank &&
                resolution.text("packId")==instance.definition.packId && resolution.text("entryId")==instance.definition.entryId)
            val proof=requireCanonical(entry.evidenceCanonical).obj().also { it.keys("schema","command","before","after") }
            require(proof.text("schema")=="mutation-evidence-v1" && proof.values["command"]==request)
            val before=proof.objectRequired("before").also { it.keys("characterSnapshot","instances","extras","creationPhase") }
            val after=proof.objectRequired("after").also { it.keys("characterSnapshot","instances","extras","creationPhase") }
            require(before.values.filterKeys { it!="instances" }==after.values.filterKeys { it!="instances" })
            val old=before.list("instances").map { decodeInstance(it.obj()) }
            val next=after.list("instances").map { decodeInstance(it.obj()) }
            require(old.none { it.instanceId==instance.instanceId })
            val added=next.single { it.instanceId==instance.instanceId }
            require(next.filterNot { it.instanceId==instance.instanceId }==old && added.definition==instance.definition &&
                added.origin==origin && added.rank==record.rank && added.parameters==payload.strings("parameters"))
            require(copiedHoldingChain(state,added,record.kind,state.ledger.entries.indexOf(entry)+1,if(missing)null else instance,committedTip))
            true
        } catch (_:Exception) { false }
    }
}

private fun inheritedCopiedHolding(state:StoredCharacter,source:StoredCharacter?,instance:AcquisitionInstance,committedTip:Boolean,missing:Boolean=false):Boolean {
    val captured=source?.instances?.singleOrNull { it.instanceId==instance.instanceId } ?: return false
    val origin=captured.origin as? AcquisitionOrigin.Legacy ?: return false
    val kind=source.ledger.opening.legacyMigration?.ownershipRecords?.singleOrNull { it.ownershipKey==origin.ownershipKey }?.kind ?: return false
    return try { copiedHoldingChain(state,captured,kind,source.ledger.entries.size+1,if(missing)null else instance,committedTip) } catch (_:Exception) { false }
}
/** The captured holding is a baseline, never permission to change rank/parameters/option. */
private fun copiedHoldingChain(state:StoredCharacter,initial:AcquisitionInstance,kind:AcquisitionKind,start:Int,
    instance:AcquisitionInstance?,committedTip:Boolean):Boolean {
    var expected:AcquisitionInstance?=initial
    for(later in state.ledger.entries.drop(start)) {
        val changes=later.changes.filter { it.instanceId==initial.instanceId }
        if(changes.isNotEmpty()) {
            require(later.type==LedgerEntryType.Purchase || later.type==LedgerEntryType.Undo)
            changes.forEach { change ->
                val prior=requireNotNull(expected) { "Paid change lacks preceding holding" }
                require(change.kind==kind && change.definition==prior.definition && change.parameters==prior.parameters &&
                    change.optionId==prior.abilityOptionId && change.fromRank==prior.rank && change.toRank>0)
                expected=prior.copy(rank=change.toRank)
            }
        } else if(later.type==LedgerEntryType.ManualCorrection || later.type==LedgerEntryType.ConfirmLegacyParameter) {
            expected=recordedCopiedHoldingMutation(state,later,expected,initial,reverse=false)
        } else if(later.type==LedgerEntryType.Undo) {
            val reversed=state.ledger.entries.single { it.operationId==later.reversesId }
            if(reversed.type==LedgerEntryType.ManualCorrection || reversed.type==LedgerEntryType.ConfirmLegacyParameter)
                expected=recordedCopiedHoldingMutation(state,reversed,expected,initial,reverse=true)
        }
    }
    // Only detached pre-receipt staging may contain the prospective next holding.
    return if(committedTip) instance==expected else instance==null || (instance.instanceId==initial.instanceId &&
        instance.definition==initial.definition && instance.origin==initial.origin)
}

private fun recordedCopiedHoldingMutation(state:StoredCharacter,entry:LedgerEntry,current:AcquisitionInstance?,identity:AcquisitionInstance,reverse:Boolean):AcquisitionInstance? {
    val receipt=state.receipts.single { it.operationId==entry.operationId }
    val request=requireCanonical(receipt.requestCanonical).obj().also { it.keys("type","characterId","expectedRevision","reason","payload") }
    require(request.text("type")=="CanonicalMutation" && request.text("characterId")==state.character.id &&
        request.long("expectedRevision")==receipt.beforeRevision && request.text("reason").isNotBlank())
    val payload=request.objectRequired("payload")
    require(payload.text("kind")==entry.type.name)
    if(entry.type==LedgerEntryType.ManualCorrection) require(payload.amount("delta")==entry.amountDelta)
    else require(entry.amountDelta==Amount.ZERO)
    val proof=requireCanonical(entry.evidenceCanonical).obj().also { it.keys("schema","command","before","after") }
    require(proof.text("schema")=="mutation-evidence-v1" && proof.values["command"]==request)
    val beforeProjection=proof.objectRequired("before").also { it.keys("characterSnapshot","instances","extras","creationPhase") }
    val afterProjection=proof.objectRequired("after").also { it.keys("characterSnapshot","instances","extras","creationPhase") }
    if(entry.type==LedgerEntryType.ManualCorrection) {
        payload.keys("kind","patch","delta")
        val patch=payload.objectRequired("patch").also { it.keys("projection","replacesInstances","replacesExtras") }
        val projection=patch.objectRequired("projection").also { it.keys("characterSnapshot","instances","extras","creationPhase") }
        fun replacing(key:String)=(patch.values[key] as? JsonValue.Bool)?.value ?: error("Expected patch replacement flag")
        require(afterProjection.values["characterSnapshot"]==projection.values["characterSnapshot"] &&
            afterProjection.values["creationPhase"]==projection.values["creationPhase"] &&
            beforeProjection.values["creationPhase"]==afterProjection.values["creationPhase"])
        for((field,flag) in listOf("instances" to "replacesInstances","extras" to "replacesExtras"))
            require(afterProjection.values[field]==(if(replacing(flag))projection.values[field] else beforeProjection.values[field])) { "Correction differs from captured patch" }
    } else {
        payload.keys("kind","instanceId","parameters","resolution")
        if(payload.text("instanceId")==identity.instanceId) {
            require(beforeProjection.values.filterKeys { it!="instances" }==afterProjection.values.filterKeys { it!="instances" })
            val beforeInstances=beforeProjection.list("instances").map { decodeInstance(it.obj()) }
            val afterInstances=afterProjection.list("instances").map { decodeInstance(it.obj()) }
            val old=beforeInstances.singleOrNull { it.instanceId==identity.instanceId }
            val next=afterInstances.single { it.instanceId==identity.instanceId }
            require(afterInstances.filterNot { it.instanceId==identity.instanceId }==beforeInstances.filterNot { it.instanceId==identity.instanceId } &&
                next.parameters==payload.strings("parameters"))
            if(old!=null) require(payload.values["resolution"]==JsonValue.Null && next==old.copy(parameters=next.parameters) && old.parameters.all { next.parameters[it.key]==it.value })
            else {
                val origin=identity.origin as AcquisitionOrigin.Legacy
                val record=state.ledger.opening.legacyMigration!!.ownershipRecords.single { it.ownershipKey==origin.ownershipKey }
                val resolution=payload.objectRequired("resolution").also { it.keys("ownershipKey","packId","entryId","rank") }
                require(resolution.text("ownershipKey")==record.ownershipKey && resolution.text("packId")==identity.definition.packId &&
                    resolution.text("entryId")==identity.definition.entryId && resolution.number("rank")==record.rank && next.rank==record.rank)
            }
        } else {
            require(beforeProjection.list("instances").map { decodeInstance(it.obj()) }.singleOrNull { it.instanceId==identity.instanceId }==
                afterProjection.list("instances").map { decodeInstance(it.obj()) }.singleOrNull { it.instanceId==identity.instanceId }) { "Unrelated confirmation changed holding" }
        }
    }
    fun holding(key:String):AcquisitionInstance? {
        val projection=proof.objectRequired(key).also { it.keys("characterSnapshot","instances","extras","creationPhase") }
        return projection.list("instances").map { decodeInstance(it.obj()) }.singleOrNull { it.instanceId==identity.instanceId }
    }
    val before=holding(if(reverse) "after" else "before")
    val after=holding(if(reverse) "before" else "after")
    require(before==current && (after==null || (after.instanceId==identity.instanceId && after.definition==identity.definition && after.origin==identity.origin)))
    require(reverse || entry.type!=LedgerEntryType.ManualCorrection || current!=null || after==null) { "Correction cannot create a removed holding" }
    return after
}

/** Fingerprint uses the same existing SHA-256 UTF-16BE identity, not an external trust claim. */
internal fun acquisitionSyncProofId(payloadCanonical:String,proofIds:List<String>):String = "sync-proof:" + legacySourceFingerprint(canonicalText(
    jsonObject("payloadCanonical" to jsonString(payloadCanonical),"proofIds" to jsonArray(proofIds.map(::jsonString)))))
internal fun captureSyncProof(state:StoredCharacter):AcquisitionSyncProof {
    val root=requireCanonical(encodeSyncEnvelope(state)).obj()
    val flat=canonicalText(JsonValue.Obj(root.values-"syncProofs"))
    val ids=state.syncProofs.map { it.proofId }
    return AcquisitionSyncProof(acquisitionSyncProofId(flat,ids),flat,ids)
}
internal fun combineSyncProofs(records:List<AcquisitionSyncProof>):List<AcquisitionSyncProof> {
    val unique=linkedMapOf<String,AcquisitionSyncProof>()
    records.forEach { record ->
        require(unique[record.proofId]?.let { it==record } != false) { "Proof identity collision" }
        unique[record.proofId]=record
    }
    return unique.values.sortedBy { it.proofId }
}
private class SyncProofContext(records:List<AcquisitionSyncProof>) {
    private val index=records.associateBy { it.proofId }
    private val resolved=mutableMapOf<String,StoredCharacter>()
    private val resolving=mutableSetOf<String>()
    init {
        require(index.size==records.size && records.map { it.proofId }==records.map { it.proofId }.sorted()) { "Duplicate/unsorted proof pool" }
        records.forEach { require(it.proofId==acquisitionSyncProofId(it.payloadCanonical,it.proofIds)) { "Proof content identity mismatch" } }
    }
    fun verifyMembership(records:List<AcquisitionSyncProof>) {
        require(records.map { it.proofId }==records.map { it.proofId }.distinct().sorted()) { "Duplicate/unsorted proof membership" }
        records.forEach { require(index[it.proofId]==it) { "Proof pool content differs" };resolve(it.proofId) }
    }
    fun resolve(id:String):StoredCharacter {
        resolved[id]?.let { return it }
        val record=index[id] ?: error("Missing referenced proof")
        require(resolving.add(id)) { "Cyclic sync proof" }
        try {
            require(record.proofIds==record.proofIds.distinct().sorted()) { "Invalid prior proof membership" }
            val root=requireCanonical(record.payloadCanonical).obj()
            require(!root.has("syncProofs")) { "Recursive proof pool" }
            val state=decodeWireState(wireRoot(record.payloadCanonical,root.text("characterId"))).copy(
                syncProofs=record.proofIds.map { index[it] ?: error("Missing prior proof") })
            validate(state,sharedProofs=this)
            require(canonicalText(JsonValue.Obj(encodeState(state,false).values-"syncProofs"))==record.payloadCanonical) { "Proof would lose values" }
            resolved[id]=state
            return state
        } finally { resolving.remove(id) }
    }
    fun canonical(id:String):String=canonicalText(encodeState(resolve(id),false))
}
private data class AdoptionBranches(val selected:StoredCharacter,val displaced:StoredCharacter,val server:StoredCharacter,
    val selectedCanonical:String,val displacedCanonical:String,val serverCanonical:String)
private fun adoptionBranches(proof:JsonValue.Obj,proofs:SyncProofContext,available:Set<String>?=null):AdoptionBranches {
    return when(proof.text("schema")) {
        "sync-adoption-v1" -> {
            proof.keys("schema","selectedCanonical","displacedCanonical","serverCanonical","expectedServerRevision","choice","archiveId")
            val selected=proof.text("selectedCanonical");val displaced=proof.text("displacedCanonical");val server=proof.text("serverCanonical")
            AdoptionBranches(decodeSyncEnvelope(selected),decodeSyncEnvelope(displaced),decodeSyncEnvelope(server),selected,displaced,server)
        }
        "sync-adoption-v2" -> {
            proof.keys("schema","selectedProofId","displacedProofId","serverProofId","expectedServerRevision","choice","archiveId")
            fun id(key:String)=proof.text(key).also { require(available==null || it in available) { "Branch proof outside captured membership" } }
            val selected=id("selectedProofId");val displaced=id("displacedProofId");val server=id("serverProofId")
            AdoptionBranches(proofs.resolve(selected),proofs.resolve(displaced),proofs.resolve(server),proofs.canonical(selected),proofs.canonical(displaced),proofs.canonical(server))
        }
        else -> throw UnsupportedEnvelope("Unsupported adoption proof schema")
    }
}
/** Shared strict dedicated-request boundary, also used by durable pending-resolution admission. */
internal fun validateResolutionRequest(request:AcquisitionResolution) {
    val state=decodeSyncEnvelope(request.selectedCanonical)
    val tip=state.ledger.entries.lastOrNull()
    val receipt=state.receipts.lastOrNull()
    require(request.operationId.isNotBlank() && request.archiveId.isNotBlank() && request.expectedServerRevision>=0 &&
        state.character.id==request.characterId && tip?.operationId==request.operationId && tip.type==LedgerEntryType.SyncAdoption &&
        receipt?.operationId==request.operationId && receipt.afterRevision==state.revision) { "Resolution must identify its exact adoption tip" }
    val proof=requireCanonical(tip.evidenceCanonical).obj()
    require(proof.text("archiveId")==request.archiveId && proof.text("choice")==request.choice.name &&
        proof.long("expectedServerRevision")==request.expectedServerRevision) { "Resolution differs from archived confirmed choice" }
}

/** Validate nested wire structure without recursively accepting local bookkeeping. */
private fun validateSyncPayload(raw: String, characterId: String): JsonValue.Obj {
    val root=wireRoot(raw,characterId)
    validate(decodeWireState(root))
    return root
}
private fun wireRoot(raw:String,characterId:String):JsonValue.Obj {
    val root=requireCanonical(raw).obj()
    require(root.number("formatVersion") == 1 && !root.has("gameplay")) { "Gameplay format has no enabled cloud reader" }
    require(root.text("characterId")==characterId) { "Sync payload character scope mismatch" }
    require(!root.has("outbox") && !root.has("storageRevision") && !root.has("syncMetadata")) { "Local metadata in sync payload" }
    require(!root.objectRequired("extras").has("portraitUri")) { "Device metadata in sync payload" }
    return root
}
private fun decodeWireState(root:JsonValue.Obj):StoredCharacter {
    val extras=root.objectRequired("extras")
    return decodeState(JsonValue.Obj(root.values + mapOf("outbox" to jsonArray(emptyList()), "storageRevision" to decimal(0),
        "syncMetadata" to jsonObject("serverRevision" to decimal(0), "remoteConflictCanonical" to JsonValue.Null),
        "extras" to JsonValue.Obj(extras.values + ("portraitUri" to JsonValue.Null)))))
}
/** Exact remote projection decoding; missing/unknown/recursive local fields fail closed. */
fun decodeSyncEnvelope(raw:String):StoredCharacter {
    val parsed=requireCanonical(raw).obj()
    val state=decodeWireState(wireRoot(raw,parsed.text("characterId")))
    validate(state)
    require(encodeSyncEnvelope(state)==raw) { "Wire projection would lose values" }
    return state
}
internal fun flatSyncEnvelope(state:StoredCharacter):String {
    val root=JsonReader(encodeSyncEnvelope(state)).read().obj()
    return canonicalText(JsonValue.Obj(root.values - "copySources" - "syncProofs"))
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
