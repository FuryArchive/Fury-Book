package com.furybook.dubl.ui.master

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.furybook.core.cloud.CampaignDashboard
import com.furybook.core.cloud.CampaignInvite
import com.furybook.dubl.master.PartyRow
import com.furybook.dubl.master.partyRows
import com.furybook.dubl.master.readOnlyView
import com.furybook.ui.account.FuryAccount
import com.furybook.ui.components.FuryButton
import com.furybook.ui.components.FuryButtonStyle
import com.furybook.ui.components.FuryChipGroup
import com.furybook.ui.components.FuryChoice
import com.furybook.ui.components.FuryDialogAction
import com.furybook.ui.components.FuryDialogScaffold
import com.furybook.ui.components.FuryTextField
import com.furybook.ui.theme.DublDanger
import com.furybook.ui.theme.DublMana
import com.furybook.ui.theme.DublMuted
import com.furybook.ui.theme.DublText
import com.furybook.ui.theme.FuryColors
import com.furybook.ui.theme.FuryShapes
import com.furybook.ui.theme.FurySpacing
import kotlinx.coroutines.launch

/**
 * Партия: the only tab that needs the account. The GM's own campaigns with the players' sheets (read-only), invites
 * and «Убрать игрока»; campaigns the GM plays in are listed without GM data.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PartyTab(
    scope: MasterScope,
    account: FuryAccount,
    catalogs: MasterCatalogs,
    dashboard: CampaignDashboard?,
    onDashboard: (CampaignDashboard?) -> Unit,
    onSignIn: () -> Unit,
) {
    if (!account.signedIn) {
        MasterPage {
            MasterCard(Modifier.widthIn(max = 560.dp)) {
                Text("Для партии нужен аккаунт", color = DublText, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "Чтобы видеть листы игроков, войдите: листы приходят из Fury Cloud. Бой, враги, заметки и справочник работают и без входа.",
                    color = DublMuted, style = MaterialTheme.typography.bodyMedium,
                )
                FuryButton("Войти", onSignIn, style = FuryButtonStyle.Primary)
            }
        }
        return
    }
    val state = scope.state
    val coroutines = rememberCoroutineScope()
    var error by remember { mutableStateOf<String?>(null) }
    var invite by remember { mutableStateOf<CampaignInvite?>(null) }
    var creating by remember { mutableStateOf(false) }
    var viewing by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    val owned = account.campaigns.filter { it.isOwner }
    val playing = account.campaigns.filter { !it.isOwner }
    val selectedId = state.partyCampaignId?.takeIf { id -> owned.any { it.id == id } } ?: owned.firstOrNull()?.id

    fun load(id: String?) {
        if (id == null) { onDashboard(null); return }
        loading = true
        coroutines.launch {
            account.dashboard(id).fold({ onDashboard(it); error = null }, { error = it.message ?: "Не удалось загрузить партию." })
            loading = false
        }
    }
    LaunchedEffect(Unit) { account.refreshCampaigns() }
    LaunchedEffect(selectedId) { invite = null; load(selectedId) }

    MasterPage {
        val shown = dashboard?.takeIf { it.id == selectedId }
        MasterHeader(
            "Партия",
            if (shown == null) "Листы игроков из облака, только для чтения."
            else "Листы игроков из облака, только для чтения. Обновлено ${shown.players.maxOfOrNull { it.updatedAt }?.let(::partyTime) ?: "—"}.",
        ) {
            FuryButton("Обновить", { coroutines.launch { account.refreshCampaigns() }; load(selectedId) }, enabled = !loading)
            if (selectedId != null) FuryButton("Пригласить", {
                coroutines.launch { account.createInvite(selectedId).fold({ invite = it; error = null }, { error = it.message ?: "Не удалось создать приглашение." }) }
            })
            FuryButton("+ Кампания", { creating = true })
        }
        error?.let { Text(it, color = DublDanger, style = MaterialTheme.typography.bodyMedium) }
        if (owned.isEmpty()) {
            Text("У вас пока нет своих кампаний. «+ Кампания» создаёт новую, «Пригласить» даёт код для игроков.", color = DublMuted, style = MaterialTheme.typography.bodyMedium)
        } else if (owned.size > 1) {
            FuryChipGroup(owned.map { FuryChoice(it.id, it.name) }, selectedId, { state.partyCampaignId = it }, columns = 3)
        }
        invite?.let { code ->
            val p = FuryColors.current
            Column(
                Modifier.fillMaxWidth().background(p.goldSoft, FuryShapes.panel).border(1.dp, p.goldLine, FuryShapes.panel).padding(FurySpacing.md),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text("КОД ПРИГЛАШЕНИЯ", color = p.goldText, style = com.furybook.ui.theme.furyOverlineStyle())
                Text(code.token, color = DublText, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("Игрок вводит код в «Персонажи» → на своём персонаже → «Вступить по коду».", color = DublMuted, style = MaterialTheme.typography.bodySmall)
            }
        }
        if (shown != null) {
            val rows = partyRows(shown, catalogs.conditions)
            if (rows.isEmpty()) Text("В кампании «${shown.name}» пока нет игроков.", color = DublMuted, style = MaterialTheme.typography.bodyMedium)
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val wide = maxWidth >= 760.dp
                Column(verticalArrangement = Arrangement.spacedBy(FurySpacing.xs)) {
                    rows.forEach { row -> PartyRowView(row, wide) { viewing = row.userId } }
                }
            }
        } else if (loading) {
            Text("Загружаем партию…", color = DublMuted, style = MaterialTheme.typography.bodyMedium)
        }
        if (playing.isNotEmpty()) {
            MasterCard(Modifier.fillMaxWidth()) {
                Text("ВЫ ИГРАЕТЕ В", color = DublMuted, style = com.furybook.ui.theme.furyOverlineStyle())
                playing.forEach { campaign ->
                    // «Выйти» here too: the linked character may be deleted or on another device.
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(FurySpacing.sm)) {
                        Text("${campaign.name} · участников: ${campaign.memberCount}", Modifier.weight(1f), color = DublText, style = MaterialTheme.typography.bodyMedium)
                        FuryButton("Выйти", {
                            coroutines.launch {
                                account.leaveCampaign(campaign.id).fold(
                                    { scope.toast.show("Вы вышли из кампании «${campaign.name}»"); error = null },
                                    { error = it.message ?: "Не удалось выйти из кампании." },
                                )
                            }
                        }, style = FuryButtonStyle.Danger)
                    }
                }
                Text("Своего персонажа в кампанию добавляют и меняют в «Персонажи».", color = DublMuted, style = MaterialTheme.typography.bodySmall)
            }
        }
    }

    if (creating) {
        var name by remember { mutableStateOf("") }
        FuryDialogScaffold(
            title = "Новая кампания",
            onDismiss = { creating = false },
            primary = FuryDialogAction("Создать", {
                creating = false
                coroutines.launch {
                    account.createCampaign(name).fold({ state.partyCampaignId = it.id; error = null }, { error = it.message ?: "Не удалось создать кампанию." })
                }
            }, enabled = name.isNotBlank()),
            secondary = FuryDialogAction("Отмена", { creating = false }),
        ) { FuryTextField(name, { name = it }, "Название") }
    }
    val member = dashboard?.players?.firstOrNull { it.userId == viewing }
    if (member != null && dashboard != null) {
        val view = remember(member, catalogs) { readOnlyView(member, catalogs.development) }
        FuryDialogScaffold(
            title = member.character.name,
            subtitle = "${member.nickname} · только просмотр",
            onDismiss = { viewing = null },
            maxWidth = 960.dp,
            destructive = FuryDialogAction("Убрать игрока", {
                val campaignId = dashboard.id
                viewing = null
                coroutines.launch {
                    account.removeMember(campaignId, member.userId).fold(
                        { scope.toast.show("${member.nickname} больше не в кампании"); load(campaignId) },
                        { error = it.message ?: "Не удалось убрать игрока." },
                    )
                }
            }),
        ) { ReadOnlyCharacterView(view) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PartyRowView(row: PartyRow, wide: Boolean, onOpen: () -> Unit) {
    val p = FuryColors.current
    val vitals: @Composable (Modifier) -> Unit = { m ->
        Row(m, horizontalArrangement = Arrangement.spacedBy(FurySpacing.md)) {
            Vital("Здоровье", row.hp, p.acc, Modifier.weight(1f))
            Vital("Выносл.", row.endurance, p.gold, Modifier.weight(1f))
            row.mana?.let { Vital("Мана", it, DublMana, Modifier.weight(1f)) } ?: Column(Modifier.weight(1f)) {}
        }
    }
    Column(
        Modifier.fillMaxWidth().background(p.panel, FuryShapes.panel).border(1.dp, p.line2, FuryShapes.panel)
            .clickable(role = Role.Button, onClickLabel = "Открыть лист: ${row.characterName}", onClick = onOpen)
            .padding(horizontal = FurySpacing.md, vertical = FurySpacing.sm),
        verticalArrangement = Arrangement.spacedBy(FurySpacing.xs),
    ) {
        val name: @Composable (Modifier) -> Unit = { m ->
            Column(m) {
                Text(row.characterName, color = DublText, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text("${row.nickname} · ${partyTime(row.updatedAt)}", color = DublMuted, style = MaterialTheme.typography.bodySmall)
            }
        }
        val defense = "${row.defense} / ${row.reflexes} / ${if (row.initiative >= 0) "+" else ""}${row.initiative}"
        if (wide) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(FurySpacing.md)) {
                name(Modifier.weight(1.4f))
                vitals(Modifier.weight(2.4f))
                Column(Modifier.weight(0.9f)) {
                    Text("ЗАЩ / РЕФЛ / ИНИЦ", color = DublMuted, style = com.furybook.ui.theme.furyOverlineStyle())
                    Text(defense, color = DublText, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                }
                Text(row.conditions.joinToString(" · ").ifBlank { "—" }, Modifier.weight(1.2f), color = DublMuted, style = MaterialTheme.typography.bodySmall)
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                name(Modifier.weight(1f))
                Text(defense, color = DublText, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
            vitals(Modifier.fillMaxWidth())
            if (row.conditions.isNotEmpty()) Text(row.conditions.joinToString(" · "), color = DublMuted, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun Vital(label: String, value: Pair<Int, Int>, color: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text("$label ${value.first}/${value.second}", color = DublMuted, style = MaterialTheme.typography.bodySmall)
        MasterBar(value.first, value.second, color, Modifier.fillMaxWidth())
    }
}
