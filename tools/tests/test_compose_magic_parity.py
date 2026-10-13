from pathlib import Path
MAGIC = (Path(__file__).resolve().parents[2] / 'desktopApp/src/main/kotlin/com/furybook/desktop/screens/MagicScreen.kt').read_text(encoding='utf-8')


def test_magic_defaults_to_hiding_unlearned_schools_and_can_add_school():
    assert 'mutableStateOf(true)' in MAGIC
    assert 'addSchool' in MAGIC
    assert '+ Школа' in MAGIC
    assert 'addMagicSchool' in MAGIC


def test_spellbook_and_catalog_have_school_filter():
    assert 'schoolFilter' in MAGIC
    assert 'Школа:' in MAGIC
    assert 'schoolFilter ?: "Все"' in MAGIC
    assert 'schoolFilter == null' in MAGIC
    assert 'MagicSchoolCatalog.parseSchools(spell.school).contains(schoolFilter)' in MAGIC


def test_school_errors_are_reported_instead_of_silent_failure():
    assert 'schoolError' in MAGIC
    assert 'Не удалось сохранить школу' in MAGIC


def test_desktop_magic_exposes_android_mana_mutations_and_creation_lock():
    assert 'changeMana(-1)' in MAGIC
    assert 'changeMana(1)' in MAGIC
    assert 'enabled = character.manaCurrent > 0' in MAGIC
    assert 'enabled = character.manaCurrent < character.effectiveManaMaximum' in MAGIC
    assert 'enabled = state.canonicalMode || !character.creationComplete' in MAGIC
    assert 'actions?.openTarget(AcquisitionKind.MANA,"mana:rank",next)' in MAGIC
    assert 'actions?.lowerHolding(AcquisitionKind.MANA,"mana:rank",next)' in MAGIC
    assert 'else state.setMagicManaRank(next)' in MAGIC


def test_desktop_magic_keeps_incomplete_catalog_spells_addable_for_local_fix():
    assert 'enabled = !spell.incomplete' not in MAGIC
    assert 'if (spell.incomplete) "Добавить и исправить"' in MAGIC


def test_spell_usability_warning_matches_android_learned_semantics():
    assert 'if (spell.learned && !usability.usable)' in MAGIC


def test_custom_spell_save_keeps_mana_text_in_sync_with_numeric_cost():
    assert 'val manaCost =' in MAGIC
    assert 'manaText = manaCost.toString()' in MAGIC


def test_school_power_and_magic_numeric_inputs_do_not_have_desktop_only_digit_caps():
    assert 'RankStepper(power, max = Int.MAX_VALUE)' in MAGIC
    assert '.take(2)' not in MAGIC
    assert '.take(3)' not in MAGIC
    assert '.take(5)' not in MAGIC
