package com.furybook.cloud

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
