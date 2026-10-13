from tools.tests.kotlin_harness_support import assert_application_route
from pathlib import Path
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
SHARED = ROOT / 'shared/src/commonMain/kotlin'
HARNESS = ROOT / 'tools/tests/kotlin/EquipmentParityHarness.kt'
ANDROID_CONTROLLER = ROOT / 'app/src/main/java/com/furybook/android/state/CharacterController.kt'
DESKTOP_STATE = ROOT / 'desktopApp/src/main/kotlin/com/furybook/desktop/DesktopAppState.kt'
DESKTOP_SCREEN = ROOT / 'desktopApp/src/main/kotlin/com/furybook/desktop/screens/EquipmentScreen.kt'


def test_android_and_desktop_equipment_use_shared_application_and_rules():
    android = ANDROID_CONTROLLER.read_text(encoding='utf-8')
    desktop_state = DESKTOP_STATE.read_text(encoding='utf-8')
    desktop_screen = DESKTOP_SCREEN.read_text(encoding='utf-8')
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
        assert_application_route(android, method, call)
        assert_application_route(desktop_state, method, call)
    assert 'application.equipment.syncCatalogLoads(magicEquipmentCatalog.gear)' in desktop_state
    assert 'CharacterSession' not in android + desktop_state
    for rule in ('equipmentLoad', 'equipmentCapacity', 'burden', 'catalogGearLoad'):
        assert f'MagicEquipmentRules.{rule}' in desktop_screen, rule


def test_desktop_equipment_does_not_add_android_incompatible_input_caps():
    text = DESKTOP_SCREEN.read_text(encoding='utf-8')
    assert '.take(4)' not in text
    assert '.take(8)' not in text


def test_desktop_equipment_searches_canonical_content_fields_like_android():
    text = DESKTOP_SCREEN.read_text(encoding='utf-8')
    assert 'item.description' in text
    assert 'item.fields.values.joinToString(" ")' in text
    assert 'entry.description' in text
    assert 'entry.fields.values.joinToString(" ")' in text


def test_shared_equipment_behavior_harness():
    from tools.tests.kotlin_harness_support import run_harness
    result = run_harness(HARNESS)
    assert 'EQUIPMENT_PARITY_OK' in result.stdout
