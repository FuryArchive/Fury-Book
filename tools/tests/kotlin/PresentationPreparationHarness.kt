import com.furybook.android.ui.screens.*
import com.furybook.dubl.acquisition.*
import kotlinx.coroutines.runBlocking
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

fun main() = runBlocking<Unit> {
    val caller = Thread.currentThread()
    var calls = 0
    val prepared = prepareDevelopmentRows(listOf("all", "plan", "owned"), { it }) {
        check(Thread.currentThread() !== caller)
        calls++
        "ready:$it"
    }
    repeat(4) { check(prepared.getValue("owned") == "ready:owned") }
    check(calls == 3)
    val changed = prepareDevelopmentRows(listOf("all"), { it }) { "new-character:$it" }
    check(changed == mapOf("all" to "new-character:all"))

    val source = RuleSourceRef("presentation-fixture", "1", "Explicit synthetic typed dependencies")
    fun definition(ref: DefinitionRef, requirement: RequirementNode = RequirementNode.AllOf(
        "none", source, emptyList(), NoRequirementsEvidence(source, "None"))) = AcquisitionDefinition(
        ref, AcquisitionKind.DEVELOPMENT, 3, RepeatPolicy.Single, requirement,
        PriceRule.Fixed(20, 0, source), SourceStatus.Confirmed, "Same display name", source)
    val root = DefinitionRef("core", "root")
    val otherPack = DefinitionRef("addon", "root")
    val dependent = DefinitionRef("core", "dependent")
    val owned = RequirementNode.OwnedRank("rank", source, root.packId, root.entryId, ComparisonOperator.GE, 1)
    val tree = RequirementNode.AllOf("and", source, listOf(owned,
        RequirementNode.AnyOf("or", source, listOf(owned))))
    val catalog = AcquisitionCatalog(listOf(definition(root), definition(otherPack), definition(dependent, tree),
        definition(DefinitionRef("core", "prohibition"), RequirementNode.Exclusive("not", source, owned)),
        definition(DefinitionRef("core", "unknown"), RequirementNode.Unresolved("unresolved", source, "unknown")))
        .associateBy { it.ref }, "catalog", "author", sourceRuleRevision="source")
    val hints = AcquisitionDependencyHints(catalog)
    check(hints.dependents(root) == listOf(dependent))
    check(hints.dependents(otherPack).isEmpty())
    check(hints.counts() == mapOf(root to 1))
    check(catalog.definitions.getValue(dependent).requirements == tree)
    val cache = AcquisitionDependencyHintCache()
    val one = cache.get(catalog)
    check(one === cache.get(catalog.copy()))
    check(one !== cache.get(catalog.copy(sourceRuleRevision="new-source")))
    val androidCache = AndroidDependencyHintCache()
    val catalogs = (1..8).map { number ->
        val ref = DefinitionRef("generation-$number", "dependent")
        catalog.copy(catalogRevision="catalog-$number", definitions=mapOf(root to definition(root), ref to definition(ref, owned))) to ref
    }
    val pool = Executors.newFixedThreadPool(4)
    try {
        pool.invokeAll((0 until 160).map { index -> Callable {
            val (value, expected) = catalogs[index % catalogs.size]
            check(androidCache.get(value).dependents(root) == listOf(expected))
        } }).forEach { it.get(10, TimeUnit.SECONDS) }
    } finally { pool.shutdownNow() }
    val order = mutableListOf<String>()
    check(importAndRefreshContent({ order += "refresh" }) { order += "install"; "result" } == "result")
    check(order == listOf("install", "refresh"))
    check(runCatching { importAndRefreshContent({ order += "wrong" }) { error("install rejected") } }.isFailure)
    check(order == listOf("install", "refresh"))
    println("PRESENTATION_PREPARATION_OK")
}
