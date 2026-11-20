package com.furybook.dubl.ui.editor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.furybook.dubl.model.CharacterEconomy
import com.furybook.dubl.model.ChiRules
import com.furybook.dubl.model.developmentNormalize
import com.furybook.dubl.ui.sheet.SheetLayout
import com.furybook.ui.theme.FuryColors
import com.furybook.ui.theme.FurySpacing

/** One ЦИ technique, read-only in the editor; [status] is the rules' reason it can't be used, or «Можно использовать». */
data class EditorChiTechniqueLine(val name: String, val school: String, val cost: Int, val action: String, val effect: String, val status: String)

data class EditorChiCard(
    val active: Boolean,
    /** «Внутренняя ЦИ» keeps the resource on. */
    val locked: Boolean,
    val lockReason: String?,
    val current: Int,
    val maximum: Int,
    val bonusRanks: Int,
    val rankXp: Int,
    val maximumText: String,
    val techniques: List<EditorChiTechniqueLine>,
)

internal const val EDITOR_CHI_MAX_BONUS_RANKS = 10

/**
 * The ЦИ setup card of the Навыки step's ЦИ tab (the old tab's «Ресурс ЦИ»); null unless the ЦИ pack mounts the tab.
 * The step's search ([query]: name, school, effect) and «Доступно» ([availableOnly]: requirements met) filter the
 * techniques too, as the old ЦИ tab's search and filter did.
 */
fun editorChiCard(host: DublEditorHost, query: String = "", availableOnly: Boolean = false): EditorChiCard? {
    if (!editorShowsChi(host)) return null
    val c = host.active
    val rules = ChiRules(c, host.developmentCatalog)
    return EditorChiCard(
        active = c.chiActive,
        locked = c.chiAutomaticAccess,
        lockReason = if (c.chiAutomaticAccess) "Ресурс открыт способностью «Внутренняя ЦИ» и остаётся активным, пока способность изучена." else null,
        current = c.chiCurrent,
        maximum = c.chiMaximum,
        bonusRanks = c.chiBonusRanks,
        rankXp = CharacterEconomy.CHI_BONUS_RANK_XP,
        maximumText = "Максимум: база ${c.chiBaseMaximum} + запас ${c.chiBonusRanks} + развитие ${c.chiProgressionBonus} = ${c.chiMaximum}",
        techniques = host.chiCatalog.techniques.mapNotNull { technique ->
            val availability = rules.availability(technique)
            val needle = developmentNormalize(query)
            if (needle.isNotBlank() && needle !in developmentNormalize("${technique.name} ${technique.school} ${technique.effect}")) return@mapNotNull null
            if (availableOnly && !availability.unlocked) return@mapNotNull null
            EditorChiTechniqueLine(
                technique.name, technique.school, technique.chiCost, technique.action, technique.effect,
                if (availability.canUse) "Можно использовать" else availability.reason,
            )
        },
    )
}

@Composable
internal fun EditorChiCardView(host: DublEditorHost, card: EditorChiCard, layout: SheetLayout) {
    val p = FuryColors.current
    val control = editorControl(layout)
    var techniquesOpen by remember { mutableStateOf(false) }
    EditorCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(FurySpacing.sm)) {
            Column(Modifier.weight(1f)) {
                Text("ЦИ", color = p.text, style = MaterialTheme.typography.titleMedium)
                Text(if (card.active) "${card.current} / ${card.maximum}" else "ЦИ выключено", color = p.muted, style = MaterialTheme.typography.labelMedium.merge(EditorTabular))
            }
            if (!card.locked) {
                EditorChip(if (card.active) "Вкл." else "Выкл.", card.active, { host.setChiEnabled(!card.active) }, control, role = Role.Switch)
            }
        }
        card.lockReason?.let { Text(it, color = p.goldText, style = MaterialTheme.typography.bodySmall) }
        if (card.active) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(FurySpacing.sm)) {
                Text("Доп. ранги", Modifier.weight(1f), color = p.text2, style = MaterialTheme.typography.bodyMedium)
                EditorSquareButton("−", "Убрать доп. ранг ЦИ", card.bonusRanks > 0, control) { host.setChiBonusRanks(card.bonusRanks - 1) }
                Text("${card.bonusRanks}", color = p.text, style = MaterialTheme.typography.titleMedium.merge(EditorTabular))
                EditorSquareButton("+", "Добавить доп. ранг ЦИ", card.bonusRanks < EDITOR_CHI_MAX_BONUS_RANKS, control) { host.setChiBonusRanks(card.bonusRanks + 1) }
            }
            Text("${card.rankXp} XP за ранг · до $EDITOR_CHI_MAX_BONUS_RANKS", color = p.goldText, style = MaterialTheme.typography.labelSmall)
            Text(card.maximumText, color = p.muted, style = MaterialTheme.typography.bodySmall)
        } else {
            Text("Включение ресурса само по себе не расходует XP и не выдаёт способности автоматически.", color = p.muted, style = MaterialTheme.typography.bodySmall)
        }
        Box(
            Modifier.fillMaxWidth().heightIn(min = control).clickable(role = Role.Button) { techniquesOpen = !techniquesOpen },
            contentAlignment = Alignment.CenterStart,
        ) {
            Text((if (techniquesOpen) "▾ " else "▸ ") + "Приёмы ЦИ · ${card.techniques.size}", color = p.goldText, style = MaterialTheme.typography.labelLarge)
        }
        if (techniquesOpen) {
            if (card.techniques.isEmpty()) Text("Приёмов ЦИ в каталоге нет.", color = p.muted, style = MaterialTheme.typography.bodySmall)
            card.techniques.forEach { line ->
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("${line.name} · ${line.school}", color = p.text, style = MaterialTheme.typography.labelLarge)
                    Text("${line.cost} ЦИ · ${line.action}", color = p.goldText, style = MaterialTheme.typography.labelSmall)
                    Text(line.effect, color = p.text2, style = MaterialTheme.typography.bodySmall)
                    Text(line.status, color = p.muted, style = MaterialTheme.typography.labelSmall)
                }
            }
            Text("Использовать приём — на листе: нажмите шкалу ЦИ.", color = p.dim, style = MaterialTheme.typography.labelSmall)
        }
    }
}
