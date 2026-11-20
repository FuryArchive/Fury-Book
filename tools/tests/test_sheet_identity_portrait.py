from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SHEET = ROOT / "shared/src/commonMain/kotlin/com/furybook/dubl/ui/sheet"
PLATFORMS = [
    "desktopApp/src/main/kotlin/com/furybook/desktop/Main.kt",
    "webApp/src/jsMain/kotlin/com/furybook/web/Main.kt",
    "app/src/main/java/com/furybook/android/ui/DublApp.kt",
]


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def test_identity_and_economy_dialogs_use_the_edit_host():
    basics = read(SHEET.parent / "editor" / "EditorBasicsStep.kt")
    economy = read(SHEET / "SheetEconomyDialog.kt")
    assert "setIdentity(" in basics and "SheetEconomyDialog(" in basics
    for text in ("FuryDialogScaffold(", "setEconomy(", "completeCreation()", "reopenCreation()", "sheetEconomyForm("):
        assert text in economy, text


def test_portrait_supports_pick_and_remove_with_a_placeholder():
    portrait = read(SHEET / "SheetPortrait.kt")
    assert "typealias SheetPortraitLoader" in portrait
    assert "onRemove" in portrait and "onPick" in portrait


def test_every_platform_passes_a_portrait_loader_and_picker():
    for rel in PLATFORMS:
        source = read(ROOT / rel)
        assert "portraitLoader =" in source, rel
        assert "onPickPortrait =" in source, rel
