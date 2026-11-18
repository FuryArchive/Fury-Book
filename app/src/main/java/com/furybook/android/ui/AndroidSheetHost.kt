package com.furybook.android.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.furybook.android.data.AndroidContentPackState
import com.furybook.android.data.ConditionCatalogRepository
import com.furybook.android.data.DevelopmentCatalogRepository
import com.furybook.android.data.SkillEffectCatalogRepository
import com.furybook.android.state.CharacterController
import com.furybook.content.FcpComposition
import com.furybook.dubl.content.DublResourceMeterModel
import com.furybook.dubl.content.DublResourceToggleModel
import com.furybook.dubl.content.DublUiMount
import com.furybook.dubl.content.DublUiRegistry
import com.furybook.dubl.content.DublUiRenderModels
import com.furybook.dubl.model.AttributeId
import com.furybook.dubl.model.CharacterConditionId
import com.furybook.dubl.model.CharacterSheetExtras
import com.furybook.dubl.model.CharacterSheetResourceId
import com.furybook.dubl.model.ConditionCatalog
import com.furybook.dubl.model.DevelopmentCatalog
import com.furybook.dubl.model.DublCharacter
import com.furybook.dubl.model.SheetGroup
import com.furybook.dubl.model.SkillCategory
import com.furybook.dubl.model.SkillEffectCatalog
import com.furybook.dubl.model.effectiveDevelopmentCatalog
import com.furybook.dubl.model.withoutChiContent
import com.furybook.dubl.application.CharacterRestorePoint
import com.furybook.dubl.model.DevelopmentAcquisitionRequest
import com.furybook.dubl.model.DevelopmentAcquisitionResult
import com.furybook.dubl.model.UntrainedRule
import com.furybook.dubl.ui.editor.DublEditorHost

/** Android adapter exposing [CharacterController] and the bundled catalogs to the shared sheet. */
class AndroidSheetHost(
    private val controller: CharacterController,
    private val context: Context,
    override val chiPackEnabled: Boolean,
) : DublEditorHost {
    private val appContext = context.applicationContext

    override val active: DublCharacter get() = controller.active
    override val extras: CharacterSheetExtras get() = controller.extras
    override val canUndo: Boolean get() = controller.canUndo
    override val conditionCatalog: ConditionCatalog = ConditionCatalogRepository(appContext).load()
    private val canonicalDevelopmentCatalog: DevelopmentCatalog =
        DevelopmentCatalogRepository(appContext).load(includeChi = chiPackEnabled)
    override val developmentCatalog: DevelopmentCatalog
        get() = controller.active
            .effectiveDevelopmentCatalog(canonicalDevelopmentCatalog)
            .let { catalog -> if (chiPackEnabled) catalog else catalog.withoutChiContent() }
    override val skillEffectCatalog: SkillEffectCatalog = SkillEffectCatalogRepository(appContext).load()

    // The pack composition reads installed manifests from disk; resolve it once per host (the host is
    // remembered per chi setting, and the sheet leaves composition while packs are managed elsewhere).
    private val composition: FcpComposition by lazy { AndroidContentPackState.composition(appContext, chiPackEnabled) }
    private var meterCache: Pair<DublCharacter, List<DublResourceMeterModel>>? = null

    /** Meters for the current character, recomputed from the cached composition only when it changes. */
    override fun resourceMeters(): List<DublResourceMeterModel> {
        val character = controller.active
        meterCache?.let { (cachedFor, meters) -> if (cachedFor == character) return meters }
        return DublUiRenderModels.resourceMeters(composition, character).also { meterCache = character to it }
    }

    override fun uiMounts(surface: String, component: String): List<DublUiMount> =
        DublUiRegistry.mounts(composition, surface, component)

    override fun changeResource(resource: CharacterSheetResourceId, delta: Int) {
        controller.changeResource(resource, delta)
    }

    override fun changeCustomResource(uid: String, delta: Int) {
        controller.changeCustomResource(uid, delta)
    }

    override fun restoreMountedResource(resource: CharacterSheetResourceId) {
        controller.restoreMountedResource(resource)
    }

    override fun toggleCondition(condition: CharacterConditionId) {
        controller.toggleCondition(condition)
    }

    override fun setCustomConditionActive(id: String, active: Boolean) {
        controller.setCustomConditionActive(id, active)
    }

    override fun undoLast(): Boolean = controller.undoLast()

    override fun setIdentity(name: String, concept: String, size: Int, legs: Int, manaEnabled: Boolean) {
        controller.setIdentity(name, concept, size, legs, manaEnabled)
    }
    override fun setEconomy(total: Int, creation: Int, adjustment: Int, abilityPointsOverride: Int?) {
        controller.setEconomy(total, creation, adjustment, abilityPointsOverride)
    }
    override fun setExperience(total: Int) { controller.setExperience(total) }
    override fun completeCreation() { controller.completeCreation() }
    override fun reopenCreation() { controller.reopenCreation() }
    override fun changeAttribute(id: AttributeId, delta: Int) { controller.changeAttribute(id, delta) }
    override fun setHealthMaximumOverride(value: Int?) { controller.setHealthMaximumOverride(value) }
    override fun setEnduranceMaximumOverride(value: Int?) { controller.setEnduranceMaximumOverride(value) }
    override fun setManaMaximumOverride(value: Int?) { controller.setManaMaximumOverride(value) }
    override fun addCustomResource(name: String, maximum: Int, current: Int): String? =
        controller.addCustomResource(name, maximum, current)
    override fun updateCustomResource(uid: String, name: String, current: Int, maximum: Int) {
        controller.updateCustomResource(uid, name, current, maximum)
    }
    override fun removeCustomResource(uid: String) { controller.removeCustomResource(uid) }
    override fun resourceToggleModels(): List<DublResourceToggleModel> =
        DublUiRenderModels.resourceToggles(composition, controller.active)
    override fun setResourceHidden(resource: CharacterSheetResourceId, hidden: Boolean) {
        controller.setResourceHidden(resource, hidden)
    }
    override fun setConditionOverride(condition: CharacterConditionId, title: String?, description: String?) {
        controller.setConditionOverride(condition, title, description)
    }
    override fun resetConditionOverride(condition: CharacterConditionId) { controller.resetConditionOverride(condition) }
    override fun addCustomCondition(title: String, description: String, active: Boolean): String? =
        controller.addCustomCondition(title, description, active)
    override fun updateCustomCondition(id: String, title: String, description: String, active: Boolean?) {
        controller.updateCustomCondition(id, title, description, active)
    }
    override fun removeCustomCondition(id: String) { controller.removeCustomCondition(id) }
    override fun setSkillGroups(groups: List<SheetGroup>) { controller.setSkillGroups(groups) }
    override fun setDevelopmentGroups(groups: List<SheetGroup>) { controller.setDevelopmentGroups(groups) }
    override fun addNote(title: String, body: String): String? = controller.addNote(title, body)
    override fun updateNote(id: String, title: String, body: String) { controller.updateNote(id, title, body) }
    override fun removeNote(id: String) { controller.removeNote(id) }
    override fun setPortrait(uri: String?) { controller.setPortrait(uri) }
    override fun changeSkillRank(skillId: String, delta: Int) { controller.changeSkillRank(skillId, delta) }
    override fun addSpecializedSkill(templateId: String, specialization: String): String? =
        controller.addSpecializedSkill(templateId, specialization)
    override fun addCustomSkill(name: String, description: String, attributes: List<AttributeId>, untrained: UntrainedRule): String? =
        controller.addCustomSkill(name, description, attributes, untrained)
    override fun acquireDevelopment(request: DevelopmentAcquisitionRequest): DevelopmentAcquisitionResult =
        controller.acquireDevelopment(developmentCatalog, request)
    override fun setDevelopmentRank(entryId: String, rank: Int, optionIndex: Int) { controller.setDevelopmentRank(entryId, rank, optionIndex) }
    override fun restorePoint(): CharacterRestorePoint = controller.restorePoint()
    override fun restore(point: CharacterRestorePoint): Boolean = controller.restore(point)
    override fun setSkillAttributes(skillId: String, attributes: List<AttributeId>) { controller.setSkillAttributes(skillId, attributes) }
    override fun setSkillModifier(skillId: String, modifier: Int) { controller.setSkillModifier(skillId, modifier) }
    override fun setSkillFormulaNote(skillId: String, note: String) { controller.setSkillFormulaNote(skillId, note) }
    override fun setSkillNameOverride(skillId: String, name: String) { controller.setSkillNameOverride(skillId, name) }
    override fun setSkillDescriptionOverride(skillId: String, description: String) { controller.setSkillDescriptionOverride(skillId, description) }
    override fun setSkillCategoryOverride(skillId: String, category: SkillCategory?) { controller.setSkillCategoryOverride(skillId, category) }
    override fun setSkillUntrainedOverride(skillId: String, rule: UntrainedRule?) { controller.setSkillUntrainedOverride(skillId, rule) }
    override fun setSkillAutoOverrides(skillId: String, auto6: String?, auto12: String?) { controller.setSkillAutoOverrides(skillId, auto6, auto12) }
    override fun resetSkillDefinitionOverrides(skillId: String) { controller.resetSkillDefinitionOverrides(skillId) }
    override fun hideSkill(skillId: String) { controller.hideSkill(skillId) }
    override fun restoreSkill(skillId: String) { controller.restoreSkill(skillId) }
    override fun restoreAllSkills() { controller.restoreAllSkills() }
    override fun setSkillEffectEnabled(effectId: String, enabled: Boolean) { controller.setSkillEffectEnabled(effectId, enabled) }
    override fun deleteDynamicSkill(skillId: String) { controller.deleteDynamicSkill(skillId) }
    override fun setPreferredSkillAttribute(skillId: String, attribute: AttributeId?) { controller.setPreferredSkillAttribute(skillId, attribute) }
}

@Composable
fun rememberAndroidSheetHost(
    controller: CharacterController,
    context: Context,
    chiPackEnabled: Boolean,
): AndroidSheetHost = remember(controller, context, chiPackEnabled) {
    AndroidSheetHost(controller, context, chiPackEnabled)
}
