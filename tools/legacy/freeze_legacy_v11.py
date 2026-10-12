"""Reproduce the frozen migration input from Git objects, never from current prices."""
import pathlib,json,hashlib,subprocess
root=pathlib.Path(__file__).resolve().parents[2]
BASELINE='f47a5256a66ddc5e1b7b5c1e204e57279fc7227f'
sources=[]
def blob(path):
 return subprocess.check_output(['git','show',BASELINE+':shared/src/commonMain/'+path],cwd=root)
def read(path):
 raw=blob(path);sources.append({'path':'shared/src/commonMain/'+path,'sha256':hashlib.sha256(raw).hexdigest()});return json.loads(raw)
base='resources/fcp/'
core=read(base+'dubl-3.69/manifest.json');chi=read(base+'dubl-chi-3.69/manifest.json')
rows=[]
for manifest in [core,chi]:
 for entry in sorted(manifest['entries'],key=lambda x:x.get('order',0)):
  if entry['kind']=='dubl.development':
   for e in read(base+manifest['id']+'/'+entry['path'])['entries']:
    rows.append({'id':e['id'],'packId':manifest['id'],'cost':e.get('cost',0),'ability':e.get('costType')=='ability','options':[max(0,o.get('value',0)) for o in e.get('abilityOptions',[])]})
skills=read(base+'dubl-3.69/content/skills_catalog.json')
magic=read(base+'dubl-3.69/content/magic_equipment_catalog.json')

for f in ['CharacterEconomy.kt','DevelopmentModels.kt','SkillModels.kt','GeneratedSkillCatalog.kt','MagicEquipmentModels.kt','CharacterModels.kt']:
 p='kotlin/com/furybook/dubl/model/'+f;sources.append({'path':'shared/src/commonMain/'+p,'sha256':hashlib.sha256(blob(p)).hexdigest()})
p='kotlin/com/furybook/dubl/data/SnapshotCodec.kt'
sources.append({'path':'shared/src/commonMain/'+p,'sha256':hashlib.sha256(blob(p)).hexdigest()})
payload={'format':'dubl.legacy-economy-v11','baseline':'f47a5256a66ddc5e1b7b5c1e204e57279fc7227f','sources':sources,'packOrder':['dubl-3.69','dubl-chi-3.69'],'inactiveChiClaims':[c['id'] for c in chi.get('claims',[]) if c['kind']=='dubl.development'],'developments':rows,'skillRankCosts':skills['rankCosts'],'skillIds':[s['id'] for s in skills['skills']],'builtInSkillIds':[s['id'] for s in skills['skills'] if not s.get('template',False)],'spellIds':[s['id'] for s in magic['spells']]}
raw=json.dumps(payload,ensure_ascii=False,indent=2)+'\n'
(root/'rulesets/dubl-3.69/acquisition/legacy-v11-catalog.json').write_text(raw)
(root/'shared/src/commonMain/resources/fcp/dubl-3.69/content/legacy-v11-catalog.json').write_text(raw)
print('SHA',hashlib.sha256(raw.encode()).hexdigest())
