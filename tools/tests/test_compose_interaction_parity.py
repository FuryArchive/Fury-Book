from pathlib import Path

# The sheet's roll flow is the shared one in shared/.../ui/sheet.
ROOT = Path(__file__).resolve().parents[2]
SHEET_DIR = ROOT / 'shared/src/commonMain/kotlin/com/furybook/dubl/ui/sheet'
SESSION = SHEET_DIR / 'SheetRollSession.kt'
SCREEN = SHEET_DIR / 'SheetPlay.kt'


def test_attribute_quick_roll_preserves_selected_attribute():
    session = SESSION.read_text(encoding='utf-8')
    assert 'request.attribute' in session
    assert 'RollContext.ATTRIBUTE' in session


def test_sheet_skills_roll_directly_without_forcing_navigation():
    screen = SCREEN.read_text(encoding='utf-8')
    assert 'open(SheetRollSubject.Skill(it))' in screen


def test_context_rolls_show_owned_effect_reminders():
    session = SESSION.read_text(encoding='utf-8')
    assert '.forContext(' in session
    assert 'host.developmentCatalog' in session
    assert 'host.skillEffectCatalog' in session
    assert 'reminders' in session
