package com.furybook.dubl.acquisition

/** Display text is captured beside the identity; it is never used to infer a prerequisite witness. */
object AcquisitionSkillSpecializations {
    const val ID_PARAMETER = "specialization"
    const val NAME_PARAMETER = "specializationName"

    fun supported(definition: AcquisitionDefinition): Boolean = definition.kind == AcquisitionKind.SKILL &&
        definition.parameterDefinitions.map { it.key }.containsAll(listOf(ID_PARAMETER, NAME_PARAMETER))

    fun choices(definition: AcquisitionDefinition): List<ParameterChoice> =
        definition.parameterDefinitions.singleOrNull { it.key == ID_PARAMETER }?.choices.orEmpty()

    /** Allocate instanceId once at the explicit new-choice action, then retain this target for all quotes. */
    fun newTarget(definition: AcquisitionDefinition, instanceId: String, name: String, targetRank: Int = 1,
        choiceId: String? = null): AcquisitionTarget {
        require(supported(definition)) { "This skill does not define a specialization choice" }
        require(instanceId.isNotBlank()) { "A specialization requires a captured instance identity" }
        val choice = choiceId?.let { id -> choices(definition).single { it.id == id } }
        val displayName = choice?.label ?: cleanName(name)
        require(displayName.isNotBlank()) { "Name the specialization" }
        return AcquisitionTarget(definition.ref, instanceId, targetRank,
            mapOf(ID_PARAMETER to (choice?.id ?: "custom:$instanceId"), NAME_PARAMETER to displayName))
    }

    internal fun displayName(definition: AcquisitionDefinition, parameters: Map<String, String>): String? {
        if (!supported(definition)) return null
        val name = parameters[NAME_PARAMETER]?.takeIf { it.isNotBlank() } ?: return null
        val label = definition.name.takeIf { it.isNotBlank() && it != definition.ref.entryId } ?: return null
        return "${label.removeSuffix(" (Любое)")} ($name)"
    }

    internal fun cleanName(value: String): String = value.trim().replace(Regex("\\s+"), " ")
}

/** Duplicate display choices are rejected visibly, never silently mapped to another owned identity. */
internal fun skillSpecializationSelectionIssue(definitions: List<ParameterDefinition>, parameters: Map<String, String>,
    owned: List<AcquisitionInstance>, existingId: String?): Pair<RequirementStatus, String>? {
    val identity = definitions.singleOrNull { it.key == AcquisitionSkillSpecializations.ID_PARAMETER } ?: return null
    if (definitions.none { it.key == AcquisitionSkillSpecializations.NAME_PARAMETER }) return null
    val id = parameters[AcquisitionSkillSpecializations.ID_PARAMETER] ?: return null
    val name = parameters[AcquisitionSkillSpecializations.NAME_PARAMETER] ?: return null
    val cleaned = AcquisitionSkillSpecializations.cleanName(name)
    if (cleaned.isBlank()) return RequirementStatus.ChoiceRequired to "Name the specialization"
    if (identity.excludedLabels.any { it.label.equals(cleaned, ignoreCase = true) })
        return RequirementStatus.Conflict to "This specialization has its own skill definition; select that skill"
    val choice = identity.choices.singleOrNull { it.id == id }
    if (choice != null && name != choice.label) return RequirementStatus.Conflict to "The specialization name differs from its selected source choice"
    if (choice == null && identity.choices.any { it.label.equals(cleaned, ignoreCase = true) })
        return RequirementStatus.ChoiceRequired to "Select the named specialization explicitly"
    if (existingId == null && owned.any { it.parameters[AcquisitionSkillSpecializations.NAME_PARAMETER]
            ?.let(AcquisitionSkillSpecializations::cleanName)?.equals(cleaned, ignoreCase = true) == true })
        return RequirementStatus.Conflict to "A specialization with this name already exists; select its instance"
    return null
}

/** Invalid explicit aliases retain their saved ownership but cannot become extra knowledge witnesses. */
internal fun excludedSkillSpecialization(definition: AcquisitionDefinition, parameters: Map<String, String>): Boolean {
    if (!AcquisitionSkillSpecializations.supported(definition)) return false
    val name=parameters[AcquisitionSkillSpecializations.NAME_PARAMETER]?.let(AcquisitionSkillSpecializations::cleanName) ?: return false
    return definition.parameterDefinitions.single { it.key==AcquisitionSkillSpecializations.ID_PARAMETER }.excludedLabels
        .any { it.label.equals(name, ignoreCase=true) }
}
