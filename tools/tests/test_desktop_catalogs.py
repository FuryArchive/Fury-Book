from pathlib import Path
import shutil, subprocess, tempfile, unittest
ROOT=Path(__file__).resolve().parents[2]
SHARED=ROOT/'shared/src/commonMain/kotlin'
LOADER=ROOT/'shared/src/desktopMain/kotlin/com/furybook/desktop/data/DesktopCatalogLoader.kt'
HARNESS=ROOT/'tools/tests/kotlin/DesktopCatalogHarness.kt'
class DesktopCatalogsTest(unittest.TestCase):
    def test_canonical_android_assets_load_for_desktop(self):
        from tools.tests.kotlin_harness_support import run_harness
        result = run_harness(HARNESS, extra_sources=[LOADER], args=[str(ROOT)])
        self.assertIn('DESKTOP_CATALOGS_OK', result.stdout)
if __name__=='__main__': unittest.main()
