package com.furybook.android.state

import com.furybook.android.data.AndroidAcquisitionStore
import com.furybook.android.data.AndroidLegacyWriteGate
import com.furybook.dubl.acquisition.*
import com.furybook.dubl.actions.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.furybook.dubl.application.DublApplication
import com.furybook.dubl.application.CanonicalWritePolicy
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
    private val mainDispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.Main.immediate,
    val experimentalGameplayEnabled: Boolean = false,
) {
    private fun createApplication(canonical: Boolean) = DublApplication(
        characterStore = repository,
        extrasStore = extrasRepository,
        idFactory = { UUID.randomUUID().toString() },
        acquisitionService = acquisitionStore?.takeIf { canonical }?.let { AcquisitionService(it, catalogProvider) { UUID.randomUUID().toString() } },
        mutationCoordinator = acquisitionStore?.takeIf { canonical }?.let { CharacterMutationCoordinator(it,catalogProvider) { UUID.randomUUID().toString() } },
        acquisitionUndo = acquisitionStore?.takeIf { canonical }?.let(::AcquisitionUndo),
        characterCreator = acquisitionStore?.takeIf { canonical }?.let { AcquisitionCharacterCreator(it, catalogProvider) { capabilities } },
        writePolicy = if(canonical) CanonicalWritePolicy.Protected else CanonicalWritePolicy.LegacyIsolated,
    )

    private var application = createApplication(initialCanonicalStates.isNotEmpty() || legacyGate?.readOnly == true)
    private val commandApplication by lazy { createApplication(true) }
    var acquisitionUiModel: AcquisitionUiModel? by mutableStateOf(null)
        private set
    private fun acquisitionScope(): AcquisitionScope {
        val owner=ownerScope()
        return AcquisitionScope(checkNotNull(acquisitionStore).storageIdentity+"|"+owner.first.orEmpty(),snapshot.activeCharacterId,controllerGeneration,selectionGeneration,owner.second)
    }
    private fun currentAcquisitionScope(scope:AcquisitionScope):Boolean {
        val current=acquisitionScope()
        return !disposed && scope.storageScopeId==current.storageScopeId && scope.controllerGeneration==current.controllerGeneration &&
            scope.selectionGeneration==current.selectionGeneration && scope.accountGeneration==current.accountGeneration && scope.viewCharacterId==snapshot.activeCharacterId
    }
    private fun currentAcquisitionOwner(scope:AcquisitionScope):Boolean {
        val current=acquisitionScope()
        return !disposed && scope.storageScopeId==current.storageScopeId && scope.controllerGeneration==current.controllerGeneration && scope.accountGeneration==current.accountGeneration
    }
    fun gameplayScope(): GameplayScope {
        val owner = ownerScope()
        return GameplayScope(checkNotNull(acquisitionStore).storageIdentity,
            "${owner.first.orEmpty()}:${owner.second}:$controllerGeneration", snapshot.activeCharacterId, selectionGeneration)
    }
    private fun currentGameplayScope(value: GameplayScope) = !disposed && value == gameplayScope()
    private val retainedGameplay by lazy {
        val native = checkNotNull(acquisitionStore)
        val serialized = object : AcquisitionStore {
            override suspend fun load(characterId: String) = native.load(characterId)
            override suspend fun compareAndSet(characterId: String, expectedStorageRevision: Long, next: StoredCharacter): CommitResult {
                val captured = gameplayScope()
                val owner = ownerScope()
                val operation = checkNotNull(next.gameplay?.receipts?.lastOrNull()).request.operationId
                val command = AndroidCommandScope(native.storageIdentity, characterId, owner.first, controllerGeneration,
                    selectionGeneration, owner.second, operation, expectedStorageRevision, snapshot.activeCharacterId)
                return queue.execute(command) {
                    withContext(mainDispatcher) { check(currentGameplayScope(captured)) { "Android gameplay view changed while queued" } }
                    native.compareAndSet(characterId, expectedStorageRevision, next)
                }
            }
        }
        GameplayController(serialized, ::gameplayScope, ::currentGameplayScope,
            { GameplayCapabilities(experimentalGameplayEnabled && capabilities.localDurable && !disposed, true) },
            { UUID.randomUUID().toString() })
    }
    val gameplay: GameplayController? get() = if (experimentalGameplayEnabled && isCanonicalProtected) retainedGameplay else null
    private val retainedGameplayMigration by lazy {
        val native = checkNotNull(acquisitionStore)
        GameplayMigrationController(native, ::gameplayScope, ::currentGameplayScope,
            { GameplayCapabilities(experimentalGameplayEnabled && capabilities.localDurable && !disposed, true) },
            { UUID.randomUUID().toString() }) { request ->
                val captured = gameplayScope(); val owner = ownerScope()
                val command = AndroidCommandScope(native.storageIdentity, request.characterId, owner.first, controllerGeneration,
                    selectionGeneration, owner.second, request.operationId, request.expectedStorageRevision, snapshot.activeCharacterId)
                queue.execute(command) {
                    withContext(mainDispatcher) { check(currentGameplayScope(captured)) { "Android migration view changed while queued" } }
                    native.migrateGameplay(request)
                }
            }
    }
    val gameplayMigration: GameplayMigrationController? get() = if (gameplay != null) retainedGameplayMigration else null
    suspend fun refreshAfterGameplay(captured: GameplayScope) {
        check(currentGameplayScope(captured)) { "Android gameplay view changed" }
        val state = (checkNotNull(acquisitionStore).load(captured.characterId) as? LoadResult.Loaded)?.state
            ?: error("Android gameplay canonical state unavailable")
        withContext(mainDispatcher) {
            check(currentGameplayScope(captured)) { "Android gameplay view changed" }
            publish(state); acquisition.activateCurrentView()
        }
    }
    val acquisition: AcquisitionController by lazy {
        val port=ApplicationAcquisitionCommandPort(commandApplication,checkNotNull(acquisitionStore),catalogProvider,{capabilities},::currentAcquisitionScope,
            { scope,state ->
                val emptyOrigin=scope.characterId!=scope.viewCharacterId && acquisitionStore.load(scope.viewCharacterId) is LoadResult.Missing
                withContext(mainDispatcher) {
                check(currentAcquisitionScope(scope)) { "Captured view changed" }
                publish(state,selectNew=scope.characterId!=scope.viewCharacterId,dropPlaceholder=emptyOrigin)
                acquisition.activateCurrentView()
            } },object:AcquisitionExecutor {
                override suspend fun <T> read(block:suspend()->T):T = withContext(Dispatchers.IO) { block() }
                override suspend fun <T> run(block:suspend()->T):T {
                    val dispatch=capturedAcquisitionCommand();val captured=dispatch.scope
                    check(currentAcquisitionOwner(captured)) { "Captured owner changed" }
                    val owner=ownerScope()
                    val command=AndroidCommandScope(checkNotNull(acquisitionStore).storageIdentity,captured.characterId,owner.first,captured.controllerGeneration,
                        captured.selectionGeneration,captured.accountGeneration,dispatch.operationId,canonicalStates[captured.characterId]?.storageRevision ?: -1,captured.viewCharacterId)
                    return queue.execute(command,block)
                }
            })
        AcquisitionController(port,::acquisitionScope,{UUID.randomUUID().toString()}) { acquisitionUiModel=it }
    }
    fun reviewNewCharacterFunding(initialXP:Long)=acquisition.reviewNewCharacterFunding(initialXP)
    suspend fun confirmNewCharacterFunding(key:AcquisitionQuoteKey)=acquisition.confirm(key)

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

    val acquisitionCapabilities: AcquisitionCapabilities get() = capabilities
    val isCanonicalProtected: Boolean get() = canonicalStates.isNotEmpty() || legacyGate?.readOnly == true
    /** Installed by the root renderer; detached Shared staging still validates every field. */
    var onCanonicalNonFinancialReview: ((DublApplication.() -> Unit) -> Unit)? = null
    private fun descriptive(edit: DublApplication.() -> Unit) {
        if (isCanonicalProtected) {
            val review = onCanonicalNonFinancialReview
            if (review == null) storageError = "Редактор недоступен; сохранённый лист не изменён"
            else review(edit)
        } else sync { application.edit() }
    }

    fun setProfile(name: String, concept: String, experience: Int, size: Int, legs: Int, manaEnabled: Boolean) =
        sync { application.character.setProfile(name, concept, experience, size, legs, manaEnabled) }
    fun setIdentity(name: String, concept: String, size: Int, legs: Int, manaEnabled: Boolean) =
        sync { application.character.setIdentity(name, concept, size, legs, manaEnabled) }
    fun setName(name: String) = descriptive { character.setName(name) }
    fun setSize(size: Int) = sync { application.character.setSize(size) }
    fun setLegs(legs: Int) = sync { application.character.setLegs(legs) }
    fun changeAttribute(id: AttributeId, delta: Int) = sync { application.character.changeAttribute(id, delta) }
    fun setExperience(total: Int) = sync { application.character.setExperience(total) }
    fun setCreationExperience(value: Int) = sync { application.character.setCreationExperience(value) }
    fun setXpAdjustment(value: Int) = sync { application.character.setXpAdjustment(value) }
    fun setAbilityPointsOverride(value: Int?) = sync { application.character.setAbilityPointsOverride(value) }
    fun completeCreation() = sync { application.character.completeCreation() }
    fun reopenCreation() = sync { application.character.reopenCreation() }
    fun changeHp(delta: Int) = descriptive { character.changeHp(delta) }
    fun changeEndurance(delta: Int) = descriptive { character.changeEndurance(delta) }
    fun changeMana(delta: Int) = descriptive { magic.changeMana(delta) }
    fun changeChi(delta: Int) = descriptive { development.changeChi(delta) }
    fun changeResource(resource: CharacterSheetResourceId, delta: Int) = when (resource) {
        CharacterSheetResourceId.HEALTH -> changeHp(delta)
        CharacterSheetResourceId.ENDURANCE -> changeEndurance(delta)
        CharacterSheetResourceId.MANA -> changeMana(delta)
        CharacterSheetResourceId.CHI -> changeChi(delta)
    }
    fun setChiEnabled(enabled: Boolean) = sync { application.development.setChiEnabled(enabled) }
    fun setChiBonusRanks(rank: Int) = sync { application.development.setChiBonusRanks(rank) }
    fun restoreChi() = descriptive { development.restoreChi() }
    fun restoreMountedResource(resource: CharacterSheetResourceId) = when (resource) {
        CharacterSheetResourceId.CHI -> restoreChi()
        else -> Unit
    }
    fun setHealthMaximumOverride(value: Int?) = descriptive { character.setHealthMaximumOverride(value) }
    fun setEnduranceMaximumOverride(value: Int?) = descriptive { character.setEnduranceMaximumOverride(value) }
    fun setManaMaximumOverride(value: Int?) = descriptive { character.setManaMaximumOverride(value) }

    fun addCustomResource(name: String, maximum: Int, current: Int = maximum): String? =
        sync { application.character.addCustomResource(name, maximum, current) }
    fun updateCustomResource(uid: String, name: String, current: Int, maximum: Int) =
        descriptive { character.updateCustomResource(uid, name, current, maximum) }
    fun changeCustomResource(uid: String, delta: Int) = descriptive { character.changeCustomResource(uid, delta) }
    fun removeCustomResource(uid: String) = descriptive { character.removeCustomResource(uid) }

    fun changeSkillRank(skillId: String, delta: Int) = sync { application.skills.changeRank(skillId, delta) }
    fun setSkillAttributes(skillId: String, attributes: List<AttributeId>) = sync { application.skills.setAttributes(skillId, attributes) }
    fun setSkillModifier(skillId: String, modifier: Int) = descriptive { skills.setModifier(skillId, modifier) }
    fun setSkillFormulaNote(skillId: String, note: String) = descriptive { skills.setFormulaNote(skillId, note) }
    fun setSkillNameOverride(skillId: String, name: String) = descriptive { skills.setNameOverride(skillId, name) }
    fun setSkillDescriptionOverride(skillId: String, description: String) = descriptive { skills.setDescriptionOverride(skillId, description) }
    fun setSkillCategoryOverride(skillId: String, category: com.furybook.dubl.model.SkillCategory?) = sync { application.skills.setCategoryOverride(skillId, category) }
    fun setSkillUntrainedOverride(skillId: String, rule: UntrainedRule?) = sync { application.skills.setUntrainedOverride(skillId, rule) }
    fun setSkillAutoOverrides(skillId: String, auto6: String?, auto12: String?) = sync { application.skills.setAutoOverrides(skillId, auto6, auto12) }
    fun resetSkillDefinitionOverrides(skillId: String) = sync { application.skills.resetDefinitionOverrides(skillId) }
    fun hideSkill(skillId: String) = descriptive { skills.hide(skillId) }
    fun restoreSkill(skillId: String) = descriptive { skills.restore(skillId) }
    fun setSkillEffectEnabled(effectId: String, enabled: Boolean) = descriptive { skills.setEffectEnabled(effectId, enabled) }
    fun restoreAllSkills() = descriptive { skills.restoreAll() }
    fun addSpecializedSkill(templateId: String, specialization: String): String? = sync { application.skills.addSpecialized(templateId, specialization) }
    fun addCustomSkill(name: String, description: String, attributes: List<AttributeId>, untrained: UntrainedRule): String? =
        sync { application.skills.addCustom(name, description, attributes, untrained) }
    fun deleteDynamicSkill(skillId: String) = sync { application.skills.deleteDynamic(skillId) }
    fun setPreferredSkillAttribute(skillId: String, attribute: AttributeId?) = descriptive { skills.setPreferredAttribute(skillId, attribute) }

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

    fun setGearLoadAutomatic(enabled: Boolean) = descriptive { equipment.setLoadAutomatic(enabled) }
    fun setGearManualLoad(value: Double) = descriptive { equipment.setManualLoad(value) }
    fun syncCatalogGearLoads(entries: List<GearCatalogEntry>) = descriptive { equipment.syncCatalogLoads(entries) }
    fun addCatalogGear(entry: GearCatalogEntry): String = sync { application.equipment.addCatalog(entry) }
    fun addCustomGear(item: GearItem): String = sync { application.equipment.addCustom(item) }
    fun updateGearItem(item: GearItem) = descriptive { equipment.updateItem(item) }
    fun setGearItemCarried(uid: String, carried: Boolean) = descriptive { equipment.setItemCarried(uid, carried) }
    fun setGearItemQuantity(uid: String, quantity: Int) = descriptive { equipment.setItemQuantity(uid, quantity) }
    fun removeGearItem(uid: String) = descriptive { equipment.removeItem(uid) }

    fun setPortrait(uri: String?) = descriptive { sheet.setPortrait(uri) }
    fun toggleCondition(condition: CharacterConditionId) = descriptive { sheet.toggleCondition(condition) }
    fun setConditions(conditions: Set<CharacterConditionId>) = descriptive { sheet.setConditions(conditions) }
    fun setResourceHidden(resource: CharacterSheetResourceId, hidden: Boolean) = descriptive { sheet.setResourceHidden(resource, hidden) }
    fun setSkillGroups(groups: List<SheetGroup>) = descriptive { sheet.setSkillGroups(groups) }
    fun setDevelopmentGroups(groups: List<SheetGroup>) = descriptive { sheet.setDevelopmentGroups(groups) }
    fun setConditionOverride(condition: CharacterConditionId, title: String?, description: String?) = descriptive { sheet.setConditionOverride(condition, title, description) }
    fun resetConditionOverride(condition: CharacterConditionId) = descriptive { sheet.resetConditionOverride(condition) }
    fun addCustomCondition(title: String, description: String = "", active: Boolean = false): String? = sync { application.sheet.addCustomCondition(title, description, active) }
    fun updateCustomCondition(id: String, title: String, description: String, active: Boolean? = null) = descriptive { sheet.updateCustomCondition(id, title, description, active) }
    fun setCustomConditionActive(id: String, active: Boolean) = descriptive { sheet.setCustomConditionActive(id, active) }
    fun removeCustomCondition(id: String) = descriptive { sheet.removeCustomCondition(id) }

    fun exportActiveCharacter(): String = canonicalStates[snapshot.activeCharacterId]?.let(::encodeEnvelope) ?: application.transfer.exportActive()
    fun importCharacter(raw: String): CharacterTransferImportResult = sync { application.transfer.importCharacter(raw) }

    fun createCharacter() = sync { application.character.createCharacter() }
    fun selectCharacter(id: String) {
        selectionGeneration++
        pendingLegacyMigration=null;preparedLegacyMigration=null;preparedLegacyScope=null
        if(canonicalStates.isNotEmpty() || legacyGate?.readOnly==true) {
            require(snapshot.characters.any { it.id==id })
            snapshot=snapshot.copy(activeCharacterId=id)
            extras=canonicalStates[id]?.extras ?: extrasRepository.load(id)
            if(acquisitionStore!=null) acquisition.activateCurrentView()
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
    private val queue=AndroidAcquisitionQueue(::currentScope,main=mainDispatcher,commandCurrent=::currentCommandScope)
    private fun currentScope(scope:AndroidCommandScope):Boolean {
        val owner=ownerScope()
        return !disposed && scope.storageIdentity==acquisitionStore?.storageIdentity && scope.viewCharacterId==snapshot.activeCharacterId &&
            scope.controllerGeneration==controllerGeneration && scope.selectionGeneration==selectionGeneration && scope.ownerId==owner.first && scope.accountGeneration==owner.second
    }
    private fun currentCommandScope(scope:AndroidCommandScope):Boolean {
        val owner=ownerScope()
        return !disposed && scope.storageIdentity==acquisitionStore?.storageIdentity && scope.controllerGeneration==controllerGeneration && scope.ownerId==owner.first && scope.accountGeneration==owner.second
    }
    private fun commandScope(id:String,operationId:String):AndroidCommandScope {
        val owner=ownerScope()
        return AndroidCommandScope(checkNotNull(acquisitionStore).storageIdentity,id,owner.first,controllerGeneration,selectionGeneration,owner.second,operationId,canonicalStates[id]?.storageRevision ?: -1L)
    }
    fun dispose() { disposed=true;controllerGeneration++;pendingLegacyMigration=null;preparedLegacyMigration=null;preparedLegacyScope=null }
    fun declareLegacyComposition(ids:List<String>) { declaredComposition=ids.toList();selectionGeneration++;pendingLegacyMigration=null;preparedLegacyMigration=null;preparedLegacyScope=null }
    suspend fun prepareLocalMigration(ids:List<String>,oldCatalog:LegacyCatalogSnapshot?):MigrationResult {
        val scope=withContext(mainDispatcher) { declareLegacyComposition(ids);commandScope(snapshot.activeCharacterId,"migration-prepare") }
        val composition=ids.toList()
        val result=withContext(Dispatchers.IO) { checkNotNull(acquisitionStore).prepareMigration(scope.characterId,composition,oldCatalog) }
        withContext(mainDispatcher) {
            if(!currentScope(scope)) throw StaleAndroidCommandScope()
            preparedLegacyScope=scope
            pendingLegacyMigration=(result as? MigrationResult.NeedsBalanceConfirmation)?.pending
            preparedLegacyMigration=(result as? MigrationResult.Ready)?.state
            storageError=when(result) { is MigrationResult.NeedsBalanceConfirmation -> result.pending.reason; is MigrationResult.Unsupported -> result.reason;is MigrationResult.Corrupt -> result.reason;else -> null }
        }
        return result
    }
    suspend fun confirmLocalOpeningBalance(balance:Amount,reason:String):CommitResult {
        val state=withContext(mainDispatcher) {
            if(!currentScope(checkNotNull(preparedLegacyScope))) throw StaleAndroidCommandScope()
            val pending=checkNotNull(pendingLegacyMigration)
            require(pending.origin.characterId==snapshot.activeCharacterId && pending.origin.activePackIds==declaredComposition)
            confirmLegacyBalance(pending,balance,reason)
        }
        return commitMigration(state)
    }
    suspend fun commitPreparedLocalMigration():CommitResult=commitMigration(withContext(mainDispatcher) { checkNotNull(preparedLegacyMigration) })
    private suspend fun commitMigration(state:StoredCharacter):CommitResult {
        if(!capabilities.localDurable) return CommitResult.StorageFailure("Android device durability/process-death acceptance is not verified; migration writes are disabled")
        val scope=withContext(mainDispatcher) {
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
        val scope=withContext(mainDispatcher) { commandScope(request.characterId,"preview") }
        withContext(mainDispatcher) { if(!currentScope(scope)) throw StaleAndroidCommandScope() }
        val plan=withContext(Dispatchers.IO) { commandApplication.development.previewAcquisition(request) }
        withContext(mainDispatcher) { if(!currentScope(scope)) throw StaleAndroidCommandScope() }
        return plan
    }
    suspend fun applyLocalAcquisition(command:ConfirmAcquisition):ApplyResult {
        if(!capabilities.localDurable) return ApplyResult.StorageFailure("Android acquisition durability is not verified; purchase is disabled")
        val scope=withContext(mainDispatcher) { commandScope(command.characterId,command.operationId) }
        return durableCommand(scope,{ commandApplication.development.confirmAcquisition(command) },{
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
            withContext(mainDispatcher) { if(currentScope(scope)) storageError=failure.message ?: "Durable recovery failed; no display state was published" }
            throw failure
        }
    }
    suspend fun mutateLocalCanonical(command:CanonicalMutation):MutationResult {
        if(!capabilities.localDurable) return MutationResult.StorageFailure("Android durable mutation is not verified; writes are disabled")
        val scope=withContext(mainDispatcher) { commandScope(command.characterId,command.operationId) }
        return durableCommand(scope,{ commandApplication.mutateCanonical(command) },{
            it is MutationResult.Applied || it is MutationResult.AlreadyApplied || it is MutationResult.AlreadyUndone
        },{ reload(scope.characterId) }) { result,loaded ->
            storageError=when(result) { is MutationResult.StorageFailure -> result.reason;is MutationResult.Blocked -> result.reason;is MutationResult.IdentityConflict -> result.reason;is MutationResult.Stale -> "Character changed; review again";else -> null }
            if(loaded!=null) publish(loaded)
        }
    }
    suspend fun eligibleLocalUndo():UndoEligibility {
        if(!capabilities.localDurable) return UndoEligibility.Unavailable("Android durable Undo is not verified")
        val scope=withContext(mainDispatcher) { commandScope(snapshot.activeCharacterId,"undo-preview") }
        val result=withContext(Dispatchers.IO) { commandApplication.eligibleCanonicalUndo(scope.characterId) }
        withContext(mainDispatcher) { if(!currentScope(scope)) throw StaleAndroidCommandScope() }
        return result
    }
    suspend fun undoLocalCanonical(characterId:String,operationId:String,expectedRevision:Long):ApplyResult {
        if(!capabilities.localDurable) return ApplyResult.StorageFailure("Android durable Undo is not verified; writes are disabled")
        val scope=withContext(mainDispatcher) { commandScope(characterId,operationId) }
        return durableCommand(scope,{ commandApplication.undoCanonical(characterId,operationId,expectedRevision) },{
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
    private fun publish(state:StoredCharacter,selectNew:Boolean=false,dropPlaceholder:Boolean=false) {
        canonicalStates=canonicalStates+(state.character.id to state)
        val characters=snapshot.characters.filterNot { dropPlaceholder && it.id==snapshot.activeCharacterId }.map { if(it.id==state.character.id)state.character else it }.toMutableList()
        if(characters.none { it.id==state.character.id }) characters+=state.character
        snapshot=snapshot.copy(characters=characters,activeCharacterId=if(selectNew)state.character.id else snapshot.activeCharacterId)
        application=createApplication(true)
        if(snapshot.activeCharacterId==state.character.id) extras=state.extras
    }
    suspend fun recoveryExport():Map<String,ByteArray> = checkNotNull(acquisitionStore).recoveryExportAll()

}
