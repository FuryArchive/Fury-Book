from pathlib import Path
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
SHARED = ROOT / "shared/src/commonMain/kotlin"
HARNESS = ROOT / "tools/tests/kotlin/ConditionLocalOverrideHarness.kt"


def test_conditions_support_local_override_custom_add_reset_and_delete():
    from tools.tests.kotlin_harness_support import run_harness
    result = run_harness(HARNESS)
    assert 'CONDITION_LOCAL_OVERRIDE_OK' in result.stdout
