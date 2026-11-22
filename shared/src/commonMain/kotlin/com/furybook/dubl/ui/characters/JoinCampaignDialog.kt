package com.furybook.dubl.ui.characters

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.furybook.core.cloud.CampaignSummary
import com.furybook.ui.account.FuryAccount
import com.furybook.ui.components.FuryButton
import com.furybook.ui.components.FuryButtonStyle
import com.furybook.ui.components.FuryDialogAction
import com.furybook.ui.components.FuryDialogScaffold
import com.furybook.ui.components.FuryTextField
import com.furybook.ui.theme.DublMuted
import com.furybook.ui.theme.DublText
import com.furybook.ui.theme.FurySpacing
import kotlinx.coroutines.launch

/**
 * A character's campaigns: the ones it is linked to, with «Выйти из кампании», and «Вступить по коду».
 * [onDone] gets the toast text after a join or a leave.
 */
@Composable
fun JoinCampaignDialog(
    account: FuryAccount,
    characterId: String,
    characterName: String,
    nameOf: (String) -> String?,
    onDismiss: () -> Unit,
    onDone: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var code by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val linked = account.campaigns.filter { it.linkedCharacterId == characterId }

    fun join() {
        val clean = code.trim()
        if (clean.isEmpty()) { error = "Введите код приглашения."; return }
        val before = account.campaigns
        busy = true; error = null
        scope.launch {
            account.joinCampaign(clean, characterId).fold(
                { joined -> onDone(joinMessage(before, joined, characterId, characterName, nameOf)) },
                { error = it.message ?: "Не удалось вступить в кампанию." },
            )
            busy = false
        }
    }

    fun leave(campaign: CampaignSummary) {
        busy = true; error = null
        scope.launch {
            account.leaveCampaign(campaign.id).fold(
                { onDone("$characterName больше не в кампании «${campaign.name}»") },
                { error = it.message ?: "Не удалось выйти из кампании." },
            )
            busy = false
        }
    }

    FuryDialogScaffold(
        title = if (linked.isEmpty()) "Вступить по коду" else "Кампании персонажа",
        subtitle = "Персонаж: $characterName",
        onDismiss = { if (!busy) onDismiss() },
        primary = FuryDialogAction("Вступить", ::join, enabled = !busy),
        secondary = FuryDialogAction("Отмена", onDismiss, enabled = !busy),
    ) {
        linked.forEach { campaign ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(FurySpacing.sm)) {
                Column(Modifier.weight(1f)) {
                    Text(campaign.name, color = DublText, style = MaterialTheme.typography.titleSmall)
                    Text("Участников: ${campaign.memberCount}", color = DublMuted, style = MaterialTheme.typography.bodySmall)
                }
                FuryButton("Выйти из кампании", { leave(campaign) }, style = FuryButtonStyle.Danger, enabled = !busy)
            }
        }
        FuryTextField(code, { code = it.uppercase(); error = null }, "Код приглашения", error = error)
        Text(
            "Мастер увидит лист этого персонажа (только чтение). Если в этой кампании уже есть другой ваш персонаж, его место займёт этот.",
            color = DublMuted, style = MaterialTheme.typography.bodySmall,
        )
    }
}

internal fun joinMessage(
    before: List<CampaignSummary>,
    joined: CampaignSummary,
    characterId: String,
    characterName: String,
    nameOf: (String) -> String?,
): String {
    val base = "$characterName в кампании «${joined.name}»"
    val replaced = joinReplaced(before, joined, characterId) ?: return base
    return "$base. ${nameOf(replaced) ?: "Прежний персонаж"} больше в ней не участвует."
}
