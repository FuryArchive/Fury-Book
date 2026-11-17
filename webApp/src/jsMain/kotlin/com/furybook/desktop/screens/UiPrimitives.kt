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

@Composable
internal fun DesktopPanel(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = DesktopSurface,
        border = BorderStroke(1.dp, DesktopBorder.copy(alpha = 0.88f)),
    ) {
        content()
    }
}

@Composable
internal fun DesktopSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: DesktopIconKind? = null,
    action: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) DesktopIcon(icon, tint = DesktopMuted, size = 22.dp)
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, color = DesktopMuted, style = MaterialTheme.typography.bodySmall)
            }
        }
        action?.invoke()
    }
}

@Composable
internal fun DesktopHeroPanel(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = DesktopSurfaceRaised,
        border = BorderStroke(1.dp, DesktopBorder.copy(alpha = 0.90f)),
    ) {
        content()
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

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun DesktopSmallAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    emphasized: Boolean = false,
) {
    var hovered by remember { mutableStateOf(false) }
    val background by animateColorAsState(
        targetValue = when {
            emphasized && hovered && enabled -> FuryAccentFillPressed
            emphasized -> FuryAccentFill
            hovered && enabled -> DesktopSurfaceRaised
            else -> DesktopSurfaceInset
        },
        animationSpec = tween(FuryMotion.FastMs),
        label = "small-action-background",
    )
    val borderColor by animateColorAsState(
        targetValue = when {
            emphasized -> DesktopAccent
            hovered && enabled -> DesktopAccent.copy(alpha = .42f)
            else -> DesktopBorder.copy(alpha = .88f)
        },
        animationSpec = tween(FuryMotion.FastMs),
        label = "small-action-border",
    )
    Surface(
        modifier = modifier
            .pointerMoveFilter(
                onEnter = { if (enabled) hovered = true; false },
                onExit = { hovered = false; false },
            )
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(9.dp),
        color = background,
        border = BorderStroke(1.dp, borderColor),
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            maxLines = 1,
            color = when {
                !enabled -> DesktopMuted
                emphasized -> FuryOnAccentFill
                else -> DesktopText
            },
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun DesktopInlineAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var hovered by remember { mutableStateOf(false) }
    val textColor by animateColorAsState(
        targetValue = if (hovered) DesktopAccent else DesktopMuted,
        animationSpec = tween(FuryMotion.FastMs),
        label = "inline-action-text",
    )
    Text(
        label,
        modifier = modifier
            .pointerMoveFilter(
                onEnter = { hovered = true; false },
                onExit = { hovered = false; false },
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 5.dp),
        color = textColor,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
    )
}

@Composable
internal fun DesktopMetricCell(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    tint: Color = DesktopAccent,
    onClick: (() -> Unit)? = null,
) {
    val interactive = if (onClick != null) modifier.clickable(onClick = onClick) else modifier
    Surface(
        modifier = interactive,
        shape = RoundedCornerShape(10.dp),
        color = DesktopSurfaceInset.copy(alpha = 0.68f),
        border = BorderStroke(1.dp, DesktopBorder.copy(alpha = 0.54f)),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, color = DesktopMuted, style = MaterialTheme.typography.labelMedium, maxLines = 1)
            Text(value, color = tint, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

@Composable
internal fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            DesktopSectionHeader(title = title, action = action)
            content()
        }
    }
}

@Composable
internal fun KeyValue(label: String, value: String, tint: Color = DesktopAccent) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
        Text(label, color = DesktopMuted, modifier = Modifier.weight(1f))
        Text(value, color = tint, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
    }
}

@Composable
internal fun RankStepper(rank: Int, min: Int = 0, max: Int, enabled: Boolean = true, onChange: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(enabled = enabled && rank > min, onClick = { onChange(rank - 1) }) { Text("−") }
        Text(rank.toString(), fontWeight = FontWeight.Bold)
        Button(enabled = enabled && rank < max, onClick = { onChange(rank + 1) }) { Text("+") }
    }
}

@Composable
internal fun EmptyState(text: String) {
    Spacer(Modifier.height(4.dp))
    Text(text, color = DesktopMuted, style = MaterialTheme.typography.bodyMedium)
}

internal fun signed(value: Int): String = if (value >= 0) "+$value" else value.toString()
internal fun formatNumber(value: Double): String {
    if (value % 1.0 == 0.0) return value.toInt().toString()
    val rounded = kotlin.math.round(value * 100.0) / 100.0
    return rounded.toString().trimEnd('0').trimEnd('.')
}

enum class DesktopIconKind {
    SHEET, SKILLS, DEVELOPMENT, MAGIC, EQUIPMENT, CHARACTERS, SETTINGS,
    PORTRAIT, HEALTH, ENDURANCE, MANA, CHI,
    STRENGTH, CONSTITUTION, DEXTERITY, SPEED, INTELLIGENCE, PERCEPTION, WILL, CHARISMA,
    DEFENSE, REFLEXES, INITIATIVE, FORTITUDE, RUN, SIZE, NOTES, DICE, DETAIL, GENERIC_SKILL,
}

@Composable
internal fun DesktopIcon(
    kind: DesktopIconKind,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    size: Dp = 18.dp,
) {
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = Stroke(width = (w * 0.10f).coerceAtLeast(1.4f))
        val thin = Stroke(width = (w * 0.075f).coerceAtLeast(1.1f))
        fun line(x1: Float, y1: Float, x2: Float, y2: Float, st: Stroke = stroke) =
            drawLine(tint, start = androidx.compose.ui.geometry.Offset(w * x1, h * y1), end = androidx.compose.ui.geometry.Offset(w * x2, h * y2), strokeWidth = st.width)
        fun circle(x: Float, y: Float, r: Float, st: Stroke = stroke) =
            drawCircle(tint, radius = w * r, center = androidx.compose.ui.geometry.Offset(w * x, h * y), style = st)
        when (kind) {
            DesktopIconKind.HEALTH, DesktopIconKind.CONSTITUTION, DesktopIconKind.FORTITUDE -> {
                val p = Path().apply {
                    moveTo(w * .5f, h * .84f); cubicTo(w * .16f, h * .62f, w * .08f, h * .34f, w * .27f, h * .23f)
                    cubicTo(w * .40f, h * .15f, w * .49f, h * .26f, w * .5f, h * .32f)
                    cubicTo(w * .51f, h * .26f, w * .60f, h * .15f, w * .73f, h * .23f)
                    cubicTo(w * .92f, h * .34f, w * .84f, h * .62f, w * .5f, h * .84f)
                    close()
                }
                drawPath(p, tint, style = stroke)
            }
            DesktopIconKind.ENDURANCE -> {
                val p = Path().apply { moveTo(w*.58f,h*.08f); lineTo(w*.28f,h*.52f); lineTo(w*.49f,h*.52f); lineTo(w*.38f,h*.92f); lineTo(w*.74f,h*.40f); lineTo(w*.53f,h*.40f); close() }
                drawPath(p, tint)
            }
            DesktopIconKind.MANA -> {
                val p = Path().apply { moveTo(w*.5f,h*.08f); cubicTo(w*.30f,h*.38f,w*.20f,h*.51f,w*.20f,h*.67f); cubicTo(w*.20f,h*.86f,w*.35f,h*.94f,w*.5f,h*.94f); cubicTo(w*.65f,h*.94f,w*.80f,h*.86f,w*.80f,h*.67f); cubicTo(w*.80f,h*.51f,w*.70f,h*.38f,w*.5f,h*.08f); close() }
                drawPath(p,tint,style=stroke)
            }
            DesktopIconKind.CHI -> {
                drawArc(color=tint, startAngle=30f, sweepAngle=280f, useCenter=false, topLeft=androidx.compose.ui.geometry.Offset(w*.16f,h*.16f), size=androidx.compose.ui.geometry.Size(w*.68f,h*.68f), style=stroke)
                circle(.52f,.50f,.08f,thin)
            }
            DesktopIconKind.STRENGTH -> {
                line(.18f,.38f,.18f,.68f); line(.30f,.30f,.30f,.76f); line(.30f,.53f,.70f,.53f); line(.70f,.30f,.70f,.76f); line(.82f,.38f,.82f,.68f)
            }
            DesktopIconKind.DEXTERITY -> { line(.18f,.74f,.80f,.22f); line(.28f,.76f,.82f,.42f,thin); line(.18f,.52f,.58f,.18f,thin) }
            DesktopIconKind.SPEED, DesktopIconKind.RUN -> { line(.15f,.34f,.62f,.34f); line(.35f,.54f,.82f,.54f); line(.20f,.74f,.63f,.74f); line(.62f,.34f,.52f,.25f,thin); line(.82f,.54f,.72f,.45f,thin) }
            DesktopIconKind.INTELLIGENCE -> {
                val p=Path().apply { moveTo(w*.12f,h*.24f); quadraticBezierTo(w*.32f,h*.16f,w*.49f,h*.28f); lineTo(w*.49f,h*.80f); quadraticBezierTo(w*.32f,h*.68f,w*.12f,h*.76f); close() }
                drawPath(p,tint,style=thin); val q=Path().apply { moveTo(w*.88f,h*.24f); quadraticBezierTo(w*.68f,h*.16f,w*.51f,h*.28f); lineTo(w*.51f,h*.80f); quadraticBezierTo(w*.68f,h*.68f,w*.88f,h*.76f); close() }; drawPath(q,tint,style=thin)
            }
            DesktopIconKind.PERCEPTION -> {
                val p=Path().apply { moveTo(w*.08f,h*.50f); quadraticBezierTo(w*.28f,h*.22f,w*.50f,h*.22f); quadraticBezierTo(w*.72f,h*.22f,w*.92f,h*.50f); quadraticBezierTo(w*.72f,h*.78f,w*.50f,h*.78f); quadraticBezierTo(w*.28f,h*.78f,w*.08f,h*.50f) }; drawPath(p,tint,style=thin); circle(.5f,.5f,.12f,thin)
            }
            DesktopIconKind.WILL -> { line(.5f,.08f,.5f,.92f,thin); line(.08f,.5f,.92f,.5f,thin); line(.20f,.20f,.80f,.80f,thin); line(.80f,.20f,.20f,.80f,thin); circle(.5f,.5f,.08f,thin) }
            DesktopIconKind.CHARISMA, DesktopIconKind.CHARACTERS -> { circle(.36f,.34f,.12f,thin); circle(.66f,.38f,.10f,thin); drawArc(color=tint, startAngle=200f, sweepAngle=140f, useCenter=false, topLeft=androidx.compose.ui.geometry.Offset(w*.14f,h*.42f), size=androidx.compose.ui.geometry.Size(w*.44f,h*.46f), style=thin); drawArc(color=tint, startAngle=205f, sweepAngle=130f, useCenter=false, topLeft=androidx.compose.ui.geometry.Offset(w*.48f,h*.48f), size=androidx.compose.ui.geometry.Size(w*.40f,h*.38f), style=thin) }
            DesktopIconKind.DEFENSE -> { val p=Path().apply { moveTo(w*.5f,h*.08f); lineTo(w*.82f,h*.20f); lineTo(w*.78f,h*.62f); quadraticBezierTo(w*.68f,h*.82f,w*.5f,h*.92f); quadraticBezierTo(w*.32f,h*.82f,w*.22f,h*.62f); lineTo(w*.18f,h*.20f); close() }; drawPath(p,tint,style=stroke) }
            DesktopIconKind.REFLEXES -> { drawArc(color=tint, startAngle=200f, sweepAngle=250f, useCenter=false, topLeft=androidx.compose.ui.geometry.Offset(w*.16f,h*.16f), size=androidx.compose.ui.geometry.Size(w*.68f,h*.68f), style=stroke); line(.18f,.34f,.18f,.12f,thin); line(.18f,.12f,.38f,.18f,thin) }
            DesktopIconKind.INITIATIVE -> { line(.22f,.12f,.78f,.12f); line(.22f,.88f,.78f,.88f); line(.30f,.16f,.70f,.84f,thin); line(.70f,.16f,.30f,.84f,thin) }
            DesktopIconKind.SIZE -> { circle(.5f,.20f,.10f,thin); line(.5f,.30f,.5f,.68f); line(.30f,.44f,.70f,.44f); line(.5f,.68f,.32f,.92f); line(.5f,.68f,.68f,.92f) }
            DesktopIconKind.DICE, DesktopIconKind.GENERIC_SKILL -> { drawRoundRect(tint, topLeft=androidx.compose.ui.geometry.Offset(w*.14f,h*.14f), size=androidx.compose.ui.geometry.Size(w*.72f,h*.72f), cornerRadius=androidx.compose.ui.geometry.CornerRadius(w*.12f,w*.12f), style=thin); drawCircle(tint,w*.045f,androidx.compose.ui.geometry.Offset(w*.34f,h*.34f)); drawCircle(tint,w*.045f,androidx.compose.ui.geometry.Offset(w*.66f,h*.66f)); drawCircle(tint,w*.045f,androidx.compose.ui.geometry.Offset(w*.50f,h*.50f)) }
            DesktopIconKind.NOTES -> { drawRoundRect(tint, topLeft=androidx.compose.ui.geometry.Offset(w*.20f,h*.10f), size=androidx.compose.ui.geometry.Size(w*.60f,h*.80f), cornerRadius=androidx.compose.ui.geometry.CornerRadius(w*.06f,w*.06f), style=thin); line(.32f,.38f,.68f,.38f,thin); line(.32f,.53f,.68f,.53f,thin); line(.32f,.68f,.58f,.68f,thin) }
            DesktopIconKind.MAGIC -> { circle(.5f,.5f,.28f,thin); line(.5f,.06f,.5f,.24f,thin); line(.5f,.76f,.5f,.94f,thin); line(.06f,.5f,.24f,.5f,thin); line(.76f,.5f,.94f,.5f,thin) }
            DesktopIconKind.EQUIPMENT -> { drawRoundRect(tint, topLeft=androidx.compose.ui.geometry.Offset(w*.16f,h*.30f), size=androidx.compose.ui.geometry.Size(w*.68f,h*.52f), cornerRadius=androidx.compose.ui.geometry.CornerRadius(w*.08f,w*.08f), style=thin); drawArc(color=tint, startAngle=180f, sweepAngle=180f, useCenter=false, topLeft=androidx.compose.ui.geometry.Offset(w*.34f,h*.12f), size=androidx.compose.ui.geometry.Size(w*.32f,h*.34f), style=thin) }
            DesktopIconKind.DEVELOPMENT -> { circle(.50f,.50f,.12f,thin); circle(.50f,.18f,.07f,thin); circle(.80f,.50f,.07f,thin); circle(.50f,.82f,.07f,thin); circle(.20f,.50f,.07f,thin); line(.50f,.25f,.50f,.38f,thin); line(.62f,.50f,.73f,.50f,thin); line(.50f,.62f,.50f,.75f,thin); line(.27f,.50f,.38f,.50f,thin) }
            DesktopIconKind.SKILLS -> { circle(.5f,.5f,.30f,thin); line(.5f,.20f,.5f,.80f,thin); line(.20f,.5f,.80f,.5f,thin); circle(.5f,.5f,.07f,thin) }
            DesktopIconKind.SHEET -> { drawRoundRect(tint, topLeft=androidx.compose.ui.geometry.Offset(w*.18f,h*.10f), size=androidx.compose.ui.geometry.Size(w*.64f,h*.80f), cornerRadius=androidx.compose.ui.geometry.CornerRadius(w*.06f,w*.06f), style=thin); line(.32f,.32f,.68f,.32f,thin); line(.32f,.48f,.68f,.48f,thin); line(.32f,.64f,.56f,.64f,thin) }
            DesktopIconKind.SETTINGS -> { circle(.5f,.5f,.26f,thin); circle(.5f,.5f,.08f,thin); line(.5f,.06f,.5f,.22f,thin); line(.5f,.78f,.5f,.94f,thin); line(.06f,.5f,.22f,.5f,thin); line(.78f,.5f,.94f,.5f,thin) }
            DesktopIconKind.PORTRAIT -> { val p=Path().apply { moveTo(w*.50f,h*.08f); lineTo(w*.76f,h*.26f); lineTo(w*.68f,h*.76f); lineTo(w*.50f,h*.92f); lineTo(w*.32f,h*.76f); lineTo(w*.24f,h*.26f); close() }; drawPath(p,tint,style=thin) }
            DesktopIconKind.DETAIL -> { line(.35f,.20f,.66f,.50f,thin); line(.66f,.50f,.35f,.80f,thin) }
        }
    }
}

@Composable
internal fun FurySegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(9.dp),
        color = DesktopSurfaceInset,
        border = BorderStroke(1.dp, DesktopBorder.copy(alpha = .88f)),
    ) {
        Row(Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, label ->
                val selected = index == selectedIndex
                val itemBackground by animateColorAsState(
                    targetValue = if (selected) DesktopAccentSoft.copy(alpha = .92f) else Color.Transparent,
                    animationSpec = tween(FuryMotion.FastMs),
                    label = "segmented-background-$index",
                )
                val itemColor by animateColorAsState(
                    targetValue = if (selected) DesktopAccent else DesktopMuted,
                    animationSpec = tween(FuryMotion.FastMs),
                    label = "segmented-text-$index",
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(itemBackground)
                        .clickable { onSelected(index) }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        color = itemColor,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
internal fun FuryChoiceButton(
    label: String,
    meta: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val background by animateColorAsState(
        targetValue = if (selected) DesktopAccentSoft.copy(alpha = .72f) else DesktopSurfaceInset.copy(alpha = .84f),
        animationSpec = tween(FuryMotion.FastMs),
        label = "choice-background",
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected) DesktopAccent.copy(alpha = .86f) else DesktopBorder.copy(alpha = .72f),
        animationSpec = tween(FuryMotion.FastMs),
        label = "choice-border",
    )
    val primaryColor by animateColorAsState(
        targetValue = if (selected) DesktopText else DesktopMuted,
        animationSpec = tween(FuryMotion.FastMs),
        label = "choice-text",
    )
    val metaColor by animateColorAsState(
        targetValue = if (selected) DesktopAccent else DesktopMuted,
        animationSpec = tween(FuryMotion.FastMs),
        label = "choice-meta",
    )
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(9.dp),
        color = background,
        border = BorderStroke(1.dp, borderColor),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 11.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                label,
                modifier = Modifier.weight(1f),
                color = primaryColor,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.width(8.dp))
            Text(meta, color = metaColor, fontWeight = FontWeight.Bold)
        }
    }
}
