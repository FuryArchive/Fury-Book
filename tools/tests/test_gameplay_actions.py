from pathlib import Path
import tempfile


def test_overexertion_transitions_execute_shared_code():
    from tools.tests.kotlin_harness_support import run_harness

    harness = Path(__file__).parent / "kotlin/GameplayActionHarness.kt"
    result = run_harness(harness)
    assert "GameplayActionHarness: 32 assertions passed" in result.stdout


def test_gameplay_history_reexecutes_receipts():
    from tools.tests.kotlin_harness_support import run_harness
    result = run_harness(Path(__file__).parent / "kotlin/GameplayJournalHarness.kt")
    assert "GameplayJournalHarness: 14 assertions passed" in result.stdout


def test_gameplay_history_codec_is_strict_and_lossless():
    from tools.tests.kotlin_harness_support import run_harness
    result = run_harness(Path(__file__).parent / "kotlin/GameplayJournalCodecHarness.kt")
    assert "GameplayJournalCodecHarness: 29 assertions passed" in result.stdout


def test_gameplay_envelope_is_opt_in_and_preserves_acquisition_history():
    from tools.tests.kotlin_harness_support import run_harness
    result = run_harness(Path(__file__).parent / "kotlin/GameplayEnvelopeHarness.kt")
    assert "GameplayEnvelopeHarness: 16 assertions passed" in result.stdout


def test_gameplay_service_cas_and_uncertain_result_recovery():
    from tools.tests.kotlin_harness_support import run_harness
    result = run_harness(Path(__file__).parent / "kotlin/GameplayServiceHarness.kt")
    assert "GameplayServiceHarness: 22 assertions passed; simulated storage only" in result.stdout


def test_gameplay_desktop_survives_fresh_process_and_faults():
    from tools.tests.kotlin_harness_support import run_harness
    root = Path(__file__).resolve().parents[2]
    sources = [root / "shared/src/desktopMain/kotlin/com/furybook/dubl/data" / name for name in (
        "DesktopCharacterStore.kt", "DesktopCharacterExtrasStore.kt", "DesktopAcquisitionStore.kt")]
    with tempfile.TemporaryDirectory(prefix="furybook-gameplay-") as directory:
        for phase in ("start", "finish", "verify", "faults"):
            result = run_harness(Path(__file__).parent / "kotlin/GameplayDesktopHarness.kt",
                                 extra_sources=sources, args=(directory, phase))
            assert f"GAMEPLAY_DESKTOP_PHASE_OK:{phase}" in result.stdout
