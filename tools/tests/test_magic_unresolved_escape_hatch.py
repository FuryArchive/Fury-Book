from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SESSION = (ROOT / 'shared/src/commonMain/kotlin/com/furybook/dubl/state/CharacterSession.kt').read_text(encoding='utf-8')
# Stage 5: both old Магия screens are gone; the catalog lives in the editor's Магия step.
STEP = (ROOT / 'shared/src/commonMain/kotlin/com/furybook/dubl/ui/editor/EditorMagicStep.kt').read_text(encoding='utf-8')
ACTIONS = (ROOT / 'shared/src/commonMain/kotlin/com/furybook/dubl/ui/editor/EditorMagicActions.kt').read_text(encoding='utf-8')


def test_shared_backend_does_not_hard_reject_incomplete_rulebook_spell():
    assert 'if (entry.incomplete) return false' not in SESSION
    assert 'incomplete = entry.incomplete' in SESSION
    assert 'conflictNote = entry.conflictNote' in SESSION


def test_editor_catalog_keeps_incomplete_spell_addable_and_editable_after_add():
    assert 'enabled = !known && !entry.incomplete' not in STEP
    assert 'enabled = !known)' in STEP or 'enabled = !known' in STEP
    assert 'if (uid != null && entry.incomplete) onFix(uid)' in STEP
    assert 'host.addCatalogSpell(entry)' in ACTIONS
