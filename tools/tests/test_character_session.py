from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
SHARED = ROOT / "shared/src/commonMain/kotlin"
SESSION = SHARED / "com/dubl/character/android/state/CharacterSession.kt"
HARNESS = ROOT / "tools/tests/kotlin/CharacterSessionHarness.kt"

class CharacterSessionBehaviorTest(unittest.TestCase):
    def test_shared_session_cross_feature_behavior(self):
        from tools.tests.kotlin_harness_support import run_harness
        result = run_harness(HARNESS)
        self.assertIn('CHARACTER_SESSION_OK', result.stdout)

if __name__ == "__main__":
    unittest.main()
