package com.furybook.desktop.cloud

import com.furybook.core.cloud.FuryAuthProtocol
import com.furybook.core.cloud.FuryAuthSession
import com.furybook.core.cloud.FuryAuthSessionStore
import com.furybook.core.cloud.FuryCloudAuth
import com.furybook.core.cloud.FuryCloudTransport
import com.furybook.core.cloud.NativeCloudMetadata
import com.furybook.core.cloud.NativeCloudMetadataStore
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.prefs.Preferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val SUPABASE_URL = "https://xmmekahkeervdvrywmys.supabase.co"
private const val SUPABASE_PUBLISHABLE_KEY = "sb_publishable_L4tEqZbiS_rCCY4juHbllw_4HVbe6UI"

class DesktopFuryCloudGateway : FuryCloudAuth, FuryCloudTransport {
    private val sessionStore = DesktopAuthSessionStore()
    private val client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(12))
        .build()

    fun currentSession(): FuryAuthSession? = sessionStore.load()

    override suspend fun restore(): FuryAuthSession? = sessionStore.load()

    override suspend fun signIn(email: String, password: String): Result<FuryAuthSession> = runCatching {
        withContext(Dispatchers.IO) {
            val response = request(
                path = "/auth/v1/token?grant_type=password",
                body = FuryAuthProtocol.passwordBody(email, password),
                bearer = null,
            )
            if (response.statusCode() !in 200..299) {
                error(FuryAuthProtocol.friendlyError(FuryAuthProtocol.parseError(response.body())))
            }
            FuryAuthProtocol.parseSession(response.body()).also(sessionStore::save)
        }
    }

    override suspend fun signUp(email: String, password: String, nickname: String): Result<FuryAuthSession?> = runCatching {
        withContext(Dispatchers.IO) {
            val response = request(
                path = "/auth/v1/signup?redirect_to=https%3A%2F%2Ffuryarchive.github.io%2FFury-Book%2Fapp%2F",
                body = FuryAuthProtocol.signUpBody(email, password, nickname),
                bearer = null,
            )
            if (response.statusCode() !in 200..299) {
                error(FuryAuthProtocol.friendlyError(FuryAuthProtocol.parseError(response.body())))
            }
            val session = runCatching { FuryAuthProtocol.parseSession(response.body()) }.getOrNull()
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
        if (response.statusCode() == 401) {
            session = refresh(session.refreshToken)
            response = request("/rest/v1/rpc/$function", jsonBody, session.accessToken)
        }
        if (response.statusCode() !in 200..299) {
            val parsed = FuryAuthProtocol.parseError(response.body())
            error(parsed.ifBlank { "Fury Cloud: HTTP ${response.statusCode()}" })
        }
        response.body()
    }

    private fun refresh(refreshToken: String): FuryAuthSession {
        val response = request(
            path = "/auth/v1/token?grant_type=refresh_token",
            body = FuryAuthProtocol.refreshBody(refreshToken),
            bearer = null,
        )
        if (response.statusCode() !in 200..299) {
            sessionStore.clear()
            error("Сессия Fury Account истекла. Войдите снова.")
        }
        return FuryAuthProtocol.parseSession(response.body()).also(sessionStore::save)
    }

    private fun request(path: String, body: String, bearer: String?): HttpResponse<String> {
        val builder = HttpRequest.newBuilder(URI.create(SUPABASE_URL + path))
            .timeout(Duration.ofSeconds(20))
            .header("apikey", SUPABASE_PUBLISHABLE_KEY)
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
        if (!bearer.isNullOrBlank()) builder.header("Authorization", "Bearer $bearer")
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString())
    }
}

class DesktopAuthSessionStore : FuryAuthSessionStore {
    private val prefs = Preferences.userRoot().node("com/furybook/cloud-auth")

    override fun load(): FuryAuthSession? {
        val userId = prefs.get("user_id", "").takeIf { it.isNotBlank() } ?: return null
        val access = prefs.get("access_token", "").takeIf { it.isNotBlank() } ?: return null
        val refresh = prefs.get("refresh_token", "").takeIf { it.isNotBlank() } ?: return null
        return FuryAuthSession(
            userId = userId,
            email = prefs.get("email", ""),
            accessToken = access,
            refreshToken = refresh,
        )
    }

    override fun save(session: FuryAuthSession) {
        prefs.put("user_id", session.userId)
        prefs.put("email", session.email)
        prefs.put("access_token", session.accessToken)
        prefs.put("refresh_token", session.refreshToken)
        prefs.flush()
    }

    override fun clear() {
        prefs.clear()
        prefs.flush()
    }
}

class DesktopCloudMetadataStore : NativeCloudMetadataStore {
    private val prefs = Preferences.userRoot().node("com/furybook/cloud-sync")

    override fun load(): NativeCloudMetadata {
        val revisions = prefs.get("known_revisions", "")
            .lineSequence()
            .mapNotNull { line ->
                val split = line.lastIndexOf('=')
                if (split <= 0) return@mapNotNull null
                val id = line.substring(0, split)
                val revision = line.substring(split + 1).toIntOrNull() ?: return@mapNotNull null
                id to revision
            }
            .toMap()
        return NativeCloudMetadata(
            linkedUserId = prefs.get("linked_user_id", ""),
            knownRevisions = revisions,
            stateRevision = prefs.getInt("state_revision", 0),
            dirtyIds = decodeSet(prefs.get("dirty_ids", "")),
            deletedIds = decodeSet(prefs.get("deleted_ids", "")),
        )
    }

    override fun save(metadata: NativeCloudMetadata) {
        prefs.put("linked_user_id", metadata.linkedUserId)
        prefs.putInt("state_revision", metadata.stateRevision)
        prefs.put("known_revisions", metadata.knownRevisions.entries.joinToString("\\n") { "${it.key}=${it.value}" })
        prefs.put("dirty_ids", encodeSet(metadata.dirtyIds))
        prefs.put("deleted_ids", encodeSet(metadata.deletedIds))
        prefs.flush()
    }

    override fun clear() {
        prefs.clear()
        prefs.flush()
    }

    private fun encodeSet(values: Set<String>): String = values.joinToString("\\n")
    private fun decodeSet(raw: String): Set<String> = raw.lineSequence().filter(String::isNotBlank).toSet()
}

fun desktopCloudDeviceLabel(): String {
    val os = System.getProperty("os.name", "Desktop")
    val host = runCatching { java.net.InetAddress.getLocalHost().hostName }.getOrDefault("device")
    return "Desktop · $os · $host"
}
