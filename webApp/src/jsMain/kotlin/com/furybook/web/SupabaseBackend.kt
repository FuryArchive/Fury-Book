@file:Suppress("UnsafeCastFromDynamic")

package com.furybook.web

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.furybook.dubl.data.SnapshotCodec
import kotlin.js.Promise
import kotlinx.browser.window
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.await
import kotlinx.coroutines.launch

private const val SUPABASE_URL = "https://xmmekahkeervdvrywmys.supabase.co"
private const val SUPABASE_PUBLISHABLE_KEY = "sb_publishable_L4tEqZbiS_rCCY4juHbllw_4HVbe6UI"
private const val SYNC_DELAY_MS = 650

@JsModule("@supabase/supabase-js")
@JsNonModule
external object SupabaseModule {
    fun createClient(supabaseUrl: String, supabaseKey: String): dynamic
}

data class WebAuthSession(val userId: String, val email: String)

enum class CloudSyncPhase { CONNECTING, PENDING, SYNCING, SYNCED, ERROR, CONFLICT }

data class SyncHistoryItem(
    val characterId: String,
    val characterName: String,
    val revision: Int,
    val action: String,
    val device: String,
    val changedAt: String,
)

data class CloudSyncStatus(
    val phase: CloudSyncPhase = CloudSyncPhase.CONNECTING,
    val lastSuccessfulAt: String? = null,
    val lastSuccessfulDevice: String? = null,
    val message: String? = null,
    val history: List<SyncHistoryItem> = emptyList(),
)

data class CloudConflict(
    val characterId: String,
    val characterName: String,
    val serverRevision: Int,
    val serverUpdatedAt: String?,
    val serverUpdatedBy: String?,
    val serverDataJson: String,
    val serverExtrasJson: String,
    val localDataJson: String,
    val localExtrasJson: String,
    val deleteRequested: Boolean = false,
)

data class CloudBootstrap(
    val snapshot: String,
    val extras: String,
    val chiEnabled: Boolean,
    val nickname: String,
    val hasCharacters: Boolean,
)

private data class ScheduledState(val snapshot: String, val extras: String, val chiEnabled: Boolean)

class WebCloudSync {
    private val client: dynamic = SupabaseModule.createClient(SUPABASE_URL, SUPABASE_PUBLISHABLE_KEY)
    private val scope = MainScope()
    private val deviceLabel = detectDeviceLabel()
    private var userId: String? = null
    private var pendingTimer: Int? = null
    private var lastScheduled: ScheduledState? = null
    private val characterRevisions = linkedMapOf<String, Int>()
    private val serverPayloads = linkedMapOf<String, String>()
    private var stateRevision: Int = 0
    private var lastStateFingerprint: String = ""

    var status: CloudSyncStatus by mutableStateOf(CloudSyncStatus())
        private set

    var conflict: CloudConflict? by mutableStateOf(null)
        private set

    var profileNickname: String by mutableStateOf("")
        private set

    suspend fun restoreSession(): WebAuthSession? {
        val response = (client.auth.getSession() as Promise<dynamic>).await()
        val authError = response.error
        if (authError != null) error(authErrorMessage(authError.message as? String, login = true))
        val session = response.data?.session ?: return null
        val id = session.user?.id as? String ?: return null
        userId = id
        return WebAuthSession(id, (session.user?.email as? String).orEmpty())
    }

    suspend fun signIn(email: String, password: String): Result<WebAuthSession> = runCatching {
        val credentials = js("({})")
        credentials.email = email.trim()
        credentials.password = password
        val response = (client.auth.signInWithPassword(credentials) as Promise<dynamic>).await()
        val authError = response.error
        if (authError != null) error(authErrorMessage(authError.message as? String, login = true))
        val session = response.data?.session ?: error("Не удалось создать сессию. Попробуйте ещё раз.")
        val id = session.user.id as String
        userId = id
        WebAuthSession(id, (session.user.email as? String).orEmpty())
    }

    suspend fun signUp(email: String, password: String, nickname: String): Result<WebAuthSession?> = runCatching {
        val credentials = js("({})")
        credentials.email = email.trim()
        credentials.password = password
        val options = js("({})")
        options.emailRedirectTo = window.location.origin + window.location.pathname
        val metadata = js("({})")
        metadata.nickname = nickname.trim()
        options.data = metadata
        credentials.options = options
        val response = (client.auth.signUp(credentials) as Promise<dynamic>).await()
        val authError = response.error
        if (authError != null) error(authErrorMessage(authError.message as? String, login = false))
        val session = response.data?.session
        if (session == null) {
            null
        } else {
            val id = session.user.id as String
            userId = id
            WebAuthSession(id, (session.user.email as? String).orEmpty())
        }
    }

    suspend fun signOut() {
        runCatching { (client.auth.signOut() as Promise<dynamic>).await() }
        userId = null
        pendingTimer?.let(window::clearTimeout)
        pendingTimer = null
        lastScheduled = null
        characterRevisions.clear()
        serverPayloads.clear()
        stateRevision = 0
        lastStateFingerprint = ""
        conflict = null
        profileNickname = ""
        status = CloudSyncStatus()
    }

    suspend fun pull(): CloudBootstrap {
        status = status.copy(phase = CloudSyncPhase.CONNECTING, message = null)
        val data = rpc("get_web_bootstrap", js("({})"))
        val profile = data.profile
        val state = data.state
        val characters = data.characters
        val historyData = data.history

        characterRevisions.clear()
        serverPayloads.clear()

        val characterArray = js("[]")
        val extrasRoot = js("({})")
        extrasRoot.version = 1
        val extrasCharacters = js("({})")
        extrasRoot.characters = extrasCharacters

        val count = (characters?.length as? Int) ?: 0
        for (index in 0 until count) {
            val item = characters[index]
            val id = item.id as String
            characterArray.push(item.data)
            extrasCharacters[id] = item.extras
            val revision = numberToInt(item.revision)
            characterRevisions[id] = revision
            serverPayloads[id] = payloadFingerprint(item.data, item.extras)
        }

        val snapshotRoot = js("({})")
        snapshotRoot.schema = SnapshotCodec.SCHEMA
        snapshotRoot.characters = characterArray
        val requestedActive = state?.activeCharacterId as? String
        val fresh = if (count == 0) SnapshotCodec.fresh(::webUuid) else null
        val snapshotRaw = if (fresh != null) {
            SnapshotCodec.encode(fresh)
        } else {
            snapshotRoot.activeCharacterId = requestedActive ?: (characters[0].id as String)
            js("JSON.stringify")(snapshotRoot) as String
        }

        val extrasRaw = if (count == 0) "{\"version\":1,\"characters\":{}}" else js("JSON.stringify")(extrasRoot) as String
        val packState = state?.packState
        val chiEnabled = (packState?.chiEnabled as? Boolean) ?: false
        stateRevision = numberToInt(state?.revision)
        val activeForFingerprint = if (fresh != null) fresh.activeCharacterId else (snapshotRoot.activeCharacterId as String)
        lastStateFingerprint = if (stateRevision == 0) "" else stateFingerprint(activeForFingerprint, chiEnabled)

        val history = mutableListOf<SyncHistoryItem>()
        val historyCount = (historyData?.length as? Int) ?: 0
        for (index in 0 until historyCount) {
            val item = historyData[index]
            history += SyncHistoryItem(
                characterId = (item.characterId as? String).orEmpty(),
                characterName = (item.characterName as? String).orEmpty().ifBlank { "Персонаж" },
                revision = numberToInt(item.revision),
                action = (item.action as? String).orEmpty(),
                device = (item.device as? String).orEmpty(),
                changedAt = (item.changedAt as? String).orEmpty(),
            )
        }

        val last = history.firstOrNull()
        profileNickname = (profile?.nickname as? String).orEmpty()
        status = CloudSyncStatus(
            phase = CloudSyncPhase.SYNCED,
            lastSuccessfulAt = last?.changedAt ?: (state?.updatedAt as? String),
            lastSuccessfulDevice = last?.device ?: (state?.updatedBy as? String),
            history = history,
        )
        conflict = null

        return CloudBootstrap(
            snapshot = snapshotRaw,
            extras = extrasRaw,
            chiEnabled = chiEnabled,
            nickname = profileNickname,
            hasCharacters = count > 0,
        )
    }

    fun schedule(snapshot: String, extras: String, chiEnabled: Boolean) {
        if (userId == null || conflict != null) return
        lastScheduled = ScheduledState(snapshot, extras, chiEnabled)
        pendingTimer?.let(window::clearTimeout)
        status = status.copy(phase = CloudSyncPhase.PENDING, message = "Изменения ожидают синхронизации")
        pendingTimer = window.setTimeout({
            pendingTimer = null
            scope.launch { syncScheduled() }
        }, SYNC_DELAY_MS)
    }

    suspend fun retryLast(): Result<Unit> = runCatching {
        val scheduled = lastScheduled ?: error("Нет изменений для повторной синхронизации")
        syncSnapshot(scheduled)
    }

    suspend fun forceConflict(): Result<Unit> = runCatching {
        val current = conflict ?: return@runCatching
        if (current.deleteRequested) {
            val payload = js("({})")
            payload.p_character_id = current.characterId
            payload.p_expected_revision = current.serverRevision
            payload.p_device = deviceLabel
            val result = rpc("delete_web_character", payload)
            if ((result.status as? String) == "conflict") {
                updateConflictFromResult(current, result)
                error("Персонаж снова изменился на другом устройстве.")
            }
            characterRevisions.remove(current.characterId)
            serverPayloads.remove(current.characterId)
            val updatedAt = result.updatedAt as? String
            val updatedBy = result.updatedBy as? String
            conflict = null
            status = status.copy(phase = CloudSyncPhase.SYNCED, lastSuccessfulAt = updatedAt, lastSuccessfulDevice = updatedBy, message = null)
            return@runCatching
        }

        val payload = js("({})")
        payload.p_character_id = current.characterId
        payload.p_data = js("JSON.parse")(current.localDataJson)
        payload.p_extras = js("JSON.parse")(current.localExtrasJson)
        payload.p_expected_revision = current.serverRevision
        payload.p_device = deviceLabel
        payload.p_action = "force"
        val result = rpc("save_web_character", payload)
        if ((result.status as? String) == "conflict") {
            updateConflictFromResult(current, result)
            error("Персонаж снова изменился на другом устройстве.")
        }
        val revision = numberToInt(result.revision)
        characterRevisions[current.characterId] = revision
        val data = js("JSON.parse")(current.localDataJson)
        val extras = js("JSON.parse")(current.localExtrasJson)
        serverPayloads[current.characterId] = payloadFingerprint(data, extras)
        val updatedAt = result.updatedAt as? String
        val updatedBy = result.updatedBy as? String
        prependHistory(current.characterId, current.characterName, revision, "force", updatedBy ?: deviceLabel, updatedAt.orEmpty())
        conflict = null
        status = status.copy(phase = CloudSyncPhase.SYNCED, lastSuccessfulAt = updatedAt, lastSuccessfulDevice = updatedBy, message = null)
    }

    fun clearConflictForReload() {
        conflict = null
    }

    suspend fun updateNickname(nickname: String): Result<String> = runCatching {
        val payload = js("({})")
        payload.p_nickname = nickname.trim()
        val result = rpc("set_profile_nickname", payload)
        val saved = (result.nickname as? String).orEmpty().ifBlank { nickname.trim() }
        profileNickname = saved
        saved
    }

    private suspend fun syncScheduled() {
        val scheduled = lastScheduled ?: return
        runCatching { syncSnapshot(scheduled) }
            .onFailure { error ->
                if (conflict == null) {
                    status = status.copy(
                        phase = CloudSyncPhase.ERROR,
                        message = error.message ?: "Не удалось синхронизироваться с Fury Cloud",
                    )
                }
            }
    }

    private suspend fun syncSnapshot(scheduled: ScheduledState) {
        if (conflict != null) return
        status = status.copy(phase = CloudSyncPhase.SYNCING, message = "Синхронизация…")

        val snapshot = js("JSON.parse")(scheduled.snapshot)
        val extrasRoot = js("JSON.parse")(scheduled.extras)
        val extrasCharacters = extrasRoot.characters
        val localIds = linkedSetOf<String>()
        val characters = snapshot.characters
        val count = characters.length as Int

        for (index in 0 until count) {
            val data = characters[index]
            val id = data.id as String
            localIds += id
            val extras = if (extrasCharacters == null || extrasCharacters[id] == null) js("({})") else extrasCharacters[id]
            val fingerprint = payloadFingerprint(data, extras)
            if (serverPayloads[id] == fingerprint) continue

            val payload = js("({})")
            payload.p_character_id = id
            payload.p_data = data
            payload.p_extras = extras
            payload.p_expected_revision = characterRevisions[id] ?: 0
            payload.p_device = deviceLabel
            payload.p_action = "save"
            val result = rpc("save_web_character", payload)
            if ((result.status as? String) == "conflict") {
                conflict = CloudConflict(
                    characterId = id,
                    characterName = (data.name as? String).orEmpty().ifBlank { "Персонаж" },
                    serverRevision = numberToInt(result.serverRevision),
                    serverUpdatedAt = result.updatedAt as? String,
                    serverUpdatedBy = result.updatedBy as? String,
                    serverDataJson = if (result.serverData == null) "{}" else js("JSON.stringify")(result.serverData) as String,
                    serverExtrasJson = if (result.serverExtras == null) "{}" else js("JSON.stringify")(result.serverExtras) as String,
                    localDataJson = js("JSON.stringify")(data) as String,
                    localExtrasJson = js("JSON.stringify")(extras) as String,
                )
                status = status.copy(phase = CloudSyncPhase.CONFLICT, message = "Конфликт изменений")
                return
            }

            val revision = numberToInt(result.revision)
            characterRevisions[id] = revision
            serverPayloads[id] = fingerprint
            val updatedAt = result.updatedAt as? String
            val updatedBy = result.updatedBy as? String
            prependHistory(id, (data.name as? String).orEmpty().ifBlank { "Персонаж" }, revision, "save", updatedBy ?: deviceLabel, updatedAt.orEmpty())
            status = status.copy(lastSuccessfulAt = updatedAt, lastSuccessfulDevice = updatedBy)
        }

        val deletedIds = serverPayloads.keys.filter { it !in localIds }
        for (id in deletedIds) {
            val payload = js("({})")
            payload.p_character_id = id
            payload.p_expected_revision = characterRevisions[id] ?: 0
            payload.p_device = deviceLabel
            val result = rpc("delete_web_character", payload)
            if ((result.status as? String) == "conflict") {
                conflict = CloudConflict(
                    characterId = id,
                    characterName = "Удалённый персонаж",
                    serverRevision = numberToInt(result.serverRevision),
                    serverUpdatedAt = result.updatedAt as? String,
                    serverUpdatedBy = result.updatedBy as? String,
                    serverDataJson = js("JSON.stringify")(result.serverData) as String,
                    serverExtrasJson = js("JSON.stringify")(result.serverExtras) as String,
                    localDataJson = "{}",
                    localExtrasJson = "{}",
                    deleteRequested = true,
                )
                status = status.copy(phase = CloudSyncPhase.CONFLICT, message = "Конфликт удаления")
                return
            }
            characterRevisions.remove(id)
            serverPayloads.remove(id)
        }

        val activeCharacterId = snapshot.activeCharacterId as String
        val currentStateFingerprint = stateFingerprint(activeCharacterId, scheduled.chiEnabled)
        if (currentStateFingerprint != lastStateFingerprint) {
            val statePayload = js("({})")
            statePayload.p_active_character_id = activeCharacterId
            val pack = js("({})")
            pack.chiEnabled = scheduled.chiEnabled
            statePayload.p_pack_state = pack
            statePayload.p_expected_revision = stateRevision
            statePayload.p_device = deviceLabel
            var stateResult = rpc("save_web_state", statePayload)
            if ((stateResult.status as? String) == "conflict") {
                statePayload.p_expected_revision = numberToInt(stateResult.serverRevision)
                stateResult = rpc("save_web_state", statePayload)
            }
            if ((stateResult.status as? String) != "ok") error("Не удалось синхронизировать настройки Fury Book")
            stateRevision = numberToInt(stateResult.revision)
            lastStateFingerprint = currentStateFingerprint
            status = status.copy(lastSuccessfulAt = stateResult.updatedAt as? String, lastSuccessfulDevice = stateResult.updatedBy as? String)
        }

        status = status.copy(phase = CloudSyncPhase.SYNCED, message = null)
    }

    private fun updateConflictFromResult(previous: CloudConflict, result: dynamic) {
        conflict = previous.copy(
            serverRevision = numberToInt(result.serverRevision),
            serverUpdatedAt = result.updatedAt as? String,
            serverUpdatedBy = result.updatedBy as? String,
            serverDataJson = if (result.serverData == null) previous.serverDataJson else js("JSON.stringify")(result.serverData) as String,
            serverExtrasJson = if (result.serverExtras == null) previous.serverExtrasJson else js("JSON.stringify")(result.serverExtras) as String,
        )
        status = status.copy(phase = CloudSyncPhase.CONFLICT, message = "Конфликт изменений")
    }

    private fun prependHistory(characterId: String, name: String, revision: Int, action: String, device: String, changedAt: String) {
        val item = SyncHistoryItem(characterId, name, revision, action, device, changedAt)
        status = status.copy(history = (listOf(item) + status.history).take(12))
    }

    private suspend fun currentAccessToken(): String {
        val response = (client.auth.getSession() as Promise<dynamic>).await()
        val authError = response.error
        if (authError != null) error(authErrorMessage(authError.message as? String, login = true))
        return response.data?.session?.access_token as? String ?: error("Сессия истекла. Войдите снова.")
    }

    private suspend fun rpc(name: String, payload: dynamic): dynamic {
        val token = currentAccessToken()
        val url = "$SUPABASE_URL/rest/v1/rpc/$name"
        val init = js("({})")
        init.method = "POST"
        init.headers = js("({})")
        init.headers.apikey = SUPABASE_PUBLISHABLE_KEY
        init.headers.Authorization = "Bearer $token"
        init.headers["Content-Type"] = "application/json"
        init.headers.Accept = "application/json"
        init.body = js("JSON.stringify")(payload)
        val response = (window.asDynamic().fetch(url, init) as Promise<dynamic>).await()
        val body = (response.text() as Promise<String>).await()
        if (!(response.ok as Boolean)) {
            val message = runCatching {
                val parsed = js("JSON.parse")(body)
                (parsed.message as? String) ?: (parsed.error_description as? String) ?: body
            }.getOrDefault(body)
            error(message.ifBlank { "Fury Cloud недоступен" })
        }
        return if (body.isBlank()) js("({})") else js("JSON.parse")(body)
    }

    private fun payloadFingerprint(data: dynamic, extras: dynamic): String =
        (js("JSON.stringify")(data) as String) + "\u0000" + (js("JSON.stringify")(extras) as String)

    private fun stateFingerprint(activeCharacterId: String, chiEnabled: Boolean): String = "$activeCharacterId|$chiEnabled"

    private fun numberToInt(value: dynamic): Int = when (value) {
        null -> 0
        is Number -> value.toInt()
        is String -> value.toIntOrNull() ?: 0
        else -> 0
    }
}

private fun authErrorMessage(raw: String?, login: Boolean): String {
    val message = raw.orEmpty()
    val lower = message.lowercase()
    return when {
        "invalid login credentials" in lower -> "Неверный email или пароль."
        "email not confirmed" in lower -> "Email ещё не подтверждён. Откройте письмо от Fury Book и подтвердите аккаунт."
        "user already registered" in lower || "already been registered" in lower -> "Аккаунт с таким email уже существует."
        "password" in lower && "least" in lower -> "Пароль слишком короткий. Используйте минимум 8 символов."
        "rate limit" in lower -> "Слишком много попыток. Подождите немного и попробуйте снова."
        message.isNotBlank() && !login -> "Не удалось создать аккаунт: $message"
        message.isNotBlank() -> "Не удалось войти. Проверьте email и пароль."
        login -> "Не удалось войти. Проверьте email и пароль."
        else -> "Не удалось создать аккаунт."
    }
}

private fun detectDeviceLabel(): String {
    val ua = window.navigator.userAgent
    val browser = when {
        "Firefox" in ua -> "Firefox"
        "Edg/" in ua -> "Edge"
        "Chrome" in ua || "Chromium" in ua -> "Chromium"
        "Safari" in ua -> "Safari"
        else -> "Browser"
    }
    val os = when {
        "Android" in ua -> "Android"
        "Windows" in ua -> "Windows"
        "Linux" in ua -> "Linux"
        "Mac OS" in ua -> "macOS"
        else -> "Web"
    }
    return "Web · $browser · $os"
}
