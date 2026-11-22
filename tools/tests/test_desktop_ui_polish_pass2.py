from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SCREENS = ROOT / 'desktopApp/src/main/kotlin/com/furybook/desktop/screens'
PRIMITIVES = SCREENS / 'UiPrimitives.kt'
SHEET_DIR = ROOT / 'shared/src/commonMain/kotlin/com/furybook/dubl/ui/sheet'
ROLL = SCREENS / 'RollDialog.kt'
MAIN = ROOT / 'desktopApp/src/main/kotlin/com/furybook/desktop/Main.kt'


def read(path: Path) -> str:
    assert path.exists(), f'missing {path}'
    return path.read_text(encoding='utf-8')


def section(src: str, start: str, end: str) -> str:
    return src.split(start, 1)[1].split(end, 1)[0]


def test_roll_flow_uses_fury_segmented_mode_control_and_selected_attribute_buttons():
    # Stage 3: every skill roll is the shared roll panel: chip groups for attribute and mode, no radio buttons.
    panel = read(SHEET_DIR / 'SheetRollPanel.kt')
    assert 'FuryChipGroup(' in panel
    assert 'RadioButton(' not in panel

def test_economy_dialog_uses_user_facing_russian_labels_and_summary_cards():
    economy = read(SHEET_DIR / 'SheetEconomyDialog.kt')
    assert 'XP adjustment' not in economy
    assert 'ОС override' not in economy
    assert '"Поправка"' in economy
    assert '"ОС вручную"' in economy
    assert 'РАСПРЕДЕЛЕНИЕ ОПЫТА' in economy
    assert 'потрачено' in economy
    assert 'осталось' in economy


def test_conditions_dialog_uses_compact_condition_rows_with_active_highlight_and_edit_action():
    conditions = read(SHEET_DIR / 'SheetConditionsDialog.kt')
    assert 'ConditionRow(' in conditions
    assert 'active = condition in extras.activeConditions || locked' in conditions
    assert 'onEdit = { editor = ConditionEditor.Standard(condition) }' in conditions
    assert 'if (active) DublAccentSoft' in conditions
    assert 'Изменить' in conditions


def test_smoothness_recent_change_is_timed_overlay_not_layout_item():
    # Stage 2b: the sheet's undo toast lives in Main.kt over the shared sheet.
    main = read(MAIN)
    assert 'FuryUndoToast(' in main
    assert 'LaunchedEffect(toast, toastHovered)' in main
    assert 'delay(FuryMotion.UndoToastDurationMs)' in main
    assert 'toastHovered' in main


def test_smoothness_dialog_popover_and_resource_motion_contracts():
    primitives = read(PRIMITIVES)
    assert 'internal object FuryMotion' in primitives
    dialog = section(primitives, 'internal fun FuryDialog(', '\n@Composable\ninternal fun ')
    assert 'animateFloatAsState' in dialog
    assert '.graphicsLayer' in dialog
    assert 'animateContentSize' in dialog
    assert 'FuryMotion.DialogMs' in dialog
    # Stage 2b: the breakdown popover and resource tile went with the old sheet.

def test_smoothness_common_controls_interpolate_and_hover():
    # Stage 6 follow-ups: FurySegmentedControl, FuryChoiceButton, DesktopSmallAction and DesktopInlineAction were
    # deleted with the last screens that used them (Персонажи and Кампания). The shared FuryButton/FuryChipGroup
    # replaced them; what is left of the desktop primitives must not regrow them.
    primitives = read(PRIMITIVES)
    for gone in ('FurySegmentedControl', 'FuryChoiceButton', 'DesktopSmallAction', 'DesktopInlineAction'):
        assert f'fun {gone}(' not in primitives, gone


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
    assert 'remember(character.development, character.developmentOverrides, character.customDevelopmentEntries)' in development


def test_smoothness_top_level_navigation_enters_without_double_composing_heavy_screens():
    main = read(MAIN)
    assert 'AnimatedContent(' not in main
    content = section(main, 'private fun DesktopContent(', '') if False else main.split('private fun DesktopContent(', 1)[1]
    assert 'key(section)' in content
    assert 'animateFloatAsState' in content
    assert '.graphicsLayer' in content
    assert 'FuryMotion.FastMs' in content
    # Navigation now lives in the shared FuryShell; the desktop only hands it the sections.
    assert 'FuryShell(' in main
