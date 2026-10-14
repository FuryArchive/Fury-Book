from pathlib import Path


def test_overexertion_transitions_execute_shared_code():
    from tools.tests.kotlin_harness_support import run_harness

    harness = Path(__file__).parent / "kotlin/GameplayActionHarness.kt"
    result = run_harness(harness)
    assert "GameplayActionHarness: 32 assertions passed" in result.stdout
