from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]

def test_opt_in_gate_is_at_real_desktop_construction_and_blocks_cloud():
    main = (ROOT / 'desktopApp/src/main/kotlin/com/furybook/desktop/Main.kt').read_text()
    assert 'furybook.acquisition.local' in main
    assert 'localAcquisitionEnabled = localAcquisitionEnabled' in main
    assert 'if (state.canonicalMode)' in main
    assert main.index('legacyCloudForStorage(acquisitionStore, localAcquisitionEnabled)') < main.index('DesktopCloudController(baseCharacters, baseExtras).also')
    gate = (ROOT / 'desktopApp/src/main/kotlin/com/furybook/desktop/DesktopStartupGate.kt').read_text()
    assert 'store?.hasProtectedHeads() != false' in gate
    assert 'null else create()' in gate
    assert 'LocalAcquisitionStoragePanel(state)' in main
    assert 'return' in main[main.index('LocalAcquisitionStoragePanel(state)'):main.index('LaunchedEffect(cloudController)')]

def test_storage_failures_are_visible_and_startup_never_substitutes_fresh():
    main = (ROOT / 'desktopApp/src/main/kotlin/com/furybook/desktop/Main.kt').read_text()
    assert 'Не удалось открыть данные' in main
    assert 'state.storageError' in main
    assert 'SnapshotCodec.fresh' not in main

def test_migration_declaration_edit_invalidates_confirmation_and_captured_packs_are_shown():
    main = (ROOT / 'desktopApp/src/main/kotlin/com/furybook/desktop/Main.kt').read_text()
    field = main[main.index('OutlinedTextField(packs'):main.index('Text("Подготовить миграцию")')]
    assert 'state.declareLegacyComposition(' in field
    assert 'prepared.ledger.opening.legacyMigration?.origin?.activePackIds?.joinToString()' in main
    assert 'state.pendingLegacyMigration!!.origin.activePackIds.joinToString()' in main
