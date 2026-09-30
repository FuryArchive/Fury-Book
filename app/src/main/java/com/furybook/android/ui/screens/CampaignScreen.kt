package com.furybook.android.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import com.furybook.android.cloud.AndroidCloudController
import com.furybook.android.state.CharacterController
import com.furybook.android.ui.components.DublScreenHeader
import com.furybook.core.cloud.CampaignMember
import com.furybook.dubl.model.AttributeId
import com.furybook.dubl.model.resolvedSkills
import com.furybook.ui.components.DublCard
import kotlinx.coroutines.launch

@Composable
fun CampaignScreen(
    controller: CharacterController,
    cloudController: AndroidCloudController,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var campaignName by remember { mutableStateOf("") }
    var inviteCode by remember { mutableStateOf("") }
    var selectedCharacterId by remember(controller.snapshot.activeCharacterId) {
        mutableStateOf(controller.snapshot.activeCharacterId)
    }
    var characterMenuOpen by remember { mutableStateOf(false) }
    var generatedInvite by remember { mutableStateOf<String?>(null) }
    var selectedMemberId by remember { mutableStateOf<String?>(null) }
    var showFullSheet by remember { mutableStateOf(false) }

    LaunchedEffect(cloudController.session) {
        if (cloudController.session != null) cloudController.refreshCampaigns()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        DublScreenHeader(
            title = "Кампании",
            subtitle = "Приглашения и GM Screen",
            action = { TextButton(onClick = onBack) { Text("Назад") } },
        )

        if (cloudController.session == null) {
            DublCard(Modifier.fillMaxWidth()) {
                Text("Нужен Fury Account", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Войдите в Fury Account во вкладке «Ещё», чтобы создавать кампании и принимать приглашения.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Column
        }

        DublCard(Modifier.fillMaxWidth()) {
            Text("Создать кампанию", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = campaignName,
                onValueChange = { campaignName = it.take(80) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Название") },
            )
            Button(
                modifier = Modifier.fillMaxWidth(),
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
            ) { Text("Создать · стать GM") }
            Text(
                "GM Screen доступен только создателю кампании.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        DublCard(Modifier.fillMaxWidth()) {
            Text("Вступить по приглашению", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = inviteCode,
                onValueChange = { inviteCode = it.uppercase().take(64) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Код приглашения") },
            )
            Column {
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { characterMenuOpen = true },
                ) {
                    Text(
                        controller.snapshot.characters.firstOrNull { it.id == selectedCharacterId }?.name
                            ?: controller.active.name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                DropdownMenu(
                    expanded = characterMenuOpen,
                    onDismissRequest = { characterMenuOpen = false },
                ) {
                    controller.snapshot.characters.forEach { character ->
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
                modifier = Modifier.fillMaxWidth(),
                enabled = inviteCode.isNotBlank() && selectedCharacterId.isNotBlank(),
                onClick = {
                    scope.launch {
                        cloudController.joinCampaign(inviteCode, selectedCharacterId)
                            .onSuccess { inviteCode = "" }
                    }
                },
            ) { Text("Вступить этим персонажем") }
        }

        cloudController.campaignMessage?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        cloudController.campaigns.forEach { campaign ->
            DublCard(Modifier.fillMaxWidth()) {
                Text(campaign.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    if (campaign.isOwner) {
                        "GM / владелец · игроков: ${campaign.memberCount}"
                    } else {
                        val linked = controller.snapshot.characters.firstOrNull { it.id == campaign.linkedCharacterId }?.name
                            ?: "Синхронизированный персонаж"
                        "Игрок · $linked"
                    },
                    color = if (campaign.isOwner) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
                if (campaign.isOwner) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            modifier = Modifier.weight(1f),
                            onClick = {
                                scope.launch {
                                    cloudController.createCampaignInvite(campaign.id)
                                        .onSuccess { generatedInvite = it.token }
                                }
                            },
                        ) { Text("Пригласить") }
                        Button(
                            modifier = Modifier.weight(1f),
                            onClick = {
                                scope.launch {
                                    cloudController.openGmDashboard(campaign.id)
                                        .onSuccess {
                                            selectedMemberId = it.players.firstOrNull()?.userId
                                            showFullSheet = false
                                        }
                                }
                            },
                        ) { Text("GM Screen") }
                    }
                } else {
                    TextButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { scope.launch { cloudController.leaveCampaign(campaign.id) } },
                    ) { Text("Выйти из кампании") }
                }
            }
        }

        if (cloudController.campaigns.isEmpty()) {
            DublCard(Modifier.fillMaxWidth()) {
                Text(
                    "Пока нет кампаний.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        generatedInvite?.let { code ->
            DublCard(Modifier.fillMaxWidth()) {
                Text("Код приглашения", style = MaterialTheme.typography.titleMedium)
                Text(
                    code,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "Игрок вводит код и выбирает один из своих синхронизированных персонажей.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        cloudController.campaignDashboard?.let { dashboard ->
            DublCard(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(dashboard.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("GM Screen · только для владельца", color = MaterialTheme.colorScheme.primary)
                    }
                    TextButton(onClick = {
                        scope.launch { cloudController.openGmDashboard(dashboard.id) }
                    }) { Text("Обновить") }
                }
                Text(
                    "Игроки меняют свои листы сами. Здесь GM получает read-only состояние из Fury Cloud.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )

                if (dashboard.players.isEmpty()) {
                    Text("Игроков пока нет.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                dashboard.players.forEach { member ->
                    MobileGmMemberCard(
                        member = member,
                        selected = selectedMemberId == member.userId,
                        onClick = {
                            selectedMemberId = member.userId
                            showFullSheet = false
                        },
                    )
                }

                dashboard.players.firstOrNull { it.userId == selectedMemberId }?.let { member ->
                    MobileGmInspector(
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

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun MobileGmMemberCard(
    member: CampaignMember,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val c = member.character
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(c.name, fontWeight = FontWeight.Bold, maxLines = 1)
                    Text(member.nickname, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
                Text("HP ${c.hpCurrent}/${c.healthMaximum}", fontWeight = FontWeight.Bold)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                MobileMetric("Защ", c.defense.toString())
                MobileMetric("Вним", c.perception.toString())
                MobileMetric("Стойк", c.fortitude.toString())
                MobileMetric("Рефл", c.reflexes.toString())
                MobileMetric("Иниц", if (c.initiative >= 0) "+${c.initiative}" else c.initiative.toString())
            }
            if (member.extras.activeConditions.isNotEmpty()) {
                Text(
                    member.extras.activeConditions.joinToString { it.title },
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun MobileMetric(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun MobileGmInspector(
    member: CampaignMember,
    showFullSheet: Boolean,
    onToggleFullSheet: () -> Unit,
    onRemove: () -> Unit,
) {
    val c = member.character
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.large,
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(c.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Игрок: ${member.nickname}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                "HP ${c.hpCurrent}/${c.healthMaximum} · Выносливость ${c.enduranceCurrent}/${c.enduranceMaximum}",
                fontWeight = FontWeight.SemiBold,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                MobileMetric("Защита", c.defense.toString())
                MobileMetric("Внимание", c.perception.toString())
                MobileMetric("Стойкость", c.fortitude.toString())
                MobileMetric("Рефлексы", c.reflexes.toString())
                MobileMetric("Инициатива", if (c.initiative >= 0) "+${c.initiative}" else c.initiative.toString())
            }
            if (member.extras.activeConditions.isNotEmpty()) {
                Text(
                    "Состояния: ${member.extras.activeConditions.joinToString { it.title }}",
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = onToggleFullSheet,
            ) { Text(if (showFullSheet) "Свернуть лист" else "Открыть полный лист") }

            if (showFullSheet) {
                Text("Характеристики", fontWeight = FontWeight.Bold)
                AttributeId.entries.chunked(4).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        row.forEach { attr -> MobileMetric(attr.shortTitle, c.attribute(attr).toString()) }
                    }
                }

                Text("Умения", fontWeight = FontWeight.Bold)
                c.resolvedSkills(includeHidden = true).forEach { skill ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(skill.name.ifBlank { skill.id }, modifier = Modifier.weight(1f), maxLines = 2)
                        Text("${skill.rank}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Text("Развитие", fontWeight = FontWeight.Bold)
                Text(
                    "Получено: ${c.development.count { it.value.rank > 0 }} · своих записей: ${c.customDevelopmentEntries.size}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                c.customDevelopmentEntries.forEach { Text("• ${it.name}") }

                Text("Магия", fontWeight = FontWeight.Bold)
                c.magic.schools.forEach { Text("• ${it.name}: ${it.rank}") }
                c.magic.spells.forEach { Text("• ${it.name}") }
                if (c.magic.schools.isEmpty() && c.magic.spells.isEmpty()) {
                    Text("Нет записей", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                Text("Снаряжение", fontWeight = FontWeight.Bold)
                c.gear.items.forEach { Text("• ${it.name} ×${it.quantity}") }
                if (c.gear.items.isEmpty()) {
                    Text("Нет предметов", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            TextButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = onRemove,
            ) { Text("Убрать игрока из кампании") }
        }
    }
}
