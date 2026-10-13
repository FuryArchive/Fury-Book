from pathlib import Path
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
SHARED = ROOT / "shared/src/commonMain/kotlin"
HARNESS = ROOT / "tools/tests/kotlin/DevelopmentLocalOverrideHarness.kt"


def test_development_canonical_entries_can_be_locally_overridden_and_custom_entries_added():
    from tools.tests.kotlin_harness_support import run_harness
    result = run_harness(HARNESS)
    assert 'DEVELOPMENT_LOCAL_OVERRIDE_OK' in result.stdout
