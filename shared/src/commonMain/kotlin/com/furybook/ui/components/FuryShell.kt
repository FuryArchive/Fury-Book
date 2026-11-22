package com.furybook.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.Layout
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import com.furybook.ui.account.AccountDot
import com.furybook.ui.account.AccountDotView
import com.furybook.ui.theme.DublAccent
import com.furybook.ui.theme.DublGold
import com.furybook.ui.theme.FuryMinTouch
import com.furybook.ui.theme.FuryShapes
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

/** The tabs drop to a row of their own only when their natural width exceeds the [room] beside the title. */
fun furyShellTabsOnOwnRow(tabsWidth: Int, room: Int): Boolean = tabsWidth > room

/** Reports [content]'s natural width without placing it (nothing drawn, nothing clickable). */
@Composable
private fun NaturalWidth(onWidth: (Int) -> Unit, content: @Composable () -> Unit) {
    val report by rememberUpdatedState(onWidth)
    Layout(content) { measurables, _ ->
        report(measurables.maxOfOrNull { it.maxIntrinsicWidth(Constraints.Infinity) } ?: 0)
        layout(0, 0) {}
    }
}

/**
 * The app frame. Wide: a top bar with the wordmark, tabs for every section, the account and the
 * character button. Phone: a slim top bar (wordmark, «Меню») and the content; the menu holds the
 * character switch, every section and the account. (Bottom tabs exist only for sections marked primary.)
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
    /** Extra top-bar controls (the theme button): next to the character button, or in the phone menu. */
    account: (@Composable () -> Unit)? = null,
    /** On phones, hide the wordmark bar (and any bottom tabs): a full-screen view (the character editor) is open. */
    phoneChromeHidden: Boolean = false,
    /** The Fury Account entry: a top-bar button on wide screens, a card in the phone menu. */
    accountEntry: FuryShellAccount? = null,
    content: @Composable () -> Unit,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        if (furyShellIsPhone(maxWidth.value.toInt())) {
            PhoneShell(title, characterName, sections, selected, onSelect, onSwitchCharacter, account, phoneChromeHidden, accountEntry, content)
        } else {
            WideShell(title, characterName, sections, selected, onSelect, onSwitchCharacter, account, accountEntry, content)
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
    account: (@Composable () -> Unit)?,
    accountEntry: FuryShellAccount?,
    content: @Composable () -> Unit,
) {
    var accountOpen by remember { mutableStateOf(false) }
    if (accountOpen && accountEntry != null) {
        FuryDialogScaffold(title = "Меню", subtitle = characterName, onDismiss = { accountOpen = false }, subtitleMaxLines = 1) {
            FuryButton("Сменить персонажа", { accountOpen = false; onSwitchCharacter() }, Modifier.fillMaxWidth())
            accountEntry.panel(this) { accountOpen = false }
        }
    }
    Column(Modifier.fillMaxSize()) {
        var tabsWidth by remember { mutableStateOf(0) }
        var room by remember { mutableStateOf(Int.MAX_VALUE) }
        val tabsOnOwnRow = furyShellTabsOnOwnRow(tabsWidth, room)
        val tabs: @Composable (Modifier) -> Unit = { m ->
            FuryTopTabs(
                tabs = sections.map { FuryTab(it.value, it.label) },
                selected = selected,
                onSelect = onSelect,
                modifier = m.horizontalScroll(rememberScrollState()),
            )
        }
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
            // Too narrow for all the tabs here (tablets, half-screen windows): they move to the row below.
            Box(Modifier.weight(1f).onSizeChanged { room = it.width }) {
                NaturalWidth({ tabsWidth = it }) { FuryTopTabs(sections.map { FuryTab(it.value, it.label) }, selected, onSelect) }
                if (!tabsOnOwnRow) tabs(Modifier)
            }
            Spacer(Modifier.width(FurySpacing.md))
            account?.let { slot -> slot(); Spacer(Modifier.width(FurySpacing.sm)) }
            accountEntry?.let { entry -> AccountTopButton(entry) { accountOpen = true }; Spacer(Modifier.width(FurySpacing.sm)) }
            FuryButton("$characterName ▾", onSwitchCharacter, Modifier.widthIn(max = 280.dp))
        }
        if (tabsOnOwnRow) {
            Box(Modifier.fillMaxWidth().background(DublSurface).padding(start = FurySpacing.lg, end = FurySpacing.lg, bottom = FurySpacing.sm)) { tabs(Modifier) }
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
    account: (@Composable () -> Unit)?,
    chromeHidden: Boolean,
    accountEntry: FuryShellAccount?,
    content: @Composable () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val menuSections = furyMenuSections(sections)
    // The content slot stays in one place whether or not the bars show: moving it would recreate its state.
    Column(Modifier.fillMaxSize()) {
        // Wordmark and «Меню» only: the sheet header already names the character (R7).
        if (!chromeHidden) Row(
            Modifier.fillMaxWidth().background(DublSurface).padding(horizontal = FurySpacing.lg, vertical = FurySpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(FurySpacing.md),
        ) {
            // Small text: plain crimson is 4.497:1 on the bar, the strong tint clears 4.5:1.
            Text(title, Modifier.weight(1f), color = DublAccentStrong, style = furyOverlineStyle(), maxLines = 1)
            Box {
                FuryIconButton(if (accountEntry?.dot == AccountDot.GOLD) "Меню: конфликт изменений" else "Меню", { menuOpen = true }) {
                    Text("☰", color = DublText, style = MaterialTheme.typography.titleMedium)
                }
                // A conflict waits in the account panel: a gold badge on the menu button.
                if (accountEntry?.dot == AccountDot.GOLD) {
                    Box(Modifier.align(Alignment.TopEnd).padding(4.dp).width(10.dp).height(10.dp).clip(androidx.compose.foundation.shape.CircleShape).background(DublGold))
                }
            }
        }
        if (!chromeHidden) Box(Modifier.fillMaxWidth().height(1.dp).background(DublBorder))
        Box(Modifier.weight(1f).fillMaxWidth()) { content() }
        // Since stage 5 every app passes no primary sections: phones get no bottom bar, everything sits in «Меню».
        if (!chromeHidden && furyBottomTabs(sections).isNotEmpty()) {
            FuryBottomTabs(
                tabs = furyBottomTabs(sections).map { FuryTab(it.value, it.bottomTabLabel) },
                selected = selected,
                onSelect = onSelect,
            )
        }
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
            account?.invoke()
            accountEntry?.panel?.invoke(this) { menuOpen = false }
        }
    }
}

/**
 * The shell's account entry: [label] is the nickname or «Гость», [dot] its sync state. [panel] is the
 * account card shown in the menu (phone) or behind the top-bar account button (wide); [close] shuts the menu.
 */
class FuryShellAccount(
    val label: String,
    val dot: AccountDot,
    val panel: @Composable ColumnScope.(close: () -> Unit) -> Unit,
)

/** Wide screens: the nickname (or «Гость») with its sync dot, opening the menu with the account panel. */
@Composable
private fun AccountTopButton(account: FuryShellAccount, onClick: () -> Unit) {
    Row(
        Modifier.defaultMinSize(minHeight = FuryMinTouch).widthIn(max = 240.dp).clip(FuryShapes.control)
            .border(2.dp, com.furybook.ui.theme.FuryBorderStrong, FuryShapes.control)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(FurySpacing.sm),
    ) {
        AccountDotView(account.dot)
        Text(account.label, color = DublText, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, maxLines = 1)
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
