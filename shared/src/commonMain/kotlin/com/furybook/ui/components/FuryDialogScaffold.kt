package com.furybook.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.furybook.ui.layout.DublLayoutClass
import com.furybook.ui.layout.layoutClassForWidth
import com.furybook.ui.theme.DublMuted
import com.furybook.ui.theme.DublSurface
import com.furybook.ui.theme.DublText
import com.furybook.ui.theme.FuryBorderStrong
import com.furybook.ui.theme.FuryShapes
import com.furybook.ui.theme.FurySpacing

enum class FuryDialogPresentation { Centered, BottomSheet }

private const val BOTTOM_SHEET_MAX_WIDTH_DP = 600

/**
 * Phones get a sheet rising from the bottom edge, within thumb reach; anything wider gets a
 * centred panel. A narrow desktop window is still COMPACT but should not get a phone sheet,
 * so the decision is made on width alone.
 */
@Suppress("UNUSED_PARAMETER")
fun furyDialogPresentation(layout: DublLayoutClass, widthDp: Int): FuryDialogPresentation =
    if (widthDp < BOTTOM_SHEET_MAX_WIDTH_DP) FuryDialogPresentation.BottomSheet else FuryDialogPresentation.Centered

/** On a phone-width sheet three buttons do not fit on one line, so they stack with the primary on top. */
fun furyDialogStacksActions(presentation: FuryDialogPresentation): Boolean =
    presentation == FuryDialogPresentation.BottomSheet

data class FuryDialogAction(val label: String, val onClick: () -> Unit, val enabled: Boolean = true)

/**
 * The one frame every popup uses: title with an optional line of context, a close button,
 * scrolling content, and a footer with the destructive action set apart on the left and a
 * single filled primary action on the right.
 */
@Composable
fun FuryDialogScaffold(
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    primary: FuryDialogAction? = null,
    secondary: FuryDialogAction? = null,
    destructive: FuryDialogAction? = null,
    maxWidth: Dp = 560.dp,
    subtitleMaxLines: Int = Int.MAX_VALUE,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val widthDp = this.maxWidth.value.toInt()
            val sheet = furyDialogPresentation(layoutClassForWidth(widthDp), widthDp) == FuryDialogPresentation.BottomSheet
            val availableHeight = this.maxHeight
            // Scrim: tapping outside the panel dismisses.
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f))
                    .pointerInput(onDismiss) { detectTapGestures(onTap = { onDismiss() }) },
            )
            val shape = if (sheet) FuryShapes.sheet else FuryShapes.panel
            Column(
                modifier = modifier
                    .align(if (sheet) Alignment.BottomCenter else Alignment.Center)
                    .then(if (sheet) Modifier.fillMaxWidth() else Modifier.padding(FurySpacing.xl).widthIn(max = maxWidth).fillMaxWidth())
                    .heightIn(max = availableHeight * 0.9f)
                    .clip(shape)
                    .background(DublSurface)
                    .border(1.dp, FuryBorderStrong, shape)
                    // Swallow taps so they do not reach the scrim, without merging the panel's
                    // content into a single clickable node for screen readers.
                    .pointerInput(Unit) { detectTapGestures(onTap = {}) }
                    .padding(FurySpacing.xl),
                verticalArrangement = Arrangement.spacedBy(FurySpacing.lg),
            ) {
                if (sheet) {
                    Box(
                        Modifier.align(Alignment.CenterHorizontally).width(44.dp).height(4.dp)
                            .clip(RoundedCornerShape(2.dp)).background(FuryBorderStrong),
                    )
                }
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(FurySpacing.md)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(title, color = DublText, style = MaterialTheme.typography.titleLarge)
                        if (!subtitle.isNullOrBlank()) {
                            Text(
                                subtitle, color = DublMuted, style = MaterialTheme.typography.bodyMedium,
                                maxLines = subtitleMaxLines, overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    FuryIconButton("Закрыть", onDismiss) {
                        Text("✕", color = DublMuted, style = MaterialTheme.typography.titleMedium)
                    }
                }
                Column(
                    modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(FurySpacing.lg),
                    content = content,
                )
                if (primary != null || secondary != null || destructive != null) {
                    val presentation = if (sheet) FuryDialogPresentation.BottomSheet else FuryDialogPresentation.Centered
                    if (furyDialogStacksActions(presentation)) {
                        Column(verticalArrangement = Arrangement.spacedBy(FurySpacing.sm)) {
                            if (primary != null) {
                                FuryButton(primary.label, primary.onClick, Modifier.fillMaxWidth(), FuryButtonStyle.Primary, primary.enabled)
                            }
                            if (secondary != null) {
                                FuryButton(secondary.label, secondary.onClick, Modifier.fillMaxWidth(), FuryButtonStyle.Secondary, secondary.enabled)
                            }
                            if (destructive != null) {
                                FuryButton(destructive.label, destructive.onClick, Modifier.fillMaxWidth(), FuryButtonStyle.Danger, destructive.enabled)
                            }
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(FurySpacing.sm)) {
                            // The left slot takes whatever width the right-hand buttons leave, so a long
                            // destructive label is the one that ellipsizes, never the primary button.
                            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                                if (destructive != null) {
                                    FuryButton(destructive.label, destructive.onClick, style = FuryButtonStyle.Danger, enabled = destructive.enabled)
                                }
                            }
                            if (secondary != null) {
                                FuryButton(secondary.label, secondary.onClick, style = FuryButtonStyle.Secondary, enabled = secondary.enabled)
                            }
                            if (primary != null) {
                                FuryButton(primary.label, primary.onClick, style = FuryButtonStyle.Primary, enabled = primary.enabled)
                            }
                        }
                    }
                }
            }
        }
    }
}
