from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def read(rel: str) -> str:
    return (ROOT / rel).read_text(encoding="utf-8")


def test_character_sheet_shows_skill_and_development_sections():
    # Play sheet redesign: skills by attribute and owned perks, both on the play body.
    source = read("shared/src/commonMain/kotlin/com/furybook/dubl/ui/sheet/SheetPlay.kt")
    assert "sheetSkillsByAttribute(" in source
    assert "sheetPerkGroups(" in source

def test_development_sheet_restores_parent_child_hierarchy_inside_user_groups():
    models = read("shared/src/commonMain/kotlin/com/furybook/dubl/ui/sheet/SheetPlayModels.kt")
    panel = read("shared/src/commonMain/kotlin/com/furybook/dubl/ui/sheet/SheetPlayPerks.kt")
    assert "SheetGroupingRules.hierarchicalOrder(group.itemIds, parentById)" in models
    assert "SheetGroupingRules.localDepth" in models
    assert "card.depth" in panel

def test_bottom_sheet_does_not_intercept_material_nested_scroll():
    interaction = read("app/src/main/java/com/furybook/android/ui/components/SheetInteraction.kt")
    policy = read("app/src/main/java/com/furybook/android/ui/components/SheetInteractionPolicy.kt")
    assert ".nestedScroll(" not in interaction
    assert "dismissKeyboardOnPointerDown()" in interaction
    assert "): Float = 0f" in policy


def test_keyboard_focus_is_cleared_from_app_and_sheets():
    app = read("app/src/main/java/com/furybook/android/ui/DublApp.kt")
    sheets = read("app/src/main/java/com/furybook/android/ui/components/SheetInteraction.kt")
    assert "Modifier.dismissKeyboardOnPointerDown()" in app
    assert "dismissKeyboardOnPointerDown()" in sheets


def test_character_sheet_skill_tap_always_starts_with_stock_attribute_chooser():
    # Stage 3: the roll offers the allowed attributes (+ «другая…») and starts on the remembered allowed one, else stock, else the first allowed.
    source = read("shared/src/commonMain/kotlin/com/furybook/dubl/ui/sheet/SheetRollSession.kt")
    model = read("shared/src/commonMain/kotlin/com/furybook/dubl/model/SkillModels.kt")
    assert "?.takeIf { it in skill.attributes }\n        ?: skill.stockAttribute.takeIf { it in skill.attributes }" in source
    assert "attributeChoices = skill.attributes" in source
    assert "skillCalculationForRoll" in source
    assert "val stockAttribute: AttributeId" in model
    assert "definition?.defaultAttribute ?: attributes.first()" in model
