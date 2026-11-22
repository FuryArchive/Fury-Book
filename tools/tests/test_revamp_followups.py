"""Small follow-ups from the stage 5 and 6 reviews that have no unit-test harness (Compose-only behaviour)."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
UI = ROOT / 'shared/src/commonMain/kotlin/com/furybook/dubl/ui'


def text(path: Path) -> str:
    assert path.exists(), f'missing {path}'
    return path.read_text(encoding='utf-8')


def test_dialogs_close_after_composition_not_during_it():
    for name in ['sheet/SheetDevelopment.kt', 'sheet/SheetSkillInfoDialog.kt', 'sheet/SheetPlayGear.kt', 'sheet/SheetPlaySpells.kt']:
        src = text(UI / name)
        assert 'FuryDismissWhenGone(onDismiss)' in src, name
        assert 'run { onDismiss(); return }' not in src, name
        assert '        onDismiss()\n        return\n' not in src, name


def test_unlearned_spells_warn_about_a_weak_school():
    step = text(UI / 'editor/EditorMagicStep.kt')
    assert 'if (row.learned) row.weakText' not in step
    assert 'row.weakText?.let' in step


def test_catalog_panes_page_and_keep_their_own_scroll():
    pane = text(UI / 'editor/EditorListPane.kt')
    assert 'scrolls.getOrPut(paneKey)' in pane
    for name in ['editor/EditorMagicStep.kt', 'editor/EditorGearStep.kt']:
        src = text(UI / name)
        assert 'editorCatalogPage(rows, shown)' in src, name
        assert 'paneKey = state.' in src, name
