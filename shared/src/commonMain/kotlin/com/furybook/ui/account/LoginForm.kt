package com.furybook.ui.account

enum class LoginTab { SIGN_IN, SIGN_UP, RESET }

data class LoginForm(
    val tab: LoginTab = LoginTab.SIGN_IN,
    val email: String = "",
    val password: String = "",
    val nickname: String = "",
)

private val emailPattern = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

/** The first problem with the form, in the order the player reads the fields, or null when it can be sent. */
fun LoginForm.error(): String? {
    val cleanEmail = email.trim()
    if (cleanEmail.isEmpty()) return "Введите email."
    if (!emailPattern.matches(cleanEmail)) return "Введите корректный email."
    if (tab == LoginTab.RESET) return null
    if (password.isBlank()) return "Введите пароль."
    if (tab == LoginTab.SIGN_UP) {
        if (password.length < 8) return "Пароль должен содержать минимум 8 символов."
        if (nickname.trim().length !in 2..32) return "Никнейм должен содержать от 2 до 32 символов."
    }
    return null
}
