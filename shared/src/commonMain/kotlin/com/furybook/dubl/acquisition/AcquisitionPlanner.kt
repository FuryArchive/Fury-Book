package com.furybook.dubl.acquisition

import com.furybook.core.json.*
import com.furybook.dubl.model.*

/** Parameters and selections are stable IDs. A null instance selects only an unambiguous Single definition. */
class AcquisitionTarget(val definition: DefinitionRef, val instanceId: String? = null, val targetRank: Int,
    parameters: Map<String, String> = emptyMap(), val optionId: String? = null) {
    val parameters: Map<String, String> = PlannerMap(parameters)
    fun copy(definition: DefinitionRef = this.definition, instanceId: String? = this.instanceId,
        targetRank: Int = this.targetRank, parameters: Map<String,String> = this.parameters, optionId: String? = this.optionId) =
        AcquisitionTarget(definition, instanceId, targetRank, parameters, optionId)
}

class AcquisitionRequest(val characterId: String, targets: List<AcquisitionTarget>,
    choiceSelections: Map<String, String> = emptyMap(), val includeTargets: Boolean = true,
    /** Explicit parameter/instance selections for a prerequisite question, keyed by its stable scoped ID. */
    prerequisiteSelections: Map<String, List<AcquisitionTarget>> = emptyMap(),
    externalConfirmations: List<RequirementWitness.External> = emptyList()) {
    val externalConfirmations: List<RequirementWitness.External> = EnvelopeList(externalConfirmations)
    val targets: List<AcquisitionTarget> = EnvelopeList(targets)
    val choiceSelections: Map<String,String> = PlannerMap(choiceSelections)
    val prerequisiteSelections: Map<String,List<AcquisitionTarget>> = PlannerMap(prerequisiteSelections.mapValues { EnvelopeList(it.value) })
    val canonical: String get() = canonicalJson(jsonStringify(jsonObject(
        "externalConfirmations" to JsonValue.Arr(externalConfirmations.map(::externalJson)),
        "characterId" to jsonString(characterId), "targets" to JsonValue.Arr(targets.map(::targetJson)),
        "choiceSelections" to stringMap(choiceSelections), "includeTargets" to JsonValue.Bool(includeTargets),
        "prerequisiteSelections" to JsonValue.Obj(prerequisiteSelections.mapValues { JsonValue.Arr(it.value.map(::targetJson)) }))))
}

data class AcquisitionIssue(val status: RequirementStatus, val reason: String, val definition: DefinitionRef? = null,
    val nodeId: String? = null, val sourceRef: RuleSourceRef? = null)
class AcquisitionChoiceOption(val id: String, val label: String, changes: List<AcquisitionChange>, quote: PriceResult,
    issues: List<AcquisitionIssue>, questions: List<AcquisitionChoice> = emptyList()) {
    val quote: PriceResult = if (quote is PriceResult.Known) snapshotQuote(quote) else quote
    val changes: List<AcquisitionChange> = EnvelopeList(changes)
    val issues: List<AcquisitionIssue> = EnvelopeList(issues)
    val questions: List<AcquisitionChoice> = EnvelopeList(questions)
}
class AcquisitionChoice(val id: String, val label: String, options: List<AcquisitionChoiceOption>, val selectedId: String? = null) {
    val options: List<AcquisitionChoiceOption> = EnvelopeList(options)
    val resolved: Boolean get() = selectedId != null && options.any { it.id == selectedId }
}
class AcquisitionPlan(val request: AcquisitionRequest, val baseRevision: Long, val catalogRevision: String,
    val resolutionRevision: String, changes: List<AcquisitionChange>, questions: List<AcquisitionChoice>,
    issues: List<AcquisitionIssue>, val cost: Amount?, val remaining: Amount?, val previewToken: String,
    val projectedContext: AcquisitionContext, prices: List<PriceResult.Known>) {
    val changes: List<AcquisitionChange> = EnvelopeList(changes)
    val questions: List<AcquisitionChoice> = EnvelopeList(questions)
    val issues: List<AcquisitionIssue> = EnvelopeList(issues)
    /** One quote per change, in execution order, including source and discount evidence. */
    val prices: List<PriceResult.Known> = EnvelopeList(prices.map(::snapshotQuote))
    val executable: Boolean get() = issues.isEmpty() && questions.all { it.resolved } && cost != null &&
        remaining != null && remaining.xp >= 0 && remaining.ap >= 0
}
class AcquisitionProjection(val context: AcquisitionContext, issues: List<AcquisitionIssue>, prices: List<PriceResult.Known>) {
    val issues: List<AcquisitionIssue> = EnvelopeList(issues)
    val prices: List<PriceResult.Known> = EnvelopeList(prices.map(::snapshotQuote))
}

/** Stable catalog IDs only; unknown or ambiguous legacy spell links supply no learned fact. */
fun acquisitionContext(state: StoredCharacter, catalog: AcquisitionCatalog,
    pendingConfirmations: List<RequirementWitness.External> = emptyList()): AcquisitionContext {
    val learned = state.character.magic.spells.filter { it.learned && !it.custom }.mapNotNull { spell ->
        spell.catalogId?.let { id -> catalog.definitions.values.singleOrNull { it.ref.entryId == id && it.kind == AcquisitionKind.SPELL }?.ref }
    }.toSet()
    return AcquisitionContext(state.character, state.instances, learned, state.creationPhase, retainedAcquisitionConfirmations(state) + pendingConfirmations, catalog)
}

fun plan(request: AcquisitionRequest, state: StoredCharacter, catalog: AcquisitionCatalog): AcquisitionPlan =
    Planning(request, state, acquisitionContext(state, catalog, request.externalConfirmations)).run()

/** Replay the exact ordered changes. Known price alone never admits a change. No persistence or debit. */
fun validateAcquisitionProjection(changes: List<AcquisitionChange>, state: StoredCharacter,
    catalog: AcquisitionCatalog, pendingConfirmations: List<RequirementWitness.External> = emptyList()): AcquisitionProjection {
    val issues = mutableListOf<AcquisitionIssue>()
    for (confirmation in pendingConfirmations) try { validateExternal(confirmation) } catch (_: IllegalArgumentException) {
        issues += AcquisitionIssue(RequirementStatus.Conflict, "Invalid external confirmation")
    }
    if (issues.isNotEmpty()) return AcquisitionProjection(acquisitionContext(state,catalog),issues,emptyList())
    var context = acquisitionContext(state, catalog, pendingConfirmations)
    val prices = mutableListOf<PriceResult.Known>()
    for (change in changes) {
        val checked = admit(change, context)
        if (checked.issue != null) { issues += checked.issue; break }
        prices += checked.price!!
        context = project(change, context)
    }
    if (issues.isEmpty()) for (change in changes) {
        val d = context.catalog.definitions.getValue(change.definition)
        val proof = evaluate(d.requirements, context)
        if (proof.status != RequirementStatus.Satisfied) issues += issue(d.ref, proof)
    }
    return AcquisitionProjection(context, issues, prices)
}

private class Planning(val request: AcquisitionRequest, val state: StoredCharacter, val initial: AcquisitionContext) {
    private var work = 4096
    private data class Draft(val context: AcquisitionContext, val changes: List<AcquisitionChange> = emptyList(),
        val prices: List<PriceResult.Known> = emptyList(), val questions: List<AcquisitionChoice> = emptyList(),
        val issues: List<AcquisitionIssue> = emptyList())
    private val usedChoices = mutableSetOf<String>()
    private val usedPrerequisites = mutableSetOf<String>()

    fun run(): AcquisitionPlan {
        var draft = Draft(initial)
        for (confirmation in request.externalConfirmations) try { validateExternal(confirmation) } catch (_: IllegalArgumentException) {
            draft = block(draft, null, "Invalid external confirmation", RequirementStatus.Conflict)
        }
        if (request.characterId != state.character.id) draft = block(draft, null, "Character identity mismatch", RequirementStatus.Conflict)
        if (initial.instances.map { it.instanceId }.distinct().size != initial.instances.size)
            draft = block(draft, null, "Duplicate character instance IDs", RequirementStatus.Conflict)
        if (state.character.creationComplete != (state.creationPhase == RequirementNode.CreationPhase.Phase.Finalized))
            draft = block(draft, null, "Creation phase differs from character", RequirementStatus.Conflict)
        if (draft.issues.isEmpty()) for (target in request.targets) draft = acquire(draft, target, emptySet(), request.includeTargets)
        if (request.choiceSelections.keys.any { it !in usedChoices } || request.prerequisiteSelections.keys.any { it !in usedPrerequisites })
            draft = block(draft, null, "Stale or unused prerequisite/choice selection", RequirementStatus.Conflict)
        val replay = validateAcquisitionProjection(draft.changes, state, initial.catalog, request.externalConfirmations)
        draft = draft.copy(issues = (draft.issues + replay.issues).distinct())
        var cost: Amount? = null
        var remaining: Amount? = null
        if (draft.issues.isEmpty() && draft.questions.all { it.resolved }) try {
            cost = draft.prices.fold(Amount.ZERO) { a, p -> a + p.cost }
            remaining = balance(state.ledger) - cost
            if (remaining.xp < 0 || remaining.ap < 0) draft = block(draft, null, "Insufficient canonical balance", RequirementStatus.Unsatisfied)
        } catch (_: ArithmeticException) {
            draft = block(draft, null, "Amount overflow", RequirementStatus.SourceUnresolved)
            cost = null; remaining = null
        }
        val token = canonicalJson(jsonStringify(jsonObject(
            "request" to JsonReader(request.canonical).read(), "characterId" to jsonString(state.character.id),
            "baseRevision" to jsonString(state.revision.toString()), "catalogRevision" to jsonString(initial.catalog.catalogRevision),
            "resolutionRevision" to jsonString(initial.catalog.resolutionRevision), "phase" to jsonString(state.creationPhase.name),
            "changes" to JsonValue.Arr(draft.changes.map(::changeJson)), "cost" to amountJson(cost), "remaining" to amountJson(remaining),
            "issues" to JsonValue.Arr(draft.issues.map { jsonObject("status" to jsonString(it.status.name), "reason" to jsonString(it.reason),
                "definition" to (it.definition?.let(::refJson) ?: JsonValue.Null), "nodeId" to nullable(it.nodeId)) }),
            "choices" to JsonValue.Arr(draft.questions.map { jsonObject("id" to jsonString(it.id), "selectedId" to nullable(it.selectedId)) }),
            "quotes" to JsonValue.Arr(draft.prices.map { jsonObject("cost" to amountJson(it.cost),
                "sources" to JsonValue.Arr(it.componentSources.map(::sourceJson)),
                "discountEvidence" to JsonValue.Arr(it.discountEvidence.map(::proofJson))) })) ))
        return AcquisitionPlan(request, state.revision, initial.catalog.catalogRevision, initial.catalog.resolutionRevision,
            draft.changes, draft.questions, draft.issues, cost, remaining, token, draft.context, draft.prices)
    }

    private fun acquire(start: Draft, target: AcquisitionTarget, path: Set<DefinitionRef>, include: Boolean = true): Draft {
        val d = start.context.catalog.definitions[target.definition]
            ?: return block(start, target.definition, "Missing catalog definition", RequirementStatus.SourceUnresolved)
        if (work-- <= 0 || path.size >= 64) return block(start, d.ref, "Planner traversal limit reached", RequirementStatus.SourceUnresolved)
        val resolved = resolveTarget(target, d, start.context)
        if (resolved.issue != null) return start.copy(issues = start.issues + resolved.issue)
        val change = resolved.change!!
        if (include && change.fromRank == change.toRank) return start
        if (d.ref in path) return block(start, d.ref, "Cyclic dependency", RequirementStatus.SourceUnresolved)
        val next = satisfy(start, d.requirements, d.ref, path + d.ref)
        val proof = evaluate(d.requirements, next.context)
        if (proof.status != RequirementStatus.Satisfied) return next.copy(issues = next.issues + issue(d.ref, proof))
        if (!include) return next
        // Dependencies may have changed this instance. Re-resolve against the new projection.
        val now = resolveTarget(target, d, next.context)
        if (now.issue != null) return next.copy(issues = next.issues + now.issue)
        val actual = now.change!!
        if (actual.fromRank == actual.toRank) return next
        val checked = admit(actual, next.context)
        if (checked.issue != null) return next.copy(issues = next.issues + checked.issue)
        return next.copy(context = project(actual, next.context), changes = next.changes + actual, prices = next.prices + checked.price!!)
    }

    private fun satisfy(start: Draft, node: RequirementNode, owner: DefinitionRef, path: Set<DefinitionRef>, depth: Int = 0): Draft {
        if (depth >= 64 || work-- <= 0) return block(start, owner, "Planner traversal limit reached", RequirementStatus.SourceUnresolved, node.nodeId)
        if (evaluate(node, start.context).status == RequirementStatus.Satisfied) return start
        val key = acquisitionChoiceId(owner, node.nodeId)
        val supplied = request.prerequisiteSelections[key]
        if (supplied != null) {
            usedPrerequisites += key
            if (node is RequirementNode.Unresolved || node is RequirementNode.ExternalEvidence || node is RequirementNode.Exclusive || node is RequirementNode.CreationPhase)
                return block(start, owner, "A target selection cannot replace source or external evidence", RequirementStatus.SourceUnresolved, node.nodeId)
            var next = start
            for (target in supplied) next = acquire(next, target, path)
            val proof = evaluate(node, next.context)
            return if (proof.status == RequirementStatus.Satisfied) next else next.copy(issues = next.issues + issue(owner, proof))
        }
        return when (node) {
            is RequirementNode.AllOf -> node.children.fold(start) { draft, child -> satisfy(draft, child, owner, path, depth+1) }
            is RequirementNode.AnyOf -> {
                if (node.children.map { it.nodeId }.distinct().size != node.children.size)
                    return block(start, owner, "Duplicate alternative node IDs", RequirementStatus.SourceUnresolved, node.nodeId)
                val alternatives = node.children.map { child -> child.nodeId to satisfy(start, child, owner, path, depth+1) }
                val selected = request.choiceSelections[key]
                if (selected != null) usedChoices += key
                val choice = AcquisitionChoice(key, node.sourceRef.originalText, alternatives.map { (id, draft) ->
                    val issues = draft.issues.drop(start.issues.size)
                    val questions = draft.questions.drop(start.questions.size)
                    val child = node.children.single { it.nodeId == id }
                    val proof = evaluate(child, draft.context)
                    val failures = if (proof.status == RequirementStatus.Satisfied) issues else issues + issue(owner, proof)
                    val quoted = if (failures.isEmpty() && questions.all { it.resolved }) try {
                        PriceResult.Known(draft.prices.drop(start.prices.size).fold(Amount.ZERO) { a,p -> a+p.cost }, child.sourceRef,
                            componentSources=draft.prices.drop(start.prices.size).flatMap { it.componentSources })
                    } catch (_: ArithmeticException) { PriceResult.Unknown("Amount overflow") }
                    else PriceResult.Unknown("Alternative has unresolved requirements or selections")
                    AcquisitionChoiceOption(id, child.sourceRef.originalText, draft.changes.drop(start.changes.size), quoted, failures, questions)
                }, selected)
                val chosen = alternatives.singleOrNull { it.first == selected }?.second
                when {
                    chosen != null -> chosen.copy(questions = chosen.questions + choice)
                    selected != null -> block(start.copy(questions=start.questions+choice), owner, "Unknown stable alternative ID", RequirementStatus.Conflict, node.nodeId)
                    else -> start.copy(questions=start.questions+choice)
                }
            }
            is RequirementNode.OwnedRank -> dependency(start, node, owner, DefinitionRef(node.packId,node.entryId), node.operator, node.operand, node.instanceId, emptyMap(), null, path)
            is RequirementNode.Specialization -> dependency(start, node, owner, DefinitionRef(node.packId,node.entryId), node.operator, node.operand,
                node.instanceId, node.qualifier?.let { mapOf(it.key to it.valueId) }.orEmpty(), null, path)
            is RequirementNode.BranchAccess -> if (node.operand) dependency(start,node,owner,DefinitionRef(node.packId,node.branchId),ComparisonOperator.GE,1,null,emptyMap(),node.accessOptionId,path)
                else blockedProof(start,node,owner)
            is RequirementNode.LearnedSpell -> if (node.operand) dependency(start,node,owner,DefinitionRef(node.packId,node.entryId),ComparisonOperator.GE,1,null,emptyMap(),null,path)
                else blockedProof(start,node,owner)
            is RequirementNode.Attribute -> {
                val desired = desiredRank(node.operator,node.operand) ?: return blockedProof(start,node,owner)
                val current = start.context.character.attributes[node.attributeId] ?: return blockedProof(start,node,owner)
                val base = desired.toLong() - start.context.character.attribute(node.attributeId).toLong() + current.base
                if (base !in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) return blockedProof(start,node,owner)
                acquire(start, AcquisitionTarget(DefinitionRef("dubl-3.69", "attribute:${node.attributeId.name}"), targetRank=base.toInt()), path)
            }
            is RequirementNode.SkillFamily -> {
                val exact = start.context.catalog.definitions[DefinitionRef(node.packId,node.familyId)]
                if (exact?.kind == AcquisitionKind.SKILL) dependency(start,node,owner,exact.ref,node.operator,node.operand,null,emptyMap(),null,path)
                else {
                    val members = start.context.catalog.definitions.values.filter { it.kind == AcquisitionKind.SKILL &&
                        it.ref.packId == node.packId && node.familyId in it.familyIds && it.classificationSources["family:${node.familyId}"] != null }
                    val options = members.map { RequirementNode.OwnedRank(node.nodeId+"/member/"+it.ref.entryId,node.sourceRef,
                        it.ref.packId,it.ref.entryId,node.operator,node.operand) }
                    when (options.size) {
                        0 -> blockedProof(start,node,owner)
                        1 -> satisfy(start,options.single(),owner,path,depth+1)
                        else -> satisfy(start,RequirementNode.AnyOf(node.nodeId,node.sourceRef,options),owner,path,depth+1)
                    }
                }
            }
            is RequirementNode.DistinctCount -> start.copy(questions=start.questions +
                AcquisitionChoice(key, "Select explicit prerequisite instances: ${node.sourceRef.originalText}", emptyList()))
            else -> blockedProof(start,node,owner)
        }
    }

    private fun dependency(start: Draft, node: RequirementNode, owner: DefinitionRef, ref: DefinitionRef,
        operator: ComparisonOperator, operand: Int, instanceId: String?, parameters: Map<String,String>, option: String?, path: Set<DefinitionRef>): Draft {
        val rank = desiredRank(operator, operand) ?: return blockedProof(start,node,owner)
        val d = start.context.catalog.definitions[ref] ?: return block(start,ref,"Missing prerequisite definition",RequirementStatus.SourceUnresolved,node.nodeId)
        val matches = selectInstances(ref,parameters,start.context.instances).filter { (instanceId == null || it.instanceId == instanceId) && (option == null || it.abilityOptionId == option) }
        val existing = matches.singleOrNull()
        if (matches.size > 1 || (existing == null && (d.parameterDefinitions.isNotEmpty() || d.repeatPolicy !is RepeatPolicy.Single ||
                (option == null && !d.abilityOptionIds.isNullOrEmpty())))) {
            val unknown = d.parameterDefinitions.mapNotNull { (it.domain as? ParameterDomain.Unresolved)?.reason }
            var next = start.copy(questions=start.questions + AcquisitionChoice(acquisitionChoiceId(owner,node.nodeId),
                "Select stable instance, parameters and option for ${ref.entryId}", emptyList()))
            if (unknown.isNotEmpty()) next = block(next,ref,unknown.joinToString("; "),RequirementStatus.SourceUnresolved,node.nodeId)
            return next
        }
        val target = AcquisitionTarget(ref, existing?.instanceId ?: instanceId, maxOf(rank, existing?.rank ?: rank),
            existing?.parameters ?: parameters, existing?.abilityOptionId ?: option)
        val next = acquire(start,target,path)
        val proof = evaluate(node,next.context)
        return if (proof.status == RequirementStatus.Satisfied) next else next.copy(issues=next.issues+issue(owner,proof))
    }
    private fun blockedProof(start: Draft,node: RequirementNode,owner: DefinitionRef) = start.copy(issues=start.issues+issue(owner,evaluate(node,start.context)))
    private fun block(start: Draft, ref: DefinitionRef?, reason: String, status: RequirementStatus, node: String? = null) =
        start.copy(issues=start.issues+AcquisitionIssue(status,reason,ref,node))
}

fun acquisitionChoiceId(owner: DefinitionRef, nodeId: String): String = framed("choice", listOf(owner.packId, owner.entryId, nodeId))
private fun desiredRank(operator: ComparisonOperator, operand: Int): Int? = when (operator) {
    ComparisonOperator.GE, ComparisonOperator.EQ -> operand
    ComparisonOperator.GT -> if (operand == Int.MAX_VALUE) null else operand+1
    else -> null
}
private data class Resolution(val change: AcquisitionChange? = null, val issue: AcquisitionIssue? = null)
private fun resolveTarget(target: AcquisitionTarget, d: AcquisitionDefinition, context: AcquisitionContext): Resolution {
    fun fail(reason: String, status: RequirementStatus = RequirementStatus.SourceUnresolved) = Resolution(issue=AcquisitionIssue(status,reason,d.ref))
    if (d.maxRank == null) return fail("Definition rank limit is unresolved")
    if (d.sourceStatus == SourceStatus.Unavailable) return fail("Source unavailable")
    val attribute = attributeId(d)
    if (d.kind == AcquisitionKind.ATTRIBUTE) {
        if (attribute == null) return fail("Unmapped stable attribute identity")
        val value = context.character.attributes[attribute] ?: return fail("Missing character attribute")
        if (target.targetRank > d.maxRank || target.targetRank < -5) return fail("Target outside attribute rank limits",RequirementStatus.Unsatisfied)
        if (target.targetRank < value.base) return fail("Attribute reduction requires separate operation",RequirementStatus.Conflict)
        if (target.parameters.isNotEmpty() || target.optionId != null) return fail("Unexpected attribute parameters or option",RequirementStatus.Conflict)
        val id = framed("attribute",listOf(context.character.id,d.ref.packId,d.ref.entryId))
        if (target.instanceId != null && target.instanceId != id) return fail("Attribute identity mismatch",RequirementStatus.Conflict)
        if (context.instances.any { it.instanceId == id || it.definition == d.ref }) return fail("Attribute ownership must use character base projection",RequirementStatus.Conflict)
        return Resolution(AcquisitionChange(d.kind,d.ref,id,value.base,target.targetRank,emptyMap(),null))
    }
    // T03 permits no-op legacy over-cap ranks; a planner target still must obey its confirmed cap.
    if (target.targetRank !in 1..d.maxRank) return fail("Target rank outside definition limits",RequirementStatus.Unsatisfied)
    val existing = context.instances.filter { it.definition == d.ref }
    val id = target.instanceId ?: when {
        d.repeatPolicy !is RepeatPolicy.Single -> return fail("Select explicit specialization instance ID",RequirementStatus.ChoiceRequired)
        existing.size > 1 -> return fail("Ambiguous existing instance",RequirementStatus.Conflict)
        existing.size == 1 -> existing.single().instanceId
        else -> framed("planned",listOf(context.character.id,d.ref.packId,d.ref.entryId))
    }
    val identity = validateAcquisitionTarget(d.ref,d.maxRank,d.parameterDefinitions,d.repeatPolicy,id,target.targetRank,
        target.parameters,target.optionId,context.instances,d.abilityOptionIds)
    if (identity is IdentityValidation.Blocked) return fail(identity.reason,identity.status)
    val from = context.instances.singleOrNull { it.instanceId == id }?.rank ?: 0
    return Resolution(AcquisitionChange(d.kind,d.ref,id,from,target.targetRank,target.parameters,target.optionId))
}
private data class Admission(val price: PriceResult.Known? = null, val issue: AcquisitionIssue? = null)
private fun admit(change: AcquisitionChange, context: AcquisitionContext): Admission {
    val d = context.catalog.definitions[change.definition] ?: return Admission(issue=AcquisitionIssue(RequirementStatus.SourceUnresolved,"Missing definition",change.definition))
    val resolved = resolveTarget(AcquisitionTarget(change.definition,change.instanceId,change.toRank,change.parameters,change.optionId),d,context)
    if (resolved.issue != null) return Admission(issue=resolved.issue)
    if (resolved.change != change || change.fromRank >= change.toRank) return Admission(issue=AcquisitionIssue(RequirementStatus.Conflict,"Stale or invalid transition",d.ref))
    val proof = evaluate(d.requirements,context)
    if (proof.status != RequirementStatus.Satisfied) return Admission(issue=issue(d.ref,proof))
    return when (val price = quote(change,context)) {
        is PriceResult.Known -> Admission(price)
        is PriceResult.Unknown -> Admission(issue=AcquisitionIssue(RequirementStatus.SourceUnresolved,price.reason,d.ref,sourceRef=d.priceRule.sourceRef))
    }
}
private fun project(change: AcquisitionChange, context: AcquisitionContext): AcquisitionContext {
    val d = context.catalog.definitions.getValue(change.definition)
    val attribute = attributeId(d)
    val character = if (attribute != null) context.character.copy(attributes=context.character.attributes +
        (attribute to context.character.attributes.getValue(attribute).copy(base=change.toRank))) else context.character
    val old = context.instances.singleOrNull { it.instanceId == change.instanceId }
    val instances = if (attribute != null) context.instances else context.instances.filterNot { it.instanceId == change.instanceId } +
        AcquisitionInstance(change.instanceId,change.definition,change.toRank,change.parameters,change.optionId,old?.origin ?: AcquisitionOrigin.New)
    val learned = if (change.kind == AcquisitionKind.SPELL) context.learnedSpellIds + change.definition else context.learnedSpellIds
    return AcquisitionContext(character,instances,learned,context.creationPhase,context.externalEvidence,context.catalog)
}
private fun attributeId(d: AcquisitionDefinition): AttributeId? = if (d.kind == AcquisitionKind.ATTRIBUTE && d.ref.packId == "dubl-3.69")
    AttributeId.entries.singleOrNull { d.ref.entryId == "attribute:${it.name}" } else null
private fun issue(owner: DefinitionRef, proof: RequirementResult) = AcquisitionIssue(proof.status,
    proof.reason?.takeIf { it.isNotBlank() } ?: "Requirement ${proof.nodeId}: ${proof.status}",owner,proof.nodeId)
private fun framed(prefix: String, fields: List<String>) = prefix + ":" + fields.joinToString("") { "${it.length}:$it" }

private class PlannerMap<K,V>(values: Map<K,V>) : AbstractMap<K,V>() {
    private class Entry<K,V>(override val key: K, override val value: V) : Map.Entry<K,V> {
        override fun equals(other: Any?) = other is Map.Entry<*,*> && key == other.key && value == other.value
        override fun hashCode() = (key?.hashCode() ?: 0) xor (value?.hashCode() ?: 0)
    }
    private val captured = values.map { Entry(it.key,it.value) }
    override val entries: Set<Map.Entry<K,V>> = object : AbstractSet<Map.Entry<K,V>>() {
        override val size get() = captured.size
        override fun iterator(): Iterator<Map.Entry<K,V>> = object : Iterator<Map.Entry<K,V>> {
            private var index = 0
            override fun hasNext() = index < captured.size
            override fun next(): Map.Entry<K,V> = if (hasNext()) captured[index++] else throw NoSuchElementException()
        }
    }
}
private fun nullable(value: String?): JsonValue = value?.let(::jsonString) ?: JsonValue.Null
private fun stringMap(values: Map<String,String>) = JsonValue.Obj(values.mapValues { jsonString(it.value) })
private fun refJson(ref: DefinitionRef) = jsonObject("packId" to jsonString(ref.packId),"entryId" to jsonString(ref.entryId))
private fun amountJson(value: Amount?): JsonValue = value?.let { JsonReader(it.toJson()).read() } ?: JsonValue.Null
private fun targetJson(value: AcquisitionTarget) = jsonObject("definition" to refJson(value.definition),"instanceId" to nullable(value.instanceId),
    "targetRank" to JsonValue.Num(value.targetRank.toString()),"parameters" to stringMap(value.parameters),"optionId" to nullable(value.optionId))
private fun changeJson(value: AcquisitionChange) = jsonObject("kind" to jsonString(value.kind.name),"definition" to refJson(value.definition),
    "instanceId" to jsonString(value.instanceId),"fromRank" to JsonValue.Num(value.fromRank.toString()),"toRank" to JsonValue.Num(value.toRank.toString()),
    "parameters" to stringMap(value.parameters),"optionId" to nullable(value.optionId))
internal fun sourceJson(value: RuleSourceRef) = jsonObject("documentId" to jsonString(value.documentId),"documentVersion" to jsonString(value.documentVersion),
    "originalText" to jsonString(value.originalText),"sheet" to nullable(value.sheet),"paragraph" to (value.paragraph?.let { JsonValue.Num(it.toString()) } ?: JsonValue.Null),
    "resolutionId" to nullable(value.resolutionId),"resolutionRevision" to nullable(value.resolutionRevision))
private fun proofJson(value: RequirementResult): JsonValue = jsonObject("nodeId" to jsonString(value.nodeId),"status" to jsonString(value.status.name),
    "witnesses" to JsonValue.Arr(value.witnesses.map(::witnessJson)),"reason" to nullable(value.reason),"children" to JsonValue.Arr(value.children.map(::proofJson)))

private fun witnessJson(value: RequirementWitness): JsonValue = when (value) {
    is RequirementWitness.External -> externalJson(value)
    is RequirementWitness.OwnedInstance -> jsonObject("type" to jsonString("OwnedInstance"),"packId" to jsonString(value.packId),"entryId" to jsonString(value.entryId),"instanceId" to jsonString(value.instanceId),"rank" to JsonValue.Num(value.rank.toString()))
    is RequirementWitness.Branch -> jsonObject("type" to jsonString("Branch"),"packId" to jsonString(value.packId),"branchId" to jsonString(value.branchId),"accessOptionId" to jsonString(value.accessOptionId))
    is RequirementWitness.LearnedSpell -> jsonObject("type" to jsonString("LearnedSpell"),"packId" to jsonString(value.packId),"entryId" to jsonString(value.entryId))
}

private fun snapshotQuote(price: PriceResult.Known) = price.copy(discountEvidence=EnvelopeList(price.discountEvidence), componentSources=EnvelopeList(price.componentSources))
