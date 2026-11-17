from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SHEET = ROOT / "shared/src/commonMain/kotlin/com/furybook/dubl/ui/sheet"
PLATFORMS = {
    "desktopApp/src/main/kotlin/com/furybook/desktop/Main.kt": "DesktopSheetDevelopmentDetails(",
    "webApp/src/jsMain/kotlin/com/furybook/web/Main.kt": "DesktopSheetDevelopmentDetails(",
    "app/src/main/java/com/furybook/android/ui/DublApp.kt": "SheetDevelopmentDetailsDialog(",
}


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def test_notes_support_add_edit_delete():
    source = read(SHEET / "SheetNotes.kt")
    for text in ("addNote(", "updateNote(", "removeNote(", "noteTitleInput(", "noteBodyInput(", "FuryDialogScaffold("):
        assert text in source, text


def test_development_panel_and_shared_details():
    source = read(SHEET / "SheetDevelopment.kt")
    for text in ("Нет взятых навыков", "requirementsUnmet", "onOpenDetails", "fun SheetDevelopmentDetailsDialog(", "requirements("):
        assert text in source, text
    assert "Color(0x" not in source


def test_every_platform_passes_development_details():
    for rel, call in PLATFORMS.items():
        source = read(ROOT / rel)
        assert "developmentDetails =" in source, rel
        assert call in source, rel


def test_desktop_details_keep_local_edit_reset_and_delete():
    for rel in (
        "desktopApp/src/main/kotlin/com/furybook/desktop/screens/SheetDevelopmentDetails.kt",
        "webApp/src/jsMain/kotlin/com/furybook/desktop/screens/SheetDevelopmentDetails.kt",
    ):
        source = read(ROOT / rel)
        for text in ("DevelopmentDetailsDialog(", "DevelopmentLocalEditDialog(", "resetDevelopmentOverride", "removeCustomDevelopment", "updateCustomDevelopment", "setDevelopmentOverride"):
            assert text in source, (rel, text)
