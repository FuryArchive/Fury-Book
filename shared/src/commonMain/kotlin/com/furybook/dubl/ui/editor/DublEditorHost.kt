package com.furybook.dubl.ui.editor

import com.furybook.dubl.application.CharacterRestorePoint
import com.furybook.dubl.model.AttributeId
import com.furybook.dubl.model.DevelopmentAcquisitionRequest
import com.furybook.dubl.model.DevelopmentAcquisitionResult
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
}
