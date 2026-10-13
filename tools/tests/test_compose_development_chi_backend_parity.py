from tools.tests.kotlin_harness_support import assert_application_route
from pathlib import Path
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
SHARED = ROOT / 'shared/src/commonMain/kotlin'
HARNESS = ROOT / 'tools/tests/kotlin/DevelopmentChiParityHarness.kt'
ANDROID_CONTROLLER = ROOT / 'app/src/main/java/com/furybook/android/state/CharacterController.kt'
DESKTOP_STATE = ROOT / 'desktopApp/src/main/kotlin/com/furybook/desktop/DesktopAppState.kt'


def test_android_and_desktop_route_development_and_chi_through_shared_application():
    android = ANDROID_CONTROLLER.read_text(encoding='utf-8')
    desktop = DESKTOP_STATE.read_text(encoding='utf-8')
    mapping = {
        'setDevelopmentRank': 'application.development.setRank',
        'setChiEnabled': 'application.development.setChiEnabled',
        'setChiBonusRanks': 'application.development.setChiBonusRanks',
        'restoreChi': 'application.development.restoreChi',
        'changeChi': 'application.development.changeChi',
    }
    for method, call in mapping.items():
        assert_application_route(android, method, call)
        assert_application_route(desktop, method, call)
    assert 'CharacterSession' not in android + desktop
    assert 'fun mutate(' not in desktop


def test_shared_development_martial_and_chi_behavior_harness():
    from tools.tests.kotlin_harness_support import run_harness
    result = run_harness(HARNESS)
    assert 'DEVELOPMENT_CHI_PARITY_OK' in result.stdout
