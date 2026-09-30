package com.furybook.desktop.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.pointerMoveFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.decodeToImageBitmap
import com.furybook.web.dataUrlBytes
import com.furybook.web.pickPortraitDataUrl
import com.furybook.web.webUuid
import androidx.compose.ui.zIndex
import com.furybook.content.FcpOrderedUiItem
import com.furybook.content.FcpUiAccentToken
import com.furybook.content.FcpUiComponent
import com.furybook.content.FcpUiHostOrder
import com.furybook.content.FcpUiIconToken
import com.furybook.content.FcpUiSurface
import com.furybook.content.orderedUiItems
import com.furybook.content.presentation
import com.furybook.dubl.content.DublUiFeature
import com.furybook.dubl.content.forFeature
import com.furybook.dubl.content.resourceCurrent
import com.furybook.dubl.model.AttributeId
import com.furybook.dubl.model.CharacterConditionId
import com.furybook.dubl.model.CharacterEconomy
import com.furybook.dubl.model.CharacterSheetExtras
import com.furybook.dubl.model.CharacterNote
import com.furybook.dubl.model.CharacterSheetResourceId
import com.furybook.dubl.model.CustomCondition
import com.furybook.dubl.model.CustomResource
import com.furybook.dubl.model.DevelopmentEntry
import com.furybook.dubl.model.DevelopmentProgress
import com.furybook.dubl.model.DevelopmentRules
import com.furybook.dubl.model.DevelopmentSheetSectionType
import com.furybook.dubl.model.DublCharacter
import com.furybook.dubl.model.RollContext
import com.furybook.dubl.model.ResolvedSkill
import com.furybook.dubl.model.SheetGroup
import com.furybook.dubl.model.SheetGroupingRules
import com.furybook.dubl.model.SkillCategory
import com.furybook.dubl.model.SkillEffectRules
import com.furybook.dubl.model.resolvedSkills
import com.furybook.dubl.model.displayNotes
import com.furybook.dubl.model.characterSheetDevelopmentSections
import com.furybook.dubl.model.isMagicSchoolSheetEntryId
import com.furybook.dubl.model.ensureMagicSchoolsInSpecialGroup
import com.furybook.dubl.model.skillCalculationForRoll
import com.furybook.desktop.DesktopAppState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private sealed interface SheetUndo {
    data class Resource(val resource: CharacterSheetResourceId, val delta: Int) : SheetUndo
    data class Attribute(val id: AttributeId, val delta: Int) : SheetUndo
    data class Conditions(val previous: Set<CharacterConditionId>) : SheetUndo
    data class Identity(val previous: DublCharacter) : SheetUndo
}
private data class RecentSheetChange(val text: String, val undo: SheetUndo? = null)
private data class ContextRollRequest(val context: RollContext, val attribute: AttributeId? = null)
private data class SheetSkillRollRequest(val skill: ResolvedSkill, val attribute: AttributeId)
private data class SheetSkillPresentation(val bonus: String, val breakdownLines: List<String>)
private enum class GroupingKind(val title: String) { SKILLS("Умения"), DEVELOPMENT("Навыки") }

@Composable
fun CharacterSheetScreen(
    state: DesktopAppState,
    modifier: Modifier = Modifier,
    onNavigateSkills: () -> Unit = {},
    onNavigateDevelopment: () -> Unit = {},
    onNavigateMagic: () -> Unit = {},
    onNavigateEquipment: () -> Unit = {},
) {
    val character = state.activeCharacter
    val extras = state.extras
    val economy = CharacterEconomy.breakdown(
        character,
        state.developmentCatalog,
        includeChi = state.uiMounts(FcpUiSurface.CHARACTER_ECONOMY, FcpUiComponent.XP_LINE).forFeature(DublUiFeature.CHI) != null,
    )
    var showIdentity by remember(character.id) { mutableStateOf(false) }
    var showEconomy by remember(character.id) { mutableStateOf(false) }
    var showConditions by remember(character.id) { mutableStateOf(false) }
    var createNote by remember(character.id) { mutableStateOf(false) }
    var editingNote by remember(character.id) { mutableStateOf<CharacterNote?>(null) }
    var showVisibility by remember(character.id) { mutableStateOf(false) }
    var showHealthControl by remember(character.id) { mutableStateOf(false) }
    var customResource by remember(character.id) { mutableStateOf<CustomResource?>(null) }
    var createResource by remember(character.id) { mutableStateOf(false) }
    var maximumResource by remember(character.id) { mutableStateOf<CharacterSheetResourceId?>(null) }
    var rollRequest by remember(character.id) { mutableStateOf<ContextRollRequest?>(null) }
    var sheetRollAttributeChoice by remember(character.id) { mutableStateOf<ResolvedSkill?>(null) }
    var sheetRollRequest by remember(character.id) { mutableStateOf<SheetSkillRollRequest?>(null) }
    var sheetDevelopmentEntry by remember(character.id) { mutableStateOf<DevelopmentEntry?>(null) }
    var sheetEditingDevelopment by remember(character.id) { mutableStateOf<DevelopmentEntry?>(null) }
    var grouping by remember(character.id) { mutableStateOf<GroupingKind?>(null) }
    var recent by remember(character.id) { mutableStateOf<RecentSheetChange?>(null) }
    var displayedRecent by remember(character.id) { mutableStateOf<RecentSheetChange?>(null) }
    var recentHovered by remember(character.id) { mutableStateOf(false) }

    LaunchedEffect(recent) {
        if (recent != null) displayedRecent = recent
    }
    LaunchedEffect(recent, recentHovered) {
        val snapshot = recent ?: return@LaunchedEffect
        if (!recentHovered) {
            delay(FuryMotion.UndoToastDurationMs)
            if (recent == snapshot && !recentHovered) recent = null
        }
    }

    fun changeResource(resource: CharacterSheetResourceId, delta: Int) {
        val before = character.resourceCurrent(resource)
        state.changeResource(resource, delta)
        val after = state.activeCharacter.resourceCurrent(resource)
        val applied = after - before
        if (applied != 0) recent = RecentSheetChange("${resource.title} ${signed(applied)}", SheetUndo.Resource(resource, applied))
    }

    fun undoRecent() {
        if (recent?.undo != null) state.undoLast()
        recent = null
    }

    val effectiveConditions = remember(extras.activeConditions, character.enduranceCurrent) {
        buildSet {
            addAll(extras.activeConditions)
            if (character.enduranceCurrent == 0) add(CharacterConditionId.WEAKNESS)
        }
    }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val compactSheet = maxWidth < 880.dp
        val wideSheet = maxWidth >= 1320.dp
        Box(Modifier.fillMaxSize()) {
            LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                CharacterHero(
                    state = state,
                    character = character,
                    extras = extras,
                    economy = economy,
                    effectiveConditions = effectiveConditions,
                    compact = compactSheet,
                    wide = wideSheet,
                    onEditIdentity = { showIdentity = true },
                    onEconomy = { showEconomy = true },
                    onConditions = { showConditions = true },
                    onResourceDelta = ::changeResource,
                    onResourceVisibility = { showVisibility = true },
                    onHealthControl = { showHealthControl = true },
                    onEditMaximum = { maximumResource = it },
                    onEditCustomResource = { customResource = it },
                    onCreateCustomResource = { createResource = true },
                    onAttributeDelta = { id, delta ->
                        state.changeAttribute(id, delta)
                        recent = RecentSheetChange("${id.title} ${signed(delta)}", SheetUndo.Attribute(id, delta))
                    },
                    onRoll = { context, attribute -> rollRequest = ContextRollRequest(context, attribute) },
                )
            }

            item {
                SkillsDevelopmentWorkspace(
                    state = state,
                    character = character,
                    extras = extras,
                    compact = compactSheet,
                    onSkillRoll = { sheetRollAttributeChoice = it },
                    onNavigateSkills = onNavigateSkills,
                    onGrouping = { grouping = it },
                    onDevelopmentDetails = { sheetDevelopmentEntry = it },
                    onNavigateDevelopment = onNavigateDevelopment,
                    onNavigateMagic = onNavigateMagic,
                )
            }

            item {
                QuickCombatActionsPanel(
                    onRoll = { context -> rollRequest = ContextRollRequest(context) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

                item {
                    NotesPanel(
                        notes = extras.displayNotes(),
                        onAdd = { createNote = true },
                        onEdit = { editingNote = it },
                        onDelete = { state.removeNote(it.id) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            AnimatedVisibility(
                visible = recent != null,
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
                displayedRecent?.let { change ->
                    FuryUndoToast(
                        text = change.text,
                        canUndo = change.undo != null,
                        onUndo = ::undoRecent,
                        onDismiss = { recent = null; recentHovered = false },
                        onHoverChange = { recentHovered = it },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }

    if (showIdentity) IdentityDialog(state, character, onDismiss = { showIdentity = false }) { previous -> recent = RecentSheetChange("Персонаж изменён", SheetUndo.Identity(previous)) }
    if (showEconomy) EconomyDialog(state, onDismiss = { showEconomy = false })
    if (showConditions) ConditionsDialog(state, onDismiss = { showConditions = false }) { previous -> recent = RecentSheetChange("Состояния изменены", SheetUndo.Conditions(previous)) }
    if (showVisibility) ResourceVisibilityDialog(state, onDismiss = { showVisibility = false })
    if (createNote) NoteEditorDialog(
        title = "Новая заметка",
        initial = null,
        onSave = { noteTitle, body -> state.addNote(noteTitle, body); createNote = false },
        onDismiss = { createNote = false },
    )
    editingNote?.let { note ->
        NoteEditorDialog(
            title = "Редактировать заметку",
            initial = note,
            onSave = { noteTitle, body -> state.updateNote(note.id, noteTitle, body); editingNote = null },
            onDismiss = { editingNote = null },
        )
    }
    if (showHealthControl) HealthControlDialog(
        current = state.activeCharacter.hpCurrent,
        maximum = state.activeCharacter.healthMaximum,
        onChange = { delta -> changeResource(CharacterSheetResourceId.HEALTH, delta) },
        onEditMaximum = { showHealthControl = false; maximumResource = CharacterSheetResourceId.HEALTH },
        onDismiss = { showHealthControl = false },
    )
    if (createResource) CustomResourceDialog(state, null, onDismiss = { createResource = false })
    customResource?.let { resource -> CustomResourceDialog(state, resource, onDismiss = { customResource = null }) }
    maximumResource?.let { resource -> MaximumDialog(state, resource, onDismiss = { maximumResource = null }) }
    rollRequest?.let { request -> ContextRollDialog(
        character = state.activeCharacter,
        context = request.context,
        developmentCatalog = state.developmentCatalog,
        effectCatalog = state.skillEffectCatalog,
        initialAttribute = request.attribute,
        onDismiss = { rollRequest = null },
    ) }
    sheetDevelopmentEntry?.let { entry ->
        DevelopmentDetailsDialog(
            state = state,
            entry = entry,
            onOpenEntry = { targetId -> state.developmentCatalog.byId(targetId)?.let { sheetDevelopmentEntry = it } },
            onEditLocal = {
                sheetEditingDevelopment = entry
                sheetDevelopmentEntry = null
            },
            onResetLocal = {
                state.resetDevelopmentOverride(entry.id)
                sheetDevelopmentEntry = null
            },
            onDeleteCustom = {
                state.removeCustomDevelopment(entry.id)
                sheetDevelopmentEntry = null
            },
            hasLocalOverride = character.developmentOverrides.containsKey(entry.id),
            isCustom = character.customDevelopmentEntries.any { it.id == entry.id },
            onDismiss = { sheetDevelopmentEntry = null },
        )
    }
    sheetEditingDevelopment?.let { entry ->
        val isCustom = state.activeCharacter.customDevelopmentEntries.any { it.id == entry.id }
        DevelopmentLocalEditDialog(
            initial = entry,
            title = if (isCustom) "Редактировать свою запись" else "Локальная правка",
            onSave = { updated ->
                if (isCustom) state.updateCustomDevelopment(updated) else state.setDevelopmentOverride(updated)
                sheetEditingDevelopment = null
            },
            onDismiss = { sheetEditingDevelopment = null },
        )
    }
    sheetRollAttributeChoice?.let { skill ->
        SkillAttributeChoiceDialog(
            character = state.activeCharacter,
            skill = skill,
            onConfirm = { attribute ->
                sheetRollAttributeChoice = null
                sheetRollRequest = SheetSkillRollRequest(skill, attribute)
            },
            onDismiss = { sheetRollAttributeChoice = null },
        )
    }
    sheetRollRequest?.let { request -> SkillRollDialog(
        character = state.activeCharacter,
        skill = request.skill,
        preferredAttribute = null,
        initialAttribute = request.attribute,
        developmentCatalog = state.developmentCatalog,
        effectCatalog = state.skillEffectCatalog,
        onPreferredAttribute = {},
        onDismiss = { sheetRollRequest = null },
    ) }
    grouping?.let { kind -> GroupingManagerDialog(state, kind, onDismiss = { grouping = null }) }
}


@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CharacterHero(
    state: DesktopAppState,
    character: DublCharacter,
    extras: CharacterSheetExtras,
    economy: com.furybook.dubl.model.CharacterEconomyBreakdown,
    effectiveConditions: Set<CharacterConditionId>,
    compact: Boolean,
    wide: Boolean,
    onEditIdentity: () -> Unit,
    onEconomy: () -> Unit,
    onConditions: () -> Unit,
    onResourceDelta: (CharacterSheetResourceId, Int) -> Unit,
    onResourceVisibility: () -> Unit,
    onHealthControl: () -> Unit,
    onEditMaximum: (CharacterSheetResourceId) -> Unit,
    onEditCustomResource: (CustomResource) -> Unit,
    onCreateCustomResource: () -> Unit,
    onAttributeDelta: (AttributeId, Int) -> Unit,
    onRoll: (RollContext, AttributeId?) -> Unit,
) {
    val activeCustom = extras.customConditions.filter { it.active }
    DesktopHeroPanel(Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            when {
                compact -> {
                    DesktopHeroSection(Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            HeroPortrait(state, extras)
                            HeroIdentity(character, economy, onEditIdentity, onEconomy)
                            HeroConditions(extras, effectiveConditions, activeCustom, onConditions)
                        }
                    }
                    DesktopHeroSection(Modifier.fillMaxWidth()) {
                        HeroResources(
                            state, character, extras, true,
                            onResourceDelta, onResourceVisibility, onHealthControl,
                            onEditMaximum, onEditCustomResource, onCreateCustomResource,
                        )
                    }
                }
                wide -> {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        DesktopHeroSection(Modifier.weight(.44f)) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                verticalAlignment = Alignment.Top,
                            ) {
                                HeroPortrait(state, extras)
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    HeroIdentity(character, economy, onEditIdentity, onEconomy)
                                    HeroConditions(extras, effectiveConditions, activeCustom, onConditions)
                                }
                            }
                        }
                        DesktopHeroSection(Modifier.weight(.56f)) {
                            HeroResources(
                                state, character, extras, false,
                                onResourceDelta, onResourceVisibility, onHealthControl,
                                onEditMaximum, onEditCustomResource, onCreateCustomResource,
                            )
                        }
                    }
                }
                else -> {
                    DesktopHeroSection(Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            HeroPortrait(state, extras)
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                HeroIdentity(character, economy, onEditIdentity, onEconomy)
                                HeroConditions(extras, effectiveConditions, activeCustom, onConditions)
                            }
                        }
                    }
                    DesktopHeroSection(Modifier.fillMaxWidth()) {
                        HeroResources(
                            state, character, extras, false,
                            onResourceDelta, onResourceVisibility, onHealthControl,
                            onEditMaximum, onEditCustomResource, onCreateCustomResource,
                        )
                    }
                }
            }

            HeroTelemetry(
                character = character,
                compact = compact,
                wide = wide,
                onAttributeDelta = onAttributeDelta,
                onRoll = onRoll,
            )
        }
    }
}

@Composable
private fun HeroPortrait(state: DesktopAppState, extras: CharacterSheetExtras) {
    Surface(
        modifier = Modifier.width(132.dp).height(132.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.72f)),
    ) {
        Box(Modifier.fillMaxSize()) {
            extras.portraitUri?.let { portraitPath ->
                PortraitImage(portraitPath, Modifier.fillMaxSize())
            } ?: Column(
                Modifier.fillMaxSize().padding(bottom = 30.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                DesktopIcon(DesktopIconKind.PORTRAIT, tint = DesktopMuted, size = 50.dp)
                Spacer(Modifier.height(7.dp))
                Text("Портрет", color = DesktopMuted, style = MaterialTheme.typography.bodySmall)
            }
            Surface(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                color = DesktopSurfaceInset.copy(alpha = 0.92f),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 7.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (extras.portraitUri == null) "Добавить" else "Сменить",
                        color = DesktopAccent,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.clickable {
                            pickPortraitDataUrl { dataUrl -> state.setPortrait(dataUrl) }
                        }.padding(horizontal = 5.dp, vertical = 2.dp),
                    )
                    if (extras.portraitUri != null) {
                        Text(
                            "Убрать",
                            color = DesktopMuted,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.clickable { state.setPortrait(null) }.padding(horizontal = 5.dp, vertical = 2.dp),
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HeroIdentity(
    character: DublCharacter,
    economy: com.furybook.dubl.model.CharacterEconomyBreakdown,
    onEditIdentity: () -> Unit,
    onEconomy: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(character.name, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(character.concept.ifBlank { "Без концепта" }, color = DesktopMuted, style = MaterialTheme.typography.bodyLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("XP ${character.experience}", color = DesktopGold, fontWeight = FontWeight.Bold)
                Text("Осталось ${economy.remainingXp}", color = if (economy.overspentXp) MaterialTheme.colorScheme.error else DesktopAccent)
                Text("ОС ${economy.abilityPointsRemaining}/${economy.abilityPointsBudget}", color = DesktopGold)
                Text("Размер ${character.size} · Ног ${character.legs}", color = DesktopMuted)
                Text(if (character.creationComplete) "Создание завершено" else "Режим создания", color = DesktopMuted)
            }
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(5.dp)) {
            DesktopSmallAction("Редактировать", onEditIdentity)
            DesktopInlineAction("Расход опыта", onEconomy)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HeroConditions(
    extras: CharacterSheetExtras,
    effectiveConditions: Set<CharacterConditionId>,
    activeCustom: List<CustomCondition>,
    onConditions: () -> Unit,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        if (effectiveConditions.isEmpty() && activeCustom.isEmpty()) {
            DesktopConditionChip(label = "Нет активных состояний", onClick = onConditions)
        }
        effectiveConditions.forEach { condition ->
            val local = extras.conditionOverrides[condition]
            DesktopConditionChip(
                label = local?.title ?: condition.title,
                automatic = condition == CharacterConditionId.WEAKNESS && condition !in extras.activeConditions,
                onClick = onConditions,
            )
        }
        activeCustom.forEach { condition ->
            DesktopConditionChip(label = condition.title, onClick = onConditions)
        }
        if (effectiveConditions.isNotEmpty() || activeCustom.isNotEmpty()) {
            DesktopConditionChip(label = "+ Состояние", onClick = onConditions)
        }
    }
}

@Composable
private fun HeroResources(
    state: DesktopAppState,
    character: DublCharacter,
    extras: CharacterSheetExtras,
    compact: Boolean,
    onResourceDelta: (CharacterSheetResourceId, Int) -> Unit,
    onResourceVisibility: () -> Unit,
    onHealthControl: () -> Unit,
    onEditMaximum: (CharacterSheetResourceId) -> Unit,
    onEditCustomResource: (CustomResource) -> Unit,
    onCreateCustomResource: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember(character.id) { mutableStateOf(false) }
    val mountedResources = state.resourceMeterModels(character)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                DesktopIcon(DesktopIconKind.HEALTH, tint = DesktopHealth, size = 20.dp)
                Text("Ресурсы", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            DesktopInlineAction("Добавить ресурс", onCreateCustomResource)
            DesktopInlineAction("Показать / скрыть", onResourceVisibility)
        }

        val tileItems = mutableListOf<FcpOrderedUiItem<@Composable (Modifier) -> Unit>>()
        if (CharacterSheetResourceId.HEALTH !in extras.hiddenResourceIds) tileItems += FcpOrderedUiItem(FcpUiHostOrder.PRIMARY, "health") { tileModifier ->
            DesktopResourceTile(
                DesktopIconKind.HEALTH, "Здоровье", character.hpCurrent, character.healthMaximum, DesktopHealth,
                { onResourceDelta(CharacterSheetResourceId.HEALTH, -1) },
                { onResourceDelta(CharacterSheetResourceId.HEALTH, 1) },
                tileModifier,
                onSecondary = onHealthControl,
            )
        }
        if (CharacterSheetResourceId.ENDURANCE !in extras.hiddenResourceIds) tileItems += FcpOrderedUiItem(FcpUiHostOrder.SECONDARY, "endurance") { tileModifier ->
            DesktopResourceTile(
                DesktopIconKind.ENDURANCE, "Выносливость", character.enduranceCurrent, character.enduranceMaximum, DesktopStamina,
                { onResourceDelta(CharacterSheetResourceId.ENDURANCE, -1) },
                { onResourceDelta(CharacterSheetResourceId.ENDURANCE, 1) },
                tileModifier,
                onSecondary = { onEditMaximum(CharacterSheetResourceId.ENDURANCE) },
            )
        }
        if ((character.manaEnabled || character.effectiveManaMaximum > 0) && CharacterSheetResourceId.MANA !in extras.hiddenResourceIds) tileItems += FcpOrderedUiItem(FcpUiHostOrder.TERTIARY, "mana") { tileModifier ->
            DesktopResourceTile(
                DesktopIconKind.MANA, "Мана", character.manaCurrent, character.effectiveManaMaximum, DesktopMana,
                { onResourceDelta(CharacterSheetResourceId.MANA, -1) },
                { onResourceDelta(CharacterSheetResourceId.MANA, 1) },
                tileModifier,
                onSecondary = { onEditMaximum(CharacterSheetResourceId.MANA) },
            )
        }
        mountedResources
            .filter { model -> model.available && model.resourceId !in extras.hiddenResourceIds }
            .forEach { model ->
                val presentation = model.presentation
                tileItems += FcpOrderedUiItem(presentation.order, "fcp:${model.resourceId.name}") { tileModifier ->
                    DesktopResourceTile(
                        fcpDesktopIcon(presentation.icon),
                        presentation.label,
                        model.current,
                        model.maximum,
                        fcpDesktopAccent(presentation.accent),
                        { onResourceDelta(model.resourceId, -1) },
                        { onResourceDelta(model.resourceId, 1) },
                        tileModifier,
                        onSecondary = if (model.restoreable) {
                            { state.restoreMountedResource(model.resourceId) }
                        } else {
                            null
                        },
                    )
                }
            }
        character.customResources.forEachIndexed { index, resource ->
            tileItems += FcpOrderedUiItem(FcpUiHostOrder.CUSTOM + index, "custom:${resource.uid}") { tileModifier ->
                DesktopResourceTile(
                    DesktopIconKind.GENERIC_SKILL,
                    resource.name,
                    resource.current,
                    resource.maximum,
                    DesktopCustomResource,
                    { state.changeCustomResource(resource.uid, -1) },
                    { state.changeCustomResource(resource.uid, 1) },
                    tileModifier,
                    onSecondary = { onEditCustomResource(resource) },
                )
            }
        }

        val tiles = orderedUiItems(tileItems)
        if (tiles.isEmpty()) {
            Text("Нет видимых ресурсов", color = DesktopMuted, style = MaterialTheme.typography.bodySmall)
            return@Column
        }

        val maxVisible = if (compact) 4 else 6
        val overflow = (tiles.size - maxVisible).coerceAtLeast(0)
        val shown = if (expanded) tiles else tiles.take(maxVisible)
        val columns = if (compact) 2 else if (shown.size <= 4) shown.size.coerceAtLeast(1) else 3
        shown.chunked(columns).forEach { rowTiles ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                rowTiles.forEach { tile -> Box(Modifier.weight(1f)) { tile(Modifier.fillMaxWidth()) } }
                repeat(columns - rowTiles.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        if (overflow > 0 || expanded) {
            Text(
                if (expanded) "Свернуть" else "Ещё $overflow",
                color = DesktopAccent,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.align(Alignment.End).clickable { expanded = !expanded }.padding(horizontal = 6.dp, vertical = 3.dp),
            )
        }
    }
}

@Composable
private fun HeroTelemetry(
    character: DublCharacter,
    compact: Boolean,
    wide: Boolean,
    onAttributeDelta: (AttributeId, Int) -> Unit,
    onRoll: (RollContext, AttributeId?) -> Unit,
) {
    if (wide && !compact) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            DesktopHeroSection(Modifier.weight(.58f)) {
                HeroCharacteristicsStrip(
                    character = character,
                    compact = false,
                    wide = false,
                    embedded = true,
                    onAttributeDelta = onAttributeDelta,
                    onRoll = onRoll,
                )
            }
            DesktopHeroSection(Modifier.weight(.42f)) {
                HeroMetricsStrip(
                    character = character,
                    compact = false,
                    wide = false,
                    embedded = true,
                    onRoll = onRoll,
                )
            }
        }
    } else {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            DesktopHeroSection(Modifier.fillMaxWidth()) {
                HeroCharacteristicsStrip(
                    character = character,
                    compact = compact,
                    wide = wide,
                    onAttributeDelta = onAttributeDelta,
                    onRoll = onRoll,
                )
            }
            DesktopHeroSection(Modifier.fillMaxWidth()) {
                HeroMetricsStrip(
                    character = character,
                    compact = compact,
                    wide = wide,
                    onRoll = onRoll,
                )
            }
        }
    }
}

@Composable
private fun HeroCharacteristicsStrip(
    character: DublCharacter,
    compact: Boolean,
    wide: Boolean,
    embedded: Boolean = false,
    onAttributeDelta: (AttributeId, Int) -> Unit,
    onRoll: (RollContext, AttributeId?) -> Unit,
) {
    val order = listOf(
        AttributeId.STRENGTH,
        AttributeId.CONSTITUTION,
        AttributeId.DEXTERITY,
        AttributeId.SPEED,
        AttributeId.INTELLIGENCE,
        AttributeId.PERCEPTION,
        AttributeId.WILL,
        AttributeId.CHARISMA,
    )
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
            DesktopIcon(DesktopIconKind.STRENGTH, tint = DesktopAccent, size = 18.dp)
            Text("Характеристики", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        val columns = when {
            embedded -> 2
            compact -> 2
            wide -> 4
            else -> 2
        }
        order.chunked(columns).forEach { rowAttributes ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                rowAttributes.forEach { id ->
                    DesktopHeroAttributeCell(
                        icon = attributeIcon(id),
                        title = id.title,
                        value = character.attribute(id).toString(),
                        onRoll = { onRoll(RollContext.ATTRIBUTE, id) },
                        onMinus = { onAttributeDelta(id, -1) },
                        onPlus = { onAttributeDelta(id, 1) },
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat(columns - rowAttributes.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

private fun attributeIcon(id: AttributeId): DesktopIconKind = when (id) {
    AttributeId.STRENGTH -> DesktopIconKind.STRENGTH
    AttributeId.CONSTITUTION -> DesktopIconKind.CONSTITUTION
    AttributeId.DEXTERITY -> DesktopIconKind.DEXTERITY
    AttributeId.SPEED -> DesktopIconKind.SPEED
    AttributeId.INTELLIGENCE -> DesktopIconKind.INTELLIGENCE
    AttributeId.PERCEPTION -> DesktopIconKind.PERCEPTION
    AttributeId.WILL -> DesktopIconKind.WILL
    AttributeId.CHARISMA -> DesktopIconKind.CHARISMA
}

@Composable
private fun HeroMetricsStrip(
    character: DublCharacter,
    compact: Boolean,
    wide: Boolean,
    embedded: Boolean = false,
    onRoll: (RollContext, AttributeId?) -> Unit,
) {
    data class Metric(
        val icon: DesktopIconKind,
        val title: String,
        val value: String,
        val context: RollContext?,
    )
    val metrics = listOf(
        Metric(DesktopIconKind.DEFENSE, "Защита", character.defense.toString(), null),
        Metric(DesktopIconKind.REFLEXES, "Рефлексы", character.reflexes.toString(), RollContext.REFLEXES),
        Metric(DesktopIconKind.INITIATIVE, "Инициатива", character.initiative.toString(), RollContext.INITIATIVE),
        Metric(DesktopIconKind.FORTITUDE, "Стойкость", character.fortitude.toString(), RollContext.FORTITUDE),
        Metric(DesktopIconKind.RUN, "Бег", formatNumber(character.runFull), RollContext.RUN),
        Metric(DesktopIconKind.SIZE, "Размер", character.size.toString(), null),
    )
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
            DesktopIcon(DesktopIconKind.INITIATIVE, tint = DesktopGold, size = 18.dp)
            Text("Показатели", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        val columns = when {
            embedded -> 2
            compact -> 2
            wide -> 6
            else -> 3
        }
        metrics.chunked(columns).forEach { rowMetrics ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                rowMetrics.forEach { metric ->
                    val tint = when (metric.icon) {
                        DesktopIconKind.FORTITUDE, DesktopIconKind.RUN -> DesktopAccent
                        DesktopIconKind.INITIATIVE -> DesktopGold
                        else -> DesktopMuted
                    }
                    DesktopHeroMetricCell(
                        icon = metric.icon,
                        title = metric.title,
                        value = metric.value,
                        tint = tint,
                        onRoll = metric.context?.let { context -> { onRoll(context, null) } },
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat(columns - rowMetrics.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun SkillsDevelopmentWorkspace(
    state: DesktopAppState,
    character: DublCharacter,
    extras: CharacterSheetExtras,
    compact: Boolean,
    onSkillRoll: (ResolvedSkill) -> Unit,
    onNavigateSkills: () -> Unit,
    onGrouping: (GroupingKind) -> Unit,
    onDevelopmentDetails: (DevelopmentEntry) -> Unit,
    onNavigateDevelopment: () -> Unit,
    onNavigateMagic: () -> Unit,
) {
    if (compact) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SheetSkillsPanel(
                state, character, extras, onSkillRoll, onNavigateSkills, onGrouping,
                Modifier.fillMaxWidth(),
            )
            SheetDevelopmentPanel(
                state, character, extras, onGrouping, onDevelopmentDetails, onNavigateDevelopment, onNavigateMagic,
                Modifier.fillMaxWidth(),
            )
        }
    } else {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Box(Modifier.weight(.35f)) {
                SheetSkillsPanel(
                    state, character, extras, onSkillRoll, onNavigateSkills, onGrouping,
                    Modifier.fillMaxWidth(),
                )
            }
            Box(Modifier.weight(.65f)) {
                SheetDevelopmentPanel(
                    state, character, extras, onGrouping, onDevelopmentDetails, onNavigateDevelopment, onNavigateMagic,
                    Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun SheetSkillsPanel(
    state: DesktopAppState,
    character: DublCharacter,
    extras: CharacterSheetExtras,
    onSkillRoll: (ResolvedSkill) -> Unit,
    onOpenAll: () -> Unit,
    onGrouping: (GroupingKind) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Keep hidden skills in the grouping model so hiding/restoring a skill does not
    // silently destroy its previous group placement. Rendering still uses only visible skills.
    val allSkills = remember(character.skills, character.hiddenSkillIds) {
        character.resolvedSkills(includeHidden = true)
    }
    val visibleSkills = remember(character.skills, character.hiddenSkillIds) {
        character.resolvedSkills()
    }
    val defaults = remember(allSkills) { defaultSkillGroups(allSkills) }
    val groups = remember(extras.skillGroups, defaults, allSkills) {
        SheetGroupingRules.normalize(
            extras.skillGroups,
            defaults,
            allSkills.map { it.id },
            "skills:ungrouped",
        )
    }
    LaunchedEffect(groups, extras.skillGroups) {
        if (groups != extras.skillGroups) state.setSkillGroups(groups)
    }
    val visibleById = remember(visibleSkills) { visibleSkills.associateBy { it.id } }
    val visibleGroups = remember(groups, visibleById) {
        groups.map { group ->
            group.copy(itemIds = group.itemIds.filter(visibleById::containsKey))
        }.filter { it.itemIds.isNotEmpty() }
    }
    val effectRules = remember(
        character.development,
        character.disabledSkillEffectIds,
        state.developmentCatalog,
        state.skillEffectCatalog,
    ) {
        SkillEffectRules(character, state.developmentCatalog, state.skillEffectCatalog)
    }
    val skillPresentations = remember(
        character.attributes,
        character.size,
        character.skills,
        character.development,
        character.disabledSkillEffectIds,
        character.gear,
        visibleSkills,
        effectRules,
    ) {
        visibleSkills.associate { skill ->
            val selected = skill.stockAttribute
            val calc = character.skillCalculationForRoll(skill, selected)
            val effects = effectRules.forSkill(skill)
            val breakdownLines = buildList {
                calc.contributions.forEach { contribution ->
                    add("${contribution.label}: ${signed(contribution.value)}")
                }
                effects.automaticContributions.forEach { contribution ->
                    add("${contribution.label}: ${signed(contribution.value)}")
                }
                if (calc.unavailableReason.isNotBlank()) add(calc.unavailableReason)
            }
            skill.id to SheetSkillPresentation(
                bonus = calc.total?.plus(effects.automaticBonus)?.let(::signed) ?: "—",
                breakdownLines = breakdownLines,
            )
        }
    }

    DesktopPanel(modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            DesktopSectionHeader(
                "Умения",
                icon = DesktopIconKind.SKILLS,
                action = {
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                        DesktopInlineAction("Настроить группы", { onGrouping(GroupingKind.SKILLS) })
                        DesktopInlineAction("Все умения →", onOpenAll)
                    }
                },
            )
            if (visibleSkills.isEmpty()) {
                EmptyState("Все умения скрыты. Вернуть их можно на экране «Умения».")
            } else {
                visibleGroups.forEach { group ->
                    val groupSkills = group.itemIds.mapNotNull(visibleById::get)
                    SheetGroupHeaderCompact(
                        title = group.title,
                        count = groupSkills.size,
                        collapsed = group.collapsed,
                        onToggle = {
                            state.setSkillGroups(SheetGroupingRules.toggleCollapsed(groups, group.id))
                        },
                    )
                    AnimatedVisibility(
                        visible = !group.collapsed,
                        enter = expandVertically(tween(FuryMotion.StandardMs)) + fadeIn(tween(FuryMotion.FastMs)),
                        exit = shrinkVertically(tween(FuryMotion.StandardMs)) + fadeOut(tween(FuryMotion.FastMs)),
                    ) {
                        BoxWithConstraints(Modifier.fillMaxWidth()) {
                            if (maxWidth < 560.dp) {
                                Column(
                                    Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    groupSkills.forEach { skill ->
                                        val presentation = skillPresentations.getValue(skill.id)
                                        DesktopSkillRow(
                                            icon = skillIcon(skill.category),
                                            title = skill.name,
                                            rank = skill.rank,
                                            bonus = presentation.bonus,
                                            breakdownLines = presentation.breakdownLines,
                                            onRoll = { onSkillRoll(skill) },
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                    }
                                }
                            } else {
                                val (left, right) = SheetGroupingRules.balancedColumns(groupSkills) { skill ->
                                    when {
                                        skill.name.length >= 34 -> 3
                                        skill.name.length >= 20 -> 2
                                        else -> 1
                                    }
                                }
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                                    verticalAlignment = Alignment.Top,
                                ) {
                                    listOf(left, right).forEach { columnSkills ->
                                        Column(
                                            Modifier.weight(1f),
                                            verticalArrangement = Arrangement.spacedBy(6.dp),
                                        ) {
                                            columnSkills.forEach { skill ->
                                                val presentation = skillPresentations.getValue(skill.id)
                                                DesktopSkillRow(
                                                    icon = skillIcon(skill.category),
                                                    title = skill.name,
                                                    rank = skill.rank,
                                                    bonus = presentation.bonus,
                                                    breakdownLines = presentation.breakdownLines,
                                                    onRoll = { onSkillRoll(skill) },
                                                    modifier = Modifier.fillMaxWidth(),
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}


private fun skillIcon(category: SkillCategory): DesktopIconKind = when (category) {
    SkillCategory.COMBAT -> DesktopIconKind.DEFENSE
    SkillCategory.PHYSICAL -> DesktopIconKind.RUN
    SkillCategory.FIELD -> DesktopIconKind.PERCEPTION
    SkillCategory.SOCIAL -> DesktopIconKind.CHARISMA
    SkillCategory.KNOWLEDGE -> DesktopIconKind.INTELLIGENCE
    SkillCategory.TECHNICAL -> DesktopIconKind.EQUIPMENT
    SkillCategory.CUSTOM -> DesktopIconKind.GENERIC_SKILL
}

@Composable
private fun SheetGroupHeaderCompact(
    title: String,
    count: Int,
    collapsed: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth().clickable(onClick = onToggle),
        shape = RoundedCornerShape(7.dp),
        color = DesktopSurfaceRaised.copy(alpha = .36f),
        border = BorderStroke(1.dp, DesktopBorder.copy(alpha = .46f)),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 9.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Text(if (collapsed) "▸" else "▾", color = DesktopAccent, fontWeight = FontWeight.Bold)
            Text(title, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(count.toString(), color = DesktopMuted, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun DevelopmentBranchConnector(modifier: Modifier = Modifier) {
    Canvas(modifier.width(16.dp).height(22.dp)) {
        val stroke = 1.25.dp.toPx()
        val x = 4.dp.toPx()
        val mid = size.height * .5f
        drawLine(
            color = DesktopBorder.copy(alpha = .95f),
            start = androidx.compose.ui.geometry.Offset(x, 0f),
            end = androidx.compose.ui.geometry.Offset(x, mid),
            strokeWidth = stroke,
        )
        drawLine(
            color = DesktopBorder.copy(alpha = .95f),
            start = androidx.compose.ui.geometry.Offset(x, mid),
            end = androidx.compose.ui.geometry.Offset(size.width, mid),
            strokeWidth = stroke,
        )
    }
}

@Composable
private fun DevelopmentTreeRow(
    item: com.furybook.dubl.model.DevelopmentSheetItem,
    displayDepth: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val child = displayDepth > 0
    val indent = (displayDepth.coerceAtMost(3) * 10).dp
    Surface(
        modifier = modifier.fillMaxWidth().padding(start = if (child) 8.dp else 0.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(if (child) 6.dp else 8.dp),
        color = if (child) DesktopSurfaceInset.copy(alpha = .46f) else DesktopSurfaceInset.copy(alpha = .72f),
        border = if (child) null else BorderStroke(1.dp, DesktopBorder.copy(alpha = .62f)),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(
                start = 7.dp + indent,
                end = 9.dp,
                top = if (child) 3.dp else 5.dp,
                bottom = if (child) 3.dp else 5.dp,
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (child) 5.dp else 7.dp),
        ) {
            if (child) DevelopmentBranchConnector()
            DesktopIcon(
                DesktopIconKind.DEVELOPMENT,
                tint = if (child) DesktopMuted.copy(alpha = .82f) else DesktopGold,
                size = if (child) 16.dp else 18.dp,
            )
            Text(
                item.entry.name,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (child) DesktopText.copy(alpha = .90f) else DesktopText,
                style = if (child) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
                fontWeight = if (child) FontWeight.Medium else FontWeight.SemiBold,
            )
            Text(
                rankLabel(item.rank),
                color = DesktopAccent,
                fontWeight = FontWeight.Bold,
                style = if (child) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun SheetDevelopmentPanel(
    state: DesktopAppState,
    character: DublCharacter,
    extras: CharacterSheetExtras,
    onGrouping: (GroupingKind) -> Unit,
    onDevelopmentDetails: (DevelopmentEntry) -> Unit,
    onNavigateDevelopment: () -> Unit,
    onNavigateMagic: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val developmentItems = remember(
        character.development,
        character.developmentOverrides,
        character.customDevelopmentEntries,
        character.magic.schools,
        state.developmentCatalog,
    ) {
        character.characterSheetDevelopmentSections(state.developmentCatalog)
            .flatMap { it.items }
            .distinctBy { it.entry.id }
    }
    val developmentDefaults = defaultDevelopmentGroups(character, state)
    val developmentIds = developmentItems.map { it.entry.id }
    val developmentGroups = SheetGroupingRules.normalize(
        ensureMagicSchoolsInSpecialGroup(extras.developmentGroups, developmentDefaults, developmentIds),
        developmentDefaults,
        developmentIds,
        "development:ungrouped",
    )
    LaunchedEffect(developmentGroups, extras.developmentGroups) {
        if (developmentGroups != extras.developmentGroups) state.setDevelopmentGroups(developmentGroups)
    }
    val developmentById = developmentItems.associateBy { it.entry.id }
    val parentById = developmentItems.associate { it.entry.id to it.parentId }

    DesktopPanel(modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            DesktopSectionHeader(
                "Навыки и развитие",
                subtitle = "Профессиональные навыки, особенности и пути развития",
                icon = DesktopIconKind.DEVELOPMENT,
                action = {
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                        DesktopInlineAction("Настроить группы", { onGrouping(GroupingKind.DEVELOPMENT) })
                        DesktopInlineAction("Все навыки →", onNavigateDevelopment)
                    }
                },
            )
            if (developmentItems.isEmpty()) {
                EmptyState("Взятых навыков и боевых искусств пока нет.")
            } else {
                developmentGroups.forEach { group ->
                    val orderedIds = SheetGroupingRules.hierarchicalOrder(group.itemIds, parentById)
                    val groupItems = orderedIds.mapNotNull(developmentById::get)
                    if (groupItems.isNotEmpty()) {
                        SheetGroupHeaderCompact(
                            title = group.title,
                            count = groupItems.size,
                            collapsed = group.collapsed,
                            onToggle = {
                                state.setDevelopmentGroups(SheetGroupingRules.toggleCollapsed(developmentGroups, group.id))
                            },
                        )
                        AnimatedVisibility(
                            visible = !group.collapsed,
                            enter = expandVertically(tween(FuryMotion.StandardMs)) + fadeIn(tween(FuryMotion.FastMs)),
                            exit = shrinkVertically(tween(FuryMotion.StandardMs)) + fadeOut(tween(FuryMotion.FastMs)),
                        ) {
                            val groupIds = groupItems.map { it.entry.id }
                            val blocks = SheetGroupingRules.hierarchyBlocks(groupIds, parentById)
                                .map { ids -> ids.mapNotNull(developmentById::get) }
                            val (leftBlocks, rightBlocks) = SheetGroupingRules.balancedColumns(blocks) { block ->
                                block.sumOf { item ->
                                    1 + SheetGroupingRules.localDepth(item.entry.id, groupIds, parentById).coerceAtMost(1)
                                }
                            }
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(9.dp),
                                verticalAlignment = Alignment.Top,
                            ) {
                                listOf(leftBlocks, rightBlocks).forEach { columnBlocks ->
                                    Column(
                                        Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        columnBlocks.forEach { block ->
                                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                block.forEach { item ->
                                                    DevelopmentTreeRow(
                                                        item = item,
                                                        displayDepth = SheetGroupingRules.localDepth(item.entry.id, groupIds, parentById),
                                                        onClick = {
                                                            if (isMagicSchoolSheetEntryId(item.entry.id)) onNavigateMagic()
                                                            else onDevelopmentDetails(item.entry)
                                                        },
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}


@Composable
private fun QuickCombatActionsPanel(
    onRoll: (RollContext) -> Unit,
    modifier: Modifier = Modifier,
) {
    val contexts = listOf(
        RollContext.DODGE,
        RollContext.ATTACK,
        RollContext.FLURRY,
        RollContext.PARRY,
        RollContext.FEINT,
        RollContext.GRAPPLE,
        RollContext.DISARM,
        RollContext.TRIP,
        RollContext.PUSH,
        RollContext.KNOCKDOWN,
        RollContext.BREAK_ITEM,
    )
    DesktopPanel(modifier) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            DesktopSectionHeader(
                "Боевые действия",
                subtitle = "Быстрые проверки без отдельного боевого состояния",
                icon = DesktopIconKind.DEFENSE,
            )
            contexts.chunked(4).forEach { rowContexts ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    rowContexts.forEach { context ->
                        OutlinedButton(
                            onClick = { onRoll(context) },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(context.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    repeat(4 - rowContexts.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

private fun rankLabel(rank: Int): String = when (rank) {
    1 -> "I"
    2 -> "II"
    3 -> "III"
    4 -> "IV"
    5 -> "V"
    else -> rank.toString()
}

@Composable
private fun NotesPanel(
    notes: List<CharacterNote>,
    onAdd: () -> Unit,
    onEdit: (CharacterNote) -> Unit,
    onDelete: (CharacterNote) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expandedNoteIds by remember(notes.map { it.id }) {
        mutableStateOf<Set<String>>(notes.mapTo(linkedSetOf()) { it.id })
    }
    DesktopPanel(modifier) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            DesktopSectionHeader(
                "Заметки",
                icon = DesktopIconKind.NOTES,
                action = { DesktopSmallAction("Добавить заметку", onAdd) },
            )
            if (notes.isEmpty()) {
                EmptyState("Заметок пока нет.")
            } else {
                notes.forEach { note ->
                    val expanded = note.id in expandedNoteIds
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(9.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .16f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .42f)),
                    ) {
                        Column(Modifier.fillMaxWidth()) {
                            Row(
                                Modifier.fillMaxWidth()
                                    .clickable {
                                        expandedNoteIds = if (expanded) expandedNoteIds - note.id else expandedNoteIds + note.id
                                    }
                                    .padding(horizontal = 11.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(if (expanded) "▾" else "▸", color = DesktopAccent, fontWeight = FontWeight.Bold)
                                DesktopIcon(DesktopIconKind.NOTES, tint = DesktopMuted, size = 17.dp)
                                Text(
                                    note.title,
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                DesktopInlineAction("Изменить", { onEdit(note) })
                                DesktopInlineAction("Удалить", { onDelete(note) })
                            }
                            if (expanded) {
                                Surface(
                                    modifier = Modifier.fillMaxWidth().padding(start = 11.dp, end = 11.dp, bottom = 10.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    color = DesktopSurfaceInset.copy(alpha = .54f),
                                ) {
                                    Text(
                                        note.body.ifBlank { "Пустая заметка." },
                                        color = if (note.body.isBlank()) DesktopMuted else MaterialTheme.colorScheme.onSurface,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.fillMaxWidth().padding(11.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NoteEditorDialog(
    title: String,
    initial: CharacterNote?,
    onSave: (String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var noteTitle by remember(initial?.id) { mutableStateOf(initial?.title.orEmpty()) }
    var body by remember(initial?.id) { mutableStateOf(initial?.body.orEmpty()) }
    FuryDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = noteTitle,
                    onValueChange = { noteTitle = it.take(120) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Заголовок") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = body,
                    onValueChange = { body = it.take(12000) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 200.dp),
                    label = { Text("Текст заметки") },
                    minLines = 8,
                    maxLines = 24,
                )
            }
        },
        confirmButton = {
            Button(
                enabled = noteTitle.isNotBlank(),
                onClick = { onSave(noteTitle.trim(), body) },
            ) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

@Composable
private fun HealthControlDialog(
    current: Int,
    maximum: Int,
    onChange: (Int) -> Unit,
    onEditMaximum: () -> Unit,
    onDismiss: () -> Unit,
) {
    var amountText by remember(current, maximum) { mutableStateOf("1") }
    val amount = amountText.toIntOrNull()?.coerceAtLeast(0) ?: 0
    FuryDialog(
        onDismissRequest = onDismiss,
        title = { Text("Здоровье · $current / $maximum") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it.filter(Char::isDigit).take(5) },
                    label = { Text("Количество урона / лечения") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        enabled = amount > 0,
                        onClick = { onChange(-amount); onDismiss() },
                        modifier = Modifier.weight(1f),
                    ) { Text("Получить урон") }
                    OutlinedButton(
                        enabled = amount > 0 && current < maximum,
                        onClick = { onChange(amount); onDismiss() },
                        modifier = Modifier.weight(1f),
                    ) { Text("Лечение") }
                }
                TextButton(
                    enabled = current < maximum,
                    onClick = { onChange(maximum - current); onDismiss() },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Восстановить всё здоровье") }
                TextButton(onClick = onEditMaximum, modifier = Modifier.fillMaxWidth()) { Text("Изменить максимум здоровья") }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } },
    )
}

@Composable
private fun GroupHeader(group: SheetGroup, count: Int, onToggle: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text("${group.title} · $count", color = DesktopAccent, fontWeight = FontWeight.Bold)
        TextButton(onClick = onToggle) { Text(if (group.collapsed) "Развернуть" else "Свернуть") }
    }
}

@Composable
private fun IdentityDialog(state: DesktopAppState, character: DublCharacter, onDismiss: () -> Unit, onChanged: (DublCharacter) -> Unit) {
    var name by remember(character.id) { mutableStateOf(character.name) }
    var concept by remember(character.id) { mutableStateOf(character.concept) }
    var size by remember(character.id) { mutableStateOf(character.size.toString()) }
    var legs by remember(character.id) { mutableStateOf(character.legs.toString()) }
    var manaEnabled by remember(character.id) { mutableStateOf(character.manaEnabled) }
    FuryDialog(
        onDismissRequest = onDismiss,
        title = { Text("Персонаж") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Имя") })
                OutlinedTextField(concept, { concept = it }, label = { Text("Концепт") })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(size, { size = it.filter(Char::isDigit).take(2) }, label = { Text("Размер") }, modifier = Modifier.weight(1f))
                    OutlinedTextField(legs, { legs = it.filter(Char::isDigit).take(2) }, label = { Text("Ноги") }, modifier = Modifier.weight(1f))
                }
                Row(verticalAlignment = Alignment.CenterVertically) { Switch(manaEnabled, { manaEnabled = it }); Text("Использовать ману") }
            }
        },
        confirmButton = { TextButton(onClick = {
            val before = character
            state.setIdentity(
                name = name,
                concept = concept,
                size = size.toIntOrNull() ?: 5,
                legs = legs.toIntOrNull() ?: 2,
                manaEnabled = manaEnabled,
            )
            onChanged(before); onDismiss()
        }) { Text("Сохранить") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

@Composable
private fun EconomySummaryCard(
    title: String,
    rows: List<Pair<String, String>>,
    warning: Boolean = false,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(9.dp),
        color = if (warning) DesktopAccentSoft.copy(alpha = .42f) else DesktopSurfaceInset.copy(alpha = .68f),
        border = BorderStroke(1.dp, if (warning) DesktopAccent.copy(alpha = .52f) else DesktopBorder.copy(alpha = .62f)),
    ) {
        Column(Modifier.padding(horizontal = 11.dp, vertical = 9.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(title, fontWeight = FontWeight.Bold, color = if (warning) DesktopAccent else DesktopText)
            rows.forEach { (label, value) ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(label, color = DesktopMuted, style = MaterialTheme.typography.bodySmall)
                    Text(
                        value,
                        color = if (warning) MaterialTheme.colorScheme.error else DesktopText,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun EconomyDialog(state: DesktopAppState, onDismiss: () -> Unit) {
    val character = state.activeCharacter
    val showChiEconomy = state.uiMounts(FcpUiSurface.CHARACTER_ECONOMY, FcpUiComponent.XP_LINE).forFeature(DublUiFeature.CHI) != null
    val economy = CharacterEconomy.breakdown(character, state.developmentCatalog, includeChi = showChiEconomy)
    var total by remember(character.id) { mutableStateOf(character.experience.toString()) }
    var creation by remember(character.id) { mutableStateOf(character.effectiveCreationExperience.toString()) }
    var adjustment by remember(character.id) { mutableStateOf(character.xpAdjustment.toString()) }
    var abilityOverride by remember(character.id) { mutableStateOf(character.abilityPointsOverride?.toString().orEmpty()) }
    FuryDialog(
        onDismissRequest = onDismiss,
        title = { Text("Опыт и создание") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        total,
                        { total = it.filter(Char::isDigit).take(8) },
                        label = { Text("Общий опыт") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        creation,
                        { creation = it.filter(Char::isDigit).take(8) },
                        label = { Text("Стартовый опыт") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        adjustment,
                        { adjustment = it.filter { c -> c.isDigit() || c == '-' }.take(9) },
                        label = { Text("Корректировка опыта") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        abilityOverride,
                        { abilityOverride = it.filter(Char::isDigit).take(5) },
                        label = { Text("Очки способностей вручную") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                }
                Text(
                    "Оставьте поле очков способностей пустым, чтобы использовать автоматический расчёт от стартового опыта.",
                    color = DesktopMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(9.dp),
                    color = DesktopSurfaceInset.copy(alpha = .52f),
                    border = BorderStroke(1.dp, DesktopBorder.copy(alpha = .52f)),
                ) {
                    Column(Modifier.padding(horizontal = 11.dp, vertical = 9.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Распределение опыта", fontWeight = FontWeight.Bold)
                        Text(
                            "Характеристики ${economy.attributeXp} · Умения ${economy.skillXp} · Навыки ${economy.developmentXp}",
                            color = DesktopMuted,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            buildList {
                                add("Мана ${economy.manaXp}")
                                if (showChiEconomy) add("ЦИ ${economy.chiXp}")
                                add("Школы ${economy.magicSchoolXp}")
                                add("Заклинания ${economy.spellXp}")
                            }.joinToString(" · "),
                            color = DesktopMuted,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                EconomySummaryCard(
                    title = "Бюджет опыта",
                    rows = listOf(
                        "Потрачено" to economy.spentXp.toString(),
                        "Осталось" to economy.remainingXp.toString(),
                    ),
                    warning = economy.overspentXp,
                )
                EconomySummaryCard(
                    title = "Очки способностей",
                    rows = listOf(
                        "Потрачено" to "${economy.abilityPointsSpent} из ${economy.abilityPointsBudget}",
                        "Осталось" to economy.abilityPointsRemaining.toString(),
                    ),
                    warning = economy.overspentAbilityPoints,
                )
                if (economy.unpricedLearnedSpells > 0) {
                    Text("Заклинаний без цены опыта: ${economy.unpricedLearnedSpells}", color = DesktopGold)
                }
                if (character.creationComplete) {
                    OutlinedButton(onClick = { state.reopenCreation() }, modifier = Modifier.fillMaxWidth()) {
                        Text("Вернуться в создание")
                    }
                } else {
                    Button(onClick = { state.completeCreation() }, modifier = Modifier.fillMaxWidth()) {
                        Text("Завершить создание")
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = {
            state.setEconomy(
                total = total.toIntOrNull() ?: 0,
                creation = creation.toIntOrNull() ?: 0,
                adjustment = adjustment.toIntOrNull() ?: 0,
                abilityPointsOverride = abilityOverride.toIntOrNull(),
            )
            onDismiss()
        }) { Text("Сохранить") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } },
    )
}

@Composable
private fun MaximumDialog(state: DesktopAppState, resource: CharacterSheetResourceId, onDismiss: () -> Unit) {
    val character = state.activeCharacter
    val current = when (resource) {
        CharacterSheetResourceId.HEALTH -> character.healthMaximumOverride ?: character.healthMaximum
        CharacterSheetResourceId.ENDURANCE -> character.enduranceMaximumOverride ?: character.enduranceMaximum
        CharacterSheetResourceId.MANA -> character.manaMaximumOverride ?: character.effectiveManaMaximum
        else -> return
    }
    var text by remember(resource) { mutableStateOf(current.toString()) }
    FuryDialog(
        onDismissRequest = onDismiss,
        title = { Text("Максимум: ${resource.title}") },
        text = { OutlinedTextField(text, { text = it.filter(Char::isDigit).take(5) }, label = { Text("Ручной максимум") }) },
        confirmButton = { TextButton(onClick = {
            val value = text.toIntOrNull()?.coerceAtLeast(0) ?: 0
            when (resource) {
                CharacterSheetResourceId.HEALTH -> state.setHealthMaximumOverride(value)
                CharacterSheetResourceId.ENDURANCE -> state.setEnduranceMaximumOverride(value)
                CharacterSheetResourceId.MANA -> state.setManaMaximumOverride(value)
                else -> Unit
            }
            onDismiss()
        }) { Text("Сохранить") } },
        dismissButton = {
            Row {
                TextButton(onClick = {
                    when (resource) {
                        CharacterSheetResourceId.HEALTH -> state.setHealthMaximumOverride(null)
                        CharacterSheetResourceId.ENDURANCE -> state.setEnduranceMaximumOverride(null)
                        CharacterSheetResourceId.MANA -> state.setManaMaximumOverride(null)
                        else -> Unit
                    }
                    onDismiss()
                }) { Text("По формуле") }
                TextButton(onClick = onDismiss) { Text("Отмена") }
            }
        },
    )
}

@Composable
private fun CustomResourceDialog(state: DesktopAppState, resource: CustomResource?, onDismiss: () -> Unit) {
    var name by remember(resource?.uid) { mutableStateOf(resource?.name.orEmpty()) }
    var current by remember(resource?.uid) { mutableStateOf((resource?.current ?: 0).toString()) }
    var maximum by remember(resource?.uid) { mutableStateOf((resource?.maximum ?: 1).toString()) }
    FuryDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (resource == null) "Новый ресурс" else resource.name) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(7.dp)) { OutlinedTextField(name, { name = it }, label = { Text("Название") }); OutlinedTextField(current, { current = it.filter(Char::isDigit) }, label = { Text("Текущее") }); OutlinedTextField(maximum, { maximum = it.filter(Char::isDigit) }, label = { Text("Максимум") }) } },
        confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { val max = maximum.toIntOrNull()?.coerceAtLeast(0) ?: 0; val cur = current.toIntOrNull()?.coerceIn(0, max) ?: 0; if (resource == null) state.addCustomResource(name, max, cur) else state.updateCustomResource(resource.uid, name, cur, max); onDismiss() }) { Text("Сохранить") } },
        dismissButton = { Row { if (resource != null) TextButton(onClick = { state.removeCustomResource(resource.uid); onDismiss() }) { Text("Удалить") }; TextButton(onClick = onDismiss) { Text("Отмена") } } },
    )
}

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
private fun ConditionRow(
    title: String,
    description: String,
    active: Boolean,
    marker: String? = null,
    onActiveChange: (Boolean) -> Unit,
    onEdit: () -> Unit,
) {
    var hovered by remember(title) { mutableStateOf(false) }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .pointerMoveFilter(
                onEnter = { hovered = true; false },
                onExit = { hovered = false; false },
            ),
        shape = RoundedCornerShape(8.dp),
        color = if (active) DesktopAccentSoft.copy(alpha = .34f) else DesktopSurfaceInset.copy(alpha = .36f),
        border = BorderStroke(1.dp, if (active) DesktopAccent.copy(alpha = .38f) else DesktopBorder.copy(alpha = .38f)),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 7.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Checkbox(
                checked = active,
                onCheckedChange = onActiveChange,
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(title, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    if (!marker.isNullOrBlank()) {
                        Text(marker, color = DesktopGold, style = MaterialTheme.typography.labelMedium)
                    }
                }
                if (description.isNotBlank()) {
                    Text(
                        description,
                        color = DesktopMuted,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Box(Modifier.width(86.dp), contentAlignment = Alignment.CenterEnd) {
                if (hovered || active) {
                    TextButton(onClick = onEdit) { Text("Изменить") }
                }
            }
        }
    }
}

@Composable
private fun ConditionsDialog(state: DesktopAppState, onDismiss: () -> Unit, onChanged: (Set<CharacterConditionId>) -> Unit) {
    val previous = state.extras.activeConditions
    var editCondition by remember { mutableStateOf<CharacterConditionId?>(null) }
    var editCustomId by remember { mutableStateOf<String?>(null) }
    var createCustom by remember { mutableStateOf(false) }
    FuryDialog(
        onDismissRequest = onDismiss,
        title = { Text("Состояния") },
        text = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                CharacterConditionId.entries.forEach { condition ->
                    val local = state.extras.conditionOverrides[condition]
                    ConditionRow(
                        title = local?.title ?: condition.title,
                        description = local?.description ?: state.conditionCatalog.summary(condition),
                        active = condition in state.extras.activeConditions,
                        marker = if (local != null) "локальная правка" else null,
                        onActiveChange = { state.toggleCondition(condition) },
                        onEdit = { editCondition = condition },
                    )
                }
                if (state.extras.customConditions.isNotEmpty()) {
                    Text(
                        "Свои состояния",
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                    )
                    state.extras.customConditions.forEach { condition ->
                        ConditionRow(
                            title = condition.title,
                            description = condition.description,
                            active = condition.active,
                            marker = "своё",
                            onActiveChange = { checked -> state.setCustomConditionActive(condition.id, checked) },
                            onEdit = { editCustomId = condition.id },
                        )
                    }
                }
                OutlinedButton(onClick = { createCustom = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("+ Добавить своё состояние")
                }
            }
        },
        confirmButton = { TextButton(onClick = { onChanged(previous); onDismiss() }) { Text("Готово") } },
    )

    editCondition?.let { condition ->
        val local = state.extras.conditionOverrides[condition]
        ConditionOverrideDialog(
            canonicalTitle = condition.title,
            canonicalDescription = state.conditionCatalog.summary(condition),
            title = local?.title ?: condition.title,
            description = local?.description ?: state.conditionCatalog.summary(condition),
            onSave = { title, description ->
                state.setConditionOverride(condition, title, description)
                editCondition = null
            },
            onReset = {
                state.resetConditionOverride(condition)
                editCondition = null
            },
            onDismiss = { editCondition = null },
        )
    }

    if (createCustom) {
        CustomConditionDialog(
            condition = null,
            onSave = { title, description, active ->
                state.addCustomCondition(title, description, active)
                createCustom = false
            },
            onDelete = {},
            onDismiss = { createCustom = false },
        )
    }

    editCustomId?.let { id ->
        state.extras.customConditions.firstOrNull { it.id == id }?.let { condition ->
            CustomConditionDialog(
                condition = condition,
                onSave = { title, description, active ->
                    state.updateCustomCondition(id, title, description, active)
                    editCustomId = null
                },
                onDelete = {
                    state.removeCustomCondition(id)
                    editCustomId = null
                },
                onDismiss = { editCustomId = null },
            )
        }
    }
}

@Composable
private fun ConditionOverrideDialog(
    canonicalTitle: String,
    canonicalDescription: String,
    title: String,
    description: String,
    onSave: (String, String) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    var localTitle by remember(title) { mutableStateOf(title) }
    var localDescription by remember(description) { mutableStateOf(description) }
    FuryDialog(
        onDismissRequest = onDismiss,
        title = { Text("Локальная правка состояния") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Канон остаётся неизменным. Эта трактовка хранится только у персонажа.", color = DesktopMuted)
                OutlinedTextField(localTitle, { localTitle = it }, label = { Text("Название") }, singleLine = true)
                OutlinedTextField(localDescription, { localDescription = it }, label = { Text("Описание / трактовка") })
                Text("Рулбук: $canonicalTitle", color = DesktopMuted, fontSize = 11.sp)
                if (canonicalDescription.isNotBlank()) Text(canonicalDescription, color = DesktopMuted, fontSize = 11.sp)
            }
        },
        confirmButton = { TextButton(enabled = localTitle.isNotBlank(), onClick = { onSave(localTitle, localDescription) }) { Text("Сохранить") } },
        dismissButton = {
            Row {
                TextButton(onClick = onReset) { Text("К рулбуку") }
                TextButton(onClick = onDismiss) { Text("Отмена") }
            }
        },
    )
}

@Composable
private fun CustomConditionDialog(
    condition: CustomCondition?,
    onSave: (String, String, Boolean) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var title by remember(condition?.id) { mutableStateOf(condition?.title.orEmpty()) }
    var description by remember(condition?.id) { mutableStateOf(condition?.description.orEmpty()) }
    var active by remember(condition?.id) { mutableStateOf(condition?.active ?: false) }
    FuryDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (condition == null) "Добавить своё состояние" else "Изменить своё состояние") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("Название") }, singleLine = true)
                OutlinedTextField(description, { description = it }, label = { Text("Описание / домашнее правило") })
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(active, { active = it })
                    Text("Активно")
                }
            }
        },
        confirmButton = { TextButton(enabled = title.isNotBlank(), onClick = { onSave(title, description, active) }) { Text("Сохранить") } },
        dismissButton = {
            Row {
                if (condition != null) TextButton(onClick = onDelete) { Text("Удалить") }
                TextButton(onClick = onDismiss) { Text("Отмена") }
            }
        },
    )
}

@Composable
private fun ResourceVisibilityDialog(state: DesktopAppState, onDismiss: () -> Unit) {
    val mountedToggles = state.resourceToggleModels()
    FuryDialog(
        onDismissRequest = onDismiss,
        title = { Text("Видимость ресурсов") },
        text = {
            Column {
                listOf(
                    CharacterSheetResourceId.HEALTH,
                    CharacterSheetResourceId.ENDURANCE,
                    CharacterSheetResourceId.MANA,
                ).forEach { resource ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        val hidden = resource in state.extras.hiddenResourceIds
                        Checkbox(!hidden, { visible -> state.setResourceHidden(resource, !visible) })
                        Text(resource.title)
                    }
                }
                mountedToggles.forEach { model ->
                    val presentation = model.presentation
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        val hidden = model.resourceId in state.extras.hiddenResourceIds
                        Checkbox(
                            checked = model.available && !hidden,
                            onCheckedChange = { visible -> state.setResourceHidden(model.resourceId, !visible) },
                            enabled = model.available,
                        )
                        presentation.icon?.let { token ->
                            DesktopIcon(
                                kind = fcpDesktopIcon(token),
                                tint = DesktopAccent,
                                size = 16.dp,
                            )
                        }
                        Text(presentation.label)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Готово") } },
    )
}

@Composable
private fun GroupingDragHandle(
    modifier: Modifier = Modifier,
    active: Boolean = false,
) {
    Surface(
        modifier = modifier.size(28.dp),
        shape = RoundedCornerShape(7.dp),
        color = if (active) DesktopAccentSoft.copy(alpha = .82f) else DesktopSurfaceInset.copy(alpha = .88f),
        border = BorderStroke(1.dp, if (active) DesktopAccent.copy(alpha = .72f) else DesktopBorder.copy(alpha = .68f)),
    ) {
        Canvas(Modifier.fillMaxSize().padding(8.dp)) {
            val dot = 1.35.dp.toPx()
            val xs = listOf(size.width * .30f, size.width * .70f)
            val ys = listOf(size.height * .16f, size.height * .50f, size.height * .84f)
            xs.forEach { x -> ys.forEach { y -> drawCircle(if (active) DesktopAccent else DesktopMuted, dot, androidx.compose.ui.geometry.Offset(x, y)) } }
        }
    }
}

@Composable
private fun GroupCountBadge(count: Int) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = DesktopSurfaceInset.copy(alpha = .76f),
        border = BorderStroke(1.dp, DesktopBorder.copy(alpha = .58f)),
    ) {
        Text(
            count.toString(),
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
            color = DesktopMuted,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun GroupActionMenu(
    canDelete: Boolean,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        DesktopTinyButton("⋯", { expanded = true }, Modifier.size(28.dp))
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Переименовать") },
                onClick = { expanded = false; onRename() },
            )
            if (canDelete) {
                DropdownMenuItem(
                    text = { Text("Удалить группу") },
                    onClick = { expanded = false; onDelete() },
                )
            }
        }
    }
}

@Composable
private fun GroupingManagerDialog(state: DesktopAppState, kind: GroupingKind, onDismiss: () -> Unit) {
    val character = state.activeCharacter
    val skillItems = character.resolvedSkills(includeHidden = true)
    val developmentRules = DevelopmentRules(character, state.developmentCatalog, DevelopmentProgress(character.development))
    val developmentItems = developmentRules.ownedSheetSections().flatMap { it.items }.distinctBy { it.entry.id }
    val labels = if (kind == GroupingKind.SKILLS) {
        skillItems.associate { skill ->
            skill.id to if (skill.id in character.hiddenSkillIds) "${skill.name} · скрыто" else skill.name
        }
    } else {
        developmentItems.associate { it.entry.id to it.entry.name }
    }
    val parentById = if (kind == GroupingKind.DEVELOPMENT) developmentItems.associate { it.entry.id to it.parentId } else emptyMap()
    val defaults = if (kind == GroupingKind.SKILLS) defaultSkillGroups(skillItems) else defaultDevelopmentGroups(character, state)
    val ungroupedId = if (kind == GroupingKind.SKILLS) "skills:ungrouped" else "development:ungrouped"
    var groups by remember(character.id, kind) {
        mutableStateOf(
            SheetGroupingRules.normalize(
                if (kind == GroupingKind.SKILLS) state.extras.skillGroups else state.extras.developmentGroups,
                defaults,
                labels.keys.toList(),
                ungroupedId,
            ),
        )
    }
    var newName by remember { mutableStateOf("") }
    var creatingGroup by remember { mutableStateOf(false) }
    var editingGroupId by remember { mutableStateOf<String?>(null) }
    var editingGroupName by remember { mutableStateOf("") }
    var hoveredGroupId by remember { mutableStateOf<String?>(null) }
    val groupBounds = remember(character.id, kind) { mutableMapOf<String, Rect>() }
    val itemBounds = remember(character.id, kind) { mutableMapOf<String, Rect>() }
    val listState = rememberLazyListState()
    val dragScope = rememberCoroutineScope()
    val density = LocalDensity.current
    var listBounds by remember(character.id, kind) { mutableStateOf(Rect.Zero) }

    fun persist(next: List<SheetGroup>) {
        groups = next
        if (kind == GroupingKind.SKILLS) state.setSkillGroups(next) else state.setDevelopmentGroups(next)
    }

    val visualGroups = groups.map { group ->
        if (kind == GroupingKind.DEVELOPMENT) group.copy(itemIds = SheetGroupingRules.hierarchicalOrder(group.itemIds, parentById)) else group
    }
    val allVisualIds = visualGroups.flatMap { it.itemIds }
    val treeRootIds = parentById.keys.filterTo(mutableSetOf()) { id -> parentById[id] == null && parentById.values.any { it == id } }

    fun autoScroll(windowY: Float) {
        if (listBounds == Rect.Zero) return
        val edgePx = with(density) { 54.dp.toPx() }
        val delta = when {
            windowY < listBounds.top + edgePx -> -30f
            windowY > listBounds.bottom - edgePx -> 30f
            else -> 0f
        }
        if (delta != 0f) dragScope.launch { listState.scrollBy(delta) }
    }

    fun groupAt(windowY: Float, excludingId: String? = null): String? {
        val candidates = groupBounds.entries.filter { it.key != excludingId }
        val direct = candidates.firstOrNull { (_, bounds) -> windowY in bounds.top..bounds.bottom }
        return direct?.key ?: candidates.minByOrNull { (_, bounds) ->
            kotlin.math.abs(windowY - ((bounds.top + bounds.bottom) / 2f))
        }?.key
    }

    fun moveBlockAt(block: List<String>, windowY: Float) {
        val moving = block.toSet()
        val targetGroupId = groupAt(windowY) ?: return
        val targetGroup = visualGroups.firstOrNull { it.id == targetGroupId } ?: return
        val remaining = targetGroup.itemIds.filterNot { it in moving }
        val targetItemId = remaining.filter(itemBounds::containsKey).minByOrNull { itemId ->
            val bounds = itemBounds[itemId] ?: return@minByOrNull Float.MAX_VALUE
            kotlin.math.abs(windowY - ((bounds.top + bounds.bottom) / 2f))
        }
        val targetIndex = if (targetItemId == null) {
            remaining.size
        } else {
            val bounds = itemBounds[targetItemId]
            val base = remaining.indexOf(targetItemId).coerceAtLeast(0)
            if (bounds != null && windowY > (bounds.top + bounds.bottom) / 2f) base + 1 else base
        }
        var next = SheetGroupingRules.moveItems(visualGroups, block, targetGroupId, targetIndex)
        if (kind == GroupingKind.DEVELOPMENT) {
            next = next.map { it.copy(itemIds = SheetGroupingRules.hierarchicalOrder(it.itemIds, parentById)) }
        }
        persist(next)
    }

    fun moveGroupAt(groupId: String, windowY: Float) {
        val targetGroupId = groupAt(windowY, excludingId = groupId) ?: return
        val remaining = visualGroups.filterNot { it.id == groupId }
        val targetIndex = remaining.indexOfFirst { it.id == targetGroupId }.takeIf { it >= 0 } ?: return
        val bounds = groupBounds[targetGroupId]
        val insertion = if (bounds != null && windowY > (bounds.top + bounds.bottom) / 2f) targetIndex + 1 else targetIndex
        persist(SheetGroupingRules.moveGroupToIndex(visualGroups, groupId, insertion))
    }

    FuryDialog(
        onDismissRequest = onDismiss,
        title = { Text("Группы · ${kind.title}") },
        text = {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 620.dp)
                    .onGloballyPositioned { listBounds = it.boundsInWindow() },
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                item {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = DesktopSurfaceInset.copy(alpha = .72f),
                        border = BorderStroke(1.dp, DesktopBorder.copy(alpha = .58f)),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            "Перетаскивайте группы и элементы за ручку. Корень ветки переносит дочерние элементы вместе.",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            color = DesktopMuted,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }

                items(visualGroups, key = { it.id }) { group ->
                    var groupDragOffset by remember(group.id) { mutableStateOf(0f) }
                    var groupDragging by remember(group.id) { mutableStateOf(false) }
                    var groupPointerY by remember(group.id) { mutableStateOf(0f) }
                    val dropTarget = hoveredGroupId == group.id
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .zIndex(if (groupDragging) 8f else 0f)
                            .graphicsLayer { translationY = groupDragOffset }
                            .onGloballyPositioned { groupBounds[group.id] = it.boundsInWindow() },
                        shape = RoundedCornerShape(10.dp),
                        color = if (dropTarget) DesktopAccentSoft.copy(alpha = .46f) else DesktopSurfaceInset.copy(alpha = .52f),
                        border = BorderStroke(if (dropTarget) 2.dp else 1.dp, if (dropTarget) DesktopAccent else DesktopBorder.copy(alpha = .72f)),
                    ) {
                        Column(Modifier.padding(horizontal = 8.dp, vertical = 7.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                GroupingDragHandle(
                                    modifier = Modifier.pointerInput(group.id) {
                                        var accumulated = 0f
                                        detectDragGesturesAfterLongPress(
                                            onDragStart = { local ->
                                                accumulated = 0f
                                                groupDragOffset = 0f
                                                groupDragging = true
                                                groupPointerY = (groupBounds[group.id]?.top ?: 0f) + local.y
                                                autoScroll(groupPointerY)
                                                hoveredGroupId = groupAt(groupPointerY, group.id)
                                            },
                                            onDragCancel = {
                                                groupDragOffset = 0f
                                                groupDragging = false
                                                hoveredGroupId = null
                                            },
                                            onDragEnd = {
                                                groupDragOffset = 0f
                                                groupDragging = false
                                                moveGroupAt(group.id, groupPointerY)
                                                hoveredGroupId = null
                                            },
                                            onDrag = { change, amount ->
                                                change.consume()
                                                accumulated += amount.y
                                                groupDragOffset = accumulated
                                                groupPointerY += amount.y
                                                autoScroll(groupPointerY)
                                                hoveredGroupId = groupAt(groupPointerY, group.id)
                                            },
                                        )
                                    },
                                    active = groupDragging,
                                )
                                Row(
                                    modifier = Modifier.weight(1f).clickable { persist(SheetGroupingRules.toggleCollapsed(visualGroups, group.id)) },
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                                ) {
                                    Text(if (group.collapsed) "▸" else "▾", color = DesktopAccent, fontWeight = FontWeight.Bold)
                                    Text(
                                        group.title,
                                        modifier = Modifier.weight(1f),
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    GroupCountBadge(group.itemIds.size)
                                }
                                GroupActionMenu(
                                    canDelete = group.id != ungroupedId,
                                    onRename = { editingGroupId = group.id; editingGroupName = group.title },
                                    onDelete = { persist(SheetGroupingRules.deleteGroup(visualGroups, group.id, ungroupedId)) },
                                )
                            }

                            if (editingGroupId == group.id) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    OutlinedTextField(
                                        editingGroupName,
                                        { editingGroupName = it.take(80) },
                                        label = { Text("Название группы") },
                                        modifier = Modifier.weight(1f),
                                        singleLine = true,
                                    )
                                    Button(
                                        enabled = editingGroupName.isNotBlank(),
                                        onClick = {
                                            persist(SheetGroupingRules.renameGroup(visualGroups, group.id, editingGroupName))
                                            editingGroupId = null
                                        },
                                    ) { Text("Сохранить") }
                                    TextButton(onClick = { editingGroupId = null }) { Text("Отмена") }
                                }
                            }

                            AnimatedVisibility(
                                visible = !group.collapsed,
                                enter = expandVertically(tween(FuryMotion.StandardMs)) + fadeIn(tween(FuryMotion.FastMs)),
                                exit = shrinkVertically(tween(FuryMotion.StandardMs)) + fadeOut(tween(FuryMotion.FastMs)),
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                if (group.itemIds.isEmpty()) {
                                    Text(
                                        "Пустая группа — перетащите сюда элемент.",
                                        modifier = Modifier.padding(start = 35.dp, top = 3.dp, bottom = 3.dp),
                                        color = DesktopMuted,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                                group.itemIds.forEach { itemId ->
                                    val depth = SheetGroupingRules.localDepth(itemId, group.itemIds, parentById)
                                    val movesTree = itemId in treeRootIds
                                    val block = if (movesTree) SheetGroupingRules.subtreeBlock(itemId, parentById, allVisualIds) else listOf(itemId)
                                    DraggableGroupingItem(
                                        itemId = itemId,
                                        label = labels[itemId] ?: itemId,
                                        depth = depth,
                                        treeSize = block.size,
                                        movesTree = movesTree,
                                        onBounds = { itemBounds[itemId] = it },
                                        onDragWindowY = { y -> autoScroll(y); hoveredGroupId = groupAt(y) },
                                        onDropWindowY = { y -> moveBlockAt(block, y); hoveredGroupId = null },
                                        onDragCancel = { hoveredGroupId = null },
                                    )
                                }
                                }
                            }
                        }
                    }
                }

                item {
                    if (creatingGroup) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                newName,
                                { newName = it.take(80) },
                                label = { Text("Название новой группы") },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                            )
                            Button(
                                enabled = newName.isNotBlank(),
                                onClick = {
                                    persist(SheetGroupingRules.addGroup(groups, "custom:${webUuid()}", newName))
                                    newName = ""
                                    creatingGroup = false
                                },
                            ) { Text("Добавить") }
                            TextButton(onClick = { newName = ""; creatingGroup = false }) { Text("Отмена") }
                        }
                    } else {
                        OutlinedButton(onClick = { creatingGroup = true }, modifier = Modifier.fillMaxWidth()) {
                            Text("+ Создать группу")
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Готово") } },
    )
}

@Composable
private fun DraggableGroupingItem(
    itemId: String,
    label: String,
    depth: Int,
    treeSize: Int,
    movesTree: Boolean,
    onBounds: (Rect) -> Unit,
    onDragWindowY: (Float) -> Unit,
    onDropWindowY: (Float) -> Unit,
    onDragCancel: () -> Unit,
) {
    var dragOffset by remember(itemId) { mutableStateOf(0f) }
    var dragging by remember(itemId) { mutableStateOf(false) }
    var bounds by remember(itemId) { mutableStateOf(Rect.Zero) }
    val child = depth > 0

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = (depth.coerceAtMost(3) * 10).dp + if (child) 6.dp else 0.dp)
            .zIndex(if (dragging) 5f else 0f)
            .graphicsLayer { translationY = dragOffset }
            .onGloballyPositioned { coordinates -> bounds = coordinates.boundsInWindow(); onBounds(bounds) },
        shape = RoundedCornerShape(if (child) 6.dp else 7.dp),
        color = when {
            dragging -> DesktopAccentSoft.copy(alpha = .78f)
            child -> DesktopSurfaceInset.copy(alpha = .36f)
            else -> DesktopSurfaceRaised.copy(alpha = .42f)
        },
        border = if (dragging) BorderStroke(1.dp, DesktopAccent) else null,
    ) {
        Row(
            Modifier.padding(horizontal = 7.dp, vertical = if (child) 4.dp else 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (child) DevelopmentBranchConnector(Modifier.width(13.dp).height(18.dp))
            GroupingDragHandle(
                modifier = Modifier.pointerInput(itemId, movesTree, treeSize) {
                    var accumulated = 0f
                    var pointerY = 0f
                    detectDragGesturesAfterLongPress(
                        onDragStart = { local ->
                            accumulated = 0f
                            dragOffset = 0f
                            dragging = true
                            pointerY = bounds.top + local.y
                            onDragWindowY(pointerY)
                        },
                        onDragCancel = {
                            dragOffset = 0f
                            dragging = false
                            onDragCancel()
                        },
                        onDragEnd = {
                            dragOffset = 0f
                            dragging = false
                            onDropWindowY(pointerY)
                        },
                        onDrag = { change, amount ->
                            change.consume()
                            accumulated += amount.y
                            dragOffset = accumulated
                            pointerY += amount.y
                            onDragWindowY(pointerY)
                        },
                    )
                },
                active = dragging,
            )
            Text(
                label,
                modifier = Modifier.weight(1f),
                fontWeight = if (child) FontWeight.Medium else FontWeight.SemiBold,
                style = if (child) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
                color = if (child) DesktopText.copy(alpha = .88f) else DesktopText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (movesTree) {
                Text(
                    "ветка · $treeSize",
                    color = DesktopGold,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

private fun defaultSkillGroups(skills: List<com.furybook.dubl.model.ResolvedSkill>): List<SheetGroup> = SkillCategory.entries.mapNotNull { category ->
    skills.filter { it.category == category }.map { it.id }.takeIf { it.isNotEmpty() }?.let { SheetGroup("skills:${category.name}", category.title, it) }
}

private fun defaultDevelopmentGroups(character: DublCharacter, state: DesktopAppState): List<SheetGroup> = character.characterSheetDevelopmentSections(state.developmentCatalog).map { section ->
    SheetGroup(
        "development:${section.type.name}",
        when (section.type) {
            DevelopmentSheetSectionType.REGULAR -> "Обычные навыки"
            DevelopmentSheetSectionType.SPECIAL -> "Спец. навыки"
            DevelopmentSheetSectionType.MARTIAL_ARTS -> "Боевые искусства"
            DevelopmentSheetSectionType.CHI -> "ЦИ"
        },
        section.items.map { it.entry.id },
    )
}


@Composable
private fun PortraitImage(path: String, modifier: Modifier = Modifier.fillMaxWidth().height(176.dp)) {
    val bitmap by produceState<ImageBitmap?>(initialValue = null, path) {
        value = runCatching {
            if (path.startsWith("data:image/")) dataUrlBytes(path).decodeToImageBitmap() else null
        }.getOrNull()
    }
    bitmap?.let { image ->
        Image(
            bitmap = image,
            contentDescription = "Портрет персонажа",
            contentScale = ContentScale.Crop,
            modifier = modifier,
        )
    }
}


private fun fcpDesktopIcon(token: String?): DesktopIconKind = when (token) {
    FcpUiIconToken.CHI -> DesktopIconKind.CHI
    else -> DesktopIconKind.GENERIC_SKILL
}

private fun fcpDesktopAccent(token: String?): Color = when (token) {
    FcpUiAccentToken.FURY_ACCENT -> DesktopAccent
    else -> DesktopAccent
}
