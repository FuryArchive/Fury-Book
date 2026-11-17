package com.furybook.android.ui.screens

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.furybook.android.data.AndroidContentPackState
import com.furybook.android.data.ConditionCatalogRepository
import com.furybook.android.data.DevelopmentCatalogRepository
import com.furybook.android.data.SkillEffectCatalogRepository
import com.furybook.content.FcpOrderedUiItem
import com.furybook.content.FcpUiAccentToken
import com.furybook.content.FcpUiComponent
import com.furybook.content.FcpUiHostOrder
import com.furybook.content.FcpUiIconToken
import com.furybook.content.FcpUiSurface
import com.furybook.content.orderedUiItems
import com.furybook.dubl.content.DublResourceMeterModel
import com.furybook.dubl.content.DublResourceToggleModel
import com.furybook.dubl.content.DublUiFeature
import com.furybook.dubl.content.forFeature
import com.furybook.dubl.content.resourceCurrent
import com.furybook.dubl.model.AttributeId
import com.furybook.dubl.model.CharacterConditionId
import com.furybook.dubl.model.ConditionLocalOverride
import com.furybook.dubl.model.CharacterEconomy
import com.furybook.dubl.model.CharacterEconomyBreakdown
import com.furybook.dubl.model.CharacterSheetResourceId
import com.furybook.dubl.model.DublCharacter
import com.furybook.dubl.model.FlurryWeaponProfile
import com.furybook.dubl.model.CustomCondition
import com.furybook.dubl.model.CustomResource
import com.furybook.dubl.model.DevelopmentCatalog
import com.furybook.dubl.model.DevelopmentEntry
import com.furybook.dubl.model.DevelopmentSheetItem
import com.furybook.dubl.model.DevelopmentSheetItemSource
import com.furybook.dubl.model.DevelopmentSheetSection
import com.furybook.dubl.model.DevelopmentSheetSectionType
import com.furybook.dubl.model.DevelopmentProgress
import com.furybook.dubl.model.DevelopmentRules
import com.furybook.dubl.model.RequirementStatus
import com.furybook.dubl.model.MagicEquipmentRules
import com.furybook.dubl.model.ResolvedSkill
import com.furybook.dubl.model.RollContribution
import com.furybook.dubl.model.RollContext
import com.furybook.dubl.model.RollFollowUp
import com.furybook.dubl.model.RollMode
import com.furybook.dubl.model.RollResult
import com.furybook.dubl.model.RollTargetOutcome
import com.furybook.dubl.model.SheetGroup
import com.furybook.dubl.model.SheetGroupingRules
import com.furybook.dubl.model.SkillCategory
import com.furybook.dubl.model.SkillEffectDefinition
import com.furybook.dubl.model.SkillEffectRules
import com.furybook.dubl.model.SkillRollEffectOption
import com.furybook.dubl.model.allowedAttributes
import com.furybook.dubl.model.allowedSkillIds
import com.furybook.dubl.model.AttackOutcome
import com.furybook.dubl.model.availableFollowUp
import com.furybook.dubl.model.compareRollToTarget
import com.furybook.dubl.model.resolve
import com.furybook.dubl.model.resolveAttack
import com.furybook.dubl.model.rule
import com.furybook.dubl.model.summary
import com.furybook.dubl.model.developmentNormalize
import com.furybook.dubl.model.rollPreset
import com.furybook.dubl.model.rollCheck
import com.furybook.dubl.model.rollFollowUp
import com.furybook.dubl.model.resolveSkill
import com.furybook.dubl.model.resolvedSkills
import com.furybook.dubl.model.skillCalculation
import com.furybook.dubl.model.skillCalculationForRoll
import com.furybook.dubl.model.selectedTotals
import com.furybook.android.state.CharacterController
import com.furybook.android.ui.components.containSheetOverscroll
import com.furybook.ui.components.DublCard
import com.furybook.android.ui.components.DublSwitch
import com.furybook.ui.theme.FuryAccentFill
import com.furybook.ui.theme.FuryOnAccentFill
import com.furybook.ui.theme.DublAccentSoft
import com.furybook.ui.theme.DublDanger
import com.furybook.ui.theme.DublGold
import com.furybook.ui.theme.FuryPalette
import com.furybook.ui.theme.DublMana
import java.text.DecimalFormat
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// «Последнее: … / Отменить» bar and its wording; moved unchanged from the removed OverviewScreen.kt (stage 2b).

private sealed interface UndoAction {
    data class Resource(val resource: CharacterSheetResourceId, val appliedDelta: Int) : UndoAction
    data class Attribute(val id: AttributeId, val appliedDelta: Int) : UndoAction
    data class Conditions(val previous: Set<CharacterConditionId>) : UndoAction
    data class Name(val previous: String) : UndoAction
    data class Experience(val previous: Int, val previousCreation: Int) : UndoAction
    data class Size(val previous: Int) : UndoAction
    data class Legs(val previous: Int) : UndoAction
}

private data class RecentChange(
    val text: String,
    val accent: Color,
    val undo: UndoAction? = null,
    val token: Long = System.nanoTime(),
)

@Composable
private fun RecentChangeBar(
    change: RecentChange,
    onUndo: (() -> Unit)?,
) = RecentChangeBar(text = change.text, accent = change.accent, onUndo = onUndo)

/** The undo bar; also shown by the shell for changes made on the shared play sheet. */
@Composable
internal fun RecentChangeBar(
    text: String,
    accent: Color,
    onUndo: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(9.dp),
        color = accent.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.24f)),
    ) {
        Row(
            modifier = Modifier.padding(start = 11.dp, end = 5.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Последнее: $text",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelLarge,
                color = accent,
            )
            if (onUndo != null) {
                TextButton(onClick = onUndo) {
                    Text("Отменить", color = accent)
                }
            }
        }
    }
}

private fun resourceChangeText(label: String, delta: Int): String {
    val sign = if (delta > 0) "+" else "−"
    return "$sign${abs(delta)} $label"
}

/**
 * The bar's wording and colour for a resource step, exactly as this screen words its own steps
 * («−1 здоровья» in the health colour; mounted resources use their pack label and accent).
 * The shell uses it for steps made on the shared play sheet.
 */
internal fun recentResourceChange(
    resource: CharacterSheetResourceId,
    applied: Int,
    mountedResources: List<DublResourceMeterModel>,
    palette: FuryPalette,
): Pair<String, Color> {
    val mounted = mountedResources.firstOrNull { model -> model.resourceId == resource }
    if (mounted != null) {
        return resourceChangeText(mounted.presentation.label, applied) to fcpAccentColor(mounted.presentation.accent, palette)
    }
    return when (resource) {
        CharacterSheetResourceId.HEALTH -> resourceChangeText("здоровья", applied) to palette.acc
        CharacterSheetResourceId.ENDURANCE -> resourceChangeText("выносливости", applied) to palette.gold
        CharacterSheetResourceId.MANA -> resourceChangeText("маны", applied) to DublMana
        else -> resourceChangeText(resource.title, applied) to palette.acc
    }
}

private fun fcpAccentColor(token: String?, palette: FuryPalette): Color = when (token) {
    FcpUiAccentToken.FURY_ACCENT -> palette.acc
    else -> palette.acc
}
