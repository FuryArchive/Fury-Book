"""Exact authored source identities added after the original runtime promotion.

These expectations use retained source bindings, never names or the generated
catalog under test. Legacy catalog counts remain locked separately.
"""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
FACTS = ROOT / "rulesets/dubl-3.69/acquisition"


def _facts(name):
    return json.loads((FACTS / name).read_text(encoding="utf-8"))


def native_martial_ids():
    records = _facts("native_martial_rules.json")["records"]
    ids = {record["ref"]["entryId"] for record in records}
    assert len(ids) == len(records) == 501
    return ids


def native_core_development_ids():
    occurrences = _facts("native_occurrence_bindings.json")["bindings"]
    recovered = {record["entryId"] for record in occurrences if record["newDefinition"]}
    drawbacks = {record["id"] for record in _facts("native_drawback_rules.json")["records"]}
    proficiency = {_facts("native_weapon_proficiencies.json")["definition"]["id"]}
    assert len(recovered) == 11 and len(drawbacks) == 52
    assert not (recovered & drawbacks or recovered & proficiency or drawbacks & proficiency)
    return recovered | drawbacks | proficiency


def native_magic_ids(kind):
    records = _facts("native_magic_supplement_rules.json")["records"]
    selected = [record for record in records if record["kind"] == kind]
    ids = {record["id"] for record in selected}
    assert len(ids) == len(selected)
    assert len(ids) == {"DEVELOPMENT": 22, "SPELL": 33, "SKILL": 1}[kind]
    return ids


def assert_native_runtime_ids(filename, entries):
    expected = {
        "development_martial_catalog.json": native_martial_ids,
        "development_native_supplement_catalog.json": native_core_development_ids,
        "development_native_magic_catalog.json": lambda: native_magic_ids("DEVELOPMENT"),
    }.get(filename)
    if expected is not None:
        ids = [entry["id"] for entry in entries]
        assert len(ids) == len(set(ids)), filename
        assert set(ids) == expected(), filename
