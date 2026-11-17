package com.furybook.desktop

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.zIndex
import com.furybook.ui.components.FuryButton
import com.furybook.ui.components.FuryButtonStyle
import com.furybook.ui.components.FuryCharacterPicker
import com.furybook.ui.components.FuryShell
import com.furybook.ui.components.FuryShellCharacter
import com.furybook.ui.components.FuryShellSection
import com.furybook.ui.layout.DublLayoutClass
import com.furybook.ui.layout.layoutClassForWidth
import com.furybook.ui.theme.DublTheme
import com.furybook.desktop.screens.CharacterSheetScreen
import com.furybook.desktop.screens.DesktopSheetDevelopmentDetails
import com.furybook.desktop.screens.CampaignScreen
import com.furybook.desktop.screens.CharactersScreen
import com.furybook.desktop.screens.DevelopmentScreen
import com.furybook.desktop.screens.EquipmentScreen
import com.furybook.desktop.screens.FuryMotion
import com.furybook.desktop.screens.FuryUndoToast
import com.furybook.desktop.screens.MagicScreen
import com.furybook.desktop.screens.SkillsScreen
import com.furybook.desktop.cloud.DesktopCloudController
import com.furybook.core.cloud.ObservingCharacterStore
import com.furybook.core.cloud.ObservingCharacterExtrasStore
import com.furybook.dubl.data.DesktopCharacterStore
import com.furybook.dubl.data.DesktopCharacterExtrasStore
import com.furybook.dubl.ui.sheet.SheetChangeReportingHost
import com.furybook.dubl.ui.sheet.SheetScreen
import kotlinx.coroutines.delay

internal enum class DesktopSection(val label: String) {
    SHEET("Лист"),
    SKILLS("Умения"),
    DEVELOPMENT("Навыки"),
    MAGIC("Магия"),
    EQUIPMENT("Снаряжение"),
    CAMPAIGN("Кампания"),
    CHARACTERS("Персонажи"),
}

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Fury Book — DUBL 3.69",
        icon = painterResource("fury-icon.png"),
    ) {
        DublTheme { DesktopVisualTheme { DesktopApp() } }
    }
}

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
private fun DesktopApp() {
    val baseCharacters = remember { DesktopCharacterStore() }
    val baseExtras = remember { DesktopCharacterExtrasStore() }
    val observedCharacters = remember(baseCharacters) { ObservingCharacterStore(baseCharacters) }
    val observedExtras = remember(baseExtras) { ObservingCharacterExtrasStore(baseExtras) }
    val cloudController = remember(baseCharacters, baseExtras) {
        DesktopCloudController(baseCharacters, baseExtras).also {
            it.bind(observedCharacters, observedExtras)
        }
    }
    val reloadToken = cloudController.reloadToken
    val state = remember(reloadToken) { DesktopAppState(observedCharacters, observedExtras) }
    var selected by remember { mutableStateOf(DesktopSection.SHEET) }

    LaunchedEffect(cloudController) {
        cloudController.updateEnabledPackIds(state.enabledContentPackIds())
        cloudController.restoreAndSync()
        state.applyCloudContentPackState(cloudController.enabledPackIds)
    }
    LaunchedEffect(state.chiPackEnabled) {
        val availableIds = state.contentPackComposition.available.mapTo(linkedSetOf()) { it.id }
        val activeIds = state.enabledContentPackIds()
        cloudController.updateEnabledPackIds((cloudController.enabledPackIds - availableIds) + activeIds)
    }

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
            DesktopContent(state, cloudController, selected, layout, { selected = it }, Modifier.fillMaxSize())
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

/** Phones show the five play sections as bottom tabs; campaign and characters sit behind «Меню». */
private val DesktopSection.primary: Boolean
    get() = this != DesktopSection.CAMPAIGN && this != DesktopSection.CHARACTERS

/** «Снаряжение» does not fit a fifth of a phone; the phone tab uses the spec's «Вещи». */
private val DesktopSection.phoneLabel: String?
    get() = if (this == DesktopSection.EQUIPMENT) "Вещи" else null

@Composable
private fun DesktopContent(
    state: DesktopAppState,
    cloudController: DesktopCloudController,
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
                    DublLayoutClass.COMPACT -> 16.dp
                    DublLayoutClass.NORMAL -> 24.dp
                    DublLayoutClass.WIDE -> 28.dp
                },
                vertical = if (layout == DublLayoutClass.COMPACT) 14.dp else 22.dp,
            )
        key(section) {
            var entered by remember(section) { mutableStateOf(false) }
            LaunchedEffect(section) { entered = true }
            val pageProgress by animateFloatAsState(
                targetValue = if (entered) 1f else 0f,
                animationSpec = tween(FuryMotion.FastMs),
                label = "desktop-section-enter",
            )
            val enter = Modifier.graphicsLayer {
                alpha = pageProgress
                translationY = (1f - pageProgress) * 6f
            }
            val animatedPageModifier = pageModifier.then(enter)
            when (section) {
                // The shared sheet pads itself and picks its layout from the full content width.
                DesktopSection.SHEET -> DesktopSheet(
                    state = state,
                    onNavigate = onNavigate,
                    playModifier = Modifier.widthIn(max = 2160.dp).fillMaxSize().then(enter),
                )
                DesktopSection.SKILLS -> SkillsScreen(state, animatedPageModifier)
                DesktopSection.DEVELOPMENT -> DevelopmentScreen(state, animatedPageModifier)
                DesktopSection.MAGIC -> MagicScreen(state, animatedPageModifier)
                DesktopSection.EQUIPMENT -> EquipmentScreen(state, animatedPageModifier)
                DesktopSection.CAMPAIGN -> CampaignScreen(state, cloudController, animatedPageModifier)
                DesktopSection.CHARACTERS -> CharactersScreen(state, cloudController, animatedPageModifier)
            }
        }
    }
}

private data class SheetToast(val text: String, val canUndo: Boolean, val token: Int)

/**
 * The shared sheet (play and edit). Changes made on it raise the same
 * undo toast the old sheet showed, and «Отменить» calls the same one-step [DesktopAppState.undoLast].
 */
@Composable
private fun DesktopSheet(
    state: DesktopAppState,
    onNavigate: (DesktopSection) -> Unit,
    playModifier: Modifier,
) {
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
            onOpenSkills = { onNavigate(DesktopSection.SKILLS) },
            onOpenDevelopment = { onNavigate(DesktopSection.DEVELOPMENT) },
            portraitLoader = { path -> rememberDesktopPortrait(path) },
            developmentDetails = { entryId, close -> DesktopSheetDevelopmentDetails(state, entryId, close) },
            onPickPortrait = { chooseDesktopPortraitFile()?.let(state::importPortrait)?.let(state::setPortrait) },
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
