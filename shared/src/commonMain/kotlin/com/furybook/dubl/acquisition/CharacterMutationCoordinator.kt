package com.furybook.dubl.acquisition

import com.furybook.core.json.*
import com.furybook.dubl.model.*
import com.furybook.dubl.application.DublApplication
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Captured exact values, not a callback retained across retries. Null collections mean preserve. */
class CharacterPatch(character: DublCharacter, instances: List<AcquisitionInstance>? = null, extras: CharacterSheetExtras? = null) {
    internal val canonical: String
    internal val replacesInstances = instances != null
    internal val replacesExtras = extras != null
    init {
        val temporary = projectionContainer(character, instances.orEmpty(), extras ?: CharacterSheetExtras())
        canonical = mutationProjectionCanonical(temporary)
    }
    internal fun apply(state: StoredCharacter): StoredCharacter {
        val decoded = decodeMutationProjection(state, canonical)
        require(decoded.character.id == state.character.id) { "Patch character scope differs" }
        return state.copy(character=decoded.character, instances=if(replacesInstances) decoded.instances else state.instances,
            extras=if(replacesExtras) decoded.extras else state.extras,
            creationPhase=decoded.creationPhase)
    }
    internal fun json() = jsonObject("projection" to JsonReader(canonical).read(),
        "replacesInstances" to jsonBoolean(replacesInstances), "replacesExtras" to jsonBoolean(replacesExtras))
}

sealed interface MutationPayload {
    data class Grant(val delta: Amount) : MutationPayload
    data class ManualCorrection(val characterPatch: CharacterPatch, val delta: Amount) : MutationPayload
    data object CompleteCreation : MutationPayload
    class ConfirmLegacyParameter(val instanceId: String, parameters: Map<String,String>,
        val resolution: LegacyOwnershipResolution? = null) : MutationPayload {
        val parameters: Map<String,String> = AcquisitionChange(AcquisitionKind.DEVELOPMENT,DefinitionRef("capture","parameters"),"capture",0,1,parameters,null).parameters
    }
    data class NonFinancial(val characterPatch: CharacterPatch) : MutationPayload
}

/** An unresolved holding must retain its original rank and deterministic source-scoped ID. */
data class LegacyOwnershipResolution(val ownershipKey: String, val definition: DefinitionRef, val rank: Int)
data class CanonicalMutation(val operationId: String, val characterId: String, val expectedRevision: Long,
    val payload: MutationPayload, val reason: String? = null) {
    val canonical: String get() = canonicalJson(jsonStringify(jsonObject("type" to jsonString("CanonicalMutation"),
        "characterId" to jsonString(characterId), "expectedRevision" to jsonString(expectedRevision.toString()),
        "reason" to (reason?.let(::jsonString) ?: JsonValue.Null), "payload" to when(val p=payload) {
            is MutationPayload.Grant -> jsonObject("kind" to jsonString("Grant"),"delta" to JsonReader(p.delta.toJson()).read())
            is MutationPayload.ManualCorrection -> jsonObject("kind" to jsonString("ManualCorrection"),"patch" to p.characterPatch.json(),"delta" to JsonReader(p.delta.toJson()).read())
            MutationPayload.CompleteCreation -> jsonObject("kind" to jsonString("CompleteCreation"))
            is MutationPayload.NonFinancial -> jsonObject("kind" to jsonString("NonFinancial"),"patch" to p.characterPatch.json())
            is MutationPayload.ConfirmLegacyParameter -> jsonObject("kind" to jsonString("ConfirmLegacyParameter"),
                "instanceId" to jsonString(p.instanceId),"parameters" to JsonValue.Obj(p.parameters.mapValues { jsonString(it.value) }),
                "resolution" to (p.resolution?.let { jsonObject("ownershipKey" to jsonString(it.ownershipKey),"packId" to jsonString(it.definition.packId),
                    "entryId" to jsonString(it.definition.entryId),"rank" to jsonNumber(it.rank)) } ?: JsonValue.Null))
        })))
}

sealed interface MutationResult {
    data class Applied(val receipt: Receipt): MutationResult
    data class AlreadyApplied(val receipt: Receipt): MutationResult
    data class AlreadyUndone(val receipt: Receipt): MutationResult
    data class Stale(val currentRevision: Long): MutationResult
    data class Blocked(val reason: String): MutationResult
    data class StorageFailure(val reason: String): MutationResult
    data class IdentityConflict(val reason: String): MutationResult
}

/** Canonical non-purchase boundary. CAS is authoritative across services/processes. */
class CharacterMutationCoordinator(private val store: AcquisitionStore, private val catalogProvider: () -> AcquisitionCatalog,
    private val idFactory: () -> String) {
    private val locks = MutationLocks()
    suspend fun mutate(command: CanonicalMutation): MutationResult = locks.forCharacter(command.characterId).withLock {
        try { mutateLocked(command) } catch(cancelled: CancellationException) { throw cancelled }
        catch(failure: Exception) { MutationResult.StorageFailure(failure.message ?: "Mutation storage unavailable") }
    }
    /** Capture once and retain the returned command, including generated IDs, for all commit retries.
     * The callback runs on exact detached state and cannot reach a live session/store.
     * Unavailable loads retain their exact outcome; a stale stage throws before running the callback.
     */
    suspend fun prepareNonFinancial(operationId: String, characterId: String, expectedRevision: Long,
        edit: DublApplication.() -> Unit): CanonicalMutation {
        val loaded = store.load(characterId)
        val state = (loaded as? LoadResult.Loaded)?.state ?: throw AcquisitionPreviewUnavailable(loaded)
        require(state.revision == expectedRevision) { "Canonical revision changed before staging" }
        val detached = DublApplication.detached(state.character,state.extras,idFactory)
        detached.edit()
        require(detached.snapshot.characters.size == 1 && detached.active.id == characterId) { "Staging cannot create/delete/select a character" }
        return CanonicalMutation(operationId,characterId,expectedRevision,
            MutationPayload.NonFinancial(CharacterPatch(detached.active,extras=detached.activeExtras)))
    }
    /** One-shot convenience. For uncertain commits/retries use prepareNonFinancial + mutate and
     * retain that command; rerunning a callback is a new stage, not a durable identity mechanism.
     */
    suspend fun stageNonFinancial(operationId: String, characterId: String, expectedRevision: Long,
        edit: DublApplication.() -> Unit): MutationResult = try {
        mutate(prepareNonFinancial(operationId,characterId,expectedRevision,edit))
    } catch(cancelled: CancellationException) { throw cancelled }
      catch(failure: IllegalArgumentException) { MutationResult.Blocked(failure.message ?: "Invalid staged edit") }
      catch(failure: AcquisitionPreviewUnavailable) { MutationResult.Blocked("Canonical character unavailable") }
      catch(failure: Exception) { MutationResult.StorageFailure(failure.message ?: "Staging failed") }
    private suspend fun mutateLocked(command: CanonicalMutation): MutationResult {
        if(command.operationId.isBlank() || command.characterId.isBlank()) return MutationResult.IdentityConflict("Blank operation or character ID")
        var original: String? = null
        repeat(8) {
            val state=(store.load(command.characterId) as? LoadResult.Loaded)?.state ?: return MutationResult.Blocked("Canonical character unavailable")
            if(state.character.id != command.characterId) return MutationResult.IdentityConflict("Stored scope differs")
            state.receipts.singleOrNull { it.operationId==command.operationId }?.let {
                if(it.requestCanonical!=command.canonical) return MutationResult.IdentityConflict("Operation ID reused with different payload")
                return if(it.status==ReceiptStatus.Undone) MutationResult.AlreadyUndone(it) else MutationResult.AlreadyApplied(it)
            }
            if(state.revision != command.expectedRevision) return MutationResult.Stale(state.revision)
            val projection=encodeSyncEnvelope(state)
            if(original!=null && original!=projection) return MutationResult.Stale(state.revision)
            original=projection
            val next=try { prepare(command,state,acquisitionContext(state,catalogProvider()).catalog) }
                catch(failure: IllegalArgumentException) { return MutationResult.Blocked(failure.message ?: "Invalid mutation") }
                catch(failure: ArithmeticException) { return MutationResult.Blocked(failure.message ?: "Overflow") }
            when(val result=store.compareAndSet(command.characterId,state.storageRevision,next)) {
                CommitResult.Committed -> return MutationResult.Applied(next.receipts.last())
                CommitResult.Conflict -> Unit
                is CommitResult.StorageFailure -> return MutationResult.StorageFailure(result.reason)
            }
        }
        return MutationResult.StorageFailure("Storage metadata remained busy; retry the same operation ID")
    }
    private fun prepare(command: CanonicalMutation, state: StoredCharacter, catalog: AcquisitionCatalog): StoredCharacter {
        var delta=Amount.ZERO
        val type: LedgerEntryType
        val after=when(val p=command.payload) {
            is MutationPayload.Grant -> { require(!command.reason.isNullOrBlank()) { "Grant requires a reason" };delta=p.delta;type=LedgerEntryType.Grant;state }
            is MutationPayload.ManualCorrection -> {
                require(!command.reason.isNullOrBlank()) { "Manual correction requires explicit amount and reason" }
                delta=p.delta;type=LedgerEntryType.ManualCorrection
                p.characterPatch.apply(state).also { require(it.creationPhase==state.creationPhase) { "Manual reopening cannot erase completion or restore creation-only access" }
                    validateManualInstances(state,it,catalog) }
            }
            is MutationPayload.NonFinancial -> { type=LedgerEntryType.NonFinancial;p.characterPatch.apply(state).also {
                require(financialProjection(state)==financialProjection(it)) { "Nonfinancial edit changes acquisition or financial fields" } } }
            MutationPayload.CompleteCreation -> {
                type=if(state.character.creationComplete) LedgerEntryType.NonFinancial else LedgerEntryType.CompleteCreation
                if(state.character.creationComplete) state else {
                    val detached=DublApplication.detached(state.character,state.extras,idFactory)
                    detached.character.completeCreation()
                    state.copy(character=detached.active,creationPhase=RequirementNode.CreationPhase.Phase.Finalized)
                }
            }
            is MutationPayload.ConfirmLegacyParameter -> { require(!command.reason.isNullOrBlank()) { "Legacy confirmation requires reason" }
                type=LedgerEntryType.ConfirmLegacyParameter;confirmLegacy(state,p,catalog) }
        }
        val evidence=canonicalJson(jsonStringify(jsonObject("schema" to jsonString("mutation-evidence-v1"),
            "command" to JsonReader(command.canonical).read(),"before" to JsonReader(mutationProjectionCanonical(state)).read(),
            "after" to JsonReader(mutationProjectionCanonical(after)).read())))
        return appendCanonicalOperation(state,after,command.operationId,command.canonical,type,delta,
            RuleVersions(catalog.catalogRevision,catalog.resolutionRevision),evidence)
    }
}

internal class MutationLocks {
    private val registry=Mutex(); private val locks=mutableMapOf<String,Mutex>()
    suspend fun forCharacter(id:String):Mutex = registry.withLock { locks.getOrPut(id) { Mutex() } }
}
internal fun projectionContainer(character:DublCharacter,instances:List<AcquisitionInstance>,extras:CharacterSheetExtras) = StoredCharacter(
    character,extras,instances,Ledger(Opening("projection","projection",Amount.ZERO,
        if(character.creationComplete) RequirementNode.CreationPhase.Phase.Finalized else RequirementNode.CreationPhase.Phase.Creating,OpeningProvenance.NewCharacter),emptyList()),
    emptyList(),emptyList(),SyncMetadata(0),0,0,if(character.creationComplete) RequirementNode.CreationPhase.Phase.Finalized else RequirementNode.CreationPhase.Phase.Creating)
internal fun mutationProjectionCanonical(state:StoredCharacter):String {
    val root=JsonReader(encodeProjectionEnvelope(state.copy(outbox=emptyList()))).read() as JsonValue.Obj
    return canonicalJson(jsonStringify(JsonValue.Obj(root.values.filterKeys { it in setOf("characterSnapshot","instances","extras","creationPhase") })))
}
/** Decode finite projections through the exact envelope codec; no normalization or history restoration. */
internal fun decodeMutationProjection(state:StoredCharacter,raw:String):StoredCharacter {
    val projection=JsonReader(raw,strict=true).read() as JsonValue.Obj
    require(projection.values.keys==setOf("characterSnapshot","instances","extras","creationPhase")) { "Invalid mutation projection fields" }
    val root=JsonReader(encodeEnvelope(state.copy(outbox=emptyList()))).read() as JsonValue.Obj
    return decodeProjectionEnvelope(canonicalJson(jsonStringify(JsonValue.Obj(root.values+projection.values))))
}
internal fun appendCanonicalOperation(before:StoredCharacter,after:StoredCharacter,id:String,request:String,type:LedgerEntryType,
    delta:Amount,versions:RuleVersions,evidence:String,changes:List<AcquisitionChange> = emptyList(),reverses:String?=null):StoredCharacter {
    require(before.revision<Long.MAX_VALUE && before.storageRevision<Long.MAX_VALUE) { "Revision overflow" }
    val entry=LedgerEntry(id,type,changes,delta,versions,reverses,before.creationPhase,evidence)
    val remaining=balance(before.ledger)+delta
    val receipt=Receipt(id,request,SavedAcquisitionResult(Amount.ZERO-delta,remaining,changes),ReceiptStatus.Applied,before.revision,before.revision+1)
    val receipts=before.receipts.map { if(it.operationId==reverses) it.copy(status=ReceiptStatus.Undone) else it }+receipt
    val saved=after.copy(ledger=before.ledger.copy(entries=before.ledger.entries+entry),receipts=receipts,
        outbox=before.outbox,syncMetadata=before.syncMetadata,revision=before.revision+1,storageRevision=before.storageRevision+1)
    val queued=saved.copy(outbox=saved.outbox+OutboxItem(id,before.syncMetadata.serverRevision,encodeSyncEnvelope(saved)))
    encodeEnvelope(queued)
    return queued
}

/** Conservative financial projection: descriptive/check edits, current resources and equipment may change.
 * Skill modifier/formulaNote are existing check edits, unused by acquisition evaluation/pricing;
 * skill identity, attributes, categories, override rules and ranks are retained in this comparison. */
internal fun financialProjection(state:StoredCharacter):String {
    val c=state.character
    val stripped=c.copy(name="",concept="",hpCurrent=0,enduranceCurrent=0,manaCurrent=0,chiCurrent=0,
        healthMaximumOverride=null,enduranceMaximumOverride=null,manaMaximumOverride=null,customResources=emptyList(),
        hiddenSkillIds=emptySet(),disabledSkillEffectIds=emptySet(),gear=CharacterGear(),
        skills=c.skills.mapValues { (_,s)-> s.copy(name="",description="",modifier=0,formulaNote="") })
    return acquisitionProjectionCanonical(state.copy(character=stripped,outbox=emptyList()))
}
private fun validateManualInstances(before:StoredCharacter,after:StoredCharacter,catalog:AcquisitionCatalog) {
    // New specializations belong to strict acquisition. Existing holdings can be explicitly corrected.
    after.instances.forEach { instance ->
        val old=before.instances.singleOrNull { it.instanceId==instance.instanceId }
        require(old!=null && old.definition==instance.definition && old.origin==instance.origin) { "New or remapped holdings require acquisition/legacy confirmation" }
        require(catalog.definitions[instance.definition]!=null || old==instance) { "Unknown holding cannot be silently retrained" }
    }
}
private fun confirmLegacy(state:StoredCharacter,p:MutationPayload.ConfirmLegacyParameter,catalog:AcquisitionCatalog):StoredCharacter {
    val provenance=state.ledger.opening.legacyMigration ?: throw IllegalArgumentException("No original legacy provenance")
    val source=provenance.origin
    val old=state.instances.singleOrNull { it.instanceId==p.instanceId }
    val instance=if(p.resolution!=null) {
        val resolution=p.resolution
        val record=unresolvedLegacyOwnership(state,catalog).singleOrNull { it.ownershipKey==resolution.ownershipKey }
            ?: throw IllegalArgumentException("Holding is not unresolved")
        val definition=catalog.definitions[resolution.definition] ?: throw IllegalArgumentException("Unknown resolution definition")
        require(record.rank==resolution.rank && record.kind==definition.kind && (record.candidateEntryId==null || record.candidateEntryId==resolution.definition.entryId)) { "Resolution must match original kind, identity and rank" }
        val origin=AcquisitionOrigin.Legacy(source.characterId,source.schema.toString(),source.sourceRevision,source.sourceFingerprint,record.ownershipKey)
        require(p.instanceId==legacyInstanceId(resolution.definition,origin)) { "Resolution instance ID differs from original scope" }
        require(old==null) { "Resolution cannot replace a holding" }
        AcquisitionInstance(p.instanceId,resolution.definition,record.rank,p.parameters,null,origin)
    } else {
        require(old!=null && old.origin is AcquisitionOrigin.Legacy) { "Select an existing legacy instance" }
        require(old.parameters.all { p.parameters[it.key]==it.value }) { "Confirmation cannot rewrite known legacy parameters" }
        old.copy(parameters=p.parameters)
    }
    val definition=catalog.definitions[instance.definition] ?: throw IllegalArgumentException("Unknown legacy definition")
    require(instance.origin==AcquisitionOrigin.Legacy(source.characterId,source.schema.toString(),source.sourceRevision,source.sourceFingerprint,(instance.origin as AcquisitionOrigin.Legacy).ownershipKey)) { "Legacy scope mismatch" }
    require(instance.instanceId==legacyInstanceId(instance.definition,instance.origin)) { "Legacy identity mismatch" }
    require(instance.parameters.keys==definition.parameterDefinitions.map { it.key }.toSet()) { "Missing or unknown parameter" }
    definition.parameterDefinitions.forEach { parameter ->
        val value=instance.parameters.getValue(parameter.key)
        when(val domain=parameter.domain) {
            is ParameterDomain.StableIds -> require(value in domain.valueIds) { "Unknown parameter value" }
            ParameterDomain.UserDefinedIds -> require(value.isNotBlank())
            is ParameterDomain.Unresolved -> throw IllegalArgumentException(domain.reason)
        }
    }
    if(definition.repeatPolicy is RepeatPolicy.DistinctParameter && definition.repeatPolicy.requireUnique)
        require(state.instances.none { it.instanceId!=instance.instanceId && it.definition==instance.definition && it.parameters==instance.parameters }) { "Duplicate specialization" }
    return state.copy(instances=state.instances.filterNot { it.instanceId==instance.instanceId }+instance)
}
