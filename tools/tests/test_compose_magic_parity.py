from pathlib import Path
# Stage 5: the Магия tab is gone; its functions live in the editor's Магия step and the sheet's Заклинания block.
EDITOR = Path(__file__).resolve().parents[2] / 'shared/src/commonMain/kotlin/com/furybook/dubl/ui/editor'
SHEET = Path(__file__).resolve().parents[2] / 'shared/src/commonMain/kotlin/com/furybook/dubl/ui/sheet'
STEP = (EDITOR / 'EditorMagicStep.kt').read_text(encoding='utf-8')
MODELS = (EDITOR / 'EditorMagicModels.kt').read_text(encoding='utf-8')
ACTIONS = (EDITOR / 'EditorMagicActions.kt').read_text(encoding='utf-8')
FORMS = (EDITOR / 'EditorMagicForms.kt').read_text(encoding='utf-8')
DIALOGS = (EDITOR / 'EditorMagicDialogs.kt').read_text(encoding='utf-8')
SPELLS = (SHEET / 'SheetSpells.kt').read_text(encoding='utf-8')


def test_schools_list_only_owned_and_add_picks_from_the_rulebook():
    assert '+ Школа' in STEP
    assert 'addMagicSchool' in ACTIONS
    assert 'fun editorAddableSchools' in MODELS
    assert 'MagicSchoolCatalog.schools.filterNot { it in owned }' in MODELS


def test_spellbook_and_catalog_have_school_filter():
    assert 'state.spellSchool' in STEP
    assert 'state.spellCatalogSchool' in STEP
    assert 'school in MagicSchoolCatalog.parseSchools(' in MODELS


def test_school_errors_are_reported_instead_of_silent_failure():
    assert 'Не удалось сохранить школу' in ACTIONS


def test_mana_is_spent_from_the_sheet_and_rank_is_creation_only():
    assert 'CharacterSheetResourceId.MANA, -amount' in SPELLS
    assert 'rankLocked = character.creationComplete' in MODELS
    assert '!header.rankLocked' in STEP


def test_incomplete_catalog_spells_stay_addable_for_local_fix():
    assert 'enabled = !entry.incomplete' not in STEP
    assert '"Добавить и исправить"' in STEP


def test_spell_usability_warning_shows_for_learned_and_unlearned_spells():
    # Was learned-only (old Android). Since the stage 6 follow-ups unlearned spells warn too, before XP is spent.
    assert 'row.weakText?.let' in STEP
    assert 'if (row.learned) row.weakText' not in STEP


def test_custom_spell_save_keeps_mana_text_in_sync_with_numeric_cost():
    assert 'val manaCost =' in FORMS
    assert 'manaText = manaText.trim().ifBlank { manaCost.toString() }' in FORMS


def test_school_power_and_magic_numeric_inputs_do_not_have_desktop_only_digit_caps():
    # Power steps keep the school's note (updateMagicSchool) and have no upper cap.
    assert 'editorStepSchoolPower(host, state, school.index, +1)' in STEP
    assert 'host.updateMagicSchool(index, school.name, next, school.note)' in ACTIONS
    for cap in ('.take(2)', '.take(3)', '.take(5)'):
        assert cap not in DIALOGS, cap
