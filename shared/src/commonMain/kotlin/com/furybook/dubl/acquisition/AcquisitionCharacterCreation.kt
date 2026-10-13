package com.furybook.dubl.acquisition

import com.furybook.core.json.*
import com.furybook.dubl.model.CharacterSheetExtras
import com.furybook.dubl.model.DublCharacter
import kotlinx.coroutines.CancellationException

/** This policy is for the first opening only, not an XP/AP conversion or an advancement award. */
data class CreationFundingPolicy(val ruleId: String, val xpPerAP: Long, val sourceRef: RuleSourceRef) {
    internal val supported: Boolean get() = ruleId == INITIAL_AP_RULE && xpPerAP == 1000L &&
        sourceRef.resolutionId == ruleId && !sourceRef.resolutionRevision.isNullOrBlank() &&
        sourceRef.documentId.isNotBlank() && sourceRef.documentVersion.isNotBlank() && sourceRef.originalText.isNotBlank()
}
internal const val INITIAL_AP_RULE = "UR_NEW_CHARACTER_INITIAL_AP_PER_1000_XP"

/** Shared computes the displayed AP. Review has no ID allocation, store read, or write. */
data class NewCharacterFundingReview(
    val reviewKey: String,
    val initialXP: Long,
    val initialAP: Long?,
    val name: String,
    val concept: String,
    val catalogRevision: String,
    val resolutionRevision: String,
    val policySource: RuleSourceRef?,
    val capabilities: AcquisitionCapabilities,
    val blockReason: String?,
) {
    val executable: Boolean get() = blockReason == null && initialAP != null
}

/** The caller allocates both IDs once after confirmation and retains this exact intent on retry. */
data class CreateAcquisitionCharacter(
    val creationId: String,
    val characterId: String,
    val initialXP: Long,
    val name: String,
    val concept: String,
    val catalogRevision: String,
    val resolutionRevision: String,
) {
    val canonical: String get() = canonicalJson(jsonStringify(jsonObject(
        "type" to jsonString("CreateAcquisitionCharacter"), "creationId" to jsonString(creationId),
        "characterId" to jsonString(characterId), "initialXP" to jsonString(initialXP.toString()),
        "name" to jsonString(name), "concept" to jsonString(concept),
        "catalogRevision" to jsonString(catalogRevision), "resolutionRevision" to jsonString(resolutionRevision))))
}

sealed interface NewCharacterCreationResult {
    data class Created(val state: StoredCharacter) : NewCharacterCreationResult
    data class AlreadyCreated(val state: StoredCharacter) : NewCharacterCreationResult
    data class StalePolicy(val review: NewCharacterFundingReview) : NewCharacterCreationResult
    data class IdentityConflict(val reason: String) : NewCharacterCreationResult
    data class Unavailable(val reason: String, val loadResult: LoadResult? = null) : NewCharacterCreationResult
    data class StorageFailure(val reason: String) : NewCharacterCreationResult
}

/** One explicit absence CAS. Migration, copy, restore and ordinary mutation never call this class.
 * The injected store belongs to one captured owner/storage scope; adapters guard that scope before
 * starting and publishing. No capability grants cross-owner access or allows a legacy fallback.
 */
class AcquisitionCharacterCreator(
    private val store: AcquisitionStore,
    private val catalogProvider: () -> AcquisitionCatalog,
    private val capabilitiesProvider: () -> AcquisitionCapabilities,
) {
    fun review(initialXP: Long, name: String = "Новый персонаж", concept: String = ""): NewCharacterFundingReview =
        review(initialXP, name, concept, catalogProvider(), capabilitiesProvider())

    private fun review(initialXP: Long, name: String, concept: String, catalog: AcquisitionCatalog,
        capabilities: AcquisitionCapabilities): NewCharacterFundingReview {
        val policy = catalog.creationFundingPolicy
        val reason = when {
            initialXP !in 0..Int.MAX_VALUE.toLong() -> "Initial XP must be between 0 and ${Int.MAX_VALUE}"
            !capabilities.localDurable || !capabilities.formatWritable -> "Durable canonical creation is unavailable"
            catalog.catalogRevision.isBlank() || catalog.resolutionRevision.isBlank() -> "Catalog authority is unavailable"
            policy?.supported != true -> "Confirmed new-character funding policy is unavailable"
            else -> null
        }
        val initialAP = if (initialXP in 0..Int.MAX_VALUE.toLong() && policy?.supported == true) initialXP / policy.xpPerAP else null
        val key = canonicalJson(jsonStringify(jsonObject("type" to jsonString("NewCharacterFundingReview"),
            "initialXP" to jsonString(initialXP.toString()), "initialAP" to (initialAP?.let { jsonString(it.toString()) } ?: JsonValue.Null),
            "name" to jsonString(name), "concept" to jsonString(concept),
            "catalogRevision" to jsonString(catalog.catalogRevision), "resolutionRevision" to jsonString(catalog.resolutionRevision),
            "policy" to (policy?.let(::fundingPolicyJson) ?: JsonValue.Null),
            "localDurable" to jsonBoolean(capabilities.localDurable), "formatWritable" to jsonBoolean(capabilities.formatWritable),
            "blockReason" to (reason?.let(::jsonString) ?: JsonValue.Null))))
        return NewCharacterFundingReview(key, initialXP, initialAP, name, concept, catalog.catalogRevision,
            catalog.resolutionRevision, policy?.sourceRef, capabilities, reason)
    }

    /** Read-only reconciliation. Missing never means create and today's policy never reprices history. */
    suspend fun reconcile(command: CreateAcquisitionCharacter): NewCharacterCreationResult = safely {
        validateCommand(command)?.let { return@safely it }
        existing(command, store.load(command.characterId))
    }

    suspend fun create(command: CreateAcquisitionCharacter): NewCharacterCreationResult = safely {
        validateCommand(command)?.let { return@safely it }
        val loaded = store.load(command.characterId)
        if (loaded !is LoadResult.Missing) return@safely existing(command, loaded)
        if (loaded.characterId != command.characterId)
            return@safely NewCharacterCreationResult.IdentityConflict("Missing result belongs to another character")
        val catalog = catalogProvider()
        val review = review(command.initialXP, command.name, command.concept, catalog, capabilitiesProvider())
        if (!review.executable) return@safely NewCharacterCreationResult.Unavailable(review.blockReason ?: "Funding is unavailable")
        if (command.catalogRevision != catalog.catalogRevision || command.resolutionRevision != catalog.resolutionRevision)
            return@safely NewCharacterCreationResult.StalePolicy(review)
        val policy = checkNotNull(catalog.creationFundingPolicy)
        val next = StoredCharacter(
            DublCharacter(command.characterId, name=command.name, concept=command.concept,
                experience=command.initialXP.toInt(), creationExperience=command.initialXP.toInt()),
            CharacterSheetExtras(), emptyList(),
            Ledger(Opening(creationIdentity(command), legacySourceFingerprint(command.canonical),
                Amount(command.initialXP, checkNotNull(review.initialAP)), RequirementNode.CreationPhase.Phase.Creating,
                OpeningProvenance.NewCharacter, fundingReason(command, policy)), emptyList()),
            emptyList(), emptyList(), SyncMetadata(0), 0, 0, RequirementNode.CreationPhase.Phase.Creating)
        // Validate the exact durable representation before offering it to a store.
        encodeEnvelope(next)
        val capabilities = capabilitiesProvider()
        if (!capabilities.localDurable || !capabilities.formatWritable)
            return@safely NewCharacterCreationResult.Unavailable("Durable canonical creation became unavailable")
        when (val result = store.compareAndSet(command.characterId, -1, next)) {
            CommitResult.Committed -> when (val reloaded = existing(command, store.load(command.characterId))) {
                is NewCharacterCreationResult.AlreadyCreated -> NewCharacterCreationResult.Created(reloaded.state)
                else -> NewCharacterCreationResult.StorageFailure("Committed creation could not be reconciled; retain the same command")
            }
            CommitResult.Conflict -> existing(command, store.load(command.characterId))
            is CommitResult.StorageFailure -> NewCharacterCreationResult.StorageFailure(result.reason)
        }
    }

    private fun validateCommand(command: CreateAcquisitionCharacter): NewCharacterCreationResult? = when {
        command.creationId.isBlank() || command.characterId.isBlank() || command.catalogRevision.isBlank() || command.resolutionRevision.isBlank() ->
            NewCharacterCreationResult.IdentityConflict("Creation requires explicit identity and reviewed authority")
        command.initialXP !in 0..Int.MAX_VALUE.toLong() -> NewCharacterCreationResult.Unavailable("Initial XP is outside the supported range")
        else -> null
    }

    private fun existing(command: CreateAcquisitionCharacter, loaded: LoadResult): NewCharacterCreationResult {
        if (loaded !is LoadResult.Loaded)
            return NewCharacterCreationResult.Unavailable("No matching canonical new-character opening: ${loaded::class.simpleName}", loaded)
        val state = loaded.state
        val opening = state.ledger.opening
        if (state.character.id != command.characterId || opening.provenance != OpeningProvenance.NewCharacter ||
            opening.legacyMigration != null || opening.legacyStage != RequirementNode.CreationPhase.Phase.Creating ||
            opening.migrationId != creationIdentity(command) || opening.sourceFingerprint != legacySourceFingerprint(command.canonical))
            return NewCharacterCreationResult.IdentityConflict("Character identity already has different funding/history")
        val reason = try { JsonReader(opening.reason ?: "", strict=true).read() as? JsonValue.Obj } catch (_: IllegalArgumentException) { null } catch (_: IllegalStateException) { null }
        val policy = reason?.values?.get("policy") as? JsonValue.Obj
        val source = policy?.values?.get("sourceRef") as? JsonValue.Obj
        // Exact retained source identity is checked, not compared with today's catalog or policy.
        val originalPolicy = try {
            if (policy == null || source == null) null else CreationFundingPolicy(
                policy.creationString("ruleId"), policy.creationString("xpPerAP").toLong(), RuleSourceRef(
                    source.creationString("documentId"), source.creationString("documentVersion"), source.creationString("originalText"),
                    source.creationOptionalString("sheet"), (source.values["paragraph"] as? JsonValue.Num)?.raw?.toInt(),
                    source.creationString("resolutionId"), source.creationString("resolutionRevision")))
        } catch (_: IllegalArgumentException) { null } catch (_: IllegalStateException) { null }
        if (originalPolicy?.supported != true || opening.reason != fundingReason(command, originalPolicy) ||
            opening.balance != Amount(command.initialXP, command.initialXP / originalPolicy.xpPerAP))
            return NewCharacterCreationResult.IdentityConflict("Recorded funding origin differs from the retained creation intent")
        return NewCharacterCreationResult.AlreadyCreated(state)
    }

    private suspend fun safely(block: suspend () -> NewCharacterCreationResult): NewCharacterCreationResult = try { block() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { NewCharacterCreationResult.StorageFailure(failure.message ?: "Creation storage unavailable") }
}

private fun creationIdentity(command: CreateAcquisitionCharacter): String =
    "new-character-v1:" + listOf(command.creationId, command.characterId).joinToString("") { "${it.length}:$it" }

private fun fundingReason(command: CreateAcquisitionCharacter, policy: CreationFundingPolicy): String = canonicalJson(jsonStringify(jsonObject(
    "type" to jsonString("new-character-funding-v1"), "intent" to JsonReader(command.canonical, strict=true).read(),
    "initialXP" to jsonString(command.initialXP.toString()), "initialAP" to jsonString((command.initialXP / policy.xpPerAP).toString()),
    "policy" to fundingPolicyJson(policy))))

private fun fundingPolicyJson(policy: CreationFundingPolicy): JsonValue.Obj = jsonObject(
    "ruleId" to jsonString(policy.ruleId), "xpPerAP" to jsonString(policy.xpPerAP.toString()),
    "sourceRef" to policy.sourceRef.let { s -> jsonObject("documentId" to jsonString(s.documentId),
        "documentVersion" to jsonString(s.documentVersion), "originalText" to jsonString(s.originalText),
        "sheet" to (s.sheet?.let(::jsonString) ?: JsonValue.Null), "paragraph" to (s.paragraph?.let(::jsonNumber) ?: JsonValue.Null),
        "resolutionId" to (s.resolutionId?.let(::jsonString) ?: JsonValue.Null),
        "resolutionRevision" to (s.resolutionRevision?.let(::jsonString) ?: JsonValue.Null)) })

private fun JsonValue.Obj.creationString(key: String): String =
    (values[key] as? JsonValue.Str)?.value ?: throw IllegalArgumentException("Missing funding $key")
private fun JsonValue.Obj.creationOptionalString(key: String): String? =
    if (values[key] == JsonValue.Null) null else creationString(key)
