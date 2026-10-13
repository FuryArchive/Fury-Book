"""Synthetic decoder contracts, never accepted as an actual test receipt."""
import copy,json
from pathlib import Path
import pytest
ROOT=Path(__file__).resolve().parents[2]
def module():
    assert (ROOT/'tools/rulebook/acquisition_native_evidence.py').exists()
    from tools.rulebook import acquisition_native_evidence
    return acquisition_native_evidence

def fixture():
    m=module();ref={'packId':'dubl-3.69','entryId':'athletics'}; char='hero'; inst='owned/athletics'
    case=dict(caseId='fixture',fromRank=0,toRank=1,optionId='DEFAULT',creationPhase='CREATION',discountConditions='NONE',parameters={'semanticClass':'FIXED'},openingXP=100,openingAP=2,expectedCostXP=10,expectedCostAP=0,expectedRemainingXP=90,expectedRemainingAP=2,expectedUndoXP=100,expectedUndoAP=2)
    requirement=dict(actualDefinitionRef=ref,actualKind='SKILL',executionRequirement={'expectedOutcome':'APPLIED','minimumAttributes':{},'selfTaughtOwned':False})
    bind=dict(kind='SKILL',definitionRef=ref,characterId=char,instanceId=inst,parameters={},optionId=None)
    before=dict(formatVersion=1,characterId=char,characterSnapshot={'schema':9,'activeCharacterId':char,'characters':[{'id':char,'skills':[],'attributes':{},'development':[],'creationComplete':False}]},extras={'notes':'preserve'},instances=[],ledger={'opening':{'balance':{'xp':'100','ap':'2'}},'entries':[]},receipts=[],revision='0',storageRevision='0',creationPhase='Creating',outbox=[],syncMetadata={'serverRevision':'0'})
    request=dict(characterId=char,targets=[dict(definition=ref,instanceId=inst,targetRank=1,parameters={},optionId=None)],externalConfirmations=[],choiceSelections={},includeTargets=True,prerequisiteSelections={})
    change=dict(kind='SKILL',definition=ref,instanceId=inst,fromRank=0,toRank=1,parameters={},optionId=None)
    cost={'xp':'10','ap':'0'};remaining={'xp':'90','ap':'2'}
    quote={'cost':cost,'sources':[{'documentId':'source','documentVersion':'v1','sheet':'tab','paragraph':1,'originalText':'10'}],'discountEvidence':[]}
    token=dict(request=request,characterId=char,baseRevision='0',catalogRevision='catalog',resolutionRevision='resolution',phase='Creating',changes=[change],cost=cost,remaining=remaining,issues=[],choices=[],quotes=[quote])
    canonical=json.dumps(dict(type='ConfirmAcquisition',characterId=char,request=request,expectedRevision='0',previewToken=json.dumps(token)))
    receipt=dict(operationId='purchase',requestCanonical=canonical,result=dict(cost=cost,remaining=remaining,changes=[change]),status='Applied',beforeRevision='0',afterRevision='1')
    after=copy.deepcopy(before);after.update(revision='1',storageRevision='1')
    after['instances']=[dict(instanceId=inst,definition=ref,rank=1,parameters={},abilityOptionId=None,origin={'type':'New'})]
    after['characterSnapshot']['characters'][0]['skills']=[dict(id=inst,definitionId='athletics',rank=1)]
    saved=dict(schema='acquisition-evidence-v1',externalConfirmations=[],phase='Creating',undoProjection=dict(schema='acquisition-projection-v1',before={k:before[k] for k in ('characterSnapshot','instances')},after={k:after[k] for k in ('characterSnapshot','instances')}),prices=[dict(instanceId=inst,sourceRef=quote['sources'][0],quote=quote)])
    entry=dict(operationId='purchase',type='Purchase',changes=[change],amountDelta={'xp':'-10','ap':'0'},ruleVersions={'catalogRevision':'catalog','resolutionRevision':'resolution'},reversesId=None,creationPhase='Creating',evidenceCanonical=json.dumps(saved))
    after['receipts']=[receipt];after['ledger']['entries']=[entry];after['outbox']=[{'operationId':'purchase'}]
    undone=copy.deepcopy(before);undone.update(revision='2',storageRevision='2');old=copy.deepcopy(receipt);old['status']='Undone'
    inverse=copy.deepcopy(change);inverse.update(fromRank=1,toRank=0)
    undoRequest=json.dumps(dict(type='Undo',characterId=char,expectedRevision='1'))
    undoReceipt=dict(operationId='undo',requestCanonical=undoRequest,result=dict(cost={'xp':'-10','ap':'0'},remaining={'xp':'100','ap':'2'},changes=[inverse]),status='Applied',beforeRevision='1',afterRevision='2')
    inverseEntry=dict(operationId='undo',type='Undo',changes=[inverse],amountDelta=cost,ruleVersions=entry['ruleVersions'],reversesId='purchase',creationPhase='Creating',evidenceCanonical=json.dumps(dict(schema='undo-evidence-v1',reversesId='purchase',originalRequest=canonical)))
    undone['receipts']=[old,undoReceipt];undone['ledger']['entries']=[entry,inverseEntry];undone['outbox']=[{'operationId':'purchase'},{'operationId':'undo'}]
    record=dict(outcome='APPLIED',binding=bind,native=dict(before=before,after=after,reopened=copy.deepcopy(after),undone=undone,reopenedUndone=copy.deepcopy(undone),requestCanonical=canonical))
    return record,case,requirement,dict(catalogRevision='catalog',resolutionRevision='resolution',sourceRefs=[quote['sources'][0]])

def validate(parts):return module().validate_native(*parts)

def test_actual_shape_contract_checks_receipt_ledger_reopen_and_full_undo():
    result=validate(fixture());assert result['outcome']=='APPLIED' and result['operationId']=='purchase' and result['undoOperationId']=='undo'
    assert result['normalization']=='DERIVED_FROM_NATIVE_CANONICAL_RESULT_AND_LEDGER'

@pytest.mark.parametrize('mutation',['kind_spoof','kind_missing','wrong_ref','wrong_rank','wrong_request','wrong_quote','wrong_cost','wrong_ledger_sign','wrong_original_status','wrong_undo_request','wrong_undo_business','changed_reopen','all_blocked_positive'])
def test_native_identity_money_and_full_restoration_corruption_rejected(mutation):
    args=list(fixture());r=args[0];n=r['native'];a=n['after'];u=n['undone']
    if mutation=='kind_spoof':r['binding']['kind']='DEVELOPMENT'
    elif mutation=='kind_missing':r['binding'].pop('kind')
    elif mutation=='wrong_ref':r['binding']['definitionRef']['packId']='other'
    elif mutation=='wrong_rank':args[1]['toRank']=2
    elif mutation=='wrong_request':req=json.loads(n['requestCanonical']);req['request']['targets'][0]['targetRank']=2;n['requestCanonical']=json.dumps(req)
    elif mutation=='wrong_quote':req=json.loads(n['requestCanonical']);token=json.loads(req['previewToken']);token['quotes'][0]['cost']['xp']='0';req['previewToken']=json.dumps(token);n['requestCanonical']=json.dumps(req)
    elif mutation=='wrong_cost':a['receipts'][0]['result']['cost']['xp']='0'
    elif mutation=='wrong_ledger_sign':a['ledger']['entries'][0]['amountDelta']['xp']='10'
    elif mutation=='wrong_original_status':u['receipts'][0]['status']='Applied'
    elif mutation=='wrong_undo_request':u['receipts'][1]['requestCanonical']=json.dumps(dict(type='Undo',characterId='other',expectedRevision='1'))
    elif mutation=='wrong_undo_business':u['extras']['notes']='lost'
    elif mutation=='changed_reopen':n['reopened']['extras']['notes']='lost'
    elif mutation=='all_blocked_positive':r['outcome']='BLOCKED';n.update(after=copy.deepcopy(n['before']),reopened=copy.deepcopy(n['before']));n.pop('undone');n.pop('reopenedUndone')
    with pytest.raises((ValueError,KeyError)):validate(args)

@pytest.mark.parametrize('mutation',['delete_instance','wrong_rank','wrong_instance','wrong_definition','wrong_parameter','missing_character_rank'])
def test_forged_post_ownership_is_rejected_even_when_inverse_mirror_is_synchronized(mutation):
    args=list(fixture());n=args[0]['native'];a=n['after']
    if mutation=='delete_instance':a['instances']=[]
    elif mutation=='wrong_rank':a['instances'][0]['rank']=0
    elif mutation=='wrong_instance':a['instances'][0]['instanceId']='other'
    elif mutation=='wrong_definition':a['instances'][0]['definition']['entryId']='other'
    elif mutation=='wrong_parameter':a['instances'][0]['parameters']={'arbitrary':'x'}
    else:a['characterSnapshot']['characters'][0]['skills']=[]
    proof=json.loads(a['ledger']['entries'][0]['evidenceCanonical']);proof['undoProjection']['after']={k:a[k] for k in ('characterSnapshot','instances')};a['ledger']['entries'][0]['evidenceCanonical']=json.dumps(proof)
    n['reopened']=copy.deepcopy(a)
    with pytest.raises((ValueError,KeyError)):validate(args)
