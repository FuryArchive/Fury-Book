from pathlib import Path
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
SHARED = ROOT / "shared/src/commonMain/kotlin"
HARNESS = ROOT / "tools/tests/kotlin/DevelopmentOverrideSessionHarness.kt"


def test_character_session_owns_development_override_and_custom_mutations():
    from tools.tests.kotlin_harness_support import run_harness
    result = run_harness(HARNESS)
    assert 'DEVELOPMENT_OVERRIDE_SESSION_OK' in result.stdout
