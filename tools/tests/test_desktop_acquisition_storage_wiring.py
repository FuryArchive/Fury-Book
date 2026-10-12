from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]

def test_opt_in_gate_is_at_real_desktop_construction_and_blocks_cloud():
    main = (ROOT / 'desktopApp/src/main/kotlin/com/furybook/desktop/Main.kt').read_text()
    assert 'furybook.acquisition.local' in main
    assert 'localAcquisitionEnabled = localAcquisitionEnabled' in main
    assert 'if (localAcquisitionEnabled)' in main
    assert 'LocalAcquisitionStoragePanel(state)' in main
    assert 'return' in main[main.index('LocalAcquisitionStoragePanel(state)'):main.index('LaunchedEffect(cloudController)')]

def test_storage_failures_are_visible_and_startup_never_substitutes_fresh():
    main = (ROOT / 'desktopApp/src/main/kotlin/com/furybook/desktop/Main.kt').read_text()
    assert 'Не удалось открыть данные' in main
    assert 'state.storageError' in main
    assert 'SnapshotCodec.fresh' not in main
