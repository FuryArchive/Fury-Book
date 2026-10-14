package com.furybook.dubl.actions

/** Persisted metadata only. Current resources stay exclusively on DublCharacter. */
class GameplayJournal(
    val revision: Long = 0,
    val pending: PendingPhysicalCheck? = null,
    receipts: List<GameplayReceipt> = emptyList(),
    val lastCommittedStorageRevision: Long? = null,
) {
    private val saved = receipts.toList()
    val receipts: List<GameplayReceipt> get() = saved.toList()
    fun copy(revision: Long = this.revision, pending: PendingPhysicalCheck? = this.pending,
        receipts: List<GameplayReceipt> = this.receipts,
        lastCommittedStorageRevision: Long? = this.lastCommittedStorageRevision) =
        GameplayJournal(revision, pending, receipts, lastCommittedStorageRevision)
    override fun equals(other: Any?): Boolean = other is GameplayJournal &&
        revision == other.revision && pending == other.pending && saved == other.saved &&
        lastCommittedStorageRevision == other.lastCommittedStorageRevision
    override fun hashCode(): Int = listOf(revision, pending, saved, lastCommittedStorageRevision).hashCode()

    /** Re-execute each typed receipt, never trust a claimed cost or success marker. */
    fun validate(characterId: String) {
        require(revision >= 0 && characterId.isNotBlank())
        require(lastCommittedStorageRevision == null || lastCommittedStorageRevision >= 0)
        require(saved.size.toLong() == revision) { "Gameplay history is truncated" }
        require(saved.map { it.request.operationId }.distinct().size == saved.size) { "Duplicate gameplay ID" }
        var previous: PendingPhysicalCheck? = null
        saved.forEachIndexed { index, receipt ->
            require(receipt.request.characterId == characterId && receipt.beforeRevision == index.toLong())
            require(receipt.pendingBefore == previous) { "Broken pending action chain" }
            val before = GameplayState(characterId, index.toLong(), receipt.enduranceBefore,
                receipt.weakBefore, receipt.resourceConsequencesKnownBefore, receipt.pendingBefore, saved.take(index))
            val replay = when (val request = receipt.request) {
                is StartOverexertion -> GameplayActions.start(before, request)
                is ResolvePhysicalCheck -> GameplayActions.resolve(before, request)
                is UndoGameplay -> GameplayActions.undo(before, request)
            }
            require(replay is ActionTransition.Applied && replay.receipt == receipt) { "Invalid gameplay receipt" }
            previous = receipt.pendingAfter
        }
        require(previous == pending) { "Pending action differs from history" }
    }

    companion object {
        fun from(state: GameplayState, storageRevision: Long? = null) =
            GameplayJournal(state.revision, state.pending, state.receipts, storageRevision)
    }
}
