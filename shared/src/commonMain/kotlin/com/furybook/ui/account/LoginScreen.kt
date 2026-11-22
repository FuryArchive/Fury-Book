package com.furybook.ui.account

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.furybook.ui.components.FuryButton
import com.furybook.ui.components.FuryButtonStyle
import com.furybook.ui.components.FuryChipGroup
import com.furybook.ui.components.FuryChoice
import com.furybook.ui.components.FuryTextField
import com.furybook.ui.theme.DublAccent
import com.furybook.ui.theme.DublBackground
import com.furybook.ui.theme.DublBorder
import com.furybook.ui.theme.DublDanger
import com.furybook.ui.theme.DublMuted
import com.furybook.ui.theme.DublSurface
import com.furybook.ui.theme.FuryColors
import com.furybook.ui.theme.FuryShapes
import com.furybook.ui.theme.FurySpacing
import kotlinx.coroutines.launch

/** Tab labels; a phone-width card gets the short ones so nothing is cut off. */
internal fun loginTabs(narrow: Boolean) = listOf(
    FuryChoice(LoginTab.SIGN_IN, "Вход"),
    FuryChoice(LoginTab.SIGN_UP, if (narrow) "Создать" else "Создать аккаунт"),
    FuryChoice(LoginTab.RESET, if (narrow) "Сброс" else "Сброс пароля"),
)

/**
 * The first screen: sign in, create an account, reset a password, or «Играть без входа». A guest who
 * opens it later from the menu gets [onBack] to return to the app.
 */
@Composable
fun FuryLoginScreen(
    account: FuryAccount,
    onGuest: () -> Unit,
    onSignedIn: () -> Unit,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var form by remember { mutableStateOf(LoginForm()) }
    var error by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    fun switchTab(tab: LoginTab) { form = form.copy(tab = tab); error = null; info = null }

    fun submit() {
        if (busy) return
        val problem = form.error()
        if (problem != null) { error = problem; info = null; return }
        val email = form.email.trim()
        busy = true; error = null; info = null
        scope.launch {
            when (form.tab) {
                LoginTab.SIGN_IN -> account.signIn(email, form.password)
                    .onSuccess { onSignedIn() }
                    .onFailure { error = it.message ?: "Не удалось войти." }
                LoginTab.SIGN_UP -> account.signUp(email, form.password, form.nickname.trim())
                    .onSuccess { signedIn ->
                        if (signedIn) onSignedIn() else {
                            form = form.copy(tab = LoginTab.SIGN_IN, password = "")
                            info = "Аккаунт создан. Подтвердите email из письма, затем войдите."
                        }
                    }
                    .onFailure { error = it.message ?: "Не удалось создать аккаунт." }
                LoginTab.RESET -> account.requestPasswordReset(email)
                    .onSuccess { info = "Письмо со ссылкой отправлено на $email." }
                    .onFailure { error = it.message ?: "Не удалось отправить письмо." }
            }
            busy = false
        }
    }

    Box(
        modifier.fillMaxSize().background(DublBackground).verticalScroll(rememberScrollState()).imePadding().padding(FurySpacing.lg),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.widthIn(max = 440.dp).fillMaxWidth()
                .background(DublSurface, FuryShapes.panel).border(1.dp, DublBorder, FuryShapes.panel)
                .padding(FurySpacing.xl),
            verticalArrangement = Arrangement.spacedBy(FurySpacing.lg),
        ) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("FURY BOOK", color = DublAccent, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(
                    "Листы персонажей DUBL 3.69 — на телефоне, компьютере и в браузере",
                    color = DublMuted, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
                )
            }
            BoxWithConstraints {
                FuryChipGroup(loginTabs(narrow = maxWidth < 380.dp), form.tab, ::switchTab, columns = 3)
            }
            Column(verticalArrangement = Arrangement.spacedBy(FurySpacing.sm)) {
                FuryTextField(
                    form.email, { form = form.copy(email = it) }, "Email",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                )
                if (form.tab != LoginTab.RESET) {
                    FuryTextField(
                        form.password, { form = form.copy(password = it) }, "Пароль",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        visualTransformation = PasswordVisualTransformation(),
                    )
                }
                if (form.tab == LoginTab.SIGN_UP) {
                    FuryTextField(form.nickname, { form = form.copy(nickname = it) }, "Никнейм")
                    Hint("На этот адрес придёт письмо для подтверждения аккаунта.")
                }
                if (form.tab == LoginTab.RESET) Hint("Введите email, на который отправить ссылку для сброса пароля.")
                error?.let { Text(it, color = DublDanger, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold) }
                info?.let { Text(it, color = FuryColors.current.goldText, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold) }
                FuryButton(
                    when (form.tab) { LoginTab.SIGN_IN -> "Войти"; LoginTab.SIGN_UP -> "Создать аккаунт"; LoginTab.RESET -> "Отправить ссылку" },
                    ::submit, Modifier.fillMaxWidth().height(48.dp), style = FuryButtonStyle.Primary, enabled = !busy,
                )
                if (form.tab == LoginTab.SIGN_IN) {
                    FuryButton("Забыли пароль?", { switchTab(LoginTab.RESET) }, Modifier.align(Alignment.CenterHorizontally), style = FuryButtonStyle.Ghost)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(FurySpacing.sm)) {
                Box(Modifier.weight(1f).height(1.dp).background(DublBorder))
                Text("или", color = DublMuted, style = MaterialTheme.typography.bodySmall)
                Box(Modifier.weight(1f).height(1.dp).background(DublBorder))
            }
            if (onBack == null) {
                FuryButton("Играть без входа", onGuest, Modifier.fillMaxWidth().height(48.dp))
                Hint("Персонажи сохранятся на этом устройстве. Войти можно позже — они перейдут в аккаунт.", center = true)
            } else {
                FuryButton("Назад", onBack, Modifier.fillMaxWidth().height(48.dp))
                Hint("Персонажи с этого устройства перейдут в аккаунт.", center = true)
            }
        }
    }
}

@Composable
private fun Hint(text: String, center: Boolean = false) {
    Text(
        text, Modifier.fillMaxWidth(), color = DublMuted, style = MaterialTheme.typography.bodySmall,
        textAlign = if (center) TextAlign.Center else TextAlign.Start,
    )
}
