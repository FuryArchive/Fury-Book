from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SHELL = ROOT / "shared/src/commonMain/kotlin/com/furybook/ui/components/FuryShell.kt"


def phone_shell_source() -> str:
    source = SHELL.read_text(encoding="utf-8")
    start = source.index("private fun <T> PhoneShell(")
    end = source.index("data class FuryShellCharacter", start)
    return source[start:end]


def test_phone_shell_keeps_content_in_one_place_when_chrome_hides():
    # Hiding the bars must not move content() to another branch: that recreates the sheet's
    # remembered state, so the editor would close the moment it opened (character editor stage).
    body = phone_shell_source()
    assert body.count("content()") == 1, body.count("content()")
    assert "if (!chromeHidden)" in body
