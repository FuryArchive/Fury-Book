from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
STATE = ROOT / 'desktopApp/src/main/kotlin/com/furybook/desktop/DesktopAppState.kt'
UI_FILES = [
    ROOT / 'desktopApp/src/main/kotlin/com/furybook/desktop/Main.kt',
    *sorted((ROOT / 'desktopApp/src/main/kotlin/com/furybook/desktop/screens').glob('*.kt')),
]


def test_desktop_state_exposes_observable_snapshot_backed_runtime_projection():
    text = STATE.read_text(encoding='utf-8')
    assert 'val activeCharacter: DublCharacter' in text
    assert 'snapshot.activeCharacter' in text
    assert 'withoutRuntimeDevelopmentEffects' in text
    assert 'suppressedEntryIds = chiDevelopmentIds' in text


def test_compose_ui_never_reads_unobservable_session_active_directly():
    offenders = []
    for path in UI_FILES:
        text = path.read_text(encoding='utf-8')
        if 'state.session.active' in text:
            offenders.append(path.relative_to(ROOT).as_posix())
    assert not offenders, 'unobservable active-character reads: ' + ', '.join(offenders)


def test_desktop_session_is_private_behind_observable_state_adapter():
    text = STATE.read_text(encoding='utf-8')
    assert 'private var application = createApplication()' in text
    assert 'private fun createApplication(): DublApplication' in text
    assert 'application = createApplication(); refresh()' in text
    assert 'CharacterSession' not in text
