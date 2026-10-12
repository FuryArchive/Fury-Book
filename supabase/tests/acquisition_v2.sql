\set ON_ERROR_STOP on
-- Only a disposable schema fixture. All users, data, helpers and test results roll back.
\if :{?ACQUISITION_OUTER_TRANSACTION}
\else
BEGIN;
\endif
\ir acquisition_v2_fixtures.sql
\ir acquisition_v2_invalid_fixtures.sql
\ir acquisition_v2_test_support.sql
SET LOCAL ROLE authenticated;
SELECT set_config('request.jwt.claim.sub','aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',true),
 set_config('request.jwt.claim.role','authenticated',true), set_config('request.jwt.claims','',true);
DO $$ DECLARE f jsonb; r jsonb;
BEGIN
 SELECT body INTO f FROM acquisition_fixtures WHERE name='op1';
 IF to_regprocedure('public.acquisition_sync_v2(text,bigint,jsonb,jsonb,integer,jsonb)') IS NULL THEN
  PERFORM pg_temp.check_test('V2 RPC exists', false, 'missing V2 RPC on baseline');
  -- Baseline has no validator: seed the same genuine T15 envelope through its old public writer.
  r := public.save_web_character('hero',f->'p_envelope','{}',0,'test');
 ELSE
  EXECUTE 'SELECT public.acquisition_sync_v2($1,$2,$3,$4,$5,$6)'
   INTO r USING 'hero',0::bigint,f->'p_operation_ids',f->'p_envelope',2,NULL::jsonb;
  PERFORM pg_temp.check_test('V2 RPC exists',true);
  PERFORM pg_temp.check_test('first genuine producer operation acknowledged',r = jsonb_build_object(
   'status','ack','operationId','op1','payloadCanonical',(SELECT canonical FROM acquisition_fixtures WHERE name='op1'),'serverRevision','1'),r::text);
 END IF;
END $$;
SELECT pg_temp.blocked('old web save cannot downgrade migrated character',
 $$SELECT public.save_web_character('hero','{"id":"hero"}','{}',1,'old')$$);
SELECT pg_temp.blocked('old web save with full envelope cannot bypass operation identity',
 $$SELECT public.save_web_character('hero',(SELECT body->'p_envelope' FROM acquisition_fixtures WHERE name='op1'),'{}',1,'old')$$);
SELECT pg_temp.blocked('old native force cannot overwrite migrated character',
 $$SELECT public.native_force_character('hero','{"id":"hero"}','{}',1,'old',false)$$);
SELECT pg_temp.blocked('old native force cannot delete ledger',
 $$SELECT public.native_force_character('hero','{}','{}',1,'old',true)$$);
SELECT pg_temp.blocked('old web delete cannot remove ledger',
 $$SELECT public.delete_web_character('hero',1,'old')$$);
SELECT pg_temp.blocked('old native sync cannot overwrite ledger',
 $$SELECT public.native_sync_snapshot('{"schema":11,"characters":[{"id":"hero"}],"activeCharacterId":"hero"}',
 '{}',ARRAY['hero'],ARRAY[]::text[],'{"hero":1}','{}',0,'old')$$);
SELECT pg_temp.blocked('old native sync cannot delete ledger',
 $$SELECT public.native_sync_snapshot('{"schema":11,"characters":[],"activeCharacterId":null}',
 '{}',ARRAY[]::text[],ARRAY['hero'],'{"hero":1}','{}',0,'old')$$);
SELECT pg_temp.blocked('old native link cannot clone a migrated identity as legacy',
 $$SELECT public.native_link_device('{"schema":11,"characters":[{"id":"hero","name":"copy"}],"activeCharacterId":"hero"}','{}','{}','old')$$);
SELECT pg_temp.blocked('old bootstrap explicitly refuses migrated object', $$SELECT public.get_web_bootstrap()$$);
SELECT pg_temp.blocked('direct legacy user_state writer cannot create parallel downgraded snapshot',
 $$WITH x AS (INSERT INTO public.user_state(user_id,snapshot) VALUES (auth.uid(),'{"schema":11,"characters":[{"id":"hero"}]}') RETURNING 1) SELECT to_jsonb(count(*)) FROM x$$);
SELECT pg_temp.blocked('direct character update denied',
 $$WITH x AS (UPDATE public.web_characters SET data='{}' WHERE user_id=auth.uid() AND character_id='hero' RETURNING 1) SELECT to_jsonb(count(*)) FROM x$$);
DO $$ DECLARE r jsonb; f jsonb; v jsonb;
BEGIN
 -- Genuine legacy compatibility remains revision gated; NULL must never skip a CAS check.
 r := public.save_web_character('legacy','{"id":"legacy"}','{}',0,'test');
 PERFORM pg_temp.check_test('unmigrated legacy save still works',r->>'status'='ok',r::text);
 v := pg_temp.attempt($q$SELECT public.native_force_character('legacy','{}','{}',0,'old',false)$q$);
 PERFORM pg_temp.check_test('stale legacy force revision conflicts',v->'result'->>'status'='conflict',v::text);
END $$;
SELECT pg_temp.blocked('null expected legacy save revision refused', $$SELECT public.save_web_character('legacy','{}','{}',NULL,'old')$$);
SELECT pg_temp.blocked('null expected legacy force revision refused', $$SELECT public.native_force_character('legacy','{}','{}',NULL,'old',false)$$);
SELECT pg_temp.blocked('null expected legacy delete revision refused', $$SELECT public.delete_web_character('legacy',NULL,'old')$$);
SELECT pg_temp.blocked('null expected legacy state revision refused', $$SELECT public.save_web_state('legacy','{}',NULL,'old')$$);
SELECT set_config('request.jwt.claim.sub','bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb',true);
DO $$ DECLARE r jsonb; BEGIN
 r:=public.native_force_character('hero','{}','{}',1,'B',false);
 PERFORM pg_temp.check_test('B cannot overwrite A',r->>'status'='missing',r::text);
 DECLARE v jsonb; BEGIN
   v:=pg_temp.attempt($q$SELECT to_jsonb(count(*)) FROM public.web_characters WHERE user_id<>'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb'::uuid$q$);
   PERFORM pg_temp.check_test('B direct SELECT never reads A',v->>'sqlstate'='42501' OR v->'result'='0'::jsonb,v::text);
  END;
END $$;
SELECT pg_temp.blocked('old fresh import cannot introduce V2 without receipts',
 $$SELECT public.save_web_character('fresh','{"id":"fresh","formatVersion":1}','{}',0,'old')$$);
SELECT pg_temp.blocked('old native link cannot introduce fresh V2 data',
 $$SELECT public.native_link_device('{"schema":11,"characters":[{"id":"fresh","ledger":{}}],"activeCharacterId":"fresh"}','{}','{}','old')$$);
SELECT pg_temp.blocked('old native snapshot cannot introduce fresh V2 data',
 $$SELECT public.native_sync_snapshot('{"schema":11,"characters":[{"id":"fresh","ledger":{}}],"activeCharacterId":"fresh"}',
 '{}',ARRAY['fresh'],ARRAY[]::text[],'{}','{}',0,'old')$$);
SELECT set_config('request.jwt.claim.sub','',true);
SELECT pg_temp.blocked('missing owner authentication is rejected', $$SELECT public.save_web_character('x','{}','{}',0,'old')$$);
SELECT pg_temp.blocked('missing owner V2 write refused',
 $$SELECT public.acquisition_sync_v2('hero',0,'["op1"]',(SELECT body->'p_envelope' FROM acquisition_fixtures WHERE name='op1'),2,NULL)$$);
SELECT pg_temp.blocked('missing owner native link refused',
 $$SELECT public.native_link_device('{"schema":11,"characters":[]}','{}','{}','old')$$);
SELECT pg_temp.blocked('missing owner campaign read refused',
 $$SELECT public.campaign_get_dashboard('cccccccc-cccc-4ccc-8ccc-cccccccccccc')$$);
SELECT set_config('request.jwt.claim.sub','invalid-uuid',true);
DO $$ DECLARE v jsonb := pg_temp.attempt($q$SELECT public.get_web_bootstrap()$q$); BEGIN
 PERFORM pg_temp.check_test('malformed owner never authorizes',v->>'sqlstate'='22P02',v::text);
END $$;
SELECT set_config('request.jwt.claim.sub','aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',true);
-- RPC-specific tests only execute when it exists, keeping RED focused on missing behavior.
\ir acquisition_v2_rpc_assertions.sql
RESET ROLE;
TABLE acquisition_results;
DO $$ DECLARE n integer; BEGIN
 SELECT count(*) INTO n FROM acquisition_results WHERE NOT passed;
 IF n>0 THEN RAISE EXCEPTION 'acquisition V2 assertions failed: %',n; END IF;
 RAISE NOTICE 'acquisition V2 assertions PASS: %', (SELECT count(*) FROM acquisition_results);
END $$;
ROLLBACK;
DO $$ BEGIN
 IF EXISTS(SELECT 1 FROM auth.users WHERE id IN ('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa','bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb')) THEN
  RAISE EXCEPTION 'rollback fixture users leaked';
 END IF;
END $$;
