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
    /** Explicit source-confirmed or approved-ruling per-step price; never inferred from a multi-rank literal alone. */
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
    /** Stable semantic membership; evaluators must not infer this from display names. */
    val familyIds: Set<String> = emptySet(),
    val categoryIds: Set<String> = emptySet(),
    val classificationSources: Map<String, RuleSourceRef> = emptyMap(),
    val includedGrants: List<IncludedGrant> = emptyList(),
    val instanceCountLimit: InstanceCountLimit? = null,
    val newParameterValueRequirement: NewParameterValueRequirement? = null,
)

data class AcquisitionCatalog(
    val definitions: Map<DefinitionRef, AcquisitionDefinition>,
    val catalogRevision: String,
    val resolutionRevision: String,
    val creationFundingPolicy: CreationFundingPolicy? = null,
    val sourceRuleRevision: String = "",
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
    val sourceRuleRevisions = mutableListOf<String>()
    val fundingPolicies = mutableListOf<CreationFundingPolicy>()
    var invalidFundingPayload = false
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
                root.optionalString("sourceRuleRevision")?.let { sourceRuleRevisions += manifest.id + ":" + it }
                val funding = root.objectValue("creationFundingPolicy")?.let { value ->
                    require(manifest.id == "dubl-3.69") { "Initial funding must be declared only by the core pack" }
                    require(value.requiredString("rounding") == "FLOOR" &&
                        value.requiredString("trigger") == "GENUINELY_NEW_CHARACTER_INITIALIZATION" &&
                        value.requiredString("repeat") == "NEVER" &&
                        !value.requiredBool("appliesToExistingOrMigrated") && !value.requiredBool("ongoingXPGrantAP")) { "Unsupported funding policy" }
                    CreationFundingPolicy(value.requiredString("ruleId"), value.money("xpPerAP"), parseSource(value.requiredObject("sourceRef"))).also {
                        require(it.supported && it.sourceRef.resolutionRevision == root.requiredString("resolutionRevision")) { "Unconfirmed funding authority" }
                    }
                }
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
                funding?.let { fundingPolicies += it }
                root.requiredArray("diagnostics").forEach { value ->
                    val d = value.requiredObject()
                    val ref = DefinitionRef(d.requiredString("packId"), d.requiredString("definitionId"))
                    if (ref in definitions) diagnostics += CatalogDiagnostic(d.requiredString("kind"), d.requiredString("message"), ref,
                        d.optionalString("nodeId"), d.objectValue("sourceRef")?.let(::parseSource))
                }
            } catch (e: IllegalArgumentException) {
                invalidFundingPayload = true
                diagnostics += CatalogDiagnostic("invalid-acquisition-payload", "${manifest.id}/${entry.path}: ${e.message}")
            } catch (e: IllegalStateException) {
                invalidFundingPayload = true
                diagnostics += CatalogDiagnostic("invalid-acquisition-payload", "${manifest.id}/${entry.path}: ${e.message}")
            }
        }
    }
    validateReferences(definitions, diagnostics)
    if (fundingPolicies.size > 1) diagnostics += CatalogDiagnostic("conflicting-funding-policy", "Multiple initial funding declarations")
    val funding = if (invalidFundingPayload) null else fundingPolicies.singleOrNull()
    return CatalogLoadResult(AcquisitionCatalog(definitions.toMap(), catalogRevisions.joinToString("|"), resolutionRevisions.joinToString("|"),
        funding, sourceRuleRevisions.joinToString("|")), diagnostics.toList())
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
        }, item.array("choices").map { value ->
            val choice = value.requiredObject()
            ParameterChoice(choice.requiredString("id"), choice.requiredString("label"), parseSource(choice.requiredObject("sourceRef")),
                choice.objectValue("parameterValues")?.values?.mapValues { it.value.requiredText() }.orEmpty(),
                choice.array("weaponKinds").map { raw -> val kind = raw.requiredObject()
                    WeaponProficiencyKind(kind.requiredString("id"), kind.requiredString("label"), parseSource(kind.requiredObject("sourceRef"))) })
        }, item.array("excludedLabels").map { value ->
            val excluded = value.requiredObject()
            ParameterExcludedLabel(excluded.requiredString("label"), parseSource(excluded.requiredObject("sourceRef")))
        }, item.optionalString("label"), item.values["requiredAtRank"]?.let { item.requiredInt("requiredAtRank") } ?: 1,
            item.objectValue("selectionSource")?.let { selection ->
                when(selection.requiredString("type")) {
                    "OwnedInstances" -> ParameterSelectionSource.OwnedInstances(selection.requiredString("familyId"), selection.requiredInt("minimumRank"))
                    "OwnedWeaponKinds" -> ParameterSelectionSource.OwnedWeaponKinds
                    "OrderedOwnedInstances" -> ParameterSelectionSource.OrderedOwnedInstances(selection.requiredArray("definitions").map { raw ->
                        val ref=raw.requiredObject(); DefinitionRef(ref.requiredString("packId"),ref.requiredString("entryId"))
                    }.toSet(),selection.requiredInt("baseLength"),selection.requiredBool("plusTargetRank"),
                        selection.values["minimumRank"]?.let { selection.requiredInt("minimumRank") } ?: 1)
                    else -> error("Unsupported parameter selection source")
                }
            }, item.optionalString("uniqueWithinGroup"), item.optionalString("displayNameKey"), item.optionalString("identityDomain"))
    }
    val definition = AcquisitionDefinition(
        DefinitionRef(ref.requiredString("packId"), ref.requiredString("entryId")),
        AcquisitionKind.valueOf(o.requiredString("kind")),
        if (o.isNull("maxRank")) null else o.requiredInt("maxRank"),
        when (repeat.requiredString("type")) {
            "Single" -> RepeatPolicy.Single
            "Independent" -> RepeatPolicy.Independent
            "DistinctParameter" -> RepeatPolicy.DistinctParameter(repeat.requiredArray("parameterKeys").map { it.requiredText() }.toSet(), repeat.requiredBool("requireUnique"))
            "Unresolved" -> RepeatPolicy.Unresolved(repeat.requiredString("reason"))
            else -> error("Unknown repeat policy")
        },
        parseRequirement(o.requiredObject("requirements")), parsePrice(o.requiredObject("priceRule")), status,
        o.requiredString("name"), source, parameters,
        if (o.isNull("abilityOptionIds")) null else o.requiredArray("abilityOptionIds").map { it.requiredText() }.toSet(),
        o.requiredString("requirementsText", allowEmpty = true),
        o.array("familyIds").map { it.requiredText() }.toSet(),
        o.array("categoryIds").map { it.requiredText() }.toSet(),
        o.objectValue("classificationSources")?.values?.mapValues { parseSource(it.value.requiredObject()) }.orEmpty(),
        o.array("includedGrants").map { raw ->
            val grant = raw.requiredObject()
            IncludedGrant(parseSource(grant.requiredObject("sourceRef")), grant.requiredString("parameterKey"),
                grant.requiredInt("activateAtRank"), grant.requiredObject("targetByValue").values.mapValues { (_, value) ->
                    val target = value.requiredObject()
                    DefinitionRef(target.requiredString("packId"), target.requiredString("entryId"))
                }, grant.requiredInt("targetRank"), grant.requiredBool("requireNewOwnership"), grant.requiredBool("retainsSourceEligibility"),
                grant.objectValue("targetDefinition")?.let { DefinitionRef(it.requiredString("packId"), it.requiredString("entryId")) },
                grant.values["creationOnly"]?.let { grant.requiredBool("creationOnly") } ?: false,
                grant.objectValue("targetParameterChoices")?.values?.mapValues { (_, values) ->
                    (values as? JsonValue.Arr ?: error("Expected allowed parameter values")).values.map { it.requiredText() }.toSet()
                }.orEmpty(), grant.objectValue("customTargetEvidence")?.let { custom -> IncludedGrantCustomEvidence(
                    custom.requiredString("parameterKey"), custom.requiredString("evidenceKey"), parseSource(custom.requiredObject("sourceRef")),
                    custom.array("allowedNamedGroupIds").map { it.requiredText() }.toSet()) })
        },
        o.objectValue("instanceCountLimit")?.let { limit ->
            InstanceCountLimit(parseSource(limit.requiredObject("sourceRef")), parseAttributeId(limit.requiredString("attributeId")),
                limit.requiredInt("numerator"), limit.requiredInt("denominator"))
        },
        o.objectValue("newParameterValueRequirement")?.let { rule -> NewParameterValueRequirement(
            rule.requiredArray("parameterKeys").map { it.requiredText() }.toSet(), rule.requiredInt("minimumNewValues"),
            parseSource(rule.requiredObject("sourceRef"))) },
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

private fun parseAttributeId(id: String): AttributeId = when (id) {
    "STR" -> AttributeId.STRENGTH; "DEX" -> AttributeId.DEXTERITY; "CON" -> AttributeId.CONSTITUTION
    "SPD" -> AttributeId.SPEED; "PER" -> AttributeId.PERCEPTION; "INT" -> AttributeId.INTELLIGENCE
    "WIL" -> AttributeId.WILL; "CHA" -> AttributeId.CHARISMA
    else -> error("Unknown attribute ID")
}

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
        "Attribute" -> RequirementNode.Attribute(id, source, parseAttributeId(o.requiredString("attributeId")), op(), number())
        "OwnedRank" -> RequirementNode.OwnedRank(id, source, pack(), entry(), op(), number(), o.optionalString("instanceId"))
        "NumericProperty" -> RequirementNode.NumericProperty(id, source, o.requiredString("propertyId"), op(), number())
        "SelectedInstance" -> RequirementNode.SelectedInstance(id, source, o.requiredString("parameterKey"), parseRequirement(o.requiredObject("predicate")))
        "SkillFamily" -> RequirementNode.SkillFamily(id, source, pack(), o.requiredString("familyId"), op(), number())
        "Specialization" -> RequirementNode.Specialization(id, source, pack(), entry(), op(), number(), o.objectValue("qualifier")?.let { ParameterMatch(it.requiredString("key"), it.requiredString("valueId")) }, o.optionalString("instanceId"))
        "BranchAccess" -> RequirementNode.BranchAccess(id, source, pack(), o.requiredString("branchId"), o.optionalString("accessOptionId"), op(), o.requiredBool("operand"),
            o.objectValue("parameterBindings")?.values?.mapValues { it.value.requiredText() }.orEmpty())
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

private fun validateReferences(definitions: MutableMap<DefinitionRef, AcquisitionDefinition>, diagnostics: MutableList<CatalogDiagnostic>) {
    val snapshot = definitions.toMap()
    val edges = snapshot.keys.associateWith { linkedSetOf<DefinitionRef>() }
    for (d in snapshot.values) {
        fun problem(n: RequirementNode, kind: String, message: String): RequirementNode.Unresolved {
            diagnostics += CatalogDiagnostic(kind, message, d.ref, n.nodeId, n.sourceRef)
            return RequirementNode.Unresolved(n.nodeId, n.sourceRef, "$kind:$message")
        }
        fun validateNode(n: RequirementNode, positiveDependency: Boolean = true): RequirementNode {
            // Validation is structural, not semantic evaluation: keep each failure in
            // its exact AND/OR/count/exclusion scope for the future evaluator.
            when (n) {
                is RequirementNode.AllOf -> return RequirementNode.AllOf(n.nodeId, n.sourceRef, n.children.map { validateNode(it, positiveDependency) }, n.noRequirementsEvidence)
                is RequirementNode.AnyOf -> return RequirementNode.AnyOf(n.nodeId, n.sourceRef, n.children.map { validateNode(it, positiveDependency) })
                is RequirementNode.DistinctCount -> return n.copy(predicate = validateNode(n.predicate, positiveDependency))
                is RequirementNode.SelectedInstance -> return if (d.parameterDefinitions.none { it.key == n.parameterKey })
                    problem(n, "missing-subject-parameter", n.parameterKey) else n.copy(predicate = validateNode(n.predicate, positiveDependency))
                is RequirementNode.Exclusive -> return n.copy(conflicting = validateNode(n.conflicting, positiveDependency = false))
                is RequirementNode.NumericProperty -> return when {
                    n.propertyId !in setOf("MAGIC_POWER","FORTITUDE") -> problem(n, "unsupported-numeric-property", n.propertyId)
                    n.operand < 0 -> problem(n, "numeric-property-out-of-range", n.operand.toString())
                    else -> n
                }
                else -> Unit
            }
            val ref = when (n) {
                is RequirementNode.OwnedRank -> DefinitionRef(n.packId, n.entryId)
                is RequirementNode.SkillFamily -> DefinitionRef(n.packId, n.familyId)
                is RequirementNode.Specialization -> DefinitionRef(n.packId, n.entryId)
                is RequirementNode.BranchAccess -> DefinitionRef(n.packId, n.branchId)
                is RequirementNode.LearnedSpell -> DefinitionRef(n.packId, n.entryId)
                else -> return n
            }
            val exact = snapshot[ref]
            val family = n is RequirementNode.SkillFamily && exact == null
            val targets = if (family) snapshot.values.filter { it.ref.packId == ref.packId && ref.entryId in it.familyIds }
                else listOfNotNull(exact)
            if (targets.isEmpty()) return problem(n, "missing-reference", ref.toString())
            // A rival's possession is a negative restriction, not a positive dependency.
            if (positiveDependency) edges.getValue(d.ref).addAll(targets.map { it.ref })
            if (n is RequirementNode.LearnedSpell && targets.any { it.kind != AcquisitionKind.SPELL })
                return problem(n, "reference-kind-mismatch", "LearnedSpell must reference a spell")
            if (n is RequirementNode.SkillFamily && targets.any { it.kind != AcquisitionKind.SKILL })
                return problem(n, "reference-kind-mismatch", "SkillFamily must reference skills")
            val rank = when (n) {
                is RequirementNode.OwnedRank -> n.operand
                is RequirementNode.SkillFamily -> n.operand
                is RequirementNode.Specialization -> n.operand
                else -> null
            }
            // A family is an existential choice: one verified member can witness
            // the rank even when another member's maximum is unknown.
            if (rank != null && targets.none { target -> (rank >= 0 && hasReferencedSchoolOwnership(target)) ||
                    confirmedPurchaseRankCeiling(target)?.let { rank >= 0 && rank <= it } == true })
                return problem(n, if (targets.any { it.maxRank == null }) "unresolved-reference-rank" else "rank-out-of-range", "$rank / ${targets.map { it.maxRank }}")
            if (n is RequirementNode.BranchAccess && n.accessOptionId != null && targets.none { it.abilityOptionIds?.contains(n.accessOptionId) == true })
                return problem(n, "option-out-of-range", n.accessOptionId)
            if (n is RequirementNode.BranchAccess && n.parameterBindings.any { (target, subject) ->
                    d.parameterDefinitions.none { it.key == subject } || targets.any { definition -> definition.parameterDefinitions.none { it.key == target } }
                }) return problem(n, "invalid-parameter-binding", "Subject and access parameter keys must both be declared")
            return n
        }
        fun validatePrice(price: PriceRule, path: String): PriceRule {
            fun priceProblem(kind: String, message: String, source: RuleSourceRef): PriceRule.Unknown {
                diagnostics += CatalogDiagnostic(kind, message, d.ref, path, source)
                return PriceRule.Unknown("$kind:$message", source)
            }
            return when (price) {
                is PriceRule.TransitionTable -> price.copy(transitions = price.transitions.filter { row ->
                    if (confirmedPurchaseRankCeiling(d)?.let { row.toRank <= it } != true) {
                        priceProblem("price-rank-out-of-range", "${row.fromRank}->${row.toRank} / ${d.maxRank}", row.sourceRef)
                        false // This exact absent transition is Unknown, not a zero or a fallback row.
                    } else true
                })
                is PriceRule.ByOption -> price.copy(options = price.options.mapValues { (option, rule) ->
                    if (d.abilityOptionIds?.contains(option) != true)
                        priceProblem("price-option-out-of-range", option, rule.sourceRef)
                    else validatePrice(rule, "$path/option/$option")
                })
                is PriceRule.Conditional -> price.copy(condition = validateNode(price.condition),
                    whenTrue = validatePrice(price.whenTrue, "$path/true"), whenFalse = validatePrice(price.whenFalse, "$path/false"))
                else -> price
            }
        }
        var checked = d.copy(requirements = validateNode(d.requirements), priceRule = validatePrice(d.priceRule, d.ref.entryId + "/price"))
        if (d.newParameterValueRequirement?.parameterKeys?.any { key -> d.parameterDefinitions.none { it.key == key } } == true) {
            diagnostics += CatalogDiagnostic("invalid-new-parameter-value-requirement", "Unknown parameter key", d.ref,
                sourceRef = d.newParameterValueRequirement.sourceRef)
            checked = block(checked, "invalid-new-parameter-value-requirement")
        }
        for(parameter in d.parameterDefinitions) {
            val ordered=parameter.selectionSource as? ParameterSelectionSource.OrderedOwnedInstances ?: continue
            if(parameter.displayNameKey==null || ordered.definitions.any { it !in snapshot }) {
                diagnostics+=CatalogDiagnostic("invalid-ordered-selection",parameter.key,d.ref,sourceRef=d.sourceRef)
                checked=block(checked,"invalid-ordered-selection:"+parameter.key)
            }
        }
        for (grant in d.includedGrants) {
            val parameter = d.parameterDefinitions.singleOrNull { it.key == grant.parameterKey }
            val fixed = grant.targetDefinition?.let(snapshot::get)
            val invalid = d.maxRank == null || grant.activateAtRank > d.maxRank ||
                if (grant.targetDefinition != null) {
                    fixed == null || fixed.ref == d.ref || fixed.maxRank?.let { it < grant.targetRank } != false ||
                        grant.targetParameterChoices.any { (key, allowed) -> fixed.parameterDefinitions.singleOrNull { it.key == key }
                            ?.let { targetParameter -> allowed.any { value -> targetParameter.choices.none { it.id == value } } } != false } ||
                        (grant.customTargetEvidence != null && grant.customTargetEvidence.parameterKey !in grant.targetParameterChoices)
                } else parameter == null || parameter.requiredAtRank > grant.activateAtRank || grant.targetByValue.any { (value, ref) ->
                    ref == d.ref || snapshot[ref]?.maxRank?.let { it < grant.targetRank } != false ||
                        ((parameter.domain as? ParameterDomain.StableIds)?.valueIds?.contains(value) == false)
                }
            if (invalid) {
                diagnostics += CatalogDiagnostic("invalid-included-grant", grant.parameterKey, d.ref, sourceRef = grant.sourceRef)
                checked = block(checked, "invalid-included-grant:" + grant.parameterKey)
            }
        }
        definitions[d.ref] = checked
    }
    // An SCC across OR/count/price branches is only a potential cycle. T05 must
    // detect recursion on the selected proof path; do not rewrite these nodes.
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
                val d = snapshot.getValue(ref)
                diagnostics += CatalogDiagnostic("dependency-cycle", "Potential dependency cycle: ${component.joinToString()}", ref, d.requirements.nodeId, d.sourceRef)
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
