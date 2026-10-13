from pathlib import Path
import os
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
HARNESS = ROOT / "tools/tests/kotlin/DevelopmentAcquisitionHarness.kt"
ANDROID_CONTROLLER = ROOT / "app/src/main/java/com/furybook/android/state/CharacterController.kt"
DESKTOP_STATE = ROOT / "desktopApp/src/main/kotlin/com/furybook/desktop/DesktopAppState.kt"


def test_platform_adapters_expose_the_same_shared_acquisition_operation():
    android = ANDROID_CONTROLLER.read_text(encoding="utf-8")
    desktop = DESKTOP_STATE.read_text(encoding="utf-8")
    for text in (android, desktop):
        assert "fun acquireDevelopment" in text
        assert "application.development.acquire" in text


def test_legacy_planner_requires_canonical_context_and_cannot_write_or_create_undo():
    from tools.tests.kotlin_harness_support import run_harness
    result = run_harness(HARNESS)
    assert 'DEVELOPMENT_ACQUISITION_OK' in result.stdout
