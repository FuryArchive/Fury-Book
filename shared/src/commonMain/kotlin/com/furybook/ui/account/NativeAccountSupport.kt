package com.furybook.ui.account

import kotlinx.coroutines.async

import com.furybook.core.cloud.FuryCloudTransport
import com.furybook.core.cloud.NativeCloudStatus
import com.furybook.core.cloud.NativeSyncConflict
import com.furybook.core.json.jsonObject
import com.furybook.core.json.jsonString
import com.furybook.core.json.jsonStringify
import com.furybook.core.json.parseRoot
import com.furybook.core.json.string
import com.furybook.dubl.model.AppSnapshot

/** Desktop and Android share these steps of their [FuryAccount] adapters. */
fun NativeCloudStatus.toAccountStatus(pending: Int): FuryAccountStatus = when (this) {
    NativeCloudStatus.SIGNED_OUT -> FuryAccountStatus.SIGNED_OUT
    NativeCloudStatus.CONNECTING -> FuryAccountStatus.CONNECTING
    NativeCloudStatus.SYNCING -> FuryAccountStatus.SYNCING
    NativeCloudStatus.SYNCED -> if (pending > 0) FuryAccountStatus.PENDING else FuryAccountStatus.SYNCED
    NativeCloudStatus.OFFLINE -> FuryAccountStatus.OFFLINE
    NativeCloudStatus.CONFLICT -> FuryAccountStatus.CONFLICT
    NativeCloudStatus.ERROR -> FuryAccountStatus.ERROR
}

fun accountStatusText(status: FuryAccountStatus, message: String?, pending: Int): String = message?.takeIf { it.isNotBlank() } ?: when (status) {
    FuryAccountStatus.SIGNED_OUT -> "Не подключено"
    FuryAccountStatus.CONNECTING -> "Подключение…"
    FuryAccountStatus.SYNCING -> "Синхронизация…"
    FuryAccountStatus.SYNCED -> "Синхронизировано"
    FuryAccountStatus.PENDING -> "Изменений к отправке: $pending"
    FuryAccountStatus.OFFLINE -> "Офлайн · изменения в очереди"
    FuryAccountStatus.CONFLICT -> "Конфликт изменений"
    FuryAccountStatus.ERROR -> "Ошибка синхронизации"
}

fun nativeAccountConflicts(conflicts: List<NativeSyncConflict>, snapshot: AppSnapshot): List<FuryAccountConflict> =
    conflicts.map { conflict ->
        FuryAccountConflict(
            conflict.characterId,
            snapshot.characters.firstOrNull { it.id == conflict.characterId }?.name ?: "Персонаж",
            deleted = conflict.deleteRequested,
        )
    }

/** The nickname RPC the web app already uses; same Fury Account users. */
suspend fun setNativeNickname(transport: FuryCloudTransport, nickname: String): String {
    val clean = nickname.trim()
    val raw = transport.rpc("set_profile_nickname", jsonStringify(jsonObject("p_nickname" to jsonString(clean))))
    return runCatching { parseRoot(raw).string("nickname") }.getOrNull()?.takeIf { it.isNotBlank() } ?: clean
}

/** Runs [block] in [scope] and waits for it; if the caller is cancelled (the login screen left), [block] still finishes. */
suspend fun <T> detachedFromCaller(scope: kotlinx.coroutines.CoroutineScope, block: suspend () -> T): T =
    scope.async { block() }.await()

/** «2026-11-21T10:05:00Z» → «21.11 10:05» (the server's time, as stored); anything else as it came. */
fun furyShortTime(raw: String): String {
    val match = Regex("""(\d{4})-(\d{2})-(\d{2})[T ](\d{2}):(\d{2})""").find(raw) ?: return raw
    val (_, month, day, hour, minute) = match.destructured
    return "$day.$month $hour:$minute"
}

fun lastSyncText(at: String?, device: String?): String? = at?.takeIf(String::isNotBlank)?.let { time ->
    listOf("Последняя синхронизация: ${furyShortTime(time)}", device?.trim().orEmpty()).filter(String::isNotBlank).joinToString(" · ")
}
