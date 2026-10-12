from pathlib import Path
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
HARNESS = ROOT / "tools/tests/kotlin/DevelopmentAcquisitionHarness.kt"
ANDROID_CONTROLLER = ROOT / "app/src/main/java/com/furybook/android/state/CharacterController.kt"
DESKTOP_STATE = ROOT / "desktopApp/src/main/kotlin/com/furybook/desktop/DesktopAppState.kt"


def test_platform_adapters_expose_the_same_shared_acquisition_operation():
    android = ANDROID_CONTROLLER.read_text(encoding="utf-8")
    desktop = DESKTOP_STATE.read_text(encoding="utf-8")
    for text in (android, desktop):
        assert "fun acquireDevelopment" in text
        assert "application.development.acquire" in text


def test_shared_recursive_choices_batch_rejection_storage_and_single_undo_boundary():
    kotlinc = shutil.which("kotlinc")
    assert kotlinc is not None
    shared = ROOT / "shared/src/commonMain/kotlin"
    sources = sorted((shared / "com/furybook/dubl/model").glob("*.kt"))
    sources += [
        shared / "com/furybook/dubl/data/CharacterStore.kt",
        shared / "com/furybook/dubl/state/CharacterSession.kt",
        shared / "com/furybook/dubl/application/ApplicationUndoManager.kt",
        shared / "com/furybook/dubl/application/DevelopmentApplication.kt",
        HARNESS,
    ]
    with tempfile.TemporaryDirectory() as td:
        jar = Path(td) / "development-acquisition.jar"
        compiled = subprocess.run(
            [kotlinc, "-language-version", "2.0", *map(str, sources), "-include-runtime", "-d", str(jar)],
            cwd=ROOT,
            capture_output=True,
            text=True,
            timeout=90,
        )
        assert compiled.returncode == 0, compiled.stderr
        result = subprocess.run(
            ["java", "-jar", str(jar)],
            cwd=ROOT,
            capture_output=True,
            text=True,
            timeout=30,
        )
        assert result.returncode == 0, result.stderr + result.stdout
        assert "DEVELOPMENT_ACQUISITION_OK" in result.stdout
