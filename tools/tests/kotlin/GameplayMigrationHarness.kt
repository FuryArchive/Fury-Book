package com.furybook.dubl.actions

import com.furybook.dubl.acquisition.*
import com.furybook.dubl.data.DesktopAcquisitionStore
import com.furybook.dubl.data.DesktopAcquisitionWriteStage
import com.furybook.dubl.model.*
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.runBlocking

fun main(args: Array<String>) = runBlocking {
    val root = Path.of(args[0]); val phase = args[1]
    fun original() = StoredCharacter(DublCharacter("hero", name = "Preserve", experience = 100),
        CharacterSheetExtras(notes = "Original notes"), emptyList(),
        Ledger(Opening("opening", "source", Amount(100, 2), RequirementNode.CreationPhase.Phase.Creating,
            OpeningProvenance.NewCharacter), emptyList()), emptyList(), emptyList(), SyncMetadata(0), 0, 0,
        RequirementNode.CreationPhase.Phase.Creating)
    val source = original(); val raw = encodeEnvelope(source)
    val request = GameplayMigrationRequest("migration", "hero", 0, raw)
    val enabled = DesktopAcquisitionStore(root, gameplayFormatEnabled = true)
    when (phase) {
        "migrate" -> {
            val old = DesktopAcquisitionStore(root)
            check(old.compareAndSet("hero", -1, source) == CommitResult.Committed)
            check(old.migrateGameplay(request) is GameplayMigrationResult.Blocked)
            check(Files.readString(old.envelopePath("hero")) == raw)
            val prepared = prepareGameplayMigration(source, raw, request)
            check(prepared.character == source.character && prepared.extras == source.extras && prepared.ledger == source.ledger)
            check(enabled.compareAndSet("hero", 0, prepared) is CommitResult.StorageFailure)
            val result = enabled.migrateGameplay(request) as GameplayMigrationResult.Migrated
            check(Files.readString(Path.of(result.backupPath)) == raw)
            val migrated = (enabled.load("hero") as LoadResult.Loaded).state
            check(migrated.storageRevision == 1L && migrated.revision == 0L && migrated.gameplay?.revision == 0L)
            check(migrated.gameplay?.migration?.sourceRaw == raw)
            check(old.load("hero") is LoadResult.Unsupported)
            check(GameplayService(enabled) { GameplayCapabilities(true, true) }.apply(
                StartOverexertion("start", "hero", 0, PhysicalAttribute.DEXTERITY), 1) is GameplayCommitResult.Applied)
        }
        "reopen" -> {
            val state = (enabled.load("hero") as LoadResult.Loaded).state
            check(state.character.enduranceCurrent == 2 && state.gameplay?.pending?.startOperationId == "start")
            val replay = enabled.migrateGameplay(request) as GameplayMigrationResult.AlreadyMigrated
            check(state.gameplay?.migration?.sourceRaw == raw && Files.readString(Path.of(replay.backupPath)) == raw)
            check(enabled.migrateGameplay(request.copy(operationId = "different")) is GameplayMigrationResult.Blocked)
            val backupState = (decodeEnvelope(Files.readString(Path.of(replay.backupPath))) as LoadResult.Loaded).state
            val restored = DesktopAcquisitionStore(root.resolve("restored-format1"))
            check(restored.compareAndSet("hero", -1, backupState) == CommitResult.Committed)
            check((restored.load("hero") as LoadResult.Loaded).state == source)
            check(GameplayService(enabled) { GameplayCapabilities(true, true) }.apply(
                UndoGameplay("undo-start", "hero", 1, "start"), 2) is GameplayCommitResult.Applied)
            check((enabled.load("hero") as LoadResult.Loaded).state.gameplay?.migration?.sourceRaw == raw)
        }
        "faults" -> {
            for (stage in listOf(DesktopAcquisitionWriteStage.AfterGameplayBackup,
                DesktopAcquisitionWriteStage.BeforeGameplayHeadReplace, DesktopAcquisitionWriteStage.AfterReplace)) {
                val folder = root.resolve(stage.name)
                val seed = DesktopAcquisitionStore(folder)
                check(seed.compareAndSet("hero", -1, source) == CommitResult.Committed)
                var fired = false; var replaces = 0
                val faulty = DesktopAcquisitionStore(folder, fault = { reached ->
                    if (reached == DesktopAcquisitionWriteStage.AfterReplace) replaces++
                    if (!fired && reached == stage && (stage != DesktopAcquisitionWriteStage.AfterReplace || replaces == 2)) {
                        fired = true; error("injected $stage")
                    }
                }, gameplayFormatEnabled = true)
                check(faulty.migrateGameplay(request) is GameplayMigrationResult.StorageFailure && fired)
                val retry = DesktopAcquisitionStore(folder, gameplayFormatEnabled = true).migrateGameplay(request)
                val backup = when (retry) {
                    is GameplayMigrationResult.Migrated -> { check(stage != DesktopAcquisitionWriteStage.AfterReplace); retry.backupPath }
                    is GameplayMigrationResult.AlreadyMigrated -> { check(stage == DesktopAcquisitionWriteStage.AfterReplace); retry.backupPath }
                    else -> error("Retry failed: $retry")
                }
                check(Files.readString(Path.of(backup)) == raw)
            }
            val cloud = source.copy(syncMetadata = SyncMetadata(7))
            check(runCatching { prepareGameplayMigration(cloud, encodeEnvelope(cloud), request.copy(expectedSourceCanonical = encodeEnvelope(cloud))) }.isFailure)
            check(runCatching { prepareGameplayMigration(source, raw, request.copy(expectedStorageRevision = 1)) }.isFailure)
            check(runCatching { prepareGameplayMigration(source, raw, request.copy(expectedSourceCanonical = "{}")) }.isFailure)
            check(runCatching { prepareGameplayMigration(source, raw, request.copy(characterId = "other")) }.isFailure)
        }
        else -> error("Unknown phase")
    }
    println("GAMEPLAY_MIGRATION_PHASE_OK:$phase")
}
