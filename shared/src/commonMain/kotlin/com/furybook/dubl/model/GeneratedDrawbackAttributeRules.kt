package com.furybook.dubl.model

// Generated from rulesets/dubl-3.69/acquisition/native_drawback_rules.json.
// Effective modifiers only: purchased bases, paid history and initial AP remain unchanged.
internal fun DublCharacter.drawbackAttributeModifier(attribute: AttributeId): Int = when (attribute) {
    AttributeId.CHARISMA -> -developmentRank("drawback_10c5fa534ffe4613")
    AttributeId.CONSTITUTION -> -developmentRank("drawback_2523e7a7a3a97101")
    AttributeId.DEXTERITY -> -developmentRank("drawback_a0f942e069cc9556")
    AttributeId.INTELLIGENCE -> -developmentRank("drawback_de922465326abd46")
    AttributeId.PERCEPTION -> -developmentRank("drawback_8ec48b76b983d1a2")
    AttributeId.SPEED -> -developmentRank("drawback_e2fa776810fa04f3")
    AttributeId.STRENGTH -> -developmentRank("drawback_181a437917c503e7")
    AttributeId.WILL -> -developmentRank("drawback_3399d4ec1a5975fc")
}

internal fun drawbackAttributeAffectedBy(entryId: String): AttributeId? = when (entryId) {
    "drawback_181a437917c503e7" -> AttributeId.STRENGTH
    "drawback_2523e7a7a3a97101" -> AttributeId.CONSTITUTION
    "drawback_a0f942e069cc9556" -> AttributeId.DEXTERITY
    "drawback_e2fa776810fa04f3" -> AttributeId.SPEED
    "drawback_de922465326abd46" -> AttributeId.INTELLIGENCE
    "drawback_3399d4ec1a5975fc" -> AttributeId.WILL
    "drawback_10c5fa534ffe4613" -> AttributeId.CHARISMA
    "drawback_8ec48b76b983d1a2" -> AttributeId.PERCEPTION
    else -> null
}
