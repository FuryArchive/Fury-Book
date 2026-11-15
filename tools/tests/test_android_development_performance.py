from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
FEATS = (ROOT / 'app/src/main/java/com/furybook/android/ui/screens/FeatsScreen.kt').read_text(encoding='utf-8')
APP = (ROOT / 'app/src/main/java/com/furybook/android/ui/DublApp.kt').read_text(encoding='utf-8')
DEV_REPO = (ROOT / 'app/src/main/java/com/furybook/android/data/DevelopmentCatalogRepository.kt').read_text(encoding='utf-8')
CHI_REPO = (ROOT / 'app/src/main/java/com/furybook/android/data/ChiCatalogRepository.kt').read_text(encoding='utf-8')
PLANNER = (ROOT / 'shared/src/commonMain/kotlin/com/furybook/dubl/model/DevelopmentAcquisition.kt').read_text(encoding='utf-8')


def test_default_android_development_browser_filters_off_main_thread():
    assert 'produceState<DevelopmentFilterResult?>' in FEATS
    assert 'withContext(Dispatchers.Default)' in FEATS
    assert 'takeIf { it.key == filterKey }' in FEATS


def test_android_development_rows_use_ui_confined_lazy_availability():
    assert 'availabilityById' in FEATS
    assert 'val availabilityById = remember(character, catalog)' in FEATS
    assert 'availabilityById.getOrPut(entry.id) { rules.availability(entry) }' in FEATS
    row = FEATS.split('private fun DevelopmentRow(', 1)[1].split('@Composable\nprivate fun DevelopmentStatusPill', 1)[0]
    assert 'availability: DevelopmentAvailability' in row
    assert 'rules: DevelopmentRules' not in row
    assert 'rules.availability(entry)' not in row


def test_android_reverse_unlock_index_is_deferred_until_detail_selection():
    assert 'DevelopmentUnlockUiIndex' not in FEATS
    assert 'developmentUnlockIndex' not in FEATS
    assert 'val selectedUnlocksAsync by produceState<DevelopmentUnlockResult?>' in FEATS
    selected = FEATS.split('val selectedUnlocksAsync by produceState', 1)[1].split('val filteredChiTechniques', 1)[0]
    assert 'request.entryId == null' in selected
    assert 'withContext(Dispatchers.Default)' in selected
    assert 'workerPlanner.unlocks(request.entryId)' in selected
    assert 'workerContext.ensureActive()' in selected


def test_android_app_start_does_not_prewarm_large_development_catalogs():
    assert 'DevelopmentCatalogRepository(appContext).load()' not in APP
    assert 'ChiCatalogRepository(appContext).load()' not in APP
    assert 'delay(250)' not in APP


def test_android_catalogs_remain_cached_after_first_real_use():
    assert 'cache[cacheKey]' in DEV_REPO and 'synchronized(lock)' in DEV_REPO
    assert '@Volatile' in CHI_REPO and 'cached' in CHI_REPO and 'synchronized' in CHI_REPO


def test_shared_planner_still_exposes_reverse_unlock_index_once():
    assert 'fun unlockCounts()' in PLANNER
    assert 'private val unlocksIndex' in PLANNER
