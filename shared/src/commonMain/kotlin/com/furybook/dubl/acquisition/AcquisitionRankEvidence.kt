package com.furybook.dubl.acquisition

/** A reviewed first purchase does not establish an omitted maximum or authorize later ranks. */
internal fun confirmedPurchaseRankCeiling(definition: AcquisitionDefinition): Int? = definition.maxRank ?: 1.takeIf {
    definition.sourceStatus != SourceStatus.Unavailable && definition.repeatPolicy == RepeatPolicy.Single &&
        definition.classificationSources["purchase:initial-rank-confirmed"]?.originalText?.isNotBlank() == true
}
