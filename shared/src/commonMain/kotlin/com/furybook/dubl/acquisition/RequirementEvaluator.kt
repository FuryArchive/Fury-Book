package com.furybook.dubl.acquisition

import com.furybook.dubl.model.DublCharacter
import com.furybook.dubl.model.MagicEquipmentRules

/** Read-only acquisition facts. Learned IDs contain learned=true only; names never establish identity.
 * Canonical instances are the ownership authority (including skills, schools and mana).
 * The character supplies the existing pure attribute calculation, including size modifiers.
 */
class AcquisitionContext(
    character: DublCharacter,
    instances: List<AcquisitionInstance>,
    learnedSpellIds: Set<DefinitionRef>,
    val creationPhase: RequirementNode.CreationPhase.Phase,
    externalEvidence: List<RequirementWitness.External>,
    catalog: AcquisitionCatalog,
    operationChanges: List<AcquisitionChange> = emptyList(),
    operationGrantSelections: Map<String, List<AcquisitionTarget>> = emptyMap(),
) {
    val character: DublCharacter = character.copy(attributes = FactMap(character.attributes))
    val instances: List<AcquisitionInstance> = FactList(instances)
    val learnedSpellIds: Set<DefinitionRef> = FactSet(learnedSpellIds)
    val externalEvidence: List<RequirementWitness.External> = FactList(externalEvidence)
    val operationChanges: List<AcquisitionChange> = FactList(operationChanges)
    val operationGrantSelections: Map<String, List<AcquisitionTarget>> = FactMap(operationGrantSelections.mapValues { FactList(it.value) })
    val catalog: AcquisitionCatalog = catalog.copy(definitions = FactMap(catalog.definitions.mapValues { (_, d) ->
        d.copy(familyIds = FactSet(d.familyIds), categoryIds = FactSet(d.categoryIds),
            classificationSources = FactMap(d.classificationSources), parameterDefinitions = FactList(d.parameterDefinitions), includedGrants = FactList(d.includedGrants),
            abilityOptionIds = d.abilityOptionIds?.let { FactSet(it) }, priceRule = snapshotPrice(d.priceRule))
    }))
}

/** One semantic entry point for typed requirements; no string parser or UI dependency. */
fun evaluate(node: RequirementNode, context: AcquisitionContext, subjectParameters: Map<String, String> = emptyMap()): RequirementResult =
    Evaluation(context, FactMap(subjectParameters)).visit(node, emptySet(), 0).result

private class EvaluationBudget(var remaining: Int = 16_384)
private class Evaluation(private val context: AcquisitionContext, private val subjectParameters: Map<String, String>,
    private val budget: EvaluationBudget = EvaluationBudget()) {
    // Depth protects the common targets' stacks; work bounds pathological branching as well as cycles.
    private val maxDepth = 64
    data class Candidate(val definition: DefinitionRef, val instance: AcquisitionInstance?)
    /** Feasibility and candidate binding are separate from the public diagnostic status.
     * An AND may retain a source blocker while another child definitively rules out this candidate.
     */
    data class Proof(val result: RequirementResult,
        val canSatisfy: Boolean = result.status != RequirementStatus.Unsatisfied && result.status != RequirementStatus.Conflict,
        val bindingStatus: RequirementStatus? = null) {
        val status get() = result.status
        val witnesses get() = result.witnesses
        val reason get() = result.reason
        val actualValue get() = result.actualValue
    }

    fun visit(node: RequirementNode, path: Set<DefinitionRef>, depth: Int, candidate: Candidate? = null,
        ownershipOnly: Boolean = false): Proof {
        if (depth >= maxDepth || budget.remaining-- <= 0) return unresolved(node, "Requirement traversal limit reached")
            .copy(bindingStatus = if (candidate != null) RequirementStatus.SourceUnresolved else null)
        val proof = when (node) {
            is RequirementNode.AllOf -> {
                val children = node.children.map { visit(it, path, depth + 1, candidate, ownershipOnly) }
                val status = aggregate(children, any = false)
                result(node, status, children.flatMap { it.witnesses }.distinct(), children).copy(
                    canSatisfy = children.all { it.canSatisfy }, bindingStatus = compositeBinding(children, status, any = false))
            }
            is RequirementNode.AnyOf -> {
                val children = mutableListOf<Proof>()
                // In a count, independent truth cannot suppress a later bound proof of this candidate.
                for (child in node.children) {
                    val alternative = visit(child, path, depth + 1, candidate, ownershipOnly)
                    children += alternative
                    if (alternative.status == RequirementStatus.Satisfied &&
                        (candidate == null || alternative.bindingStatus == RequirementStatus.Satisfied)) break
                }
                val chosen = children.firstOrNull { it.status == RequirementStatus.Satisfied &&
                    (candidate == null || it.bindingStatus == RequirementStatus.Satisfied) }
                    ?: children.firstOrNull { it.status == RequirementStatus.Satisfied }
                val status = aggregate(children, any = true)
                result(node, status, chosen?.witnesses.orEmpty(), children).copy(
                    canSatisfy = children.any { it.canSatisfy }, bindingStatus = compositeBinding(children, status, any = true))
            }
            is RequirementNode.Attribute -> number(node, context.character.attribute(node.attributeId), node.operator, node.operand)
            is RequirementNode.NumericProperty -> when {
                node.propertyId !in setOf("MAGIC_POWER","FORTITUDE") -> unresolved(node, "Unsupported numeric property: ${node.propertyId}")
                node.operand < 0 -> unresolved(node, "Invalid numeric property boundary")
                else -> number(node, if(node.propertyId=="FORTITUDE")context.character.fortitude else MagicEquipmentRules.highestMagicPower(context.character), node.operator, node.operand)
            }
            is RequirementNode.OwnedRank -> ownership(node, DefinitionRef(node.packId, node.entryId), node.operator,
                node.operand, node.instanceId, null, null, path, depth, candidate, ownershipOnly)
            is RequirementNode.Specialization -> ownership(node, DefinitionRef(node.packId, node.entryId), node.operator,
                node.operand, node.instanceId, node.qualifier, null, path, depth, candidate, ownershipOnly)
            is RequirementNode.SkillFamily -> family(node, path, depth, candidate, ownershipOnly)
            is RequirementNode.BranchAccess -> branch(node, path, depth, candidate, ownershipOnly)
            is RequirementNode.SelectedInstance -> selected(node, path, depth, ownershipOnly)
            is RequirementNode.LearnedSpell -> spell(node, path, depth, candidate, ownershipOnly)
            is RequirementNode.DistinctCount -> count(node, path, depth, ownershipOnly)
            is RequirementNode.CreationPhase -> result(node,
                if (context.creationPhase == node.operand) RequirementStatus.Satisfied else RequirementStatus.Unsatisfied,
                actual = RequirementValue.Phase(context.creationPhase), required = RequirementValue.Phase(node.operand))
            is RequirementNode.Exclusive -> {
                // Exclusion asks about actual possession. A broken old prerequisite does not erase a rival root.
                val conflict = visit(node.conflicting, path, depth + 1, candidate, ownershipOnly = true)
                val status = when (conflict.status) {
                    RequirementStatus.Satisfied -> RequirementStatus.Conflict
                    RequirementStatus.Unsatisfied -> RequirementStatus.Satisfied
                    else -> conflict.status
                }
                result(node, status, conflict.witnesses, listOf(conflict),
                    if (status == RequirementStatus.Conflict) "Incompatible ownership" else conflict.reason)
            }
            is RequirementNode.ExternalEvidence -> {
                val facts = context.externalEvidence.filter { it.evidenceKey == node.evidenceKey && it.scopeId == node.scopeId &&
                    it.evidenceId.isNotBlank() && it.sourceRef.originalText.isNotBlank() }
                when {
                    facts.isEmpty() -> result(node, RequirementStatus.ChoiceRequired, reason = "Confirm external evidence in scope ${node.scopeId}",
                        required = RequirementValue.Flag(node.operand))
                    node.operand -> result(node, RequirementStatus.Satisfied, facts, actual = RequirementValue.Flag(true), required = RequirementValue.Flag(true))
                    else -> result(node, RequirementStatus.Unsatisfied, facts, actual = RequirementValue.Flag(true), required = RequirementValue.Flag(false))
                }
            }
            is RequirementNode.Unresolved -> unresolved(node, node.reason)
        }
        if (candidate == null) return proof
        val binding = when (node) {
            is RequirementNode.OwnedRank -> candidate.definition == DefinitionRef(node.packId, node.entryId)
            is RequirementNode.Specialization -> candidate.definition == DefinitionRef(node.packId, node.entryId)
            is RequirementNode.SkillFamily -> true // Family selection itself already checks the candidate's stable membership.
            is RequirementNode.BranchAccess -> node.operand && candidate.definition == DefinitionRef(node.packId, node.branchId)
            is RequirementNode.SelectedInstance -> candidate.instance?.instanceId == subjectParameters[node.parameterKey]
            is RequirementNode.LearnedSpell -> node.operand && candidate.definition == DefinitionRef(node.packId, node.entryId)
            is RequirementNode.Unresolved -> true
            else -> return proof
        }
        return proof.copy(bindingStatus = if (!binding || !proof.canSatisfy) null else when {
            proof.status == RequirementStatus.Satisfied && binds(candidate, proof) -> RequirementStatus.Satisfied
            proof.status == RequirementStatus.SourceUnresolved -> RequirementStatus.SourceUnresolved
            proof.status == RequirementStatus.ChoiceRequired -> RequirementStatus.ChoiceRequired
            else -> null
        })
    }

    private fun ownership(node: RequirementNode, ref: DefinitionRef, operator: ComparisonOperator, operand: Int,
        instanceId: String?, qualifier: ParameterMatch?, optionId: String?, path: Set<DefinitionRef>, depth: Int,
        candidate: Candidate?, ownershipOnly: Boolean, parameterMatches: Map<String, String> = emptyMap()): Proof {
        if (candidate != null && candidate.definition != ref) return number(node, 0, operator, operand, forceAbsent = true)
        val definition = context.catalog.definitions[ref] ?: return unresolved(node, "Missing definition $ref")
        if (!ownershipOnly) {
            if (definition.sourceStatus == SourceStatus.Unavailable) return unresolved(node, "Source unavailable for $ref")
            val confirmed = confirmedPurchaseRankCeiling(definition)
            if (confirmed == null || operand < 0 || operand > confirmed) return unresolved(node, "Unknown or invalid rank boundary for $ref")
        }
        val mana = manaReserveDefinition(ref, context.catalog)
        val ownership = if (mana != null) manaReserveOwnership(context, ref) else context.instances
        val instances = ownership.filter { it.definition == ref && (instanceId == null || it.instanceId == instanceId) &&
            (candidate == null || it.instanceId == candidate.instance?.instanceId) }
        val filtered = instances.filter { (ownershipOnly || !excludedSkillSpecialization(definition,it.parameters)) &&
            (qualifier == null || it.parameters[qualifier.key] == qualifier.valueId) &&
            (optionId == null || it.abilityOptionId == optionId) && parameterMatches.all { (key, value) -> it.parameters[key] == value } }
        val matching = filtered.filter { compare(it.rank, operator, operand) }
        if (matching.isEmpty()) {
            val unknown = instances.any { compare(it.rank, operator, operand) &&
                (qualifier == null || it.parameters[qualifier.key] == null || it.parameters[qualifier.key] == qualifier.valueId) &&
                (optionId == null || it.abilityOptionId == null || it.abilityOptionId == optionId) &&
                parameterMatches.all { (key, value) -> it.parameters[key] == null || it.parameters[key] == value } }
            if (unknown && (qualifier != null || optionId != null || parameterMatches.isNotEmpty())) return result(node, RequirementStatus.ChoiceRequired,
                reason = "Confirm unknown legacy parameter or access option", required = RequirementValue.Number(operand))
            return number(node, filtered.maxOfOrNull { it.rank } ?: 0, operator, operand, forceAbsent = filtered.isEmpty())
        }
        val proofs = mutableListOf<Proof>()
        for (owned in matching) {
            if (context.instances.count { it.instanceId == owned.instanceId } != 1) {
                proofs += result(node, RequirementStatus.Conflict, reason = "Duplicate instance ID ${owned.instanceId}")
                continue
            }
            if (!ownershipOnly && definition.maxRank != null && owned.rank > definition.maxRank) {
                proofs += unresolved(node, "Owned rank exceeds source boundary for $ref")
                continue
            }
            val witness = RequirementWitness.OwnedInstance(ref.packId, ref.entryId, owned.instanceId, owned.rank)
            if (ownershipOnly) return number(node, owned.rank, operator, operand, listOf(witness))
            // Creation/GM approval admits an increase; it does not revoke an already-owned reserve.
            if (mana != null) return number(node, owned.rank, operator, operand, listOf(witness))
            val proof = prerequisite(node, definition, path, depth, owned.parameters)
            proofs += proof
            if (proof.status == RequirementStatus.Satisfied) return result(node, RequirementStatus.Satisfied,
                listOf(witness) + proof.witnesses, listOf(proof), actual = RequirementValue.Number(owned.rank), required = RequirementValue.Number(operand))
        }
        return result(node, aggregate(proofs, any = true), children = proofs,
            reason = "Owned prerequisite cannot prove its requirements", required = RequirementValue.Number(operand))
            .copy(canSatisfy = proofs.any { it.canSatisfy })
    }

    private fun prerequisite(node: RequirementNode, definition: AcquisitionDefinition, path: Set<DefinitionRef>, depth: Int,
        parameters: Map<String, String> = emptyMap()): Proof {
        if (definition.ref in path) return unresolved(node, "Requirement cycle: ${(path + definition.ref).joinToString(" -> ")}")
        // Reset count's candidate restriction: this object's dependencies may be other owned objects.
        return Evaluation(context, FactMap(parameters), budget).visit(definition.requirements, path + definition.ref, depth + 1)
    }

    private fun selected(node: RequirementNode.SelectedInstance, path: Set<DefinitionRef>, depth: Int, ownershipOnly: Boolean): Proof {
        val id = subjectParameters[node.parameterKey]?.takeIf { it.isNotBlank() }
            ?: return result(node, RequirementStatus.ChoiceRequired, reason = "Select owned instance ${node.parameterKey}")
        val owned = context.instances.filter { it.instanceId == id }
        if (owned.size != 1) return result(node, if (owned.isEmpty()) RequirementStatus.Unsatisfied else RequirementStatus.Conflict,
            reason = "Selected instance is not uniquely owned")
        val candidate = Candidate(owned.single().definition, owned.single())
        val proof = visit(node.predicate, path, depth + 1, candidate, ownershipOnly)
        val status = if (proof.status == RequirementStatus.Satisfied && !binds(candidate, proof)) RequirementStatus.Unsatisfied else proof.status
        return result(node, status, proof.witnesses, listOf(proof), proof.reason, proof.result.actualValue, proof.result.requiredValue)
    }

    private fun family(node: RequirementNode.SkillFamily, path: Set<DefinitionRef>, depth: Int, candidate: Candidate?, ownershipOnly: Boolean): Proof {
        val definitions = context.catalog.definitions.values.filter { d -> d.ref.packId == node.packId && d.kind == AcquisitionKind.SKILL &&
            (d.ref.entryId == node.familyId || (node.familyId in d.familyIds && "family:${node.familyId}" in d.classificationSources)) }
        if (definitions.isEmpty()) return unresolved(node, "Missing source-backed skill family ${node.familyId}")
        val inScope = definitions.filter { d -> (candidate == null || candidate.definition == d.ref) && context.instances.any { it.definition == d.ref } }
        if (inScope.isEmpty()) return number(node, 0, node.operator, node.operand, forceAbsent = true)
        val proofs = inScope.map { d -> ownership(node, d.ref, node.operator, node.operand, null, null, null, path, depth, candidate, ownershipOnly) }
        val satisfied = proofs.firstOrNull { it.status == RequirementStatus.Satisfied }
        return if (satisfied != null) result(node, satisfied.status, satisfied.witnesses, proofs,
            actual = satisfied.actualValue, required = RequirementValue.Number(node.operand))
        else result(node, aggregate(proofs, any = true), children = proofs,
            actual = RequirementValue.Number(context.instances.filter { i -> inScope.any { it.ref == i.definition } }.maxOfOrNull { it.rank } ?: 0),
            required = RequirementValue.Number(node.operand)).copy(canSatisfy = proofs.any { it.canSatisfy })
    }

    private fun branch(node: RequirementNode.BranchAccess, path: Set<DefinitionRef>, depth: Int, candidate: Candidate?, ownershipOnly: Boolean): Proof {
        val parameters = linkedMapOf<String, String>()
        for ((target, subject) in node.parameterBindings) {
            parameters[target] = subjectParameters[subject]?.takeIf { it.isNotBlank() }
                ?: return result(node, RequirementStatus.ChoiceRequired, reason = "Select subject parameter $subject for access")
        }
        val presence = ownership(node, DefinitionRef(node.packId, node.branchId), ComparisonOperator.GE, 1, null, null,
            node.accessOptionId, path, depth, candidate, ownershipOnly, parameters)
        val status = if (node.operand) presence.status else when (presence.status) {
            RequirementStatus.Satisfied -> RequirementStatus.Unsatisfied
            RequirementStatus.Unsatisfied -> RequirementStatus.Satisfied
            else -> presence.status
        }
        val witnesses = presence.witnesses.map { witness ->
            if (witness is RequirementWitness.OwnedInstance && witness.packId == node.packId && witness.entryId == node.branchId && node.accessOptionId != null)
                RequirementWitness.Branch(node.packId, node.branchId, node.accessOptionId) else witness
        }
        return result(node, status, witnesses, listOf(presence), presence.reason,
            actual = if (presence.status in listOf(RequirementStatus.Satisfied, RequirementStatus.Unsatisfied)) RequirementValue.Flag(presence.status == RequirementStatus.Satisfied) else null,
            required = RequirementValue.Flag(node.operand)).copy(canSatisfy = if (node.operand) presence.canSatisfy else status != RequirementStatus.Unsatisfied && status != RequirementStatus.Conflict)
    }

    private fun spell(node: RequirementNode.LearnedSpell, path: Set<DefinitionRef>, depth: Int, candidate: Candidate?, ownershipOnly: Boolean): Proof {
        val ref = DefinitionRef(node.packId, node.entryId)
        val definition = context.catalog.definitions[ref] ?: return unresolved(node, "Missing spell $ref")
        if (definition.kind != AcquisitionKind.SPELL) return unresolved(node, "Referenced definition is not a spell")
        if (!ownershipOnly && definition.sourceStatus == SourceStatus.Unavailable) return unresolved(node, "Spell source unavailable")
        val learned = ref in context.learnedSpellIds && (candidate == null || candidate.definition == ref)
        if (learned != node.operand) return result(node, RequirementStatus.Unsatisfied,
            actual = RequirementValue.Flag(learned), required = RequirementValue.Flag(node.operand))
        if (!learned) return result(node, RequirementStatus.Satisfied, actual = RequirementValue.Flag(false), required = RequirementValue.Flag(false))
        val proof = if (ownershipOnly) null else prerequisite(node, definition, path, depth)
        return result(node, proof?.status ?: RequirementStatus.Satisfied,
            if (proof == null || proof.status == RequirementStatus.Satisfied) listOf(RequirementWitness.LearnedSpell(node.packId, node.entryId)) else emptyList(),
            listOfNotNull(proof), proof?.reason, actual = RequirementValue.Flag(true), required = RequirementValue.Flag(true))
            .copy(canSatisfy = proof?.canSatisfy ?: true)
    }

    private fun count(node: RequirementNode.DistinctCount, path: Set<DefinitionRef>, depth: Int, ownershipOnly: Boolean): Proof {
        val referenced = countCandidateDefinitions(node.predicate,depth+1)
        val candidates = (context.instances.map { Candidate(it.definition, it) } +
            context.learnedSpellIds.filter { ref -> context.instances.none { it.definition == ref } }.map { Candidate(it, null) }
            ).filter { referenced == null || it.definition in referenced }
        fun key(candidate: Candidate): Any = if (node.uniqueness == DistinctScope.Definition) candidate.definition
            else candidate.instance?.instanceId ?: candidate.definition
        val evaluated = mutableListOf<Pair<Candidate,Proof>>()
        val enough = mutableSetOf<Any>()
        for(candidate in candidates) {
            val proof=visit(node.predicate,path,depth+1,candidate,ownershipOnly)
            evaluated+=candidate to proof
            if(proof.bindingStatus==RequirementStatus.Satisfied) enough+=key(candidate)
            // Further candidates cannot invalidate an already proven lower bound for GE/GT.
            if((node.operator==ComparisonOperator.GE && enough.size>=node.operand) ||
                (node.operator==ComparisonOperator.GT && enough.size>node.operand))
                return result(node,RequirementStatus.Satisfied,
                    evaluated.filter { it.second.bindingStatus==RequirementStatus.Satisfied }.flatMap { it.second.witnesses }.distinct(),
                    evaluated.map { it.second },actual=RequirementValue.Number(enough.size),required=RequirementValue.Number(node.operand))
        }
        val matches = evaluated.filter { (_, proof) -> proof.bindingStatus == RequirementStatus.Satisfied }
        val known = matches.map { key(it.first) }.toSet()
        val uncertain = evaluated.filter { it.second.canSatisfy &&
            (it.second.bindingStatus == RequirementStatus.SourceUnresolved || it.second.bindingStatus == RequirementStatus.ChoiceRequired) }
        val possible = known + uncertain.map { key(it.first) }
        val lower = known.size
        val upper = possible.size
        // A definite enough lower bound can satisfy GE, but uncertainty cannot prove EQ/LE.
        val truth = (lower..upper).map { compare(it, node.operator, node.operand) }.distinct()
        val status = when {
            truth == listOf(true) -> RequirementStatus.Satisfied
            truth == listOf(false) -> RequirementStatus.Unsatisfied
            uncertain.any { it.second.bindingStatus == RequirementStatus.SourceUnresolved } -> RequirementStatus.SourceUnresolved
            else -> RequirementStatus.ChoiceRequired
        }
        return result(node, status, matches.flatMap { it.second.witnesses }.distinct(), evaluated.map { it.second },
            if (truth.size > 1) "Distinct count includes unresolved candidates ($lower..$upper)" else null,
            RequirementValue.Number(lower), RequirementValue.Number(node.operand))
    }

    /** A typed predicate can bind only the definitions it names. Unknown predicates retain
     * every candidate and their original uncertainty; this index never proves a match.
     */
    private fun countCandidateDefinitions(node: RequirementNode, depth: Int): Set<DefinitionRef>? {
        if(depth>=maxDepth || budget.remaining--<=0) return null
        return when(node) {
            is RequirementNode.OwnedRank -> setOf(DefinitionRef(node.packId,node.entryId))
            is RequirementNode.Specialization -> setOf(DefinitionRef(node.packId,node.entryId))
            is RequirementNode.SkillFamily -> {
                val definitions=context.catalog.definitions.values.filter {
                    it.kind==AcquisitionKind.SKILL && it.ref.packId==node.packId && (it.ref.entryId==node.familyId ||
                        (node.familyId in it.familyIds && "family:${node.familyId}" in it.classificationSources))
                }.map { it.ref }.toSet()
                definitions.takeIf { it.isNotEmpty() }
            }
            is RequirementNode.BranchAccess -> if(node.operand) setOf(DefinitionRef(node.packId,node.branchId)) else emptySet()
            is RequirementNode.LearnedSpell -> if(node.operand) setOf(DefinitionRef(node.packId,node.entryId)) else emptySet()
            is RequirementNode.SelectedInstance -> context.instances.filter { it.instanceId==subjectParameters[node.parameterKey] }.map { it.definition }.toSet()
            is RequirementNode.AllOf, is RequirementNode.AnyOf -> {
                val children=if(node is RequirementNode.AllOf) node.children else (node as RequirementNode.AnyOf).children
                val definitions=mutableSetOf<DefinitionRef>()
                for(child in children) definitions+=countCandidateDefinitions(child,depth+1) ?: return null
                definitions
            }
            is RequirementNode.Unresolved -> null
            else -> emptySet()
        }
    }

    private fun aggregate(children: List<Proof>, any: Boolean): RequirementStatus {
        if (any && children.any { it.status == RequirementStatus.Satisfied }) return RequirementStatus.Satisfied
        if (!any && children.all { it.status == RequirementStatus.Satisfied }) return RequirementStatus.Satisfied
        val precedence = if (any) listOf(RequirementStatus.SourceUnresolved, RequirementStatus.ChoiceRequired, RequirementStatus.Conflict, RequirementStatus.Unsatisfied)
            else listOf(RequirementStatus.Conflict, RequirementStatus.SourceUnresolved, RequirementStatus.ChoiceRequired, RequirementStatus.Unsatisfied)
        return precedence.firstOrNull { status -> children.any { it.status == status } } ?: RequirementStatus.Unsatisfied
    }

    private fun compositeBinding(children: List<Proof>, status: RequirementStatus, any: Boolean): RequirementStatus? {
        if (!any && children.any { !it.canSatisfy }) return null
        val bindings = children.filter { it.canSatisfy }.mapNotNull { it.bindingStatus }
        if (bindings.isEmpty()) return null
        // A bound conjunct still needs every other conjunct's permission, even an unbound external fact.
        if (!any && status != RequirementStatus.Satisfied) return status
        return listOf(RequirementStatus.Satisfied, RequirementStatus.SourceUnresolved, RequirementStatus.ChoiceRequired)
            .firstOrNull { it in bindings }
    }

    private fun binds(candidate: Candidate, proof: Proof): Boolean = proof.witnesses.any {
        when (it) {
            is RequirementWitness.OwnedInstance -> it.packId == candidate.definition.packId && it.entryId == candidate.definition.entryId &&
                it.instanceId == candidate.instance?.instanceId
            is RequirementWitness.LearnedSpell -> it.packId == candidate.definition.packId && it.entryId == candidate.definition.entryId
            is RequirementWitness.Branch -> it.packId == candidate.definition.packId && it.branchId == candidate.definition.entryId
            is RequirementWitness.External -> false
        }
    }

    private fun number(node: RequirementNode, actual: Int, operator: ComparisonOperator, required: Int,
        witnesses: List<RequirementWitness> = emptyList(), forceAbsent: Boolean = false) =
        result(node, if (!forceAbsent && compare(actual, operator, required)) RequirementStatus.Satisfied else RequirementStatus.Unsatisfied,
            witnesses, actual = RequirementValue.Number(actual), required = RequirementValue.Number(required))
    private fun unresolved(node: RequirementNode, reason: String) = result(node, RequirementStatus.SourceUnresolved, reason = reason)
    private fun result(node: RequirementNode, status: RequirementStatus, witnesses: List<RequirementWitness> = emptyList(),
        children: List<Proof> = emptyList(), reason: String? = null, actual: RequirementValue? = null, required: RequirementValue? = null) =
        Proof(RequirementResult(status, node.nodeId, witnesses, children.map { it.result }, reason, actual, required))
}

/** Capture every reachable price container; T02 nodes and T03 domains/policies already own immutable snapshots. */
private fun snapshotPrice(price: PriceRule): PriceRule = when (price) {
    is PriceRule.Fixed, is PriceRule.PerRank, is PriceRule.Unknown -> price
    is PriceRule.TransitionTable -> price.copy(transitions = FactList(price.transitions))
    is PriceRule.ByOption -> price.copy(options = FactMap(price.options.mapValues { snapshotPrice(it.value) }))
    is PriceRule.Conditional -> price.copy(whenTrue = snapshotPrice(price.whenTrue), whenFalse = snapshotPrice(price.whenFalse))
}

private fun compare(actual: Int, operator: ComparisonOperator, required: Int): Boolean = when (operator) {
    ComparisonOperator.EQ -> actual == required
    ComparisonOperator.GE -> actual >= required
    ComparisonOperator.LE -> actual <= required
    ComparisonOperator.LT -> actual < required
    ComparisonOperator.GT -> actual > required
}

private class FactList<T>(values: Collection<T>) : AbstractList<T>() {
    private val snapshot = values.toList()
    override val size get() = snapshot.size
    override fun get(index: Int): T = snapshot[index]
}
private class FactSet<T>(values: Collection<T>) : AbstractSet<T>() {
    private val snapshot = values.toSet().toList()
    override val size get() = snapshot.size
    override fun iterator(): Iterator<T> = object : Iterator<T> {
        private var index = 0
        override fun hasNext() = index < snapshot.size
        override fun next(): T = if (hasNext()) snapshot[index++] else throw NoSuchElementException()
    }
}
private class FactMap<K, V>(values: Map<K, V>) : AbstractMap<K, V>() {
    private data class Entry<K, V>(override val key: K, override val value: V) : Map.Entry<K, V>
    override val entries: Set<Map.Entry<K, V>> = FactSet(values.map { Entry(it.key, it.value) })
}
