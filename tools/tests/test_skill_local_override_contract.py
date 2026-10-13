from pathlib import Path
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
SHARED = ROOT / "shared/src/commonMain/kotlin"
HARNESS = ROOT / "tools/tests/kotlin/SkillLocalOverrideHarness.kt"


def test_builtin_skill_supports_local_override_and_reset_without_losing_progression():
    from tools.tests.kotlin_harness_support import run_harness
    result = run_harness(HARNESS)
    assert 'SKILL_LOCAL_OVERRIDE_OK' in result.stdout
