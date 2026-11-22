package com.furybook.ui.account

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.furybook.ui.components.DublCard
import com.furybook.ui.components.FuryButton
import com.furybook.ui.components.FuryButtonStyle
import com.furybook.ui.components.FuryDialogAction
import com.furybook.ui.components.FuryDialogScaffold
import com.furybook.ui.components.FuryTextField
import com.furybook.ui.theme.DublDanger
import com.furybook.ui.theme.DublGold
import com.furybook.ui.theme.DublMuted
import com.furybook.ui.theme.DublText
import com.furybook.ui.theme.FuryColors
import com.furybook.ui.theme.FurySpacing
import com.furybook.ui.theme.furyOverlineStyle
import kotlinx.coroutines.launch

enum class AccountDot { GREY, GREEN, GOLD, RED }

/** The dot beside the account name: gold only when a conflict waits for the player. */
fun accountDotColor(status: FuryAccountStatus): AccountDot = when (status) {
    FuryAccountStatus.SYNCED -> AccountDot.GREEN
    FuryAccountStatus.CONFLICT -> AccountDot.GOLD
    FuryAccountStatus.ERROR -> AccountDot.RED
    else -> AccountDot.GREY
}

/** With changes not yet sent, «Выйти» offers to send them first; otherwise it only confirms. */
fun signOutNeedsChoice(pending: Int): Boolean = pending > 0

private val SyncedGreen = Color(0xFF58B368)

@Composable
fun AccountDotView(dot: AccountDot, modifier: Modifier = Modifier) {
    val color = when (dot) {
        AccountDot.GREY -> DublMuted
        AccountDot.GREEN -> SyncedGreen
        AccountDot.GOLD -> DublGold
        AccountDot.RED -> DublDanger
    }
    androidx.compose.foundation.layout.Box(modifier.size(8.dp).clip(CircleShape).background(color))
}

/**
 * The menu's Fury Account card. A guest gets one way in; a signed-in player gets the nickname, sync state,
 * conflicts, the sync log and «Выйти». [onSignedOut] runs once the device has been cleared.
 */
@Composable
fun FuryAccountPanel(
    account: FuryAccount,
    isGuest: Boolean,
    onSignIn: () -> Unit,
    onSignedOut: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    DublCard(modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(FurySpacing.sm)) {
            Text("FURY ACCOUNT", color = DublMuted, style = furyOverlineStyle())
            if (isGuest || !account.signedIn) {
                Text(
                    "Вы играете без входа. Войдите, чтобы персонажи были на всех устройствах и мастер видел ваш лист.",
                    color = DublText, style = MaterialTheme.typography.bodyMedium,
                )
                FuryButton("Войти или создать аккаунт", onSignIn, Modifier.fillMaxWidth(), style = FuryButtonStyle.Primary)
                return@Column
            }
            NicknameRow(account)
            Text(account.email, color = DublMuted, style = MaterialTheme.typography.bodySmall)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(FurySpacing.sm)) {
                AccountDotView(accountDotColor(account.status))
                Text(account.statusText, Modifier.weight(1f), color = DublText, style = MaterialTheme.typography.bodyMedium)
                FuryButton("Синхронизировать", account::requestSync)
            }
            account.lastSync?.let { Text(it, color = DublMuted, style = MaterialTheme.typography.bodySmall) }
            account.conflicts.forEach { conflict ->
                Column(verticalArrangement = Arrangement.spacedBy(FurySpacing.xs)) {
                    Text("КОНФЛИКТ ИЗМЕНЕНИЙ", color = FuryColors.current.goldText, style = furyOverlineStyle())
                    Text(
                        if (conflict.deleted) "«${conflict.name}» удалён на этом устройстве, а на другом изменён."
                        else "«${conflict.name}» изменён на другом устройстве.",
                        color = DublText, style = MaterialTheme.typography.bodyMedium,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(FurySpacing.sm)) {
                        FuryButton(if (conflict.deleted) "Удалить всё равно" else "Сохранить локальную", { scope.launch { account.keepLocal(conflict.characterId) } })
                        FuryButton("Загрузить облачную", { scope.launch { account.useCloud() } })
                    }
                }
            }
            SyncLog(account.syncLog)
            SignOutButton(account, onSignedOut)
        }
    }
}

@Composable
private fun NicknameRow(account: FuryAccount) {
    val scope = rememberCoroutineScope()
    var draft by remember(account.nickname) { mutableStateOf(account.nickname) }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val clean = draft.trim()
    val error = if (clean.isNotEmpty() && clean.length !in 2..32) "Никнейм должен содержать от 2 до 32 символов." else null
    FuryTextField(draft, { draft = it; message = null }, "Никнейм", error = error)
    if (clean != account.nickname && clean.length in 2..32) {
        FuryButton("Сохранить никнейм", {
            busy = true
            scope.launch {
                message = account.updateNickname(clean).fold({ "Никнейм сохранён." }, { it.message ?: "Не удалось сохранить никнейм." })
                busy = false
            }
        }, enabled = !busy)
    }
    message?.let { Text(it, color = FuryColors.current.goldText, style = MaterialTheme.typography.bodySmall) }
}

@Composable
private fun SyncLog(entries: List<FuryAccountLogEntry>) {
    var open by remember { mutableStateOf(false) }
    FuryButton(if (open) "Скрыть журнал синхронизации" else "Журнал синхронизации", { open = !open }, style = FuryButtonStyle.Ghost)
    if (!open) return
    if (entries.isEmpty()) {
        Text("Пока пусто.", color = DublMuted, style = MaterialTheme.typography.bodySmall)
    }
    entries.forEach { entry ->
        Row(horizontalArrangement = Arrangement.spacedBy(FurySpacing.sm)) {
            Text(entry.time, Modifier.width(96.dp), color = DublMuted, style = MaterialTheme.typography.bodySmall)
            Text(entry.text, Modifier.weight(1f), color = DublText, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun SignOutButton(account: FuryAccount, onSignedOut: () -> Unit) {
    val scope = rememberCoroutineScope()
    var asking by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    FuryButton("Выйти", { error = null; asking = true }, style = FuryButtonStyle.Danger)
    if (!asking) return
    val discard: () -> Unit = {
        busy = true
        scope.launch { account.signOutDiscarding(); busy = false; asking = false; onSignedOut() }
    }
    FurySignOutDialog(
        pending = account.pendingChanges,
        busy = busy,
        error = error,
        onCancel = { if (!busy) asking = false },
        onSignOut = discard,
        onSyncAndSignOut = {
            busy = true
            scope.launch {
                account.syncAndSignOut().fold(
                    { asking = false; onSignedOut() },
                    { error = it.message ?: "Не удалось синхронизировать." },
                )
                busy = false
            }
        },
    )
}

/**
 * «Выйти из аккаунта?»: the account's characters leave this device; GM data stays. With [pending] changes the
 * player chooses between sending them first and losing them.
 */
@Composable
fun FurySignOutDialog(
    pending: Int,
    busy: Boolean,
    error: String?,
    onCancel: () -> Unit,
    onSignOut: () -> Unit,
    onSyncAndSignOut: () -> Unit,
) {
    val choice = signOutNeedsChoice(pending)
    FuryDialogScaffold(
        title = "Выйти из аккаунта?",
        onDismiss = onCancel,
        primary = if (choice) FuryDialogAction("Синхронизировать и выйти", onSyncAndSignOut, enabled = !busy) else null,
        secondary = FuryDialogAction("Отмена", onCancel, enabled = !busy),
        destructive = FuryDialogAction(if (choice) "Выйти всё равно" else "Выйти", onSignOut, enabled = !busy),
    ) {
        if (choice) {
            Text(
                "Есть неотправленные изменения: $pending. Если выйти всё равно, они пропадут.",
                color = DublText, style = MaterialTheme.typography.bodyMedium,
            )
        }
        Text(
            "Персонажи аккаунта уйдут с этого устройства и вернутся, когда вы войдёте снова. Бой, враги и заметки мастера останутся.",
            color = DublText, style = MaterialTheme.typography.bodyMedium,
        )
        error?.let { Text(it, color = DublDanger, style = MaterialTheme.typography.bodySmall) }
    }
}
