package com.furybook.ui.account

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LoginFormTest {
    private val ok = LoginForm(LoginTab.SIGN_UP, "vika@fury.app", "длинныйпароль", "Вика")

    @Test
    fun loginErrorsInOrder() {
        assertEquals("Введите email.", ok.copy(email = "  ", password = "").error())
        assertEquals("Введите корректный email.", ok.copy(email = "vika@fury", password = "").error())
        assertEquals("Введите пароль.", ok.copy(password = " ", nickname = "").error())
        assertEquals("Пароль должен содержать минимум 8 символов.", ok.copy(password = "1234567", nickname = "").error())
        assertEquals("Никнейм должен содержать от 2 до 32 символов.", ok.copy(nickname = " В ").error())
        assertEquals("Никнейм должен содержать от 2 до 32 символов.", ok.copy(nickname = "в".repeat(33)).error())
        assertNull(ok.error())
    }

    @Test
    fun signInSkipsSignUpRules() {
        val signIn = ok.copy(tab = LoginTab.SIGN_IN, nickname = "")
        assertNull(signIn.error())
        // Older accounts may have shorter passwords; only new accounts get the 8-character rule.
        assertNull(signIn.copy(password = "123456").error())
        assertEquals("Введите пароль.", signIn.copy(password = "").error())
    }

    @Test
    fun resetIgnoresPassword() {
        val reset = LoginForm(LoginTab.RESET, " vika@fury.app ", "", "")
        assertNull(reset.error())
        assertEquals("Введите email.", reset.copy(email = "").error())
    }
}
