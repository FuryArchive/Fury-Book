from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SHEET = ROOT / "shared/src/commonMain/kotlin/com/furybook/dubl/ui/sheet"


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def test_notes_support_add_edit_delete():
    source = read(SHEET / "SheetNotes.kt") + read(SHEET / "SheetPlay.kt")
    for text in ("addNote(", "updateNote(", "removeNote(", "noteTitleInput(", "noteBodyInput(", "FuryDialogScaffold("):
        assert text in source, text


def test_development_panel_and_shared_details():
    source = read(SHEET / "SheetDevelopment.kt")
    for text in ("fun SheetDevelopmentDetailsDialog(", "requirements("):
        assert text in source, text
    assert "Color(0x" not in source


def test_every_platform_uses_the_shared_details_dialog():
    # Stage 4: no platform hook; the shared dialog opens the editor on the perk («Настроить в редакторе»).
    for rel in (
        "desktopApp/src/main/kotlin/com/furybook/desktop/Main.kt",
        "webApp/src/jsMain/kotlin/com/furybook/web/Main.kt",
        "app/src/main/java/com/furybook/android/ui/DublApp.kt",
    ):
        assert "developmentDetails =" not in read(ROOT / rel), rel
    screen = read(SHEET / "SheetScreen.kt")
    assert "SheetDevelopmentDetailsDialog(" in screen
    assert "entryId = dialog.entryId" in screen


def test_local_edit_reset_and_delete_moved_to_the_editor():
    actions = read(ROOT / "shared/src/commonMain/kotlin/com/furybook/dubl/ui/editor/EditorPerkActions.kt")
    for text in ("resetDevelopmentOverride", "removeCustomDevelopment", "updateCustomDevelopment", "setDevelopmentOverride"):
        assert text in actions, text
    assert "fun EditorPerkFormDialog(" in read(ROOT / "shared/src/commonMain/kotlin/com/furybook/dubl/ui/editor/EditorPerkFormDialog.kt")
