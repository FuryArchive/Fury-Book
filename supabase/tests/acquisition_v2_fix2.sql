\set ON_ERROR_STOP on
\if :{?fix2_mode}
\else
\set fix2_mode all
\endif
\if :{?fix2_case}
\else
\set fix2_case ''
\endif
\if :{?fix2_phase}
\else
\set fix2_phase all
\endif
BEGIN;
\ir ../migrations/20261012222846_acquisition_ledger_v2.sql
\ir acquisition_v2_fixtures.sql
\ir acquisition_v2_fix2_fixtures.sql
\ir acquisition_v2_fix2_invalid_fixtures.sql
\ir acquisition_v2_test_support.sql
\ir acquisition_v2_fix2_observed.sql
CREATE TEMP TABLE acquisition_case_mode(name text NOT NULL CHECK(name IN ('all','positive','negative','positive-a','positive-b','negative-a','negative-b')));
INSERT INTO acquisition_case_mode VALUES (:'fix2_mode');
CREATE TEMP TABLE acquisition_case_selection(name text PRIMARY KEY);
INSERT INTO acquisition_case_selection
 SELECT name FROM (SELECT name,row_number() OVER(PARTITION BY (name LIKE 'fix2-%') ORDER BY name) AS ord FROM acquisition_fixtures WHERE name LIKE 'fix2-%' OR name LIKE 'bad-fix2-%') f
 WHERE (name LIKE 'fix2-%' AND ((SELECT name FROM acquisition_case_mode) IN ('all','positive')
 OR ((SELECT name FROM acquisition_case_mode)='positive-a' AND ord<=7) OR ((SELECT name FROM acquisition_case_mode)='positive-b' AND ord>7)))
 OR (name LIKE 'bad-fix2-%' AND ((SELECT name FROM acquisition_case_mode) IN ('all','negative')
 OR ((SELECT name FROM acquisition_case_mode)='negative-a' AND ord<=14) OR ((SELECT name FROM acquisition_case_mode)='negative-b' AND ord>14)));
CREATE TEMP TABLE acquisition_case_one(name text NOT NULL);
INSERT INTO acquisition_case_one VALUES (:'fix2_case');
DELETE FROM acquisition_case_selection WHERE (SELECT name FROM acquisition_case_one)<>'' AND name<>(SELECT name FROM acquisition_case_one);
DO $$ BEGIN PERFORM acquisition_private.require(EXISTS(SELECT 1 FROM acquisition_case_selection),'Empty compatibility case selection'); END $$;
CREATE TEMP TABLE acquisition_case_phase(name text NOT NULL CHECK(name IN ('all','validator','protocol','upload','replay')));
INSERT INTO acquisition_case_phase VALUES (:'fix2_phase');
CREATE TEMP TABLE acquisition_observed_run(name text PRIMARY KEY,state jsonb NOT NULL);
CREATE TEMP TABLE acquisition_refusal_reasons(reason text PRIMARY KEY);
GRANT SELECT ON acquisition_case_mode,acquisition_case_selection,acquisition_case_phase TO authenticated;
GRANT SELECT ON acquisition_observed_state TO authenticated;
GRANT SELECT,INSERT ON acquisition_refusal_reasons,acquisition_observed_run TO authenticated;
-- Administrative observation/seeding is temporary, synthetic and rollback scoped.
-- The actual retry call below runs after this definer returns, as authenticated.
CREATE FUNCTION pg_temp.compatibility_observe(p_name text) RETURNS jsonb
LANGUAGE sql SECURITY DEFINER SET search_path='' AS $$
 SELECT jsonb_build_object(
 'floor',(SELECT to_jsonb(c) FROM acquisition_private.characters c WHERE c.user_id=auth.uid() AND c.character_id=f.body->>'p_character_id'),
 'operations',coalesce((SELECT jsonb_agg(to_jsonb(o) ORDER BY o.operation_id) FROM acquisition_private.operations o WHERE o.user_id=auth.uid() AND o.character_id=f.body->>'p_character_id'),'[]'::jsonb),
 'archives',coalesce((SELECT jsonb_agg(to_jsonb(a) ORDER BY a.archive_id) FROM acquisition_private.archives a WHERE a.user_id=auth.uid() AND a.character_id=f.body->>'p_character_id'),'[]'::jsonb),
 'publicRow',(SELECT to_jsonb(c) FROM public.web_characters c WHERE c.user_id=auth.uid() AND c.character_id=f.body->>'p_character_id'),
 'history',coalesce((SELECT jsonb_agg(to_jsonb(h) ORDER BY h.id) FROM public.sync_history h WHERE h.user_id=auth.uid() AND h.character_id=f.body->>'p_character_id'),'[]'::jsonb))
 FROM pg_temp.acquisition_fixtures f WHERE f.name=p_name
$$;
CREATE FUNCTION pg_temp.compatibility_seed_replay(p_name text) RETURNS void
LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$ DECLARE observed jsonb; f record; entries json; receipts json; item jsonb; entry json; receipt json; BEGIN
 SELECT * INTO STRICT f FROM pg_temp.acquisition_fixtures WHERE name=p_name;
 SELECT state INTO STRICT observed FROM pg_temp.acquisition_observed_state WHERE name=p_name;
 PERFORM acquisition_private.require(observed->'acknowledgement'=jsonb_build_object('status','ack','operationId',f.body->'p_operation_ids'->>0,'payloadCanonical',f.canonical,'serverRevision','1') AND observed->'readReply'=jsonb_build_object('status','envelope','payloadCanonical',f.canonical,'serverRevision','1','minWriterVersion',2),'Observed upload/reader identity differs from fixture');
 PERFORM acquisition_private.require(observed->'observed'->'floor'->>'user_id'=auth.uid()::text AND observed->'observed'->'floor'->>'character_id'=f.body->>'p_character_id' AND observed->'observed'->'floor'->>'envelope_canonical'=f.canonical AND observed->'observed'->'floor'->>'server_revision'='1' AND observed->'observed'->'floor'->>'min_writer_version'='2' AND observed->'observed'->'floor'->>'deleted'='false' AND f.body->>'p_expected_revision'='0' AND observed->'observed'->'archives'='[]'::jsonb,'Observed committed floor/scope differs');
 entries:=acquisition_private.field(acquisition_private.field(f.canonical::json,'ledger'),'entries'); receipts:=acquisition_private.field(f.canonical::json,'receipts');
 PERFORM acquisition_private.require(jsonb_array_length(observed->'observed'->'operations')=json_array_length(entries),'Observed operation completeness differs');
 FOR item IN SELECT value FROM jsonb_array_elements(observed->'observed'->'operations') LOOP
  SELECT value INTO entry FROM json_array_elements(entries) WHERE acquisition_private.field_text(value,'operationId')=item->>'operation_id';
  SELECT value INTO receipt FROM json_array_elements(receipts) WHERE acquisition_private.field_text(value,'operationId')=item->>'operation_id';
  PERFORM acquisition_private.require(item->>'user_id'=auth.uid()::text AND item->>'character_id'=f.body->>'p_character_id' AND item->>'entry_canonical'=entry::text AND item->>'receipt_identity'=acquisition_private.remove_field(receipt,'status')::text AND item->'resolution'='null'::jsonb,'Observed receipt/body/scope differs from accepted source');
  IF item->>'operation_id'=f.body->'p_operation_ids'->>0 THEN PERFORM acquisition_private.require(item->>'payload_canonical'=f.canonical AND item->'acknowledgement'=observed->'acknowledgement','Observed tip acknowledgement differs'); END IF;
 END LOOP;
 PERFORM acquisition_private.require(observed->'observed'->'publicRow'->>'user_id'=auth.uid()::text AND observed->'observed'->'publicRow'->>'character_id'=f.body->>'p_character_id' AND observed->'observed'->'publicRow'->>'revision'='1' AND observed->'observed'->'publicRow'->'data'=jsonb_build_object('characterId',f.body->>'p_character_id','formatVersion',1,'minReaderVersion',2),'Observed application marker/scope differs');
 INSERT INTO acquisition_private.characters SELECT * FROM jsonb_populate_record(NULL::acquisition_private.characters,observed->'observed'->'floor');
 INSERT INTO acquisition_private.operations SELECT * FROM jsonb_populate_recordset(NULL::acquisition_private.operations,observed->'observed'->'operations');
 INSERT INTO public.web_characters SELECT * FROM jsonb_populate_record(NULL::public.web_characters,observed->'observed'->'publicRow');
 INSERT INTO public.sync_history SELECT * FROM jsonb_populate_recordset(NULL::public.sync_history,observed->'observed'->'history');
 PERFORM acquisition_private.require(pg_temp.compatibility_observe(p_name)=observed->'observed','Seeded committed rows/receipts differ from exact observed upload');
END $$;
CREATE FUNCTION pg_temp.compatibility_replay(p_name text) RETURNS jsonb
LANGUAGE plpgsql AS $$ DECLARE observed jsonb; f record; before_state jsonb; reply jsonb; got jsonb; BEGIN
 SELECT * INTO STRICT f FROM acquisition_fixtures WHERE name=p_name;
 SELECT state INTO STRICT observed FROM acquisition_observed_state WHERE name=p_name;
 PERFORM pg_temp.compatibility_seed_replay(p_name);
 before_state:=pg_temp.compatibility_observe(p_name);
 reply:=public.acquisition_sync_v2(f.body->>'p_character_id',0,f.body->'p_operation_ids',f.body->'p_envelope',2,NULL);
 got:=public.acquisition_get_v2(f.body->>'p_character_id',2);
 RETURN jsonb_build_object('seedExact',before_state=observed->'observed','staleRetryExact',reply=observed->'acknowledgement','readExact',got=observed->'readReply','nonmutation',pg_temp.compatibility_observe(p_name)=before_state);
END $$;
CREATE FUNCTION pg_temp.compatibility_roundtrip(p_name text,p_retry boolean DEFAULT true) RETURNS jsonb LANGUAGE plpgsql AS $$
DECLARE f record; a jsonb; retry jsonb; got jsonb; BEGIN
 SELECT * INTO STRICT f FROM acquisition_fixtures WHERE name=p_name;
 a:=public.acquisition_sync_v2(f.body->>'p_character_id',0,f.body->'p_operation_ids',f.body->'p_envelope',2,NULL);
 IF p_retry THEN retry:=public.acquisition_sync_v2(f.body->>'p_character_id',0,f.body->'p_operation_ids',f.body->'p_envelope',2,NULL); END IF;
 got:=public.acquisition_get_v2(f.body->>'p_character_id',2);
 RETURN jsonb_build_object('ack',a->>'status'='ack' AND a->>'payloadCanonical'=f.canonical AND a->>'serverRevision'='1' AND a->>'operationId'=f.body->'p_operation_ids'->>0,
 'acknowledgement',a,'readReply',got,'observed',CASE WHEN NOT p_retry THEN pg_temp.compatibility_observe(p_name) ELSE NULL END,
 'retry',a=retry,'read',got->>'status'='envelope' AND got->>'payloadCanonical'=f.canonical AND got->>'serverRevision'='1','response',a->>'reason');
END $$;
DO $$ DECLARE f record; accepted boolean; reason text; BEGIN
 FOR f IN SELECT name,canonical FROM acquisition_fixtures WHERE name IN(SELECT name FROM acquisition_case_selection) AND (SELECT name FROM acquisition_case_phase) IN ('all','validator') ORDER BY name LOOP
  accepted:=false;reason:=NULL;
  BEGIN PERFORM acquisition_private.validate_wire(f.canonical); accepted:=true; EXCEPTION WHEN OTHERS THEN reason:=SQLERRM; END;
  PERFORM pg_temp.check_test('legacy compatibility validator: '||f.name,accepted=(f.name LIKE 'fix2-%'),reason);
 END LOOP;
END $$;
SET LOCAL ROLE authenticated;
SELECT set_config('request.jwt.claim.sub','aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',true),set_config('request.jwt.claim.role','authenticated',true),set_config('request.jwt.claims','',true);
DO $$ DECLARE f record; v jsonb; r jsonb; BEGIN
 FOR f IN SELECT name,body FROM acquisition_fixtures WHERE name IN(SELECT name FROM acquisition_case_selection) AND (SELECT name FROM acquisition_case_phase) IN ('all','protocol','upload','replay') ORDER BY name LOOP
  IF (SELECT name FROM acquisition_case_phase)='replay' THEN
   v:=pg_temp.attempt(format('SELECT pg_temp.compatibility_replay(%L)',f.name));
   PERFORM pg_temp.check_test('observed seeded exact stale retry/read/nonmutation: '||f.name,v->'result'->>'seedExact'='true' AND v->'result'->>'staleRetryExact'='true' AND v->'result'->>'readExact'='true' AND v->'result'->>'nonmutation'='true',v::text);
  ELSIF f.name LIKE 'fix2-%' THEN
   v:=pg_temp.attempt(format('SELECT pg_temp.compatibility_roundtrip(%L,%L)',f.name,(SELECT name FROM acquisition_case_phase)<>'upload'));
   IF (SELECT name FROM acquisition_case_phase)='upload' AND v->'result'->>'ack'='true' THEN INSERT INTO acquisition_observed_run VALUES(f.name,v->'result'); END IF;
   PERFORM pg_temp.check_test('public legacy exact ACK/'||CASE WHEN (SELECT name FROM acquisition_case_phase)='upload' THEN '' ELSE 'retry/' END||'read: '||f.name,v->'result'->>'ack'='true' AND ((SELECT name FROM acquisition_case_phase)='upload' OR v->'result'->>'retry'='true') AND v->'result'->>'read'='true',v::text);
  ELSE
   r:=public.acquisition_sync_v2(f.body->>'p_character_id',0,f.body->'p_operation_ids',f.body->'p_envelope',2,NULL);
   IF r->>'status'='identityConflict' THEN INSERT INTO acquisition_refusal_reasons VALUES(r->>'reason') ON CONFLICT DO NOTHING; END IF;
   PERFORM pg_temp.check_test('legacy malformed non-ack: '||f.name,r->>'status'='identityConflict',r::text);
  END IF;
 END LOOP;
END $$;
RESET ROLE;
SELECT pg_temp.check_test('legacy compatibility no durable partial writes',NOT EXISTS(SELECT 1 FROM acquisition_private.characters) AND NOT EXISTS(SELECT 1 FROM acquisition_private.operations) AND NOT EXISTS(SELECT 1 FROM acquisition_private.archives) AND NOT EXISTS(SELECT 1 FROM public.web_characters) AND NOT EXISTS(SELECT 1 FROM public.sync_history));
TABLE acquisition_results;
SELECT 'OBSERVED_ACCEPTED_STATE:'||name||':'||state::text FROM acquisition_observed_run WHERE (SELECT name FROM acquisition_case_phase)='upload';
SELECT 'REFUSAL_REASON:'||reason FROM acquisition_refusal_reasons ORDER BY reason;
DO $$ DECLARE n integer; BEGIN
 SELECT count(*) INTO n FROM acquisition_results WHERE NOT passed;
 IF n>0 THEN RAISE EXCEPTION 'legacy compatibility assertions failed: %',n; END IF;
 RAISE NOTICE 'legacy compatibility assertions PASS: %',(SELECT count(*) FROM acquisition_results);
END $$;
ROLLBACK;
