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
import com.furybook.core.cloud.CampaignMember
import com.furybook.desktop.DesktopAppState
import com.furybook.desktop.cloud.DesktopCloudController
import com.furybook.dubl.model.AttributeId
import com.furybook.dubl.model.resolvedSkills
import kotlinx.coroutines.launch

@Composable
fun CampaignScreen(
    state: DesktopAppState,
    cloudController: DesktopCloudController,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var campaignName by remember { mutableStateOf("") }
    var inviteCode by remember { mutableStateOf("") }
    var selectedCharacterId by remember(state.snapshot.activeCharacterId) { mutableStateOf(state.snapshot.activeCharacterId) }
    var characterMenuOpen by remember { mutableStateOf(false) }
    var generatedInvite by remember { mutableStateOf<String?>(null) }
    var selectedMemberId by remember { mutableStateOf<String?>(null) }
    var showFullSheet by remember { mutableStateOf(false) }

    LaunchedEffect(cloudController.session) {
        if (cloudController.session != null) cloudController.refreshCampaigns()
    }

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        DesktopSectionHeader(
            title = "Кампании",
            subtitle = "Приглашения игроков и GM Screen",
            action = {
                if (cloudController.session != null) {
                    DesktopSmallAction("Обновить", onClick = {
                        scope.launch {
                            cloudController.refreshCampaigns()
                            cloudController.campaignDashboard?.let { cloudController.openGmDashboard(it.id) }
                        }
                    })
                }
            },
        )

        if (cloudController.session == null) {
            SectionCard("Fury Account") {
                Text(
                    "Чтобы создавать кампании или принимать приглашения, войдите в Fury Account во вкладке «Персонажи».",
                    color = DesktopMuted,
                )
            }
            return@Column
        }

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
                    enabled = campaignName.trim().length >= 2,
                    onClick = {
                        scope.launch {
                            cloudController.createCampaign(campaignName)
                                .onSuccess {
                                    campaignName = ""
                                    selectedMemberId = null
                                    showFullSheet = false
                                }
                        }
                    },
                ) { Text("Создать") }
            }
            Text(
                "Создатель кампании становится её GM. GM Screen и доступ к листам игроков доступны только владельцу.",
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
                    enabled = inviteCode.isNotBlank() && selectedCharacterId.isNotBlank(),
                    onClick = {
                        scope.launch {
                            cloudController.joinCampaign(inviteCode, selectedCharacterId)
                                .onSuccess { inviteCode = "" }
                        }
                    },
                ) { Text("Вступить") }
            }
        }

        cloudController.campaignMessage?.let {
            Text(it, color = DesktopMuted, style = MaterialTheme.typography.bodySmall)
        }

        if (cloudController.campaigns.isEmpty()) {
            SectionCard("Мои кампании") {
                EmptyState("Пока нет кампаний. Создайте свою или введите код приглашения.")
            }
        } else {
            SectionCard("Мои кампании") {
                cloudController.campaigns.forEach { campaign ->
                    DesktopPanel(Modifier.fillMaxWidth()) {
                        Column(
                            Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(campaign.name, fontWeight = FontWeight.Bold)
                                    Text(
                                        if (campaign.isOwner) {
                                            "GM / владелец · игроков: ${campaign.memberCount}"
                                        } else {
                                            val linked = state.snapshot.characters.firstOrNull { it.id == campaign.linkedCharacterId }?.name
                                                ?: "Персонаж синхронизирован"
                                            "Игрок · $linked"
                                        },
                                        color = if (campaign.isOwner) DesktopAccent else DesktopMuted,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                                if (campaign.isOwner) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        DesktopSmallAction("Пригласить", onClick = {
                                            scope.launch {
                                                cloudController.createCampaignInvite(campaign.id)
                                                    .onSuccess { generatedInvite = it.token }
                                            }
                                        })
                                        DesktopSmallAction("GM Screen", emphasized = true, onClick = {
                                            scope.launch {
                                                cloudController.openGmDashboard(campaign.id)
                                                    .onSuccess {
                                                        selectedMemberId = it.players.firstOrNull()?.userId
                                                        showFullSheet = false
                                                    }
                                            }
                                        })
                                    }
                                } else {
                                    TextButton(onClick = {
                                        scope.launch { cloudController.leaveCampaign(campaign.id) }
                                    }) { Text("Выйти") }
                                }
                            }
                        }
                    }
                }
            }
        }

        generatedInvite?.let { code ->
            SectionCard("Приглашение") {
                Text("Код: $code", color = DesktopAccent, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    "Передайте этот код игроку. Он выбирает своего персонажа при вступлении.",
                    color = DesktopMuted,
                )
            }
        }

        cloudController.campaignDashboard?.let { dashboard ->
            SectionCard(
                title = "${dashboard.name} · GM Screen",
                action = {
                    DesktopSmallAction("Обновить данные", onClick = {
                        scope.launch { cloudController.openGmDashboard(dashboard.id) }
                    })
                },
            ) {
                Text(
                    "Только просмотр: игроки редактируют собственные листы. Сервер отдаёт эти данные только владельцу кампании.",
                    color = DesktopMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
                if (dashboard.players.isEmpty()) {
                    EmptyState("В кампании пока нет игроков. Создайте код приглашения.")
                } else {
                    dashboard.players.forEach { member ->
                        GmMemberRow(
                            member = member,
                            selected = selectedMemberId == member.userId,
                            onClick = {
                                selectedMemberId = member.userId
                                showFullSheet = false
                            },
                        )
                    }
                }

                dashboard.players.firstOrNull { it.userId == selectedMemberId }?.let { member ->
                    GmCharacterInspector(
                        member = member,
                        showFullSheet = showFullSheet,
                        onToggleFullSheet = { showFullSheet = !showFullSheet },
                        onRemove = {
                            scope.launch {
                                cloudController.removeCampaignMember(dashboard.id, member.userId)
                                selectedMemberId = cloudController.campaignDashboard?.players?.firstOrNull()?.userId
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
private fun GmMemberRow(
    member: CampaignMember,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val c = member.character
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        color = if (selected) DesktopAccentSoft else DesktopSurfaceInset,
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1.6f)) {
                Text(c.name, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(member.nickname, color = DesktopMuted, style = MaterialTheme.typography.bodySmall, maxLines = 1)
            }
            GmMetric("HP", "${c.hpCurrent}/${c.healthMaximum}", Modifier.weight(.75f))
            GmMetric("Защита", c.defense.toString(), Modifier.weight(.6f))
            GmMetric("Вним.", c.perception.toString(), Modifier.weight(.6f))
            GmMetric("Стойк.", c.fortitude.toString(), Modifier.weight(.6f))
            GmMetric("Рефл.", c.reflexes.toString(), Modifier.weight(.6f))
            GmMetric("Иниц.", signed(c.initiative), Modifier.weight(.6f))
            Text(
                member.extras.activeConditions.joinToString { it.title }.ifBlank { "—" },
                modifier = Modifier.weight(1.2f),
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
private fun GmCharacterInspector(
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
                DesktopMetricCell("Инициатива", signed(c.initiative), Modifier.weight(1f))
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
                        DesktopMetricCell(
                            attr.shortTitle,
                            c.attribute(attr).toString(),
                            Modifier.weight(1f),
                        )
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
                c.customDevelopmentEntries.forEach { entry ->
                    Text("• ${entry.name}", color = DesktopText)
                }

                Text("Магия", fontWeight = FontWeight.Bold)
                if (c.magic.schools.isEmpty() && c.magic.spells.isEmpty()) {
                    Text("Нет записей", color = DesktopMuted)
                } else {
                    c.magic.schools.forEach { school -> Text("• ${school.name}: ${school.rank}") }
                    c.magic.spells.forEach { spell -> Text("• ${spell.name}${if (!spell.learned) " (не изучено)" else ""}") }
                }

                Text("Снаряжение", fontWeight = FontWeight.Bold)
                if (c.gear.items.isEmpty()) {
                    Text("Нет предметов", color = DesktopMuted)
                } else {
                    c.gear.items.forEach { item ->
                        Text("• ${item.name} ×${item.quantity}${if (!item.carried) " · не несёт" else ""}")
                    }
                }
            }
        }
    }
}
