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

    /** Existing derived/read-only character fact, never an automatically purchasable rank. */
    data class NumericProperty(
        override val nodeId: String,
        override val sourceRef: RuleSourceRef,
        val propertyId: String,
        val operator: ComparisonOperator,
        val operand: Int,
    ) : RequirementNode {
        init { require(propertyId.isNotBlank()) { "A numeric property requires a stable ID" } }
    }

    /** The subject chooses an existing instance ID; the predicate proves that exact instance. */
    data class SelectedInstance(override val nodeId: String, override val sourceRef: RuleSourceRef,
        val parameterKey: String, val predicate: RequirementNode) : RequirementNode {
        init { require(parameterKey.isNotBlank()) }
    }

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

    class BranchAccess(
        override val nodeId: String,
        override val sourceRef: RuleSourceRef,
        val packId: String,
        val branchId: String,
        val accessOptionId: String? = null,
        val operator: ComparisonOperator = ComparisonOperator.EQ,
        val operand: Boolean = true,
        parameterBindings: Map<String, String> = emptyMap(),
    ) : RequirementNode {
        /** Prerequisite parameter key -> this subject's selected parameter key. */
        val parameterBindings: Map<String, String> = RequirementMapSnapshot(parameterBindings)
        init {
            require(operator == ComparisonOperator.EQ) { "Access compares boolean presence with EQ" }
            require(this.parameterBindings.all { it.key.isNotBlank() && it.value.isNotBlank() })
        }
        fun copy(nodeId: String = this.nodeId, sourceRef: RuleSourceRef = this.sourceRef,
            packId: String = this.packId, branchId: String = this.branchId, accessOptionId: String? = this.accessOptionId,
            operator: ComparisonOperator = this.operator, operand: Boolean = this.operand,
            parameterBindings: Map<String, String> = this.parameterBindings) =
            BranchAccess(nodeId, sourceRef, packId, branchId, accessOptionId, operator, operand, parameterBindings)
        override fun equals(other: Any?): Boolean = other is BranchAccess && nodeId == other.nodeId && sourceRef == other.sourceRef &&
            packId == other.packId && branchId == other.branchId && accessOptionId == other.accessOptionId &&
            operator == other.operator && operand == other.operand && parameterBindings == other.parameterBindings
        override fun hashCode(): Int = listOf(nodeId, sourceRef, packId, branchId, accessOptionId, operator, operand, parameterBindings).hashCode()
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

private class RequirementMapSnapshot<K, V>(values: Map<K, V>) : AbstractMap<K, V>() {
    private class Entry<K, V>(override val key: K, override val value: V) : Map.Entry<K, V> {
        override fun equals(other: Any?): Boolean = other is Map.Entry<*, *> && key == other.key && value == other.value
        override fun hashCode(): Int = (key?.hashCode() ?: 0) xor (value?.hashCode() ?: 0)
    }
    private val captured = values.map { Entry(it.key, it.value) }
    override val entries: Set<Map.Entry<K, V>> = object : AbstractSet<Map.Entry<K, V>>() {
        override val size get() = captured.size
        override fun iterator(): Iterator<Map.Entry<K, V>> = object : Iterator<Map.Entry<K, V>> {
            private var index = 0
            override fun hasNext() = index < captured.size
            override fun next(): Map.Entry<K, V> = captured[index++]
        }
    }
}
