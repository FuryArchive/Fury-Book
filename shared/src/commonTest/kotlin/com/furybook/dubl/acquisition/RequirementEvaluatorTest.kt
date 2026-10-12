package com.furybook.dubl.acquisition

import com.furybook.dubl.model.*
import kotlin.test.*

class RequirementEvaluatorTest {
    private val source = RuleSourceRef("DUBL core", "3.69", "Подтверждённое условие")
    private val pack = "dubl-3.69"
    private fun ref(id: String) = DefinitionRef(pack, id)
    private fun none(id: String) = RequirementNode.AllOf("$id/none", source, emptyList(), NoRequirementsEvidence(source, "No requirements"))
    private fun owned(id: String, rank: Int = 1, nodeId: String = "need/$id") =
        RequirementNode.OwnedRank(nodeId, source, pack, id, ComparisonOperator.GE, rank)
    private fun definition(id: String, requirements: RequirementNode = none(id), kind: AcquisitionKind = AcquisitionKind.DEVELOPMENT,
        families: Set<String> = emptySet(), status: SourceStatus = SourceStatus.Confirmed, cap: Int? = 10) =
        AcquisitionDefinition(ref(id), kind, cap, RepeatPolicy.Single, requirements, PriceRule.Fixed(1, 0, source), status,
            "Display $id", source, familyIds = families, classificationSources = families.associate { "family:$it" to source })
    private fun instance(id: String, rank: Int = 1, instanceId: String = "instance/$id", parameters: Map<String, String> = emptyMap(), option: String? = null) =
        AcquisitionInstance(instanceId, ref(id), rank, parameters, option, AcquisitionOrigin.New)
    private fun context(definitions: List<AcquisitionDefinition>, instances: List<AcquisitionInstance> = emptyList(),
        learned: Set<DefinitionRef> = emptySet(), creating: Boolean = true, evidence: List<RequirementWitness.External> = emptyList(), strength: Int = 1) =
        AcquisitionContext(DublCharacter("test", attributes = defaultAttributes() + (AttributeId.STRENGTH to AttributeValue(strength))),
            instances, learned, if (creating) RequirementNode.CreationPhase.Phase.Creating else RequirementNode.CreationPhase.Phase.Finalized,
            evidence, AcquisitionCatalog(definitions.associateBy { it.ref }, "catalog-v1", "resolution-v1"))
    private fun status(node: RequirementNode, context: AcquisitionContext) = evaluate(node, context).status

    @Test fun weapon_specialization_matches_selected_family() {
        val node = RequirementNode.Specialization("weapon", source, pack, "weapon-spec", ComparisonOperator.GE, 1, ParameterMatch("weaponTypeId", "melee"))
        val defs = listOf(definition("weapon-spec"))
        assertEquals(RequirementStatus.Satisfied, status(node, context(defs, listOf(instance("weapon-spec", parameters = mapOf("weaponTypeId" to "melee"))))))
        assertEquals(RequirementStatus.Unsatisfied, status(node, context(defs, listOf(instance("weapon-spec", parameters = mapOf("weaponTypeId" to "ranged"))))))
        assertEquals(RequirementStatus.ChoiceRequired, status(node, context(defs, listOf(instance("weapon-spec")))))
        assertEquals(RequirementStatus.Unsatisfied, status(node, context(defs)))
    }

    @Test fun spell_learned_true_only() {
        val node = RequirementNode.LearnedSpell("spell", source, pack, "spell")
        val defs = listOf(definition("spell", kind = AcquisitionKind.SPELL))
        assertEquals(RequirementStatus.Unsatisfied, status(node, context(defs)))
        // Owning a row is not a learned=true fact.
        assertEquals(RequirementStatus.Unsatisfied, status(node, context(defs, listOf(instance("spell")))))
        val result = evaluate(node, context(defs, learned = setOf(ref("spell"))))
        assertEquals(RequirementStatus.Satisfied, result.status)
        assertEquals(listOf(RequirementWitness.LearnedSpell(pack, "spell")), result.witnesses)
    }

    @Test fun distinct_cries_2_3() {
        val defs = listOf(definition("cry-a"), definition("cry-b"), definition("unrelated"))
        val predicate = RequirementNode.AnyOf("cries", source, listOf(owned("cry-a"), owned("cry-b")))
        fun count(n: Int, scope: DistinctScope = DistinctScope.Definition) = RequirementNode.DistinctCount("count", source, predicate, scope, ComparisonOperator.GE, n)
        val one = context(defs, listOf(instance("cry-a", 3), instance("unrelated", 3)))
        assertEquals(RequirementStatus.Unsatisfied, status(count(2), one))
        assertEquals(RequirementValue.Number(1), evaluate(count(2), one).actualValue)
        val two = context(defs, listOf(instance("cry-a", 3), instance("cry-b")))
        assertEquals(RequirementStatus.Satisfied, status(count(2), two))
        assertEquals(RequirementStatus.Unsatisfied, status(count(3), two))
        val copies = context(defs, listOf(instance("cry-a"), instance("cry-a", instanceId = "second")))
        assertEquals(RequirementStatus.Unsatisfied, status(count(2), copies))
        assertEquals(RequirementStatus.Satisfied, status(count(2, DistinctScope.Instance), copies))
    }

    @Test fun knowledge_rank_before_parenthesis() {
        val actualSource = source.copy(originalText = "Знание4(Любое)", paragraph = 10530)
        val node = RequirementNode.SkillFamily("knowledge/P10530", actualSource, pack, "knowledge", ComparisonOperator.GE, 4)
        val defs = listOf(definition("knowledge_academic", kind = AcquisitionKind.SKILL, families = setOf("knowledge")),
            definition("knowledge_template", kind = AcquisitionKind.SKILL, families = setOf("knowledge"), cap = null))
        assertEquals(RequirementStatus.Satisfied, status(node, context(defs, listOf(instance("knowledge_academic", 4)))))
        assertEquals(RequirementStatus.Unsatisfied, status(node, context(defs, listOf(instance("knowledge_academic", 3)))))
    }

    @Test fun creation_second_rank_unresolved() {
        val unknown = RequirementNode.Unresolved("beauty/second", source, "Second rank permission during creation unresolved")
        val old = context(listOf(definition("beauty", unknown)), listOf(instance("beauty")))
        assertEquals(RequirementStatus.SourceUnresolved, status(unknown, old))
        val creating = RequirementNode.CreationPhase("phase", source, RequirementNode.CreationPhase.Phase.Creating)
        assertEquals(RequirementStatus.Unsatisfied, status(creating, context(listOf(definition("beauty")), listOf(instance("beauty")), creating = false)))
    }

    @Test fun recursive_invalid_prerequisite_is_blocked() {
        val attribute = RequirementNode.Attribute("parent/str", source, AttributeId.STRENGTH, ComparisonOperator.GE, 4)
        val defs = listOf(definition("parent", attribute), definition("child", owned("parent")))
        assertEquals(RequirementStatus.Unsatisfied, status(owned("child"), context(defs, listOf(instance("parent"), instance("child")))))
        val unknownDefs = listOf(definition("parent", RequirementNode.Unresolved("lost", source, "missing source")), definition("child", owned("parent")))
        assertEquals(RequirementStatus.SourceUnresolved, status(owned("child"), context(unknownDefs, listOf(instance("parent"), instance("child")))))
    }

    @Test fun actual_incompatible_perfect_roots_conflict_even_if_other_root_invalid() {
        val restriction = RequirementNode.Exclusive("only-one", source, owned("perfect-b"))
        val defs = listOf(definition("perfect-a", restriction), definition("perfect-b", RequirementNode.Unresolved("b/unknown", source, "Legacy invalid")))
        val ctx = context(defs, listOf(instance("perfect-b")))
        assertEquals(RequirementStatus.Conflict, status(restriction, ctx))
        assertEquals(RequirementStatus.Satisfied, status(restriction, context(defs)))
        assertEquals(1, ctx.instances.size)
    }

    @Test fun unrelated_legacy_deviation_does_not_change_independent_condition() {
        val defs = listOf(definition("valid"), definition("legacy", RequirementNode.Unresolved("legacy/unknown", source, "old deviation")))
        assertEquals(RequirementStatus.Satisfied, status(owned("valid"), context(defs, listOf(instance("valid"), instance("legacy")))))
    }

    @Test fun potential_scc_does_not_poison_feasible_alternative() {
        val a = RequirementNode.AnyOf("a/or", source, listOf(owned("b"), RequirementNode.Attribute("a/str", source, AttributeId.STRENGTH, ComparisonOperator.GE, 1)))
        val defs = listOf(definition("a", a), definition("b", owned("a")))
        val ctx = context(defs, listOf(instance("a"), instance("b")))
        assertEquals(RequirementStatus.Satisfied, status(owned("a", nodeId = "root/a"), ctx))
        assertEquals(RequirementStatus.Satisfied, status(owned("b", nodeId = "root/b"), ctx))
        assertEquals(RequirementStatus.SourceUnresolved, status(owned("b", nodeId = "root/b"), context(defs, ctx.instances, strength = 0)))
    }

    @Test fun unavoidable_cycle_and_depth_are_bounded() {
        val defs = listOf(definition("a", owned("b")), definition("b", owned("a")))
        val result = evaluate(owned("a", nodeId = "root/a"), context(defs, listOf(instance("a"), instance("b"))))
        assertEquals(RequirementStatus.SourceUnresolved, result.status)
        assertTrue(result.toString().contains("cycle", ignoreCase = true))
        val chain = (0..80).map { n -> definition("chain$n", if (n == 80) none("last") else owned("chain${n + 1}")) }
        assertEquals(RequirementStatus.SourceUnresolved, status(owned("chain0"), context(chain, (0..80).map { instance("chain$it") })))
    }

    @Test fun unresolved_or_branch_and_advisory_completeness_remain_local() {
        val any = RequirementNode.AnyOf("or", source, listOf(RequirementNode.Unresolved("bad", source, "missing alternative"), none("good")))
        assertEquals(RequirementStatus.Satisfied, status(any, context(emptyList())))
        val partial = definition("partial", status = SourceStatus.NeedsResolution)
        assertEquals(RequirementStatus.Satisfied, status(owned("partial"), context(listOf(partial), listOf(instance("partial")))))
        assertEquals(RequirementStatus.SourceUnresolved, status(owned("partial"), context(listOf(partial.copy(sourceStatus = SourceStatus.Unavailable)), listOf(instance("partial")))))
    }

    @Test fun external_evidence_is_scoped_and_cannot_resolve_unknown_source() {
        val node = RequirementNode.ExternalEvidence("external", source, "trainer", "branch-a")
        val fact = RequirementWitness.External("trainer", "branch-a", "confirmation-id", source)
        assertEquals(RequirementStatus.ChoiceRequired, status(node, context(emptyList())))
        assertEquals(RequirementStatus.ChoiceRequired, status(node, context(emptyList(), evidence = listOf(fact.copy(scopeId = "branch-b")))))
        assertEquals(RequirementStatus.Satisfied, status(node, context(emptyList(), evidence = listOf(fact))))
        assertEquals(RequirementStatus.SourceUnresolved, status(RequirementNode.Unresolved("unknown", source, "Author question"), context(emptyList(), evidence = listOf(fact))))
    }

    @Test fun branch_requires_stable_option_and_recursive_proof() {
        val node = RequirementNode.BranchAccess("branch", source, pack, "access", "mastery")
        val defs = listOf(definition("access"))
        assertEquals(RequirementStatus.Satisfied, status(node, context(defs, listOf(instance("access", option = "mastery")))))
        assertEquals(RequirementStatus.Unsatisfied, status(node, context(defs, listOf(instance("access", option = "gift")))))
        assertEquals(RequirementStatus.ChoiceRequired, status(node, context(defs, listOf(instance("access")))))
        val broken = listOf(definition("access", RequirementNode.Unresolved("access/source", source, "Access unknown")))
        assertEquals(RequirementStatus.SourceUnresolved, status(node, context(broken, listOf(instance("access", option = "mastery")))))
    }

    @Test fun branch_witness_preserves_pack_identity_of_same_id_ancestor() {
        val other = DefinitionRef("another-pack", "access")
        val dependency = RequirementNode.OwnedRank("access/ancestor", source, other.packId, other.entryId, ComparisonOperator.GE, 1)
        val defs = listOf(definition("access", dependency), definition("access").copy(ref = other))
        val otherOwned = instance("access", instanceId = "other").copy(definition = other)
        val node = RequirementNode.BranchAccess("access/proof", source, pack, "access", "mastery")
        val proof = evaluate(node, context(defs, listOf(instance("access", option = "mastery"), otherOwned)))
        assertEquals(RequirementStatus.Satisfied, proof.status)
        assertEquals(1, proof.witnesses.filterIsInstance<RequirementWitness.Branch>().size)
        assertTrue(proof.witnesses.any { it is RequirementWitness.OwnedInstance && it.packId == other.packId && it.instanceId == "other" })
    }

    @Test fun comparisons_preserve_actual_and_required_and_use_shared_attribute_calculation() {
        val ctx = context(emptyList(), strength = 3)
        for ((op, expected) in listOf(ComparisonOperator.EQ to true, ComparisonOperator.GE to true, ComparisonOperator.LE to true, ComparisonOperator.GT to false, ComparisonOperator.LT to false)) {
            val result = evaluate(RequirementNode.Attribute("attribute", source, AttributeId.STRENGTH, op, 3), ctx)
            assertEquals(if (expected) RequirementStatus.Satisfied else RequirementStatus.Unsatisfied, result.status)
            assertEquals(RequirementValue.Number(3), result.actualValue)
            assertEquals(RequirementValue.Number(3), result.requiredValue)
        }
        val large = AcquisitionContext(ctx.character.copy(size = 7), emptyList(), emptySet(), ctx.creationPhase, emptyList(), ctx.catalog)
        assertEquals(RequirementStatus.Satisfied, status(RequirementNode.Attribute("size", source, AttributeId.STRENGTH, ComparisonOperator.EQ, 5), large))
    }

    @Test fun distinct_count_finds_bound_or_proof_in_both_orders_without_counting_unrelated_instances() {
        val defs = listOf(definition("a"), definition("b"))
        val ctx = context(defs, listOf(instance("a"), instance("b")))
        for (alternatives in listOf(listOf(none("independent"), owned("a")), listOf(owned("a"), none("independent")))) {
            val predicate = RequirementNode.AnyOf("count/or", source, alternatives)
            for (scopedPredicate in listOf(predicate, RequirementNode.AllOf("count/and", source,
                listOf(RequirementNode.Attribute("count/str", source, AttributeId.STRENGTH, ComparisonOperator.GE, 1), predicate)))) {
                val count = RequirementNode.DistinctCount("count", source, scopedPredicate, DistinctScope.Definition, ComparisonOperator.EQ, 1)
                val proof = evaluate(count, ctx)
                assertEquals(RequirementStatus.Satisfied, proof.status)
                assertEquals(RequirementValue.Number(1), proof.actualValue)
                assertEquals(listOf("instance/a"), proof.witnesses.filterIsInstance<RequirementWitness.OwnedInstance>().map { it.instanceId })
            }
        }
    }

    @Test fun distinct_count_excludes_impossible_conjunctions_but_preserves_genuinely_uncertain_matches() {
        val defs = listOf(definition("a"), definition("b"))
        val open = RequirementNode.Unresolved("count/open", source, "Open candidate rule")
        for (terms in listOf(listOf(owned("a"), open), listOf(open, owned("a")))) {
            val predicate = RequirementNode.AllOf("count/and", source, terms)
            for (operator in listOf(ComparisonOperator.EQ, ComparisonOperator.LE)) {
                val count = RequirementNode.DistinctCount("count", source, predicate, DistinctScope.Definition, operator, 0)
                val unrelated = evaluate(count, context(defs, listOf(instance("b"))))
                assertEquals(RequirementStatus.Satisfied, unrelated.status)
                assertEquals(RequirementValue.Number(0), unrelated.actualValue)
                assertNull(unrelated.reason)
                val possible = evaluate(count, context(defs, listOf(instance("a"), instance("b"))))
                assertEquals(RequirementStatus.SourceUnresolved, possible.status)
                assertEquals(RequirementValue.Number(0), possible.actualValue)
                assertTrue(possible.reason.orEmpty().contains("0..1"))
            }
            // Independent truth cannot conceal a potentially bound but unresolved alternative.
            val alternatives = RequirementNode.AnyOf("count/or", source, listOf(none("independent"), predicate))
            val count = RequirementNode.DistinctCount("count", source, alternatives, DistinctScope.Definition, ComparisonOperator.EQ, 0)
            assertEquals(RequirementStatus.SourceUnresolved, evaluate(count, context(defs, listOf(instance("a"), instance("b")))).status)
            assertEquals(RequirementStatus.Satisfied, evaluate(count, context(defs, listOf(instance("b")))).status)
        }
    }

    @Test fun acquisition_context_deeply_snapshots_reachable_catalog_collections() {
        val parameters = mutableListOf(ParameterDefinition("weaponTypeId", ParameterDomain.StableIds(setOf("melee"))))
        val options = mutableSetOf("mastery")
        val rows = mutableListOf(PriceRule.Transition(0, 1, 7, 0, source))
        val nestedOptions = mutableMapOf<String, PriceRule>("nested" to PriceRule.TransitionTable(rows, source))
        val conditional = PriceRule.Conditional(none("condition"), PriceRule.ByOption(nestedOptions, source),
            PriceRule.TransitionTable(rows, source), source)
        val outerOptions = mutableMapOf<String, PriceRule>("first" to conditional)
        val definition = definition("a").copy(parameterDefinitions = parameters, abilityOptionIds = options,
            priceRule = PriceRule.ByOption(outerOptions, source))
        val definitions = mutableMapOf(definition.ref to definition)
        val ctx = AcquisitionContext(DublCharacter("snapshot"), emptyList(), emptySet(),
            RequirementNode.CreationPhase.Phase.Creating, emptyList(), AcquisitionCatalog(definitions, "catalog", "resolution"))
        parameters.clear()
        options.clear()
        rows.clear()
        nestedOptions["nested"] = PriceRule.Fixed(999, 0, source)
        outerOptions.clear()
        definitions.clear()
        val captured = ctx.catalog.definitions.getValue(ref("a"))
        assertEquals(listOf("weaponTypeId"), captured.parameterDefinitions.map { it.key })
        assertEquals(setOf("mastery"), captured.abilityOptionIds)
        val outer = assertIs<PriceRule.ByOption>(captured.priceRule)
        val condition = assertIs<PriceRule.Conditional>(outer.options.getValue("first"))
        val inner = assertIs<PriceRule.ByOption>(condition.whenTrue)
        for (table in listOf(assertIs<PriceRule.TransitionTable>(inner.options.getValue("nested")),
            assertIs<PriceRule.TransitionTable>(condition.whenFalse))) {
            assertEquals(listOf(PriceRule.Transition(0, 1, 7, 0, source)), table.transitions)
        }
        assertEquals("catalog", ctx.catalog.catalogRevision)
        assertEquals("resolution", ctx.catalog.resolutionRevision)
    }

    @Test fun typed_availability_never_runs_display_parser_or_force_bypass() {
        val entry = DevelopmentEntry("target", "Target", "", "", 1, DevelopmentCostType.XP, 2, "Ловкость 99", "", "", emptyList(), null, emptyList(), false, false, false, "", "")
        val typed = definition("target")
        val ctx = context(listOf(typed))
        val rules = DevelopmentRules(ctx.character, DevelopmentCatalog("legacy", listOf(entry)), DevelopmentProgress(), ctx)
        assertTrue(rules.availability(entry).canIncrease)
        val blockedCtx = context(listOf(typed.copy(requirements = RequirementNode.Unresolved("target/unknown", source, "Unknown source"))))
        val blocked = DevelopmentRules(blockedCtx.character, DevelopmentCatalog("legacy", listOf(entry.copy(requirements = "-"))), DevelopmentProgress(), blockedCtx)
        assertFalse(blocked.availability(entry).canIncrease)
        assertFalse(blocked.availability(entry).canForceIncrease)
        val missing = DevelopmentRules(ctx.character, DevelopmentCatalog("legacy", listOf(entry)), DevelopmentProgress(), context(emptyList()))
        assertFalse(missing.availability(entry).canIncrease)
        assertFalse(missing.availability(entry).canForceIncrease)
    }
}
