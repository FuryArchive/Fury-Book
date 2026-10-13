package com.furybook.android.state

import com.furybook.android.data.AndroidAcquisitionStore
import com.furybook.android.data.AndroidLegacyWriteGate
import com.furybook.dubl.acquisition.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.furybook.dubl.application.DublApplication
import com.furybook.dubl.data.CharacterStore
import com.furybook.dubl.data.CharacterExtrasStore
import com.furybook.dubl.application.CharacterTransferImportResult
import com.furybook.dubl.model.AppSnapshot
import com.furybook.dubl.model.AttributeId
import com.furybook.dubl.model.CharacterConditionId
import com.furybook.dubl.model.CharacterSheetExtras
import com.furybook.dubl.model.CharacterSheetResourceId
import com.furybook.dubl.model.DevelopmentEntry
import com.furybook.dubl.model.DevelopmentCatalog
import com.furybook.dubl.model.DevelopmentAcquisitionRequest
import com.furybook.dubl.model.DevelopmentAcquisitionResult
import com.furybook.dubl.model.DublCharacter
import com.furybook.dubl.model.GearCatalogEntry
import com.furybook.dubl.model.GearItem
import com.furybook.dubl.model.KnownSpell
import com.furybook.dubl.model.SheetGroup
import com.furybook.dubl.model.SpellCatalogEntry
import com.furybook.dubl.model.UntrainedRule
import com.furybook.dubl.model.withoutRuntimeDevelopmentEffects
import java.util.UUID

/** Android observable adapter over the shared application boundary. */
class CharacterController internal constructor(
    private val repository: CharacterStore,
    private val extrasRepository: CharacterExtrasStore,
    private val acquisitionStore: AndroidAcquisitionStore? = null,
    initialCanonicalStates: Map<String, StoredCharacter> = emptyMap(),
    private val legacyGate: AndroidLegacyWriteGate? = null,
    private val catalogProvider: () -> AcquisitionCatalog = { AcquisitionCatalog(emptyMap(), "unavailable", "unavailable") },
    private val ownerScope: () -> Pair<String?, Long> = { null to 0L },
    private val capabilities: AcquisitionCapabilities = AndroidAcquisitionRuntimeGate.production,
) {
    private val application = DublApplication(
        characterStore = repository,
        extrasStore = extrasRepository,
        idFactory = { UUID.randomUUID().toString() },
        acquisitionService = acquisitionStore?.let { AcquisitionService(it, catalogProvider) { UUID.randomUUID().toString() } },
        mutationCoordinator = acquisitionStore?.let { CharacterMutationCoordinator(it,catalogProvider) { UUID.randomUUID().toString() } },
        acquisitionUndo = acquisitionStore?.let(::AcquisitionUndo),
    )

    var snapshot: AppSnapshot by mutableStateOf(application.snapshot)
        private set
    var extras: CharacterSheetExtras by mutableStateOf(application.activeExtras)
        private set

    private var suppressedDevelopmentIds: Set<String> by mutableStateOf(emptySet())
    private var suppressChiRuntime: Boolean by mutableStateOf(false)

    val active: DublCharacter
        get() = snapshot.activeCharacter.withoutRuntimeDevelopmentEffects(
            suppressedEntryIds = suppressedDevelopmentIds,
            suppressChiResource = suppressChiRuntime,
        )

    fun setRuntimeContentSuppression(developmentIds: Set<String>, suppressChiResource: Boolean) {
        suppressedDevelopmentIds = developmentIds
        suppressChiRuntime = suppressChiResource
    }

    private fun <T> sync(block: () -> T): T {
        check(!disposed && canonicalStates.isEmpty() && legacyGate?.readOnly!=true) { "Canonical acquisition/recovery is read-only; legacy mutation is blocked" }
        val result = if(legacyGate!=null) legacyGate.legacyWrite { block() } else block()
        snapshot = application.snapshot
        extras = application.activeExtras
        return result
    }

    fun setProfile(name: String, concept: String, experience: Int, size: Int, legs: Int, manaEnabled: Boolean) =
        sync { application.character.setProfile(name, concept, experience, size, legs, manaEnabled) }
    fun setIdentity(name: String, concept: String, size: Int, legs: Int, manaEnabled: Boolean) =
        sync { application.character.setIdentity(name, concept, size, legs, manaEnabled) }
    fun setName(name: String) = sync { application.character.setName(name) }
    fun setSize(size: Int) = sync { application.character.setSize(size) }
    fun setLegs(legs: Int) = sync { application.character.setLegs(legs) }
    fun changeAttribute(id: AttributeId, delta: Int) = sync { application.character.changeAttribute(id, delta) }
    fun setExperience(total: Int) = sync { application.character.setExperience(total) }
    fun setCreationExperience(value: Int) = sync { application.character.setCreationExperience(value) }
    fun setXpAdjustment(value: Int) = sync { application.character.setXpAdjustment(value) }
    fun setAbilityPointsOverride(value: Int?) = sync { application.character.setAbilityPointsOverride(value) }
    fun completeCreation() = sync { application.character.completeCreation() }
    fun reopenCreation() = sync { application.character.reopenCreation() }
    fun changeHp(delta: Int) = sync { application.character.changeHp(delta) }
    fun changeEndurance(delta: Int) = sync { application.character.changeEndurance(delta) }
    fun changeMana(delta: Int) = sync { application.magic.changeMana(delta) }
    fun changeChi(delta: Int) = sync { application.development.changeChi(delta) }
    fun changeResource(resource: CharacterSheetResourceId, delta: Int) = when (resource) {
        CharacterSheetResourceId.HEALTH -> changeHp(delta)
        CharacterSheetResourceId.ENDURANCE -> changeEndurance(delta)
        CharacterSheetResourceId.MANA -> changeMana(delta)
        CharacterSheetResourceId.CHI -> changeChi(delta)
    }
    fun setChiEnabled(enabled: Boolean) = sync { application.development.setChiEnabled(enabled) }
    fun setChiBonusRanks(rank: Int) = sync { application.development.setChiBonusRanks(rank) }
    fun restoreChi() = sync { application.development.restoreChi() }
    fun restoreMountedResource(resource: CharacterSheetResourceId) = when (resource) {
        CharacterSheetResourceId.CHI -> restoreChi()
        else -> Unit
    }
    fun setHealthMaximumOverride(value: Int?) = sync { application.character.setHealthMaximumOverride(value) }
    fun setEnduranceMaximumOverride(value: Int?) = sync { application.character.setEnduranceMaximumOverride(value) }
    fun setManaMaximumOverride(value: Int?) = sync { application.character.setManaMaximumOverride(value) }

    fun addCustomResource(name: String, maximum: Int, current: Int = maximum): String? =
        sync { application.character.addCustomResource(name, maximum, current) }
    fun updateCustomResource(uid: String, name: String, current: Int, maximum: Int) =
        sync { application.character.updateCustomResource(uid, name, current, maximum) }
    fun changeCustomResource(uid: String, delta: Int) = sync { application.character.changeCustomResource(uid, delta) }
    fun removeCustomResource(uid: String) = sync { application.character.removeCustomResource(uid) }

    fun changeSkillRank(skillId: String, delta: Int) = sync { application.skills.changeRank(skillId, delta) }
    fun setSkillAttributes(skillId: String, attributes: List<AttributeId>) = sync { application.skills.setAttributes(skillId, attributes) }
    fun setSkillModifier(skillId: String, modifier: Int) = sync { application.skills.setModifier(skillId, modifier) }
    fun setSkillFormulaNote(skillId: String, note: String) = sync { application.skills.setFormulaNote(skillId, note) }
    fun setSkillNameOverride(skillId: String, name: String) = sync { application.skills.setNameOverride(skillId, name) }
    fun setSkillDescriptionOverride(skillId: String, description: String) = sync { application.skills.setDescriptionOverride(skillId, description) }
    fun setSkillCategoryOverride(skillId: String, category: com.furybook.dubl.model.SkillCategory?) = sync { application.skills.setCategoryOverride(skillId, category) }
    fun setSkillUntrainedOverride(skillId: String, rule: UntrainedRule?) = sync { application.skills.setUntrainedOverride(skillId, rule) }
    fun setSkillAutoOverrides(skillId: String, auto6: String?, auto12: String?) = sync { application.skills.setAutoOverrides(skillId, auto6, auto12) }
    fun resetSkillDefinitionOverrides(skillId: String) = sync { application.skills.resetDefinitionOverrides(skillId) }
    fun hideSkill(skillId: String) = sync { application.skills.hide(skillId) }
    fun restoreSkill(skillId: String) = sync { application.skills.restore(skillId) }
    fun setSkillEffectEnabled(effectId: String, enabled: Boolean) = sync { application.skills.setEffectEnabled(effectId, enabled) }
    fun restoreAllSkills() = sync { application.skills.restoreAll() }
    fun addSpecializedSkill(templateId: String, specialization: String): String? = sync { application.skills.addSpecialized(templateId, specialization) }
    fun addCustomSkill(name: String, description: String, attributes: List<AttributeId>, untrained: UntrainedRule): String? =
        sync { application.skills.addCustom(name, description, attributes, untrained) }
    fun deleteDynamicSkill(skillId: String) = sync { application.skills.deleteDynamic(skillId) }
    fun setPreferredSkillAttribute(skillId: String, attribute: AttributeId?) = sync { application.skills.setPreferredAttribute(skillId, attribute) }

    fun setDevelopmentRank(entryId: String, rank: Int, optionIndex: Int = 0) = sync { application.development.setRank(entryId, rank, optionIndex) }
    fun acquireDevelopment(catalog: DevelopmentCatalog, request: DevelopmentAcquisitionRequest): DevelopmentAcquisitionResult = sync { application.development.acquire(catalog, request) }
    fun setDevelopmentOverride(entry: DevelopmentEntry) = sync { application.development.setOverride(entry) }
    fun resetDevelopmentOverride(entryId: String) = sync { application.development.resetOverride(entryId) }
    fun addCustomDevelopment(entry: DevelopmentEntry): String? = sync { application.development.addCustom(entry) }
    fun updateCustomDevelopment(entry: DevelopmentEntry): Boolean = sync { application.development.updateCustom(entry) }
    fun removeCustomDevelopment(entryId: String) = sync { application.development.removeCustom(entryId) }

    fun setMagicManaRank(rank: Int) = sync { application.magic.setManaRank(rank) }
    @Deprecated("0.3.1 uses per-school magic power")
    fun setMagicPower(power: Int) = sync { application.magic.setLegacyPower(power) }
    fun setMagicSchoolPower(name: String, power: Int) = sync { application.magic.setSchoolPower(name, power) }
    fun addMagicSchool(name: String, rank: Int, note: String): Boolean = sync { application.magic.addSchool(name, rank, note) }
    fun updateMagicSchool(index: Int, name: String, rank: Int, note: String): Boolean = sync { application.magic.updateSchool(index, name, rank, note) }
    fun removeMagicSchool(index: Int) = sync { application.magic.removeSchool(index) }
    fun addCatalogSpell(entry: SpellCatalogEntry): Boolean = sync { application.magic.addCatalogSpell(entry) }
    fun addCustomSpell(spell: KnownSpell): String = sync { application.magic.addCustomSpell(spell) }
    fun updateSpell(spell: KnownSpell) = sync { application.magic.updateSpell(spell) }
    fun setSpellLearned(uid: String, learned: Boolean) = sync { application.magic.setSpellLearned(uid, learned) }
    fun removeSpell(uid: String) = sync { application.magic.removeSpell(uid) }

    fun setGearLoadAutomatic(enabled: Boolean) = sync { application.equipment.setLoadAutomatic(enabled) }
    fun setGearManualLoad(value: Double) = sync { application.equipment.setManualLoad(value) }
    fun syncCatalogGearLoads(entries: List<GearCatalogEntry>) = sync { application.equipment.syncCatalogLoads(entries) }
    fun addCatalogGear(entry: GearCatalogEntry): String = sync { application.equipment.addCatalog(entry) }
    fun addCustomGear(item: GearItem): String = sync { application.equipment.addCustom(item) }
    fun updateGearItem(item: GearItem) = sync { application.equipment.updateItem(item) }
    fun setGearItemCarried(uid: String, carried: Boolean) = sync { application.equipment.setItemCarried(uid, carried) }
    fun setGearItemQuantity(uid: String, quantity: Int) = sync { application.equipment.setItemQuantity(uid, quantity) }
    fun removeGearItem(uid: String) = sync { application.equipment.removeItem(uid) }

    fun setPortrait(uri: String?) = sync { application.sheet.setPortrait(uri) }
    fun toggleCondition(condition: CharacterConditionId) = sync { application.sheet.toggleCondition(condition) }
    fun setConditions(conditions: Set<CharacterConditionId>) = sync { application.sheet.setConditions(conditions) }
    fun setResourceHidden(resource: CharacterSheetResourceId, hidden: Boolean) = sync { application.sheet.setResourceHidden(resource, hidden) }
    fun setSkillGroups(groups: List<SheetGroup>) = sync { application.sheet.setSkillGroups(groups) }
    fun setDevelopmentGroups(groups: List<SheetGroup>) = sync { application.sheet.setDevelopmentGroups(groups) }
    fun setConditionOverride(condition: CharacterConditionId, title: String?, description: String?) = sync { application.sheet.setConditionOverride(condition, title, description) }
    fun resetConditionOverride(condition: CharacterConditionId) = sync { application.sheet.resetConditionOverride(condition) }
    fun addCustomCondition(title: String, description: String = "", active: Boolean = false): String? = sync { application.sheet.addCustomCondition(title, description, active) }
    fun updateCustomCondition(id: String, title: String, description: String, active: Boolean? = null) = sync { application.sheet.updateCustomCondition(id, title, description, active) }
    fun setCustomConditionActive(id: String, active: Boolean) = sync { application.sheet.setCustomConditionActive(id, active) }
    fun removeCustomCondition(id: String) = sync { application.sheet.removeCustomCondition(id) }

    fun exportActiveCharacter(): String = application.transfer.exportActive()
    fun importCharacter(raw: String): CharacterTransferImportResult = sync { application.transfer.importCharacter(raw) }

    fun createCharacter() = sync { application.character.createCharacter() }
    fun selectCharacter(id: String) {
        selectionGeneration++
        pendingLegacyMigration=null;preparedLegacyMigration=null;preparedLegacyScope=null
        if(canonicalStates.isNotEmpty() || legacyGate?.readOnly==true) {
            require(snapshot.characters.any { it.id==id })
            snapshot=snapshot.copy(activeCharacterId=id)
            extras=canonicalStates[id]?.extras ?: extrasRepository.load(id)
        } else sync { application.character.selectCharacter(id) }
    }
    fun deleteActive() = sync { application.character.deleteActive() }
    fun undoLast(): Boolean = sync { application.undoLast() }
    private var canonicalStates = initialCanonicalStates.toMap()
    private var controllerGeneration=0L
    private var selectionGeneration=0L
    private var disposed=false
    var storageError:String? by mutableStateOf(null)
        private set
    var pendingLegacyMigration:PendingMigration? by mutableStateOf(null)
        private set
    private var preparedLegacyMigration:StoredCharacter?=null
    private var preparedLegacyScope:AndroidCommandScope?=null
    private var declaredComposition:List<String> = emptyList()
    private val queue=AndroidAcquisitionQueue(::currentScope)
    private fun currentScope(scope:AndroidCommandScope):Boolean {
        val owner=ownerScope()
        return !disposed && scope.storageIdentity==acquisitionStore?.storageIdentity && scope.characterId==snapshot.activeCharacterId &&
            scope.controllerGeneration==controllerGeneration && scope.selectionGeneration==selectionGeneration && scope.ownerId==owner.first && scope.accountGeneration==owner.second
    }
    private fun commandScope(id:String,operationId:String):AndroidCommandScope {
        val owner=ownerScope()
        return AndroidCommandScope(checkNotNull(acquisitionStore).storageIdentity,id,owner.first,controllerGeneration,selectionGeneration,owner.second,operationId,canonicalStates[id]?.storageRevision ?: -1L)
    }
    fun dispose() { disposed=true;controllerGeneration++;pendingLegacyMigration=null;preparedLegacyMigration=null;preparedLegacyScope=null }
    fun declareLegacyComposition(ids:List<String>) { declaredComposition=ids.toList();selectionGeneration++;pendingLegacyMigration=null;preparedLegacyMigration=null;preparedLegacyScope=null }
    suspend fun prepareLocalMigration(ids:List<String>,oldCatalog:LegacyCatalogSnapshot?):MigrationResult {
        val scope=withContext(Dispatchers.Main.immediate) { declareLegacyComposition(ids);commandScope(snapshot.activeCharacterId,"migration-prepare") }
        val composition=ids.toList()
        val result=withContext(Dispatchers.IO) { checkNotNull(acquisitionStore).prepareMigration(scope.characterId,composition,oldCatalog) }
        withContext(Dispatchers.Main.immediate) {
            if(!currentScope(scope)) throw StaleAndroidCommandScope()
            preparedLegacyScope=scope
            pendingLegacyMigration=(result as? MigrationResult.NeedsBalanceConfirmation)?.pending
            preparedLegacyMigration=(result as? MigrationResult.Ready)?.state
            storageError=when(result) { is MigrationResult.NeedsBalanceConfirmation -> result.pending.reason; is MigrationResult.Unsupported -> result.reason;is MigrationResult.Corrupt -> result.reason;else -> null }
        }
        return result
    }
    suspend fun confirmLocalOpeningBalance(balance:Amount,reason:String):CommitResult {
        val state=withContext(Dispatchers.Main.immediate) {
            if(!currentScope(checkNotNull(preparedLegacyScope))) throw StaleAndroidCommandScope()
            val pending=checkNotNull(pendingLegacyMigration)
            require(pending.origin.characterId==snapshot.activeCharacterId && pending.origin.activePackIds==declaredComposition)
            confirmLegacyBalance(pending,balance,reason)
        }
        return commitMigration(state)
    }
    suspend fun commitPreparedLocalMigration():CommitResult=commitMigration(withContext(Dispatchers.Main.immediate) { checkNotNull(preparedLegacyMigration) })
    private suspend fun commitMigration(state:StoredCharacter):CommitResult {
        if(!capabilities.localDurable) return CommitResult.StorageFailure("Android device durability/process-death acceptance is not verified; migration writes are disabled")
        val scope=withContext(Dispatchers.Main.immediate) {
            checkNotNull(preparedLegacyScope).copy(operationId=state.ledger.opening.migrationId,expectedStorageRevision=-1).also {
                if(!currentScope(it)) throw StaleAndroidCommandScope()
                require(state.ledger.opening.legacyMigration?.origin?.activePackIds==declaredComposition)
            }
        }
        return durableCommand(scope,{ checkNotNull(acquisitionStore).compareAndSet(scope.characterId,-1,state) },{ it==CommitResult.Committed },{ reload(scope.characterId) }) { result,loaded ->
            storageError=if(result==CommitResult.Committed)null else "Source changed or storage failed; prepare again"
            if(loaded!=null) { pendingLegacyMigration=null;preparedLegacyMigration=null;preparedLegacyScope=null;publish(loaded) }
        }
    }
    suspend fun previewLocalAcquisition(request:AcquisitionRequest):AcquisitionPlan {
        check(capabilities.localDurable) { "Android acquisition durability is not verified; purchase is unavailable" }
        val scope=withContext(Dispatchers.Main.immediate) { commandScope(request.characterId,"preview") }
        withContext(Dispatchers.Main.immediate) { if(!currentScope(scope)) throw StaleAndroidCommandScope() }
        val plan=withContext(Dispatchers.IO) { application.development.previewAcquisition(request) }
        withContext(Dispatchers.Main.immediate) { if(!currentScope(scope)) throw StaleAndroidCommandScope() }
        return plan
    }
    suspend fun applyLocalAcquisition(command:ConfirmAcquisition):ApplyResult {
        if(!capabilities.localDurable) return ApplyResult.StorageFailure("Android acquisition durability is not verified; purchase is disabled")
        val scope=withContext(Dispatchers.Main.immediate) { commandScope(command.characterId,command.operationId) }
        return durableCommand(scope,{ application.development.confirmAcquisition(command) },{
            it is ApplyResult.Applied || it is ApplyResult.AlreadyApplied || it is ApplyResult.AlreadyUndone
        },{ reload(scope.characterId) }) { result,loaded ->
            storageError=when(result) { is ApplyResult.StorageFailure -> result.reason;is ApplyResult.IdentityConflict -> result.reason;is ApplyResult.Stale -> "Preview is stale; review again";is ApplyResult.Blocked -> result.issues.joinToString { it.reason };else -> null }
            if(loaded!=null) publish(loaded)
        }
    }
    private suspend fun <T> durableCommand(scope:AndroidCommandScope,work:suspend () -> T,acknowledged:(T)->Boolean,
        reload:suspend () -> StoredCharacter,publish:(T,StoredCharacter?)->Unit):T {
        try { return queue.run(scope,work,acknowledged,reload,publish) }
        catch(stale:StaleAndroidCommandScope) { throw stale }
        catch(cancelled:kotlinx.coroutines.CancellationException) { throw cancelled }
        catch(failure:Exception) {
            withContext(Dispatchers.Main.immediate) { if(currentScope(scope)) storageError=failure.message ?: "Durable recovery failed; no display state was published" }
            throw failure
        }
    }
    suspend fun mutateLocalCanonical(command:CanonicalMutation):MutationResult {
        if(!capabilities.localDurable) return MutationResult.StorageFailure("Android durable mutation is not verified; writes are disabled")
        val scope=withContext(Dispatchers.Main.immediate) { commandScope(command.characterId,command.operationId) }
        return durableCommand(scope,{ application.mutateCanonical(command) },{
            it is MutationResult.Applied || it is MutationResult.AlreadyApplied || it is MutationResult.AlreadyUndone
        },{ reload(scope.characterId) }) { result,loaded ->
            storageError=when(result) { is MutationResult.StorageFailure -> result.reason;is MutationResult.Blocked -> result.reason;is MutationResult.IdentityConflict -> result.reason;is MutationResult.Stale -> "Character changed; review again";else -> null }
            if(loaded!=null) publish(loaded)
        }
    }
    suspend fun eligibleLocalUndo():UndoEligibility {
        if(!capabilities.localDurable) return UndoEligibility.Unavailable("Android durable Undo is not verified")
        val scope=withContext(Dispatchers.Main.immediate) { commandScope(snapshot.activeCharacterId,"undo-preview") }
        val result=withContext(Dispatchers.IO) { application.eligibleCanonicalUndo(scope.characterId) }
        withContext(Dispatchers.Main.immediate) { if(!currentScope(scope)) throw StaleAndroidCommandScope() }
        return result
    }
    suspend fun undoLocalCanonical(characterId:String,operationId:String,expectedRevision:Long):ApplyResult {
        if(!capabilities.localDurable) return ApplyResult.StorageFailure("Android durable Undo is not verified; writes are disabled")
        val scope=withContext(Dispatchers.Main.immediate) { commandScope(characterId,operationId) }
        return durableCommand(scope,{ application.undoCanonical(characterId,operationId,expectedRevision) },{
            it is ApplyResult.Applied || it is ApplyResult.AlreadyApplied || it is ApplyResult.AlreadyUndone
        },{ reload(scope.characterId) }) { result,loaded ->
            storageError=when(result) { is ApplyResult.StorageFailure -> result.reason;is ApplyResult.Blocked -> result.issues.joinToString { it.reason };is ApplyResult.IdentityConflict -> result.reason;else -> null }
            if(loaded!=null) publish(loaded)
        }
    }
    private suspend fun reload(id:String):StoredCharacter {
        val loaded=checkNotNull(acquisitionStore).load(id)
        return (loaded as? LoadResult.Loaded)?.state ?: throw AcquisitionPreviewUnavailable(loaded)
    }
    private fun publish(state:StoredCharacter) {
        canonicalStates=canonicalStates+(state.character.id to state)
        val characters=snapshot.characters.map { if(it.id==state.character.id)state.character else it }
        snapshot=snapshot.copy(characters=characters)
        if(snapshot.activeCharacterId==state.character.id) extras=state.extras
    }
    suspend fun recoveryExport():Map<String,ByteArray> = checkNotNull(acquisitionStore).recoveryExportAll()

}
