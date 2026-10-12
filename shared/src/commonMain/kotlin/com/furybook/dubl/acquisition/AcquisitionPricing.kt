package com.furybook.dubl.acquisition

import com.furybook.core.json.*

/** Signed balances and marginal costs. Negative costs are explicit source-backed grants. */
data class Amount(val xp: Long, val ap: Long) {
    operator fun plus(other: Amount) = Amount(add(xp, other.xp), add(ap, other.ap))
    operator fun minus(other: Amount) = Amount(subtract(xp, other.xp), subtract(ap, other.ap))
    fun toJson(): String = "{\"xp\":\"$xp\",\"ap\":\"$ap\"}"

    companion object {
        val ZERO = Amount(0, 0)
        fun fromJson(json: String): Amount {
            val root = JsonReader(json).read() as? JsonValue.Obj ?: throw IllegalArgumentException("Expected amount object")
            fun read(key: String): Long {
                val text = (root.values[key] as? JsonValue.Str)?.value
                    ?: throw IllegalArgumentException("$key must be a decimal string")
                require(Regex("-?(0|[1-9][0-9]*)").matches(text) && text != "-0") { "Invalid decimal amount" }
                return text.toLongOrNull() ?: throw IllegalArgumentException("Amount overflow")
            }
            return Amount(read("xp"), read("ap"))
        }
        private fun add(a: Long, b: Long): Long {
            if ((b > 0 && a > Long.MAX_VALUE - b) || (b < 0 && a < Long.MIN_VALUE - b))
                throw ArithmeticException("Amount overflow")
            return a + b
        }
        private fun subtract(a: Long, b: Long): Long {
            if ((b > 0 && a < Long.MIN_VALUE + b) || (b < 0 && a > Long.MAX_VALUE + b))
                throw ArithmeticException("Amount overflow")
            return a - b
        }
    }
}

class AcquisitionChange(
    val kind: AcquisitionKind,
    val definition: DefinitionRef,
    val instanceId: String,
    val fromRank: Int,
    val toRank: Int,
    parameters: Map<String, String>,
    val optionId: String?,
) {
    val parameters: Map<String, String> = PriceParameterSnapshot(parameters)
    fun copy(kind: AcquisitionKind = this.kind, definition: DefinitionRef = this.definition,
        instanceId: String = this.instanceId, fromRank: Int = this.fromRank, toRank: Int = this.toRank,
        parameters: Map<String, String> = this.parameters, optionId: String? = this.optionId) =
        AcquisitionChange(kind, definition, instanceId, fromRank, toRank, parameters, optionId)
    override fun equals(other: Any?): Boolean = other is AcquisitionChange && kind == other.kind &&
        definition == other.definition && instanceId == other.instanceId && fromRank == other.fromRank &&
        toRank == other.toRank && parameters == other.parameters && optionId == other.optionId
    override fun hashCode(): Int = listOf(kind, definition, instanceId, fromRank, toRank, parameters, optionId).hashCode()
    override fun toString(): String = "AcquisitionChange($kind, $definition, $instanceId, $fromRank->$toRank, $parameters, $optionId)"
}

private class PriceParameterSnapshot(parameters: Map<String, String>) : AbstractMap<String, String>() {
    private class Entry(override val key: String, override val value: String) : Map.Entry<String, String> {
        override fun equals(other: Any?): Boolean = other is Map.Entry<*, *> && key == other.key && value == other.value
        override fun hashCode(): Int = key.hashCode() xor value.hashCode()
    }
    private val captured = parameters.map { (key, value) -> Entry(key, value) }
    override val entries: Set<Map.Entry<String, String>> = object : AbstractSet<Map.Entry<String, String>>() {
        override val size: Int get() = captured.size
        override fun iterator(): Iterator<Map.Entry<String, String>> = object : Iterator<Map.Entry<String, String>> {
            private var index = 0
            override fun hasNext(): Boolean = index < captured.size
            override fun next(): Map.Entry<String, String> = if (hasNext()) captured[index++] else throw NoSuchElementException()
        }
    }
}

sealed interface PriceResult {
    data class Known(val cost: Amount, val sourceRef: RuleSourceRef,
        val discountEvidence: List<RequirementResult> = emptyList(),
        val componentSources: List<RuleSourceRef> = listOf(sourceRef)) : PriceResult
    data class Unknown(val reason: String) : PriceResult
}

/** Price only: identity, rank caps, admission and affordability are planner/apply responsibilities.
 * Never reads legacy cumulative calculators, display labels or the test oracle.
 */
fun quote(change: AcquisitionChange, context: AcquisitionContext): PriceResult {
    val definition = context.catalog.definitions[change.definition]
        ?: return PriceResult.Unknown("Missing definition ${change.definition}")
    if (definition.kind != change.kind) return PriceResult.Unknown("Acquisition kind mismatch")
    if (definition.sourceStatus == SourceStatus.Unavailable) return PriceResult.Unknown("Source unavailable")
    if (change.fromRank > change.toRank) return PriceResult.Unknown("Reverse acquisition requires a separate operation")
    if (change.kind != AcquisitionKind.ATTRIBUTE && change.fromRank < 0) return PriceResult.Unknown("Invalid negative rank")
    // A no-change request is explicitly zero, never an inference from missing price evidence.
    if (change.fromRank == change.toRank) return PriceResult.Known(Amount.ZERO, definition.sourceRef)
    return try { PriceEvaluation(change, context).price(definition.priceRule) }
    catch (_: ArithmeticException) { PriceResult.Unknown("Amount overflow") }
}

private class PriceEvaluation(private val change: AcquisitionChange, private val context: AcquisitionContext) {
    private var remaining = 16_384
    fun price(rule: PriceRule, depth: Int = 0): PriceResult {
        if (depth >= 64 || remaining-- <= 0) return PriceResult.Unknown("Price traversal limit reached")
        return when (rule) {
            is PriceRule.Unknown -> PriceResult.Unknown(rule.reason)
            is PriceRule.Fixed -> if (change.fromRank == 0 && change.toRank == 1)
                PriceResult.Known(Amount(rule.xp, rule.ap), rule.sourceRef)
                else PriceResult.Unknown("Fixed price confirms only rank 0 -> 1")
            is PriceRule.PerRank -> {
                val steps = change.toRank.toLong() - change.fromRank.toLong()
                PriceResult.Known(Amount(multiply(rule.xp, steps), multiply(rule.ap, steps)), rule.sourceRef)
            }
            is PriceRule.TransitionTable -> {
                val exact = rule.transitions.filter { it.fromRank == change.fromRank && it.toRank == change.toRank }
                if (exact.size > 1) return PriceResult.Unknown("Conflicting price transitions")
                if (exact.size == 1) return exact.single().let { PriceResult.Known(Amount(it.xp, it.ap), it.sourceRef) }
                // Sum explicit adjacent rows; never infer a later rank from the first price.
                val rows = rule.transitions.filter { it.fromRank >= change.fromRank && it.toRank <= change.toRank &&
                    it.toRank.toLong() - it.fromRank.toLong() == 1L }.sortedBy { it.fromRank }
                if (rows.size.toLong() != change.toRank.toLong() - change.fromRank.toLong())
                    return PriceResult.Unknown("Unconfirmed price transition ${change.fromRank} -> ${change.toRank}")
                var cursor = change.fromRank
                var sum = Amount.ZERO
                for (row in rows) {
                    if (row.fromRank != cursor) return PriceResult.Unknown("Missing or duplicate price step")
                    sum += Amount(row.xp, row.ap)
                    cursor = row.toRank
                }
                PriceResult.Known(sum, rule.sourceRef, componentSources=rows.map { it.sourceRef })
            }
            is PriceRule.ByOption -> {
                val selected = rule.options[change.optionId] ?: return PriceResult.Unknown("Select a source-confirmed payment/access option")
                price(selected, depth + 1)
            }
            is PriceRule.Conditional -> {
                val proof = evaluate(rule.condition, context)
                val branch = when (proof.status) {
                    RequirementStatus.Satisfied -> rule.whenTrue
                    RequirementStatus.Unsatisfied -> rule.whenFalse
                    else -> {
                        // A condition irrelevant to this transition must not block
                        // a price both branches independently establish (e.g. skill rank3).
                        val yes = price(rule.whenTrue, depth + 1)
                        val no = price(rule.whenFalse, depth + 1)
                        if (yes is PriceResult.Known && no is PriceResult.Known && yes.cost == no.cost)
                            return PriceResult.Known(yes.cost, rule.sourceRef,
                                componentSources=(listOf(rule.sourceRef) + yes.componentSources + no.componentSources).distinct())
                        return PriceResult.Unknown("Unresolved price condition: ${proof.status}: ${proof.reason}")
                    }
                }
                when (val quoted = price(branch, depth + 1)) {
                    is PriceResult.Unknown -> quoted
                    is PriceResult.Known -> quoted.copy(discountEvidence=listOf(proof) + quoted.discountEvidence,
                        componentSources=listOf(rule.sourceRef) + quoted.componentSources)
                }
            }
        }
    }

    private fun multiply(value: Long, count: Long): Long {
        require(count > 0)
        if (value > Long.MAX_VALUE / count || value < Long.MIN_VALUE / count) throw ArithmeticException("Amount overflow")
        return value * count
    }
}
