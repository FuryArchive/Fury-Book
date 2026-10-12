package com.furybook.dubl.acquisition

import com.furybook.core.json.*
import com.furybook.dubl.model.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class ConfirmAcquisition(val operationId: String, val characterId: String, val request: AcquisitionRequest,
    val expectedRevision: Long, val previewToken: String) {
    /** The confirmed intent includes the exact quote, even after Undo or a lost reply. */
    val canonical: String get() = canonicalJson(jsonStringify(jsonObject(
        "type" to jsonString("ConfirmAcquisition"), "characterId" to jsonString(characterId),
        "request" to JsonReader(request.canonical).read(), "expectedRevision" to jsonString(expectedRevision.toString()),
        "previewToken" to jsonString(previewToken))))
}

sealed interface ApplyResult {
    data class Applied(val receipt: Receipt) : ApplyResult
    data class AlreadyApplied(val receipt: Receipt) : ApplyResult
    data class AlreadyUndone(val receipt: Receipt) : ApplyResult
    data class Stale(val newPlan: AcquisitionPlan) : ApplyResult
    data class Blocked(val issues: List<AcquisitionIssue>) : ApplyResult
    data class StorageFailure(val reason: String) : ApplyResult
    data class IdentityConflict(val reason: String) : ApplyResult
}

/** Carries the original load outcome; a preview never substitutes a new empty character. */
class AcquisitionPreviewUnavailable(val loadResult: LoadResult) : IllegalStateException("Canonical character unavailable: ${loadReason(loadResult)}")

/** Pure rules plus one durable CAS. The injected store remains the cross-service/process authority. */
class AcquisitionService(private val store: AcquisitionStore, private val catalogProvider: () -> AcquisitionCatalog,
    private val idFactory: () -> String) {
    private val lockRegistry = Mutex()
    private val characterLocks = mutableMapOf<String, Mutex>()
    private suspend fun characterLock(id: String): Mutex = lockRegistry.withLock { characterLocks.getOrPut(id) { Mutex() } }

    /** UI retains this ID across retries of the same confirmed intent. */
    fun newOperationId(): String = idFactory().also { require(it.isNotBlank()) { "Blank operation ID" } }

    suspend fun preview(request: AcquisitionRequest): AcquisitionPlan {
        val loaded = store.load(request.characterId)
        val state = (loaded as? LoadResult.Loaded)?.state ?: throw AcquisitionPreviewUnavailable(loaded)
        return plan(request, state, catalogProvider())
    }

    suspend fun apply(command: ConfirmAcquisition): ApplyResult = characterLock(command.characterId).withLock {
        try { applyLocked(command) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { ApplyResult.StorageFailure(failure.message ?: "Acquisition storage unavailable") }
    }

    private suspend fun applyLocked(command: ConfirmAcquisition): ApplyResult {
        if (command.operationId.isBlank() || command.characterId.isBlank() || command.request.characterId != command.characterId)
            return ApplyResult.IdentityConflict("Invalid operation or character scope")
        var originalProjection: String? = null
        repeat(8) {
            val loaded = store.load(command.characterId)
            val state = (loaded as? LoadResult.Loaded)?.state ?: return blocked("Canonical character unavailable: ${loadReason(loaded)}")
            if (state.character.id != command.characterId) return ApplyResult.IdentityConflict("Stored character scope differs")
            state.receipts.singleOrNull { it.operationId == command.operationId }?.let { receipt ->
                if (receipt.requestCanonical != command.canonical) return ApplyResult.IdentityConflict("Operation ID reused with different confirmation")
                return if (receipt.status == ReceiptStatus.Undone) ApplyResult.AlreadyUndone(receipt) else ApplyResult.AlreadyApplied(receipt)
            }
            val tokenCharacter = try { (JsonReader(command.previewToken, strict = true).read() as? JsonValue.Obj)
                ?.values?.get("characterId") as? JsonValue.Str } catch (_: IllegalArgumentException) { null }
            if (tokenCharacter == null || tokenCharacter.value != command.characterId)
                return ApplyResult.IdentityConflict("Preview token character scope differs")
            // One fresh catalog snapshot supplies token comparison, replay, final checks, and evidence.
            val catalog = acquisitionContext(state, catalogProvider()).catalog
            val current = plan(command.request, state, catalog)
            if (command.expectedRevision != state.revision || command.previewToken != current.previewToken)
                return ApplyResult.Stale(current)
            val projection = encodeSyncEnvelope(state)
            if (originalProjection != null && originalProjection != projection) return ApplyResult.Stale(current)
            originalProjection = projection
            if (!current.executable) return ApplyResult.Blocked(current.issues.ifEmpty {
                listOf(AcquisitionIssue(RequirementStatus.ChoiceRequired, "Resolve all preview choices before confirmation")) })
            val next = try { prepareCommit(command, state, catalog, current) }
                catch (failure: IllegalArgumentException) { return blocked(failure.message ?: "Invalid acquisition projection") }
                catch (failure: ArithmeticException) { return blocked(failure.message ?: "Acquisition arithmetic overflow") }
            if (next.issues.isNotEmpty()) return ApplyResult.Blocked(next.issues)
            val saved = next.state!!
            when (val committed = store.compareAndSet(command.characterId, state.storageRevision, saved)) {
                CommitResult.Committed -> return ApplyResult.Applied(saved.receipts.last())
                CommitResult.Conflict -> Unit // Reload; only metadata may have changed without reconfirmation.
                is CommitResult.StorageFailure -> return ApplyResult.StorageFailure(committed.reason)
            }
        }
        return ApplyResult.StorageFailure("Storage metadata remained busy; retry the same operation ID")
    }

    private data class Prepared(val state: StoredCharacter? = null, val issues: List<AcquisitionIssue> = emptyList())
    private fun prepareCommit(command: ConfirmAcquisition, state: StoredCharacter, catalog: AcquisitionCatalog,
        confirmed: AcquisitionPlan): Prepared {
        val replay = validateAcquisitionProjection(confirmed.changes, state, catalog, command.request.externalConfirmations)
        if (replay.issues.isNotEmpty()) return Prepared(issues = replay.issues)
        val normalized = normalizeProjection(state, replay.context, confirmed.changes, catalog)
        val after = acquisitionContext(normalized, catalog, command.request.externalConfirmations)
        val before = acquisitionContext(state, catalog, command.request.externalConfirmations)
        val issues = mutableListOf<AcquisitionIssue>()
        // Replay checks every step; the stored projection must match the last step of each instance.
        for (change in confirmed.changes.associateBy { it.instanceId }.values) {
            val definition = catalog.definitions.getValue(change.definition)
            val proof = evaluate(definition.requirements, after)
            if (proof.status != RequirementStatus.Satisfied) issues += AcquisitionIssue(proof.status,
                proof.reason ?: "Final requirement failed", definition.ref, proof.nodeId, definition.requirements.sourceRef)
            if (change.kind != AcquisitionKind.ATTRIBUTE) require(after.instances.single { it.instanceId == change.instanceId }.let {
                it.definition == change.definition && it.rank == change.toRank && it.parameters == change.parameters && it.abilityOptionId == change.optionId
            }) { "Normalized ownership differs from confirmed transition" }
        }
        // Existing manual violations do not become a blanket gate. A new violation of a previously
        // satisfied conjunct (including an exclusion) is forbidden, even on an already-invalid root.
        for (ref in before.instances.filter { it.rank > 0 }.map { it.definition }.distinct()) {
            val definition = catalog.definitions[ref] ?: continue
            fun check(node: RequirementNode) {
                val previous = evaluate(node, before)
                val next = evaluate(node, after)
                if (previous.status == RequirementStatus.Satisfied && next.status != RequirementStatus.Satisfied)
                    issues += AcquisitionIssue(next.status, next.reason ?: "Purchase invalidates existing requirement", ref, node.nodeId, node.sourceRef)
                if (node is RequirementNode.AllOf) node.children.forEach(::check)
            }
            check(definition.requirements)
        }
        if (issues.isNotEmpty()) return Prepared(issues = issues.distinct())
        val cost = replay.prices.fold(Amount.ZERO) { total, price -> total + price.cost }
        val remaining = balance(state.ledger) - cost
        require(cost == confirmed.cost && remaining == confirmed.remaining && replay.prices == confirmed.prices) { "Final quote differs from confirmation" }
        require(remaining.xp >= 0 && remaining.ap >= 0) { "Insufficient canonical balance" }
        val saved = appendCanonicalOperation(state, normalized, command.operationId, command.canonical,
            if (confirmed.changes.isEmpty()) LedgerEntryType.NonFinancial else LedgerEntryType.Purchase,
            Amount.ZERO - cost, RuleVersions(catalog.catalogRevision, catalog.resolutionRevision),
            encodeAcquisitionEvidence(command.request.externalConfirmations, priceEvidence(confirmed, state, normalized)),
            confirmed.changes)
        require(balance(saved.ledger) == remaining && balance(saved.ledger) - balance(state.ledger) == Amount.ZERO - cost) { "Monetary delta mismatch" }
        return Prepared(saved)
    }
}

private fun blocked(reason: String) = ApplyResult.Blocked(listOf(AcquisitionIssue(RequirementStatus.Conflict, reason)))
private fun loadReason(result: LoadResult): String = when (result) {
    is LoadResult.Missing -> "missing"
    is LoadResult.MigrationRequired -> "migration required"
    is LoadResult.Unsupported -> result.reason
    is LoadResult.Corrupt -> result.reason
    is LoadResult.Loaded -> "loaded"
}

/** Targeted canonical normalization, never the legacy full-sheet clamping/repricing path. */
private fun normalizeProjection(state: StoredCharacter, projected: AcquisitionContext,
    changes: List<AcquisitionChange>, catalog: AcquisitionCatalog): StoredCharacter {
    var character = projected.character
    val updatedSkillKeys = mutableSetOf<String>()
    for (change in changes) {
        val definition = catalog.definitions.getValue(change.definition)
        when (change.kind) {
            AcquisitionKind.ATTRIBUTE -> Unit // Replay has already set the exact base attribute.
            AcquisitionKind.MANA -> character = character.copy(magic = character.magic.copy(manaRank = change.toRank), manaEnabled = true)
            AcquisitionKind.CHI_BONUS -> character = character.copy(chiBonusRanks = change.toRank)
            AcquisitionKind.SPELL -> {
                require(catalog.definitions.values.count { it.kind == AcquisitionKind.SPELL && it.ref.entryId == change.definition.entryId } == 1) {
                    "Spell presentation cannot represent ambiguous pack identity" }
                val matches = character.magic.spells.filter { !it.custom && it.catalogId == change.definition.entryId }
                require(matches.size <= 1) { "Ambiguous learned spell presentation" }
                val old = matches.singleOrNull()
                val spell = old?.copy(learned = true) ?: KnownSpell(change.instanceId, change.definition.entryId, definition.name, learned = true)
                require(character.magic.spells.none { it.uid == spell.uid && it !== old }) { "Spell presentation identity collision" }
                character = character.copy(magic = character.magic.copy(spells = character.magic.spells.filterNot { it === old } + spell))
            }
            // These legacy containers have no pack/parameter/option identity. Only project a lossless
            // single definition, retaining the authoritative richer instances for canonical consumers.
            AcquisitionKind.DEVELOPMENT -> if (definition.repeatPolicy == RepeatPolicy.Single && change.parameters.isEmpty() && change.optionId == null &&
                catalog.definitions.keys.count { it.entryId == change.definition.entryId } == 1) {
                val old = character.development[change.definition.entryId]
                character = character.copy(development = character.development + (change.definition.entryId to
                    (old?.copy(rank = change.toRank) ?: OwnedDevelopment(change.toRank))))
            }
            AcquisitionKind.SKILL -> {
                val owned = state.instances.singleOrNull { it.instanceId == change.instanceId }
                val legacyKey = (owned?.origin as? AcquisitionOrigin.Legacy)?.ownershipKey?.removePrefix("skill:")
                val key = legacyKey?.takeIf { it in character.skills } ?: change.instanceId
                val old = character.skills[key]
                require(old == null || key in updatedSkillKeys || (owned != null &&
                    (old.definitionId == change.definition.entryId || (old.definitionId == null && key == legacyKey)))) {
                    "Skill presentation identity collision"
                }
                updatedSkillKeys += key
                character = character.copy(skills = character.skills + (key to (old?.copy(rank = change.toRank)
                    ?: CharacterSkill(key, change.definition.entryId, definition.name, rank = change.toRank))))
            }
            AcquisitionKind.SCHOOL -> Unit // Names cannot encode canonical school identity; instances are authoritative.
        }
    }
    return state.copy(character = character, instances = projected.instances)
}

private fun priceEvidence(plan: AcquisitionPlan, before: StoredCharacter, after: StoredCharacter): String {
    // The token's quote objects are produced by the shared planner and retain complete typed proof
    // trees. Persist those exact objects, plus each quote's primary source and transition instance.
    val token = JsonReader(plan.previewToken, strict = true).read() as JsonValue.Obj
    val quotes = (token.values.getValue("quotes") as JsonValue.Arr).values
    return canonicalJson(jsonStringify(jsonObject("phase" to jsonString(before.creationPhase.name),
        "undoProjection" to jsonObject("schema" to jsonString("acquisition-projection-v1"),
            "before" to JsonReader(acquisitionProjectionCanonical(before)).read(),
            "after" to JsonReader(acquisitionProjectionCanonical(after)).read()),
        "prices" to JsonValue.Arr(plan.prices.mapIndexed { index, price -> jsonObject(
            "instanceId" to jsonString(plan.changes[index].instanceId), "sourceRef" to sourceJson(price.sourceRef),
            "quote" to quotes[index]) }))))
}


/** Finite exact projection: deliberately excludes history, receipts, extras and all storage/sync data.
 * Outbox is omitted before codec validation because the staged projection is not a committed tip yet.
 */
fun acquisitionProjectionCanonical(state: StoredCharacter): String {
    val envelope = JsonReader(encodeEnvelope(state.copy(outbox = emptyList()))).read() as JsonValue.Obj
    return canonicalJson(jsonStringify(jsonObject("characterSnapshot" to envelope.values.getValue("characterSnapshot"),
        "instances" to envelope.values.getValue("instances"))))
}

data class AcquisitionUndoEvidence(val beforeCanonical: String, val afterCanonical: String)

/** Structural evidence reader, not authorization to restore. Undo must validate the original operation,
 * its changes and eligible current tip, then validate the decoded projection through the envelope codec.
 */
fun decodeAcquisitionUndoEvidence(evidenceCanonical: String): AcquisitionUndoEvidence? = try {
    require(canonicalJson(evidenceCanonical) == evidenceCanonical)
    val root = JsonReader(evidenceCanonical, strict = true).read() as JsonValue.Obj
    require(root.values["schema"] == jsonString("acquisition-evidence-v1"))
    val evidence = root.values["undoProjection"] as JsonValue.Obj
    require(evidence.values.keys == setOf("schema", "before", "after"))
    require(evidence.values["schema"] == jsonString("acquisition-projection-v1"))
    fun projection(key: String): String {
        val value = evidence.values.getValue(key) as JsonValue.Obj
        require(value.values.keys == setOf("characterSnapshot", "instances"))
        require(value.values["characterSnapshot"] is JsonValue.Obj && value.values["instances"] is JsonValue.Arr)
        return canonicalJson(jsonStringify(value))
    }
    AcquisitionUndoEvidence(projection("before"), projection("after"))
} catch (_: Exception) { null }
