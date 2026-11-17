package com.furybook.android.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.furybook.android.data.AndroidContentPackState
import com.furybook.android.data.ConditionCatalogRepository
import com.furybook.android.data.DevelopmentCatalogRepository
import com.furybook.android.data.SkillEffectCatalogRepository
import com.furybook.android.state.CharacterController
import com.furybook.content.FcpComposition
import com.furybook.dubl.content.DublResourceMeterModel
import com.furybook.dubl.content.DublUiRenderModels
import com.furybook.dubl.model.CharacterConditionId
import com.furybook.dubl.model.CharacterSheetExtras
import com.furybook.dubl.model.CharacterSheetResourceId
import com.furybook.dubl.model.ConditionCatalog
import com.furybook.dubl.model.DevelopmentCatalog
import com.furybook.dubl.model.DublCharacter
import com.furybook.dubl.model.SkillEffectCatalog
import com.furybook.dubl.model.effectiveDevelopmentCatalog
import com.furybook.dubl.model.withoutChiContent
import com.furybook.dubl.ui.sheet.DublSheetHost

/** Android adapter exposing [CharacterController] and the bundled catalogs to the shared sheet. */
class AndroidSheetHost(
    private val controller: CharacterController,
    private val context: Context,
    override val chiPackEnabled: Boolean,
) : DublSheetHost {
    private val appContext = context.applicationContext

    override val active: DublCharacter get() = controller.active
    override val extras: CharacterSheetExtras get() = controller.extras
    override val canUndo: Boolean get() = controller.canUndo
    override val conditionCatalog: ConditionCatalog = ConditionCatalogRepository(appContext).load()
    private val canonicalDevelopmentCatalog: DevelopmentCatalog =
        DevelopmentCatalogRepository(appContext).load(includeChi = chiPackEnabled)
    override val developmentCatalog: DevelopmentCatalog
        get() = controller.active
            .effectiveDevelopmentCatalog(canonicalDevelopmentCatalog)
            .let { catalog -> if (chiPackEnabled) catalog else catalog.withoutChiContent() }
    override val skillEffectCatalog: SkillEffectCatalog = SkillEffectCatalogRepository(appContext).load()

    // The pack composition reads installed manifests from disk; resolve it once per host (the host is
    // remembered per chi setting, and the sheet leaves composition while packs are managed elsewhere).
    private val composition: FcpComposition by lazy { AndroidContentPackState.composition(appContext, chiPackEnabled) }
    private var meterCache: Pair<DublCharacter, List<DublResourceMeterModel>>? = null

    /** Meters for the current character, recomputed from the cached composition only when it changes. */
    override fun resourceMeters(): List<DublResourceMeterModel> {
        val character = controller.active
        meterCache?.let { (cachedFor, meters) -> if (cachedFor == character) return meters }
        return DublUiRenderModels.resourceMeters(composition, character).also { meterCache = character to it }
    }

    override fun changeResource(resource: CharacterSheetResourceId, delta: Int) {
        controller.changeResource(resource, delta)
    }

    override fun changeCustomResource(uid: String, delta: Int) {
        controller.changeCustomResource(uid, delta)
    }

    override fun restoreMountedResource(resource: CharacterSheetResourceId) {
        controller.restoreMountedResource(resource)
    }

    override fun toggleCondition(condition: CharacterConditionId) {
        controller.toggleCondition(condition)
    }

    override fun setCustomConditionActive(id: String, active: Boolean) {
        controller.setCustomConditionActive(id, active)
    }

    override fun undoLast(): Boolean = controller.undoLast()
}

@Composable
fun rememberAndroidSheetHost(
    controller: CharacterController,
    context: Context,
    chiPackEnabled: Boolean,
): AndroidSheetHost = remember(controller, context, chiPackEnabled) {
    AndroidSheetHost(controller, context, chiPackEnabled)
}
