package com.furybook.android.ui.acquisition

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import com.furybook.android.data.*
import com.furybook.content.FcpComposition
import com.furybook.dubl.acquisition.AcquisitionCatalog
import com.furybook.dubl.acquisition.loadAcquisitionCatalog
import com.furybook.dubl.content.*
import com.furybook.dubl.data.mergeDevelopmentCatalogs
import com.furybook.dubl.model.*

/** One captured FCP composition for display and canonical quotes. Not another price authority. */
data class AndroidAcquisitionContent(val composition: FcpComposition, val development: DevelopmentCatalog,
    val chi: ChiCatalog, val magicEquipment: MagicEquipmentCatalog, val skillEffects: SkillEffectCatalog,
    val conditions: ConditionCatalog, val acquisition: AcquisitionCatalog) {
    companion object {
        /** Caller dispatches this filesystem/catalog preparation off the UI thread. */
        fun load(context: Context, includeChi: Boolean): AndroidAcquisitionContent {
            val composition = AndroidContentPackState.composition(context, includeChi)
            val core = AndroidDublFcp.loader(context)
            val chi = AndroidDublFcp.chiLoader(context)
            val external = composition.active.filter { it.id !in AndroidContentPackState.bundledPackIds() }
                .map { AndroidFcpInstaller.openInstalled(context, it) }
            val layers = mutableListOf(core.loadDevelopment().let { if (includeChi) it else it.withoutEntries(chi.claimedDevelopmentIds()) })
            if (includeChi) layers += chi.loadDevelopment()
            external.forEach { layers += DublDevelopmentAddon.load(it) }
            return AndroidAcquisitionContent(composition, mergeDevelopmentCatalogs(*layers.toTypedArray()),
                if (includeChi) chi.loadChi() else ChiCatalog("disabled", emptyList(), emptyList()),
                core.loadMagicEquipment(), core.loadSkillEffects(), core.loadConditions(),
                loadAcquisitionCatalog(listOf(core.pack) + (if (includeChi) listOf(chi.pack) else emptyList()) + external, includeChi).catalog)
        }
    }
}
val LocalAndroidAcquisitionContent = staticCompositionLocalOf<AndroidAcquisitionContent> { error("Captured Android content is required") }
