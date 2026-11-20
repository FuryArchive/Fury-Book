from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
MAIN = ROOT / 'desktopApp/src/main/kotlin/com/furybook/desktop/Main.kt'
STATE = ROOT / 'desktopApp/src/main/kotlin/com/furybook/desktop/DesktopAppState.kt'
SCREENS = ROOT / 'desktopApp/src/main/kotlin/com/furybook/desktop/screens'


def text(path: Path) -> str:
    assert path.exists(), f'missing {path}'
    return path.read_text(encoding='utf-8')


def test_compose_host_uses_real_shared_sessions_and_file_persistence():
    host = text(MAIN)
    state = text(STATE)
    for token in [
        'DublApplication',
        'DesktopCharacterStore', 'DesktopCharacterExtrasStore',
        'DesktopCatalogLoader',
    ]:
        assert token in state
    assert 'CHARACTERS' in host
    assert 'DesktopAppState' in host
    assert 'InMemoryCharacterStore' not in host + state
    assert 'FeaturePlaceholder' not in host + state


def test_all_parity_screens_exist():
    required = {
        'CharactersScreen.kt': ['CharactersScreen', 'createCharacter', 'selectCharacter', 'deleteActive'],
        # Stage 4: DevelopmentScreen.kt is gone; perks live in the shared editor (test_editor_perk_functions.py).
        # Stage 5: MagicScreen.kt and EquipmentScreen.kt are gone; magic and gear live in the shared editor and sheet.
    }
    for name, tokens in required.items():
        src = text(SCREENS / name)
        for token in tokens:
            assert token in src, f'{name} missing {token}'
    shared = ROOT / 'shared/src/commonMain/kotlin/com/furybook/dubl/ui'
    moved = {
        'editor/EditorMagicStep.kt': ['setMagicManaRank', 'editorStepSchoolPower'],
        'editor/EditorMagicActions.kt': ['addCatalogSpell', 'addCatalogGear'],
        'editor/EditorGearStep.kt': ['setGearLoadAutomatic'],
        'sheet/SheetGear.kt': ['equipmentLoad'],
    }
    for name, tokens in moved.items():
        src = text(shared / name)
        for token in tokens:
            assert token in src, f'{name} missing {token}'
    # Stage 2b: the character sheet is the shared SheetScreen (all platforms).
    sheet = text(ROOT / 'shared/src/commonMain/kotlin/com/furybook/dubl/ui/sheet/SheetScreen.kt')
    assert 'fun SheetScreen(' in sheet
    assert 'EditorScreen(' in sheet, 'SheetScreen.kt opens the character editor'
    play = text(ROOT / 'shared/src/commonMain/kotlin/com/furybook/dubl/ui/sheet/SheetPlay.kt')
    for token in ['SHEET_COMBAT_CONTEXTS', 'displayNotes()']:
        assert token in play, f'SheetPlay.kt missing {token}'


def test_compose_screens_have_no_horizontal_scroll():
    for path in SCREENS.glob('*.kt'):
        assert 'horizontalScroll' not in text(path), path.name


def test_compose_host_does_not_keep_fake_persistence_markers():
    src = text(MAIN)
    assert 'persistentStoreMarker' not in src
    assert 'persistentAdapters' not in src
