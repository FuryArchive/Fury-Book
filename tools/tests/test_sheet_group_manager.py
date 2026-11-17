from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SHEET = ROOT / "shared/src/commonMain/kotlin/com/furybook/dubl/ui/sheet"


def test_group_manager_supports_drag_rename_delete_create_collapse_and_reset():
    source = (SHEET / "SheetGroupManager.kt").read_text(encoding="utf-8") + (SHEET / "SheetGroupDrag.kt").read_text(encoding="utf-8")
    for text in ("FuryDialogScaffold(", "detectDragGesturesAfterLongPress", "detectDragGestures", "moveGroupToIndex",
                 "subtreeBlock", "renameGroup", "deleteGroup", "addGroup", "toggleCollapsed", "По умолчанию",
                 "SheetGroupKind.SKILLS", "SheetGroupKind.DEVELOPMENT", "applyGroupDrop(", "dropIndex("):
        assert text in source, text
    assert "Color(0x" not in source
