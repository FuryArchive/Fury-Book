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

/** A source-backed named choice in an otherwise open, user-defined specialization domain. */
data class WeaponProficiencyKind(val id: String, val label: String, val sourceRef: RuleSourceRef) {
    init { require(id.isNotBlank() && label.isNotBlank() && sourceRef.originalText.isNotBlank()) }
}
class ParameterChoice(val id: String, val label: String, val sourceRef: RuleSourceRef, parameterValues: Map<String,String> = emptyMap(),
    weaponKinds: List<WeaponProficiencyKind> = emptyList()) {
    val parameterValues: Map<String,String> = IdentityParameterSnapshot(parameterValues)
    val weaponKinds: List<WeaponProficiencyKind> = IdentityListSnapshot(weaponKinds)
    init { require(id.isNotBlank() && label.isNotBlank() && sourceRef.originalText.isNotBlank())
        require(this.weaponKinds.map { it.id }.distinct().size == this.weaponKinds.size) }
    override fun equals(other:Any?):Boolean=other is ParameterChoice && id==other.id && label==other.label && sourceRef==other.sourceRef && parameterValues==other.parameterValues && weaponKinds==other.weaponKinds
    override fun hashCode():Int=listOf(id,label,sourceRef,parameterValues,weaponKinds).hashCode()
    override fun toString():String="ParameterChoice(id=$id, label=$label, parameterValues=$parameterValues)"
}

/** A named specialization already has its own ordinary skill definition and purchase path. */
data class ParameterExcludedLabel(val label: String, val sourceRef: RuleSourceRef) {
    init { require(label.isNotBlank() && sourceRef.originalText.isNotBlank()) }
}

sealed interface ParameterSelectionSource {
    data object OwnedWeaponKinds : ParameterSelectionSource
    data class OwnedInstances(val familyId: String, val minimumRank: Int) : ParameterSelectionSource {
        init { require(familyId.isNotBlank() && minimumRank > 0) }
    }
    class OrderedOwnedInstances(definitions:Set<DefinitionRef>,val baseLength:Int,val plusTargetRank:Boolean,val minimumRank:Int=1) : ParameterSelectionSource {
        val definitions:Set<DefinitionRef> = IdentitySetSnapshot(definitions)
        init { require(this.definitions.isNotEmpty() && baseLength>=0 && minimumRank>0) }
        fun lengthAt(rank:Int):Int=baseLength+if(plusTargetRank)rank else 0
        override fun equals(other:Any?):Boolean=other is OrderedOwnedInstances && definitions==other.definitions && baseLength==other.baseLength && plusTargetRank==other.plusTargetRank && minimumRank==other.minimumRank
        override fun hashCode():Int=listOf(definitions,baseLength,plusTargetRank,minimumRank).hashCode()
    }
}

class ParameterDefinition(val key: String, val domain: ParameterDomain, choices: List<ParameterChoice> = emptyList(),
    excludedLabels: List<ParameterExcludedLabel> = emptyList(), val label: String? = null, val requiredAtRank: Int = 1,
    val selectionSource: ParameterSelectionSource? = null, val uniqueWithinGroup: String? = null,
    val displayNameKey: String? = null, val identityDomain: String? = null) {
    val choices: List<ParameterChoice> = IdentityListSnapshot(choices)
    val excludedLabels: List<ParameterExcludedLabel> = IdentityListSnapshot(excludedLabels)
    init {
        require(key.isNotBlank())
        require(requiredAtRank > 0 && (label == null || label.isNotBlank()) && (uniqueWithinGroup == null || uniqueWithinGroup.isNotBlank()))
        require(displayNameKey==null || (displayNameKey.isNotBlank() && displayNameKey!=key))
        require(identityDomain==null || identityDomain.isNotBlank())
        require(this.choices.map { it.id }.distinct().size == this.choices.size) { "Duplicate named parameter choices" }
        require(this.excludedLabels.map { it.label.lowercase() }.distinct().size == this.excludedLabels.size) { "Duplicate excluded parameter labels" }
        if (domain is ParameterDomain.StableIds) require(this.choices.all { it.id in domain.valueIds })
    }
    fun copy(key: String = this.key, domain: ParameterDomain = this.domain, choices: List<ParameterChoice> = this.choices,
        excludedLabels: List<ParameterExcludedLabel> = this.excludedLabels, label: String? = this.label, requiredAtRank: Int = this.requiredAtRank,
        selectionSource: ParameterSelectionSource? = this.selectionSource, uniqueWithinGroup: String? = this.uniqueWithinGroup,
        displayNameKey: String? = this.displayNameKey, identityDomain: String? = this.identityDomain) =
        ParameterDefinition(key, domain, choices, excludedLabels, label, requiredAtRank, selectionSource, uniqueWithinGroup, displayNameKey, identityDomain)
    operator fun component1() = key
    operator fun component2() = domain
    operator fun component3() = choices
    override fun equals(other: Any?): Boolean = other is ParameterDefinition && key == other.key && domain == other.domain &&
        choices == other.choices && excludedLabels == other.excludedLabels && label == other.label && requiredAtRank == other.requiredAtRank &&
        selectionSource == other.selectionSource && uniqueWithinGroup == other.uniqueWithinGroup && displayNameKey==other.displayNameKey && identityDomain==other.identityDomain
    override fun hashCode(): Int = listOf(key, domain, choices, excludedLabels, label, requiredAtRank, selectionSource, uniqueWithinGroup, displayNameKey, identityDomain).hashCode()
    override fun toString(): String = "ParameterDefinition(key=$key, domain=$domain, choices=$choices, excludedLabels=$excludedLabels)"
}

/** Canonical type owned by T03; T04 assigns these policies from confirmed catalog sources. */
sealed interface RepeatPolicy {
    data object Single : RepeatPolicy
    /** Source permits independent repeated copies without a semantic parameter choice. */
    data object Independent : RepeatPolicy

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
    catalog: AcquisitionCatalog? = null,
): IdentityValidation {
    fun blocked(status: RequirementStatus, reason: String) = IdentityValidation.Blocked(status, reason)
    if (instanceId.isBlank()) return blocked(RequirementStatus.ChoiceRequired, "Select a stable instance ID")
    if (instances.map { it.instanceId }.toSet().size != instances.size) {
        return blocked(RequirementStatus.Conflict, "Duplicate character instance IDs")
    }
    val existing = instances.singleOrNull { it.instanceId == instanceId }
    val transitionPayment = catalog?.definitions?.get(definition)?.hasTransitionPaymentOptions == true
    if (existing != null) {
        if (existing.definition != definition) return blocked(RequirementStatus.Conflict, "Instance ID belongs to a different definition")
        if (existing.parameters.any { prior ->
                val sequence=parameterDefinitions.singleOrNull { it.key==prior.key || it.displayNameKey==prior.key }?.selectionSource as? ParameterSelectionSource.OrderedOwnedInstances
                if(sequence!=null && targetRank>existing.rank)
                    AcquisitionOrderedSelections.decode(prior.value)?.size!=sequence.lengthAt(existing.rank) ||
                        !AcquisitionOrderedSelections.preservesPrefix(prior.value,parameters[prior.key])
                else parameters[prior.key]!=prior.value
            })
            return blocked(RequirementStatus.Conflict, "Previously selected parameters cannot be changed or removed during rank advancement")
        val added = parameters.keys - existing.parameters.keys
        if (added.any { key -> parameterDefinitions.singleOrNull { it.key == key || it.displayNameKey==key }?.let {
                targetRank <= existing.rank || it.requiredAtRank <= existing.rank || it.requiredAtRank > targetRank
            } != false }) return blocked(RequirementStatus.Conflict, "Only newly activated rank selections may be added during advancement")
        if (!transitionPayment && existing.abilityOptionId != abilityOptionId)
            return blocked(RequirementStatus.Conflict, "Ability option changes require a separate defined operation")
        if (transitionPayment && abilityOptionId != null && abilityOptionId !in abilityOptionIds.orEmpty())
            return blocked(RequirementStatus.Unsatisfied, "Payment option ID is not defined by the catalog")
        if (existing.rank == targetRank) return IdentityValidation.NoChange(existing)
    }
    if (maxRank < 1) return blocked(RequirementStatus.SourceUnresolved, "Definition rank limit is unresolved")
    if (targetRank !in 1..maxRank) return blocked(RequirementStatus.Unsatisfied, "Target rank is outside the definition rank limit")
    if (existing != null && targetRank < existing.rank) return blocked(RequirementStatus.Conflict, "Reducing an owned rank requires a separate operation")
    if (parameterDefinitions.map { it.key }.toSet().size != parameterDefinitions.size) {
        return blocked(RequirementStatus.SourceUnresolved, "Duplicate parameter definitions")
    }
    val activeParameters = parameterDefinitions.filter { it.requiredAtRank <= targetRank }
    val definedKeys = activeParameters.flatMap { listOfNotNull(it.key,it.displayNameKey) }.toSet()
    if (parameters.keys.any { it !in definedKeys }) return blocked(RequirementStatus.Unsatisfied, "Parameter key is not defined by the catalog")
    for (parameter in activeParameters) {
        val domain = parameter.domain
        if (domain is ParameterDomain.Unresolved) return blocked(RequirementStatus.SourceUnresolved, domain.reason)
        val value = parameters[parameter.key]
        if (value.isNullOrBlank()) return blocked(RequirementStatus.ChoiceRequired, "Choose parameter ${parameter.key}")
        if (domain is ParameterDomain.StableIds && value !in domain.valueIds) {
            return blocked(RequirementStatus.Unsatisfied, "Unknown value ID for parameter ${parameter.key}")
        }
        if (parameter.selectionSource == ParameterSelectionSource.OwnedWeaponKinds) {
            if (catalog == null) return blocked(RequirementStatus.SourceUnresolved, "Owned weapon kinds require the source catalog")
            val kind = AcquisitionWeaponProficiencies.ownedKinds(instances, catalog).singleOrNull { it.id == value }
                ?: return blocked(RequirementStatus.Unsatisfied, "Choose an actually mastered weapon kind")
            val nameKey = parameter.displayNameKey
                ?: return blocked(RequirementStatus.SourceUnresolved, "Weapon kind selection needs a captured display name")
            if (parameters[nameKey] != kind.label) return blocked(RequirementStatus.Conflict, "Weapon kind name differs from its mastered identity")
            continue
        }
        val ordered=parameter.selectionSource as? ParameterSelectionSource.OrderedOwnedInstances
        if(ordered!=null) {
            val sequence=AcquisitionOrderedSelections.decode(value)
                ?: return blocked(RequirementStatus.Unsatisfied,"Choose a canonical ordered sequence")
            if(sequence.size!=ordered.lengthAt(targetRank)) return blocked(RequirementStatus.ChoiceRequired,"Choose all techniques in the sequence")
            val selected=sequence.map { id -> instances.singleOrNull { it.instanceId==id }
                ?: return blocked(RequirementStatus.Unsatisfied,"Choose actually owned techniques") }
            if(selected.any { it.definition !in ordered.definitions || it.rank<ordered.minimumRank })
                return blocked(RequirementStatus.Unsatisfied,"Selected technique is outside the source-defined known choices")
            val nameKey=parameter.displayNameKey
                ?: return blocked(RequirementStatus.SourceUnresolved,"Ordered selection requires a captured display field")
            val names=AcquisitionOrderedSelections.decode(parameters[nameKey])
                ?: return blocked(RequirementStatus.ChoiceRequired,"Capture the selected technique names")
            if(catalog==null) return blocked(RequirementStatus.SourceUnresolved,"Ordered choice labels require the catalog")
            if(names!=selected.map { AcquisitionLabels(catalog).acquisition(it.definition,it.parameters) })
                return blocked(RequirementStatus.Conflict,"Ordered choice names differ from the selected owned identities")
            continue
        }
        val choice=parameter.choices.singleOrNull { it.id==value }
        if(choice!=null && choice.parameterValues.any { parameters[it.key]!=it.value })
            return blocked(RequirementStatus.Conflict,"A source choice requires its declared parameter values")
        parameter.displayNameKey?.let { nameKey ->
            val name=parameters[nameKey]?.let(AcquisitionSkillSpecializations::cleanName)
            if(name.isNullOrBlank()) return blocked(RequirementStatus.ChoiceRequired,"Name the selected parameter")
            if(choice!=null && name!=choice.label) return blocked(RequirementStatus.Conflict,"Source choice display name differs from its selected identity")
            if(choice==null && parameter.choices.any { it.label.equals(name,ignoreCase=true) })
                return blocked(RequirementStatus.ChoiceRequired,"Select the named source choice explicitly")
            if(parameter.excludedLabels.any { it.label.equals(name,ignoreCase=true) })
                return blocked(RequirementStatus.Conflict,"This named choice has a separate purchase path")
            parameter.identityDomain?.let { identityDomain ->
                for(owned in instances) for(prior in catalog?.definitions?.get(owned.definition)?.parameterDefinitions.orEmpty()) {
                    if(prior.identityDomain==identityDomain && owned.parameters[prior.key]==value) {
                        val oldName=prior.displayNameKey?.let { owned.parameters[it] }
                        if(oldName!=null && oldName!=name) return blocked(RequirementStatus.Conflict,"A reused identity must retain its captured name")
                    }
                }
            }
        }
        val selectedSource = parameter.selectionSource
        if (selectedSource is ParameterSelectionSource.OwnedInstances) {
            val selected = instances.singleOrNull { it.instanceId == value }
                ?: return blocked(RequirementStatus.Unsatisfied, "Select an owned instance")
            val selectedDefinition = catalog?.definitions?.get(selected.definition)
                ?: return blocked(RequirementStatus.SourceUnresolved, "Selected instance family requires the catalog")
            // The selected-instance requirement evaluates/repairs its rank after identity resolution.
            if (selectedSource.familyId !in selectedDefinition.familyIds ||
                selectedDefinition.classificationSources["family:${selectedSource.familyId}"] == null)
                return blocked(RequirementStatus.Unsatisfied, "Selected owned instance does not meet the required family and rank")
        }
    }
    for ((_, group) in activeParameters.filter { it.uniqueWithinGroup != null }.groupBy { it.uniqueWithinGroup }) {
        val selected = group.mapNotNull { parameters[it.key] }
        if (selected.distinct().size != selected.size) return blocked(RequirementStatus.Conflict, "Rank selections in this group must be distinct")
    }
    skillSpecializationSelectionIssue(parameterDefinitions, parameters, instances.filter { it.definition == definition }, existing?.instanceId)?.let {
        return blocked(it.first, it.second)
    }
    if (abilityOptionIds == null) return blocked(RequirementStatus.SourceUnresolved, "Ability option source is unresolved")
    if (abilityOptionIds.any(String::isBlank)) return blocked(RequirementStatus.SourceUnresolved, "Catalog contains a blank ability option ID")
    if (abilityOptionIds.isNotEmpty() && abilityOptionId.isNullOrBlank()) {
        return blocked(RequirementStatus.ChoiceRequired, "Choose a stable ability option ID")
    }
    if (abilityOptionId != null && abilityOptionId !in abilityOptionIds) {
        return blocked(RequirementStatus.Unsatisfied, "Ability option ID is not defined by the catalog")
    }
    fun sameRepeatParameter(owned:AcquisitionInstance,key:String):Boolean? {
        val before=owned.parameters[key] ?: return null
        val after=parameters[key] ?: return null
        return if(parameterDefinitions.singleOrNull { it.key==key }?.selectionSource is ParameterSelectionSource.OrderedOwnedInstances) {
            catalog?.let { AcquisitionOrderedSelections.sameTechniques(before,after,instances,it) }
        } else before==after
    }
    if(existing!=null) {
        if(repeatPolicy is RepeatPolicy.DistinctParameter && repeatPolicy.requireUnique) {
            for(owned in instances.filter { it.instanceId!=existing.instanceId && it.definition==definition }) {
                val matches=repeatPolicy.parameterKeys.map { sameRepeatParameter(owned,it) }
                if(matches.any { it==false }) continue
                if(matches.any { it==null }) return blocked(RequirementStatus.ChoiceRequired,"Confirm legacy parameters before proving repeat uniqueness")
                return blocked(RequirementStatus.Conflict,"An instance already owns this parameter selection")
            }
        }
        return IdentityValidation.ExistingInstance(existing)
    }
    catalog?.definitions?.get(definition)?.newParameterValueRequirement?.let { rule ->
        val previous = instances.filter { it.definition == definition }.flatMap { owned ->
            rule.parameterKeys.mapNotNull { owned.parameters[it] }
        }.toSet()
        val selected = rule.parameterKeys.mapNotNull(parameters::get).toSet()
        if ((selected - previous).size < rule.minimumNewValues)
            return blocked(RequirementStatus.Conflict, "This repeat must add a newly available source-defined choice")
    }
    val sameDefinition = instances.filter { it.definition == definition }
    when (repeatPolicy) {
        RepeatPolicy.Independent -> Unit
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
                    val matches=repeatPolicy.parameterKeys.map { sameRepeatParameter(owned,it) }
                    // A known different component proves distinctness; unknown components cannot prove it.
                    if (matches.any { it==false }) continue
                    if (matches.any { it==null }) {
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

internal fun snapshotAcquisitionParameters(parameters:Map<String,String>):Map<String,String> = IdentityParameterSnapshot(parameters)

private class IdentityListSnapshot<T>(values: List<T>) : AbstractList<T>() {
    private val values = values.toList()
    override val size get() = values.size
    override fun get(index: Int): T = values[index]
}
