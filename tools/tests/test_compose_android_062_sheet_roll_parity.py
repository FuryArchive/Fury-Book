from pathlib import Path

# Stage 3: skill rolls offer the allowed attributes plus «другая…», starting on the remembered allowed one.
ROOT = Path(__file__).resolve().parents[2]
SESSION = (ROOT / 'shared/src/commonMain/kotlin/com/furybook/dubl/ui/sheet/SheetRollSession.kt').read_text(encoding='utf-8')
MODELS = (ROOT / 'shared/src/commonMain/kotlin/com/furybook/dubl/ui/sheet/SheetPlayModels.kt').read_text(encoding='utf-8')
SCREEN = (ROOT / 'shared/src/commonMain/kotlin/com/furybook/dubl/ui/sheet/SheetScreen.kt').read_text(encoding='utf-8')


def test_sheet_skill_roll_offers_allowed_attributes_and_others_behind_another():
    assert 'request.attribute ?: sheetRememberedAttribute(skill, host.extras)' in SESSION
    assert 'attributeChoices = skill.attributes' in SESSION
    assert 'otherAttributeChoices = AttributeId.entries.filter { it !in skill.attributes }' in SESSION
    assert 'skillCalculationForRoll(skill, attribute)' in SESSION


def test_only_allowed_picks_are_remembered():
    assert 'sheetRemembersAttribute(skill, attribute)' in SCREEN
    assert 'host.setPreferredSkillAttribute(skillId, attribute)' in SCREEN


def test_character_sheet_skill_summary_uses_stock_attribute_like_android_062():
    assert 'val attribute = skill.stockAttribute' in MODELS
    assert 'skillCalculationForRoll(skill, attribute)' in MODELS
