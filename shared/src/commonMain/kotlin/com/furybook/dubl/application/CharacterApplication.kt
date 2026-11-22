package com.furybook.dubl.application

import com.furybook.dubl.model.AttributeId
import com.furybook.dubl.state.CharacterExtrasSession
import com.furybook.dubl.state.CharacterSession

class CharacterApplication internal constructor(
    private val session: CharacterSession,
    private val extras: CharacterExtrasSession,
    private val undo: ApplicationUndoManager,
) {
    fun setProfile(
        name: String,
        concept: String,
        experience: Int,
        size: Int,
        legs: Int,
        manaEnabled: Boolean,
    ) = session.updateActive { current ->
        val cleanExperience = experience.coerceAtLeast(0)
        var updated = current.copy(
            name = name.trim().ifBlank { "Новый персонаж" },
            concept = concept.trim(),
            experience = cleanExperience,
            creationExperience = if (current.creationComplete) {
                current.creationExperience.coerceAtMost(cleanExperience)
            } else {
                cleanExperience
            },
            size = size.coerceIn(1, 10),
            legs = legs.coerceAtLeast(2),
            manaEnabled = manaEnabled,
        )
        if (!current.creationComplete) updated = updated.copy(hpCurrent = updated.healthMaximum)
        updated
    }

    fun setIdentity(name: String, concept: String, size: Int, legs: Int, manaEnabled: Boolean) {
        val before = session.active
        session.updateActive { current ->
            val updated = current.copy(
                name = name.trim().ifBlank { "Новый персонаж" },
                concept = concept.trim(),
                size = size.coerceIn(1, 10),
                legs = legs.coerceAtLeast(2),
                manaEnabled = manaEnabled,
            )
            if (!current.creationComplete) updated.copy(hpCurrent = updated.healthMaximum) else updated
        }
        val after = session.active
        if (after != before) {
            undo.record {
                session.updateActive { current ->
                    val restored = current.copy(
                        name = before.name,
                        concept = before.concept,
                        size = before.size,
                        legs = before.legs,
                        manaEnabled = before.manaEnabled,
                    )
                    if (!current.creationComplete) restored.copy(hpCurrent = restored.healthMaximum) else restored
                }
            }
        }
    }

    fun setName(name: String) {
        val before = session.active.name
        session.updateActive { it.copy(name = name.trim().ifBlank { "Новый персонаж" }) }
        if (session.active.name != before) undo.record { session.updateActive { it.copy(name = before) } }
    }

    fun setSize(size: Int) {
        val before = session.active.size
        session.updateActive { current ->
            val updated = current.copy(size = size.coerceIn(1, 10))
            if (!current.creationComplete) updated.copy(hpCurrent = updated.healthMaximum) else updated
        }
        if (session.active.size != before) {
            undo.record {
                session.updateActive { current ->
                    val restored = current.copy(size = before)
                    if (!current.creationComplete) restored.copy(hpCurrent = restored.healthMaximum) else restored
                }
            }
        }
    }

    fun setLegs(legs: Int) {
        val before = session.active.legs
        session.updateActive { it.copy(legs = legs.coerceAtLeast(2)) }
        if (session.active.legs != before) undo.record { session.updateActive { it.copy(legs = before) } }
    }

    fun changeAttribute(id: AttributeId, delta: Int) {
        val before = session.active.attributes.getValue(id).base
        session.changeAttribute(id, delta)
        val applied = session.active.attributes.getValue(id).base - before
        if (applied != 0) undo.record { session.changeAttribute(id, -applied) }
    }

    fun setExperience(total: Int) {
        val beforeTotal = session.active.experience
        val beforeCreation = session.active.creationExperience
        session.setExperience(total)
        if (session.active.experience != beforeTotal || session.active.creationExperience != beforeCreation) {
            undo.record {
                session.setExperience(beforeTotal)
                session.setCreationExperience(beforeCreation)
            }
        }
    }

    fun setCreationExperience(value: Int) = session.setCreationExperience(value)
    fun setXpAdjustment(value: Int) = session.setXpAdjustment(value)
    fun setAbilityPointsOverride(value: Int?) = session.setAbilityPointsOverride(value)

    fun setEconomy(total: Int, creation: Int, adjustment: Int, abilityPointsOverride: Int?) =
        session.updateActive { character ->
            val cleanTotal = total.coerceAtLeast(0)
            character.copy(
                experience = cleanTotal,
                creationExperience = creation.coerceIn(0, cleanTotal),
                xpAdjustment = adjustment.coerceIn(-1_000_000, 1_000_000),
                abilityPointsOverride = abilityPointsOverride?.coerceAtLeast(0),
            )
        }

    fun completeCreation() = session.completeCreation()
    fun reopenCreation() = session.reopenCreation()

    fun changeHp(delta: Int) {
        val before = session.active.hpCurrent
        session.changeHp(delta)
        val applied = session.active.hpCurrent - before
        if (applied != 0) undo.record { session.changeHp(-applied) }
    }

    fun changeEndurance(delta: Int) {
        val before = session.active.enduranceCurrent
        session.changeEndurance(delta)
        val applied = session.active.enduranceCurrent - before
        if (applied != 0) undo.record { session.changeEndurance(-applied) }
    }

    fun setHealthMaximumOverride(value: Int?) = session.setHealthMaximumOverride(value)
    fun setEnduranceMaximumOverride(value: Int?) = session.setEnduranceMaximumOverride(value)
    fun setManaMaximumOverride(value: Int?) = session.setManaMaximumOverride(value)

    fun addCustomResource(name: String, maximum: Int, current: Int = maximum): String? =
        session.addCustomResource(name, maximum, current)

    fun updateCustomResource(uid: String, name: String, current: Int, maximum: Int) =
        session.updateCustomResource(uid, name, current, maximum)

    fun changeCustomResource(uid: String, delta: Int) = session.changeCustomResource(uid, delta)
    fun removeCustomResource(uid: String) = session.removeCustomResource(uid)

    fun createCharacter() = session.createCharacter()
    fun selectCharacter(id: String) = session.selectCharacter(id)

    /** Duplicates [id] with its extras (portrait, conditions…); returns the copy's id. */
    fun duplicate(id: String): String? = session.duplicate(id)?.also { copyId -> extras.replace(copyId, extras.load(id)) }

    fun deleteActive() {
        val deletedId = session.active.id
        extras.delete(deletedId)
        session.deleteActive()
    }
}
