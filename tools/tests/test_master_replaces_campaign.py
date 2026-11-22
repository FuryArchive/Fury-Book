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
