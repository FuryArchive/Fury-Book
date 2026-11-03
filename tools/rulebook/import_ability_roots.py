from __future__ import annotations

import re

from .diagnostics import make_diagnostic
from .source_index import normalize_lookup

_INT_RE = re.compile(r"-?\d+")
_LABEL_RE = re.compile(r"^(требовани[ея]|описание|особое|примечание)\s*:?\s*(.*)$", re.IGNORECASE | re.DOTALL)


def _ability_table_after(raw_ir: dict, start_index: int) -> int | None:
    blocks = raw_ir.get("blocks", [])
    heading_path = blocks[start_index].get("headingPath", [])
    for index in range(start_index + 1, min(start_index + 6, len(blocks))):
        block = blocks[index]
        if block.get("kind") == "table":
            rows = block.get("rows", [])
            if not rows:
                continue
            header = " ".join(str(cell) for cell in rows[0]).casefold().replace("ё", "е")
            if "тип" in header and ("стоимость" in header or "ценность" in header or "источник" in header):
                return index
            continue
        if block.get("kind") == "paragraph":
            text = str(block.get("text", "")).strip()
            if not text:
                # Some DOCX sections contain an empty Heading paragraph that resets
                # headingPath before the actual table. It is structural noise.
                continue
            if block.get("headingPath", []) != heading_path:
                break
            # Non-empty prose before a table is tolerated only inside the same section.
            continue
    return None




def _next_nonblank_paragraph(blocks: list[dict], start: int, limit: int) -> tuple[int, str] | None:
    for index in range(start, min(limit, len(blocks))):
        block = blocks[index]
        if block.get("kind") != "paragraph":
            if block.get("kind") == "table":
                continue
            continue
        text = str(block.get("text", "")).strip()
        if text:
            return index, text
    return None


def _looks_like_child_card_start(blocks: list[dict], index: int, limit: int) -> bool:
    block = blocks[index]
    if block.get("kind") != "paragraph":
        return False
    text = str(block.get("text", "")).strip()
    if not text or re.match(r"^(стоимость|ранг(?:и)?|требовани[ея]|выгода|описание|особое|примечание)\s*:", text, re.IGNORECASE):
        return False
    nxt = _next_nonblank_paragraph(blocks, index + 1, limit)
    if not nxt:
        return False
    next_text = nxt[1].strip()
    return bool(next_text.startswith("[") or re.match(r"^стоимость\s*:", next_text, re.IGNORECASE))


def _root_prose(raw_ir: dict, heading_index: int, table_index: int) -> tuple[list[str], list[str], list[str]]:
    blocks = raw_ir.get("blocks", [])
    heading = blocks[heading_index]
    heading_path = heading.get("headingPath", [])
    limit = min(len(blocks), heading_index + 40)
    pre: list[str] = []
    post: list[str] = []
    refs: list[str] = []
    for index in range(heading_index + 1, limit):
        block = blocks[index]
        if index == table_index or block.get("kind") == "table":
            continue
        if block.get("kind") != "paragraph":
            continue
        text = str(block.get("text", "")).strip()
        if not text:
            continue
        if block.get("headingLevel") and block.get("headingPath", []) != heading_path:
            break
        if _looks_like_child_card_start(blocks, index, limit):
            break
        # A changed non-empty heading path after the ability table is a new section.
        if index > table_index and block.get("headingPath", []) != heading_path and block.get("headingLevel"):
            break
        target = pre if index < table_index else post
        target.append(text)
        refs.append(str(block.get("id", "")))
    return pre, post, refs


def _unique_cells(row: list[object]) -> list[str]:
    result: list[str] = []
    for cell in row:
        text = str(cell or "").strip()
        if text and text not in result:
            result.append(text)
    return result


def _parse_labeled_row(row: list[object]) -> tuple[str, str] | None:
    cells = _unique_cells(row)
    if not cells:
        return None
    first = cells[0].strip()
    match = _LABEL_RE.match(first)
    if not match:
        return None
    label = normalize_lookup(match.group(1))
    inline = match.group(2).strip()
    if inline:
        return label, inline
    if len(cells) > 1:
        second = cells[1].strip()
        second_match = _LABEL_RE.match(second)
        if second_match:
            return label, second_match.group(2).strip()
        return label, second
    return label, ""


def _parse_ability_table(table: dict) -> tuple[list[dict], str, str, list[str]]:
    options: list[dict] = []
    requirements = ""
    text_parts: list[str] = []
    empty_fields: list[str] = []
    rows = table.get("rows", [])

    for row in rows[1:]:
        labeled = _parse_labeled_row(row)
        if labeled:
            label, value = labeled
            if label.startswith("требован"):
                requirements = value
            elif label in {"описание", "особое", "примечание"}:
                if value:
                    visible = "Описание" if label == "описание" else ("Особое" if label == "особое" else "Примечание")
                    text_parts.append(f"{visible}: {value}")
                else:
                    empty_fields.append(label)
            continue

        cells = _unique_cells(row)
        if len(cells) < 2:
            continue
        match = _INT_RE.search(cells[1])
        if match:
            options.append({"source": cells[0], "value": int(match.group(0))})

    return options, requirements, "\n".join(text_parts), empty_fields


def import_ability_roots(raw_ir: dict, bindings: dict, source_key: str = "core") -> tuple[dict, list[dict]]:
    blocks = raw_ir.get("blocks", [])
    lookup: dict[str, list[int]] = {}
    for index, block in enumerate(blocks):
        if block.get("kind") != "paragraph":
            continue
        lookup.setdefault(normalize_lookup(str(block.get("text", ""))), []).append(index)

    entries: list[dict] = []
    diagnostics: list[dict] = []
    for binding in bindings.get("bindings", []):
        name = str(binding.get("name", "")).strip()
        entry_id = str(binding.get("id", "")).strip()
        subject = f"ability-root:{entry_id or name}"
        candidates: list[tuple[int, int]] = []
        for index in lookup.get(normalize_lookup(name), []):
            table_index = _ability_table_after(raw_ir, index)
            if table_index is not None:
                candidates.append((index, table_index))

        source_context = [normalize_lookup(str(value)) for value in binding.get("sourceContext", []) if str(value).strip()]
        if source_context and len(candidates) > 1:
            contextual = []
            for item in candidates:
                path = [normalize_lookup(str(value)) for value in blocks[item[0]].get("headingPath", [])]
                if all(context in path for context in source_context):
                    contextual.append(item)
            if contextual:
                candidates = contextual

        if len(candidates) != 1:
            refs = [f"{source_key}:{blocks[index]['id']}" for index, _ in candidates]
            diagnostics.append(make_diagnostic(
                "ability-root-source-ambiguous" if candidates else "ability-root-source-missing",
                subject,
                refs,
                "warning",
                f"Expected one ability-source table for {name!r}, found {len(candidates)}.",
                sourcePolicy=[source_key],
            ))
            continue

        heading_index, table_index = candidates[0]
        table = blocks[table_index]
        options, requirements, table_benefit, empty_fields = _parse_ability_table(table)
        pre_prose, post_prose, prose_refs = _root_prose(raw_ir, heading_index, table_index)
        benefit_parts = [*pre_prose]
        if table_benefit:
            benefit_parts.append(table_benefit)
        benefit_parts.extend(post_prose)
        benefit = "\n".join(part for part in benefit_parts if part).strip()
        refs = [f"{source_key}:{blocks[heading_index]['id']}", f"{source_key}:{table['id']}"]
        refs.extend(f"{source_key}:{ref}" for ref in prose_refs if ref)
        entry = {
            "id": entry_id,
            "name": name,
            "abilityOptions": options,
            "requirements": requirements,
            "benefit": benefit,
            "sourceRefs": refs,
            "emptyFields": empty_fields,
        }
        entries.append(entry)

        if empty_fields:
            diagnostics.append(make_diagnostic(
                "ability-root-template-incomplete",
                subject,
                refs,
                "warning",
                f"Rulebook ability root {name!r} contains empty template fields: {', '.join(empty_fields)}.",
                sourcePolicy=[source_key],
                missingFields=empty_fields,
            ))
        if not options:
            diagnostics.append(make_diagnostic(
                "ability-root-options-missing",
                subject,
                refs,
                "warning",
                f"Rulebook ability root {name!r} has no parsed ability source/cost options.",
                sourcePolicy=[source_key],
            ))
        if any(normalize_lookup(option["source"]) == "???" for option in options):
            diagnostics.append(make_diagnostic(
                "ability-root-draft-source",
                subject,
                refs,
                "warning",
                f"Rulebook ability root {name!r} uses draft source '???'.",
                sourcePolicy=[source_key],
            ))

    return {"schemaVersion": 1, "source": source_key, "entries": entries}, diagnostics


def _runtime_text(value: object) -> str:
    return "\n".join(re.sub(r"[ \t]+", " ", line).strip() for line in str(value or "").splitlines()).strip()


def _semantic_benefit(value: object) -> str:
    text = _runtime_text(value)
    text = re.sub(r"(?im)^\s*(описание|особое|примечание)\s*:\s*", "", text)
    return re.sub(r"\s+", " ", text).strip()


def _normalized_options(value: object) -> list[dict]:
    result: list[dict] = []
    for item in value or []:
        if not isinstance(item, dict):
            continue
        result.append({
            "source": re.sub(r"\s+", " ", str(item.get("source", ""))).strip(),
            "value": int(item.get("value") or 0),
        })
    return result


def compare_ability_roots_runtime(imported: dict, runtime_root: dict) -> list[dict]:
    runtime_by_id = {str(entry.get("id", "")): entry for entry in runtime_root.get("entries", [])}
    diagnostics: list[dict] = []
    for source_entry in imported.get("entries", []):
        entry_id = str(source_entry.get("id", ""))
        runtime = runtime_by_id.get(entry_id)
        if runtime is None:
            continue
        differences: dict[str, dict] = {}
        source_options = _normalized_options(source_entry.get("abilityOptions"))
        runtime_options = _normalized_options(runtime.get("abilityOptions"))
        if source_options != runtime_options:
            differences["abilityOptions"] = {"rulebook": source_options, "runtime": runtime_options}
        source_requirements = re.sub(r"\s+", " ", str(source_entry.get("requirements") or "")).strip()
        runtime_requirements = re.sub(r"\s+", " ", str(runtime.get("requirements") or "")).strip()
        if source_requirements != runtime_requirements:
            differences["requirements"] = {"rulebook": source_entry.get("requirements") or "", "runtime": runtime.get("requirements") or ""}
        source_benefit = _semantic_benefit(source_entry.get("benefit"))
        runtime_benefit = _semantic_benefit(runtime.get("benefit"))
        generic_helper = _semantic_benefit("Открывает доступ к навыкам этой ветки. Навыки приобретаются отдельно за опыт.")
        if source_benefit != runtime_benefit and not (not source_benefit and runtime_benefit == generic_helper):
            differences["benefit"] = {"rulebook": source_entry.get("benefit") or "", "runtime": runtime.get("benefit") or ""}
        if differences:
            diagnostics.append(make_diagnostic(
                "ability-root-runtime-drift",
                f"ability-root:{entry_id}",
                list(source_entry.get("sourceRefs", [])),
                "warning",
                f"Runtime ability root {source_entry.get('name')!r} differs from parsed rulebook mechanics.",
                details={"differences": differences},
            ))
    return diagnostics


def promote_ability_roots(imported: dict, bindings: dict, version: str = "3.69") -> dict:
    binding_by_id = {str(item.get("id", "")): item for item in bindings.get("bindings", [])}
    runtime_entries: list[dict] = []
    for source in imported.get("entries", []):
        entry_id = str(source.get("id", ""))
        binding = binding_by_id.get(entry_id, {})
        options = _normalized_options(source.get("abilityOptions"))
        empty_fields = [str(value) for value in source.get("emptyFields", []) if str(value)]
        unresolved = binding.get("unresolved") or {}
        runtime = {
            "id": entry_id,
            "name": str(source.get("name", "")),
            "section": "Особые способности",
            "category": str(binding.get("category") or "Особые способности"),
            "costType": "ability",
            "ranks": 1,
            "requirements": _runtime_text(source.get("requirements")),
            "benefit": _runtime_text(source.get("benefit")),
            "abilityOptions": options,
            "sourceRefs": list(source.get("sourceRefs", [])),
        }
        if binding.get("repeatable"):
            runtime["repeatable"] = True
        if binding.get("perfectRoot"):
            runtime["perfectRoot"] = True
        tags = [str(value) for value in binding.get("tags", []) if str(value)]
        if tags:
            runtime["tags"] = tags

        draft_source = any(normalize_lookup(str(option.get("source", ""))) == "???" for option in options)
        # Empty "Описание:/Особое:" rows are the book's normal root template: the
        # mechanics live in the same-named child card. They stay import
        # diagnostics but must not block every child of the branch.
        incomplete = bool(not options or draft_source or unresolved)
        if incomplete:
            reasons: list[str] = []
            if empty_fields:
                reasons.append(f"В рулбуке пустые поля: {', '.join(empty_fields)}")
            if not options:
                reasons.append("В рулбуке не определён источник/стоимость способности")
            if draft_source:
                reasons.append("В рулбуке источник способности указан как ???")
            if unresolved:
                reasons.append(str(unresolved.get("mechanicsConflict") or "Правило требует явной трактовки"))
            runtime["incomplete"] = True
            runtime["mechanicsConflict"] = "; ".join(reasons) + "."
            runtime["conflictNote"] = str(
                unresolved.get("conflictNote")
                or "Каноническая запись оставлена неполной; используйте локальную правку персонажа для трактовки вашей группы."
            )
        runtime_entries.append(runtime)
    return {"version": version, "entries": runtime_entries}
