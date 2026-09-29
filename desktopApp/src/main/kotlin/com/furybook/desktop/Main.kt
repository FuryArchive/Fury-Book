package com.furybook.desktop

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.furybook.ui.layout.DublLayoutClass
import com.furybook.ui.layout.layoutClassForWidth
import com.furybook.ui.theme.DublTheme
import com.furybook.desktop.screens.CharacterSheetScreen
import com.furybook.desktop.screens.CharactersScreen
import com.furybook.desktop.screens.DevelopmentScreen
import com.furybook.desktop.screens.DesktopIcon
import com.furybook.desktop.screens.DesktopIconKind
import com.furybook.desktop.screens.DesktopAccent
import com.furybook.desktop.screens.DesktopAccentSoft
import com.furybook.desktop.screens.DesktopBackground
import com.furybook.desktop.screens.DesktopBorder
import com.furybook.desktop.screens.DesktopGold
import com.furybook.desktop.screens.DesktopMuted
import com.furybook.desktop.screens.DesktopSurface
import com.furybook.desktop.screens.DesktopSurfaceInset
import com.furybook.desktop.screens.DesktopSurfaceRaised
import com.furybook.desktop.screens.DesktopText
import com.furybook.desktop.screens.EquipmentScreen
import com.furybook.desktop.screens.FuryMotion
import com.furybook.desktop.screens.MagicScreen
import com.furybook.desktop.screens.SkillsScreen
import com.furybook.desktop.cloud.DesktopCloudController
import com.furybook.core.cloud.ObservingCharacterStore
import com.furybook.core.cloud.ObservingCharacterExtrasStore
import com.furybook.dubl.data.DesktopCharacterStore
import com.furybook.dubl.data.DesktopCharacterExtrasStore

internal enum class DesktopSection(val label: String) {
    SHEET("Лист"),
    SKILLS("Умения"),
    DEVELOPMENT("Навыки"),
    MAGIC("Магия"),
    EQUIPMENT("Снаряжение"),
    CHARACTERS("Персонажи"),
}

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Fury Book — DUBL 3.69",
        icon = painterResource("fury-icon.svg"),
    ) {
        DublTheme { DesktopVisualTheme { DesktopApp() } }
    }
}

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
        cloudController.setChiEnabled(state.chiPackEnabled)
        cloudController.restoreAndSync()
    }
    LaunchedEffect(state.chiPackEnabled) {
        cloudController.setChiEnabled(state.chiPackEnabled)
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val layout = layoutClassForWidth(maxWidth.value.toInt())
        if (layout == DublLayoutClass.COMPACT) {
            Column(Modifier.fillMaxSize()) {
                CompactNavigation(selected, { selected = it })
                DesktopContent(state, cloudController, selected, layout, { selected = it }, Modifier.weight(1f))
            }
        } else {
            Row(Modifier.fillMaxSize()) {
                DesktopRail(state, selected, { selected = it }, Modifier.width(230.dp).fillMaxHeight())
                DesktopContent(state, cloudController, selected, layout, { selected = it }, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun DesktopRail(
    state: DesktopAppState,
    selected: DesktopSection,
    onSelected: (DesktopSection) -> Unit,
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
        Text(
            "DUBL 3.69",
            color = DesktopMuted,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
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
        NavigationItem(DesktopSection.CHARACTERS,
            DesktopSection.CHARACTERS == selected,
            { onSelected(DesktopSection.CHARACTERS) },
            Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun CompactNavigation(selected: DesktopSection, onSelected: (DesktopSection) -> Unit) {
    Column(Modifier.fillMaxWidth().background(DesktopSurfaceInset).padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        DesktopSection.entries.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { section -> NavigationItem(section, section == selected, { onSelected(section) }, Modifier.weight(1f)) }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
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
            DesktopIcon(
                kind = section.iconKind,
                tint = iconColor,
                size = 20.dp,
            )
            Text(
                section.label,
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                color = textColor,
            )
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
                DesktopSection.CHARACTERS -> CharactersScreen(state, cloudController, animatedPageModifier)
            }
        }
    }
}
