package com.furybook.web

import com.furybook.content.FcpComposition
import com.furybook.content.FcpTextSource
import com.furybook.content.parseFcpManifest
import com.furybook.dubl.content.DublChiFcp
import com.furybook.dubl.content.DublChiFcpCatalogLoader
import com.furybook.dubl.content.DublFcp
import com.furybook.dubl.content.DublFcpCatalogLoader
import com.furybook.dubl.data.mergeDevelopmentCatalogs
import com.furybook.dubl.model.ChiCatalog
import com.furybook.dubl.model.ConditionCatalog
import com.furybook.dubl.model.DevelopmentCatalog
import com.furybook.dubl.model.MagicEquipmentCatalog
import com.furybook.dubl.model.SkillEffectCatalog
import com.furybook.dubl.model.withoutEntries
import kotlinx.browser.window
import kotlinx.coroutines.await

class WebCatalogLoader(resourceTexts: Map<String, String>) {
    private val source = FcpTextSource { path -> resourceTexts[path] }
    private val fcp: DublFcpCatalogLoader = DublFcp.open(source)
    private val chiFcp: DublChiFcpCatalogLoader = DublChiFcp.open(source)

    val manifest get() = fcp.pack.manifest
    val chiManifest get() = chiFcp.pack.manifest

    fun loadConditions(): ConditionCatalog = fcp.loadConditions()
    fun loadDevelopment(includeChi: Boolean = false): DevelopmentCatalog {
        val composition = FcpComposition.resolve(
            manifests = listOf(manifest, chiManifest),
            requiredPackIds = setOf(DublFcp.PACK_ID),
            enabledPackIds = if (includeChi) setOf(DublChiFcp.PACK_ID) else emptySet(),
        )
        val core = fcp.loadDevelopment().withoutEntries(composition.inactiveClaims("dubl.development"))
        return if (includeChi) mergeDevelopmentCatalogs(core, chiFcp.loadDevelopment()) else core
    }
    fun loadChiDevelopment(): DevelopmentCatalog = chiFcp.loadDevelopment()
    fun loadChi(): ChiCatalog = chiFcp.loadChi()
    fun loadMagicEquipment(): MagicEquipmentCatalog = fcp.loadMagicEquipment()
    fun loadSkillEffects(): SkillEffectCatalog = fcp.loadSkillEffects()
    fun verifyChiPack() = chiFcp.verifyContent()
}

private val bundledPackRoots = listOf("fcp/dubl-3.69", "fcp/dubl-chi-3.69")

suspend fun loadBundledFcpTexts(): Map<String, String> {
    val result = linkedMapOf<String, String>()
    suspend fun fetch(path: String): String {
        val response = (window.asDynamic().fetch("./$path") as kotlin.js.Promise<dynamic>).await()
        check(response.ok as Boolean) { "Failed to load $path: HTTP ${response.status}" }
        return (response.text() as kotlin.js.Promise<String>).await()
    }
    for (root in bundledPackRoots) {
        val manifestPath = "$root/manifest.json"
        val raw = fetch(manifestPath)
        val manifest = parseFcpManifest(raw) // Validates relative paths before any content request.
        result[manifestPath] = raw
        for (entry in manifest.entries.sortedWith(compareBy({ it.order }, { it.path }))) {
            val path = "$root/${entry.path}"
            if (path !in result) result[path] = fetch(path)
        }
    }
    return result
}
