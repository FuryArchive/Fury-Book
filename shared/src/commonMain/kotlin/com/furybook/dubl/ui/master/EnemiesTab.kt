package com.furybook.dubl.ui.master

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.furybook.dubl.master.MasterEnemy
import com.furybook.dubl.master.addEnemy
import com.furybook.dubl.master.duplicateEnemy
import com.furybook.dubl.master.enemyCopiesInFight
import com.furybook.ui.components.FuryButton
import com.furybook.ui.components.FuryButtonStyle
import com.furybook.ui.theme.DublMuted
import com.furybook.ui.theme.DublText
import com.furybook.ui.theme.FurySpacing

/** Враги: enemy templates. «В бой» adds a copy, so two goblins are two rows. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun EnemiesTab(scope: MasterScope) {
    val data = scope.state.data
    var editing by remember { mutableStateOf<MasterEnemy?>(null) }
    var creating by remember { mutableStateOf(false) }
    MasterPage {
        MasterHeader("Враги", "Заготовки противников. «В бой» добавляет копию — два гоблина будут двумя строками.") {
            FuryButton("+ Новый враг", { creating = true }, style = FuryButtonStyle.Primary)
        }
        if (data.enemies.isEmpty()) {
            Text("Врагов пока нет. «+ Новый враг» создаёт заготовку с числами из книги или вашими.", color = DublMuted, style = MaterialTheme.typography.bodyMedium)
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val columns = masterColumns(maxWidth.value.toInt())
            Column(verticalArrangement = Arrangement.spacedBy(FurySpacing.md)) {
                data.enemies.chunked(columns).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(FurySpacing.md)) {
                        row.forEach { enemy ->
                            val inFight = enemyCopiesInFight(data.fight, enemy)
                            MasterCard(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.Bottom) {
                                    Text(enemy.name, Modifier.weight(1f), color = DublText, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    if (inFight > 0) Text("в бою: $inFight", color = DublMuted, style = MaterialTheme.typography.bodySmall)
                                }
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    MasterStat("Защ", enemy.defense.toString())
                                    MasterStat("Рефл", enemy.reflexes.toString())
                                    MasterStat("Иниц", enemy.initiative.toString())
                                    MasterStat("Здор", enemy.health.toString())
                                    MasterStat("Вын", enemy.endurance.toString())
                                }
                                if (enemy.attacks.isNotBlank()) Text(enemy.attacks, color = DublText, style = MaterialTheme.typography.bodyMedium)
                                if (enemy.notes.isNotBlank()) Text(enemy.notes, color = DublMuted, style = MaterialTheme.typography.bodySmall)
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(FurySpacing.xs), verticalArrangement = Arrangement.spacedBy(FurySpacing.xs)) {
                                    FuryButton("В бой", {
                                        scope.change { it.copy(fight = addEnemy(it.fight, enemy, masterId())) }
                                        scope.toast.show("${enemy.name} в бою")
                                    }, style = FuryButtonStyle.Primary)
                                    FuryButton("Изменить", { editing = enemy })
                                    FuryButton("Дублировать", { scope.change { d -> d.copy(enemies = d.enemies.insertAfter(enemy, duplicateEnemy(enemy, masterId()))) } })
                                    FuryButton("Удалить", { scope.change("${enemy.name} удалён") { d -> d.copy(enemies = d.enemies - enemy) } }, style = FuryButtonStyle.Danger)
                                }
                            }
                        }
                        repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
    if (creating || editing != null) {
        val base = editing
        EnemyFormDialog(
            initial = base,
            onDismiss = { creating = false; editing = null },
            onSave = { saved ->
                creating = false; editing = null
                scope.change { d -> d.copy(enemies = if (base == null) d.enemies + saved else d.enemies.map { if (it.id == saved.id) saved else it }) }
            },
        )
    }
}

private fun List<MasterEnemy>.insertAfter(anchor: MasterEnemy, item: MasterEnemy): List<MasterEnemy> {
    val index = indexOf(anchor)
    return if (index < 0) this + item else toMutableList().apply { add(index + 1, item) }
}

