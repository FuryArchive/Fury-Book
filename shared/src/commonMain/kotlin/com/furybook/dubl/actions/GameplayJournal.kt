package com.furybook.dubl.actions

/** Persisted metadata only. Current resources stay exclusively on DublCharacter. */
class GameplayJournal(
    val revision: Long = 0,
    val pending: PendingPhysicalCheck? = null,
    receipts: List<GameplayReceipt> = emptyList(),
) {
    private val saved = receipts.toList()
    val receipts: List<GameplayReceipt> get() = saved.toList()
    fun copy(revision: Long = this.revision, pending: PendingPhysicalCheck? = this.pending,
        receipts: List<GameplayReceipt> = this.receipts) = GameplayJournal(revision, pending, receipts)
    override fun equals(other: Any?): Boolean = other is GameplayJournal &&
        revision == other.revision && pending == other.pending && saved == other.saved
    override fun hashCode(): Int = listOf(revision, pending, saved).hashCode()

    /** Re-execute each typed receipt, never trust a claimed cost or success marker. */
    fun validate(characterId: String) {
        require(revision >= 0 && characterId.isNotBlank())
        require(saved.size.toLong() == revision) { "Gameplay history is truncated" }
        require(saved.map { it.request.operationId }.distinct().size == saved.size) { "Duplicate gameplay ID" }
        var previous: PendingPhysicalCheck? = null
        saved.forEachIndexed { index, receipt ->
            require(receipt.request.characterId == characterId && receipt.beforeRevision == index.toLong())
            require(receipt.pendingBefore == previous) { "Broken pending action chain" }
            val before = GameplayState(characterId, index.toLong(), receipt.enduranceBefore,
                receipt.weakBefore, receipt.resourceConsequencesKnownBefore, receipt.pendingBefore)
            val replay = when (val request = receipt.request) {
                is StartOverexertion -> GameplayActions.start(before, request)
                is ResolvePhysicalCheck -> GameplayActions.resolve(before, request)
            }
            require(replay is ActionTransition.Applied && replay.receipt == receipt) { "Invalid gameplay receipt" }
            previous = receipt.pendingAfter
        }
        require(previous == pending) { "Pending action differs from history" }
    }

    companion object {
        fun from(state: GameplayState) = GameplayJournal(state.revision, state.pending, state.receipts)
    }
}
