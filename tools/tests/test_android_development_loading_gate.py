from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
FEATS = (ROOT / 'app/src/main/java/com/furybook/android/ui/screens/FeatsScreen.kt').read_text(encoding='utf-8')


def test_android_development_has_full_loading_gate_before_browser_mounts():
    assert 'private data class DevelopmentScreenPreparation' in FEATS
    assert 'private fun DevelopmentLoadingScreen(' in FEATS
    assert 'CircularProgressIndicator(' in FEATS
    assert 'Первое открытие может занять несколько секунд' in FEATS
    assert 'if (prepared == null)' in FEATS
    loading_gate = FEATS.split('if (prepared == null)', 1)[1].split('val catalog = prepared.catalog', 1)[0]
    assert 'DevelopmentLoadingScreen(' in loading_gate
    assert 'return' in loading_gate


def test_android_prepares_only_static_catalog_off_main_thread():
    preparation = FEATS.split('val preparation by produceState', 1)[1].split('val catalog = prepared.catalog', 1)[0]
    assert 'withContext(Dispatchers.Default)' in preparation
    assert 'DevelopmentScreenIndex(effectiveCatalog)' in preparation
    assert 'DevelopmentRules(' not in preparation
    assert 'key2 = catalogInput' in preparation
    assert 'key2 = character' not in preparation
    assert 'takeIf { it.input == catalogInput }' in preparation


def test_android_development_rows_do_not_run_requirement_engine_during_composition():
    row = FEATS.split('private fun DevelopmentRow(', 1)[1].split('@Composable\nprivate fun DevelopmentStatusPill', 1)[0]
    assert 'availability: DevelopmentAvailability' in row
    assert 'rules: DevelopmentRules' not in row
    assert 'rules.availability(entry)' not in row

    owned = FEATS.split('private fun OwnedDevelopmentRow(', 1)[1].split('private fun developmentRequirementNeedLabel', 1)[0]
    assert 'availability: DevelopmentAvailability' in owned
    assert 'rules.availability(entry)' not in owned
