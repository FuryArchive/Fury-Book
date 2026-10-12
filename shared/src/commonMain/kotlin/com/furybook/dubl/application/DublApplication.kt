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
    private val mutationCoordinator: com.furybook.dubl.acquisition.CharacterMutationCoordinator? = null,
    private val acquisitionUndo: com.furybook.dubl.acquisition.AcquisitionUndo? = null,
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
    /** Legacy session Undo only; canonical receipts use undoCanonical. */
    val canUndo: Boolean get() = undoManager.canUndo

    fun undoLast(): Boolean = undoManager.undoLast()

    suspend fun mutateCanonical(command: com.furybook.dubl.acquisition.CanonicalMutation) =
        checkNotNull(mutationCoordinator) { "Canonical mutation coordinator is not configured" }.mutate(command)
    suspend fun eligibleCanonicalUndo(characterId: String) =
        checkNotNull(acquisitionUndo) { "Canonical Undo is not configured" }.eligible(characterId)
    suspend fun undoCanonical(characterId: String, operationId: String, expectedRevision: Long) =
        checkNotNull(acquisitionUndo) { "Canonical Undo is not configured" }.undo(characterId, operationId, expectedRevision)
    suspend fun prepareNonFinancial(operationId: String, characterId: String, expectedRevision: Long, edit: DublApplication.() -> Unit) =
        checkNotNull(mutationCoordinator) { "Canonical mutation coordinator is not configured" }.prepareNonFinancial(operationId, characterId, expectedRevision, edit)
    suspend fun stageNonFinancial(operationId: String, characterId: String, expectedRevision: Long, edit: DublApplication.() -> Unit) =
        checkNotNull(mutationCoordinator) { "Canonical mutation coordinator is not configured" }.stageNonFinancial(operationId, characterId, expectedRevision, edit)

    companion object {
        internal fun detached(character: DublCharacter, extras: CharacterSheetExtras, idFactory: () -> String): DublApplication {
            // Read-only collection interfaces may still wrap mutable collections from a cached
            // adapter. Exact codec capture prevents staging from aliasing any canonical object.
            val captured = (com.furybook.dubl.acquisition.decodeEnvelope(com.furybook.dubl.acquisition.encodeEnvelope(
                com.furybook.dubl.acquisition.projectionContainer(character, emptyList(), extras)))
                as com.furybook.dubl.acquisition.LoadResult.Loaded).state
            val extrasStore = object : CharacterExtrasStore {
                private var value = captured.extras
                override fun load(characterId: String) = value
                override fun save(characterId: String, extras: CharacterSheetExtras) { value = extras }
            }
            return DublApplication(com.furybook.dubl.state.DetachedCharacterStore(AppSnapshot(listOf(captured.character), captured.character.id)), extrasStore, idFactory, idFactory)
        }
    }
}
