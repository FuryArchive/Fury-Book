"""Build negative SQL cases from genuine Kotlin wire. Positive golden bytes are never rewritten."""
from pathlib import Path
import copy
import json
import re

class Number(str):
    pass

def quote(value):
    out = json.dumps(value, ensure_ascii=False, separators=(',', ':'))
    return ''.join(('\\u%04X\\u%04X' % (0xD800+((ord(c)-0x10000)//1024), 0xDC00+((ord(c)-0x10000)%1024)))
                   if ord(c)>0xFFFF else ('\\u%04X' % ord(c)) if 0xD800<=ord(c)<=0xDFFF else c for c in out)

def render(value):
    if isinstance(value, Number): return str(value)
    if isinstance(value, str): return quote(value)
    if isinstance(value, list): return '['+','.join(map(render,value))+']'
    if isinstance(value, dict):
        keys=sorted(value,key=lambda k:k.encode('utf-16-be','surrogatepass'))
        return '{'+','.join(quote(k)+':'+render(value[k]) for k in keys)+'}'
    return json.dumps(value,separators=(',',':'))

root=Path(__file__).resolve().parents[2]
source=(root/'supabase/tests/acquisition_v2_fixtures.sql').read_text()
fixtures={}
for name,dto,wire in re.findall(r"VALUES \('([^']+)', \$dto\$(.*?)\$dto\$::jsonb, \$wire\$(.*?)\$wire\$\);",source):
    value=json.loads(wire,parse_int=Number,parse_float=Number)
    assert render(value)==wire, 'Independent negative-case renderer differs from genuine producer '+name
    fixtures[name]=(json.loads(dto),value,wire)
rows=[]
def case(name,base,edit):
    dto,value,_=fixtures[base]
    value=copy.deepcopy(value); edit(value)
    wire=render(value)
    body=copy.deepcopy(dto); body['p_envelope']={'payloadCanonical':wire}
    rows.append("INSERT INTO acquisition_fixtures VALUES ('%s', $dto$%s$dto$::jsonb, $wire$%s$wire$);" %
                (name,json.dumps(body,ensure_ascii=True,separators=(',',':')),wire))
case('bad-body','op1',lambda v:v['extras'].__setitem__('notes','different'))
case('bad-receipts','op2',lambda v:v.__setitem__('receipts',[]))
case('bad-chain','op2',lambda v:v['receipts'][1].__setitem__('beforeRevision','0'))
case('bad-finance','op2',lambda v:v['receipts'][1]['result']['remaining'].__setitem__('xp','99'))
case('bad-local','op2',lambda v:v.__setitem__('pendingResolution',{}))
case('bad-format','op2',lambda v:v.__setitem__('formatVersion',Number('2')))
case('bad-missing-proof','second-choice',lambda v:v.pop('syncProofs'))
case('bad-proof-id','second-choice',lambda v:v['syncProofs'][0].__setitem__('proofId','sync-proof:forged'))
case('bad-cycle','second-choice',lambda v:v['syncProofs'][0].__setitem__('proofIds',[v['syncProofs'][0]['proofId']]))
case('bad-prefix','after-adoption',lambda v:v['ledger']['entries'][0]['ruleVersions'].__setitem__('catalogRevision','changed-history'))
case('bad-note-loss','op2',lambda v:v['extras'].pop('noteEntries'))
case('bad-portrait','op2',lambda v:v['extras'].__setitem__('portraitUri','local://portrait'))
case('bad-receipt-scope','op2',lambda v:v['receipts'][1].__setitem__('requestCanonical',v['receipts'][1]['requestCanonical'].replace('"characterId":"hero"','"characterId":"B"')))
case('bad-entry-duplicate','op2',lambda v:v['ledger']['entries'][1].__setitem__('operationId','op1'))
case('bad-receipt-duplicate','op2',lambda v:v['receipts'][1].__setitem__('operationId','op1'))
case('bad-revision-type','op2',lambda v:v.__setitem__('revision',Number('2')))
case('bad-unknown-field','op2',lambda v:v.__setitem__('futureRequired',{}))
case('bad-inner-proof-pool','second-choice',lambda v:v['syncProofs'][0].__setitem__('payloadCanonical',v['syncProofs'][0]['payloadCanonical'][:-1]+',"syncProofs":[]}'))
def consistent_projection_edit(value, edit):
    edit(value['characterSnapshot']['characters'][0])
    tip=value['ledger']['entries'][-1]
    proof=json.loads(tip['evidenceCanonical'],parse_int=Number,parse_float=Number)
    for phase in ['before','after']: edit(proof[phase]['characterSnapshot']['characters'][0])
    tip['evidenceCanonical']=render(proof)
case('bad-nested-required','op2',lambda v:consistent_projection_edit(v,lambda ch:ch['gear'].__setitem__('futureRequired',{})))
case('bad-nested-type','op2',lambda v:consistent_projection_edit(v,lambda ch:ch['gear'].__setitem__('loadManual','wrong-type')))
def forged_import_cost(v):
    v['ledger']['entries'][-1]['amountDelta']['xp']='1'
    v['receipts'][-1]['result']['cost']['xp']='-1'
    v['receipts'][-1]['result']['remaining']['xp']=str(int(v['receipts'][-1]['result']['remaining']['xp'])+1)
case('bad-import-cost','copy',forged_import_cost)
case('bad-import-request','copy',lambda v:v['receipts'][-1].__setitem__('requestCanonical',v['receipts'][-1]['requestCanonical'].replace('sha256-utf16be:','sha256-forged:')))
def forged_command_extra(v):
    r=v['receipts'][-1]
    request=json.loads(r['requestCanonical'],parse_int=Number,parse_float=Number)
    request['unknownRequired']={};r['requestCanonical']=render(request)
    evidence=json.loads(v['ledger']['entries'][-1]['evidenceCanonical'],parse_int=Number,parse_float=Number)
    evidence['command']=request;v['ledger']['entries'][-1]['evidenceCanonical']=render(evidence)
case('bad-command-field','op2',forged_command_extra)
case('bad-legacy-baseline','legacy-migrated',lambda v:v['ledger']['opening']['legacyMigration'].__setitem__('calculatorBaseline','fake-baseline'))
case('bad-legacy-origin-id','legacy-migrated',lambda v:v['ledger']['opening'].__setitem__('migrationId','legacy-v11:forged'))
case('bad-instance-parameter','purchase',lambda v:v['instances'][0].__setitem__('parameters',{'unknown':''}))
case('bad-instance-option','purchase',lambda v:v['instances'][0].__setitem__('abilityOptionId',''))
def forged_change_kind(v):
    v['ledger']['entries'][-1]['changes'][0]['kind']='FAKE'
    v['receipts'][-1]['result']['changes'][0]['kind']='FAKE'
case('bad-change-kind','purchase',forged_change_kind)
def forged_condition(v):
    v['extras']['activeConditions']=['UNKNOWN']
    proof=json.loads(v['ledger']['entries'][-1]['evidenceCanonical'],parse_int=Number,parse_float=Number)
    for phase in ['before','after']: proof[phase]['extras']['activeConditions']=['UNKNOWN']
    v['ledger']['entries'][-1]['evidenceCanonical']=render(proof)
case('bad-extra-condition','op2',forged_condition)
def consistent_extra(v,field,value):
    v['extras'][field]=value
    proof=json.loads(v['ledger']['entries'][-1]['evidenceCanonical'],parse_int=Number,parse_float=Number)
    for phase in ['before','after']: proof[phase]['extras'][field]=value
    v['ledger']['entries'][-1]['evidenceCanonical']=render(proof)
case('bad-custom-format','op2',lambda v:consistent_extra(v,'customConditions','garbage'))
case('bad-group-format','op2',lambda v:consistent_extra(v,'skillGroups','garbage'))
case('bad-condition-format','op2',lambda v:consistent_extra(v,'conditionOverrides','garbage'))
def forged_payload(v,field,value):
    request=json.loads(v['receipts'][-1]['requestCanonical'],parse_int=Number,parse_float=Number)
    request['payload'][field]=value
    v['receipts'][-1]['requestCanonical']=render(request)
    proof=json.loads(v['ledger']['entries'][-1]['evidenceCanonical'],parse_int=Number,parse_float=Number)
    proof['command']=request;v['ledger']['entries'][-1]['evidenceCanonical']=render(proof)
case('bad-payload-kind','op2',lambda v:forged_payload(v,'kind','FAKE'))
case('bad-payload-field','op2',lambda v:forged_payload(v,'unknownRequired',{}))
# Raw duplicate key and invalid spellings cannot be generated by an ordinary JSON object model.
for name,wire in [('bad-duplicate-key',fixtures['op2'][2][:-1]+',"revision":"2"}'),
                  ('bad-number-grammar',fixtures['op2'][2].replace('"schema":11','"schema":01',1)),
                  ('bad-escape',fixtures['op2'][2].replace('SQL fixture','SQL\\xfixture',1))]:
    dto=copy.deepcopy(fixtures['op2'][0]);dto['p_envelope']={'payloadCanonical':wire}
    rows.append("INSERT INTO acquisition_fixtures VALUES ('%s', $dto$%s$dto$::jsonb, $wire$%s$wire$);" %
                (name,json.dumps(dto,ensure_ascii=True,separators=(',',':')),wire))
(root/'supabase/tests/acquisition_v2_invalid_fixtures.sql').write_text('-- Independent test-only mutations of genuine T15 producer fixtures.\n'+'\n'.join(rows)+'\n')
print('Negative fixtures:',len(rows),'; all genuine positive wires byte-checked before mutations')

# Contract-directed admission regressions. Original positive/negative bytes remain unchanged.
extra=(root/'supabase/tests/acquisition_v2_fix1_fixtures.sql')
if extra.exists():
    for name,dto,wire in re.findall(r"VALUES \('([^']+)', \$dto\$(.*?)\$dto\$::jsonb, \$wire\$(.*?)\$wire\$\);",extra.read_text()):
        value=json.loads(wire,parse_int=Number,parse_float=Number)
        assert render(value)==wire, 'Extra public producer renderer differs: '+name
        fixtures[name]=(json.loads(dto),value,wire)
    rows=[]
    for field in ['skills','development','developmentOverrides','customDevelopmentEntries','hiddenSkillIds','disabledSkillEffectIds']:
        case('bad-fix1-duplicate-'+field,'fix1-manual',lambda v,f=field:consistent_projection_edit(v,lambda c:c[f].append(copy.deepcopy(c[f][0]))))
    for field,key in [('skills','id'),('development','id'),('developmentOverrides','id'),('customDevelopmentEntries','id'),('customResources','uid')]:
        case('bad-fix1-filtered-'+field,'fix1-manual',lambda v,f=field,k=key:consistent_projection_edit(v,lambda c:c[f][0].__setitem__(k,'\u2003')))
    for field in ['development','developmentOverrides','customDevelopmentEntries']:
        case('bad-fix1-trimmed-'+field,'fix1-manual',lambda v,f=field:consistent_projection_edit(v,lambda c:c[f][0].__setitem__('id',' padded ')))
    for field in ['hiddenSkillIds','disabledSkillEffectIds']:
        case('bad-fix1-blank-'+field,'fix1-manual',lambda v,f=field:consistent_projection_edit(v,lambda c:c.__setitem__(f,['\u00a0'])))
    case('bad-fix1-trimmed-disabled','fix1-manual',lambda v:consistent_projection_edit(v,lambda c:c.__setitem__('disabledSkillEffectIds',[' padded '])))
    for field in ['experience','hpCurrent','abilityPointsOverride']:
        case('bad-fix1-int-zero-'+field,'fix1-manual',lambda v,f=field:consistent_projection_edit(v,lambda c:c.__setitem__(f,Number('-0'))))
    case('bad-fix1-int-overflow','fix1-manual',lambda v:consistent_projection_edit(v,lambda c:c.__setitem__('size',Number('2147483648'))))
    case('bad-fix1-filtered-skill-option','fix1-manual',lambda v:consistent_projection_edit(v,lambda c:c['skills'][0].__setitem__('definitionId','')))
    case('bad-fix1-trimmed-access','fix1-manual',lambda v:consistent_projection_edit(v,lambda c:c['developmentOverrides'][0].__setitem__('accessId',' padded ')))
    for field,value in [('reason',{}),('reason',Number('1')),('migrationId',None),('sourceFingerprint',False),('legacyStage',Number('1'))]:
        case('bad-fix1-opening-'+field+'-'+type(value).__name__,'purchase',lambda v,f=field,x=value:v['ledger']['opening'].__setitem__(f,x))
    foreign={'type':'Legacy','characterId':'foreign','schema':'11','sourceRevision':'1','sourceFingerprint':'source','ownershipKey':'development:purchase'}
    case('bad-fix1-foreign-origin','purchase',lambda v:v['instances'][0].__setitem__('origin',foreign))
    for field,value in [('schema',Number('11')),('sourceRevision',None),('sourceFingerprint',''),('ownershipKey','\u2003')]:
        case('bad-fix1-origin-'+field,'legacy-confirm',lambda v,f=field,x=value:v['instances'][0]['origin'].__setitem__(f,x))
    for field,value in [('rank',Number('2')),('candidateEntryId','foreign'),('custom',True)]:
        case('bad-fix1-ownership-'+field,'legacy-confirm',lambda v,f=field,x=value:v['ledger']['opening']['legacyMigration']['ownershipRecords'][0].__setitem__(f,x))
    case('bad-fix1-ownership-removed','legacy-confirm',lambda v:v['ledger']['opening']['legacyMigration'].__setitem__('ownershipRecords',[]))
    case('bad-fix1-ownership-duplicate','legacy-confirm',lambda v:v['ledger']['opening']['legacyMigration']['ownershipRecords'].append(copy.deepcopy(v['ledger']['opening']['legacyMigration']['ownershipRecords'][0])))
    case('bad-fix1-stage','legacy-confirm',lambda v:v['ledger']['opening'].__setitem__('legacyStage','Finalized'))
    case('bad-fix1-mapping','legacy-confirm',lambda v:v['ledger']['opening']['legacyMigration']['origin'].__setitem__('ownershipMappings',{'foreign':{'packId':'dubl-3.69','entryId':'magic-school:боевая магия'}}))
    for field,value in [('characterId','foreign'),('sourceFingerprint','forged'),('ownershipKey','development:foreign')]:
        case('bad-fix1-provenance-'+field,'legacy-confirm',lambda v,f=field,x=value:v['instances'][0]['origin'].__setitem__(f,x))
    case('bad-fix1-instance-identity','legacy-confirm',lambda v:v['instances'][0].__setitem__('instanceId','legacy:forged'))
    case('bad-fix1-instance-candidate','legacy-confirm',lambda v:v['instances'][0]['definition'].__setitem__('entryId','foreign'))
    case('bad-fix1-copied-removed','fix1-legacy-copy',lambda v:v.__setitem__('instances',[]))
    case('bad-fix1-recopied-removed','fix1-legacy-copy2',lambda v:v.__setitem__('instances',[]))
    case('bad-fix1-copied-rank','fix1-legacy-copy',lambda v:v['instances'][0].__setitem__('rank',Number('2')))
    case('bad-fix1-copied-parameters','fix1-legacy-copy',lambda v:v['instances'][0].__setitem__('parameters',{'forged':'value'}))
    case('bad-fix1-restored-removed','fix1-legacy-restore',lambda v:v.__setitem__('instances',[]))
    case('bad-fix1-copied-confirm-removed','fix1-copied-confirm',lambda v:v.__setitem__('instances',[]))
    case('bad-fix1-copied-confirm-rank','fix1-copied-confirm',lambda v:v['instances'][0].__setitem__('rank',Number('2')))
    case('bad-fix1-correction-flag','fix1-legacy-remove',lambda v:forged_payload(v,'patch',dict(json.loads(v['receipts'][-1]['requestCanonical'],parse_int=Number,parse_float=Number)['payload']['patch'],replacesInstances=False)))
    def forged_correction_before(v):
        proof=json.loads(v['ledger']['entries'][-1]['evidenceCanonical'],parse_int=Number,parse_float=Number)
        proof['before']['instances'][0]['rank']=Number('2');v['ledger']['entries'][-1]['evidenceCanonical']=render(proof)
    case('bad-fix1-correction-before','fix1-legacy-remove',forged_correction_before)
    def forged_paid_from(v):
        v['ledger']['entries'][-1]['changes'][0]['fromRank']=Number('2')
        v['receipts'][-1]['result']['changes'][0]['fromRank']=Number('2')
    case('bad-fix1-paid-from','fix1-legacy-paid',forged_paid_from)
    case('bad-fix1-paid-undo-removed','fix1-legacy-paid-undo',lambda v:v.__setitem__('instances',[]))
    case('bad-fix1-ownership-order','fix1-old',lambda v:v['ledger']['opening']['legacyMigration']['ownershipRecords'].reverse())
    def captured_raw_edit(v, edit):
        import hashlib
        origin=v['ledger']['opening']['legacyMigration']['origin']
        raw=json.loads(origin['raw'],parse_int=Number,parse_float=Number);edit(raw);origin['raw']=render(raw)
        fingerprint='sha256-utf16be:'+hashlib.sha256(origin['raw'].encode('utf-16-be','surrogatepass')).hexdigest()
        v['ledger']['opening']['sourceFingerprint']=fingerprint
        fields=[origin['characterId'],str(origin['schema']),origin['sourceRevision'],fingerprint]
        framed=''.join(str(len(x.encode('utf-16-be','surrogatepass'))//2)+':'+x for x in fields)
        v['ledger']['opening']['migrationId']='legacy-v11:sha256-utf16be:'+hashlib.sha256(framed.encode('utf-16-be','surrogatepass')).hexdigest()
    for name,edit in [
        ('schema-missing',lambda r:r.pop('schema')),
        ('schema-wrong',lambda r:r.__setitem__('schema',Number('1'))),
        ('characters-duplicate',lambda r:r['characters'].append(copy.deepcopy(r['characters'][0]))),
        ('characters-object',lambda r:r.__setitem__('characters',{})),
        ('selection-null',lambda r:r.__setitem__('activeCharacterId',None)),
        ('wrong-ruleset',lambda r:r['characters'][0]['ruleset'].__setitem__('id','foreign')),
        ('fractional-experience',lambda r:r['characters'][0].__setitem__('experience',Number('1.5'))),
        ('invalid-bool',lambda r:r['characters'][0].__setitem__('creationComplete','invalid')),
        ('duplicate-skills',lambda r:r['characters'][0].__setitem__('skills',[{'id':'x','rank':Number('1')},{'id':'x','rank':Number('1')}])),
        ('unrecorded-ownership',lambda r:r['characters'][0].__setitem__('development',[{'id':'x','rank':Number('1')}])),
    ]:
        case('bad-fix1-backup-'+name,'legacy-migrated',lambda v,e=edit:captured_raw_edit(v,e))
    (root/'supabase/tests/acquisition_v2_fix1_invalid_fixtures.sql').write_text('-- Lossless representation/provenance regressions from public producer fixtures.\n'+'\n'.join(rows)+'\n')
    print('Admission negative fixtures:',len(rows))

extra2=root/'supabase/tests/acquisition_v2_fix2_fixtures.sql'
if extra2.exists():
    for name,dto,wire in re.findall(r"VALUES \('([^']+)', \$dto\$(.*?)\$dto\$::jsonb, \$wire\$(.*?)\$wire\$\);",extra2.read_text()):
        value=json.loads(wire,parse_int=Number,parse_float=Number)
        assert render(value)==wire, 'Independent compatibility renderer differs '+name
        fixtures[name]=(json.loads(dto),value,wire)
    rows=[]
    malformed=['','+','-','١.١','١x','−١','＋١',' ١','١ ','²','𝟙','2147483648','-2147483649','٢١٤٧٤٨٣٦٤٨','-٢١٤٧٤٨٣٦٤٩','1\x00','1\ud800']
    for i,value in enumerate(malformed):
        case('bad-fix2-integer-string-'+str(i),'fix2-arabic',lambda v,t=value:captured_raw_edit(v,lambda r:r['characters'][0].__setitem__('experience',t)))
    for i,value in enumerate([Number('1.5'),Number('2147483648'),None,True,[],{}]):
        case('bad-fix2-integer-type-'+str(i),'fix2-arabic',lambda v,t=value:captured_raw_edit(v,lambda r:r['characters'][0].__setitem__('experience',t)))
    def exact_raw_replace(v,raw):
        import hashlib
        origin=v['ledger']['opening']['legacyMigration']['origin'];origin['raw']=raw
        fp='sha256-utf16be:'+hashlib.sha256(raw.encode('utf-16-be','surrogatepass')).hexdigest()
        v['ledger']['opening']['sourceFingerprint']=fp
        fields=[origin['characterId'],str(origin['schema']),origin['sourceRevision'],fp]
        framed=''.join(str(len(s.encode('utf-16-be','surrogatepass'))//2)+':'+s for s in fields)
        v['ledger']['opening']['migrationId']='legacy-v11:sha256-utf16be:'+hashlib.sha256(framed.encode('utf-16-be','surrogatepass')).hexdigest()
    for name,raw in [
        ('raw-control','{"schema":11,"characters":[{"id":"fix2-arabic","name":"Bad\x00raw"}]}'),
        ('raw-escape','{"schema":11,"characters":[{"id":"fix2-arabic","name":"Bad\\\ud800raw"}]}'),
        ('raw-token','{"schema":11,"characters":[{"id":"fix2-arabic","experience":\ud800}]}'),
    ]:
        case('bad-fix2-'+name,'fix2-arabic',lambda v,t=raw:exact_raw_replace(v,t))
    case('bad-fix2-ownership-removed','fix2-school-nul',lambda v:v['ledger']['opening']['legacyMigration'].__setitem__('ownershipRecords',[]))
    case('bad-fix2-ownership-substituted','fix2-school-high',lambda v:v['ledger']['opening']['legacyMigration']['ownershipRecords'][0].__setitem__('ownershipKey','school:replacement'))
    (root/'supabase/tests/acquisition_v2_fix2_invalid_fixtures.sql').write_text('-- Malformed present legacy values remain rejected; original raw fingerprints are recomputed.\n'+'\n'.join(rows)+'\n')
    print('Legacy compatibility negative fixtures:',len(rows))
