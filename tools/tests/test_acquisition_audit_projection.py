"""Original source-only audit projections never import historical PASS counters."""
import copy
from pathlib import Path
import pytest
ROOT=Path(__file__).resolve().parents[2]

def module():
    assert (ROOT/'tools/rulebook/acquisition_audit_projection.py').exists()
    from tools.rulebook import acquisition_audit_projection
    return acquisition_audit_projection

def test_exact_pinned_projection_and_all_source_joins():
    m=module(); report=m.validate_projection(ROOT)
    assert report==dict(sourceOccurrences=536,matchedOccurrences=524,uniqueMatchedRuntimeIds=517,
        unboundOccurrences=12,graphNodes=1498,buyable=1111,reference=319,useOnly=68,
        originalGeneralQuestions=27,additionalExcerpts=70,preservedExcerpts=6,
        sourceNameDiscrepancies=[dict(sourceId='special:t.z1o9nhkkrzeb:64216',
          occurrenceName='Ускоренное восприятие',inventoryName='Мир вокруг словно замедляется для Ваших глаз.')])

@pytest.mark.parametrize('mutation',['quote','binding','status','missing','recomputed_hash'])
def test_source_projection_corruption_fails_even_with_self_recomputed_digest(mutation):
    m=module(); p=m.read(ROOT/m.PROJECTION);e=m.read(ROOT/m.EXCERPTS)
    if mutation=='quote':p['generalQuestions'][0]['citations'][0]['exactQuote']+=' invented'
    elif mutation=='binding':p['sourceRequirements'][0]['runtime_id']='invented'
    elif mutation=='status':p['sourceRequirements'][0]['status']='PASS'
    elif mutation=='missing':p['sourceRequirements'].pop()
    else:e['sourceEvidence']['core:P34']['text']+=' invented';e['sourceEvidence']['core:P34']['sha256']=m.sha(e['sourceEvidence']['core:P34']['text'].encode());e['projectionSha256']=m.digest(e['sourceEvidence'])
    with pytest.raises(ValueError):m.validate_projection(ROOT,p,e)
