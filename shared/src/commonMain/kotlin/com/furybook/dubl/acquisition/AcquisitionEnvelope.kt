package com.furybook.dubl.acquisition

import com.furybook.dubl.model.CharacterSheetExtras
import com.furybook.dubl.model.DublCharacter

/** Construction-time gates; cloud V2 stays false until every server writer/reader is guarded. */
data class AcquisitionCapabilities(val localDurable: Boolean, val cloudLedgerV2: Boolean, val formatWritable: Boolean) {
    val canSync: Boolean get() = localDurable && cloudLedgerV2 && formatWritable
}
/** Flat ancestry. Each source is an exact wire projection without local metadata or nested ancestry. */
data class AcquisitionCopySource(val sourceCharacterId: String, val destinationCharacterId: String,
    val importOperationId: String, val sourceCanonical: String)

/** Deduplicated branch bytes and the exact prior pool membership needed to reconstruct its wire. */
class AcquisitionSyncProof(val proofId:String,val payloadCanonical:String,proofIds:List<String>) {
    val proofIds:List<String> = EnvelopeList(proofIds)
    fun copy(proofId:String=this.proofId,payloadCanonical:String=this.payloadCanonical,proofIds:List<String> =this.proofIds)=
        AcquisitionSyncProof(proofId,payloadCanonical,proofIds)
    override fun equals(other:Any?):Boolean=other is AcquisitionSyncProof && proofId==other.proofId && payloadCanonical==other.payloadCanonical && proofIds==other.proofIds
    override fun hashCode():Int=listOf(proofId,payloadCanonical,proofIds).hashCode()
}
data class PendingAcquisitionResolution(val request:AcquisitionResolution,val localCanonical:String,val serverCanonical:String)

enum class ReceiptStatus { Applied, Undone }
class SavedAcquisitionResult(val cost: Amount, val remaining: Amount, changes: List<AcquisitionChange>) {
    val changes: List<AcquisitionChange> = EnvelopeList(changes)
    fun copy(cost: Amount = this.cost, remaining: Amount = this.remaining, changes: List<AcquisitionChange> = this.changes) =
        SavedAcquisitionResult(cost, remaining, changes)
    override fun equals(other: Any?): Boolean = other is SavedAcquisitionResult && cost == other.cost && remaining == other.remaining && changes == other.changes
    override fun hashCode(): Int = listOf(cost, remaining, changes).hashCode()
}
data class Receipt(
    val operationId: String,
    val requestCanonical: String,
    val result: SavedAcquisitionResult,
    val status: ReceiptStatus,
    val beforeRevision: Long,
    val afterRevision: Long,
)
data class OutboxItem(val operationId: String, val parentServerRevision: Long, val payloadCanonical: String)
data class SyncMetadata(val serverRevision: Long, val remoteConflictCanonical: String? = null, val pendingResolution:PendingAcquisitionResolution?=null)

/** Local durable unit. Gameplay, local CAS and remote CAS revisions have separate meanings. */
class StoredCharacter(
    character: DublCharacter,
    val extras: CharacterSheetExtras,
    instances: List<AcquisitionInstance>,
    val ledger: Ledger,
    receipts: List<Receipt>,
    outbox: List<OutboxItem>,
    val syncMetadata: SyncMetadata,
    val revision: Long,
    val storageRevision: Long,
    val creationPhase: RequirementNode.CreationPhase.Phase,
    val formatVersion: Int = 1,
    copySources: List<AcquisitionCopySource> = emptyList(),
    syncProofs:List<AcquisitionSyncProof> = emptyList(),
) {
    val character:DublCharacter = reconcileAcquisitionDoubleTokens(character)
    val syncProofs:List<AcquisitionSyncProof> = EnvelopeList(syncProofs)
    val copySources: List<AcquisitionCopySource> = EnvelopeList(copySources)
    val instances: List<AcquisitionInstance> = EnvelopeList(instances)
    val receipts: List<Receipt> = EnvelopeList(receipts)
    val outbox: List<OutboxItem> = EnvelopeList(outbox)
    override fun equals(other: Any?): Boolean = other is StoredCharacter && character == other.character && extras == other.extras &&
        instances == other.instances && ledger == other.ledger && receipts == other.receipts && outbox == other.outbox &&
        syncMetadata == other.syncMetadata && revision == other.revision && storageRevision == other.storageRevision &&
        creationPhase == other.creationPhase && formatVersion == other.formatVersion && copySources == other.copySources && syncProofs==other.syncProofs
    override fun hashCode(): Int = listOf(character, extras, instances, ledger, receipts, outbox, syncMetadata, revision,
        storageRevision, creationPhase, formatVersion, copySources, syncProofs).hashCode()
    fun copy(character: DublCharacter = this.character, extras: CharacterSheetExtras = this.extras,
        instances: List<AcquisitionInstance> = this.instances, ledger: Ledger = this.ledger,
        receipts: List<Receipt> = this.receipts, outbox: List<OutboxItem> = this.outbox,
        syncMetadata: SyncMetadata = this.syncMetadata, revision: Long = this.revision,
        storageRevision: Long = this.storageRevision, creationPhase: RequirementNode.CreationPhase.Phase = this.creationPhase,
        formatVersion: Int = this.formatVersion, copySources: List<AcquisitionCopySource> = this.copySources, syncProofs:List<AcquisitionSyncProof> = this.syncProofs) = StoredCharacter(reconcileAcquisitionDoubleTokens(character,this.character), extras, instances, ledger, receipts, outbox,
        syncMetadata, revision, storageRevision, creationPhase, formatVersion, copySources, syncProofs)
}
