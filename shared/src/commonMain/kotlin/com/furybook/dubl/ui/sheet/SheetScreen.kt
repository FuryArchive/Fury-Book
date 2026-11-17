package com.furybook.dubl.ui.sheet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.furybook.dubl.model.AttributeId
import com.furybook.dubl.model.CharacterConditionId
import com.furybook.dubl.model.CharacterEconomy
import com.furybook.ui.components.FuryDialogScaffold
import com.furybook.ui.components.FuryRollTray
import com.furybook.ui.theme.DublMuted
import com.furybook.ui.theme.FurySpacing
import com.furybook.ui.theme.furyOverlineStyle

enum class SheetLayout { PHONE, WIDE, WIDE_WITH_TRAY }

/** Below 600dp a phone layout; from 1100dp the roll tray docks on the right. */
fun sheetLayoutFor(widthDp: Int): SheetLayout = when {
    widthDp < 600 -> SheetLayout.PHONE
    widthDp < 1100 -> SheetLayout.WIDE
    else -> SheetLayout.WIDE_WITH_TRAY
}

private val TRAY_WIDTH = 340.dp

// Short labels from the B phone mockup.
private val PHONE_METRIC_LABELS = mapOf(
    "Рефлексы" to "Рефл.",
    "Инициатива" to "Иниц.",
    "Стойкость" to "Стойк.",
    "Размер" to "Разм",
)

private val PHONE_ATTRIBUTE_LABELS = mapOf(
    AttributeId.CONSTITUTION to "Телосл.",
    AttributeId.PERCEPTION to "Воспр.",
)

/** Six metric tiles fit a phone row only with short labels. */
internal fun sheetPhoneMetricLabel(label: String): String = PHONE_METRIC_LABELS[label] ?: label

/** Phone attribute tiles: full titles except the two that do not fit. */
internal fun sheetPhoneAttributeLabel(id: AttributeId): String = PHONE_ATTRIBUTE_LABELS[id] ?: id.title

/**
 * The play view of the character sheet: header, vitals, metrics, attributes, skills, combat.
 * Owns roll state; rolls open in a bottom sheet (phone), a centred dialog (wide) or the docked tray.
 */
@Composable
fun SheetScreen(host: DublSheetHost, onEdit: () -> Unit, onOpenSkills: () -> Unit, modifier: Modifier = Modifier) {
    val state = remember(host.active.id) { SheetScreenState() }
    BoxWithConstraints(modifier.fillMaxSize()) {
        val layout = sheetLayoutFor(maxWidth.value.toInt())
        val header: @Composable (Modifier) -> Unit = { headerModifier -> SheetScreenHeader(host, layout, onEdit, headerModifier) }
        val body: @Composable () -> Unit = { SheetScreenBody(host, layout, state, onOpenSkills) }
        when (layout) {
            SheetLayout.PHONE -> Column(Modifier.fillMaxSize()) {
                // The name stays in view while the rest of the sheet scrolls under it.
                header(Modifier.padding(start = FurySpacing.lg, end = FurySpacing.lg, top = FurySpacing.lg, bottom = FurySpacing.sm))
                SheetScrollColumn(Modifier.weight(1f).fillMaxWidth()) { body() }
            }
            SheetLayout.WIDE -> SheetScrollColumn(Modifier.fillMaxSize()) {
                header(Modifier)
                body()
            }
            SheetLayout.WIDE_WITH_TRAY -> Row(Modifier.fillMaxSize()) {
                SheetScrollColumn(Modifier.weight(1f).fillMaxHeight()) {
                    header(Modifier)
                    body()
                }
                Column(
                    Modifier.width(TRAY_WIDTH).fillMaxHeight().verticalScroll(rememberScrollState())
                        .padding(top = FurySpacing.lg, end = FurySpacing.lg, bottom = FurySpacing.lg),
                ) {
                    SheetScreenRoll(host, state, Modifier.fillMaxWidth())
                }
            }
        }
        if (layout != SheetLayout.WIDE_WITH_TRAY && state.rollOpen && state.request != null) {
            // FuryDialogScaffold scrolls its content and picks sheet or centred panel by width.
            FuryDialogScaffold(title = "Бросок", onDismiss = state::dismiss) {
                SheetScreenRoll(host, state, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun SheetScrollColumn(modifier: Modifier, content: @Composable () -> Unit) {
    Column(
        modifier.verticalScroll(rememberScrollState()).padding(FurySpacing.lg),
        verticalArrangement = Arrangement.spacedBy(FurySpacing.lg),
    ) { content() }
}

@Composable
private fun SheetScreenHeader(host: DublSheetHost, layout: SheetLayout, onEdit: () -> Unit, modifier: Modifier) {
    val character = host.active
    val economy = CharacterEconomy.breakdown(
        character,
        host.developmentCatalog,
        // Desktop/Android pass "the chi pack mounts the XP line"; only the chi pack declares that mount.
        includeChi = host.chiPackEnabled,
    )
    SheetHeader(
        name = character.name,
        concept = character.concept,
        xpFree = economy.remainingXp,
        xpTotal = character.experience,
        abilityText = "ОС ${economy.abilityPointsRemaining}/${economy.abilityPointsBudget}",
        conditions = sheetActiveConditions(host),
        compact = layout == SheetLayout.PHONE,
        onToggleCondition = { chip ->
            if (chip.custom) {
                host.setCustomConditionActive(chip.key, false)
            } else {
                CharacterConditionId.entries.firstOrNull { it.name == chip.key }?.let(host::toggleCondition)
            }
        },
        onEdit = onEdit,
        modifier = modifier,
    )
}

@Composable
private fun SheetScreenBody(host: DublSheetHost, layout: SheetLayout, state: SheetScreenState, onOpenSkills: () -> Unit) {
    val phone = layout == SheetLayout.PHONE
    val character = host.active
    SheetVitals(
        vitals = sheetVitals(host),
        compact = phone,
        onChange = { vital, delta ->
            val resource = vital.resource
            val uid = vital.customUid
            if (resource != null) host.changeResource(resource, delta) else if (uid != null) host.changeCustomResource(uid, delta)
        },
        onRestore = { vital -> vital.resource?.let(host::restoreMountedResource) },
    )
    val metrics = sheetMetrics(character)
    SheetStatGrid(
        stats = if (phone) metrics.map { it.copy(label = sheetPhoneMetricLabel(it.label)) } else metrics,
        columns = when (layout) {
            SheetLayout.PHONE -> 6
            SheetLayout.WIDE -> 3
            SheetLayout.WIDE_WITH_TRAY -> 6
        },
        compact = phone,
        onRoll = state::open,
    )
    SheetSection("Характеристики")
    val attributes = sheetAttributes(character)
    SheetStatGrid(
        // Phone tiles are too narrow for «Телосложение» and «Восприятие».
        stats = if (phone) attributes.map { stat -> stat.copy(label = phoneAttributeLabel(stat)) } else attributes,
        columns = 4,
        compact = phone,
        onRoll = state::open,
    )
    SheetSection("Умения")
    SheetSkills(
        groups = sheetSkillGroups(host),
        columns = if (phone) 1 else 2,
        onRoll = { skillId -> state.open(SheetRollSubject.Skill(skillId)) },
        onOpenAll = onOpenSkills,
    )
    SheetSection("Боевые действия")
    SheetCombat(SHEET_COMBAT_CONTEXTS, onRoll = { context -> state.open(SheetRollSubject.Context(context)) })
}

private fun phoneAttributeLabel(stat: SheetStat): String =
    (stat.roll as? SheetRollSubject.Attribute)?.id?.let(::sheetPhoneAttributeLabel) ?: stat.label

@Composable
private fun SheetSection(title: String) {
    Text(title.uppercase(), Modifier.padding(top = FurySpacing.sm), color = DublMuted, style = furyOverlineStyle())
}

/** The roll panel for the current setup, or the tray's empty state before anything was tapped. */
@Composable
private fun SheetScreenRoll(host: DublSheetHost, state: SheetScreenState, modifier: Modifier) {
    val request = state.request
    if (request == null) {
        FuryRollTray(view = null, history = state.history, onRollAgain = {}, modifier = modifier)
        return
    }
    SheetRollPanel(
        host = host,
        request = request,
        onRequestChange = state::updateRequest,
        result = state.result,
        onRoll = { state.roll(host) },
        onFollowUp = { state.followUp() },
        history = state.history,
        modifier = modifier,
    )
}
