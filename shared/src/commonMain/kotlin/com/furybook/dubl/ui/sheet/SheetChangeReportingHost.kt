package com.furybook.dubl.ui.sheet

import com.furybook.dubl.content.resourceCurrent
import com.furybook.dubl.model.CharacterConditionId
import com.furybook.dubl.model.CharacterSheetResourceId

/** A change made on the play sheet that the platform's undo toast announces. */
sealed interface SheetChange {
    /** Desktop wording; Android words resource steps its own way. */
    val text: String

    data class Resource(val resource: CharacterSheetResourceId, val applied: Int) : SheetChange {
        override val text: String get() = "${resource.title} ${if (applied > 0) "+$applied" else applied.toString()}"
    }

    data class Condition(val condition: CharacterConditionId, val added: Boolean) : SheetChange {
        override val text: String
            get() = if (added) "Добавлено состояние: ${condition.title}" else "Убрано состояние: ${condition.title}"
    }
}

/**
 * Wraps a platform host so the shared sheet's changes reach the platform's existing undo toast.
 * It reports the same changes the old sheets announced (built-in resource steps and condition
 * toggles, each recorded by the application's one-step undo); undo itself stays [undoLast].
 */
class SheetChangeReportingHost(
    private val delegate: DublSheetHost,
    private val onChange: (SheetChange) -> Unit,
) : DublSheetHost by delegate {
    override fun changeResource(resource: CharacterSheetResourceId, delta: Int) {
        val before = delegate.active.resourceCurrent(resource)
        delegate.changeResource(resource, delta)
        val applied = delegate.active.resourceCurrent(resource) - before
        if (applied != 0) onChange(SheetChange.Resource(resource, applied))
    }

    override fun toggleCondition(condition: CharacterConditionId) {
        val wasActive = condition in delegate.extras.activeConditions
        delegate.toggleCondition(condition)
        val isActive = condition in delegate.extras.activeConditions
        if (isActive != wasActive) onChange(SheetChange.Condition(condition, added = isActive))
    }
}
