from pathlib import Path
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
SHARED = ROOT / 'shared/src/commonMain/kotlin'
HARNESS = ROOT / 'tools/tests/kotlin/RulesBoundaryHarness.kt'
SHEET_STATS = ROOT / 'shared/src/commonMain/kotlin/com/furybook/dubl/ui/sheet/SheetStatBreakdown.kt'
SHEET_ROLLS = ROOT / 'shared/src/commonMain/kotlin/com/furybook/dubl/ui/sheet/SheetRollSession.kt'
# Stage 5: school XP is shown by the shared editor's Магия step.
EDITOR_MAGIC = ROOT / 'shared/src/commonMain/kotlin/com/furybook/dubl/ui/editor/EditorMagicModels.kt'
# Stage 4: the ЦИ setup moved from the Навыки tabs into the shared editor's ЦИ card.
EDITOR_CHI = ROOT / 'shared/src/commonMain/kotlin/com/furybook/dubl/ui/editor/EditorChiCard.kt'


def read(path: Path) -> str:
    return path.read_text(encoding='utf-8')


def test_roll_context_choices_are_owned_by_shared_rules():
    # Stage 2b: context rolls on every platform go through the shared SheetRollSession.
    sheet = read(SHEET_ROLLS)
    assert 'context.allowedSkillIds()' in sheet
    assert 'context.allowedAttributes(' in sheet


def test_selected_skill_effect_totals_are_owned_by_shared_rules():
    # Stage 3: the desktop skill roll dialog went with the Умения tab; the shared roll session is the only one.
    sheet = read(SHEET_ROLLS)
    assert '.selectedTotals(request.selectedEffectIds)' in sheet
    assert 'sumOf { it.numericBonus }' not in sheet
    assert 'sumOf { it.advantageDice }' not in sheet
    assert 'sumOf { it.hindranceDice }' not in sheet

def test_chi_breakdown_and_cost_constants_are_owned_by_shared_rules():
    for text in (read(EDITOR_CHI),):
        assert 'c.chiAutomaticAccess' in text
        assert 'c.chiBaseMaximum' in text
        assert 'c.chiProgressionBonus' in text
        assert 'CharacterEconomy.CHI_BONUS_RANK_XP' in text
        assert 'DevelopmentEffectIds.MASTER_CHI) * 2' not in text
        assert 'DevelopmentEffectIds.AWAKENED_CHI) * 3' not in text
        assert '50 XP за ранг' not in text


def test_magic_school_rank_xp_display_uses_shared_rule():
    editor = read(EDITOR_MAGIC)
    assert 'MagicEquipmentRules.magicSchoolRankXp(school.rank)' in editor
    assert '* 25' not in editor



def test_character_sheet_stat_explanations_use_shared_formula_components():
    # Stage 2b: the shared sheet's formula popup (all platforms) reads the shared bonus properties
    # inside `with(character)`; it must never recompute them.
    sheet = read(SHEET_STATS)
    assert 'with(character)' in sheet
    for component in ('runMultiplier', 'runStormSpeedBonus', 'runRunnerBonus', 'strengthSizeModifier',
                      'speedSizeModifier', 'quickReflexesBonus', 'improvedInitiativeBonus', 'stormLordBonus',
                      'stalwartBonus', 'stillMountainBonus'):
        assert component in sheet, component
    assert 'fun runMultiplier(' not in sheet
    assert 'size - 5' not in sheet
    assert '5 - size' not in sheet


def test_shared_rules_boundary_harness():
    kotlinc = shutil.which('kotlinc')
    assert kotlinc is not None
    sources = sorted((SHARED / 'com/furybook/dubl/model').glob('*.kt'))
    with tempfile.TemporaryDirectory() as td:
        jar = Path(td) / 'rules-boundary.jar'
        compiled = subprocess.run(
            [kotlinc, *map(str, sources), str(HARNESS), '-include-runtime', '-d', str(jar)],
            cwd=ROOT, capture_output=True, text=True,
        )
        assert compiled.returncode == 0, compiled.stderr
        result = subprocess.run(['java', '-jar', str(jar)], cwd=ROOT, capture_output=True, text=True)
        assert result.returncode == 0, result.stderr + result.stdout
        assert 'RULES_BOUNDARY_OK' in result.stdout
