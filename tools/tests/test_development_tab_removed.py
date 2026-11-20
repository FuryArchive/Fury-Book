from pathlib import Path

# Stage 4: the Навыки tab is gone; own perks, local edits, bulk ranks, the plan and the ЦИ setup live in the
# editor's Навыки step, and ЦИ techniques are used from the sheet's ЦИ meter.
ROOT = Path(__file__).resolve().parents[2]
DESKTOP_MAIN = ROOT / "desktopApp/src/main/kotlin/com/furybook/desktop/Main.kt"
WEB_MAIN = ROOT / "webApp/src/jsMain/kotlin/com/furybook/web/Main.kt"
ANDROID_APP = ROOT / "app/src/main/java/com/furybook/android/ui/DublApp.kt"
OLD_FILES = [
    ROOT / "desktopApp/src/main/kotlin/com/furybook/desktop/screens/DevelopmentScreen.kt",
    ROOT / "webApp/src/jsMain/kotlin/com/furybook/desktop/screens/DevelopmentScreen.kt",
    ROOT / "app/src/main/java/com/furybook/android/ui/screens/FeatsScreen.kt",
    ROOT / "desktopApp/src/main/kotlin/com/furybook/desktop/screens/SheetDevelopmentDetails.kt",
    ROOT / "webApp/src/jsMain/kotlin/com/furybook/desktop/screens/SheetDevelopmentDetails.kt",
]
EDITOR = ROOT / "shared/src/commonMain/kotlin/com/furybook/dubl/ui/editor"
SHEET = ROOT / "shared/src/commonMain/kotlin/com/furybook/dubl/ui/sheet"


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def test_no_development_section_on_any_platform():
    for path in (DESKTOP_MAIN, WEB_MAIN):
        text = read(path)
        assert 'DEVELOPMENT("Навыки")' not in text, path.name
        assert "DesktopSection.DEVELOPMENT" not in text, path.name
    android = read(ANDROID_APP)
    assert "AppSection.FEATS" not in android
    assert "FeatsScreen" not in android


def test_old_development_screens_are_gone():
    for path in OLD_FILES:
        assert not path.exists(), path


def test_moved_functions_live_in_shared_code():
    actions = read(EDITOR / "EditorPerkActions.kt")
    for call in ("removeCustomDevelopment", "resetDevelopmentOverride", "addCustomDevelopment", "updateCustomDevelopment", "setDevelopmentOverride"):
        assert call in actions, call
    chi_card = read(EDITOR / "EditorChiCard.kt")
    for call in ("setChiEnabled", "setChiBonusRanks", "ChiRules"):
        assert call in chi_card, call
    chi_panel = read(SHEET / "SheetChiPanel.kt")
    for call in ("ChiRules", "restoreMountedResource", "CharacterSheetResourceId.CHI"):
        assert call in chi_panel, call
    perks = read(EDITOR / "EditorPerkModels.kt")
    for text in ("includeTarget = false", "targetRank = max", "fun EditorPerkContext.planSummary("):
        assert text in perks, text
    form = read(EDITOR / "EditorPerkForm.kt")
    assert "fun EditorPerkForm.toEntry(" in form
    assert "«Настроить в редакторе»" in read(SHEET / "SheetDevelopment.kt") or '"Настроить в редакторе"' in read(SHEET / "SheetDevelopment.kt")
