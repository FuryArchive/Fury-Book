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
import com.furybook.dubl.model.compareRollToTarget
import com.furybook.dubl.model.developmentNormalize
import com.furybook.dubl.model.rollPreset
import com.furybook.dubl.model.rule
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
import com.furybook.ui.theme.DublAccent
import com.furybook.ui.theme.DublAccentSoft
import com.furybook.ui.theme.DublDanger
import com.furybook.ui.theme.DublGold
import com.furybook.ui.theme.DublHealth
import com.furybook.ui.theme.DublMana
import com.furybook.ui.theme.DublStamina
import java.text.DecimalFormat
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class StatId(val title: String, val glyph: String) {
    DEFENSE("Защита", "◆"),
    REFLEXES("Рефлексы", "↯"),
    INITIATIVE("Инициатива", "◷"),
    FORTITUDE("Стойкость", "⬢"),
    RUN("Бег", "»"),
    SIZE("Размер", "◇"),
}

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

private const val SKILL_UNGROUPED_ID = "skills:ungrouped"
private const val DEVELOPMENT_UNGROUPED_ID = "development:ungrouped"

private data class StatInfo(
    val id: StatId,
    val value: String,
    val formula: String,
    val breakdown: List<String>,
    val note: String? = null,
)

@Composable
fun OverviewScreen(controller: CharacterController, chiPackEnabled: Boolean) {
    val character = controller.active
    val context = LocalContext.current
    val mountedResourceMeters = remember(context.applicationContext, chiPackEnabled, character) {
        AndroidContentPackState.resourceMeterModels(context, chiPackEnabled, character)
    }
    val mountedResourceToggles = remember(context.applicationContext, chiPackEnabled, character) {
        AndroidContentPackState.resourceToggleModels(context, chiPackEnabled, character)
    }
    val chiEconomyUi = remember(context.applicationContext, chiPackEnabled) {
        AndroidContentPackState.uiMounts(context, chiPackEnabled, FcpUiSurface.CHARACTER_ECONOMY, FcpUiComponent.XP_LINE).forFeature(DublUiFeature.CHI)
    }
    val conditionCatalog = remember(context.applicationContext) {
        ConditionCatalogRepository(context.applicationContext).load()
    }
    val developmentCatalog = remember(context.applicationContext, chiPackEnabled) {
        DevelopmentCatalogRepository(context.applicationContext).load(includeChi = chiPackEnabled)
    }
    val economy = remember(character, developmentCatalog, chiEconomyUi) {
        CharacterEconomy.breakdown(character, developmentCatalog, includeChi = chiEconomyUi != null)
    }
    val sheetExtras = controller.extras
    val listState = rememberLazyListState()
    val compactHeroVisible by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 150
        }
    }

    var showAdvancedEdit by remember { mutableStateOf(false) }
    var showConditions by remember { mutableStateOf(false) }
    var selectedCondition by remember { mutableStateOf<CharacterConditionId?>(null) }
    var editCustomConditionId by remember(character.id) { mutableStateOf<String?>(null) }
    var createCustomCondition by remember(character.id) { mutableStateOf(false) }
    var showSkillGroupManager by remember { mutableStateOf(false) }
    var showDevelopmentGroupManager by remember { mutableStateOf(false) }
    var selectedSkillId by remember(character.id) { mutableStateOf<String?>(null) }
    var selectedSkillRoll by remember(character.id) { mutableStateOf<Pair<String, AttributeId>?>(null) }
    var selectedDevelopmentId by remember(character.id) { mutableStateOf<String?>(null) }
    var selectedResource by remember { mutableStateOf<CharacterSheetResourceId?>(null) }
    var selectedCustomResourceId by remember(character.id) { mutableStateOf<String?>(null) }
    var editCustomResourceId by remember(character.id) { mutableStateOf<String?>(null) }
    var createCustomResource by remember(character.id) { mutableStateOf(false) }
    var editMaximumResource by remember(character.id) { mutableStateOf<CharacterSheetResourceId?>(null) }
    var selectedAttribute by remember { mutableStateOf<AttributeId?>(null) }
    var selectedStat by remember { mutableStateOf<StatId?>(null) }
    var showFortitudeRoll by remember { mutableStateOf(false) }
    var selectedRollContext by remember(character.id) { mutableStateOf<RollContext?>(null) }
    var showResourceVisibility by remember { mutableStateOf(false) }
    var showNameEdit by remember { mutableStateOf(false) }
    var showExperienceEdit by remember { mutableStateOf(false) }
    var recentChange by remember(character.id) { mutableStateOf<RecentChange?>(null) }

    fun recordRecent(change: RecentChange) {
        recentChange = change
    }

    fun toggleCondition(condition: CharacterConditionId) {
        val previous = sheetExtras.activeConditions
        val next = previous.toMutableSet().apply {
            if (!add(condition)) remove(condition)
        }.toSet()
        controller.setConditions(next)
        val enabled = condition in next
        recordRecent(
            RecentChange(
                text = if (enabled) "Добавлено состояние: ${condition.title}" else "Убрано состояние: ${condition.title}",
                accent = DublAccent,
                undo = UndoAction.Conditions(previous),
            ),
        )
    }

    val portraitPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            controller.setPortrait(uri.toString())
        }
    }

    LaunchedEffect(recentChange?.token) {
        if (recentChange != null) {
            delay(6000)
            recentChange = null
        }
    }

    val effectiveConditions = remember(sheetExtras.activeConditions, character.enduranceCurrent) {
        buildSet {
            addAll(sheetExtras.activeConditions)
            if (character.enduranceCurrent == 0) add(CharacterConditionId.WEAKNESS)
        }
    }
    val trainedSkills = remember(character) { character.resolvedSkills().filter { it.rank > 0 } }
    val normalizedSkillGroups = remember(trainedSkills, sheetExtras.skillGroups) {
        SheetGroupingRules.normalize(
            sheetExtras.skillGroups,
            defaultSkillGroups(trainedSkills),
            trainedSkills.map { it.id },
            SKILL_UNGROUPED_ID,
        )
    }
    LaunchedEffect(normalizedSkillGroups, sheetExtras.skillGroups) {
        if (normalizedSkillGroups != sheetExtras.skillGroups) {
            controller.setSkillGroups(normalizedSkillGroups)
        }
    }
    val trainedSkillsById = remember(trainedSkills) { trainedSkills.associateBy { it.id } }

    val overviewDevelopmentRules = remember(character, developmentCatalog) {
        DevelopmentRules(character, developmentCatalog, DevelopmentProgress(character.development))
    }
    val overviewDevelopmentItems = remember(character, developmentCatalog) {
        overviewDevelopmentRules.ownedSheetSections().flatMap { it.items }.distinctBy { it.entry.id }
    }
    val normalizedDevelopmentGroups = remember(overviewDevelopmentItems, sheetExtras.developmentGroups, character, developmentCatalog) {
        SheetGroupingRules.normalize(
            sheetExtras.developmentGroups,
            defaultDevelopmentGroups(character, developmentCatalog),
            overviewDevelopmentItems.map { it.entry.id },
            DEVELOPMENT_UNGROUPED_ID,
        )
    }
    LaunchedEffect(normalizedDevelopmentGroups, sheetExtras.developmentGroups) {
        if (normalizedDevelopmentGroups != sheetExtras.developmentGroups) {
            controller.setDevelopmentGroups(normalizedDevelopmentGroups)
        }
    }
    val overviewDevelopmentById = remember(overviewDevelopmentItems) { overviewDevelopmentItems.associateBy { it.entry.id } }
    val developmentParentById = remember(overviewDevelopmentItems) {
        overviewDevelopmentItems.associate { it.entry.id to it.parentId }
    }
    val developmentTreeRootIds = remember(overviewDevelopmentItems, developmentParentById) {
        val parentIds = developmentParentById.values.filterNotNull().toSet()
        overviewDevelopmentItems.asSequence()
            .filter { item ->
                item.entry.id in parentIds && item.parentId == null &&
                    (item.entry.isSpecialDevelopment || item.entry.isMartialArt)
            }
            .map { it.entry.id }
            .toSet()
    }
    val invalidOverviewDevelopmentIds = remember(overviewDevelopmentItems, overviewDevelopmentRules) {
        overviewDevelopmentItems.asSequence()
            .filter { item -> overviewDevelopmentRules.requirements(item.entry).any { it.status != RequirementStatus.OK } }
            .map { it.entry.id }
            .toSet()
    }

    fun undoLast() {
        if (recentChange?.undo != null) controller.undoLast()
        recentChange = null
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                CharacterHero(
                    character = character,
                    economy = economy,
                    portraitUri = sheetExtras.portraitUri,
                    onAdvancedEdit = { showAdvancedEdit = true },
                    onPortraitClick = { portraitPicker.launch(arrayOf("image/*")) },
                    onNameClick = { showNameEdit = true },
                    onExperienceClick = { showExperienceEdit = true },
                )
            }

            item {
                SectionTitle(
                    title = "Ресурсы",
                    trailing = "Настроить",
                    onTrailingClick = { showResourceVisibility = true },
                )
                Spacer(Modifier.height(8.dp))
                ResourceStrip(
                    character = character,
                    hiddenResources = sheetExtras.hiddenResourceIds,
                    mountedResources = mountedResourceMeters,
                    onResourceClick = { selectedResource = it },
                    onCustomResourceClick = { selectedCustomResourceId = it },
                    onConfigure = { showResourceVisibility = true },
                )
                Spacer(Modifier.height(8.dp))
                ConditionStrip(
                    conditions = effectiveConditions,
                    overrides = sheetExtras.conditionOverrides,
                    customConditions = sheetExtras.customConditions.filter { it.active },
                    autoWeakness = character.enduranceCurrent == 0,
                    onManage = { showConditions = true },
                    onConditionClick = { selectedCondition = it },
                    onCustomClick = { editCustomConditionId = it },
                )
                recentChange?.let {
                    Spacer(Modifier.height(8.dp))
                    RecentChangeBar(change = it, onUndo = if (it.undo != null) ::undoLast else null)
                }
            }

            item {
                SectionTitle("Характеристики", trailing = "Нажмите, чтобы изменить")
                Spacer(Modifier.height(8.dp))
                AttributeGrid(
                    character = character,
                    onAttributeClick = { selectedAttribute = it },
                )
            }

            item {
                SectionTitle("Показатели", trailing = "Нажмите для формулы")
                Spacer(Modifier.height(8.dp))
                KeyStats(
                    character = character,
                    onStatClick = { selectedStat = it },
                    onFortitudeRoll = { showFortitudeRoll = true },
                    onReflexesRoll = { selectedRollContext = RollContext.REFLEXES },
                    onInitiativeRoll = { selectedRollContext = RollContext.INITIATIVE },
                )
            }

            item {
                QuickChecksSection(onRoll = { selectedRollContext = it })
            }

            ownedSkillSectionItems(
                character = character,
                skills = trainedSkills,
                groups = normalizedSkillGroups,
                byId = trainedSkillsById,
                onGroupsChanged = { groups -> controller.setSkillGroups(groups) },
                onConfigure = { showSkillGroupManager = true },
                onSkillClick = { selectedSkillId = it.id },
            )

            ownedDevelopmentSectionItems(
                items = overviewDevelopmentItems,
                groups = normalizedDevelopmentGroups,
                byId = overviewDevelopmentById,
                parentById = developmentParentById,
                invalidIds = invalidOverviewDevelopmentIds,
                onGroupsChanged = { groups -> controller.setDevelopmentGroups(groups) },
                onConfigure = { showDevelopmentGroupManager = true },
                onEntryClick = { selectedDevelopmentId = it.id },
            )
        }

        if (compactHeroVisible) {
            CompactHeroBar(
                character = character,
                portraitUri = sheetExtras.portraitUri,
                conditionCount = effectiveConditions.size,
                hiddenResources = sheetExtras.hiddenResourceIds,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 12.dp, vertical = 7.dp)
                    .zIndex(2f),
                onHealthClick = { selectedResource = CharacterSheetResourceId.HEALTH },
            )
        }
    }

    if (showAdvancedEdit) {
        EditCharacterDialog(
            character = character,
            onDismiss = { showAdvancedEdit = false },
            onConfirm = { name, concept, experience, size, legs, manaEnabled ->
                controller.setProfile(name, concept, experience, size, legs, manaEnabled)
                showAdvancedEdit = false
            },
        )
    }

    if (showNameEdit) {
        TextValueEditSheet(
            title = "Имя персонажа",
            initialValue = character.name,
            numeric = false,
            onSave = { value ->
                val previous = character.name
                val next = value.ifBlank { "Новый персонаж" }
                controller.setName(next)
                if (next != previous) {
                    recordRecent(
                        RecentChange(
                            text = "Имя изменено",
                            accent = DublGold,
                            undo = UndoAction.Name(previous),
                        ),
                    )
                }
                showNameEdit = false
            },
            onDismiss = { showNameEdit = false },
        )
    }

    if (showExperienceEdit) {
        ExperienceEconomySheet(
            character = character,
            economy = economy,
            showChi = chiEconomyUi != null,
            hasSelfTaught = developmentCatalog.matchingName("Самоучка").any { entry ->
                (character.development[entry.id]?.rank ?: 0) > 0
            },
            onSetExperience = { next ->
                val previous = character.experience
                val previousCreation = character.creationExperience
                controller.setExperience(next)
                if (next != previous) {
                    recordRecent(
                        RecentChange(
                            text = "Общий опыт: $previous → $next",
                            accent = DublGold,
                            undo = UndoAction.Experience(previous, previousCreation),
                        ),
                    )
                }
            },
            onSetCreationExperience = controller::setCreationExperience,
            onSetAdjustment = controller::setXpAdjustment,
            onSetAbilityOverride = controller::setAbilityPointsOverride,
            onCompleteCreation = controller::completeCreation,
            onReopenCreation = controller::reopenCreation,
            onDismiss = { showExperienceEdit = false },
        )
    }

    if (showConditions) {
        ConditionPickerSheet(
            active = sheetExtras.activeConditions,
            autoWeakness = character.enduranceCurrent == 0,
            overrides = sheetExtras.conditionOverrides,
            customConditions = sheetExtras.customConditions,
            onToggle = ::toggleCondition,
            onInfo = { condition ->
                showConditions = false
                selectedCondition = condition
            },
            onToggleCustom = { id, active ->
                controller.setCustomConditionActive(id, active)
            },
            onEditCustom = { id ->
                showConditions = false
                editCustomConditionId = id
            },
            onAddCustom = {
                showConditions = false
                createCustomCondition = true
            },
            onDismiss = { showConditions = false },
        )
    }

    selectedCondition?.let { condition ->
        val automatic = condition == CharacterConditionId.WEAKNESS && character.enduranceCurrent == 0
        val local = sheetExtras.conditionOverrides[condition]
        val canonicalSummary = conditionCatalog.summary(condition)
        ConditionDetailSheet(
            condition = condition,
            title = local?.title ?: condition.title,
            summary = local?.description ?: canonicalSummary,
            canonicalTitle = condition.title,
            canonicalSummary = canonicalSummary,
            active = condition in effectiveConditions,
            automatic = automatic,
            onToggle = {
                if (!automatic) toggleCondition(condition)
                selectedCondition = null
            },
            onSaveLocal = { title, description ->
                val cleanTitle = title.trim().takeIf { it.isNotBlank() && it != condition.title }
                val cleanDescription = description.trim().takeIf { it != canonicalSummary }
                controller.setConditionOverride(condition, cleanTitle, cleanDescription)
                selectedCondition = null
            },
            onResetLocal = {
                controller.resetConditionOverride(condition)
                selectedCondition = null
            },
            onDismiss = { selectedCondition = null },
        )
    }


    if (createCustomCondition) {
        CustomConditionSheet(
            condition = null,
            onSave = { title, description, active ->
                val clean = title.trim()
                if (clean.isNotBlank()) {
                    controller.addCustomCondition(clean, description.trim(), active)
                }
                createCustomCondition = false
            },
            onDelete = {},
            onDismiss = { createCustomCondition = false },
        )
    }

    editCustomConditionId?.let { id ->
        val custom = sheetExtras.customConditions.firstOrNull { it.id == id }
        if (custom == null) {
            editCustomConditionId = null
        } else {
            CustomConditionSheet(
                condition = custom,
                onSave = { title, description, active ->
                    val clean = title.trim()
                    if (clean.isNotBlank()) {
                        controller.updateCustomCondition(id, clean, description.trim(), active)
                    }
                    editCustomConditionId = null
                },
                onDelete = {
                    controller.removeCustomCondition(id)
                    editCustomConditionId = null
                },
                onDismiss = { editCustomConditionId = null },
            )
        }
    }

    if (showSkillGroupManager) {
        val defaults = defaultSkillGroups(trainedSkills)
        val normalized = SheetGroupingRules.normalize(
            sheetExtras.skillGroups,
            defaults,
            trainedSkills.map { it.id },
            SKILL_UNGROUPED_ID,
        )
        GroupManagerSheet(
            title = "Группы умений",
            groups = normalized,
            itemLabels = trainedSkills.associate { it.id to it.name },
            itemParentIds = emptyMap(),
            treeRootIds = emptySet(),
            ungroupedId = SKILL_UNGROUPED_ID,
            onGroupsChanged = { groups -> controller.setSkillGroups(groups) },
            onDismiss = { showSkillGroupManager = false },
        )
    }

    if (showDevelopmentGroupManager) {
        val owned = ownedDevelopmentItems(character, developmentCatalog)
        val defaults = defaultDevelopmentGroups(character, developmentCatalog)
        val normalized = SheetGroupingRules.normalize(
            sheetExtras.developmentGroups,
            defaults,
            owned.map { it.entry.id },
            DEVELOPMENT_UNGROUPED_ID,
        )
        GroupManagerSheet(
            title = "Группы навыков",
            groups = normalized,
            itemLabels = owned.associate { it.entry.id to it.entry.name },
            itemParentIds = developmentParentById,
            treeRootIds = developmentTreeRootIds,
            ungroupedId = DEVELOPMENT_UNGROUPED_ID,
            onGroupsChanged = { groups -> controller.setDevelopmentGroups(groups) },
            onDismiss = { showDevelopmentGroupManager = false },
        )
    }

    selectedSkillId?.let { skillId ->
        character.resolveSkill(skillId)?.let { skill ->
            SkillAttributeChoiceSheet(
                character = character,
                skill = skill,
                onConfirm = { attribute ->
                    selectedSkillId = null
                    selectedSkillRoll = skill.id to attribute
                },
                onDismiss = { selectedSkillId = null },
            )
        } ?: run { selectedSkillId = null }
    }

    selectedSkillRoll?.let { (skillId, attribute) ->
        character.resolveSkill(skillId)?.let { skill ->
            DublSkillRollSheet(
                character = character,
                skill = skill,
                initialAttribute = attribute,
                onDismiss = { selectedSkillRoll = null },
            )
        } ?: run { selectedSkillRoll = null }
    }

    selectedDevelopmentId?.let { entryId ->
        developmentCatalog.byId(entryId)?.let { entry ->
            OwnedDevelopmentDetailSheet(
                entry = entry,
                character = character,
                catalog = developmentCatalog,
                onDismiss = { selectedDevelopmentId = null },
            )
        } ?: run { selectedDevelopmentId = null }
    }

    if (showFortitudeRoll) {
        val fortitudePreset = character.rollPreset(RollContext.FORTITUDE)
        CheckRollSheet(
            title = "Проверка Стойкости",
            bonusTitle = "Стойкость",
            checkBonus = fortitudePreset.bonus ?: character.fortitude,
            formulaText = fortitudePreset.formulaText,
            rememberKey = "fortitude-${character.id}",
            onDismiss = { showFortitudeRoll = false },
        )
    }

    selectedRollContext?.let { rollContext ->
        ContextRollSheet(
            character = character,
            context = rollContext,
            developmentCatalog = developmentCatalog,
            onDismiss = { selectedRollContext = null },
        )
    }

    if (showResourceVisibility) {
        ResourceVisibilitySheet(
            character = character,
            hidden = sheetExtras.hiddenResourceIds,
            mountedToggles = mountedResourceToggles,
            onToggle = { resourceId ->
                controller.setResourceHidden(
                    resourceId,
                    hidden = resourceId !in sheetExtras.hiddenResourceIds,
                )
            },
            onAddCustom = { showResourceVisibility = false; createCustomResource = true },
            onEditCustom = { uid -> showResourceVisibility = false; editCustomResourceId = uid },
            onDismiss = { showResourceVisibility = false },
        )
    }

    selectedResource?.let { resource ->
        val mounted = mountedResourceMeters.firstOrNull { model -> model.resourceId == resource }
        if (mounted != null) {
            val presentation = mounted.presentation
            ResourceAdjustSheet(
                title = presentation.label,
                current = mounted.current,
                maximum = mounted.maximum,
                accent = fcpAccentColor(presentation.accent),
                onChange = { requestedDelta ->
                    val before = character.resourceCurrent(resource)
                    val after = (before + requestedDelta).coerceIn(0, mounted.maximum)
                    val applied = after - before
                    if (applied != 0) {
                        controller.changeResource(resource, applied)
                        recordRecent(
                            RecentChange(
                                text = resourceChangeText(presentation.label, applied),
                                accent = fcpAccentColor(presentation.accent),
                                undo = UndoAction.Resource(resource, applied),
                            ),
                        )
                    }
                },
                onEditMaximum = null,
                onDismiss = { selectedResource = null },
            )
        } else {
            when (resource) {
                CharacterSheetResourceId.HEALTH -> HealthControlSheet(
                    current = character.hpCurrent,
                    maximum = character.healthMaximum,
                    onChange = { requestedDelta ->
                        val before = character.hpCurrent
                        val after = (before + requestedDelta).coerceAtMost(character.healthMaximum)
                        val applied = after - before
                        if (applied != 0) {
                            controller.changeResource(resource, applied)
                            recordRecent(
                                RecentChange(
                                    text = resourceChangeText("здоровья", applied),
                                    accent = DublHealth,
                                    undo = UndoAction.Resource(resource, applied),
                                ),
                            )
                        }
                    },
                    onEditMaximum = { selectedResource = null; editMaximumResource = resource },
                    onDismiss = { selectedResource = null },
                )
                CharacterSheetResourceId.ENDURANCE -> ResourceAdjustSheet(
                    title = "Выносливость",
                    current = character.enduranceCurrent,
                    maximum = character.enduranceMaximum,
                    accent = DublStamina,
                    onChange = { requestedDelta ->
                        val before = character.enduranceCurrent
                        val after = (before + requestedDelta).coerceIn(0, character.enduranceMaximum)
                        val applied = after - before
                        if (applied != 0) {
                            controller.changeResource(resource, applied)
                            recordRecent(
                                RecentChange(
                                    text = resourceChangeText("выносливости", applied),
                                    accent = DublStamina,
                                    undo = UndoAction.Resource(resource, applied),
                                ),
                            )
                        }
                    },
                    onEditMaximum = { selectedResource = null; editMaximumResource = resource },
                    onDismiss = { selectedResource = null },
                )
                CharacterSheetResourceId.MANA -> ResourceAdjustSheet(
                    title = "Мана",
                    current = character.manaCurrent,
                    maximum = character.effectiveManaMaximum,
                    accent = DublMana,
                    onChange = { requestedDelta ->
                        val before = character.manaCurrent
                        val after = (before + requestedDelta).coerceIn(0, character.effectiveManaMaximum)
                        val applied = after - before
                        if (applied != 0) {
                            controller.changeResource(resource, applied)
                            recordRecent(
                                RecentChange(
                                    text = resourceChangeText("маны", applied),
                                    accent = DublMana,
                                    undo = UndoAction.Resource(resource, applied),
                                ),
                            )
                        }
                    },
                    onEditMaximum = { selectedResource = null; editMaximumResource = resource },
                    onDismiss = { selectedResource = null },
                )
                else -> selectedResource = null
            }
        }
    }

    selectedCustomResourceId?.let { uid ->
        character.customResources.firstOrNull { it.uid == uid }?.let { resource ->
            CustomResourceControlSheet(
                resource = resource,
                onChange = { controller.changeCustomResource(uid, it) },
                onEdit = { selectedCustomResourceId = null; editCustomResourceId = uid },
                onDismiss = { selectedCustomResourceId = null },
            )
        } ?: run { selectedCustomResourceId = null }
    }

    if (createCustomResource) {
        CustomResourceEditDialog(
            initial = null,
            onSave = { name, current, maximum ->
                controller.addCustomResource(name, maximum, current)
                createCustomResource = false
            },
            onDismiss = { createCustomResource = false },
        )
    }

    editCustomResourceId?.let { uid ->
        character.customResources.firstOrNull { it.uid == uid }?.let { resource ->
            CustomResourceEditDialog(
                initial = resource,
                onSave = { name, current, maximum ->
                    controller.updateCustomResource(uid, name, current, maximum)
                    editCustomResourceId = null
                },
                onDelete = { controller.removeCustomResource(uid); editCustomResourceId = null },
                onDismiss = { editCustomResourceId = null },
            )
        } ?: run { editCustomResourceId = null }
    }

    editMaximumResource?.let { resource ->
        val calculated = when (resource) {
            CharacterSheetResourceId.HEALTH -> character.calculatedHealthMaximum
            CharacterSheetResourceId.ENDURANCE -> 3
            CharacterSheetResourceId.MANA -> if (character.magic.manaRank > 0) MagicEquipmentRules.manaMaximum(character) else character.manaMaximum
            else -> return@let
        }
        val override = when (resource) {
            CharacterSheetResourceId.HEALTH -> character.healthMaximumOverride
            CharacterSheetResourceId.ENDURANCE -> character.enduranceMaximumOverride
            CharacterSheetResourceId.MANA -> character.manaMaximumOverride
            else -> null
        }
        MaximumResourceDialog(
            title = when (resource) {
                CharacterSheetResourceId.HEALTH -> "Максимум здоровья"
                CharacterSheetResourceId.ENDURANCE -> "Максимум выносливости"
                CharacterSheetResourceId.MANA -> "Максимум маны"
                else -> resource.title
            },
            calculated = calculated,
            override = override,
            onSave = { value ->
                when (resource) {
                    CharacterSheetResourceId.HEALTH -> controller.setHealthMaximumOverride(value)
                    CharacterSheetResourceId.ENDURANCE -> controller.setEnduranceMaximumOverride(value)
                    CharacterSheetResourceId.MANA -> controller.setManaMaximumOverride(value)
                    else -> Unit
                }
                editMaximumResource = null
            },
            onDismiss = { editMaximumResource = null },
        )
    }

    selectedAttribute?.let { id ->
        AttributeAdjustSheet(
            id = id,
            character = character,
            onChange = { delta ->
                controller.changeAttribute(id, delta)
                recordRecent(
                    RecentChange(
                        text = "${id.title} ${signed(delta)}",
                        accent = attributeAccent(id),
                        undo = UndoAction.Attribute(id, delta),
                    ),
                )
            },
            onDismiss = { selectedAttribute = null },
        )
    }

    selectedStat?.let { statId ->
        StatInfoSheet(
            info = statInfo(statId, character),
            character = character,
            onRollFortitude = {
                selectedStat = null
                showFortitudeRoll = true
            },
            onSetSize = { size ->
                val previous = character.size
                val next = size.coerceIn(1, 10)
                controller.setSize(next)
                if (next != previous) {
                    recordRecent(
                        RecentChange(
                            text = "Размер: $previous → $next",
                            accent = statAccent(StatId.SIZE),
                            undo = UndoAction.Size(previous),
                        ),
                    )
                }
            },
            onSetLegs = { legs ->
                val previous = character.legs
                val next = legs.coerceAtLeast(2)
                controller.setLegs(next)
                if (next != previous) {
                    recordRecent(
                        RecentChange(
                            text = "Количество ног: $previous → $next",
                            accent = statAccent(StatId.RUN),
                            undo = UndoAction.Legs(previous),
                        ),
                    )
                }
            },
            onDismiss = { selectedStat = null },
        )
    }
}

@Composable
private fun CharacterHero(
    character: DublCharacter,
    economy: CharacterEconomyBreakdown,
    portraitUri: String?,
    onAdvancedEdit: () -> Unit,
    onPortraitClick: () -> Unit,
    onNameClick: () -> Unit,
    onExperienceClick: () -> Unit,
) {
    DublCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CharacterPortrait(
                character = character,
                portraitUri = portraitUri,
                size = 68.dp,
                onClick = onPortraitClick,
                showHint = true,
            )

            Column(Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = character.name,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(7.dp))
                            .clickable(onClick = onNameClick)
                            .padding(vertical = 3.dp),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                    )
                    TextButton(onClick = onAdvancedEdit) {
                        Text("Ещё")
                    }
                }

                Spacer(Modifier.height(7.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    MetaPill(
                        label = "XP потрачено",
                        value = "${economy.spentXp} / ${economy.totalExperience}",
                        modifier = Modifier.weight(1f),
                        onClick = onExperienceClick,
                    )
                    MetaPill(
                        label = "ОС осталось",
                        value = "${economy.abilityPointsRemaining} / ${economy.abilityPointsBudget}",
                        modifier = Modifier.weight(1f),
                        onClick = onExperienceClick,
                    )
                }
            }
        }
    }
}

@Composable
private fun CharacterPortrait(
    character: DublCharacter,
    portraitUri: String?,
    size: Dp,
    onClick: (() -> Unit)? = null,
    showHint: Boolean = false,
) {
    val bitmap = rememberPortraitBitmap(portraitUri)
    val shape = RoundedCornerShape(if (size >= 60.dp) 15.dp else 11.dp)
    val clickableModifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier

    Surface(
        modifier = Modifier
            .size(size)
            .clip(shape)
            .then(clickableModifier),
        shape = shape,
        color = DublAccentSoft,
        border = BorderStroke(1.dp, DublAccent.copy(alpha = 0.62f)),
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = "Портрет ${character.name}",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Text(
                    text = character.name.trim().firstOrNull()?.uppercase() ?: "D",
                    fontSize = if (size >= 60.dp) 26.sp else 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            if (showHint) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
                ) {
                    Text(
                        text = if (bitmap == null) "Фото" else "Сменить",
                        modifier = Modifier.padding(vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun rememberPortraitBitmap(uriString: String?): ImageBitmap? {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(initialValue = null, key1 = uriString) {
        value = if (uriString.isNullOrBlank()) {
            null
        } else {
            withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(Uri.parse(uriString))?.use { stream ->
                        BitmapFactory.decodeStream(stream)?.asImageBitmap()
                    }
                }.getOrNull()
            }
        }
    }
    return bitmap
}

@Composable
private fun CompactHeroBar(
    character: DublCharacter,
    portraitUri: String?,
    conditionCount: Int,
    hiddenResources: Set<CharacterSheetResourceId>,
    modifier: Modifier = Modifier,
    onHealthClick: () -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.9f)),
        shadowElevation = 7.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            CharacterPortrait(character = character, portraitUri = portraitUri, size = 38.dp)
            Text(
                text = character.name,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
            if (CharacterSheetResourceId.HEALTH !in hiddenResources) {
                val critical = healthCriticalLevel(character.hpCurrent, character.healthMaximum)
                val healthAccent = if (critical > 0) Color(0xFFD7656E) else DublHealth
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(9.dp))
                        .clickable(onClick = onHealthClick),
                    shape = RoundedCornerShape(9.dp),
                    color = healthAccent.copy(alpha = if (critical > 0) 0.15f else 0.09f),
                    border = BorderStroke(if (critical > 0) 1.5.dp else 1.dp, healthAccent.copy(alpha = 0.55f)),
                ) {
                    Text(
                        text = "HP ${character.hpCurrent}/${character.healthMaximum}",
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = healthAccent,
                    )
                }
            }
            if (CharacterSheetResourceId.ENDURANCE !in hiddenResources) {
                Text(
                    text = "Вын ${character.enduranceCurrent}/${character.enduranceMaximum}",
                    style = MaterialTheme.typography.labelLarge,
                    color = DublStamina,
                )
            }
            if (conditionCount > 0) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = DublAccent.copy(alpha = 0.16f),
                ) {
                    Text(
                        text = conditionCount.toString(),
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = DublAccent,
                    )
                }
            }
        }
    }
}

@Composable
private fun MetaPill(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val clickModifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    Surface(
        modifier = modifier.then(clickModifier),
        shape = RoundedCornerShape(8.dp),
        color = DublGold.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, DublGold.copy(alpha = 0.46f)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = DublGold.copy(alpha = 0.82f),
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
            Text(
                text = value,
                fontSize = 17.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.Bold,
                color = DublGold,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun SectionTitle(
    title: String,
    trailing: String? = null,
    onTrailingClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                modifier = Modifier.size(width = 4.dp, height = 18.dp),
                shape = RoundedCornerShape(2.dp),
                color = DublAccent,
            ) {}
            Spacer(Modifier.width(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
        if (trailing != null) {
            if (onTrailingClick != null) {
                TextButton(onClick = onTrailingClick) { Text(trailing) }
            } else {
                Text(
                    text = trailing,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ResourceStrip(
    character: DublCharacter,
    hiddenResources: Set<CharacterSheetResourceId>,
    mountedResources: List<DublResourceMeterModel>,
    onResourceClick: (CharacterSheetResourceId) -> Unit,
    onCustomResourceClick: (String) -> Unit,
    onConfigure: () -> Unit,
) {
    val mountedById = mountedResources.associateBy { it.resourceId }
    val resources = orderedUiItems(
        buildList {
            if (CharacterSheetResourceId.HEALTH !in hiddenResources) {
                add(FcpOrderedUiItem(FcpUiHostOrder.PRIMARY, CharacterSheetResourceId.HEALTH.name, CharacterSheetResourceId.HEALTH))
            }
            if (CharacterSheetResourceId.ENDURANCE !in hiddenResources) {
                add(FcpOrderedUiItem(FcpUiHostOrder.SECONDARY, CharacterSheetResourceId.ENDURANCE.name, CharacterSheetResourceId.ENDURANCE))
            }
            if (character.manaEnabled && CharacterSheetResourceId.MANA !in hiddenResources) {
                add(FcpOrderedUiItem(FcpUiHostOrder.TERTIARY, CharacterSheetResourceId.MANA.name, CharacterSheetResourceId.MANA))
            }
            mountedResources
                .filter { model -> model.available && model.resourceId !in hiddenResources }
                .forEach { model ->
                    add(
                        FcpOrderedUiItem(
                            model.presentation.order,
                            "fcp:${model.resourceId.name}",
                            model.resourceId,
                        ),
                    )
                }
        },
    )

    if (resources.isEmpty() && character.customResources.isEmpty()) {
        Surface(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onConfigure),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.65f)),
        ) {
            Text(
                text = "Все ресурсы скрыты · нажмите, чтобы настроить",
                modifier = Modifier.padding(14.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        resources.chunked(3).forEach { rowResources ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowResources.forEach { resource ->
                    val mounted = mountedById[resource]
                    when {
                        mounted != null -> {
                            val presentation = mounted.presentation
                            CompactResourceCard(
                                title = presentation.label,
                                current = mounted.current,
                                maximum = mounted.maximum,
                                accent = fcpAccentColor(presentation.accent),
                                modifier = Modifier.weight(1f),
                                icon = fcpIconGlyph(presentation.icon),
                            ) { onResourceClick(resource) }
                        }
                        resource == CharacterSheetResourceId.HEALTH -> CompactResourceCard(
                            "Здоровье",
                            character.hpCurrent,
                            character.healthMaximum,
                            DublHealth,
                            Modifier.weight(1f),
                            healthCriticalLevel(character.hpCurrent, character.healthMaximum),
                        ) { onResourceClick(resource) }
                        resource == CharacterSheetResourceId.ENDURANCE -> CompactResourceCard(
                            "Выносливость",
                            character.enduranceCurrent,
                            character.enduranceMaximum,
                            DublStamina,
                            Modifier.weight(1f),
                        ) { onResourceClick(resource) }
                        resource == CharacterSheetResourceId.MANA -> CompactResourceCard(
                            "Мана",
                            character.manaCurrent,
                            character.effectiveManaMaximum,
                            DublMana,
                            Modifier.weight(1f),
                        ) { onResourceClick(resource) }
                        else -> Spacer(Modifier.weight(1f))
                    }
                }
                repeat(3 - rowResources.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        character.customResources.chunked(2).forEach { rowResources ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowResources.forEach { resource ->
                    CompactResourceCard(
                        title = resource.name,
                        current = resource.current,
                        maximum = resource.maximum,
                        accent = DublGold,
                        modifier = Modifier.weight(1f),
                    ) { onCustomResourceClick(resource.uid) }
                }
                if (rowResources.size < 2) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun CompactResourceCard(
    title: String,
    current: Int,
    maximum: Int,
    accent: Color,
    modifier: Modifier = Modifier,
    criticalLevel: Int = 0,
    icon: String? = null,
    onClick: () -> Unit,
) {
    val animatedCurrent by animateIntAsState(
        targetValue = current,
        animationSpec = tween(durationMillis = 280),
        label = "$title value",
    )
    val targetProgress = if (maximum <= 0) 0f else (current.toFloat() / maximum.toFloat()).coerceIn(0f, 1f)
    val animatedProgress by animateFloatAsState(
        targetValue = targetProgress,
        animationSpec = tween(durationMillis = 340),
        label = "$title progress",
    )
    val displayAccent = if (criticalLevel > 0) Color(0xFFD7656E) else accent
    val borderWidth = when (criticalLevel) {
        3 -> 2.25.dp
        2 -> 2.dp
        1 -> 1.35.dp
        else -> 1.dp
    }

    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = displayAccent.copy(alpha = if (criticalLevel > 0) 0.10f else 0.045f),
        border = BorderStroke(borderWidth, displayAccent.copy(alpha = if (criticalLevel > 0) 0.72f else 0.34f)),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (icon != null) {
                        Text(icon, color = displayAccent, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        text = title,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    )
                }
                if (criticalLevel >= 2) {
                    Text(
                        text = if (criticalLevel == 3) "0 HP" else "КРИТ.",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = displayAccent,
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = "$animatedCurrent / $maximum",
                fontSize = 19.sp,
                lineHeight = 23.sp,
                fontWeight = FontWeight.Bold,
                color = displayAccent,
            )
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = displayAccent,
                trackColor = displayAccent.copy(alpha = 0.14f),
            )
        }
    }
}

private fun healthCriticalLevel(current: Int, maximum: Int): Int {
    if (current <= 0) return 3
    if (maximum <= 0) return 0
    val ratio = current.toDouble() / maximum.toDouble()
    if (ratio <= 0.25) return 2
    if (ratio <= 0.50) return 1
    return 0
}

@Composable
private fun ConditionStrip(
    conditions: Set<CharacterConditionId>,
    overrides: Map<CharacterConditionId, ConditionLocalOverride>,
    customConditions: List<CustomCondition>,
    autoWeakness: Boolean,
    onManage: () -> Unit,
    onConditionClick: (CharacterConditionId) -> Unit,
    onCustomClick: (String) -> Unit,
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Состояния",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = onManage) { Text("Изменить") }
        }
        val ordered = CharacterConditionId.entries.filter { it in conditions }
        if (ordered.isEmpty() && customConditions.isEmpty()) {
            Text(
                text = "Нет активных состояний",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ordered.chunked(2).forEach { rowConditions ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        rowConditions.forEach { condition ->
                            val automatic = condition == CharacterConditionId.WEAKNESS && autoWeakness
                            ConditionChip(
                                condition = condition,
                                title = overrides[condition]?.title ?: condition.title,
                                automatic = automatic,
                                modifier = Modifier.weight(1f),
                                onClick = { onConditionClick(condition) },
                            )
                        }
                        if (rowConditions.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
                customConditions.chunked(2).forEach { rowConditions ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        rowConditions.forEach { condition ->
                            CustomConditionChip(
                                condition = condition,
                                modifier = Modifier.weight(1f),
                                onClick = { onCustomClick(condition.id) },
                            )
                        }
                        if (rowConditions.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun ConditionChip(
    condition: CharacterConditionId,
    title: String = condition.title,
    automatic: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(9.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(9.dp),
        color = DublAccentSoft.copy(alpha = 0.42f),
        border = BorderStroke(1.dp, DublAccent.copy(alpha = 0.38f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
            if (automatic) {
                Text("авто", style = MaterialTheme.typography.labelSmall, color = DublStamina)
            }
        }
    }
}

@Composable
private fun RecentChangeBar(
    change: RecentChange,
    onUndo: (() -> Unit)?,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(9.dp),
        color = change.accent.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, change.accent.copy(alpha = 0.24f)),
    ) {
        Row(
            modifier = Modifier.padding(start = 11.dp, end = 5.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Последнее: ${change.text}",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelLarge,
                color = change.accent,
            )
            if (onUndo != null) {
                TextButton(onClick = onUndo) {
                    Text("Отменить", color = change.accent)
                }
            }
        }
    }
}

@Composable
private fun QuickChecksSection(onRoll: (RollContext) -> Unit) {
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
    var expanded by remember { mutableStateOf(false) }
    DublCard(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Боевые действия", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    if (expanded) "Без боевого состояния; цель/СЛ вводится вручную." else "${contexts.size} быстрых действий · свернуто",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Свернуть" else "Открыть") }
        }
        if (expanded) {
            Spacer(Modifier.height(7.dp))
            contexts.chunked(2).forEach { rowContexts ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    rowContexts.forEach { context ->
                        OutlinedButton(
                            onClick = { onRoll(context) },
                            modifier = Modifier.weight(1f),
                        ) { Text(context.title, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    }
                    if (rowContexts.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ContextRollSheet(
    character: DublCharacter,
    context: RollContext,
    developmentCatalog: DevelopmentCatalog,
    onDismiss: () -> Unit,
) {
    val androidContext = LocalContext.current
    val effectCatalog = remember(androidContext.applicationContext) {
        SkillEffectCatalogRepository(androidContext.applicationContext).load()
    }
    val allowedSkillIds = remember(context) { context.allowedSkillIds() }
    val skillOptions = allowedSkillIds.mapNotNull(character::resolveSkill)
    var selectedSkillId by remember(context, character.id) {
        mutableStateOf(skillOptions.firstOrNull()?.id)
    }
    fun attributesFor(skillId: String?): List<AttributeId> = context.allowedAttributes(skillId)
    var selectedAttribute by remember(context, selectedSkillId, character.id) {
        mutableStateOf(attributesFor(selectedSkillId).firstOrNull())
    }
    var flurryProfile by remember(context, character.id) { mutableStateOf(FlurryWeaponProfile.LIGHT) }
    var shortFlurry by remember(context, character.id) { mutableStateOf(false) }
    val basePreset = character.rollPreset(context, selectedSkillId, selectedAttribute)
    val flurryRule = if (context == RollContext.FLURRY) flurryProfile.rule(shortFlurry) else null
    val baseBonus = basePreset.bonus
    val preset = if (flurryRule != null && baseBonus != null) {
        val label = if (shortFlurry) "Короткий шквал" else "Шквал атак"
        basePreset.copy(
            bonus = baseBonus + flurryRule.attackBonus,
            contributions = basePreset.contributions + RollContribution(label, flurryRule.attackBonus),
            formulaText = "${basePreset.formulaText} + $label",
        )
    } else {
        basePreset
    }
    val alreadyAppliedLabels = preset.contributions.map { developmentNormalize(it.label) }.toSet()
    val reminderContext = if (context == RollContext.FLURRY) RollContext.ATTACK else context
    val reminders = remember(character, reminderContext, developmentCatalog, effectCatalog, alreadyAppliedLabels) {
        SkillEffectRules(character, developmentCatalog, effectCatalog)
            .forContext(reminderContext)
            .filterNot { developmentNormalize(it.sourceName) in alreadyAppliedLabels }
    }
    CheckRollSheet(
        title = context.title,
        bonusTitle = "Бонус проверки",
        checkBonus = preset.bonus,
        formulaText = preset.formulaText,
        rememberKey = "context-${character.id}-${context.name}-${selectedSkillId ?: "none"}",
        skillOptions = skillOptions,
        selectedSkillId = selectedSkillId,
        onSkillSelected = { id ->
            selectedSkillId = id
            selectedAttribute = attributesFor(id).firstOrNull()
        },
        attributeOptions = attributesFor(selectedSkillId),
        selectedAttribute = selectedAttribute,
        onAttributeSelected = { selectedAttribute = it },
        automaticContributions = preset.contributions,
        effectReminders = reminders,
        flurryProfile = flurryProfile.takeIf { context == RollContext.FLURRY },
        shortFlurry = shortFlurry,
        onFlurryProfile = { flurryProfile = it },
        onShortFlurry = { shortFlurry = it },
        onDismiss = onDismiss,
    )
}

@Composable
private fun KeyStats(
    character: DublCharacter,
    onStatClick: (StatId) -> Unit,
    onFortitudeRoll: () -> Unit,
    onReflexesRoll: () -> Unit,
    onInitiativeRoll: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        StatId.entries.chunked(3).forEach { rowStats ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                rowStats.forEach { stat ->
                    val info = statInfo(stat, character)
                    StatTile(
                        stat = stat,
                        value = info.value,
                        accent = statAccent(stat),
                        modifier = Modifier.weight(1f),
                        onClick = { onStatClick(stat) },
                        onQuickRoll = when (stat) {
                            StatId.FORTITUDE -> onFortitudeRoll
                            StatId.REFLEXES -> onReflexesRoll
                            StatId.INITIATIVE -> onInitiativeRoll
                            else -> null
                        },
                    )
                }
            }
        }
    }
}

private fun statAccent(id: StatId): Color = when (id) {
    StatId.DEFENSE -> Color(0xFFBB5863)
    StatId.REFLEXES -> Color(0xFF65A0A8)
    StatId.INITIATIVE -> DublGold
    StatId.FORTITUDE -> Color(0xFF7FA06F)
    StatId.RUN -> Color(0xFF6C96B5)
    StatId.SIZE -> Color(0xFF8E80B5)
}

@Composable
private fun StatTile(
    stat: StatId,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onQuickRoll: (() -> Unit)? = null,
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = accent.copy(alpha = 0.055f),
        border = BorderStroke(1.25.dp, accent.copy(alpha = 0.48f)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(
                    text = stat.glyph,
                    fontSize = 17.sp,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = accent,
                )
                if (onQuickRoll != null) {
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable(onClick = onQuickRoll),
                        shape = RoundedCornerShape(6.dp),
                        color = accent.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, accent.copy(alpha = 0.35f)),
                    ) {
                        Text(
                            text = "⚄",
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = accent,
                        )
                    }
                }
            }
            Spacer(Modifier.height(3.dp))
            Text(
                text = value,
                fontSize = 21.sp,
                lineHeight = 24.sp,
                fontWeight = FontWeight.Bold,
                color = accent,
            )
            Text(
                text = stat.title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
    }
}

private fun statInfo(id: StatId, character: DublCharacter): StatInfo = when (id) {
    StatId.DEFENSE -> StatInfo(
        id = id,
        value = character.defense.toString(),
        formula = "10 − Размер + Скорость + Ловкость + штраф нагрузки",
        breakdown = listOf(
            "10 − ${character.size} + ${character.speed} + ${character.dexterity}",
            "Штраф нагрузки: ${signed(character.equipmentLoadPenalty)}",
            "Итог: ${character.defense}",
        ),
    )
    StatId.REFLEXES -> StatInfo(
        id = id,
        value = signed(character.reflexes),
        formula = "Скорость + Ловкость + нагрузка + Быстрые рефлексы",
        breakdown = listOf(
            "${character.speed} + ${character.dexterity}",
            "Штраф нагрузки: ${signed(character.equipmentLoadPenalty)}",
            "Быстрые рефлексы: ${signed(character.quickReflexesBonus)}",
            "Итог: ${signed(character.reflexes)}",
        ),
    )
    StatId.INITIATIVE -> StatInfo(
        id = id,
        value = signed(character.initiative),
        formula = "Скорость + Восприятие + Улучшенная инициатива + Повелитель Бури",
        breakdown = listOf(
            "${character.speed} + ${character.perception}",
            "Улучшенная инициатива: ${signed(character.improvedInitiativeBonus)}",
            "Повелитель Бури: ${signed(character.stormLordBonus)}",
            "Итог: ${signed(character.initiative)}",
        ),
    )
    StatId.FORTITUDE -> StatInfo(
        id = id,
        value = signed(character.fortitude),
        formula = "Телосложение + Воля + Стойкий + Неподвижная Гора",
        breakdown = listOf(
            "${character.constitution} + ${character.will}",
            "Стойкий: ${signed(character.stalwartBonus)}",
            "Неподвижная Гора: ${signed(character.stillMountainBonus)}",
            "Итог: ${signed(character.fortitude)}",
        ),
    )
    StatId.RUN -> StatInfo(
        id = id,
        value = "${formatNumber(character.runFull)} м",
        formula = "Базовый бег + (Скорость + Повелитель Бури) × множитель + нагрузка + Бегун",
        breakdown = listOf(
            "Базовый бег: ${formatNumber(character.runBase)} м",
            "Скорость: ${character.speed}",
            "Повелитель Бури: ${signed(character.runStormSpeedBonus)}",
            "Множитель: ${formatNumber(character.runMultiplier)}",
            "Штраф нагрузки: ${signed(character.equipmentLoadPenalty)}",
            "Бегун: ${signed(character.runRunnerBonus)}",
            "Итог: ${formatNumber(character.runFull)} м",
        ),
        note = "Множитель зависит от Размера (${character.size}) и количества ног (${character.legs}).",
    )
    StatId.SIZE -> StatInfo(
        id = id,
        value = character.size.toString(),
        formula = "Задаётся напрямую",
        breakdown = listOf(
            "Размер: ${character.size}",
            "Модификатор Силы: ${signed(character.strengthSizeModifier)}",
            "Модификатор Скорости: ${signed(character.speedSizeModifier)}",
        ),
        note = "Размер влияет на Силу, Скорость, Защиту, здоровье и Бег.",
    )
}

private fun defaultSkillGroups(skills: List<ResolvedSkill>): List<SheetGroup> = SkillCategory.entries.mapNotNull { category ->
    val ids = skills.filter { it.category == category }.map { it.id }
    ids.takeIf { it.isNotEmpty() }?.let { SheetGroup("skills:${category.name}", category.title, it) }
}

private fun developmentSheetSections(
    character: DublCharacter,
    catalog: DevelopmentCatalog,
): List<DevelopmentSheetSection> = DevelopmentRules(
    character,
    catalog,
    DevelopmentProgress(character.development),
).ownedSheetSections()

private fun ownedDevelopmentItems(
    character: DublCharacter,
    catalog: DevelopmentCatalog,
): List<DevelopmentSheetItem> = developmentSheetSections(character, catalog).flatMap { it.items }.distinctBy { it.entry.id }

private fun defaultDevelopmentGroups(
    character: DublCharacter,
    catalog: DevelopmentCatalog,
): List<SheetGroup> = developmentSheetSections(character, catalog).map { section ->
    SheetGroup(
        id = "development:${section.type.name}",
        title = developmentSectionTitle(section.type),
        itemIds = section.items.map { it.entry.id },
    )
}

private fun developmentSectionTitle(type: DevelopmentSheetSectionType): String = when (type) {
    DevelopmentSheetSectionType.REGULAR -> "Обычные навыки"
    DevelopmentSheetSectionType.SPECIAL -> "Спец. навыки"
    DevelopmentSheetSectionType.MARTIAL_ARTS -> "Боевые искусства"
    DevelopmentSheetSectionType.CHI -> "ЦИ"
}

private fun LazyListScope.ownedSkillSectionItems(
    character: DublCharacter,
    skills: List<ResolvedSkill>,
    groups: List<SheetGroup>,
    byId: Map<String, ResolvedSkill>,
    onGroupsChanged: (List<SheetGroup>) -> Unit,
    onConfigure: () -> Unit,
    onSkillClick: (ResolvedSkill) -> Unit,
) {
    item(key = "owned-skills-title") {
        SectionTitle(
            title = "Умения",
            trailing = if (skills.isEmpty()) "Нет взятых" else "Группы · ${skills.size}",
            onTrailingClick = onConfigure.takeIf { skills.isNotEmpty() },
        )
    }
    if (skills.isEmpty()) {
        item(key = "owned-skills-empty") {
            Text(
                "Здесь появятся все умения с рангом 1 и выше.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    groups.forEach { group ->
        val groupSkills = group.itemIds.mapNotNull(byId::get)
        item(key = "skill-group:${group.id}") {
            SheetGroupHeader(
                title = group.title,
                count = groupSkills.size,
                collapsed = group.collapsed,
                accent = groupSkills.firstOrNull()?.let(::skillAccent) ?: DublAccent,
                onToggle = { onGroupsChanged(SheetGroupingRules.toggleCollapsed(groups, group.id)) },
            )
        }
        if (!group.collapsed && groupSkills.isNotEmpty()) {
            item(key = "skill-grid:${group.id}") {
                val (left, right) = SheetGroupingRules.balancedColumns(groupSkills) { compactTileWeight(it.name) }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        left.forEach { skill ->
                            CompactSkillTile(
                                character = character,
                                skill = skill,
                                modifier = Modifier.fillMaxWidth(),
                                onClick = { onSkillClick(skill) },
                            )
                        }
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        right.forEach { skill ->
                            CompactSkillTile(
                                character = character,
                                skill = skill,
                                modifier = Modifier.fillMaxWidth(),
                                onClick = { onSkillClick(skill) },
                            )
                        }
                    }
                }
            }
        }
    }
    item(key = "owned-skills-help") {
        Text(
            "Тап — выбрать характеристику и бросить · группы и порядок меняются через «Группы»",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun LazyListScope.ownedDevelopmentSectionItems(
    items: List<DevelopmentSheetItem>,
    groups: List<SheetGroup>,
    byId: Map<String, DevelopmentSheetItem>,
    parentById: Map<String, String?>,
    invalidIds: Set<String>,
    onGroupsChanged: (List<SheetGroup>) -> Unit,
    onConfigure: () -> Unit,
    onEntryClick: (DevelopmentEntry) -> Unit,
) {
    if (items.isEmpty()) return
    item(key = "owned-development-title") {
        SectionTitle("Взятые навыки", trailing = "Группы · ${items.size}", onTrailingClick = onConfigure)
    }
    groups.forEach { group ->
        val orderedIds = SheetGroupingRules.hierarchicalOrder(group.itemIds, parentById)
        val groupItems = orderedIds.mapNotNull(byId::get)
        item(key = "development-group:${group.id}") {
            SheetGroupHeader(
                title = group.title,
                count = groupItems.size,
                collapsed = group.collapsed,
                accent = groupItems.firstOrNull()?.entry?.let(::developmentAccent) ?: DublGold,
                onToggle = { onGroupsChanged(SheetGroupingRules.toggleCollapsed(groups, group.id)) },
            )
        }
        if (!group.collapsed && groupItems.isNotEmpty()) {
            item(key = "development-grid:${group.id}") {
                val groupIds = groupItems.map { it.entry.id }
                val blocks = SheetGroupingRules.hierarchyBlocks(groupIds, parentById)
                    .map { blockIds -> blockIds.mapNotNull(byId::get) }
                val (leftBlocks, rightBlocks) = SheetGroupingRules.balancedColumns(blocks) { block ->
                    block.sumOf { item ->
                        compactTileWeight(item.entry.name) +
                            SheetGroupingRules.localDepth(item.entry.id, groupIds, parentById).coerceAtMost(1)
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        leftBlocks.flatten().forEach { item ->
                            CompactDevelopmentTile(
                                item = item,
                                displayDepth = SheetGroupingRules.localDepth(item.entry.id, groupIds, parentById),
                                invalid = item.entry.id in invalidIds,
                                modifier = Modifier.fillMaxWidth(),
                                onClick = if (item.source == DevelopmentSheetItemSource.DEVELOPMENT) {
                                    { onEntryClick(item.entry) }
                                } else null,
                            )
                        }
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        rightBlocks.flatten().forEach { item ->
                            CompactDevelopmentTile(
                                item = item,
                                displayDepth = SheetGroupingRules.localDepth(item.entry.id, groupIds, parentById),
                                invalid = item.entry.id in invalidIds,
                                modifier = Modifier.fillMaxWidth(),
                                onClick = if (item.source == DevelopmentSheetItemSource.DEVELOPMENT) {
                                    { onEntryClick(item.entry) }
                                } else null,
                            )
                        }
                    }
                }
            }
        }
    }
    item(key = "owned-development-help") {
        Text(
            "↳ показывает связь внутри дерева · порядок и группы меняются через «Группы»",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun compactTileWeight(label: String): Int = when {
    label.length >= 34 -> 3
    label.length >= 20 -> 2
    else -> 1
}

@Composable
private fun SheetGroupHeader(
    title: String,
    count: Int,
    collapsed: Boolean,
    accent: Color,
    onToggle: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(9.dp))
            .clickable(onClick = onToggle),
        shape = RoundedCornerShape(9.dp),
        color = accent.copy(alpha = 0.055f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.30f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (collapsed) "▸" else "▾",
                color = accent,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.width(7.dp))
            Text(
                title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                count.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = accent,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun CompactSkillTile(
    character: DublCharacter,
    skill: ResolvedSkill,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val selected = skill.stockAttribute
    val calculation = character.skillCalculationForRoll(skill, selected)
    val bonus = calculation.total?.let(::signed) ?: "—"
    val accent = skillAccent(skill)
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color = accent.copy(alpha = 0.045f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.34f)),
    ) {
        Column(Modifier.padding(horizontal = 9.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    skill.name,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    bonus,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = accent,
                )
            }
            Spacer(Modifier.height(3.dp))
            Text(
                "${selected.shortTitle} · ранг ${skill.rank}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun skillAccent(skill: ResolvedSkill): Color = when (skill.category) {
    SkillCategory.COMBAT -> DublDanger
    SkillCategory.PHYSICAL -> DublStamina
    SkillCategory.FIELD -> Color(0xFF71A492)
    SkillCategory.SOCIAL -> DublAccent
    SkillCategory.KNOWLEDGE -> DublMana
    SkillCategory.TECHNICAL -> DublGold
    SkillCategory.CUSTOM -> Color(0xFF8E80B5)
}

@Composable
private fun CompactDevelopmentTile(
    item: DevelopmentSheetItem,
    displayDepth: Int,
    invalid: Boolean,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)?,
) {
    val accent = if (invalid) DublDanger else developmentAccent(item.entry)
    val interactiveModifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(9.dp))
            .then(interactiveModifier),
        shape = RoundedCornerShape(9.dp),
        color = accent.copy(alpha = 0.04f),
        border = BorderStroke(1.dp, accent.copy(alpha = if (invalid) 0.62f else 0.30f)),
    ) {
        Row(
            modifier = Modifier.padding(
                start = 10.dp + (displayDepth.coerceAtMost(2) * 6).dp,
                end = 10.dp,
                top = 7.dp,
                bottom = 7.dp,
            ),
            verticalAlignment = Alignment.Top,
        ) {
            if (displayDepth > 0) {
                Text(
                    "↳",
                    style = MaterialTheme.typography.labelLarge,
                    color = accent.copy(alpha = 0.82f),
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.width(5.dp))
            }
            Text(
                item.entry.name,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.width(7.dp))
            Text(
                "${item.rank}",
                style = MaterialTheme.typography.labelLarge,
                color = accent,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

private fun developmentAccent(entry: DevelopmentEntry): Color = when {
    entry.isMartialArt -> DublDanger
    entry.isChiDevelopment -> Color(0xFF8E80B5)
    entry.isSpecialDevelopment -> DublAccent
    else -> DublGold
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OwnedDevelopmentDetailSheet(
    entry: DevelopmentEntry,
    character: DublCharacter,
    catalog: DevelopmentCatalog,
    onDismiss: () -> Unit,
) {
    val progress = DevelopmentProgress(character.development)
    val rules = DevelopmentRules(character, catalog, progress)
    val rank = progress.rank(entry.id)
    val checks = rules.requirements(entry)
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 690.dp)
                .containSheetOverscroll()
                .verticalScroll(rememberScrollState())
                .padding(start = 18.dp, end = 18.dp, bottom = 26.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(entry.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(
                        "${entry.section} · ${entry.category}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text("Ранг $rank/${entry.maxRank}", style = MaterialTheme.typography.labelLarge, color = DublGold, fontWeight = FontWeight.Bold)
            }
            if (entry.tags.isNotEmpty()) {
                Text(entry.tags.joinToString(" · "), style = MaterialTheme.typography.labelMedium, color = DublGold)
            }
            if (entry.benefit.isNotBlank()) {
                Text("Эффект", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(entry.benefit)
            }
            if (entry.notes.isNotBlank()) {
                Text("Особое", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(entry.notes)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
            Text("Требования", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            checks.forEach { check ->
                val color = when (check.status) {
                    RequirementStatus.OK -> Color(0xFF71A492)
                    RequirementStatus.FAIL -> DublDanger
                    RequirementStatus.MANUAL -> DublGold
                }
                Text(
                    "${when (check.status) { RequirementStatus.OK -> "✓"; RequirementStatus.FAIL -> "✕"; RequirementStatus.MANUAL -> "?" }} ${check.text}",
                    style = MaterialTheme.typography.bodySmall,
                    color = color,
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
            Text(
                if (entry.isAbility) "Стоимость доступа: ${rules.abilityCost(entry, progress.optionIndex(entry.id))} ОС"
                else "Стоимость ранга: ${entry.cost} XP",
                style = MaterialTheme.typography.labelLarge,
                color = DublGold,
            )
            if (entry.conflictNote.isNotBlank()) {
                Text("Расхождение книги", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(entry.conflictNote, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun AttributeGrid(
    character: DublCharacter,
    onAttributeClick: (AttributeId) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = if (maxWidth >= 620.dp) 4 else 2
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AttributeId.entries.chunked(columns).forEach { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    rowItems.forEach { id ->
                        AttributeCard(
                            id = id,
                            character = character,
                            modifier = Modifier.weight(1f),
                            onClick = { onAttributeClick(id) },
                        )
                    }
                    repeat(columns - rowItems.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

private fun attributeAccent(id: AttributeId): Color = when (id) {
    AttributeId.STRENGTH -> Color(0xFFB96363)
    AttributeId.DEXTERITY -> Color(0xFFC08D52)
    AttributeId.CONSTITUTION -> Color(0xFF7FA06F)
    AttributeId.SPEED -> Color(0xFF65A0A8)
    AttributeId.INTELLIGENCE -> Color(0xFF718FB8)
    AttributeId.PERCEPTION -> Color(0xFF8E80B5)
    AttributeId.WILL -> Color(0xFFA87193)
    AttributeId.CHARISMA -> Color(0xFFC07E6D)
}

@Composable
private fun AttributeCard(
    id: AttributeId,
    character: DublCharacter,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val base = character.attributes[id]?.base ?: character.attributeRaw(id)
    val total = character.attribute(id)
    val effectiveModifier = total - base
    val accent = attributeAccent(id)
    val modified = effectiveModifier != 0

    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = if (modified) DublGold.copy(alpha = 0.055f) else accent.copy(alpha = 0.055f),
        border = BorderStroke(
            if (modified) 2.dp else 1.5.dp,
            if (modified) DublGold.copy(alpha = 0.78f) else accent.copy(alpha = 0.72f),
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = id.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (modified) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "База $base · ${signed(effectiveModifier)}",
                        style = MaterialTheme.typography.labelLarge,
                        color = DublGold,
                    )
                }
            }
            Text(
                text = total.toString(),
                fontSize = 31.sp,
                lineHeight = 35.sp,
                fontWeight = FontWeight.Bold,
                color = accent,
            )
        }
    }
}

@Composable
private fun CustomConditionChip(
    condition: CustomCondition,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(9.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(9.dp),
        color = DublAccentSoft.copy(alpha = 0.28f),
        border = BorderStroke(1.dp, DublAccent.copy(alpha = 0.28f)),
    ) {
        Text(
            text = condition.title,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConditionPickerSheet(
    active: Set<CharacterConditionId>,
    autoWeakness: Boolean,
    overrides: Map<CharacterConditionId, ConditionLocalOverride>,
    customConditions: List<CustomCondition>,
    onToggle: (CharacterConditionId) -> Unit,
    onInfo: (CharacterConditionId) -> Unit,
    onToggleCustom: (String, Boolean) -> Unit,
    onEditCustom: (String) -> Unit,
    onAddCustom: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .containSheetOverscroll()
                .padding(start = 18.dp, end = 18.dp, bottom = 22.dp),
        ) {
            Text("Состояния", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                "Слабость включается автоматически при нулевой Выносливости. Локальные правки и свои состояния действуют только для этого персонажа.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            LazyColumn(
                modifier = Modifier.heightIn(max = 520.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(CharacterConditionId.entries, key = { it.name }) { condition ->
                    val auto = condition == CharacterConditionId.WEAKNESS && autoWeakness
                    val checked = condition in active || auto
                    val local = overrides[condition]
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        color = if (checked) DublAccentSoft.copy(alpha = 0.38f) else MaterialTheme.colorScheme.surface,
                        border = BorderStroke(
                            1.dp,
                            if (checked) DublAccent.copy(alpha = 0.42f)
                            else MaterialTheme.colorScheme.outline.copy(alpha = 0.55f),
                        ),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = if (auto) null else { _ -> onToggle(condition) },
                                enabled = !auto,
                            )
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(7.dp))
                                    .clickable(enabled = !auto) { onToggle(condition) }
                                    .padding(vertical = 6.dp),
                            ) {
                                Text(
                                    text = local?.title ?: condition.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                )
                                if (local != null) {
                                    Text(
                                        "локальная правка",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = DublGold,
                                    )
                                }
                            }
                            if (auto) {
                                Text("авто", style = MaterialTheme.typography.labelSmall, color = DublStamina)
                                Spacer(Modifier.width(4.dp))
                            }
                            TextButton(onClick = { onInfo(condition) }) { Text("?") }
                        }
                    }
                }
                if (customConditions.isNotEmpty()) {
                    item {
                        Text(
                            "Свои состояния",
                            modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    items(customConditions, key = { it.id }) { condition ->
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(
                                    checked = condition.active,
                                    onCheckedChange = { checked -> onToggleCustom(condition.id, checked) },
                                )
                                Text(condition.title, modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                                TextButton(onClick = { onEditCustom(condition.id) }) { Text("Изменить") }
                            }
                        }
                    }
                }
                item {
                    OutlinedButton(
                        onClick = onAddCustom,
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    ) { Text("Добавить своё состояние") }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConditionDetailSheet(
    condition: CharacterConditionId,
    title: String,
    summary: String,
    canonicalTitle: String,
    canonicalSummary: String,
    active: Boolean,
    automatic: Boolean,
    onToggle: () -> Unit,
    onSaveLocal: (String, String) -> Unit,
    onResetLocal: () -> Unit,
    onDismiss: () -> Unit,
) {
    var localTitle by remember(condition, title) { mutableStateOf(title) }
    var localSummary by remember(condition, summary) { mutableStateOf(summary) }
    val hasLocal = title != canonicalTitle || summary != canonicalSummary
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .containSheetOverscroll()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(summary, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (automatic) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "Сейчас состояние активно автоматически из-за нулевой Выносливости.",
                    style = MaterialTheme.typography.labelLarge,
                    color = DublStamina,
                )
            }
            Spacer(Modifier.height(18.dp))
            if (!automatic) {
                if (active) {
                    OutlinedButton(onClick = onToggle, modifier = Modifier.fillMaxWidth()) { Text("Убрать состояние") }
                } else {
                    Button(onClick = onToggle, modifier = Modifier.fillMaxWidth()) { Text("Добавить состояние") }
                }
            }
            Spacer(Modifier.height(18.dp))
            Text("Локальная правка состояния", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "Правка действует только для этого персонажа и не меняет импортированный рулбук.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = localTitle,
                onValueChange = { localTitle = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Название") },
                singleLine = true,
            )
            OutlinedTextField(
                value = localSummary,
                onValueChange = { localSummary = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Описание / локальная трактовка") },
                minLines = 3,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { onSaveLocal(localTitle, localSummary) },
                    modifier = Modifier.weight(1f),
                ) { Text("Сохранить локально") }
                OutlinedButton(
                    onClick = onResetLocal,
                    enabled = hasLocal,
                    modifier = Modifier.weight(1f),
                ) { Text("Сбросить к рулбуку") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomConditionSheet(
    condition: CustomCondition?,
    onSave: (String, String, Boolean) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var title by remember(condition?.id) { mutableStateOf(condition?.title.orEmpty()) }
    var description by remember(condition?.id) { mutableStateOf(condition?.description.orEmpty()) }
    var active by remember(condition?.id) { mutableStateOf(condition?.active ?: false) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .containSheetOverscroll()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                if (condition == null) "Добавить своё состояние" else "Изменить своё состояние",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            OutlinedTextField(title, { title = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Название") })
            OutlinedTextField(
                description,
                { description = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Описание / домашнее правило") },
                minLines = 3,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = active, onCheckedChange = { active = it })
                Text("Активно")
            }
            Button(
                onClick = { onSave(title, description, active) },
                enabled = title.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Сохранить") }
            if (condition != null) {
                TextButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) { Text("Удалить") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GroupManagerSheet(
    title: String,
    groups: List<SheetGroup>,
    itemLabels: Map<String, String>,
    itemParentIds: Map<String, String?>,
    treeRootIds: Set<String>,
    ungroupedId: String,
    onGroupsChanged: (List<SheetGroup>) -> Unit,
    onDismiss: () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var newGroupName by remember(title) { mutableStateOf("") }
    var editingId by remember(title) { mutableStateOf<String?>(null) }
    var editingName by remember(title) { mutableStateOf("") }
    val groupBounds = remember(title) { mutableMapOf<String, Rect>() }
    val itemBounds = remember(title) { mutableMapOf<String, Rect>() }
    var hoveredGroupId by remember(title) { mutableStateOf<String?>(null) }
    val groupListState = rememberLazyListState()
    val dragScope = rememberCoroutineScope()
    val density = LocalDensity.current
    var groupListBounds by remember(title) { mutableStateOf(Rect.Zero) }

    fun autoScrollGroups(windowY: Float) {
        if (groupListBounds == Rect.Zero) return
        val edge = 54.dp
        val edgePx = with(density) { edge.toPx() }
        val delta = when {
            windowY < groupListBounds.top + edgePx -> -30f
            windowY > groupListBounds.bottom - edgePx -> 30f
            else -> 0f
        }
        if (delta != 0f) dragScope.launch { groupListState.scrollBy(delta) }
    }

    val visualGroups = groups.map { group ->
        group.copy(itemIds = SheetGroupingRules.hierarchicalOrder(group.itemIds, itemParentIds))
    }
    val allVisualIds = visualGroups.flatMap { it.itemIds }

    fun groupAt(windowY: Float, excludingId: String? = null): String? {
        val candidates = groupBounds.entries.filter { it.key != excludingId }
        val direct = candidates.firstOrNull { (_, bounds) -> windowY >= bounds.top && windowY <= bounds.bottom }
        if (direct != null) return direct.key
        return candidates.minByOrNull { (_, bounds) ->
            kotlin.math.abs(windowY - ((bounds.top + bounds.bottom) / 2f))
        }?.key
    }

    fun moveBlockAt(block: List<String>, windowY: Float) {
        val moving = block.toSet()
        val targetGroupId = groupAt(windowY) ?: return
        val targetGroup = visualGroups.firstOrNull { it.id == targetGroupId } ?: return
        val remainingTarget = targetGroup.itemIds.filterNot { it in moving }
        val candidateIds = remainingTarget.filter { itemBounds.containsKey(it) }
        val targetItemId = candidateIds.minByOrNull { itemId ->
            val bounds = itemBounds[itemId] ?: return@minByOrNull Float.MAX_VALUE
            kotlin.math.abs(windowY - ((bounds.top + bounds.bottom) / 2f))
        }
        val targetIndex = if (targetItemId == null) {
            remainingTarget.size
        } else {
            val bounds = itemBounds[targetItemId]
            val base = remainingTarget.indexOf(targetItemId).coerceAtLeast(0)
            if (bounds != null && windowY > (bounds.top + bounds.bottom) / 2f) base + 1 else base
        }
        val moved = SheetGroupingRules.moveItems(
            groups = visualGroups,
            itemIds = block,
            targetGroupId = targetGroupId,
            targetIndex = targetIndex,
        ).map { group ->
            group.copy(itemIds = SheetGroupingRules.hierarchicalOrder(group.itemIds, itemParentIds))
        }
        onGroupsChanged(moved)
    }

    fun moveGroupAt(groupId: String, windowY: Float) {
        val targetGroupId = groupAt(windowY, excludingId = groupId) ?: return
        val remaining = visualGroups.filterNot { it.id == groupId }
        val targetIndex = remaining.indexOfFirst { it.id == targetGroupId }.takeIf { it >= 0 } ?: return
        val targetBounds = groupBounds[targetGroupId]
        val insertion = if (targetBounds != null && windowY > (targetBounds.top + targetBounds.bottom) / 2f) {
            targetIndex + 1
        } else {
            targetIndex
        }
        onGroupsChanged(SheetGroupingRules.moveGroupToIndex(visualGroups, groupId, insertion))
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .containSheetOverscroll()
                .padding(start = 16.dp, end = 16.dp, bottom = 22.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = DublAccent.copy(alpha = 0.055f),
                border = BorderStroke(1.dp, DublAccent.copy(alpha = 0.24f)),
            ) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                    Text(
                        "Drag & drop",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = DublAccent,
                    )
                    Text(
                        "Зажмите ≡ у группы или карточки и тяните. Корень спец. ветки / боевого искусства переносит всё дерево; ребёнок переносится отдельно. В одной группе связь родитель → дети восстанавливается автоматически.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            LazyColumn(
                modifier = Modifier
                    .heightIn(max = 520.dp)
                    .onGloballyPositioned { groupListBounds = it.boundsInWindow() },
                state = groupListState,
                verticalArrangement = Arrangement.spacedBy(9.dp),
                contentPadding = PaddingValues(bottom = 4.dp),
            ) {
                items(visualGroups, key = { it.id }) { group ->
                    DisposableEffect(group.id) {
                        onDispose { groupBounds.remove(group.id) }
                    }
                    var groupDragOffsetY by remember(group.id) { mutableStateOf(0f) }
                    var groupDragging by remember(group.id) { mutableStateOf(false) }
                    var groupPointerWindowY by remember(group.id) { mutableStateOf(0f) }
                    val isDropTarget = hoveredGroupId == group.id
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .zIndex(if (groupDragging) 8f else 0f)
                            .graphicsLayer { translationY = groupDragOffsetY }
                            .onGloballyPositioned { coordinates ->
                                groupBounds[group.id] = coordinates.boundsInWindow()
                            },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isDropTarget) DublAccent.copy(alpha = 0.055f) else MaterialTheme.colorScheme.surface,
                        border = BorderStroke(
                            if (isDropTarget) 2.dp else 1.dp,
                            if (isDropTarget) DublAccent.copy(alpha = 0.72f)
                            else MaterialTheme.colorScheme.outline.copy(alpha = 0.42f),
                        ),
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .pointerInput(group.id) {
                                            var accumulated = 0f
                                            detectDragGesturesAfterLongPress(
                                                onDragStart = { localOffset ->
                                                    accumulated = 0f
                                                    groupDragOffsetY = 0f
                                                    groupDragging = true
                                                    val bounds = groupBounds[group.id]
                                                    groupPointerWindowY = (bounds?.top ?: 0f) + localOffset.y
                                                    autoScrollGroups(groupPointerWindowY)
                                                    hoveredGroupId = groupAt(groupPointerWindowY, excludingId = group.id)
                                                },
                                                onDragCancel = {
                                                    accumulated = 0f
                                                    groupDragOffsetY = 0f
                                                    groupDragging = false
                                                    hoveredGroupId = null
                                                },
                                                onDragEnd = {
                                                    groupDragOffsetY = 0f
                                                    groupDragging = false
                                                    moveGroupAt(group.id, groupPointerWindowY)
                                                    hoveredGroupId = null
                                                    accumulated = 0f
                                                },
                                                onDrag = { change, dragAmount ->
                                                    change.consume()
                                                    accumulated += dragAmount.y
                                                    groupDragOffsetY = accumulated
                                                    groupPointerWindowY += dragAmount.y
                                                    autoScrollGroups(groupPointerWindowY)
                                                    hoveredGroupId = groupAt(groupPointerWindowY, excludingId = group.id)
                                                },
                                            )
                                        },
                                    shape = RoundedCornerShape(8.dp),
                                    color = DublAccent.copy(alpha = 0.10f),
                                    border = BorderStroke(1.dp, DublAccent.copy(alpha = 0.32f)),
                                ) {
                                    Text(
                                        "≡",
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = DublAccent,
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                                Surface(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            onGroupsChanged(SheetGroupingRules.toggleCollapsed(visualGroups, group.id))
                                        },
                                    shape = RoundedCornerShape(8.dp),
                                    color = DublAccent.copy(alpha = 0.06f),
                                ) {
                                    Text(
                                        if (group.collapsed) "▸" else "▾",
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                        fontWeight = FontWeight.Bold,
                                        color = DublAccent,
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        group.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        "${group.itemIds.size} элементов · за ≡ двигается вся группа",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(7.dp),
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        editingId = group.id
                                        editingName = group.title
                                    },
                                    modifier = Modifier.weight(1f),
                                ) { Text("Переименовать", maxLines = 1) }
                                if (group.id != ungroupedId) {
                                    OutlinedButton(
                                        onClick = {
                                            onGroupsChanged(SheetGroupingRules.deleteGroup(visualGroups, group.id, ungroupedId))
                                            if (editingId == group.id) editingId = null
                                        },
                                        modifier = Modifier.weight(1f),
                                    ) { Text("Удалить", color = DublDanger, maxLines = 1) }
                                }
                            }

                            if (editingId == group.id) {
                                OutlinedTextField(
                                    value = editingName,
                                    onValueChange = { editingName = it },
                                    label = { Text("Название группы") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                ) {
                                    TextButton(onClick = { editingId = null }) { Text("Отмена") }
                                    TextButton(
                                        onClick = {
                                            onGroupsChanged(SheetGroupingRules.renameGroup(visualGroups, group.id, editingName))
                                            editingId = null
                                            focusManager.clearFocus()
                                        },
                                    ) { Text("Сохранить") }
                                }
                            }

                            if (!group.collapsed) {
                                if (group.itemIds.isEmpty()) {
                                    Text(
                                        "Пустая группа — перетащите сюда карточку.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                } else {
                                    val groupIds = group.itemIds
                                    group.itemIds.forEach { itemId ->
                                        DisposableEffect(itemId) {
                                            onDispose { itemBounds.remove(itemId) }
                                        }
                                        val depth = SheetGroupingRules.localDepth(itemId, groupIds, itemParentIds)
                                        val block = if (itemId in treeRootIds) {
                                            SheetGroupingRules.subtreeBlock(itemId, itemParentIds, allVisualIds)
                                        } else {
                                            listOf(itemId)
                                        }
                                        GroupManagerItemCard(
                                            itemId = itemId,
                                            label = itemLabels[itemId] ?: itemId,
                                            depth = depth,
                                            treeSize = block.size,
                                            movesTree = itemId in treeRootIds && block.size > 1,
                                            onBounds = { bounds -> itemBounds[itemId] = bounds },
                                            onDragWindowY = { windowY ->
                                                autoScrollGroups(windowY)
                                                hoveredGroupId = groupAt(windowY)
                                            },
                                            onDragCancel = { hoveredGroupId = null },
                                            onDropWindowY = { windowY ->
                                                hoveredGroupId = null
                                                moveBlockAt(block, windowY)
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
            OutlinedTextField(
                value = newGroupName,
                onValueChange = { newGroupName = it },
                label = { Text("Новая группа") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        onGroupsChanged(emptyList())
                        focusManager.clearFocus()
                    },
                    modifier = Modifier.weight(1f),
                ) { Text("По умолчанию") }
                Button(
                    onClick = {
                        val id = "user:${System.nanoTime()}"
                        onGroupsChanged(SheetGroupingRules.addGroup(visualGroups, id, newGroupName))
                        newGroupName = ""
                        focusManager.clearFocus()
                    },
                    enabled = newGroupName.isNotBlank(),
                    modifier = Modifier.weight(1f),
                ) { Text("Создать") }
            }
        }
    }
}

@Composable
private fun GroupManagerItemCard(
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
    var dragOffsetY by remember(itemId) { mutableStateOf(0f) }
    var dragging by remember(itemId) { mutableStateOf(false) }
    var cardBounds by remember(itemId) { mutableStateOf(Rect.Zero) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = (depth.coerceAtMost(3) * 12).dp)
            .zIndex(if (dragging) 5f else 0f)
            .graphicsLayer { translationY = dragOffsetY }
            .onGloballyPositioned { coordinates ->
                cardBounds = coordinates.boundsInWindow()
                onBounds(cardBounds)
            }
            .pointerInput(itemId, movesTree, treeSize) {
                var accumulated = 0f
                var pointerWindowY = 0f
                detectDragGesturesAfterLongPress(
                    onDragStart = { localOffset ->
                        accumulated = 0f
                        dragOffsetY = 0f
                        dragging = true
                        pointerWindowY = cardBounds.top + localOffset.y
                        onDragWindowY(pointerWindowY)
                    },
                    onDragCancel = {
                        accumulated = 0f
                        dragOffsetY = 0f
                        dragging = false
                        onDragCancel()
                    },
                    onDragEnd = {
                        dragOffsetY = 0f
                        dragging = false
                        onDropWindowY(pointerWindowY)
                        accumulated = 0f
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        accumulated += dragAmount.y
                        dragOffsetY = accumulated
                        pointerWindowY += dragAmount.y
                        onDragWindowY(pointerWindowY)
                    },
                )
            },
        shape = RoundedCornerShape(10.dp),
        color = if (dragging) DublAccent.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f),
        border = BorderStroke(
            1.dp,
            if (dragging) DublAccent.copy(alpha = 0.75f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.30f),
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "≡",
                fontSize = 23.sp,
                fontWeight = FontWeight.Bold,
                color = DublAccent,
            )
            Spacer(Modifier.width(9.dp))
            if (depth > 0) {
                Text("↳", color = DublAccent.copy(alpha = 0.80f), fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(5.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (movesTree) {
                    Text(
                        "Корень дерева · переносит $treeSize элементов",
                        style = MaterialTheme.typography.labelSmall,
                        color = DublGold,
                    )
                } else if (depth > 0) {
                    Text(
                        "Дочерний навык · переносится отдельно",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Text(
                "Зажать\nи тянуть",
                style = MaterialTheme.typography.labelSmall,
                color = DublAccent,
                textAlign = TextAlign.End,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SkillAttributeChoiceSheet(
    character: DublCharacter,
    skill: ResolvedSkill,
    onConfirm: (AttributeId) -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedAttribute by remember(skill.id) { mutableStateOf(skill.stockAttribute) }
    val calculation = character.skillCalculationForRoll(skill, selectedAttribute)
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .containSheetOverscroll()
                .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("${skill.name}: характеристика", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                "По умолчанию выбрана стоковая характеристика умения. Для этого броска можно заменить её на любую другую.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = skillAccent(skill).copy(alpha = 0.055f),
                border = BorderStroke(1.dp, skillAccent(skill).copy(alpha = 0.30f)),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(selectedAttribute.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            calculation.formulaText(skill, showConfiguredOptions = false),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        calculation.total?.let(::signed) ?: "—",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = skillAccent(skill),
                    )
                }
            }

            AttributeId.entries.chunked(2).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    row.forEach { attribute ->
                        FilterChip(
                            selected = selectedAttribute == attribute,
                            onClick = { selectedAttribute = attribute },
                            modifier = Modifier.weight(1f),
                            label = {
                                Text(
                                    "${attribute.shortTitle} · ${signed(character.attribute(attribute))}",
                                    maxLines = 1,
                                )
                            },
                        )
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }

            Button(
                onClick = { onConfirm(selectedAttribute) },
                modifier = Modifier.fillMaxWidth(),
                enabled = calculation.total != null,
            ) {
                Text("К броску · ${calculation.total?.let(::signed) ?: "—"}")
            }
        }
    }
}

@Composable
internal fun DublSkillRollSheet(
    character: DublCharacter,
    skill: ResolvedSkill,
    initialAttribute: AttributeId,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val developmentCatalog = remember(context.applicationContext) {
        DevelopmentCatalogRepository(context.applicationContext).load()
    }
    val effectCatalog = remember(context.applicationContext) {
        SkillEffectCatalogRepository(context.applicationContext).load()
    }
    val calculation = character.skillCalculationForRoll(skill, initialAttribute)
    val effects = remember(character, skill.id, developmentCatalog, effectCatalog) {
        SkillEffectRules(character, developmentCatalog, effectCatalog).forSkill(skill)
    }
    CheckRollSheet(
        title = skill.name,
        bonusTitle = "Бонус умения · ${initialAttribute.shortTitle}",
        checkBonus = calculation.total?.plus(effects.automaticBonus),
        formulaText = calculation.formulaText(skill, showConfiguredOptions = false),
        rememberKey = "${skill.id}:${initialAttribute.name}",
        selectedAttribute = initialAttribute,
        automaticContributions = effects.automaticContributions,
        effectOptions = effects.options,
        effectReminders = effects.reminders,
        onDismiss = onDismiss,
    )
}


/** Compatibility path for the dedicated Skills screen, where configured skill
 * attributes are still edited/remembered in that screen's existing flow. */
@Composable
internal fun DublSkillRollSheet(
    character: DublCharacter,
    skill: ResolvedSkill,
    preferredAttribute: AttributeId? = null,
    onPreferredAttribute: ((AttributeId) -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val developmentCatalog = remember(context.applicationContext) {
        DevelopmentCatalogRepository(context.applicationContext).load()
    }
    val effectCatalog = remember(context.applicationContext) {
        SkillEffectCatalogRepository(context.applicationContext).load()
    }
    var selectedAttribute by remember(skill.id, skill.attributes, preferredAttribute) {
        mutableStateOf(preferredAttribute?.takeIf { it in skill.attributes } ?: skill.attributes.first())
    }
    val calculation = character.skillCalculation(skill, selectedAttribute)
    val effects = remember(character, skill.id, developmentCatalog, effectCatalog) {
        SkillEffectRules(character, developmentCatalog, effectCatalog).forSkill(skill)
    }
    CheckRollSheet(
        title = skill.name,
        bonusTitle = "Бонус умения",
        checkBonus = calculation.total?.plus(effects.automaticBonus),
        formulaText = calculation.formulaText(skill),
        rememberKey = "skills-screen:${skill.id}",
        attributeOptions = skill.attributes,
        selectedAttribute = selectedAttribute,
        automaticContributions = effects.automaticContributions,
        effectOptions = effects.options,
        effectReminders = effects.reminders,
        onAttributeSelected = {
            selectedAttribute = it
            onPreferredAttribute?.invoke(it)
        },
        onDismiss = onDismiss,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CheckRollSheet(
    title: String,
    bonusTitle: String,
    checkBonus: Int?,
    formulaText: String,
    rememberKey: Any,
    skillOptions: List<ResolvedSkill> = emptyList(),
    selectedSkillId: String? = null,
    onSkillSelected: ((String) -> Unit)? = null,
    attributeOptions: List<AttributeId> = emptyList(),
    selectedAttribute: AttributeId? = null,
    automaticContributions: List<RollContribution> = emptyList(),
    effectOptions: List<SkillRollEffectOption> = emptyList(),
    effectReminders: List<SkillEffectDefinition> = emptyList(),
    onAttributeSelected: ((AttributeId) -> Unit)? = null,
    flurryProfile: FlurryWeaponProfile? = null,
    shortFlurry: Boolean = false,
    onFlurryProfile: ((FlurryWeaponProfile) -> Unit)? = null,
    onShortFlurry: ((Boolean) -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    var advantageCount by remember(rememberKey) { mutableStateOf(0) }
    var hindranceCount by remember(rememberKey) { mutableStateOf(0) }
    var situationalText by remember(rememberKey) { mutableStateOf("0") }
    var targetText by remember(rememberKey) { mutableStateOf("") }
    var selectedEffectIds by remember(rememberKey) { mutableStateOf(setOf<String>()) }
    var result by remember(rememberKey) { mutableStateOf<RollResult?>(null) }
    val scrollState = rememberScrollState()

    val situationalBonus = situationalText.toIntOrNull()?.coerceIn(-99, 99) ?: 0
    val targetValue = targetText.toIntOrNull()?.coerceIn(-999, 999)
    val selectedEffectTotals = effectOptions.selectedTotals(selectedEffectIds)
    val effectiveCheckBonus = checkBonus?.plus(selectedEffectTotals.numericBonus)
    val totalAdvantage = advantageCount + selectedEffectTotals.advantageDice
    val totalHindrance = hindranceCount + selectedEffectTotals.hindranceDice
    val mode = when {
        totalAdvantage > 0 -> RollMode.ADVANTAGE
        totalHindrance > 0 -> RollMode.HINDRANCE
        else -> RollMode.NORMAL
    }
    val effectCount = when (mode) {
        RollMode.ADVANTAGE -> totalAdvantage
        RollMode.HINDRANCE -> totalHindrance
        RollMode.NORMAL -> 0
    }

    fun invalidateResult() {
        result = null
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .containSheetOverscroll()
                .verticalScroll(scrollState)
                .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(
                text = effectiveCheckBonus?.let { "$bonusTitle: ${signed(it)}" } ?: "Проверка недоступна",
                style = MaterialTheme.typography.titleMedium,
                color = if (effectiveCheckBonus != null) DublGold else DublAccent,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                formulaText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (automaticContributions.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text("Автоматические эффекты", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = DublAccent)
                automaticContributions.forEach { contribution ->
                    Text(
                        "${contribution.label}: ${signed(contribution.value)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (effectOptions.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text("Ситуационные эффекты навыков", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(5.dp))
                effectOptions.forEach { option ->
                    val selected = option.id in selectedEffectIds
                    FilterChip(
                        selected = selected,
                        onClick = {
                            val next = selectedEffectIds.toMutableSet()
                            if (!next.add(option.id)) {
                                next.remove(option.id)
                            } else if (option.advantageDice > 0) {
                                effectOptions.filter { it.hindranceDice > 0 }.forEach { next.remove(it.id) }
                                hindranceCount = 0
                            } else if (option.hindranceDice > 0) {
                                effectOptions.filter { it.advantageDice > 0 }.forEach { next.remove(it.id) }
                                advantageCount = 0
                            }
                            selectedEffectIds = next
                            invalidateResult()
                        },
                        label = {
                            val suffix = when {
                                option.numericBonus != 0 -> " (${signed(option.numericBonus)})"
                                option.advantageDice > 0 -> " (+${option.advantageDice} преимущество)"
                                option.hindranceDice > 0 -> " (+${option.hindranceDice} помеха)"
                                else -> ""
                            }
                            Text(option.label + suffix)
                        },
                    )
                    if (selected && option.description.isNotBlank()) {
                        Text(
                            option.description,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 8.dp, bottom = 4.dp),
                        )
                    }
                }
            }
            if (effectReminders.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    color = DublGold.copy(alpha = 0.035f),
                    border = BorderStroke(1.dp, DublGold.copy(alpha = 0.20f)),
                ) {
                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("Связанные правила", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = DublGold)
                        effectReminders.forEach { effect ->
                            Text(
                                "${effect.sourceName}: ${effect.effectText}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            if (skillOptions.size > 1 && onSkillSelected != null) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "Умение для проверки",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    items(skillOptions, key = { it.id }) { option ->
                        FilterChip(
                            selected = selectedSkillId == option.id,
                            onClick = {
                                onSkillSelected(option.id)
                                invalidateResult()
                            },
                            label = { Text(option.name) },
                        )
                    }
                }
            }

            if (attributeOptions.size > 1 && onAttributeSelected != null) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "Характеристика для броска",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    items(attributeOptions, key = { it.name }) { attribute ->
                        FilterChip(
                            selected = selectedAttribute == attribute,
                            onClick = {
                                onAttributeSelected(attribute)
                                invalidateResult()
                            },
                            label = { Text(attribute.title) },
                        )
                    }
                }
            }

            if (flurryProfile != null && onFlurryProfile != null && onShortFlurry != null) {
                val rule = flurryProfile.rule(shortFlurry)
                Spacer(Modifier.height(12.dp))
                Text("Параметры шквала", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    items(FlurryWeaponProfile.entries, key = { it.name }) { profile ->
                        FilterChip(
                            selected = flurryProfile == profile,
                            onClick = {
                                onFlurryProfile(profile)
                                invalidateResult()
                            },
                            label = { Text(profile.title) },
                        )
                    }
                }
                Spacer(Modifier.height(7.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    FilterChip(
                        selected = !shortFlurry,
                        onClick = {
                            onShortFlurry(false)
                            invalidateResult()
                        },
                        label = { Text("Полный шквал") },
                    )
                    FilterChip(
                        selected = shortFlurry,
                        onClick = {
                            onShortFlurry(true)
                            invalidateResult()
                        },
                        label = { Text("Короткий · 2 ОД") },
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    buildString {
                        append("Бонус +${rule.attackBonus}. ")
                        append("1 попадание за каждые ${rule.excessPerHit} превышения")
                        if (rule.shortExcessPenalty > 0) append("; для короткого требуемое превышение +${rule.shortExcessPenalty}")
                        append(". Максимум ${rule.maxHits}. ")
                        append("Реакцию использовать нельзя.")
                        if (rule.alternateWeaponDamage) append(" Урон двух оружий чередуется.")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (checkBonus != null) {
                Spacer(Modifier.height(16.dp))
                Text(
                    "Условия броска",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(8.dp))

                RollCounter(
                    title = "Преимущества",
                    count = advantageCount,
                    onMinus = {
                        advantageCount = (advantageCount - 1).coerceAtLeast(0)
                        invalidateResult()
                    },
                    onPlus = {
                        advantageCount += 1
                        hindranceCount = 0
                        selectedEffectIds = selectedEffectIds - effectOptions.filter { it.hindranceDice > 0 }.map { it.id }.toSet()
                        invalidateResult()
                    },
                )
                Spacer(Modifier.height(7.dp))
                RollCounter(
                    title = "Помехи",
                    count = hindranceCount,
                    onMinus = {
                        hindranceCount = (hindranceCount - 1).coerceAtLeast(0)
                        invalidateResult()
                    },
                    onPlus = {
                        hindranceCount += 1
                        advantageCount = 0
                        selectedEffectIds = selectedEffectIds - effectOptions.filter { it.advantageDice > 0 }.map { it.id }.toSet()
                        invalidateResult()
                    },
                )

                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = situationalText,
                    onValueChange = { raw ->
                        situationalText = sanitizeSignedBonus(raw)
                        invalidateResult()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Ситуационный бонус / штраф") },
                    supportingText = { Text("Например: +2 за инструменты или −3 за условия") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    trailingIcon = {
                        TextButton(
                            onClick = {
                                val value = situationalText.toIntOrNull() ?: 0
                                situationalText = when {
                                    value > 0 -> (-value).toString()
                                    value < 0 -> abs(value).toString()
                                    else -> "0"
                                }
                                invalidateResult()
                            },
                        ) { Text("±") }
                    },
                    singleLine = true,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = targetText,
                    onValueChange = { raw ->
                        targetText = raw.filter { it.isDigit() || it == '-' }.take(4)
                        invalidateResult()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("СЛ / результат противника (необязательно)") },
                    supportingText = { Text("Нужен только для автоматического сравнения результата") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    text = "Преимущества и помехи не смешиваются автоматически: выбор одного типа сбрасывает другой, потому что книга не описывает их совместное разрешение.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(9.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
                ) {
                    Text(
                        text = rollSetupText(mode, effectCount, bonusTitle, effectiveCheckBonus ?: 0, situationalBonus),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        result = rollCheck(
                            mode = mode,
                            effectCount = effectCount,
                            checkBonus = effectiveCheckBonus ?: 0,
                            checkBonusLabel = bonusTitle,
                            situationalBonus = situationalBonus,
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 12.dp),
                ) {
                    Text("Бросить")
                }
            }

            result?.let { roll ->
                Spacer(Modifier.height(18.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(13.dp),
                    color = DublGold.copy(alpha = 0.06f),
                    border = BorderStroke(1.dp, DublGold.copy(alpha = 0.42f)),
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = roll.total.toString(),
                            fontSize = 42.sp,
                            lineHeight = 46.sp,
                            fontWeight = FontWeight.Bold,
                            color = DublGold,
                        )
                        targetValue?.let { target ->
                            val comparison = compareRollToTarget(roll, target)
                            Text(
                                text = when (comparison.outcome) {
                                    RollTargetOutcome.SUCCESS -> "Выше цели на ${comparison.margin}"
                                    RollTargetOutcome.TIE -> "Равно цели · разница 0"
                                    RollTargetOutcome.FAILURE -> "Ниже цели на ${kotlin.math.abs(comparison.margin)}"
                                },
                                style = MaterialTheme.typography.labelLarge,
                                color = when (comparison.outcome) {
                                    RollTargetOutcome.SUCCESS -> DublAccent
                                    RollTargetOutcome.TIE -> DublGold
                                    RollTargetOutcome.FAILURE -> DublDanger
                                },
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Text(
                            text = rollResultCaption(roll),
                            style = MaterialTheme.typography.labelLarge,
                            color = roll.specialResult?.let { DublAccent } ?: MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (roll.specialResult != null) FontWeight.Bold else FontWeight.Normal,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(5.dp))
                        Text(
                            text = rollModeDescription(roll),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(10.dp))
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(horizontal = 2.dp),
                        ) {
                            itemsIndexed(roll.dice) { index, die ->
                                DiceChip(value = die, selected = index in roll.chosenIndices)
                            }
                        }
                        roll.followUp?.let { followUp ->
                            Spacer(Modifier.height(12.dp))
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(11.dp),
                                color = DublAccent.copy(alpha = 0.08f),
                                border = BorderStroke(1.dp, DublAccent.copy(alpha = 0.45f)),
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Text(
                                        text = followUpPrompt(followUp),
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = DublAccent,
                                        textAlign = TextAlign.Center,
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Button(
                                        onClick = { result = rollFollowUp(roll) },
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Text(followUp.buttonTitle)
                                    }
                                }
                            }
                        }
                        roll.followUpDie?.let { die ->
                            Spacer(Modifier.height(10.dp))
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(11.dp),
                                color = DublGold.copy(alpha = 0.07f),
                                border = BorderStroke(1.dp, DublGold.copy(alpha = 0.38f)),
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    DiceChip(value = die, selected = true)
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = followUpDieLabel(roll, die),
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = DublGold,
                                        )
                                        roll.specialResult?.let { special ->
                                            Text(
                                                text = special.title,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = DublAccent,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = buildRollBreakdown(roll),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                        roll.note?.let { note ->
                            Spacer(Modifier.height(7.dp))
                            Text(
                                text = note,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = DublAccent,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RollCounter(
    title: String,
    count: Int,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
            )
            OutlinedButton(
                onClick = onMinus,
                enabled = count > 0,
                contentPadding = PaddingValues(horizontal = 13.dp, vertical = 7.dp),
            ) { Text("−") }
            Text(
                text = count.toString(),
                modifier = Modifier.width(28.dp),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            OutlinedButton(
                onClick = onPlus,
                contentPadding = PaddingValues(horizontal = 13.dp, vertical = 7.dp),
            ) { Text("+") }
        }
    }
}

@Composable
private fun DiceChip(value: Int, selected: Boolean) {
    Surface(
        shape = RoundedCornerShape(9.dp),
        color = if (selected) DublGold.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            if (selected) 1.5.dp else 1.dp,
            if (selected) DublGold else MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
        ),
    ) {
        Text(
            text = value.toString(),
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 8.dp),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = if (selected) DublGold else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun rollSetupText(
    mode: RollMode,
    effectCount: Int,
    bonusTitle: String,
    checkBonus: Int,
    situationalBonus: Int,
): String {
    val diceText = when (mode) {
        RollMode.NORMAL -> "2d6"
        RollMode.ADVANTAGE -> "${2 + effectCount}d6, оставить 2 наибольших"
        RollMode.HINDRANCE -> "${2 + effectCount}d6, оставить 2 наименьших"
    }
    return buildString {
        append(diceText)
        append(" · ").append(bonusTitle.lowercase()).append(" ").append(signed(checkBonus))
        if (situationalBonus != 0) {
            append(" · ситуация ").append(signed(situationalBonus))
        }
    }
}

private fun rollModeDescription(roll: RollResult): String = buildString {
    when (roll.mode) {
        RollMode.NORMAL -> append("Обычный бросок · 2d6")
        RollMode.ADVANTAGE -> append("Преимущества ×${roll.effectCount} · ${2 + roll.effectCount}d6 · выбрать 2 наибольших")
        RollMode.HINDRANCE -> append("Помехи ×${roll.effectCount} · ${2 + roll.effectCount}d6 · выбрать 2 наименьших")
    }
    if (roll.grantedAdvantageDie) append(" · +1 кость за дубль")
}

private fun followUpPrompt(followUp: RollFollowUp): String = when (followUp) {
    RollFollowUp.ADVANTAGE_DIE -> "Выпал дубль — доступен дополнительный куб."
    RollFollowUp.CRITICAL_FAILURE_CONFIRMATION -> "Критический провал — можно бросить кость подтверждения."
    RollFollowUp.SUPERIORITY_DIE -> "Критический успех — доступна кость превосходства."
}

private fun rollResultCaption(roll: RollResult): String = roll.specialResult?.title ?: "Итог проверки"

private fun followUpDieLabel(roll: RollResult, die: Int): String = when (roll.resolvedFollowUp) {
    RollFollowUp.ADVANTAGE_DIE -> "Кость преимущества: $die"
    RollFollowUp.CRITICAL_FAILURE_CONFIRMATION -> "Кость подтверждения: $die"
    RollFollowUp.SUPERIORITY_DIE -> "Кость превосходства: $die"
    null -> "Дополнительная кость: $die"
}

private fun buildRollBreakdown(roll: RollResult): String = buildString {
    append("Кости ")
    append(roll.chosenIndices.sorted().joinToString(" + ") { roll.dice[it].toString() })
    append(" · ").append(roll.checkBonusLabel.lowercase()).append(" ").append(signed(roll.checkBonus))
    if (roll.situationalBonus != 0) {
        append(" · ситуация ").append(signed(roll.situationalBonus))
    }
    if (roll.followUpDie != null && roll.resolvedFollowUp == RollFollowUp.SUPERIORITY_DIE) {
        append(" · превосходство +").append(roll.followUpDie)
    }
}

private fun sanitizeSignedBonus(raw: String): String {
    if (raw.isBlank()) return ""
    val negative = raw.trimStart().startsWith('-')
    val digits = raw.filter(Char::isDigit).take(2)
    if (digits.isEmpty()) return if (negative) "-" else ""
    return (if (negative) "-" else "") + digits
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ResourceVisibilitySheet(
    character: DublCharacter,
    hidden: Set<CharacterSheetResourceId>,
    mountedToggles: List<DublResourceToggleModel>,
    onToggle: (CharacterSheetResourceId) -> Unit,
    onAddCustom: () -> Unit,
    onEditCustom: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
        ) {
            Text("Ресурсы на листе", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                "Скрытие влияет только на отображение. Значения ресурсов не удаляются.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(14.dp))
            ResourceVisibilityRow(
                title = CharacterSheetResourceId.HEALTH.title,
                visible = CharacterSheetResourceId.HEALTH !in hidden,
                enabled = true,
                onToggle = { onToggle(CharacterSheetResourceId.HEALTH) },
            )
            ResourceVisibilityRow(
                title = CharacterSheetResourceId.ENDURANCE.title,
                visible = CharacterSheetResourceId.ENDURANCE !in hidden,
                enabled = true,
                onToggle = { onToggle(CharacterSheetResourceId.ENDURANCE) },
            )
            ResourceVisibilityRow(
                title = CharacterSheetResourceId.MANA.title,
                visible = character.manaEnabled && CharacterSheetResourceId.MANA !in hidden,
                enabled = character.manaEnabled,
                subtitle = if (character.manaEnabled) null else "Мана отключена у персонажа",
                onToggle = { onToggle(CharacterSheetResourceId.MANA) },
            )
            mountedToggles.forEach { model ->
                val presentation = model.presentation
                ResourceVisibilityRow(
                    title = presentation.label,
                    icon = fcpIconGlyph(presentation.icon),
                    visible = model.available && model.resourceId !in hidden,
                    enabled = model.available,
                    subtitle = if (model.available) null else "${presentation.label} недоступен у персонажа",
                    onToggle = { onToggle(model.resourceId) },
                )
            }
            if (character.customResources.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                Text("Кастомные ресурсы", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                character.customResources.forEach { resource ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onEditCustom(resource.uid) }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(resource.name, style = MaterialTheme.typography.bodyLarge)
                            Text("${resource.current} / ${resource.maximum}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text("Изменить", style = MaterialTheme.typography.labelMedium, color = DublGold)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onAddCustom, modifier = Modifier.fillMaxWidth()) {
                Text("+ Добавить кастомный ресурс")
            }
        }
    }
}

@Composable
private fun ResourceVisibilityRow(
    title: String,
    visible: Boolean,
    enabled: Boolean,
    subtitle: String? = null,
    icon: String? = null,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Text(icon, color = DublAccent, fontWeight = FontWeight.Bold)
                }
                Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            subtitle?.let {
                Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        DublSwitch(
            checked = visible,
            onCheckedChange = { onToggle() },
            enabled = enabled,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StatInfoSheet(
    info: StatInfo,
    character: DublCharacter,
    onRollFortitude: () -> Unit,
    onSetSize: (Int) -> Unit,
    onSetLegs: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var sizeText by remember(character.id, character.size) { mutableStateOf(character.size.toString()) }
    var legsText by remember(character.id, character.legs) { mutableStateOf(character.legs.toString()) }
    val accent = statAccent(info.id)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(42.dp),
                    shape = RoundedCornerShape(11.dp),
                    color = accent.copy(alpha = 0.10f),
                    border = BorderStroke(1.dp, accent.copy(alpha = 0.5f)),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(info.id.glyph, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = accent)
                    }
                }
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f)) {
                    Text(info.id.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        info.value,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = accent,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Text("Формула", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = DublGold)
            Text(info.formula, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(10.dp))
            info.breakdown.forEach { line ->
                Text(
                    line,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            info.note?.let {
                Spacer(Modifier.height(10.dp))
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            if (info.id == StatId.FORTITUDE) {
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = onRollFortitude,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("⚄  Бросить Стойкость")
                }
            }

            if (info.id == StatId.SIZE) {
                Spacer(Modifier.height(18.dp))
                HorizontalDivider()
                Spacer(Modifier.height(14.dp))
                Text("Быстрое редактирование", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = sizeText,
                    onValueChange = { sizeText = it.filter(Char::isDigit).take(2) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Размер 1–10") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        onSetSize(sizeText.toIntOrNull()?.coerceIn(1, 10) ?: character.size)
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Сохранить размер") }
            }

            if (info.id == StatId.RUN) {
                Spacer(Modifier.height(18.dp))
                HorizontalDivider()
                Spacer(Modifier.height(14.dp))
                Text("Быстрое редактирование", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = legsText,
                    onValueChange = { legsText = it.filter(Char::isDigit).take(2) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Количество ног") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        onSetLegs(legsText.toIntOrNull()?.coerceAtLeast(2) ?: character.legs)
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Сохранить") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ResourceAdjustSheet(
    title: String,
    current: Int,
    maximum: Int,
    accent: Color,
    onChange: (Int) -> Unit,
    onEditMaximum: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(4.dp))
            Text(
                "$current / $maximum",
                fontSize = 36.sp,
                lineHeight = 42.sp,
                fontWeight = FontWeight.Bold,
                color = accent,
            )
            Spacer(Modifier.height(18.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = { onChange(-1) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                ) { Text("− 1") }
                Button(
                    onClick = { onChange(1) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                ) { Text("+ 1") }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "Значение ограничено диапазоном 0–$maximum",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (onEditMaximum != null) {
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onEditMaximum, modifier = Modifier.fillMaxWidth()) {
                    Text("Изменить максимум")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AttributeAdjustSheet(
    id: AttributeId,
    character: DublCharacter,
    onChange: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val base = character.attributes[id]?.base ?: character.attributeRaw(id)
    val total = character.attribute(id)
    val modifier = total - base

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(id.title, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(2.dp))
            Text(
                total.toString(),
                fontSize = 42.sp,
                lineHeight = 48.sp,
                fontWeight = FontWeight.Bold,
                color = attributeAccent(id),
            )
            if (modifier != 0) {
                Text(
                    "База $base · текущая модификация ${signed(modifier)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = DublGold,
                )
            } else {
                Text(
                    "Базовое значение $base",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(10.dp))
            val nextCost = CharacterEconomy.nextAttributeCost(base)
            val refund = CharacterEconomy.previousAttributeRefund(base)
            Text(
                buildString {
                    if (nextCost != null) append("Следующий ранг: $nextCost XP") else append("Максимум по таблице")
                    if (refund != null) append(" · снижение: возврат $refund XP")
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = { onChange(-1) },
                    enabled = base > -5,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                ) { Text("− 1") }
                Button(
                    onClick = { onChange(1) },
                    enabled = base < 10,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                ) { Text("+ 1") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HealthControlSheet(
    current: Int,
    maximum: Int,
    onChange: (Int) -> Unit,
    onEditMaximum: () -> Unit,
    onDismiss: () -> Unit,
) {
    var amountText by remember(current, maximum) { mutableStateOf("1") }
    val amount = amountText.toIntOrNull()?.coerceAtLeast(0) ?: 0

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Здоровье", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(4.dp))
            Text(
                "$current / $maximum",
                fontSize = 36.sp,
                lineHeight = 42.sp,
                fontWeight = FontWeight.Bold,
                color = DublHealth,
            )
            Spacer(Modifier.height(18.dp))

            OutlinedTextField(
                value = amountText,
                onValueChange = { input -> amountText = input.filter(Char::isDigit).take(5) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Количество") },
                supportingText = { Text("Введите урон или лечение целым числом") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                textStyle = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                ),
            )

            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = {
                        if (amount > 0) onChange(-amount)
                        onDismiss()
                    },
                    enabled = amount > 0,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = DublAccent),
                    shape = RoundedCornerShape(10.dp),
                ) { Text("Получить урон") }
                OutlinedButton(
                    onClick = {
                        if (amount > 0) onChange(amount)
                        onDismiss()
                    },
                    enabled = amount > 0 && current < maximum,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                ) { Text("Лечение") }
            }

            Spacer(Modifier.height(8.dp))
            TextButton(
                onClick = {
                    onChange(maximum - current)
                    onDismiss()
                },
                enabled = current < maximum,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Восстановить всё здоровье") }
            TextButton(onClick = onEditMaximum, modifier = Modifier.fillMaxWidth()) {
                Text("Изменить максимум здоровья")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomResourceControlSheet(
    resource: CustomResource,
    onChange: (Int) -> Unit,
    onEdit: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(resource.name, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(4.dp))
            Text("${resource.current} / ${resource.maximum}", fontSize = 36.sp, lineHeight = 42.sp, fontWeight = FontWeight.Bold, color = DublGold)
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { onChange(-1) }, enabled = resource.current > 0, modifier = Modifier.weight(1f)) { Text("− 1") }
                Button(onClick = { onChange(1) }, enabled = resource.current < resource.maximum, modifier = Modifier.weight(1f)) { Text("+ 1") }
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onEdit, modifier = Modifier.fillMaxWidth()) { Text("Изменить ресурс") }
        }
    }
}

@Composable
private fun CustomResourceEditDialog(
    initial: CustomResource?,
    onSave: (String, Int, Int) -> Unit,
    onDelete: (() -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    var name by remember(initial?.uid) { mutableStateOf(initial?.name.orEmpty()) }
    var maximumText by remember(initial?.uid) { mutableStateOf((initial?.maximum ?: 1).toString()) }
    var currentText by remember(initial?.uid) { mutableStateOf((initial?.current ?: 0).toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Новый ресурс" else "Изменить ресурс") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Название") }, singleLine = true)
                OutlinedTextField(maximumText, { maximumText = it.filter(Char::isDigit).take(6) }, label = { Text("Максимум") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                OutlinedTextField(currentText, { currentText = it.filter(Char::isDigit).take(6) }, label = { Text("Текущее значение") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
            }
        },
        confirmButton = {
            Button(onClick = {
                val max = maximumText.toIntOrNull()?.coerceAtLeast(0) ?: 0
                val current = currentText.toIntOrNull()?.coerceIn(0, max) ?: 0
                if (name.trim().isNotBlank()) onSave(name.trim(), current, max)
            }, enabled = name.trim().isNotBlank()) { Text("Сохранить") }
        },
        dismissButton = {
            Row {
                if (onDelete != null) TextButton(onClick = onDelete) { Text("Удалить") }
                TextButton(onClick = onDismiss) { Text("Отмена") }
            }
        },
    )
}

@Composable
private fun MaximumResourceDialog(
    title: String,
    calculated: Int,
    override: Int?,
    onSave: (Int?) -> Unit,
    onDismiss: () -> Unit,
) {
    var valueText by remember(title, override) { mutableStateOf((override ?: calculated).toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("По формуле: $calculated", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = valueText,
                    onValueChange = { valueText = it.filter(Char::isDigit).take(6) },
                    label = { Text("Максимум вручную") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            Button(onClick = { onSave(valueText.toIntOrNull()?.coerceAtLeast(0) ?: 0) }) { Text("Сохранить") }
        },
        dismissButton = {
            Row {
                if (override != null) TextButton(onClick = { onSave(null) }) { Text("Сбросить к формуле") }
                TextButton(onClick = onDismiss) { Text("Отмена") }
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TextValueEditSheet(
    title: String,
    initialValue: String,
    numeric: Boolean,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var value by remember(initialValue) { mutableStateOf(initialValue) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = value,
                onValueChange = { input ->
                    value = if (numeric) input.filter(Char::isDigit).take(9) else input.take(80)
                },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (numeric) KeyboardType.Number else KeyboardType.Text,
                ),
                singleLine = true,
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { onSave(value) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
            ) { Text("Сохранить") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExperienceEconomySheet(
    character: DublCharacter,
    economy: CharacterEconomyBreakdown,
    showChi: Boolean,
    hasSelfTaught: Boolean,
    onSetExperience: (Int) -> Unit,
    onSetCreationExperience: (Int) -> Unit,
    onSetAdjustment: (Int) -> Unit,
    onSetAbilityOverride: (Int?) -> Unit,
    onCompleteCreation: () -> Unit,
    onReopenCreation: () -> Unit,
    onDismiss: () -> Unit,
) {
    var totalText by remember(character.id) { mutableStateOf(character.experience.toString()) }
    var creationText by remember(character.id) { mutableStateOf(character.effectiveCreationExperience.toString()) }
    var adjustmentText by remember(character.id) { mutableStateOf(character.xpAdjustment.toString()) }
    var abilityOverrideText by remember(character.id) {
        mutableStateOf(character.abilityPointsOverride?.toString().orEmpty())
    }
    val saveValues = {
        val total = totalText.toIntOrNull()?.coerceAtLeast(0) ?: character.experience
        val creation = creationText.toIntOrNull()?.coerceIn(0, total)
            ?: character.effectiveCreationExperience.coerceAtMost(total)
        val adjustment = adjustmentText.toIntOrNull() ?: character.xpAdjustment
        onSetExperience(total)
        onSetCreationExperience(creation)
        onSetAdjustment(adjustment)
        onSetAbilityOverride(abilityOverrideText.toIntOrNull()?.coerceAtLeast(0))
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 720.dp)
                .containSheetOverscroll()
                .verticalScroll(rememberScrollState())
                .padding(start = 18.dp, end = 18.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Опыт и создание персонажа", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                if (character.creationComplete) "Создание завершено" else "Режим создания",
                style = MaterialTheme.typography.labelLarge,
                color = if (character.creationComplete) MaterialTheme.colorScheme.onSurfaceVariant else DublGold,
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EconomyValue("Осталось XP", economy.remainingXp.toString(), Modifier.weight(1f), economy.remainingXp < 0)
                EconomyValue("Потрачено XP", economy.spentXp.toString(), Modifier.weight(1f))
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EconomyValue("ОС", "${economy.abilityPointsRemaining}/${economy.abilityPointsBudget}", Modifier.weight(1f), economy.abilityPointsRemaining < 0)
                EconomyValue("Стартовый XP", economy.creationExperience.toString(), Modifier.weight(1f))
            }

            HorizontalDivider()
            NumericField("Общий накопленный XP", totalText) { totalText = it }
            NumericField("Стартовый XP", creationText) { creationText = it }
            NumericField("Поправка расходов XP (+ расход / − возврат)", adjustmentText) { adjustmentText = it }
            OutlinedTextField(
                value = abilityOverrideText,
                onValueChange = { abilityOverrideText = it.filter(Char::isDigit) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Лимит ОС вручную (пусто = по правилу)") },
                supportingText = { Text("Рекомендация книги: ${economy.recommendedAbilityPoints} ОС по стартовому XP") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
            )
            Button(
                onClick = saveValues,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Сохранить расчёт") }

            HorizontalDivider()
            Text("Расход XP", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            EconomyLine("Характеристики", economy.attributeXp)
            EconomyLine("Умения", economy.skillXp)
            EconomyLine("Навыки", economy.developmentXp)
            EconomyLine("Базовый запас маны", economy.manaXp)
            if (showChi) EconomyLine("Дополнительный запас ЦИ", economy.chiXp)
            EconomyLine("Сила магии по школам", economy.magicSchoolXp)
            EconomyLine("Заклинания", economy.spellXp)
            EconomyLine("Ручная поправка", economy.adjustmentXp)
            HorizontalDivider()
            EconomyLine("Итого", economy.spentXp, bold = true)

            if (economy.unpricedLearnedSpells > 0) {
                Text(
                    "${economy.unpricedLearnedSpells} изуч. заклин. выходят за таблицу 0–20 маны и не имеют ручной цены XP. Укажите стоимость в карточке заклинания.",
                    style = MaterialTheme.typography.bodySmall,
                    color = DublDanger,
                )
            }
            if (economy.attributeXp < 0) {
                Text(
                    "Отрицательные характеристики дают возврат XP буквально по таблице книги. Книга не уточняет, является ли этот возврат обязательным — при необходимости скорректируйте его ручной поправкой.",
                    style = MaterialTheme.typography.bodySmall,
                    color = DublGold,
                )
            }
            if (economy.overspentAbilityPoints) {
                Text(
                    "ОС превышают рекомендованный лимит на ${-economy.abilityPointsRemaining}. Это предупреждение, а не блокировка: правило книги сформулировано как рекомендация.",
                    style = MaterialTheme.typography.bodySmall,
                    color = DublGold,
                )
            }
            if (hasSelfTaught) {
                Text(
                    if (character.creationComplete) {
                        "Самоучка: скидка на первые два ранга умений действует только после создания. История покупок в старых сейвах не хранится, поэтому скидка не применяется ретроактивно автоматически — используйте ручную поправку XP, если она положена."
                    } else {
                        "Самоучка не работает во время создания персонажа; его скидка начнёт иметь смысл только после завершения создания."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = DublGold,
                )
            }

            if (!character.creationComplete) {
                Button(
                    onClick = {
                        saveValues()
                        onCompleteCreation()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Завершить создание персонажа") }
                Text(
                    "После завершения стартовый XP фиксирует базу для ОС, а Базовый запас маны и другие требования «только при создании» больше нельзя будет повышать.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                OutlinedButton(
                    onClick = onReopenCreation,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Вернуть режим создания") }
                Text(
                    "Используйте только для исправления старого/ошибочного листа: это снова разрешит creation-only покупки.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun EconomyValue(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    danger: Boolean = false,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (danger) DublDanger else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun EconomyLine(label: String, value: Int, bold: Boolean = false) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)
        Text(
            value.toString(),
            fontWeight = if (bold) FontWeight.Bold else FontWeight.SemiBold,
            color = if (value < 0) DublGold else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun EditCharacterDialog(
    character: DublCharacter,
    onDismiss: () -> Unit,
    onConfirm: (String, String, Int, Int, Int, Boolean) -> Unit,
) {
    var name by remember(character.id) { mutableStateOf(character.name) }
    var concept by remember(character.id) { mutableStateOf(character.concept) }
    var experience by remember(character.id) { mutableStateOf(character.experience.toString()) }
    var size by remember(character.id) { mutableStateOf(character.size.toString()) }
    var legs by remember(character.id) { mutableStateOf(character.legs.toString()) }
    var manaEnabled by remember(character.id) { mutableStateOf(character.manaEnabled) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Дополнительное редактирование") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Имя") }, singleLine = true)
                OutlinedTextField(concept, { concept = it }, label = { Text("Концепт") })
                NumericField("Общий опыт", experience) { experience = it }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumericField("Размер 1–10", size, Modifier.weight(1f)) { size = it }
                    NumericField("Количество ног", legs, Modifier.weight(1f)) { legs = it }
                }
                HorizontalDivider()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Использовать ману")
                    DublSwitch(checked = manaEnabled, onCheckedChange = { manaEnabled = it })
                }
                Text(
                    "Максимумы здоровья, выносливости и маны меняются прямо из карточек ресурсов.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(
                        name,
                        concept,
                        experience.toIntOrNull()?.coerceAtLeast(0) ?: 0,
                        size.toIntOrNull()?.coerceIn(1, 10) ?: 5,
                        legs.toIntOrNull()?.coerceAtLeast(2) ?: 2,
                        manaEnabled,
                    )
                },
            ) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

@Composable
private fun NumericField(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { input -> onValueChange(input.filter { it.isDigit() || it == '-' }) },
        modifier = modifier.fillMaxWidth(),
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
    )
}

private fun resourceChangeText(label: String, delta: Int): String {
    val sign = if (delta > 0) "+" else "−"
    return "$sign${abs(delta)} $label"
}

private fun signed(value: Int): String = if (value >= 0) "+$value" else value.toString()

private fun formatNumber(value: Double): String = DecimalFormat("0.##").format(value)


private fun fcpAccentColor(token: String?): Color = when (token) {
    FcpUiAccentToken.FURY_ACCENT -> DublAccent
    else -> DublAccent
}

private fun fcpIconGlyph(token: String?): String? = when (token) {
    FcpUiIconToken.CHI -> "◎"
    else -> null
}
