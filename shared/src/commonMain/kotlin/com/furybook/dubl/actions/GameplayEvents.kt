package com.furybook.dubl.actions

/** Explicit boundaries only. View modes and wall-clock passage are not game events. */
sealed interface GameBoundary {
    val boundaryId: String
    data class TurnStarted(override val boundaryId: String, val actorId: String) : GameBoundary
    data class TurnEnded(override val boundaryId: String, val actorId: String, val startedBoundaryId: String) : GameBoundary
    data class RoundEnded(override val boundaryId: String) : GameBoundary
    data class SceneEnded(override val boundaryId: String) : GameBoundary
    data class RestCompleted(override val boundaryId: String, val kind: RestKind, val minutes: Int) : GameBoundary
}
enum class RestKind { BRIEF, FULL, OTHER }
data class RecordGameBoundary(val operationId: String, val characterId: String, val expectedRevision: Long,
    val boundary: GameBoundary)

/** A source-specific action must create an instance; this lifecycle layer cannot invent its cost/modifier. */
sealed interface EffectExpiry {
    data class NextOwnTurnStart(val afterOrdinal: Long) : EffectExpiry
    data class OwnTurnEnd(val ordinal: Long) : EffectExpiry
    data class RoundEnds(val afterOrdinal: Long, val count: Int) : EffectExpiry
    data object SceneEnd : EffectExpiry
    data object Indefinite : EffectExpiry
    data class Unresolved(val source: String, val reason: String) : EffectExpiry
}
data class TimedEffectInstance(val instanceId: String, val sourceRule: String, val createdAtRevision: Long,
    val expiry: EffectExpiry)
data class OpenGameTurn(val actorId: String, val startedBoundaryId: String)
data class GameBoundaryReceipt(val request: RecordGameBoundary, val expiredEffectIds: List<String>,
    val blockedEffectIds: List<String>, val afterRevision: Long)

/** Pure lifecycle state, not an independently persisted sidecar or an AP/HP/endurance account. */
class GameplayTimeline(val characterId: String, val revision: Long = 0, val ownTurnOrdinal: Long = 0,
    val roundOrdinal: Long = 0, val openTurn: OpenGameTurn? = null,
    effects: List<TimedEffectInstance> = emptyList(), receipts: List<GameBoundaryReceipt> = emptyList()) {
    private val savedEffects = effects.toList()
    private val savedReceipts = receipts.map { it.copy(expiredEffectIds = it.expiredEffectIds.toList(), blockedEffectIds = it.blockedEffectIds.toList()) }
    val effects get() = savedEffects.toList()
    val receipts get() = savedReceipts.map { it.copy(expiredEffectIds = it.expiredEffectIds.toList(), blockedEffectIds = it.blockedEffectIds.toList()) }
    init {
        require(characterId.isNotBlank() && revision >= 0 && ownTurnOrdinal >= 0 && roundOrdinal >= 0)
        require(savedEffects.map { it.instanceId }.distinct().size == savedEffects.size)
        require(savedReceipts.map { it.request.operationId }.distinct().size == savedReceipts.size)
        require(savedReceipts.map { it.request.boundary.boundaryId }.distinct().size == savedReceipts.size)
        require(savedReceipts.size.toLong() == revision)
        var replayOwn = 0L; var replayRound = 0L; var replayOpen: OpenGameTurn? = null
        savedReceipts.forEachIndexed { index, receipt ->
            require(receipt.request.characterId == characterId && receipt.request.expectedRevision == index.toLong() && receipt.afterRevision == index + 1L)
            require(receipt.request.operationId.isNotBlank() && receipt.request.boundary.boundaryId.isNotBlank())
            require(receipt.expiredEffectIds.all { it.isNotBlank() } && receipt.expiredEffectIds.distinct().size == receipt.expiredEffectIds.size)
            require(receipt.blockedEffectIds.all { it.isNotBlank() } && receipt.blockedEffectIds.distinct().size == receipt.blockedEffectIds.size)
            require(receipt.expiredEffectIds.none { it in receipt.blockedEffectIds })
            when (val event = receipt.request.boundary) {
                is GameBoundary.TurnStarted -> {
                    require(event.actorId.isNotBlank() && replayOpen == null)
                    if (event.actorId == characterId) replayOwn++
                    replayOpen = OpenGameTurn(event.actorId, event.boundaryId)
                }
                is GameBoundary.TurnEnded -> {
                    require(replayOpen == OpenGameTurn(event.actorId, event.startedBoundaryId)); replayOpen = null
                }
                is GameBoundary.RoundEnded -> replayRound++
                is GameBoundary.SceneEnded -> Unit
                is GameBoundary.RestCompleted -> require(event.minutes > 0)
            }
        }
        require(ownTurnOrdinal == replayOwn && roundOrdinal == replayRound && openTurn == replayOpen) { "Timeline counters differ from explicit events" }
        val expiredIds = savedReceipts.flatMap { it.expiredEffectIds }.toSet()
        require(savedEffects.none { it.instanceId in expiredIds }) { "Expired effect ID cannot be resurrected" }
        savedEffects.forEach {
            require(it.instanceId.isNotBlank() && it.sourceRule.isNotBlank() && it.createdAtRevision in 0..revision)
            when (val expiry = it.expiry) {
                is EffectExpiry.NextOwnTurnStart -> require(expiry.afterOrdinal == ownTurnOrdinal)
                is EffectExpiry.OwnTurnEnd -> require(expiry.ordinal > ownTurnOrdinal || (expiry.ordinal == ownTurnOrdinal && expiry.ordinal > 0 && openTurn?.actorId == characterId))
                is EffectExpiry.RoundEnds -> require(expiry.afterOrdinal in 0..roundOrdinal && expiry.count > 0 && roundOrdinal - expiry.afterOrdinal < expiry.count)
                is EffectExpiry.Unresolved -> require(expiry.source.isNotBlank() && expiry.reason.isNotBlank())
                else -> Unit
            }
        }
        openTurn?.let { require(it.actorId.isNotBlank() && it.startedBoundaryId.isNotBlank()) }
    }
}
sealed interface BoundaryTransition {
    data class Applied(val timeline: GameplayTimeline, val receipt: GameBoundaryReceipt) : BoundaryTransition
    data class Replayed(val receipt: GameBoundaryReceipt) : BoundaryTransition
    data class Blocked(val reason: String) : BoundaryTransition
}

object GameplayEvents {
    fun apply(state: GameplayTimeline, request: RecordGameBoundary): BoundaryTransition {
        state.receipts.firstOrNull { it.request.operationId == request.operationId }?.let {
            return if (it.request == request) BoundaryTransition.Replayed(it) else BoundaryTransition.Blocked("Event ID reused with different input")
        }
        fun blocked(reason: String) = BoundaryTransition.Blocked(reason)
        if (request.operationId.isBlank() || request.characterId != state.characterId || request.expectedRevision != state.revision || state.revision == Long.MAX_VALUE)
            return blocked("Event identity or revision changed")
        val event = request.boundary
        if (event.boundaryId.isBlank() || state.receipts.any { it.request.boundary.boundaryId == event.boundaryId })
            return blocked("Boundary identity is missing or already recorded")
        var own = state.ownTurnOrdinal; var round = state.roundOrdinal; var open = state.openTurn
        when (event) {
            is GameBoundary.TurnStarted -> {
                if (event.actorId.isBlank() || open != null) return blocked("Close the recorded turn before starting another")
                if (event.actorId == state.characterId) {
                    if (own == Long.MAX_VALUE) return blocked("Own-turn counter overflow")
                    own++
                }
                open = OpenGameTurn(event.actorId, event.boundaryId)
            }
            is GameBoundary.TurnEnded -> {
                if (open != OpenGameTurn(event.actorId, event.startedBoundaryId)) return blocked("Turn end does not match the open turn")
                open = null
            }
            is GameBoundary.RoundEnded -> {
                if (round == Long.MAX_VALUE) return blocked("Round counter overflow")
                round++
            }
            is GameBoundary.RestCompleted -> if (event.minutes <= 0) return blocked("Rest needs a positive explicit duration")
            is GameBoundary.SceneEnded -> Unit
        }
        val expired = mutableListOf<String>(); val unresolved = mutableListOf<String>()
        for (effect in state.effects) {
            val end = when (val expiry = effect.expiry) {
                is EffectExpiry.NextOwnTurnStart -> event is GameBoundary.TurnStarted && event.actorId == state.characterId && own > expiry.afterOrdinal
                is EffectExpiry.OwnTurnEnd -> event is GameBoundary.TurnEnded && event.actorId == state.characterId && own >= expiry.ordinal
                is EffectExpiry.RoundEnds -> event is GameBoundary.RoundEnded && round - expiry.afterOrdinal >= expiry.count
                EffectExpiry.SceneEnd -> event is GameBoundary.SceneEnded
                EffectExpiry.Indefinite -> false
                is EffectExpiry.Unresolved -> { unresolved += effect.instanceId; false }
            }
            if (end) expired += effect.instanceId
        }
        val receipt = GameBoundaryReceipt(request, expired.toList(), unresolved.toList(), state.revision + 1)
        return BoundaryTransition.Applied(GameplayTimeline(state.characterId, state.revision + 1, own, round, open,
            state.effects.filterNot { it.instanceId in expired }, state.receipts + receipt), receipt)
    }
}
