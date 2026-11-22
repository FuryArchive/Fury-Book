package com.furybook.dubl.ui.editor

import com.furybook.dubl.application.CharacterRestorePoint
import com.furybook.dubl.model.AttributeId
import com.furybook.dubl.model.ChiCatalog
import com.furybook.dubl.model.DevelopmentCatalog
import com.furybook.dubl.model.DevelopmentEntry
import com.furybook.dubl.model.DevelopmentAcquisitionRequest
import com.furybook.dubl.model.DevelopmentAcquisitionResult
import com.furybook.dubl.model.GearCatalogEntry
import com.furybook.dubl.model.GearItem
import com.furybook.dubl.model.KnownSpell
import com.furybook.dubl.model.MagicEquipmentCatalog
import com.furybook.dubl.model.SpellCatalogEntry
import com.furybook.dubl.model.SkillCategory
import com.furybook.dubl.model.UntrainedRule
import com.furybook.dubl.ui.sheet.DublSheetEditHost

/**
 * What the character editor needs beyond the sheet: skill ranks, perks and the restore point behind
 * «Отменить всё». Every member is a one-line delegate to the matching `DublApplication` call.
 */
interface DublEditorHost : DublSheetEditHost {
    fun changeSkillRank(skillId: String, delta: Int)
    fun addSpecializedSkill(templateId: String, specialization: String): String?
    fun addCustomSkill(name: String, description: String, attributes: List<AttributeId>, untrained: UntrainedRule): String?
    fun acquireDevelopment(request: DevelopmentAcquisitionRequest): DevelopmentAcquisitionResult
    fun setDevelopmentRank(entryId: String, rank: Int, optionIndex: Int = 0)
    fun restorePoint(): CharacterRestorePoint
    fun restore(point: CharacterRestorePoint): Boolean

    // Per-skill settings (the old Умения tab's inspector), now in the editor's skill panel.
    fun setSkillAttributes(skillId: String, attributes: List<AttributeId>)
    fun setSkillModifier(skillId: String, modifier: Int)
    fun setSkillFormulaNote(skillId: String, note: String)
    fun setSkillNameOverride(skillId: String, name: String)
    fun setSkillDescriptionOverride(skillId: String, description: String)
    fun setSkillCategoryOverride(skillId: String, category: SkillCategory?)
    fun setSkillUntrainedOverride(skillId: String, rule: UntrainedRule?)
    fun setSkillAutoOverrides(skillId: String, auto6: String?, auto12: String?)
    fun resetSkillDefinitionOverrides(skillId: String)
    fun hideSkill(skillId: String)
    fun restoreSkill(skillId: String)
    fun restoreAllSkills()
    fun setSkillEffectEnabled(effectId: String, enabled: Boolean)
    fun deleteDynamicSkill(skillId: String)
    fun setPreferredSkillAttribute(skillId: String, attribute: AttributeId?)

    // Perk editing and ЦИ setup (the old Навыки tab), now in the editor's Навыки step.
    /** The catalog before this character's local edits and own perks; prices the change list per side. */
    val canonicalDevelopmentCatalog: DevelopmentCatalog
    /** ЦИ techniques; empty when the ЦИ pack is off. */
    val chiCatalog: ChiCatalog
    fun addCustomDevelopment(entry: DevelopmentEntry): String?
    fun updateCustomDevelopment(entry: DevelopmentEntry): Boolean
    fun removeCustomDevelopment(entryId: String)
    fun setDevelopmentOverride(entry: DevelopmentEntry)
    fun resetDevelopmentOverride(entryId: String)
    fun setChiEnabled(enabled: Boolean)
    fun setChiBonusRanks(rank: Int)

    // Magic and gear (the old Магия and Снаряжение tabs), now in the editor's steps and the sheet's blocks.
    /** Rulebook spells and gear of the enabled content packs. */
    val magicEquipmentCatalog: MagicEquipmentCatalog
    fun setMagicManaRank(rank: Int)
    fun setMagicSchoolPower(name: String, power: Int)
    fun addMagicSchool(name: String, rank: Int, note: String): Boolean
    fun updateMagicSchool(index: Int, name: String, rank: Int, note: String): Boolean
    fun removeMagicSchool(index: Int)
    fun addCatalogSpell(entry: SpellCatalogEntry): Boolean
    fun addCustomSpell(spell: KnownSpell): String
    fun updateSpell(spell: KnownSpell)
    fun setSpellLearned(uid: String, learned: Boolean)
    fun removeSpell(uid: String)
    fun setGearLoadAutomatic(enabled: Boolean)
    fun setGearManualLoad(value: Double)
    /** Repairs stored loads of catalog items (older builds stored weight); the sheet runs it whenever it shows a character. */
    fun syncCatalogGearLoads(entries: List<GearCatalogEntry>)
    fun addCatalogGear(entry: GearCatalogEntry): String
    fun addCustomGear(item: GearItem): String
    fun updateGearItem(item: GearItem)
    fun setGearItemCarried(uid: String, carried: Boolean)
    fun setGearItemQuantity(uid: String, quantity: Int)
    fun removeGearItem(uid: String)
}
