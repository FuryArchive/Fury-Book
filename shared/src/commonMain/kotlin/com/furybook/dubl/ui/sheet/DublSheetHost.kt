package com.furybook.dubl.ui.sheet

import com.furybook.dubl.content.DublResourceMeterModel
import com.furybook.dubl.model.CharacterConditionId
import com.furybook.dubl.model.CharacterSheetExtras
import com.furybook.dubl.model.CharacterSheetResourceId
import com.furybook.dubl.model.ConditionCatalog
import com.furybook.dubl.model.DevelopmentCatalog
import com.furybook.dubl.model.DublCharacter
import com.furybook.dubl.model.SkillEffectCatalog

/** Platform boundary used by the shared, adaptive character sheet. */
interface DublSheetHost {
    val active: DublCharacter
    val extras: CharacterSheetExtras
    val canUndo: Boolean
    val conditionCatalog: ConditionCatalog
    val developmentCatalog: DevelopmentCatalog
    val skillEffectCatalog: SkillEffectCatalog

    fun resourceMeters(): List<DublResourceMeterModel>
    fun changeResource(resource: CharacterSheetResourceId, delta: Int)
    fun changeCustomResource(uid: String, delta: Int)
    fun restoreMountedResource(resource: CharacterSheetResourceId)
    fun toggleCondition(condition: CharacterConditionId)
    fun setCustomConditionActive(id: String, active: Boolean)
    fun undoLast(): Boolean
}
