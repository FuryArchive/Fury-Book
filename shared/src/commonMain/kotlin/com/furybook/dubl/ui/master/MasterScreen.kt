package com.furybook.dubl.ui.master

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.furybook.core.cloud.CampaignDashboard
import com.furybook.dubl.master.MasterData
import com.furybook.dubl.master.MasterStore
import com.furybook.dubl.model.ConditionCatalog
import com.furybook.dubl.model.DevelopmentCatalog
import com.furybook.dubl.model.MagicEquipmentCatalog
import com.furybook.dubl.model.SkillDefinition
import com.furybook.ui.account.FuryAccount
import com.furybook.ui.components.FuryTab
import com.furybook.ui.components.FuryToastHost
import com.furybook.ui.components.FuryToastState
import com.furybook.ui.components.FuryTopTabs
import com.furybook.ui.theme.DublMuted
import com.furybook.ui.theme.DublText
import com.furybook.ui.theme.FuryColors
import com.furybook.ui.theme.FuryShapes
import com.furybook.ui.theme.FurySpacing
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** The rulebook catalogs Справочник searches and Бой takes conditions from. */
data class MasterCatalogs(
    val development: DevelopmentCatalog,
    val magic: MagicEquipmentCatalog,
    val conditions: ConditionCatalog,
    val skills: List<SkillDefinition>,
)

@OptIn(ExperimentalUuidApi::class)
internal fun masterId(): String = Uuid.random().toString()

/** Today as «YYYY-MM-DD» (UTC), for new notes; the GM can edit it. */
@OptIn(ExperimentalTime::class)
internal fun masterToday(): String = Clock.System.now().toString().take(10)

/** What the tabs share: the state, a way to change data (with an undo toast) and the toast itself. */
internal class MasterScope(val state: MasterState, val toast: FuryToastState) {
    fun change(undoable: String? = null, block: (MasterData) -> MasterData) {
        state.update(block, undoable)
        if (undoable != null) toast.show(undoable, onUndo = { state.undo() })
    }
}

/**
 * Мастер: Бой, Враги, Заметки and Справочник work offline with no account (their data is [store], on this
 * device); Партия needs the account and uses the existing campaign calls only.
 */
@Composable
fun DublMasterScreen(
    store: MasterStore,
    account: FuryAccount,
    catalogs: MasterCatalogs,
    onSignIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = remember(store) { MasterState(store) }
    val toast = remember { FuryToastState() }
    val scope = remember(state, toast) { MasterScope(state, toast) }
    var dashboard by remember { mutableStateOf<CampaignDashboard?>(null) }
    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            FuryTopTabs(
                tabs = MasterTab.entries.map { FuryTab(it, it.label) },
                selected = state.tab,
                onSelect = { state.tab = it },
                modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = FurySpacing.lg, vertical = FurySpacing.sm),
            )
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (state.tab) {
                    MasterTab.FIGHT -> FightTab(scope, catalogs.conditions, dashboard)
                    MasterTab.ENEMIES -> EnemiesTab(scope)
                    MasterTab.NOTES -> NotesTab(scope)
                    MasterTab.LOOKUP -> LookupTab(scope, catalogs)
                    MasterTab.PARTY -> PartyTab(scope, account, catalogs, dashboard, { dashboard = it }, onSignIn)
                }
            }
        }
        FuryToastHost(toast)
    }
}

/** A scrolling tab body with the standard padding (room at the bottom for the toast). */
@Composable
internal fun MasterPage(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = FurySpacing.lg).padding(top = FurySpacing.sm, bottom = 80.dp),
        verticalArrangement = Arrangement.spacedBy(FurySpacing.md),
        content = content,
    )
}

/** Title, a line of explanation and the tab's actions; the actions drop under the title on phones. */
@Composable
internal fun MasterHeader(title: String, subtitle: String, actions: @Composable () -> Unit = {}) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val text: @Composable (Modifier) -> Unit = { m ->
            Column(m) {
                Text(title, color = DublText, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(subtitle, color = DublMuted, style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (maxWidth < 560.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(FurySpacing.sm)) {
                text(Modifier)
                Row(horizontalArrangement = Arrangement.spacedBy(FurySpacing.sm)) { actions() }
            }
        } else {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(FurySpacing.sm)) {
                text(Modifier.weight(1f))
                actions()
            }
        }
    }
}

@Composable
internal fun MasterCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val p = FuryColors.current
    Column(
        modifier.background(p.panel, FuryShapes.panel).border(1.dp, p.line2, FuryShapes.panel).padding(FurySpacing.md),
        verticalArrangement = Arrangement.spacedBy(FurySpacing.sm),
        content = content,
    )
}

/** «Защита 7»-style chip. */
@Composable
internal fun MasterStat(label: String, value: String) {
    val p = FuryColors.current
    Row(Modifier.background(p.panel2, FuryShapes.control).padding(horizontal = 8.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, color = DublMuted, style = MaterialTheme.typography.bodySmall)
        Text(value, color = DublText, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
    }
}

/** A thin bar for current/maximum (HP, Выносливость, Мана). */
@Composable
internal fun MasterBar(current: Int, max: Int, color: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    val p = FuryColors.current
    val fraction = if (max <= 0) 0f else (current.toFloat() / max).coerceIn(0f, 1f)
    Box(modifier.height(4.dp).background(p.track, FuryShapes.control)) {
        Box(Modifier.fillMaxWidth(fraction).height(4.dp).background(color, FuryShapes.control))
    }
}

/** Grid columns for cards: 3 wide, 2 on tablets, 1 on phones. */
internal fun masterColumns(widthDp: Int): Int = when {
    widthDp >= 1000 -> 3
    widthDp >= 600 -> 2
    else -> 1
}
