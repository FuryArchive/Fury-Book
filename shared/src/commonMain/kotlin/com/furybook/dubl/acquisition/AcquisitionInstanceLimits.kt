package com.furybook.dubl.acquisition

import com.furybook.dubl.model.AttributeId

/** Source ratio: owned instance count * denominator <= effective attribute * numerator.
 * This governs additional copies; old over-limit ownership is retained without repricing.
 */
data class InstanceCountLimit(val sourceRef: RuleSourceRef, val attributeId: AttributeId,
    val numerator: Int, val denominator: Int) {
    init {
        require(sourceRef.originalText.isNotBlank())
        require(numerator > 0 && denominator > 0)
    }
}

internal fun instanceCountLimitIssue(definition: AcquisitionDefinition, context: AcquisitionContext,
    additionalCount: Int = 0): AcquisitionIssue? {
    val limit = definition.instanceCountLimit ?: return null
    require(additionalCount >= 0)
    val proposed = context.instances.count { it.definition == definition.ref }.toLong() + additionalCount
    val attribute = context.character.attribute(limit.attributeId).toLong()
    return if (proposed * limit.denominator <= attribute * limit.numerator) null else
        AcquisitionIssue(RequirementStatus.Unsatisfied, "The proposed number of instances exceeds the source attribute limit",
            definition.ref, definition.ref.entryId + "/instance-count-limit", limit.sourceRef)
}
