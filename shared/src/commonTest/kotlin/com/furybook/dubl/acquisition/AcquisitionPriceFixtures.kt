package com.furybook.dubl.acquisition

import com.furybook.core.json.*
import com.furybook.dubl.model.*

/** Test inputs only. Expectations are decoded literally, never obtained from quote or a catalog. */
data class ExpectedPrice(val cost: Amount, val remaining: Amount, val undoBalance: Amount, val evidenceStatus: String)
data class PriceFixture(val change: AcquisitionChange, val context: AcquisitionContext, val expected: ExpectedPrice,
    val opening: Amount, val knownQuote: Amount?, val expectedApply: String?)

class AcquisitionPriceFixtures(private val readCase: (String) -> String, private val catalog: AcquisitionCatalog) {
    fun load(caseId: String): PriceFixture = decode(readCase(caseId))

    fun decode(json: String): PriceFixture {
        val row = JsonReader(json).read() as JsonValue.Obj
        fun text(key: String) = (row.values[key] as JsonValue.Str).value
        fun number(key: String) = (row.values[key] as JsonValue.Num).raw.toLong()
        fun amount(xp: String, ap: String) = Amount(number(xp), number(ap))
        val ident = row.values.getValue("definitionRef")
        val ref = if (ident is JsonValue.Obj) DefinitionRef((ident.values.getValue("pack") as JsonValue.Str).value,
            (ident.values.getValue("id") as JsonValue.Str).value)
            else catalog.definitions.keys.single { it.entryId == (ident as JsonValue.Str).value }
        val parameterRecord = (row.values.getValue("parameters") as JsonValue.Obj).values
        val semanticClass = (parameterRecord["semanticClass"] as? JsonValue.Str)?.value
        val explicitParameters = parameterRecord
            .filterKeys { it != "semanticClass" }.mapValues { (it.value as JsonValue.Str).value }
        val parameterKey = when (ref.entryId) {
            "knowledge_template", "performance_template", "profession_template", "craft_template" -> "specialization"
            "feat_cd965f927a426cc2" -> "knowledgeId"
            "feat_7fc6fc6e63da9688" -> "resistanceType"
            else -> null
        }
        val params = if (explicitParameters.isNotEmpty() || parameterKey == null) explicitParameters else when (semanticClass) {
            "SAME_ID", "RENAMED_SAME_ID" -> mapOf(parameterKey to "spec:a")
            "OTHER_ID", "TWO_DISTINCT" -> mapOf(parameterKey to "spec:b")
            else -> emptyMap() // EMPTY/UNKNOWN never become invented stable IDs.
        }
        val phase = if (text("creationPhase") == "CREATION") RequirementNode.CreationPhase.Phase.Creating
            else RequirementNode.CreationPhase.Phase.Finalized
        val attrs = mutableMapOf<AttributeId, AttributeValue>()
        val instances = mutableListOf<AcquisitionInstance>()
        fun owned(id: String, rank: Int, parameters: Map<String,String> = emptyMap(), instance: String = id) {
            instances += AcquisitionInstance(instance, DefinitionRef("dubl-3.69", id), rank, parameters, null, AcquisitionOrigin.New)
        }
        // T01's explicit legality witness is projected into actual canonical ownership and INT6.
        if (text("discountConditions") == "SELF_TAUGHT") {
            attrs[AttributeId.INTELLIGENCE] = AttributeValue(6)
            owned("feat_dd0ba74aedebce10", 1)
        }
        val aliases = mapOf("DEX" to AttributeId.DEXTERITY, "SPD" to AttributeId.SPEED,
            "STR" to AttributeId.STRENGTH, "CON" to AttributeId.CONSTITUTION)
        val prerequisites = (row.values.getValue("ownedPrerequisites") as JsonValue.Arr).values
        for (value in prerequisites) {
            val p = value as JsonValue.Obj
            aliases.forEach { (key, attr) -> (p.values[key] as? JsonValue.Num)?.let { attrs[attr] = AttributeValue(it.raw.toInt()) } }
            (p.values["skill"] as? JsonValue.Str)?.let { owned(it.value, (p.values.getValue("rank") as JsonValue.Num).raw.toInt()) }
            // Independent source projections: Бегун P11780 needs SPD2 + athletics2;
            // Выносливый P11644 needs CON4. No production rule traversal builds facts.
            when ((p.values["name"] as? JsonValue.Str)?.value) {
                "Бегун" -> {
                    owned("feat_2d6d89e4371fda60", (p.values.getValue("rank") as JsonValue.Num).raw.toInt())
                    owned("athletics", 2)
                    attrs[AttributeId.SPEED] = AttributeValue(2)
                }
                "Выносливый" -> {
                    owned("feat_600fc03329750988", (p.values.getValue("rank") as JsonValue.Num).raw.toInt())
                    attrs[AttributeId.CONSTITUTION] = AttributeValue(4)
                }
            }
            (p.values["knowledgeInstances"] as? JsonValue.Arr)?.values?.forEach { entry ->
                val k = entry as JsonValue.Obj
                val id = (k.values.getValue("id") as JsonValue.Str).value
                owned("knowledge_template", (k.values.getValue("rank") as JsonValue.Num).raw.toInt(),
                    mapOf("specialization" to id), "knowledge:$id")
            }
        }
        // Price-only anchors do not assert missing admission facts; block anchors above do.
        val from = number("fromRank").toInt()
        val instance = "fixture-target"
        if (text("instance") in setOf("SECOND_DISTINCT", "REPEAT_SAME_FORBIDDEN") || semanticClass == "TWO_DISTINCT") {
            val prior = when {
                text("instance") == "REPEAT_SAME_FORBIDDEN" -> params
                parameterKey == "specialization" -> mapOf("specialization" to "spec:a")
                "resistanceType" in params -> mapOf("resistanceType" to "fire")
                "knowledgeId" in params -> mapOf("knowledgeId" to "anatomy")
                else -> emptyMap()
            }
            if (prior.isNotEmpty()) instances += AcquisitionInstance("fixture-prior", ref, 1, prior, null, AcquisitionOrigin.New)
        }
        if (from > 0 && catalog.definitions[ref]?.kind != AcquisitionKind.ATTRIBUTE)
            instances += AcquisitionInstance(instance, ref, from, params, null, AcquisitionOrigin.New)
        val policy = ref.entryId == "approved-policy-receipt"
        val effectiveCatalog = if (!policy) catalog else {
            val source = RuleSourceRef("approved:acquisition-spec", "2026-10-12", "Receipt specimen: purchase20, catalog30, refund20")
            val d = AcquisitionDefinition(ref, AcquisitionKind.DEVELOPMENT, 1, RepeatPolicy.Single,
                RequirementNode.AllOf("policy", source, emptyList(), NoRequirementsEvidence(source, "Policy test specimen")),
                PriceRule.Fixed(20, 0, source), SourceStatus.Confirmed, "Receipt specimen", source)
            catalog.copy(definitions = catalog.definitions + (ref to d))
        }
        val change = AcquisitionChange(effectiveCatalog.definitions.getValue(ref).kind, ref, instance, from,
            number("toRank").toInt(), params, text("optionId").takeUnless { it == "DEFAULT" })
        val context = AcquisitionContext(DublCharacter("oracle", attributes=attrs), instances, emptySet(), phase, emptyList(), effectiveCatalog)
        return PriceFixture(change, context, ExpectedPrice(amount("expectedCostXP", "expectedCostAP"),
            amount("expectedRemainingXP", "expectedRemainingAP"), amount("expectedUndoXP", "expectedUndoAP"), text("evidenceStatus")),
            amount("openingXP", "openingAP"), (row.values["knownQuoteXP"] as? JsonValue.Num)?.let { Amount(it.raw.toLong(), 0) },
            (row.values["expectedApply"] as? JsonValue.Str)?.value)
    }
}
