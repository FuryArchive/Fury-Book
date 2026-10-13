package com.furybook.android.ui.acquisition

import com.furybook.dubl.acquisition.*

/** Typed display only. Missing evidence is never rendered as false or numeric zero. */
fun acquisitionRequirementValue(value: RequirementValue?, label: String): String = when (value) {
    null -> "не установлено"
    is RequirementValue.Number -> value.value.toString()
    is RequirementValue.Flag -> if (value.value) "да" else "нет"
    is RequirementValue.Phase -> when (value.value) {
        RequirementNode.CreationPhase.Phase.Creating -> "создание персонажа"
        RequirementNode.CreationPhase.Phase.Finalized -> "после создания"
    }
    is RequirementValue.StableId -> label // Exact model-bound readable label, never a new catalog lookup or raw ID.
}
