package com.furybook.dubl.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.furybook.dubl.ui.sheet.SheetEconomyDialog
import com.furybook.dubl.ui.sheet.SheetLayout
import com.furybook.dubl.ui.sheet.SheetPortrait
import com.furybook.dubl.ui.sheet.SheetPortraitLoader
import com.furybook.dubl.ui.sheet.sheetEconomyView
import com.furybook.ui.components.FuryStepper
import com.furybook.ui.components.FuryTextField
import com.furybook.ui.theme.FuryColors
import com.furybook.ui.theme.FurySpacing
import kotlinx.coroutines.delay

private const val IDENTITY_SAVE_DELAY_MS = 500L

/** Основа: identity, portrait, size/legs/mana and the XP budget. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun EditorBasicsStep(
    host: DublEditorHost,
    state: EditorState,
    data: EditorFrameData,
    layout: SheetLayout,
    portraitLoader: SheetPortraitLoader?,
    onPickPortrait: (() -> Unit)?,
) {
    val p = FuryColors.current
    val character = host.active
    var economyOpen by remember { mutableStateOf(false) }
    val control = editorControl(layout)
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(editorContentPadding(layout)),
        verticalArrangement = Arrangement.spacedBy(FurySpacing.md),
    ) {
        EditorStepTitle("Основа", "Кто это и сколько опыта даёт мастер. Ресурсы, состояния и заметки меняются прямо на листе.")
        Row(Modifier.editorReadableWidth(), horizontalArrangement = Arrangement.spacedBy(FurySpacing.md), verticalAlignment = Alignment.Top) {
            if (portraitLoader != null || onPickPortrait != null || host.extras.portraitUri != null) {
                SheetPortrait(
                    uri = host.extras.portraitUri,
                    name = character.name,
                    loader = portraitLoader,
                    onPick = onPickPortrait,
                    onRemove = if (host.extras.portraitUri != null) ({ host.setPortrait(null) }) else null,
                    size = if (layout == SheetLayout.PHONE) 88.dp else 120.dp,
                )
            }
            IdentityFields(host, Modifier.weight(1f))
        }
        FlowRow(Modifier.editorReadableWidth(), horizontalArrangement = Arrangement.spacedBy(FurySpacing.sm), verticalArrangement = Arrangement.spacedBy(FurySpacing.sm)) {
            EditorCard(Modifier.width(200.dp)) {
                EditorOverline("Размер")
                FuryStepper(character.size, { host.setIdentity(character.name, character.concept, it, character.legs, character.manaEnabled) },
                    min = 1, max = 10, decreaseLabel = "Уменьшить размер", increaseLabel = "Увеличить размер")
                Text("5 — человек", color = p.muted, style = MaterialTheme.typography.labelSmall)
            }
            EditorCard(Modifier.width(200.dp)) {
                EditorOverline("Ноги")
                FuryStepper(character.legs, { host.setIdentity(character.name, character.concept, character.size, it, character.manaEnabled) },
                    min = 2, max = 99, decreaseLabel = "Меньше ног", increaseLabel = "Больше ног")
                Text("влияет на бег", color = p.muted, style = MaterialTheme.typography.labelSmall)
            }
            EditorCard(Modifier.width(200.dp)) {
                EditorOverline("Мана")
                EditorChip(if (character.manaEnabled) "Включена" else "Выключена", character.manaEnabled, {
                    host.setIdentity(character.name, character.concept, character.size, character.legs, !character.manaEnabled)
                }, control, role = androidx.compose.ui.semantics.Role.Switch)
                Text("запас маны на листе", color = p.muted, style = MaterialTheme.typography.labelSmall)
            }
        }
        EditorCard(Modifier.editorReadableWidth(), background = p.goldSoft, border = p.goldLine) {
            EditorOverline("Опыт", color = p.goldText)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(FurySpacing.sm), verticalArrangement = Arrangement.spacedBy(FurySpacing.sm), itemVerticalAlignment = Alignment.CenterVertically) {
                EditorOutlineButton("−100", { host.setExperience((character.experience - 100).coerceAtLeast(0)) }, control)
                ExperienceField(character.experience) { host.setExperience(it) }
                EditorOutlineButton("+100", { host.setExperience(character.experience + 100) }, control)
                Text(
                    if (character.creationComplete) "всего XP · при создании ${character.creationExperience}" else "всего XP · весь опыт идёт на создание",
                    color = p.goldText, style = MaterialTheme.typography.bodySmall,
                )
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(FurySpacing.sm), verticalArrangement = Arrangement.spacedBy(FurySpacing.sm), itemVerticalAlignment = Alignment.CenterVertically) {
                Text("Очки способностей", color = p.text, style = MaterialTheme.typography.labelLarge)
                val budget = data.economy.abilityPointsBudget
                FuryStepper(budget, { next ->
                    host.setEconomy(character.experience, character.creationExperience, character.xpAdjustment, next.coerceAtLeast(0))
                }, min = 0, decreaseLabel = "Меньше очков способностей", increaseLabel = "Больше очков способностей")
                Text(
                    if (character.abilityPointsOverride != null) "задано вручную (по правилам ${data.economy.recommendedAbilityPoints})"
                    else "по правилам: 1 за каждые полные 1000 XP опыта создания",
                    color = p.goldText, style = MaterialTheme.typography.bodySmall,
                )
                if (character.abilityPointsOverride != null) {
                    EditorOutlineButton("вернуть авто", {
                        host.setEconomy(character.experience, character.creationExperience, character.xpAdjustment, null)
                    }, control)
                }
            }
            EditorOutlineButton("Опыт создания и поправка…", { economyOpen = true }, control)
        }
    }
    if (economyOpen) SheetEconomyDialog(sheetEconomyView(host), host, onDismiss = { economyOpen = false })
}

/** Name and concept, saved after a short pause and when the step closes, not on every key. */
@Composable
private fun IdentityFields(host: DublEditorHost, modifier: Modifier) {
    val character = host.active
    var name by remember(character.id) { mutableStateOf(character.name) }
    var concept by remember(character.id) { mutableStateOf(character.concept) }
    val currentHost by rememberUpdatedState(host)
    fun save() {
        val c = currentHost.active
        if (c.name != name || c.concept != concept) currentHost.setIdentity(name, concept, c.size, c.legs, c.manaEnabled)
    }
    LaunchedEffect(name, concept) {
        delay(IDENTITY_SAVE_DELAY_MS)
        save()
    }
    DisposableEffect(character.id) { onDispose { save() } }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(FurySpacing.sm)) {
        FuryTextField(name, { name = it.take(120) }, "Имя")
        FuryTextField(concept, { concept = it.take(200) }, "Концепция")
    }
}

@Composable
private fun ExperienceField(value: Int, onCommit: (Int) -> Unit) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    LaunchedEffect(text) {
        delay(IDENTITY_SAVE_DELAY_MS)
        text.toIntOrNull()?.takeIf { it >= 0 && it != value }?.let(onCommit)
    }
    FuryTextField(
        text, { next -> text = next.filter(Char::isDigit).take(7) }, "Всего XP", Modifier.width(140.dp),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    )
}
