package com.furybook.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.furybook.ui.theme.DublAccent
import com.furybook.ui.theme.DublAccentStrong
import com.furybook.ui.theme.DublBorder
import com.furybook.ui.theme.DublSurface
import com.furybook.ui.theme.DublText
import com.furybook.ui.theme.FurySpacing
import com.furybook.ui.theme.furyOverlineStyle

/**
 * One destination of the app shell. [primary] sections are the phone's bottom tabs; [phoneLabel]
 * is a shorter name for a fifth of a phone's width (wide tabs and the menu use [label]).
 */
data class FuryShellSection<T>(val value: T, val label: String, val primary: Boolean, val phoneLabel: String? = null) {
    val bottomTabLabel: String get() = phoneLabel ?: label
}

private const val MAX_BOTTOM_TABS = 5
private const val PHONE_MAX_WIDTH_DP = 600

/** The phone's bottom tabs: the primary sections in order, at most five. */
fun <T> furyBottomTabs(sections: List<FuryShellSection<T>>): List<FuryShellSection<T>> =
    sections.filter { it.primary }.take(MAX_BOTTOM_TABS)

/** Everything that is not a bottom tab sits behind the phone's «Меню» button, so nothing is unreachable. */
fun <T> furyMenuSections(sections: List<FuryShellSection<T>>): List<FuryShellSection<T>> {
    val tabs = furyBottomTabs(sections)
    return sections.filter { it !in tabs }
}

/** Same breakpoint as the sheet: below 600dp the shell is a phone shell. */
fun furyShellIsPhone(widthDp: Int): Boolean = widthDp < PHONE_MAX_WIDTH_DP

/**
 * The app frame. Wide: a top bar with the wordmark, tabs for every section and the character
 * button. Phone: a slim top bar (wordmark, «Меню»), the content, and five bottom tabs; the
 * menu holds the character switch and the remaining sections.
 */
@Composable
fun <T> FuryShell(
    title: String,
    characterName: String,
    sections: List<FuryShellSection<T>>,
    selected: T,
    onSelect: (T) -> Unit,
    onSwitchCharacter: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        if (furyShellIsPhone(maxWidth.value.toInt())) {
            PhoneShell(title, characterName, sections, selected, onSelect, onSwitchCharacter, content)
        } else {
            WideShell(title, characterName, sections, selected, onSelect, onSwitchCharacter, content)
        }
    }
}

@Composable
private fun <T> WideShell(
    title: String,
    characterName: String,
    sections: List<FuryShellSection<T>>,
    selected: T,
    onSelect: (T) -> Unit,
    onSwitchCharacter: () -> Unit,
    content: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().background(DublSurface).padding(horizontal = FurySpacing.xl, vertical = FurySpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                title,
                color = DublAccent,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
            Spacer(Modifier.width(FurySpacing.xl))
            // Seven tabs do not fit a 600dp window next to the wordmark; they scroll rather than clip.
            Box(Modifier.weight(1f)) {
                FuryTopTabs(
                    tabs = sections.map { FuryTab(it.value, it.label) },
                    selected = selected,
                    onSelect = onSelect,
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                )
            }
            Spacer(Modifier.width(FurySpacing.md))
            FuryButton("$characterName ▾", onSwitchCharacter, Modifier.widthIn(max = 280.dp))
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(DublBorder))
        Box(Modifier.weight(1f).fillMaxWidth()) { content() }
    }
}

@Composable
private fun <T> PhoneShell(
    title: String,
    characterName: String,
    sections: List<FuryShellSection<T>>,
    selected: T,
    onSelect: (T) -> Unit,
    onSwitchCharacter: () -> Unit,
    content: @Composable () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val menuSections = furyMenuSections(sections)
    Column(Modifier.fillMaxSize()) {
        // Wordmark and «Меню» only: the sheet header already names the character (R7).
        Row(
            Modifier.fillMaxWidth().background(DublSurface).padding(horizontal = FurySpacing.lg, vertical = FurySpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(FurySpacing.md),
        ) {
            // Small text: plain crimson is 4.497:1 on the bar, the strong tint clears 4.5:1.
            Text(title, Modifier.weight(1f), color = DublAccentStrong, style = furyOverlineStyle(), maxLines = 1)
            FuryIconButton("Меню", { menuOpen = true }) {
                Text("☰", color = DublText, style = MaterialTheme.typography.titleMedium)
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(DublBorder))
        Box(Modifier.weight(1f).fillMaxWidth()) { content() }
        FuryBottomTabs(
            tabs = furyBottomTabs(sections).map { FuryTab(it.value, it.bottomTabLabel) },
            selected = selected,
            onSelect = onSelect,
        )
    }
    if (menuOpen) {
        FuryDialogScaffold(title = "Меню", subtitle = characterName, onDismiss = { menuOpen = false }, subtitleMaxLines = 1) {
            FuryButton(
                text = "Сменить персонажа",
                onClick = { menuOpen = false; onSwitchCharacter() },
                modifier = Modifier.fillMaxWidth(),
            )
            menuSections.forEach { section ->
                FuryButton(
                    text = section.label,
                    onClick = { menuOpen = false; onSelect(section.value) },
                    modifier = Modifier.fillMaxWidth(),
                    style = if (section.value == selected) FuryButtonStyle.Primary else FuryButtonStyle.Secondary,
                )
            }
        }
    }
}

data class FuryShellCharacter(val id: String, val name: String)

/** Quick character switch for the shell's character button, with a way to the full characters screen. */
@Composable
fun FuryCharacterPicker(
    characters: List<FuryShellCharacter>,
    activeId: String,
    onSelect: (String) -> Unit,
    onManage: () -> Unit,
    onDismiss: () -> Unit,
    manageLabel: String = "Управление персонажами…",
) {
    FuryDialogScaffold(title = "Персонаж", onDismiss = onDismiss) {
        characters.forEach { character ->
            val active = character.id == activeId
            FuryButton(
                text = if (active) "✓ ${character.name}" else character.name,
                onClick = { onSelect(character.id); onDismiss() },
                modifier = Modifier.fillMaxWidth(),
                style = if (active) FuryButtonStyle.Primary else FuryButtonStyle.Secondary,
            )
        }
        FuryButton(
            text = manageLabel,
            onClick = { onDismiss(); onManage() },
            modifier = Modifier.fillMaxWidth(),
            style = FuryButtonStyle.Ghost,
        )
    }
}
