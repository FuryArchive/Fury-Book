from pathlib import Path

# Stage 2b: the sheet's roll flow is the shared SheetRollSession; the desktop Skills screen keeps SkillRollDialog.
ROOT = Path(__file__).resolve().parents[2]
SESSION = (ROOT / 'shared/src/commonMain/kotlin/com/furybook/dubl/ui/sheet/SheetRollSession.kt').read_text(encoding='utf-8')
MODELS = (ROOT / 'shared/src/commonMain/kotlin/com/furybook/dubl/ui/sheet/SheetModels.kt').read_text(encoding='utf-8')
ROLL = (ROOT / 'desktopApp/src/main/kotlin/com/furybook/desktop/screens/RollDialog.kt').read_text(encoding='utf-8')


def test_sheet_skill_roll_defaults_to_stock_attribute_and_allows_any_attribute():
    assert 'request.attribute ?: skill.stockAttribute' in SESSION
    assert 'attributeChoices = AttributeId.entries.toList()' in SESSION
    assert 'skillCalculationForRoll(skill, attribute)' in SESSION


def test_skill_roll_accepts_explicit_initial_attribute_for_skills_screen_flow():
    assert 'initialAttribute: AttributeId? = null' in ROLL
    assert 'initialAttribute ?: preferredAttribute' in ROLL


def test_character_sheet_skill_summary_uses_stock_attribute_like_android_062():
    assert 'val attribute = skill.stockAttribute' in MODELS
    assert 'skillCalculationForRoll(skill, attribute)' in MODELS
