package com.furybook.web

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.ComposeViewport
import androidx.compose.ui.zIndex
import com.furybook.desktop.DesktopAppState
import com.furybook.desktop.screens.*
import com.furybook.dubl.ui.sheet.SheetChangeReportingHost
import com.furybook.dubl.ui.sheet.SheetScreen
import com.furybook.ui.components.FuryButton
import com.furybook.ui.components.FuryButtonStyle
import com.furybook.ui.components.FuryCharacterPicker
import com.furybook.ui.components.FuryShell
import com.furybook.ui.components.FuryShellCharacter
import com.furybook.ui.components.FuryShellSection
import com.furybook.ui.layout.DublLayoutClass
import com.furybook.ui.layout.layoutClassForWidth
import com.furybook.ui.theme.DublTheme
import com.furybook.ui.theme.furyFontsReady
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal enum class DesktopSection(val label: String) {
    SHEET("Лист"),
    SKILLS("Умения"),
    DEVELOPMENT("Навыки"),
    MAGIC("Магия"),
    EQUIPMENT("Снаряжение"),
    CAMPAIGN("Кампания"),
    CHARACTERS("Ещё"),
}

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    ComposeViewport(viewportContainerId = "furybook-app") {
        DublTheme {
            DesktopVisualTheme {
                WebRoot()
            }
        }
    }
}

@Composable
private fun WebRoot() {
    val cloud = remember { WebCloudSync() }
    val scope = rememberCoroutineScope()
    var resources by remember { mutableStateOf<Map<String, String>?>(null) }
    var session by remember { mutableStateOf<WebAuthSession?>(null) }
    var booting by remember { mutableStateOf(true) }
    var fatalError by remember { mutableStateOf<String?>(null) }
    var connectionError by remember { mutableStateOf<String?>(null) }
    var appEpoch by remember { mutableStateOf(0) }

    suspend fun hydrateRemote(): CloudBootstrap {
        val remote = cloud.pull()
        BrowserCharacterStore().replaceRaw(remote.snapshot)
        BrowserCharacterExtrasStore().replaceRaw(remote.extras)
        BrowserPackState.chiEnabled = remote.chiEnabled
        return remote
    }

    fun cloudErrorMessage(error: Throwable): String =
        error.message?.takeIf { it.isNotBlank() }
            ?: "Не удалось подключиться к Fury Cloud. Проверьте интернет и попробуйте снова."

    LaunchedEffect(Unit) {
        runCatching { resources = loadBundledFcpTexts() }
            .onFailure { fatalError = it.message ?: "Не удалось загрузить правила Fury Book" }
        if (fatalError == null) {
            runCatching { cloud.restoreSession() }
                .onSuccess { restored ->
                    session = restored
                    if (restored != null) {
                        clearBrowserSessionData()
                        runCatching { hydrateRemote() }
                            .onSuccess { connectionError = null }
                            .onFailure { connectionError = cloudErrorMessage(it) }
                    }
                }
                .onFailure { connectionError = cloudErrorMessage(it) }
        }
        booting = false
    }

    val fontsReady = furyFontsReady()
    when {
        fatalError != null -> FatalScreen(fatalError!!)
        booting || resources == null || !fontsReady -> LoadingScreen("Загружаем Fury Book")
        session == null -> LoginScreen(
            cloud = cloud,
            onAuthenticated = { authenticated ->
                scope.launch {
                    clearBrowserSessionData()
                    session = authenticated
                    connectionError = null
                    runCatching { hydrateRemote() }
                        .onSuccess { appEpoch++ }
                        .onFailure { connectionError = cloudErrorMessage(it) }
                }
            },
        )
        connectionError != null -> CloudUnavailableScreen(
            message = connectionError!!,
            onRetry = {
                scope.launch {
                    runCatching { hydrateRemote() }
                        .onSuccess {
                            connectionError = null
                            appEpoch++
                        }
                        .onFailure { connectionError = cloudErrorMessage(it) }
                }
            },
            onSignOut = {
                scope.launch {
                    cloud.signOut()
                    clearBrowserSessionData()
                    session = null
                    connectionError = null
                    appEpoch++
                }
            },
        )
        else -> {
            val activeSession = session!!
            key(activeSession.userId, appEpoch) {
                val state = remember(resources, activeSession.userId, appEpoch) {
                    DesktopAppState(resources!!, cloud)
                }

                LaunchedEffect(state) {
                    cloud.schedule(
                        snapshot = BrowserCharacterStore().raw(),
                        extras = BrowserCharacterExtrasStore().raw(),
                        chiEnabled = BrowserPackState.chiEnabled,
                    )
                }

                FuryWebApp(
                    state = state,
                    cloud = cloud,
                    accountName = cloud.profileNickname.ifBlank { activeSession.email.substringBefore('@') },
                    accountEmail = activeSession.email,
                    onSignOut = {
                        scope.launch {
                            cloud.signOut()
                            clearBrowserSessionData()
                            session = null
                            connectionError = null
                            appEpoch++
                        }
                    },
                )

                cloud.conflict?.let { conflict ->
                    ConflictDialog(
                        conflict = conflict,
                        onUseCloud = {
                            scope.launch {
                                cloud.clearConflictForReload()
                                runCatching { hydrateRemote() }
                                    .onSuccess { appEpoch++ }
                                    .onFailure { connectionError = cloudErrorMessage(it) }
                            }
                        },
                        onKeepMine = {
                            scope.launch {
                                cloud.forceConflict()
                            }
                        },
                    )
                }

                if (cloud.status.phase == CloudSyncPhase.ERROR) {
                    SyncErrorDialog(
                        message = cloud.status.message ?: "Fury Cloud недоступен",
                        onRetry = { scope.launch { cloud.retryLast() } },
                        onReloadCloud = {
                            scope.launch {
                                runCatching { hydrateRemote() }
                                    .onSuccess { appEpoch++ }
                                    .onFailure { connectionError = cloudErrorMessage(it) }
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun LoadingScreen(message: String) {
    Box(Modifier.fillMaxSize().background(DesktopBackground), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            CircularProgressIndicator(color = DesktopAccent)
            Text(message, color = DesktopMuted)
        }
    }
}

@Composable
private fun FatalScreen(message: String) {
    Box(Modifier.fillMaxSize().background(DesktopBackground).padding(24.dp), contentAlignment = Alignment.Center) {
        Surface(
            color = DesktopSurface,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, DesktopBorder),
        ) {
            Column(Modifier.widthIn(max = 520.dp).padding(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Fury Book не запустился", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(message, color = DesktopMuted)
            }
        }
    }
}

private enum class AuthMode { LOGIN, REGISTER }

@Composable
private fun LoginScreen(
    cloud: WebCloudSync,
    onAuthenticated: (WebAuthSession) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var mode by remember { mutableStateOf(AuthMode.LOGIN) }
    var nickname by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var statusIsError by remember { mutableStateOf(false) }

    fun validate(): String? {
        val cleanEmail = email.trim()
        if (cleanEmail.isBlank()) return "Введите email."
        if (!Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$").matches(cleanEmail)) return "Введите корректный email."
        if (password.isBlank()) return "Введите пароль."
        if (mode == AuthMode.REGISTER) {
            val cleanNickname = nickname.trim()
            if (cleanNickname.length !in 2..32) return "Никнейм должен содержать от 2 до 32 символов."
            if (password.length < 8) return "Пароль должен содержать минимум 8 символов."
        }
        return null
    }

    Box(
        Modifier.fillMaxSize().background(DesktopBackground).padding(20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.widthIn(max = 450.dp).fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = DesktopSurface,
            border = BorderStroke(1.dp, DesktopBorder),
            shadowElevation = 18.dp,
        ) {
            Column(
                Modifier.padding(horizontal = 28.dp, vertical = 30.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Fury Book", color = DesktopAccent, fontSize = 34.sp, fontWeight = FontWeight.Bold)
                Text("DUBL 3.69 · Web", color = DesktopMuted)
                Text(
                    if (mode == AuthMode.LOGIN) "Войдите в Fury Account. Веб-версия работает только с облаком."
                    else "Создайте Fury Account. После регистрации подтвердите email из письма.",
                    color = DesktopMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(2.dp))

                if (mode == AuthMode.REGISTER) {
                    OutlinedTextField(
                        value = nickname,
                        onValueChange = { nickname = it.take(32); status = null },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Никнейм") },
                        supportingText = { Text("От 2 до 32 символов. Будет отображаться в Fury Book.") },
                    )
                }

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it; status = null },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Email") },
                    supportingText = {
                        if (mode == AuthMode.REGISTER) Text("На этот адрес придёт письмо для подтверждения аккаунта.")
                    },
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; status = null },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    label = { Text("Пароль") },
                    supportingText = {
                        if (mode == AuthMode.REGISTER) Text("Минимум 8 символов.")
                    },
                )

                status?.let {
                    Text(
                        it,
                        color = if (statusIsError) MaterialTheme.colorScheme.error else DesktopMuted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                Button(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !busy,
                    onClick = {
                        val validation = validate()
                        if (validation != null) {
                            status = validation
                            statusIsError = true
                            return@Button
                        }
                        busy = true
                        status = null
                        statusIsError = false
                        scope.launch {
                            if (mode == AuthMode.LOGIN) {
                                cloud.signIn(email, password)
                                    .onSuccess(onAuthenticated)
                                    .onFailure {
                                        status = it.message ?: "Неверный email или пароль."
                                        statusIsError = true
                                    }
                            } else {
                                cloud.signUp(email, password, nickname)
                                    .onSuccess { created ->
                                        if (created != null) {
                                            onAuthenticated(created)
                                        } else {
                                            status = "Аккаунт создан. Мы отправили письмо на ${email.trim()}. Подтвердите email, затем вернитесь сюда и войдите."
                                            statusIsError = false
                                            mode = AuthMode.LOGIN
                                        }
                                    }
                                    .onFailure {
                                        status = it.message ?: "Не удалось создать аккаунт."
                                        statusIsError = true
                                    }
                            }
                            busy = false
                        }
                    },
                ) {
                    Text(
                        when {
                            busy -> "Подождите…"
                            mode == AuthMode.LOGIN -> "Войти"
                            else -> "Создать аккаунт"
                        },
                    )
                }

                if (mode == AuthMode.LOGIN) {
                    TextButton(
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !busy,
                        onClick = { kotlinx.browser.window.location.href = "../reset-password.html" },
                    ) {
                        Text("Забыли пароль?")
                    }
                }

                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !busy,
                    onClick = {
                        mode = if (mode == AuthMode.LOGIN) AuthMode.REGISTER else AuthMode.LOGIN
                        status = null
                        statusIsError = false
                    },
                ) {
                    Text(if (mode == AuthMode.LOGIN) "Нет аккаунта? Создать" else "Уже есть аккаунт? Войти")
                }

                if (mode == AuthMode.REGISTER) {
                    Text(
                        "1. Введите никнейм, email и пароль.\n2. Нажмите «Создать аккаунт».\n3. Откройте письмо и подтвердите email.\n4. Вернитесь сюда и войдите.",
                        color = DesktopMuted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun CloudUnavailableScreen(
    message: String,
    onRetry: () -> Unit,
    onSignOut: () -> Unit,
) {
    Box(Modifier.fillMaxSize().background(DesktopBackground).padding(24.dp), contentAlignment = Alignment.Center) {
        Surface(
            modifier = Modifier.widthIn(max = 520.dp).fillMaxWidth(),
            color = DesktopSurface,
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, DesktopBorder),
        ) {
            Column(Modifier.padding(26.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Нет связи с Fury Cloud", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(message, color = DesktopMuted)
                Text("Веб-версия не работает офлайн, поэтому данные персонажей здесь не редактируются без соединения.", color = DesktopMuted, style = MaterialTheme.typography.bodySmall)
                Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) { Text("Повторить") }
                TextButton(onClick = onSignOut, modifier = Modifier.fillMaxWidth()) { Text("Выйти из аккаунта") }
            }
        }
    }
}

@Composable
private fun ConflictDialog(
    conflict: CloudConflict,
    onUseCloud: () -> Unit,
    onKeepMine: () -> Unit,
) {
    FuryDialog(
        onDismissRequest = {},
        title = { Text("Персонаж изменён на другом устройстве") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(conflict.characterName, fontWeight = FontWeight.SemiBold)
                Text(
                    "В облаке уже есть более новая версия #${conflict.serverRevision}. " +
                        "Источник: ${conflict.serverUpdatedBy ?: "другое устройство"}.",
                    color = DesktopMuted,
                )
                conflict.serverUpdatedAt?.let { Text("Изменено: ${formatWebTime(it)}", color = DesktopMuted, style = MaterialTheme.typography.bodySmall) }
                Text(
                    "Fury Book не стал ничего перезаписывать автоматически. Можно загрузить облачную версию или явно заменить её текущей. Перед заменой облачная версия остаётся в истории синхронизации.",
                    color = DesktopMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = { Button(onClick = onUseCloud) { Text("Загрузить облачную") } },
        dismissButton = {
            OutlinedButton(onClick = onKeepMine) {
                Text(if (conflict.deleteRequested) "Удалить всё равно" else "Сохранить мою версию")
            }
        },
    )
}

@Composable
private fun SyncErrorDialog(
    message: String,
    onRetry: () -> Unit,
    onReloadCloud: () -> Unit,
) {
    FuryDialog(
        onDismissRequest = {},
        title = { Text("Синхронизация остановлена") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(message, color = DesktopMuted)
                Text("Редактирование заблокировано, пока Fury Book не подтвердит запись в облако.", color = DesktopMuted, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { Button(onClick = onRetry) { Text("Повторить синхронизацию") } },
        dismissButton = { TextButton(onClick = onReloadCloud) { Text("Отбросить локальные изменения") } },
    )
}

private fun formatWebTime(value: String): String = runCatching {
    js("new Date(value).toLocaleString()") as String
}.getOrDefault(value)

@Composable
private fun DesktopVisualTheme(content: @Composable () -> Unit) {
    // Colours and fonts come from the shared DublTheme. The dense desktop screens were laid out
    // around 14sp secondary text, so that size is kept until those screens are rebuilt.
    val typography = MaterialTheme.typography
    MaterialTheme(
        typography = typography.copy(
            bodySmall = typography.bodySmall.copy(fontSize = 14.sp, lineHeight = 18.sp),
            labelMedium = typography.labelMedium.copy(fontSize = 14.sp, lineHeight = 18.sp),
        ),
        content = content,
    )
}

@Composable
private fun FuryWebApp(
    state: DesktopAppState,
    cloud: WebCloudSync,
    accountName: String,
    accountEmail: String,
    onSignOut: () -> Unit,
) {
    var selected by remember { mutableStateOf(DesktopSection.SHEET) }
    var characterPickerOpen by remember { mutableStateOf(false) }

    FuryShell(
        title = "FURY BOOK",
        characterName = state.activeCharacter.name,
        sections = DesktopSection.entries.map { FuryShellSection(it, it.label, primary = it.primary, phoneLabel = it.phoneLabel) },
        selected = selected,
        onSelect = { selected = it },
        onSwitchCharacter = { characterPickerOpen = true },
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val layout = layoutClassForWidth(maxWidth.value.toInt())
            Column(Modifier.fillMaxSize()) {
                // The account and «Выйти» used to sit in the rail; they now head the «Ещё» section on every width.
                if (selected == DesktopSection.CHARACTERS) {
                    MobileAccountStrip(accountName, accountEmail, onSignOut)
                }
                DesktopContent(state, cloud, selected, layout, { selected = it }, Modifier.weight(1f))
            }
        }
    }
    if (characterPickerOpen) {
        FuryCharacterPicker(
            characters = state.snapshot.characters.map { FuryShellCharacter(it.id, it.name) },
            activeId = state.snapshot.activeCharacterId,
            onSelect = { id ->
                state.selectCharacter(id)
                selected = DesktopSection.SHEET
            },
            onManage = { selected = DesktopSection.CHARACTERS },
            onDismiss = { characterPickerOpen = false },
        )
    }
}

/** Phones show the five play sections as bottom tabs; campaign and «Ещё» sit behind «Меню». */
private val DesktopSection.primary: Boolean
    get() = this != DesktopSection.CAMPAIGN && this != DesktopSection.CHARACTERS

/** «Снаряжение» does not fit a fifth of a phone; the phone tab uses the spec's «Вещи». */
private val DesktopSection.phoneLabel: String?
    get() = if (this == DesktopSection.EQUIPMENT) "Вещи" else null

@Composable
private fun MobileAccountStrip(accountName: String, accountEmail: String, onSignOut: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(DesktopSurfaceInset).padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(accountName, color = DesktopText, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(accountEmail, color = DesktopMuted, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        }
        TextButton(onClick = onSignOut) { Text("Выйти") }
    }
}

@Composable
private fun DesktopContent(
    state: DesktopAppState,
    cloud: WebCloudSync,
    section: DesktopSection,
    layout: DublLayoutClass,
    onNavigate: (DesktopSection) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        val pageModifier = Modifier
            .widthIn(max = 2160.dp)
            .fillMaxWidth()
            .fillMaxHeight()
            .padding(
                horizontal = when (layout) {
                    DublLayoutClass.COMPACT -> 12.dp
                    DublLayoutClass.NORMAL -> 24.dp
                    DublLayoutClass.WIDE -> 28.dp
                },
                vertical = if (layout == DublLayoutClass.COMPACT) 10.dp else 22.dp,
            )
        key(section) {
            var entered by remember(section) { mutableStateOf(false) }
            LaunchedEffect(section) { entered = true }
            val pageProgress by animateFloatAsState(
                targetValue = if (entered) 1f else 0f,
                animationSpec = tween(FuryMotion.FastMs),
                label = "web-section-enter",
            )
            val enter = Modifier.graphicsLayer {
                alpha = pageProgress
                translationY = (1f - pageProgress) * 6f
            }
            val animatedPageModifier = pageModifier.then(enter)
            when (section) {
                // The shared sheet pads itself and picks its layout from the full content width.
                DesktopSection.SHEET -> WebSheet(
                    state = state,
                    onNavigate = onNavigate,
                    playModifier = Modifier.widthIn(max = 2160.dp).fillMaxSize().then(enter),
                    editModifier = animatedPageModifier,
                )
                DesktopSection.SKILLS -> SkillsScreen(state, animatedPageModifier)
                DesktopSection.DEVELOPMENT -> DevelopmentScreen(state, animatedPageModifier)
                DesktopSection.MAGIC -> MagicScreen(state, animatedPageModifier)
                DesktopSection.EQUIPMENT -> EquipmentScreen(state, animatedPageModifier)
                DesktopSection.CAMPAIGN -> CampaignScreen(state, cloud, animatedPageModifier)
                DesktopSection.CHARACTERS -> CharactersScreen(state, animatedPageModifier)
            }
        }
    }
}
private data class SheetToast(val text: String, val canUndo: Boolean, val token: Int)

/**
 * The play view, or the old sheet behind «Правка». Changes made on the play view raise the same
 * undo toast the old sheet shows, and «Отменить» calls the same one-step [DesktopAppState.undoLast].
 */
@Composable
private fun WebSheet(
    state: DesktopAppState,
    onNavigate: (DesktopSection) -> Unit,
    playModifier: Modifier,
    editModifier: Modifier,
) {
    var editing by remember { mutableStateOf(false) }
    if (editing) {
        Column(editModifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            FuryButton("Готово", onClick = { editing = false }, style = FuryButtonStyle.Primary)
            CharacterSheetScreen(
                state = state,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                onNavigateSkills = { onNavigate(DesktopSection.SKILLS) },
                onNavigateDevelopment = { onNavigate(DesktopSection.DEVELOPMENT) },
                onNavigateMagic = { onNavigate(DesktopSection.MAGIC) },
                onNavigateEquipment = { onNavigate(DesktopSection.EQUIPMENT) },
            )
        }
        return
    }
    val activeId = state.activeCharacter.id
    var toast by remember(activeId) { mutableStateOf<SheetToast?>(null) }
    // Keeps the last toast on screen while it animates out.
    var displayedToast by remember(activeId) { mutableStateOf<SheetToast?>(null) }
    var toastHovered by remember(activeId) { mutableStateOf(false) }
    var toastCount by remember { mutableStateOf(0) }
    // Keyed on the character too: the toast state above is per character, and a host kept from
    // before a switch would keep writing into the previous character's toast.
    val host = remember(state, activeId) {
        SheetChangeReportingHost(state) { change -> toast = SheetToast(change.text, state.canUndo, ++toastCount) }
    }
    LaunchedEffect(toast) {
        if (toast != null) displayedToast = toast
    }
    LaunchedEffect(toast, toastHovered) {
        val shown = toast ?: return@LaunchedEffect
        if (!toastHovered) {
            delay(FuryMotion.UndoToastDurationMs)
            if (toast == shown && !toastHovered) toast = null
        }
    }
    Box(playModifier) {
        SheetScreen(
            host,
            onEdit = { editing = true },
            onOpenSkills = { onNavigate(DesktopSection.SKILLS) },
            portraitLoader = { dataUrl -> rememberWebPortrait(dataUrl) },
            onPickPortrait = { pickPortraitDataUrl { dataUrl -> state.setPortrait(dataUrl) } },
        )
        AnimatedVisibility(
            visible = toast != null,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(horizontal = 18.dp, vertical = 10.dp)
                .widthIn(max = 860.dp)
                .fillMaxWidth()
                .zIndex(40f),
            enter = slideInVertically(
                animationSpec = tween(FuryMotion.StandardMs),
                initialOffsetY = { -it / 2 },
            ) + fadeIn(tween(FuryMotion.FastMs)),
            exit = slideOutVertically(
                animationSpec = tween(FuryMotion.StandardMs),
                targetOffsetY = { -it / 3 },
            ) + fadeOut(tween(FuryMotion.FastMs)),
        ) {
            displayedToast?.let { shown ->
                FuryUndoToast(
                    text = shown.text,
                    canUndo = shown.canUndo,
                    onUndo = {
                        if (shown.canUndo) state.undoLast()
                        toast = null
                    },
                    onDismiss = { toast = null; toastHovered = false },
                    onHoverChange = { toastHovered = it },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
