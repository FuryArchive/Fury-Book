package com.furybook.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
) {
    private val application = DublApplication(
        characterStore = characterStore,
        extrasStore = extrasStore,
        idFactory = { UUID.randomUUID().toString() },
    )
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
        application.equipment.syncCatalogLoads(magicEquipmentCatalog.gear)
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
        canonicalDevelopmentCatalog = composeCanonicalDevelopmentCatalog()
        return installed.manifest
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
                refresh()
            }
        }
    }

    fun setChiPackActive(enabled: Boolean) {
        if (enabled) catalogLoader.verifyChiPack()
        contentPackPreferences.putBoolean(DublChiFcp.PACK_ID, enabled)
        chiPackEnabled = enabled
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

    private inline fun <T> sync(action: () -> T): T {
        val result = action()
        refresh()
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
    fun setSkillNameOverride(skillId: String, name: String) = sync { application.skills.setNameOverride(skillId, name) }
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
    fun acquireDevelopment(request: DevelopmentAcquisitionRequest): DevelopmentAcquisitionResult = sync { application.development.acquire(developmentCatalog, request) }
    fun setDevelopmentOverride(entry: DevelopmentEntry) = sync { application.development.setOverride(entry) }
    fun resetDevelopmentOverride(entryId: String) = sync { application.development.resetOverride(entryId) }
    fun addCustomDevelopment(entry: DevelopmentEntry): String? = sync { application.development.addCustom(entry) }
    fun updateCustomDevelopment(entry: DevelopmentEntry): Boolean = sync { application.development.updateCustom(entry) }
    fun removeCustomDevelopment(entryId: String) = sync { application.development.removeCustom(entryId) }

    fun setMagicManaRank(rank: Int) = sync { application.magic.setManaRank(rank) }
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
    fun setNotes(notes: String) = sync { application.sheet.setNotes(notes) }
    fun addNote(title: String, body: String = ""): String? = sync { application.sheet.addNote(title, body) }
    fun updateNote(id: String, title: String, body: String) = sync { application.sheet.updateNote(id, title, body) }
    fun removeNote(id: String) = sync { application.sheet.removeNote(id) }
    fun setConditionOverride(condition: CharacterConditionId, title: String?, description: String?) = sync { application.sheet.setConditionOverride(condition, title, description) }
    fun resetConditionOverride(condition: CharacterConditionId) = sync { application.sheet.resetConditionOverride(condition) }
    fun addCustomCondition(title: String, description: String = "", active: Boolean = false): String? = sync { application.sheet.addCustomCondition(title, description, active) }
    fun updateCustomCondition(id: String, title: String, description: String, active: Boolean? = null) = sync { application.sheet.updateCustomCondition(id, title, description, active) }
    fun setCustomConditionActive(id: String, active: Boolean) = sync { application.sheet.setCustomConditionActive(id, active) }
    fun removeCustomCondition(id: String) = sync { application.sheet.removeCustomCondition(id) }

    fun exportActiveCharacter(): String = application.transfer.exportActive()
    fun importCharacter(raw: String): CharacterTransferImportResult = sync { application.transfer.importCharacter(raw) }

    fun createCharacter() = sync { application.character.createCharacter() }
    fun selectCharacter(id: String) = sync { application.character.selectCharacter(id) }
    fun deleteActive() = sync { application.character.deleteActive() }
    fun undoLast(): Boolean = sync { application.undoLast() }

    fun importPortrait(source: Path): String? = runCatching {
        val ext = source.fileName.toString().substringAfterLast('.', "png").take(8)
        val portraits = DesktopCharacterStore.defaultDataDirectory().resolve("portraits")
        Files.createDirectories(portraits)
        val target = portraits.resolve("${application.active.id}-${UUID.randomUUID()}.$ext")
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING)
        target.toAbsolutePath().toString()
    }.getOrNull()
}
