from pathlib import Path

# Stage 2b: the sheet's group manager and roll flow are the shared ones in shared/.../ui/sheet.
ROOT = Path(__file__).resolve().parents[2]
SHEET_DIR = ROOT / 'shared/src/commonMain/kotlin/com/furybook/dubl/ui/sheet'
MANAGER = SHEET_DIR / 'SheetGroupManager.kt'
DRAG = SHEET_DIR / 'SheetGroupDrag.kt'
SESSION = SHEET_DIR / 'SheetRollSession.kt'
SCREEN = SHEET_DIR / 'SheetScreen.kt'


def test_group_manager_has_real_long_press_drag_drop():
    text = MANAGER.read_text(encoding='utf-8') + DRAG.read_text(encoding='utf-8')
    assert 'detectDragGesturesAfterLongPress' in text
    assert '.pointerInput(' in text
    assert 'onGloballyPositioned' in text
    assert 'SheetGroupingRules.moveItems' in text
    assert 'SheetGroupingRules.moveGroupToIndex' in text
    assert 'subtreeBlock' in text


def test_attribute_quick_roll_preserves_selected_attribute():
    session = SESSION.read_text(encoding='utf-8')
    assert 'request.attribute' in session
    assert 'RollContext.ATTRIBUTE' in session


def test_sheet_skills_roll_directly_without_forcing_navigation():
    screen = SCREEN.read_text(encoding='utf-8')
    assert 'open(SheetRollSubject.Skill(skillId))' in screen


def test_context_rolls_show_owned_effect_reminders():
    session = SESSION.read_text(encoding='utf-8')
    assert '.forContext(' in session
    assert 'host.developmentCatalog' in session
    assert 'host.skillEffectCatalog' in session
    assert 'reminders' in session
