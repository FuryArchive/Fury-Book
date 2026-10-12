package com.furybook.dubl.acquisition

import com.furybook.core.json.*
import com.furybook.dubl.model.*
import com.furybook.dubl.application.DublApplication
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.withLock

sealed interface UndoEligibility {
    data class Available(val operationId: String, val type: LedgerEntryType, val expectedRevision: Long,
        val amountDelta: Amount) : UndoEligibility
    /** Exact load failure is retained for adapters, without putting original bytes in the explanation. */
    data class Unavailable(val reason: String, val loadResult: LoadResult? = null) : UndoEligibility
    data class StorageFailure(val reason: String) : UndoEligibility
}

/** A persisted inverse operation, never a restore of an earlier envelope/history. */
class AcquisitionUndo(private val store: AcquisitionStore) {
    private val locks=MutationLocks()
    /** Read-only; callers must still supply this current revision to undo and retain their new ID. */
    suspend fun eligible(characterId: String): UndoEligibility {
        val loaded=try { store.load(characterId) }
            catch(cancelled: CancellationException) { throw cancelled }
            catch(failure: Exception) { return UndoEligibility.StorageFailure(failure.message ?: "Undo storage unavailable") }
        if(loaded !is LoadResult.Loaded) return UndoEligibility.Unavailable(when(loaded) {
            is LoadResult.Missing -> "Character is missing"
            is LoadResult.MigrationRequired -> "Character requires migration"
            is LoadResult.Corrupt -> "Character envelope is corrupt: ${loaded.reason}"
            is LoadResult.Unsupported -> "Character envelope is unsupported: ${loaded.reason}"
            is LoadResult.Loaded -> error("Handled above")
        },loaded)
        return try {
            require(loaded.state.character.id==characterId) { "Stored character scope differs" }
            encodeEnvelope(loaded.state)
            val candidate=resolveUndoCandidate(loaded.state)
            UndoEligibility.Available(candidate.entry.operationId,candidate.entry.type,loaded.state.revision,Amount.ZERO-candidate.entry.amountDelta)
        } catch(cancelled: CancellationException) { throw cancelled }
          catch(failure: Exception) { UndoEligibility.Unavailable(failure.message ?: "Invalid inverse evidence") }
    }
    suspend fun undo(characterId:String,operationId:String,expectedRevision:Long):ApplyResult = locks.forCharacter(characterId).withLock {
        try { undoLocked(characterId,operationId,expectedRevision) }
        catch(cancelled:CancellationException) { throw cancelled }
        catch(failure:Exception) { ApplyResult.StorageFailure(failure.message ?: "Undo storage unavailable") }
    }
    private suspend fun undoLocked(characterId:String,operationId:String,expectedRevision:Long):ApplyResult {
        if(characterId.isBlank() || operationId.isBlank()) return ApplyResult.IdentityConflict("Blank Undo identity")
        val request=canonicalJson(jsonStringify(jsonObject("type" to jsonString("Undo"),"characterId" to jsonString(characterId),
            "expectedRevision" to jsonString(expectedRevision.toString()))))
        var originalProjection:String?=null
        repeat(8) {
            val state=(store.load(characterId) as? LoadResult.Loaded)?.state ?: return undoBlocked("Canonical character unavailable")
            if(state.character.id!=characterId) return ApplyResult.IdentityConflict("Stored character scope differs")
            state.receipts.singleOrNull { it.operationId==operationId }?.let {
                if(it.requestCanonical!=request) return ApplyResult.IdentityConflict("Operation ID reused with different Undo payload")
                return if(it.status==ReceiptStatus.Undone) ApplyResult.AlreadyUndone(it) else ApplyResult.AlreadyApplied(it)
            }
            if(state.revision!=expectedRevision) return undoBlocked("Character changed; Undo requires the expected gameplay revision")
            val projection=encodeSyncEnvelope(state)
            if(originalProjection!=null && originalProjection!=projection) return undoBlocked("Canonical state changed during Undo")
            originalProjection=projection
            val next=try {
                val candidate=resolveUndoCandidate(state)
                val tip=candidate.entry
                val receipt=candidate.receipt
                val restored=candidate.restored
                appendCanonicalOperation(state,restored,operationId,request,LedgerEntryType.Undo,Amount.ZERO-tip.amountDelta,
                    tip.ruleVersions,canonicalJson(jsonStringify(jsonObject("schema" to jsonString("undo-evidence-v1"),
                        "reversesId" to jsonString(tip.operationId),"originalRequest" to jsonString(receipt.requestCanonical)))),
                    tip.changes.asReversed().map { it.copy(fromRank=it.toRank,toRank=it.fromRank) },tip.operationId)
            } catch(failure:IllegalArgumentException) { return undoBlocked(failure.message ?: "Invalid inverse evidence") }
              catch(failure:ArithmeticException) { return undoBlocked(failure.message ?: "Undo overflow") }
              catch(failure:ClassCastException) { return undoBlocked("Malformed inverse evidence") }
              catch(failure:NoSuchElementException) { return undoBlocked("Incomplete inverse evidence") }
            when(val result=store.compareAndSet(characterId,state.storageRevision,next)) {
                CommitResult.Committed -> return ApplyResult.Applied(next.receipts.last())
                CommitResult.Conflict -> Unit
                is CommitResult.StorageFailure -> return ApplyResult.StorageFailure(result.reason)
            }
        }
        return ApplyResult.StorageFailure("Storage metadata remained busy; retry the same Undo ID")
    }
}
private fun undoBlocked(reason:String)=ApplyResult.Blocked(listOf(AcquisitionIssue(RequirementStatus.Conflict,reason)))
private fun objectJson(raw:String)=JsonReader(raw,strict=true).read() as JsonValue.Obj
private fun JsonValue.Obj.text(key:String)=(values.getValue(key) as JsonValue.Str).value

private fun validatePurchaseInverse(state:StoredCharacter,entry:LedgerEntry,receipt:Receipt,pureNoop:Boolean=false):StoredCharacter {
    val proof=decodeAcquisitionUndoEvidence(entry.evidenceCanonical) ?: throw IllegalArgumentException("Missing exact purchase inverse evidence")
    require(proof.afterCanonical==acquisitionProjectionCanonical(state)) { "Current holdings differ from the purchase result" }
    if(pureNoop) require(entry.type==LedgerEntryType.NonFinancial && entry.changes.isEmpty() && entry.amountDelta==Amount.ZERO &&
        proof.beforeCanonical==proof.afterCanonical) { "Only an exact inert acquisition no-op may be crossed" }
    else require(entry.type==LedgerEntryType.Purchase)
    val beforeRoot=objectJson(proof.beforeCanonical)
    val current=objectJson(mutationProjectionCanonical(state))
    val before=decodeMutationProjection(state,canonicalJson(jsonStringify(JsonValue.Obj(current.values+beforeRoot.values))))
    require(before.character.id==state.character.id && before.creationPhase==state.creationPhase && entry.creationPhase==state.creationPhase) { "Purchase phase/scope mismatch" }
    // Check saved confirmation/token/evidence against the exact ledger operation; never quote today's catalog.
    val command=objectJson(receipt.requestCanonical)
    require(command.text("type")=="ConfirmAcquisition" && command.text("characterId")==state.character.id)
    require(command.text("expectedRevision")==receipt.beforeRevision.toString())
    val token=objectJson(command.text("previewToken"))
    require(token.values["request"]==command.values["request"] && token.text("characterId")==state.character.id)
    require(token.text("baseRevision")==receipt.beforeRevision.toString() && token.text("phase")==entry.creationPhase.name)
    require(token.text("catalogRevision")==entry.ruleVersions.catalogRevision && token.text("resolutionRevision")==entry.ruleVersions.resolutionRevision)
    val envelope=objectJson(encodeEnvelope(state.copy(outbox=emptyList())))
    val ledger=envelope.values.getValue("ledger") as JsonValue.Obj
    val encodedEntry=(ledger.values.getValue("entries") as JsonValue.Arr).values.map { it as JsonValue.Obj }.single { it.text("operationId")==entry.operationId }
    val tokenChanges=token.values.getValue("changes") as JsonValue.Arr
    require(tokenChanges==encodedEntry.values["changes"]) { "Receipt token transitions differ from ledger" }
    require(Amount.fromJson(jsonStringify(token.values.getValue("cost")))==receipt.result.cost)
    require(Amount.fromJson(jsonStringify(token.values.getValue("remaining")))==receipt.result.remaining)
    require((pureNoop || entry.changes.isNotEmpty()) && entry.amountDelta.xp<=0 && entry.amountDelta.ap<=0)
    require((token.values["issues"] as JsonValue.Arr).values.isEmpty()) { "Saved acquisition was blocked" }
    val evidence=objectJson(entry.evidenceCanonical)
    require(evidence.text("phase")==entry.creationPhase.name)
    val quotes=(token.values.getValue("quotes") as JsonValue.Arr).values
    val prices=(evidence.values.getValue("prices") as JsonValue.Arr).values
    require(quotes.size==entry.changes.size && prices.size==quotes.size)
    var total=Amount.ZERO
    prices.forEachIndexed { index,value -> val row=value as JsonValue.Obj
        require(row.text("instanceId")==entry.changes[index].instanceId && row.values["quote"]==quotes[index])
        total+=Amount.fromJson(jsonStringify((quotes[index] as JsonValue.Obj).values.getValue("cost")))
    }
    require(total==receipt.result.cost) { "Purchase price evidence differs from paid amount" }
    require(purchaseForward(before,state,entry.changes)==acquisitionProjectionCanonical(state)) { "Inverse evidence changes fields not owned by the purchase" }
    return state.copy(character=before.character,instances=before.instances)
}

/** Replay only the recorded ownership transition and allowed presentation fields. No current prices/rules. */
private fun purchaseForward(before:StoredCharacter,after:StoredCharacter,changes:List<AcquisitionChange>):String {
    var character=before.character
    var instances=before.instances
    for(change in changes) {
        require(change.toRank>change.fromRank) { "Purchase is not an increasing transition" }
        val old=instances.singleOrNull { it.instanceId==change.instanceId }
        if(change.kind==AcquisitionKind.ATTRIBUTE) {
            require(change.definition.packId=="dubl-3.69")
            val id=AttributeId.entries.single { "attribute:${it.name}"==change.definition.entryId }
            val value=character.attributes.getValue(id)
            require(value.base==change.fromRank)
            character=character.copy(attributes=character.attributes+(id to value.copy(base=change.toRank)))
        } else {
            require((old?.rank ?: 0)==change.fromRank && (old==null || (old.definition==change.definition && old.parameters==change.parameters && old.abilityOptionId==change.optionId))) { "Original rank or identity differs from recorded transition" }
            instances=instances.filterNot { it.instanceId==change.instanceId }+AcquisitionInstance(change.instanceId,change.definition,change.toRank,change.parameters,change.optionId,old?.origin ?: AcquisitionOrigin.New)
            when(change.kind) {
                AcquisitionKind.MANA -> character=character.copy(manaEnabled=true,magic=character.magic.copy(manaRank=change.toRank))
                AcquisitionKind.CHI_BONUS -> character=character.copy(chiBonusRanks=change.toRank)
                AcquisitionKind.DEVELOPMENT -> {
                    // Rich/ambiguous instances may intentionally have no legacy presentation row.
                    val final=after.character.development[change.definition.entryId]
                    if(final!=before.character.development[change.definition.entryId]) {
                        val prior=character.development[change.definition.entryId]
                        character=character.copy(development=character.development+(change.definition.entryId to (prior?.copy(rank=change.toRank) ?: OwnedDevelopment(change.toRank))))
                    }
                }
                AcquisitionKind.SKILL -> {
                    val legacyKey=(old?.origin as? AcquisitionOrigin.Legacy)?.ownershipKey?.removePrefix("skill:")
                    val key=legacyKey?.takeIf { it in character.skills } ?: change.instanceId
                    val prior=character.skills[key]
                    val final=after.character.skills.getValue(key)
                    require(prior!=null || (final.id==key && final.definitionId==change.definition.entryId))
                    character=character.copy(skills=character.skills+(key to (prior?.copy(rank=change.toRank)
                        ?: CharacterSkill(key,change.definition.entryId,final.name,rank=change.toRank))))
                }
                AcquisitionKind.SPELL -> {
                    val prior=character.magic.spells.filter { !it.custom && it.catalogId==change.definition.entryId }.also { require(it.size<=1) }.singleOrNull()
                    val final=after.character.magic.spells.single { !it.custom && it.catalogId==change.definition.entryId }
                    val spell=prior?.copy(learned=true) ?: KnownSpell(change.instanceId,change.definition.entryId,final.name,learned=true)
                    character=character.copy(magic=character.magic.copy(spells=character.magic.spells.filterNot { it===prior }+spell))
                }
                AcquisitionKind.SCHOOL -> Unit
                AcquisitionKind.ATTRIBUTE -> error("Handled above")
            }
        }
    }
    return acquisitionProjectionCanonical(before.copy(character=character,instances=instances))
}

internal fun mutationEvidenceStates(state:StoredCharacter,entry:LedgerEntry,receipt:Receipt):Pair<StoredCharacter,StoredCharacter> {
    val root=objectJson(entry.evidenceCanonical)
    require(root.text("schema")=="mutation-evidence-v1" && root.values["command"]==JsonReader(receipt.requestCanonical).read()) { "Mutation evidence command mismatch" }
    return decodeMutationProjection(state,canonicalJson(jsonStringify(root.values.getValue("before")))) to
        decodeMutationProjection(state,canonicalJson(jsonStringify(root.values.getValue("after"))))
}
private fun validateMutationInverse(state:StoredCharacter,entry:LedgerEntry,receipt:Receipt):StoredCharacter {
    val (before,after)=mutationEvidenceStates(state,entry,receipt)
    require(mutationProjectionCanonical(state)==mutationProjectionCanonical(after)) { "Current character differs from mutation result" }
    val command=objectJson(receipt.requestCanonical)
    require(command.text("type")=="CanonicalMutation" && command.text("expectedRevision")==receipt.beforeRevision.toString())
    val payload=command.values.getValue("payload") as JsonValue.Obj
    require(payload.text("kind")==entry.type.name)
    if(entry.type!=LedgerEntryType.CompleteCreation) require(!command.text("reason").isBlank())
    require(entry.creationPhase==before.creationPhase)
    when(entry.type) {
        LedgerEntryType.Grant -> {
            require(mutationProjectionCanonical(before)==mutationProjectionCanonical(after))
            require(Amount.fromJson(jsonStringify(payload.values.getValue("delta")))==entry.amountDelta)
        }
        LedgerEntryType.ManualCorrection -> {
            require(before.creationPhase==after.creationPhase)
            require(Amount.fromJson(jsonStringify(payload.values.getValue("delta")))==entry.amountDelta)
            val patch=payload.values.getValue("patch") as JsonValue.Obj
            val decoded=decodeMutationProjection(before,canonicalJson(jsonStringify(patch.values.getValue("projection"))))
            val expected=before.copy(character=decoded.character,
                instances=if(patch.values["replacesInstances"]==jsonBoolean(true)) decoded.instances else before.instances,
                extras=if(patch.values["replacesExtras"]==jsonBoolean(true)) decoded.extras else before.extras)
            require(mutationProjectionCanonical(expected)==mutationProjectionCanonical(after)) { "Manual patch differs from inverse projection" }
        }
        LedgerEntryType.CompleteCreation -> {
            require(entry.amountDelta==Amount.ZERO && !before.character.creationComplete)
            val detached=DublApplication.detached(before.character,before.extras,{"unused"});detached.character.completeCreation()
            require(mutationProjectionCanonical(before.copy(character=detached.active,creationPhase=RequirementNode.CreationPhase.Phase.Finalized))==mutationProjectionCanonical(after))
        }
        LedgerEntryType.ConfirmLegacyParameter -> {
            require(entry.amountDelta==Amount.ZERO && before.character==after.character && before.extras==after.extras && before.creationPhase==after.creationPhase)
            val id=payload.text("instanceId");val instance=after.instances.single { it.instanceId==id }
            val old=before.instances.singleOrNull { it.instanceId==id }
            require(before.instances.filterNot { it.instanceId==id }==after.instances.filterNot { it.instanceId==id })
            require(JsonValue.Obj(instance.parameters.mapValues { jsonString(it.value) })==payload.values["parameters"])
            val origin=instance.origin as? AcquisitionOrigin.Legacy ?: throw IllegalArgumentException("Not legacy")
            if(old!=null) require(old.copy(parameters=instance.parameters)==instance && old.parameters.all { instance.parameters[it.key]==it.value })
            else {
                val record=before.ledger.opening.legacyMigration!!.ownershipRecords.single { it.ownershipKey==origin.ownershipKey }
                val resolution=payload.values.getValue("resolution") as JsonValue.Obj
                require(record.rank==instance.rank && resolution.text("ownershipKey")==record.ownershipKey && resolution.text("packId")==instance.definition.packId && resolution.text("entryId")==instance.definition.entryId)
                require(resolution.values["rank"]==jsonNumber(record.rank))
            }
        }
        else -> throw IllegalArgumentException("Ineligible mutation")
    }
    return state.copy(character=before.character,instances=before.instances,extras=before.extras,creationPhase=before.creationPhase)
}

/** Removal evidence is derived from actual, applied, exact manual before/after data, never a loose flag. */
internal fun activeLegacyRemovalKeys(state:StoredCharacter):Set<String> = buildSet {
    for(entry in state.ledger.entries.filter { it.type==LedgerEntryType.ManualCorrection }) {
        val receipt=state.receipts.singleOrNull { it.operationId==entry.operationId && it.status==ReceiptStatus.Applied } ?: continue
        try {
            val (before,after)=mutationEvidenceStates(state,entry,receipt)
            // Reuse authorization checks at the recorded after projection, not the current later tip.
            validateMutationInverse(after,entry,receipt)
            before.instances.filter { it.origin is AcquisitionOrigin.Legacy }.forEach { old ->
                val next=after.instances.singleOrNull { it.instanceId==old.instanceId }
                if(next==null || next.rank<old.rank) add((old.origin as AcquisitionOrigin.Legacy).ownershipKey)
            }
        } catch(_:Exception) { /* Malformed historical evidence grants no resolution permission. */ }
    }
}

private data class UndoCandidate(val entry: LedgerEntry, val receipt: Receipt, val restored: StoredCharacter)
/** Shared by the read-only UI query and the committing command. */
private fun resolveUndoCandidate(state: StoredCharacter): UndoCandidate {
    var index=state.ledger.entries.lastIndex
    require(index>=0) { "No operation to undo" }
    // Revision remains a durable operation/history counter. Only fully validated inert
    // acquisition confirmations may be crossed; every other canonical edit is a barrier.
    while(index>=0 && state.ledger.entries[index].type==LedgerEntryType.NonFinancial) {
        val noop=state.ledger.entries[index]
        val noopReceipt=state.receipts.single { it.operationId==noop.operationId }
        require(noopReceipt.status==ReceiptStatus.Applied) { "No-op is not applied" }
        validatePurchaseInverse(state,noop,noopReceipt,pureNoop=true)
        index--
    }
    require(index>=0) { "No eligible operation before acquisition no-ops" }
    val tip=state.ledger.entries[index]
    require(tip.type!=LedgerEntryType.Undo) { "Cannot undo an inverse operation" }
    val receipt=state.receipts.single { it.operationId==tip.operationId }
    require(receipt.status==ReceiptStatus.Applied && receipt.afterRevision==index.toLong()+1) { "Original operation is not the eligible applied tip" }
    val restored=if(tip.type==LedgerEntryType.Purchase) validatePurchaseInverse(state,tip,receipt)
        else validateMutationInverse(state,tip,receipt)
    return UndoCandidate(tip,receipt,restored)
}
