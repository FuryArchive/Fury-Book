package com.furybook.android.state

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
class CharacterController(
    repository: CharacterStore,
    extrasRepository: CharacterExtrasStore,
) {
    private val application = DublApplication(
        characterStore = repository,
        extrasStore = extrasRepository,
        idFactory = { UUID.randomUUID().toString() },
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

    private inline fun <T> sync(block: () -> T): T {
        val result = block()
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
    fun selectCharacter(id: String) = sync { application.character.selectCharacter(id) }
    fun deleteActive() = sync { application.character.deleteActive() }
    fun undoLast(): Boolean = sync { application.undoLast() }
}
