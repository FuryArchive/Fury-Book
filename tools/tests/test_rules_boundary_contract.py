from pathlib import Path
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
SHARED = ROOT / 'shared/src/commonMain/kotlin'
HARNESS = ROOT / 'tools/tests/kotlin/RulesBoundaryHarness.kt'
ANDROID_OVERVIEW = ROOT / 'app/src/main/java/com/furybook/android/ui/screens/OverviewScreen.kt'
ANDROID_FEATS = ROOT / 'app/src/main/java/com/furybook/android/ui/screens/FeatsScreen.kt'
ANDROID_MAGIC = ROOT / 'app/src/main/java/com/furybook/android/ui/screens/MagicScreen.kt'
DESKTOP_ROLL = ROOT / 'desktopApp/src/main/kotlin/com/furybook/desktop/screens/RollDialog.kt'
DESKTOP_DEVELOPMENT = ROOT / 'desktopApp/src/main/kotlin/com/furybook/desktop/screens/DevelopmentScreen.kt'


def read(path: Path) -> str:
    return path.read_text(encoding='utf-8')


def test_roll_context_choices_are_owned_by_shared_rules():
    android = read(ANDROID_OVERVIEW)
    desktop = read(DESKTOP_ROLL)
    assert 'context.allowedSkillIds()' in android
    assert 'context.allowedAttributes(' in android
    assert 'context.allowedSkillIds()' in desktop
    assert 'context.allowedAttributes(' in desktop


def test_selected_skill_effect_totals_are_owned_by_shared_rules():
    android = read(ANDROID_OVERVIEW)
    desktop = read(DESKTOP_ROLL)
    assert '.selectedTotals(selectedEffectIds)' in android
    assert '.selectedTotals(selectedEffects)' in desktop
    for text in (android, desktop):
        assert 'sumOf { it.numericBonus }' not in text
        assert 'sumOf { it.advantageDice }' not in text
        assert 'sumOf { it.hindranceDice }' not in text


def test_chi_breakdown_and_cost_constants_are_owned_by_shared_rules():
    android = read(ANDROID_FEATS)
    desktop = read(DESKTOP_DEVELOPMENT)
    for text in (android, desktop):
        assert 'character.chiAutomaticAccess' in text
        assert 'character.chiBaseMaximum' in text
        assert 'character.chiProgressionBonus' in text
        assert 'CharacterEconomy.CHI_BONUS_RANK_XP' in text
        assert 'DevelopmentEffectIds.MASTER_CHI) * 2' not in text
        assert 'DevelopmentEffectIds.AWAKENED_CHI) * 3' not in text
        assert '50 XP за ранг' not in text


def test_magic_school_rank_xp_display_uses_shared_rule():
    android = read(ANDROID_MAGIC)
    assert 'MagicEquipmentRules.magicSchoolRankXp(power)' in android
    assert '${power * 25} XP' not in android



def test_character_sheet_stat_explanations_use_shared_formula_components():
    android = read(ANDROID_OVERVIEW)
    assert 'character.runMultiplier' in android
    assert 'character.runStormSpeedBonus' in android
    assert 'character.runRunnerBonus' in android
    assert 'character.strengthSizeModifier' in android
    assert 'character.speedSizeModifier' in android
    assert 'character.quickReflexesBonus' in android
    assert 'character.improvedInitiativeBonus' in android
    assert 'character.stormLordBonus' in android
    assert 'character.stalwartBonus' in android
    assert 'character.stillMountainBonus' in android
    assert 'private fun runMultiplier(' not in android
    assert 'character.size - 5' not in android
    assert '5 - character.size' not in android


def test_shared_rules_boundary_harness():
    from tools.tests.kotlin_harness_support import run_harness
    result = run_harness(HARNESS)
    assert 'RULES_BOUNDARY_OK' in result.stdout
