package com.furybook.dubl.acquisition

import com.furybook.content.FcpComposition
import com.furybook.content.FcpContentPack
import com.furybook.core.json.*
import com.furybook.dubl.model.AttributeId

/** Unknown rank caps are null, never unlimited. Monetary literals are decimal strings in FCP. */
enum class AcquisitionKind { DEVELOPMENT, ATTRIBUTE, SKILL, MANA, SCHOOL, SPELL, CHI_BONUS }
/** Completeness metadata, not a blanket gate: evaluate the requested transition's
 * requirements and price. Unavailable source is always an explicit root blocker. */
enum class SourceStatus { Confirmed, NeedsResolution, Unavailable }

sealed interface PriceRule {
    val sourceRef: RuleSourceRef
    /** Applies to the single 0 -> 1 purchase. Other transitions need explicit evidence. */
    data class Fixed(val xp: Long, val ap: Long, override val sourceRef: RuleSourceRef) : PriceRule
    /** Explicit source-confirmed per-step price; never inferred from a multi-rank literal. */
    data class PerRank(val xp: Long, val ap: Long, override val sourceRef: RuleSourceRef) : PriceRule
    data class Transition(val fromRank: Int, val toRank: Int, val xp: Long, val ap: Long, val sourceRef: RuleSourceRef)
    data class TransitionTable(val transitions: List<Transition>, override val sourceRef: RuleSourceRef) : PriceRule
    data class ByOption(val options: Map<String, PriceRule>, override val sourceRef: RuleSourceRef) : PriceRule
    data class Conditional(val condition: RequirementNode, val whenTrue: PriceRule, val whenFalse: PriceRule, override val sourceRef: RuleSourceRef) : PriceRule
    data class Unknown(val reason: String, override val sourceRef: RuleSourceRef) : PriceRule
}

data class AcquisitionDefinition(
    val ref: DefinitionRef,
    val kind: AcquisitionKind,
    val maxRank: Int?,
    val repeatPolicy: RepeatPolicy,
    val requirements: RequirementNode,
    val priceRule: PriceRule,
    val sourceStatus: SourceStatus,
    val name: String,
    val sourceRef: RuleSourceRef,
    val parameterDefinitions: List<ParameterDefinition> = emptyList(),
    val abilityOptionIds: Set<String>? = emptySet(),
    val requirementsText: String = "",
)

data class AcquisitionCatalog(
    val definitions: Map<DefinitionRef, AcquisitionDefinition>,
    val catalogRevision: String,
    val resolutionRevision: String,
)

data class CatalogDiagnostic(
    val kind: String,
    val message: String,
    val definition: DefinitionRef? = null,
    val nodeId: String? = null,
    val sourceRef: RuleSourceRef? = null,
)

data class CatalogLoadResult(val catalog: AcquisitionCatalog, val diagnostics: List<CatalogDiagnostic>)

/** Only declared entries are read. Composition order and inactive development claims mirror FCP. */
fun loadAcquisitionCatalog(packs: List<FcpContentPack>, includeChi: Boolean): CatalogLoadResult {
    val diagnostics = mutableListOf<CatalogDiagnostic>()
    val definitions = linkedMapOf<DefinitionRef, AcquisitionDefinition>()
    val catalogRevisions = mutableListOf<String>()
    val resolutionRevisions = mutableListOf<String>()
    val composition = try {
        FcpComposition.resolve(packs.map { it.manifest }, setOf("dubl-3.69"),
            packs.map { it.manifest.id }.filter { it != "dubl-chi-3.69" || includeChi }.toSet())
    } catch (e: IllegalArgumentException) {
        return CatalogLoadResult(AcquisitionCatalog(emptyMap(), "", ""), listOf(CatalogDiagnostic("composition-conflict", e.message ?: "Invalid composition")))
    } catch (e: IllegalStateException) {
        return CatalogLoadResult(AcquisitionCatalog(emptyMap(), "", ""), listOf(CatalogDiagnostic("composition-conflict", e.message ?: "Invalid composition")))
    }
    val inactiveClaims = composition.inactiveClaims("dubl.development")
    val claimOwners = composition.active.flatMap { m -> m.claims("dubl.development").map { it.id to m.id } }.toMap()
    for (manifest in composition.active) {
        val pack = packs.single { it.manifest.id == manifest.id }
        if (manifest.ruleset.id != "dubl" || manifest.ruleset.version != "3.69") {
            diagnostics += CatalogDiagnostic("unsupported-ruleset", manifest.id)
            continue
        }
        val entries = manifest.entries("dubl.acquisition")
        if (entries.isEmpty()) diagnostics += CatalogDiagnostic("missing-acquisition-payload", manifest.id)
        for (entry in entries) {
            try {
                val root = parseRoot(pack.read(entry))
                require(root.requiredInt("schemaVersion") == 1) { "Unsupported acquisition schema" }
                catalogRevisions += manifest.id + ":" + entry.path + ":" + root.requiredString("catalogRevision")
                resolutionRevisions += manifest.id + ":" + root.requiredString("resolutionRevision")
                val parsed = root.requiredArray("definitions").map { parseDefinition(it.requiredObject()) }
                require(parsed.all { it.ref.packId == manifest.id }) { "Definition declares another pack owner" }
                for (raw in parsed) {
                    if (raw.kind == AcquisitionKind.DEVELOPMENT && raw.ref.entryId in inactiveClaims) continue
                    // Existing claims gate content visibility; an active claim does not rewrite the
                    // original pack identity of the claimed core entry.
                    val owner = claimOwners[raw.ref.entryId]
                    if (owner != null && owner != raw.ref.packId && raw.ref.packId != "dubl-3.69") continue
                    val previous = definitions[raw.ref]
                    if (previous != null) {
                        diagnostics += CatalogDiagnostic("duplicate-definition", "Duplicate stable identity", raw.ref)
                        definitions[raw.ref] = block(previous, "duplicate-definition")
                    } else definitions[raw.ref] = raw
                }
                root.requiredArray("diagnostics").forEach { value ->
                    val d = value.requiredObject()
                    val ref = DefinitionRef(d.requiredString("packId"), d.requiredString("definitionId"))
                    if (ref in definitions) diagnostics += CatalogDiagnostic(d.requiredString("kind"), d.requiredString("message"), ref,
                        d.optionalString("nodeId"), d.objectValue("sourceRef")?.let(::parseSource))
                }
            } catch (e: IllegalArgumentException) {
                diagnostics += CatalogDiagnostic("invalid-acquisition-payload", "${manifest.id}/${entry.path}: ${e.message}")
            } catch (e: IllegalStateException) {
                diagnostics += CatalogDiagnostic("invalid-acquisition-payload", "${manifest.id}/${entry.path}: ${e.message}")
            }
        }
    }
    validateReferences(definitions, diagnostics)
    return CatalogLoadResult(AcquisitionCatalog(definitions.toMap(), catalogRevisions.joinToString("|"), resolutionRevisions.joinToString("|")), diagnostics.toList())
}

private fun block(d: AcquisitionDefinition, reason: String): AcquisitionDefinition = d.copy(
    requirements = RequirementNode.AllOf(d.ref.entryId + "/blocked/" + reason, d.sourceRef,
        listOf(d.requirements, RequirementNode.Unresolved(d.ref.entryId + "/block/" + reason, d.sourceRef, reason))),
    sourceStatus = if (d.sourceStatus == SourceStatus.Unavailable) SourceStatus.Unavailable else SourceStatus.NeedsResolution,
)

private fun parseDefinition(o: JsonValue.Obj): AcquisitionDefinition {
    val ref = o.requiredObject("ref")
    val source = parseSource(o.requiredObject("sourceRef"))
    val status = SourceStatus.valueOf(o.requiredString("sourceStatus"))
    val repeat = o.requiredObject("repeatPolicy")
    val parameters = o.requiredArray("parameterDefinitions").map { p ->
        val item = p.requiredObject(); val domain = item.requiredObject("domain")
        ParameterDefinition(item.requiredString("key"), when (domain.requiredString("type")) {
            "StableIds" -> ParameterDomain.StableIds(domain.requiredArray("valueIds").map { it.requiredText() }.toSet())
            "UserDefinedIds" -> ParameterDomain.UserDefinedIds
            "Unresolved" -> ParameterDomain.Unresolved(domain.requiredString("reason"))
            else -> error("Unknown parameter domain")
        })
    }
    val definition = AcquisitionDefinition(
        DefinitionRef(ref.requiredString("packId"), ref.requiredString("entryId")),
        AcquisitionKind.valueOf(o.requiredString("kind")),
        if (o.isNull("maxRank")) null else o.requiredInt("maxRank"),
        when (repeat.requiredString("type")) {
            "Single" -> RepeatPolicy.Single
            "DistinctParameter" -> RepeatPolicy.DistinctParameter(repeat.requiredArray("parameterKeys").map { it.requiredText() }.toSet(), repeat.requiredBool("requireUnique"))
            "Unresolved" -> RepeatPolicy.Unresolved(repeat.requiredString("reason"))
            else -> error("Unknown repeat policy")
        },
        parseRequirement(o.requiredObject("requirements")), parsePrice(o.requiredObject("priceRule")), status,
        o.requiredString("name"), source, parameters,
        if (o.isNull("abilityOptionIds")) null else o.requiredArray("abilityOptionIds").map { it.requiredText() }.toSet(),
        o.requiredString("requirementsText", allowEmpty = true),
    )
    require(definition.maxRank == null || definition.maxRank >= 0) { "Invalid rank cap" }
    // Missing source authority must remain a root conjunction blocker even if a
    // malformed/add-on payload supplies an apparently satisfied subtree or price.
    return if (status == SourceStatus.Unavailable) block(definition.copy(priceRule = PriceRule.Unknown("source-unverified", source)), "source-unverified") else definition
}

private fun parseSource(o: JsonValue.Obj) = RuleSourceRef(
    o.requiredString("documentId"), o.requiredString("documentVersion"), o.requiredString("originalText", allowEmpty = true),
    o.optionalString("sheet"), if (o.values["paragraph"] == null || o.isNull("paragraph")) null else o.requiredInt("paragraph"),
    o.optionalString("resolutionId"), o.optionalString("resolutionRevision"),
)

private fun parseRequirement(o: JsonValue.Obj): RequirementNode {
    val id = o.requiredString("nodeId"); val source = parseSource(o.requiredObject("sourceRef"))
    fun op() = ComparisonOperator.valueOf(o.requiredString("operator"))
    fun pack() = o.requiredString("packId")
    fun entry() = o.requiredString("entryId")
    fun number() = o.requiredInt("operand")
    fun children() = o.requiredArray("children").map { parseRequirement(it.requiredObject()) }
    return when (o.requiredString("type")) {
        "AllOf" -> RequirementNode.AllOf(id, source, children(), o.optionalString("noRequirementsStatement")?.let { NoRequirementsEvidence(source, it) })
        "AnyOf" -> RequirementNode.AnyOf(id, source, children())
        "Attribute" -> RequirementNode.Attribute(id, source, when (o.requiredString("attributeId")) {
            "STR" -> AttributeId.STRENGTH; "DEX" -> AttributeId.DEXTERITY; "CON" -> AttributeId.CONSTITUTION
            "SPD" -> AttributeId.SPEED; "PER" -> AttributeId.PERCEPTION; "INT" -> AttributeId.INTELLIGENCE
            "WIL" -> AttributeId.WILL; "CHA" -> AttributeId.CHARISMA
            else -> error("Unknown attribute ID")
        }, op(), number())
        "OwnedRank" -> RequirementNode.OwnedRank(id, source, pack(), entry(), op(), number(), o.optionalString("instanceId"))
        "SkillFamily" -> RequirementNode.SkillFamily(id, source, pack(), o.requiredString("familyId"), op(), number())
        "Specialization" -> RequirementNode.Specialization(id, source, pack(), entry(), op(), number(), o.objectValue("qualifier")?.let { ParameterMatch(it.requiredString("key"), it.requiredString("valueId")) }, o.optionalString("instanceId"))
        "BranchAccess" -> RequirementNode.BranchAccess(id, source, pack(), o.requiredString("branchId"), o.optionalString("accessOptionId"), op(), o.requiredBool("operand"))
        "LearnedSpell" -> RequirementNode.LearnedSpell(id, source, pack(), entry(), op(), o.requiredBool("operand"))
        "DistinctCount" -> RequirementNode.DistinctCount(id, source, parseRequirement(o.requiredObject("predicate")), DistinctScope.valueOf(o.requiredString("uniqueness")), op(), number())
        "CreationPhase" -> RequirementNode.CreationPhase(id, source, RequirementNode.CreationPhase.Phase.valueOf(o.requiredString("operand")), op())
        "Exclusive" -> RequirementNode.Exclusive(id, source, parseRequirement(o.requiredObject("conflicting")))
        "ExternalEvidence" -> RequirementNode.ExternalEvidence(id, source, o.requiredString("evidenceKey"), o.requiredString("scopeId"), op(), o.requiredBool("operand"))
        "Unresolved" -> RequirementNode.Unresolved(id, source, o.requiredString("reason"))
        else -> RequirementNode.Unresolved(id, source, "Unsupported requirement node: ${o.string("type")}")
    }
}

private fun parsePrice(o: JsonValue.Obj): PriceRule {
    val source = parseSource(o.requiredObject("sourceRef"))
    return when (o.requiredString("type")) {
        "Fixed" -> PriceRule.Fixed(o.money("xp"), o.money("ap"), source)
        "PerRank" -> PriceRule.PerRank(o.money("xp"), o.money("ap"), source)
        "TransitionTable" -> {
            val transitions = o.requiredArray("transitions").map { value ->
                val t = value.requiredObject()
                PriceRule.Transition(t.requiredInt("fromRank"), t.requiredInt("toRank"), t.money("xp"), t.money("ap"), parseSource(t.requiredObject("sourceRef")))
            }
            require(transitions.all { it.toRank > it.fromRank }) { "Invalid price transition" }
            require(transitions.map { it.fromRank to it.toRank }.distinct().size == transitions.size) { "Duplicate price transition" }
            PriceRule.TransitionTable(transitions, source)
        }
        "ByOption" -> PriceRule.ByOption(o.requiredObject("options").values.mapValues { parsePrice(it.value.requiredObject()) }, source)
        "Conditional" -> PriceRule.Conditional(parseRequirement(o.requiredObject("condition")), parsePrice(o.requiredObject("whenTrue")), parsePrice(o.requiredObject("whenFalse")), source)
        "Unknown" -> PriceRule.Unknown(o.requiredString("reason"), source)
        else -> PriceRule.Unknown("Unsupported price rule: ${o.string("type")}", source)
    }
}

private fun nodes(node: RequirementNode): List<RequirementNode> = listOf(node) + when (node) {
    is RequirementNode.AllOf -> node.children.flatMap(::nodes)
    is RequirementNode.AnyOf -> node.children.flatMap(::nodes)
    is RequirementNode.DistinctCount -> nodes(node.predicate)
    is RequirementNode.Exclusive -> nodes(node.conflicting)
    else -> emptyList()
}

private fun priceRequirementNodes(price: PriceRule): List<RequirementNode> = when (price) {
    is PriceRule.Conditional -> nodes(price.condition) + priceRequirementNodes(price.whenTrue) + priceRequirementNodes(price.whenFalse)
    is PriceRule.ByOption -> price.options.values.flatMap(::priceRequirementNodes)
    else -> emptyList()
}

private fun validateReferences(definitions: MutableMap<DefinitionRef, AcquisitionDefinition>, diagnostics: MutableList<CatalogDiagnostic>) {
    val edges = linkedMapOf<DefinitionRef, MutableSet<DefinitionRef>>()
    fun problem(d: AcquisitionDefinition, n: RequirementNode, kind: String, message: String) {
        diagnostics += CatalogDiagnostic(kind, message, d.ref, n.nodeId, n.sourceRef)
        definitions[d.ref] = block(definitions.getValue(d.ref), "$kind:${n.nodeId}")
    }
    for (d in definitions.values.toList()) {
        edges[d.ref] = linkedSetOf()
        for (n in nodes(d.requirements) + priceRequirementNodes(d.priceRule)) {
            val ref = when (n) {
                is RequirementNode.OwnedRank -> DefinitionRef(n.packId, n.entryId)
                is RequirementNode.SkillFamily -> DefinitionRef(n.packId, n.familyId)
                is RequirementNode.Specialization -> DefinitionRef(n.packId, n.entryId)
                is RequirementNode.BranchAccess -> DefinitionRef(n.packId, n.branchId)
                is RequirementNode.LearnedSpell -> DefinitionRef(n.packId, n.entryId)
                else -> continue
            }
            val target = definitions[ref]
            if (target == null) { problem(d, n, "missing-reference", ref.toString()); continue }
            edges.getValue(d.ref) += ref
            val rank = when (n) {
                is RequirementNode.OwnedRank -> n.operand
                is RequirementNode.SkillFamily -> n.operand
                is RequirementNode.Specialization -> n.operand
                else -> null
            }
            if (rank != null && (target.maxRank == null || rank < 0 || rank > target.maxRank))
                problem(d, n, if (target.maxRank == null) "unresolved-reference-rank" else "rank-out-of-range", "$rank / ${target.maxRank}")
            if (n is RequirementNode.LearnedSpell && target.kind != AcquisitionKind.SPELL)
                problem(d, n, "reference-kind-mismatch", "LearnedSpell must reference a spell")
            if (n is RequirementNode.SkillFamily && target.kind != AcquisitionKind.SKILL)
                problem(d, n, "reference-kind-mismatch", "SkillFamily must reference a skill")
            if (n is RequirementNode.BranchAccess && n.accessOptionId != null && target.abilityOptionIds?.contains(n.accessOptionId) != true)
                problem(d, n, "option-out-of-range", n.accessOptionId)
        }
        fun validatePrice(price: PriceRule) {
            when (price) {
                is PriceRule.TransitionTable -> if (price.transitions.any { d.maxRank == null || it.toRank > d.maxRank })
                    problem(d, d.requirements, "price-rank-out-of-range", "Transition requires confirmed rank cap")
                is PriceRule.ByOption -> {
                    if (price.options.keys.any { d.abilityOptionIds?.contains(it) != true })
                        problem(d, d.requirements, "price-option-out-of-range", "Unknown price option")
                    price.options.values.forEach(::validatePrice)
                }
                is PriceRule.Conditional -> { validatePrice(price.whenTrue); validatePrice(price.whenFalse) }
                else -> Unit
            }
        }
        validatePrice(d.priceRule)
    }
    // Tarjan SCC: preserve OR grouping, report cycles without selecting an alternative.
    var index = 0
    val indices = mutableMapOf<DefinitionRef, Int>(); val low = mutableMapOf<DefinitionRef, Int>()
    val stack = mutableListOf<DefinitionRef>(); val active = mutableSetOf<DefinitionRef>()
    fun visit(v: DefinitionRef) {
        indices[v] = index; low[v] = index; index++; stack += v; active += v
        for (w in edges[v].orEmpty()) {
            if (w !in indices) { visit(w); low[v] = minOf(low.getValue(v), low.getValue(w)) }
            else if (w in active) low[v] = minOf(low.getValue(v), indices.getValue(w))
        }
        if (low[v] == indices[v]) {
            val component = mutableListOf<DefinitionRef>()
            do { val w = stack.removeAt(stack.lastIndex); active -= w; component += w } while (w != v)
            if (component.size > 1 || v in edges[v].orEmpty()) component.forEach { ref ->
                val d = definitions.getValue(ref)
                problem(d, d.requirements, "dependency-cycle", component.joinToString())
            }
        }
    }
    edges.keys.forEach { if (it !in indices) visit(it) }
}

// Required fields never use MiniJson's display-oriented default 0/false/empty helpers.
private fun JsonValue.requiredObject() = this as? JsonValue.Obj ?: error("Expected object")
private fun JsonValue.requiredText() = (this as? JsonValue.Str)?.value ?: error("Expected string")
private fun JsonValue.Obj.requiredObject(key: String) = values[key]?.requiredObject() ?: error("Missing $key")
private fun JsonValue.Obj.requiredArray(key: String) = (values[key] as? JsonValue.Arr)?.values ?: error("Missing array $key")
private fun JsonValue.Obj.requiredString(key: String, allowEmpty: Boolean = false): String =
    values[key]?.requiredText()?.also { require(allowEmpty || it.isNotBlank()) { "Blank $key" } } ?: error("Missing $key")
private fun JsonValue.Obj.optionalString(key: String): String? = if (values[key] == null || isNull(key)) null else requiredString(key)
private fun JsonValue.Obj.requiredInt(key: String): Int = (values[key] as? JsonValue.Num)?.raw?.toIntOrNull() ?: error("Invalid integer $key")
private fun JsonValue.Obj.requiredBool(key: String) = (values[key] as? JsonValue.Bool)?.value ?: error("Invalid boolean $key")
private fun JsonValue.Obj.money(key: String): Long {
    val literal = requiredString(key)
    require(Regex("-?(0|[1-9][0-9]*)").matches(literal)) { "Noncanonical money $key" }
    return literal.toLongOrNull() ?: error("Money overflow $key")
}
