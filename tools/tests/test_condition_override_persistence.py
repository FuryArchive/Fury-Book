from pathlib import Path
import os
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
SHARED = ROOT / "shared/src/commonMain/kotlin"
DESKTOP = ROOT / "shared/src/desktopMain/kotlin"
HARNESS = ROOT / "tools/tests/kotlin/ConditionOverridePersistenceHarness.kt"


def test_desktop_condition_overrides_and_custom_conditions_round_trip():
    from tools.tests.kotlin_harness_support import run_harness
    result = run_harness(HARNESS, extra_sources=[ROOT / 'shared/src/desktopMain/kotlin/com/furybook/dubl/data' / name for name in ('DesktopCharacterStore.kt','DesktopCharacterExtrasStore.kt','DesktopAcquisitionStore.kt')], main='com.furybook.dubl.data.ConditionOverridePersistenceHarnessKt')
    assert 'CONDITION_OVERRIDE_PERSISTENCE_OK' in result.stdout


def test_android_extras_repository_persists_condition_overlay():
    text = (ROOT / "app/src/main/java/com/furybook/android/data/CharacterSheetExtrasRepository.kt").read_text()
    assert "condition_overrides" in text
    assert "custom_conditions" in text
    assert "conditionOverrides" in text
    assert "customConditions" in text
