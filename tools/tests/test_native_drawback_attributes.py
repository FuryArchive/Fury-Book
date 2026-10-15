from pathlib import Path
from tools.rulebook.generate_drawback_attributes import render, TARGET


def test_effective_attribute_modifiers_are_generated_from_exact_native_drawback_facts():
    root = Path(__file__).resolve().parents[2]
    assert (root / TARGET).read_text() == render(root)
