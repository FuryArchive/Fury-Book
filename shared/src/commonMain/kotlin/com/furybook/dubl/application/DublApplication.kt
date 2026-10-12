package com.furybook.dubl.application

import com.furybook.dubl.acquisition.AcquisitionService
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
    acquisitionService: AcquisitionService? = null,
) {
    private val characterSession = CharacterSession(characterStore, idFactory)
    private val extrasSession = CharacterExtrasSession(extrasStore, customConditionIdFactory)
    private val undoManager = ApplicationUndoManager()

    val character = CharacterApplication(characterSession, extrasSession, undoManager)
    val skills = SkillsApplication(characterSession, extrasSession)
    val development = DevelopmentApplication(characterSession, undoManager, acquisitionService)
    val magic = MagicApplication(characterSession, undoManager)
    val equipment = EquipmentApplication(characterSession)
    val sheet = SheetApplication(extrasSession, { characterSession.active.id }, undoManager)
    val transfer = CharacterTransferApplication(characterSession, extrasSession)

    val snapshot: AppSnapshot get() = characterSession.snapshot
    val active: DublCharacter get() = characterSession.active
    val activeExtras: CharacterSheetExtras get() = sheet.current
    val canUndo: Boolean get() = undoManager.canUndo

    fun undoLast(): Boolean = undoManager.undoLast()
}
