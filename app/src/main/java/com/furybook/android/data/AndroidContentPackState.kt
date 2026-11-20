package com.furybook.android.data

import android.content.Context
import com.furybook.content.FcpComposition
import com.furybook.content.FcpManifest
import com.furybook.dubl.content.DublChiFcp
import com.furybook.dubl.content.DublDevelopmentAddon
import com.furybook.dubl.content.DublFcp
import com.furybook.dubl.content.DublUiMount
import com.furybook.dubl.content.DublUiRegistry
import com.furybook.dubl.data.mergeDevelopmentCatalogs
object AndroidContentPackState {
    private const val PREFS = "fury-content-packs"

    private fun preferences(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isPackEnabled(context: Context, packId: String): Boolean =
        packId == DublFcp.PACK_ID || preferences(context).getBoolean(packId, false)

    fun setPackEnabled(context: Context, packId: String, enabled: Boolean) {
        when (packId) {
            DublFcp.PACK_ID -> require(enabled) { "Required FCP ${DublFcp.PACK_ID} cannot be disabled" }
            DublChiFcp.PACK_ID -> if (enabled) AndroidDublFcp.chiLoader(context).verifyContent()
            else -> {
                val manifest = AndroidFcpInstaller.findInstalled(context, packId)
                    ?: error("Installed FCP not found: $packId")
                if (enabled && !isPackEnabled(context, packId)) {
                    validateExternalActivation(context, manifest)
                }
            }
        }
        if (packId != DublFcp.PACK_ID) {
            preferences(context).edit().putBoolean(packId, enabled).apply()
            DevelopmentCatalogRepository.invalidate()
        }
    }

    fun isChiEnabled(context: Context): Boolean = isPackEnabled(context, DublChiFcp.PACK_ID)

    fun setChiEnabled(context: Context, enabled: Boolean) =
        setPackEnabled(context, DublChiFcp.PACK_ID, enabled)

    fun composition(context: Context, chiEnabled: Boolean): FcpComposition {
        val installed = AndroidFcpInstaller.listInstalled(context)
        val enabled = linkedSetOf<String>()
        if (chiEnabled) enabled += DublChiFcp.PACK_ID
        installed.asSequence()
            .filter(DublDevelopmentAddon::isSupported)
            .filter { preferences(context).getBoolean(it.id, false) }
            .mapTo(enabled) { it.id }

        return FcpComposition.resolve(
            manifests = buildList {
                add(AndroidDublFcp.loader(context).pack.manifest)
                add(AndroidDublFcp.chiLoader(context).pack.manifest)
                addAll(installed)
            },
            requiredPackIds = setOf(DublFcp.PACK_ID),
            enabledPackIds = enabled,
        )
    }

    fun uiMounts(
        context: Context,
        enabled: Boolean,
        surface: String,
        component: String,
    ): List<DublUiMount> = DublUiRegistry.mounts(composition(context, enabled), surface, component)

    fun canActivatePack(manifest: FcpManifest): Boolean =
        manifest.id == DublFcp.PACK_ID ||
            manifest.id == DublChiFcp.PACK_ID ||
            DublDevelopmentAddon.isSupported(manifest)

    fun bundledPackIds(): Set<String> = setOf(DublFcp.PACK_ID, DublChiFcp.PACK_ID)

    fun chiDevelopmentIds(context: Context): Set<String> = linkedSetOf<String>().apply {
        addAll(AndroidDublFcp.chiLoader(context).loadDevelopment().entries.map { it.id })
        addAll(AndroidDublFcp.chiLoader(context).claimedDevelopmentIds())
    }

    private fun validateExternalActivation(context: Context, manifest: FcpManifest) {
        DublDevelopmentAddon.requireSupported(manifest)
        val candidate = DublDevelopmentAddon.load(AndroidFcpInstaller.openInstalled(context, manifest))
        val layers = mutableListOf(
            AndroidDublFcp.loader(context).loadDevelopment(),
            AndroidDublFcp.chiLoader(context).loadDevelopment(),
        )
        AndroidFcpInstaller.listInstalled(context)
            .asSequence()
            .filter { it.id != manifest.id }
            .filter(DublDevelopmentAddon::isSupported)
            .filter { preferences(context).getBoolean(it.id, false) }
            .map { installed -> DublDevelopmentAddon.load(AndroidFcpInstaller.openInstalled(context, installed)) }
            .forEach(layers::add)
        layers += candidate
        mergeDevelopmentCatalogs(*layers.toTypedArray())
    }
}
