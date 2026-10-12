package com.furybook.dubl.application

import com.furybook.dubl.acquisition.AcquisitionService
import com.furybook.dubl.acquisition.AcquisitionRequest
import com.furybook.dubl.acquisition.AcquisitionPlan
import com.furybook.dubl.acquisition.ConfirmAcquisition
import com.furybook.dubl.acquisition.ApplyResult
import com.furybook.dubl.model.DevelopmentEntry
import com.furybook.dubl.model.DevelopmentCatalog
import com.furybook.dubl.model.DevelopmentAcquisitionPlanner
import com.furybook.dubl.model.DevelopmentAcquisitionRequest
import com.furybook.dubl.model.DevelopmentAcquisitionResult
import com.furybook.dubl.state.CharacterSession

class DevelopmentApplication internal constructor(
    private val session: CharacterSession,
    private val undo: ApplicationUndoManager,
    private val acquisitionService: AcquisitionService? = null,
) {
    /** Canonical commands stay in their durable store; legacy session/Undo are never published here. */
    suspend fun previewAcquisition(request: AcquisitionRequest): AcquisitionPlan =
        checkNotNull(acquisitionService) { "Canonical acquisition service is not configured" }.preview(request)

    suspend fun confirmAcquisition(command: ConfirmAcquisition): ApplyResult =
        checkNotNull(acquisitionService) { "Canonical acquisition service is not configured" }.apply(command)

    fun setRank(entryId: String, rank: Int, optionIndex: Int = 0) = session.setDevelopmentRank(entryId, rank, optionIndex)
    fun setOverride(entry: DevelopmentEntry) = session.setDevelopmentOverride(entry)
    fun resetOverride(entryId: String) = session.resetDevelopmentOverride(entryId)
    fun addCustom(entry: DevelopmentEntry): String? = session.addCustomDevelopment(entry)
    fun updateCustom(entry: DevelopmentEntry): Boolean = session.updateCustomDevelopment(entry)
    fun removeCustom(entryId: String) = session.removeCustomDevelopment(entryId)
    fun setChiEnabled(enabled: Boolean) = session.setChiEnabled(enabled)
    fun setChiBonusRanks(rank: Int) = session.setChiBonusRanks(rank)

    fun acquire(
        catalog: DevelopmentCatalog,
        request: DevelopmentAcquisitionRequest,
    ): DevelopmentAcquisitionResult {
        val before = session.active
        val plan = DevelopmentAcquisitionPlanner(before, catalog).plan(request)
        if (!plan.canApply) {
            val message = when {
                plan.unresolvedRequirements.isNotEmpty() -> "Есть требования, которые нельзя выполнить автоматически"
                !plan.canAfford -> "Недостаточно XP или очков способностей"
                plan.steps.isEmpty() -> "Все выбранные требования уже выполнены"
                else -> "План нельзя применить"
            }
            return DevelopmentAcquisitionResult(false, plan, message)
        }
        session.updateActive { plan.projectedCharacter }
        val after = session.active
        if (after != before) {
            undo.record { session.updateActive { before } }
            return DevelopmentAcquisitionResult(true, plan)
        }
        return DevelopmentAcquisitionResult(false, plan, "Изменения не потребовались")
    }

    fun changeChi(delta: Int) {
        val before = session.active.chiCurrent
        session.changeChi(delta)
        val applied = session.active.chiCurrent - before
        if (applied != 0) undo.record { session.changeChi(-applied) }
    }

    fun restoreChi() = session.restoreChi()
}
