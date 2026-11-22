package com.furybook.dubl.ui.master

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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.furybook.core.cloud.CampaignDashboard
import com.furybook.dubl.master.FightEntry
import com.furybook.dubl.master.FightKind
import com.furybook.dubl.master.addPlayer
import com.furybook.dubl.master.canNudgeUp
import com.furybook.dubl.master.changeHp
import com.furybook.dubl.master.fightOrder
import com.furybook.dubl.master.newFight
import com.furybook.dubl.master.nextTurn
import com.furybook.dubl.master.nudgeUp
import com.furybook.dubl.master.removeEntry
import com.furybook.dubl.master.rollInitiative
import com.furybook.dubl.master.setInitiative
import com.furybook.dubl.master.toggleCondition
import com.furybook.dubl.model.ConditionCatalog
import com.furybook.ui.components.FuryButton
import com.furybook.ui.components.FuryButtonStyle
import com.furybook.ui.components.FuryDialogAction
import com.furybook.ui.components.FuryDialogScaffold
import com.furybook.ui.components.FuryTextField
import com.furybook.ui.theme.DublMuted
import com.furybook.ui.theme.DublText
import com.furybook.ui.theme.FuryColors
import com.furybook.ui.theme.FuryShapes
import com.furybook.ui.theme.FurySpacing
import kotlin.random.Random

/** Бой: the initiative tracker. Initiative is typed (dice are rolled at the table); «Бросить» is optional. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FightTab(scope: MasterScope, conditions: ConditionCatalog, dashboard: CampaignDashboard?) {
    val fight = scope.state.data.fight
    var addingPlayer by remember { mutableStateOf(false) }
    var conditionFor by remember { mutableStateOf<FightEntry?>(null) }
    MasterPage {
        MasterHeader(
            "Бой · раунд ${fight.round}",
            "Инициативу бросаете кубами за столом — впишите числа. Хиты и состояния врагов ведёт только мастер.",
        ) {
            FuryButton("Новый бой", { scope.change(if (fight.entries.isEmpty()) null else "Бой очищен") { it.copy(fight = newFight()) } })
            FuryButton("Следующий →", { scope.change { it.copy(fight = nextTurn(it.fight)) } }, style = FuryButtonStyle.Primary, enabled = fight.entries.isNotEmpty())
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(FurySpacing.sm), verticalArrangement = Arrangement.spacedBy(FurySpacing.sm)) {
            FuryButton("+ Игрок по имени", { addingPlayer = true })
            FuryButton("+ Из врагов", { scope.state.tab = MasterTab.ENEMIES })
            FuryButton("+ Партия", {
                val players = dashboard?.players.orEmpty()
                if (players.isEmpty()) {
                    scope.toast.show("Сначала откройте кампанию во вкладке «Партия»")
                } else {
                    scope.change { data ->
                        data.copy(fight = players.fold(data.fight) { f, m -> addPlayer(f, m.character.name, m.character.initiative, masterId()) })
                    }
                    scope.toast.show("В бой добавлена партия: ${players.size}")
                }
            })
        }
        val order = fightOrder(fight)
        if (order.isEmpty()) {
            MasterCard(Modifier.fillMaxWidth()) {
                Text("В бою никого. Добавьте игроков по имени, врагов из списка или всю партию.", color = DublMuted, style = MaterialTheme.typography.bodyMedium)
            }
        }
        order.forEach { entry ->
            FightRow(
                entry = entry,
                isTurn = entry.uid == fight.turnUid,
                canNudge = canNudgeUp(fight, entry.uid),
                onNudge = { scope.change { it.copy(fight = nudgeUp(it.fight, entry.uid)) } },
                onInitiative = { text -> scope.change { it.copy(fight = setInitiative(it.fight, entry.uid, text)) } },
                onHp = { delta -> scope.change { it.copy(fight = changeHp(it.fight, entry.uid, delta)) } },
                onRemoveCondition = { c -> scope.change { it.copy(fight = toggleCondition(it.fight, entry.uid, c)) } },
                onAddCondition = { conditionFor = entry },
                onRoll = {
                    val roll = rollInitiative(entry) { Random.nextInt(1, 7) }
                    scope.change { it.copy(fight = setInitiative(it.fight, entry.uid, roll.toString())) }
                    val bonus = entry.initiativeBonus?.takeIf { it != 0 }?.let { if (it > 0) " + $it" else " − ${-it}" }.orEmpty()
                    scope.toast.show("${entry.name}: 2d6$bonus = $roll")
                },
                onRemove = { scope.change("${entry.name} убран из боя") { it.copy(fight = removeEntry(it.fight, entry.uid)) } },
            )
        }
    }
    if (addingPlayer) {
        AddPlayerDialog(
            onDismiss = { addingPlayer = false },
            onAdd = { name, bonus -> addingPlayer = false; scope.change { it.copy(fight = addPlayer(it.fight, name, bonus, masterId())) } },
        )
    }
    conditionFor?.let { entry ->
        ConditionDialog(
            entry = entry,
            catalog = conditions,
            onDismiss = { conditionFor = null },
            onPick = { c -> conditionFor = null; scope.change { it.copy(fight = toggleCondition(it.fight, entry.uid, c)) } },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FightRow(
    entry: FightEntry,
    isTurn: Boolean,
    canNudge: Boolean,
    onNudge: () -> Unit,
    onInitiative: (String) -> Unit,
    onHp: (Int) -> Unit,
    onRemoveCondition: (String) -> Unit,
    onAddCondition: () -> Unit,
    onRoll: () -> Unit,
    onRemove: () -> Unit,
) {
    val p = FuryColors.current
    val marker: @Composable () -> Unit = {
        Box(Modifier.width(28.dp), contentAlignment = Alignment.Center) {
            when {
                // A drawn arrow: «▶» turns into a colour emoji on some systems.
                isTurn -> Text("→", color = p.accText, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { contentDescription = "Сейчас ходит" })
                canNudge -> Text(
                    "▲", color = DublMuted, style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.clickable(role = Role.Button, onClickLabel = "Выше при равной инициативе", onClick = onNudge).padding(4.dp),
                )
            }
        }
    }
    val name: @Composable (Modifier) -> Unit = { m ->
        Column(m, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(FurySpacing.sm)) {
                Text(entry.name, color = DublText, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(if (entry.kind == FightKind.PLAYER) "игрок" else "враг", color = DublMuted, style = MaterialTheme.typography.bodySmall)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                entry.conditions.forEach { c -> ConditionChip("$c ×", "Снять: $c") { onRemoveCondition(c) } }
                ConditionChip("+ состояние", "Добавить состояние: ${entry.name}", onClick = onAddCondition)
            }
        }
    }
    val hp: @Composable (Modifier) -> Unit = { m ->
        val max = entry.hpMax
        if (max == null) Box(m) else Row(m, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            FuryButton("−", { onHp(-1) })
            Column(Modifier.width(64.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${entry.hp ?: max}/$max", color = DublText, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                MasterBar(entry.hp ?: max, max, p.acc, Modifier.fillMaxWidth())
            }
            FuryButton("+", { onHp(1) })
        }
    }
    BoxWithConstraints(
        Modifier.fillMaxWidth()
            .background(if (isTurn) p.accSoft else p.panel, FuryShapes.panel)
            .border(1.dp, if (isTurn) p.accLine else p.line2, FuryShapes.panel)
            .padding(horizontal = FurySpacing.md, vertical = FurySpacing.sm),
    ) {
        if (maxWidth < 640.dp) {
            // Phones: initiative and name on one line, hit points and «Бросить» under them.
            Column(verticalArrangement = Arrangement.spacedBy(FurySpacing.xs)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(FurySpacing.sm)) {
                    marker()
                    InitiativeField(entry.initiative, entry.name, onInitiative)
                    name(Modifier.weight(1f))
                    FuryButton("✕", onRemove, style = FuryButtonStyle.Ghost)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(FurySpacing.sm)) {
                    hp(Modifier.weight(1f))
                    FuryButton("Бросить", onRoll)
                }
            }
        } else {
            // Fixed widths so the hit points and «Бросить» line up from row to row.
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(FurySpacing.sm)) {
                marker()
                InitiativeField(entry.initiative, entry.name, onInitiative)
                name(Modifier.width(280.dp))
                hp(Modifier.width(HP_BOX_WIDTH))
                FuryButton("Бросить", onRoll)
                FuryButton("✕", onRemove, style = FuryButtonStyle.Ghost)
            }
        }
    }
}

private val HP_BOX_WIDTH = 168.dp

@Composable
private fun InitiativeField(value: String, name: String, onChange: (String) -> Unit) {
    val p = FuryColors.current
    BasicTextField(
        value = value,
        onValueChange = { onChange(it.filter { ch -> ch.isDigit() || ch == '-' }.take(3)) },
        singleLine = true,
        textStyle = MaterialTheme.typography.titleMedium.copy(color = DublText, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center),
        cursorBrush = SolidColor(p.acc),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.width(56.dp).heightIn(min = 44.dp).background(p.bg, FuryShapes.control).border(1.dp, p.track, FuryShapes.control)
            .padding(horizontal = 6.dp, vertical = 10.dp).semantics { contentDescription = "Инициатива: $name" },
    )
}

@Composable
private fun ConditionChip(label: String, description: String, onClick: () -> Unit) {
    val p = FuryColors.current
    Text(
        label,
        color = DublText,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.background(p.panel2, FuryShapes.control).clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description }.padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

@Composable
private fun AddPlayerDialog(onDismiss: () -> Unit, onAdd: (String, Int?) -> Unit) {
    var name by remember { mutableStateOf("") }
    var bonus by remember { mutableStateOf("") }
    val bonusError = if (bonus.isNotBlank() && bonus.trim().toIntOrNull() == null) "Целое число" else null
    FuryDialogScaffold(
        title = "Игрок по имени",
        onDismiss = onDismiss,
        primary = FuryDialogAction("Добавить", { onAdd(name, bonus.trim().toIntOrNull()) }, enabled = name.isNotBlank() && bonusError == null),
        secondary = FuryDialogAction("Отмена", onDismiss),
    ) {
        FuryTextField(name, { name = it }, "Имя")
        FuryTextField(bonus, { bonus = it }, "Бонус к инициативе (необязательно)", error = bonusError, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        Text("Без бонуса «Бросить» даёт просто 2d6.", color = DublMuted, style = MaterialTheme.typography.bodySmall)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ConditionDialog(entry: FightEntry, catalog: ConditionCatalog, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    var own by remember { mutableStateOf("") }
    FuryDialogScaffold(
        title = "Состояние",
        subtitle = entry.name,
        onDismiss = onDismiss,
        primary = FuryDialogAction("Добавить своё", { onPick(own) }, enabled = own.isNotBlank()),
        secondary = FuryDialogAction("Отмена", onDismiss),
    ) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(FurySpacing.xs), verticalArrangement = Arrangement.spacedBy(FurySpacing.xs)) {
            catalog.conditions.filter { it.name !in entry.conditions }.forEach { condition ->
                FuryButton(condition.name, { onPick(condition.name) })
            }
        }
        FuryTextField(own, { own = it }, "Своё состояние")
    }
}
