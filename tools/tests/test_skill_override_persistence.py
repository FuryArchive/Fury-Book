from pathlib import Path
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
SHARED = ROOT / "shared/src/commonMain/kotlin"
HARNESS = ROOT / "tools/tests/kotlin/SkillOverridePersistenceHarness.kt"


def test_skill_local_definition_overrides_round_trip_through_snapshot_codec():
    from tools.tests.kotlin_harness_support import run_harness
    result = run_harness(HARNESS)
    assert 'SKILL_OVERRIDE_PERSISTENCE_OK' in result.stdout
