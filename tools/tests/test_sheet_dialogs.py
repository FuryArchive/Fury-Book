from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SHEET = ROOT / "shared/src/commonMain/kotlin/com/furybook/dubl/ui/sheet"
DIALOGS = ["SheetVitalDialog.kt", "SheetStatDialog.kt", "SheetConditionsDialog.kt"]


def read(name: str) -> str:
    return (SHEET / name).read_text(encoding="utf-8")


def test_play_dialogs_exist_and_use_the_fury_dialog_scaffold():
    for name in DIALOGS:
        source = read(name)
        assert "FuryDialogScaffold(" in source, name
        assert "Color(0x" not in source, name


def test_vital_dialog_offers_damage_heal_restore_and_maximum():
    source = read("SheetVitalDialog.kt")
    for text in ("Получить урон", "Лечение", "Восстановить всё", "vitalAmountDelta(", "setHealthMaximumOverride", "removeCustomResource"):
        assert text in source, text


def test_conditions_dialog_covers_overrides_and_custom_conditions():
    source = read("SheetConditionsDialog.kt")
    for text in ("setConditionOverride", "resetConditionOverride", "addCustomCondition", "updateCustomCondition",
                 "removeCustomCondition", "setCustomConditionActive", "conditionCatalog.summary", "К рулбуку"):
        assert text in source, text


def test_stat_dialog_shows_breakdown_and_edits_size_and_legs():
    source = read("SheetStatDialog.kt")
    assert "breakdown.lines" in source
    assert "setIdentity(" in source


def test_sheet_opens_the_dialogs():
    assert "onOpen" in read("SheetVitals.kt")
    assert "onOpenInfo" in read("SheetStats.kt")
    assert "onOpenConditions" in read("SheetHeader.kt")
