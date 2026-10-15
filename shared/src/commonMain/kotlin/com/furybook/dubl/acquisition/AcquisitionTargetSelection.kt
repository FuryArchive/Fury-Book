package com.furybook.dubl.acquisition

/** A definition opens an explicit instance choice; an exact holding opens that holding only. */
fun acquisitionTargetForSelection(view: AcquisitionCharacterView, kind: AcquisitionKind, id: String, rank: Int): AcquisitionTarget? {
    val holding = view.state.instances.singleOrNull { it.instanceId == id }
    val definition = if (holding != null) view.catalog.definitions[holding.definition]?.takeIf { it.kind == kind }
        else view.catalog.definitions.values.singleOrNull { it.kind == kind && it.ref.entryId == id }
    definition ?: return null
    val owned = holding ?: if (definition.repeatPolicy == RepeatPolicy.Single)
        view.state.instances.singleOrNull { it.definition == definition.ref } else null
    val requestedRank = if (holding == null && definition.repeatPolicy != RepeatPolicy.Single) 1 else rank
    val paymentChoice = definition.classificationSources["payment:transition-options"]?.originalText?.isNotBlank() == true
    return AcquisitionTarget(definition.ref, owned?.instanceId, requestedRank, owned?.parameters.orEmpty(),
        if (paymentChoice) null else owned?.abilityOptionId)
}
