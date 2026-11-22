package com.furybook.dubl.ui.characters

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.furybook.dubl.ui.sheet.SheetPortrait
import com.furybook.dubl.ui.sheet.SheetPortraitLoader
import com.furybook.ui.account.FuryAccount
import com.furybook.ui.components.FuryButton
import com.furybook.ui.components.FuryButtonStyle
import com.furybook.ui.components.FuryChipGroup
import com.furybook.ui.components.FuryChoice
import com.furybook.ui.components.FuryDialogAction
import com.furybook.ui.components.FuryDialogScaffold
import com.furybook.ui.components.FuryToastHost
import com.furybook.ui.components.FuryToastState
import com.furybook.ui.theme.DublMuted
import com.furybook.ui.theme.DublText
import com.furybook.ui.theme.FuryColors
import com.furybook.ui.theme.FuryShapes
import com.furybook.ui.theme.FurySpacing
import com.furybook.ui.theme.furyOverlineStyle

data class DublPackRow(val id: String, val name: String, val note: String, val enabled: Boolean, val locked: Boolean)

/** «Пакеты контента»: the platform's .fcp list, its toggle and import, and the last result line. */
class DublPacksSection(
    val rows: List<DublPackRow>,
    val onToggle: (String, Boolean) -> Unit,
    val onImport: (() -> Unit)?,
    val status: String? = null,
)

/** Characters per row: 3 on wide screens, 2 on tablets, 1 on phones. */
fun charactersGridColumns(widthDp: Int): Int = when {
    widthDp >= 1000 -> 3
    widthDp >= 600 -> 2
    else -> 1
}

/**
 * Персонажи: the roster and «Архив», per-character actions, campaigns for a signed-in player, and content packs.
 * [onImportFile] picks a file and hands back its text (null when it can't be read); [onExportFile] saves a file
 * and reports whether it worked. Neither calls back when the player cancels.
 */
@Composable
fun DublCharactersScreen(
    host: DublCharactersHost,
    account: FuryAccount,
    packs: DublPacksSection?,
    onOpen: () -> Unit,
    onNew: () -> Unit,
    onImportFile: (onRead: (String?) -> Unit) -> Unit,
    onExportFile: (fileName: String, content: String, onSaved: (Boolean) -> Unit) -> Unit,
    modifier: Modifier = Modifier,
    portraitLoader: SheetPortraitLoader? = null,
) {
    val toast = remember { FuryToastState() }
    var showArchive by remember { mutableStateOf(false) }
    // The archive store is not observable; bump this after changing it.
    var archiveTick by remember { mutableStateOf(0) }
    var deleting by remember { mutableStateOf<CharacterCard?>(null) }
    var joining by remember { mutableStateOf<CharacterCard?>(null) }
    val signedIn = account.signedIn
    LaunchedEffect(signedIn) { if (signedIn) account.refreshCampaigns() }

    @Suppress("UNUSED_EXPRESSION") archiveTick
    val campaigns = if (signedIn) account.campaigns else emptyList()
    val active = characterCards(host, archived = false, campaigns = campaigns)
    val archived = characterCards(host, archived = true, campaigns = campaigns)
    val cards = if (showArchive) archived else active
    val nameOf: (String) -> String? = { id -> host.characters.firstOrNull { it.id == id }?.name }

    fun export(card: CharacterCard) {
        onExportFile(exportFileName(card.name), host.exportCharacter(card.id)) { saved ->
            toast.show(if (saved) "Персонаж экспортирован: ${card.name}" else "Не удалось сохранить файл персонажа.")
        }
    }

    fun actions(card: CharacterCard): List<Pair<String, () -> Unit>> = if (card.archived) listOf(
        "Вернуть из архива" to { unarchiveCharacter(host, card.id); archiveTick++; toast.show("${card.name} снова в списке") },
        "Экспорт" to { export(card) },
        "Удалить" to { deleting = card },
    ) else buildList {
        add("Экспорт" to { export(card) })
        add("Дублировать" to {
            host.duplicate(card.id)?.let { copyId -> toast.show("Создана копия: ${nameOf(copyId) ?: card.name}") }
            Unit
        })
        add((if (card.campaign != null) "Кампания…" else "Вступить по коду") to {
            if (signedIn) joining = card else toast.show("Кампании доступны после входа в аккаунт")
        })
        add("В архив" to {
            val wasActive = card.active
            archiveCharacter(host, card.id); archiveTick++
            toast.show("${card.name} в архиве", onUndo = { unarchiveCharacter(host, card.id); if (wasActive) host.select(card.id); archiveTick++ })
        })
        if (host.characters.size > 1) add("Удалить" to { deleting = card })
    }

    Box(modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val columns = charactersGridColumns(maxWidth.value.toInt())
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(FurySpacing.lg).padding(bottom = 72.dp),
                verticalArrangement = Arrangement.spacedBy(FurySpacing.lg),
            ) {
                Header(onNew = onNew, onImport = {
                    onImportFile { raw ->
                        toast.show(raw?.let { importResultMessage(host.importCharacter(it)) } ?: "Не удалось прочитать файл персонажа.")
                    }
                })
                FuryChipGroup(
                    listOf(FuryChoice(false, "Мои · ${active.size}"), FuryChoice(true, "Архив · ${archived.size}")),
                    showArchive, { showArchive = it }, Modifier.widthIn(max = 360.dp), columns = 2,
                )
                if (cards.isEmpty()) {
                    Text(
                        if (showArchive) "Архив пуст. «В архив» убирает персонажа из списка, не удаляя его."
                        else "Персонажей нет — создайте нового или импортируйте файл.",
                        color = DublMuted, style = MaterialTheme.typography.bodyMedium,
                    )
                }
                cards.chunked(columns).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(FurySpacing.md)) {
                        row.forEach { card ->
                            CharacterCardView(
                                card, host.portraitOf(card.id), portraitLoader, actions(card), Modifier.weight(1f),
                                onOpen = {
                                    if (card.archived) toast.show("Персонаж в архиве — сначала верните его")
                                    else { host.select(card.id); onOpen() }
                                },
                            )
                        }
                        repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
                packs?.let { PacksCard(it) }
            }
        }
        FuryToastHost(toast)
    }

    deleting?.let { card ->
        FuryDialogScaffold(
            title = "Удалить персонажа?",
            subtitle = card.name,
            onDismiss = { deleting = null },
            secondary = FuryDialogAction("Отмена", { deleting = null }),
            destructive = FuryDialogAction("Удалить", {
                deleting = null
                host.delete(card.id)
                if (host.characters.any { it.id == card.id }) {
                    toast.show("Последнего персонажа удалить нельзя")
                } else {
                    if (card.archived) unarchiveCharacter(host, card.id)
                    keepActiveUnarchived(host)
                    archiveTick++
                    toast.show("${card.name} удалён")
                }
            }),
        ) {
            Text(
                "${card.name} будет удалён вместе с настройками листа. Это действие нельзя отменить.",
                color = DublText, style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
    joining?.let { card ->
        JoinCampaignDialog(
            account = account,
            characterId = card.id,
            characterName = card.name,
            nameOf = nameOf,
            onDismiss = { joining = null },
            onDone = { message -> joining = null; toast.show(message) },
        )
    }
}

@Composable
private fun Header(onNew: () -> Unit, onImport: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val title: @Composable (Modifier) -> Unit = { m ->
            Column(m) {
                Text("Персонажи", color = DublText, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("Нажмите на персонажа, чтобы открыть его лист.", color = DublMuted, style = MaterialTheme.typography.bodyMedium)
            }
        }
        val buttons: @Composable () -> Unit = {
            FuryButton("Импорт", onImport)
            FuryButton("+ Новый", onNew, style = FuryButtonStyle.Primary)
        }
        // Phones: the buttons go under the title so «Персонажи» keeps one line.
        if (maxWidth < 520.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(FurySpacing.sm)) {
                title(Modifier)
                Row(horizontalArrangement = Arrangement.spacedBy(FurySpacing.sm)) { buttons() }
            }
        } else {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(FurySpacing.sm)) {
                title(Modifier.weight(1f))
                buttons()
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CharacterCardView(
    card: CharacterCard,
    portrait: String?,
    loader: SheetPortraitLoader?,
    actions: List<Pair<String, () -> Unit>>,
    modifier: Modifier,
    onOpen: () -> Unit,
) {
    val p = FuryColors.current
    Column(
        modifier.alpha(if (card.archived) 0.85f else 1f).background(p.panel, FuryShapes.panel)
            .border(1.dp, if (card.active) p.accLine else p.line2, FuryShapes.panel).padding(FurySpacing.md),
        verticalArrangement = Arrangement.spacedBy(FurySpacing.sm),
    ) {
        Row(
            Modifier.fillMaxWidth().clickable(role = Role.Button, onClickLabel = "Открыть ${card.name}", onClick = onOpen),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(FurySpacing.md),
        ) {
            SheetPortrait(portrait, card.name, loader, onPick = null, onRemove = null, size = 48.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(FurySpacing.sm)) {
                    Text(card.name, Modifier.weight(1f, fill = false), color = DublText, style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (card.active) {
                        Text("АКТИВНЫЙ", Modifier.background(p.accSoft, FuryShapes.control).padding(horizontal = 6.dp, vertical = 2.dp),
                            color = p.accText, style = furyOverlineStyle())
                    }
                }
                Text(card.concept.ifBlank { "Без концепта" }, color = DublMuted, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("XP ${card.xp}", color = DublMuted, style = MaterialTheme.typography.bodySmall)
            }
        }
        card.campaign?.let { name ->
            Text("кампания «$name»", color = DublText, style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.background(p.panel2, FuryShapes.control).padding(horizontal = 8.dp, vertical = 4.dp))
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(FurySpacing.xs), verticalArrangement = Arrangement.spacedBy(FurySpacing.xs)) {
            actions.forEach { (label, action) ->
                FuryButton(label, action, style = if (label == "Удалить") FuryButtonStyle.Danger else FuryButtonStyle.Secondary)
            }
        }
    }
}

@Composable
private fun PacksCard(section: DublPacksSection) {
    val p = FuryColors.current
    Column(
        Modifier.fillMaxWidth().background(p.panel, FuryShapes.panel).border(1.dp, p.line2, FuryShapes.panel).padding(FurySpacing.md),
        verticalArrangement = Arrangement.spacedBy(FurySpacing.sm),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Пакеты контента", Modifier.weight(1f), color = DublText, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            section.onImport?.let { FuryButton("Импортировать .fcp", it, style = FuryButtonStyle.Ghost) }
        }
        Text(
            "Активные FCP определяют не только правила и каталоги, но и подключаемые части интерфейса.",
            color = DublMuted, style = MaterialTheme.typography.bodySmall,
        )
        section.rows.forEach { row ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(FurySpacing.md)) {
                Column(Modifier.weight(1f)) {
                    Text(row.name, color = DublText, style = MaterialTheme.typography.titleSmall)
                    Text(row.note, color = DublMuted, style = MaterialTheme.typography.bodySmall)
                }
                FuryButton(
                    when { row.locked && row.enabled -> "Включён"; row.locked -> "Недоступен"; row.enabled -> "Включён"; else -> "Выключен" },
                    { section.onToggle(row.id, !row.enabled) },
                    style = if (row.enabled) FuryButtonStyle.Primary else FuryButtonStyle.Secondary,
                    enabled = !row.locked,
                )
            }
        }
        section.status?.let { Text(it, color = DublMuted, style = MaterialTheme.typography.bodySmall) }
    }
}
