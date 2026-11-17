from pathlib import Path

# Stage 2b: the shared sheet replaced CharacterSheetScreen; these behaviours now live in shared/.../ui/sheet.
ROOT = Path(__file__).resolve().parents[2]
SHEET_DIR = ROOT / 'shared/src/commonMain/kotlin/com/furybook/dubl/ui/sheet'
VITAL = (SHEET_DIR / 'SheetVitalDialog.kt').read_text(encoding='utf-8')
SCREEN = (SHEET_DIR / 'SheetScreen.kt').read_text(encoding='utf-8')
PORTRAIT = (SHEET_DIR / 'SheetPortrait.kt').read_text(encoding='utf-8')
DESKTOP_DETAILS = (ROOT / 'desktopApp/src/main/kotlin/com/furybook/desktop/screens/SheetDevelopmentDetails.kt').read_text(encoding='utf-8')
DESKTOP_PORTRAITS = (ROOT / 'desktopApp/src/main/kotlin/com/furybook/desktop/DesktopPortraits.kt').read_text(encoding='utf-8')
DEV = (ROOT / 'desktopApp/src/main/kotlin/com/furybook/desktop/screens/DevelopmentScreen.kt').read_text(encoding='utf-8')


def test_health_has_android_style_amount_control():
    assert 'Получить урон' in VITAL
    assert 'Лечение' in VITAL
    assert 'Восстановить всё здоровье' in VITAL


def test_owned_development_on_sheet_opens_details():
    assert 'SheetDialog.DevelopmentDetails(' in SCREEN
    assert 'DevelopmentDetailsDialog(' in DESKTOP_DETAILS
    assert 'fun DevelopmentDetailsDialog(' in DEV


def test_portrait_is_visible_not_only_filename():
    assert 'Image(' in PORTRAIT
    assert 'loadImageBitmap' in DESKTOP_PORTRAITS
    assert 'SwingPanel' not in PORTRAIT and 'SwingPanel' not in DESKTOP_PORTRAITS
    assert 'portraitUri' in SCREEN
