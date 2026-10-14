package com.furybook.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.furybook.content.FcpComposition
import com.furybook.content.FcpManifest
import com.furybook.dubl.acquisition.*
import com.furybook.dubl.actions.*
import com.furybook.dubl.data.CharacterStore
import com.furybook.dubl.data.CharacterExtrasStore
import com.furybook.dubl.data.CharacterTransferRejectReason
import com.furybook.content.FcpContentPack
import com.furybook.content.FcpTextSource
import com.furybook.web.BrowserAcquisitionStore
import com.furybook.dubl.application.DublApplication
import com.furybook.dubl.application.CanonicalWritePolicy
import com.furybook.dubl.application.CharacterTransferImportResult
import com.furybook.dubl.content.DublChiFcp
import com.furybook.dubl.content.DublFcp
import com.furybook.dubl.content.DublResourceMeterModel
import com.furybook.dubl.content.DublResourceToggleModel
import com.furybook.dubl.content.DublUiMount
import com.furybook.dubl.content.DublUiRegistry
import com.furybook.dubl.content.DublUiRenderModels
import com.furybook.dubl.data.mergeDevelopmentCatalogs
import com.furybook.dubl.data.SnapshotCodec
import com.furybook.dubl.model.*
import com.furybook.web.BrowserCharacterExtrasStore
import com.furybook.web.BrowserCharacterStore
import com.furybook.web.BrowserPackState
import com.furybook.web.CloudSyncStatus
import com.furybook.web.WebCatalogLoader
import com.furybook.web.WebCloudSync
import com.furybook.web.webUuid
import com.furybook.web.WebCanonicalReadState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class DesktopAppState(
    private val resourceTexts: Map<String, String>,
    private val cloudSync: WebCloudSync? = null,
    private val acquisitionStore: BrowserAcquisitionStore? = null,
    initialCanonicalStates: Map<String,StoredCharacter> = emptyMap(),
    val localAcquisitionEnabled:Boolean = false,
    private val acquisitionCatalogProvider:(()->AcquisitionCatalog)? = null,
    val experimentalGameplayEnabled: Boolean = false,
) {
    private val characterStore = BrowserCharacterStore()
    private val extrasStore = BrowserCharacterExtrasStore()
    private var canonicalStates = initialCanonicalStates.toMap()
    private var readOnlySelection:String? = null
    var canonicalReadState by mutableStateOf(WebCanonicalReadState.initial(acquisitionStore != null, initialCanonicalStates.isNotEmpty()))
        private set
    val canonicalMode get() = localAcquisitionEnabled || canonicalReadState.blocksLegacy
    val readOnlyCanonicalMode get() = canonicalMode && !localAcquisitionEnabled
    var canonicalLoadOutcome:LoadResult by mutableStateOf(initialCanonicalStates.values.firstOrNull()?.let { LoadResult.Loaded(it) } ?: LoadResult.Missing("unclassified"))
        private set
    var canonicalViewGeneration by mutableStateOf(0L)
        private set
    val contentRevision get() = canonicalViewGeneration
    private val canonicalReadLane = Mutex()
    private val canonicalCommandLane = Mutex()
    var onCanonicalNonFinancialReview:((DublApplication.()->Unit)->Unit)? = null
    val cloudLedgerV2:Boolean = false
    var storageError:String? by mutableStateOf(null);private set
    private val acquisitionService by lazy { AcquisitionService(requireNotNull(acquisitionStore),::acquisitionCatalog,::webUuid) }
    private var cachedAcquisitionCatalog:AcquisitionCatalog? = null
    private fun acquisitionCatalog():AcquisitionCatalog {
        acquisitionCatalogProvider?.let { return it() }
        cachedAcquisitionCatalog?.let { return it }
        val source=FcpTextSource { resourceTexts[it] }
        return loadAcquisitionCatalog(contentPackComposition.active.map { FcpContentPack.load("fcp/${it.id}",source) },chiPackEnabled).catalog.also { cachedAcquisitionCatalog=it }
    }
    private fun createApplication(forceCanonical:Boolean=false):DublApplication {
        if(!canonicalMode && !forceCanonical) return DublApplication(characterStore,extrasStore,::webUuid,::webUuid)
        val original=characterStore.load()
        val originalCharacters=if(!characterStore.hasStoredState() && canonicalStates.isNotEmpty()) emptyList() else original.characters
        val characters=originalCharacters.map { canonicalStates[it.id]?.character ?: it }.toMutableList()
        canonicalStates.values.filter { value -> characters.none { it.id==value.character.id } }.forEach { characters += it.character }
        val view=AppSnapshot(characters,readOnlySelection?.takeIf { id -> characters.any { it.id==id } } ?: original.activeCharacterId.takeIf { id -> characters.any { it.id==id } } ?: characters.firstOrNull()?.id ?: original.activeCharacterId)
        val readonly=object:CharacterStore {
            override fun load()=view
            override fun save(snapshot:AppSnapshot)=error("Legacy writer cannot change a canonical browser view")
        }
        val readonlyExtras=object:CharacterExtrasStore {
            override fun load(characterId:String)=canonicalStates[characterId]?.extras ?: extrasStore.load(characterId)
            override fun save(characterId:String,extras:CharacterSheetExtras)=error("Legacy extras writer cannot change a canonical browser view")
        }
        return DublApplication(readonly,readonlyExtras,::webUuid,::webUuid,
            acquisitionService=acquisitionStore?.let { acquisitionService },
            mutationCoordinator=acquisitionStore?.let { CharacterMutationCoordinator(it,::acquisitionCatalog,::webUuid) },
            acquisitionUndo=acquisitionStore?.let(::AcquisitionUndo),
            characterCreator=acquisitionStore?.let { AcquisitionCharacterCreator(it,::acquisitionCatalog,::localCapabilities) },
            writePolicy=CanonicalWritePolicy.Protected)
    }
    private var application = createApplication()
    private val commandApplication by lazy { createApplication(true) }
    private var selectionGeneration=0L
    private var controllerGeneration=0L
    private val accountGeneration=acquisitionStore?.scopeGeneration ?: 0L
    private var disposed=false
    var acquisitionUiModel:AcquisitionUiModel? by mutableStateOf(null)
        private set
    private fun localCapabilities():AcquisitionCapabilities {
        val available=acquisitionStore?.capabilities
        return AcquisitionCapabilities(localAcquisitionEnabled && !disposed && available?.localDurable==true,false,available?.formatWritable==true)
    }
    private fun acquisitionScope()=AcquisitionScope(checkNotNull(acquisitionStore).storageIdentity,snapshot.activeCharacterId,controllerGeneration,selectionGeneration,accountGeneration)
    private fun currentAcquisitionScope(scope:AcquisitionScope):Boolean = !disposed && acquisitionStore?.isCurrentScope()==true &&
        scope.storageScopeId==acquisitionStore.storageIdentity && scope.accountGeneration==acquisitionStore.scopeGeneration &&
        scope.controllerGeneration==controllerGeneration && scope.selectionGeneration==selectionGeneration && scope.viewCharacterId==snapshot.activeCharacterId
    private fun currentAcquisitionOwner(scope:AcquisitionScope):Boolean = !disposed && acquisitionStore?.isCurrentScope()==true &&
        scope.storageScopeId==acquisitionStore.storageIdentity && scope.accountGeneration==acquisitionStore.scopeGeneration &&
        scope.controllerGeneration==controllerGeneration
    fun gameplayScope() = GameplayScope(checkNotNull(acquisitionStore).storageIdentity,
        "browser:$accountGeneration:$controllerGeneration", snapshot.activeCharacterId, selectionGeneration)
    private fun currentGameplayScope(value: GameplayScope) = !disposed && acquisitionStore?.isCurrentScope() == true &&
        accountGeneration == acquisitionStore.scopeGeneration && value == gameplayScope()
    private val retainedGameplay by lazy {
        val native = checkNotNull(acquisitionStore)
        val serialized = object : AcquisitionStore {
            override suspend fun load(characterId: String) = native.load(characterId)
            override suspend fun compareAndSet(characterId: String, expectedStorageRevision: Long, next: StoredCharacter): CommitResult {
                val captured = gameplayScope()
                return canonicalCommandLane.withLock {
                    check(currentGameplayScope(captured)) { "Browser gameplay scope changed while queued" }
                    native.compareAndSet(characterId, expectedStorageRevision, next)
                }
            }
        }
        GameplayController(serialized, ::gameplayScope, ::currentGameplayScope,
            { GameplayCapabilities(experimentalGameplayEnabled && localCapabilities().localDurable, true) }, ::webUuid)
    }
    val gameplay: GameplayController? get() = if (experimentalGameplayEnabled && canonicalMode) retainedGameplay else null
    private val retainedGameplayMigration by lazy {
        val native = checkNotNull(acquisitionStore)
        GameplayMigrationController(native, ::gameplayScope, ::currentGameplayScope,
            { GameplayCapabilities(experimentalGameplayEnabled && localCapabilities().localDurable, true) }, ::webUuid) { request ->
                val captured = gameplayScope()
                canonicalCommandLane.withLock {
                    check(currentGameplayScope(captured)) { "Browser migration scope changed while queued" }
                    native.migrateGameplay(request)
                }
            }
    }
    val gameplayMigration: GameplayMigrationController? get() = if (gameplay != null) retainedGameplayMigration else null
    suspend fun refreshAfterGameplay(captured: GameplayScope) {
        check(currentGameplayScope(captured)) { "Browser gameplay view changed" }
        val state = (checkNotNull(acquisitionStore).load(captured.characterId) as? LoadResult.Loaded)?.state
            ?: error("Browser gameplay canonical state unavailable")
        check(currentGameplayScope(captured)) { "Browser gameplay view changed" }
        canonicalStates = canonicalStates + (state.character.id to state)
        canonicalReadState = canonicalReadState.loaded(true); canonicalLoadOutcome = LoadResult.Loaded(state); canonicalViewGeneration++
        application = createApplication(); refresh(); acquisition.activateCurrentView()
    }
    val acquisition:AcquisitionController by lazy {
        AcquisitionController(ApplicationAcquisitionCommandPort(commandApplication,checkNotNull(acquisitionStore),::acquisitionCatalog,::localCapabilities,::currentAcquisitionScope,
            { scope,state ->
                check(currentAcquisitionScope(scope)) { "Captured owner/view changed" }
                canonicalStates=canonicalStates+(state.character.id to state)
                canonicalReadState=canonicalReadState.loaded(true);canonicalLoadOutcome=LoadResult.Loaded(state);canonicalViewGeneration++
                if(scope.characterId!=scope.viewCharacterId) readOnlySelection=state.character.id
                application=createApplication();refresh();acquisition.activateCurrentView()
            },object:AcquisitionExecutor {
                override suspend fun <T> read(block:suspend()->T):T=block()
                override suspend fun <T> run(block:suspend()->T):T {
                    val captured=capturedAcquisitionCommand().scope
                    return canonicalCommandLane.withLock {
                        check(currentAcquisitionOwner(captured)) { "Captured browser owner changed" }
                        block()
                    }
                }
            }),::acquisitionScope,::webUuid) { acquisitionUiModel=it }
    }
    fun reviewNewCharacterFunding(initialXP:Long)=acquisition.reviewNewCharacterFunding(initialXP)
    suspend fun confirmNewCharacterFunding(key:AcquisitionQuoteKey)=acquisition.confirm(key)
    fun dispose() { disposed=true;controllerGeneration++;onCanonicalNonFinancialReview=null }

    private val catalogLoader = WebCatalogLoader(resourceTexts)

    val corePackManifest = catalogLoader.manifest
    val chiPackManifest = catalogLoader.chiManifest
    private val chiDevelopmentIds = linkedSetOf<String>().apply {
        addAll(catalogLoader.loadChiDevelopment().entries.map { it.id })
        addAll(catalogLoader.chiManifest.claims("dubl.development").map { it.id })
    }

    var chiPackEnabled: Boolean by mutableStateOf(BrowserPackState.chiEnabled)
        private set

    val conditionCatalog = catalogLoader.loadConditions()
    private var canonicalDevelopmentCatalog: DevelopmentCatalog by mutableStateOf(composeCanonicalDevelopmentCatalog())
    val developmentCatalog: DevelopmentCatalog
        get() = activeCharacter
            .effectiveDevelopmentCatalog(canonicalDevelopmentCatalog)
            .let { catalog -> if (chiPackEnabled) catalog else catalog.withoutChiContent() }
    var chiCatalog: ChiCatalog by mutableStateOf(
        if (chiPackEnabled) catalogLoader.loadChi() else ChiCatalog("disabled", emptyList(), emptyList()),
    )
        private set
    val magicEquipmentCatalog = catalogLoader.loadMagicEquipment()
    val skillEffectCatalog = catalogLoader.loadSkillEffects()

    var snapshot: AppSnapshot by mutableStateOf(application.snapshot)
        private set
    var extras: CharacterSheetExtras by mutableStateOf(application.activeExtras)
        private set

    val webCloudSync: WebCloudSync?
        get() = cloudSync

    val cloudStatus: CloudSyncStatus?
        get() = cloudSync?.status

    val activeCharacter: DublCharacter
        get() = if (chiPackEnabled) snapshot.activeCharacter else snapshot.activeCharacter.withoutRuntimeDevelopmentEffects(
            suppressedEntryIds = chiDevelopmentIds,
            suppressChiResource = true,
        )

    init {
        if (!canonicalMode) application.equipment.syncCatalogLoads(magicEquipmentCatalog.gear)
        refresh()
    }

    val contentPackComposition: FcpComposition
        get() = FcpComposition.resolve(
            manifests = listOf(corePackManifest, chiPackManifest),
            requiredPackIds = setOf(DublFcp.PACK_ID),
            enabledPackIds = if (chiPackEnabled) setOf(DublChiFcp.PACK_ID) else emptySet(),
        )

    fun uiMounts(surface: String, component: String): List<DublUiMount> =
        DublUiRegistry.mounts(contentPackComposition, surface, component)

    fun resourceMeterModels(character: DublCharacter = activeCharacter): List<DublResourceMeterModel> =
        DublUiRenderModels.resourceMeters(contentPackComposition, character)

    fun resourceToggleModels(character: DublCharacter = activeCharacter): List<DublResourceToggleModel> =
        DublUiRenderModels.resourceToggles(contentPackComposition, character)

    fun canActivateContentPack(manifest: FcpManifest): Boolean =
        manifest.id == DublFcp.PACK_ID || manifest.id == DublChiFcp.PACK_ID

    fun setContentPackActive(packId: String, enabled: Boolean) {
        when (packId) {
            DublFcp.PACK_ID -> require(enabled) { "Required FCP ${DublFcp.PACK_ID} cannot be disabled" }
            DublChiFcp.PACK_ID -> setChiPackActive(enabled)
            else -> error("External FCP import is not available in the web build yet")
        }
    }

    fun setChiPackActive(enabled: Boolean) {
        if(canonicalMode) { storageError="Состав канонического листа меняется только подтверждённой операцией";return }
        if (enabled) catalogLoader.verifyChiPack()
        cachedAcquisitionCatalog=null
        BrowserPackState.chiEnabled = enabled
        chiPackEnabled = enabled
        canonicalDevelopmentCatalog = composeCanonicalDevelopmentCatalog()
        chiCatalog = if (enabled) catalogLoader.loadChi() else ChiCatalog("disabled", emptyList(), emptyList())
        refresh()
        scheduleCloudSync()
    }

    private fun composeCanonicalDevelopmentCatalog(): DevelopmentCatalog =
        catalogLoader.loadDevelopment(includeChi = contentPackComposition.isActive(DublChiFcp.PACK_ID))

    fun refresh() {
        snapshot = application.snapshot
        extras = application.activeExtras
    }

    private fun scheduleCloudSync() {
        if (!canonicalMode) cloudSync?.schedule(SnapshotCodec.encode(snapshot), extrasStore.raw(), chiPackEnabled)
    }

    private inline fun <T> sync(action: () -> T): T? {
        if(canonicalMode) {
            storageError="Канонический лист доступен для чтения. Используйте подтверждённую операцию журнала; выпуск локальных покупок пока выключен."
            return null
        }
        return try { val result=action();refresh();scheduleCloudSync();result }
            catch(failure:Exception) { storageError="Ошибка сохранения: ${failure.message}";application=createApplication();refresh();null }
    }
    private fun requireLocalWrites() {
        check(localAcquisitionEnabled && acquisitionStore?.capabilities?.localDurable==true) { "Local durable acquisitions are disabled or unavailable" }
    }
    /** Classification is acknowledged only after every native head has been read successfully. */
    suspend fun initializeCanonicalView() = canonicalReadLane.withLock {
        val store=acquisitionStore ?: return@withLock
        val generation=controllerGeneration;val selection=selectionGeneration
        canonicalReadState=canonicalReadState.begin()
        try {
            check(!disposed && store.isCurrentScope()) { "Browser owner unavailable" }
            val loaded=linkedMapOf<String,StoredCharacter>()
            val outcomes=linkedMapOf<String,LoadResult>()
            val ids=(store.characterIds()+snapshot.characters.map { it.id }).distinct()
            for(id in ids) {
                val result=store.load(id);outcomes[id]=result
                when(result) {
                    is LoadResult.Loaded -> loaded[id]=result.state
                    is LoadResult.Missing,is LoadResult.MigrationRequired -> Unit
                    else -> throw AcquisitionPreviewUnavailable(result)
                }
            }
            check(!disposed && generation==controllerGeneration && selection==selectionGeneration && store.isCurrentScope()) { "Captured owner/view changed" }
            canonicalStates=loaded.toMap();storageError=null
            canonicalReadState=canonicalReadState.loaded(localAcquisitionEnabled || loaded.isNotEmpty())
            canonicalLoadOutcome=outcomes[snapshot.activeCharacterId] ?: LoadResult.Missing(snapshot.activeCharacterId)
            canonicalViewGeneration++;application=createApplication();if(!canonicalMode) application.equipment.syncCatalogLoads(magicEquipmentCatalog.gear);refresh()
        } catch(cancelled:CancellationException) { throw cancelled }
        catch(failure:Exception) {
            if(!disposed && generation==controllerGeneration && selection==selectionGeneration) {
                canonicalReadState=canonicalReadState.failed("Каноническое хранилище недоступно. Исходные данные сохранены для восстановления.")
                storageError=canonicalReadState.error
            }
            throw failure
        }
    }
    private suspend fun reloadCanonicalView() = initializeCanonicalView()
    private fun descriptive(edit:DublApplication.()->Unit) {
        if(canonicalMode) {
            val review=onCanonicalNonFinancialReview
            if(review==null) storageError="Сохранённый лист недоступен; правка не выполнена" else review(edit)
        } else sync { application.edit() }
    }
    suspend fun previewLocalAcquisition(request:AcquisitionRequest):AcquisitionPlan {
        requireLocalWrites();return commandApplication.development.previewAcquisition(request)
    }
    suspend fun applyLocalAcquisition(command:ConfirmAcquisition):ApplyResult {
        requireLocalWrites()
        val result=commandApplication.development.confirmAcquisition(command)
        storageError=when(result) { is ApplyResult.StorageFailure->result.reason;is ApplyResult.Blocked->result.issues.joinToString { it.reason };is ApplyResult.Stale->"Предпросмотр устарел";is ApplyResult.IdentityConflict->result.reason;else->null }
        if(result is ApplyResult.Applied || result is ApplyResult.AlreadyApplied || result is ApplyResult.AlreadyUndone)
            runCatching { reloadCanonicalView() }.onFailure { storageError="Сохранено, но просмотр не обновился: ${it.message}" }
        return result
    }
    suspend fun mutateCanonical(command:CanonicalMutation):MutationResult {
        requireLocalWrites()
        val result=commandApplication.mutateCanonical(command)
        storageError=when(result) { is MutationResult.StorageFailure->result.reason;is MutationResult.Blocked->result.reason;is MutationResult.IdentityConflict->result.reason;is MutationResult.Stale->"Лист изменился";else->null }
        if(result is MutationResult.Applied || result is MutationResult.AlreadyApplied || result is MutationResult.AlreadyUndone) reloadCanonicalView()
        return result
    }
    suspend fun undoCanonical(operationId:String,expectedRevision:Long):ApplyResult {
        requireLocalWrites()
        val result=commandApplication.undoCanonical(snapshot.activeCharacterId,operationId,expectedRevision)
        if(result is ApplyResult.Applied || result is ApplyResult.AlreadyApplied || result is ApplyResult.AlreadyUndone) reloadCanonicalView()
        storageError=(result as? ApplyResult.StorageFailure)?.reason
        return result
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
    fun setEconomy(total: Int, creation: Int, adjustment: Int, abilityPointsOverride: Int?) =
        sync { application.character.setEconomy(total, creation, adjustment, abilityPointsOverride) }
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
    fun setSkillNameOverride(skillId: String, name: String) = descriptive { skills.setNameOverride(skillId, name) }
    fun setSkillDescriptionOverride(skillId: String, description: String) = sync { application.skills.setDescriptionOverride(skillId, description) }
    fun setSkillCategoryOverride(skillId: String, category: SkillCategory?) = sync { application.skills.setCategoryOverride(skillId, category) }
    fun setSkillUntrainedOverride(skillId: String, rule: UntrainedRule?) = sync { application.skills.setUntrainedOverride(skillId, rule) }
    fun setSkillAutoOverrides(skillId: String, auto6: String?, auto12: String?) = sync { application.skills.setAutoOverrides(skillId, auto6, auto12) }
    fun resetSkillDefinitionOverrides(skillId: String) = sync { application.skills.resetDefinitionOverrides(skillId) }
    fun hideSkill(skillId: String) = sync { application.skills.hide(skillId) }
    fun restoreSkill(skillId: String) = sync { application.skills.restore(skillId) }
    fun restoreAllSkills() = sync { application.skills.restoreAll() }
    fun setSkillEffectEnabled(effectId: String, enabled: Boolean) = sync { application.skills.setEffectEnabled(effectId, enabled) }
    fun addSpecializedSkill(templateId: String, specialization: String): String? = sync { application.skills.addSpecialized(templateId, specialization) }
    fun addCustomSkill(name: String, description: String, attributes: List<AttributeId>, untrained: UntrainedRule): String? =
        sync { application.skills.addCustom(name, description, attributes, untrained) }
    fun deleteDynamicSkill(skillId: String) = sync { application.skills.deleteDynamic(skillId) }
    fun setPreferredSkillAttribute(skillId: String, attribute: AttributeId?) = sync { application.skills.setPreferredAttribute(skillId, attribute) }

    fun setDevelopmentRank(entryId: String, rank: Int, optionIndex: Int = 0) = sync { application.development.setRank(entryId, rank, optionIndex) }
    fun acquireDevelopment(request: DevelopmentAcquisitionRequest): DevelopmentAcquisitionResult = sync { application.development.acquire(developmentCatalog, request) } ?: DevelopmentAcquisitionResult(false, DevelopmentAcquisitionPlanner(activeCharacter,developmentCatalog).plan(request),storageError ?: "Storage unavailable")
    fun setDevelopmentOverride(entry: DevelopmentEntry) = sync { application.development.setOverride(entry) }
    fun resetDevelopmentOverride(entryId: String) = sync { application.development.resetOverride(entryId) }
    fun addCustomDevelopment(entry: DevelopmentEntry): String? = sync { application.development.addCustom(entry) }
    fun updateCustomDevelopment(entry: DevelopmentEntry): Boolean = sync { application.development.updateCustom(entry) } ?: false
    fun removeCustomDevelopment(entryId: String) = sync { application.development.removeCustom(entryId) }

    fun setMagicManaRank(rank: Int) = sync { application.magic.setManaRank(rank) }
    fun setMagicSchoolPower(name: String, power: Int) = sync { application.magic.setSchoolPower(name, power) }
    fun addMagicSchool(name: String, rank: Int, note: String): Boolean = sync { application.magic.addSchool(name, rank, note) } ?: false
    fun updateMagicSchool(index: Int, name: String, rank: Int, note: String): Boolean = sync { application.magic.updateSchool(index, name, rank, note) } ?: false
    fun removeMagicSchool(index: Int) = sync { application.magic.removeSchool(index) }
    fun addCatalogSpell(entry: SpellCatalogEntry): Boolean = sync { application.magic.addCatalogSpell(entry) } ?: false
    fun addCustomSpell(spell: KnownSpell): String = sync { application.magic.addCustomSpell(spell) } ?: ""
    fun updateSpell(spell: KnownSpell) = sync { application.magic.updateSpell(spell) }
    fun setSpellLearned(uid: String, learned: Boolean) = sync { application.magic.setSpellLearned(uid, learned) }
    fun removeSpell(uid: String) = sync { application.magic.removeSpell(uid) }

    fun setGearLoadAutomatic(enabled: Boolean) = sync { application.equipment.setLoadAutomatic(enabled) }
    fun setGearManualLoad(value: Double) = sync { application.equipment.setManualLoad(value) }
    fun syncCatalogGearLoads(entries: List<GearCatalogEntry>) = sync { application.equipment.syncCatalogLoads(entries) }
    fun addCatalogGear(entry: GearCatalogEntry): String = sync { application.equipment.addCatalog(entry) } ?: ""
    fun addCustomGear(item: GearItem): String = sync { application.equipment.addCustom(item) } ?: ""
    fun updateGearItem(item: GearItem) = sync { application.equipment.updateItem(item) }
    fun setGearItemCarried(uid: String, carried: Boolean) = sync { application.equipment.setItemCarried(uid, carried) }
    fun setGearItemQuantity(uid: String, quantity: Int) = sync { application.equipment.setItemQuantity(uid, quantity) }
    fun removeGearItem(uid: String) = sync { application.equipment.removeItem(uid) }

    fun setPortrait(uri: String?) = descriptive { sheet.setPortrait(uri) }
    fun toggleCondition(condition: CharacterConditionId) = sync { application.sheet.toggleCondition(condition) }
    fun setConditions(conditions: Set<CharacterConditionId>) = sync { application.sheet.setConditions(conditions) }
    fun setResourceHidden(resource: CharacterSheetResourceId, hidden: Boolean) = descriptive { sheet.setResourceHidden(resource, hidden) }
    fun setSkillGroups(groups: List<SheetGroup>) = descriptive { sheet.setSkillGroups(groups) }
    fun setDevelopmentGroups(groups: List<SheetGroup>) = descriptive { sheet.setDevelopmentGroups(groups) }
    fun setNotes(notes: String) = descriptive { sheet.setNotes(notes) }
    fun addNote(title: String, body: String = ""): String? = sync { application.sheet.addNote(title, body) }
    fun updateNote(id: String, title: String, body: String) = descriptive { sheet.updateNote(id, title, body) }
    fun removeNote(id: String) = descriptive { sheet.removeNote(id) }
    fun setConditionOverride(condition: CharacterConditionId, title: String?, description: String?) = sync { application.sheet.setConditionOverride(condition, title, description) }
    fun resetConditionOverride(condition: CharacterConditionId) = sync { application.sheet.resetConditionOverride(condition) }
    fun addCustomCondition(title: String, description: String = "", active: Boolean = false): String? = sync { application.sheet.addCustomCondition(title, description, active) }
    fun updateCustomCondition(id: String, title: String, description: String, active: Boolean? = null) = sync { application.sheet.updateCustomCondition(id, title, description, active) }
    fun setCustomConditionActive(id: String, active: Boolean) = sync { application.sheet.setCustomConditionActive(id, active) }
    fun removeCustomCondition(id: String) = sync { application.sheet.removeCustomCondition(id) }

    fun canonicalBalance(characterId:String):Amount? = canonicalStates[characterId]?.let { balance(it.ledger) }

    fun exportActiveCharacter(): String = canonicalStates[snapshot.activeCharacterId]?.let(::encodeEnvelope) ?: application.transfer.exportActive()
    fun importCharacter(raw: String): CharacterTransferImportResult {
        val result=sync { application.transfer.importCharacter(raw) } ?: CharacterTransferImportResult.Rejected(CharacterTransferRejectReason.INVALID_FILE)
        if(result is CharacterTransferImportResult.Imported) canonicalViewGeneration++
        return result
    }

    fun createCharacter() = sync { application.character.createCharacter() }
    fun selectCharacter(id: String) {
        selectionGeneration++
        if(canonicalMode) { require(snapshot.characters.any { it.id==id });readOnlySelection=id;canonicalLoadOutcome=canonicalStates[id]?.let { LoadResult.Loaded(it) } ?: LoadResult.Missing(id);application=createApplication();refresh();acquisition.activateCurrentView() }
        else sync { application.character.selectCharacter(id) }
    }
    fun deleteActive() = sync { application.character.deleteActive() }
    fun undoLast(): Boolean = sync { application.undoLast() } ?: false
}
