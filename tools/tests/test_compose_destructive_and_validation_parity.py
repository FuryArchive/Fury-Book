from pathlib import Path
ROOT = Path(__file__).resolve().parents[2]
# Stage 5: magic and gear deletes live in the editor; each takes a restore point and offers «Вернуть»
# (the editor's pattern since stage 4) instead of the old desktop confirm dialogs.
ACTIONS = (ROOT/'shared/src/commonMain/kotlin/com/furybook/dubl/ui/editor/EditorMagicActions.kt').read_text(encoding='utf-8')
SKILLS = (ROOT/'shared/src/commonMain/kotlin/com/furybook/dubl/ui/editor/EditorSkillForms.kt').read_text(encoding='utf-8')


def _body(name):
    start = ACTIONS.index(f'fun {name}(')
    end = ACTIONS.find('\ninternal fun ', start + 1)
    return ACTIONS[start:end if end > 0 else len(ACTIONS)]


def test_magic_destructive_actions_offer_return():
    for name in ('editorRemoveSpell', 'editorRemoveSchool'):
        body = _body(name)
        assert 'val before = host.restorePoint()' in body, name
        assert 'undoTo = before' in body, name


def test_equipment_delete_offers_return():
    body = _body('editorRemoveGear')
    assert 'val before = host.restorePoint()' in body
    assert 'undoTo = before' in body


def test_custom_skill_creation_surfaces_duplicate_or_invalid_error():
    # Stage 3: adding skills lives in the editor's form; a duplicate or blank name shows an error, not a silent no-op.
    assert 'Такое умение уже есть.' in SKILLS
    assert 'Введите название.' in SKILLS
    assert 'addCustomSkill(' in SKILLS
    assert 'addSpecializedSkill(' in SKILLS
