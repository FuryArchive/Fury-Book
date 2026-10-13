from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
APP = (ROOT / 'app/src/main/java/com/furybook/android/ui/DublApp.kt').read_text(encoding='utf-8')


def test_feats_navigation_renders_loading_frame_before_mounting_heavy_screen():
    assert 'pendingSection' in APP
    assert 'AppSection.FEATS -> pendingSection = AppSection.FEATS' in APP
    assert 'DevelopmentNavigationLoadingScreen(' in APP
    assert 'withFrameNanos' in APP

    effect = APP.split('LaunchedEffect(pendingSection)', 1)[1].split('Scaffold(', 1)[0]
    assert effect.index('withFrameNanos') < effect.index('selected = AppSection.FEATS')

    content = APP.split(') { innerPadding ->', 1)[1]
    assert 'if (pendingSection == AppSection.FEATS)' in content
    assert content.index('DevelopmentNavigationLoadingScreen(') < content.index('AppSection.FEATS -> FeatsScreen(controller, chiPackEnabled)')
