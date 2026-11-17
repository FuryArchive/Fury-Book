from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
DESKTOP_MAIN = 'desktopApp/src/main/kotlin/com/furybook/desktop/Main.kt'
WEB_MAIN = 'webApp/src/jsMain/kotlin/com/furybook/web/Main.kt'


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding='utf-8')


def test_theme_uses_bundled_families():
    type_kt = read('shared/src/commonMain/kotlin/com/furybook/ui/theme/Type.kt')
    for token in ['Res.font.unbounded_black', 'Res.font.unbounded_bold', 'Res.font.onest_regular',
                  'Res.font.onest_medium', 'Res.font.onest_semibold', 'Res.font.onest_bold',
                  'fun furyTypography()', 'fun furyFontsReady()']:
        assert token in type_kt, token
    assert 'typography = furyTypography()' in read('shared/src/commonMain/kotlin/com/furybook/ui/theme/Theme.kt')


def test_shells_do_not_override_font_sizes_and_web_waits_for_fonts():
    for path in [DESKTOP_MAIN, WEB_MAIN]:
        assert 'typography.headlineMedium.copy(' not in read(path), path
    assert 'furyFontsReady()' in read(WEB_MAIN)
