package com.furybook.dubl.actions

/** P127's closed set. This is not an arbitrary skill/attack attribute selector. */
enum class PhysicalAttribute { STRENGTH, DEXTERITY, SPEED, CONSTITUTION }

sealed interface GameplayRequest {
    val operationId: String
    val characterId: String
    val expectedRevision: Long
}

data class StartOverexertion(
    override val operationId: String,
    override val characterId: String,
    override val expectedRevision: Long,
    val attribute: PhysicalAttribute,
) : GameplayRequest

/** P127 does not require natural faces or a degree of success. */
data class PhysicalCheckResult(val success: Boolean)

data class ResolvePhysicalCheck(
    override val operationId: String,
    override val characterId: String,
    override val expectedRevision: Long,
    val startOperationId: String,
    val result: PhysicalCheckResult,
) : GameplayRequest

data class UndoGameplay(
    override val operationId: String,
    override val characterId: String,
    override val expectedRevision: Long,
    val targetOperationId: String,
) : GameplayRequest

data class PendingPhysicalCheck(
    val startOperationId: String,
    val attribute: PhysicalAttribute,
    val ruleVersion: String,
    val endurancePaid: Int,
    val advantageDice: Int,
)

data class GameplayReceipt(
    val request: GameplayRequest,
    val beforeRevision: Long,
    val afterRevision: Long,
    val enduranceBefore: Int,
    val enduranceAfter: Int,
    val pendingBefore: PendingPhysicalCheck?,
    val pendingAfter: PendingPhysicalCheck?,
    val weakBefore: Boolean,
    val weakAfter: Boolean,
    val resourceConsequencesKnownBefore: Boolean,
)

/**
 * Pure transition input/output, NOT a separate persisted resource account.
 * The eventual envelope adapter must read endurance from DublCharacter and commit the
 * resulting character and action history together. No UI/store writes use this type yet.
 * resourceConsequencesKnown is fail-closed for unresolved tired/exhausted interactions.
 */
class GameplayState(
    val characterId: String,
    val revision: Long,
    val endurance: Int,
    val weak: Boolean = false,
    val resourceConsequencesKnown: Boolean = true,
    val pending: PendingPhysicalCheck? = null,
    receipts: List<GameplayReceipt> = emptyList(),
) {
    private val savedReceipts = receipts.toList()
    val receipts: List<GameplayReceipt> get() = savedReceipts.toList()
    init {
        require(characterId.isNotBlank() && revision >= 0 && endurance >= 0)
        require(savedReceipts.map { it.request.operationId }.distinct().size == savedReceipts.size)
        require(savedReceipts.all { it.request.characterId == characterId })
    }
    fun copy(
        characterId: String = this.characterId,
        revision: Long = this.revision,
        endurance: Int = this.endurance,
        weak: Boolean = this.weak,
        resourceConsequencesKnown: Boolean = this.resourceConsequencesKnown,
        pending: PendingPhysicalCheck? = this.pending,
        receipts: List<GameplayReceipt> = this.receipts,
    ) = GameplayState(characterId, revision, endurance, weak, resourceConsequencesKnown, pending, receipts)
}

data class ActionPreview(
    val allowed: Boolean,
    val reason: String?,
    val enduranceCost: Int = 1,
    val advantageDice: Int = 1,
    val ruleVersion: String = GameplayActions.OVEREXERTION_VERSION,
)

sealed interface ActionTransition {
    data class Applied(val state: GameplayState, val receipt: GameplayReceipt) : ActionTransition
    /** Exact original outcome. This never rewinds or replaces the caller's current state. */
    data class Replayed(val receipt: GameplayReceipt, val undone: Boolean = false) : ActionTransition
    data class Blocked(val reason: String) : ActionTransition
}

object GameplayActions {
    const val OVEREXERTION_VERSION = "dubl-3.69:P125,P127,P14776-P14781:v1"

    fun preview(state: GameplayState, request: StartOverexertion): ActionPreview {
        val reason = identityProblem(state, request) ?: when {
            state.pending != null -> "Сначала завершите ожидающую физическую проверку"
            !state.resourceConsequencesKnown -> "Последствия расхода выносливости требуют решения по источнику"
            state.weak || state.endurance == 0 -> "Слабость запрещает тратить выносливость (P14781)"
            else -> null
        }
        return ActionPreview(reason == null, reason)
    }

    fun start(state: GameplayState, request: StartOverexertion): ActionTransition {
        replay(state, request)?.let { return it }
        val preview = preview(state, request)
        if (!preview.allowed) return ActionTransition.Blocked(checkNotNull(preview.reason))
        val remaining = state.endurance - preview.enduranceCost
        val pending = PendingPhysicalCheck(request.operationId, request.attribute, preview.ruleVersion,
            preview.enduranceCost, preview.advantageDice)
        return applied(state, request, remaining, pending, state.weak || remaining == 0)
    }

    fun resolve(state: GameplayState, request: ResolvePhysicalCheck): ActionTransition {
        replay(state, request)?.let { return it }
        identityProblem(state, request)?.let { return ActionTransition.Blocked(it) }
        val pending = state.pending ?: return ActionTransition.Blocked("Нет ожидающей физической проверки")
        if (pending.startOperationId != request.startOperationId)
            return ActionTransition.Blocked("Результат относится к другому действию")
        if (pending.ruleVersion != OVEREXERTION_VERSION)
            return ActionTransition.Blocked("Закреплённая версия правила не поддерживается")
        // Failure does not refund P125 expenditure. The result is retained in its typed receipt.
        return applied(state, request, state.endurance, null, state.weak)
    }

    fun undo(state: GameplayState, request: UndoGameplay): ActionTransition {
        replay(state, request)?.let { return it }
        identityProblem(state, request)?.let { return ActionTransition.Blocked(it) }
        val target = state.receipts.lastOrNull()
            ?: return ActionTransition.Blocked("Нет последнего действия для отмены")
        if (target.request.operationId != request.targetOperationId || target.request is UndoGameplay)
            return ActionTransition.Blocked("Можно отменить только последнюю независимую операцию, не саму отмену")
        if (target.afterRevision != state.revision || target.enduranceAfter != state.endurance ||
            target.pendingAfter != state.pending || target.weakAfter != state.weak ||
            target.resourceConsequencesKnownBefore != state.resourceConsequencesKnown)
            return ActionTransition.Blocked("Состояние изменилось после действия; обратная операция небезопасна")
        return applied(state, request, target.enduranceBefore, target.pendingBefore, target.weakBefore)
    }

    private fun identityProblem(state: GameplayState, request: GameplayRequest): String? = when {
        request.operationId.isBlank() -> "Не задан идентификатор операции"
        request.characterId != state.characterId -> "Другой персонаж"
        request.expectedRevision != state.revision -> "Персонаж изменился: подтвердите новый расчёт"
        state.revision == Long.MAX_VALUE -> "Исчерпан диапазон версий"
        else -> null
    }

    private fun replay(state: GameplayState, request: GameplayRequest): ActionTransition? {
        val previous = state.receipts.find { it.request.operationId == request.operationId } ?: return null
        return if (previous.request == request) ActionTransition.Replayed(previous,
            state.receipts.any { (it.request as? UndoGameplay)?.targetOperationId == request.operationId })
        else ActionTransition.Blocked("Этот идентификатор уже использован для другой операции")
    }

    private fun applied(state: GameplayState, request: GameplayRequest, endurance: Int,
        pending: PendingPhysicalCheck?, weak: Boolean): ActionTransition.Applied {
        val receipt = GameplayReceipt(request, state.revision, state.revision + 1, state.endurance,
            endurance, state.pending, pending, state.weak, weak, state.resourceConsequencesKnown)
        val next = state.copy(revision = state.revision + 1, endurance = endurance, weak = weak,
            pending = pending, receipts = state.receipts + receipt)
        return ActionTransition.Applied(next, receipt)
    }
}
