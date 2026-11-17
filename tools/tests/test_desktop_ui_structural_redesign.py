from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
MAIN = ROOT / 'desktopApp/src/main/kotlin/com/furybook/desktop/Main.kt'
PRIMITIVES = ROOT / 'desktopApp/src/main/kotlin/com/furybook/desktop/screens/UiPrimitives.kt'
SHEET_DIR = ROOT / 'shared/src/commonMain/kotlin/com/furybook/dubl/ui/sheet'
# Stage 2b: the desktop character sheet is the shared SheetScreen; old hero-layout tests were removed.


def sheet_source() -> str:
    return ''.join(path.read_text(encoding='utf-8') for path in sorted(SHEET_DIR.glob('*.kt')))


def read(path: Path) -> str:
    assert path.exists(), f'missing {path}'
    return path.read_text(encoding='utf-8')


def test_desktop_workspace_caps_ultrawide_stretch_and_rail_has_no_prototype_footer():
    main = read(MAIN)
    assert '.widthIn(max = 2160.dp)' in main
    assert 'contentAlignment = Alignment.TopCenter' in main
    assert 'Desktop 0.2 · Compose parity' not in main
    # The 230dp sidebar rail is gone; the shared FuryShell offers every section, incl. CHARACTERS.
    assert 'Modifier.width(230.dp)' not in main
    assert 'FuryShell(' in main
    assert 'sections = DesktopSection.entries.map { FuryShellSection(' in main
    assert 'CHARACTERS("Персонажи")' in main


def test_desktop_presentation_primitives_exist():
    src = read(PRIMITIVES)
    for primitive in ('DesktopPanel', 'DesktopSectionHeader', 'DesktopHeroPanel', 'DesktopMetricCell', 'DesktopSmallAction'):
        assert f'fun {primitive}' in src, primitive


def test_character_sheet_preserves_shared_application_callbacks():
    sheet = sheet_source()
    for token in (
        'host.changeResource(',
        'host.changeAttribute(',
        'host.setPortrait(',
        'host.setSkillGroups(',
        'host.setDevelopmentGroups(',
    ):
        assert token in sheet, token
    assert 'state.undoLast()' in read(MAIN)
    assert 'horizontalScroll' not in sheet


def test_character_sheet_keeps_notes():
    screen = read(SHEET_DIR / 'SheetScreen.kt')
    assert 'SheetNotes(host.extras.displayNotes()' in screen


def test_hero_metrics_expose_only_requested_quick_rolls():
    models = read(SHEET_DIR / 'SheetModels.kt')
    metrics = models.split('fun sheetMetrics(', 1)[1].split('\n)\n', 1)[0]
    for token in ('RollContext.REFLEXES', 'RollContext.INITIATIVE', 'RollContext.FORTITUDE', 'RollContext.RUN'):
        assert token in metrics
    assert 'SheetStat("Защита", character.defense, null' in metrics
    assert 'SheetStat("Размер", character.size, null' in metrics

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


def test_sheet_keeps_grouping_and_development_details_reachable():
    screen = read(SHEET_DIR / 'SheetScreen.kt')
    assert 'SheetDialog.Groups(SheetGroupKind.SKILLS)' in screen
    assert 'SheetDialog.Groups(SheetGroupKind.DEVELOPMENT)' in screen
    assert 'SheetDialog.DevelopmentDetails(id)' in screen
    assert 'DesktopSheetDevelopmentDetails(state, entryId, close)' in read(MAIN)


def test_sheet_skills_show_all_visible_ranks_and_preserve_hidden_group_membership():
    models = read(SHEET_DIR / 'SheetModels.kt')
    assert 'fun sheetSkillGroupsForEdit(' in models
    assert 'resolvedSkills(includeHidden = true)' in models
    assert 'SheetGroupingRules.normalize(' in models
def test_narrow_skills_column_falls_back_to_one_internal_column():
    screen = read(SHEET_DIR / 'SheetScreen.kt')
    assert 'columns = if (phone) 1 else 2' in screen


def test_sheet_development_panel_restores_grouped_hierarchy_from_shared_parent_ids():
    models = read(SHEET_DIR / 'SheetEditModels.kt')
    assert 'ownedSheetSections()' in models
    assert 'it.entry.id to it.parentId' in models
    assert 'SheetGroupingRules.hierarchicalOrder(' in models
    assert 'SheetGroupingRules.localDepth(' in models


def test_grouping_manager_includes_rank_zero_visible_skills_and_keeps_tree_drag_contract():
    manager = read(SHEET_DIR / 'SheetGroupManager.kt')
    assert 'resolvedSkills(includeHidden = true)' in manager
    assert 'SheetGroupingRules.subtreeBlock(' in manager
    assert 'treeRoots' in manager


def test_desktop_skills_screen_keeps_all_visible_default_and_hidden_restore_entrypoint():
    skills = read(ROOT / 'desktopApp/src/main/kotlin/com/furybook/desktop/screens/SkillsScreen.kt')
    assert 'val skills = character.resolvedSkills().filter { skill ->' in skills
    assert '(!learnedOnly || skill.rank > 0)' in skills
    assert 'character.hiddenSkillIds.size' in skills
    assert 'HiddenSkillsDialog' in skills
    assert 'state.hideSkill(skill.id)' in skills
    assert 'state.restoreSkill(skill.id)' in skills
    assert 'state.restoreAllSkills()' in skills


def test_sheet_actions_use_clear_user_facing_copy_instead_of_service_labels():
    sheet = sheet_source()
    for token in ('Добавить ресурс', 'Ресурсы: добавить / скрыть', 'Настроить группы', '"Все умения"', '"Все навыки"', '"Опыт"'):
        assert token in sheet, token
    assert 'Text("+ ресурс"' not in sheet
    assert 'Text("Видимость"' not in sheet
    assert 'Text("Группы")' not in sheet
    assert 'Text("Открыть все →")' not in sheet


def test_notes_panel_supports_multiple_titled_collapsible_notes_and_grows_with_content():
    notes = read(SHEET_DIR / 'SheetNotes.kt')
    assert 'notes: List<CharacterNote>' in notes
    assert 'Добавить заметку' in notes
    assert 'expanded' in notes
    assert 'note.title' in notes
    assert 'note.body' in notes
    assert 'host.removeNote(note.id)' in notes
    assert 'maxLines = 5' not in notes
    assert 'Заметок пока нет' in notes


def test_sheet_skill_bonus_breakdown_with_automatic_effect_sources_is_in_the_roll_panel():
    # Stage 2b: the hover popover is replaced by the roll panel's pre-roll formula line and automatic-effects list.
    session = read(SHEET_DIR / 'SheetRollSession.kt')
    panel = read(SHEET_DIR / 'SheetRollPanel.kt')
    assert 'fun sheetRollFormulaLine(' in session
    assert 'automaticEffects' in session
    assert 'Автоматически' in panel


def test_secondary_desktop_typography_is_readable_and_inline_actions_are_neutral():
    main = read(MAIN)
    primitives = read(PRIMITIVES)
    assert 'bodySmall = typography.bodySmall.copy(fontSize = 14.sp' in main
    assert 'labelMedium = typography.labelMedium.copy(fontSize = 14.sp' in main
    assert 'internal fun DesktopInlineAction' in primitives
    inline = primitives.split('internal fun DesktopInlineAction', 1)[1].split('@Composable', 1)[0]
    assert 'DesktopMuted' in inline
    assert 'animateColorAsState' in inline
