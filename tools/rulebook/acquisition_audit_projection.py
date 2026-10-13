"""Dev-only, lossless source-field projections from the two pinned original audits.

Archived runtime results, counters, triage, severity and generated interpretations
are excluded. Reproduction reads ZIP JSON only and never executes archive code.
"""
from pathlib import Path
import argparse
import hashlib
import json
import zipfile
from tools.rulebook.acquisition_coverage_records import read, semantic_sha as digest, require
PROJECTION='rulesets/dubl-3.69/acquisition/audit_source_projection.json'
EXCERPTS='rulesets/dubl-3.69/acquisition/source_review_excerpts.json'
ARCHIVES={'skills':'1690c42dc28ae722154cd6b6d8ba5f14200de1bf8d7f5228f8b6454a264e66c3',
          'acquisition':'be8e3f90db91b27f282b4ce3eb78c0b27818b4cc2ff60becccc6b52ce27f54f3'}
MEMBERS={'ordinary_inventory.json':'726ae6f14fdfc6edb7fba38884bce3d7240b3149cb58ca3b980cdb1bb63b4097',
'special_inventory.json':'debea02cf3dc5bc570c9c92f4fd8afb88b2edd0ed1ed049d313e4e040cac01dc',
'general_source_issues.json':'bba07af8966500a21c5590b59e01d594f62d232ab6caaf7a192d43c25c7024be',
'source_requirements.json':'7d0d5b58e46644e559f3ea71eabc4112b0415c534e741f44cb1f39d961db96f6',
'graph/nodes.json':'327661f01cf6dea046ebffb08d9a78778f4d18855dbb4bc7fed8fd0fed748d4a',
'source/furybook_skill_inventory.json':'32e1461d21f2d79f13111dd06e5cce71c389a201f3dbf3ba2a65b316b9bbafc5',
'source/furybook_current_corebook.json':'238a93086f22b04b1f8b26a018a0c8c5b70815c67d8976c468dabbb7a4e14fa9'}
PROJECTION_SHA='deb4dc91a7d362e94da8a58942928ad93ed4395c8591fc097ba427a97879109a'
EXCERPTS_SHA='df8b2295f5b3fb6d45c9efb21894fddcb29ba438b0ab8ca98a1037041ebde229'
REQUIREMENT_FIELDS=('source_id','source_kind','name','source_tab','source_cost_p','source_paragraph_start','source_url','source_cost_raw','source_ranks_raw','source_requirements_raw','runtime_id')

def sha(data):return hashlib.sha256(data).hexdigest()
def select(row,keys):return {k:row[k] for k in keys if k in row}

def extract(root,skills,acquisition):
    values={}
    for kind,path in [('skills',skills),('acquisition',acquisition)]:
        require(sha(Path(path).read_bytes())==ARCHIVES[kind],'Original audit archive hash mismatch: '+kind)
        with zipfile.ZipFile(path) as archive:
            for member in (list(MEMBERS)[:3] if kind=='skills' else list(MEMBERS)[3:]):
                raw=archive.read(member) # ZIP CRC is checked by the standard reader.
                require(sha(raw)==MEMBERS[member],'Original audit member hash mismatch: '+member)
                values[member]=json.loads(raw)
    source=read(root/'rulesets/dubl-3.69/acquisition/source_questions.json')
    excerpts=read(root/EXCERPTS)
    paragraphs=values['source/furybook_current_corebook.json']['paragraphs']
    questions=[select(r,('id','issue','citations')) for r in values['general_source_issues.json']]
    original_six={f'core:P{p}' for p in (11903,14075,14077,14078,14181,14182)}
    extras={k:v for k,v in excerpts['sourceEvidence'].items() if k in original_six}
    for q in questions:
        for citation in q['citations']:
            p=citation['paragraphIndex'];row=paragraphs[p];key=f'core:P{p}'
            require(all(citation[k]==row[k] for k in ('tabId','startIndex','endIndex')),'Cited source offsets differ')
            require(citation['exactQuote'] in row['text'],'Cited quote is not exact source text')
            if key not in source['sourceEvidence']:
                extras[key]={**row,'paragraph':p,'sha256':sha(row['text'].encode())}
    require(len(extras)==76,'Unexpected additional paragraph set')
    excerpts.update(sourceEvidence=dict(sorted(extras.items())),projectionSha256=digest(dict(sorted(extras.items()))),
        derivation='Six original approved source-rule paragraphs preserved byte-for-byte as records, plus 70 exact original-audit question paragraphs absent from immutable sourceEvidence. No source interpretation or runtime PASS evidence.')
    inventory=values['source/furybook_skill_inventory.json']
    p=dict(schemaVersion=1,devOnly=True,authority='SOURCE_FIELDS_ONLY_NOT_RUNTIME_ACCEPTANCE',
        archiveLibraryVersion=1,archiveSha256=ARCHIVES,memberSha256=MEMBERS,
        sourceProvenance=inventory['provenance'],
        ordinaryInventory=[select(r,('source_id','canonical_source_id','canonical_source_kind','name','source_tab','source_paragraph','source_cost','source_ranks','source_requirements','source_rule','source_full','source_url','source_cost_p','runtime_id')) for r in values['ordinary_inventory.json']],
        specialInventory=[select(r,('scope','name','group','source_cost_P','source_start','source_end','tab','source_url','rule','benefit','cost','ranks','requirements','runtime_id')) for r in values['special_inventory.json']['rows']],
        generalQuestions=questions,
        sourceRequirements=[select(r,REQUIREMENT_FIELDS) for r in values['source_requirements.json']],
        sourceInventory=[select(r,('id','kind','name','tab_id','start_index','end_index','paragraph_start','paragraph_end','source_url','cost_raw','ranks_raw','prerequisites_raw','source_paragraphs_unmodified','source_excerpt_unmodified')) for r in inventory['skills']],
        graphNodes=[select(r,('id','kind','name','pack','active_core_only','active_with_chi')) for r in values['graph/nodes.json']])
    (root/PROJECTION).write_text(json.dumps(p,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    (root/EXCERPTS).write_text(json.dumps(excerpts,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    return digest(p),digest(excerpts)

def validate_projection(root,projection=None,excerpts=None):
    root=Path(root);p=read(root/PROJECTION) if projection is None else projection
    e=read(root/EXCERPTS) if excerpts is None else excerpts
    require(digest(p)==PROJECTION_SHA,'Pinned source projection changed')
    require(digest(e)==EXCERPTS_SHA,'Pinned exact excerpts changed')
    source=read(root/'rulesets/dubl-3.69/acquisition/source_questions.json')
    current={r['sourceId']:r for r in source['sourceOccurrences']}
    rows={r['source_id']:r for r in p['sourceRequirements']}
    inventory={r['id']:r for r in p['sourceInventory']}
    require(len(rows)==536 and set(rows)==set(current)==set(inventory),'Source occurrence universe differs')
    name_discrepancies=[]
    for identity,row in rows.items():
        require(row['runtime_id']==(current[identity]['runtimeId'] or ''),'Original/current runtime binding differs')
        require(row['name']==current[identity]['name'],'Current/source occurrence names differ')
        if row['name']!=inventory[identity]['name']:
            # Preserve the original audit's misplaced description-as-name. The
            # pinned complete projection above prevents accepting a new alias.
            name_discrepancies.append(dict(sourceId=identity, occurrenceName=row['name'],
                inventoryName=inventory[identity]['name']))
    require(name_discrepancies==[dict(sourceId='special:t.z1o9nhkkrzeb:64216',
        occurrenceName='Ускоренное восприятие', inventoryName='Мир вокруг словно замедляется для Ваших глаз.')],
        'Original audit name discrepancy set changed')
    require({r['id'] for r in p['graphNodes']}=={r['id'] for r in source['definitions']},'Graph identities differ')
    merged={**source['sourceEvidence'],**e['sourceEvidence']}
    for q in p['generalQuestions']:
        for c in q['citations']:
            row=merged[f"core:P{c['paragraphIndex']}"]
            require(all(c[k]==row[k] for k in ('tabId','startIndex','endIndex')) and c['exactQuote'] in row['text'],'Source question citation drift')
    definitions=source['definitions'];bound=[r['runtime_id'] for r in rows.values() if r['runtime_id']]
    return dict(sourceOccurrences=len(rows),matchedOccurrences=len(bound),uniqueMatchedRuntimeIds=len(set(bound)),
        unboundOccurrences=sum(r['runtime_id']=='' and current[i]['runtimeId'] is None for i,r in rows.items()),
        graphNodes=len(definitions),buyable=sum(r['included'] for r in definitions),
        reference=sum(not r['included'] and r['kind']!='chi_technique' for r in definitions),
        useOnly=sum(r['kind']=='chi_technique' for r in definitions),originalGeneralQuestions=len(p['generalQuestions']),
        additionalExcerpts=len(e['sourceEvidence'])-6,preservedExcerpts=6,
        sourceNameDiscrepancies=name_discrepancies)

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--repo-root',type=Path,default=Path('.'))
    parser.add_argument('--skills-archive',type=Path);parser.add_argument('--acquisition-archive',type=Path)
    args=parser.parse_args()
    if args.skills_archive and args.acquisition_archive:print(extract(args.repo_root,args.skills_archive,args.acquisition_archive))
    else:print(validate_projection(args.repo_root))
