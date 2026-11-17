package com.furybook.dubl.ui.sheet

import com.furybook.dubl.content.resourceCurrent
import com.furybook.dubl.model.CharacterConditionId
import com.furybook.dubl.model.CharacterSheetResourceId

/**
 * Wraps a platform host so the shared sheet's changes reach the platform's existing undo toast.
 * It reports the same changes the old sheets announced (built-in resource steps and condition
 * toggles, each recorded by the application's one-step undo); undo itself stays [undoLast].
 */
class SheetChangeReportingHost(
    private val delegate: DublSheetHost,
    private val onChange: (String) -> Unit,
) : DublSheetHost by delegate {
    override fun changeResource(resource: CharacterSheetResourceId, delta: Int) {
        val before = delegate.active.resourceCurrent(resource)
        delegate.changeResource(resource, delta)
        val applied = delegate.active.resourceCurrent(resource) - before
        if (applied != 0) onChange("${resource.title} ${if (applied > 0) "+$applied" else applied.toString()}")
    }

    override fun toggleCondition(condition: CharacterConditionId) {
        val wasActive = condition in delegate.extras.activeConditions
        delegate.toggleCondition(condition)
        val isActive = condition in delegate.extras.activeConditions
        if (isActive != wasActive) {
            onChange(if (isActive) "Добавлено состояние: ${condition.title}" else "Убрано состояние: ${condition.title}")
        }
    }
}
