#!/usr/bin/env python3
from __future__ import annotations

import argparse
import gc
import json
from pathlib import Path

from .import_acquisition import (build_acquisition_catalogs, correct_development_payload,
                                 build_native_development_supplement)
from .extract_docx import extract_docx, write_json
from .diagnostics import make_diagnostic
from .provenance import attach_provenance
from .source_index import build_source_index
from .source_diagnostics import detect_source_diagnostics
from .import_conditions import import_conditions
from .import_skills import import_skills
from .import_ability_roots import import_ability_roots, compare_ability_roots_runtime, promote_ability_roots
from .import_martial_arts import import_martial_arts, compare_martial_runtime, promote_martial_arts
from .import_chi import import_chi, compare_chi_runtime, promote_chi_development, promote_chi_catalog
from .import_magic import import_core_spells, import_archmage_spells, compare_spells_runtime, promote_spell_catalog
from .import_equipment import import_gear, compare_gear_runtime, promote_gear_catalog
from .import_skill_effects import import_skill_effects
from .import_development import (
    import_regular_development,
    import_special_development,
    compare_regular_development_runtime,
    promote_regular_development,
    promote_special_development,
    import_magic_development,
    promote_magic_development,
)

DOMAIN_FILES = {
    "development": ("development_catalog.json", "development.json", ["entries"]),
    "chi": ("chi_catalog.json", "chi.json", ["schools", "techniques"]),
    "magic_equipment": ("magic_equipment_catalog.json", "magic_equipment.json", ["spells", "gear"]),
    "skill_effects": ("skill_effects_catalog.json", "skill_effects.json", ["effects"]),
}


def _write(data: dict, path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def _normalize_sources(source_or_sources: Path | dict[str, Path]) -> dict[str, Path]:
    if isinstance(source_or_sources, dict):
        return {str(key): Path(value) for key, value in source_or_sources.items()}
    return {"core": Path(source_or_sources)}


def build_ruleset(source_or_sources: Path | dict[str, Path], repo_root: Path, output_dir: Path) -> dict:
    sources = _normalize_sources(source_or_sources)
    repo_root = Path(repo_root)
    output_dir = Path(output_dir)
    config_path = repo_root / "rulesets/dubl-3.69/config.json"
    if config_path.exists():
        ruleset_config = json.loads(config_path.read_text(encoding="utf-8"))
    else:
        ruleset_config = {}
    domain_config = ruleset_config.get("domains", {})
    acquisition_enabled = (repo_root / "rulesets/dubl-3.69/acquisition/source_questions.json").is_file()

    def domain_meta(domain: str, base: dict) -> dict:
        tracked = domain_config.get(domain, {})
        merged = dict(base)
        for key in ("status", "sources", "runtimeArtifact", "compiledArtifact"):
            if key in tracked:
                merged[key] = tracked[key]
        return merged

    # Process source indexes/diagnostics incrementally. Holding every Raw IR plus every
    # full per-source index at once causes severe memory/GC pressure on large modules
    # such as the Archmage book. Generated domain importers currently need Core IR;
    # supplement IR remains reproducibly available under build/source if needed later.
    raw_by_source: dict[str, dict] = {}
    source_meta: dict[str, dict] = {}
    aggregate_stats = {"blocks": 0, "paragraphs": 0, "tables": 0, "headings": 0}
    merged_headings: dict[str, list[str]] = {}
    merged_text: dict[str, list[str]] = {}
    merged_blocks: dict[str, dict] = {}
    all_diagnostics: list[dict] = []

    for key in sorted(sources):
        raw_ir = extract_docx(sources[key])
        local_index = build_source_index(raw_ir, source_key=key)
        all_diagnostics.extend(detect_source_diagnostics({key: raw_ir}))
        source_meta[key] = raw_ir["source"]
        for stat in aggregate_stats:
            aggregate_stats[stat] += int(raw_ir["stats"].get(stat, 0))
        write_json(raw_ir, output_dir / f"source/{key}_raw_ir.json")

        for norm, refs in local_index.get("headings", {}).items():
            merged_headings.setdefault(norm, []).extend(refs)
        for norm, refs in local_index.get("text", {}).items():
            merged_text.setdefault(norm, []).extend(refs)
        merged_blocks.update(local_index.get("blocks", {}))

        if key == "core":
            raw_by_source[key] = raw_ir

    source_index = {
        "schemaVersion": 1,
        "sources": sorted(sources),
        "headings": {key: merged_headings[key] for key in sorted(merged_headings)},
        "text": {key: merged_text[key] for key in sorted(merged_text)},
        "blocks": merged_blocks,
    }
    _write(source_index, output_dir / "source/source_index.json")
    domains: dict[str, dict] = {}
    shared = repo_root / "shared/src/commonMain/resources/fcp/dubl-3.69/content"
    chi_shared = repo_root / "shared/src/commonMain/resources/fcp/dubl-chi-3.69/content"
    for domain, (input_name, output_name, list_keys) in DOMAIN_FILES.items():
        if domain in {"chi", "magic_equipment", "skill_effects"} and domain_config.get(domain, {}).get("status") == "source_generated":
            continue
        root = json.loads((shared / input_name).read_text(encoding="utf-8"))
        enriched, diagnostics, coverage = attach_provenance(root, list_keys, domain, source_index)
        _write(enriched, output_dir / "content" / output_name)
        all_diagnostics.extend(diagnostics)
        domains[domain] = domain_meta(domain, {
            "status": "bootstrap_mirror",
            "input": f"shared/src/commonMain/resources/fcp/dubl-3.69/content/{input_name}",
            "output": f"content/{output_name}",
            "coverage": coverage,
        })

    # Provenance bootstrap domains are complete. The merged source index is by far
    # the largest in-memory structure and generated-domain importers below work
    # directly from Raw IR, so release it before loading more module IR.
    del source_index, merged_headings, merged_text, merged_blocks
    gc.collect()

    development_bindings_path = repo_root / "rulesets/dubl-3.69/bindings/development_regular.json"
    if development_bindings_path.exists():
        development_bindings = json.loads(development_bindings_path.read_text(encoding="utf-8"))
        development_candidate, development_diagnostics = import_regular_development(
            raw_by_source["core"], development_bindings, "core"
        )
        runtime_development = json.loads((shared / "development_catalog.json").read_text(encoding="utf-8"))
        if any(entry.get("section") == "Навыки" for entry in runtime_development.get("entries", [])):
            development_diagnostics.extend(
                compare_regular_development_runtime(development_candidate, runtime_development)
            )
        promoted_development = promote_regular_development(
            development_candidate, development_bindings, version="3.69"
        )
        _write(development_candidate, output_dir / "content/development_regular_source.json")
        _write(correct_development_payload(promoted_development, repo_root) if acquisition_enabled else promoted_development, output_dir / "content/development_regular.json")
        all_diagnostics.extend(development_diagnostics)
        development_total = len(development_bindings.get("bindings", []))
        development_linked = len(development_candidate.get("entries", []))
        development_ambiguous = sum(
            1 for diagnostic in development_diagnostics
            if diagnostic.get("kind") == "development-source-ambiguous"
        )
        development_missing = max(0, development_total - development_linked - development_ambiguous)
        domains["development_regular"] = domain_meta("development_regular", {
            "status": "source_generated_candidate",
            "sourcePolicy": ["core"],
            "bindings": "rulesets/dubl-3.69/bindings/development_regular.json",
            "sourceOutput": "content/development_regular_source.json",
            "output": "content/development_regular.json",
            "coverage": {
                "total": development_total,
                "linked": development_linked,
                "ambiguous": development_ambiguous,
                "missing": development_missing,
            },
        })

    special_bindings_path = repo_root / "rulesets/dubl-3.69/bindings/development_special.json"
    if special_bindings_path.exists():
        special_bindings = json.loads(special_bindings_path.read_text(encoding="utf-8"))
        special_candidate, special_diagnostics = import_special_development(
            raw_by_source["core"], special_bindings, "core"
        )
        runtime_development = json.loads((shared / "development_catalog.json").read_text(encoding="utf-8"))
        if any(entry.get("section") == "Ветки способностей" for entry in runtime_development.get("entries", [])):
            special_diagnostics.extend(compare_regular_development_runtime(special_candidate, runtime_development))
        promoted_special = promote_special_development(special_candidate, special_bindings, version="3.69")
        _write(special_candidate, output_dir / "content/development_special_source.json")
        _write(correct_development_payload(promoted_special, repo_root) if acquisition_enabled else promoted_special, output_dir / "content/development_special.json")
        all_diagnostics.extend(special_diagnostics)
        special_total = len(special_bindings.get("bindings", []))
        special_linked = len(special_candidate.get("entries", []))
        special_ambiguous = sum(
            1 for diagnostic in special_diagnostics
            if diagnostic.get("kind") == "development-source-ambiguous"
        )
        special_missing = max(0, special_total - special_linked - special_ambiguous)
        domains["development_special"] = domain_meta("development_special", {
            "status": "source_generated_candidate",
            "sourcePolicy": ["core"],
            "bindings": "rulesets/dubl-3.69/bindings/development_special.json",
            "sourceOutput": "content/development_special_source.json",
            "output": "content/development_special.json",
            "coverage": {
                "total": special_total,
                "linked": special_linked,
                "ambiguous": special_ambiguous,
                "missing": special_missing,
            },
        })

    magic_dev_bindings_path = repo_root / "rulesets/dubl-3.69/bindings/development_magic.json"
    if magic_dev_bindings_path.exists():
        magic_dev_bindings = json.loads(magic_dev_bindings_path.read_text(encoding="utf-8"))
        magic_dev_candidate, magic_dev_diagnostics = import_magic_development(
            raw_by_source["core"], magic_dev_bindings, "core"
        )
        runtime_development = json.loads((shared / "development_catalog.json").read_text(encoding="utf-8"))
        if any(entry.get("section") == "Магические навыки" for entry in runtime_development.get("entries", [])):
            magic_dev_diagnostics.extend(compare_regular_development_runtime(magic_dev_candidate, runtime_development))
        promoted_magic_dev = promote_magic_development(magic_dev_candidate, magic_dev_bindings, version="3.69")
        _write(magic_dev_candidate, output_dir / "content/development_magic_source.json")
        _write(promoted_magic_dev, output_dir / "content/development_magic.json")
        all_diagnostics.extend(magic_dev_diagnostics)
        magic_dev_total = len(magic_dev_bindings.get("bindings", []))
        magic_dev_linked = len(magic_dev_candidate.get("entries", []))
        magic_dev_ambiguous = sum(
            1 for diagnostic in magic_dev_diagnostics
            if diagnostic.get("kind") == "development-source-ambiguous"
        )
        magic_dev_missing = max(0, magic_dev_total - magic_dev_linked - magic_dev_ambiguous)
        domains["development_magic"] = domain_meta("development_magic", {
            "status": "source_generated",
            "sourcePolicy": ["core"],
            "bindings": "rulesets/dubl-3.69/bindings/development_magic.json",
            "sourceOutput": "content/development_magic_source.json",
            "output": "content/development_magic.json",
            "coverage": {
                "total": magic_dev_total,
                "linked": magic_dev_linked,
                "ambiguous": magic_dev_ambiguous,
                "missing": magic_dev_missing,
            },
        })

    ability_bindings_path = repo_root / "rulesets/dubl-3.69/bindings/ability_roots.json"
    if ability_bindings_path.exists():
        ability_bindings = json.loads(ability_bindings_path.read_text(encoding="utf-8"))
        ability_candidate, ability_diagnostics = import_ability_roots(
            raw_by_source["core"], ability_bindings, "core"
        )
        runtime_development = json.loads((shared / "development_catalog.json").read_text(encoding="utf-8"))
        if any(entry.get("section") == "Особые способности" for entry in runtime_development.get("entries", [])):
            ability_diagnostics.extend(compare_ability_roots_runtime(ability_candidate, runtime_development))
        promoted_ability = promote_ability_roots(ability_candidate, ability_bindings, version="3.69")
        _write(ability_candidate, output_dir / "content/development_ability_roots_source.json")
        _write(correct_development_payload(promoted_ability, repo_root) if acquisition_enabled else promoted_ability,
               output_dir / "content/development_ability_roots.json")
        all_diagnostics.extend(ability_diagnostics)
        ability_total = len(ability_bindings.get("bindings", []))
        ability_linked = len(ability_candidate.get("entries", []))
        ability_ambiguous = sum(
            1 for diagnostic in ability_diagnostics
            if diagnostic.get("kind") == "ability-root-source-ambiguous"
        )
        ability_missing = max(0, ability_total - ability_linked - ability_ambiguous)
        domains["development_ability_roots"] = domain_meta("development_ability_roots", {
            "status": "source_generated_candidate",
            "sourcePolicy": ["core"],
            "bindings": "rulesets/dubl-3.69/bindings/ability_roots.json",
            "sourceOutput": "content/development_ability_roots_source.json",
            "output": "content/development_ability_roots.json",
            "coverage": {
                "total": ability_total,
                "linked": ability_linked,
                "ambiguous": ability_ambiguous,
                "missing": ability_missing,
            },
        })

    if "melee" in sources:
        melee_raw_path = output_dir / "source/melee_raw_ir.json"
        melee_raw = json.loads(melee_raw_path.read_text(encoding="utf-8"))
        martial_candidate, martial_diagnostics = import_martial_arts(melee_raw, "melee")
        runtime_development = json.loads((shared / "development_catalog.json").read_text(encoding="utf-8"))
        if any(entry.get("section") == "Боевые искусства" for entry in runtime_development.get("entries", [])):
            martial_diagnostics.extend(compare_martial_runtime(martial_candidate, runtime_development))
        promoted_martial = promote_martial_arts(martial_candidate, version="3.69")
        if (repo_root / "rulesets/dubl-3.69/acquisition/native_martial_rules.json").exists():
            from .native_martial_rules import correct_native_martial_payload
            promoted_martial = correct_native_martial_payload(promoted_martial, repo_root)
        _write(martial_candidate, output_dir / "content/development_martial_source.json")
        _write(promoted_martial, output_dir / "content/development_martial.json")
        all_diagnostics.extend(martial_diagnostics)
        martial_total = len(martial_candidate.get("entries", []))
        domains["development_martial"] = domain_meta("development_martial", {
            "status": "source_generated",
            "sourcePolicy": ["melee"],
            "sourceOutput": "content/development_martial_source.json",
            "output": "content/development_martial.json",
            "coverage": {"total": martial_total, "linked": martial_total, "ambiguous": 0, "missing": 0},
        })

        chi_candidate, chi_diagnostics = import_chi(melee_raw, "melee")
        runtime_development = json.loads((shared / "development_catalog.json").read_text(encoding="utf-8"))
        runtime_chi_path = chi_shared / "chi_catalog.json"
        if not runtime_chi_path.exists():
            runtime_chi_path = shared / "chi_catalog.json"
        runtime_chi = json.loads(runtime_chi_path.read_text(encoding="utf-8"))
        if any(entry.get("section") == "ЦИ" for entry in runtime_development.get("entries", [])):
            chi_diagnostics.extend(compare_chi_runtime(chi_candidate, runtime_development, runtime_chi))
        else:
            # The standalone Chi catalog remains comparable even after development entries are promoted out.
            chi_diagnostics.extend(compare_chi_runtime(chi_candidate, {"entries": []}, runtime_chi))
        promoted_chi_development = promote_chi_development(chi_candidate, version="3.69")
        promoted_chi_catalog = promote_chi_catalog(chi_candidate, version="3.69")
        _write(chi_candidate, output_dir / "content/chi_source.json")
        _write(promoted_chi_development, output_dir / "content/development_chi.json")
        _write(promoted_chi_catalog, output_dir / "content/chi.json")
        all_diagnostics.extend(chi_diagnostics)
        chi_dev_total = len(chi_candidate.get("developments", []))
        chi_catalog_total = len(chi_candidate.get("schools", [])) + len(chi_candidate.get("techniques", []))
        domains["development_chi"] = domain_meta("development_chi", {
            "status": "source_generated",
            "sourcePolicy": ["melee"],
            "sourceOutput": "content/chi_source.json",
            "output": "content/development_chi.json",
            "coverage": {"total": chi_dev_total, "linked": chi_dev_total, "ambiguous": 0, "missing": 0},
        })
        domains["chi"] = domain_meta("chi", {
            "status": "source_generated",
            "sourcePolicy": ["melee"],
            "sourceOutput": "content/chi_source.json",
            "output": "content/chi.json",
            "coverage": {"total": chi_catalog_total, "linked": chi_catalog_total, "ambiguous": 0, "missing": 0},
        })

    # Martial/Chi generation no longer needs the supplement Raw IR. Avoid carrying
    # it into the large core spell pass.
    if "melee_raw" in locals():
        del melee_raw
    gc.collect()

    if domain_config.get("magic_equipment", {}).get("status") == "source_generated":
        spell_bindings_path = repo_root / "rulesets/dubl-3.69/bindings/spells.json"
        gear_bindings_path = repo_root / "rulesets/dubl-3.69/bindings/gear.json"
        magic_source_diagnostics: list[dict] = []
        core_spell_payload = {"schemaVersion": 1, "source": "core", "spells": []}
        archmage_spell_payload = {"schemaVersion": 1, "source": "archmage", "spells": []}
        gear_payload = {"schemaVersion": 1, "source": "core", "gear": []}

        if not spell_bindings_path.exists() or not gear_bindings_path.exists():
            missing = []
            if not spell_bindings_path.exists(): missing.append(str(spell_bindings_path))
            if not gear_bindings_path.exists(): missing.append(str(gear_bindings_path))
            magic_source_diagnostics.append(make_diagnostic(
                kind="magic-equipment-bindings-missing",
                subject="magic_equipment",
                source_refs=[],
                severity="error",
                message="Tracked magic/equipment bindings are missing: " + ", ".join(missing),
            ))
        else:
            spell_bindings = json.loads(spell_bindings_path.read_text(encoding="utf-8"))
            gear_bindings = json.loads(gear_bindings_path.read_text(encoding="utf-8"))
            core_spell_payload, core_spell_diags = import_core_spells(
                raw_by_source["core"], {"bindings": spell_bindings.get("core", [])}, "core"
            )
            magic_source_diagnostics.extend(core_spell_diags)
            if "archmage" in sources:
                archmage_raw = json.loads((output_dir / "source/archmage_raw_ir.json").read_text(encoding="utf-8"))
                archmage_spell_payload, archmage_spell_diags = import_archmage_spells(
                    archmage_raw, {"bindings": spell_bindings.get("archmage", [])}, "archmage"
                )
                magic_source_diagnostics.extend(archmage_spell_diags)
            elif spell_bindings.get("archmage"):
                magic_source_diagnostics.append(make_diagnostic(
                    kind="archmage-source-missing", subject="magic_equipment", source_refs=[], severity="error",
                    message="Archmage bindings exist but the Archmage source was not provided.", sourcePolicy=["archmage"],
                ))
            gear_payload, gear_diags = import_gear(raw_by_source["core"], gear_bindings, "core")
            magic_source_diagnostics.extend(gear_diags)

            runtime_magic = json.loads((shared / "magic_equipment_catalog.json").read_text(encoding="utf-8"))
            magic_source_diagnostics.extend(compare_spells_runtime(core_spell_payload, runtime_magic))
            magic_source_diagnostics.extend(compare_spells_runtime(archmage_spell_payload, runtime_magic))
            magic_source_diagnostics.extend(compare_gear_runtime(gear_payload, runtime_magic))

        promoted_core = promote_spell_catalog(core_spell_payload, version="3.69")
        promoted_archmage = promote_spell_catalog(archmage_spell_payload, version="3.69")
        promoted_gear = promote_gear_catalog(gear_payload, version="3.69")
        promoted_magic = {
            "version": "3.69",
            "spells": promoted_core.get("spells", []) + promoted_archmage.get("spells", []),
            "gear": promoted_gear.get("gear", []),
        }
        if (repo_root / "rulesets/dubl-3.69/acquisition/native_magic_supplement_rules.json").exists():
            from .native_magic_supplement import correct_native_magic_equipment_payload
            promoted_magic = correct_native_magic_equipment_payload(promoted_magic, repo_root)
        _write(core_spell_payload, output_dir / "content/magic_core_source.json")
        _write(archmage_spell_payload, output_dir / "content/magic_archmage_source.json")
        _write(gear_payload, output_dir / "content/equipment_source.json")
        _write(promoted_magic, output_dir / "content/magic_equipment.json")
        all_diagnostics.extend(magic_source_diagnostics)
        total = len(json.loads(spell_bindings_path.read_text(encoding="utf-8")).get("core", [])) + len(json.loads(spell_bindings_path.read_text(encoding="utf-8")).get("archmage", [])) + len(json.loads(gear_bindings_path.read_text(encoding="utf-8")).get("bindings", [])) if spell_bindings_path.exists() and gear_bindings_path.exists() else 1
        linked = len(core_spell_payload.get("spells", [])) + len(archmage_spell_payload.get("spells", [])) + len(gear_payload.get("gear", []))
        if (repo_root / "rulesets/dubl-3.69/acquisition/native_magic_supplement_rules.json").exists():
            from .native_magic_supplement import load_native_magic_supplement
            supplement_spell_count = sum(r['kind'] == 'SPELL' for r in load_native_magic_supplement(repo_root)['records'])
            total += supplement_spell_count
            linked += supplement_spell_count
        source_errors = sum(1 for d in magic_source_diagnostics if d.get("severity") == "error")
        domains["magic_equipment"] = domain_meta("magic_equipment", {
            "status": "source_generated",
            "sourcePolicy": ["core", "archmage"],
            "bindings": ["rulesets/dubl-3.69/bindings/spells.json", "rulesets/dubl-3.69/bindings/gear.json"],
            "sourceOutputs": ["content/magic_core_source.json", "content/magic_archmage_source.json", "content/equipment_source.json"],
            "output": "content/magic_equipment.json",
            "coverage": {"total": total, "linked": linked, "ambiguous": 0, "missing": max(0, total - linked)},
        })

    try:
        conditions = import_conditions(raw_by_source["core"], "core")
        condition_total = len(conditions.get("conditions", [])) + len(conditions.get("mechanics", []))
        condition_coverage = {"total": condition_total, "linked": condition_total, "ambiguous": 0, "missing": 0}
    except ValueError as exc:
        conditions = {"schemaVersion": 1, "source": "core", "conditions": [], "mechanics": []}
        condition_coverage = {"total": 1, "linked": 0, "ambiguous": 0, "missing": 1}
        all_diagnostics.append(make_diagnostic(
            kind="source-section-missing",
            subject="conditions",
            source_refs=[],
            severity="error",
            message=str(exc),
            sourcePolicy=["core"],
        ))
    _write(conditions, output_dir / "content/conditions.json")
    domains["conditions"] = domain_meta("conditions", {
        "status": "source_generated",
        "sourcePolicy": ["core"],
        "output": "content/conditions.json",
        "coverage": condition_coverage,
    })

    skills_config = domain_config.get("skills")
    skill_bindings_path = repo_root / "rulesets/dubl-3.69/bindings/skills.json"
    if skills_config is not None or skill_bindings_path.exists():
        if not skill_bindings_path.exists():
            skills_payload = {"schemaVersion": 1, "source": "core", "rankCosts": [], "rankCostSourceRefs": [], "skills": []}
            skill_diagnostics = [make_diagnostic(
                kind="skill-bindings-missing",
                subject="skills",
                source_refs=[],
                severity="error",
                message=f"Tracked skill bindings are missing: {skill_bindings_path}",
            )]
            skill_coverage = {"total": 1, "linked": 0, "ambiguous": 0, "missing": 1}
        else:
            bindings = json.loads(skill_bindings_path.read_text(encoding="utf-8"))
            try:
                skills_payload, skill_diagnostics = import_skills(raw_by_source["core"], bindings, "core")
                binding_count = len(bindings.get("bindings", []))
                linked_count = len(skills_payload.get("skills", []))
                skill_coverage = {
                    "total": binding_count,
                    "linked": linked_count,
                    "ambiguous": 0,
                    "missing": max(0, binding_count - linked_count),
                }
            except ValueError as exc:
                skills_payload = {"schemaVersion": 1, "source": "core", "rankCosts": [], "rankCostSourceRefs": [], "skills": []}
                skill_diagnostics = [make_diagnostic(
                    kind="source-section-missing",
                    subject="skills",
                    source_refs=[],
                    severity="error",
                    message=str(exc),
                    sourcePolicy=["core"],
                )]
                binding_count = len(bindings.get("bindings", []))
                skill_coverage = {"total": binding_count, "linked": 0, "ambiguous": 0, "missing": binding_count}
        if (repo_root / "rulesets/dubl-3.69/acquisition/native_magic_supplement_rules.json").exists():
            from .native_magic_supplement import correct_native_magic_skills_payload
            skills_payload = correct_native_magic_skills_payload(skills_payload, repo_root)
            skill_coverage["total"] += 1
            skill_coverage["linked"] += 1
        _write(skills_payload, output_dir / "content/skills.json")
        all_diagnostics.extend(skill_diagnostics)
        domains["skills"] = domain_meta("skills", {
            "status": "source_generated_candidate",
            "sourcePolicy": ["core", "archmage"] if (repo_root / "rulesets/dubl-3.69/acquisition/native_magic_supplement_rules.json").exists() else ["core"],
            "bindings": "rulesets/dubl-3.69/bindings/skills.json",
            "output": "content/skills.json",
            "coverage": skill_coverage,
        })

    if acquisition_enabled and (repo_root / "rulesets/dubl-3.69/acquisition/native_occurrence_bindings.json").exists():
        native_supplement = build_native_development_supplement(repo_root)
        _write(native_supplement, output_dir / "content/development_native_supplement.json")
        domains["development_native_supplement"] = domain_meta("development_native_supplement", {
            "status": "source_generated",
            "sourcePolicy": ["core"],
            "bindings": [str(p.relative_to(repo_root)) for p in [
                repo_root / "rulesets/dubl-3.69/acquisition/native_occurrence_bindings.json",
                repo_root / "rulesets/dubl-3.69/acquisition/native_drawback_rules.json",
                repo_root / "rulesets/dubl-3.69/acquisition/native_weapon_proficiencies.json"] if p.exists()],
            "output": "content/development_native_supplement.json",
            "coverage": {"total": len(native_supplement["entries"]),
                         "linked": len(native_supplement["entries"]), "ambiguous": 0, "missing": 0},
        })

    if acquisition_enabled and (repo_root / "rulesets/dubl-3.69/acquisition/native_magic_supplement_rules.json").exists():
        from .native_magic_supplement import native_magic_development_payload
        native_magic = native_magic_development_payload(repo_root)
        _write(native_magic, output_dir / "content/development_native_magic.json")
        domains["development_native_magic"] = domain_meta("development_native_magic", {
            "status": "source_generated", "sourcePolicy": ["archmage"],
            "bindings": "rulesets/dubl-3.69/acquisition/native_magic_supplement_rules.json",
            "output": "content/development_native_magic.json",
            "coverage": {"total": len(native_magic["entries"]), "linked": len(native_magic["entries"]),
                         "ambiguous": 0, "missing": 0},
        })

    if domain_config.get("skill_effects", {}).get("status") == "source_generated":
        skill_effect_bindings_path = repo_root / "rulesets/dubl-3.69/bindings/skill_effects.json"
        if not skill_effect_bindings_path.exists():
            skill_effect_payload = {"version": "3.69", "effects": []}
            skill_effect_diagnostics = [make_diagnostic(
                kind="skill-effect-bindings-missing",
                subject="skill_effects",
                source_refs=[],
                severity="error",
                message=f"Tracked skill-effect bindings are missing: {skill_effect_bindings_path}",
            )]
            skill_effect_total = 1
        else:
            development_entries: list[dict] = []
            for filename in (
                "development_regular.json",
                "development_special.json",
                "development_ability_roots.json",
                "development_martial.json",
                "development_chi.json",
                "development_magic.json",
                "development_native_supplement.json",
                "development_native_magic.json",
            ):
                path = output_dir / "content" / filename
                if path.exists():
                    development_entries.extend(json.loads(path.read_text(encoding="utf-8")).get("entries", []))
            skill_effect_bindings = json.loads(skill_effect_bindings_path.read_text(encoding="utf-8"))
            skill_effect_payload, skill_effect_diagnostics = import_skill_effects(
                {"entries": development_entries}, skill_effect_bindings
            )
            skill_effect_total = len(skill_effect_bindings.get("bindings", []))
        _write(skill_effect_payload, output_dir / "content/skill_effects.json")
        all_diagnostics.extend(skill_effect_diagnostics)
        skill_effect_linked = len(skill_effect_payload.get("effects", []))
        domains["skill_effects"] = domain_meta("skill_effects", {
            "status": "source_generated",
            "sourcePolicy": ["core", "melee"],
            "bindings": "rulesets/dubl-3.69/bindings/skill_effects.json",
            "output": "content/skill_effects.json",
            "coverage": {
                "total": skill_effect_total,
                "linked": skill_effect_linked,
                "ambiguous": 0,
                "missing": max(0, skill_effect_total - skill_effect_linked),
            },
        })

    # Like other source-bound domains, acquisition is enabled by its committed input.
    if acquisition_enabled:
        for pack_id, payload in build_acquisition_catalogs(repo_root).items():
            _write(payload, output_dir / "content" / pack_id / "acquisition_catalog.json")
        domains["acquisition"] = {"status": "source_generated_with_blockers",
            "output": "content/dubl-3.69/acquisition_catalog.json",
            "optionalOutput": "content/dubl-chi-3.69/acquisition_catalog.json"}

    diagnostics_root = {
        "schemaVersion": 1,
        "ruleset": "dubl-3.69",
        "diagnostics": sorted(all_diagnostics, key=lambda d: d["id"]),
    }
    _write(diagnostics_root, output_dir / "diagnostics.json")

    resolutions_path = output_dir / "resolutions.json"
    tracked_resolutions_path = repo_root / "rulesets/dubl-3.69/resolutions.json"
    if tracked_resolutions_path.exists():
        tracked_resolutions = json.loads(tracked_resolutions_path.read_text(encoding="utf-8"))
    else:
        tracked_resolutions = {"schemaVersion": 1, "ruleset": "dubl-3.69", "resolutions": []}
    _write(tracked_resolutions, resolutions_path)

    manifest = {
        "schemaVersion": 1,
        "rulesetId": "dubl",
        "rulesetVersion": "3.69",
        "sources": source_meta,
        "sourceStats": aggregate_stats,
        "domains": domains,
        "diagnostics": {
            "total": len(all_diagnostics),
            "errors": sum(1 for d in all_diagnostics if d["severity"] == "error"),
            "warnings": sum(1 for d in all_diagnostics if d["severity"] == "warning"),
        },
    }
    if list(sources) == ["core"]:
        manifest["source"] = source_meta["core"]
    _write(manifest, output_dir / "manifest.json")
    return manifest


def _parse_source_arg(values: list[str]) -> dict[str, Path]:
    sources: dict[str, Path] = {}
    for value in values:
        if "=" not in value:
            if "core" in sources:
                raise ValueError("unqualified --source may only be used once")
            sources["core"] = Path(value)
            continue
        key, path = value.split("=", 1)
        if not key or not path:
            raise ValueError(f"invalid source mapping: {value}")
        sources[key] = Path(path)
    if "core" not in sources:
        raise ValueError("a core rulebook source is required")
    return sources


def main() -> None:
    parser = argparse.ArgumentParser(description="Build DUBL 3.69 ruleset bootstrap bundle from rulebook sources")
    parser.add_argument("--source", action="append", required=True, help="Path for core or key=path (core/melee/archmage)")
    parser.add_argument("--repo-root", type=Path, default=Path("."))
    parser.add_argument("--output-dir", type=Path, default=Path("build/rulesets/dubl-3.69"))
    args = parser.parse_args()
    sources = _parse_source_arg(args.source)
    manifest = build_ruleset(sources, args.repo_root, args.output_dir)
    print(json.dumps({"sourceStats": manifest["sourceStats"], "domains": manifest["domains"], "diagnostics": manifest["diagnostics"]}, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
