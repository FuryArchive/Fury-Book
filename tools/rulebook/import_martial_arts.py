from __future__ import annotations

import hashlib
import re
from dataclasses import dataclass, field
from typing import Sequence

from .diagnostics import make_diagnostic

MARTIAL_SECTIONS = {
    "рукопашные искусства": "Рукопашные",
    "оружейные искусства": "Оружейные",
    "гибридные искусства": "Гибридные",
    "другое": "Рукопашные",
    "кунг-фу": "Кунг-фу",
    "оружейное кунг-фу": "Оружейные",
}
STYLE_ALIASES = {"карате": "Каратэ", "ган ката": "Ган-Ката"}


def clean(value: object) -> str:
    return re.sub(r"[ \t]+", " ", str(value or "").replace("\u00a0", " ")).strip()


def normalize(value: object) -> str:
    text = clean(value).casefold().replace("ё", "е")
    text = text.replace("–", "-").replace("—", "-")
    return text.strip(" .,:;!?")


def stable_id(kind: str, name: str) -> str:
    digest = hashlib.sha1(f"{kind}:{normalize(name)}".encode("utf-8")).hexdigest()[:16]
    return f"martial_{kind}_{digest}"


def canonical_style_name(name: str) -> str:
    return STYLE_ALIASES.get(normalize(name), clean(name))


def _is_paragraph(block: dict) -> bool:
    return block.get("kind") == "paragraph"


def _blank(block: dict) -> bool:
    return _is_paragraph(block) and not clean(block.get("text"))


def _style(block: dict) -> str:
    return str(block.get("style") or "")


def _first_int(pattern: str, text: str) -> int | None:
    m = re.search(pattern, text, flags=re.I | re.M)
    return int(m.group(1)) if m else None


def _field(text: str, label: str, stops: Sequence[str]) -> str:
    stop = "|".join(re.escape(x) for x in stops)
    suffix = rf"(?=\n\s*(?:{stop})\s*:|\Z)" if stop else r"\Z"
    m = re.search(rf"(?:^|\n)\s*{re.escape(label)}\s*:\s*(.*?){suffix}", text, flags=re.I | re.S)
    if not m:
        return ""
    return "\n".join(clean(line) for line in m.group(1).splitlines() if clean(line)).strip()


def _cost_rank(text: str) -> tuple[int | None, int]:
    cost = _first_int(r"Стоимость\s*:\s*(\d+)", text)
    if cost is None:
        cost = _first_int(r"^\s*(\d+)\s*,", text)
    rank = _first_int(r"Ранг(?:и|ов)?\s*:\s*(\d+)", text)
    if rank is None:
        rank = _first_int(r",\s*(\d+)\s*ранг", text)
    return cost, rank or 1


def _requirements(text: str) -> str:
    return _field(text, "Требование", ["Выгода", "Примечание", "Особое", "Ограничение", "Описание", "Стоимость", "Ранг", "Ранги"]).strip(" .")


def _benefit(text: str) -> str:
    return _field(text, "Выгода", ["Примечание", "Особое", "Ограничение", "Стоимость", "Требование", "Ранг", "Ранги"]).strip()


def _prefixed(text: str, label: str) -> str:
    return _field(text, label, ["Описание", "История", "Обязательное условие", "Стоимость", "Ранг", "Ранги", "Требование", "Выгода", "Примечание", "Особое", "Ограничение"]).strip()


def _entry(*, entry_id: str, name: str, category: str, cost: int, ranks: int, requirements: str,
           benefit: str, notes: str, tags: list[str], source_refs: list[str], source_styles: list[str] | None = None) -> dict:
    result = {
        "id": entry_id,
        "name": clean(name),
        "section": "Боевые искусства",
        "category": clean(category),
        "cost": max(0, int(cost)),
        "costType": "xp",
        "ranks": max(1, int(ranks)),
        "requirements": clean(requirements) or "-",
        "benefit": "\n".join(clean(x) for x in str(benefit).splitlines() if clean(x)),
        "notes": "\n".join(clean(x) for x in str(notes).splitlines() if clean(x)),
        "tags": list(dict.fromkeys(clean(x) for x in tags if clean(x))),
        "sourceRefs": list(dict.fromkeys(source_refs)),
    }
    if source_styles is not None:
        result["sourceStyles"] = list(source_styles)
    return result


def _style_root(name: str, martial_type: str, blocks: list[dict], source_key: str) -> dict | None:
    root: list[dict] = []
    for block in blocks:
        if block.get("kind") == "table":
            break
        n = normalize(block.get("text"))
        if n in {"приемы", "стойки", "совершенная способность"}:
            break
        if _is_paragraph(block):
            root.append(block)
    lines = [str(b.get("text", "")).strip() for b in root if clean(b.get("text"))]
    joined = "\n".join(lines)
    cost, ranks = _cost_rank(joined)
    benefit = _benefit(joined)
    if cost is None or not benefit:
        return None
    req = _requirements(joined)
    mandatory = next((clean(x.split(":",1)[1]) for x in lines if normalize(x).startswith("обязательное условие:") and ":" in x), "")
    if mandatory:
        req = "; ".join(x for x in (req, mandatory) if x)
    metadata_start = next((i for i,x in enumerate(lines) if re.search(r"Стоимость\s*:", x, re.I)), len(lines))
    flavor=[]
    for line in lines[:metadata_start]:
        if normalize(line) in {normalize(name), f"описание: {normalize(name)}"}:
            continue
        if normalize(line).startswith("обязательное условие:"):
            continue
        # A dedicated Description field belongs to source notes/flavor, not mechanics.
        if normalize(line).startswith("описание:"):
            flavor.append(clean(line))
        elif clean(line):
            flavor.append(clean(line))
    special=next((clean(x.split(":",1)[1]) for x in lines if normalize(x).startswith("особое:") and ":" in x), "")
    notes="\n".join(dict.fromkeys([*flavor, *( [f"Особое: {special}"] if special else [])]))
    refs=[f"{source_key}:{b['id']}" for b in root if b.get('id')]
    return _entry(entry_id=stable_id("style",name),name=name,category=f"{martial_type} стили",cost=cost,ranks=ranks,
                  requirements=req,benefit=benefit,notes=notes,tags=["Боевые искусства","Боевой стиль",martial_type],source_refs=refs)


@dataclass
class Occurrence:
    name: str
    cost: int
    ranks: int
    requirements: str
    benefit: str
    notes: str
    source_style: str
    martial_type: str
    stance: bool
    source_refs: list[str]

    @property
    def score(self) -> int:
        return len(self.requirements) + 2 * len(self.benefit) + len(self.notes)


@dataclass
class Aggregate:
    best: Occurrence
    occurrences: list[Occurrence] = field(default_factory=list)

    def add(self, item: Occurrence) -> None:
        self.occurrences.append(item)
        if item.score > self.best.score:
            self.best = item

    def entry(self) -> dict:
        sources=sorted({x.source_style for x in self.occurrences}, key=normalize)
        types=sorted({x.martial_type for x in self.occurrences}, key=normalize)
        req=self.best.requirements
        if len(sources)>1 and (not req or normalize(req)==normalize(f"Боевые искусства ({self.best.source_style})")):
            req="Боевые искусства: " + ", ".join(sources[:-1]) + (f" или {sources[-1]}" if len(sources)>1 else sources[0])
        category="Общие приёмы" if len(sources)>1 else f"Приёмы · {sources[0]}"
        tags=["Боевые искусства","Приём",*types,*sources]
        if any(x.stance for x in self.occurrences): tags.append("Стойка")
        refs=[]
        for x in self.occurrences:
            refs.extend(x.source_refs)
        return _entry(entry_id=stable_id("technique",self.best.name),name=self.best.name,category=category,cost=self.best.cost,
                      ranks=self.best.ranks,requirements=req,benefit=self.best.benefit,notes=self.best.notes,tags=tags,
                      source_refs=refs,source_styles=sources)


def _paragraph_techniques(style_name: str, martial_type: str, blocks: list[dict], source_key: str) -> list[Occurrence]:
    out=[]; active=False
    for idx, block in enumerate(blocks):
        if not _is_paragraph(block):
            continue
        text=str(block.get("text", "")); n=normalize(text)
        if n in {"приемы", "стойки", "навыки"}:
            active=True; continue
        if _style(block)=="Heading 3":
            if n in {"приемы", "стойки", "навыки"}: active=True
            elif n=="совершенная способность": active=False
            continue
        if not active or not re.search(r"Стоимость\s*:\s*\d+", text, re.I):
            continue
        start=idx-1
        while start>=0 and not _blank(blocks[start]) and blocks[start].get("kind")!="table":
            if _is_paragraph(blocks[start]) and _style(blocks[start]) in {"Title","Heading 2","Heading 3"}:
                break
            start-=1
        start+=1
        end=idx+1
        while end<len(blocks) and not _blank(blocks[end]) and blocks[end].get("kind")!="table":
            if _is_paragraph(blocks[end]) and _style(blocks[end]) in {"Title","Heading 2","Heading 3"}:
                break
            end+=1
        chunk=[b for b in blocks[start:end] if _is_paragraph(b) and clean(b.get("text"))]
        if not chunk: continue
        name=clean(chunk[0].get("text"))
        if normalize(name) in {"приемы","стойки"} or ":" in name: continue
        joined="\n".join(str(b.get("text","")).strip() for b in chunk)
        cost,ranks=_cost_rank(joined)
        benefit=_benefit(joined)
        if cost is None or not benefit: continue
        req=_requirements(joined) or f"Боевые искусства ({style_name})"
        cost_pos=next((i for i,b in enumerate(chunk) if re.search(r"Стоимость\s*:",str(b.get('text','')),re.I)),1)
        pre=[clean(b.get('text')) for b in chunk[1:cost_pos] if clean(b.get('text'))]
        pre=[part for part in pre if not (part.startswith("(") and part.endswith(")"))]
        note=_prefixed(joined,"Примечание")
        notes="\n".join([*pre, *([f"Примечание: {note}"] if note else [])])
        refs=[f"{source_key}:{b['id']}" for b in chunk if b.get('id')]
        out.append(Occurrence(name,cost,ranks,req,benefit,notes,style_name,martial_type,"[стойка]" in normalize(joined),refs))
    return out


def _table_techniques(style_name: str, martial_type: str, blocks: list[dict], source_key: str) -> list[Occurrence]:
    out=[]; subsection=""
    for block in blocks:
        if _is_paragraph(block):
            n=normalize(block.get("text"))
            if _style(block)=="Heading 3" or n in {"стойки","приемы"}: subsection=n
            if n=="совершенная способность": subsection=""
            continue
        if block.get("kind")!="table" or subsection not in {"стойки","приемы"}: continue
        rows=block.get("rows",[])
        for row in rows[1:]:
            cells=[clean(x) for x in row]
            if len(cells)<4: continue
            name,cost_text,effect=cells[1],cells[2],cells[3]
            if not name or not cost_text or not effect: continue
            cost,ranks=_cost_rank(cost_text)
            if cost is None: continue
            req=_requirements(effect) or f"Боевые искусства ({style_name})"
            benefit=_benefit(effect) or effect
            out.append(Occurrence(name,cost,ranks,req,benefit,"",style_name,martial_type,
                                  subsection=="стойки" or "[стойка]" in normalize(effect),[f"{source_key}:{block['id']}"]))
    return out


def import_martial_arts(raw_ir: dict, source_key: str = "melee") -> tuple[dict, list[dict]]:
    blocks=raw_ir.get("blocks",[])
    styles=[]; active_name=None; active_type=None; active_blocks=[]; current_type=None
    def flush():
        nonlocal active_name,active_type,active_blocks
        if active_name and active_type: styles.append((active_name,active_type,active_blocks))
        active_name=None; active_type=None; active_blocks=[]
    for block in blocks:
        if not _is_paragraph(block):
            if active_name: active_blocks.append(block)
            continue
        text=clean(block.get("text")); n=normalize(text)
        if _style(block)=="Title" and n in MARTIAL_SECTIONS:
            flush(); current_type=MARTIAL_SECTIONS[n]; continue
        if _style(block)=="Title" and n=="ци":
            flush(); current_type=None; continue
        if current_type and _style(block)=="Title" and text:
            flush(); active_name=canonical_style_name(text); active_type=current_type; continue
        if active_name: active_blocks.append(block)
    flush()

    style_entries=[]; aggregates={}; diagnostics=[]
    for name,mtype,sblocks in styles:
        style=_style_root(name,mtype,sblocks,source_key)
        if style is None:
            diagnostics.append(make_diagnostic("martial-style-fields-missing",f"martial-style:{name}",[],"warning",
                                              f"Could not parse complete cost/benefit for martial style {name!r}.",sourcePolicy=[source_key]))
        else:
            style_entries.append(style)
        for occ in [*_paragraph_techniques(name,mtype,sblocks,source_key), *_table_techniques(name,mtype,sblocks,source_key)]:
            key=normalize(occ.name)
            if key in aggregates: aggregates[key].add(occ)
            else: aggregates[key]=Aggregate(best=occ,occurrences=[occ])
    techniques=[aggregates[k].entry() for k in sorted(aggregates)]
    style_entries.sort(key=lambda x:(normalize(x['category']),normalize(x['name'])))
    techniques.sort(key=lambda x:(normalize(x['category']),normalize(x['name'])))
    return {"schemaVersion":1,"source":source_key,"styles":style_entries,"techniques":techniques,"entries":style_entries+techniques}, diagnostics


def _norm_text(value: object) -> str:
    return re.sub(r"\s+", " ", str(value or "")).strip()


def compare_martial_runtime(imported: dict, runtime: dict) -> list[dict]:
    runtime_by_id={str(x.get('id','')):x for x in runtime.get('entries',[]) if x.get('section')=='Боевые искусства'}
    diagnostics=[]
    for src in imported.get('entries',[]):
        cur=runtime_by_id.get(str(src.get('id','')))
        if not cur: continue
        diffs={}
        for field in ('cost','ranks'):
            if int(src.get(field) or 0)!=int(cur.get(field) or 0): diffs[field]={'rulebook':src.get(field),'runtime':cur.get(field)}
        for field in ('requirements','benefit','notes'):
            if _norm_text(src.get(field))!=_norm_text(cur.get(field)): diffs[field]={'rulebook':src.get(field) or '', 'runtime':cur.get(field) or ''}
        if diffs:
            diagnostics.append(make_diagnostic('martial-runtime-drift',f"martial:{src.get('id')}",list(src.get('sourceRefs',[])),'warning',
                                              f"Runtime martial entry {src.get('name')!r} differs from rulebook import.",details={'differences':diffs}))
    return diagnostics


def promote_martial_arts(imported: dict, version: str = "3.69") -> dict:
    return {"version": version, "entries": [dict(entry) for entry in imported.get("entries", [])]}
