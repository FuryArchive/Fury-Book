package com.furybook.dubl.acquisition

import com.furybook.dubl.model.AttributeId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class RequirementModelsTest {
    private val source = RuleSourceRef(
        documentId = "core", documentVersion = "3.69", sheet = "Development", paragraph = 42,
        originalText = "Ловкость 3 или Скорость 3, Ближний бой 3 или Безоружный бой 3",
        resolutionId = "confirmed-grouping", resolutionRevision = "1",
    )

    @Test
    fun and_or_tree_preserves_scope() {
        val attributes = RequirementNode.AnyOf("attributes", source, listOf(
            RequirementNode.Attribute("dex", source, AttributeId.DEXTERITY, ComparisonOperator.GE, 3),
            RequirementNode.Attribute("spd", source, AttributeId.SPEED, ComparisonOperator.GE, 3),
        ))
        val skills = RequirementNode.AnyOf("skills", source, listOf(
            RequirementNode.OwnedRank("melee", source, "dubl-3.69", "melee", ComparisonOperator.GE, 3),
            RequirementNode.OwnedRank("unarmed", source, "dubl-3.69", "unarmed", ComparisonOperator.GE, 3),
        ))
        val tree = RequirementNode.AllOf("root", source, listOf(attributes, skills))

        val sameTree = RequirementNode.AllOf("root", source.copy(), listOf(
            RequirementNode.AnyOf("attributes", source.copy(), attributes.children.toList()),
            RequirementNode.AnyOf("skills", source.copy(), skills.children.toList()),
        ))
        assertEquals(sameTree, tree)
        assertEquals(sameTree.hashCode(), tree.hashCode())
        assertEquals(listOf(attributes, skills), tree.children)
        assertEquals(listOf("dex", "spd"), assertIs<RequirementNode.AnyOf>(tree.children[0]).children.map { it.nodeId })
        assertEquals(listOf("melee", "unarmed"), assertIs<RequirementNode.AnyOf>(tree.children[1]).children.map { it.nodeId })
        assertNotEquals<RequirementNode>(tree, RequirementNode.AnyOf("root", source, listOf(attributes, skills)))
    }

    @Test
    fun unknown_is_not_empty_all() {
        val unknown = RequirementNode.Unresolved("lost-rule", source, "Missing confirmed grouping")
        val tree = RequirementNode.AllOf("root", source, listOf(unknown))

        assertEquals(unknown, tree.children.single())
        assertIs<RequirementNode.Unresolved>(tree.children.single())
        assertEquals("Missing confirmed grouping", unknown.reason)
        assertEquals(source, unknown.sourceRef)
    }

    @Test
    fun empty_and_requires_explicit_no_requirements() {
        assertFailsWith<IllegalArgumentException> { RequirementNode.AllOf("empty", source, emptyList()) }
        val evidence = NoRequirementsEvidence(source.copy(originalText = "Нет требований"), "Explicit source statement")
        val none = RequirementNode.AllOf("none", source, emptyList(), evidence)
        assertEquals(emptyList(), none.children)
        assertEquals(evidence, none.noRequirementsEvidence)
        assertFailsWith<IllegalArgumentException> { NoRequirementsEvidence(source, " ") }
        assertFailsWith<IllegalArgumentException> { NoRequirementsEvidence(source.copy(originalText = ""), "Claim") }
        assertFailsWith<IllegalArgumentException> { RequirementNode.AnyOf("empty-or", source, emptyList()) }
        assertFailsWith<IllegalArgumentException> {
            RequirementNode.AllOf("contradiction", source, listOf(none), evidence)
        }
    }

    @Test
    fun comparisons_keep_the_source_operator_and_operand() {
        assertEquals(setOf("EQ", "GE", "LE", "LT", "GT"), ComparisonOperator.entries.map { it.name }.toSet())
        for (operator in ComparisonOperator.entries) {
            val attribute = RequirementNode.Attribute("dex", source, AttributeId.DEXTERITY, operator, 4)
            val rank = RequirementNode.OwnedRank("rank", source, "pack", "entry", operator, 2, "instance-a")
            assertEquals(operator, attribute.operator)
            assertEquals(4, attribute.operand)
            assertEquals(operator, rank.operator)
            assertEquals(2, rank.operand)
            assertEquals("instance-a", rank.instanceId)
        }
    }

    @Test
    fun distinct_count_keeps_predicate_and_uniqueness_scope() {
        val predicate = RequirementNode.SkillFamily("cries", source, "pack", "battle-cries", ComparisonOperator.GT, 0)
        val byDefinition = RequirementNode.DistinctCount(
            "three-cries", source, predicate, DistinctScope.Definition, ComparisonOperator.EQ, 3,
        )
        val byInstance = byDefinition.copy(uniqueness = DistinctScope.Instance, operator = ComparisonOperator.LE)

        assertEquals(predicate, byDefinition.predicate)
        assertEquals(ComparisonOperator.GT, assertIs<RequirementNode.SkillFamily>(byDefinition.predicate).operator)
        assertEquals(DistinctScope.Definition, byDefinition.uniqueness)
        assertEquals(DistinctScope.Instance, byInstance.uniqueness)
        assertEquals(ComparisonOperator.EQ, byDefinition.operator)
        assertEquals(ComparisonOperator.LE, byInstance.operator)
        assertEquals(3, byInstance.operand)
        assertNotEquals(byDefinition, byInstance)
    }

    @Test
    fun identity_qualifiers_and_evidence_are_not_display_names() {
        val qualifier = ParameterMatch("weapon-category", "cold-weapon")
        val specialization = RequirementNode.Specialization(
            "weapon", source, "pack", "weapon-specialization", ComparisonOperator.GE, 3, qualifier, "instance-1",
        )
        val branch = RequirementNode.BranchAccess("branch", source, "pack", "school", "ap-access")
        val spell = RequirementNode.LearnedSpell("spell", source, "pack", "mystic-weapon")
        val external = RequirementNode.ExternalEvidence("training", source, "training-approved", "weapon-transition-2")
        val exclusive = RequirementNode.Exclusive("exclusive", source, specialization)

        assertEquals(qualifier, specialization.qualifier)
        assertEquals("instance-1", specialization.instanceId)
        assertEquals("ap-access", branch.accessOptionId)
        assertEquals(true, spell.operand)
        assertEquals("mystic-weapon", spell.entryId)
        assertEquals("weapon-transition-2", external.scopeId)
        assertEquals(specialization, exclusive.conflicting)
        assertNotEquals(external, external.copy(scopeId = "weapon-transition-3"))
        assertNotEquals(spell, spell.copy(packId = "different-pack"))
    }

    @Test
    fun creation_phase_is_a_typed_transition_condition() {
        val phase = RequirementNode.CreationPhase("creation-only", source, RequirementNode.CreationPhase.Phase.Creating)
        assertEquals(ComparisonOperator.EQ, phase.operator)
        assertNotEquals(phase, phase.copy(operand = RequirementNode.CreationPhase.Phase.Finalized))
        assertFailsWith<IllegalArgumentException> { phase.copy(operator = ComparisonOperator.GE) }
        assertFailsWith<IllegalArgumentException> {
            RequirementNode.LearnedSpell("spell", source, "pack", "spell", operator = ComparisonOperator.LT)
        }
    }

    @Test
    fun source_wording_and_resolution_metadata_remain_exact() {
        val exact = source.copy(originalText = "А ил Б / В ли Г / Д и Е / Ж или З")
        val node = RequirementNode.Unresolved("raw", exact, "Unconfirmed separators")
        assertEquals("А ил Б / В ли Г / Д и Е / Ж или З", node.sourceRef.originalText)
        assertEquals("core", node.sourceRef.documentId)
        assertEquals("3.69", node.sourceRef.documentVersion)
        assertEquals("Development", node.sourceRef.sheet)
        assertEquals(42, node.sourceRef.paragraph)
        assertEquals("confirmed-grouping", node.sourceRef.resolutionId)
        assertEquals("1", node.sourceRef.resolutionRevision)
        val unresolvedSource = exact.copy(resolutionId = null, resolutionRevision = null)
        assertNull(unresolvedSource.resolutionId)
        assertNull(unresolvedSource.resolutionRevision)
    }

    @Test
    fun group_children_are_immutable_snapshots() {
        val unknown = RequirementNode.Unresolved("unknown", source, "Not imported")
        val input = mutableListOf<RequirementNode>(unknown)
        val all = RequirementNode.AllOf("all", source, input)
        val any = RequirementNode.AnyOf("any", source, input)
        input.clear()
        assertEquals(listOf(unknown), all.children)
        assertEquals(listOf(unknown), any.children)
        assertFailsWith<ClassCastException> { (all.children as MutableList<RequirementNode>).clear() }
        assertFailsWith<ClassCastException> { (any.children as MutableList<RequirementNode>).clear() }
    }

    @Test
    fun result_retains_typed_values_witnesses_children_and_unknown_reason() {
        val witness = RequirementWitness.OwnedInstance("pack", "cry", "instance-1", 3)
        val child = RequirementResult(
            status = RequirementStatus.SourceUnresolved, nodeId = "unknown", reason = "Missing source rule",
        )
        val witnesses = mutableListOf<RequirementWitness>(witness)
        val children = mutableListOf(child)
        val result = RequirementResult(
            status = RequirementStatus.Unsatisfied, nodeId = "count", witnesses = witnesses, children = children,
            reason = "Need three distinct cries", actualValue = RequirementValue.Number(1),
            requiredValue = RequirementValue.Number(3),
        )
        witnesses.clear()
        children.clear()
        assertEquals(listOf(witness), result.witnesses)
        assertEquals(listOf(child), result.children)
        assertEquals("Missing source rule", result.children.single().reason)
        assertEquals(RequirementStatus.SourceUnresolved, result.children.single().status)
        assertNull(child.actualValue)
        assertNull(child.requiredValue)
        assertEquals(RequirementValue.Number(1), result.actualValue)
        assertEquals(RequirementValue.Number(3), result.requiredValue)
        assertEquals("count", result.nodeId)
        assertFailsWith<ClassCastException> { (result.witnesses as MutableList<RequirementWitness>).clear() }
        assertFailsWith<ClassCastException> { (result.children as MutableList<RequirementResult>).clear() }
        assertEquals(
            setOf("Satisfied", "Unsatisfied", "ChoiceRequired", "SourceUnresolved", "Conflict"),
            RequirementStatus.entries.map { it.name }.toSet(),
        )
    }
}
