package com.furybook.android.ui

import com.furybook.android.data.AndroidArchiveStore
import com.furybook.android.data.AndroidStartStore
import com.furybook.dubl.ui.characters.DublCharactersScreen
import com.furybook.ui.account.FuryAccountPanel
import com.furybook.ui.account.FuryLoginScreen
import com.furybook.ui.account.FuryStartMode
import com.furybook.ui.account.accountDotColor
import com.furybook.ui.account.furyShowsLogin
import com.furybook.ui.components.FuryShellAccount
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.furybook.android.data.AndroidContentPackState
import com.furybook.android.cloud.AndroidCloudController
import com.furybook.android.data.CharacterRepository
import com.furybook.android.data.AndroidFcpInstaller
import com.furybook.android.data.CharacterSheetExtrasRepository
import com.furybook.android.state.CharacterController
import com.furybook.core.cloud.ObservingCharacterStore
import com.furybook.core.cloud.ObservingCharacterExtrasStore
import com.furybook.android.ui.components.dismissKeyboardOnPointerDown
import com.furybook.android.ui.screens.CampaignScreen
import com.furybook.dubl.content.DublChiFcp
import com.furybook.android.ui.screens.RecentChangeBar
import com.furybook.android.ui.screens.recentResourceChange
import com.furybook.dubl.ui.sheet.SheetChange
import com.furybook.dubl.ui.sheet.SheetChangeReportingHost
import com.furybook.dubl.ui.sheet.SheetScreen
import com.furybook.ui.components.FuryButton
import com.furybook.ui.components.FuryButtonStyle
import com.furybook.ui.components.FuryCharacterPicker
import com.furybook.ui.components.FuryShell
import com.furybook.ui.components.FuryShellCharacter
import com.furybook.ui.components.FuryShellSection
import com.furybook.ui.theme.FuryColors
import com.furybook.ui.components.FuryThemeButton
import com.furybook.ui.theme.DublAccent
import com.furybook.ui.theme.DublGold
import com.furybook.ui.theme.DublFocus
import com.furybook.ui.theme.DublMuted
import com.furybook.ui.theme.DublSurface
import com.furybook.ui.theme.DublSurfaceInset
import kotlinx.coroutines.delay

private enum class AppSection(val label: String) {
    OVERVIEW("Лист"),
    CAMPAIGN("Кампания"),
    CHARACTERS("Персонажи"),
}

@Composable
fun DublApp() {
    val appContext = androidx.compose.ui.platform.LocalContext.current.applicationContext
    val characterRepository = remember(appContext) { CharacterRepository(appContext) }
    val extrasRepository = remember(appContext) { CharacterSheetExtrasRepository(appContext) }
    val observingCharacters = remember(characterRepository) { ObservingCharacterStore(characterRepository) }
    val observingExtras = remember(extrasRepository) { ObservingCharacterExtrasStore(extrasRepository) }
    val cloudController = remember(appContext, characterRepository, extrasRepository) {
        AndroidCloudController(appContext, characterRepository, extrasRepository).also {
            it.bind(observingCharacters, observingExtras)
        }
    }
    val reloadToken = cloudController.reloadToken
    val archiveStore = remember(appContext) { AndroidArchiveStore(appContext) }
    val startStore = remember(appContext) { AndroidStartStore(appContext) }
    var startMode by remember { mutableStateOf(startStore.load()) }
    // A guest opened «Войти» from the menu: the login page with a way back.
    var loginFromGuest by rememberSaveable { mutableStateOf(false) }
    val controller = remember(appContext, reloadToken) {
        CharacterController(observingCharacters, observingExtras, archiveStore)
    }
    var selected by rememberSaveable { mutableStateOf(AppSection.OVERVIEW) }
    var chiPackEnabled by rememberSaveable { mutableStateOf(AndroidContentPackState.isChiEnabled(appContext)) }
    val chiDevelopmentIds = remember(appContext) { AndroidContentPackState.chiDevelopmentIds(appContext) }

    LaunchedEffect(cloudController) {
        cloudController.updateEnabledPackIds(
            AndroidContentPackState.composition(appContext, chiPackEnabled).active.mapTo(linkedSetOf()) { it.id },
        )
        cloudController.restoreAndSync()
        val cloudIds = cloudController.enabledPackIds
        AndroidContentPackState.setChiEnabled(appContext, DublChiFcp.PACK_ID in cloudIds)
        AndroidFcpInstaller.listInstalled(appContext).forEach { manifest ->
            AndroidContentPackState.setPackEnabled(appContext, manifest.id, manifest.id in cloudIds)
        }
        chiPackEnabled = AndroidContentPackState.isChiEnabled(appContext)
    }

    LaunchedEffect(chiPackEnabled) {
        val installedIds = AndroidFcpInstaller.listInstalled(appContext).mapTo(linkedSetOf()) { it.id }
        val optionalAvailable = installedIds + DublChiFcp.PACK_ID
        val availableActive = AndroidContentPackState.composition(appContext, chiPackEnabled).active
            .mapTo(linkedSetOf()) { it.id }
        cloudController.updateEnabledPackIds((cloudController.enabledPackIds - optionalAvailable) + availableActive)
    }

    LaunchedEffect(chiPackEnabled, controller.snapshot.activeCharacterId) {
        controller.setRuntimeContentSuppression(
            developmentIds = if (chiPackEnabled) emptySet() else chiDevelopmentIds,
            suppressChiResource = !chiPackEnabled,
        )
    }

    fun saveStart(mode: FuryStartMode) { startMode = mode; startStore.save(mode) }
    if (loginFromGuest || furyShowsLogin(startMode, cloudController.signedIn, controller.snapshot.characters.isNotEmpty())) {
        BackHandler(enabled = loginFromGuest) { loginFromGuest = false }
        Scaffold(containerColor = MaterialTheme.colorScheme.background) { innerPadding ->
            FuryLoginScreen(
                account = cloudController,
                onGuest = { saveStart(FuryStartMode.GUEST) },
                onSignedIn = { saveStart(FuryStartMode.SIGNED_IN); loginFromGuest = false; selected = AppSection.OVERVIEW },
                onBack = if (loginFromGuest) ({ loginFromGuest = false }) else null,
                modifier = Modifier.fillMaxSize().padding(innerPadding).dismissKeyboardOnPointerDown(),
            )
        }
        return
    }
    val accountEntry = FuryShellAccount(
        label = if (cloudController.signedIn) cloudController.nickname.ifBlank { cloudController.email.substringBefore('@') } else "Гость",
        dot = accountDotColor(cloudController.status),
    ) { close ->
        FuryAccountPanel(
            account = cloudController,
            isGuest = !cloudController.signedIn,
            onSignIn = { close(); loginFromGuest = true },
            onSignedOut = {
                close()
                // The account's characters left the device; so does the list of which were archived.
                archiveStore.save(emptySet())
                saveStart(FuryStartMode.NONE)
            },
        )
    }
    val packs = rememberAndroidPacks(chiPackEnabled) { packId, enabled ->
        AndroidContentPackState.setPackEnabled(appContext, packId, enabled)
        if (packId == DublChiFcp.PACK_ID) chiPackEnabled = enabled
        val nextIds = cloudController.enabledPackIds.toMutableSet().apply { if (enabled) add(packId) else remove(packId) }
        cloudController.updateEnabledPackIds(nextIds)
        cloudController.requestSync()
    }
    val files = rememberAndroidCharacterFiles()

    var characterPickerOpen by remember { mutableStateOf(false) }
    // Set by «+ Создать»; the sheet consumes it by opening the editor at Основа.
    var openEditorAtBasics by remember { mutableStateOf(false) }
    var editorShown by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.dismissKeyboardOnPointerDown(),
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        FuryShell(
            title = "FURY BOOK",
            characterName = controller.active.name,
            sections = AppSection.entries.map { FuryShellSection(it, it.label, primary = false) },
            selected = selected,
            onSelect = { target -> if (target != selected) selected = target },
            onSwitchCharacter = { characterPickerOpen = true },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            account = { FuryThemeButton() },
            phoneChromeHidden = editorShown && selected == AppSection.OVERVIEW,
            accountEntry = accountEntry,
        ) {
                when (selected) {
                    AppSection.OVERVIEW -> AndroidSheet(
                        controller = controller,
                        chiPackEnabled = chiPackEnabled,
                        openEditorAtBasics = openEditorAtBasics,
                        onOpenEditorConsumed = { openEditorAtBasics = false },
                        onEditorShown = { editorShown = it },
                    )
                    AppSection.CAMPAIGN -> CampaignScreen(
                        controller = controller,
                        cloudController = cloudController,
                        onBack = { selected = AppSection.CHARACTERS },
                    )
                    AppSection.CHARACTERS -> DublCharactersScreen(
                        host = controller,
                        account = cloudController,
                        packs = packs,
                        onOpen = { selected = AppSection.OVERVIEW },
                        onNew = { controller.createCharacter(); openEditorAtBasics = true; selected = AppSection.OVERVIEW },
                        onImportFile = files.importFile,
                        onExportFile = files.exportFile,
                        portraitLoader = { uri -> rememberAndroidPortrait(uri) },
                    )
                }
        }
    }

    if (characterPickerOpen) {
        FuryCharacterPicker(
            characters = controller.snapshot.characters.map { FuryShellCharacter(it.id, it.name) },
            activeId = controller.snapshot.activeCharacterId,
            onSelect = { id ->
                controller.selectCharacter(id)
                selected = AppSection.OVERVIEW
            },
            onManage = { selected = AppSection.CHARACTERS },
            onDismiss = { characterPickerOpen = false },
        )
    }
}

private data class SheetToast(val text: String, val accent: Color, val canUndo: Boolean, val token: Int)

/**
 * The shared sheet (play and edit). Changes made on it raise the same «Последнее: … / Отменить»
 * bar the old sheet showed, and «Отменить» calls the
 * same one-step [CharacterController.undoLast].
 */
@Composable
private fun AndroidSheet(
    controller: CharacterController,
    chiPackEnabled: Boolean,
    openEditorAtBasics: Boolean = false,
    onOpenEditorConsumed: () -> Unit = {},
    onEditorShown: (Boolean) -> Unit = {},
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    // Leaving the sheet tab closes the editor with it; the shell gets its bottom tabs back.
    DisposableEffect(Unit) { onDispose { onEditorShown(false) } }
    val activeId = controller.active.id
    // Same picker as the old sheet: keep read access across restarts, then store the URI.
    val portraitPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            controller.setPortrait(uri.toString())
        }
    }
    var toast by remember(activeId) { mutableStateOf<SheetToast?>(null) }
    var toastCount by remember { mutableStateOf(0) }
    val baseHost = rememberAndroidSheetHost(controller, context, chiPackEnabled)
    // Keyed on the character too: the toast state above is per character, and a host kept from
    // before a switch would keep writing into the previous character's toast.
    // The toast colours follow the theme the player has on at the moment of the change.
    val palette by rememberUpdatedState(FuryColors.current)
    val host = remember(baseHost, activeId) {
        SheetChangeReportingHost(baseHost) { change ->
            // Same wording and colour as the old Android sheet's «Последнее: …» bar.
            val (text, accent) = when (change) {
                is SheetChange.Resource -> recentResourceChange(change.resource, change.applied, baseHost.resourceMeters(), palette)
                is SheetChange.Experience -> change.text to palette.gold
                is SheetChange.Condition, is SheetChange.Attribute, SheetChange.Identity -> change.text to palette.acc
            }
            toast = SheetToast(text, accent, controller.canUndo, ++toastCount)
        }
    }
    LaunchedEffect(toast?.token) {
        if (toast != null) {
            delay(6000)
            toast = null
        }
    }
    Box(Modifier.fillMaxSize()) {
        SheetScreen(
            host,
            // Back from the editor returns to the play sheet instead of leaving the app.
            backHandler = { enabled, onBack -> BackHandler(enabled = enabled, onBack = onBack) },
            portraitLoader = { uri -> rememberAndroidPortrait(uri) },
            onPickPortrait = { portraitPicker.launch(arrayOf("image/*")) },
            openEditorAtBasics = openEditorAtBasics,
            onOpenEditorConsumed = onOpenEditorConsumed,
            onEditorShown = onEditorShown,
        )
        toast?.let { shown ->
            val undoShown: () -> Unit = {
                controller.undoLast()
                toast = null
            }
            RecentChangeBar(
                text = shown.text,
                accent = shown.accent,
                onUndo = undoShown.takeIf { shown.canUndo },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .background(DublSurface, RoundedCornerShape(9.dp)),
            )
        }
    }
}

