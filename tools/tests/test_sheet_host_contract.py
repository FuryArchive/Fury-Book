from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def read(rel: str) -> str:
    return (ROOT / rel).read_text(encoding="utf-8")


HOST = "shared/src/commonMain/kotlin/com/furybook/dubl/ui/sheet/DublSheetHost.kt"
MEMBERS = [
    "active", "extras", "canUndo", "conditionCatalog", "developmentCatalog",
    "skillEffectCatalog", "resourceMeters", "changeResource", "changeCustomResource",
    "restoreMountedResource", "toggleCondition", "setCustomConditionActive", "undoLast",
    "chiPackEnabled",
]


def test_host_interface_declares_every_member():
    source = read(HOST)
    assert "interface DublSheetHost" in source
    for name in MEMBERS:
        assert name in source, name


def test_desktop_and_web_app_state_implement_host():
    for rel in (
        "desktopApp/src/main/kotlin/com/furybook/desktop/DesktopAppState.kt",
        "webApp/src/jsMain/kotlin/com/furybook/desktop/DesktopAppState.kt",
    ):
        assert ") : DublSheetEditHost" in read(rel), rel


def test_android_sheet_host_implements_host():
    source = read("app/src/main/java/com/furybook/android/ui/AndroidSheetHost.kt")
    assert "class AndroidSheetHost(" in source
    assert ") : DublSheetEditHost" in source
    assert "fun rememberAndroidSheetHost(" in source


EDIT_HOST = "shared/src/commonMain/kotlin/com/furybook/dubl/ui/sheet/DublSheetEditHost.kt"
EDIT_MEMBERS = [
    "setIdentity", "setEconomy", "completeCreation", "reopenCreation", "changeAttribute",
    "setHealthMaximumOverride", "setEnduranceMaximumOverride", "setManaMaximumOverride",
    "addCustomResource", "updateCustomResource", "removeCustomResource", "resourceToggleModels",
    "setResourceHidden", "setConditionOverride", "resetConditionOverride", "addCustomCondition",
    "updateCustomCondition", "removeCustomCondition", "setSkillGroups", "setDevelopmentGroups",
    "addNote", "updateNote", "removeNote", "setPortrait",
]


def test_edit_host_extends_play_host_and_declares_every_member():
    source = read(EDIT_HOST)
    assert "interface DublSheetEditHost : DublSheetHost" in source
    for name in EDIT_MEMBERS:
        assert f"fun {name}(" in source, name
