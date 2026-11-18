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


def test_martial_art_availability_is_prepared_off_main_behind_loading_gate():
    source = read("app/src/main/java/com/furybook/android/ui/screens/FeatsScreen.kt")
    assert "DevelopmentLoadingScreen(stage = loadingStage)" in source
    assert "withContext(Dispatchers.Default)" in source
    assert "availabilityById" in source
    assert "associate { entry -> entry.id to preparationRules.availability(entry) }" in source
    row = source.split("private fun DevelopmentRow(", 1)[1].split("@Composable\nprivate fun DevelopmentStatusPill", 1)[0]
    assert "availability: DevelopmentAvailability" in row
    assert "rules.availability(entry)" not in row
    assert "!availableOnly || availabilityById[entry.id]?.canIncrease == true" in source


def test_character_sheet_skill_tap_always_starts_with_stock_attribute_chooser():
    # Stage 2b: the shared roll panel offers every attribute and starts from the stock one.
    source = read("shared/src/commonMain/kotlin/com/furybook/dubl/ui/sheet/SheetRollSession.kt")
    model = read("shared/src/commonMain/kotlin/com/furybook/dubl/model/SkillModels.kt")
    assert "request.attribute ?: skill.stockAttribute" in source
    assert "attributeChoices = AttributeId.entries.toList()" in source
    assert "skillCalculationForRoll" in source
    assert "val stockAttribute: AttributeId" in model
    assert "definition?.defaultAttribute ?: attributes.first()" in model

def test_key_bottom_sheets_skip_partial_expansion():
    rolls = read("app/src/main/java/com/furybook/android/ui/screens/SkillRollSheets.kt")
    feats = read("app/src/main/java/com/furybook/android/ui/screens/FeatsScreen.kt")
    assert "rememberModalBottomSheetState(skipPartiallyExpanded = true)" in rolls
    assert "rememberModalBottomSheetState(skipPartiallyExpanded = true)" in feats
