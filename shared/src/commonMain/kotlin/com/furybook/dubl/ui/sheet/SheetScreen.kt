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
import com.furybook.dubl.model.displayNotes
import com.furybook.dubl.model.SheetGroupingRules
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
 * The character sheet: play view, and the same screen in edit mode behind «Правка». Owns roll state,
 * the open popup and the mode; rolls open in a bottom sheet (phone), a centred dialog (wide) or the
 * docked tray. Platform pieces come in as callbacks: portrait loading/picking and development details.
 */
@Composable
fun SheetScreen(
    host: DublSheetEditHost,
    onOpenSkills: () -> Unit,
    onOpenDevelopment: () -> Unit,
    modifier: Modifier = Modifier,
    portraitLoader: SheetPortraitLoader? = null,
    onPickPortrait: (() -> Unit)? = null,
    /** Details of an owned development entry; platform-owned until stage 4 rebuilds Навыки. */
    developmentDetails: (@Composable (entryId: String, onDismiss: () -> Unit) -> Unit)? = null,
    /** Android passes its BackHandler so system Back leaves edit mode instead of the app. */
    backHandler: @Composable (enabled: Boolean, onBack: () -> Unit) -> Unit = { _, _ -> },
) {
    val state = remember { SheetScreenState() }
    state.syncCharacter(host.active.id)
    backHandler(state.editing) { state.updateEditing(false) }
    BoxWithConstraints(modifier.fillMaxSize()) {
        val layout = sheetLayoutFor(maxWidth.value.toInt())
        val header: @Composable (Modifier) -> Unit = { headerModifier ->
            SheetScreenHeader(host, layout, state, headerModifier, portraitLoader, onPickPortrait)
        }
        val body: @Composable () -> Unit = { SheetScreenBody(host, layout, state, onOpenSkills, onOpenDevelopment) }
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
        SheetScreenDialog(host, layout, state, developmentDetails)
    }
}

/** The popup [SheetScreenState.dialog] names, if any. */
@Composable
private fun SheetScreenDialog(
    host: DublSheetEditHost,
    layout: SheetLayout,
    state: SheetScreenState,
    developmentDetails: (@Composable (String, () -> Unit) -> Unit)?,
) {
    val close = state::closeDialog
    when (val dialog = state.dialog) {
        null -> Unit
        is SheetDialog.Vital -> sheetVitals(host).firstOrNull { it.id == dialog.vitalId }
            ?.let { vital -> SheetVitalDialog(vital, host, close) } ?: close()
        is SheetDialog.Stat -> SheetStatDialog(
            breakdown = sheetStatBreakdown(host.active, dialog.stat),
            onRoll = dialog.roll?.let { subject -> { state.open(subject, dialog = layout != SheetLayout.WIDE_WITH_TRAY) } },
            sizeLegsEditor = dialog.stat == SheetStatId.SIZE,
            host = host,
            onDismiss = close,
        )
        SheetDialog.Conditions -> SheetConditionsDialog(host, close)
        SheetDialog.Identity -> SheetIdentityDialog(host, close)
        SheetDialog.Economy -> SheetEconomyDialog(sheetEconomyView(host), host, close)
        SheetDialog.Resources -> SheetResourcesDialog(host, close)
        is SheetDialog.Groups -> SheetGroupManager(dialog.kind, host, close)
        is SheetDialog.DevelopmentDetails ->
            developmentDetails?.invoke(dialog.entryId, close) ?: SheetDevelopmentDetailsDialog(host, dialog.entryId, close)
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
    host: DublSheetEditHost,
    layout: SheetLayout,
    state: SheetScreenState,
    modifier: Modifier,
    portraitLoader: SheetPortraitLoader?,
    onPickPortrait: (() -> Unit)?,
) {
    val character = host.active
    val editing = state.editing
    // Recomputed only when the character, its extras, the chi pack or the catalog change.
    val includeChi = sheetChiEconomyMounted(host)
    val economy = remember(host, character, host.extras, includeChi, host.developmentCatalog) {
        // Chi XP counts only when a pack mounts the chi feature on the economy XP line (FCP contract).
        CharacterEconomy.breakdown(character, host.developmentCatalog, includeChi = includeChi)
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
        onEdit = { state.updateEditing(!editing) },
        modifier = modifier,
        onOpenConditions = { state.openDialog(SheetDialog.Conditions) },
        portrait = if (portraitLoader == null && onPickPortrait == null && host.extras.portraitUri == null) null else {
            {
                SheetPortrait(
                    uri = host.extras.portraitUri,
                    name = character.name,
                    loader = portraitLoader,
                    onPick = onPickPortrait.takeIf { editing },
                    onRemove = if (editing) ({ host.setPortrait(null) }) else null,
                    size = if (layout == SheetLayout.PHONE) 64.dp else 88.dp,
                )
            }
        },
        onOpenIdentity = if (editing) ({ state.openDialog(SheetDialog.Identity) }) else null,
        onOpenEconomy = { state.openDialog(SheetDialog.Economy) },
        editing = editing,
    )
}

@Composable
private fun SheetScreenBody(
    host: DublSheetEditHost,
    layout: SheetLayout,
    state: SheetScreenState,
    onOpenSkills: () -> Unit,
    onOpenDevelopment: () -> Unit,
) {
    val phone = layout == SheetLayout.PHONE
    val editing = state.editing
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
        onOpen = { vital -> state.openDialog(SheetDialog.Vital(vital.id)) },
        onManageResources = if (editing) ({ state.openDialog(SheetDialog.Resources) }) else null,
    )
    SheetStatGrid(
        stats = sheetMetrics(character),
        columns = sheetMetricColumns(layout),
        compact = phone,
        onRoll = open,
        onOpenInfo = { stat -> stat.info?.let { state.openDialog(SheetDialog.Stat(it, stat.roll)) } },
    )
    SheetSection("Характеристики")
    val attributes = sheetAttributes(character)
    SheetStatGrid(
        // Phone tiles are too narrow for «Телосложение» and «Восприятие».
        stats = if (phone) attributes.map { stat -> stat.copy(label = phoneAttributeLabel(stat)) } else attributes,
        // Edit mode puts −/+ under each value; two phone columns leave room for both buttons.
        columns = if (phone && editing) 2 else 4,
        compact = phone,
        onRoll = open,
        onAdjust = if (editing) ({ stat, delta ->
            (stat.roll as? SheetRollSubject.Attribute)?.let { host.changeAttribute(it.id, delta) }
        }) else null,
    )
    SheetSection("Умения")
    val skillGroups = remember(host, character, host.extras, host.developmentCatalog, editing) {
        if (editing) sheetSkillGroupsForEdit(host) else sheetSkillGroups(host)
    }
    SheetSkills(
        groups = skillGroups,
        columns = if (phone) 1 else 2,
        onRoll = { skillId -> open(SheetRollSubject.Skill(skillId)) },
        onOpenAll = onOpenSkills,
        onToggleCollapsed = { id -> host.setSkillGroups(SheetGroupingRules.toggleCollapsed(normalizedSkillGroups(host), id)) },
        onManageGroups = if (editing) ({ state.openDialog(SheetDialog.Groups(SheetGroupKind.SKILLS)) }) else null,
    )
    SheetSection("Взятые навыки")
    val developmentGroups = remember(host, character, host.extras, host.developmentCatalog) { sheetDevelopmentGroups(host) }
    SheetDevelopment(
        groups = developmentGroups,
        onOpenDetails = { id -> state.openDialog(SheetDialog.DevelopmentDetails(id)) },
        onOpenAll = onOpenDevelopment,
        onManageGroups = if (editing) ({ state.openDialog(SheetDialog.Groups(SheetGroupKind.DEVELOPMENT)) }) else null,
        onToggleCollapsed = { id -> host.setDevelopmentGroups(SheetGroupingRules.toggleCollapsed(normalizedDevelopmentGroups(host), id)) },
    )
    SheetSection("Боевые действия")
    SheetCombat(SHEET_COMBAT_CONTEXTS, onRoll = { context -> open(SheetRollSubject.Context(context)) })
    SheetSection("Заметки")
    SheetNotes(host.extras.displayNotes(), host = host.takeIf { editing || !phone })
}

private fun phoneAttributeLabel(stat: SheetStat): String =
    (stat.roll as? SheetRollSubject.Attribute)?.id?.let(::sheetPhoneAttributeLabel) ?: stat.label

@Composable
private fun SheetSection(title: String) {
    Text(title.uppercase(), Modifier.padding(top = FurySpacing.sm), color = DublMuted, style = furyOverlineStyle())
}

/** The roll panel for the current setup, or the tray's empty state before anything was tapped. */
@Composable
private fun SheetScreenRoll(host: DublSheetEditHost, state: SheetScreenState, modifier: Modifier, inlineRollButton: Boolean = true) {
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
