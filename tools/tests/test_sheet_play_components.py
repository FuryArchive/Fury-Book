from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SHEET = ROOT / 'shared/src/commonMain/kotlin/com/furybook/dubl/ui/sheet'
FILES = ['SheetHeader.kt', 'SheetVitals.kt', 'SheetStats.kt', 'SheetSkills.kt', 'SheetCombat.kt', 'SheetRollPanel.kt']


def read(name: str) -> str:
    return (SHEET / name).read_text(encoding='utf-8')


def test_every_play_component_file_exists():
    for name in FILES:
        assert (SHEET / name).is_file(), name


def test_component_signatures():
    expected = {
        'SheetHeader.kt': 'fun SheetHeader(',
        'SheetVitals.kt': 'fun SheetVitals(',
        'SheetStats.kt': 'fun SheetStatGrid(',
        'SheetSkills.kt': 'fun SheetSkills(',
        'SheetCombat.kt': 'fun SheetCombat(',
        'SheetRollPanel.kt': 'fun SheetRollPanel(',
    }
    for name, signature in expected.items():
        assert signature in read(name), name


def test_header_caps_long_names_at_two_lines():
    header = read('SheetHeader.kt')
    assert 'maxLines = 2' in header
    assert 'TextOverflow.Ellipsis' in header
    assert '"Правка"' in header
    assert 'automatic' in header


def test_vitals_label_their_buttons():
    vitals = read('SheetVitals.kt')
    for token in ['"Уменьшить ', '"Увеличить ', '"Восстановить"', 'FuryBar(', 'restoreable']:
        assert token in vitals, token


def test_skills_have_an_empty_state():
    assert '"Нет изученных умений"' in read('SheetSkills.kt')


def test_roll_panel_covers_every_input():
    panel = read('SheetRollPanel.kt')
    for token in ['RollMode.ADVANTAGE', 'RollMode.HINDRANCE', 'FlurryWeaponProfile', 'followUp', 'blockedReason',
                  'sheetFollowUp(', 'buttonTitle', '"Защита цели"', '"Цель / СЛ"', 'FuryMultiChipGroup', '"Короткий"',
                  '.note', 'reminders', '"Автоматически: "', 'FuryRollTray(']:
        assert token in panel, token


def test_no_hard_coded_colours_under_sheet():
    for path in SHEET.rglob('*.kt'):
        assert 'Color(0x' not in path.read_text(encoding='utf-8'), path.name


def test_blocked_roll_keeps_a_disabled_button_and_shows_the_reason():
    tray = (ROOT / 'shared/src/commonMain/kotlin/com/furybook/ui/components/FuryRollTray.kt').read_text(encoding='utf-8')
    assert 'enabled = blockedReason == null' in tray
    assert 'Text(blockedReason' in tray
    assert 'blockedReason = options.blockedReason' in read('SheetRollPanel.kt')
