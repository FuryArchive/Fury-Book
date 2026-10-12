package com.furybook.dubl.acquisition

/** FCP identity: labels, translations and rank are deliberately absent. */
data class DefinitionRef(val packId: String, val entryId: String) {
    init {
        require(packId.isNotBlank()) { "A definition requires a pack ID" }
        require(entryId.isNotBlank()) { "A definition requires an entry ID" }
    }
}

sealed interface AcquisitionOrigin {
    data object New : AcquisitionOrigin

    /** T09 supplies the fingerprint of the exact original bytes; this layer does not hash or migrate. */
    data class Legacy(
        val characterId: String,
        val schema: String,
        val sourceRevision: String,
        val sourceFingerprint: String,
        val ownershipKey: String,
    ) : AcquisitionOrigin {
        init {
            require(listOf(characterId, schema, sourceRevision, sourceFingerprint, ownershipKey).all(String::isNotBlank)) {
                "Legacy identity requires explicit source provenance and ownership key"
            }
        }
    }
}

/** Rank belongs to this instance; rank limits are supplied by its catalog definition. */
class AcquisitionInstance(
    val instanceId: String,
    val definition: DefinitionRef,
    val rank: Int,
    parameters: Map<String, String>,
    val abilityOptionId: String?,
    val origin: AcquisitionOrigin,
) {
    val parameters: Map<String, String> = IdentityParameterSnapshot(parameters)

    init {
        require(instanceId.isNotBlank()) { "An instance requires a character-scoped ID" }
        require(rank > 0) { "An owned instance requires a positive rank" }
        require(this.parameters.all { (key, value) -> key.isNotBlank() && value.isNotBlank() }) {
            "Unknown parameters must be absent, never blank IDs"
        }
        require(abilityOptionId == null || abilityOptionId.isNotBlank()) { "An option ID cannot be blank" }
    }

    fun copy(
        instanceId: String = this.instanceId,
        definition: DefinitionRef = this.definition,
        rank: Int = this.rank,
        parameters: Map<String, String> = this.parameters,
        abilityOptionId: String? = this.abilityOptionId,
        origin: AcquisitionOrigin = this.origin,
    ): AcquisitionInstance = AcquisitionInstance(instanceId, definition, rank, parameters, abilityOptionId, origin)

    override fun equals(other: Any?): Boolean = other is AcquisitionInstance &&
        instanceId == other.instanceId && definition == other.definition && rank == other.rank &&
        parameters == other.parameters && abilityOptionId == other.abilityOptionId && origin == other.origin

    override fun hashCode(): Int = listOf(instanceId, definition, rank, parameters, abilityOptionId, origin).hashCode()
    override fun toString(): String =
        "AcquisitionInstance(instanceId=$instanceId, definition=$definition, rank=$rank, " +
            "parameters=$parameters, abilityOptionId=$abilityOptionId, origin=$origin)"
}

sealed interface ParameterDomain {
    class StableIds(valueIds: Set<String>) : ParameterDomain {
        val valueIds: Set<String> = IdentitySetSnapshot(valueIds)
        init { require(this.valueIds.isNotEmpty() && this.valueIds.all(String::isNotBlank)) }
    }

    /** User-defined knowledge/skill identities are supplied by the caller, never inferred from names. */
    data object UserDefinedIds : ParameterDomain
    data class Unresolved(val reason: String) : ParameterDomain {
        init { require(reason.isNotBlank()) }
    }
}

data class ParameterDefinition(val key: String, val domain: ParameterDomain) {
    init { require(key.isNotBlank()) }
}

/** Canonical type owned by T03; T04 assigns these policies from confirmed catalog sources. */
sealed interface RepeatPolicy {
    data object Single : RepeatPolicy

    /** Keys form a tuple; uniqueness is required only when the source explicitly requires it. */
    class DistinctParameter(parameterKeys: Set<String>, val requireUnique: Boolean = true) : RepeatPolicy {
        val parameterKeys: Set<String> = IdentitySetSnapshot(parameterKeys)
        init { require(this.parameterKeys.isNotEmpty() && this.parameterKeys.all(String::isNotBlank)) }
    }

    data class Unresolved(val reason: String) : RepeatPolicy {
        init { require(reason.isNotBlank()) }
    }
}

/** Pure identity validation; no state change, prerequisite evaluation, price or payment is performed. */
sealed interface IdentityValidation {
    data object NewInstance : IdentityValidation
    data class ExistingInstance(val instance: AcquisitionInstance) : IdentityValidation
    data class NoChange(val instance: AcquisitionInstance) : IdentityValidation
    data class Blocked(val status: RequirementStatus, val reason: String) : IdentityValidation {
        init {
            require(status != RequirementStatus.Satisfied)
            require(reason.isNotBlank())
        }
    }
}

/** Empty filters retain unknown legacy ownership; nonempty filters never treat missing values as wildcards. */
fun selectInstances(
    definition: DefinitionRef,
    parameters: Map<String, String>,
    instances: List<AcquisitionInstance>,
): List<AcquisitionInstance> {
    if (parameters.any { (key, value) -> key.isBlank() || value.isBlank() }) return emptyList()
    return instances.filter { instance ->
        instance.definition == definition && parameters.all { (key, value) -> instance.parameters[key] == value }
    }
}

/**
 * Catalog-owned maxRank/schema/policy are inputs, not duplicated definitions.
 * instanceId denotes an explicit new/existing identity. A caller must select an existing instance
 * rather than infer one by name or turn a higher rank into a second specialization.
 * An unknown legacy parameter/option must be confirmed by the separate mutation path before raising.
 * abilityOptionIds=null means unresolved source; emptySet means explicitly no option selection.
 */
fun validateAcquisitionTarget(
    definition: DefinitionRef,
    maxRank: Int,
    parameterDefinitions: List<ParameterDefinition>,
    repeatPolicy: RepeatPolicy,
    instanceId: String,
    targetRank: Int,
    parameters: Map<String, String>,
    abilityOptionId: String?,
    instances: List<AcquisitionInstance>,
    abilityOptionIds: Set<String>? = emptySet(),
): IdentityValidation {
    fun blocked(status: RequirementStatus, reason: String) = IdentityValidation.Blocked(status, reason)
    if (instanceId.isBlank()) return blocked(RequirementStatus.ChoiceRequired, "Select a stable instance ID")
    if (instances.map { it.instanceId }.toSet().size != instances.size) {
        return blocked(RequirementStatus.Conflict, "Duplicate character instance IDs")
    }
    val existing = instances.singleOrNull { it.instanceId == instanceId }
    if (existing != null) {
        if (existing.definition != definition) return blocked(RequirementStatus.Conflict, "Instance ID belongs to a different definition")
        if (existing.parameters != parameters) return blocked(RequirementStatus.Conflict, "Parameter changes require an explicit confirmation or correction operation")
        if (existing.abilityOptionId != abilityOptionId) return blocked(RequirementStatus.Conflict, "Ability option changes require a separate defined operation")
        if (existing.rank == targetRank) return IdentityValidation.NoChange(existing)
    }
    if (maxRank < 1) return blocked(RequirementStatus.SourceUnresolved, "Definition rank limit is unresolved")
    if (targetRank !in 1..maxRank) return blocked(RequirementStatus.Unsatisfied, "Target rank is outside the definition rank limit")
    if (existing != null && targetRank < existing.rank) return blocked(RequirementStatus.Conflict, "Reducing an owned rank requires a separate operation")
    if (parameterDefinitions.map { it.key }.toSet().size != parameterDefinitions.size) {
        return blocked(RequirementStatus.SourceUnresolved, "Duplicate parameter definitions")
    }
    val definedKeys = parameterDefinitions.map { it.key }.toSet()
    if (parameters.keys.any { it !in definedKeys }) return blocked(RequirementStatus.Unsatisfied, "Parameter key is not defined by the catalog")
    for (parameter in parameterDefinitions) {
        val domain = parameter.domain
        if (domain is ParameterDomain.Unresolved) return blocked(RequirementStatus.SourceUnresolved, domain.reason)
        val value = parameters[parameter.key]
        if (value.isNullOrBlank()) return blocked(RequirementStatus.ChoiceRequired, "Choose parameter ${parameter.key}")
        if (domain is ParameterDomain.StableIds && value !in domain.valueIds) {
            return blocked(RequirementStatus.Unsatisfied, "Unknown value ID for parameter ${parameter.key}")
        }
    }
    if (abilityOptionIds == null) return blocked(RequirementStatus.SourceUnresolved, "Ability option source is unresolved")
    if (abilityOptionIds.any(String::isBlank)) return blocked(RequirementStatus.SourceUnresolved, "Catalog contains a blank ability option ID")
    if (abilityOptionIds.isNotEmpty() && abilityOptionId.isNullOrBlank()) {
        return blocked(RequirementStatus.ChoiceRequired, "Choose a stable ability option ID")
    }
    if (abilityOptionId != null && abilityOptionId !in abilityOptionIds) {
        return blocked(RequirementStatus.Unsatisfied, "Ability option ID is not defined by the catalog")
    }
    if (existing != null) return IdentityValidation.ExistingInstance(existing)
    val sameDefinition = instances.filter { it.definition == definition }
    when (repeatPolicy) {
        RepeatPolicy.Single -> if (sameDefinition.isNotEmpty()) {
            return blocked(RequirementStatus.Conflict, "Definition does not permit another instance")
        }
        is RepeatPolicy.Unresolved -> if (sameDefinition.isNotEmpty()) {
            return blocked(RequirementStatus.SourceUnresolved, repeatPolicy.reason)
        }
        is RepeatPolicy.DistinctParameter -> {
            if (!definedKeys.containsAll(repeatPolicy.parameterKeys)) {
                return blocked(RequirementStatus.SourceUnresolved, "Repeat policy references an undefined parameter")
            }
            if (repeatPolicy.requireUnique) {
                for (owned in sameDefinition) {
                    val keys = repeatPolicy.parameterKeys
                    // A known different component proves distinctness; unknown components cannot prove it.
                    if (keys.any { owned.parameters[it] != null && owned.parameters[it] != parameters[it] }) continue
                    if (keys.any { owned.parameters[it] == null }) {
                        return blocked(RequirementStatus.ChoiceRequired, "Confirm legacy parameters before proving repeat uniqueness")
                    }
                    return blocked(RequirementStatus.Conflict, "An instance already owns this parameter selection")
                }
            }
        }
    }
    return IdentityValidation.NewInstance
}

/** Collision-free framing, deterministic across targets/platforms; no display name, rank or option index. */
fun legacyInstanceId(definition: DefinitionRef, origin: AcquisitionOrigin.Legacy): String {
    val fields = listOf(origin.characterId, origin.schema, origin.sourceRevision, origin.sourceFingerprint,
        origin.ownershipKey, definition.packId, definition.entryId)
    return "legacy:" + fields.joinToString("") { "${it.length}:$it" }
}

private class IdentitySetSnapshot<T>(values: Set<T>) : AbstractSet<T>() {
    private val values = values.toList()
    override val size: Int get() = values.size
    override fun iterator(): Iterator<T> = object : Iterator<T> {
        private var index = 0
        override fun hasNext(): Boolean = index < values.size
        override fun next(): T {
            if (!hasNext()) throw NoSuchElementException()
            return values[index++]
        }
    }
}

private class IdentityParameterSnapshot(parameters: Map<String, String>) : AbstractMap<String, String>() {
    private data class Entry(override val key: String, override val value: String) : Map.Entry<String, String>
    override val entries: Set<Map.Entry<String, String>> = IdentitySetSnapshot(
        parameters.map { (key, value) -> Entry(key, value) }.toSet(),
    )
}
