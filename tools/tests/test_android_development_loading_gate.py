from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
FEATS = (ROOT / 'app/src/main/java/com/furybook/android/ui/screens/FeatsScreen.kt').read_text(encoding='utf-8')


def test_android_development_has_full_loading_gate_before_browser_mounts():
    assert 'private data class DevelopmentScreenPreparation' in FEATS
    assert 'private fun DevelopmentLoadingScreen(' in FEATS
    assert 'CircularProgressIndicator(' in FEATS
    assert 'Первое открытие может занять несколько секунд' in FEATS
    assert 'if (preparation == null || preparation?.preparedFor != character || (acquisition?.canonical == true && canonicalView == null))' in FEATS
    loading_gate = FEATS.split('if (preparation == null || preparation?.preparedFor != character || (acquisition?.canonical == true && canonicalView == null))', 1)[1].split('val prepared = preparation!!', 1)[0]
    assert 'DevelopmentLoadingScreen(' in loading_gate
    assert 'return' in loading_gate


def test_android_development_precomputes_row_availability_off_main_thread():
    from tools.tests.kotlin_harness_support import run_harness
    folder = ROOT / 'app/src/main/java/com/furybook/android/ui/screens'
    result = run_harness(ROOT / 'tools/tests/kotlin/PresentationPreparationHarness.kt', extra_sources=[
        folder / name for name in ('PreparedDevelopmentRows.kt', 'AndroidDependencyHintCache.kt', 'ContentImportRefresh.kt')])
    assert 'PRESENTATION_PREPARATION_OK' in result.stdout
    assert 'prepareDevelopmentRows(entries, { it.id }, ::calculateAvailability)' in FEATS
    assert 'checkNotNull(publishedRows).third.getValue(entry.id)' in FEATS


def test_android_development_rows_do_not_run_requirement_engine_during_composition():
    row = FEATS.split('private fun DevelopmentRow(', 1)[1].split('@Composable\nprivate fun DevelopmentStatusPill', 1)[0]
    assert 'availability: DevelopmentAvailability' in row
    assert 'rules: DevelopmentRules' not in row
    assert 'rules.availability(entry)' not in row

    owned = FEATS.split('private fun OwnedDevelopmentRow(', 1)[1].split('private fun developmentRequirementNeedLabel', 1)[0]
    assert 'availability: DevelopmentAvailability' in owned
    assert 'rules.availability(entry)' not in owned
