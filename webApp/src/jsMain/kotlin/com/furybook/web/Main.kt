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
import com.furybook.ui.theme.FuryThemedApp
import com.furybook.ui.components.FuryThemeButton
import com.furybook.ui.components.FuryShellAccount
import com.furybook.ui.account.FuryAccountPanel
import com.furybook.ui.account.FuryLoginScreen
import com.furybook.ui.account.FuryStartMode
import com.furybook.ui.account.accountDotColor
import com.furybook.ui.account.furyShowsLogin
import com.furybook.ui.theme.FuryColors
import com.furybook.dubl.ui.characters.DublCharactersScreen
import com.furybook.dubl.ui.characters.unarchivedCharacters
import com.furybook.dubl.master.MasterStore
import com.furybook.dubl.model.SkillCatalog
import com.furybook.dubl.ui.master.DublMasterScreen
import com.furybook.dubl.ui.master.MasterCatalogs
import com.furybook.ui.theme.furyFontsReady
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal enum class DesktopSection(val label: String) {
    SHEET("Лист"),
    MASTER("Мастер"),
    CHARACTERS("Персонажи"),
}

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    ComposeViewport(viewportContainerId = "furybook-app") {
        FuryThemedApp(remember { WebThemeStore() }) {
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
    val startStore = remember { WebStartStore() }
    val archiveStore = remember { WebArchiveStore() }
    var startMode by remember { mutableStateOf(startStore.load()) }
    var resources by remember { mutableStateOf<Map<String, String>?>(null) }
    var booting by remember { mutableStateOf(true) }
    var fatalError by remember { mutableStateOf<String?>(null) }
    var connectionError by remember { mutableStateOf<String?>(null) }
    var appEpoch by remember { mutableStateOf(0) }
    // A guest opened «Войти» from the menu: the login page with a way back.
    var loginFromGuest by remember { mutableStateOf(false) }
    var mergeNotice by remember { mutableStateOf<String?>(null) }

    fun saveStart(mode: FuryStartMode) { startMode = mode; startStore.save(mode) }

    /**
     * Loads the account. Guest characters still in this browser (an upload that never finished, a tab closed too
     * early) join it every time, so they can't stay stranded while signed in.
     */
    suspend fun hydrateRemote() {
        val joined = hydrateRemoteMergingGuest(cloud)
        if (joined > 0) mergeNotice = "Персонажей с этого устройства добавлено в аккаунт: $joined."
    }

    fun cloudErrorMessage(error: Throwable): String =
        error.message?.takeIf { it.isNotBlank() }
            ?: "Не удалось подключиться к Fury Cloud. Проверьте интернет и попробуйте снова."

    val account = remember {
        WebAccount(
            cloud = cloud,
            onSignedIn = {
                connectionError = null
                // Guest characters still in this browser join the account; the pull never replaces them.
                runCatching { hydrateRemote() }.onFailure { connectionError = cloudErrorMessage(it) }
                saveStart(FuryStartMode.SIGNED_IN)
                appEpoch++
            },
            onSignedOut = {
                WebGuestUpload.reset()
                clearBrowserSessionData()
                // The account's characters left the browser; so does the list of which were archived.
                archiveStore.save(emptySet())
                connectionError = null
                saveStart(FuryStartMode.NONE)
                appEpoch++
            },
            onUseCloud = {
                runCatching { hydrateRemote() }
                    .onSuccess { appEpoch++ }
                    .onFailure { connectionError = cloudErrorMessage(it) }
            },
        )
    }

    LaunchedEffect(Unit) {
        runCatching { resources = loadBundledFcpTexts() }
            .onFailure { fatalError = it.message ?: "Не удалось загрузить правила Fury Book" }
        if (fatalError == null) {
            runCatching { cloud.restoreSession() }
                .onSuccess { restored ->
                    account.session = restored
                    if (restored != null) {
                        clearBrowserSessionData()
                        runCatching { hydrateRemote() }
                            .onSuccess { connectionError = null }
                            .onFailure { connectionError = cloudErrorMessage(it) }
                    } else if (startMode == FuryStartMode.GUEST) {
                        BrowserGuestStorage.enable()
                    }
                }
                .onFailure {
                    // Offline with no session: a guest still plays from this browser's storage.
                    if (startMode == FuryStartMode.GUEST) BrowserGuestStorage.enable() else connectionError = cloudErrorMessage(it)
                }
        }
        booting = false
    }
    LaunchedEffect(cloud.status.phase) { WebGuestUpload.onSyncPhase(cloud.status.phase, cloud) }

    val fontsReady = furyFontsReady()
    when {
        fatalError != null -> FatalScreen(fatalError!!)
        booting || resources == null || !fontsReady -> LoadingScreen("Загружаем Fury Book")
        loginFromGuest || furyShowsLogin(startMode, account.signedIn, BrowserGuestStorage.hasData()) -> FuryLoginScreen(
            account = account,
            onGuest = {
                BrowserGuestStorage.enable()
                saveStart(FuryStartMode.GUEST)
                appEpoch++
            },
            onSignedIn = { loginFromGuest = false },
            onBack = if (loginFromGuest) ({ loginFromGuest = false }) else null,
            modifier = Modifier.fillMaxSize(),
        )
        account.signedIn && connectionError != null -> CloudUnavailableScreen(
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
            onSignOut = { scope.launch { account.signOutDiscarding() } },
        )
        else -> {
            val activeSession = account.session
            key(activeSession?.userId ?: "guest", appEpoch) {
                val state = remember(resources, activeSession?.userId, appEpoch) {
                    DesktopAppState(resources!!, if (activeSession != null) cloud else null, archiveStore)
                }

                if (activeSession != null) LaunchedEffect(state) {
                    cloud.schedule(
                        snapshot = BrowserCharacterStore().raw(),
                        extras = BrowserCharacterExtrasStore().raw(),
                        chiEnabled = BrowserPackState.chiEnabled,
                    )
                }

                val accountEntry = FuryShellAccount(
                    label = if (account.signedIn) account.nickname.ifBlank { account.email.substringBefore('@') } else "Гость",
                    dot = accountDotColor(account.status),
                ) { close ->
                    FuryAccountPanel(
                        account = account,
                        isGuest = !account.signedIn,
                        onSignIn = { close(); loginFromGuest = true },
                        onSignedOut = close,
                    )
                }
                FuryWebApp(
                    state = state,
                    cloud = cloud,
                    account = account,
                    accountEntry = accountEntry,
                    mergeNotice = mergeNotice,
                    onDismissMergeNotice = { mergeNotice = null },
                    onSignIn = { loginFromGuest = true },
                )

                if (activeSession != null) cloud.conflict?.let { conflict ->
                    ConflictDialog(
                        conflict = conflict,
                        onUseCloud = { scope.launch { account.useCloud() } },
                        onKeepMine = { scope.launch { cloud.forceConflict() } },
                    )
                }

                if (activeSession != null && cloud.status.phase == CloudSyncPhase.ERROR) {
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
    account: WebAccount,
    accountEntry: FuryShellAccount,
    mergeNotice: String?,
    onDismissMergeNotice: () -> Unit,
    onSignIn: () -> Unit,
) {
    // Мастер's data is the browser's, not the account's: it survives sign-out.
    val masterStore = remember { WebMasterStore() }
    var selected by remember { mutableStateOf(DesktopSection.SHEET) }
    var characterPickerOpen by remember { mutableStateOf(false) }
    // Set by «Новый персонаж»; the sheet consumes it by opening the editor at Основа.
    var openEditorAtBasics by remember { mutableStateOf(false) }
    var editorShown by remember { mutableStateOf(false) }

    FuryShell(
        title = "FURY BOOK",
        characterName = state.activeCharacter.name,
        sections = DesktopSection.entries.map { FuryShellSection(it, it.label, primary = false) },
        selected = selected,
        onSelect = { selected = it },
        onSwitchCharacter = { characterPickerOpen = true },
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        account = { FuryThemeButton() },
        phoneChromeHidden = editorShown && selected == DesktopSection.SHEET,
        accountEntry = accountEntry,
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val layout = layoutClassForWidth(maxWidth.value.toInt())
            Column(Modifier.fillMaxSize()) {
                if (!account.signedIn && !(editorShown && selected == DesktopSection.SHEET)) GuestBar(layout, onSignIn)
                mergeNotice?.let { NoticeBar(it, onDismissMergeNotice) }
                // Signed in, the guest bar is gone, but Мастер still saves to this browser: its storage problems show here.
                if (account.signedIn) BrowserGuestStorage.problem?.let { NoticeBar(it) { BrowserGuestStorage.problem = null } }
                DesktopContent(
                    state, account, selected, layout, { selected = it }, Modifier.weight(1f),
                    masterStore = masterStore,
                    onSignIn = onSignIn,
                    sheetHooks = WebSheetHooks(
                        openEditorAtBasics = openEditorAtBasics,
                        onOpenEditorConsumed = { openEditorAtBasics = false },
                        onEditorShown = { editorShown = it },
                        onCharacterCreated = { openEditorAtBasics = true; selected = DesktopSection.SHEET },
                    ),
                )
            }
        }
    }
    if (characterPickerOpen) {
        FuryCharacterPicker(
            characters = unarchivedCharacters(state).map { FuryShellCharacter(it.id, it.name) },
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


/** Guest mode: one line on where the characters live, a quota warning when the browser is full, and «Войти». */
@Composable
private fun GuestBar(layout: DublLayoutClass, onSignIn: () -> Unit) {
    val p = FuryColors.current
    Row(
        Modifier.fillMaxWidth().background(p.panel2).padding(horizontal = 14.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                if (layout == DublLayoutClass.COMPACT) "Гостевой режим: всё хранится в этом браузере."
                else "Гостевой режим: всё хранится на этом устройстве. В браузере очистка данных сайта удалит их — делайте экспорт.",
                color = p.muted, style = MaterialTheme.typography.bodySmall,
            )
            BrowserGuestStorage.problem?.let { Text(it, color = p.accText, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold) }
        }
        FuryButton("Войти", onSignIn, style = FuryButtonStyle.Ghost)
    }
}

@Composable
private fun NoticeBar(text: String, onDismiss: () -> Unit) {
    val p = FuryColors.current
    Row(
        Modifier.fillMaxWidth().background(p.goldSoft).padding(horizontal = 14.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, Modifier.weight(1f), color = p.goldText, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
        FuryButton("✕", onDismiss, style = FuryButtonStyle.Ghost)
    }
}

@Composable
private fun DesktopContent(
    state: DesktopAppState,
    account: WebAccount,
    section: DesktopSection,
    layout: DublLayoutClass,
    onNavigate: (DesktopSection) -> Unit,
    modifier: Modifier = Modifier,
    masterStore: MasterStore,
    onSignIn: () -> Unit,
    sheetHooks: WebSheetHooks = WebSheetHooks(),
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
                    hooks = sheetHooks,
                )
                DesktopSection.MASTER -> DublMasterScreen(
                    store = masterStore,
                    account = account,
                    catalogs = MasterCatalogs(state.canonicalDevelopmentCatalog, state.magicEquipmentCatalog, state.conditionCatalog, SkillCatalog.builtIns),
                    onSignIn = onSignIn,
                    modifier = Modifier.widthIn(max = 2160.dp).fillMaxSize().then(enter),
                )
                DesktopSection.CHARACTERS -> DublCharactersScreen(
                    host = state,
                    account = account,
                    packs = rememberWebPacks(state),
                    onOpen = { onNavigate(DesktopSection.SHEET) },
                    onNew = { state.createCharacter(); sheetHooks.onCharacterCreated() },
                    onImportFile = { onRead -> pickTextFile(".dubl,application/json,text/plain") { raw -> onRead(raw) } },
                    onExportFile = { fileName, content, onSaved -> downloadTextFile(fileName, content); onSaved(true) },
                    modifier = animatedPageModifier,
                    portraitLoader = { dataUrl -> rememberWebPortrait(dataUrl) },
                )
            }
        }
    }
}
/** How the sheet section talks to the shell about the editor. */
private data class WebSheetHooks(
    val openEditorAtBasics: Boolean = false,
    val onOpenEditorConsumed: () -> Unit = {},
    val onEditorShown: (Boolean) -> Unit = {},
    val onCharacterCreated: () -> Unit = {},
)

private data class SheetToast(val text: String, val canUndo: Boolean, val token: Int)

/**
 * The shared sheet (play and edit). Changes made on it raise the same
 * undo toast the old sheet showed, and «Отменить» calls the same one-step [DesktopAppState.undoLast].
 */
@Composable
private fun WebSheet(
    state: DesktopAppState,
    onNavigate: (DesktopSection) -> Unit,
    playModifier: Modifier,
    hooks: WebSheetHooks = WebSheetHooks(),
) {
    val activeId = state.activeCharacter.id
    // Leaving the sheet section closes the editor with it; the shell gets its phone chrome back.
    DisposableEffect(Unit) { onDispose { hooks.onEditorShown(false) } }
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
            portraitLoader = { dataUrl -> rememberWebPortrait(dataUrl) },
            onPickPortrait = { pickPortraitDataUrl { dataUrl -> state.setPortrait(dataUrl) } },
            openEditorAtBasics = hooks.openEditorAtBasics,
            onOpenEditorConsumed = hooks.onOpenEditorConsumed,
            onEditorShown = hooks.onEditorShown,
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
