package com.furybook.android.ui.acquisition

import com.furybook.dubl.acquisition.*

data class AndroidAcquisitionViewBinding(
    val price: String,
    val remaining: String?,
    val status: String,
    val sync: String,
    val canConfirm: Boolean,
    val receiptPrice: String?,
    val receiptRemaining: String?,
    val currentBalance: String?,
)

fun acquisitionAmount(value: Amount): String = "${value.xp} XP / ${value.ap} ОСп"
fun acquisitionPrice(value: AcquisitionDisplayedPrice): String = when (value) {
    is AcquisitionDisplayedPrice.Known -> acquisitionAmount(value.amount)
    is AcquisitionDisplayedPrice.Unknown -> value.message
}

fun bindAndroidAcquisition(model: AcquisitionUiModel) = AndroidAcquisitionViewBinding(
    acquisitionPrice(model.price), model.remaining?.let(::acquisitionAmount), model.summary,
    when (model.sync.status) {
        AcquisitionSyncStatus.LocalOnlyUnavailable -> "Облачная синхронизация недоступна; локальный результат отдельный"
        AcquisitionSyncStatus.Pending -> "Ожидает синхронизации: ${model.sync.pendingCount}"
        AcquisitionSyncStatus.Synced -> "Синхронизировано"
        AcquisitionSyncStatus.Retryable -> "Нужен повтор синхронизации"
        AcquisitionSyncStatus.Conflict -> "Конфликт облачных версий; автоматического объединения нет"
        AcquisitionSyncStatus.Unavailable -> "Статус синхронизации недоступен"
    }, model.canConfirm && model.phase == AcquisitionUiPhase.Ready,
    model.receipt?.cost?.let(::acquisitionAmount), model.receipt?.remaining?.let(::acquisitionAmount),
    model.receipt?.currentBalance?.let(::acquisitionAmount))

fun acquisitionRowLabel(row: AcquisitionDisplayRow): String = row.label + when {
    row.excludedTarget -> " · не приобретается"
    row.alreadyOwned -> " · уже приобретено"
    row.fromRank != null && row.toRank != null -> " · ${row.fromRank} → ${row.toRank}"
    else -> ""
}

/** Both signed amounts must be entered explicitly; blank is never an implied zero. */
fun parseManualAmount(xp: String, ap: String, reason: String): Amount? {
    if (reason.isBlank()) return null
    fun integer(value: String): Long? = value.trim().takeIf { it.matches(Regex("[+-]?[0-9]+")) }?.toLongOrNull()
    return Amount(integer(xp) ?: return null, integer(ap) ?: return null)
}
