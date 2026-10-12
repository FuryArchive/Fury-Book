package com.furybook.dubl.acquisition

/** Provenance of explicit initial funding; historical purchases are never reconstructed here. */
enum class OpeningProvenance { LegacyCalculated, UserConfirmed, NewCharacter }
data class Opening(
    val migrationId: String,
    val sourceFingerprint: String,
    val balance: Amount,
    val legacyStage: RequirementNode.CreationPhase.Phase,
    val provenance: OpeningProvenance,
    val reason: String? = null,
    val legacyMigration: LegacyMigrationProvenance? = null,
)

enum class LedgerEntryType { Purchase, Undo, Grant, ManualCorrection, NonFinancial, CompleteCreation, ConfirmLegacyParameter }
data class RuleVersions(val catalogRevision: String, val resolutionRevision: String)

class LedgerEntry(
    val operationId: String,
    val type: LedgerEntryType,
    changes: List<AcquisitionChange>,
    val amountDelta: Amount,
    val ruleVersions: RuleVersions,
    val reversesId: String? = null,
    val creationPhase: RequirementNode.CreationPhase.Phase,
    /** Source/discount evidence captured by the command, never recalculated by the codec. */
    val evidenceCanonical: String = "{}",
) {
    val changes: List<AcquisitionChange> = EnvelopeList(changes)
    override fun equals(other: Any?): Boolean = other is LedgerEntry && operationId == other.operationId &&
        type == other.type && changes == other.changes && amountDelta == other.amountDelta && ruleVersions == other.ruleVersions &&
        reversesId == other.reversesId && creationPhase == other.creationPhase && evidenceCanonical == other.evidenceCanonical
    override fun hashCode(): Int = listOf(operationId, type, changes, amountDelta, ruleVersions, reversesId, creationPhase, evidenceCanonical).hashCode()
    fun copy(operationId: String = this.operationId, type: LedgerEntryType = this.type,
        changes: List<AcquisitionChange> = this.changes, amountDelta: Amount = this.amountDelta,
        ruleVersions: RuleVersions = this.ruleVersions, reversesId: String? = this.reversesId,
        creationPhase: RequirementNode.CreationPhase.Phase = this.creationPhase,
        evidenceCanonical: String = this.evidenceCanonical) =
        LedgerEntry(operationId, type, changes, amountDelta, ruleVersions, reversesId, creationPhase, evidenceCanonical)
}

class Ledger(val opening: Opening, entries: List<LedgerEntry>) {
    val entries: List<LedgerEntry> = EnvelopeList(entries)
    override fun equals(other: Any?): Boolean = other is Ledger && opening == other.opening && entries == other.entries
    override fun hashCode(): Int = 31 * opening.hashCode() + entries.hashCode()
    fun copy(opening: Opening = this.opening, entries: List<LedgerEntry> = this.entries) = Ledger(opening, entries)
}

/** Exact checked accumulation; no legacy calculator, catalog lookup or price recomputation. */
fun balance(ledger: Ledger): Amount = ledger.entries.fold(ledger.opening.balance) { total, entry -> total + entry.amountDelta }

/** Read-only defensive copy, including a non-mutable public list interface on JVM and JS. */
internal class EnvelopeList<T>(source: List<T>) : AbstractList<T>() {
    private val captured = source.toList()
    override val size: Int get() = captured.size
    override fun get(index: Int): T = captured[index]
}
