package com.furybook.dubl.ui.sheet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import com.furybook.dubl.ui.editor.DublEditorHost
import com.furybook.dubl.ui.editor.EditorScreen
import com.furybook.dubl.ui.editor.EditorState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.furybook.dubl.model.AttributeId
import com.furybook.dubl.model.CharacterConditionId
import com.furybook.dubl.model.CharacterEconomy
import com.furybook.dubl.model.resolveSkill
import com.furybook.dubl.model.CharacterEconomyBreakdown
import com.furybook.dubl.model.displayNotes
import com.furybook.dubl.model.SheetGroupingRules
import com.furybook.ui.components.FuryDialogAction
import com.furybook.ui.components.FuryDialogScaffold
import com.furybook.ui.components.FuryRollTray
import com.furybook.ui.theme.DublMuted
import com.furybook.ui.theme.FurySpacing
import com.furybook.ui.theme.furyOverlineStyle

enum class SheetLayout { PHONE, WIDE, WIDE_WITH_TRAY }

/** Below 600dp a phone layout; from 1100dp the roll tray docks on the right. */
fun sheetLayoutFor(widthDp: Int): SheetLayout = when {
    widthDp < 600 -> SheetLayout.PHONE
    widthDp < 1100 -> SheetLayout.WIDE
    else -> SheetLayout.WIDE_WITH_TRAY
}

private val TRAY_WIDTH = 340.dp

/**
 * The character sheet: the play view, or the character editor while ✎ is on. Owns roll state,
 * the open popup and the mode; rolls open in a bottom sheet (phone), a centred dialog (wide) or the
 * docked tray. Platform pieces come in as callbacks: portrait loading/picking and development details.
 */
@Composable
fun SheetScreen(
    host: DublEditorHost,
    modifier: Modifier = Modifier,
    portraitLoader: SheetPortraitLoader? = null,
    onPickPortrait: (() -> Unit)? = null,
    /** Details of an owned development entry; platform-owned until stage 4 rebuilds Навыки. */
    developmentDetails: (@Composable (entryId: String, onDismiss: () -> Unit) -> Unit)? = null,
    /** Android passes its BackHandler so system Back closes the editor instead of leaving the app. */
    backHandler: @Composable (enabled: Boolean, onBack: () -> Unit) -> Unit = { _, _ -> },
    /** Set right after «Новый персонаж»: open the editor at Основа, then [onOpenEditorConsumed]. */
    openEditorAtBasics: Boolean = false,
    onOpenEditorConsumed: () -> Unit = {},
    /** Tells the shell when the editor covers the sheet (the phone bottom nav hides). */
    onEditorShown: (Boolean) -> Unit = {},
    onOpenMagic: () -> Unit = {},
    onOpenGear: () -> Unit = {},
) {
    val state = remember { SheetScreenState() }
    state.syncCharacter(host.active.id)
    state.syncHost(host)
    val playState = remember { SheetPlayState() }
    playState.syncCharacter(host.active.id)
    val editorState = remember { EditorState() }
    backHandler(state.editing) { state.updateEditing(false) }
    // A fresh snapshot each time the editor opens, taken in the click/effect that opens it.
    val currentHost by rememberUpdatedState(host)
    SideEffect { state.onEditorOpening = { forceBasics -> editorState.open(currentHost, forceBasics) } }
    LaunchedEffect(openEditorAtBasics) {
        if (openEditorAtBasics) {
            state.openEditor(forceBasics = true)
            onOpenEditorConsumed()
        }
    }
    LaunchedEffect(state.editing) { onEditorShown(state.editing) }
    // The editor reports its own changes; the platform's undo toasts stay quiet while it is open.
    SideEffect { (host as? SheetChangeReportingHost)?.muted = state.editing }
    BoxWithConstraints(modifier.fillMaxSize()) {
        val layout = sheetLayoutFor(maxWidth.value.toInt())
        if (state.editing) {
            EditorScreen(
                host, editorState, onClose = { state.updateEditing(false) },
                portraitLoader = portraitLoader, onPickPortrait = onPickPortrait, onOpenMagic = onOpenMagic, onOpenGear = onOpenGear,
            )
            return@BoxWithConstraints
        }
        val portrait: (@Composable (Dp) -> Unit)? =
            if (portraitLoader == null && host.extras.portraitUri == null) null else { size ->
                SheetPortrait(uri = host.extras.portraitUri, name = host.active.name, loader = portraitLoader, onPick = null, onRemove = null, size = size)
            }
        SheetPlayBody(host, layout, state, playState, portrait, TRAY_WIDTH) { rollModifier -> SheetScreenRoll(host, state, rollModifier) }
        SheetScreenRollDialog(host, layout, state)
        SheetScreenDialog(host, layout, state, developmentDetails)
    }
}

/** Phone and tablet roll: the dialog with the roll options; desktop shows the same panel in its tray. */
@Composable
private fun SheetScreenRollDialog(host: DublEditorHost, layout: SheetLayout, state: SheetScreenState) {
    val dialogRequest = state.request
    if (layout != SheetLayout.WIDE_WITH_TRAY && state.rollOpen && dialogRequest != null) {
        // FuryDialogScaffold scrolls its content and picks sheet or centred panel by width. The roll
        // button sits in its pinned footer, so «Бросить снова» stays in view under a long result.
        val blocked = sheetRollOptions(host, dialogRequest).blockedReason
        FuryDialogScaffold(
            title = "Бросок",
            onDismiss = state::dismiss,
            primary = FuryDialogAction(sheetRollButtonLabel(state.result != null), { state.roll(host) }, enabled = blocked == null),
        ) {
            SheetScreenRoll(host, state, Modifier.fillMaxWidth(), inlineRollButton = false)
        }
    }
}

/** The popup [SheetScreenState.dialog] names, if any. */
@Composable
private fun SheetScreenDialog(
    host: DublSheetEditHost,
    layout: SheetLayout,
    state: SheetScreenState,
    developmentDetails: (@Composable (String, () -> Unit) -> Unit)?,
) {
    val close = state::closeDialog
    when (val dialog = state.dialog) {
        null -> Unit
        is SheetDialog.Vital -> sheetVitals(host).firstOrNull { it.id == dialog.vitalId }
            ?.let { vital -> SheetVitalDialog(vital, host, close) } ?: close()
        is SheetDialog.Stat -> SheetStatDialog(
            breakdown = sheetStatBreakdown(host.active, dialog.stat),
            onRoll = dialog.roll?.let { subject -> { state.open(subject, dialog = layout != SheetLayout.WIDE_WITH_TRAY) } },
            sizeLegsEditor = dialog.stat == SheetStatId.SIZE,
            host = host,
            onDismiss = close,
        )
        SheetDialog.Conditions -> SheetConditionsDialog(host, close)
        SheetDialog.Economy -> SheetEconomyDialog(sheetEconomyView(host), host, close)
        SheetDialog.Resources -> SheetResourcesDialog(host, close)
        is SheetDialog.DevelopmentDetails ->
            developmentDetails?.invoke(dialog.entryId, close) ?: SheetDevelopmentDetailsDialog(host, dialog.entryId, close)
    }
}

/** Chi XP counts only when a pack mounts the chi feature on the economy XP line (FCP contract). */
@Composable
internal fun rememberSheetEconomy(host: DublSheetEditHost): CharacterEconomyBreakdown {
    val character = host.active
    val includeChi = sheetChiEconomyMounted(host)
    return remember(host, character, host.extras, includeChi, host.developmentCatalog) {
        CharacterEconomy.breakdown(character, host.developmentCatalog, includeChi = includeChi)
    }
}

/** The roll panel for the current setup, or the tray's empty state before anything was tapped. */
@Composable
private fun SheetScreenRoll(host: DublEditorHost, state: SheetScreenState, modifier: Modifier, inlineRollButton: Boolean = true) {
    val request = state.request
    if (request == null) {
        FuryRollTray(view = null, history = state.history, onRollAgain = {}, modifier = modifier)
        return
    }
    SheetRollPanel(
        host = host,
        request = request,
        onRequestChange = state::updateRequest,
        result = state.result,
        onRoll = { state.roll(host) },
        onFollowUp = { state.followUp() },
        history = state.history,
        modifier = modifier,
        showRollButton = inlineRollButton,
        onAttributePicked = { skillId, attribute ->
            val skill = host.active.resolveSkill(skillId)
            if (skill != null && sheetRemembersAttribute(skill, attribute)) host.setPreferredSkillAttribute(skillId, attribute)
        },
    )
}
