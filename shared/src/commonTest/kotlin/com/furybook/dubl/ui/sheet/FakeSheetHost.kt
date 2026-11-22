package com.furybook.dubl.ui.sheet

import com.furybook.dubl.model.GearCatalogEntry
import com.furybook.dubl.model.GearItem
import com.furybook.dubl.model.KnownSpell
import com.furybook.dubl.model.MagicEquipmentCatalog
import com.furybook.dubl.model.SpellCatalogEntry
import com.furybook.dubl.model.SkillCategory

import com.furybook.content.FcpComposition
import com.furybook.content.FcpUiComponent
import com.furybook.content.FcpUiContribution
import com.furybook.content.FcpUiSurface
import com.furybook.dubl.content.DublUiBinding
import com.furybook.dubl.content.DublUiFeature
import com.furybook.dubl.content.DublUiMount
import com.furybook.dubl.content.DublUiRegistry
import com.furybook.content.FcpContentPack
import com.furybook.content.FcpTextSource
import com.furybook.dubl.application.DublApplication
import com.furybook.dubl.content.DublFcp
import com.furybook.dubl.content.DublFcpCatalogLoader
import com.furybook.dubl.content.DublResourceMeterModel
import com.furybook.dubl.content.DublResourceToggleModel
import com.furybook.dubl.content.DublUiRenderModels
import com.furybook.dubl.data.CharacterExtrasStore
import com.furybook.dubl.data.InMemoryCharacterStore
import com.furybook.dubl.model.AppSnapshot
import com.furybook.dubl.model.AttributeId
import com.furybook.dubl.model.CharacterConditionId
import com.furybook.dubl.model.CharacterSheetExtras
import com.furybook.dubl.model.CharacterSheetResourceId
import com.furybook.dubl.model.DevelopmentCatalog
import com.furybook.dubl.model.DublCharacter
import com.furybook.dubl.model.SheetGroup
import com.furybook.dubl.model.SkillEffectCatalog
import com.furybook.dubl.application.CharacterRestorePoint
import com.furybook.dubl.model.DevelopmentAcquisitionRequest
import com.furybook.dubl.model.DevelopmentAcquisitionResult
import com.furybook.dubl.model.UntrainedRule
import com.furybook.dubl.ui.editor.DublEditorHost
import com.furybook.dubl.ui.editor.editorCatalogFor
import com.furybook.dubl.model.ChiCatalog
import com.furybook.dubl.model.DevelopmentEntry

private class InMemoryExtrasStore : CharacterExtrasStore {
    private val values = linkedMapOf<String, CharacterSheetExtras>()
    override fun load(characterId: String): CharacterSheetExtras = values[characterId] ?: CharacterSheetExtras()
    override fun save(characterId: String, extras: CharacterSheetExtras) { values[characterId] = extras }
    override fun delete(characterId: String) { values.remove(characterId) }
}

private val fakePackFiles = mapOf(
    "packs/dubl/manifest.json" to """
        {
          "format":"fury.content-pack","formatVersion":1,"id":"dubl-3.69","name":"DUBL 3.69","version":"3.69",
          "ruleset":{"id":"dubl","version":"3.69","engineApi":1},
          "modules":[{"id":"core","name":"Core","required":true,"enabledByDefault":true}],
          "entries":[
            {"kind":"dubl.skills","path":"content/skills.json","modules":["core"],"order":10},
            {"kind":"dubl.conditions","path":"content/conditions.json","modules":["core"],"order":20},
            {"kind":"dubl.development","path":"content/dev.json","modules":["core"],"order":100},
            {"kind":"dubl.magic-equipment","path":"content/magic.json","modules":["core"],"order":300},
            {"kind":"dubl.skill-effects","path":"content/effects.json","modules":["core"],"order":400}
          ]
        }
    """.trimIndent(),
    "packs/dubl/content/skills.json" to """{"rankCosts":[0],"skills":[]}""",
    "packs/dubl/content/conditions.json" to """{"conditions":[{"id":"hurt","name":"Hurt","description":"x"}]}""",
    "packs/dubl/content/dev.json" to """{"version":"core","entries":[{"id":"a","name":"A","cost":1}]}""",
    "packs/dubl/content/magic.json" to """{"version":"1","spells":[],"gear":[]}""",
    "packs/dubl/content/effects.json" to """{"version":"1","effects":[]}""",
)

/** Real [DublApplication] over in-memory stores, for sheet tests. */
class FakeSheetHost(
    character: DublCharacter,
    extras: CharacterSheetExtras = CharacterSheetExtras(),
    override val chiPackEnabled: Boolean = false,
    developmentCatalogOverride: DevelopmentCatalog? = null,
    skillEffectCatalogOverride: SkillEffectCatalog? = null,
    chiCatalogOverride: ChiCatalog? = null,
    magicEquipmentCatalogOverride: MagicEquipmentCatalog? = null,
) : DublEditorHost, com.furybook.dubl.ui.characters.DublCharactersHost {
    private val extrasStore = InMemoryExtrasStore().also { it.save(character.id, extras) }
    private val application = DublApplication(
        characterStore = InMemoryCharacterStore(AppSnapshot(listOf(character), character.id)),
        extrasStore = extrasStore,
        idFactory = { "fake-${nextId++}" },
    )
    private var nextId = 1
    private val loader = DublFcpCatalogLoader(
        FcpContentPack.load("packs/dubl", FcpTextSource(fakePackFiles::get)),
    )
    private val composition = FcpComposition.resolve(
        manifests = listOf(loader.pack.manifest),
        requiredPackIds = setOf(DublFcp.PACK_ID),
        enabledPackIds = emptySet(),
    )

    override val active get() = application.active
    override val characters get() = application.snapshot.characters
    override val activeId get() = application.snapshot.activeCharacterId
    override val archive = com.furybook.dubl.ui.characters.InMemoryFuryArchiveStore()
    override fun portraitOf(id: String): String? = extrasStore.load(id).portraitUri
    override fun select(id: String) = application.character.selectCharacter(id)
    override fun create() = application.character.createCharacter()
    override fun delete(id: String) { application.character.selectCharacter(id); application.character.deleteActive() }
    override fun duplicate(id: String): String? = application.character.duplicate(id)
    override fun exportCharacter(id: String): String = application.transfer.export(id).orEmpty()
    override fun importCharacter(raw: String) = application.transfer.importCharacter(raw)
    override val extras get() = application.activeExtras
    override val canUndo get() = application.canUndo
    override val conditionCatalog = loader.loadConditions()
    override val canonicalDevelopmentCatalog = developmentCatalogOverride ?: loader.loadDevelopment()
    override val developmentCatalog: DevelopmentCatalog get() = editorCatalogFor(this, active)
    override val chiCatalog = chiCatalogOverride ?: ChiCatalog("disabled", emptyList(), emptyList())
    override val skillEffectCatalog = skillEffectCatalogOverride ?: loader.loadSkillEffects()
    override val magicEquipmentCatalog = magicEquipmentCatalogOverride ?: loader.loadMagicEquipment()

    override fun resourceMeters(): List<DublResourceMeterModel> =
        DublUiRenderModels.resourceMeters(composition, active)

    /** The fake pack has no chi pack; with [chiPackEnabled] it stands in the chi pack's economy XP-line mount. */
    override fun uiMounts(surface: String, component: String): List<DublUiMount> {
        val real = DublUiRegistry.mounts(composition, surface, component)
        val chiXpLine = surface == FcpUiSurface.CHARACTER_ECONOMY && component == FcpUiComponent.XP_LINE
        val chiTab = surface == FcpUiSurface.DEVELOPMENT_TABS && component == FcpUiComponent.DEVELOPMENT_BROWSER
        return if (chiPackEnabled && (chiXpLine || chiTab)) {
            // Same order and icon as the real ЦИ pack's development tab (manifest: order 40, icon "chi").
            real + DublUiMount(DublUiFeature.CHI, FcpUiContribution("fake-chi-${if (chiTab) "tab" else "xp"}", surface, component, DublUiBinding.CHI, "ЦИ", 40, mapOf("icon" to "chi")))
        } else real
    }

    override fun changeResource(resource: CharacterSheetResourceId, delta: Int) {
        when (resource) {
            CharacterSheetResourceId.HEALTH -> application.character.changeHp(delta)
            CharacterSheetResourceId.ENDURANCE -> application.character.changeEndurance(delta)
            CharacterSheetResourceId.MANA -> application.magic.changeMana(delta)
            CharacterSheetResourceId.CHI -> application.development.changeChi(delta)
        }
    }

    override fun changeCustomResource(uid: String, delta: Int) {
        application.character.changeCustomResource(uid, delta)
    }

    override fun restoreMountedResource(resource: CharacterSheetResourceId) {
        if (resource == CharacterSheetResourceId.CHI) application.development.restoreChi()
    }

    override fun toggleCondition(condition: CharacterConditionId) {
        application.sheet.toggleCondition(condition)
    }

    override fun setCustomConditionActive(id: String, active: Boolean) {
        application.sheet.setCustomConditionActive(id, active)
    }

    override fun undoLast(): Boolean = application.undoLast()

    override fun setIdentity(name: String, concept: String, size: Int, legs: Int, manaEnabled: Boolean) {
        application.character.setIdentity(name, concept, size, legs, manaEnabled)
    }
    override fun setEconomy(total: Int, creation: Int, adjustment: Int, abilityPointsOverride: Int?) {
        application.character.setEconomy(total, creation, adjustment, abilityPointsOverride)
    }
    override fun setExperience(total: Int) { application.character.setExperience(total) }
    override fun completeCreation() { application.character.completeCreation() }
    override fun reopenCreation() { application.character.reopenCreation() }
    override fun changeAttribute(id: AttributeId, delta: Int) { application.character.changeAttribute(id, delta) }
    override fun setHealthMaximumOverride(value: Int?) { application.character.setHealthMaximumOverride(value) }
    override fun setEnduranceMaximumOverride(value: Int?) { application.character.setEnduranceMaximumOverride(value) }
    override fun setManaMaximumOverride(value: Int?) { application.character.setManaMaximumOverride(value) }
    override fun addCustomResource(name: String, maximum: Int, current: Int): String? =
        application.character.addCustomResource(name, maximum, current)
    override fun updateCustomResource(uid: String, name: String, current: Int, maximum: Int) {
        application.character.updateCustomResource(uid, name, current, maximum)
    }
    override fun removeCustomResource(uid: String) { application.character.removeCustomResource(uid) }
    override fun resourceToggleModels(): List<DublResourceToggleModel> = DublUiRenderModels.resourceToggles(composition, active)
    override fun setResourceHidden(resource: CharacterSheetResourceId, hidden: Boolean) {
        application.sheet.setResourceHidden(resource, hidden)
    }
    override fun setConditionOverride(condition: CharacterConditionId, title: String?, description: String?) {
        application.sheet.setConditionOverride(condition, title, description)
    }
    override fun resetConditionOverride(condition: CharacterConditionId) { application.sheet.resetConditionOverride(condition) }
    override fun addCustomCondition(title: String, description: String, active: Boolean): String? =
        application.sheet.addCustomCondition(title, description, active)
    override fun updateCustomCondition(id: String, title: String, description: String, active: Boolean?) {
        application.sheet.updateCustomCondition(id, title, description, active)
    }
    override fun removeCustomCondition(id: String) { application.sheet.removeCustomCondition(id) }
    override fun setSkillGroups(groups: List<SheetGroup>) { application.sheet.setSkillGroups(groups) }
    override fun setDevelopmentGroups(groups: List<SheetGroup>) { application.sheet.setDevelopmentGroups(groups) }
    override fun addNote(title: String, body: String): String? = application.sheet.addNote(title, body)
    override fun updateNote(id: String, title: String, body: String) { application.sheet.updateNote(id, title, body) }
    override fun removeNote(id: String) { application.sheet.removeNote(id) }
    override fun setPortrait(uri: String?) { application.sheet.setPortrait(uri) }

    override fun changeSkillRank(skillId: String, delta: Int) { application.skills.changeRank(skillId, delta) }
    override fun addSpecializedSkill(templateId: String, specialization: String): String? =
        application.skills.addSpecialized(templateId, specialization)
    override fun addCustomSkill(name: String, description: String, attributes: List<AttributeId>, untrained: UntrainedRule): String? =
        application.skills.addCustom(name, description, attributes, untrained)
    override fun acquireDevelopment(request: DevelopmentAcquisitionRequest): DevelopmentAcquisitionResult =
        application.development.acquire(developmentCatalog, request)
    override fun setDevelopmentRank(entryId: String, rank: Int, optionIndex: Int) { application.development.setRank(entryId, rank, optionIndex) }
    override fun restorePoint(): CharacterRestorePoint = application.restorePoint()
    override fun restore(point: CharacterRestorePoint): Boolean = application.restore(point)
    override fun setSkillAttributes(skillId: String, attributes: List<AttributeId>) { application.skills.setAttributes(skillId, attributes) }
    override fun setSkillModifier(skillId: String, modifier: Int) { application.skills.setModifier(skillId, modifier) }
    override fun setSkillFormulaNote(skillId: String, note: String) { application.skills.setFormulaNote(skillId, note) }
    override fun setSkillNameOverride(skillId: String, name: String) { application.skills.setNameOverride(skillId, name) }
    override fun setSkillDescriptionOverride(skillId: String, description: String) { application.skills.setDescriptionOverride(skillId, description) }
    override fun setSkillCategoryOverride(skillId: String, category: SkillCategory?) { application.skills.setCategoryOverride(skillId, category) }
    override fun setSkillUntrainedOverride(skillId: String, rule: UntrainedRule?) { application.skills.setUntrainedOverride(skillId, rule) }
    override fun setSkillAutoOverrides(skillId: String, auto6: String?, auto12: String?) { application.skills.setAutoOverrides(skillId, auto6, auto12) }
    override fun resetSkillDefinitionOverrides(skillId: String) { application.skills.resetDefinitionOverrides(skillId) }
    override fun hideSkill(skillId: String) { application.skills.hide(skillId) }
    override fun restoreSkill(skillId: String) { application.skills.restore(skillId) }
    override fun restoreAllSkills() { application.skills.restoreAll() }
    override fun setSkillEffectEnabled(effectId: String, enabled: Boolean) { application.skills.setEffectEnabled(effectId, enabled) }
    override fun deleteDynamicSkill(skillId: String) { application.skills.deleteDynamic(skillId) }
    override fun setPreferredSkillAttribute(skillId: String, attribute: AttributeId?) { application.skills.setPreferredAttribute(skillId, attribute) }

    /** Test helper: creates a new character and makes it active, as the character list does. */
    fun createCharacter() { application.character.createCharacter() }

    override fun addCustomDevelopment(entry: DevelopmentEntry): String? = application.development.addCustom(entry)
    override fun updateCustomDevelopment(entry: DevelopmentEntry): Boolean = application.development.updateCustom(entry)
    override fun removeCustomDevelopment(entryId: String) { application.development.removeCustom(entryId) }
    override fun setDevelopmentOverride(entry: DevelopmentEntry) { application.development.setOverride(entry) }
    override fun resetDevelopmentOverride(entryId: String) { application.development.resetOverride(entryId) }
    override fun setChiEnabled(enabled: Boolean) { application.development.setChiEnabled(enabled) }
    override fun setChiBonusRanks(rank: Int) { application.development.setChiBonusRanks(rank) }

    override fun setMagicManaRank(rank: Int) { application.magic.setManaRank(rank) }
    override fun setMagicSchoolPower(name: String, power: Int) { application.magic.setSchoolPower(name, power) }
    override fun addMagicSchool(name: String, rank: Int, note: String): Boolean = application.magic.addSchool(name, rank, note)
    override fun updateMagicSchool(index: Int, name: String, rank: Int, note: String): Boolean = application.magic.updateSchool(index, name, rank, note)
    override fun removeMagicSchool(index: Int) { application.magic.removeSchool(index) }
    override fun addCatalogSpell(entry: SpellCatalogEntry): Boolean = application.magic.addCatalogSpell(entry)
    override fun addCustomSpell(spell: KnownSpell): String = application.magic.addCustomSpell(spell)
    override fun updateSpell(spell: KnownSpell) { application.magic.updateSpell(spell) }
    override fun setSpellLearned(uid: String, learned: Boolean) { application.magic.setSpellLearned(uid, learned) }
    override fun removeSpell(uid: String) { application.magic.removeSpell(uid) }
    override fun setGearLoadAutomatic(enabled: Boolean) { application.equipment.setLoadAutomatic(enabled) }
    override fun setGearManualLoad(value: Double) { application.equipment.setManualLoad(value) }
    override fun syncCatalogGearLoads(entries: List<GearCatalogEntry>) { application.equipment.syncCatalogLoads(entries) }
    override fun addCatalogGear(entry: GearCatalogEntry): String = application.equipment.addCatalog(entry)
    override fun addCustomGear(item: GearItem): String = application.equipment.addCustom(item)
    override fun updateGearItem(item: GearItem) { application.equipment.updateItem(item) }
    override fun setGearItemCarried(uid: String, carried: Boolean) { application.equipment.setItemCarried(uid, carried) }
    override fun setGearItemQuantity(uid: String, quantity: Int) { application.equipment.setItemQuantity(uid, quantity) }
    override fun removeGearItem(uid: String) { application.equipment.removeItem(uid) }
}
