from pathlib import Path
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
SHARED = ROOT / "shared/src/commonMain/kotlin"
HARNESS = ROOT / "tools/tests/kotlin/DevelopmentOverridePersistenceHarness.kt"


def test_development_overrides_and_custom_entries_round_trip_through_snapshot_codec():
    from tools.tests.kotlin_harness_support import run_harness
    result = run_harness(HARNESS)
    assert 'DEVELOPMENT_OVERRIDE_PERSISTENCE_OK' in result.stdout
