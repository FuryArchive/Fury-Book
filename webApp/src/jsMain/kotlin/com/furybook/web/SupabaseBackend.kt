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

    suspend fun pull(): CloudBootstrap? {
        val id = userId ?: return null
        val query = client.from("user_state")
            .select("snapshot,extras,pack_state,revision")
            .eq("user_id", id)
            .maybeSingle()
        val response = (query as Promise<dynamic>).await()
        response.error?.let { syncError -> kotlin.error(syncError.message as? String ?: "Cloud sync error") }
        val data = response.data ?: return null
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
            scope.launch { push(snapshot, extras, chiEnabled) }
        }, 800)
    }

    suspend fun push(snapshot: String, extras: String, chiEnabled: Boolean) {
        val id = userId ?: return
        val pack = js("({})")
        pack.chiEnabled = chiEnabled
        val row = js("({})")
        row.user_id = id
        row.snapshot = snapshot
        row.extras = extras
        row.pack_state = pack
        row.updated_at = js("new Date().toISOString()")
        val response = (client.from("user_state").upsert(row) as Promise<dynamic>).await()
        response.error?.let { syncError -> kotlin.error(syncError.message as? String ?: "Cloud sync error") }
    }
}
