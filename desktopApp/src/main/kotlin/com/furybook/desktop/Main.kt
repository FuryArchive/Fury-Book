package com.furybook.desktop

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.testTag
import kotlinx.coroutines.launch
import com.furybook.desktop.screens.DesktopAcquisitionRoot
import com.furybook.desktop.screens.LocalDesktopAcquisition
import com.furybook.desktop.screens.AcquisitionBalanceCard
import com.furybook.desktop.screens.acquisitionAmount
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.furybook.ui.layout.DublLayoutClass
import com.furybook.ui.layout.layoutClassForWidth
import com.furybook.ui.theme.DublTheme
import com.furybook.desktop.screens.CharacterSheetScreen
import com.furybook.desktop.screens.CampaignScreen
import com.furybook.desktop.screens.CharactersScreen
import com.furybook.desktop.screens.DevelopmentScreen
import com.furybook.desktop.screens.DesktopIcon
import com.furybook.desktop.screens.DesktopIconKind
import com.furybook.desktop.screens.DesktopAccent
import com.furybook.desktop.screens.DesktopAccentSoft
import com.furybook.desktop.screens.DesktopBackground
import com.furybook.desktop.screens.DesktopBorder
import com.furybook.desktop.screens.DesktopGold
import com.furybook.desktop.screens.DesktopMuted
import com.furybook.desktop.screens.DesktopSurface
import com.furybook.desktop.screens.DesktopSurfaceInset
import com.furybook.desktop.screens.DesktopSurfaceRaised
import com.furybook.desktop.screens.DesktopText
import com.furybook.desktop.screens.EquipmentScreen
import com.furybook.desktop.screens.FuryMotion
import com.furybook.desktop.screens.MagicScreen
import com.furybook.desktop.screens.SkillsScreen
import com.furybook.desktop.cloud.DesktopCloudController
import com.furybook.core.cloud.ObservingCharacterStore
import com.furybook.core.cloud.ObservingCharacterExtrasStore
import com.furybook.dubl.acquisition.*
import com.furybook.dubl.data.DesktopAcquisitionStore
import com.furybook.dubl.data.DesktopCharacterStore
import com.furybook.dubl.data.DesktopCharacterExtrasStore

internal enum class DesktopSection(val label: String) {
    SHEET("Лист"),
    SKILLS("Умения"),
    DEVELOPMENT("Навыки"),
    MAGIC("Магия"),
    EQUIPMENT("Снаряжение"),
    CAMPAIGN("Кампания"),
    CHARACTERS("Персонажи"),
}

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Fury Book — DUBL 3.69",
        icon = painterResource("fury-icon.png"),
    ) {
        DublTheme { DesktopVisualTheme { DesktopApp() } }
    }
}

@Composable
internal fun DesktopVisualTheme(content: @Composable () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    MaterialTheme(
        colorScheme = colors.copy(
            primary = DesktopAccent,
            onPrimary = Color.White,
            primaryContainer = DesktopAccentSoft,
            onPrimaryContainer = DesktopText,
            secondary = DesktopGold,
            background = DesktopBackground,
            onBackground = DesktopText,
            surface = DesktopSurface,
            onSurface = DesktopText,
            surfaceVariant = DesktopSurfaceRaised,
            onSurfaceVariant = DesktopMuted,
            outline = DesktopBorder,
            outlineVariant = DesktopBorder,
        ),
        typography = typography.copy(
            headlineMedium = typography.headlineMedium.copy(fontSize = 30.sp, lineHeight = 36.sp),
            headlineSmall = typography.headlineSmall.copy(fontSize = 28.sp, lineHeight = 34.sp),
            titleLarge = typography.titleLarge.copy(fontSize = 20.sp, lineHeight = 25.sp),
            titleMedium = typography.titleMedium.copy(fontSize = 17.sp, lineHeight = 22.sp),
            bodyLarge = typography.bodyLarge.copy(fontSize = 16.sp, lineHeight = 22.sp),
            bodyMedium = typography.bodyMedium.copy(fontSize = 15.sp, lineHeight = 20.sp),
            bodySmall = typography.bodySmall.copy(fontSize = 14.sp, lineHeight = 18.sp),
            labelLarge = typography.labelLarge.copy(fontSize = 14.sp, lineHeight = 18.sp),
            labelMedium = typography.labelMedium.copy(fontSize = 14.sp, lineHeight = 18.sp),
        ),
        content = content,
    )
}

@Composable
private fun DesktopApp() {
    val preferences = remember { java.util.prefs.Preferences.userRoot().node("com/furybook/runtime") }
    val localRulesEnabled = remember { preferences.getBoolean("localRules", java.lang.Boolean.getBoolean("furybook.gameplay.local")) }
    var pendingLocalRules by remember { mutableStateOf<Boolean?>(null) }
    val localAcquisitionEnabled = remember { localRulesEnabled || java.lang.Boolean.getBoolean("furybook.acquisition.local") }
    val baseCharacters = remember { DesktopCharacterStore() }
    val baseExtras = remember { DesktopCharacterExtrasStore() }
    val observedCharacters = remember(baseCharacters) { ObservingCharacterStore(baseCharacters) }
    val observedExtras = remember(baseExtras) { ObservingCharacterExtrasStore(baseExtras) }
    val acquisitionStore = remember { DesktopAcquisitionStore(gameplayFormatEnabled = true) }
    val legacyCloudController = remember(baseCharacters, baseExtras, localAcquisitionEnabled, acquisitionStore) {
        legacyCloudForStorage(acquisitionStore, localAcquisitionEnabled) {
            DesktopCloudController(baseCharacters, baseExtras).also { it.bind(observedCharacters, observedExtras) }
        }
    }
    val reloadToken = legacyCloudController?.reloadToken ?: 0
    val startup = remember(reloadToken, localAcquisitionEnabled) {
        runCatching { DesktopAppState(observedCharacters, observedExtras,
            localAcquisitionEnabled = localAcquisitionEnabled, acquisitionStore = acquisitionStore,
            experimentalGameplayEnabled = localRulesEnabled) }
    }
    val state = startup.getOrNull()
    DisposableEffect(state) { onDispose { state?.dispose() } }
    Column(Modifier.fillMaxSize()) {
    com.furybook.ui.components.LocalRulesModeControl(localRulesEnabled, pendingLocalRules) { enabled ->
        preferences.putBoolean("localRules", enabled); preferences.flush(); pendingLocalRules = enabled
    }
    Box(Modifier.weight(1f).fillMaxWidth()) {
    if (state == null) {
        Column(Modifier.fillMaxSize().padding(24.dp)) {
            Text("Не удалось открыть данные", style = MaterialTheme.typography.headlineSmall)
            Text(startup.exceptionOrNull()?.message ?: "Хранилище недоступно")
            Text("Исходные файлы сохранены. Запись заблокирована; проверьте резервную копию и формат.")
        }
    } else DesktopAppContent(state, legacyCloudController)
    }
    }
}

/** Exact production content shared by the native Window and the supported offscreen host. */
@Composable
internal fun DesktopAppContent(state: DesktopAppState, legacyCloudController: DesktopCloudController? = null) {
    if (state.canonicalMode) {
        LaunchedEffect(state) { if (!state.canonicalViewReady) runCatching { state.initializeCanonicalView() } }
        LaunchedEffect(state, state.contentRevision) { runCatching { state.refreshCanonicalContent() } }
        if (!state.canonicalViewReady) { LocalAcquisitionStoragePanel(state); return }
        DesktopAcquisitionRoot(state) {
            if (state.experimentalGameplayEnabled && (state.localStoredCharacter() as? LoadResult.Loaded)?.state?.formatVersion == 1) {
                val captured = state.gameplayScope()
                com.furybook.ui.components.LocalRulesPreparationPanel(captured,
                    checkNotNull(state.gameplayMigration), state.exportActiveCharacter()) { state.refreshAfterGameplay(captured) }
            } else DesktopNavigationContent(state, null)
        }
        return // Protected heads, including default-false read-only, never bind a legacy cloud writer.
    }
    val cloudController = requireNotNull(legacyCloudController)

    LaunchedEffect(cloudController) {
        cloudController.updateEnabledPackIds(state.enabledContentPackIds())
        cloudController.restoreAndSync()
        state.applyCloudContentPackState(cloudController.enabledPackIds)
    }
    LaunchedEffect(state.chiPackEnabled) {
        val availableIds = state.contentPackComposition.available.mapTo(linkedSetOf()) { it.id }
        val activeIds = state.enabledContentPackIds()
        cloudController.updateEnabledPackIds((cloudController.enabledPackIds - availableIds) + activeIds)
    }

    DesktopNavigationContent(state, cloudController)
}

@Composable
private fun DesktopNavigationContent(state: DesktopAppState, cloudController: DesktopCloudController?) {
    var selected by remember(state) { mutableStateOf(if (state.unfundedPlaceholder) DesktopSection.CHARACTERS else DesktopSection.SHEET) }
    Column(Modifier.fillMaxSize()) {
        state.storageError?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(12.dp)) }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().background(MaterialTheme.colorScheme.background)) {
            val layout = layoutClassForWidth(maxWidth.value.toInt())
            if (layout == DublLayoutClass.COMPACT) {
                Column(Modifier.fillMaxSize()) {
                    CompactNavigation(selected, { selected = it })
                    DesktopContent(state, cloudController, selected, layout, { selected = it }, Modifier.weight(1f))
                }
            } else {
                Row(Modifier.fillMaxSize()) {
                    DesktopRail(state, selected, { selected = it }, Modifier.width(230.dp).fillMaxHeight())
                    DesktopContent(state, cloudController, selected, layout, { selected = it }, Modifier.weight(1f))
                }
            }
        }
    }
}

/** T12 storage-only opt-in. Legacy editors/cloud controls stay unavailable until canonical UI lands. */
@Composable
private fun LocalAcquisitionStoragePanel(state: DesktopAppState) {
    val scope = rememberCoroutineScope()
    var packs by remember { mutableStateOf("dubl-3.69") }
    var xp by remember { mutableStateOf("") }
    var ap by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }
    val loaded = runCatching { state.localStoredCharacter() }.getOrNull()
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Локальный журнал: ${state.activeCharacter.name}", style = MaterialTheme.typography.headlineSmall)
        Text("Изолированная локальная копия. Облачная запись этого формата выключена.")
        when (loaded) {
            is LoadResult.Loaded -> {
                val amount = balance(loaded.state.ledger)
                Text("Сохранено на устройстве: ${amount.xp} XP / ${amount.ap} ОСп")
                Text("История и очередь сохранены. Загрузка канонического просмотра.")
            }
            is LoadResult.MigrationRequired -> {
                Text("Укажите известный состав исходного листа. Старые покупки не переоцениваются новым каталогом.")
                OutlinedTextField(packs, {
                    packs = it
                    state.declareLegacyComposition(it.split(',').map(String::trim).filter(String::isNotBlank))
                    localError = null
                }, enabled = state.localAcquisitionEnabled, label = { Text("Пакеты исходного листа, через запятую") })
                Button(enabled = state.localAcquisitionEnabled, onClick = {
                    localError = runCatching { state.prepareLocalMigration(packs.split(',').map(String::trim).filter(String::isNotBlank)) }
                        .exceptionOrNull()?.message
                }) { Text("Подготовить миграцию") }
                state.preparedLegacyMigration?.let { prepared ->
                    Text("Подготовленный состав: ${prepared.ledger.opening.legacyMigration?.origin?.activePackIds?.joinToString()}")
                    val opening = balance(prepared.ledger)
                    Text("Остаток по сохранённым старым правилам: ${opening.xp} XP / ${opening.ap} ОСп")
                    Button(enabled = state.localAcquisitionEnabled, onClick = {
                        scope.launch { localError = runCatching { state.commitPreparedLocalMigrationAsync() }.fold({ result -> if (result == CommitResult.Committed) null else "Перенос не подтверждён: $result" }, { it.message }) }
                    }) { Text("Подтверждаю исходные пакеты и перенос") }
                }
                if (state.pendingLegacyMigration != null) {
                    Text("Подготовленный состав: ${state.pendingLegacyMigration!!.origin.activePackIds.joinToString()}")
                    OutlinedTextField(xp, { xp = it }, label = { Text("Известный остаток XP") })
                    OutlinedTextField(ap, { ap = it }, label = { Text("Известный остаток ОСп") })
                    OutlinedTextField(reason, { reason = it }, label = { Text("Основание подтверждения") })
                    Button(enabled = state.localAcquisitionEnabled && xp.toLongOrNull() != null && ap.toLongOrNull() != null && reason.isNotBlank(), onClick = {
                        scope.launch { localError = runCatching { state.confirmLocalOpeningBalanceAsync(xp.toLong(), ap.toLong(), reason) }.fold({ result -> if (result == CommitResult.Committed) null else "Перенос не подтверждён: $result" }, { it.message }) }
                    }) { Text("Подтверждаю остаток и исходные пакеты") }
                }
            }
            is LoadResult.LegacyImportConflict -> Text(loaded.reason)
            is LoadResult.Unsupported -> Text("Формат не поддерживается: ${loaded.reason}")
            is LoadResult.Corrupt -> Text("Повреждённые данные: ${loaded.reason}")
            else -> Text("Хранилище недоступно. Запись заблокирована.")
        }
        (localError ?: state.storageError)?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun DesktopRail(
    state: DesktopAppState,
    selected: DesktopSection,
    onSelected: (DesktopSection) -> Unit,
    modifier: Modifier = Modifier,
) {
    var switcherOpen by remember { mutableStateOf(false) }
    Column(
        modifier = modifier.background(DesktopSurfaceInset).padding(horizontal = 14.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            "Fury Book",
            style = MaterialTheme.typography.headlineMedium,
            color = DesktopAccent,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
        Text(
            "DUBL 3.69",
            color = DesktopMuted,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        Surface(
            modifier = Modifier.fillMaxWidth().testTag("character.selector").clickable { switcherOpen = true },
            color = DesktopSurfaceRaised,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.52f)),
        ) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 11.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(state.activeCharacter.name, fontWeight = FontWeight.SemiBold, maxLines = 2)
                Text(if (state.canonicalMode) "Канонический лист · сменить ▼" else "${state.activeCharacter.experience} XP · сменить ▼", color = DesktopMuted, style = MaterialTheme.typography.bodySmall)
            }
        }
        DropdownMenu(expanded = switcherOpen, onDismissRequest = { switcherOpen = false }) {
            state.snapshot.characters.forEach { character ->
                DropdownMenuItem(
                    text = { Text(if (character.id == state.snapshot.activeCharacterId) "✓ ${character.name}" else character.name) },
                    modifier = Modifier.testTag("character.${character.id}"),
                    onClick = {
                        state.selectCharacter(character.id)
                        switcherOpen = false
                        onSelected(DesktopSection.SHEET)
                    },
                )
            }
            DropdownMenuItem(
                text = { Text("Управление персонажами…") },
                onClick = { switcherOpen = false; onSelected(DesktopSection.CHARACTERS) },
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
        DesktopSection.entries.filter { it != DesktopSection.CHARACTERS }.forEach { section ->
            NavigationItem(section, section == selected, { onSelected(section) }, Modifier.fillMaxWidth())
        }
        Spacer(Modifier.weight(1f))
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
        NavigationItem(DesktopSection.CHARACTERS,
            DesktopSection.CHARACTERS == selected,
            { onSelected(DesktopSection.CHARACTERS) },
            Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun CompactNavigation(selected: DesktopSection, onSelected: (DesktopSection) -> Unit) {
    Column(Modifier.fillMaxWidth().background(DesktopSurfaceInset).padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        DesktopSection.entries.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { section -> NavigationItem(section, section == selected, { onSelected(section) }, Modifier.weight(1f)) }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun NavigationItem(section: DesktopSection, active: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val background by animateColorAsState(
        targetValue = if (active) DesktopAccentSoft else Color.Transparent,
        animationSpec = tween(FuryMotion.FastMs),
        label = "navigation-background",
    )
    val iconColor by animateColorAsState(
        targetValue = if (active) DesktopAccent else DesktopMuted,
        animationSpec = tween(FuryMotion.FastMs),
        label = "navigation-icon",
    )
    val textColor by animateColorAsState(
        targetValue = if (active) MaterialTheme.colorScheme.onSurface else DesktopMuted,
        animationSpec = tween(FuryMotion.FastMs),
        label = "navigation-text",
    )
    Surface(
        modifier = modifier.testTag("navigation.${section.name}").clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color = background,
        border = if (active) BorderStroke(1.dp, DesktopAccent.copy(alpha = 0.32f)) else null,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DesktopIcon(
                kind = section.iconKind,
                tint = iconColor,
                size = 20.dp,
            )
            Text(
                section.label,
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                color = textColor,
            )
        }
    }
}

private val DesktopSection.iconKind: DesktopIconKind
    get() = when (this) {
        DesktopSection.SHEET -> DesktopIconKind.SHEET
        DesktopSection.SKILLS -> DesktopIconKind.SKILLS
        DesktopSection.DEVELOPMENT -> DesktopIconKind.DEVELOPMENT
        DesktopSection.MAGIC -> DesktopIconKind.MAGIC
        DesktopSection.EQUIPMENT -> DesktopIconKind.EQUIPMENT
        DesktopSection.CAMPAIGN -> DesktopIconKind.CHARACTERS
        DesktopSection.CHARACTERS -> DesktopIconKind.CHARACTERS
    }

@Composable
private fun DesktopContent(
    state: DesktopAppState,
    cloudController: DesktopCloudController?,
    section: DesktopSection,
    layout: DublLayoutClass,
    onNavigate: (DesktopSection) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        val pageModifier = Modifier
            .widthIn(max = 2160.dp)
            .fillMaxWidth()
            .fillMaxHeight()
            .padding(
                horizontal = when (layout) {
                    DublLayoutClass.COMPACT -> 16.dp
                    DublLayoutClass.NORMAL -> 24.dp
                    DublLayoutClass.WIDE -> 28.dp
                },
                vertical = if (layout == DublLayoutClass.COMPACT) 14.dp else 22.dp,
            )
        key(section) {
            var entered by remember(section) { mutableStateOf(false) }
            LaunchedEffect(section) { entered = true }
            val pageProgress by animateFloatAsState(
                targetValue = if (entered) 1f else 0f,
                animationSpec = tween(FuryMotion.FastMs),
                label = "desktop-section-enter",
            )
            val animatedPageModifier = pageModifier.graphicsLayer {
                alpha = pageProgress
                translationY = (1f - pageProgress) * 6f
            }
            if (state.canonicalMode && section != DesktopSection.CHARACTERS && state.localStoredCharacter() !is LoadResult.Loaded) {
                LocalAcquisitionStoragePanel(state)
            } else when (section) {
                DesktopSection.SHEET -> CharacterSheetScreen(
                    state = state,
                    modifier = animatedPageModifier,
                    onNavigateSkills = { onNavigate(DesktopSection.SKILLS) },
                    onNavigateDevelopment = { onNavigate(DesktopSection.DEVELOPMENT) },
                    onNavigateMagic = { onNavigate(DesktopSection.MAGIC) },
                    onNavigateEquipment = { onNavigate(DesktopSection.EQUIPMENT) },
                )
                DesktopSection.SKILLS -> SkillsScreen(state, animatedPageModifier)
                DesktopSection.DEVELOPMENT -> DevelopmentScreen(state, animatedPageModifier)
                DesktopSection.MAGIC -> MagicScreen(state, animatedPageModifier)
                DesktopSection.EQUIPMENT -> EquipmentScreen(state, animatedPageModifier)
                DesktopSection.CAMPAIGN -> if (cloudController != null) CampaignScreen(state, cloudController, animatedPageModifier) else Text("Облачная кампания недоступна для канонического локального журнала.")
                DesktopSection.CHARACTERS -> if (cloudController != null) CharactersScreen(state, cloudController, animatedPageModifier) else CanonicalRoster(state, animatedPageModifier)
            }
        }
    }
}

@Composable
private fun CanonicalRoster(state: DesktopAppState, modifier: Modifier = Modifier) {
    val actions=LocalDesktopAcquisition.current ?: return
    var exportStatus by remember { mutableStateOf<String?>(null) }
    val scope=rememberCoroutineScope()
    Column(modifier, verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text("Персонажи", style=MaterialTheme.typography.headlineSmall)
        Text("Каноническое локальное хранилище. Облачная запись выключена.")
        if(state.readOnlyCanonicalMode) Text("Только чтение: просмотр и существующий экспорт доступны, новые операции выключены.")
        state.snapshot.characters.filterNot { state.unfundedPlaceholder }.forEach { character ->
            Button(onClick={actions.hideForSelection();state.selectCharacter(character.id)},modifier=Modifier.testTag("character.${character.id}")) { Text(character.name) }
        }
        Button(onClick=actions::newCharacter,modifier=Modifier.testTag("acquisition.funding.open")) { Text("Создать с явным начальным балансом") }
        if(state.localStoredCharacter() !is LoadResult.Loaded && !state.unfundedPlaceholder) LocalAcquisitionStoragePanel(state)
        if(state.localStoredCharacter() is LoadResult.Loaded) {
            Button(onClick={
                val captured=state.exportActiveCharacter()
                val dialog=java.awt.FileDialog(null as java.awt.Frame?,"Экспорт персонажа DUBL",java.awt.FileDialog.SAVE)
                dialog.file="character.dubl";dialog.isVisible=true
                val name=dialog.file
                val directory=dialog.directory
                dialog.dispose()
                if(name!=null && directory!=null) scope.launch {
                    exportStatus=try {
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            val target=java.nio.file.Path.of(directory,if(name.endsWith(".dubl",true)) name else "$name.dubl")
                            java.nio.file.Files.writeString(target,captured,java.nio.charset.StandardCharsets.UTF_8)
                        }
                        "Канонический персонаж экспортирован."
                    } catch(_:Exception) { "Не удалось экспортировать персонажа." }
                }
            },modifier=Modifier.testTag("acquisition.export")) { Text("Экспорт") }
            exportStatus?.let { Text(it) }
            AcquisitionBalanceCard()
        }
    }
}
