package com.furybook.dubl.actions

import com.furybook.dubl.acquisition.*
import com.furybook.dubl.data.DesktopAcquisitionStore
import com.furybook.dubl.model.*
import java.nio.file.Path
import java.nio.file.Files
import kotlinx.coroutines.runBlocking

fun main(args: Array<String>) = runBlocking {
    val root = Path.of(args[0]); val phase = args[1]
    val store = DesktopAcquisitionStore(root, gameplayFormatEnabled = true)
    val enabled = { GameplayCapabilities(true, true) }
    val events = GameplayEventService(store, enabled)
    val start = RecordGameBoundary("turn", "hero", 0, GameBoundary.TurnStarted("own-1", "hero"))
    when (phase) {
        "start" -> {
            val source = StoredCharacter(DublCharacter("hero"), CharacterSheetExtras(notes="original"), emptyList(),
                Ledger(Opening("opening", "source", Amount.ZERO, RequirementNode.CreationPhase.Phase.Creating,
                    OpeningProvenance.NewCharacter), emptyList()), emptyList(), emptyList(), SyncMetadata(0), 0, 0,
                RequirementNode.CreationPhase.Phase.Creating)
            check(store.compareAndSet("hero", -1, source) == CommitResult.Committed)
            check(store.migrateGameplay(GameplayMigrationRequest("migration", "hero", 0, encodeEnvelope(source))) is GameplayMigrationResult.Migrated)
            check(events.apply(start, 1) is GameplayEventCommitResult.Applied)
            check(GameplayService(store, enabled).apply(StartOverexertion("effort", "hero", 0, PhysicalAttribute.STRENGTH), 2) is GameplayCommitResult.Applied)
        }
        "reopen" -> {
            val saved = (store.load("hero") as LoadResult.Loaded).state
            check(saved.storageRevision == 3L && saved.character.enduranceCurrent == 2 && saved.gameplay!!.events!!.timeline.openTurn?.actorId == "hero")
            check(events.apply(start, 1) is GameplayEventCommitResult.Replayed)
            check(events.apply(RecordGameBoundary("end", "hero", 1, GameBoundary.TurnEnded("end-1", "hero", "own-1")), 3) is GameplayEventCommitResult.Applied)
            check(GameplayService(store, enabled).apply(UndoGameplay("undo", "hero", 1, "effort"), 4) is GameplayCommitResult.Blocked)
            check(events.apply(RecordGameBoundary("rest", "hero", 2, GameBoundary.RestCompleted("rest-1", RestKind.FULL, 480)), 4) is GameplayEventCommitResult.Applied)
        }
        "verify" -> {
            val saved = (store.load("hero") as LoadResult.Loaded).state
            check(saved.storageRevision == 5L && saved.character.enduranceCurrent == 2 && saved.gameplay!!.pending?.startOperationId == "effort")
            check(saved.gameplay!!.events!!.timeline.revision == 3L && saved.gameplay!!.events!!.timeline.openTurn == null)
            check(saved.extras.notes == "original" && saved.gameplay!!.migration != null)
            check(DesktopAcquisitionStore(root).load("hero") is LoadResult.Unsupported)
            Files.writeString(root.resolve("event-format2.json"), encodeEnvelope(saved))
        }
        else -> error("Unknown phase")
    }
    println("GAMEPLAY_EVENTS_DESKTOP_OK:$phase")
}
