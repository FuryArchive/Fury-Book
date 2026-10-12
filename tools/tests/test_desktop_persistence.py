from pathlib import Path
import os, shutil, subprocess, tempfile, unittest
ROOT=Path(__file__).resolve().parents[2]
SHARED=ROOT/'shared/src/commonMain/kotlin'
DESKTOP_DIR=ROOT/'shared/src/desktopMain/kotlin/com/furybook/dubl/data'
HARNESS=ROOT/'tools/tests/kotlin/DesktopPersistenceHarness.kt'
class DesktopPersistenceTest(unittest.TestCase):
    def test_round_trip_snapshot_and_extras(self):
        kotlinc=shutil.which('kotlinc'); self.assertIsNotNone(kotlinc)
        # Model/legacy codecs now share the canonical acquisition/application types.
        # Compile the real public dependency closure rather than omitting those sources.
        sources=sorted(path for path in SHARED.rglob('*.kt') if '/ui/' not in path.as_posix())
        sources += [DESKTOP_DIR/'DesktopCharacterStore.kt', DESKTOP_DIR/'DesktopCharacterExtrasStore.kt',
                    DESKTOP_DIR/'DesktopAcquisitionStore.kt', HARNESS]
        coroutines=Path(kotlinc).resolve().parents[1]/'lib/kotlinx-coroutines-core-jvm.jar'
        self.assertTrue(coroutines.is_file(), 'Kotlin distribution must include its coroutine runtime')
        with tempfile.TemporaryDirectory() as td:
            jar=Path(td)/'persist.jar'
            r=subprocess.run([kotlinc,'-cp',str(coroutines),*map(str,sources),'-include-runtime','-d',str(jar)],cwd=ROOT,capture_output=True,text=True)
            self.assertEqual(0,r.returncode,r.stderr)
            run=subprocess.run(['java','-cp',os.pathsep.join((str(jar),str(coroutines))),'DesktopPersistenceHarnessKt'],cwd=ROOT,capture_output=True,text=True)
            self.assertEqual(0,run.returncode,run.stderr)
            self.assertIn('DESKTOP_PERSISTENCE_OK',run.stdout)
if __name__=='__main__': unittest.main()
