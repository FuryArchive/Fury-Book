package com.furybook.android.acquisition

import com.furybook.android.ui.screens.AndroidDependencyHintCache
import com.furybook.android.ui.screens.importAndRefreshContent
import com.furybook.dubl.acquisition.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class AndroidPresentationPreparationTest {
    @Test fun overlapping_hint_reads_keep_each_exact_catalog_generation() {
        val base = t13Catalog()
        val root = base.definitions.values.single()
        val catalogs = (1..8).map { number ->
            val ref = DefinitionRef("addon-$number", "dependent")
            base.copy(catalogRevision = "catalog-$number", definitions = base.definitions + (ref to root.copy(ref = ref,
                requirements = RequirementNode.OwnedRank("need", root.sourceRef, root.ref.packId, root.ref.entryId, ComparisonOperator.GE, 1)))) to ref
        }
        val cache = AndroidDependencyHintCache()
        val pool = Executors.newFixedThreadPool(4)
        try {
            val work = (0 until 160).map { index -> Callable {
                val (catalog, expected) = catalogs[index % catalogs.size]
                assertEquals(listOf(expected), cache.get(catalog).dependents(root.ref))
            } }
            pool.invokeAll(work).forEach { it.get(10, TimeUnit.SECONDS) }
            val same = cache.get(catalogs.first().first)
            assertSame(same, cache.get(catalogs.first().first))
        } finally { pool.shutdownNow() }
    }

    @Test fun successful_import_refreshes_after_install_once_and_failure_never_reports_success() {
        val order = mutableListOf<String>()
        val imported = importAndRefreshContent({ order += "root-recapture" }) { order += "install"; "catalog-result" }
        assertEquals("catalog-result", imported)
        assertEquals(listOf("install", "root-recapture"), order)
        val failed = runCatching { importAndRefreshContent({ order += "must-not-refresh" }) { error("fixture install failure") } }
        assertTrue(failed.isFailure)
        assertEquals(listOf("install", "root-recapture"), order)
    }
}
