from pathlib import Path
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
SHARED = ROOT / "shared/src/commonMain/kotlin"
HARNESS = ROOT / "tools/tests/kotlin/DevelopmentUnresolvedEscapeHarness.kt"


def test_incomplete_development_entries_can_be_force_added_instead_of_hard_locked():
    from tools.tests.kotlin_harness_support import run_harness
    result = run_harness(HARNESS)
    assert 'DEVELOPMENT_UNRESOLVED_ESCAPE_OK' in result.stdout
