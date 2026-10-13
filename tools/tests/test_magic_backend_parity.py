from tools.tests.kotlin_harness_support import assert_application_route
from pathlib import Path
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
SHARED = ROOT / 'shared/src/commonMain/kotlin'
HARNESS = ROOT / 'tools/tests/kotlin/MagicParityHarness.kt'
ANDROID_CONTROLLER = ROOT / 'app/src/main/java/com/furybook/android/state/CharacterController.kt'
DESKTOP_STATE = ROOT / 'desktopApp/src/main/kotlin/com/furybook/desktop/DesktopAppState.kt'


def test_android_and_desktop_magic_route_through_shared_application_capability():
    android = ANDROID_CONTROLLER.read_text(encoding='utf-8')
    desktop = DESKTOP_STATE.read_text(encoding='utf-8')
    mapping = {
        'setMagicManaRank': 'application.magic.setManaRank',
        'setMagicSchoolPower': 'application.magic.setSchoolPower',
        'addMagicSchool': 'application.magic.addSchool',
        'updateMagicSchool': 'application.magic.updateSchool',
        'removeMagicSchool': 'application.magic.removeSchool',
        'addCatalogSpell': 'application.magic.addCatalogSpell',
        'addCustomSpell': 'application.magic.addCustomSpell',
        'updateSpell': 'application.magic.updateSpell',
        'removeSpell': 'application.magic.removeSpell',
        'changeMana': 'application.magic.changeMana',
    }
    for method, call in mapping.items():
        assert f'fun {method}' in android, method
        assert_application_route(android, method, call)
        assert f'fun {method}' in desktop, method
        assert_application_route(desktop, method, call)
    assert 'CharacterSession' not in android + desktop
    assert 'fun mutate(' not in desktop


def test_shared_magic_behavior_harness():
    from tools.tests.kotlin_harness_support import run_harness
    result = run_harness(HARNESS)
    assert 'MAGIC_PARITY_OK' in result.stdout
