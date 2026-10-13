package com.furybook.dubl.acquisition

import com.furybook.core.json.*

/** Presentation of recorded facts only. No catalog, evaluator, pricing or authorization is consulted. */
data class AcquisitionSavedOperation(val operationId:String, val type:LedgerEntryType?,
    val phase:RequirementNode.CreationPhase.Phase?, val beforeRevision:Long, val afterRevision:Long,
    val ruleVersions:RuleVersions?, val evidence:AcquisitionSavedEvidence)
sealed interface AcquisitionSavedEvidence {
    data class Recorded(val prices:List<AcquisitionSavedPrice>):AcquisitionSavedEvidence
    data class Unavailable(val explanation:String):AcquisitionSavedEvidence
    data class Unsupported(val explanation:String):AcquisitionSavedEvidence
    data class Malformed(val explanation:String):AcquisitionSavedEvidence
}
data class AcquisitionSavedPrice(val instanceId:String, val source:RuleSourceRef, val cost:Amount,
    val componentSources:List<RuleSourceRef>, val discountProofs:List<RequirementResult>)

fun savedAcquisitionOperation(receipt:Receipt, entry:LedgerEntry?):AcquisitionSavedOperation {
    val bound=entry?.takeIf { it.operationId==receipt.operationId }
    return AcquisitionSavedOperation(receipt.operationId,bound?.type,bound?.creationPhase,receipt.beforeRevision,receipt.afterRevision,
        bound?.ruleVersions,if(bound==null) AcquisitionSavedEvidence.Unavailable("Сохранённая запись операции недоступна") else savedPriceEvidence(receipt,bound))
}

private class UnsupportedSavedShape:IllegalArgumentException()
private fun JsonValue.obj()=this as? JsonValue.Obj ?: error("Expected object")
private fun JsonValue.Obj.exact(vararg keys:String) { if(values.keys!=keys.toSet()) throw UnsupportedSavedShape() }
private fun JsonValue.Obj.text(key:String)=(values.getValue(key) as? JsonValue.Str)?.value ?: error("Expected string")
private fun JsonValue.Obj.identity(key:String)=text(key).also { require(it.isNotBlank()) }
private fun JsonValue.Obj.array(key:String)=(values.getValue(key) as? JsonValue.Arr)?.values ?: error("Expected array")

private fun savedPriceEvidence(receipt:Receipt,entry:LedgerEntry):AcquisitionSavedEvidence = try {
    require(canonicalJson(entry.evidenceCanonical)==entry.evidenceCanonical)
    val root=JsonReader(entry.evidenceCanonical,strict=true).read().obj()
    if(root.values.isEmpty()) AcquisitionSavedEvidence.Unavailable("Объяснение цены не было сохранено")
    else if(root.values["schema"]!=jsonString("acquisition-evidence-v1")) AcquisitionSavedEvidence.Unsupported("Версия сохранённого объяснения не поддерживается")
    else {
        root.exact("schema","externalConfirmations","phase","undoProjection","prices")
        require(root.text("phase")==entry.creationPhase.name)
        // Confirmations/source shapes use the existing typed codec. Projection is not restored or interpreted here.
        readSavedExternals(root.values.getValue("externalConfirmations"))
        val projection=root.values.getValue("undoProjection").obj()
        projection.exact("schema","before","after")
        if(projection.values["schema"]!=jsonString("acquisition-projection-v1")) throw UnsupportedSavedShape()
        require(projection.values["before"] is JsonValue.Obj && projection.values["after"] is JsonValue.Obj)
        val inverse=decodeAcquisitionUndoEvidence(entry.evidenceCanonical) ?: error("Malformed saved projection")
        require(entry.changes==receipt.result.changes)
        val prices=root.array("prices").map { raw ->
            val row=raw.obj();row.exact("instanceId","sourceRef","quote")
            val quote=row.values.getValue("quote").obj();quote.exact("cost","sources","discountEvidence")
            val amount=quote.values.getValue("cost").obj();amount.exact("xp","ap")
            AcquisitionSavedPrice(row.identity("instanceId"),readSavedSource(row.values.getValue("sourceRef")),
                Amount.fromJson(jsonStringify(amount)),EnvelopeList(quote.array("sources").map(::readSavedSource)),
                EnvelopeList(quote.array("discountEvidence").map { readSavedProof(it,0) }))
        }
        require(prices.size==entry.changes.size)
        prices.forEachIndexed { index,price -> require(price.instanceId==entry.changes[index].instanceId) }
        val cost=prices.fold(Amount.ZERO) { total,price -> total+price.cost }
        require(cost==receipt.result.cost && cost+entry.amountDelta==Amount.ZERO)
        when(entry.type) {
            LedgerEntryType.Purchase -> require(entry.changes.isNotEmpty())
            LedgerEntryType.NonFinancial -> require(entry.changes.isEmpty() && cost==Amount.ZERO && inverse.beforeCanonical==inverse.afterCanonical)
            else -> error("Not an acquisition operation")
        }
        AcquisitionSavedEvidence.Recorded(EnvelopeList(prices))
    }
} catch(_:UnsupportedSavedShape) { AcquisitionSavedEvidence.Unsupported("Форма сохранённого объяснения не поддерживается") }
  catch(_:Exception) { AcquisitionSavedEvidence.Malformed("Сохранённое объяснение повреждено; цена не пересчитывалась") }

private fun readSavedExternals(value:JsonValue):List<RequirementWitness.External> {
    require(value is JsonValue.Arr)
    val raw=canonicalJson(jsonStringify(jsonObject("schema" to jsonString("acquisition-evidence-v1"),"externalConfirmations" to value)))
    val decoded=decodeAcquisitionConfirmations(raw)
    require(JsonValue.Arr(decoded.map(::externalJson))==value)
    return decoded
}
private fun readSavedSource(value:JsonValue):RuleSourceRef = readSavedExternals(JsonValue.Arr(listOf(jsonObject(
    "evidenceKey" to jsonString("saved-source-shape"),"scopeId" to jsonString("saved-source-shape"),
    "evidenceId" to jsonString("saved-source-shape"),"sourceRef" to value)))).single().sourceRef
private fun readSavedProof(value:JsonValue,depth:Int):RequirementResult {
    require(depth<=64)
    val proof=value.obj();proof.exact("nodeId","status","witnesses","reason","children")
    val status=RequirementStatus.entries.singleOrNull { it.name==proof.text("status") } ?: throw UnsupportedSavedShape()
    val reason=proof.values.getValue("reason").let { if(it==JsonValue.Null) null else (it as? JsonValue.Str)?.value ?: error("Invalid reason") }
    val witnesses=proof.array("witnesses").map { raw ->
        val row=raw.obj()
        if("type" !in row.values) readSavedExternals(JsonValue.Arr(listOf(row))).single()
        else when(row.text("type")) {
            "OwnedInstance" -> { row.exact("type","packId","entryId","instanceId","rank")
                val rank=(row.values.getValue("rank") as? JsonValue.Num)?.raw?.toIntOrNull() ?: error("Invalid rank")
                RequirementWitness.OwnedInstance(row.identity("packId"),row.identity("entryId"),row.identity("instanceId"),rank) }
            "Branch" -> { row.exact("type","packId","branchId","accessOptionId")
                RequirementWitness.Branch(row.identity("packId"),row.identity("branchId"),row.identity("accessOptionId")) }
            "LearnedSpell" -> { row.exact("type","packId","entryId")
                RequirementWitness.LearnedSpell(row.identity("packId"),row.identity("entryId")) }
            else -> throw UnsupportedSavedShape()
        }
    }
    return RequirementResult(status,proof.identity("nodeId"),witnesses,proof.array("children").map { readSavedProof(it,depth+1) },reason)
}
