package com.furybook.core.cloud

data class FuryAuthSession(
    val userId: String,
    val email: String,
    val accessToken: String,
    val refreshToken: String,
)

interface FuryAuthSessionStore {
    fun load(): FuryAuthSession?
    fun save(session: FuryAuthSession)
    fun clear()
}

interface FuryCloudAuth {
    suspend fun signIn(email: String, password: String): Result<FuryAuthSession>
    suspend fun signUp(email: String, password: String, nickname: String): Result<FuryAuthSession?>
    /** Sends a password-reset email. The reset itself is completed on the Fury Book web page. */
    suspend fun requestPasswordReset(email: String): Result<Unit>
    suspend fun restore(): FuryAuthSession?
    suspend fun signOut()
}

enum class NativeCloudStatus {
    SIGNED_OUT,
    CONNECTING,
    SYNCING,
    SYNCED,
    OFFLINE,
    CONFLICT,
    ERROR,
}
