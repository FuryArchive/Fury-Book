import com.furybook.desktop.DesktopAppState
import com.furybook.dubl.acquisition.*
import com.furybook.dubl.data.*
import com.furybook.dubl.model.*
import java.nio.file.Files
import kotlinx.coroutines.runBlocking

/** Real AppState/Compose state and filesystem, with no production API or dependency substitutes. */
fun main(args: Array<String>) = runBlocking {
    val dir = Files.createTempDirectory("t12-confirmation")
    val file = dir.resolve("characters.json")
    DesktopCharacterStore(file).save(AppSnapshot(listOf(DublCharacter("real", experience = 100)), "real"))
    val sourceBytes = Files.readAllBytes(file)
    var failPreparation = false
    val store = DesktopAcquisitionStore(dir) { stage ->
        // An unsupported parent-directory force also exercises visible preparation/storage errors.
        if (failPreparation && stage.name == "BeforeAncestorDirectoryFlush") error("preparation storage unavailable")
    }
    val app = DesktopAppState(DesktopCharacterStore(file), DesktopCharacterExtrasStore(dir.resolve("sheet-extras.json")), true, store)
    check(app.prepareLocalMigration(listOf("dubl-3.69")) is MigrationResult.Ready)
    if (args.single() == "failure") {
        // This also reproduces the pre-fix failure without relying on a new fault stage.
        val container = store.envelopePath("real").parent
        Files.list(container).use { paths -> paths.forEach(Files::delete) }
        Files.delete(container); Files.writeString(container, "not a directory")
        check(runCatching { app.prepareLocalMigration(listOf("dubl-3.69", "unavailable-old-addon")) }.isFailure)
        check(app.preparedLegacyMigration == null && app.pendingLegacyMigration == null) { "Failed preparation retained the previous confirmation" }
        check(app.storageError != null)
        check(runCatching { app.commitPreparedLocalMigration() }.isFailure)
        Files.delete(container)
        check(app.prepareLocalMigration(listOf("dubl-3.69", "unavailable-old-addon")) is MigrationResult.NeedsBalanceConfirmation)
        failPreparation = true
        check(runCatching { app.prepareLocalMigration(listOf("dubl-3.69")) }.isFailure)
        check(app.preparedLegacyMigration == null && app.pendingLegacyMigration == null)
        check(runCatching { app.confirmLocalOpeningBalance(100, 0, "known") }.isFailure)
    } else {
        // Reflection lets this regression execute against the reviewed pre-fix public module.
        val declare = app.javaClass.methods.singleOrNull { it.name == "declareLegacyComposition" }
            ?: error("Declaration edits cannot invalidate migration confirmation")
        declare.invoke(app, listOf("dubl-3.69", "unavailable-old-addon"))
        check(app.preparedLegacyMigration == null && app.pendingLegacyMigration == null)
        check(runCatching { app.commitPreparedLocalMigration() }.isFailure)
        check(app.prepareLocalMigration(listOf("dubl-3.69", "unavailable-old-addon")) is MigrationResult.NeedsBalanceConfirmation)
        check(app.pendingLegacyMigration!!.origin.activePackIds == listOf("dubl-3.69", "unavailable-old-addon"))
        declare.invoke(app, listOf("dubl-3.69"))
        check(app.pendingLegacyMigration == null && app.preparedLegacyMigration == null)
        check(runCatching { app.confirmLocalOpeningBalance(100, 0, "known") }.isFailure)
        check(app.prepareLocalMigration(listOf("dubl-3.69")) is MigrationResult.Ready)
        check(app.preparedLegacyMigration!!.ledger.opening.legacyMigration!!.origin.activePackIds == listOf("dubl-3.69"))
        check(app.commitPreparedLocalMigration() == CommitResult.Committed)
    }
    check(Files.readAllBytes(file).contentEquals(sourceBytes))
    check(!app.cloudLedgerV2)
    println("DESKTOP_MIGRATION_CONFIRMATION_${args.single().uppercase()}_OK")
}
