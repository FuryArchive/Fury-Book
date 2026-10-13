from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
FEATS = (ROOT / 'app/src/main/java/com/furybook/android/ui/screens/FeatsScreen.kt').read_text(encoding='utf-8')
APP = (ROOT / 'app/src/main/java/com/furybook/android/ui/DublApp.kt').read_text(encoding='utf-8')
DEV_REPO = (ROOT / 'app/src/main/java/com/furybook/android/data/DevelopmentCatalogRepository.kt').read_text(encoding='utf-8')
CHI_REPO = (ROOT / 'app/src/main/java/com/furybook/android/data/ChiCatalogRepository.kt').read_text(encoding='utf-8')
PLANNER = (ROOT / 'shared/src/commonMain/kotlin/com/furybook/dubl/model/DevelopmentAcquisition.kt').read_text(encoding='utf-8')


def test_default_android_development_browser_filters_off_main_thread():
    assert 'produceState<List<DevelopmentEntry>?>' in FEATS
    assert 'withContext(Dispatchers.Default)' in FEATS
    assert 'val filteredEntries = publishedRows?.second.orEmpty()' in FEATS


def test_android_development_rows_use_precomputed_requirements_after_loading_gate():
    assert 'availabilityById' in FEATS
    assert 'prepareDevelopmentRows(entries, { it.id }, ::calculateAvailability)' in FEATS
    row = FEATS.split('private fun DevelopmentRow(', 1)[1].split('@Composable\nprivate fun DevelopmentStatusPill', 1)[0]
    assert 'availability: DevelopmentAvailability' in row
    assert 'rules: DevelopmentRules' not in row
    assert 'rules.availability(entry)' not in row


def test_android_reverse_unlock_index_is_deferred_until_detail_selection():
    assert 'DevelopmentUnlockUiIndex' not in FEATS
    assert 'developmentUnlockIndex' not in FEATS
    assert 'val selectedUnlocks by produceState<List<DevelopmentEntry>?>' in FEATS
    selected = FEATS.split('val selectedUnlocks by produceState', 1)[1].split('val filteredChiTechniques', 1)[0]
    assert 'selectedEntryId' in selected
    assert 'withContext(Dispatchers.Default)' in selected
    assert 'hintCache.get(content.acquisition).dependents(reference)' in selected
    assert 'it.entryId == ref.entryId } == 1' in selected


def test_android_app_start_does_not_prewarm_large_development_catalogs():
    assert 'DevelopmentCatalogRepository(appContext).load()' not in APP
    assert 'ChiCatalogRepository(appContext).load()' not in APP
    assert 'delay(250)' not in APP


def test_android_catalogs_remain_cached_after_first_real_use():
    assert 'val cacheKey = composition.active.joinToString' in DEV_REPO
    assert 'synchronized(lock)' in DEV_REPO and 'cache[cacheKey]?.let { return it }' in DEV_REPO
    assert 'cache[cacheKey] = loaded' in DEV_REPO and 'cache.clear()' in DEV_REPO
    assert '@Volatile' in CHI_REPO and 'cached' in CHI_REPO and 'synchronized' in CHI_REPO


def test_reverse_hints_use_typed_catalog_index_not_legacy_string_purchasing():
    hints = (ROOT / 'shared/src/commonMain/kotlin/com/furybook/dubl/acquisition/AcquisitionDependencyHints.kt').read_text()
    assert 'private val reverse: Map<DefinitionRef, List<DefinitionRef>>' in hints
    assert 'fun dependents(definition: DefinitionRef)' in hints
    assert 'fun counts(): Map<DefinitionRef, Int>' in hints
    assert 'hintCache.get(content.acquisition).dependents(reference)' in FEATS
    assert 'planner.unlocks(' not in FEATS
