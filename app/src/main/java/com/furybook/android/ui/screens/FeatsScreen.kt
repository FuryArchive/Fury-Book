package com.furybook.android.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.furybook.android.data.AndroidContentPackState
import com.furybook.android.data.ChiCatalogRepository
import com.furybook.android.data.DevelopmentCatalogRepository
import com.furybook.content.FcpOrderedUiItem
import com.furybook.content.FcpUiComponent
import com.furybook.content.FcpUiHostOrder
import com.furybook.content.FcpUiIconToken
import com.furybook.content.FcpUiSurface
import com.furybook.content.orderedUiItems
import com.furybook.content.presentation
import com.furybook.dubl.content.DublUiFeature
import com.furybook.dubl.content.forFeature
import com.furybook.dubl.model.CharacterEconomy
import com.furybook.dubl.model.ChiCatalog
import com.furybook.dubl.model.effectiveDevelopmentCatalog
import com.furybook.dubl.model.DevelopmentCostType
import com.furybook.dubl.model.DevelopmentAvailability
import com.furybook.dubl.model.AbilityOption
import com.furybook.dubl.model.CharacterEconomyBreakdown
import com.furybook.dubl.model.ChiRules
import com.furybook.dubl.model.ChiTechnique
import com.furybook.dubl.model.DevelopmentCatalog
import com.furybook.dubl.model.DevelopmentAcquisitionChoice
import com.furybook.dubl.model.DevelopmentAcquisitionPlan
import com.furybook.dubl.model.DevelopmentAcquisitionPlanner
import com.furybook.dubl.model.DevelopmentAcquisitionRequest
import com.furybook.dubl.model.DevelopmentAcquisitionStep
import com.furybook.dubl.model.DevelopmentAcquisitionTarget
import com.furybook.dubl.model.DevelopmentEffectIds
import com.furybook.dubl.model.DevelopmentEntry
import com.furybook.dubl.model.DevelopmentEntryKind
import com.furybook.dubl.model.DevelopmentProgress
import com.furybook.dubl.model.DevelopmentRules
import com.furybook.dubl.model.DevelopmentScreenIndex
import com.furybook.dubl.model.DublCharacter
import com.furybook.dubl.model.IndexedDevelopmentEntry
import com.furybook.dubl.model.MagicEquipmentRules
import com.furybook.dubl.model.RequirementCheck
import com.furybook.dubl.model.RequirementStatus
import com.furybook.dubl.model.developmentNormalize
import com.furybook.dubl.model.developmentRank
import com.furybook.dubl.model.withoutChiContent
import com.furybook.android.state.CharacterController
import com.furybook.android.ui.components.containSheetOverscroll
import com.furybook.ui.components.DublCard
import com.furybook.android.ui.components.DublScreenHeader
import com.furybook.android.ui.components.DublSwitch
import com.furybook.dubl.ui.development.DevelopmentGroupVisibility
import com.furybook.ui.theme.DublAccent
import com.furybook.ui.theme.DublDanger
import com.furybook.ui.theme.DublGold
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private enum class DevelopmentTab(val title: String) {
    REGULAR("Обычные"),
    SPECIAL("Спец. ветки"),
    MARTIAL_ARTS("Боевые искусства"),
    CHI("ЦИ"),
    OWNED("Взято"),
}

private enum class DevelopmentBrowserFilter(val title: String) {
    ALL("Все"),
    AVAILABLE("Можно взять"),
    ALMOST("Почти доступно"),
    PLAN("План"),
}

private data class PendingAbilityPurchase(
    val entry: DevelopmentEntry,
    val optionIndex: Int,
)

private data class PendingRequirementOverride(
    val entry: DevelopmentEntry,
    val optionIndex: Int,
    val failedChecks: List<RequirementCheck>,
)

private data class DevelopmentScreenPreparation(
    val catalog: DevelopmentCatalog,
    val chiCatalog: ChiCatalog,
    val economy: CharacterEconomyBreakdown,
    val availabilityById: RetainedPreparationCache<String, DevelopmentAvailability>,
    val rules: DevelopmentRules,
    val index: DevelopmentScreenIndex,
)

private data class DevelopmentPreparationKey(
    val character: DublCharacter,
    val catalogVersion: String,
    val chiCatalogVersion: String,
)

private val developmentPreparationCache =
    RetainedPreparationCache<DevelopmentPreparationKey, DevelopmentScreenPreparation>(maximumEntries = 2)

@Composable
fun FeatsScreen(controller: CharacterController, chiPackEnabled: Boolean) {
    val character = controller.active
    val context = LocalContext.current
    val chiTabUi = remember(context.applicationContext, chiPackEnabled) {
        AndroidContentPackState.uiMounts(context, chiPackEnabled, FcpUiSurface.DEVELOPMENT_TABS, FcpUiComponent.DEVELOPMENT_BROWSER).forFeature(DublUiFeature.CHI)
    }
    val chiTabPresentation = chiTabUi?.presentation()
    val chiEconomyUi = remember(context.applicationContext, chiPackEnabled) {
        AndroidContentPackState.uiMounts(context, chiPackEnabled, FcpUiSurface.CHARACTER_ECONOMY, FcpUiComponent.XP_LINE).forFeature(DublUiFeature.CHI)
    }
    val developmentTabs = remember(chiTabUi) {
        orderedUiItems(
            buildList {
                add(FcpOrderedUiItem(FcpUiHostOrder.PRIMARY, DevelopmentTab.REGULAR.name, DevelopmentTab.REGULAR))
                add(FcpOrderedUiItem(FcpUiHostOrder.SECONDARY, DevelopmentTab.SPECIAL.name, DevelopmentTab.SPECIAL))
                add(FcpOrderedUiItem(FcpUiHostOrder.TERTIARY, DevelopmentTab.MARTIAL_ARTS.name, DevelopmentTab.MARTIAL_ARTS))
                if (chiTabUi != null) {
                    add(FcpOrderedUiItem(chiTabUi.order, DevelopmentTab.CHI.name, DevelopmentTab.CHI))
                }
                add(FcpOrderedUiItem(FcpUiHostOrder.TRAILING, DevelopmentTab.OWNED.name, DevelopmentTab.OWNED))
            },
        )
    }
    var loadingStage by remember(character.id) { mutableStateOf("Загружаем каталог развития…") }
    val preparation by produceState<DevelopmentScreenPreparation?>(
        initialValue = null,
        key1 = context.applicationContext,
        key2 = character,
        key3 = chiPackEnabled,
    ) {
        value = null
        loadingStage = "Загружаем каталог развития…"
        val loadedCatalogs = withContext(Dispatchers.IO) {
            val development = DevelopmentCatalogRepository(context.applicationContext).load(includeChi = chiTabUi != null)
            val chi = if (chiTabUi != null) {
                ChiCatalogRepository(context.applicationContext).load()
            } else {
                ChiCatalog("disabled", emptyList(), emptyList())
            }
            development to chi
        }

        loadingStage = "Готовим быстрый индекс навыков…"
        value = withContext(Dispatchers.Default) {
            val key = DevelopmentPreparationKey(character, loadedCatalogs.first.version, loadedCatalogs.second.version)
            developmentPreparationCache.getOrPut(key) {
                val effectiveCatalog = character.effectiveDevelopmentCatalog(loadedCatalogs.first)
                    .let { catalog -> if (chiTabUi != null) catalog else catalog.withoutChiContent() }
                val preparationProgress = DevelopmentProgress(character.development)
                val preparationRules = DevelopmentRules(character, effectiveCatalog, preparationProgress)
                DevelopmentScreenPreparation(
                    catalog = effectiveCatalog,
                    chiCatalog = loadedCatalogs.second,
                    economy = CharacterEconomy.breakdown(character, effectiveCatalog, includeChi = chiEconomyUi != null),
                    availabilityById = RetainedPreparationCache(maximumEntries = effectiveCatalog.entries.size),
                    rules = preparationRules,
                    index = DevelopmentScreenIndex(effectiveCatalog),
                )
            }
        }
    }

    if (preparation == null) {
        DevelopmentLoadingScreen(stage = loadingStage)
        return
    }

    val prepared = preparation!!
    val catalog = prepared.catalog
    val chiCatalog = prepared.chiCatalog
    val economy = prepared.economy
    val availabilityById = prepared.availabilityById
    val developmentIndex = prepared.index
    val progress = DevelopmentProgress(character.development)
    var query by remember(character.id) { mutableStateOf("") }
    var tab by remember(character.id) { mutableStateOf(DevelopmentTab.REGULAR) }
    var availableOnly by remember(character.id) { mutableStateOf(false) }
    var browserFilter by remember(character.id) { mutableStateOf(DevelopmentBrowserFilter.ALL) }
    var selectedEntryId by remember(character.id) { mutableStateOf<String?>(null) }
    var plannedDevelopmentIds by remember(character.id) { mutableStateOf(emptySet<String>()) }
    var acquisitionRequest by remember(character.id) { mutableStateOf<DevelopmentAcquisitionRequest?>(null) }
    var pendingAbilityPurchase by remember(character.id) { mutableStateOf<PendingAbilityPurchase?>(null) }
    var pendingRequirementOverride by remember(character.id) { mutableStateOf<PendingRequirementOverride?>(null) }
    var editingDevelopment by remember(character.id) { mutableStateOf<DevelopmentEntry?>(null) }
    var creatingCustomDevelopment by remember(character.id) { mutableStateOf(false) }
    var groupVisibility by remember(character.id) { mutableStateOf(DevelopmentGroupVisibility()) }

    val rules = prepared.rules
    val planner = remember(character, catalog) { DevelopmentAcquisitionPlanner(character, catalog) }
    val chiRules = remember(character, catalog) { ChiRules(character, catalog) }

    fun increase(entry: DevelopmentEntry, optionIndex: Int) {
        val availability = rules.availability(entry, optionIndex)
        when {
            availability.canIncrease -> {
                if (entry.isAbility) {
                    pendingAbilityPurchase = PendingAbilityPurchase(entry, optionIndex)
                } else {
                    controller.setDevelopmentRank(entry.id, availability.currentRank + 1, optionIndex)
                }
            }
            availability.canForceIncrease -> {
                pendingRequirementOverride = PendingRequirementOverride(
                    entry = entry,
                    optionIndex = optionIndex,
                    failedChecks = availability.checks.filter { it.status != RequirementStatus.OK },
                )
            }
        }
    }

    fun decrease(entry: DevelopmentEntry) {
        val current = progress.rank(entry.id)
        if (current <= 0) return
        controller.setDevelopmentRank(entry.id, current - 1, progress.optionIndex(entry.id))
    }

    fun branchName(entry: DevelopmentEntry): String = when {
        entry.isAbility -> entry.name
        entry.accessId != null -> catalog.byId(entry.accessId)?.name ?: entry.category.ifBlank { entry.name }
        else -> entry.category.ifBlank { entry.name }
    }

    fun availabilityFor(entry: DevelopmentEntry): DevelopmentAvailability =
        availabilityById.getOrPut(entry.id) { rules.availability(entry) }

    val filteredEntriesAsync by produceState<List<DevelopmentEntry>?>(
        null,
        query,
        tab,
        availableOnly,
        browserFilter,
        plannedDevelopmentIds,
        character,
        catalog,
        availabilityById,
    ) {
        value = null
        value = withContext(Dispatchers.Default) {
            val needle = developmentNormalize(query)
            developmentIndex.all
                .asSequence()
                .filterNot { it.entry.id == MagicEquipmentRules.BASE_MANA_ENTRY_ID }
                .filter { indexed ->
                    val entry = indexed.entry
                    when (tab) {
                        DevelopmentTab.REGULAR -> indexed.kind == DevelopmentEntryKind.REGULAR
                        DevelopmentTab.SPECIAL -> indexed.kind == DevelopmentEntryKind.SPECIAL
                        DevelopmentTab.MARTIAL_ARTS -> indexed.kind == DevelopmentEntryKind.MARTIAL
                        DevelopmentTab.CHI -> indexed.kind == DevelopmentEntryKind.CHI
                        DevelopmentTab.OWNED -> progress.rank(entry.id) > 0
                    }
                }
                .filter { indexed ->
                    needle.isBlank() || indexed.searchText.contains(needle)
                }
                .filter { indexed ->
                    val entry = indexed.entry
                    if (tab == DevelopmentTab.OWNED || tab == DevelopmentTab.CHI) {
                        tab == DevelopmentTab.OWNED || !availableOnly || availabilityFor(entry).canIncrease
                    } else {
                        when (browserFilter) {
                            DevelopmentBrowserFilter.ALL -> true
                            DevelopmentBrowserFilter.PLAN -> entry.id in plannedDevelopmentIds
                            DevelopmentBrowserFilter.AVAILABLE -> availabilityFor(entry).canIncrease
                            DevelopmentBrowserFilter.ALMOST -> {
                                val availability = availabilityFor(entry)
                                if (availability.canIncrease) false else {
                                    val missing = planner.plan(
                                        DevelopmentAcquisitionRequest.single(entry.id, includeTarget = false, enforceBudget = false),
                                    )
                                    missing.unresolvedRequirements.isEmpty() && missing.steps.size == 1
                                }
                            }
                        }
                    }
                }
                .sortedWith(
                    compareBy<IndexedDevelopmentEntry>(
                        { indexed -> if (tab == DevelopmentTab.SPECIAL || (tab == DevelopmentTab.OWNED && indexed.entry.isSpecialDevelopment)) developmentNormalize(indexed.groupName) else developmentNormalize(indexed.entry.category) },
                        { indexed -> if (indexed.entry.isAbility) 0 else 1 },
                        { indexed -> developmentNormalize(indexed.entry.name) },
                    )
                )
                .map { it.entry }
                .toList()
        }
    }
    val filteredEntries = filteredEntriesAsync.orEmpty()
    val filteredEntriesPreparing = filteredEntriesAsync == null
    val searchActive = query.isNotBlank()

    fun groupId(prefix: String, name: String): String = "$prefix:$name"
    fun toggleGroup(id: String) {
        groupVisibility = groupVisibility.toggle(id)
    }

    val selectedUnlocks by produceState<List<DevelopmentEntry>?>(
        initialValue = null,
        key1 = selectedEntryId,
        key2 = planner,
    ) {
        val id = selectedEntryId
        if (id == null) {
            value = emptyList()
        } else {
            value = null
            value = withContext(Dispatchers.Default) { planner.unlocks(id) }
        }
    }

    val filteredChiTechniques = remember(query, availableOnly, character, chiCatalog, catalog, tab) {
        if (tab != DevelopmentTab.CHI || chiTabUi == null) {
            emptyList()
        } else {
            val needle = developmentNormalize(query)
            chiCatalog.techniques.filter { technique ->
                val matches = needle.isBlank() || developmentNormalize(
                    listOf(technique.name, technique.school, technique.action, technique.effect, technique.requirements).joinToString(" ")
                ).contains(needle)
                matches && (!availableOnly || chiRules.availability(technique).unlocked)
            }.sortedWith(compareBy<ChiTechnique>({ developmentNormalize(it.school) }, { developmentNormalize(it.name) }))
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item { Spacer(Modifier.height(8.dp)) }
        item {
            DublScreenHeader(
                title = "Навыки",
                subtitle = if (chiTabUi != null) "Развитие, боевые искусства, ${chiTabUi.label} и спец. ветки" else "Развитие, боевые искусства и спец. ветки",
            )
        }

        item {
            DevelopmentBudgetCard(economy, showChi = chiEconomyUi != null)
        }

        if (plannedDevelopmentIds.isNotEmpty()) {
            val planSummary = planner.plan(
                DevelopmentAcquisitionRequest(
                    targets = plannedDevelopmentIds.sorted().map { DevelopmentAcquisitionTarget(it) },
                    enforceBudget = false,
                ),
            )
            item {
                DublCard(Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("План персонажа · ${plannedDevelopmentIds.size}", fontWeight = FontWeight.Bold)
                            Text(
                                "Осталось: ${planSummary.xpCost} XP${if (planSummary.abilityCost > 0) " · ${planSummary.abilityCost} ОС" else ""}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Button(
                            enabled = planSummary.steps.isNotEmpty(),
                            onClick = {
                                acquisitionRequest = DevelopmentAcquisitionRequest(
                                    targets = plannedDevelopmentIds.sorted().map { DevelopmentAcquisitionTarget(it) },
                                )
                            },
                        ) { Text("Взять план") }
                    }
                }
            }
        }

        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                items(developmentTabs) { target ->
                    FilterChip(
                        selected = tab == target,
                        onClick = {
                            tab = target
                            query = ""
                            availableOnly = false
                            browserFilter = DevelopmentBrowserFilter.ALL
                        },
                        label = { Text(if (target == DevelopmentTab.CHI) chiTabPresentation?.label ?: target.title else target.title) },
                        leadingIcon = if (target == DevelopmentTab.CHI) {
                            { fcpDevelopmentIcon(chiTabPresentation?.icon)?.let { Text(it) } }
                        } else null,
                    )
                }
            }
        }

        item {
            OutlinedButton(onClick = { creatingCustomDevelopment = true }) {
                Text("Своя запись")
            }
        }

        if (tab == DevelopmentTab.SPECIAL) {
            item {
                DublCard(Modifier.fillMaxWidth()) {
                    Text(
                        "Как работают спец. ветки",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "Если у ветки есть доступ за ОС, сначала откройте его. Затем навыки этой ветки покупаются отдельно за XP и проверяют свои требования.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (tab == DevelopmentTab.CHI && chiTabUi != null) {
            val automaticAccess = character.chiAutomaticAccess
            val progressionBonus = character.chiProgressionBonus
            item {
                ChiDevelopmentCard(
                    label = chiTabUi.label,
                    enabled = character.chiActive,
                    automaticAccess = automaticAccess,
                    current = character.chiCurrent,
                    maximum = character.chiMaximum,
                    baseMaximum = character.chiBaseMaximum,
                    bonusRanks = character.chiBonusRanks,
                    progressionBonus = progressionBonus,
                    onToggle = controller::setChiEnabled,
                    onChangeCurrent = controller::changeChi,
                    onChangeBonusRanks = { delta -> controller.setChiBonusRanks(character.chiBonusRanks + delta) },
                    onRestore = controller::restoreChi,
                )
            }
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Поиск развития или приёма ЦИ") },
                    singleLine = true,
                )
            }
            item {
                FilterChip(
                    selected = availableOnly,
                    onClick = { availableOnly = !availableOnly },
                    label = { Text("Доступно сейчас") },
                )
            }
            item {
                Text(
                    "Развитие: ${filteredEntries.size} · Приёмы: ${filteredChiTechniques.size}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            filteredEntries.groupBy { it.category.ifBlank { "Развитие ЦИ" } }.forEach { (category, entries) ->
                val id = groupId("chi-development", category)
                val expanded = groupVisibility.isExpanded(id, searchActive)
                item(key = "chi-development-header-$category") {
                    DevelopmentGroupHeader(category, entries.size, expanded = expanded, onToggle = { toggleGroup(id) })
                }
                if (expanded) {
                    items(entries, key = { "chi-development-${it.id}" }) { entry ->
                        DevelopmentRow(
                            entry = entry,
                            availability = availabilityFor(entry),
                            progress = progress,
                            planned = entry.id in plannedDevelopmentIds,
                            onClick = { selectedEntryId = entry.id },
                        )
                    }
                }
            }
            filteredChiTechniques.groupBy { it.school }.forEach { (school, techniques) ->
                val id = groupId("chi-technique", school)
                val expanded = groupVisibility.isExpanded(id, searchActive)
                item(key = "chi-technique-header-$school") {
                    DevelopmentGroupHeader(school, techniques.size, trailing = "Приёмы", expanded = expanded, onToggle = { toggleGroup(id) })
                }
                if (expanded) {
                    items(techniques, key = { "chi-technique-${it.id}" }) { technique ->
                        ChiTechniqueCard(
                            technique = technique,
                            availability = chiRules.availability(technique),
                            onUse = { controller.changeChi(-technique.chiCost) },
                        )
                    }
                }
            }
        } else {
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = {
                        Text(
                            when (tab) {
                                DevelopmentTab.REGULAR -> "Поиск обычного навыка"
                                DevelopmentTab.SPECIAL -> "Поиск спец. ветки или навыка"
                                DevelopmentTab.MARTIAL_ARTS -> "Поиск стиля или приёма"
                                DevelopmentTab.CHI -> ""
                                DevelopmentTab.OWNED -> "Поиск среди взятых"
                            }
                        )
                    },
                    singleLine = true,
                )
            }

            if (tab != DevelopmentTab.OWNED) {
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        items(DevelopmentBrowserFilter.entries) { filter ->
                            FilterChip(
                                selected = browserFilter == filter,
                                onClick = { browserFilter = filter },
                                label = { Text(filter.title) },
                            )
                        }
                    }
                }
            }

            item {
                Text(
                    when (tab) {
                        DevelopmentTab.REGULAR -> "Обычных навыков: ${filteredEntries.size}"
                        DevelopmentTab.SPECIAL -> "Записей спец. веток: ${filteredEntries.size}"
                        DevelopmentTab.MARTIAL_ARTS -> "Стилей и приёмов: ${filteredEntries.size}"
                        DevelopmentTab.CHI -> ""
                        DevelopmentTab.OWNED -> "Взято: ${filteredEntries.size}"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (filteredEntriesPreparing) {
                item {
                    DublCard(Modifier.fillMaxWidth()) {
                        Text(
                            "Подготавливаем список развития…",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "Требования и доступность рассчитываются в фоне — интерфейс остаётся доступным.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else if (filteredEntries.isEmpty()) {
                item {
                    DublCard(Modifier.fillMaxWidth()) {
                        Text(
                            if (tab == DevelopmentTab.OWNED) "Пока ничего не взято" else "Ничего не найдено",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            if (tab == DevelopmentTab.OWNED) {
                                "Полученные навыки, боевые искусства и открытые спец. ветки появятся здесь вместе с описаниями."
                            } else {
                                "Сбросьте поиск или фильтр доступности."
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (query.isNotBlank() || availableOnly) {
                            Spacer(Modifier.height(10.dp))
                            OutlinedButton(
                                onClick = {
                                    query = ""
                                    availableOnly = false
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("Сбросить фильтры") }
                        }
                    }
                }
            } else when (tab) {
                DevelopmentTab.REGULAR -> {
                val grouped = filteredEntries.groupBy { it.category.ifBlank { "Общие" } }
                grouped.forEach { (category, entries) ->
                    val id = groupId("regular", category)
                    val expanded = groupVisibility.isExpanded(id, searchActive)
                    item(key = "regular-header-$category") {
                        DevelopmentGroupHeader(category, entries.size, expanded = expanded, onToggle = { toggleGroup(id) })
                    }
                    if (expanded) {
                        items(entries, key = { it.id }) { entry ->
                            DevelopmentRow(
                                entry = entry,
                                availability = availabilityFor(entry),
                                progress = progress,
                                planned = entry.id in plannedDevelopmentIds,
                                onClick = { selectedEntryId = entry.id },
                            )
                        }
                    }
                }
            }

                DevelopmentTab.SPECIAL -> {
                val grouped = filteredEntries.groupBy(::branchName)
                grouped.forEach { (branch, branchEntries) ->
                    val id = groupId("special", branch)
                    val expanded = groupVisibility.isExpanded(id, searchActive)
                    val access = branchEntries.firstOrNull { it.isAbility }
                        ?: catalog.entries.firstOrNull { it.isAbility && developmentNormalize(it.name) == developmentNormalize(branch) }
                    item(key = "special-header-$branch") {
                        Column(Modifier.clickable { toggleGroup(id) }) {
                            SpecialBranchHeader(
                                title = branch,
                                entriesCount = branchEntries.count { !it.isAbility },
                                access = access,
                                progress = progress,
                                rules = rules,
                                expanded = expanded,
                            )
                        }
                    }
                    if (expanded) {
                        items(branchEntries.sortedWith(compareBy<DevelopmentEntry>({ if (it.isAbility) 0 else 1 }, { developmentNormalize(it.name) })), key = { it.id }) { entry ->
                            DevelopmentRow(
                                entry = entry,
                                availability = availabilityFor(entry),
                                progress = progress,
                                planned = entry.id in plannedDevelopmentIds,
                                onClick = { selectedEntryId = entry.id },
                            )
                        }
                    }
                }
            }

                DevelopmentTab.MARTIAL_ARTS -> {
                    val grouped = filteredEntries.groupBy { it.category.ifBlank { "Боевые искусства" } }
                    grouped.forEach { (category, entries) ->
                        val id = groupId("martial", category)
                        val expanded = groupVisibility.isExpanded(id, searchActive)
                        item(key = "martial-header-$category") {
                            DevelopmentGroupHeader(category, entries.size, expanded = expanded, onToggle = { toggleGroup(id) })
                        }
                        if (expanded) {
                            items(entries, key = { it.id }) { entry ->
                                DevelopmentRow(
                                    entry = entry,
                                    availability = availabilityFor(entry),
                                    progress = progress,
                                    planned = entry.id in plannedDevelopmentIds,
                                    onClick = { selectedEntryId = entry.id },
                                )
                            }
                        }
                    }
                }

                DevelopmentTab.CHI -> Unit

                DevelopmentTab.OWNED -> {
                    val regularOwned = filteredEntries.filter { it.isRegularDevelopment }
                    val martialOwned = filteredEntries.filter { it.isMartialArt }
                    val specialOwned = filteredEntries.filter { it.isSpecialDevelopment }
                    val chiOwned = filteredEntries.filter { it.isChiDevelopment }

                if (regularOwned.isNotEmpty()) {
                    item(key = "owned-regular-header") {
                        DevelopmentGroupHeader("Обычные навыки", regularOwned.size)
                    }
                    items(regularOwned, key = { "owned-${it.id}" }) { entry ->
                        OwnedDevelopmentRow(
                            entry = entry,
                            progress = progress,
                            availability = availabilityFor(entry),
                            onClick = { selectedEntryId = entry.id },
                        )
                    }
                }

                if (martialOwned.isNotEmpty()) {
                    item(key = "owned-martial-header") {
                        DevelopmentGroupHeader("Боевые искусства", martialOwned.size)
                    }
                    items(martialOwned, key = { "owned-${it.id}" }) { entry ->
                        OwnedDevelopmentRow(
                            entry = entry,
                            progress = progress,
                            availability = availabilityFor(entry),
                            onClick = { selectedEntryId = entry.id },
                        )
                    }
                }

                if (chiOwned.isNotEmpty()) {
                    item(key = "owned-chi-header") {
                        DevelopmentGroupHeader("ЦИ", chiOwned.size)
                    }
                    items(chiOwned, key = { "owned-${it.id}" }) { entry ->
                        OwnedDevelopmentRow(
                            entry = entry,
                            progress = progress,
                            availability = availabilityFor(entry),
                            onClick = { selectedEntryId = entry.id },
                        )
                    }
                }

                specialOwned.groupBy(::branchName).forEach { (branch, entries) ->
                    item(key = "owned-special-header-$branch") {
                        DevelopmentGroupHeader(branch, entries.size, trailing = "Спец. ветка")
                    }
                    items(entries.sortedWith(compareBy<DevelopmentEntry>({ if (it.isAbility) 0 else 1 }, { developmentNormalize(it.name) })), key = { "owned-${it.id}" }) { entry ->
                        OwnedDevelopmentRow(
                            entry = entry,
                            progress = progress,
                            availability = availabilityFor(entry),
                            onClick = { selectedEntryId = entry.id },
                        )
                    }
                }
            }
        }
        }
    }

    selectedEntryId?.let { id ->
        catalog.byId(id)?.let { entry ->
            DevelopmentDetailSheet(
                entry = entry,
                catalog = catalog,
                progress = progress,
                rules = rules,
                planner = planner,
                unlocks = selectedUnlocks,
                planned = entry.id in plannedDevelopmentIds,
                onTogglePlanned = {
                    plannedDevelopmentIds = if (entry.id in plannedDevelopmentIds) {
                        plannedDevelopmentIds - entry.id
                    } else {
                        plannedDevelopmentIds + entry.id
                    }
                },
                onAcquireRequirements = {
                    acquisitionRequest = DevelopmentAcquisitionRequest.single(entry.id, includeTarget = false)
                },
                onAcquireAll = { optionIndex ->
                    val request = DevelopmentAcquisitionRequest.single(
                        entryId = entry.id,
                        includeTarget = true,
                        optionIndex = optionIndex,
                    )
                    val plan = DevelopmentAcquisitionPlanner(character, catalog).plan(request)
                    val simpleTargetOnly = plan.choices.isEmpty() &&
                        plan.unresolvedRequirements.isEmpty() &&
                        plan.steps.size == 1 &&
                        (plan.steps.firstOrNull() as? DevelopmentAcquisitionStep.Development)?.entryId == entry.id
                    if (simpleTargetOnly && plan.canApply) {
                        controller.acquireDevelopment(catalog, request)
                        selectedEntryId = entry.id
                    } else {
                        acquisitionRequest = request
                    }
                },
                onOpenEntry = { targetId -> selectedEntryId = targetId },
                onIncrease = { optionIndex -> increase(entry, optionIndex) },
                onDecrease = { decrease(entry) },
                onEditLocal = { editingDevelopment = entry },
                onResetLocal = {
                    controller.resetDevelopmentOverride(entry.id)
                    selectedEntryId = null
                },
                onDeleteCustom = {
                    controller.removeCustomDevelopment(entry.id)
                    selectedEntryId = null
                },
                hasLocalOverride = character.developmentOverrides.containsKey(entry.id),
                isCustom = character.customDevelopmentEntries.any { it.id == entry.id },
                onDismiss = { selectedEntryId = null },
            )
        } ?: run { selectedEntryId = null }
    }

    acquisitionRequest?.let { request ->
        DevelopmentAcquisitionPreviewDialog(
            character = character,
            catalog = catalog,
            request = request,
            onApply = { resolved ->
                val focusedEntryId = selectedEntryId
                controller.acquireDevelopment(catalog, resolved)
                acquisitionRequest = null
                selectedEntryId = focusedEntryId
            },
            onDismiss = { acquisitionRequest = null },
        )
    }

    editingDevelopment?.let { entry ->
        val isCustom = character.customDevelopmentEntries.any { it.id == entry.id }
        DevelopmentLocalEditDialog(
            initial = entry,
            title = if (isCustom) "Редактировать свою запись" else "Локальная правка",
            onSave = { updated ->
                if (isCustom) controller.updateCustomDevelopment(updated) else controller.setDevelopmentOverride(updated)
                editingDevelopment = null
            },
            onDismiss = { editingDevelopment = null },
        )
    }

    if (creatingCustomDevelopment) {
        DevelopmentLocalEditDialog(
            initial = emptyCustomDevelopmentEntry(),
            title = "Своя запись",
            onSave = { updated ->
                controller.addCustomDevelopment(updated)
                creatingCustomDevelopment = false
            },
            onDismiss = { creatingCustomDevelopment = false },
        )
    }

    pendingRequirementOverride?.let { pending ->
        AlertDialog(
            onDismissRequest = { pendingRequirementOverride = null },
            title = { Text("Требования не выполнены") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text(
                        if (pending.entry.isAbility) {
                            "Открыть «${pending.entry.name}» несмотря на невыполненные требования?"
                        } else {
                            "Добавить «${pending.entry.name}» несмотря на невыполненные требования?"
                        },
                    )
                    pending.failedChecks.forEach { check ->
                        Text(
                            "• ${check.text}",
                            style = MaterialTheme.typography.bodySmall,
                            color = when (check.status) {
                                RequirementStatus.FAIL -> DublDanger
                                RequirementStatus.MANUAL -> DublGold
                                RequirementStatus.OK -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                    Text(
                        "После добавления запись останется помеченной «⚠ Требования», пока условия реально не будут выполнены.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        if (pending.entry.isAbility) {
                            "Будет потрачено ${rules.abilityCost(pending.entry, pending.optionIndex)} ОС."
                        } else {
                            "Будет учтено ${pending.entry.cost} XP за следующий ранг."
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = DublGold,
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val current = progress.rank(pending.entry.id)
                        controller.setDevelopmentRank(
                            pending.entry.id,
                            current + 1,
                            pending.optionIndex,
                        )
                        pendingRequirementOverride = null
                    },
                ) {
                    Text(if (pending.entry.isAbility) "Открыть всё равно" else "Добавить всё равно")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRequirementOverride = null }) { Text("Отмена") }
            },
        )
    }

    pendingAbilityPurchase?.let { pending ->
        val cost = rules.abilityCost(pending.entry, pending.optionIndex)
        AlertDialog(
            onDismissRequest = { pendingAbilityPurchase = null },
            title = { Text("Открыть спец. ветку?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(pending.entry.name, fontWeight = FontWeight.Bold)
                    pending.entry.abilityOptions.getOrNull(pending.optionIndex)?.let { option ->
                        Text("Источник: ${option.source}")
                    }
                    Text("Будет потрачено $cost ОС. Навыки внутри ветки покупаются отдельно за XP.")
                    if (character.creationComplete) {
                        Text(
                            "Создание уже завершено. Книга описывает покупку способностей за ОС при создании; продолжайте только по решению мастера.",
                            style = MaterialTheme.typography.bodySmall,
                            color = DublGold,
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val current = progress.rank(pending.entry.id)
                        controller.setDevelopmentRank(pending.entry.id, current + 1, pending.optionIndex)
                        pendingAbilityPurchase = null
                    },
                ) { Text("Открыть · $cost ОС") }
            },
            dismissButton = {
                TextButton(onClick = { pendingAbilityPurchase = null }) { Text("Отмена") }
            },
        )
    }
}

@Composable
private fun DevelopmentLoadingScreen(stage: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = DublAccent.copy(alpha = 0.035f),
            border = BorderStroke(1.dp, DublAccent.copy(alpha = 0.24f)),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CircularProgressIndicator(color = DublAccent)
                Text(
                    "Подготавливаем навыки",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    stage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "Первое открытие может занять несколько секунд. Fury продолжает работать — дождитесь завершения подготовки.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun DevelopmentBudgetCard(economy: CharacterEconomyBreakdown, showChi: Boolean) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = DublGold.copy(alpha = 0.035f),
        border = BorderStroke(1.dp, DublGold.copy(alpha = 0.24f)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 11.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Осталось XP",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "${economy.remainingXp} / ${economy.totalExperience}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (economy.overspentXp) DublDanger else DublGold,
                    )
                    Text(
                        "${economy.spentXp} XP потрачено",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (economy.overspentXp) DublDanger else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.End,
                ) {
                    Text(
                        "ОС",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "${economy.abilityPointsRemaining} свободно из ${economy.abilityPointsBudget}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (economy.overspentAbilityPoints) DublDanger else DublGold,
                    )
                    Text(
                        "${economy.abilityPointsSpent} ОС потрачено",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (economy.overspentAbilityPoints) DublDanger else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
            Text(
                buildList {
                    add("Характеристики ${economy.attributeXp}")
                    add("Умения ${economy.skillXp}")
                    add("Навыки ${economy.developmentXp}")
                    if (showChi) add("ЦИ ${economy.chiXp}")
                    add("Магия ${economy.manaXp + economy.magicSchoolXp + economy.spellXp}")
                    if (economy.adjustmentXp != 0) add("Поправка ${economy.adjustmentXp}")
                }.joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ChiDevelopmentCard(
    label: String,
    enabled: Boolean,
    automaticAccess: Boolean,
    current: Int,
    maximum: Int,
    baseMaximum: Int,
    bonusRanks: Int,
    progressionBonus: Int,
    onToggle: (Boolean) -> Unit,
    onChangeCurrent: (Int) -> Unit,
    onChangeBonusRanks: (Int) -> Unit,
    onRestore: () -> Unit,
) {
    DublCard(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    "Внутренняя энергия для боевых приёмов",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            DublSwitch(
                checked = enabled,
                onCheckedChange = if (automaticAccess) null else onToggle,
                enabled = !automaticAccess,
            )
        }

        if (automaticAccess) {
            Text(
                "Ресурс открыт способностью «Внутренняя ЦИ» и остаётся активным, пока способность изучена.",
                style = MaterialTheme.typography.bodySmall,
                color = DublAccent,
            )
        }

        if (!enabled) {
            Text(
                "Включите ЦИ, если персонаж освоил доступ к этому ресурсу. Сам переключатель не расходует XP и не выдаёт способности автоматически.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@DublCard
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = DublAccent.copy(alpha = 0.055f),
            border = BorderStroke(1.dp, DublAccent.copy(alpha = 0.24f)),
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text("Текущий запас", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("$current / $maximum", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(onClick = { onChangeCurrent(-1) }, enabled = current > 0) { Text("−1") }
                        OutlinedButton(onClick = { onChangeCurrent(1) }, enabled = current < maximum) { Text("+1") }
                    }
                }
                OutlinedButton(onClick = onRestore, modifier = Modifier.fillMaxWidth(), enabled = current < maximum) {
                    Text("Восстановить полностью")
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Дополнительный запас ЦИ", fontWeight = FontWeight.SemiBold)
                Text(
                    "$bonusRanks / 10 рангов · ${CharacterEconomy.CHI_BONUS_RANK_XP} XP за ранг",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = { onChangeBonusRanks(-1) }, enabled = bonusRanks > 0) { Text("−") }
                OutlinedButton(onClick = { onChangeBonusRanks(1) }, enabled = bonusRanks < 10) { Text("+") }
            }
        }

        Text(
            "Максимум: база $baseMaximum + купленный запас $bonusRanks + развитие $progressionBonus = $maximum.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "По правилам запас полностью восстанавливается после 15 минут медитации/лёгкой активности или после 8 часов отдыха.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ChiTechniqueCard(
    technique: ChiTechnique,
    availability: com.furybook.dubl.model.ChiTechniqueAvailability,
    onUse: () -> Unit,
) {
    val unlocked = availability.unlocked
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(13.dp),
        color = if (unlocked) DublAccent.copy(alpha = 0.035f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.24f),
        border = BorderStroke(1.dp, if (unlocked) DublAccent.copy(alpha = 0.20f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 11.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(technique.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        listOf(technique.action, "${technique.chiCost} ЦИ").filter { it.isNotBlank() }.joinToString(" · "),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (unlocked) DublAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    if (unlocked) "Открыт" else "Закрыт",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (unlocked) DublAccent else DublGold,
                )
            }
            if (technique.effect.isNotBlank()) {
                Text(technique.effect, style = MaterialTheme.typography.bodySmall)
            }
            if (!unlocked) {
                Text(
                    "Требуется: ${technique.requirements}",
                    style = MaterialTheme.typography.bodySmall,
                    color = DublGold,
                )
            } else if (!availability.canUse && availability.reason.isNotBlank()) {
                Text(availability.reason, style = MaterialTheme.typography.bodySmall, color = DublDanger)
            }
            OutlinedButton(
                onClick = onUse,
                enabled = availability.canUse,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (technique.chiCost > 0) "Использовать · ${technique.chiCost} ЦИ" else "Использовать · без затрат ЦИ")
            }
        }
    }
}

@Composable
private fun DevelopmentGroupHeader(
    title: String,
    count: Int,
    trailing: String? = null,
    expanded: Boolean? = null,
    onToggle: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onToggle != null) Modifier.clickable(onClick = onToggle) else Modifier)
            .padding(top = 11.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            listOfNotNull(trailing, count.toString(), expanded?.let { if (it) "▲" else "▼" }).joinToString(" · "),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
        )
    }
}

@Composable
private fun SpecialBranchHeader(
    title: String,
    entriesCount: Int,
    access: DevelopmentEntry?,
    progress: DevelopmentProgress,
    rules: DevelopmentRules,
    expanded: Boolean,
) {
    val accessRank = access?.let { progress.rank(it.id) } ?: 0
    val accessLabel = when {
        access == null -> "XP-ветка"
        accessRank > 0 -> "Доступ открыт"
        access.abilityOptions.isNotEmpty() -> {
            val costs = access.abilityOptions.map { it.value }.distinct().sorted()
            val value = if (costs.size == 1) costs.first().toString() else "${costs.first()}–${costs.last()}"
            "$value ОС для доступа"
        }
        else -> "${rules.abilityCost(access, 0)} ОС для доступа"
    }
    val accent = when {
        access == null -> DublAccent
        accessRank > 0 -> DublGold
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 11.dp, bottom = 2.dp),
        shape = RoundedCornerShape(10.dp),
        color = accent.copy(alpha = 0.035f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.18f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    accessLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = accent,
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                "$entriesCount XP-навык${if (entriesCount == 1) "" else "ов"} · ${if (expanded) "▲" else "▼"}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun OwnedDevelopmentRow(
    entry: DevelopmentEntry,
    progress: DevelopmentProgress,
    availability: DevelopmentAvailability,
    onClick: () -> Unit,
) {
    val rank = progress.rank(entry.id)
    val invalidOwned = availability.checks.any { it.status != RequirementStatus.OK }
    val accent = when {
        invalidOwned -> DublDanger
        entry.isSpecialDevelopment -> DublGold
        else -> DublAccent
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = accent.copy(alpha = 0.035f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.30f)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        entry.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        when {
                            entry.isAbility -> "Открытая спец. ветка"
                            entry.isSpecialDevelopment -> "Спец. навык · ${entry.category}"
                            else -> "Обычный навык · ${entry.category.ifBlank { "Общие" }}"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = accent,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(10.dp))
                DevelopmentStatusPill(
                    status = when {
                        invalidOwned -> "⚠ Требования"
                        entry.maxRank > 1 -> "Ранг $rank/${entry.maxRank}"
                        else -> "Взято"
                    },
                    accent = accent,
                )
            }
            if (invalidOwned) {
                Text(
                    "Текущие требования не выполнены",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = DublDanger,
                )
            }
            val description = entry.benefit.ifBlank { entry.notes }
            if (description.isNotBlank()) {
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private fun developmentRequirementNeedLabel(text: String): String {
    val match = Regex("^(.+?):\\s*(\\d+)\\s*/\\s*(\\d+)$").matchEntire(text.trim())
    if (match != null) {
        val (label, _, need) = match.destructured
        return "${label.trim()} $need"
    }
    return text.substringBefore(':').trim().ifBlank { text.trim() }
}

private fun developmentRequirementDisplay(text: String): Pair<String, String?> {
    val match = Regex("^(.+?):\\s*(\\d+)\\s*/\\s*(\\d+)$").matchEntire(text.trim())
    if (match != null) {
        val (label, actual, need) = match.destructured
        return "${label.trim()} $need" to "сейчас: $actual"
    }
    return text.trim() to null
}

private fun developmentCostLabel(xp: Int, ability: Int): String = buildString {
    if (xp > 0) append("$xp XP")
    if (ability > 0) {
        if (isNotEmpty()) append(" + ")
        append("$ability ОС")
    }
    if (isEmpty()) append("без затрат")
}

@Composable
private fun DevelopmentRow(
    entry: DevelopmentEntry,
    availability: DevelopmentAvailability,
    progress: DevelopmentProgress,
    planned: Boolean = false,
    onClick: () -> Unit,
) {
    val rank = progress.rank(entry.id)
    val owned = rank > 0
    val failedChecks = availability.checks.filter { it.status == RequirementStatus.FAIL }
    val manualChecks = availability.checks.filter { it.status == RequirementStatus.MANUAL }
    val canIncrease = availability.canIncrease
    val invalidOwned = owned && (failedChecks.isNotEmpty() || manualChecks.isNotEmpty())
    val accent = when {
        invalidOwned -> DublDanger
        owned -> DublGold
        canIncrease -> DublAccent
        else -> MaterialTheme.colorScheme.outline
    }
    val status = when {
        planned -> "★ В плане"
        invalidOwned -> "⚠ Требования"
        owned && entry.isAbility -> "✓ Открыта"
        owned -> "✓ $rank/${entry.maxRank}"
        canIncrease && entry.isAbility -> "Открыть"
        canIncrease -> "✓ Доступно"
        manualChecks.isNotEmpty() -> "? Проверить"
        failedChecks.size == 1 -> "⚠ Нужна ${developmentRequirementNeedLabel(failedChecks.first().text)}"
        failedChecks.isNotEmpty() -> {
            val failedCount = failedChecks.size
            "⚠ Не хватает $failedCount требований"
        }
        else -> "Закрыто"
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = if (owned || canIncrease) accent.copy(alpha = 0.035f) else MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, accent.copy(alpha = if (owned || canIncrease) 0.42f else 0.20f)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    entry.name,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (owned || canIncrease) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "$rank/${entry.maxRank}",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (owned || canIncrease) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (entry.isAbility) {
                        if (entry.abilityOptions.isNotEmpty()) {
                            val values = entry.abilityOptions.map { it.value }.distinct().sorted()
                            values.joinToString("–") + " ОС"
                        } else "${entry.cost} ОС"
                    } else "${entry.cost} XP",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    status,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = when {
                        planned -> DublGold
                        invalidOwned -> DublDanger
                        canIncrease || owned -> accent
                        failedChecks.isNotEmpty() -> DublGold
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val description = entry.benefit.ifBlank { entry.notes }
            if (description.isNotBlank()) {
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun DevelopmentStatusPill(
    status: String,
    accent: Color,
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = accent.copy(alpha = 0.09f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.26f)),
    ) {
        Text(
            text = status,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
            fontSize = 11.sp,
            lineHeight = 13.sp,
            fontWeight = FontWeight.Bold,
            color = accent,
            maxLines = 1,
        )
    }
}

@Composable
private fun DevelopmentRequirementsCard(
    entry: DevelopmentEntry,
    checks: List<RequirementCheck>,
    missing: DevelopmentAcquisitionPlan,
    onOpenEntry: (String) -> Unit,
    onAcquireRequirements: () -> Unit,
) {
    val completed = checks.count { it.status == RequirementStatus.OK }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = DublAccent.copy(alpha = 0.035f),
        border = BorderStroke(1.dp, DublAccent.copy(alpha = 0.22f)),
    ) {
        Column(
            modifier = Modifier.padding(11.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Требования", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "$completed / ${checks.size}",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (completed == checks.size) DublAccent else DublGold,
                )
            }
            checks.forEach { check ->
                val tint = when (check.status) {
                    RequirementStatus.OK -> DublAccent
                    RequirementStatus.FAIL -> DublDanger
                    RequirementStatus.MANUAL -> DublGold
                }
                val (label, current) = developmentRequirementDisplay(check.text)
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = check.targetEntryId != null) {
                            check.targetEntryId?.let(onOpenEntry)
                        },
                    shape = RoundedCornerShape(8.dp),
                    color = tint.copy(alpha = 0.05f),
                    border = BorderStroke(1.dp, tint.copy(alpha = 0.18f)),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            when (check.status) {
                                RequirementStatus.OK -> "✓"
                                RequirementStatus.FAIL -> "✕"
                                RequirementStatus.MANUAL -> "?"
                            },
                            color = tint,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            label,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        current?.let {
                            Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (check.targetEntryId != null) {
                            Spacer(Modifier.width(5.dp))
                            Text("›", color = tint, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            if (entry.requirements.isNotBlank()) {
                Text(
                    "По правилу: ${entry.requirements}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (missing.steps.isEmpty() && missing.unresolvedRequirements.isEmpty()) {
                Text("✓ Все требования выполнены", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = DublAccent)
            } else {
                Text("Что нужно сделать", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                missing.steps.forEach { step ->
                    Text(
                        "• ${developmentAcquisitionStepText(step)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                missing.unresolvedRequirements.forEach { unresolved ->
                    Text("• $unresolved", style = MaterialTheme.typography.bodySmall, color = DublDanger)
                }
                if (missing.steps.isNotEmpty()) {
                    Text(
                        "До выполнения требований: ${developmentCostLabel(missing.xpCost, missing.abilityCost)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = DublGold,
                    )
                    OutlinedButton(
                        enabled = missing.unresolvedRequirements.isEmpty(),
                        onClick = onAcquireRequirements,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Добрать требования · ${developmentCostLabel(missing.xpCost, missing.abilityCost)}") }
                }
            }
        }
    }
}

@Composable
private fun DevelopmentUnlocksCard(
    unlocks: List<DevelopmentEntry>?,
    onOpenEntry: (String) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(11.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.18f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.24f)),
    ) {
        Column(Modifier.padding(11.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Открывает", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    unlocks?.size?.toString() ?: "…",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = DublAccent,
                )
            }
            when {
                unlocks == null -> Text(
                    "Связи рассчитываются в фоне",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                unlocks.isEmpty() -> Text(
                    "Ничего напрямую не открывает",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> {
                    unlocks.take(8).forEach { target ->
                        Surface(
                            modifier = Modifier.fillMaxWidth().clickable { onOpenEntry(target.id) },
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.20f)),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(target.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        if (target.isAbility) "Доступ к ветке" else "${target.cost} XP",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Text("›", color = DublAccent, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    if (unlocks.size > 8) {
                        Text("И ещё ${unlocks.size - 8}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DevelopmentDetailSheet(
    entry: DevelopmentEntry,
    catalog: DevelopmentCatalog,
    progress: DevelopmentProgress,
    rules: DevelopmentRules,
    planner: DevelopmentAcquisitionPlanner,
    unlocks: List<DevelopmentEntry>?,
    planned: Boolean,
    onTogglePlanned: () -> Unit,
    onAcquireRequirements: () -> Unit,
    onAcquireAll: (Int) -> Unit,
    onOpenEntry: (String) -> Unit,
    onIncrease: (Int) -> Unit,
    onDecrease: () -> Unit,
    onEditLocal: () -> Unit,
    onResetLocal: () -> Unit,
    onDeleteCustom: () -> Unit,
    hasLocalOverride: Boolean,
    isCustom: Boolean,
    onDismiss: () -> Unit,
) {
    val currentRank = progress.rank(entry.id)
    var optionIndex by remember(entry.id, currentRank) {
        mutableStateOf(if (currentRank > 0) progress.optionIndex(entry.id) else 0)
    }
    var advancedExpanded by remember(entry.id) { mutableStateOf(false) }
    val availability = rules.availability(entry, optionIndex)
    val missing = planner.plan(
        DevelopmentAcquisitionRequest.single(entry.id, includeTarget = false, enforceBudget = false),
    )
    val acquirePlan = planner.plan(
        DevelopmentAcquisitionRequest.single(
            entryId = entry.id,
            includeTarget = true,
            optionIndex = optionIndex,
            enforceBudget = false,
        ),
    )
    val ownedInvalid = currentRank > 0 && availability.checks.any { it.status != RequirementStatus.OK }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val primaryActionLabel = when {
        entry.isAbility && missing.steps.isEmpty() && missing.unresolvedRequirements.isEmpty() ->
            "Открыть ветку · ${developmentCostLabel(acquirePlan.xpCost, acquirePlan.abilityCost)}"
        missing.steps.isEmpty() && missing.unresolvedRequirements.isEmpty() ->
            "Взять ранг · ${developmentCostLabel(acquirePlan.xpCost, acquirePlan.abilityCost)}"
        else -> "Добрать и взять · ${developmentCostLabel(acquirePlan.xpCost, acquirePlan.abilityCost)}"
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 720.dp)
                .containSheetOverscroll()
                .verticalScroll(rememberScrollState())
                .padding(start = 18.dp, end = 18.dp, bottom = 26.dp),
            verticalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(entry.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(
                        "${entry.category.ifBlank { entry.section }} · ранг $currentRank/${entry.maxRank}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (ownedInvalid) {
                    Text("⚠", color = DublDanger, style = MaterialTheme.typography.titleLarge)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = DublGold.copy(alpha = 0.10f),
                    border = BorderStroke(1.dp, DublGold.copy(alpha = 0.28f)),
                ) {
                    Text(
                        if (entry.isAbility) "${availability.abilityCost} ОС" else "${entry.cost} XP / ранг",
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = DublGold,
                    )
                }
                Text(
                    "После: ${acquirePlan.xpRemainingAfter} XP · ${acquirePlan.abilityRemainingAfter} ОС",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (entry.isAbility && entry.abilityOptions.isNotEmpty()) {
                Text("Источник способности", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    items(entry.abilityOptions.indices.toList()) { index ->
                        val option = entry.abilityOptions[index]
                        FilterChip(
                            selected = optionIndex == index,
                            enabled = currentRank == 0,
                            onClick = { optionIndex = index },
                            label = { Text("${option.source} · ${option.value} ОС") },
                        )
                    }
                }
            }

            Button(
                onClick = { onAcquireAll(optionIndex) },
                enabled = currentRank < entry.maxRank,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(primaryActionLabel) }

            if (currentRank > 0) {
                OutlinedButton(onClick = onDecrease, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        when {
                            entry.isAbility -> "Закрыть доступ"
                            currentRank == 1 -> "Убрать"
                            else -> "− ранг"
                        }
                    )
                }
            }

            if (entry.benefit.isNotBlank()) {
                DetailBlock("Что даёт", entry.benefit)
            }
            if (entry.notes.isNotBlank()) {
                DetailBlock("Особое", entry.notes)
            }
            if (entry.conflictNote.isNotBlank()) {
                DetailBlock("Расхождение книги", entry.conflictNote)
            }

            DevelopmentRequirementsCard(
                entry = entry,
                checks = availability.checks,
                missing = missing,
                onOpenEntry = onOpenEntry,
                onAcquireRequirements = onAcquireRequirements,
            )

            if (ownedInvalid) {
                Surface(
                    shape = RoundedCornerShape(9.dp),
                    color = DublDanger.copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, DublDanger.copy(alpha = 0.35f)),
                ) {
                    Text(
                        "Запись уже получена, но текущие требования не выполнены. Она не удаляется автоматически и останется помеченной, пока условия не будут соблюдены.",
                        modifier = Modifier.padding(10.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = DublDanger,
                    )
                }
            }

            DevelopmentUnlocksCard(unlocks = unlocks, onOpenEntry = onOpenEntry)

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(11.dp),
                color = DublGold.copy(alpha = 0.035f),
                border = BorderStroke(1.dp, DublGold.copy(alpha = 0.20f)),
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("План развития", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text(
                            if (planned) "Цель уже добавлена в план" else "Сохранить цель без покупки",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    FilterChip(
                        selected = planned,
                        onClick = onTogglePlanned,
                        label = { Text(if (planned) "★ В плане" else "☆ Добавить") },
                    )
                }
            }

            OutlinedButton(
                onClick = { advancedExpanded = !advancedExpanded },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (advancedExpanded) "Дополнительно ▲" else "Дополнительно ▼") }

            if (advancedExpanded) {
                Text(
                    "Ручное управление, локальные правки и принудительное добавление",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = { onIncrease(optionIndex) },
                    enabled = availability.canIncrease || availability.canForceIncrease,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        when {
                            availability.canIncrease && entry.isAbility -> "Открыть вручную · ${availability.abilityCost} ОС"
                            availability.canIncrease && currentRank > 0 -> "+ ранг вручную · ${entry.cost} XP"
                            availability.canIncrease -> "Получить вручную · ${entry.cost} XP"
                            availability.canForceIncrease && entry.isAbility -> "Открыть всё равно"
                            availability.canForceIncrease -> "Добавить всё равно"
                            else -> availability.reason
                        }
                    )
                }
                OutlinedButton(onClick = onEditLocal, modifier = Modifier.fillMaxWidth()) {
                    Text("Локальная правка")
                }
                when {
                    isCustom -> TextButton(onClick = onDeleteCustom, modifier = Modifier.fillMaxWidth()) { Text("Удалить свою запись") }
                    hasLocalOverride -> TextButton(onClick = onResetLocal, modifier = Modifier.fillMaxWidth()) { Text("Сбросить к рулбуку") }
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

private fun developmentAcquisitionStepText(step: DevelopmentAcquisitionStep): String = when (step) {
    is DevelopmentAcquisitionStep.Attribute -> "${step.label}: ${step.fromValue} → ${step.toValue} · ${step.xpCost} XP"
    is DevelopmentAcquisitionStep.Skill -> "${step.label}: ${step.fromRank} → ${step.toRank} · ${step.xpCost} XP"
    is DevelopmentAcquisitionStep.ManaRank -> "${step.label}: ${step.fromRank} → ${step.toRank} · ${step.xpCost} XP"
    is DevelopmentAcquisitionStep.Development -> "${step.label}: ${step.fromRank} → ${step.toRank}" +
        when {
            step.abilityCost > 0 -> " · ${step.abilityCost} ОС"
            step.xpCost > 0 -> " · ${step.xpCost} XP"
            else -> ""
        }
}

@Composable
private fun DevelopmentAcquisitionPreviewDialog(
    character: com.furybook.dubl.model.DublCharacter,
    catalog: DevelopmentCatalog,
    request: DevelopmentAcquisitionRequest,
    onApply: (DevelopmentAcquisitionRequest) -> Unit,
    onDismiss: () -> Unit,
) {
    var choiceSelections by remember(request) { mutableStateOf(request.choiceSelections) }
    val resolvedRequest = request.copy(choiceSelections = choiceSelections)
    val plan = remember(character, catalog, resolvedRequest) {
        DevelopmentAcquisitionPlanner(character, catalog).plan(resolvedRequest)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("План развития") },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                plan.choices.forEach { choice: DevelopmentAcquisitionChoice ->
                    Text("Выберите путь: ${choice.label}", fontWeight = FontWeight.SemiBold)
                    choice.options.forEachIndexed { index, option ->
                        OutlinedButton(
                            onClick = { choiceSelections = choiceSelections + (choice.id to index) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                "${if (choice.selectedIndex == index) "✓ " else ""}${option.label} · ${option.xpCost} XP" +
                                    if (option.abilityCost > 0) " · ${option.abilityCost} ОС" else "",
                            )
                        }
                    }
                }

                if (plan.steps.isEmpty()) {
                    Text("Все выбранные требования уже выполнены.")
                } else {
                    Text("Будет получено", fontWeight = FontWeight.Bold)
                    plan.steps.forEach { step ->
                        Text("• ${developmentAcquisitionStepText(step)}", style = MaterialTheme.typography.bodySmall)
                    }
                }

                if (plan.unresolvedRequirements.isNotEmpty()) {
                    Text("Нельзя определить автоматически", fontWeight = FontWeight.Bold, color = DublDanger)
                    plan.unresolvedRequirements.forEach { requirement ->
                        Text("• $requirement", style = MaterialTheme.typography.bodySmall, color = DublDanger)
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                Text(
                    "Итого: ${plan.xpCost} XP${if (plan.abilityCost > 0) " · ${plan.abilityCost} ОС" else ""}",
                    fontWeight = FontWeight.Bold,
                    color = DublGold,
                )
                Text(
                    "После покупки: ${plan.xpRemainingAfter} XP · ${plan.abilityRemainingAfter} ОС",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!plan.canAfford) {
                    Text("Недостаточно XP или очков способностей.", color = DublDanger)
                }
            }
        },
        confirmButton = {
            Button(
                enabled = plan.canApply,
                onClick = { onApply(resolvedRequest) },
            ) {
                Text("Применить · ${plan.xpCost} XP${if (plan.abilityCost > 0) " + ${plan.abilityCost} ОС" else ""}")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

private fun emptyCustomDevelopmentEntry(): DevelopmentEntry = DevelopmentEntry(
    id = "",
    name = "",
    section = "Свои",
    category = "Домашние правила",
    cost = 0,
    costType = DevelopmentCostType.XP,
    maxRank = 1,
    requirements = "",
    benefit = "",
    notes = "",
    tags = emptyList(),
    accessId = null,
    abilityOptions = emptyList(),
    incomplete = false,
    repeatable = false,
    perfectRoot = false,
    mechanicsConflict = "",
    conflictNote = "",
)

@Composable
private fun DevelopmentLocalEditDialog(
    initial: DevelopmentEntry,
    title: String,
    onSave: (DevelopmentEntry) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember(initial.id) { mutableStateOf(initial.name) }
    var section by remember(initial.id) { mutableStateOf(initial.section) }
    var category by remember(initial.id) { mutableStateOf(initial.category) }
    var cost by remember(initial.id) { mutableStateOf(initial.cost.toString()) }
    var maxRank by remember(initial.id) { mutableStateOf(initial.maxRank.toString()) }
    var requirements by remember(initial.id) { mutableStateOf(initial.requirements) }
    var benefit by remember(initial.id) { mutableStateOf(initial.benefit) }
    var notes by remember(initial.id) { mutableStateOf(initial.notes) }
    var tags by remember(initial.id) { mutableStateOf(initial.tags.joinToString(", ")) }
    var accessId by remember(initial.id) { mutableStateOf(initial.accessId.orEmpty()) }
    var abilityOptions by remember(initial.id) { mutableStateOf(initial.abilityOptions.joinToString("; ") { "${it.source}=${it.value}" }) }
    var mechanicsConflict by remember(initial.id) { mutableStateOf(initial.mechanicsConflict) }
    var conflictNote by remember(initial.id) { mutableStateOf(initial.conflictNote) }
    var costType by remember(initial.id) { mutableStateOf(initial.costType) }
    var incomplete by remember(initial.id) { mutableStateOf(initial.incomplete) }
    var repeatable by remember(initial.id) { mutableStateOf(initial.repeatable) }
    var perfectRoot by remember(initial.id) { mutableStateOf(initial.perfectRoot) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 620.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(name, { name = it }, label = { Text("Название") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(section, { section = it }, label = { Text("Раздел") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(category, { category = it }, label = { Text("Категория") }, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = costType == DevelopmentCostType.XP, onClick = { costType = DevelopmentCostType.XP }, label = { Text("XP") })
                    FilterChip(selected = costType == DevelopmentCostType.ABILITY, onClick = { costType = DevelopmentCostType.ABILITY }, label = { Text("ОС") })
                }
                OutlinedTextField(cost, { cost = it.filter(Char::isDigit) }, label = { Text("Стоимость") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(maxRank, { maxRank = it.filter(Char::isDigit) }, label = { Text("Макс. ранг") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(requirements, { requirements = it }, label = { Text("Требования") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(benefit, { benefit = it }, label = { Text("Эффект") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(notes, { notes = it }, label = { Text("Особое / заметки") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(tags, { tags = it }, label = { Text("Теги через запятую") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(accessId, { accessId = it }, label = { Text("ID родительской ветки") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(abilityOptions, { abilityOptions = it }, label = { Text("Варианты ОС: источник=цена; …") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(mechanicsConflict, { mechanicsConflict = it }, label = { Text("Механический конфликт") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(conflictNote, { conflictNote = it }, label = { Text("Комментарий конфликта") }, modifier = Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Правило неполное / спорное")
                    DublSwitch(checked = incomplete, onCheckedChange = { incomplete = it })
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Повторяемая запись")
                    DublSwitch(checked = repeatable, onCheckedChange = { repeatable = it })
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Корень совершенной ветки")
                    DublSwitch(checked = perfectRoot, onCheckedChange = { perfectRoot = it })
                }
            }
        },
        confirmButton = {
            Button(
                enabled = name.isNotBlank(),
                onClick = {
                    onSave(
                        initial.copy(
                            name = name,
                            section = section,
                            category = category,
                            cost = cost.toIntOrNull() ?: 0,
                            costType = costType,
                            maxRank = maxRank.toIntOrNull()?.coerceAtLeast(1) ?: 1,
                            requirements = requirements,
                            benefit = benefit,
                            notes = notes,
                            tags = tags.split(',').map(String::trim).filter(String::isNotBlank),
                            accessId = accessId.trim().takeIf(String::isNotBlank),
                            abilityOptions = abilityOptions.split(';').mapNotNull { raw ->
                                val parts = raw.split('=', limit = 2)
                                val source = parts.getOrNull(0)?.trim().orEmpty()
                                val value = parts.getOrNull(1)?.trim()?.toIntOrNull()
                                if (source.isBlank() || value == null) null else AbilityOption(source, value)
                            },
                            incomplete = incomplete,
                            repeatable = repeatable,
                            perfectRoot = perfectRoot,
                            mechanicsConflict = mechanicsConflict,
                            conflictNote = conflictNote,
                        ),
                    )
                },
            ) { Text("Сохранить локально") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

@Composable
private fun RequirementRow(
    check: RequirementCheck,
    onOpenEntry: (String) -> Unit,
) {
    val color = when (check.status) {
        RequirementStatus.OK -> Color(0xFF71A492)
        RequirementStatus.FAIL -> DublDanger
        RequirementStatus.MANUAL -> DublGold
    }
    val prefix = when (check.status) {
        RequirementStatus.OK -> "✓"
        RequirementStatus.FAIL -> "✕"
        RequirementStatus.MANUAL -> "?"
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = check.targetEntryId != null) {
                check.targetEntryId?.let(onOpenEntry)
            },
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.055f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.22f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(prefix, color = color, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(8.dp))
            Text(
                check.text,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
            )
            if (check.targetEntryId != null) {
                Text("›", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun DetailBlock(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(body, style = MaterialTheme.typography.bodyMedium)
    }
}


private fun fcpDevelopmentIcon(token: String?): String? = when (token) {
    FcpUiIconToken.CHI -> "◎"
    else -> null
}
