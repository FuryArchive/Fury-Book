package com.furybook.dubl.acquisition

/** A cited inclusion belongs to a newly gained owner rank and an explicit captured choice.
 * It never changes a target's ordinary price or grants anything merely because an old owner exists.
 */
class IncludedGrant(val sourceRef: RuleSourceRef, val parameterKey: String, val activateAtRank: Int,
    targetByValue: Map<String, DefinitionRef>, val targetRank: Int = 1,
    val requireNewOwnership: Boolean = false, val retainsSourceEligibility: Boolean = true,
    val targetDefinition: DefinitionRef? = null, val creationOnly: Boolean = false,
    targetParameterChoices: Map<String, Set<String>> = emptyMap(),
    val customTargetEvidence: IncludedGrantCustomEvidence? = null) {
    val targetByValue: Map<String, DefinitionRef> = GrantMap(targetByValue)
    val targetParameterChoices: Map<String, Set<String>> = GrantMap(targetParameterChoices.mapValues { GrantSet(it.value) })
    init {
        require(sourceRef.originalText.isNotBlank() && parameterKey.isNotBlank())
        require(activateAtRank > 0 && targetRank > 0 && (this.targetByValue.isNotEmpty() != (targetDefinition != null)))
        require(this.targetByValue.keys.all(String::isNotBlank))
        require(this.targetParameterChoices.all { (key, values) -> key.isNotBlank() && values.all(String::isNotBlank) })
        require(retainsSourceEligibility) { "Included grants cannot waive source eligibility" }
    }
}
class IncludedGrantCustomEvidence(val parameterKey: String, val evidenceKey: String, val sourceRef: RuleSourceRef,
    allowedNamedGroupIds: Set<String> = emptySet()) {
    val allowedNamedGroupIds: Set<String> = GrantSet(allowedNamedGroupIds)
    init { require(parameterKey.isNotBlank() && evidenceKey.isNotBlank() && sourceRef.originalText.isNotBlank()) }
}

internal fun activeIncludedGrants(change: AcquisitionChange, catalog: AcquisitionCatalog,
    phase: RequirementNode.CreationPhase.Phase): List<IncludedGrant> = catalog.definitions[change.definition]?.includedGrants.orEmpty()
    .filter { it.activateAtRank > change.fromRank && it.activateAtRank <= change.toRank &&
        (!it.creationOnly || phase == RequirementNode.CreationPhase.Phase.Creating) }

internal fun includedGrantChoiceId(owner: DefinitionRef, rule: IncludedGrant): String =
    acquisitionChoiceId(owner, "grant/" + rule.parameterKey)

internal data class IncludedGrantSelection(val rule: IncludedGrant, val target: AcquisitionTarget)
internal data class IncludedGrantSelections(val selections: List<IncludedGrantSelection>, val issues: List<AcquisitionIssue>)

internal fun includedGrantSelections(change: AcquisitionChange, catalog: AcquisitionCatalog,
    phase: RequirementNode.CreationPhase.Phase): IncludedGrantSelections {
    val owner = catalog.definitions[change.definition] ?: return IncludedGrantSelections(emptyList(), emptyList())
    val selections = mutableListOf<IncludedGrantSelection>()
    val issues = mutableListOf<AcquisitionIssue>()
    if (owner.includedGrants.map { it.parameterKey to it.activateAtRank }.distinct().size != owner.includedGrants.size)
        return IncludedGrantSelections(emptyList(), listOf(AcquisitionIssue(RequirementStatus.SourceUnresolved,
            "Duplicate included-grant activation", owner.ref, sourceRef = owner.sourceRef)))
    for (rule in activeIncludedGrants(change, catalog, phase).filter { it.targetDefinition == null }) {
        fun problem(status: RequirementStatus, reason: String) {
            issues += AcquisitionIssue(status, reason, owner.ref, owner.ref.entryId + "/grant/" + rule.parameterKey, rule.sourceRef)
        }
        val value = change.parameters[rule.parameterKey]
        if (value.isNullOrBlank()) { problem(RequirementStatus.ChoiceRequired, "Select the included acquisition: ${rule.parameterKey}"); continue }
        val ref = rule.targetByValue[value]
        if (ref == null) { problem(RequirementStatus.Unsatisfied, "Unknown included acquisition choice"); continue }
        val target = catalog.definitions[ref]
        if (target == null || target.maxRank == null || rule.targetRank > target.maxRank || ref == owner.ref) {
            problem(RequirementStatus.SourceUnresolved, "Included acquisition has an invalid source identity or rank"); continue
        }
        // The retained contracts select a complete definition. Extra target dimensions need their own source binding.
        if (target.parameterDefinitions.isNotEmpty() || !target.abilityOptionIds.isNullOrEmpty() || target.kind == AcquisitionKind.ATTRIBUTE) {
            problem(RequirementStatus.ChoiceRequired, "Included acquisition requires additional source-defined selections"); continue
        }
        selections += IncludedGrantSelection(rule, AcquisitionTarget(ref, targetRank = rule.targetRank))
    }
    return IncludedGrantSelections(EnvelopeList(selections), EnvelopeList(issues))
}

/** Only prior, validated changes in this same projection can authorize an included zero price. */
internal fun includedGrantQuote(change: AcquisitionChange, context: AcquisitionContext): PriceResult.Known? {
    if (change.fromRank != 0 || change.optionId != null) return null
    for (owner in context.operationChanges) {
        val selected = includedGrantSelections(owner, context.catalog, context.creationPhase)
        if (selected.issues.isNotEmpty()) continue
        val grant = selected.selections.firstOrNull { it.target.definition == change.definition && it.target.targetRank == change.toRank &&
            it.target.parameters == change.parameters } ?: continue
        if (grant.rule.requireNewOwnership && wasOwnedBeforeOperation(change.definition, context)) continue
        val source = context.catalog.definitions.getValue(owner.definition).sourceRef
        return PriceResult.Known(Amount.ZERO, grant.rule.sourceRef,
            componentSources = listOf(source, grant.rule.sourceRef, context.catalog.definitions.getValue(change.definition).sourceRef).distinct())
    }
    // Fixed targets have explicit captured parameters. A prior owner transition supplies one
    // credit per crossed source slot; every already projected target consumes exactly one.
    val credits = directGrantCredits(context)
    val prior = directGrantChanges(context)
    val proposed = AcquisitionTarget(change.definition, change.instanceId, change.toRank, change.parameters, change.optionId)
    val candidates = prior + change
    if (assignDirectGrants(credits, candidates, context).size == candidates.size) {
        val grant = credits.firstOrNull { directGrantTargetIssue(it.first, it.second, proposed, context) == null }
        if (grant != null) {
            val custom = includedGrantCustomRequirement(grant.first.definition, grant.second, proposed, context.catalog)
            return PriceResult.Known(Amount.ZERO, grant.second.sourceRef,
                componentSources = (listOf(context.catalog.definitions.getValue(grant.first.definition).sourceRef,
                    grant.second.sourceRef, context.catalog.definitions.getValue(change.definition).sourceRef) + listOfNotNull(custom?.sourceRef)).distinct(),
                discountEvidence = listOfNotNull(custom?.let { evaluate(it,context) }))
        }
    }
    return null
}

/** The custom-group decision is tied to the exact skill and stable group ID, never its label. */
internal fun includedGrantCustomRequirement(owner: DefinitionRef, rule: IncludedGrant, target: AcquisitionTarget,
    catalog: AcquisitionCatalog): RequirementNode.ExternalEvidence? {
    val custom = rule.customTargetEvidence ?: return null
    val value = target.parameters[custom.parameterKey] ?: return null
    val definition = catalog.definitions[target.definition] ?: return null
    if (value !in custom.allowedNamedGroupIds &&
        definition.parameterDefinitions.singleOrNull { it.key == custom.parameterKey }?.choices?.any { it.id == value } == true) return null
    return RequirementNode.ExternalEvidence(owner.entryId + "/grant/" + rule.parameterKey + "/custom-group", custom.sourceRef,
        custom.evidenceKey, owner.packId + "/" + owner.entryId + "/" + value)
}

internal data class IncludedGrantRequirement(val owner: DefinitionRef, val target: AcquisitionTarget,
    val requirement: RequirementNode.ExternalEvidence)
internal fun includedGrantRequirements(request: AcquisitionRequest, context: AcquisitionContext): List<IncludedGrantRequirement> =
    context.operationChanges.flatMap { owner -> activeIncludedGrants(owner, context.catalog, context.creationPhase).mapNotNull { rule ->
        val target = request.prerequisiteSelections[includedGrantChoiceId(owner.definition, rule)]?.singleOrNull()
            ?: return@mapNotNull null
        includedGrantCustomRequirement(owner.definition, rule, target, context.catalog)?.let {
            IncludedGrantRequirement(owner.definition, target, it)
        }
    } }

internal fun directGrantTargetIssue(owner: AcquisitionChange, rule: IncludedGrant, target: AcquisitionTarget,
    context: AcquisitionContext): AcquisitionIssue? {
    fun issue(status: RequirementStatus, reason: String, source: RuleSourceRef = rule.sourceRef) =
        AcquisitionIssue(status, reason, owner.definition, owner.definition.entryId + "/grant/" + rule.parameterKey, source)
    if (target.definition != rule.targetDefinition || target.targetRank != rule.targetRank || target.optionId != null)
        return issue(RequirementStatus.Unsatisfied, "Choose the source-defined included acquisition and rank")
    context.operationGrantSelections[includedGrantChoiceId(owner.definition,rule)]?.let { captured ->
        val exact=captured.singleOrNull()
            ?: return issue(RequirementStatus.ChoiceRequired,"Select exactly one included acquisition for this rank")
        if(exact.definition!=target.definition || exact.targetRank!=target.targetRank || exact.parameters!=target.parameters ||
            exact.optionId!=target.optionId || (exact.instanceId!=null && exact.instanceId!=target.instanceId))
            return issue(RequirementStatus.Conflict,"Included acquisition differs from its exact captured selection")
    }
    val definition = context.catalog.definitions[target.definition]
        ?: return issue(RequirementStatus.SourceUnresolved, "Missing included acquisition definition")
    for ((key, allowed) in rule.targetParameterChoices) {
        val value = target.parameters[key] ?: return issue(RequirementStatus.ChoiceRequired, "Choose parameter $key")
        if (value in allowed) continue
        val parameter = definition.parameterDefinitions.singleOrNull { it.key == key }
            ?: return issue(RequirementStatus.SourceUnresolved, "Unknown included parameter binding")
        if ((parameter.choices.any { it.id == value } && value !in rule.customTargetEvidence?.allowedNamedGroupIds.orEmpty()) ||
            rule.customTargetEvidence?.parameterKey != key)
            return issue(RequirementStatus.Unsatisfied, "The selected group is outside this combat skill")
        val requirement = includedGrantCustomRequirement(owner.definition, rule, target, context.catalog)
            ?: return issue(RequirementStatus.SourceUnresolved, "Missing custom-group source contract")
        val proof = evaluate(requirement, context)
        if (proof.status != RequirementStatus.Satisfied) return issue(proof.status, proof.reason ?: "Confirm the custom group with the GM", requirement.sourceRef)
    }
    if (rule.requireNewOwnership && wasTargetOwnedBeforeOperation(target, context))
        return issue(RequirementStatus.Conflict, "The source requires a newly owned included acquisition")
    return null
}

private fun directGrantCredits(context: AcquisitionContext): List<Pair<AcquisitionChange, IncludedGrant>> =
    context.operationChanges.flatMap { owner -> activeIncludedGrants(owner, context.catalog, context.creationPhase)
        .filter { it.targetDefinition != null }.map { owner to it } }

private fun directGrantChanges(context: AcquisitionContext): List<AcquisitionChange> {
    val targets = directGrantCredits(context).mapNotNull { it.second.targetDefinition }.toSet()
    return context.operationChanges.filter { it.definition in targets && it.fromRank == 0 }
}

/** Match credits to exact new instances so one slot cannot be spent twice, including bulk requests. */
private fun assignDirectGrants(credits: List<Pair<AcquisitionChange, IncludedGrant>>, changes: List<AcquisitionChange>,
    context: AcquisitionContext): Map<Int, Int> {
    val assigned = mutableMapOf<Int, Int>()
    fun match(changeIndex: Int, visited: MutableSet<Int>): Boolean {
        val change = changes[changeIndex]
        val target = AcquisitionTarget(change.definition, change.instanceId, change.toRank, change.parameters, change.optionId)
        for (creditIndex in credits.indices) {
            val (owner, rule) = credits[creditIndex]
            if (directGrantTargetIssue(owner, rule, target, context) != null) continue
            if (!visited.add(creditIndex)) continue
            val previous = assigned[creditIndex]
            if (previous == null || match(previous, visited)) { assigned[creditIndex] = changeIndex; return true }
        }
        return false
    }
    changes.indices.forEach { match(it, mutableSetOf()) }
    return assigned
}

private fun wasTargetOwnedBeforeOperation(target: AcquisitionTarget, context: AcquisitionContext): Boolean =
    context.instances.any { it.definition == target.definition && it.parameters == target.parameters && it.abilityOptionId == target.optionId &&
        (context.operationChanges.firstOrNull { change -> change.instanceId == it.instanceId }?.fromRank ?: it.rank) > 0 }

/** Final replay checks the complete inclusion, including no-change selections of already owned entries. */
internal fun includedGrantProjectionIssues(context: AcquisitionContext): List<AcquisitionIssue> {
    val issues = mutableListOf<AcquisitionIssue>()
    val selectedTargets = mutableSetOf<DefinitionRef>()
    for (owner in context.operationChanges) {
        val selected = includedGrantSelections(owner, context.catalog, context.creationPhase)
        issues += selected.issues
        for ((rule, target) in selected.selections) {
            fun problem(status: RequirementStatus, reason: String) {
                issues += AcquisitionIssue(status, reason, owner.definition, owner.definition.entryId + "/grant/" + rule.parameterKey, rule.sourceRef)
            }
            val repeatedSelection = !selectedTargets.add(target.definition)
            if (rule.requireNewOwnership && repeatedSelection) {
                problem(RequirementStatus.Conflict, "The source requires a different newly owned included acquisition"); continue
            }
            val matching = context.instances.filter { it.definition == target.definition && it.parameters.isEmpty() && it.abilityOptionId == null }
            if (matching.size != 1 || matching.single().rank < target.targetRank) {
                problem(RequirementStatus.Conflict, "The selected included acquisition is absent or ambiguous"); continue
            }
            if (rule.requireNewOwnership && wasOwnedBeforeOperation(target.definition, context)) {
                problem(RequirementStatus.Conflict, "The source requires a newly owned included acquisition"); continue
            }
            val proof = evaluate(context.catalog.definitions.getValue(target.definition).requirements, context, matching.single().parameters)
            if (proof.status != RequirementStatus.Satisfied)
                problem(proof.status, "Included acquisition does not satisfy its source eligibility: ${proof.reason ?: proof.nodeId}")
        }
    }
    val credits = directGrantCredits(context)
    val changes = directGrantChanges(context)
    val assigned = assignDirectGrants(credits, changes, context)
    for ((index, credit) in credits.withIndex()) if (index !in assigned) {
        val captured=context.operationGrantSelections[includedGrantChoiceId(credit.first.definition,credit.second)]?.singleOrNull()
        if(!credit.second.requireNewOwnership && captured!=null) {
            val invalid=directGrantTargetIssue(credit.first,credit.second,captured,context)
            if(invalid!=null) { issues+=invalid;continue }
            val owned=context.instances.filter { it.definition==captured.definition && it.parameters==captured.parameters &&
                it.abilityOptionId==captured.optionId && (captured.instanceId==null || it.instanceId==captured.instanceId) }
            if(owned.size==1 && owned.single().rank>=captured.targetRank) {
                val proof=evaluate(context.catalog.definitions.getValue(captured.definition).requirements,context,owned.single().parameters)
                if(proof.status==RequirementStatus.Satisfied) continue
                issues+=AcquisitionIssue(proof.status,"Included acquisition does not satisfy its source eligibility",
                    credit.first.definition,proof.nodeId,credit.second.sourceRef)
                continue
            }
        }
        issues += AcquisitionIssue(RequirementStatus.ChoiceRequired, "Select one new included acquisition for this source rank",
            credit.first.definition, credit.first.definition.entryId + "/grant/" + credit.second.parameterKey, credit.second.sourceRef)
    }
    if (assigned.size != changes.size) issues += AcquisitionIssue(RequirementStatus.Conflict,
        "Included acquisitions exceed compatible newly gained source ranks")
    return EnvelopeList(issues)
}

/** A captured old group consumes its source inclusion without creating an entity or a price row. */
internal fun includedGrantOwnedSelections(context: AcquisitionContext): List<AcquisitionTarget> =
    EnvelopeList(directGrantCredits(context).mapNotNull { (owner,rule) ->
        if(rule.requireNewOwnership) return@mapNotNull null
        val captured=context.operationGrantSelections[includedGrantChoiceId(owner.definition,rule)]?.singleOrNull()
            ?: return@mapNotNull null
        val owned=context.instances.singleOrNull { it.definition==captured.definition && it.parameters==captured.parameters &&
            it.abilityOptionId==captured.optionId && (captured.instanceId==null || it.instanceId==captured.instanceId) }
            ?: return@mapNotNull null
        if(context.operationChanges.any { it.instanceId==owned.instanceId } || directGrantTargetIssue(owner,rule,captured,context)!=null)
            return@mapNotNull null
        captured.copy(instanceId=owned.instanceId)
    }.distinctBy { it.instanceId })

internal fun wasOwnedBeforeOperation(ref: DefinitionRef, context: AcquisitionContext): Boolean =
    context.instances.any { instance -> instance.definition == ref &&
        (context.operationChanges.firstOrNull { it.instanceId == instance.instanceId }?.fromRank ?: instance.rank) > 0 } ||
        (ref in context.learnedSpellIds && context.operationChanges.none { it.definition == ref && it.kind == AcquisitionKind.SPELL && it.fromRank == 0 })

private class GrantMap<K, V>(values: Map<K, V>) : AbstractMap<K, V>() {
    private class Entry<K, V>(override val key: K, override val value: V) : Map.Entry<K, V> {
        override fun equals(other: Any?): Boolean = other is Map.Entry<*, *> && key == other.key && value == other.value
        override fun hashCode(): Int = (key?.hashCode() ?: 0) xor (value?.hashCode() ?: 0)
    }
    private val snapshot = values.map { Entry(it.key, it.value) }
    override val entries: Set<Map.Entry<K, V>> = object : AbstractSet<Map.Entry<K, V>>() {
        override val size get() = snapshot.size
        override fun iterator(): Iterator<Map.Entry<K, V>> = object : Iterator<Map.Entry<K, V>> {
            private var index = 0
            override fun hasNext() = index < snapshot.size
            override fun next(): Map.Entry<K, V> = if (hasNext()) snapshot[index++] else throw NoSuchElementException()
        }
    }
}
private class GrantSet<T>(values: Set<T>) : AbstractSet<T>() {
    private val snapshot = values.toList()
    override val size get() = snapshot.size
    override fun iterator(): Iterator<T> = object : Iterator<T> {
        private var index = 0
        override fun hasNext() = index < snapshot.size
        override fun next(): T = if (hasNext()) snapshot[index++] else throw NoSuchElementException()
    }
}
