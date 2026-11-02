from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
SHARED = ROOT / "shared" / "src" / "commonMain" / "kotlin"
SESSION = SHARED / "com/dubl/character/android/state/CharacterSheetSession.kt"
DESKTOP = ROOT / "desktopApp/src/main/kotlin/com/furybook/desktop/Main.kt"
APP_STATE = ROOT / "desktopApp/src/main/kotlin/com/furybook/desktop/DesktopAppState.kt"
SHEET = ROOT / "desktopApp/src/main/kotlin/com/furybook/desktop/screens/CharacterSheetScreen.kt"
HARNESS = ROOT / "tools/tests/kotlin/CharacterSheetSessionHarness.kt"


class DesktopCharacterSheetWorkflowTest(unittest.TestCase):
    def test_shared_session_behavior_matches_android_sheet_semantics(self):
        kotlinc = shutil.which("kotlinc")
        self.assertIsNotNone(kotlinc, "kotlinc is required for the shared session regression harness")
        self.assertTrue(SESSION.exists(), "CharacterSheetSession must be shared and platform-independent")

        sources = sorted((SHARED / "com/dubl/character/android/model").glob("*.kt"))
        sources += [
            SHARED / "com/dubl/character/android/data/CharacterStore.kt",
            SESSION,
            HARNESS,
        ]
        with tempfile.TemporaryDirectory() as temp:
            jar = Path(temp) / "sheet-session.jar"
            subprocess.run(
                [kotlinc, *map(str, sources), "-include-runtime", "-d", str(jar)],
                cwd=ROOT,
                check=True,
                capture_output=True,
                text=True,
            )
            result = subprocess.run(
                ["java", "-jar", str(jar)],
                cwd=ROOT,
                check=True,
                capture_output=True,
                text=True,
            )
        self.assertIn("CHARACTER_SHEET_SESSION_OK", result.stdout)

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
        self.assertIn("changeHp", sheet)
        self.assertIn("changeEndurance", sheet)
        self.assertNotIn("FoundationSummaryCard", main + sheet)
        self.assertNotIn("InMemoryCharacterStore", main + state + sheet)



if __name__ == "__main__":
    unittest.main()
