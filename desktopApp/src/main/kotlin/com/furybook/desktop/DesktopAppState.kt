package com.furybook.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.furybook.dubl.acquisition.*
import com.furybook.dubl.actions.*
import com.furybook.dubl.data.DesktopAcquisitionStore
import com.furybook.dubl.data.CharacterTransferCodec
import com.furybook.dubl.data.CharacterTransferRejectReason
import com.furybook.content.FcpContentPack
import com.furybook.content.FcpTextSource
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.furybook.dubl.application.CanonicalWritePolicy
import com.furybook.content.DesktopFcpInstaller
import com.furybook.content.FcpComposition
import com.furybook.content.FcpManifest
import com.furybook.dubl.application.DublApplication
import com.furybook.dubl.content.DublChiFcp
import com.furybook.dubl.content.DublDevelopmentAddon
import com.furybook.dubl.content.DublFcp
import com.furybook.dubl.content.DublResourceMeterModel
import com.furybook.dubl.content.DublResourceToggleModel
import com.furybook.dubl.content.DublUiMount
import com.furybook.dubl.content.DublUiRegistry
import com.furybook.dubl.content.DublUiRenderModels
import com.furybook.dubl.application.CharacterTransferImportResult
import com.furybook.dubl.data.DesktopCharacterExtrasStore
import com.furybook.dubl.data.CharacterStore
import com.furybook.dubl.data.CharacterExtrasStore
import com.furybook.dubl.data.mergeDevelopmentCatalogs
import com.furybook.dubl.data.DesktopCharacterStore
import com.furybook.dubl.model.*
import com.furybook.desktop.data.DesktopCatalogLoader
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.UUID
import java.util.prefs.Preferences

class DesktopAppState(
    private val characterStore: CharacterStore = DesktopCharacterStore(),
    private val extrasStore: CharacterExtrasStore = DesktopCharacterExtrasStore(),
    val localAcquisitionEnabled: Boolean = false,
    private val acquisitionStore: DesktopAcquisitionStore? = DesktopAcquisitionStore(),
    private val acquisitionCatalogProvider: (() -> AcquisitionCatalog)? = null,
    private val developmentCatalogOverride: DevelopmentCatalog? = null,
    val experimentalGameplayEnabled: Boolean = false,
) {
    val canonicalMode: Boolean = localAcquisitionEnabled || acquisitionStore?.hasProtectedHeads() != false
    val readOnlyCanonicalMode: Boolean get() = canonicalMode && !localAcquisitionEnabled
    // No live backend guard evidence exists. The local format must never enter legacy cloud writers.
    val cloudLedgerV2: Boolean = false
    var storageError: String? by mutableStateOf(null)
        private set
    var pendingLegacyMigration: PendingMigration? by mutableStateOf(null)
        private set
    var preparedLegacyMigration: StoredCharacter? by mutableStateOf(null)
        private set
    var declaredLegacyComposition: List<String> by mutableStateOf(emptyList())
        private set
    private val acquisitionService by lazy { AcquisitionService(requireNotNull(acquisitionStore), ::acquisitionCatalog) { UUID.randomUUID().toString() } }
    private var canonicalView: AppSnapshot? = null
    private var canonicalLoads: Map<String, LoadResult> by mutableStateOf(emptyMap())
    private var selectionGeneration = 0L
    private var controllerGeneration = 0L
    private var disposed = false
    var unfundedPlaceholder: Boolean by mutableStateOf(false)
        private set
    var canonicalViewReady: Boolean by mutableStateOf(!canonicalMode)
        private set
    private var application = createApplication()
    private var legacyStorageBlocked = false

    @Volatile private var capturedAcquisitionCatalog: AcquisitionCatalog? = null
    private val acquisitionContentLoad = Mutex()
    var contentRevision: Int by mutableStateOf(0)
        private set
    private fun acquisitionCatalog(): AcquisitionCatalog {
        acquisitionCatalogProvider?.let { return it() }
        return checkNotNull(capturedAcquisitionCatalog) { "Canonical catalog is loading" }
    }
    private fun loadAcquisitionContent(): AcquisitionCatalog {
        val source = FcpTextSource { path -> javaClass.classLoader.getResource(path)?.readText() }
        val packs = contentPackComposition.active.map { manifest ->
            if (manifest.id == DublFcp.PACK_ID || manifest.id == DublChiFcp.PACK_ID)
                FcpContentPack.load("fcp/${manifest.id}", source)
            else DesktopFcpInstaller.openInstalled(installedFcpRoot, manifest)
        }
        return loadAcquisitionCatalog(packs, chiPackEnabled).catalog
    }

    private fun createApplication(): DublApplication {
        if (!canonicalMode) return DublApplication(characterStore, extrasStore, { UUID.randomUUID().toString() })
        val store = acquisitionStore
        val original = characterStore.load()
        val loaded = canonicalLoads
        val view = canonicalView ?: original
        val readOnlyCharacters = object : CharacterStore {
            override fun load() = view
            override fun save(snapshot: AppSnapshot) = error("Legacy character writer is blocked in local acquisition mode")
        }
        val readOnlyExtras = object : CharacterExtrasStore {
            override fun load(characterId: String) = (loaded[characterId] as? LoadResult.Loaded)?.state?.extras ?: extrasStore.load(characterId)
            override fun save(characterId: String, extras: CharacterSheetExtras) = error("Legacy extras writer is blocked in local acquisition mode")
            override fun delete(characterId: String) = error("Legacy extras delete is blocked in local acquisition mode")
        }
        return DublApplication(readOnlyCharacters, readOnlyExtras, { UUID.randomUUID().toString() },
            acquisitionService = store?.let { acquisitionService },
            mutationCoordinator = store?.let { CharacterMutationCoordinator(it, ::acquisitionCatalog) { UUID.randomUUID().toString() } },
            acquisitionUndo = store?.let(::AcquisitionUndo),
            characterCreator = store?.let { AcquisitionCharacterCreator(it, ::acquisitionCatalog, ::localCapabilities) },
            writePolicy = CanonicalWritePolicy.Protected)
    }
    private fun localCapabilities() = AcquisitionCapabilities(localAcquisitionEnabled && canonicalViewReady && !disposed, false, true)
    private fun acquisitionScope() = AcquisitionScope(checkNotNull(acquisitionStore).storageIdentity,snapshot.activeCharacterId,controllerGeneration,selectionGeneration,0)
    private fun currentAcquisitionScope(scope:AcquisitionScope):Boolean = !disposed && scope.storageScopeId==acquisitionStore?.storageIdentity &&
        scope.controllerGeneration==controllerGeneration && scope.selectionGeneration==selectionGeneration && scope.viewCharacterId==snapshot.activeCharacterId
    private fun currentAcquisitionOwner(scope:AcquisitionScope):Boolean = !disposed && scope.storageScopeId==acquisitionStore?.storageIdentity &&
        scope.controllerGeneration==controllerGeneration && scope.accountGeneration==0L
    /** Disabled by default; the supplied store must independently opt into format 2. */
    fun gameplayScope() = GameplayScope(checkNotNull(acquisitionStore).storageIdentity,
        "local-desktop:$controllerGeneration", snapshot.activeCharacterId, selectionGeneration)
    private fun currentGameplayScope(value: GameplayScope) = !disposed && value == gameplayScope()
    private val retainedGameplay by lazy {
        val native = checkNotNull(acquisitionStore)
        val background = object : AcquisitionStore {
            override suspend fun load(characterId: String) = withContext(Dispatchers.IO) { native.load(characterId) }
            override suspend fun compareAndSet(characterId: String, expectedStorageRevision: Long, next: StoredCharacter): CommitResult {
                val captured = gameplayScope()
                return canonicalQueue.withLock { withContext(Dispatchers.IO) {
                    check(currentGameplayScope(captured)) { "Gameplay view changed while waiting for storage" }
                    native.compareAndSet(characterId, expectedStorageRevision, next)
                } }
            }
        }
        GameplayController(background, ::gameplayScope, ::currentGameplayScope,
            { GameplayCapabilities(experimentalGameplayEnabled && localAcquisitionEnabled && canonicalViewReady && !disposed, true) },
            { UUID.randomUUID().toString() })
    }
    val gameplay: GameplayController? get() = if (experimentalGameplayEnabled && canonicalMode) retainedGameplay else null
    suspend fun refreshAfterGameplay(captured: GameplayScope) {
        check(currentGameplayScope(captured)) { "Gameplay view changed" }
        val state = withContext(Dispatchers.IO) {
            (checkNotNull(acquisitionStore).load(captured.characterId) as? LoadResult.Loaded)?.state
                ?: error("Gameplay canonical state unavailable")
        }
        withContext(Dispatchers.Main) {
            check(currentGameplayScope(captured)) { "Gameplay view changed" }
            val current = checkNotNull(canonicalView)
            canonicalLoads = canonicalLoads + (state.character.id to LoadResult.Loaded(state))
            canonicalView = current.copy(characters = current.characters.map { if (it.id == state.character.id) state.character else it })
            application = createApplication(); refresh(); acquisition.activateCurrentView()
        }
    }
    private val canonicalQueue=Mutex()
    var acquisitionUiModel:AcquisitionUiModel? by mutableStateOf(null)
        private set
    val acquisition:AcquisitionController by lazy {
        val port=ApplicationAcquisitionCommandPort(application,checkNotNull(acquisitionStore),::acquisitionCatalog,::localCapabilities,::currentAcquisitionScope,
            { scope,state -> withContext(Dispatchers.Main) {
                check(currentAcquisitionScope(scope)) { "Captured view changed" }
                val current=checkNotNull(canonicalView)
                val characters=current.characters.filterNot { unfundedPlaceholder }.map { if(it.id==state.character.id)state.character else it }.toMutableList()
                if(characters.none { it.id==state.character.id }) characters+=state.character
                canonicalLoads=canonicalLoads+(state.character.id to LoadResult.Loaded(state))
                canonicalView=AppSnapshot(characters,if(scope.characterId!=scope.viewCharacterId)state.character.id else current.activeCharacterId)
                unfundedPlaceholder=false;application=createApplication();refresh();acquisition.activateCurrentView()
            } },object:AcquisitionExecutor {
                override suspend fun <T> read(block:suspend()->T):T = withContext(Dispatchers.IO) { block() }
                override suspend fun <T> run(block:suspend()->T):T {
                    val captured=capturedAcquisitionCommand().scope
                    return canonicalQueue.withLock {
                        check(currentAcquisitionOwner(captured)) { "Captured owner changed" }
                        withContext(Dispatchers.IO) { block() }
                    }
                }
            })
        AcquisitionController(port,::acquisitionScope,{UUID.randomUUID().toString()}) { acquisitionUiModel=it }
    }
    fun reviewNewCharacterFunding(initialXP:Long)=acquisition.reviewNewCharacterFunding(initialXP)
    suspend fun confirmNewCharacterFunding(key:AcquisitionQuoteKey)=acquisition.confirm(key)
    fun dispose() { disposed=true;controllerGeneration++ }

    /** Explicit async bootstrap; Missing is an unfunded view, never an inferred migration/opening. */
    suspend fun initializeCanonicalView() {
        check(canonicalMode)
        try {
        val generation=selectionGeneration
        refreshCanonicalContent()
        val loaded=withContext(Dispatchers.IO) {
            val store=checkNotNull(acquisitionStore);val original=characterStore.load()
            val ids=(original.characters.map { it.id }+store.characterIds()).distinct()
            Triple(original,ids.associateWith { store.load(it) },ids)
        }
        withContext(Dispatchers.Main) {
            check(!disposed && generation==selectionGeneration) { "View changed during loading" }
            val (original,heads,ids)=loaded
            val characters=ids.mapNotNull { id -> when(val value=heads.getValue(id)) {
                is LoadResult.Loaded -> value.state.character
                is LoadResult.MigrationRequired -> original.characters.singleOrNull { it.id==id } ?: throw AcquisitionPreviewUnavailable(value)
                is LoadResult.Missing -> null
                else -> throw AcquisitionPreviewUnavailable(value)
            } }
            unfundedPlaceholder=characters.isEmpty()
            val visible=characters.ifEmpty { listOf(original.activeCharacter) }
            canonicalView=AppSnapshot(visible,snapshot.activeCharacterId.takeIf { id -> visible.any { it.id==id } } ?: visible.first().id)
            canonicalLoads=heads;canonicalViewReady=true;application=createApplication();refresh()
        }
        } catch (failure: Exception) {
            storageError="Каноническое хранилище недоступно. Сохраните исходные файлы для восстановления."
            throw failure
        }
    }
    private val catalogLoader = DesktopCatalogLoader()
    private val contentPackPreferences = Preferences.userRoot().node("com/furybook/content-packs")
    private val installedFcpRoot = DesktopCharacterStore.defaultDataDirectory().resolve("fcp")
    private var installedPackRevision: Int by mutableStateOf(0)

    val corePackManifest = catalogLoader.manifest
    val chiPackManifest = catalogLoader.chiManifest
    private val chiDevelopmentIds = linkedSetOf<String>().apply {
        addAll(catalogLoader.loadChiDevelopment().entries.map { it.id })
        addAll(catalogLoader.chiManifest.claims("dubl.development").map { it.id })
    }

    var chiPackEnabled: Boolean by mutableStateOf(contentPackPreferences.getBoolean(DublChiFcp.PACK_ID, false))
        private set

    val conditionCatalog = catalogLoader.loadConditions()
    private var canonicalDevelopmentCatalog: DevelopmentCatalog by mutableStateOf(developmentCatalogOverride ?: composeCanonicalDevelopmentCatalog())
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

    val activeCharacter: DublCharacter
        get() = if (chiPackEnabled) {
            snapshot.activeCharacter
        } else {
            snapshot.activeCharacter.withoutRuntimeDevelopmentEffects(
                suppressedEntryIds = chiDevelopmentIds,
                suppressChiResource = true,
            )
        }

    init {
        if (!canonicalMode) application.equipment.syncCatalogLoads(magicEquipmentCatalog.gear)
        refresh()
    }

    val contentPackComposition: FcpComposition
        get() {
            installedPackRevision
            val installed = DesktopFcpInstaller.listInstalled(installedFcpRoot)
            val enabled = linkedSetOf<String>()
            if (chiPackEnabled) enabled += DublChiFcp.PACK_ID
            installed.asSequence()
                .filter(DublDevelopmentAddon::isSupported)
                .filter { contentPackPreferences.getBoolean(it.id, false) }
                .mapTo(enabled) { it.id }
            return FcpComposition.resolve(
                manifests = buildList {
                    add(corePackManifest)
                    add(chiPackManifest)
                    addAll(installed)
                },
                requiredPackIds = setOf(DublFcp.PACK_ID),
                enabledPackIds = enabled,
            )
        }

    fun uiMounts(surface: String, component: String): List<DublUiMount> =
        DublUiRegistry.mounts(contentPackComposition, surface, component)

    fun resourceMeterModels(character: DublCharacter = activeCharacter): List<DublResourceMeterModel> =
        DublUiRenderModels.resourceMeters(contentPackComposition, character)

    fun resourceToggleModels(character: DublCharacter = activeCharacter): List<DublResourceToggleModel> =
        DublUiRenderModels.resourceToggles(contentPackComposition, character)

    fun canActivateContentPack(manifest: FcpManifest): Boolean =
        manifest.id == DublFcp.PACK_ID ||
            manifest.id == DublChiFcp.PACK_ID ||
            DublDevelopmentAddon.isSupported(manifest)

    fun installContentPack(archive: Path): FcpManifest {
        val installed = DesktopFcpInstaller.install(
            archive = archive,
            installRoot = installedFcpRoot,
            reservedPackIds = setOf(DublFcp.PACK_ID, DublChiFcp.PACK_ID),
        )
        contentPackPreferences.putBoolean(installed.manifest.id, false)
        installedPackRevision += 1
        capturedAcquisitionCatalog = null; contentRevision++
        canonicalDevelopmentCatalog = composeCanonicalDevelopmentCatalog()
        return installed.manifest
    }

    fun enabledContentPackIds(): Set<String> = contentPackComposition.active.mapTo(linkedSetOf()) { it.id }

    fun applyCloudContentPackState(enabledPackIds: Set<String>) {
        setChiPackActive(DublChiFcp.PACK_ID in enabledPackIds)
        DesktopFcpInstaller.listInstalled(installedFcpRoot).forEach { manifest ->
            val shouldEnable = manifest.id in enabledPackIds
            if (contentPackPreferences.getBoolean(manifest.id, false) != shouldEnable) {
                setContentPackActive(manifest.id, shouldEnable)
            }
        }
    }

    fun setContentPackActive(packId: String, enabled: Boolean) {
        when (packId) {
            DublFcp.PACK_ID -> require(enabled) { "Required FCP ${DublFcp.PACK_ID} cannot be disabled" }
            DublChiFcp.PACK_ID -> setChiPackActive(enabled)
            else -> {
                val manifest = DesktopFcpInstaller.findInstalled(installedFcpRoot, packId)
                    ?: error("Installed FCP not found: $packId")
                if (enabled && !contentPackPreferences.getBoolean(packId, false)) {
                    validateExternalActivation(manifest)
                }
                contentPackPreferences.putBoolean(packId, enabled)
                canonicalDevelopmentCatalog = composeCanonicalDevelopmentCatalog()
                installedPackRevision += 1
        capturedAcquisitionCatalog = null; contentRevision++
                refresh()
            }
        }
    }

    fun setChiPackActive(enabled: Boolean) {
        if (enabled) catalogLoader.verifyChiPack()
        contentPackPreferences.putBoolean(DublChiFcp.PACK_ID, enabled)
        chiPackEnabled = enabled
        capturedAcquisitionCatalog = null; contentRevision++
        canonicalDevelopmentCatalog = composeCanonicalDevelopmentCatalog()
        chiCatalog = if (enabled) catalogLoader.loadChi() else ChiCatalog("disabled", emptyList(), emptyList())
        refresh()
    }

    private fun composeCanonicalDevelopmentCatalog(): DevelopmentCatalog {
        val composition = contentPackComposition
        val layers = mutableListOf(
            catalogLoader.loadDevelopment(includeChi = composition.isActive(DublChiFcp.PACK_ID)),
        )
        composition.active
            .asSequence()
            .filter { it.id != DublFcp.PACK_ID && it.id != DublChiFcp.PACK_ID }
            .forEach { manifest ->
                layers += DublDevelopmentAddon.load(DesktopFcpInstaller.openInstalled(installedFcpRoot, manifest))
            }
        return mergeDevelopmentCatalogs(*layers.toTypedArray())
    }

    private fun validateExternalActivation(manifest: FcpManifest) {
        DublDevelopmentAddon.requireSupported(manifest)
        val candidate = DublDevelopmentAddon.load(DesktopFcpInstaller.openInstalled(installedFcpRoot, manifest))
        val layers = mutableListOf(
            catalogLoader.loadDevelopment(includeChi = true),
        )
        DesktopFcpInstaller.listInstalled(installedFcpRoot)
            .asSequence()
            .filter { it.id != manifest.id }
            .filter(DublDevelopmentAddon::isSupported)
            .filter { contentPackPreferences.getBoolean(it.id, false) }
            .map { installed -> DublDevelopmentAddon.load(DesktopFcpInstaller.openInstalled(installedFcpRoot, installed)) }
            .forEach(layers::add)
        layers += candidate
        mergeDevelopmentCatalogs(*layers.toTypedArray())
    }
    fun refresh() {
        snapshot = application.snapshot
        extras = application.activeExtras
    }

    /** The root routes protected descriptive actions into one detached explicit review. */
    var onCanonicalNonFinancialReview: ((DublApplication.() -> Unit) -> Unit)? = null
    private fun <T> descriptive(edit: DublApplication.() -> T): T? {
        if (canonicalMode) {
            val review=onCanonicalNonFinancialReview
            if (review == null) storageError="Редактор недоступен; сохранённый лист не изменён"
            else review { edit(); Unit }
            return null
        }
        return sync { application.edit() }
    }
    suspend fun refreshCanonicalContent() = acquisitionContentLoad.withLock {
        if (canonicalMode && acquisitionCatalogProvider == null && capturedAcquisitionCatalog == null) {
            val generation=contentRevision
            val captured=withContext(Dispatchers.IO) { loadAcquisitionContent() }
            if(!disposed && generation==contentRevision) {
                capturedAcquisitionCatalog=captured
                // Publish after the off-main load, so visible quotes retry against the installed catalog.
                contentRevision++
            }
        }
    }

    private inline fun <T> sync(action: () -> T): T? {
        if (canonicalMode) {
            storageError = "Старое редактирование заблокировано для локального журнала. Используйте каноническую операцию."
            return null
        }
        if (legacyStorageBlocked) return null
        return try {
            val result = action()
            storageError = null
            refresh()
            result
        } catch (failure: Exception) {
            // Legacy sessions may change memory before their adapter throws. Reload persisted state.
            storageError = "Ошибка сохранения: ${failure.message}. Проверьте файл перед повтором."
            runCatching { application = createApplication(); refresh() }.onFailure { legacyStorageBlocked = true }
            null
        }
    }

    fun localStoredCharacter(): LoadResult = canonicalLoads[snapshot.activeCharacterId] ?: LoadResult.Missing(snapshot.activeCharacterId)
    /** Declaration edits invalidate both confirmation paths, including unsupported compositions. */
    fun declareLegacyComposition(activePackIds: List<String>) {
        check(localAcquisitionEnabled)
        selectionGeneration++
        pendingLegacyMigration = null
        preparedLegacyMigration = null
        storageError = "Состав исходного листа изменён. Подготовьте миграцию заново."
        declaredLegacyComposition = activePackIds.toList()
    }
    fun prepareLocalMigration(activePackIds: List<String>): MigrationResult {
        check(localAcquisitionEnabled)
        // Clear before anything that can throw. A failed new attempt never leaves an old result.
        pendingLegacyMigration = null
        preparedLegacyMigration = null
        declareLegacyComposition(activePackIds)
        // The caller explicitly declares the source composition. Current preferences are not evidence.
        // Only the hash-verified frozen old payload may calculate an opening; today's catalog is never used.
        val source = FcpTextSource { path -> javaClass.classLoader.getResource(path)?.readText() }
        val oldCatalog = runCatching {
            loadLegacyCatalogSnapshot(FcpContentPack.load("fcp/${DublFcp.PACK_ID}", source), declaredLegacyComposition)
        }.getOrNull()
        val result = try {
            requireNotNull(acquisitionStore).prepareMigration(snapshot.activeCharacterId, declaredLegacyComposition, oldCatalog)
        } catch (failure: Exception) {
            storageError = "Ошибка подготовки миграции: ${failure.message}. Подготовьте исходный лист заново."
            throw failure
        }
        pendingLegacyMigration = (result as? MigrationResult.NeedsBalanceConfirmation)?.pending
        preparedLegacyMigration = (result as? MigrationResult.Ready)?.state
        storageError = when (result) {
            is MigrationResult.NeedsBalanceConfirmation -> result.pending.reason
            is MigrationResult.Corrupt -> result.reason
            is MigrationResult.Unsupported -> result.reason
            is MigrationResult.Ready -> null
        }
        return result
    }
    fun confirmLocalOpeningBalance(xp: Long, ap: Long, reason: String): CommitResult {
        check(localAcquisitionEnabled)
        val pending = requireNotNull(pendingLegacyMigration) { "Prepare and review the exact legacy source first" }
        require(pending.origin.activePackIds == declaredLegacyComposition) { "Source composition changed; prepare again" }
        val state = confirmLegacyBalance(pending, Amount(xp, ap), reason)
        return commitLocalMigration(state)
    }
    fun commitPreparedLocalMigration(): CommitResult {
        check(localAcquisitionEnabled)
        return commitLocalMigration(requireNotNull(preparedLegacyMigration) { "Prepare and review the legacy migration first" })
    }
    private fun commitLocalMigration(state: StoredCharacter): CommitResult {
        val origin = requireNotNull(state.ledger.opening.legacyMigration?.origin) { "Prepared migration lacks source composition" }
        require(origin.activePackIds == declaredLegacyComposition && origin.characterId == snapshot.activeCharacterId) {
            "Source composition or character changed; prepare again"
        }
        val result: CommitResult = CommitResult.StorageFailure("Используйте асинхронное подтверждение миграции")
        storageError = when (result) {
            CommitResult.Committed -> null
            CommitResult.Conflict -> "Исходный лист изменился. Подготовьте миграцию заново."
            is CommitResult.StorageFailure -> result.reason
        }
        if (result == CommitResult.Committed) { pendingLegacyMigration = null; preparedLegacyMigration = null; reloadCanonicalView() }
        return result
    }
    private fun reloadCanonicalView() { application=createApplication();refresh() }
    suspend fun commitPreparedLocalMigrationAsync():CommitResult = commitMigrationAsync(checkNotNull(preparedLegacyMigration))
    suspend fun confirmLocalOpeningBalanceAsync(xp:Long,ap:Long,reason:String):CommitResult {
        val pending=checkNotNull(pendingLegacyMigration)
        require(pending.origin.activePackIds==declaredLegacyComposition)
        return commitMigrationAsync(confirmLegacyBalance(pending,Amount(xp,ap),reason))
    }
    private suspend fun commitMigrationAsync(state:StoredCharacter):CommitResult {
        val scope=acquisitionScope()
        val origin=checkNotNull(state.ledger.opening.legacyMigration?.origin)
        require(origin.characterId==scope.characterId && origin.activePackIds==declaredLegacyComposition)
        check(currentAcquisitionScope(scope) && localCapabilities().localDurable)
        val result=withContext(Dispatchers.IO) { checkNotNull(acquisitionStore).compareAndSet(scope.characterId,-1,state) }
        if(result==CommitResult.Committed && currentAcquisitionScope(scope)) { pendingLegacyMigration=null;preparedLegacyMigration=null;initializeCanonicalView() }
        return result
    }
    suspend fun previewLocalAcquisition(request: AcquisitionRequest): AcquisitionPlan {
        check(localAcquisitionEnabled)
        return application.development.previewAcquisition(request)
    }
    suspend fun applyLocalAcquisition(command: ConfirmAcquisition): ApplyResult {
        check(localAcquisitionEnabled)
        val result = application.development.confirmAcquisition(command)
        storageError = when (result) {
            is ApplyResult.StorageFailure -> result.reason
            is ApplyResult.Stale -> "Предпросмотр устарел. Проверьте цену заново."
            is ApplyResult.Blocked -> result.issues.joinToString { it.reason }
            is ApplyResult.IdentityConflict -> result.reason
            else -> null
        }
        if (result is ApplyResult.Applied || result is ApplyResult.AlreadyApplied || result is ApplyResult.AlreadyUndone) initializeCanonicalView()
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
        descriptive { character.addCustomResource(name, maximum, current) }
    fun updateCustomResource(uid: String, name: String, current: Int, maximum: Int) =
        descriptive { character.updateCustomResource(uid, name, current, maximum) }
    fun changeCustomResource(uid: String, delta: Int) = descriptive { character.changeCustomResource(uid, delta) }
    fun removeCustomResource(uid: String) = descriptive { character.removeCustomResource(uid) }

    fun changeSkillRank(skillId: String, delta: Int) = sync { application.skills.changeRank(skillId, delta) }
    fun setSkillAttributes(skillId: String, attributes: List<AttributeId>) = descriptive { skills.setAttributes(skillId, attributes) }
    fun setSkillModifier(skillId: String, modifier: Int) = descriptive { skills.setModifier(skillId, modifier) }
    fun setSkillFormulaNote(skillId: String, note: String) = descriptive { skills.setFormulaNote(skillId, note) }
    fun setSkillNameOverride(skillId: String, name: String) = descriptive { skills.setNameOverride(skillId, name) }
    fun setSkillDescriptionOverride(skillId: String, description: String) = descriptive { skills.setDescriptionOverride(skillId, description) }
    fun setSkillCategoryOverride(skillId: String, category: SkillCategory?) = descriptive { skills.setCategoryOverride(skillId, category) }
    fun setSkillUntrainedOverride(skillId: String, rule: UntrainedRule?) = descriptive { skills.setUntrainedOverride(skillId, rule) }
    fun setSkillAutoOverrides(skillId: String, auto6: String?, auto12: String?) = descriptive { skills.setAutoOverrides(skillId, auto6, auto12) }
    fun resetSkillDefinitionOverrides(skillId: String) = descriptive { skills.resetDefinitionOverrides(skillId) }
    fun hideSkill(skillId: String) = descriptive { skills.hide(skillId) }
    fun restoreSkill(skillId: String) = descriptive { skills.restore(skillId) }
    fun restoreAllSkills() = descriptive { skills.restoreAll() }
    fun setSkillEffectEnabled(effectId: String, enabled: Boolean) = descriptive { skills.setEffectEnabled(effectId, enabled) }
    fun addSpecializedSkill(templateId: String, specialization: String): String? = sync { application.skills.addSpecialized(templateId, specialization) }
    fun addCustomSkill(name: String, description: String, attributes: List<AttributeId>, untrained: UntrainedRule): String? =
        sync { application.skills.addCustom(name, description, attributes, untrained) }
    fun deleteDynamicSkill(skillId: String) = sync { application.skills.deleteDynamic(skillId) }
    fun setPreferredSkillAttribute(skillId: String, attribute: AttributeId?) = descriptive { skills.setPreferredAttribute(skillId, attribute) }

    fun setDevelopmentRank(entryId: String, rank: Int, optionIndex: Int = 0) = sync { application.development.setRank(entryId, rank, optionIndex) }
    fun acquireDevelopment(request: DevelopmentAcquisitionRequest): DevelopmentAcquisitionResult = sync { application.development.acquire(developmentCatalog, request) } ?: DevelopmentAcquisitionResult(false, DevelopmentAcquisitionPlanner(activeCharacter, developmentCatalog).plan(request), storageError ?: "Storage unavailable")
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

    fun setGearLoadAutomatic(enabled: Boolean) = descriptive { equipment.setLoadAutomatic(enabled) }
    fun setGearManualLoad(value: Double) = descriptive { equipment.setManualLoad(value) }
    fun syncCatalogGearLoads(entries: List<GearCatalogEntry>) = sync { application.equipment.syncCatalogLoads(entries) }
    fun addCatalogGear(entry: GearCatalogEntry): String = descriptive { equipment.addCatalog(entry) } ?: ""
    fun addCustomGear(item: GearItem): String = descriptive { equipment.addCustom(item) } ?: ""
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
    fun setNotes(notes: String) = descriptive { sheet.setNotes(notes) }
    fun addNote(title: String, body: String = ""): String? = descriptive { sheet.addNote(title, body) }
    fun updateNote(id: String, title: String, body: String) = descriptive { sheet.updateNote(id, title, body) }
    fun removeNote(id: String) = descriptive { sheet.removeNote(id) }
    fun setConditionOverride(condition: CharacterConditionId, title: String?, description: String?) = descriptive { sheet.setConditionOverride(condition, title, description) }
    fun resetConditionOverride(condition: CharacterConditionId) = descriptive { sheet.resetConditionOverride(condition) }
    fun addCustomCondition(title: String, description: String = "", active: Boolean = false): String? = descriptive { sheet.addCustomCondition(title, description, active) }
    fun updateCustomCondition(id: String, title: String, description: String, active: Boolean? = null) = descriptive { sheet.updateCustomCondition(id, title, description, active) }
    fun setCustomConditionActive(id: String, active: Boolean) = descriptive { sheet.setCustomConditionActive(id, active) }
    fun removeCustomCondition(id: String) = descriptive { sheet.removeCustomCondition(id) }

    fun exportActiveCharacter(): String = if (canonicalMode) {
        val loaded = localStoredCharacter()
        when (loaded) {
            is LoadResult.Loaded -> CharacterTransferCodec.encodeEnvelope(loaded.state)
            else -> throw AcquisitionPreviewUnavailable(loaded)
        }
    } else application.transfer.exportActive()
    fun importCharacter(raw: String): CharacterTransferImportResult = sync { application.transfer.importCharacter(raw) } ?: CharacterTransferImportResult.Rejected(CharacterTransferRejectReason.INVALID_FILE)

    fun createCharacter() = sync { application.character.createCharacter() }
    fun selectCharacter(id: String) {
        selectionGeneration++;pendingLegacyMigration=null;preparedLegacyMigration=null
        if(canonicalMode) {
            val view=canonicalView ?: snapshot
            require(view.characters.any { it.id==id });canonicalView=view.copy(activeCharacterId=id);application=createApplication();refresh();acquisition.activateCurrentView()
        } else sync { application.character.selectCharacter(id) }
    }
    fun deleteActive() = sync { application.character.deleteActive() }
    fun undoLast(): Boolean = sync { application.undoLast() } ?: false

    fun importPortrait(source: Path): String? = runCatching {
        val ext = source.fileName.toString().substringAfterLast('.', "png").take(8)
        val portraits = DesktopCharacterStore.defaultDataDirectory().resolve("portraits")
        Files.createDirectories(portraits)
        val target = portraits.resolve("${application.active.id}-${UUID.randomUUID()}.$ext")
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING)
        target.toAbsolutePath().toString()
    }.getOrNull()
}
