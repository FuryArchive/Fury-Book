from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
ANDROID_CONTROLLER = ROOT / "app/src/main/java/com/furybook/android/state/CharacterController.kt"
ANDROID_HOST = ROOT / "app/src/main/java/com/furybook/android/ui/AndroidSheetHost.kt"
DESKTOP_STATE = ROOT / "desktopApp/src/main/kotlin/com/furybook/desktop/DesktopAppState.kt"
# Stage 4: local edits and own perks moved from the Навыки tabs into the shared editor (all platforms).
EDITOR = ROOT / "shared/src/commonMain/kotlin/com/furybook/dubl/ui/editor"
WORKFLOW = ROOT / ".github/workflows/linux-appimage.yml"


def test_android_development_ui_exposes_local_override_and_custom_escape_hatch():
    controller = ANDROID_CONTROLLER.read_text()
    host = ANDROID_HOST.read_text()
    for call in ("setDevelopmentOverride", "resetDevelopmentOverride", "addCustomDevelopment", "updateCustomDevelopment", "removeCustomDevelopment"):
        assert call in controller, call
        assert f"controller.{call}(" in host, call
    assert "effectiveDevelopmentCatalog" in host


def test_desktop_development_ui_exposes_local_override_and_custom_escape_hatch():
    state = DESKTOP_STATE.read_text()
    assert "effectiveDevelopmentCatalog" in state
    for call in ("override fun setDevelopmentOverride(", "override fun addCustomDevelopment(", "override fun removeCustomDevelopment("):
        assert call in state, call


def test_editor_exposes_local_override_and_custom_escape_hatch():
    actions = (EDITOR / "EditorPerkActions.kt").read_text()
    step = (EDITOR / "EditorPerksStep.kt").read_text()
    for label in ("Локальная правка", "Своя запись", "Редактировать свою запись"):
        assert label in actions, label
    for label in ("Сбросить к рулбуку", "Удалить свою запись", "+ Свой навык"):
        assert label in step, label
    for call in ("setDevelopmentOverride", "resetDevelopmentOverride", "addCustomDevelopment", "updateCustomDevelopment", "removeCustomDevelopment"):
        assert call in actions, call
    perks = (EDITOR / "EditorPerkModels.kt").read_text()
    assert ".filter { entry -> !entry.incomplete" not in perks


def test_development_escape_hatch_contracts_are_in_linux_release_gate():
    workflow = WORKFLOW.read_text()
    for name in (
        "test_development_local_override_contract.py",
        "test_development_override_persistence.py",
        "test_development_override_session.py",
        "test_development_override_ui.py",
    ):
        assert name in workflow
