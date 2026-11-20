from pathlib import Path

# Stage 4: what the old Навыки tabs (Android FeatsScreen, desktop/web DevelopmentScreen) did now lives in the
# editor's Навыки step. These guards replace the old cockpit / purchase-parity guards and pin the same functions.
ROOT = Path(__file__).resolve().parents[2]
EDITOR = ROOT / "shared/src/commonMain/kotlin/com/furybook/dubl/ui/editor"
MODELS = (EDITOR / "EditorPerkModels.kt").read_text(encoding="utf-8")
STEP = (EDITOR / "EditorPerksStep.kt").read_text(encoding="utf-8")
ACTIONS = (EDITOR / "EditorPerkActions.kt").read_text(encoding="utf-8")
STATE = (EDITOR / "EditorState.kt").read_text(encoding="utf-8")


def test_build_filters_and_plan_workflow():
    for token in ("EditorPerkFilter.AVAILABLE", "EditorPerkFilter.ALMOST", "EditorPerkFilter.PLAN"):
        assert token in MODELS
    for label in ('"Доступно"', '"Почти"', '"План"', "План персонажа", "Взять план", "☆ В план", "★ В плане"):
        assert label in STEP, label
    assert "fun togglePlanned(" in STATE and "plannedIds" in STATE
    assert "DevelopmentAcquisitionRequest(targets = targets.map { DevelopmentAcquisitionTarget(it) }" in MODELS


def test_detail_surfaces_requirements_unlocks_and_auto_acquire():
    assert "planner.unlocks(entry.id)" in MODELS and "EditorPerkLink(" in MODELS
    assert "targetEntryId" in MODELS
    assert "state.openPerk(context" in STEP
    for label in ('"Открывает"', '"Требования"', "Добрать требования", "с цепочкой", "Взять все оставшиеся ранги"):
        assert label in STEP or label in MODELS, label
    assert "includeTarget = false" in MODELS


def test_or_choices_and_one_atomic_apply():
    assert "choiceSelections" in MODELS and "plan?.choices" in MODELS
    assert "host.acquireDevelopment(action.request)" in ACTIONS


def test_ability_source_option_drives_availability_and_cost():
    assert '"Источник ОС"' in MODELS
    assert "rules.availability(entry, option)" in MODELS
    assert "rules.abilityCost(entry, option)" in MODELS
    assert "state.optionIndex = index" in STEP
    # The take button names the ОС price (the old «Будет потрачено N ОС» confirmation).
    assert 'os.takeIf { it > 0 }?.let { "$it ОС" }' in MODELS


def test_forced_take_requires_confirmation():
    assert "Требования не выполнены" in STEP
    assert '"Открыть всё равно"' in MODELS and '"Взять всё равно"' in MODELS
    assert "confirmForce" in STEP
