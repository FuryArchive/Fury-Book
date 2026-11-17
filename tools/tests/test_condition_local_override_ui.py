from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SHEET_CONDITIONS = ROOT / "shared/src/commonMain/kotlin/com/furybook/dubl/ui/sheet/SheetConditionsDialog.kt"


def test_sheet_conditions_expose_local_override_reset_and_custom_creation():
    # Stage 2b: one shared conditions popup replaces the Android and desktop sheet dialogs.
    text = SHEET_CONDITIONS.read_text(encoding="utf-8")
    assert "Своё название и описание для этого персонажа" in text
    assert "К рулбуку" in text
    assert "Своё состояние" in text
    assert "setConditionOverride" in text
    assert "resetConditionOverride" in text
    assert "addCustomCondition" in text
    assert "conditionOverrides" in text
    assert "customConditions" in text

