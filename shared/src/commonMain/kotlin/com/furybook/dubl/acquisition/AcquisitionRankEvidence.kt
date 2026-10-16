package com.furybook.dubl.acquisition

/** A reviewed first purchase does not establish an omitted maximum or authorize later ranks. */
internal fun confirmedPurchaseRankCeiling(definition: AcquisitionDefinition): Int? = definition.maxRank ?: 1.takeIf {
    definition.sourceStatus != SourceStatus.Unavailable && definition.repeatPolicy == RepeatPolicy.Single &&
        definition.classificationSources["purchase:initial-rank-confirmed"]?.originalText?.isNotBlank() == true
}

/** Only an existing school rank is recognized; this supplies no purchase cap or price. */
internal fun hasReferencedSchoolOwnership(definition: AcquisitionDefinition): Boolean =
    definition.kind == AcquisitionKind.SCHOOL && definition.maxRank == null &&
        definition.classificationSources["ownership:source-referenced-school-rank"]?.originalText?.isNotBlank() == true
