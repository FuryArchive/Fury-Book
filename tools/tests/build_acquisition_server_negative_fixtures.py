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
