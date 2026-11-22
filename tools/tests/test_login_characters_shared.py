"""Stage 6 part 1: login first, the account in the menu, and one shared Персонажи screen."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SHARED = ROOT / 'shared/src/commonMain/kotlin/com/furybook'
HOSTS = {
    'desktop': ROOT / 'desktopApp/src/main/kotlin/com/furybook/desktop/Main.kt',
    'web': ROOT / 'webApp/src/jsMain/kotlin/com/furybook/web/Main.kt',
    'android': ROOT / 'app/src/main/java/com/furybook/android/ui/DublApp.kt',
}
OLD_SCREENS = [
    ROOT / 'desktopApp/src/main/kotlin/com/furybook/desktop/screens/CharactersScreen.kt',
    ROOT / 'webApp/src/jsMain/kotlin/com/furybook/desktop/screens/CharactersScreen.kt',
    ROOT / 'app/src/main/java/com/furybook/android/ui/screens/CharactersScreen.kt',
]


def text(path: Path) -> str:
    assert path.exists(), f'missing {path}'
    return path.read_text(encoding='utf-8')


def test_every_platform_opens_with_the_shared_login_and_characters():
    for name, path in HOSTS.items():
        src = text(path)
        assert 'FuryLoginScreen(' in src, f'{name} has no shared login screen'
        assert 'furyShowsLogin(' in src, f'{name} has no start gate'
        assert 'DublCharactersScreen(' in src, f'{name} has no shared Персонажи'
        assert 'FuryAccountPanel(' in src, f'{name} has no account panel in the menu'


def test_old_character_screens_are_gone():
    for path in OLD_SCREENS:
        assert not path.exists(), f'{path} should be deleted'
    assert 'private fun LoginScreen(' not in text(HOSTS['web'])


def test_shell_has_the_account_entry():
    shell = text(SHARED / 'ui/components/FuryShell.kt')
    assert 'accountEntry: FuryShellAccount?' in shell
    assert 'AccountDot.GOLD' in shell


def test_guest_merge_lives_in_shared_code():
    merge = text(SHARED / 'core/cloud/GuestMerge.kt')
    assert 'fun mergeGuestIntoRemote(' in merge
    assert 'hydrateRemoteMergingGuest' in text(HOSTS['web'])


def test_sign_out_clears_the_archive_list():
    for name, path in HOSTS.items():
        assert 'archive' in text(path).lower(), f'{name} never touches the archive store'


def test_web_password_reset_lands_on_the_reset_page():
    backend = text(ROOT / 'webApp/src/jsMain/kotlin/com/furybook/web/SupabaseBackend.kt')
    assert 'redirectTo = com.furybook.core.cloud.FuryAuthProtocol.RECOVERY_REDIRECT_URL' in backend
    assert 'redirectTo = window.location.origin' not in backend


def test_web_guest_copy_goes_only_after_the_upload():
    merge = text(ROOT / 'webApp/src/jsMain/kotlin/com/furybook/web/WebGuestMerge.kt')
    assert 'guestCopyCanGo(joinedIds, cloud.idsOnServer()' in merge
    # The pull happens before the working copy is cleared, so a failed pull loses nothing.
    assert merge.index('cloud.pull()') < merge.index('clearBrowserSessionData()')
    web = text(HOSTS['web'])
    assert 'suspend fun hydrateRemote() {\n        val joined = hydrateRemoteMergingGuest(cloud)' in web


def test_native_sign_in_outlives_the_login_screen():
    for path in [ROOT / 'desktopApp/src/main/kotlin/com/furybook/desktop/cloud/DesktopCloudController.kt',
                 ROOT / 'app/src/main/java/com/furybook/android/cloud/AndroidCloudController.kt']:
        assert text(path).count('detachedFromCaller(scope) { runCatching {') == 2, path.name
