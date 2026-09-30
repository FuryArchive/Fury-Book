package com.furybook.desktop.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.furybook.core.cloud.CampaignDashboard
import com.furybook.core.cloud.CampaignMember
import com.furybook.core.cloud.CampaignSummary
import com.furybook.core.cloud.FuryCampaignApi
import com.furybook.desktop.DesktopAppState
import com.furybook.dubl.model.AttributeId
import com.furybook.dubl.model.resolvedSkills
import com.furybook.web.WebCloudSync
import kotlinx.coroutines.launch

@Composable
fun CampaignScreen(
    state: DesktopAppState,
    cloud: WebCloudSync,
    modifier: Modifier = Modifier,
) {
    val api = remember(cloud) { FuryCampaignApi(cloud) { "web-campaign-fallback" } }
    val scope = rememberCoroutineScope()
    var campaigns by remember { mutableStateOf<List<CampaignSummary>>(emptyList()) }
    var dashboard by remember { mutableStateOf<CampaignDashboard?>(null) }
    var campaignName by remember { mutableStateOf("") }
    var inviteCode by remember { mutableStateOf("") }
    var selectedCharacterId by remember(state.snapshot.activeCharacterId) { mutableStateOf(state.snapshot.activeCharacterId) }
    var characterMenuOpen by remember { mutableStateOf(false) }
    var generatedInvite by remember { mutableStateOf<String?>(null) }
    var selectedMemberId by remember { mutableStateOf<String?>(null) }
    var showFullSheet by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    suspend fun refreshCampaigns() {
        campaigns = api.listCampaigns()
        val active = dashboard
        if (active != null) {
            val summary = campaigns.firstOrNull { it.id == active.id }
            dashboard = if (summary?.isOwner == true) api.getDashboard(active.id) else null
        }
    }

    LaunchedEffect(Unit) {
        runCatching { refreshCampaigns() }
            .onFailure { status = it.message ?: "Не удалось загрузить кампании." }
    }

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        DesktopSectionHeader(
            title = "Кампания",
            subtitle = "Приглашения игроков и GM Screen",
            action = {
                DesktopSmallAction("Обновить", onClick = {
                    scope.launch {
                        runCatching { refreshCampaigns() }
                            .onFailure { status = it.message ?: "Не удалось обновить кампании." }
                    }
                })
            },
        )

        SectionCard("Создать кампанию") {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = campaignName,
                    onValueChange = { campaignName = it.take(80) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    label = { Text("Название") },
                )
                Button(
                    enabled = !busy && campaignName.trim().length >= 2,
                    onClick = {
                        scope.launch {
                            busy = true
                            runCatching {
                                val created = api.createCampaign(campaignName)
                                campaigns = api.listCampaigns()
                                dashboard = api.getDashboard(created.id)
                                campaignName = ""
                                selectedMemberId = null
                                status = "Кампания создана."
                            }.onFailure { status = it.message ?: "Не удалось создать кампанию." }
                            busy = false
                        }
                    },
                ) { Text("Создать · стать GM") }
            }
            Text(
                "GM Screen и доступ к листам игроков получает только создатель кампании.",
                color = DesktopMuted,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        SectionCard("Вступить по приглашению") {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = inviteCode,
                    onValueChange = { inviteCode = it.uppercase().take(64) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    label = { Text("Код приглашения") },
                )
                Column {
                    OutlinedButton(onClick = { characterMenuOpen = true }) {
                        Text(
                            state.snapshot.characters.firstOrNull { it.id == selectedCharacterId }?.name
                                ?: state.activeCharacter.name,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    DropdownMenu(
                        expanded = characterMenuOpen,
                        onDismissRequest = { characterMenuOpen = false },
                    ) {
                        state.snapshot.characters.forEach { character ->
                            DropdownMenuItem(
                                text = { Text(character.name) },
                                onClick = {
                                    selectedCharacterId = character.id
                                    characterMenuOpen = false
                                },
                            )
                        }
                    }
                }
                Button(
                    enabled = !busy && inviteCode.isNotBlank() && selectedCharacterId.isNotBlank(),
                    onClick = {
                        scope.launch {
                            busy = true
                            runCatching {
                                api.joinCampaign(inviteCode, selectedCharacterId)
                                campaigns = api.listCampaigns()
                                inviteCode = ""
                                status = "Персонаж добавлен в кампанию."
                            }.onFailure { status = it.message ?: "Не удалось вступить в кампанию." }
                            busy = false
                        }
                    },
                ) { Text("Вступить") }
            }
        }

        status?.let {
            Text(it, color = DesktopMuted, style = MaterialTheme.typography.bodySmall)
        }

        SectionCard("Мои кампании") {
            if (campaigns.isEmpty()) {
                EmptyState("Пока нет кампаний. Создайте свою или используйте код приглашения.")
            }
            campaigns.forEach { campaign ->
                DesktopPanel(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth().padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(campaign.name, fontWeight = FontWeight.Bold)
                            Text(
                                if (campaign.isOwner) "GM / владелец · игроков: ${campaign.memberCount}"
                                else "Игрок · персонаж привязан",
                                color = if (campaign.isOwner) DesktopAccent else DesktopMuted,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        if (campaign.isOwner) {
                            DesktopSmallAction("Пригласить", onClick = {
                                scope.launch {
                                    runCatching { api.createInvite(campaign.id) }
                                        .onSuccess {
                                            generatedInvite = it.token
                                            status = null
                                        }
                                        .onFailure { status = it.message ?: "Не удалось создать приглашение." }
                                }
                            })
                            DesktopSmallAction("GM Screen", emphasized = true, onClick = {
                                scope.launch {
                                    runCatching { api.getDashboard(campaign.id) }
                                        .onSuccess {
                                            dashboard = it
                                            selectedMemberId = it.players.firstOrNull()?.userId
                                            showFullSheet = false
                                        }
                                        .onFailure { status = it.message ?: "Не удалось открыть GM Screen." }
                                }
                            })
                        } else {
                            TextButton(onClick = {
                                scope.launch {
                                    runCatching {
                                        api.leaveCampaign(campaign.id)
                                        refreshCampaigns()
                                    }.onFailure { status = it.message ?: "Не удалось выйти из кампании." }
                                }
                            }) { Text("Выйти") }
                        }
                    }
                }
            }
        }

        generatedInvite?.let { code ->
            SectionCard("Приглашение") {
                Text("Код: $code", color = DesktopAccent, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("Игрок вводит код и выбирает один из своих персонажей.", color = DesktopMuted)
            }
        }

        dashboard?.let { gm ->
            SectionCard(
                title = "${gm.name} · GM Screen",
                action = {
                    DesktopSmallAction("Обновить данные", onClick = {
                        scope.launch {
                            runCatching { api.getDashboard(gm.id) }
                                .onSuccess { dashboard = it }
                                .onFailure { status = it.message ?: "Не удалось обновить GM Screen." }
                        }
                    })
                },
            ) {
                Text(
                    "Read-only: листы редактируют игроки. Сервер отдаёт чужие чарники только владельцу этой кампании.",
                    color = DesktopMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
                if (gm.players.isEmpty()) EmptyState("Игроков пока нет.")
                gm.players.forEach { member ->
                    GmMemberRow(
                        member = member,
                        selected = selectedMemberId == member.userId,
                        onClick = {
                            selectedMemberId = member.userId
                            showFullSheet = false
                        },
                    )
                }
                gm.players.firstOrNull { it.userId == selectedMemberId }?.let { member ->
                    GmInspector(
                        member = member,
                        showFullSheet = showFullSheet,
                        onToggleFullSheet = { showFullSheet = !showFullSheet },
                        onRemove = {
                            scope.launch {
                                runCatching {
                                    api.removeMember(gm.id, member.userId)
                                    dashboard = api.getDashboard(gm.id)
                                    campaigns = api.listCampaigns()
                                    selectedMemberId = dashboard?.players?.firstOrNull()?.userId
                                }.onFailure { status = it.message ?: "Не удалось убрать игрока." }
                            }
                        },
                    )
                }
            }
        }

        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun GmMemberRow(member: CampaignMember, selected: Boolean, onClick: () -> Unit) {
    val c = member.character
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        color = if (selected) DesktopAccentSoft else DesktopSurfaceInset,
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1.5f)) {
                Text(c.name, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(member.nickname, color = DesktopMuted, style = MaterialTheme.typography.bodySmall, maxLines = 1)
            }
            GmMetric("HP", "${c.hpCurrent}/${c.healthMaximum}", Modifier.weight(.8f))
            GmMetric("Защ.", c.defense.toString(), Modifier.weight(.6f))
            GmMetric("Вним.", c.perception.toString(), Modifier.weight(.6f))
            GmMetric("Стойк.", c.fortitude.toString(), Modifier.weight(.6f))
            GmMetric("Рефл.", c.reflexes.toString(), Modifier.weight(.6f))
            GmMetric("Иниц.", if (c.initiative >= 0) "+${c.initiative}" else c.initiative.toString(), Modifier.weight(.6f))
            Text(
                member.extras.activeConditions.joinToString { it.title }.ifBlank { "—" },
                modifier = Modifier.weight(1f),
                color = DesktopMuted,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun GmMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = DesktopMuted, style = MaterialTheme.typography.labelSmall)
        Text(value, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun GmInspector(
    member: CampaignMember,
    showFullSheet: Boolean,
    onToggleFullSheet: () -> Unit,
    onRemove: () -> Unit,
) {
    val c = member.character
    DesktopHeroPanel(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(c.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("Игрок: ${member.nickname}", color = DesktopMuted)
                    Text("Обновлено: ${member.updatedAt}", color = DesktopMuted, style = MaterialTheme.typography.bodySmall)
                }
                DesktopSmallAction(if (showFullSheet) "Свернуть лист" else "Полный лист", onToggleFullSheet, emphasized = true)
                Spacer(Modifier.width(8.dp))
                DesktopSmallAction("Убрать игрока", onRemove)
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DesktopMetricCell("HP", "${c.hpCurrent}/${c.healthMaximum}", Modifier.weight(1f))
                DesktopMetricCell("Выносливость", "${c.enduranceCurrent}/${c.enduranceMaximum}", Modifier.weight(1f))
                DesktopMetricCell("Защита", c.defense.toString(), Modifier.weight(1f))
                DesktopMetricCell("Внимательность", c.perception.toString(), Modifier.weight(1f))
                DesktopMetricCell("Стойкость", c.fortitude.toString(), Modifier.weight(1f))
                DesktopMetricCell("Рефлексы", c.reflexes.toString(), Modifier.weight(1f))
                DesktopMetricCell("Инициатива", if (c.initiative >= 0) "+${c.initiative}" else c.initiative.toString(), Modifier.weight(1f))
            }

            if (member.extras.activeConditions.isNotEmpty()) {
                Text(
                    "Состояния: ${member.extras.activeConditions.joinToString { it.title }}",
                    color = DesktopAccent,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            if (showFullSheet) {
                Text("Характеристики", fontWeight = FontWeight.Bold)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AttributeId.entries.forEach { attr ->
                        DesktopMetricCell(attr.shortTitle, c.attribute(attr).toString(), Modifier.weight(1f))
                    }
                }

                Text("Умения", fontWeight = FontWeight.Bold)
                c.resolvedSkills(includeHidden = true).forEach { skill ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(skill.name.ifBlank { skill.id }, modifier = Modifier.weight(1f))
                        Text("ранг ${skill.rank}", color = DesktopMuted)
                    }
                }

                Text("Развитие", fontWeight = FontWeight.Bold)
                Text(
                    "Получено записей: ${c.development.count { it.value.rank > 0 }} · пользовательских: ${c.customDevelopmentEntries.size}",
                    color = DesktopMuted,
                )
                c.customDevelopmentEntries.forEach { Text("• ${it.name}") }

                Text("Магия", fontWeight = FontWeight.Bold)
                if (c.magic.schools.isEmpty() && c.magic.spells.isEmpty()) {
                    Text("Нет записей", color = DesktopMuted)
                } else {
                    c.magic.schools.forEach { Text("• ${it.name}: ${it.rank}") }
                    c.magic.spells.forEach { Text("• ${it.name}") }
                }

                Text("Снаряжение", fontWeight = FontWeight.Bold)
                if (c.gear.items.isEmpty()) {
                    Text("Нет предметов", color = DesktopMuted)
                } else {
                    c.gear.items.forEach { Text("• ${it.name} ×${it.quantity}") }
                }
            }
        }
    }
}
