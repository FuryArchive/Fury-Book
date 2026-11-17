package com.furybook.dubl.ui.sheet

import com.furybook.dubl.content.DublResourceToggleModel
import com.furybook.dubl.model.AttributeId
import com.furybook.dubl.model.CharacterConditionId
import com.furybook.dubl.model.CharacterSheetResourceId
import com.furybook.dubl.model.SheetGroup

/**
 * Edit operations of the shared sheet. Every member is a one-line delegate to the matching
 * `DublApplication` call on each platform; no platform adds rules of its own.
 */
interface DublSheetEditHost : DublSheetHost {
    fun setIdentity(name: String, concept: String, size: Int, legs: Int, manaEnabled: Boolean)
    fun setEconomy(total: Int, creation: Int, adjustment: Int, abilityPointsOverride: Int?)
    fun completeCreation()
    fun reopenCreation()
    fun changeAttribute(id: AttributeId, delta: Int)

    fun setHealthMaximumOverride(value: Int?)
    fun setEnduranceMaximumOverride(value: Int?)
    fun setManaMaximumOverride(value: Int?)

    fun addCustomResource(name: String, maximum: Int, current: Int = maximum): String?
    fun updateCustomResource(uid: String, name: String, current: Int, maximum: Int)
    fun removeCustomResource(uid: String)
    fun resourceToggleModels(): List<DublResourceToggleModel>
    fun setResourceHidden(resource: CharacterSheetResourceId, hidden: Boolean)

    fun setConditionOverride(condition: CharacterConditionId, title: String?, description: String?)
    fun resetConditionOverride(condition: CharacterConditionId)
    fun addCustomCondition(title: String, description: String = "", active: Boolean = false): String?
    fun updateCustomCondition(id: String, title: String, description: String, active: Boolean? = null)
    fun removeCustomCondition(id: String)

    fun setSkillGroups(groups: List<SheetGroup>)
    fun setDevelopmentGroups(groups: List<SheetGroup>)

    fun addNote(title: String, body: String = ""): String?
    fun updateNote(id: String, title: String, body: String)
    fun removeNote(id: String)

    fun setPortrait(uri: String?)
}
