package com.furybook.dubl.ui.sheet

import com.furybook.dubl.content.resourceCurrent
import com.furybook.dubl.model.AttributeId
import com.furybook.dubl.model.CharacterConditionId
import com.furybook.dubl.model.CharacterSheetResourceId

/** A change made on the play sheet that the platform's undo toast announces. */
sealed interface SheetChange {
    /** Desktop wording; Android words resource steps its own way. */
    val text: String

    data class Resource(val resource: CharacterSheetResourceId, val applied: Int) : SheetChange {
        override val text: String get() = "${resource.title} ${if (applied > 0) "+$applied" else applied.toString()}"
    }

    /** Desktop «<Характеристика> ±n». */
    data class Attribute(val attribute: AttributeId, val applied: Int) : SheetChange {
        override val text: String get() = "${attribute.title} ${if (applied > 0) "+$applied" else applied.toString()}"
    }

    /** Total XP changed on its own (Android «Общий опыт: a → b»). */
    data class Experience(val previous: Int, val next: Int) : SheetChange {
        override val text: String get() = "Общий опыт: $previous → $next"
    }

    /** Name, concept, size, legs or mana changed (desktop «Персонаж изменён»). */
    data object Identity : SheetChange {
        override val text: String get() = "Персонаж изменён"
    }

    data class Condition(val condition: CharacterConditionId, val added: Boolean) : SheetChange {
        override val text: String
            get() = if (added) "Добавлено состояние: ${condition.title}" else "Убрано состояние: ${condition.title}"
    }
}

/**
 * Wraps a platform host so the shared sheet's changes reach the platform's existing undo toast.
 * It reports the changes the application's one-step undo records and the old sheets announced:
 * built-in resource steps, condition toggles, attribute steps and identity edits; undo stays [undoLast].
 */
class SheetChangeReportingHost(
    private val delegate: DublSheetEditHost,
    private val onChange: (SheetChange) -> Unit,
) : DublSheetEditHost by delegate {
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

    override fun changeAttribute(id: AttributeId, delta: Int) {
        val before = delegate.active.attributes[id]?.base
        delegate.changeAttribute(id, delta)
        val after = delegate.active.attributes[id]?.base
        if (before != null && after != null && after != before) onChange(SheetChange.Attribute(id, after - before))
    }

    override fun setIdentity(name: String, concept: String, size: Int, legs: Int, manaEnabled: Boolean) {
        val before = delegate.active
        delegate.setIdentity(name, concept, size, legs, manaEnabled)
        if (delegate.active != before) onChange(SheetChange.Identity)
    }

    override fun setExperience(total: Int) {
        val before = delegate.active.experience
        delegate.setExperience(total)
        val after = delegate.active.experience
        if (after != before) onChange(SheetChange.Experience(before, after))
    }
}
