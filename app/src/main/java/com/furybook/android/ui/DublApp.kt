package com.furybook.android.ui

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
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
import com.furybook.android.ui.screens.CharactersScreen
import com.furybook.android.ui.screens.CampaignScreen
import com.furybook.android.ui.screens.EquipmentScreen
import com.furybook.android.ui.screens.FeatsScreen
import com.furybook.android.ui.screens.MagicScreen
import com.furybook.android.ui.screens.SkillsScreen
import com.furybook.dubl.content.DublChiFcp
import com.furybook.android.ui.screens.RecentChangeBar
import com.furybook.android.ui.screens.recentResourceChange
import com.furybook.dubl.ui.sheet.SheetChange
import com.furybook.dubl.ui.sheet.SheetChangeReportingHost
import com.furybook.dubl.ui.sheet.SheetDevelopmentDetailsDialog
import com.furybook.dubl.ui.sheet.SheetScreen
import com.furybook.ui.components.FuryButton
import com.furybook.ui.components.FuryButtonStyle
import com.furybook.ui.components.FuryCharacterPicker
import com.furybook.ui.components.FuryShell
import com.furybook.ui.components.FuryShellCharacter
import com.furybook.ui.components.FuryShellSection
import com.furybook.ui.theme.DublAccent
import com.furybook.ui.theme.DublGold
import com.furybook.ui.theme.DublFocus
import com.furybook.ui.theme.DublMuted
import com.furybook.ui.theme.DublSurface
import com.furybook.ui.theme.DublSurfaceInset
import kotlinx.coroutines.delay

private enum class AppSection(val label: String) {
    OVERVIEW("Лист"),
    SKILLS("Умения"),
    FEATS("Навыки"),
    MAGIC("Магия"),
    INVENTORY("Вещи"),
    CAMPAIGN("Кампания"),
    MORE("Ещё"),
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
    val controller = remember(appContext, reloadToken) {
        CharacterController(observingCharacters, observingExtras)
    }
    var selected by rememberSaveable { mutableStateOf(AppSection.OVERVIEW) }
    var pendingSection by remember { mutableStateOf<AppSection?>(null) }
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

    LaunchedEffect(pendingSection) {
        if (pendingSection == AppSection.FEATS) {
            // Commit at least one lightweight frame before mounting the heavy development screen.
            withFrameNanos { }
            withFrameNanos { }
            selected = AppSection.FEATS
            pendingSection = null
        }
    }

    var characterPickerOpen by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.dismissKeyboardOnPointerDown(),
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        FuryShell(
            title = "FURY BOOK",
            characterName = controller.active.name,
            sections = AppSection.entries.map { FuryShellSection(it, it.label, primary = it.primary) },
            selected = pendingSection ?: selected,
            onSelect = { target ->
                if (pendingSection == null && target != selected) {
                    when (target) {
                        AppSection.FEATS -> pendingSection = AppSection.FEATS
                        else -> selected = target
                    }
                }
            },
            onSwitchCharacter = { characterPickerOpen = true },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            if (pendingSection == AppSection.FEATS) {
                DevelopmentNavigationLoadingScreen()
            } else {
                when (selected) {
                    AppSection.OVERVIEW -> AndroidSheet(
                        controller = controller,
                        chiPackEnabled = chiPackEnabled,
                        onOpenSkills = { selected = AppSection.SKILLS },
                        // Same loading gate as the tab: the development screen is heavy to compose.
                        onOpenDevelopment = { if (pendingSection == null) pendingSection = AppSection.FEATS },
                    )
                    AppSection.SKILLS -> SkillsScreen(controller)
                    AppSection.FEATS -> FeatsScreen(controller, chiPackEnabled)
                    AppSection.MAGIC -> MagicScreen(controller)
                    AppSection.INVENTORY -> EquipmentScreen(controller)
                    AppSection.CAMPAIGN -> CampaignScreen(
                        controller = controller,
                        cloudController = cloudController,
                        onBack = { selected = AppSection.MORE },
                    )
                    AppSection.MORE -> CharactersScreen(
                        controller = controller,
                        cloudController = cloudController,
                        contentPackComposition = AndroidContentPackState.composition(appContext, chiPackEnabled),
                        onContentPackActiveChange = { packId, enabled ->
                            AndroidContentPackState.setPackEnabled(appContext, packId, enabled)
                            if (packId == DublChiFcp.PACK_ID) chiPackEnabled = enabled
                            val nextIds = cloudController.enabledPackIds.toMutableSet().apply {
                                if (enabled) add(packId) else remove(packId)
                            }
                            cloudController.updateEnabledPackIds(nextIds)
                            cloudController.requestSync()
                        },
                        onOpenCampaign = { selected = AppSection.CAMPAIGN },
                    )
                }
            }
        }
    }

    if (characterPickerOpen) {
        FuryCharacterPicker(
            characters = controller.snapshot.characters.map { FuryShellCharacter(it.id, it.name) },
            activeId = controller.snapshot.activeCharacterId,
            onSelect = { id ->
                controller.selectCharacter(id)
                pendingSection = null
                selected = AppSection.OVERVIEW
            },
            onManage = {
                pendingSection = null
                selected = AppSection.MORE
            },
            onDismiss = { characterPickerOpen = false },
        )
    }
}

/** Phones show the five play sections as bottom tabs; campaign and «Ещё» sit behind «Меню». */
private val AppSection.primary: Boolean
    get() = this != AppSection.CAMPAIGN && this != AppSection.MORE

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
    onOpenSkills: () -> Unit,
    onOpenDevelopment: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
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
    val host = remember(baseHost, activeId) {
        SheetChangeReportingHost(baseHost) { change ->
            // Same wording and colour as the old Android sheet's «Последнее: …» bar.
            val (text, accent) = when (change) {
                is SheetChange.Resource -> recentResourceChange(change.resource, change.applied, baseHost.resourceMeters())
                is SheetChange.Experience -> change.text to DublGold
                is SheetChange.Condition, is SheetChange.Attribute, SheetChange.Identity -> change.text to DublAccent
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
            onOpenSkills = onOpenSkills,
            onOpenDevelopment = onOpenDevelopment,
            // Back from edit mode returns to the play sheet instead of leaving the app.
            backHandler = { enabled, onBack -> BackHandler(enabled = enabled, onBack = onBack) },
            portraitLoader = { uri -> rememberAndroidPortrait(uri) },
            onPickPortrait = { portraitPicker.launch(arrayOf("image/*")) },
            developmentDetails = { entryId, close -> SheetDevelopmentDetailsDialog(host, entryId, close) },
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

@Composable
private fun DevelopmentNavigationLoadingScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = DublSurfaceInset,
            border = BorderStroke(1.dp, DublFocus.copy(alpha = 0.24f)),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(34.dp),
                    color = DublFocus,
                    strokeWidth = 3.dp,
                )
                Text(
                    text = "Подготавливаем навыки",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Загружаем каталог, требования и зависимости…",
                    fontSize = 13.sp,
                    color = DublMuted,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
