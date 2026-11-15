from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
FEATS = (ROOT / 'app/src/main/java/com/furybook/android/ui/screens/FeatsScreen.kt').read_text(encoding='utf-8')


def test_android_has_same_build_filters_and_plan_workflow():
    for token in ('DevelopmentBrowserFilter', 'Можно взять', 'Почти доступно', 'План'):
        assert token in FEATS
    assert 'plannedDevelopmentIds' in FEATS
    assert 'DevelopmentAcquisitionPlanner' in FEATS


def test_android_detail_sheet_surfaces_missing_requirements_unlocks_and_auto_acquire():
    assert 'DevelopmentDetailSheet(' in FEATS
    assert 'val selectedUnlocksAsync by produceState<DevelopmentUnlockResult?>' in FEATS
    assert 'unlocks = selectedUnlocks' in FEATS
    assert 'unlocks: List<DevelopmentEntry>?' in FEATS
    assert 'Что нужно сделать' in FEATS
    assert 'Открывает' in FEATS
    assert 'Добрать требования' in FEATS
    assert 'Добрать и взять' in FEATS


def test_android_acquisition_preview_supports_or_choices_and_shared_atomic_apply():
    assert 'DevelopmentAcquisitionPreviewDialog' in FEATS
    assert 'choiceSelections' in FEATS
    assert 'controller.acquireDevelopment(catalog' in FEATS
    assert 'DevelopmentAcquisitionChoice' in FEATS


def test_android_ability_auto_acquisition_uses_selected_source_option():
    assert 'Источник способности' in FEATS
    assert 'onAcquireAll: (Int) -> Unit' in FEATS
    assert 'optionIndex = optionIndex' in FEATS
    assert 'onClick = { onAcquireAll(optionIndex) }' in FEATS
