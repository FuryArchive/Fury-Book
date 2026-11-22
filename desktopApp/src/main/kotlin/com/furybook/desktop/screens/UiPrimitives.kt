package com.furybook.desktop.screens

import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerMoveFilter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.furybook.ui.theme.DublAccent
import com.furybook.ui.theme.FuryAccentFill
import com.furybook.ui.theme.FuryOnAccentFill
import com.furybook.ui.theme.FuryAccentFillPressed
import com.furybook.ui.theme.DublAccentSoft
import com.furybook.ui.theme.DublBackground
import com.furybook.ui.theme.DublBorder
import com.furybook.ui.theme.DublCustomResource
import com.furybook.ui.theme.DublGold
import com.furybook.ui.theme.DublHealth
import com.furybook.ui.theme.DublMana
import com.furybook.ui.theme.DublMuted
import com.furybook.ui.theme.DublStamina
import com.furybook.ui.theme.DublSurface
import com.furybook.ui.theme.DublSurfaceInset
import com.furybook.ui.theme.DublSurfaceRaised
import com.furybook.ui.theme.DublText

// Desktop aliases the shared Fury Book palette so Android and desktop stay visually aligned.
internal val DesktopBackground: Color @Composable @ReadOnlyComposable get() = DublBackground
internal val DesktopSurface: Color @Composable @ReadOnlyComposable get() = DublSurface
internal val DesktopSurfaceRaised: Color @Composable @ReadOnlyComposable get() = DublSurfaceRaised
internal val DesktopSurfaceInset: Color @Composable @ReadOnlyComposable get() = DublSurfaceInset
internal val DesktopBorder: Color @Composable @ReadOnlyComposable get() = DublBorder
internal val DesktopText: Color @Composable @ReadOnlyComposable get() = DublText
internal val DesktopMuted: Color @Composable @ReadOnlyComposable get() = DublMuted
internal val DesktopAccent: Color @Composable @ReadOnlyComposable get() = DublAccent
internal val DesktopAccentSoft: Color @Composable @ReadOnlyComposable get() = DublAccentSoft
internal object FuryMotion {
    const val FastMs = 110
    const val StandardMs = 170
    const val DialogMs = 200
    const val UndoToastDurationMs = 4_000L
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FuryDialog(
    onDismissRequest: () -> Unit,
    title: @Composable () -> Unit,
    text: @Composable () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val contentScroll = rememberScrollState()
    val density = LocalDensity.current
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val dialogProgress by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = tween(FuryMotion.DialogMs),
        label = "fury-dialog-enter",
    )

    BasicAlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
    ) {
        Surface(
            modifier = Modifier
                .widthIn(min = 360.dp, max = 620.dp)
                .heightIn(max = 680.dp)
                .animateContentSize(animationSpec = tween(FuryMotion.StandardMs))
                .graphicsLayer {
                    alpha = dialogProgress
                    scaleX = 0.975f + 0.025f * dialogProgress
                    scaleY = 0.975f + 0.025f * dialogProgress
                    translationY = with(density) { 8.dp.toPx() } * (1f - dialogProgress)
                },
            shape = RoundedCornerShape(12.dp),
            color = DesktopSurfaceRaised,
            border = BorderStroke(1.dp, DesktopBorder.copy(alpha = 0.95f)),
            shadowElevation = 18.dp,
        ) {
            Column(Modifier.animateContentSize(animationSpec = tween(FuryMotion.StandardMs))) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DesktopSurfaceInset.copy(alpha = 0.76f))
                        .padding(start = 18.dp, end = 10.dp, top = 13.dp, bottom = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(Modifier.weight(1f)) {
                        CompositionLocalProvider(LocalContentColor provides DesktopText) {
                            ProvideTextStyle(MaterialTheme.typography.titleLarge) { title() }
                        }
                    }
                    Text(
                        "×",
                        color = DesktopMuted,
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier
                            .clickable(onClick = onDismissRequest)
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
                HorizontalDivider(color = DesktopBorder.copy(alpha = 0.82f))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(contentScroll)
                        .animateContentSize(animationSpec = tween(FuryMotion.StandardMs))
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                ) {
                    CompositionLocalProvider(LocalContentColor provides DesktopText) {
                        ProvideTextStyle(MaterialTheme.typography.bodyMedium) { text() }
                    }
                }
                HorizontalDivider(color = DesktopBorder.copy(alpha = 0.72f))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (dismissButton != null) {
                        dismissButton()
                        Spacer(Modifier.width(8.dp))
                    }
                    confirmButton()
                }
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun FuryUndoToast(
    text: String,
    canUndo: Boolean,
    onUndo: () -> Unit,
    onDismiss: () -> Unit,
    onHoverChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var hovered by remember { mutableStateOf(false) }
    val background by animateColorAsState(
        targetValue = if (hovered) DesktopAccentSoft.copy(alpha = .96f) else DesktopSurfaceRaised.copy(alpha = .98f),
        animationSpec = tween(FuryMotion.FastMs),
        label = "undo-toast-background",
    )
    Surface(
        modifier = modifier
            .pointerMoveFilter(
                onEnter = { hovered = true; onHoverChange(true); false },
                onExit = { hovered = false; onHoverChange(false); false },
            ),
        shape = RoundedCornerShape(11.dp),
        color = background,
        border = BorderStroke(1.dp, DesktopAccent.copy(alpha = if (hovered) .52f else .34f)),
        shadowElevation = 14.dp,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(text, modifier = Modifier.weight(1f), color = DesktopText, fontWeight = FontWeight.SemiBold)
            if (canUndo) {
                TextButton(onClick = onUndo) { Text("Отменить") }
            }
            Text(
                "×",
                color = DesktopMuted,
                modifier = Modifier.clickable(onClick = onDismiss).padding(horizontal = 5.dp, vertical = 2.dp),
            )
        }
    }
}

internal fun signed(value: Int): String = if (value >= 0) "+$value" else value.toString()

enum class DesktopIconKind {
    SHEET, SKILLS, DEVELOPMENT, MAGIC, EQUIPMENT, CHARACTERS, SETTINGS,
    PORTRAIT, HEALTH, ENDURANCE, MANA, CHI,
    STRENGTH, CONSTITUTION, DEXTERITY, SPEED, INTELLIGENCE, PERCEPTION, WILL, CHARISMA,
    DEFENSE, REFLEXES, INITIATIVE, FORTITUDE, RUN, SIZE, NOTES, DICE, DETAIL, GENERIC_SKILL,
}