import com.furybook.dubl.acquisition.*
import com.furybook.dubl.actions.*
import com.furybook.dubl.data.DesktopAcquisitionStore
import com.furybook.dubl.model.*
import kotlinx.coroutines.runBlocking
import java.nio.file.Path

fun main(args: Array<String>) = runBlocking {
    val root = Path.of(args[0])
    val store = DesktopAcquisitionStore(root, gameplayFormatEnabled = true)
    val enabled = { GameplayCapabilities(true, true) }
    suspend fun state() = (store.load("hero") as LoadResult.Loaded).state
    suspend fun event(id: String, make: (String) -> GameBoundary) {
        val current = state()
        val revision = current.gameplay?.events?.timeline?.revision ?: 0
        check(GameplayEventService(store, enabled).apply(
            RecordGameBoundary(id, "hero", revision, make(id)), current.storageRevision) is GameplayEventCommitResult.Applied)
    }
    when (args[1]) {
        "start" -> {
            val source = StoredCharacter(DublCharacter("hero", experience = 1000, hpCurrent = -2),
                CharacterSheetExtras(notes = "keep exact note"), emptyList(),
                Ledger(Opening("opening", "source", Amount(1000, 0), RequirementNode.CreationPhase.Phase.Creating,
                    OpeningProvenance.NewCharacter), emptyList()), emptyList(), emptyList(), SyncMetadata(0), 0, 0,
                RequirementNode.CreationPhase.Phase.Creating)
            check(store.compareAndSet("hero", -1, source) == CommitResult.Committed)
            val raw = encodeEnvelope(source)
            check(store.migrateGameplay(GameplayMigrationRequest("migration", "hero", 0, canonicalJson(raw))) is GameplayMigrationResult.Migrated)
            val migrated = state()
            check(GameplayService(store, enabled).apply(StartOverexertion("effort", "hero", 0, PhysicalAttribute.STRENGTH), migrated.storageRevision) is GameplayCommitResult.Applied)
            event("own-1") { GameBoundary.TurnStarted(it, "hero") }
            event("reserve") { GameBoundary.DefenseReserved(it, 2) }
            event("react") { GameBoundary.ReactionSpent(it) }
            val saved = state()
            check(saved.gameplay!!.events!!.timeline.combat == CombatResources(1, 0, 2))
            check(saved.character.enduranceCurrent == 2 && saved.character.hpCurrent == -2)
            check(saved.gameplay.pending?.startOperationId == "effort")
            check(saved.gameplay.migration!!.sourceRaw == raw)
        }
        "reopen" -> {
            val reopened = state()
            check(reopened.gameplay!!.events!!.timeline.combat == CombatResources(1, 0, 2))
            val old = RecordGameBoundary("reserve", "hero", 1, GameBoundary.DefenseReserved("reserve", 2))
            check(GameplayEventService(store, enabled).apply(old, 999) is GameplayEventCommitResult.Replayed)
            check(state().gameplay!!.events!!.timeline.combat == CombatResources(1, 0, 2))
            event("end-own-1") { GameBoundary.TurnEnded(it, "hero", "own-1") }
            event("other") { GameBoundary.TurnStarted(it, "enemy") }
            event("end-other") { GameBoundary.TurnEnded(it, "enemy", "other") }
            event("round") { GameBoundary.RoundEnded(it) }
            check(state().gameplay!!.events!!.timeline.combat.defenseBonus == 2)
            event("own-2") { GameBoundary.TurnStarted(it, "hero") }
            check(state().gameplay!!.events!!.timeline.combat == CombatResources(3, 1, 0))
        }
        "verify" -> {
            val saved = state()
            check(saved.gameplay!!.events!!.timeline.combat == CombatResources(3, 1, 0))
            check(saved.character.hpCurrent == -2 && saved.character.enduranceCurrent == 2)
            check(saved.extras.notes == "keep exact note" && balance(saved.ledger) == Amount(1000, 0))
            check(saved.gameplay.pending?.startOperationId == "effort")
            val raw = encodeEnvelope(saved)
            check(decodeEnvelope(raw, true) is LoadResult.Loaded)
            check((decodeEnvelope(raw) as LoadResult.Unsupported).raw == raw)
            check(DesktopAcquisitionStore(root).load("hero") is LoadResult.Unsupported)
            check(runCatching { encodeSyncEnvelope(saved) }.isFailure)
        }
        else -> error("Unknown phase")
    }
    println("GAMEPLAY_COMBAT_DESKTOP_OK:${args[1]}")
}
