"""Stage 6 part 2: Мастер replaces Кампания on every platform."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SHARED = ROOT / 'shared/src/commonMain/kotlin/com/furybook/dubl'
HOSTS = {
    'desktop': ROOT / 'desktopApp/src/main/kotlin/com/furybook/desktop/Main.kt',
    'web': ROOT / 'webApp/src/jsMain/kotlin/com/furybook/web/Main.kt',
    'android': ROOT / 'app/src/main/java/com/furybook/android/ui/DublApp.kt',
}
OLD = [
    ROOT / 'desktopApp/src/main/kotlin/com/furybook/desktop/screens/CampaignScreen.kt',
    ROOT / 'webApp/src/jsMain/kotlin/com/furybook/desktop/screens/CampaignScreen.kt',
    ROOT / 'app/src/main/java/com/furybook/android/ui/screens/CampaignScreen.kt',
]


def text(path: Path) -> str:
    assert path.exists(), f'missing {path}'
    return path.read_text(encoding='utf-8')


def test_every_platform_has_master_instead_of_campaign():
    for name, path in HOSTS.items():
        src = text(path)
        assert 'CAMPAIGN' not in src, f'{name} still has a Кампания section'
        assert 'MASTER("Мастер")' in src, f'{name} has no Мастер section'
        assert 'DublMasterScreen(' in src, f'{name} does not render Мастер'
        assert 'MasterStore()' in src or 'MasterStore(' in src, f'{name} has no master store'


def test_old_campaign_screens_are_gone():
    for path in OLD:
        assert not path.exists(), f'{path} should be deleted'


def test_campaign_functions_live_on_in_party_and_characters():
    party = text(SHARED / 'ui/master/PartyTab.kt')
    for call in ['createInvite(', 'removeMember(', 'dashboard(', 'createCampaign(']:
        assert call in party, f'PartyTab.kt lost {call}'
    join = text(SHARED / 'ui/characters/JoinCampaignDialog.kt')
    assert 'joinCampaign(' in join and 'leaveCampaign(' in join


def test_master_data_is_local_json():
    data = text(SHARED / 'master/MasterData.kt')
    assert 'object MasterDataCodec' in data
    for store in [
        ROOT / 'shared/src/desktopMain/kotlin/com/furybook/dubl/data/DesktopMasterStore.kt',
        ROOT / 'app/src/main/java/com/furybook/android/data/AndroidMasterStore.kt',
        ROOT / 'webApp/src/jsMain/kotlin/com/furybook/web/WebMasterStore.kt',
    ]:
        assert 'MasterDataCodec' in text(store)


def test_fight_rows_are_keyed_and_campaigns_can_always_be_left():
    fight = text(SHARED / 'ui/master/FightTab.kt')
    assert 'key(entry.uid) { FightRow(' in fight
    party = text(SHARED / 'ui/master/PartyTab.kt')
    assert 'account.leaveCampaign(campaign.id)' in party


def _body(src: str, start: str) -> str:
    """The braces-balanced block that follows the first line containing [start]."""
    i = src.index(start)
    j = src.index('{', i)
    depth = 0
    for k in range(j, len(src)):
        depth += {'{': 1, '}': -1}.get(src[k], 0)
        if depth == 0:
            return src[j:k + 1]
    raise AssertionError(f'unbalanced block after {start}')


def test_sign_out_never_touches_gm_data():
    """«Выйти» clears the account's characters; Мастер's data belongs to the device and must survive it."""
    blocks = [
        _body(text(ROOT / 'desktopApp/src/main/kotlin/com/furybook/desktop/cloud/DesktopCloudController.kt'), 'override suspend fun signOutDiscarding()'),
        _body(text(ROOT / 'app/src/main/java/com/furybook/android/cloud/AndroidCloudController.kt'), 'override suspend fun signOutDiscarding()'),
        _body(text(ROOT / 'webApp/src/jsMain/kotlin/com/furybook/web/WebAccount.kt'), 'override suspend fun signOutDiscarding()'),
        _body(text(ROOT / 'shared/src/commonMain/kotlin/com/furybook/core/cloud/NativeCloudSync.kt'), 'fun clearLocal()'),
        _body(text(HOSTS['desktop']), 'onSignedOut = {'),
        _body(text(HOSTS['android']), 'onSignedOut = {'),
        _body(text(HOSTS['web']), 'onSignedOut = {'),
        _body(text(ROOT / 'webApp/src/jsMain/kotlin/com/furybook/web/BrowserPersistence.kt'), 'fun clearBrowserSessionData()'),
    ]
    for block in blocks:
        assert 'master' not in block.lower(), block[:200]
    # The browser's sign-out clear only removes the guest keys, never `fury.master`.
    clear = _body(text(ROOT / 'webApp/src/jsMain/kotlin/com/furybook/web/BrowserPersistence.kt'), '    fun clear() {\n        listOf(')
    assert 'SNAPSHOT, EXTRAS, CHI' in clear
