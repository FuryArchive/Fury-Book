from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
APP = (ROOT / 'app/src/main/java/com/furybook/android/ui/DublApp.kt').read_text(encoding='utf-8')
DEV_REPO = (ROOT / 'app/src/main/java/com/furybook/android/data/DevelopmentCatalogRepository.kt').read_text(encoding='utf-8')
CHI_REPO = (ROOT / 'app/src/main/java/com/furybook/android/data/ChiCatalogRepository.kt').read_text(encoding='utf-8')
PLANNER = (ROOT / 'shared/src/commonMain/kotlin/com/furybook/dubl/model/DevelopmentAcquisition.kt').read_text(encoding='utf-8')


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
