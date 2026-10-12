\set ON_ERROR_STOP on
BEGIN;
\ir ../migrations/20261012222846_acquisition_ledger_v2.sql
\ir acquisition_v2_fixtures.sql
\ir acquisition_v2_test_support.sql
CREATE TEMP TABLE acquisition_resource_fixtures(name text PRIMARY KEY,raw text,expected text);
GRANT SELECT ON acquisition_resource_fixtures TO authenticated;
INSERT INTO acquisition_resource_fixtures VALUES
 ('oversize-before-parse','x'||repeat(' ',8388608),'Wire payload exceeds bounded server limit'),
 ('nesting',repeat('[',65)||'0'||repeat(']',65),'Wire nesting limit exceeded');
INSERT INTO acquisition_resource_fixtures SELECT 'ledger-count',
 acquisition_private.set_field(acquisition_private.set_field(canonical::json,'ledger',
 acquisition_private.set_field((canonical::json) OPERATOR(acquisition_private.->) 'ledger','entries',
 ('['||repeat('{},',2048)||'{}]')::json)),
 'receipts',('['||repeat('{},',2048)||'{}]')::json)::text,
 'Ledger/receipt completeness mismatch' FROM acquisition_fixtures WHERE name='op1';
-- Count sentinels are tested before content checks; these are deliberately malformed large
-- inputs, not a claim that valid large product histories have been accepted or truncated.
INSERT INTO acquisition_resource_fixtures SELECT 'proof-count',acquisition_private.set_field(canonical::json,'syncProofs',('['||repeat('{},',512)||'{}]')::json)::text,
 'Invalid/excessive proof pool' FROM acquisition_fixtures WHERE name='op1';
INSERT INTO acquisition_resource_fixtures SELECT 'unsupported-identity',acquisition_private.set_field(canonical::json,'characterId','"\u0000"'::json)::text,
 'unsupported Unicode escape sequence' FROM acquisition_fixtures WHERE name='op1';
SET LOCAL ROLE authenticated;
SELECT set_config('request.jwt.claim.sub','aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',true),
 set_config('request.jwt.claim.role','authenticated',true),set_config('request.jwt.claims','',true);
DO $$ DECLARE f record; r jsonb; BEGIN
 FOR f IN SELECT * FROM acquisition_resource_fixtures ORDER BY name LOOP
  r:=public.acquisition_sync_v2('hero',0,'["op1"]',jsonb_build_object('payloadCanonical',f.raw),2,NULL);
  PERFORM pg_temp.check_test('explicit resource/representation refusal: '||f.name,r->>'status'='identityConflict' AND r->>'reason'=f.expected,r::text);
 END LOOP;
END $$;
RESET ROLE;
SELECT pg_temp.check_test('refusal writes no floor, operation, archive or public row',
 NOT EXISTS(SELECT 1 FROM acquisition_private.characters) AND NOT EXISTS(SELECT 1 FROM acquisition_private.operations) AND NOT EXISTS(SELECT 1 FROM acquisition_private.archives) AND
 NOT EXISTS(SELECT 1 FROM public.web_characters WHERE user_id IN ('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa','bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb')));
TABLE acquisition_results;
DO $$ DECLARE n integer; BEGIN SELECT count(*) INTO n FROM acquisition_results WHERE NOT passed;
 IF n>0 THEN RAISE EXCEPTION 'acquisition resource assertions failed: %',n; END IF;
 RAISE NOTICE 'acquisition resource assertions PASS: %',(SELECT count(*) FROM acquisition_results);
END $$;
ROLLBACK;
