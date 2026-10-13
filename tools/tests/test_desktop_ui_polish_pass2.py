from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SCREENS = ROOT / 'desktopApp/src/main/kotlin/com/furybook/desktop/screens'
PRIMITIVES = SCREENS / 'UiPrimitives.kt'
SHEET = SCREENS / 'CharacterSheetScreen.kt'
ROLL = SCREENS / 'RollDialog.kt'
MAIN = ROOT / 'desktopApp/src/main/kotlin/com/furybook/desktop/Main.kt'


def read(path: Path) -> str:
    assert path.exists(), f'missing {path}'
    return path.read_text(encoding='utf-8')


def section(src: str, start: str, end: str) -> str:
    return src.split(start, 1)[1].split(end, 1)[0]


def test_resource_controls_use_one_adjacent_stepper_and_leave_progress_bar_visual_only():
    primitives = read(PRIMITIVES)
    assert 'internal fun FuryStepper(' in primitives
    tile = primitives.split('internal fun DesktopResourceTile', 1)[1]
    assert 'FuryStepper(' in tile
    assert 'onMinus = onMinus' in tile
    assert 'onPlus = onPlus' in tile
    assert 'DesktopTinyButton("−", onMinus' not in tile
    assert 'DesktopTinyButton("+", onPlus' not in tile
    assert 'minusEnabled = current > 0' not in tile
    assert tile.index('FuryStepper(') < tile.index('Box(Modifier.fillMaxWidth().height(5.dp)')


def test_grouping_editor_uses_clear_drag_handles_overflow_actions_and_explicit_create_action():
    sheet = read(SHEET)
    grouping = section(sheet, 'private fun GroupingManagerDialog', '@Composable\nprivate fun DraggableGroupingItem')
    draggable = section(sheet, 'private fun DraggableGroupingItem', 'private fun defaultSkillGroups')
    assert 'GroupingDragHandle(' in grouping
    assert 'GroupActionMenu(' in grouping
    assert 'Text("✎")' not in grouping
    assert 'Text("×")' not in grouping
    assert 'Text("тянуть"' not in draggable
    assert 'GroupingDragHandle(' in draggable
    assert '+ Создать группу' in grouping
    assert 'Перетаскивайте группы и элементы за ручку' in grouping


def test_development_children_have_a_compact_branch_row_distinct_from_roots():
    sheet = read(SHEET)
    tree_row = section(sheet, 'private fun DevelopmentTreeRow', '@Composable\nprivate fun SheetDevelopmentPanel')
    assert 'val child = displayDepth > 0' in tree_row
    assert 'DevelopmentBranchConnector(' in tree_row
    assert 'if (child) 3.dp else 5.dp' in tree_row
    assert 'if (child) DesktopSurfaceInset.copy(alpha = .46f)' in tree_row
    assert 'if (child) 16.dp else 18.dp' in tree_row


def test_roll_flow_uses_fury_segmented_mode_control_and_selected_attribute_buttons():
    primitives = read(PRIMITIVES)
    roll = read(ROLL)
    assert 'internal fun FurySegmentedControl(' in primitives
    assert 'internal fun FuryChoiceButton(' in primitives
    skill_roll = section(roll, 'fun SkillRollDialog', '@Composable\nfun ContextRollDialog')
    choice = section(roll, 'fun SkillAttributeChoiceDialog', '@Composable\nfun SkillRollDialog')
    assert 'FurySegmentedControl(' in skill_roll
    assert 'RadioButton(' not in skill_roll
    assert 'FuryChoiceButton(' in choice
    assert 'selected = selectedAttribute == option' in choice
    assert '"Бросить ${calculation.total?.let(::signed) ?: "—"}"' in choice


def test_economy_dialog_uses_user_facing_russian_labels_and_summary_cards():
    sheet = read(SHEET)
    economy = section(sheet, 'private fun EconomyDialog', '@Composable\nprivate fun MaximumDialog')
    assert 'XP adjustment' not in economy
    assert 'Text("ОС override' not in economy
    assert 'Корректировка опыта' in economy
    assert 'Очки способностей вручную' in economy
    assert 'EconomySummaryCard(' in economy
    assert 'Потрачено' in economy
    assert 'Осталось' in economy


def test_conditions_dialog_uses_compact_condition_rows_with_active_highlight_and_hover_action():
    sheet = read(SHEET)
    conditions = section(sheet, 'private fun ConditionsDialog', '@Composable\nprivate fun ConditionOverrideDialog')
    assert 'ConditionRow(' in conditions
    assert 'active = condition in state.extras.activeConditions' in conditions
    assert 'onEdit = { editCondition = condition }' in conditions
    row = section(sheet, 'private fun ConditionRow', '@Composable\nprivate fun ConditionsDialog')
    assert 'pointerMoveFilter' in row
    assert 'maxLines = 3' in row
    assert 'if (active)' in row
    assert 'Изменить' in row


def test_character_sheet_imports_layout_size_when_modifier_size_is_used():
    sheet = read(SHEET)
    assert 'Modifier.size(' in sheet
    assert 'import androidx.compose.foundation.layout.size' in sheet


def test_smoothness_recent_change_is_timed_overlay_not_layout_item():
    sheet = read(SHEET)
    root = section(sheet, 'fun CharacterSheetScreen(', '@Composable\nprivate fun CharacterHero')
    assert 'FuryUndoToast(' in root
    assert 'LaunchedEffect(recent' in root
    assert 'delay(FuryMotion.UndoToastDurationMs)' in root
    assert 'recentHovered' in root
    lazy = root.split('LazyColumn(', 1)[1].split('AnimatedVisibility(', 1)[0]
    assert 'if (recent != null)' not in lazy
    assert 'FuryUndoToast(' not in lazy


def test_smoothness_dialog_popover_and_resource_motion_contracts():
    primitives = read(PRIMITIVES)
    assert 'internal object FuryMotion' in primitives
    dialog = section(primitives, 'internal fun FuryDialog(', '@Composable\ninternal fun DesktopPanel')
    assert 'animateFloatAsState' in dialog
    assert '.graphicsLayer' in dialog
    assert 'animateContentSize' in dialog
    assert 'FuryMotion.DialogMs' in dialog
    popover = section(primitives, 'private fun FuryBreakdownPopover(', '@Composable\ninternal fun DesktopResourceTile')
    assert 'MutableTransitionState' in popover
    assert 'AnimatedVisibility(' in popover
    assert 'fadeIn(' in popover and 'fadeOut(' in popover
    resource = primitives.split('internal fun DesktopResourceTile', 1)[1]
    assert 'animateFloatAsState' in resource
    assert 'fillMaxWidth(animatedFraction)' in resource
    assert 'if (fraction > 0f)' not in resource


def test_smoothness_common_controls_interpolate_and_hover():
    primitives = read(PRIMITIVES)
    segmented = section(primitives, 'internal fun FurySegmentedControl(', '@Composable\ninternal fun FuryChoiceButton')
    choice = section(primitives, 'internal fun FuryChoiceButton(', '@OptIn(ExperimentalComposeUiApi::class)\n@Composable\ninternal fun DesktopIconButton')
    assert 'animateColorAsState' in segmented
    assert 'animateColorAsState' in choice
    for start, end in (
        ('internal fun DesktopSmallAction(', '@OptIn(ExperimentalComposeUiApi::class)\n@Composable\ninternal fun DesktopInlineAction'),
        ('internal fun DesktopInlineAction(', '@Composable\ninternal fun DesktopConditionChip'),
        ('internal fun DesktopTinyButton(', '@Composable\ninternal fun FuryStepper'),
        ('internal fun FuryStepper(', '@Composable\ninternal fun FurySegmentedControl'),
        ('internal fun DesktopIconButton(', '@Composable\ninternal fun DesktopDenseAttributeRow'),
    ):
        control = section(primitives, start, end)
        assert 'pointerMoveFilter' in control
        assert 'animateColorAsState' in control


def test_smoothness_groups_animate_and_hot_derivations_are_remembered():
    sheet = read(SHEET)
    skills = section(sheet, 'private fun SheetSkillsPanel', 'private fun skillIcon')
    development = section(sheet, 'private fun SheetDevelopmentPanel', 'private fun DevelopmentDetailsDialog')
    grouping = section(sheet, 'private fun GroupingManagerDialog', '@Composable\nprivate fun DraggableGroupingItem')
    for block in (skills, development, grouping):
        assert 'AnimatedVisibility(' in block
        assert 'expandVertically(' in block
        assert 'shrinkVertically(' in block
    assert 'remember(character.skills, character.hiddenSkillIds)' in skills
    assert 'SkillEffectRules(' in skills
    assert 'remember(character.development, character.magic, character.developmentOverrides, character.customDevelopmentEntries)' in development


def test_smoothness_top_level_navigation_enters_without_double_composing_heavy_screens():
    main = read(MAIN)
    assert 'AnimatedContent(' not in main
    content = section(main, 'private fun DesktopContent(', '') if False else main.split('private fun DesktopContent(', 1)[1]
    assert 'key(section)' in content
    assert 'animateFloatAsState' in content
    assert '.graphicsLayer' in content
    assert 'FuryMotion.FastMs' in content
    navigation = section(main, 'private fun NavigationItem', 'private val DesktopSection.iconKind')
    assert 'animateColorAsState' in navigation


def test_smoothness_skill_roll_presentations_are_memoized_away_from_resource_updates():
    sheet = read(SHEET)
    skills = section(sheet, 'private fun SheetSkillsPanel', 'private fun skillIcon')
    assert 'val skillPresentations = remember(' in skills
    assert 'character.attributes' in skills
    assert 'character.gear' in skills
    assert 'character.hpCurrent' not in skills.split('val skillPresentations = remember(', 1)[1].split(') {', 1)[0]
    assert 'SheetSkillPresentation(' in skills
