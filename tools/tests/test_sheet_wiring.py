import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
DESKTOP = ROOT / 'desktopApp/src/main/kotlin/com/furybook/desktop/Main.kt'
WEB = ROOT / 'webApp/src/jsMain/kotlin/com/furybook/web/Main.kt'
ANDROID = ROOT / 'app/src/main/java/com/furybook/android/ui/DublApp.kt'
SHELL = ROOT / 'shared/src/commonMain/kotlin/com/furybook/ui/components/FuryShell.kt'

OLD_SHEETS = {
    DESKTOP: 'CharacterSheetScreen(',
    WEB: 'CharacterSheetScreen(',
    ANDROID: 'OverviewScreen(',
}


def read(path: Path) -> str:
    return path.read_text(encoding='utf-8')


def editing_branch(text: str) -> str:
    """The body of the first `if (editing) { ... }` block."""
    match = re.search(r'if \(editing\) \{', text)
    assert match, 'no `if (editing) {` branch'
    depth = 0
    for index in range(match.end() - 1, len(text)):
        if text[index] == '{':
            depth += 1
        elif text[index] == '}':
            depth -= 1
            if depth == 0:
                return text[match.end():index]
    raise AssertionError('unbalanced editing branch')


def test_shell_exists_with_the_planned_api():
    shell = read(SHELL)
    for token in [
        'data class FuryShellSection<T>(val value: T, val label: String, val primary: Boolean, val phoneLabel: String? = null)',
        'fun <T> furyBottomTabs(',
        'fun <T> furyMenuSections(',
        'fun <T> FuryShell(',
        'FuryTopTabs(',
        'FuryBottomTabs(',
        'FuryIconButton(',
        '"Меню"',
        'FuryDialogScaffold(',
    ]:
        assert token in shell, token


def test_every_platform_uses_the_shell_and_the_shared_sheet():
    for path in OLD_SHEETS:
        text = read(path)
        assert 'FuryShell(' in text, path.name
        assert 'SheetScreen(' in text, path.name
        assert 'title = "FURY BOOK"' in text, path.name


def test_old_sheet_is_no_longer_called_and_edit_mode_lives_in_the_shared_sheet():
    for path, old_sheet in OLD_SHEETS.items():
        text = read(path)
        assert old_sheet not in text, path.name
        assert 'if (editing) {' not in text, path.name
        assert 'developmentDetails =' in text, path.name
        assert 'portraitLoader =' in text, path.name
        assert 'onOpenMagic =' in text, path.name


def test_new_sheet_changes_still_reach_the_platform_undo_toast():
    for path in OLD_SHEETS:
        text = read(path)
        assert 'SheetChangeReportingHost(' in text, path.name
        assert 'undoLast()' in text, path.name
    assert 'FuryUndoToast(' in read(DESKTOP)
    assert 'FuryUndoToast(' in read(WEB)
    assert 'RecentChangeBar(' in read(ANDROID)


def test_old_navigation_is_gone():
    assert 'CompactNavigation(' not in read(DESKTOP)
    assert 'DesktopRail(' not in read(DESKTOP)
    assert 'MobileBottomNavigation(' not in read(WEB)
    assert 'DublBottomBar(' not in read(ANDROID)


def test_android_keeps_the_development_loading_gate():
    android = read(ANDROID)
    assert 'AppSection.FEATS -> pendingSection = AppSection.FEATS' in android
    assert 'DevelopmentNavigationLoadingScreen(' in android


def test_web_keeps_account_and_sign_out_reachable():
    web = read(WEB)
    assert 'MobileAccountStrip(accountName, accountEmail, onSignOut)' in web


def test_reporting_host_is_rebuilt_per_character_like_its_toast_state():
    # A host remembered across a character switch would write into the previous character's toast.
    expected = {
        DESKTOP: 'remember(state, activeId) {\n        SheetChangeReportingHost(',
        WEB: 'remember(state, activeId) {\n        SheetChangeReportingHost(',
        ANDROID: 'remember(baseHost, activeId) {\n        SheetChangeReportingHost(',
    }
    for path, token in expected.items():
        text = read(path)
        assert token in text, path.name
        assert 'var toast by remember(activeId)' in text, path.name


def test_android_play_sheet_toast_reuses_the_overview_wording():
    assert 'recentResourceChange(change.resource, change.applied' in read(ANDROID)


def test_desktop_and_web_toast_keep_the_slide_and_fade():
    for path in (DESKTOP, WEB):
        text = read(path)
        for token in ['AnimatedVisibility(', 'slideInVertically(', 'fadeOut(', 'displayedToast']:
            assert token in text, (path.name, token)


def test_phone_tab_label_is_short_and_wide_label_full():
    for path in (DESKTOP, WEB):
        text = read(path)
        assert 'phoneLabel = it.phoneLabel' in text, path.name
        assert 'EQUIPMENT("Снаряжение")' in text, path.name
