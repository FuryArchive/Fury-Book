from pathlib import Path
import os, shutil, subprocess, tempfile, unittest
ROOT=Path(__file__).resolve().parents[2]
SHARED=ROOT/'shared/src/commonMain/kotlin'
DESKTOP_DIR=ROOT/'shared/src/desktopMain/kotlin/com/furybook/dubl/data'
HARNESS=ROOT/'tools/tests/kotlin/DesktopPersistenceHarness.kt'
class DesktopPersistenceTest(unittest.TestCase):
    def test_round_trip_snapshot_and_extras(self):
        from tools.tests.kotlin_harness_support import run_harness
        result = run_harness(HARNESS, extra_sources=[ROOT / 'shared/src/desktopMain/kotlin/com/furybook/dubl/data' / name for name in ('DesktopCharacterStore.kt','DesktopCharacterExtrasStore.kt','DesktopAcquisitionStore.kt')])
        self.assertIn('DESKTOP_PERSISTENCE_OK', result.stdout)
if __name__=='__main__': unittest.main()
