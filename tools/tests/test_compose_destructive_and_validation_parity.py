from pathlib import Path
ROOT = Path(__file__).resolve().parents[2]
MAGIC = (ROOT/'desktopApp/src/main/kotlin/com/furybook/desktop/screens/MagicScreen.kt').read_text(encoding='utf-8')
GEAR = (ROOT/'desktopApp/src/main/kotlin/com/furybook/desktop/screens/EquipmentScreen.kt').read_text(encoding='utf-8')
SKILLS = (ROOT/'shared/src/commonMain/kotlin/com/furybook/dubl/ui/editor/EditorSkillForms.kt').read_text(encoding='utf-8')


def test_magic_destructive_actions_require_confirmation():
    assert 'pendingDeleteSchoolIndex' in MAGIC
    assert 'pendingDeleteSpellUid' in MAGIC
    assert 'Удалить школу?' in MAGIC
    assert 'Удалить заклинание?' in MAGIC


def test_equipment_delete_requires_confirmation():
    assert 'pendingDeleteUid' in GEAR
    assert 'Удалить предмет?' in GEAR


def test_custom_skill_creation_surfaces_duplicate_or_invalid_error():
    # Stage 3: adding skills lives in the editor's form; a duplicate or blank name shows an error, not a silent no-op.
    assert 'Такое умение уже есть.' in SKILLS
    assert 'Введите название.' in SKILLS
    assert 'addCustomSkill(' in SKILLS
    assert 'addSpecializedSkill(' in SKILLS
