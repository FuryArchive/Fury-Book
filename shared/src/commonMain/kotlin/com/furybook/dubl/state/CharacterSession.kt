package com.furybook.dubl.state

import com.furybook.dubl.data.CharacterStore
import com.furybook.dubl.model.AppSnapshot
import com.furybook.dubl.model.AttributeId
import com.furybook.dubl.model.CharacterSkill
import com.furybook.dubl.model.CustomResource
import com.furybook.dubl.model.GearCatalogEntry
import com.furybook.dubl.model.GearItem
import com.furybook.dubl.model.KnownSpell
import com.furybook.dubl.model.MagicEquipmentRules
import com.furybook.dubl.model.MagicSchool
import com.furybook.dubl.model.MagicSchoolCatalog
import com.furybook.dubl.model.SpellCatalogEntry
import com.furybook.dubl.model.DublCharacter
import com.furybook.dubl.model.normalizedLocalCopy
import com.furybook.dubl.model.DevelopmentEntry
import com.furybook.dubl.model.OwnedDevelopment
import com.furybook.dubl.model.SkillCatalog
import com.furybook.dubl.model.UntrainedRule
import com.furybook.dubl.model.resolvedSkills
import com.furybook.dubl.model.developmentRank

internal class CharacterSession(
    private val store: CharacterStore,
    private val idFactory: () -> String,
    private val beforeWrite: () -> Unit = {},
) {
    var snapshot: AppSnapshot = store.load()
        private set

    val active: DublCharacter get() = snapshot.activeCharacter

    internal fun updateActive(transform: (DublCharacter) -> DublCharacter) {
        beforeWrite()
        val activeId = snapshot.activeCharacterId
        val updated = snapshot.characters.map { character ->
            if (character.id == activeId) transform(character).let { if (store is DetachedCharacterStore) stagedResources(character, it) else it.normalized() } else character
        }
        persist(snapshot.copy(characters = updated))
    }

    fun changeAttribute(id: AttributeId, delta: Int) = updateActive { character ->
        val current = character.attributes.getValue(id)
        val nextBase = (current.base + delta).coerceIn(-5, 10)
        val updated = character.copy(
            attributes = character.attributes + (id to current.copy(base = nextBase)),
        )
        if (character.creationComplete) updated else updated.copy(hpCurrent = updated.healthMaximum)
    }

    fun setExperience(total: Int) = updateActive { character ->
        val clean = total.coerceAtLeast(0)
        character.copy(
            experience = clean,
            creationExperience = if (character.creationComplete) {
                character.creationExperience.coerceAtMost(clean)
            } else {
                clean
            },
        )
    }

    fun setCreationExperience(value: Int) = updateActive { character ->
        character.copy(creationExperience = value.coerceIn(0, character.experience.coerceAtLeast(0)))
    }

    fun setXpAdjustment(value: Int) = updateActive { character ->
        character.copy(xpAdjustment = value.coerceIn(-1_000_000, 1_000_000))
    }

    fun setAbilityPointsOverride(value: Int?) = updateActive { character ->
        character.copy(abilityPointsOverride = value?.coerceAtLeast(0))
    }

    fun completeCreation() = updateActive { character ->
        if (character.creationComplete) return@updateActive character
        val creationXp = character.effectiveCreationExperience.coerceAtMost(character.experience)
        val fullMana = if (character.manaEnabled || character.magic.manaRank > 0) character.effectiveManaMaximum else character.manaCurrent
        val fullChi = if (character.chiActive) character.chiMaximum else character.chiCurrent
        character.copy(
            creationExperience = creationXp,
            creationComplete = true,
            hpCurrent = character.healthMaximum,
            manaCurrent = fullMana,
            chiCurrent = fullChi,
        )
    }

    fun reopenCreation() = updateActive { character ->
        character.copy(creationComplete = false)
    }

    fun changeHp(delta: Int) = updateActive { it.copy(hpCurrent = it.hpCurrent + delta) }
    fun changeEndurance(delta: Int) = updateActive { it.copy(enduranceCurrent = it.enduranceCurrent + delta) }
    fun changeMana(delta: Int) = updateActive { it.copy(manaCurrent = it.manaCurrent + delta) }
    fun changeChi(delta: Int) = updateActive { it.copy(chiCurrent = it.chiCurrent + delta) }

    fun setChiEnabled(enabled: Boolean) = updateActive { character ->
        val automaticAccess = character.developmentRank(com.furybook.dubl.model.DevelopmentEffectIds.INTERNAL_CHI) > 0
        if (!enabled && automaticAccess) {
            character
        } else if (!enabled) {
            character.copy(chiEnabled = false, chiCurrent = 0)
        } else {
            val enabledCharacter = character.copy(chiEnabled = true)
            enabledCharacter.copy(
                chiCurrent = if (!character.chiActive) enabledCharacter.chiMaximum else character.chiCurrent,
            )
        }
    }

    fun setChiBonusRanks(rank: Int) = updateActive { character ->
        val next = character.copy(chiBonusRanks = rank.coerceIn(0, 10))
        if (!character.creationComplete && next.chiActive) {
            next.copy(chiCurrent = next.chiMaximum)
        } else {
            next
        }
    }

    fun restoreChi() = updateActive { character ->
        if (character.chiActive) character.copy(chiCurrent = character.chiMaximum) else character
    }

    fun setHealthMaximumOverride(value: Int?) = updateActive { character ->
        character.copy(healthMaximumOverride = value?.coerceAtLeast(0))
    }

    fun setEnduranceMaximumOverride(value: Int?) = updateActive { character ->
        character.copy(enduranceMaximumOverride = value?.coerceAtLeast(0))
    }

    fun setManaMaximumOverride(value: Int?) = updateActive { character ->
        character.copy(
            manaMaximumOverride = value?.coerceAtLeast(0),
            manaEnabled = character.manaEnabled || value != null || character.magic.manaRank > 0,
        )
    }

    fun addCustomResource(name: String, maximum: Int, current: Int = maximum): String? {
        val cleanName = name.trim().replace(Regex("\\s+"), " ")
        if (cleanName.isBlank()) return null
        val uid = newId()
        val max = maximum.coerceAtLeast(0)
        updateActive { character ->
            character.copy(
                customResources = character.customResources + CustomResource(
                    uid = uid,
                    name = cleanName,
                    current = current.coerceIn(0, max),
                    maximum = max,
                ),
            )
        }
        return uid
    }

    fun updateCustomResource(uid: String, name: String, current: Int, maximum: Int) = updateActive { character ->
        val cleanName = name.trim().replace(Regex("\\s+"), " ").ifBlank { "Ресурс" }
        val max = maximum.coerceAtLeast(0)
        character.copy(
            customResources = character.customResources.map { resource ->
                if (resource.uid == uid) resource.copy(
                    name = cleanName,
                    current = current.coerceIn(0, max),
                    maximum = max,
                ) else resource
            },
        )
    }

    fun changeCustomResource(uid: String, delta: Int) = updateActive { character ->
        character.copy(
            customResources = character.customResources.map { resource ->
                if (resource.uid == uid) resource.copy(current = resource.current + delta) else resource
            },
        )
    }

    fun removeCustomResource(uid: String) = updateActive { character ->
        character.copy(customResources = character.customResources.filterNot { it.uid == uid })
    }

    fun changeSkillRank(skillId: String, delta: Int) = updateSkill(skillId) { skill ->
        skill.copy(rank = (skill.rank + delta).coerceIn(0, 10))
    }

    fun setSkillAttributes(skillId: String, attributes: List<AttributeId>) {
        val clean = attributes.distinct()
        if (clean.isEmpty()) return
        updateSkill(skillId) { it.copy(attributes = clean) }
    }

    fun setSkillModifier(skillId: String, modifier: Int) = updateSkill(skillId) {
        it.copy(modifier = modifier.coerceIn(-99, 99))
    }

    fun setSkillFormulaNote(skillId: String, note: String) = updateSkill(skillId) {
        it.copy(formulaNote = note.trim())
    }

    fun setSkillNameOverride(skillId: String, name: String) = updateSkill(skillId) {
        val canonical = com.furybook.dubl.model.SkillCatalog.definition(it.definitionId ?: skillId)?.name.orEmpty()
        val clean = name.trim().replace(Regex("\\s+"), " ")
        it.copy(name = clean.takeUnless { value -> value.isBlank() || value == canonical }.orEmpty())
    }

    fun setSkillDescriptionOverride(skillId: String, description: String) = updateSkill(skillId) {
        val canonical = com.furybook.dubl.model.SkillCatalog.definition(it.definitionId ?: skillId)?.description.orEmpty()
        val clean = description.trim()
        it.copy(description = clean.takeUnless { value -> value == canonical }.orEmpty())
    }

    fun setSkillCategoryOverride(skillId: String, category: com.furybook.dubl.model.SkillCategory?) = updateSkill(skillId) {
        val canonical = com.furybook.dubl.model.SkillCatalog.definition(it.definitionId ?: skillId)?.category
        it.copy(categoryOverride = category.takeUnless { value -> value == canonical })
    }

    fun setSkillUntrainedOverride(skillId: String, rule: com.furybook.dubl.model.UntrainedRule?) = updateSkill(skillId) {
        val canonical = com.furybook.dubl.model.SkillCatalog.definition(it.definitionId ?: skillId)?.untrained
        it.copy(untrainedOverride = rule.takeUnless { value -> value == canonical })
    }

    fun setSkillAutoOverrides(skillId: String, auto6: String?, auto12: String?) = updateSkill(skillId) {
        val canonical = com.furybook.dubl.model.SkillCatalog.definition(it.definitionId ?: skillId)
        val clean6 = auto6?.trim()
        val clean12 = auto12?.trim()
        it.copy(
            auto6Override = clean6.takeUnless { value -> value == null || value == canonical?.auto6 },
            auto12Override = clean12.takeUnless { value -> value == null || value == canonical?.auto12 },
        )
    }

    fun resetSkillDefinitionOverrides(skillId: String) = updateSkill(skillId) {
        it.copy(
            name = "",
            description = "",
            attributes = emptyList(),
            categoryOverride = null,
            untrainedOverride = null,
            auto6Override = null,
            auto12Override = null,
        )
    }

    fun hideSkill(skillId: String) = updateActive { character ->
        character.copy(hiddenSkillIds = character.hiddenSkillIds + skillId)
    }

    fun restoreSkill(skillId: String) = updateActive { character ->
        character.copy(hiddenSkillIds = character.hiddenSkillIds - skillId)
    }

    fun restoreAllSkills() = updateActive { it.copy(hiddenSkillIds = emptySet()) }

    fun setSkillEffectEnabled(effectId: String, enabled: Boolean) = updateActive { character ->
        val id = effectId.trim()
        if (id.isBlank()) return@updateActive character
        character.copy(
            disabledSkillEffectIds = if (enabled) {
                character.disabledSkillEffectIds - id
            } else {
                character.disabledSkillEffectIds + id
            },
        )
    }

    fun setDevelopmentRank(entryId: String, rank: Int, optionIndex: Int = 0) = updateActive { character ->
        val next = character.development.toMutableMap()
        if (rank <= 0) {
            next.remove(entryId)
        } else {
            next[entryId] = OwnedDevelopment(
                rank = rank,
                optionIndex = optionIndex.coerceAtLeast(0),
            )
        }
        var updated = character.copy(development = next)
        if (!character.creationComplete && entryId == MagicEquipmentRules.INCREASED_MANA_ENTRY_ID && updated.magic.manaRank > 0) {
            updated = updated.copy(manaCurrent = MagicEquipmentRules.manaMaximum(updated))
        }
        updated
    }

    fun setDevelopmentOverride(entry: DevelopmentEntry) = updateActive { character ->
        val id = entry.id.trim()
        if (id.isBlank()) return@updateActive character
        character.copy(
            developmentOverrides = character.developmentOverrides + (id to entry.normalizedLocalCopy(id)),
        )
    }

    fun resetDevelopmentOverride(entryId: String) = updateActive { character ->
        character.copy(developmentOverrides = character.developmentOverrides - entryId)
    }

    fun addCustomDevelopment(entry: DevelopmentEntry): String? {
        val cleanName = entry.name.trim().takeIf(String::isNotBlank) ?: return null
        val existing = active.customDevelopmentEntries.mapTo(linkedSetOf()) { it.id }
        var id = "custom-development-${newId().trim()}"
        if (id == "custom-development-" || id in existing) {
            id = generateSequence(1) { it + 1 }
                .map { "custom-development-$it" }
                .first { it !in existing }
        }
        val normalized = entry.copy(id = id, name = cleanName).normalizedLocalCopy(id)
        updateActive { character ->
            character.copy(customDevelopmentEntries = character.customDevelopmentEntries + normalized)
        }
        return id
    }

    fun updateCustomDevelopment(entry: DevelopmentEntry): Boolean {
        val id = entry.id.trim()
        if (id.isBlank() || active.customDevelopmentEntries.none { it.id == id }) return false
        val normalized = entry.normalizedLocalCopy(id)
        updateActive { character ->
            character.copy(
                customDevelopmentEntries = character.customDevelopmentEntries.map { current ->
                    if (current.id == id) normalized else current
                },
            )
        }
        return true
    }

    fun removeCustomDevelopment(entryId: String) = updateActive { character ->
        character.copy(
            customDevelopmentEntries = character.customDevelopmentEntries.filterNot { it.id == entryId },
            development = character.development - entryId,
        )
    }


    fun setMagicManaRank(rank: Int) = updateActive { character ->
        val nextRank = rank.coerceIn(0, 5)
        if (character.creationComplete && nextRank != character.magic.manaRank) return@updateActive character
        var updated = character.copy(
            magic = character.magic.copy(manaRank = nextRank),
            development = character.development - MagicEquipmentRules.BASE_MANA_ENTRY_ID,
            manaEnabled = nextRank > 0 || character.manaMaximumOverride != null,
            manaCurrent = if (nextRank > 0 || character.manaMaximumOverride != null) character.manaCurrent else 0,
            manaMaximum = if (nextRank > 0) character.manaMaximum else 0,
        )
        if (!character.creationComplete && (nextRank > 0 || character.manaMaximumOverride != null)) {
            updated = updated.copy(manaCurrent = updated.effectiveManaMaximum)
        }
        updated
    }

    @Deprecated("0.3.1 uses per-school magic power")
    fun setMagicPower(power: Int) = updateActive { character ->
        character.copy(magic = character.magic.copy(power = power.coerceAtLeast(0)))
    }

    fun setMagicSchoolPower(name: String, power: Int) = updateActive { character ->
        val canonical = MagicSchoolCatalog.canonicalizeOrNull(name) ?: return@updateActive character
        val nextPower = power.coerceAtLeast(0)
        val without = character.magic.schools.filterNot {
            MagicSchoolCatalog.canonicalizeOrNull(it.name) == canonical
        }
        val schools = if (nextPower == 0) without else without + MagicSchool(canonical, nextPower)
        var updated = character.copy(
            magic = character.magic.copy(
                power = 0,
                schools = schools.sortedWith(compareBy<MagicSchool> { MagicSchoolCatalog.sortIndex(it.name) }.thenBy { it.name.lowercase() }),
            ),
        )
        if (!character.creationComplete && character.magic.manaRank > 0) {
            updated = updated.copy(manaCurrent = updated.effectiveManaMaximum)
        }
        updated
    }

    fun addMagicSchool(name: String, rank: Int, note: String): Boolean {
        val clean = MagicSchoolCatalog.canonicalizeOrNull(name) ?: return false
        if (active.magic.schools.any { it.name.equals(clean, ignoreCase = true) }) return false
        updateActive { character ->
            var updated = character.copy(
                magic = character.magic.copy(
                    schools = character.magic.schools + MagicSchool(clean, rank.coerceAtLeast(0), note.trim()),
                ),
            )
            if (!character.creationComplete && character.magic.manaRank > 0) {
                updated = updated.copy(manaCurrent = updated.effectiveManaMaximum)
            }
            updated
        }
        return true
    }

    fun updateMagicSchool(index: Int, name: String, rank: Int, note: String): Boolean {
        val clean = MagicSchoolCatalog.canonicalizeOrNull(name) ?: return false
        if (index !in active.magic.schools.indices) return false
        if (active.magic.schools.withIndex().any { (otherIndex, school) ->
                otherIndex != index && school.name.equals(clean, ignoreCase = true)
            }) return false
        updateActive { character ->
            if (index !in character.magic.schools.indices) return@updateActive character
            val next = character.magic.schools.toMutableList()
            next[index] = next[index].copy(
                name = clean,
                rank = rank.coerceAtLeast(0),
                note = note.trim(),
            )
            var updated = character.copy(magic = character.magic.copy(schools = next))
            if (!character.creationComplete && character.magic.manaRank > 0) {
                updated = updated.copy(manaCurrent = updated.effectiveManaMaximum)
            }
            updated
        }
        return true
    }


    fun removeMagicSchool(index: Int) = updateActive { character ->
        if (index !in character.magic.schools.indices) return@updateActive character
        var updated = character.copy(magic = character.magic.copy(schools = character.magic.schools.filterIndexed { i, _ -> i != index }))
        if (!character.creationComplete && character.magic.manaRank > 0) {
            updated = updated.copy(manaCurrent = updated.effectiveManaMaximum)
        }
        updated
    }

    private fun uniqueSpellUid(): String {
        val existing = active.magic.spells.mapTo(linkedSetOf()) { it.uid }
        val base = newId().trim().ifBlank { "spell" }
        if (base !in existing) return base
        return generateSequence(2) { it + 1 }
            .map { "$base-$it" }
            .first { it !in existing }
    }

    fun addCatalogSpell(entry: SpellCatalogEntry): Boolean {
        if (active.magic.spells.any {
                it.catalogId == entry.id || it.name.equals(entry.name, ignoreCase = true)
            }) return false
        val spell = KnownSpell(
            uid = uniqueSpellUid(),
            catalogId = entry.id,
            name = entry.name,
            school = entry.school,
            cost = entry.cost,
            manaText = entry.manaText,
            time = entry.time,
            range = entry.range,
            area = entry.area,
            action = entry.action,
            duration = entry.duration,
            description = entry.description,
            enhancement = entry.enhancement,
            learned = true,
            incomplete = entry.incomplete,
            conflictNote = entry.conflictNote,
        )
        updateActive { character -> character.copy(magic = character.magic.copy(spells = character.magic.spells + spell)) }
        return true
    }

    fun addCustomSpell(spell: KnownSpell): String {
        val requested = spell.uid.trim()
        val uid = if (requested.isNotBlank() && active.magic.spells.none { it.uid == requested }) requested else uniqueSpellUid()
        updateActive { character ->
            character.copy(
                magic = character.magic.copy(
                    spells = character.magic.spells + spell.copy(uid = uid, catalogId = null, custom = true),
                ),
            )
        }
        return uid
    }

    fun updateSpell(uid: String, transform: (KnownSpell) -> KnownSpell) = updateActive { character ->
        character.copy(
            magic = character.magic.copy(
                spells = character.magic.spells.map { if (it.uid == uid) transform(it).copy(uid = uid) else it },
            ),
        )
    }

    fun replaceSpell(spell: KnownSpell) = updateSpell(spell.uid) { spell }
    fun setSpellLearned(uid: String, learned: Boolean) = updateSpell(uid) { it.copy(learned = learned) }

    fun removeSpell(uid: String) = updateActive { character ->
        character.copy(magic = character.magic.copy(spells = character.magic.spells.filterNot { it.uid == uid }))
    }

    fun setGearLoadAutomatic(enabled: Boolean) = updateActive { character ->
        character.copy(gear = character.gear.copy(loadAutomatic = enabled))
    }

    fun setGearManualLoad(value: Double) = updateActive { character ->
        character.copy(gear = character.gear.copy(loadManual = value.coerceAtLeast(0.0)))
    }

    fun syncCatalogGearLoads(entries: List<GearCatalogEntry>) {
        val byId = entries.associateBy { it.id }
        val current = active
        val repaired = current.gear.items.map { item ->
            val catalogEntry = item.catalogId?.let(byId::get) ?: return@map item
            val expected = MagicEquipmentRules.catalogGearLoad(catalogEntry)
            val legacyRequirementLoad = (catalogEntry.fields["Треб."] ?: catalogEntry.fields["Требование"] ?: "0")
                .replace(',', '.')
                .toDoubleOrNull()
                ?.coerceAtLeast(0.0)
                ?: 0.0
            val isLegacyCatalogValue = kotlin.math.abs(item.load - legacyRequirementLoad) < 0.0001
            if (!isLegacyCatalogValue || kotlin.math.abs(item.load - expected) < 0.0001) item
            else item.copy(load = expected)
        }
        if (repaired == current.gear.items) return
        updateActive { character -> character.copy(gear = character.gear.copy(items = repaired)) }
    }

    fun addCatalogGear(entry: GearCatalogEntry): String {
        val existing = active.gear.items.firstOrNull { it.catalogId == entry.id }
        if (existing != null) {
            updateGearItem(existing.uid) { item -> item.copy(quantity = item.quantity + 1) }
            return existing.uid
        }

        val uid = newId()
        val load = MagicEquipmentRules.catalogGearLoad(entry)
        val item = GearItem(
            uid = uid,
            catalogId = entry.id,
            name = entry.name,
            quantity = 1,
            load = load.coerceAtLeast(0.0),
            carried = true,
            description = entry.description,
            category = entry.category,
            section = entry.section,
            fields = entry.fields,
        )
        updateActive { character -> character.copy(gear = character.gear.copy(items = character.gear.items + item)) }
        return uid
    }

    fun addCustomGear(item: GearItem): String {
        val uid = item.uid.ifBlank { newId() }
        updateActive { character ->
            character.copy(
                gear = character.gear.copy(items = character.gear.items + item.copy(uid = uid, catalogId = null, custom = true)),
            )
        }
        return uid
    }

    fun updateGearItem(uid: String, transform: (GearItem) -> GearItem) = updateActive { character ->
        character.copy(
            gear = character.gear.copy(
                items = character.gear.items.map { if (it.uid == uid) transform(it).copy(uid = uid) else it },
            ),
        )
    }

    fun replaceGearItem(item: GearItem) = updateGearItem(item.uid) { item }
    fun setGearItemCarried(uid: String, carried: Boolean) = updateGearItem(uid) { it.copy(carried = carried) }
    fun setGearItemQuantity(uid: String, quantity: Int) = updateGearItem(uid) { it.copy(quantity = quantity.coerceAtLeast(1)) }

    fun removeGearItem(uid: String) = updateActive { character ->
        character.copy(gear = character.gear.copy(items = character.gear.items.filterNot { it.uid == uid }))
    }

    fun addSpecializedSkill(templateId: String, specialization: String): String? {
        val template = SkillCatalog.templates.firstOrNull { it.id == templateId } ?: return null
        val clean = specialization.trim().replace(Regex("\\s+"), " ")
        if (clean.isBlank()) return null
        val displayName = when (template.id) {
            "knowledge_template" -> "Знание ($clean)"
            "performance_template" -> "Исполнение ($clean)"
            "profession_template" -> "Профессия ($clean)"
            "craft_template" -> "Ремесло ($clean)"
            else -> "${template.name} ($clean)"
        }
        if (active.resolvedSkills(includeHidden = true).any { it.name.equals(displayName, ignoreCase = true) }) {
            return null
        }
        val id = newId()
        updateActive { character ->
            character.copy(
                skills = character.skills + (
                    id to CharacterSkill(
                        id = id,
                        definitionId = template.id,
                        name = displayName,
                        attributes = listOf(template.defaultAttribute),
                    )
                ),
            )
        }
        return id
    }

    fun addCustomSkill(
        name: String,
        description: String,
        attributes: List<AttributeId>,
        untrained: UntrainedRule,
    ): String? {
        val cleanName = name.trim().replace(Regex("\\s+"), " ")
        val cleanAttrs = attributes.distinct()
        if (cleanName.isBlank() || cleanAttrs.isEmpty()) return null
        if (active.resolvedSkills(includeHidden = true).any { it.name.equals(cleanName, ignoreCase = true) }) {
            return null
        }
        val id = newId()
        updateActive { character ->
            character.copy(
                skills = character.skills + (
                    id to CharacterSkill(
                        id = id,
                        name = cleanName,
                        description = description.trim(),
                        attributes = cleanAttrs,
                        untrainedOverride = untrained,
                    )
                ),
            )
        }
        return id
    }

    fun deleteDynamicSkill(skillId: String) {
        if (SkillCatalog.builtIns.any { it.id == skillId }) return
        updateActive { character ->
            character.copy(
                skills = character.skills - skillId,
                hiddenSkillIds = character.hiddenSkillIds - skillId,
            )
        }
    }

    fun createCharacter() {
        val created = DublCharacter(id = newId(), name = "Новый персонаж")
        persist(
            AppSnapshot(
                characters = snapshot.characters + created,
                activeCharacterId = created.id,
            )
        )
    }

    internal fun newIdForTransfer(): String = newId()

    internal fun importTransferredCharacter(character: DublCharacter): String {
        val imported = character.copy(id = newId())
        persist(
            AppSnapshot(
                characters = snapshot.characters + imported,
                activeCharacterId = imported.id,
            )
        )
        return imported.id
    }

    fun selectCharacter(id: String) {
        if (snapshot.characters.any { it.id == id }) {
            persist(snapshot.copy(activeCharacterId = id))
        }
    }

    fun deleteActive() {
        if (snapshot.characters.size <= 1) return
        val remaining = snapshot.characters.filterNot { it.id == snapshot.activeCharacterId }
        persist(AppSnapshot(remaining, remaining.first().id))
    }

    private fun updateSkill(skillId: String, transform: (CharacterSkill) -> CharacterSkill) {
        updateActive { character ->
            val existing = character.skills[skillId]
            val builtIn = SkillCatalog.builtIns.firstOrNull { it.id == skillId }
            val base = existing ?: builtIn?.let {
                CharacterSkill(id = it.id, definitionId = it.id)
            } ?: return@updateActive character
            val updated = transform(base).copy(id = skillId)
            character.copy(skills = character.skills + (skillId to updated))
        }
    }

    private fun newId(): String { beforeWrite(); return idFactory() }

    private fun persist(newSnapshot: AppSnapshot) {
        beforeWrite()
        snapshot = newSnapshot
        store.save(newSnapshot)
    }
}

/** Exact volatile staging store: never attached to platform persistence. */
internal class DetachedCharacterStore(initial: AppSnapshot) : CharacterStore {
    private var value = initial
    override fun load() = value
    override fun save(snapshot: AppSnapshot) { value = snapshot }
}

/** Preserve the existing resource bounds only for edited resources/changed maxima. Global legacy
 * normalization would erase unrelated manual ranks, absent/rank-zero rows and exact identities.
 */
private fun stagedResources(before: DublCharacter, after: DublCharacter): DublCharacter = after.copy(
    hpCurrent = if (before.hpCurrent != after.hpCurrent || before.healthMaximum != after.healthMaximum)
        after.hpCurrent.coerceAtMost(after.healthMaximum) else after.hpCurrent,
    enduranceCurrent = if (before.enduranceCurrent != after.enduranceCurrent || before.enduranceMaximum != after.enduranceMaximum)
        after.enduranceCurrent.coerceIn(0, after.enduranceMaximum.coerceAtLeast(0)) else after.enduranceCurrent,
    manaCurrent = if (before.manaCurrent != after.manaCurrent || before.effectiveManaMaximum != after.effectiveManaMaximum)
        after.manaCurrent.coerceIn(0, after.effectiveManaMaximum.coerceAtLeast(0)) else after.manaCurrent,
    chiCurrent = if (before.chiCurrent != after.chiCurrent || before.chiMaximum != after.chiMaximum)
        after.chiCurrent.coerceIn(0, after.chiMaximum.coerceAtLeast(0)) else after.chiCurrent,
    customResources = after.customResources.map { resource ->
        if (before.customResources.singleOrNull { it.uid == resource.uid } == resource) resource
        else resource.copy(maximum = resource.maximum.coerceAtLeast(0), current = resource.current.coerceIn(0, resource.maximum.coerceAtLeast(0)))
    },
)
