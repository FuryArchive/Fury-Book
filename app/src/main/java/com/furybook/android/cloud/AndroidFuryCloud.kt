package com.furybook.android.cloud

import android.content.Context
import android.os.Build
import com.furybook.cloud.FuryAuthProtocol
import com.furybook.cloud.FuryAuthSession
import com.furybook.cloud.FuryAuthSessionStore
import com.furybook.cloud.FuryCloudAuth
import com.furybook.cloud.FuryCloudTransport
import com.furybook.cloud.NativeCloudMetadata
import com.furybook.cloud.NativeCloudMetadataStore
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

private const val SUPABASE_URL = "https://xmmekahkeervdvrywmys.supabase.co"
private const val SUPABASE_PUBLISHABLE_KEY = "sb_publishable_L4tEqZbiS_rCCY4juHbllw_4HVbe6UI"

class AndroidFuryCloudGateway(context: Context) : FuryCloudAuth, FuryCloudTransport {
    private val sessionStore = AndroidAuthSessionStore(context)

    fun currentSession(): FuryAuthSession? = sessionStore.load()

    override suspend fun restore(): FuryAuthSession? = sessionStore.load()

    override suspend fun signIn(email: String, password: String): Result<FuryAuthSession> = runCatching {
        withContext(Dispatchers.IO) {
            val response = request(
                path = "/auth/v1/token?grant_type=password",
                body = FuryAuthProtocol.passwordBody(email, password),
                bearer = null,
            )
            if (response.code !in 200..299) {
                error(FuryAuthProtocol.friendlyError(FuryAuthProtocol.parseError(response.body)))
            }
            FuryAuthProtocol.parseSession(response.body).also(sessionStore::save)
        }
    }

    override suspend fun signUp(email: String, password: String, nickname: String): Result<FuryAuthSession?> = runCatching {
        withContext(Dispatchers.IO) {
            val response = request(
                path = "/auth/v1/signup?redirect_to=https%3A%2F%2Ffuryarchive.github.io%2FFury-Book%2Fapp%2F",
                body = FuryAuthProtocol.signUpBody(email, password, nickname),
                bearer = null,
            )
            if (response.code !in 200..299) {
                error(FuryAuthProtocol.friendlyError(FuryAuthProtocol.parseError(response.body)))
            }
            val session = runCatching { FuryAuthProtocol.parseSession(response.body) }.getOrNull()
            session?.also(sessionStore::save)
        }
    }

    override suspend fun signOut() {
        val session = sessionStore.load()
        if (session != null) {
            runCatching {
                withContext(Dispatchers.IO) {
                    request("/auth/v1/logout", "{}", session.accessToken)
                }
            }
        }
        sessionStore.clear()
    }

    override suspend fun rpc(function: String, jsonBody: String): String = withContext(Dispatchers.IO) {
        var session = sessionStore.load() ?: error("Войдите в Fury Account.")
        var response = request("/rest/v1/rpc/$function", jsonBody, session.accessToken)
        if (response.code == 401) {
            session = refresh(session.refreshToken)
            response = request("/rest/v1/rpc/$function", jsonBody, session.accessToken)
        }
        if (response.code !in 200..299) {
            val parsed = FuryAuthProtocol.parseError(response.body)
            error(parsed.ifBlank { "Fury Cloud: HTTP ${response.code}" })
        }
        response.body
    }

    private fun refresh(refreshToken: String): FuryAuthSession {
        val response = request(
            path = "/auth/v1/token?grant_type=refresh_token",
            body = FuryAuthProtocol.refreshBody(refreshToken),
            bearer = null,
        )
        if (response.code !in 200..299) {
            sessionStore.clear()
            error("Сессия Fury Account истекла. Войдите снова.")
        }
        return FuryAuthProtocol.parseSession(response.body).also(sessionStore::save)
    }

    private fun request(path: String, body: String, bearer: String?): HttpResult {
        val connection = (URL(SUPABASE_URL + path).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 12_000
            readTimeout = 20_000
            doOutput = true
            setRequestProperty("apikey", SUPABASE_PUBLISHABLE_KEY)
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
            if (!bearer.isNullOrBlank()) setRequestProperty("Authorization", "Bearer $bearer")
        }
        connection.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(body) }
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val responseBody = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
        connection.disconnect()
        return HttpResult(code, responseBody)
    }

    private data class HttpResult(val code: Int, val body: String)
}

class AndroidAuthSessionStore(context: Context) : FuryAuthSessionStore {
    private val prefs = context.applicationContext.getSharedPreferences("fury-cloud-auth", Context.MODE_PRIVATE)

    override fun load(): FuryAuthSession? {
        val userId = prefs.getString("user_id", null)?.takeIf { it.isNotBlank() } ?: return null
        val access = prefs.getString("access_token", null)?.takeIf { it.isNotBlank() } ?: return null
        val refresh = prefs.getString("refresh_token", null)?.takeIf { it.isNotBlank() } ?: return null
        return FuryAuthSession(
            userId = userId,
            email = prefs.getString("email", "").orEmpty(),
            accessToken = access,
            refreshToken = refresh,
        )
    }

    override fun save(session: FuryAuthSession) {
        prefs.edit()
            .putString("user_id", session.userId)
            .putString("email", session.email)
            .putString("access_token", session.accessToken)
            .putString("refresh_token", session.refreshToken)
            .apply()
    }

    override fun clear() {
        prefs.edit().clear().apply()
    }
}

class AndroidCloudMetadataStore(context: Context) : NativeCloudMetadataStore {
    private val prefs = context.applicationContext.getSharedPreferences("fury-cloud-sync", Context.MODE_PRIVATE)

    override fun load(): NativeCloudMetadata {
        val raw = prefs.getString("metadata", null) ?: return NativeCloudMetadata()
        return runCatching {
            val root = JSONObject(raw)
            val revisionsJson = root.optJSONObject("knownRevisions") ?: JSONObject()
            val revisions = linkedMapOf<String, Int>()
            revisionsJson.keys().forEach { id -> revisions[id] = revisionsJson.optInt(id, 0) }
            NativeCloudMetadata(
                linkedUserId = root.optString("linkedUserId", ""),
                knownRevisions = revisions,
                stateRevision = root.optInt("stateRevision", 0),
                dirtyIds = root.optJSONArray("dirtyIds").toStringSet(),
                deletedIds = root.optJSONArray("deletedIds").toStringSet(),
            )
        }.getOrDefault(NativeCloudMetadata())
    }

    override fun save(metadata: NativeCloudMetadata) {
        val revisions = JSONObject()
        metadata.knownRevisions.forEach { (id, revision) -> revisions.put(id, revision) }
        val root = JSONObject()
            .put("linkedUserId", metadata.linkedUserId)
            .put("knownRevisions", revisions)
            .put("stateRevision", metadata.stateRevision)
            .put("dirtyIds", JSONArray(metadata.dirtyIds.toList()))
            .put("deletedIds", JSONArray(metadata.deletedIds.toList()))
        prefs.edit().putString("metadata", root.toString()).apply()
    }

    override fun clear() {
        prefs.edit().clear().apply()
    }

    private fun JSONArray?.toStringSet(): Set<String> {
        if (this == null) return emptySet()
        return buildSet {
            for (index in 0 until length()) {
                optString(index).takeIf { it.isNotBlank() }?.let(::add)
            }
        }
    }
}

fun androidCloudDeviceLabel(): String =
    "Android · ${Build.MANUFACTURER} ${Build.MODEL}".replace(Regex("\\s+"), " ").trim()
