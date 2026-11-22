from pathlib import Path
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
CODEC = ROOT / "shared/src/commonMain/kotlin/com/furybook/dubl/data/CharacterTransferCodec.kt"
APP = ROOT / "shared/src/commonMain/kotlin/com/furybook/dubl/application/DublApplication.kt"
TRANSFER_APP = ROOT / "shared/src/commonMain/kotlin/com/furybook/dubl/application/CharacterTransferApplication.kt"
ANDROID_CONTROLLER = ROOT / "app/src/main/java/com/furybook/android/state/CharacterController.kt"
# Stage 6: the per-platform Персонажи screens are gone; the shared screen owns the buttons and copy.
ANDROID_SCREEN = ROOT / "app/src/main/java/com/furybook/android/ui/AndroidCharacters.kt"
SHARED_SCREEN = ROOT / "shared/src/commonMain/kotlin/com/furybook/dubl/ui/characters/CharactersScreen.kt"
SHARED_MODELS = ROOT / "shared/src/commonMain/kotlin/com/furybook/dubl/ui/characters/CharactersModels.kt"
DESKTOP_STATE = ROOT / "desktopApp/src/main/kotlin/com/furybook/desktop/DesktopAppState.kt"
DESKTOP_SCREEN = ROOT / "desktopApp/src/main/kotlin/com/furybook/desktop/DesktopFiles.kt"
WORKFLOW = ROOT / ".github/workflows/linux-appimage.yml"
SHARED = ROOT / "shared/src/commonMain/kotlin/com/furybook/dubl"
HARNESS = ROOT / "tools/tests/kotlin/CharacterTransferHarness.kt"


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def test_shared_transfer_is_a_typed_application_capability():
    assert CODEC.exists()
    assert TRANSFER_APP.exists()
    codec = read(CODEC)
    app = read(APP)
    transfer = read(TRANSFER_APP)
    assert 'const val FORMAT = "dubl.character"' in codec
    assert "const val VERSION = 1" in codec
    assert "CharacterTransferRejectReason.UNSUPPORTED_RULESET" in codec
    assert "val transfer = CharacterTransferApplication" in app
    assert "fun exportActive(): String" in transfer
    assert "fun importCharacter(raw: String): CharacterTransferImportResult" in transfer



def test_shared_transfer_behavior_with_local_kotlin_harness():
    kotlinc = shutil.which("kotlinc")
    assert kotlinc is not None
    sources = []
    for folder in ("model", "data", "state", "application"):
        sources.extend(sorted((SHARED / folder).glob("*.kt")))
    sources.append(ROOT / "shared/src/commonMain/kotlin/com/furybook/core/json/MiniJson.kt")
    with tempfile.TemporaryDirectory() as temp:
        jar = Path(temp) / "character-transfer.jar"
        compile_result = subprocess.run(
            [kotlinc, *map(str, sources), str(HARNESS), "-include-runtime", "-d", str(jar)],
            cwd=ROOT, capture_output=True, text=True,
        )
        assert compile_result.returncode == 0, compile_result.stderr
        result = subprocess.run(
            ["java", "-jar", str(jar)],
            cwd=ROOT, capture_output=True, text=True,
        )
        assert result.returncode == 0, result.stderr
        assert "CHARACTER_TRANSFER_OK" in result.stdout


def test_android_characters_screen_uses_system_document_transfer():
    controller = read(ANDROID_CONTROLLER)
    screen = read(ANDROID_SCREEN)
    assert "application.transfer.exportActive()" in controller
    assert "application.transfer.importCharacter(raw)" in controller
    assert "ActivityResultContracts.CreateDocument" in screen
    assert "ActivityResultContracts.OpenDocument" in screen
    shared = read(SHARED_SCREEN)
    assert 'FuryButton("Импорт"' in shared
    assert '"Экспорт" to' in shared
    assert '".dubl"' in read(SHARED_MODELS)


def test_desktop_characters_screen_uses_native_dubl_file_dialogs():
    state = read(DESKTOP_STATE)
    screen = read(DESKTOP_SCREEN)
    assert "application.transfer.exportActive()" in state
    assert "application.transfer.importCharacter(raw)" in state
    assert "FileDialog.LOAD" in screen
    assert "FileDialog.SAVE" in screen
    assert '".dubl"' in screen
    shared = read(SHARED_SCREEN)
    assert 'FuryButton("Импорт"' in shared
    assert '"Экспорт" to' in shared


def test_linux_release_gate_runs_character_transfer_contract():
    assert "tools/tests/test_character_transfer.py" in read(WORKFLOW)
