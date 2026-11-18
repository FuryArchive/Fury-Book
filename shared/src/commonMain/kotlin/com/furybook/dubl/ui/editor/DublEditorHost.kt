package com.furybook.dubl.ui.editor

import com.furybook.dubl.application.CharacterRestorePoint
import com.furybook.dubl.model.AttributeId
import com.furybook.dubl.model.DevelopmentAcquisitionRequest
import com.furybook.dubl.model.DevelopmentAcquisitionResult
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
}
