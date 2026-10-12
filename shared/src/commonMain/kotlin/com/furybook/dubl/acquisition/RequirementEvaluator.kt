package com.furybook.dubl.acquisition

import com.furybook.dubl.model.DublCharacter

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
) {
    val character: DublCharacter = character.copy(attributes = FactMap(character.attributes))
    val instances: List<AcquisitionInstance> = FactList(instances)
    val learnedSpellIds: Set<DefinitionRef> = FactSet(learnedSpellIds)
    val externalEvidence: List<RequirementWitness.External> = FactList(externalEvidence)
    val catalog: AcquisitionCatalog = catalog.copy(definitions = FactMap(catalog.definitions.mapValues { (_, d) ->
        d.copy(familyIds = FactSet(d.familyIds), categoryIds = FactSet(d.categoryIds),
            classificationSources = FactMap(d.classificationSources))
    }))
}

/** One semantic entry point for typed requirements; no string parser or UI dependency. */
fun evaluate(node: RequirementNode, context: AcquisitionContext): RequirementResult =
    Evaluation(context).visit(node, emptySet(), 0)

private class Evaluation(private val context: AcquisitionContext) {
    // Depth protects the common targets' stacks; work bounds pathological branching as well as cycles.
    private var remaining = 16_384
    private val maxDepth = 64
    data class Candidate(val definition: DefinitionRef, val instance: AcquisitionInstance?)

    fun visit(node: RequirementNode, path: Set<DefinitionRef>, depth: Int, candidate: Candidate? = null,
        ownershipOnly: Boolean = false): RequirementResult {
        if (depth >= maxDepth || remaining-- <= 0) return unresolved(node, "Requirement traversal limit reached")
        return when (node) {
            is RequirementNode.AllOf -> {
                val children = node.children.map { visit(it, path, depth + 1, candidate, ownershipOnly) }
                val status = aggregate(children, any = false)
                result(node, status, children.flatMap { it.witnesses }.distinct(), children)
            }
            is RequirementNode.AnyOf -> {
                val children = mutableListOf<RequirementResult>()
                // No failure memo: a cyclic path must not invalidate a different proof of the same ID.
                for (child in node.children) {
                    val proof = visit(child, path, depth + 1, candidate, ownershipOnly)
                    children += proof
                    if (proof.status == RequirementStatus.Satisfied) {
                        return result(node, RequirementStatus.Satisfied, proof.witnesses, children)
                    }
                }
                result(node, aggregate(children, any = true), children = children)
            }
            is RequirementNode.Attribute -> number(node, context.character.attribute(node.attributeId), node.operator, node.operand)
            is RequirementNode.OwnedRank -> ownership(node, DefinitionRef(node.packId, node.entryId), node.operator,
                node.operand, node.instanceId, null, null, path, depth, candidate, ownershipOnly)
            is RequirementNode.Specialization -> ownership(node, DefinitionRef(node.packId, node.entryId), node.operator,
                node.operand, node.instanceId, node.qualifier, null, path, depth, candidate, ownershipOnly)
            is RequirementNode.SkillFamily -> family(node, path, depth, candidate, ownershipOnly)
            is RequirementNode.BranchAccess -> branch(node, path, depth, candidate, ownershipOnly)
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
    }

    private fun ownership(node: RequirementNode, ref: DefinitionRef, operator: ComparisonOperator, operand: Int,
        instanceId: String?, qualifier: ParameterMatch?, optionId: String?, path: Set<DefinitionRef>, depth: Int,
        candidate: Candidate?, ownershipOnly: Boolean): RequirementResult {
        if (candidate != null && candidate.definition != ref) return number(node, 0, operator, operand, forceAbsent = true)
        val definition = context.catalog.definitions[ref] ?: return unresolved(node, "Missing definition $ref")
        if (!ownershipOnly) {
            if (definition.sourceStatus == SourceStatus.Unavailable) return unresolved(node, "Source unavailable for $ref")
            if (definition.maxRank == null || operand < 0 || operand > definition.maxRank) return unresolved(node, "Unknown or invalid rank boundary for $ref")
        }
        val instances = context.instances.filter { it.definition == ref && (instanceId == null || it.instanceId == instanceId) &&
            (candidate == null || it.instanceId == candidate.instance?.instanceId) }
        val filtered = instances.filter { (qualifier == null || it.parameters[qualifier.key] == qualifier.valueId) &&
            (optionId == null || it.abilityOptionId == optionId) }
        val matching = filtered.filter { compare(it.rank, operator, operand) }
        if (matching.isEmpty()) {
            val unknown = instances.any { compare(it.rank, operator, operand) &&
                (qualifier == null || it.parameters[qualifier.key] == null || it.parameters[qualifier.key] == qualifier.valueId) &&
                (optionId == null || it.abilityOptionId == null || it.abilityOptionId == optionId) }
            if (unknown && (qualifier != null || optionId != null)) return result(node, RequirementStatus.ChoiceRequired,
                reason = "Confirm unknown legacy parameter or access option", required = RequirementValue.Number(operand))
            return number(node, filtered.maxOfOrNull { it.rank } ?: 0, operator, operand, forceAbsent = filtered.isEmpty())
        }
        val proofs = mutableListOf<RequirementResult>()
        for (owned in matching) {
            if (context.instances.count { it.instanceId == owned.instanceId } != 1) {
                proofs += result(node, RequirementStatus.Conflict, reason = "Duplicate instance ID ${owned.instanceId}")
                continue
            }
            if (!ownershipOnly && owned.rank > (definition.maxRank ?: 0)) {
                proofs += unresolved(node, "Owned rank exceeds source boundary for $ref")
                continue
            }
            val witness = RequirementWitness.OwnedInstance(ref.packId, ref.entryId, owned.instanceId, owned.rank)
            if (ownershipOnly) return number(node, owned.rank, operator, operand, listOf(witness))
            val proof = prerequisite(node, definition, path, depth)
            proofs += proof
            if (proof.status == RequirementStatus.Satisfied) return result(node, RequirementStatus.Satisfied,
                listOf(witness) + proof.witnesses, listOf(proof), actual = RequirementValue.Number(owned.rank), required = RequirementValue.Number(operand))
        }
        return result(node, aggregate(proofs, any = true), children = proofs,
            reason = "Owned prerequisite cannot prove its requirements", required = RequirementValue.Number(operand))
    }

    private fun prerequisite(node: RequirementNode, definition: AcquisitionDefinition, path: Set<DefinitionRef>, depth: Int): RequirementResult {
        if (definition.ref in path) return unresolved(node, "Requirement cycle: ${(path + definition.ref).joinToString(" -> ")}")
        // Reset count's candidate restriction: this object's dependencies may be other owned objects.
        return visit(definition.requirements, path + definition.ref, depth + 1)
    }

    private fun family(node: RequirementNode.SkillFamily, path: Set<DefinitionRef>, depth: Int, candidate: Candidate?, ownershipOnly: Boolean): RequirementResult {
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
            required = RequirementValue.Number(node.operand))
    }

    private fun branch(node: RequirementNode.BranchAccess, path: Set<DefinitionRef>, depth: Int, candidate: Candidate?, ownershipOnly: Boolean): RequirementResult {
        val presence = ownership(node, DefinitionRef(node.packId, node.branchId), ComparisonOperator.GE, 1, null, null,
            node.accessOptionId, path, depth, candidate, ownershipOnly)
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
            required = RequirementValue.Flag(node.operand))
    }

    private fun spell(node: RequirementNode.LearnedSpell, path: Set<DefinitionRef>, depth: Int, candidate: Candidate?, ownershipOnly: Boolean): RequirementResult {
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
    }

    private fun count(node: RequirementNode.DistinctCount, path: Set<DefinitionRef>, depth: Int, ownershipOnly: Boolean): RequirementResult {
        val candidates = context.instances.map { Candidate(it.definition, it) } +
            context.learnedSpellIds.filter { ref -> context.instances.none { it.definition == ref } }.map { Candidate(it, null) }
        val evaluated = candidates.map { it to visit(node.predicate, path, depth + 1, it, ownershipOnly) }
        fun key(candidate: Candidate): Any = if (node.uniqueness == DistinctScope.Definition) candidate.definition
            else candidate.instance?.instanceId ?: candidate.definition
        fun binds(candidate: Candidate, proof: RequirementResult): Boolean = proof.witnesses.any {
            when (it) {
                is RequirementWitness.OwnedInstance -> it.packId == candidate.definition.packId && it.entryId == candidate.definition.entryId &&
                    it.instanceId == candidate.instance?.instanceId
                is RequirementWitness.LearnedSpell -> it.packId == candidate.definition.packId && it.entryId == candidate.definition.entryId
                is RequirementWitness.Branch -> it.packId == candidate.definition.packId && it.branchId == candidate.definition.entryId
                is RequirementWitness.External -> false
            }
        }
        val matches = evaluated.filter { (candidate, proof) -> proof.status == RequirementStatus.Satisfied && binds(candidate, proof) }
        val known = matches.map { key(it.first) }.toSet()
        val uncertain = evaluated.filter { it.second.status == RequirementStatus.SourceUnresolved || it.second.status == RequirementStatus.ChoiceRequired }
        val possible = known + uncertain.map { key(it.first) }
        val lower = known.size
        val upper = possible.size
        // A definite enough lower bound can satisfy GE, but uncertainty cannot prove EQ/LE.
        val truth = (lower..upper).map { compare(it, node.operator, node.operand) }.distinct()
        val status = when {
            truth == listOf(true) -> RequirementStatus.Satisfied
            truth == listOf(false) -> RequirementStatus.Unsatisfied
            uncertain.any { it.second.status == RequirementStatus.SourceUnresolved } -> RequirementStatus.SourceUnresolved
            else -> RequirementStatus.ChoiceRequired
        }
        return result(node, status, matches.flatMap { it.second.witnesses }.distinct(), evaluated.map { it.second },
            if (truth.size > 1) "Distinct count includes unresolved candidates ($lower..$upper)" else null,
            RequirementValue.Number(lower), RequirementValue.Number(node.operand))
    }

    private fun aggregate(children: List<RequirementResult>, any: Boolean): RequirementStatus {
        if (any && children.any { it.status == RequirementStatus.Satisfied }) return RequirementStatus.Satisfied
        if (!any && children.all { it.status == RequirementStatus.Satisfied }) return RequirementStatus.Satisfied
        val precedence = if (any) listOf(RequirementStatus.SourceUnresolved, RequirementStatus.ChoiceRequired, RequirementStatus.Conflict, RequirementStatus.Unsatisfied)
            else listOf(RequirementStatus.Conflict, RequirementStatus.SourceUnresolved, RequirementStatus.ChoiceRequired, RequirementStatus.Unsatisfied)
        return precedence.firstOrNull { status -> children.any { it.status == status } } ?: RequirementStatus.Unsatisfied
    }

    private fun number(node: RequirementNode, actual: Int, operator: ComparisonOperator, required: Int,
        witnesses: List<RequirementWitness> = emptyList(), forceAbsent: Boolean = false) =
        result(node, if (!forceAbsent && compare(actual, operator, required)) RequirementStatus.Satisfied else RequirementStatus.Unsatisfied,
            witnesses, actual = RequirementValue.Number(actual), required = RequirementValue.Number(required))
    private fun unresolved(node: RequirementNode, reason: String) = result(node, RequirementStatus.SourceUnresolved, reason = reason)
    private fun result(node: RequirementNode, status: RequirementStatus, witnesses: List<RequirementWitness> = emptyList(),
        children: List<RequirementResult> = emptyList(), reason: String? = null, actual: RequirementValue? = null, required: RequirementValue? = null) =
        RequirementResult(status, node.nodeId, witnesses, children, reason, actual, required)
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
