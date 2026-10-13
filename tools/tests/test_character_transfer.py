from pathlib import Path
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
CODEC = ROOT / "shared/src/commonMain/kotlin/com/furybook/dubl/data/CharacterTransferCodec.kt"
APP = ROOT / "shared/src/commonMain/kotlin/com/furybook/dubl/application/DublApplication.kt"
TRANSFER_APP = ROOT / "shared/src/commonMain/kotlin/com/furybook/dubl/application/CharacterTransferApplication.kt"
ANDROID_CONTROLLER = ROOT / "app/src/main/java/com/furybook/android/state/CharacterController.kt"
ANDROID_SCREEN = ROOT / "app/src/main/java/com/furybook/android/ui/screens/CharactersScreen.kt"
DESKTOP_STATE = ROOT / "desktopApp/src/main/kotlin/com/furybook/desktop/DesktopAppState.kt"
DESKTOP_SCREEN = ROOT / "desktopApp/src/main/kotlin/com/furybook/desktop/screens/CharactersScreen.kt"
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
    from tools.tests.kotlin_harness_support import run_harness
    result = run_harness(HARNESS)
    assert 'CHARACTER_TRANSFER_OK' in result.stdout


def test_android_characters_screen_uses_system_document_transfer():
    controller = read(ANDROID_CONTROLLER)
    screen = read(ANDROID_SCREEN)
    assert "application.transfer.exportActive()" in controller
    assert "application.transfer.importCharacter(raw)" in controller
    assert "ActivityResultContracts.CreateDocument" in screen
    assert "ActivityResultContracts.OpenDocument" in screen
    assert 'Text("Импорт")' in screen
    assert 'Text("Экспорт")' in screen
    assert '".dubl"' in screen


def test_desktop_characters_screen_uses_native_dubl_file_dialogs():
    state = read(DESKTOP_STATE)
    screen = read(DESKTOP_SCREEN)
    assert "application.transfer.exportActive()" in state
    assert "application.transfer.importCharacter(raw)" in state
    assert "FileDialog.LOAD" in screen
    assert "FileDialog.SAVE" in screen
    assert 'Text("Импорт")' in screen
    assert 'Text("Экспорт")' in screen
    assert '".dubl"' in screen


def test_linux_release_gate_runs_character_transfer_contract():
    assert "tools/tests/test_character_transfer.py" in read(WORKFLOW)
