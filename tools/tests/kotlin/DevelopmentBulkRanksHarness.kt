import com.furybook.dubl.application.ApplicationUndoManager
import com.furybook.dubl.application.DevelopmentApplication
import com.furybook.dubl.data.CharacterStore
import com.furybook.dubl.data.SnapshotCodec
import com.furybook.dubl.data.parseDevelopmentCatalog
import com.furybook.dubl.model.*
import com.furybook.dubl.state.CharacterSession
import java.io.File

private class RoundTripStore(initial: DublCharacter) : CharacterStore {
    private var serialized = SnapshotCodec.encode(AppSnapshot(listOf(initial), initial.id))
    var writes = 0
        private set
    override fun load(): AppSnapshot = SnapshotCodec.decode(serialized) { error("Unexpected missing ID") }
    override fun save(snapshot: AppSnapshot) {
        serialized = SnapshotCodec.encode(snapshot)
        writes++
    }
}

private fun character(healthRank: Int = 0, size: Int = 5, complete: Boolean = true): DublCharacter = DublCharacter(
    id = "health-regression", experience = 10000, creationComplete = complete, size = size,
    attributes = defaultAttributes() + (AttributeId.CONSTITUTION to AttributeValue(2)),
    hpCurrent = -2,
    development = if (healthRank == 0) emptyMap() else mapOf(DevelopmentEffectIds.INCREDIBLE_HEALTH to OwnedDevelopment(healthRank)),
).normalized()

fun main() {
    val entries = File("shared/src/commonMain/resources/fcp").walkTopDown()
        .filter { it.isFile && it.name.startsWith("development_") && it.extension == "json" }
        .flatMap { parseDevelopmentCatalog(it.readText()).entries }.distinctBy { it.id }.toList()
    val catalog = DevelopmentCatalog("bundled-regression", entries)
    val health = catalog.byId(DevelopmentEffectIds.INCREDIBLE_HEALTH)!!
    check(health.name == "Невероятное здоровье" && health.maxRank == 20 && health.cost == 5)
    var scenarios = 0
    fun scenario(name: String, test: () -> Unit) {
        test(); scenarios++; println("PASS $name")
    }

    for (complete in listOf(false, true)) {
        scenario("all twenty sequential ranks persist and reload; creationComplete=$complete") {
            val store = RoundTripStore(character(complete = complete))
            var session = CharacterSession(store) { error("Unexpected ID creation") }
            for (rank in 1..health.maxRank) {
                val app = DevelopmentApplication(session, ApplicationUndoManager())
                val before = session.active
                val result = app.acquire(catalog, DevelopmentAcquisitionRequest.single(health.id))
                check(result.applied) { "rank $rank: ${result.message}" }
                check(result.plan.xpCost == 5)
                check(session.active.developmentRank(health.id) == rank)
                check(session.active.healthMaximum == before.healthMaximum + 1)
                check(session.active.hpCurrent == -2) { "Buying ranks must not heal damage" }
                val planner = DevelopmentAcquisitionPlanner(session.active, catalog)
                planner.unlocks(health.id)
                val rules = DevelopmentRules(session.active, catalog, DevelopmentProgress(session.active.development))
                rules.ownedSheetSections()
                catalog.entries.forEach { rules.availability(it) }
                check(store.writes == rank)
                session = CharacterSession(store) { error("Unexpected ID creation") }
                check(session.active.developmentRank(health.id) == rank)
            }
        }
    }

    for (startRank in listOf(0, 4, 5, 6, 19)) {
        scenario("bulk purchase from rank $startRank has one save and one undo") {
            val store = RoundTripStore(character(startRank))
            val session = CharacterSession(store) { "unused" }
            val undo = ApplicationUndoManager()
            val app = DevelopmentApplication(session, undo)
            val before = session.active
            val beforeXp = CharacterEconomy.breakdown(before, catalog).remainingXp
            val request = DevelopmentAcquisitionRequest.single(health.id, targetRank = health.maxRank)
            val result = app.acquire(catalog, request)
            check(result.applied) { result.message }
            val expectedCost = (health.maxRank - startRank) * 5
            check(result.plan.xpCost == expectedCost)
            check(result.plan.steps.size == 1)
            check(session.active.developmentRank(health.id) == 20)
            check(session.active.healthMaximum == before.healthMaximum + 20 - startRank)
            check(session.active.hpCurrent == before.hpCurrent)
            check(beforeXp - CharacterEconomy.breakdown(session.active, catalog).remainingXp == expectedCost)
            check(store.writes == 1)
            check(CharacterSession(store) { "unused" }.active == session.active)
            // Re-applying a stale maximum-rank request cannot charge or add ranks twice.
            check(!app.acquire(catalog, request).applied)
            check(store.writes == 1)
            check(undo.undoLast())
            check(session.active == before)
            check(store.writes == 2)
            check(!undo.undoLast())
        }
    }

    for (size in listOf(1, 6, 7, 10)) {
        scenario("bulk health scaling at size $size") {
            val store = RoundTripStore(character(size = size))
            val session = CharacterSession(store) { "unused" }
            val app = DevelopmentApplication(session, ApplicationUndoManager())
            val result = app.acquire(catalog, DevelopmentAcquisitionRequest.single(health.id, targetRank = health.maxRank))
            check(result.applied)
            check(session.active.incredibleHealthBonus == 20 * incredibleHealthPerRank(size))
            check(session.active.healthMaximum == (session.active.constitution * size + session.active.strength + 20 * incredibleHealthPerRank(size)).coerceAtLeast(0))
            check(result.plan.xpCost == 100)
        }
    }

    scenario("insufficient XP does not partially purchase ranks or save") {
        val store = RoundTripStore(character().copy(experience = 0))
        val session = CharacterSession(store) { "unused" }
        val undo = ApplicationUndoManager()
        val app = DevelopmentApplication(session, undo)
        val before = session.active
        val result = app.acquire(catalog, DevelopmentAcquisitionRequest.single(health.id, targetRank = health.maxRank))
        check(!result.applied && !result.plan.canAfford)
        check(session.active == before && store.writes == 0 && !undo.canUndo)
    }

    scenario("bulk purchase includes missing Constitution prerequisite only once") {
        val base = character().copy(attributes = defaultAttributes()).normalized()
        val store = RoundTripStore(base)
        val session = CharacterSession(store) { "unused" }
        val undo = ApplicationUndoManager()
        val app = DevelopmentApplication(session, undo)
        val before = session.active
        val result = app.acquire(catalog, DevelopmentAcquisitionRequest.single(health.id, targetRank = health.maxRank))
        check(result.applied)
        check(session.active.constitution == 2 && session.active.developmentRank(health.id) == 20)
        check(result.plan.steps.filterIsInstance<DevelopmentAcquisitionStep.Attribute>().size == 1)
        check(result.plan.steps.filterIsInstance<DevelopmentAcquisitionStep.Development>().single().xpCost == 100)
        check(store.writes == 1)
        check(undo.undoLast() && session.active == before)
    }

    scenario("already-maxed character is unchanged") {
        val store = RoundTripStore(character(20))
        val session = CharacterSession(store) { "unused" }
        val app = DevelopmentApplication(session, ApplicationUndoManager())
        val before = session.active
        val result = app.acquire(catalog, DevelopmentAcquisitionRequest.single(health.id, targetRank = health.maxRank))
        check(!result.applied && result.plan.steps.isEmpty() && result.plan.xpCost == 0)
        check(store.writes == 0 && session.active == before)
    }
    println("DEVELOPMENT_BULK_RANKS_OK scenarios=$scenarios")
}
