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
import com.furybook.ui.components.FuryDialogAction
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

private val PHONE_ATTRIBUTE_LABELS = mapOf(
    AttributeId.CONSTITUTION to "Телосл.",
    AttributeId.PERCEPTION to "Воспр.",
)

/**
 * The six metric tiles always sit in a 3×2 grid: six phone columns leave a 40dp value box at 360dp
 * («10» is 42dp wide), and six columns next to the docked tray clip «7.5» at 1100dp.
 * Three columns also leave room for the full labels on phones.
 */
@Suppress("UNUSED_PARAMETER")
internal fun sheetMetricColumns(layout: SheetLayout): Int = 3

/** Phone attribute tiles: full titles except the two that do not fit. */
internal fun sheetPhoneAttributeLabel(id: AttributeId): String = PHONE_ATTRIBUTE_LABELS[id] ?: id.title

/**
 * The play view of the character sheet: header, vitals, metrics, attributes, skills, combat.
 * Owns roll state; rolls open in a bottom sheet (phone), a centred dialog (wide) or the docked tray.
 */
@Composable
fun SheetScreen(
    host: DublSheetHost,
    onEdit: () -> Unit,
    onOpenSkills: () -> Unit,
    modifier: Modifier = Modifier,
    portraitLoader: SheetPortraitLoader? = null,
    onPickPortrait: (() -> Unit)? = null,
    /** Details of an owned development entry; platform-owned until stage 4 rebuilds Навыки. */
    developmentDetails: (@Composable (entryId: String, onDismiss: () -> Unit) -> Unit)? = null,
) {
    val state = remember(host.active.id) { SheetScreenState() }
    BoxWithConstraints(modifier.fillMaxSize()) {
        val layout = sheetLayoutFor(maxWidth.value.toInt())
        val header: @Composable (Modifier) -> Unit = { headerModifier -> SheetScreenHeader(host, layout, onEdit, headerModifier, portraitLoader, onPickPortrait) }
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
        val dialogRequest = state.request
        if (layout != SheetLayout.WIDE_WITH_TRAY && state.rollOpen && dialogRequest != null) {
            // FuryDialogScaffold scrolls its content and picks sheet or centred panel by width. The roll
            // button sits in its pinned footer, so «Бросить снова» stays in view under a long result.
            val blocked = sheetRollOptions(host, dialogRequest).blockedReason
            FuryDialogScaffold(
                title = "Бросок",
                onDismiss = state::dismiss,
                primary = FuryDialogAction(sheetRollButtonLabel(state.result != null), { state.roll(host) }, enabled = blocked == null),
            ) {
                SheetScreenRoll(host, state, Modifier.fillMaxWidth(), inlineRollButton = false)
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
private fun SheetScreenHeader(
    host: DublSheetHost,
    layout: SheetLayout,
    onEdit: () -> Unit,
    modifier: Modifier,
    portraitLoader: SheetPortraitLoader?,
    onPickPortrait: (() -> Unit)?,
) {
    val character = host.active
    // Recomputed only when the character, its extras or the chi pack change, not on every recomposition.
    val economy = remember(host, character, host.extras, host.chiPackEnabled) {
        CharacterEconomy.breakdown(
            character,
            host.developmentCatalog,
            // Desktop/Android pass "the chi pack mounts the XP line"; only the chi pack declares that mount.
            includeChi = host.chiPackEnabled,
        )
    }
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
        portrait = if (portraitLoader == null && onPickPortrait == null) null else {
            {
                SheetPortrait(
                    uri = host.extras.portraitUri,
                    name = character.name,
                    loader = portraitLoader,
                    onPick = onPickPortrait,
                    onRemove = null,
                    size = if (layout == SheetLayout.PHONE) 64.dp else 88.dp,
                )
            }
        },
    )
}

@Composable
private fun SheetScreenBody(host: DublSheetHost, layout: SheetLayout, state: SheetScreenState, onOpenSkills: () -> Unit) {
    val phone = layout == SheetLayout.PHONE
    val character = host.active
    // The docked tray shows the roll itself; only taps made without it open the dialog.
    val open: (SheetRollSubject) -> Unit = { subject -> state.open(subject, dialog = layout != SheetLayout.WIDE_WITH_TRAY) }
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
        stats = metrics,
        columns = sheetMetricColumns(layout),
        compact = phone,
        onRoll = open,
    )
    SheetSection("Характеристики")
    val attributes = sheetAttributes(character)
    SheetStatGrid(
        // Phone tiles are too narrow for «Телосложение» and «Восприятие».
        stats = if (phone) attributes.map { stat -> stat.copy(label = phoneAttributeLabel(stat)) } else attributes,
        columns = 4,
        compact = phone,
        onRoll = open,
    )
    SheetSection("Умения")
    val skillGroups = remember(host, character, host.extras, host.chiPackEnabled) { sheetSkillGroups(host) }
    SheetSkills(
        groups = skillGroups,
        columns = if (phone) 1 else 2,
        onRoll = { skillId -> open(SheetRollSubject.Skill(skillId)) },
        onOpenAll = onOpenSkills,
    )
    SheetSection("Боевые действия")
    SheetCombat(SHEET_COMBAT_CONTEXTS, onRoll = { context -> open(SheetRollSubject.Context(context)) })
}

private fun phoneAttributeLabel(stat: SheetStat): String =
    (stat.roll as? SheetRollSubject.Attribute)?.id?.let(::sheetPhoneAttributeLabel) ?: stat.label

@Composable
private fun SheetSection(title: String) {
    Text(title.uppercase(), Modifier.padding(top = FurySpacing.sm), color = DublMuted, style = furyOverlineStyle())
}

/** The roll panel for the current setup, or the tray's empty state before anything was tapped. */
@Composable
private fun SheetScreenRoll(host: DublSheetHost, state: SheetScreenState, modifier: Modifier, inlineRollButton: Boolean = true) {
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
        showRollButton = inlineRollButton,
    )
}
