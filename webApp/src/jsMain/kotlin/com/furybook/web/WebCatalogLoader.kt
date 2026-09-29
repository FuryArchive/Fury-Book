package com.furybook.web

import com.furybook.content.FcpComposition
import com.furybook.content.FcpTextSource
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

private val bundledPaths = listOf(
    "fcp/dubl-3.69/manifest.json",
    "fcp/dubl-3.69/content/conditions_catalog.json",
    "fcp/dubl-3.69/content/development_ability_roots_catalog.json",
    "fcp/dubl-3.69/content/development_catalog.json",
    "fcp/dubl-3.69/content/development_magic_catalog.json",
    "fcp/dubl-3.69/content/development_martial_catalog.json",
    "fcp/dubl-3.69/content/development_regular_catalog.json",
    "fcp/dubl-3.69/content/development_special_catalog.json",
    "fcp/dubl-3.69/content/magic_equipment_catalog.json",
    "fcp/dubl-3.69/content/skill_effects_catalog.json",
    "fcp/dubl-3.69/content/skills_catalog.json",
    "fcp/dubl-chi-3.69/manifest.json",
    "fcp/dubl-chi-3.69/content/chi_catalog.json",
    "fcp/dubl-chi-3.69/content/development_chi_catalog.json",
)

suspend fun loadBundledFcpTexts(): Map<String, String> {
    val result = linkedMapOf<String, String>()
    for (path in bundledPaths) {
        val response = (window.asDynamic().fetch("./$path") as kotlin.js.Promise<dynamic>).await()
        check(response.ok as Boolean) { "Failed to load $path: HTTP ${response.status}" }
        result[path] = (response.text() as kotlin.js.Promise<String>).await()
    }
    return result
}
