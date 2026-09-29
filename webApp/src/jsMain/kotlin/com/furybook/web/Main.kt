package com.furybook.web

import androidx.compose.animation.animateColorAsState
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
import com.furybook.desktop.DesktopAppState
import com.furybook.desktop.screens.*
import com.furybook.ui.layout.DublLayoutClass
import com.furybook.ui.layout.layoutClassForWidth
import com.furybook.ui.theme.DublTheme
import kotlinx.coroutines.launch

internal enum class DesktopSection(val label: String) {
    SHEET("Лист"),
    SKILLS("Умения"),
    DEVELOPMENT("Навыки"),
    MAGIC("Магия"),
    EQUIPMENT("Снаряжение"),
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

    when {
        fatalError != null -> FatalScreen(fatalError!!)
        booting || resources == null -> LoadingScreen("Загружаем Fury Book")
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
    val colors = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    MaterialTheme(
        colorScheme = colors.copy(
            primary = DesktopAccent,
            onPrimary = Color.White,
            primaryContainer = DesktopAccentSoft,
            onPrimaryContainer = DesktopText,
            secondary = DesktopGold,
            background = DesktopBackground,
            onBackground = DesktopText,
            surface = DesktopSurface,
            onSurface = DesktopText,
            surfaceVariant = DesktopSurfaceRaised,
            onSurfaceVariant = DesktopMuted,
            outline = DesktopBorder,
            outlineVariant = DesktopBorder,
        ),
        typography = typography.copy(
            headlineMedium = typography.headlineMedium.copy(fontSize = 30.sp, lineHeight = 36.sp),
            headlineSmall = typography.headlineSmall.copy(fontSize = 28.sp, lineHeight = 34.sp),
            titleLarge = typography.titleLarge.copy(fontSize = 20.sp, lineHeight = 25.sp),
            titleMedium = typography.titleMedium.copy(fontSize = 17.sp, lineHeight = 22.sp),
            bodyLarge = typography.bodyLarge.copy(fontSize = 16.sp, lineHeight = 22.sp),
            bodyMedium = typography.bodyMedium.copy(fontSize = 15.sp, lineHeight = 20.sp),
            bodySmall = typography.bodySmall.copy(fontSize = 14.sp, lineHeight = 18.sp),
            labelLarge = typography.labelLarge.copy(fontSize = 14.sp, lineHeight = 18.sp),
            labelMedium = typography.labelMedium.copy(fontSize = 14.sp, lineHeight = 18.sp),
        ),
        content = content,
    )
}

@Composable
private fun FuryWebApp(
    state: DesktopAppState,
    accountName: String,
    accountEmail: String,
    onSignOut: () -> Unit,
) {
    var selected by remember { mutableStateOf(DesktopSection.SHEET) }

    BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val layout = layoutClassForWidth(maxWidth.value.toInt())
        if (layout == DublLayoutClass.COMPACT) {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                bottomBar = { MobileBottomNavigation(selected, { selected = it }) },
            ) { padding ->
                Column(Modifier.fillMaxSize().padding(padding)) {
                    if (selected == DesktopSection.CHARACTERS) {
                        MobileAccountStrip(accountName, accountEmail, onSignOut)
                    }
                    DesktopContent(state, selected, layout, { selected = it }, Modifier.weight(1f))
                }
            }
        } else {
            Row(Modifier.fillMaxSize()) {
                DesktopRail(
                    state = state,
                    selected = selected,
                    onSelected = { selected = it },
                    accountName = accountName,
                    accountEmail = accountEmail,
                    onSignOut = onSignOut,
                    modifier = Modifier.width(230.dp).fillMaxHeight(),
                )
                DesktopContent(state, selected, layout, { selected = it }, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun DesktopRail(
    state: DesktopAppState,
    selected: DesktopSection,
    onSelected: (DesktopSection) -> Unit,
    accountName: String,
    accountEmail: String,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var switcherOpen by remember { mutableStateOf(false) }
    Column(
        modifier = modifier.background(DesktopSurfaceInset).padding(horizontal = 14.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            "Fury Book",
            style = MaterialTheme.typography.headlineMedium,
            color = DesktopAccent,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
        Text("DUBL 3.69", color = DesktopMuted, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 8.dp))
        Surface(
            modifier = Modifier.fillMaxWidth().clickable { switcherOpen = true },
            color = DesktopSurfaceRaised,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.52f)),
        ) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 11.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(state.activeCharacter.name, fontWeight = FontWeight.SemiBold, maxLines = 2)
                Text("${state.activeCharacter.experience} XP · сменить ▼", color = DesktopMuted, style = MaterialTheme.typography.bodySmall)
            }
        }
        DropdownMenu(expanded = switcherOpen, onDismissRequest = { switcherOpen = false }) {
            state.snapshot.characters.forEach { character ->
                DropdownMenuItem(
                    text = { Text(if (character.id == state.snapshot.activeCharacterId) "✓ ${character.name}" else character.name) },
                    onClick = {
                        state.selectCharacter(character.id)
                        switcherOpen = false
                        onSelected(DesktopSection.SHEET)
                    },
                )
            }
            DropdownMenuItem(
                text = { Text("Управление персонажами…") },
                onClick = { switcherOpen = false; onSelected(DesktopSection.CHARACTERS) },
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
        DesktopSection.entries.filter { it != DesktopSection.CHARACTERS }.forEach { section ->
            NavigationItem(section, section == selected, { onSelected(section) }, Modifier.fillMaxWidth())
        }
        Spacer(Modifier.weight(1f))
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
        NavigationItem(
            DesktopSection.CHARACTERS,
            DesktopSection.CHARACTERS == selected,
            { onSelected(DesktopSection.CHARACTERS) },
            Modifier.fillMaxWidth(),
        )
        Column(Modifier.padding(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(accountName, color = DesktopText, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(accountEmail, color = DesktopMuted, style = MaterialTheme.typography.bodySmall, maxLines = 1)
        }
        TextButton(onClick = onSignOut, modifier = Modifier.fillMaxWidth()) { Text("Выйти") }
    }
}

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
private fun MobileBottomNavigation(selected: DesktopSection, onSelected: (DesktopSection) -> Unit) {
    Column(Modifier.fillMaxWidth().background(DesktopSurfaceInset)) {
        HorizontalDivider(color = DesktopBorder.copy(alpha = .72f))
        Row(
            Modifier.fillMaxWidth().height(72.dp).padding(horizontal = 3.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DesktopSection.entries.forEach { section ->
                val active = selected == section
                Surface(
                    modifier = Modifier.weight(1f).fillMaxHeight().clickable { onSelected(section) },
                    shape = RoundedCornerShape(12.dp),
                    color = if (active) DesktopAccentSoft else Color.Transparent,
                    border = if (active) BorderStroke(1.dp, DesktopAccent.copy(alpha = .28f)) else null,
                ) {
                    Column(
                        Modifier.fillMaxSize().padding(horizontal = 1.dp, vertical = 5.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        DesktopIcon(section.iconKind, tint = if (active) DesktopAccent else DesktopMuted, size = 23.dp)
                        Text(
                            section.label,
                            fontSize = 9.5.sp,
                            lineHeight = 11.sp,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                            color = if (active) DesktopText else DesktopMuted,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NavigationItem(section: DesktopSection, active: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val background by animateColorAsState(
        targetValue = if (active) DesktopAccentSoft else Color.Transparent,
        animationSpec = tween(FuryMotion.FastMs),
        label = "navigation-background",
    )
    val iconColor by animateColorAsState(
        targetValue = if (active) DesktopAccent else DesktopMuted,
        animationSpec = tween(FuryMotion.FastMs),
        label = "navigation-icon",
    )
    val textColor by animateColorAsState(
        targetValue = if (active) MaterialTheme.colorScheme.onSurface else DesktopMuted,
        animationSpec = tween(FuryMotion.FastMs),
        label = "navigation-text",
    )
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color = background,
        border = if (active) BorderStroke(1.dp, DesktopAccent.copy(alpha = 0.32f)) else null,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DesktopIcon(kind = section.iconKind, tint = iconColor, size = 20.dp)
            Text(section.label, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal, color = textColor)
        }
    }
}

private val DesktopSection.iconKind: DesktopIconKind
    get() = when (this) {
        DesktopSection.SHEET -> DesktopIconKind.SHEET
        DesktopSection.SKILLS -> DesktopIconKind.SKILLS
        DesktopSection.DEVELOPMENT -> DesktopIconKind.DEVELOPMENT
        DesktopSection.MAGIC -> DesktopIconKind.MAGIC
        DesktopSection.EQUIPMENT -> DesktopIconKind.EQUIPMENT
        DesktopSection.CHARACTERS -> DesktopIconKind.CHARACTERS
    }

@Composable
private fun DesktopContent(
    state: DesktopAppState,
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
            val animatedPageModifier = pageModifier.graphicsLayer {
                alpha = pageProgress
                translationY = (1f - pageProgress) * 6f
            }
            when (section) {
                DesktopSection.SHEET -> CharacterSheetScreen(
                    state = state,
                    modifier = animatedPageModifier,
                    onNavigateSkills = { onNavigate(DesktopSection.SKILLS) },
                    onNavigateDevelopment = { onNavigate(DesktopSection.DEVELOPMENT) },
                    onNavigateMagic = { onNavigate(DesktopSection.MAGIC) },
                    onNavigateEquipment = { onNavigate(DesktopSection.EQUIPMENT) },
                )
                DesktopSection.SKILLS -> SkillsScreen(state, animatedPageModifier)
                DesktopSection.DEVELOPMENT -> DevelopmentScreen(state, animatedPageModifier)
                DesktopSection.MAGIC -> MagicScreen(state, animatedPageModifier)
                DesktopSection.EQUIPMENT -> EquipmentScreen(state, animatedPageModifier)
                DesktopSection.CHARACTERS -> CharactersScreen(state, animatedPageModifier)
            }
        }
    }
}
