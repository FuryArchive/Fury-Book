@file:Suppress("UnsafeCastFromDynamic")

package com.furybook.web

import kotlin.js.Promise
import kotlinx.browser.window
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.await
import kotlinx.coroutines.launch

private const val SUPABASE_URL = "https://xmmekahkeervdvrywmys.supabase.co"
private const val SUPABASE_PUBLISHABLE_KEY = "sb_publishable_L4tEqZbiS_rCCY4juHbllw_4HVbe6UI"

@JsModule("@supabase/supabase-js")
@JsNonModule
external object SupabaseModule {
    fun createClient(supabaseUrl: String, supabaseKey: String): dynamic
}

data class WebAuthSession(val userId: String, val email: String)
data class CloudBootstrap(val snapshot: String, val extras: String, val chiEnabled: Boolean)

class WebCloudSync {
    private val client: dynamic = SupabaseModule.createClient(SUPABASE_URL, SUPABASE_PUBLISHABLE_KEY)
    private val scope = MainScope()
    private var userId: String? = null
    private var pendingTimer: Int? = null

    suspend fun restoreSession(): WebAuthSession? {
        val response = (client.auth.getSession() as Promise<dynamic>).await()
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
        response.error?.let { authError -> kotlin.error(authError.message as? String ?: "Не удалось войти") }
        val session = response.data?.session ?: error("Сессия не создана")
        val id = session.user.id as String
        userId = id
        WebAuthSession(id, (session.user.email as? String).orEmpty())
    }

    suspend fun signUp(email: String, password: String): Result<WebAuthSession?> = runCatching {
        val credentials = js("({})")
        credentials.email = email.trim()
        credentials.password = password
        val options = js("({})")
        options.emailRedirectTo = window.location.origin + window.location.pathname
        credentials.options = options
        val response = (client.auth.signUp(credentials) as Promise<dynamic>).await()
        response.error?.let { authError -> kotlin.error(authError.message as? String ?: "Не удалось зарегистрироваться") }
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
    }

    private suspend fun currentAccessToken(): String {
        val response = (client.auth.getSession() as Promise<dynamic>).await()
        response.error?.let { authError -> kotlin.error(authError.message as? String ?: "Auth session error") }
        return response.data?.session?.access_token as? String ?: error("Auth session is missing")
    }

    suspend fun pull(): CloudBootstrap? {
        val id = userId ?: return null
        val token = currentAccessToken()
        val encodedId = js("encodeURIComponent")(id) as String
        val url = "$SUPABASE_URL/rest/v1/user_state?user_id=eq.$encodedId&select=snapshot,extras,pack_state,revision"
        val init = js("({})")
        init.method = "GET"
        init.headers = js("({})")
        init.headers.apikey = SUPABASE_PUBLISHABLE_KEY
        init.headers.Authorization = "Bearer $token"
        init.headers.Accept = "application/json"
        val response = (window.asDynamic().fetch(url, init) as Promise<dynamic>).await()
        val body = (response.text() as Promise<String>).await()
        if (!(response.ok as Boolean)) {
            error("Cloud sync failed (${response.status}): $body")
        }
        val rows = js("JSON.parse")(body)
        if ((rows.length as Int) == 0) return null
        val data = rows[0]
        val pack = data.pack_state
        return CloudBootstrap(
            snapshot = data.snapshot as String,
            extras = (data.extras as? String).orEmpty(),
            chiEnabled = (pack?.chiEnabled as? Boolean) ?: false,
        )
    }

    fun schedule(snapshot: String, extras: String, chiEnabled: Boolean) {
        if (userId == null) return
        pendingTimer?.let(window::clearTimeout)
        pendingTimer = window.setTimeout({
            pendingTimer = null
            scope.launch { runCatching { push(snapshot, extras, chiEnabled) } }
        }, 800)
    }

    suspend fun push(snapshot: String, extras: String, chiEnabled: Boolean) {
        val id = userId ?: return
        val token = currentAccessToken()
        val pack = js("({})")
        pack.chiEnabled = chiEnabled
        val row = js("({})")
        row.user_id = id
        row.snapshot = snapshot
        row.extras = extras
        row.pack_state = pack
        row.updated_at = js("new Date().toISOString()")

        val url = "$SUPABASE_URL/rest/v1/user_state?on_conflict=user_id"
        val init = js("({})")
        init.method = "POST"
        init.headers = js("({})")
        init.headers.apikey = SUPABASE_PUBLISHABLE_KEY
        init.headers.Authorization = "Bearer $token"
        init.headers["Content-Type"] = "application/json"
        init.headers.Prefer = "resolution=merge-duplicates,return=minimal"
        init.body = js("JSON.stringify")(row)
        val response = (window.asDynamic().fetch(url, init) as Promise<dynamic>).await()
        if (!(response.ok as Boolean)) {
            val body = (response.text() as Promise<String>).await()
            error("Cloud sync failed (${response.status}): $body")
        }
    }
}
