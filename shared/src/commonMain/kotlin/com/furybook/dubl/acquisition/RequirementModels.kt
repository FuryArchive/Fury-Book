package com.furybook.dubl.acquisition

import com.furybook.dubl.model.AttributeId

/** Original wording is retained verbatim. Missing resolution metadata means no resolution is claimed. */
data class RuleSourceRef(
    val documentId: String,
    val documentVersion: String,
    val originalText: String,
    val sheet: String? = null,
    val paragraph: Int? = null,
    val resolutionId: String? = null,
    val resolutionRevision: String? = null,
)

enum class ComparisonOperator { EQ, GE, LE, LT, GT }

/** Definition identity is (packId, entryId); instance identity is scoped to the character. */
enum class DistinctScope { Definition, Instance }

data class ParameterMatch(val key: String, val valueId: String)

/** Importers must explicitly supply evidence for a confirmed absence of requirements. */
data class NoRequirementsEvidence(val sourceRef: RuleSourceRef, val statement: String) {
    init {
        require(statement.isNotBlank()) { "An explicit no-requirements statement is required" }
        require(sourceRef.originalText.isNotBlank()) { "No-requirements evidence must retain source wording" }
    }
}

/**
 * Declarative conditions only: semantic evaluation belongs to RequirementEvaluator.
 * IDs are supplied by the importer and retained unchanged; labels and text are never identity.
 * Definition fields use stable IDs until the acquisition identity contract is introduced.
 */
sealed interface RequirementNode {
    val nodeId: String
    val sourceRef: RuleSourceRef

    class AllOf(
        override val nodeId: String,
        override val sourceRef: RuleSourceRef,
        children: List<RequirementNode>,
        val noRequirementsEvidence: NoRequirementsEvidence? = null,
    ) : RequirementNode {
        val children: List<RequirementNode> = RequirementSnapshot(children)

        init {
            require(this.children.isNotEmpty() || noRequirementsEvidence != null) {
                "An empty AllOf requires explicit no-requirements evidence"
            }
            require(this.children.isEmpty() || noRequirementsEvidence == null) {
                "No-requirements evidence cannot accompany conditions"
            }
        }

        override fun equals(other: Any?): Boolean = other is AllOf &&
            nodeId == other.nodeId && sourceRef == other.sourceRef &&
            children == other.children && noRequirementsEvidence == other.noRequirementsEvidence

        override fun hashCode(): Int = listOf(nodeId, sourceRef, children, noRequirementsEvidence).hashCode()
        override fun toString(): String =
            "AllOf(nodeId=$nodeId, sourceRef=$sourceRef, children=$children, noRequirementsEvidence=$noRequirementsEvidence)"
    }

    class AnyOf(
        override val nodeId: String,
        override val sourceRef: RuleSourceRef,
        children: List<RequirementNode>,
    ) : RequirementNode {
        val children: List<RequirementNode> = RequirementSnapshot(children)

        init {
            require(this.children.isNotEmpty()) { "An AnyOf must contain alternatives" }
        }

        override fun equals(other: Any?): Boolean = other is AnyOf &&
            nodeId == other.nodeId && sourceRef == other.sourceRef && children == other.children

        override fun hashCode(): Int = listOf(nodeId, sourceRef, children).hashCode()
        override fun toString(): String = "AnyOf(nodeId=$nodeId, sourceRef=$sourceRef, children=$children)"
    }

    data class Attribute(
        override val nodeId: String,
        override val sourceRef: RuleSourceRef,
        val attributeId: AttributeId,
        val operator: ComparisonOperator,
        val operand: Int,
    ) : RequirementNode

    data class OwnedRank(
        override val nodeId: String,
        override val sourceRef: RuleSourceRef,
        val packId: String,
        val entryId: String,
        val operator: ComparisonOperator,
        val operand: Int,
        val instanceId: String? = null,
    ) : RequirementNode

    data class SkillFamily(
        override val nodeId: String,
        override val sourceRef: RuleSourceRef,
        val packId: String,
        val familyId: String,
        val operator: ComparisonOperator,
        val operand: Int,
    ) : RequirementNode

    data class Specialization(
        override val nodeId: String,
        override val sourceRef: RuleSourceRef,
        val packId: String,
        val entryId: String,
        val operator: ComparisonOperator,
        val operand: Int,
        val qualifier: ParameterMatch? = null,
        val instanceId: String? = null,
    ) : RequirementNode

    data class BranchAccess(
        override val nodeId: String,
        override val sourceRef: RuleSourceRef,
        val packId: String,
        val branchId: String,
        val accessOptionId: String? = null,
        val operator: ComparisonOperator = ComparisonOperator.EQ,
        val operand: Boolean = true,
    ) : RequirementNode {
        init { require(operator == ComparisonOperator.EQ) { "Access compares boolean presence with EQ" } }
    }

    data class LearnedSpell(
        override val nodeId: String,
        override val sourceRef: RuleSourceRef,
        val packId: String,
        val entryId: String,
        val operator: ComparisonOperator = ComparisonOperator.EQ,
        val operand: Boolean = true,
    ) : RequirementNode {
        init { require(operator == ComparisonOperator.EQ) { "Learned state compares boolean presence with EQ" } }
    }

    /** Predicate is evaluated for each candidate; uniqueness is independent of its rank comparison. */
    data class DistinctCount(
        override val nodeId: String,
        override val sourceRef: RuleSourceRef,
        val predicate: RequirementNode,
        val uniqueness: DistinctScope,
        val operator: ComparisonOperator,
        val operand: Int,
    ) : RequirementNode

    /** This condition constrains the requested transition, rather than granting rights from old ownership. */
    data class CreationPhase(
        override val nodeId: String,
        override val sourceRef: RuleSourceRef,
        val operand: Phase,
        val operator: ComparisonOperator = ComparisonOperator.EQ,
    ) : RequirementNode {
        enum class Phase { Creating, Finalized }
        init { require(operator == ComparisonOperator.EQ) { "Creation phases are compared with EQ" } }
    }

    /** Describes conflicting ownership without deleting or suppressing existing purchases. */
    data class Exclusive(
        override val nodeId: String,
        override val sourceRef: RuleSourceRef,
        val conflicting: RequirementNode,
    ) : RequirementNode

    /** Confirmable external facts are separate from unresolved source rules. */
    data class ExternalEvidence(
        override val nodeId: String,
        override val sourceRef: RuleSourceRef,
        val evidenceKey: String,
        val scopeId: String,
        val operator: ComparisonOperator = ComparisonOperator.EQ,
        val operand: Boolean = true,
    ) : RequirementNode {
        init { require(operator == ComparisonOperator.EQ) { "External evidence compares boolean presence with EQ" } }
    }

    data class Unresolved(
        override val nodeId: String,
        override val sourceRef: RuleSourceRef,
        val reason: String,
    ) : RequirementNode {
        init { require(reason.isNotBlank()) { "An unresolved condition must retain its reason" } }
    }
}

enum class RequirementStatus { Satisfied, Unsatisfied, ChoiceRequired, SourceUnresolved, Conflict }

sealed interface RequirementValue {
    data class Number(val value: Int) : RequirementValue
    data class Flag(val value: Boolean) : RequirementValue
    data class StableId(val packId: String, val entryId: String) : RequirementValue
    data class Phase(val value: RequirementNode.CreationPhase.Phase) : RequirementValue
}

/** Evidence identifies the actual matched object, not its display name. */
sealed interface RequirementWitness {
    data class OwnedInstance(val packId: String, val entryId: String, val instanceId: String, val rank: Int) : RequirementWitness
    data class Branch(val packId: String, val branchId: String, val accessOptionId: String) : RequirementWitness
    data class LearnedSpell(val packId: String, val entryId: String) : RequirementWitness
    data class External(
        val evidenceKey: String,
        val scopeId: String,
        val evidenceId: String,
        val sourceRef: RuleSourceRef,
    ) : RequirementWitness
}

/** No status is defaulted to success and missing actual/required values are retained as unknown. */
class RequirementResult(
    val status: RequirementStatus,
    val nodeId: String,
    witnesses: List<RequirementWitness> = emptyList(),
    children: List<RequirementResult> = emptyList(),
    val reason: String? = null,
    val actualValue: RequirementValue? = null,
    val requiredValue: RequirementValue? = null,
) {
    val witnesses: List<RequirementWitness> = RequirementSnapshot(witnesses)
    val children: List<RequirementResult> = RequirementSnapshot(children)

    override fun equals(other: Any?): Boolean = other is RequirementResult &&
        status == other.status && nodeId == other.nodeId && witnesses == other.witnesses &&
        children == other.children && reason == other.reason &&
        actualValue == other.actualValue && requiredValue == other.requiredValue

    override fun hashCode(): Int =
        listOf(status, nodeId, witnesses, children, reason, actualValue, requiredValue).hashCode()

    override fun toString(): String =
        "RequirementResult(status=$status, nodeId=$nodeId, witnesses=$witnesses, children=$children, " +
            "reason=$reason, actualValue=$actualValue, requiredValue=$requiredValue)"
}

/** A defensive snapshot whose public view cannot be cast to a Kotlin MutableList. */
private class RequirementSnapshot<T>(values: List<T>) : AbstractList<T>() {
    private val values = values.toList()
    override val size: Int get() = values.size
    override fun get(index: Int): T = values[index]
}
