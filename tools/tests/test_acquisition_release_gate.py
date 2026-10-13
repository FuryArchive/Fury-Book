"""Independent acceptance joins; synthetic contracts are never release evidence."""
import copy
import importlib
from pathlib import Path

import pytest

ROOT = Path(__file__).resolve().parents[2]


def module():
    assert (ROOT / 'tools/rulebook/report_acquisition_coverage.py').is_file(), 'Missing T19 acquisition report'
    return importlib.import_module('tools.rulebook.report_acquisition_coverage')


def fixture():
    m = module()
    case = dict(caseId='known-zero', definitionRef='athletics', fromRank=0, toRank=1,
                optionId='DEFAULT', creationPhase='CREATION', discountConditions='NONE',
                instance='EXISTING', parameters={'semanticClass':'FIXED'}, sourceId=[],
                sourceRef=['fixture:source'], evidenceStatus='CONFIRMED', openingXP=100,
                openingAP=2, expectedCostXP=0, expectedCostAP=0, expectedRemainingXP=100,
                expectedRemainingAP=2, expectedUndoXP=100, expectedUndoAP=2,
                expectedApply='CONDITIONAL_ON_CONFIRMED_ADMISSION_AND_IDENTITY', blockingQuestion=[])
    unknown = copy.deepcopy(case); unknown.update(caseId='unknown', evidenceStatus='SOURCE_UNVERIFIED')
    for key in m.MONEY_FIELDS: unknown[key] = None
    cases = [case, unknown]
    ref = {'packId':'dubl-3.69','entryId':'athletics'}
    requirements = {c['caseId']: dict(rawCaseSha256=m.digest(c), effectiveCaseSha256=m.digest(c),
        candidateDefinitionRef=ref, actualDefinitionRef=ref, actualKind='SKILL',
        sourceFacets={k: ('SOURCE_UNVERIFIED' if k=='price' and c is unknown else 'CONFIRMED') for k in m.SOURCE_FACETS},
        sourceLinks=c['sourceRef'], sourceOccurrenceIds=[],
        executionRequirement={'expectedOutcome':'UNRESOLVED' if c is unknown else 'APPLIED'}) for c in cases}
    manifest = dict(scope='CONTRACT_FIXTURE', testedCommit='1'*40, inputDigests={},
                    caseRequirements=requirements, sourceRules={'rules':[], 'pendingDecisions':[]},
                    integrityErrors=[], sourceUniverse={})
    return manifest, cases


def test_missing_evidence_is_not_release_and_unknown_is_not_zero():
    manifest,cases=fixture(); report=module().report_coverage(manifest,cases,[])
    assert report['integrityOK'] and not report['releaseReady']
    assert report['requiredCaseCount']==2
    assert [c['status'] for c in report['cases']]==['UI_UNVERIFIED','SOURCE_UNVERIFIED']
    assert [c['priceClassification'] for c in report['cases']]==['KNOWN_ZERO','UNKNOWN_NULL']
    assert set(report['gates'])==set(module().REQUIRED_GATES)


@pytest.mark.parametrize('field,value',[('toRank',2),('optionId','other'),('discountConditions','SELF_TAUGHT'),
    ('creationPhase','AFTER_CREATION'),('parameters',{'semanticClass':'RENAMED_SAME_ID'}),('expectedCostXP',12)])
def test_changed_case_rank_option_phase_discount_parameter_or_price_fails(field,value):
    manifest,cases=fixture();cases[0][field]=value
    report=module().report_coverage(manifest,cases,[])
    assert not report['integrityOK'] and not report['releaseReady']
    assert report['cases'][0]['status']=='FAILED'


@pytest.mark.parametrize('mutation',['delete','duplicate','unknown_zero'])
def test_missing_duplicate_or_falsely_free_case_never_reduces_obligation(mutation):
    manifest,cases=fixture()
    if mutation=='delete':cases.pop()
    elif mutation=='duplicate':cases.append(cases[0])
    else:cases[1]['expectedCostXP']=0
    report=module().report_coverage(manifest,cases,[])
    assert not report['integrityOK'] and not report['releaseReady']
    assert report['requiredCaseCount']==2


def test_current_source_full_denominator_is_not_runtime_acceptance():
    m=module();manifest,cases,evidence=m.load_report_inputs(ROOT,[])
    report=m.report_coverage(manifest,cases,evidence)
    assert report['integrityOK'],report['integrityErrors']
    assert report['requiredCaseCount']==83818 and len(report['cases'])==83818
    assert report['sourceUniverse']['sourceOccurrences']==536
    assert report['sourceUniverse']['matchedOccurrences']==524
    assert report['sourceUniverse']['uniqueMatchedRuntimeIds']==517
    assert report['sourceUniverse']['developmentWithChi']==796
    assert report['sourceUniverse']['developmentWithoutChi']==768
    assert report['sourceUniverse']['graphNodes']==1498
    assert report['sourceUniverse']['buyable']==1111
    assert not report['releaseReady']
