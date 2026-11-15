from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
FEATS = (ROOT / "app/src/main/java/com/furybook/android/ui/screens/FeatsScreen.kt").read_text()
RULES = (ROOT / "shared/src/commonMain/kotlin/com/furybook/dubl/model/DevelopmentModels.kt").read_text()
PLANNER = (ROOT / "shared/src/commonMain/kotlin/com/furybook/dubl/model/DevelopmentAcquisition.kt").read_text()


def test_catalog_preparation_does_not_depend_on_owned_ranks():
    key = FEATS.split("private data class DevelopmentCatalogInput(", 1)[1].split(")", 1)[0]
    assert "DublCharacter" not in key
    for name in ("overrides", "customEntries", "includeChi"):
        assert name in key
    assert "key2 = catalogInput" in FEATS
    assert "val rules = remember(character, catalog)" in FEATS
    assert "val economy = remember(character, catalog" in FEATS


def test_workers_own_their_mutable_rules_and_plan_caches():
    filtered = FEATS.split("val filteredEntriesAsync by produceState", 1)[1].split("val filteredEntries =", 1)[0]
    assert "val workerRules = DevelopmentRules(" in filtered
    assert "val workerPlanner = DevelopmentAcquisitionPlanner(" in filtered
    assert "workerRules.availability" in filtered
    assert "availabilityFor(entry)" not in filtered
    assert "filterKey" in filtered and "ensureActive()" in filtered
    unlocks = FEATS.split("val selectedUnlocksAsync by produceState", 1)[1].split("val filteredChiTechniques", 1)[0]
    assert "DevelopmentAcquisitionPlanner(" in unlocks
    assert "ensureActive()" in unlocks and "unlockKey" in unlocks


def test_shared_cpu_loops_accept_cooperative_cancellation():
    assert "private val cancellationCheckpoint: () -> Unit = {}" in RULES
    assert "private val cancellationCheckpoint: () -> Unit = {}" in PLANNER
    assert RULES.count("cancellationCheckpoint()") >= 2
    assert PLANNER.count("cancellationCheckpoint()") >= 4
