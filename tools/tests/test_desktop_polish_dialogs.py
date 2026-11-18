from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SCREENS = ROOT / 'desktopApp/src/main/kotlin/com/furybook/desktop/screens'
PRIMITIVES = SCREENS / 'UiPrimitives.kt'


def read(path: Path) -> str:
    assert path.exists(), f'missing {path}'
    return path.read_text(encoding='utf-8')


def test_fury_dialog_primitive_owns_desktop_modal_visual_language():
    primitives = read(PRIMITIVES)
    assert 'internal fun FuryDialog(' in primitives
    fury_dialog = primitives.split('internal fun FuryDialog', 1)[1].split('internal fun DesktopPanel', 1)[0]

    assert 'BasicAlertDialog(' in fury_dialog
    assert 'DesktopSurfaceRaised' in fury_dialog
    assert 'DesktopBorder' in fury_dialog
    assert 'RoundedCornerShape(12.dp)' in fury_dialog
    assert '.widthIn(min = 360.dp, max = 620.dp)' in fury_dialog
    assert '.heightIn(max = 680.dp)' in fury_dialog
    assert 'rememberScrollState()' in fury_dialog
    assert '.verticalScroll(contentScroll)' in fury_dialog
    assert 'ProvideTextStyle(MaterialTheme.typography.titleLarge)' in fury_dialog
    assert fury_dialog.count('LocalContentColor provides DesktopText') >= 2
    assert 'ProvideTextStyle(MaterialTheme.typography.bodyMedium)' in fury_dialog


def test_all_desktop_feature_dialogs_use_fury_dialog_instead_of_material_alert_dialog():
    offenders = []
    users = []
    for path in sorted(SCREENS.glob('*.kt')):
        if path.name == 'UiPrimitives.kt':
            continue
        src = read(path)
        if 'AlertDialog(' in src or 'import androidx.compose.material3.AlertDialog' in src:
            offenders.append(path.name)
        if 'FuryDialog(' in src:
            users.append(path.name)

    assert not offenders, f'legacy Material AlertDialog remains in: {offenders}'
    assert {'DevelopmentScreen.kt', 'MagicScreen.kt', 'EquipmentScreen.kt', 'CharactersScreen.kt'} <= set(users)
