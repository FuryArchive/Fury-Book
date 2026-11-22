from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
FONTS = ROOT / 'shared/src/commonMain/composeResources/font'
NAMES = ['unbounded_bold', 'unbounded_black', 'onest_regular', 'onest_medium', 'onest_semibold', 'onest_bold']


def test_static_fonts_are_bundled_with_licences():
    for name in NAMES:
        path = FONTS / f'{name}.ttf'
        assert path.exists(), name
        assert 20_000 < path.stat().st_size < 400_000, name
    assert (FONTS / 'OFL-Unbounded.txt').exists()
    assert (FONTS / 'OFL-Onest.txt').exists()


def test_fonts_cover_cyrillic():
    from fontTools.ttLib import TTFont
    for name in NAMES:
        cmap = TTFont(FONTS / f'{name}.ttf').getBestCmap()
        for ch in 'ШершеньЁё0123456789+−':
            assert ord(ch) in cmap, (name, ch)


def test_shared_declares_compose_resources():
    build = (ROOT / 'shared/build.gradle.kts').read_text(encoding='utf-8')
    assert 'org.jetbrains.compose.components:components-resources:1.12.0' in build
    assert 'publicResClass = true' in build
    assert 'packageOfResClass = "com.furybook.shared.generated.resources"' in build
