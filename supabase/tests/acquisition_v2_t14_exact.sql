\set ON_ERROR_STOP on
-- Bounded current-producer refresh; original 26+10+17 fixtures are unchanged.
BEGIN;
\ir ../migrations/20261012222846_acquisition_ledger_v2.sql
\ir acquisition_v2_t14_fixtures.sql
-- Optional execution-only partition; every selected fixture retains all assertions.
\if :{?t14_case}
DELETE FROM acquisition_fixtures WHERE name<>:'t14_case';
\endif
\ir acquisition_v2_test_support.sql
CREATE TEMP TABLE t14_observed(name text PRIMARY KEY,ack jsonb NOT NULL);
GRANT INSERT,SELECT ON t14_observed TO authenticated;
DO $$ DECLARE f record;BEGIN
 FOR f IN SELECT * FROM acquisition_fixtures ORDER BY name LOOP
  PERFORM acquisition_private.validate_wire(f.canonical);
  PERFORM pg_temp.check_test('T14 fresh exact direct validator: '||f.name,f.body->'p_envelope'=jsonb_build_object('payloadCanonical',f.canonical));
 END LOOP;
END $$;
SET LOCAL ROLE authenticated;
SELECT set_config('request.jwt.claim.sub','aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',true),set_config('request.jwt.claim.role','authenticated',true),set_config('request.jwt.claims','',true);
DO $$ DECLARE f record;reply jsonb;got jsonb;BEGIN
 FOR f IN SELECT * FROM acquisition_fixtures ORDER BY name LOOP
  reply:=public.acquisition_sync_v2(f.body->>'p_character_id',0,f.body->'p_operation_ids',f.body->'p_envelope',2,NULL);
  got:=public.acquisition_get_v2(f.body->>'p_character_id',2);
  PERFORM pg_temp.check_test('T14 exact authenticated ACK/read: '||f.name,
   reply->>'status'='ack' AND reply->>'payloadCanonical'=f.canonical AND reply->>'serverRevision'='1' AND reply->>'operationId'=f.body->'p_operation_ids'->>0 AND
   got->>'status'='envelope' AND got->>'payloadCanonical'=f.canonical AND got->>'serverRevision'='1' AND got->>'minWriterVersion'='2',reply::text);
  INSERT INTO t14_observed VALUES(f.name,reply);
 END LOOP;
END $$;
RESET ROLE;
DO $$ BEGIN IF EXISTS(SELECT 1 FROM acquisition_results WHERE NOT passed) THEN RAISE EXCEPTION 'T14 exact-decimal producer refresh failed';END IF;END $$;
SELECT count(*) AS t14_assertions FROM acquisition_results;
\pset format unaligned
\pset tuples_only on
SELECT ack::text FROM t14_observed ORDER BY name;
ROLLBACK;
SELECT to_regnamespace('acquisition_private') IS NULL AS t14_migration_rolled_back;
SELECT NOT EXISTS(SELECT 1 FROM auth.users WHERE id IN ('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa','bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb')) AS t14_synthetic_users_rolled_back;
