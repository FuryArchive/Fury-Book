from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
RULES = (ROOT / "shared/src/commonMain/kotlin/com/furybook/dubl/model/DevelopmentModels.kt").read_text()
PLANNER = (ROOT / "shared/src/commonMain/kotlin/com/furybook/dubl/model/DevelopmentAcquisition.kt").read_text()


def test_shared_cpu_loops_accept_cooperative_cancellation():
    assert "private val cancellationCheckpoint: () -> Unit = {}" in RULES
    assert "private val cancellationCheckpoint: () -> Unit = {}" in PLANNER
    assert RULES.count("cancellationCheckpoint()") >= 2
    assert PLANNER.count("cancellationCheckpoint()") >= 4
