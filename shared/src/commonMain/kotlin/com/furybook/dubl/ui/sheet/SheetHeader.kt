package com.furybook.dubl.ui.sheet

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.furybook.ui.components.FuryButton
import com.furybook.ui.components.FuryButtonStyle
import com.furybook.ui.theme.DublAccent
import com.furybook.ui.theme.DublAccentSoft
import com.furybook.ui.theme.DublBackground
import com.furybook.ui.theme.DublGold
import com.furybook.ui.theme.DublMuted
import com.furybook.ui.theme.DublText
import com.furybook.ui.theme.FuryBorderStrong
import com.furybook.ui.theme.FuryMinTouch
import com.furybook.ui.theme.FuryShapes
import com.furybook.ui.theme.FurySpacing

/**
 * Name, concept, XP and active conditions. Long names wrap to two lines and then ellipsize.
 * Tapping a condition chip asks to toggle it; automatic chips (Слабость at 0 endurance) are shown muted
 * and are not tappable, as on desktop.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SheetHeader(
    name: String,
    concept: String,
    xpFree: Int,
    xpTotal: Int,
    abilityText: String,
    conditions: List<SheetConditionChip>,
    compact: Boolean,
    onToggleCondition: (SheetConditionChip) -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenConditions: (() -> Unit)? = null,
    portrait: (@Composable () -> Unit)? = null,
    onOpenIdentity: (() -> Unit)? = null,
    onOpenEconomy: (() -> Unit)? = null,
    /** In edit mode the button reads «Готово» and is the filled one. */
    editing: Boolean = false,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(FurySpacing.sm)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(FurySpacing.md), verticalAlignment = Alignment.Top) {
            portrait?.invoke()
            Column(
                Modifier.weight(1f).then(
                    onOpenIdentity?.let { open -> Modifier.clickable(role = Role.Button, onClickLabel = "Изменить персонажа") { open() } } ?: Modifier,
                ),
                verticalArrangement = Arrangement.spacedBy(FurySpacing.xs),
            ) {
                Text(
                    text = name,
                    color = DublText,
                    style = if (compact) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.headlineMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = concept.ifBlank { "Без концепта" },
                    color = DublMuted,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            FuryButton(if (editing) "Готово" else "Правка", onEdit, style = if (editing) FuryButtonStyle.Primary else FuryButtonStyle.Secondary)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(FurySpacing.sm), verticalArrangement = Arrangement.spacedBy(FurySpacing.sm)) {
            // Gold fill with near-black text: about 11:1.
            HeaderChip(
                "XP $xpTotal · осталось $xpFree",
                Modifier.background(DublGold).then(
                    onOpenEconomy?.let { open -> Modifier.clickable(role = Role.Button, onClickLabel = "Опыт") { open() } } ?: Modifier,
                ),
                DublBackground,
                bold = true,
            )
            if (abilityText.isNotBlank()) {
                HeaderChip(abilityText, Modifier.border(1.dp, FuryBorderStrong, FuryShapes.control), DublText)
            }
            if (conditions.isEmpty() && onOpenConditions == null) {
                HeaderChip("Нет активных состояний", Modifier, DublMuted)
            }
            conditions.forEach { chip ->
                if (chip.automatic) {
                    HeaderChip(
                        "${chip.title} · авто",
                        Modifier.border(1.dp, FuryBorderStrong, FuryShapes.control).then(
                            if (onOpenConditions != null) {
                                Modifier.clickable(role = Role.Button, onClickLabel = "Состояния") { onOpenConditions() }
                            } else Modifier,
                        ),
                        DublMuted,
                    )
                } else {
                    // With the conditions popup available a chip opens it (desktop HeroConditions); otherwise it toggles.
                    val action = onOpenConditions?.let { open -> Modifier.clickable(role = Role.Button, onClickLabel = "Состояния") { open() } }
                        ?: Modifier.clickable(role = Role.Button, onClickLabel = "Снять состояние") { onToggleCondition(chip) }
                    HeaderChip(
                        chip.title,
                        Modifier.background(DublAccentSoft).border(1.dp, DublAccent, FuryShapes.control).then(action),
                        DublText,
                    )
                }
            }
            if (onOpenConditions != null) {
                HeaderChip(
                    "+ Состояние",
                    Modifier.border(1.dp, FuryBorderStrong, FuryShapes.control)
                        .clickable(role = Role.Button, onClickLabel = "Добавить состояние") { onOpenConditions() },
                    DublText,
                )
            }
        }
    }
}

@Composable
private fun HeaderChip(text: String, decoration: Modifier, color: Color, bold: Boolean = false) {
    Box(
        Modifier.defaultMinSize(minHeight = FuryMinTouch).clip(FuryShapes.control).then(decoration)
            .padding(horizontal = FurySpacing.md),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = color,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
