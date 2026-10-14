package com.furybook.dubl.actions

import com.furybook.dubl.acquisition.*
import com.furybook.dubl.data.*
import com.furybook.dubl.model.*
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.runBlocking

fun main(args: Array<String>) = runBlocking {
    val root = Path.of(args[0])
    val phase = args[1]
    val store = DesktopAcquisitionStore(root, gameplayFormatEnabled = true)
    val service = GameplayService(store) { GameplayCapabilities(true, true) }
    val start = StartOverexertion("start", "hero", 0, PhysicalAttribute.SPEED)
    val finish = ResolvePhysicalCheck("finish", "hero", 1, "start", PhysicalCheckResult(false))
    fun initial() = StoredCharacter(DublCharacter("hero"), CharacterSheetExtras(notes = "keep"), emptyList(),
        Ledger(Opening("opening", "source", Amount.ZERO, RequirementNode.CreationPhase.Phase.Creating,
            OpeningProvenance.NewCharacter), emptyList()), emptyList(), emptyList(), SyncMetadata(0), 0, 0,
        RequirementNode.CreationPhase.Phase.Creating, formatVersion = 2, gameplay = GameplayJournal())
    when (phase) {
        "start" -> {
            check(store.compareAndSet("hero", -1, initial()) == CommitResult.Committed)
            check(service.apply(start, 0) is GameplayCommitResult.Applied)
            val bytes = Files.readAllBytes(store.envelopePath("hero"))
            val default = DesktopAcquisitionStore(root)
            check(default.load("hero") is LoadResult.Unsupported)
            val downgrade = initial().copy(formatVersion = 1, gameplay = null, storageRevision = 2)
            check(default.compareAndSet("hero", 1, downgrade) is CommitResult.StorageFailure)
            check(store.compareAndSet("hero", 1, downgrade) is CommitResult.StorageFailure)
            check(Files.readAllBytes(store.envelopePath("hero")).contentEquals(bytes))
        }
        "finish" -> {
            val active = (store.load("hero") as LoadResult.Loaded).state
            check(active.character.enduranceCurrent == 2 && active.gameplay?.pending?.startOperationId == "start")
            check(service.apply(start, 0) is GameplayCommitResult.Replayed)
            check(service.apply(finish, 1) is GameplayCommitResult.Applied)
        }
        "verify" -> {
            val state = (store.load("hero") as LoadResult.Loaded).state
            check(state.character.enduranceCurrent == 2 && state.gameplay?.pending == null)
            check(state.gameplay?.receipts?.size == 2 && state.revision == 0L && state.storageRevision == 2L)
            check(state.extras.notes == "keep" && state.ledger.entries.isEmpty())
            check(service.apply(finish, 1) is GameplayCommitResult.Replayed)
            check(service.apply(start, 0) is GameplayCommitResult.Replayed)
        }
        "undo" -> {
            check(service.apply(UndoGameplay("undo-finish", "hero", 2, "finish"), 2) is GameplayCommitResult.Applied)
        }
        "verify-undo" -> {
            val state = (store.load("hero") as LoadResult.Loaded).state
            check(state.character.enduranceCurrent == 2 && state.gameplay?.pending?.startOperationId == "start")
            check(state.gameplay?.receipts?.size == 3 && state.gameplay?.lastCommittedStorageRevision == 3L)
            check((service.apply(finish, 1) as GameplayCommitResult.Replayed).undone)
            check(service.apply(UndoGameplay("undo-finish", "hero", 2, "finish"), 2) is GameplayCommitResult.Replayed)
            check(service.apply(UndoGameplay("undo-start", "hero", 3, "start"), 3) is GameplayCommitResult.Blocked)
        }
        "faults" -> {
            for (stage in listOf(DesktopAcquisitionWriteStage.AfterTempFlush, DesktopAcquisitionWriteStage.AfterReplace)) {
                val folder = root.resolve(stage.name)
                check(DesktopAcquisitionStore(folder, gameplayFormatEnabled = true).compareAndSet("hero", -1, initial()) == CommitResult.Committed)
                var fired = false
                val faulty = DesktopAcquisitionStore(folder, fault = { reached ->
                    if (reached == stage && !fired) { fired = true; error("injected $stage") }
                }, gameplayFormatEnabled = true)
                val outcome = GameplayService(faulty) { GameplayCapabilities(true, true) }.apply(start, 0)
                check(fired)
                val reopened = (DesktopAcquisitionStore(folder, gameplayFormatEnabled = true).load("hero") as LoadResult.Loaded).state
                if (stage == DesktopAcquisitionWriteStage.AfterReplace) {
                    check(outcome is GameplayCommitResult.Replayed && reopened.character.enduranceCurrent == 2)
                    check(reopened.gameplay?.receipts?.size == 1)
                } else {
                    check(outcome is GameplayCommitResult.StorageFailure && reopened.character.enduranceCurrent == 3)
                    check(reopened.gameplay?.receipts?.isEmpty() == true)
                }
            }
        }
        else -> error("Unknown phase")
    }
    println("GAMEPLAY_DESKTOP_PHASE_OK:$phase")
}
