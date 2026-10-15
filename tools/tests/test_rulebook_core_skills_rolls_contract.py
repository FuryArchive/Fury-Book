from pathlib import Path
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
MODEL = ROOT / 'shared/src/commonMain/kotlin/com/furybook/dubl/model'
CHARACTER = (MODEL / 'CharacterModels.kt').read_text(encoding='utf-8')
SKILLS = (MODEL / 'SkillModels.kt').read_text(encoding='utf-8')
ROLLS = (MODEL / 'CharacterRollContexts.kt').read_text(encoding='utf-8')
RULES = MODEL / 'SkillCheckRules.kt'
ANDROID_OVERVIEW = (ROOT / 'app/src/main/java/com/furybook/android/ui/screens/OverviewScreen.kt').read_text(encoding='utf-8')
HARNESS = ROOT / 'tools/tests/kotlin/SkillCheckRulesHarness.kt'
DESKTOP_SHEET = (ROOT / 'desktopApp/src/main/kotlin/com/furybook/desktop/screens/CharacterSheetScreen.kt').read_text(encoding='utf-8')
DESKTOP_ROLL = (ROOT / 'desktopApp/src/main/kotlin/com/furybook/desktop/screens/RollDialog.kt').read_text(encoding='utf-8')
ROLL_RULES = (MODEL / 'RollRules.kt').read_text(encoding='utf-8')


def test_load_penalty_is_a_shared_roll_rule_for_attacks_and_dexterity_checks():
    assert 'fun DublCharacter.rollLoadPenalty(' in CHARACTER
    assert 'attack || attribute == AttributeId.DEXTERITY' in CHARACTER
    assert 'rollLoadPenalty(selectedAttribute)' in SKILLS
    assert 'rollLoadPenalty(attribute)' in ROLLS
    assert 'rollLoadPenalty(attribute, attack = attack)' in ROLLS


def test_quick_fortitude_and_initiative_use_all_canonical_passive_components():
    assert 'RollContribution("Неподвижная гора", stillMountainBonus)' in ROLLS
    assert 'RollContribution("Владыка бури", stormLordBonus)' in ROLLS




def test_run_quick_roll_is_a_shared_context_using_the_derived_run_value():
    assert 'RUN("Бег")' in ROLLS
    assert 'RollContext.RUN -> fixedPreset(' in ROLLS
    assert 'RollContribution("Бег", runFull.toInt())' in ROLLS

def test_skill_synergy_and_assistance_are_shared_rulebook_rules():
    assert RULES.exists()
    text = RULES.read_text(encoding='utf-8')
    assert 'fun synergyCombinedRank(firstRank: Int, secondRank: Int): Int' in text
    assert 'fun assistanceBonus(helperSkillRank: Int, helperRollTotal: Int): Int?' in text
    assert '(lowerRank + 1) / 2' in text
    assert '1 + (helperRollTotal - 10) / 4' in text


def test_android_quick_fortitude_uses_shared_roll_preset_instead_of_stale_formula_copy():
    fortitude_sheet = ANDROID_OVERVIEW.split('if (showFortitudeRoll) {', 1)[1].split('selectedRollContext?.let', 1)[0]
    assert 'ContextRollSheet(' in fortitude_sheet
    assert 'context = RollContext.FORTITUDE' in fortitude_sheet
    context_sheet = ANDROID_OVERVIEW.split('private fun ContextRollSheet(', 1)[1].split('\n@Composable', 1)[0]
    assert 'val basePreset = character.rollPreset(context, selectedSkillId, selectedAttribute)' in context_sheet
    assert 'formulaText = preset.formulaText' in context_sheet
    assert 'checkBonus = preset.bonus' in context_sheet


def test_skill_check_rules_execute_without_platform_dependencies():
    kotlinc = shutil.which('kotlinc')
    assert kotlinc is not None
    with tempfile.TemporaryDirectory() as td:
        jar = Path(td) / 'skill-check-rules.jar'
        compiled = subprocess.run(
            [kotlinc, str(RULES), str(HARNESS), '-include-runtime', '-d', str(jar)],
            cwd=ROOT, capture_output=True, text=True, timeout=30,
        )
        assert compiled.returncode == 0, compiled.stderr
        result = subprocess.run(
            ['java', '-jar', str(jar)], cwd=ROOT, capture_output=True, text=True, timeout=10,
        )
        assert result.returncode == 0, result.stderr + result.stdout
        assert 'SKILL_CHECK_RULES_OK' in result.stdout


def test_negative_health_is_preserved_and_damage_controls_do_not_stop_at_zero():
    assert 'hpCurrent = clamped.hpCurrent.coerceAtMost(clamped.healthMaximum)' in CHARACTER
    assert '(before + requestedDelta).coerceAtMost(character.healthMaximum)' in ANDROID_OVERVIEW
    assert 'enabled = amount > 0 && current > 0' not in ANDROID_OVERVIEW
    assert 'enabled = amount > 0 && current > 0' not in DESKTOP_SHEET


def test_target_comparison_respects_universal_critical_failure_and_platforms_use_roll_aware_api():
    assert 'fun compareRollToTarget(roll: RollResult, target: Int): RollTargetComparison' in ROLL_RULES
    assert 'RollSpecialResult.CRITICAL_FAILURE' in ROLL_RULES
    assert 'RollSpecialResult.CONFIRMED_CRITICAL_FAILURE' in ROLL_RULES
    assert 'compareRollToTarget(roll.total, target)' not in ANDROID_OVERVIEW
    assert 'compareRollToTarget(roll.total, target)' not in DESKTOP_ROLL
    assert 'compareRollToTarget(roll, target)' in ANDROID_OVERVIEW
    assert DESKTOP_ROLL.count('compareRollToTarget(roll, target)') >= 2
