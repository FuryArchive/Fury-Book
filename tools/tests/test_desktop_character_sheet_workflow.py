from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
SHARED = ROOT / "shared" / "src" / "commonMain" / "kotlin"
SESSION = SHARED / "com/furybook/dubl/state/CharacterSheetSession.kt"
DESKTOP = ROOT / "desktopApp/src/main/kotlin/com/furybook/desktop/Main.kt"
APP_STATE = ROOT / "desktopApp/src/main/kotlin/com/furybook/desktop/DesktopAppState.kt"
SHEET = ROOT / "desktopApp/src/main/kotlin/com/furybook/desktop/screens/CharacterSheetScreen.kt"
PORTABLE = ROOT / "packaging/linux/portable-src/com/dubl/character/portable/Main.kt"
HARNESS = ROOT / "tools/tests/kotlin/CharacterSheetSessionHarness.kt"


class DesktopCharacterSheetWorkflowTest(unittest.TestCase):
    def test_shared_session_behavior_matches_android_sheet_semantics(self):
        from tools.tests.kotlin_harness_support import run_harness
        result = run_harness(HARNESS)
        self.assertIn('CHARACTER_SHEET_SESSION_OK', result.stdout)

    def test_compose_desktop_sheet_is_interactive(self):
        main = DESKTOP.read_text(encoding="utf-8")
        state = APP_STATE.read_text(encoding="utf-8")
        sheet = SHEET.read_text(encoding="utf-8")

        self.assertIn("DesktopAppState", main)
        self.assertIn("CharacterSheetScreen", main)
        self.assertIn("DesktopCharacterStore", state)
        self.assertIn("DublApplication", state)
        self.assertNotIn("CharacterSession", state)
        self.assertIn("IdentityDialog", sheet)
        self.assertIn("changeAttribute", sheet)
        self.assertIn("state.changeResource(", sheet)
        self.assertIn("CharacterSheetResourceId.HEALTH -> changeHp(delta)", state)
        self.assertIn("CharacterSheetResourceId.ENDURANCE -> changeEndurance(delta)", state)
        self.assertNotIn("FoundationSummaryCard", main + sheet)
        self.assertNotIn("InMemoryCharacterStore", main + state + sheet)

    def test_portable_clickable_release_uses_same_shared_session(self):
        text = PORTABLE.read_text(encoding="utf-8")
        self.assertIn("CharacterSession", text)
        window = (ROOT / "packaging/linux/portable-src/com/dubl/character/portable/DublWindow.kt").read_text(encoding="utf-8")
        self.assertIn("showIdentityDialog", window)
        self.assertIn("changeAttribute", window)
        self.assertIn("changeHp", window)
        self.assertNotIn("Shared foundation", text)


if __name__ == "__main__":
    unittest.main()
