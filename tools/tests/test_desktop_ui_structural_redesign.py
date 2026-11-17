from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
MAIN = ROOT / 'desktopApp/src/main/kotlin/com/furybook/desktop/Main.kt'
PRIMITIVES = ROOT / 'desktopApp/src/main/kotlin/com/furybook/desktop/screens/UiPrimitives.kt'
SHEET = ROOT / 'desktopApp/src/main/kotlin/com/furybook/desktop/screens/CharacterSheetScreen.kt'


def read(path: Path) -> str:
    assert path.exists(), f'missing {path}'
    return path.read_text(encoding='utf-8')


def test_desktop_workspace_caps_ultrawide_stretch_and_rail_has_no_prototype_footer():
    main = read(MAIN)
    assert '.widthIn(max = 2160.dp)' in main
    assert 'contentAlignment = Alignment.TopCenter' in main
    assert 'Desktop 0.2 · Compose parity' not in main
    assert 'Modifier.width(230.dp)' in main
    assert 'DesktopSection.entries.filter { it != DesktopSection.CHARACTERS }' in main
    assert 'NavigationItem(DesktopSection.CHARACTERS' in main


def test_desktop_presentation_primitives_exist():
    src = read(PRIMITIVES)
    for primitive in (
        'DesktopPanel',
        'DesktopSectionHeader',
        'DesktopHeroPanel',
        'DesktopStatCell',
        'DesktopMetricCell',
        'DesktopResourceRow',
        'DesktopConditionChip',
        'DesktopSmallAction',
    ):
        assert f'fun {primitive}' in src, primitive


def test_character_sheet_uses_hero_telemetry_primitives_and_collapses_conditions():
    sheet = read(SHEET)
    for token in (
        'BoxWithConstraints',
        'DesktopHeroPanel',
        'DesktopResourceTile',
        'DesktopHeroAttributeCell',
        'DesktopHeroMetricCell',
        'DesktopSkillRow',
        'DesktopConditionChip',
    ):
        assert token in sheet, token
    assert 'SectionCard("Состояния"' not in sheet
    assert 'private fun HeroTelemetry' in sheet
    assert 'private fun SkillsDevelopmentWorkspace' in sheet
    assert 'private fun SheetDevelopmentPanel' in sheet


def test_character_sheet_preserves_shared_application_callbacks():
    sheet = read(SHEET)
    for token in (
        'state.changeResource(',
        'state.changeAttribute(',
        'state.undoLast()',
        'state.setPortrait(',
        'state.setSkillGroups(',
        'state.setDevelopmentGroups(',
    ):
        assert token in sheet, token
    assert 'horizontalScroll' not in sheet


def test_character_sheet_moves_telemetry_into_hero_and_keeps_notes():
    sheet = read(SHEET)
    primitives = read(PRIMITIVES)
    assert 'private fun HeroTelemetry' in sheet
    assert 'private fun HeroCharacteristicsStrip' in sheet
    assert 'private fun HeroMetricsStrip' in sheet
    assert 'private fun SkillsDevelopmentWorkspace' in sheet
    assert 'private fun SheetDevelopmentPanel' in sheet
    assert 'private fun NotesPanel' in sheet
    assert 'private fun DenseStatsSkillsRow' not in sheet
    assert 'private fun CompactCharacteristicsPanel' not in sheet
    assert 'private fun CompactMetricsPanel' not in sheet
    assert 'fun DesktopHeroAttributeCell' in primitives
    assert 'fun DesktopHeroMetricCell' in primitives
    characteristics = sheet.split('private fun HeroCharacteristicsStrip', 1)[1].split('private fun attributeIcon', 1)[0]
    assert 'wide: Boolean' in characteristics
    assert 'wide -> 4' in characteristics
    assert 'state.addNote(' in sheet
    assert 'state.updateNote(' in sheet
    assert 'state.removeNote(' in sheet

def test_live_screenshot_regression_uses_compact_hero_resources_and_skills_development_workspace():
    main = read(MAIN)
    sheet = read(SHEET)
    primitives = read(PRIMITIVES)
    for token in (
        'DesktopBackground',
        'DesktopSurface',
        'DesktopAccent',
        'DesktopGold',
        'DesktopMana',
    ):
        assert token in primitives, token
    assert 'DesktopVisualTheme' in main
    assert 'private fun HeroResources' in sheet
    assert 'maxVisible = if (compact) 4 else 6' in sheet
    assert 'if (shown.size <= 4) shown.size.coerceAtLeast(1) else 3' in sheet
    assert 'Ещё $overflow' in sheet
    assert 'private fun ResourcesPanel' not in sheet
    assert 'private fun SkillsDevelopmentWorkspace' in sheet
    workspace = sheet.split('private fun SkillsDevelopmentWorkspace', 1)[1].split('private fun SheetSkillsPanel', 1)[0]
    assert 'Modifier.weight(.35f)' in workspace
    assert 'Modifier.weight(.65f)' in workspace
    assert 'SheetSkillsPanel(' in workspace
    assert 'SheetDevelopmentPanel(' in workspace
    assert 'character.customResources.forEachIndexed { index, resource ->' in sheet
    assert 'FcpUiHostOrder.CUSTOM + index' in sheet


def test_hero_metrics_expose_only_requested_quick_rolls():
    sheet = read(SHEET)
    metrics = sheet.split('private fun HeroMetricsStrip', 1)[1].split('private fun SkillsDevelopmentWorkspace', 1)[0]
    for token in ('RollContext.REFLEXES', 'RollContext.INITIATIVE', 'RollContext.FORTITUDE', 'RollContext.RUN'):
        assert token in metrics
    assert 'Metric(DesktopIconKind.DEFENSE, "Защита", character.defense.toString(), null)' in metrics
    assert 'Metric(DesktopIconKind.SIZE, "Размер", character.size.toString(), null)' in metrics

def test_notes_flow_through_shared_application_boundary_and_persistence():
    extras = read(ROOT / 'shared/src/commonMain/kotlin/com/furybook/dubl/model/CharacterSheetExtras.kt')
    session = read(ROOT / 'shared/src/commonMain/kotlin/com/furybook/dubl/state/CharacterExtrasSession.kt')
    sheet_app = read(ROOT / 'shared/src/commonMain/kotlin/com/furybook/dubl/application/SheetApplication.kt')
    desktop_state = read(ROOT / 'desktopApp/src/main/kotlin/com/furybook/desktop/DesktopAppState.kt')
    desktop_store = read(ROOT / 'shared/src/desktopMain/kotlin/com/furybook/dubl/data/DesktopCharacterExtrasStore.kt')
    android_store = read(ROOT / 'app/src/main/java/com/furybook/android/data/CharacterSheetExtrasRepository.kt')
    assert 'data class CharacterNote' in extras
    assert 'val noteEntries: List<CharacterNote> = emptyList()' in extras
    assert 'fun CharacterSheetExtras.displayNotes()' in extras
    for token in ('fun addNote(', 'fun updateNote(', 'fun removeNote('):
        assert token in session
        assert token in sheet_app
        assert token in desktop_state
    assert 'CharacterNoteDataCodec.encode' in desktop_store
    assert 'CharacterNoteDataCodec.decode' in desktop_store
    assert 'CharacterNoteDataCodec.encode' in android_store
    assert 'CharacterNoteDataCodec.decode' in android_store
    assert '"notes"' in desktop_store


def test_dense_sheet_keeps_grouping_and_development_details_reachable():
    sheet = read(SHEET)
    assert 'onGrouping(GroupingKind.SKILLS)' in sheet
    assert 'onGrouping(GroupingKind.DEVELOPMENT)' in sheet
    assert 'onDevelopmentDetails(item.entry)' in sheet


def test_sheet_skills_show_all_visible_ranks_and_preserve_hidden_group_membership():
    sheet = read(SHEET)
    skills_panel = sheet.split('private fun SheetSkillsPanel', 1)[1].split('private fun skillIcon', 1)[0]
    assert 'character.resolvedSkills(includeHidden = true)' in skills_panel
    assert 'character.resolvedSkills()' in skills_panel
    assert 'filter { it.rank > 0 }' not in skills_panel
    assert 'SheetGroupHeaderCompact' in skills_panel
    assert 'SheetGroupingRules.balancedColumns' in skills_panel
    assert 'rank = skill.rank' in skills_panel
    assert 'calc.total?.plus(effects.automaticBonus)?.let(::signed) ?: "—"' in skills_panel

    primitives = read(PRIMITIVES)
    skill_row = primitives.split('internal fun DesktopSkillRow', 1)[1].split('internal fun DesktopResourceTile', 1)[0]
    assert 'rank: Int' in skill_row
    assert 'Text("Ранг $rank"' in skill_row
    assert 'Итоговый бонус $bonus' in skill_row


def test_narrow_skills_column_falls_back_to_one_internal_column():
    sheet = read(SHEET)
    skills_panel = sheet.split('private fun SheetSkillsPanel', 1)[1].split('private fun skillIcon', 1)[0]
    assert 'BoxWithConstraints(Modifier.fillMaxWidth())' in skills_panel
    assert 'maxWidth < 560.dp' in skills_panel
    assert 'groupSkills.forEach { skill ->' in skills_panel


def test_sheet_development_panel_restores_grouped_hierarchy_from_shared_parent_ids():
    sheet = read(SHEET)
    panel = sheet.split('private fun SheetDevelopmentPanel', 1)[1].split('private fun rankLabel', 1)[0]
    for token in (
        'val parentById = developmentItems.associate { it.entry.id to it.parentId }',
        'SheetGroupingRules.hierarchicalOrder',
        'SheetGroupingRules.hierarchyBlocks',
        'SheetGroupingRules.localDepth',
        'SheetGroupHeaderCompact',
        'DevelopmentTreeRow',
    ):
        assert token in panel, token


def test_grouping_manager_includes_rank_zero_visible_skills_and_keeps_tree_drag_contract():
    sheet = read(SHEET)
    grouping = sheet.split('private fun GroupingManagerDialog', 1)[1].split('private fun DraggableGroupingItem', 1)[0]
    assert 'character.resolvedSkills(includeHidden = true)' in grouping
    assert 'filter { it.rank > 0 }' not in grouping
    assert 'SheetGroupingRules.subtreeBlock' in grouping
    assert 'SheetGroupingRules.hierarchicalOrder' in grouping
    assert 'movesTree' in grouping


def test_desktop_skills_screen_keeps_all_visible_default_and_hidden_restore_entrypoint():
    skills = read(ROOT / 'desktopApp/src/main/kotlin/com/furybook/desktop/screens/SkillsScreen.kt')
    assert 'val skills = character.resolvedSkills().filter { skill ->' in skills
    assert '(!learnedOnly || skill.rank > 0)' in skills
    assert 'character.hiddenSkillIds.size' in skills
    assert 'HiddenSkillsDialog' in skills
    assert 'state.hideSkill(skill.id)' in skills
    assert 'state.restoreSkill(skill.id)' in skills
    assert 'state.restoreAllSkills()' in skills


def test_hero_telemetry_places_characteristics_and_metrics_side_by_side_on_wide_desktop():
    sheet = read(SHEET)
    telemetry = sheet.split('private fun HeroTelemetry', 1)[1].split('private fun HeroCharacteristicsStrip', 1)[0]
    assert 'if (wide && !compact)' in telemetry
    assert 'Modifier.weight(.58f)' in telemetry
    assert 'Modifier.weight(.42f)' in telemetry

    characteristics = sheet.split('private fun HeroCharacteristicsStrip', 1)[1].split('private fun attributeIcon', 1)[0]
    assert 'embedded: Boolean = false' in characteristics
    assert 'embedded -> 2' in characteristics

    metrics = sheet.split('private fun HeroMetricsStrip', 1)[1].split('private fun SkillsDevelopmentWorkspace', 1)[0]
    assert 'embedded: Boolean = false' in metrics
    assert 'embedded -> 2' in metrics


def test_sheet_actions_use_clear_user_facing_copy_instead_of_service_labels():
    sheet = read(SHEET)
    for token in ('Добавить ресурс', 'Показать / скрыть', 'Настроить группы', 'Все умения →', 'Все навыки →', 'Расход опыта'):
        assert token in sheet, token
    assert 'Text("+ ресурс"' not in sheet
    assert 'Text("Видимость"' not in sheet
    assert 'Text("Группы")' not in sheet
    assert 'Text("Открыть все →")' not in sheet


def test_notes_panel_supports_multiple_titled_collapsible_notes_and_grows_with_content():
    sheet = read(SHEET)
    notes = sheet.split('private fun NotesPanel', 1)[1].split('private fun NoteEditorDialog', 1)[0]
    assert 'notes: List<CharacterNote>' in notes
    assert 'Добавить заметку' in notes
    assert 'expandedNoteIds' in notes
    assert 'note.title' in notes
    assert 'note.body' in notes
    assert 'onDelete(note)' in notes
    assert 'maxLines = 5' not in notes
    assert '.heightIn(min = 78.dp)' not in notes
    assert 'Заметок пока нет.' in notes


def test_hero_visually_separates_identity_resources_characteristics_and_metrics():
    sheet = read(SHEET)
    primitives = read(PRIMITIVES)
    assert 'fun DesktopHeroSection' in primitives
    hero = sheet.split('private fun CharacterHero', 1)[1].split('private fun HeroPortrait', 1)[0]
    assert hero.count('DesktopHeroSection') >= 2
    telemetry = sheet.split('private fun HeroTelemetry', 1)[1].split('private fun HeroCharacteristicsStrip', 1)[0]
    assert telemetry.count('DesktopHeroSection') >= 2


def test_sheet_skill_bonus_exposes_hover_breakdown_with_automatic_effect_sources():
    sheet = read(SHEET)
    primitives = read(PRIMITIVES)
    skills_panel = sheet.split('private fun SheetSkillsPanel', 1)[1].split('private fun skillIcon', 1)[0]
    assert 'SkillEffectRules' in sheet
    assert 'automaticContributions' in skills_panel
    assert 'breakdownLines' in skills_panel
    assert 'calc.total?.plus(effects.automaticBonus)?.let(::signed)' in skills_panel
    skill_row = primitives.split('internal fun DesktopSkillRow', 1)[1].split('internal fun DesktopResourceTile', 1)[0]
    assert 'breakdownLines: List<String>' in skill_row
    assert 'pointerMoveFilter' in primitives
    assert 'Итоговый бонус' in skill_row


def test_secondary_desktop_typography_is_readable_and_inline_actions_are_neutral():
    main = read(MAIN)
    primitives = read(PRIMITIVES)
    assert 'bodySmall = typography.bodySmall.copy(fontSize = 14.sp' in main
    assert 'labelMedium = typography.labelMedium.copy(fontSize = 14.sp' in main
    assert 'internal fun DesktopInlineAction' in primitives
    inline = primitives.split('internal fun DesktopInlineAction', 1)[1].split('@Composable', 1)[0]
    assert 'DesktopMuted' in inline
    assert 'animateColorAsState' in inline
