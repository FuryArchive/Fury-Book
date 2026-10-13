from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def read(rel: str) -> str:
    return (ROOT / rel).read_text(encoding="utf-8")


def test_character_sheet_uses_independent_two_column_sections():
    source = read("app/src/main/java/com/furybook/android/ui/screens/OverviewScreen.kt")
    assert "ownedSkillSectionItems(" in source
    assert "ownedDevelopmentSectionItems(" in source
    assert source.count("SheetGroupingRules.balancedColumns(") >= 2
    assert "compactTileWeight" in source
    assert "compactGridRows(" not in source


def test_development_sheet_restores_parent_child_hierarchy_inside_user_groups():
    source = read("app/src/main/java/com/furybook/android/ui/screens/OverviewScreen.kt")
    assert "developmentParentById" in source
    assert "SheetGroupingRules.hierarchicalOrder(group.itemIds, parentById)" in source
    assert "SheetGroupingRules.localDepth" in source
    assert '"↳"' in source


def test_group_manager_supports_item_tree_and_group_drag_drop():
    source = read("app/src/main/java/com/furybook/android/ui/screens/OverviewScreen.kt")
    assert "GroupManagerItemCard(" in source
    assert "SheetGroupingRules.moveItems(" in source
    assert "SheetGroupingRules.subtreeBlock(" in source
    assert "SheetGroupingRules.moveGroupToIndex(" in source
    assert "treeRootIds" in source
    assert "≡" in source
    assert "Группа выше" not in source
    assert "Группа ниже" not in source


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
    assert "DevelopmentLoadingScreen(stage = if (acquisition?.readUnavailable == true)" in source
    assert "withContext(Dispatchers.Default)" in source
    assert "availabilityById" in source
    assert "prepareDevelopmentRows(entries, { it.id }, ::calculateAvailability)" in source
    row = source.split("private fun DevelopmentRow(", 1)[1].split("@Composable\nprivate fun DevelopmentStatusPill", 1)[0]
    assert "availability: DevelopmentAvailability" in row
    assert "rules.availability(entry)" not in row
    assert "!availableOnly || calculateAvailability(entry).canIncrease" in source
    assert "checkNotNull(publishedRows).third.getValue(entry.id)" in source


def test_character_sheet_skill_tap_always_starts_with_stock_attribute_chooser():
    source = read("app/src/main/java/com/furybook/android/ui/screens/OverviewScreen.kt")
    model = read("shared/src/commonMain/kotlin/com/furybook/dubl/model/SkillModels.kt")
    assert "SkillAttributeChoiceSheet(" in source
    assert "mutableStateOf(skill.stockAttribute)" in source
    assert "AttributeId.entries.chunked(2)" in source
    assert "skillCalculationForRoll" in source
    assert "val stockAttribute: AttributeId" in model
    assert "definition?.defaultAttribute ?: attributes.first()" in model


def test_key_bottom_sheets_skip_partial_expansion():
    overview = read("app/src/main/java/com/furybook/android/ui/screens/OverviewScreen.kt")
    feats = read("app/src/main/java/com/furybook/android/ui/screens/FeatsScreen.kt")
    assert overview.count("rememberModalBottomSheetState(skipPartiallyExpanded = true)") >= 4
    assert "rememberModalBottomSheetState(skipPartiallyExpanded = true)" in feats
