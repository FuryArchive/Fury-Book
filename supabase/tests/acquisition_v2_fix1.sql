\set ON_ERROR_STOP on
-- The full aggregate remains runnable, but exceeded the disposable launcher's unchanged
-- 120-second per-file limit. Named rollback batches partition the same assertions; they
-- do not cap a valid request, shorten proofs or change any admission oracle.
\if :{?fix1_mode}
\else
\set fix1_mode all
\endif
BEGIN;
\ir ../migrations/20261012222846_acquisition_ledger_v2.sql
\ir acquisition_v2_fixtures.sql
\ir acquisition_v2_fix1_fixtures.sql
\ir acquisition_v2_fix1_invalid_fixtures.sql
\ir acquisition_v2_test_support.sql
CREATE TEMP TABLE acquisition_case_mode(name text NOT NULL CHECK(name IN ('all','negative-a','negative-b','positive','atomic','campaign')));
INSERT INTO acquisition_case_mode VALUES (:'fix1_mode');
CREATE TEMP TABLE acquisition_case_selection(name text PRIMARY KEY);
INSERT INTO acquisition_case_selection
 SELECT name FROM (SELECT name,row_number() OVER(ORDER BY name) AS ord FROM acquisition_fixtures WHERE name LIKE 'bad-fix1-%') f
 WHERE (SELECT name FROM acquisition_case_mode)='all'
 OR ((SELECT name FROM acquisition_case_mode)='negative-a' AND ord<=34)
 OR ((SELECT name FROM acquisition_case_mode)='negative-b' AND ord>34);
INSERT INTO acquisition_case_selection
 SELECT name FROM acquisition_fixtures WHERE name LIKE 'fix1-%' AND (SELECT name FROM acquisition_case_mode) IN ('all','positive');
GRANT SELECT ON acquisition_case_mode,acquisition_case_selection TO authenticated;
CREATE TEMP TABLE acquisition_legacy_sources(name text PRIMARY KEY,source jsonb);
INSERT INTO acquisition_legacy_sources
 SELECT name,((canonical::json OPERATOR(acquisition_private.->) 'ledger' OPERATOR(acquisition_private.->) 'opening' OPERATOR(acquisition_private.->) 'legacyMigration' OPERATOR(acquisition_private.->) 'origin' OPERATOR(acquisition_private.->>) 'raw')::jsonb->'characters'->0)
 FROM acquisition_fixtures WHERE name IN ('legacy-confirm','legacy-migrated','fix1-old');
CREATE TEMP TABLE acquisition_refusal_reasons(reason text PRIMARY KEY);
GRANT SELECT ON acquisition_legacy_sources TO authenticated;
GRANT SELECT,INSERT ON acquisition_refusal_reasons TO authenticated;
-- Probe successes roll back independently, so RED does not poison later calls.
CREATE FUNCTION pg_temp.admission_call(p_name text) RETURNS jsonb LANGUAGE plpgsql AS $$
DECLARE f record; original jsonb; ch jsonb; r jsonb; BEGIN
 SELECT * INTO STRICT f FROM acquisition_fixtures WHERE name=p_name;
 IF f.body->>'p_character_id' IN ('fix1-legacy-copy','fix1-copied-confirm') AND f.body->>'p_expected_revision'<>'0' THEN
  PERFORM pg_temp.admission_call(CASE WHEN f.body->>'p_character_id'='fix1-copied-confirm' THEN 'fix1-copied-unresolved' ELSE 'fix1-legacy-copy' END);
  IF f.body->>'p_expected_revision'='2' THEN
   SELECT body INTO original FROM acquisition_fixtures WHERE name=CASE WHEN p_name LIKE '%paid-undo%' THEN 'fix1-legacy-paid' ELSE 'fix1-legacy-remove' END;
   PERFORM public.acquisition_sync_v2(original->>'p_character_id',1,original->'p_operation_ids',original->'p_envelope',2,NULL);
  END IF;
 END IF;
 IF f.body->>'p_character_id' IN ('legacy-confirm','legacy-migrated','fix1-old') THEN
  SELECT source INTO ch FROM acquisition_legacy_sources WHERE name=f.body->>'p_character_id';
  r:=public.save_web_character(f.body->>'p_character_id',ch,'{}',0,'synthetic');
 END IF;
 RETURN public.acquisition_sync_v2(f.body->>'p_character_id',(f.body->>'p_expected_revision')::bigint,
  f.body->'p_operation_ids',f.body->'p_envelope',2,NULL);
END $$;
DO $$ DECLARE f record; accepted boolean; reason text; BEGIN
 FOR f IN SELECT name,canonical FROM acquisition_fixtures WHERE name LIKE 'bad-fix1-%' AND name IN (SELECT name FROM acquisition_case_selection) ORDER BY name LOOP
  accepted:=false;reason:=NULL;
  BEGIN PERFORM acquisition_private.validate_wire(f.canonical); accepted:=true;
  EXCEPTION WHEN OTHERS THEN reason:=SQLERRM; END;
  PERFORM pg_temp.check_test('admission validator rejects: '||f.name,NOT accepted,reason);
 END LOOP;
END $$;
SET LOCAL ROLE authenticated;
SELECT set_config('request.jwt.claim.sub','aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',true),set_config('request.jwt.claim.role','authenticated',true),set_config('request.jwt.claims','',true);
DO $$ DECLARE f record; v jsonb; BEGIN
 FOR f IN SELECT name FROM acquisition_fixtures WHERE name LIKE 'bad-fix1-%' AND name IN (SELECT name FROM acquisition_case_selection) ORDER BY name LOOP
  v:=pg_temp.attempt(format('SELECT pg_temp.admission_call(%L)',f.name));
  IF v->'result'->>'status'='identityConflict' THEN INSERT INTO acquisition_refusal_reasons VALUES(v->'result'->>'reason') ON CONFLICT DO NOTHING; END IF;
  PERFORM pg_temp.check_test('admission RPC non-ack: '||f.name,v->'result'->>'status'='identityConflict',v::text);
 END LOOP;
 FOR f IN SELECT name,canonical FROM acquisition_fixtures WHERE name LIKE 'fix1-%' AND name IN (SELECT name FROM acquisition_case_selection) ORDER BY name LOOP
  v:=pg_temp.attempt(format('SELECT pg_temp.admission_call(%L)',f.name));
  PERFORM pg_temp.check_test('public producer still accepted: '||f.name,v->'result'->>'status'='ack' AND v->'result'->>'payloadCanonical'=f.canonical,v::text);
 END LOOP;
END $$;
-- Check refusal atomicity without the test-only attempt rollback, on a fresh identity.
DO $$ DECLARE f record; r jsonb; BEGIN
 IF (SELECT name FROM acquisition_case_mode) NOT IN ('all','atomic') THEN RETURN; END IF;
 FOR f IN SELECT body FROM acquisition_fixtures WHERE name LIKE 'bad-fix1-%' AND body->>'p_character_id' NOT IN ('legacy-confirm','legacy-migrated','fix1-old') LOOP
  r:=public.acquisition_sync_v2(f.body->>'p_character_id',0,f.body->'p_operation_ids',f.body->'p_envelope',2,NULL);
 END LOOP;
END $$;
RESET ROLE;
SELECT pg_temp.check_test('non-ack admission leaves no durable partial writes',NOT EXISTS(SELECT 1 FROM acquisition_private.characters) AND NOT EXISTS(SELECT 1 FROM acquisition_private.operations) AND NOT EXISTS(SELECT 1 FROM acquisition_private.archives) AND NOT EXISTS(SELECT 1 FROM public.web_characters)) WHERE (SELECT name FROM acquisition_case_mode) IN ('all','atomic');
SET LOCAL ROLE authenticated;
-- A rejected migration must also preserve its already-present legacy source row.
DO $$ DECLARE f record; source jsonb; r jsonb; before_row jsonb; after_row jsonb; BEGIN
 IF (SELECT name FROM acquisition_case_mode) NOT IN ('all','atomic') THEN RETURN; END IF;
 SELECT s.source INTO source FROM acquisition_legacy_sources s WHERE name='legacy-confirm';
 r:=public.save_web_character('legacy-confirm',source,'{}',0,'legacy-source');
 FOR f IN SELECT name,body FROM acquisition_fixtures WHERE name LIKE 'bad-fix1-%' AND body->>'p_character_id'='legacy-confirm' LOOP
  r:=public.acquisition_sync_v2('legacy-confirm',1,f.body->'p_operation_ids',f.body->'p_envelope',2,NULL);
  PERFORM pg_temp.check_test('existing legacy non-ack: '||f.name,r->>'status'='identityConflict',r::text);
 END LOOP;
END $$;
RESET ROLE;
SELECT pg_temp.check_test('rejected migration retains original legacy data/revision and no private writes',
 (SELECT count(*)=1 AND bool_and(character_id='legacy-confirm' AND revision=1 AND data=(SELECT source FROM acquisition_legacy_sources WHERE name='legacy-confirm')) FROM public.web_characters) AND NOT EXISTS(SELECT 1 FROM acquisition_private.characters) AND NOT EXISTS(SELECT 1 FROM acquisition_private.operations) AND NOT EXISTS(SELECT 1 FROM acquisition_private.archives)) WHERE (SELECT name FROM acquisition_case_mode) IN ('all','atomic');
SET LOCAL ROLE authenticated;
DO $$ DECLARE c uuid; uid uuid; version integer; v jsonb; BEGIN
 IF (SELECT name FROM acquisition_case_mode) NOT IN ('all','campaign') THEN RETURN; END IF;
 PERFORM set_config('request.jwt.claim.sub','bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb',true);
 c:=(public.campaign_create('Admission campaign')->>'id')::uuid;
 PERFORM set_config('request.jwt.claim.sub','aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',true);
 FOREACH uid IN ARRAY ARRAY[c,'cccccccc-cccc-4ccc-8ccc-cccccccccccc'::uuid] LOOP
  FOREACH version IN ARRAY ARRAY[1,2] LOOP
   v:=pg_temp.attempt(format('SELECT public.campaign_get_dashboard_v2(%L::uuid,%s)',uid,version));
   PERFORM pg_temp.check_test('GM auth before reader: '||uid||':'||version,v->>'error'='gm access required',v::text);
  END LOOP;
 END LOOP;
 PERFORM set_config('request.jwt.claim.sub','bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb',true);
 v:=public.campaign_get_dashboard_v2(c,1);
 PERFORM pg_temp.check_test('authorized reader refusal remains explicit',v->>'status'='identityConflict',v::text);
 v:=public.campaign_get_dashboard_v2(c,2);
 PERFORM pg_temp.check_test('authorized supported reader still works',v->>'id'=c::text,v::text);
 PERFORM set_config('request.jwt.claim.sub','',true);
 FOREACH version IN ARRAY ARRAY[1,2] LOOP
  v:=pg_temp.attempt(format('SELECT public.campaign_get_dashboard_v2(%L::uuid,%s)',c,version));
  PERFORM pg_temp.check_test('null auth before reader: '||version,v->>'error'='authentication required',v::text);
 END LOOP;
END $$;
RESET ROLE;
TABLE acquisition_results;
SELECT 'REFUSAL_REASON:'||reason FROM acquisition_refusal_reasons ORDER BY reason;
DO $$ DECLARE n integer; BEGIN SELECT count(*) INTO n FROM acquisition_results WHERE NOT passed;
 IF n>0 THEN RAISE EXCEPTION 'acquisition admission assertions failed: %',n; END IF;
 RAISE NOTICE 'acquisition admission assertions PASS: %',(SELECT count(*) FROM acquisition_results);
END $$;
ROLLBACK;
