from pathlib import Path
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
SHARED = ROOT / 'shared/src/commonMain/kotlin'
HARNESS = ROOT / 'tools/tests/kotlin/EquipmentParityHarness.kt'
ANDROID_CONTROLLER = ROOT / 'app/src/main/java/com/furybook/android/state/CharacterController.kt'
DESKTOP_STATE = ROOT / 'desktopApp/src/main/kotlin/com/furybook/desktop/DesktopAppState.kt'
# Stage 5: the Снаряжение tab is gone; the rules are read by the sheet's gear block and the editor's Снаряжение step.
SHEET_GEAR = ROOT / 'shared/src/commonMain/kotlin/com/furybook/dubl/ui/sheet/SheetGear.kt'
EDITOR_GEAR = ROOT / 'shared/src/commonMain/kotlin/com/furybook/dubl/ui/editor/EditorGearModels.kt'
EDITOR_GEAR_STEP = ROOT / 'shared/src/commonMain/kotlin/com/furybook/dubl/ui/editor/EditorGearStep.kt'
EDITOR_DIALOGS = ROOT / 'shared/src/commonMain/kotlin/com/furybook/dubl/ui/editor/EditorMagicDialogs.kt'


def test_android_and_desktop_equipment_use_shared_application_and_rules():
    android = ANDROID_CONTROLLER.read_text(encoding='utf-8')
    desktop_state = DESKTOP_STATE.read_text(encoding='utf-8')
    gear_ui = SHEET_GEAR.read_text(encoding='utf-8') + EDITOR_GEAR.read_text(encoding='utf-8') + EDITOR_GEAR_STEP.read_text(encoding='utf-8')
    mapping = {
        'setGearLoadAutomatic': 'application.equipment.setLoadAutomatic',
        'setGearManualLoad': 'application.equipment.setManualLoad',
        'syncCatalogGearLoads': 'application.equipment.syncCatalogLoads',
        'addCatalogGear': 'application.equipment.addCatalog',
        'addCustomGear': 'application.equipment.addCustom',
        'updateGearItem': 'application.equipment.updateItem',
        'removeGearItem': 'application.equipment.removeItem',
    }
    for method, call in mapping.items():
        assert f'fun {method}' in android and call in android, method
        assert f'fun {method}' in desktop_state and call in desktop_state, method
    assert 'application.equipment.syncCatalogLoads(magicEquipmentCatalog.gear)' in desktop_state
    assert 'CharacterSession' not in android + desktop_state
    for rule in ('equipmentLoad', 'equipmentCapacity', 'burden', 'catalogGearLoad'):
        assert f'MagicEquipmentRules.{rule}' in gear_ui, rule


def test_gear_inputs_do_not_add_android_incompatible_input_caps():
    text = EDITOR_GEAR_STEP.read_text(encoding='utf-8') + EDITOR_DIALOGS.read_text(encoding='utf-8')
    assert '.take(4)' not in text
    assert '.take(8)' not in text


def test_gear_searches_canonical_content_fields_like_android():
    text = EDITOR_GEAR.read_text(encoding='utf-8')
    assert 'it.description, it.fields.values.joinToString(" ")' in text
    assert 'it.category, it.description, it.fields.values.joinToString(" ")' in text


def test_shared_equipment_behavior_harness():
    kotlinc = shutil.which('kotlinc')
    assert kotlinc is not None
    sources = sorted((SHARED / 'com/furybook/dubl/model').glob('*.kt'))
    sources += [
        SHARED / 'com/furybook/dubl/data/CharacterStore.kt',
        SHARED / 'com/furybook/dubl/state/CharacterSession.kt',
        HARNESS,
    ]
    with tempfile.TemporaryDirectory() as td:
        jar = Path(td) / 'equipment-parity.jar'
        compiled = subprocess.run(
            [kotlinc, *map(str, sources), '-include-runtime', '-d', str(jar)],
            cwd=ROOT, capture_output=True, text=True,
        )
        assert compiled.returncode == 0, compiled.stderr
        result = subprocess.run(['java', '-jar', str(jar)], cwd=ROOT, capture_output=True, text=True)
        assert result.returncode == 0, result.stderr + result.stdout
        assert 'EQUIPMENT_PARITY_OK' in result.stdout
