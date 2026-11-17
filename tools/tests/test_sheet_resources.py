from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SHEET = ROOT / "shared/src/commonMain/kotlin/com/furybook/dubl/ui/sheet"


def test_resources_dialog_covers_visibility_and_custom_resources():
    source = (SHEET / "SheetResourcesDialog.kt").read_text(encoding="utf-8")
    for text in ("FuryDialogScaffold(", "setResourceHidden", "addCustomResource", "removeCustomResource",
                 "SheetCustomResourceForm(", "sheetResourceVisibility("):
        assert text in source, text
    assert "Color(0x" not in source


def test_vitals_offer_the_resources_dialog_in_edit_mode():
    assert "onManageResources" in (SHEET / "SheetVitals.kt").read_text(encoding="utf-8")
