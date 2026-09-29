package com.furybook.cloud

import com.furybook.core.json.jsonObject
import com.furybook.core.json.jsonString
import com.furybook.core.json.jsonStringify
import com.furybook.core.json.objectValue
import com.furybook.core.json.parseRoot
import com.furybook.core.json.string

object FuryAuthProtocol {
    fun passwordBody(email: String, password: String): String = jsonStringify(
        jsonObject(
            "email" to jsonString(email.trim()),
            "password" to jsonString(password),
        ),
    )

    fun refreshBody(refreshToken: String): String = jsonStringify(
        jsonObject("refresh_token" to jsonString(refreshToken)),
    )

    fun parseSession(raw: String): FuryAuthSession {
        val root = parseRoot(raw)
        val user = root.objectValue("user") ?: error("Fury Cloud auth response has no user")
        val access = root.string("access_token").ifBlank { error("Fury Cloud auth response has no access token") }
        val refresh = root.string("refresh_token").ifBlank { error("Fury Cloud auth response has no refresh token") }
        return FuryAuthSession(
            userId = user.string("id").ifBlank { error("Fury Cloud auth response has no user id") },
            email = user.string("email"),
            accessToken = access,
            refreshToken = refresh,
        )
    }

    fun parseError(raw: String): String {
        val root = runCatching { parseRoot(raw) }.getOrNull() ?: return raw
        return root.string("msg")
            .ifBlank { root.string("message") }
            .ifBlank { root.string("error_description") }
            .ifBlank { raw }
    }

    fun friendlyError(raw: String): String {
        val lower = raw.lowercase()
        return when {
            "invalid login credentials" in lower -> "Неверный email или пароль."
            "email not confirmed" in lower -> "Email ещё не подтверждён."
            "rate limit" in lower -> "Слишком много попыток. Попробуйте позже."
            else -> raw.ifBlank { "Не удалось подключиться к Fury Cloud." }
        }
    }
}
