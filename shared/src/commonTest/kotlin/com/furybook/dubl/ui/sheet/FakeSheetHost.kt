package com.furybook.dubl.ui.sheet

import com.furybook.content.FcpComposition
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
import com.furybook.dubl.model.DublCharacter
import com.furybook.dubl.model.SheetGroup

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
) : DublSheetEditHost {
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
    override val extras get() = application.activeExtras
    override val canUndo get() = application.canUndo
    override val conditionCatalog = loader.loadConditions()
    override val developmentCatalog = loader.loadDevelopment()
    override val skillEffectCatalog = loader.loadSkillEffects()

    override fun resourceMeters(): List<DublResourceMeterModel> =
        DublUiRenderModels.resourceMeters(composition, active)

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
}
