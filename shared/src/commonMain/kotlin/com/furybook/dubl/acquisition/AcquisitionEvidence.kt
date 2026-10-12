package com.furybook.dubl.acquisition

import com.furybook.core.json.*

/** Reserved schema shared by preview and the future purchase writer. Other canonical evidence fields are retained. */
fun encodeAcquisitionEvidence(externalConfirmations: List<RequirementWitness.External>, otherEvidenceCanonical: String = "{}"): String {
    val other = JsonReader(otherEvidenceCanonical, strict=true).read() as? JsonValue.Obj
        ?: throw IllegalArgumentException("Evidence must be an object")
    require("schema" !in other.values && "externalConfirmations" !in other.values) { "Reserved acquisition evidence keys" }
    externalConfirmations.forEach(::validateExternal)
    return canonicalJson(jsonStringify(JsonValue.Obj(other.values + mapOf(
        "schema" to jsonString("acquisition-evidence-v1"),
        "externalConfirmations" to JsonValue.Arr(externalConfirmations.map(::externalJson))))))
}

/** Invalid/unknown proof shape never grants. Callers may surface corruption separately; old {} has no confirmations. */
fun decodeAcquisitionConfirmations(evidenceCanonical: String): List<RequirementWitness.External> = try {
    val root = JsonReader(evidenceCanonical, strict=true).read() as? JsonValue.Obj ?: error("Expected object")
    require((root.values["schema"] as? JsonValue.Str)?.value == "acquisition-evidence-v1")
    val rows = (root.values["externalConfirmations"] as? JsonValue.Arr)?.values ?: error("Missing confirmations")
    EnvelopeList(rows.map { value ->
        val row = value as? JsonValue.Obj ?: error("Expected confirmation")
        require(row.values.keys == setOf("evidenceKey","scopeId","evidenceId","sourceRef"))
        val source = row.values["sourceRef"] as? JsonValue.Obj ?: error("Expected source")
        require(source.values.keys == setOf("documentId","documentVersion","originalText","sheet","paragraph","resolutionId","resolutionRevision"))
        fun text(obj: JsonValue.Obj, key: String): String = (obj.values[key] as? JsonValue.Str)?.value ?: error("Missing $key")
        fun optional(key: String): String? = if (source.values[key] == JsonValue.Null) null else text(source,key)
        val paragraph = if (source.values["paragraph"] == JsonValue.Null) null else
            (source.values["paragraph"] as? JsonValue.Num)?.raw?.toIntOrNull() ?: error("Invalid paragraph")
        RequirementWitness.External(text(row,"evidenceKey"),text(row,"scopeId"),text(row,"evidenceId"),
            RuleSourceRef(text(source,"documentId"),text(source,"documentVersion"),text(source,"originalText"),
                optional("sheet"),paragraph,optional("resolutionId"),optional("resolutionRevision"))).also(::validateExternal)
    })
} catch (_: IllegalArgumentException) { emptyList() } catch (_: IllegalStateException) { emptyList() }

/** Only active purchases can retain acquisition confirmations; other mutation types are not permission writers. */
fun retainedAcquisitionConfirmations(state: StoredCharacter): List<RequirementWitness.External> {
    val applied = state.receipts.filter { it.status == ReceiptStatus.Applied }.map { it.operationId }.toSet()
    return EnvelopeList(state.ledger.entries.filter { it.operationId in applied && it.type == LedgerEntryType.Purchase }
        .flatMap { decodeAcquisitionConfirmations(it.evidenceCanonical) }.distinct())
}
internal fun externalJson(value: RequirementWitness.External) = jsonObject("evidenceKey" to jsonString(value.evidenceKey),
    "scopeId" to jsonString(value.scopeId),"evidenceId" to jsonString(value.evidenceId),"sourceRef" to sourceJson(value.sourceRef))
internal fun validateExternal(value: RequirementWitness.External) {
    require(listOf(value.evidenceKey,value.scopeId,value.evidenceId,value.sourceRef.documentId,value.sourceRef.documentVersion,value.sourceRef.originalText).all { it.isNotBlank() }) {
        "External confirmation requires exact key, scope, confirmation ID and source"
    }
}
