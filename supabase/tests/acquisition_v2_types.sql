\set ON_ERROR_STOP on
BEGIN;
\ir ../migrations/20261012222846_acquisition_ledger_v2.sql
\ir acquisition_v2_fixtures.sql
\ir acquisition_v2_test_support.sql
SET LOCAL ROLE authenticated;
SELECT set_config('request.jwt.claim.sub','aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',true),
 set_config('request.jwt.claim.role','authenticated',true),set_config('request.jwt.claims','',true);
DO $$ DECLARE f record; r jsonb; raw text; source jsonb; n text; v jsonb; BEGIN
 FOR f IN SELECT * FROM acquisition_fixtures WHERE name IN ('controls','extras-structured','manual','nonfinancial','complete','purchase','empty-purchase','legacy-migrated','legacy-extras','legacy-confirm') ORDER BY name LOOP
  IF f.name LIKE 'legacy-%' THEN
   source:=(f.canonical::jsonb->'ledger'->'opening'->'legacyMigration'->'origin'->>'raw')::jsonb->'characters'->0;
   r:=public.save_web_character(f.name,source,'{}',0,'fixture');
   PERFORM pg_temp.check_test('legacy source seeded: '||f.name,r->>'status'='ok',r::text);
  END IF;
  r:=public.acquisition_sync_v2(f.body->>'p_character_id',(f.body->>'p_expected_revision')::bigint,f.body->'p_operation_ids',f.body->'p_envelope',2,NULL);
  PERFORM pg_temp.check_test('actual public producer operation accepted: '||f.name,r->>'status'='ack' AND r->>'payloadCanonical'=f.canonical,r::text);
  IF f.name='controls' THEN
   r:=public.acquisition_get_v2('controls',2);
   PERFORM pg_temp.check_test('NUL/control/UTF16-map read byte-exact',r->>'payloadCanonical'=f.canonical,r::text);
  END IF;
 END LOOP;
 FOREACH n IN ARRAY ARRAY['undo-original','undo'] LOOP
  SELECT * INTO f FROM acquisition_fixtures WHERE name=n;
  r:=public.acquisition_sync_v2('undo',(f.body->>'p_expected_revision')::bigint,f.body->'p_operation_ids',f.body->'p_envelope',2,NULL);
  PERFORM pg_temp.check_test('actual Undo chain accepted: '||n,r->>'status'='ack' AND r->>'payloadCanonical'=f.canonical,r::text);
 END LOOP;
 SELECT * INTO f FROM acquisition_fixtures WHERE name='op1';
 r:=public.acquisition_sync_v2('hero',0,f.body->'p_operation_ids',f.body->'p_envelope',2,NULL);
 SELECT * INTO f FROM acquisition_fixtures WHERE name='server2';
 r:=public.acquisition_sync_v2('hero',1,f.body->'p_operation_ids',f.body->'p_envelope',2,NULL);
 SELECT * INTO f FROM acquisition_fixtures WHERE name='choose-server-v1';
 r:=public.acquisition_sync_v2('hero',2,f.body->'p_operation_ids',f.body->'p_envelope',2,f.body->'p_resolution');
 PERFORM pg_temp.check_test('historical inline v1 proof retains exact evidence',r->>'status'='ack' AND r->>'payloadCanonical'=f.canonical,r::text);
END $$;
RESET ROLE;
TABLE acquisition_results;
DO $$ DECLARE n integer; BEGIN SELECT count(*) INTO n FROM acquisition_results WHERE NOT passed;
 IF n>0 THEN RAISE EXCEPTION 'acquisition type assertions failed: %',n; END IF;
 RAISE NOTICE 'acquisition type assertions PASS: %',(SELECT count(*) FROM acquisition_results);
END $$;
ROLLBACK;
