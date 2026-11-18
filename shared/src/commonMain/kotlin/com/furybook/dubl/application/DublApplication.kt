package com.furybook.dubl.application

import com.furybook.dubl.data.CharacterExtrasStore
import com.furybook.dubl.data.CharacterStore
import com.furybook.dubl.model.AppSnapshot
import com.furybook.dubl.model.CharacterSheetExtras
import com.furybook.dubl.model.DublCharacter
import com.furybook.dubl.state.CharacterExtrasSession
import com.furybook.dubl.state.CharacterSession

/**
 * The single public state-changing boundary for DUBL application behavior.
 *
 * Platform adapters may observe state and call capability methods, but they never
 * receive the raw sessions or arbitrary character/extras transforms.
 */
class DublApplication(
    characterStore: CharacterStore,
    extrasStore: CharacterExtrasStore,
    idFactory: () -> String,
    customConditionIdFactory: () -> String = { "" },
) {
    private val characterSession = CharacterSession(characterStore, idFactory)
    private val extrasSession = CharacterExtrasSession(extrasStore, customConditionIdFactory)
    private val undoManager = ApplicationUndoManager()

    val character = CharacterApplication(characterSession, extrasSession, undoManager)
    val skills = SkillsApplication(characterSession, extrasSession)
    val development = DevelopmentApplication(characterSession, undoManager)
    val magic = MagicApplication(characterSession, undoManager)
    val equipment = EquipmentApplication(characterSession)
    val sheet = SheetApplication(extrasSession, { characterSession.active.id }, undoManager)
    val transfer = CharacterTransferApplication(characterSession, extrasSession)

    val snapshot: AppSnapshot get() = characterSession.snapshot
    val active: DublCharacter get() = characterSession.active
    val activeExtras: CharacterSheetExtras get() = sheet.current
    val canUndo: Boolean get() = undoManager.canUndo

    fun undoLast(): Boolean = undoManager.undoLast()

    fun restorePoint(): CharacterRestorePoint = CharacterRestorePoint(active, activeExtras)

    /**
     * Writes [point] back over the active character and its extras, as one undoable step.
     * Refuses (false) when another character is active, so a stale point never lands on the wrong one.
     */
    fun restore(point: CharacterRestorePoint): Boolean {
        val id = characterSession.active.id
        if (point.character.id != id) return false
        val before = restorePoint()
        characterSession.updateActive { point.character }
        extrasSession.replace(id, point.extras)
        undoManager.record { restore(before) }
        return true
    }
}
