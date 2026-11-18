from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
COMPONENTS = ROOT / "shared/src/commonMain/kotlin/com/furybook/ui/components"
SHEET = ROOT / "shared/src/commonMain/kotlin/com/furybook/dubl/ui/sheet"
WEB = ROOT / "webApp/src/jsMain/kotlin/com/furybook/web/Main.kt"


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def test_bottom_sheet_dialog_pads_for_the_keyboard():
    assert "imePadding()" in read(COMPONENTS / "FuryDialogScaffold.kt")


def test_tabs_animate_their_selected_colour():
    tabs = read(COMPONENTS / "FuryTabs.kt")
    assert tabs.count("animateColorAsState") >= 2


def test_shell_has_an_account_slot_and_web_uses_it_for_sign_out():
    shell = read(COMPONENTS / "FuryShell.kt")
    assert "account: (@Composable () -> Unit)? = null" in shell
    web = read(WEB)
    assert "account = {" in web
    assert "onSignOut" in web.split("account = {", 1)[1][:600]

