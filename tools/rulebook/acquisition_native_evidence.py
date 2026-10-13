"""Strict joins for native canonical receipts. Normalized identities are derived,
not fields claimed to exist on the native Receipt. Unknown witnesses prove nothing.
"""
import copy
import json
import re
from tools.rulebook.acquisition_coverage_records import require, semantic_sha as digest
SELF_TAUGHT={'packId':'dubl-3.69','entryId':'feat_dd0ba74aedebce10'}

def decode(raw):
    def unique(pairs):
        value={}
        for key,item in pairs:
            require(key not in value,'duplicate_native_json_key');value[key]=item
        return value
    require(isinstance(raw,str),'canonical_json_not_string')
    return json.loads(raw,object_pairs_hook=unique)

def amount(value):
    require(set(value)=={'xp','ap'},'amount_fields')
    require(all(isinstance(v,str) and re.fullmatch(r'-?(0|[1-9][0-9]*)',v) for v in value.values()),'noncanonical_money')
    return tuple(int(value[k]) for k in ('xp','ap'))

def balance(state):
    result=amount(state['ledger']['opening']['balance'])
    for row in state['ledger']['entries']:result=tuple(a+b for a,b in zip(result,amount(row['amountDelta'])))
    return result

def character(state):
    rows=state['characterSnapshot']['characters'];require(len(rows)==1,'character_count')
    require(rows[0]['id']==state['characterId']==state['characterSnapshot']['activeCharacterId'],'character_scope')
    return rows[0]

def projection(state):return {k:state[k] for k in ('characterSnapshot','instances')}
def business(state):return {k:v for k,v in state.items() if k not in ('ledger','receipts','revision','storageRevision','outbox')}

def ownership(state,binding,rank):
    rows=[r for r in state['instances'] if r['instanceId']==binding['instanceId']]
    require(len(rows)==(1 if rank else 0),'actual_instance_presence')
    if rows:
        row=rows[0]
        require(row['definition']==binding['definitionRef'] and row['rank']==rank and row['parameters']==binding['parameters'] and row['abilityOptionId']==binding['optionId'],'actual_instance_transition')
    c=character(state);entry=binding['definitionRef']['entryId']
    if binding['kind']=='SKILL':
        matches=[r for r in c['skills'] if r['id']==binding['instanceId']]
        require(len(matches)==(1 if rank else 0),'actual_character_skill_presence')
        if matches:require(matches[0]['definitionId']==entry and matches[0]['rank']==rank,'actual_character_skill_rank')
    elif binding['kind']=='DEVELOPMENT':
        dev=c['development']
        if isinstance(dev,dict):require(dev.get(entry,0)==rank,'actual_character_development_rank')
        else:
            matches=[r for r in dev if r.get('definitionId',r.get('id'))==entry]
            require((matches[0]['rank'] if len(matches)==1 else 0)==rank,'actual_character_development_rank')
    else:raise ValueError('unsupported_positive_ownership_witness')

def validate_native(record,case,requirement,catalog):
    b=record['binding'];n=record['native'];witness=requirement['executionRequirement']
    require(b.get('kind')==requirement['actualKind'],'binding_kind_mismatch')
    require(b['definitionRef']==requirement['actualDefinitionRef'],'binding_definition_mismatch')
    require(case['parameters']=={'semanticClass':'FIXED'} and b['parameters']=={},'unresolved_parameter_witness')
    require(b['optionId']==(None if case['optionId']=='DEFAULT' else case['optionId']),'case_option_mismatch')
    require(isinstance(b['instanceId'],str) and bool(b['instanceId']),'missing_instance_identity')
    expected=witness['expectedOutcome'];require(expected in ('APPLIED','BLOCKED_SOURCE_PROHIBITION'),'unresolved_admission_witness')
    before=n['before'];after=n['after'];phase='Creating' if case['creationPhase']=='CREATION' else 'Finalized'
    require(before['characterId']==b['characterId'] and before['creationPhase']==phase,'before_character_phase')
    c=character(before)
    for attr,minimum in witness.get('minimumAttributes',{}).items():require(c['attributes'][attr]['base']>=minimum,'missing_source_prerequisite_attribute')
    self_owned=[r for r in before['instances'] if r['definition']==SELF_TAUGHT and r['rank']>=1]
    require(bool(self_owned)==witness.get('selfTaughtOwned',False),'self_taught_witness')
    if self_owned:require(c['attributes']['INTELLIGENCE']['base']>=6,'illegal_self_taught_prerequisite')
    ownership(before,b,case['fromRank'])
    require(balance(before)==(case['openingXP'],case['openingAP']),'opening_balance_mismatch')
    confirm=decode(n['requestCanonical']);request=confirm['request']
    target=dict(definition=b['definitionRef'],instanceId=b['instanceId'],targetRank=case['toRank'],parameters=b['parameters'],optionId=b['optionId'])
    require(confirm['type']=='ConfirmAcquisition' and confirm['characterId']==b['characterId'] and confirm['expectedRevision']==before['revision'],'canonical_confirmation_scope')
    require(request==dict(characterId=b['characterId'],targets=[target],externalConfirmations=[],choiceSelections={},includeTargets=True,prerequisiteSelections={}),'canonical_request_case_mismatch')
    token=decode(confirm['previewToken'])
    require(token['request']==request and token['characterId']==b['characterId'] and token['baseRevision']==before['revision'] and token['phase']==phase,'canonical_token_scope')
    require(all(token[k]==catalog[k] for k in ('catalogRevision','resolutionRevision')),'canonical_catalog_revision')
    require(n['reopened']==after,'reopen_mismatch')
    if expected=='BLOCKED_SOURCE_PROHIBITION':
        require(record['outcome']=='BLOCKED' and before==after,'source_prohibition_changed_store')
        require(token['issues'] and any(i['status']=='Unsatisfied' for i in token['issues']),'missing_source_prohibition_issue')
        require('undone' not in n and 'reopenedUndone' not in n,'blocked_not_a_receipt_or_undo')
        return dict(outcome='BLOCKED',characterId=b['characterId'],instanceId=b['instanceId'],quoteIdentity=digest(token),
                    normalization='DERIVED_FROM_NATIVE_CANONICAL_RESULT_AND_LEDGER',operationId=None,undoOperationId=None)
    require(record['outcome']=='APPLIED','positive_witness_was_blocked')
    cost=(case['expectedCostXP'],case['expectedCostAP']);remaining=(case['expectedRemainingXP'],case['expectedRemainingAP'])
    require(all(type(v) is int for v in cost) and min(cost)>=0,'unknown_positive_cost')
    change=dict(kind=b['kind'],definition=b['definitionRef'],instanceId=b['instanceId'],fromRank=case['fromRank'],toRank=case['toRank'],parameters=b['parameters'],optionId=b['optionId'])
    require(token['changes']==[change] and not token['issues'] and not token['choices'],'compound_or_blocked_token')
    require(amount(token['cost'])==cost and amount(token['remaining'])==remaining,'token_money')
    require(len(token['quotes'])==1 and amount(token['quotes'][0]['cost'])==cost,'token_quote_cost')
    require(token['quotes'][0]['sources'] and all(s in catalog['sourceRefs'] for s in token['quotes'][0]['sources']),'quote_source_provenance')
    require(after['characterId']==b['characterId'] and after['creationPhase']==phase,'after_scope')
    require(int(after['revision'])==int(before['revision'])+1 and int(after['storageRevision'])==int(before['storageRevision'])+1,'purchase_revision')
    ownership(after,b,case['toRank'])
    require(balance(after)==remaining,'saved_balance')
    require(after['ledger']['opening']==before['ledger']['opening'] and after['ledger']['entries'][:-1]==before['ledger']['entries'],'ledger_prefix')
    require(after['receipts'][:-1]==before['receipts'],'receipt_prefix')
    receipt=after['receipts'][-1];entry=after['ledger']['entries'][-1];operation=receipt['operationId']
    require(bool(operation) and receipt['status']=='Applied' and receipt['requestCanonical']==n['requestCanonical'],'original_applied_receipt')
    require(receipt['beforeRevision']==before['revision'] and receipt['afterRevision']==after['revision'],'receipt_revision')
    require(receipt['result']['changes']==[change] and amount(receipt['result']['cost'])==cost and amount(receipt['result']['remaining'])==remaining,'receipt_result')
    require(entry['operationId']==operation and entry['type']=='Purchase' and entry['changes']==[change] and entry['reversesId'] is None and entry['creationPhase']==phase,'ledger_identity')
    require(amount(entry['amountDelta'])==tuple(-v for v in cost),'signed_ledger_cost')
    require(entry['ruleVersions']=={k:catalog[k] for k in ('catalogRevision','resolutionRevision')},'ledger_rule_versions')
    saved=decode(entry['evidenceCanonical'])
    require(saved['schema']=='acquisition-evidence-v1' and saved['externalConfirmations']==[] and saved['phase']==phase,'saved_evidence_scope')
    require(saved['undoProjection']==dict(schema='acquisition-projection-v1',before=projection(before),after=projection(after)),'saved_inverse_projection')
    require(len(saved['prices'])==1 and saved['prices'][0]['instanceId']==b['instanceId'] and saved['prices'][0]['quote']==token['quotes'][0] and saved['prices'][0]['sourceRef'] in catalog['sourceRefs'],'saved_quote_join')
    # A purchase may change only its business projection and append canonical history.
    require({k:v for k,v in business(after).items() if k not in ('characterSnapshot','instances')}=={k:v for k,v in business(before).items() if k not in ('characterSnapshot','instances')},'purchase_other_business_fields')
    undone=n['undone'];require(n['reopenedUndone']==undone,'undo_reopen_mismatch')
    require(business(undone)==business(before) and undone['ledger']['opening']==before['ledger']['opening'],'full_business_restoration')
    require(int(undone['revision'])==int(after['revision'])+1 and int(undone['storageRevision'])==int(after['storageRevision'])+1,'undo_revision')
    require(balance(undone)==(case['expectedUndoXP'],case['expectedUndoAP']),'undo_balance')
    require(undone['ledger']['entries'][:-1]==after['ledger']['entries'],'undo_ledger_prefix')
    expected_receipts=copy.deepcopy(after['receipts']);expected_receipts[-1]['status']='Undone'
    require(undone['receipts'][:-1]==expected_receipts,'original_receipt_not_undone')
    inverse=undone['ledger']['entries'][-1];undo=undone['receipts'][-1];undo_id=undo['operationId']
    reverse={**change,'fromRank':change['toRank'],'toRank':change['fromRank']}
    require(undo_id and undo_id!=operation and inverse['operationId']==undo_id and inverse['type']=='Undo' and inverse['reversesId']==operation,'undo_operation_identity')
    require(inverse['changes']==[reverse] and amount(inverse['amountDelta'])==cost and inverse['creationPhase']==phase and inverse['ruleVersions']==entry['ruleVersions'],'undo_inverse_ledger')
    require(decode(inverse['evidenceCanonical'])==dict(schema='undo-evidence-v1',reversesId=operation,originalRequest=n['requestCanonical']),'undo_original_request')
    require(undo['status']=='Applied' and undo['beforeRevision']==after['revision'] and undo['afterRevision']==undone['revision'],'undo_receipt_status_revision')
    require(decode(undo['requestCanonical'])==dict(type='Undo',characterId=b['characterId'],expectedRevision=after['revision']),'undo_request_scope')
    require(undo['result']['changes']==[reverse] and amount(undo['result']['cost'])==tuple(-v for v in cost) and amount(undo['result']['remaining'])==(case['expectedUndoXP'],case['expectedUndoAP']),'undo_receipt_result')
    require([r['operationId'] for r in after['outbox']]==[r['operationId'] for r in before['outbox']]+[operation] and [r['operationId'] for r in undone['outbox']]==[r['operationId'] for r in after['outbox']]+[undo_id],'outbox_history')
    return dict(outcome='APPLIED',characterId=b['characterId'],instanceId=b['instanceId'],operationId=operation,undoOperationId=undo_id,
        quoteIdentity=digest(token),receiptIdentity=digest(receipt),undoReceiptIdentity=digest(undo),
        cost=dict(zip(('xp','ap'),cost)),normalization='DERIVED_FROM_NATIVE_CANONICAL_RESULT_AND_LEDGER')
