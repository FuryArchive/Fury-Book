\set ON_ERROR_STOP on
BEGIN;
\ir ../migrations/20261012222846_acquisition_ledger_v2.sql
\ir acquisition_v2_fixtures.sql
\ir acquisition_v2_invalid_fixtures.sql
\ir acquisition_v2_test_support.sql
-- The validator must reject each malformed wire itself, before a later CAS/archive guard.
DO $$ DECLARE f record; accepted boolean; reason text; BEGIN
 FOR f IN SELECT name,canonical FROM acquisition_fixtures WHERE name LIKE 'bad-%' ORDER BY name LOOP
  accepted:=false;reason:=NULL;
  BEGIN PERFORM acquisition_private.validate_wire(f.canonical); accepted:=true;
  EXCEPTION WHEN OTHERS THEN reason:=SQLERRM; END;
  PERFORM pg_temp.check_test('strict validator rejects malformed wire: '||f.name,NOT accepted,reason);
 END LOOP;
END $$;
SET LOCAL ROLE authenticated;
SELECT set_config('request.jwt.claim.sub','aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',true),
 set_config('request.jwt.claim.role','authenticated',true), set_config('request.jwt.claims','',true);
CREATE FUNCTION pg_temp.push_fixture(p_name text,p_expected bigint DEFAULT NULL) RETURNS jsonb LANGUAGE plpgsql AS $$
DECLARE f jsonb; BEGIN
 SELECT body INTO STRICT f FROM acquisition_fixtures WHERE name=p_name;
 RETURN public.acquisition_sync_v2(f->>'p_character_id',coalesce(p_expected,(f->>'p_expected_revision')::bigint),
  f->'p_operation_ids',f->'p_envelope',(f->>'p_writer_version')::integer,nullif(f->'p_resolution','null'::jsonb));
END $$;
DO $$ DECLARE r jsonb; f jsonb; n text; campaign uuid; token text; v jsonb;
BEGIN
 r:=pg_temp.push_fixture('lexical');
 PERFORM pg_temp.check_test('raw Double lexemes and Unicode acknowledged byte-exact',r->>'status'='ack' AND r->>'payloadCanonical'=(SELECT canonical FROM acquisition_fixtures WHERE name='lexical'),r::text);
 r:=pg_temp.push_fixture('lexical');
 PERFORM pg_temp.check_test('lexical retry returns identical stored acknowledgement',r->>'status'='ack' AND r->>'serverRevision'='1' AND r->>'payloadCanonical'=(SELECT canonical FROM acquisition_fixtures WHERE name='lexical'),r::text);
 r:=public.acquisition_get_v2('lexical',2);
 PERFORM pg_temp.check_test('lexical reader returns exact stored raw wire',r->>'status'='envelope' AND r->>'payloadCanonical'=(SELECT canonical FROM acquisition_fixtures WHERE name='lexical'),r::text);
 FOR n IN SELECT name FROM acquisition_fixtures WHERE name LIKE 'bad-%' ORDER BY name LOOP
  r:=pg_temp.push_fixture(n);
  PERFORM pg_temp.check_test('invalid canonical fixture rejected: '||n,r->>'status'='identityConflict',r::text);
 END LOOP;
 SELECT body INTO f FROM acquisition_fixtures WHERE name='op1';
 FOREACH n IN ARRAY ARRAY['{}','{"payloadCanonical":null}','{"payloadCanonical":123}','{"payloadCanonical":"{}","extra":1}'] LOOP
  r:=public.acquisition_sync_v2('hero',0,'["op1"]',n::jsonb,2,NULL);
  PERFORM pg_temp.check_test('strict wire wrapper rejected: '||n,r->>'status'='identityConflict',r::text);
 END LOOP;
 r:=public.acquisition_sync_v2('hero',0,'[]',f->'p_envelope',2,NULL);
 PERFORM pg_temp.check_test('empty operation IDs rejected',r->>'status'='identityConflict',r::text);
 r:=public.acquisition_sync_v2('hero',0,'["op1","op2"]',f->'p_envelope',2,NULL);
 PERFORM pg_temp.check_test('multiple saved tips rejected',r->>'status'='identityConflict',r::text);
 FOREACH n IN ARRAY ARRAY['0','1','3',NULL] LOOP
  r:=public.acquisition_sync_v2('hero',0,'["op1"]',f->'p_envelope',n::integer,NULL);
  PERFORM pg_temp.check_test('writer version rejected: '||coalesce(n,'null'),r->>'status'='identityConflict',r::text);
 END LOOP;
 r:=pg_temp.push_fixture('copy2');
 PERFORM pg_temp.check_test('second-generation public producer copy accepted',r->>'status'='ack' AND r->>'payloadCanonical'=(SELECT canonical FROM acquisition_fixtures WHERE name='copy2'),r::text);
 r:=pg_temp.push_fixture('op1');
 PERFORM pg_temp.check_test('campaign fixture genuine upload accepted',r->>'status'='ack',r::text);
 PERFORM set_config('request.jwt.claim.sub','bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb',true);
 campaign:=(public.campaign_create('Synthetic campaign')->>'id')::uuid;
 token:=public.campaign_create_invite(campaign,1)->>'token';
 PERFORM set_config('request.jwt.claim.sub','aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',true);
 v:=pg_temp.attempt(format('SELECT public.campaign_join(%L,%L)',token,'foreign-character'));
 PERFORM pg_temp.check_test('campaign join enforces character owner',v->>'error'='character does not belong to current user',v::text);
 PERFORM set_config('request.jwt.claim.sub','aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',true);
 r:=public.campaign_join(token,'hero');
 PERFORM pg_temp.check_test('campaign join marker does not rewrite canonical data',r->>'linkedCharacterId'='hero' AND public.acquisition_get_v2('hero',2)->>'payloadCanonical'=(SELECT canonical FROM acquisition_fixtures WHERE name='op1'),r::text);
 v:=pg_temp.attempt(format('SELECT public.campaign_get_dashboard(%L::uuid)',campaign));
 PERFORM pg_temp.check_test('legacy campaign authorization precedes format negotiation',v->>'error'='gm access required',v::text);
 v:=pg_temp.attempt(format('SELECT public.campaign_get_dashboard_v2(%L::uuid,2)',campaign));
 PERFORM pg_temp.check_test('V2 campaign still requires GM',v->>'error'='gm access required',v::text);
 PERFORM set_config('request.jwt.claim.sub','bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb',true);
 v:=pg_temp.attempt(format('SELECT public.campaign_get_dashboard(%L::uuid)',campaign));
 PERFORM pg_temp.check_test('old GM reader explicitly requires upgrade',v->>'error'='acquisition campaign reader version 2 required',v::text);
 r:=public.campaign_get_dashboard_v2(campaign,2);
 PERFORM pg_temp.check_test('V2 GM reader returns complete byte-exact acquisition branch',r->'players'->0->>'format'='acquisition-v2' AND r->'players'->0->>'payloadCanonical'=(SELECT canonical FROM acquisition_fixtures WHERE name='op1') AND r->'players'->0->'data'='null'::jsonb,r::text);
 v:=pg_temp.attempt(format('SELECT public.campaign_set_character(%L::uuid,%L)',campaign,'hero'));
 PERFORM pg_temp.check_test('campaign set-character preserves owner isolation',v->>'error'='character does not belong to current user',v::text);
 PERFORM set_config('request.jwt.claim.sub','aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',true);
 r:=public.campaign_leave(campaign);
 PERFORM pg_temp.check_test('campaign leave retains durable character',r->>'status'='ok' AND public.acquisition_get_v2('hero',2)->>'status'='envelope',r::text);
END $$;
RESET ROLE;
TABLE acquisition_results;
DO $$ DECLARE n integer; BEGIN
 SELECT count(*) INTO n FROM acquisition_results WHERE NOT passed;
 IF n>0 THEN RAISE EXCEPTION 'acquisition edge assertions failed: %',n; END IF;
 RAISE NOTICE 'acquisition edge assertions PASS: %',(SELECT count(*) FROM acquisition_results);
END $$;
ROLLBACK;
