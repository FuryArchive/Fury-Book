import com.furybook.desktop.DesktopAppState
import com.furybook.dubl.acquisition.*
import com.furybook.dubl.data.*
import com.furybook.dubl.model.*
import java.nio.file.Files
import kotlinx.coroutines.runBlocking

/** Actual Compose observable state, public application boundary, real filesystem. No UI/API stubs. */
fun main() = runBlocking {
    val dir = Files.createTempDirectory("t12-state")
    val charactersFile = dir.resolve("characters.json")
    val extrasFile = dir.resolve("sheet-extras.json")
    val character = DublCharacter("real", name = "Original")
    DesktopCharacterStore(charactersFile).save(AppSnapshot(listOf(character), "real"))
    val originalExtras = CharacterSheetExtras(notes = "Exact note", noteEntries = listOf(CharacterNote("note", "Title", "Body")))
    DesktopCharacterExtrasStore(extrasFile).save("real", originalExtras)
    val charactersBytes = Files.readAllBytes(charactersFile)
    val extrasBytes = Files.readAllBytes(extrasFile)
    val store = DesktopAcquisitionStore(dir)
    val app = DesktopAppState(DesktopCharacterStore(charactersFile), DesktopCharacterExtrasStore(extrasFile), true, store)
    check(!app.cloudLedgerV2)
    app.setName("Must not publish")
    check(app.activeCharacter.name == "Original" && app.storageError != null)
    check(Files.readAllBytes(charactersFile).contentEquals(charactersBytes))
    check(app.prepareLocalMigration(listOf("dubl-3.69", "unavailable-old-addon")) is MigrationResult.NeedsBalanceConfirmation)
    check(!Files.exists(store.envelopePath("real")))
    check(app.confirmLocalOpeningBalance(100, 10, "Explicitly known current balance") == CommitResult.Committed)
    check(app.storageError == null)
    val migrated = (store.load("real") as LoadResult.Loaded).state
    check(migrated.extras == originalExtras && balance(migrated.ledger) == Amount(100, 10))
    val request = AcquisitionRequest("real", listOf(AcquisitionTarget(DefinitionRef("dubl-3.69", "athletics"), "new-athletics", 1)))
    val preview = app.previewLocalAcquisition(request)
    check(preview.executable)
    val command = ConfirmAcquisition("state-purchase", "real", request, preview.baseRevision, preview.previewToken)
    check(app.applyLocalAcquisition(command) is ApplyResult.Applied)
    check(app.applyLocalAcquisition(command) is ApplyResult.AlreadyApplied)
    val saved = (store.load("real") as LoadResult.Loaded).state
    check(balance(saved.ledger) == Amount(90, 10) && saved.receipts.single().operationId == "state-purchase")
    val failedApp = DesktopAppState(DesktopCharacterStore(charactersFile), DesktopCharacterExtrasStore(extrasFile), true,
        DesktopAcquisitionStore(dir) { if (it == DesktopAcquisitionWriteStage.BeforeReplace) error("Injected storage fault") })
    val nextRequest = AcquisitionRequest("real", listOf(AcquisitionTarget(DefinitionRef("dubl-3.69", "barter"), "new-barter", 1)))
    val nextPreview = failedApp.previewLocalAcquisition(nextRequest)
    check(nextPreview.executable)
    check(failedApp.applyLocalAcquisition(ConfirmAcquisition("failed-purchase", "real", nextRequest, nextPreview.baseRevision, nextPreview.previewToken)) is ApplyResult.StorageFailure)
    check(failedApp.storageError != null && failedApp.activeCharacter == saved.character)
    check((store.load("real") as LoadResult.Loaded).state == saved)
    val exported = CharacterTransferCodec.decodeEnvelope(app.exportActiveCharacter()) as LoadResult.Loaded
    check(exported.state == saved)
    app.setNotes("Must not reach legacy writer")
    check(app.extras == originalExtras && app.storageError != null)
    check(Files.readAllBytes(charactersFile).contentEquals(charactersBytes))
    check(Files.readAllBytes(extrasFile).contentEquals(extrasBytes))
    // Reconstructed app observes committed canonical state, rather than normalizing/repricing it.
    val reopened = DesktopAppState(DesktopCharacterStore(charactersFile), DesktopCharacterExtrasStore(extrasFile), true, DesktopAcquisitionStore(dir))
    check(reopened.extras == originalExtras && reopened.activeCharacter == saved.character)
    Files.writeString(extrasFile, Files.readString(extrasFile) + "\n")
    check(store.load("real") is LoadResult.LegacyImportConflict)
    check(runCatching { DesktopAppState(DesktopCharacterStore(charactersFile), DesktopCharacterExtrasStore(extrasFile), true, store) }.isFailure)
    check(Files.readAllBytes(store.envelopePath("real")).isNotEmpty())
    val knownDir=Files.createTempDirectory("t12-known-legacy")
    DesktopCharacterStore(knownDir.resolve("characters.json")).save(AppSnapshot(listOf(DublCharacter("known", experience=100, creationExperience=100)),"known"))
    val knownStore=DesktopAcquisitionStore(knownDir)
    val knownApp=DesktopAppState(DesktopCharacterStore(knownDir.resolve("characters.json")),DesktopCharacterExtrasStore(knownDir.resolve("sheet-extras.json")),true,knownStore)
    val knownReady=knownApp.prepareLocalMigration(listOf("dubl-3.69")) as MigrationResult.Ready
    check(knownReady.state.ledger.opening.provenance == OpeningProvenance.LegacyCalculated)
    check(balance(knownReady.state.ledger).xp == 100L)
    check(!Files.exists(knownStore.envelopePath("known")))
    check(knownApp.commitPreparedLocalMigration() == CommitResult.Committed)
    check((knownStore.load("known") as LoadResult.Loaded).state == knownReady.state)
    println("DESKTOP_ACQUISITION_STATE_OK")
}
