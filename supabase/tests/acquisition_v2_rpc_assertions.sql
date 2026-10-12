CREATE FUNCTION pg_temp.push_fixture(p_name text, p_expected bigint DEFAULT NULL) RETURNS jsonb LANGUAGE plpgsql AS $$
DECLARE f jsonb; r jsonb;
BEGIN
 SELECT body INTO STRICT f FROM acquisition_fixtures WHERE name=p_name;
 EXECUTE 'SELECT public.acquisition_sync_v2($1,$2,$3,$4,$5,$6)' INTO r USING f->>'p_character_id',
  coalesce(p_expected,(f->>'p_expected_revision')::bigint),f->'p_operation_ids',f->'p_envelope',
  (f->>'p_writer_version')::integer,nullif(f->'p_resolution','null'::jsonb);
 RETURN r;
END $$;
DO $$ DECLARE r jsonb; f jsonb; v jsonb; e jsonb; names text[] := ARRAY[
 'duplicate exact operation returns original acknowledgement','operation reused with different payload refused',
 'stale V2 revision conflicts with exact canonical branch','ordinary request cannot carry adoption',
 'resolution cannot masquerade as ordinary operation','writer declaration alone is not ledger evidence',
 'missing receipt rejected','broken receipt chain rejected','financial receipt mismatch rejected',
 'local metadata rejected','unknown required format rejected','null V2 revision refused',
 'new reader returns exact canonical envelope','new reader future version refused','V2 owner isolation',
 'genuine divergent server branch accepted','genuine local choice binds archive and server revision',
 'resolution retry is idempotent','missing proof rejected','forged proof identity rejected',
 'cyclic proof closure rejected','forged archive rejected','reused immutable prefix refused',
 'genuine post-adoption ordinary write accepted','second adoption proof closure accepted',
 'copy/import producer identity accepted','durable tombstone persists after delete',
 'old recreate cannot erase durable floor','V2 tombstone recreate is refused','direct floor mutation denied']; n text;
BEGIN
 IF to_regprocedure('public.acquisition_sync_v2(text,bigint,jsonb,jsonb,integer,jsonb)') IS NULL THEN
  FOREACH n IN ARRAY names LOOP PERFORM pg_temp.check_test(n,false,'V2 RPC missing on baseline'); END LOOP;
  RETURN;
 END IF;
 r:=pg_temp.push_fixture('op1');
 PERFORM pg_temp.check_test(names[1],r->>'status'='ack' AND r->>'serverRevision'='1' AND
  r->>'payloadCanonical'=(SELECT canonical FROM acquisition_fixtures WHERE name='op1'),r::text);
 SELECT body INTO f FROM acquisition_fixtures WHERE name='op1';
 e:=(SELECT body->'p_envelope' FROM acquisition_fixtures WHERE name='op1-reused-valid');
 r:=public.acquisition_sync_v2('hero',0,'["op1"]',e,2,NULL);
 PERFORM pg_temp.check_test(names[2],r->>'status'='identityConflict',r::text);
 r:=pg_temp.push_fixture('op2',0);
 PERFORM pg_temp.check_test(names[3],r=jsonb_build_object('status','conflict','serverRevision','1',
  'serverCanonical',(SELECT canonical FROM acquisition_fixtures WHERE name='op1')),r::text);
 SELECT body INTO f FROM acquisition_fixtures WHERE name='choose-local';
 r:=public.acquisition_sync_v2('hero',1,f->'p_operation_ids',f->'p_envelope',2,NULL);
 PERFORM pg_temp.check_test(names[4],r->>'status'='identityConflict',r::text);
 SELECT body INTO f FROM acquisition_fixtures WHERE name='op2';
 r:=public.acquisition_sync_v2('hero',1,f->'p_operation_ids',f->'p_envelope',2,'{"choice":"Local"}');
 PERFORM pg_temp.check_test(names[5],r->>'status'='identityConflict',r::text);
 r:=public.acquisition_sync_v2('hero',1,'["op2"]','{"formatVersion":1,"characterId":"hero"}',2,NULL);
 PERFORM pg_temp.check_test(names[6],r->>'status'='identityConflict',r::text);
 r:=public.acquisition_sync_v2('hero',1,f->'p_operation_ids',(SELECT body->'p_envelope' FROM acquisition_fixtures WHERE name='bad-receipts'),2,NULL);
 PERFORM pg_temp.check_test(names[7],r->>'status'='identityConflict',r::text);
 r:=public.acquisition_sync_v2('hero',1,f->'p_operation_ids',(SELECT body->'p_envelope' FROM acquisition_fixtures WHERE name='bad-chain'),2,NULL);
 PERFORM pg_temp.check_test(names[8],r->>'status'='identityConflict',r::text);
 r:=public.acquisition_sync_v2('hero',1,f->'p_operation_ids',(SELECT body->'p_envelope' FROM acquisition_fixtures WHERE name='bad-finance'),2,NULL);
 PERFORM pg_temp.check_test(names[9],r->>'status'='identityConflict',r::text);
 r:=public.acquisition_sync_v2('hero',1,f->'p_operation_ids',(SELECT body->'p_envelope' FROM acquisition_fixtures WHERE name='bad-local'),2,NULL);
 PERFORM pg_temp.check_test(names[10],r->>'status'='identityConflict',r::text);
 r:=public.acquisition_sync_v2('hero',1,f->'p_operation_ids',(SELECT body->'p_envelope' FROM acquisition_fixtures WHERE name='bad-format'),2,NULL);
 PERFORM pg_temp.check_test(names[11],r->>'status'='identityConflict',r::text);
 r:=public.acquisition_sync_v2('hero',NULL,f->'p_operation_ids',f->'p_envelope',2,NULL);
 PERFORM pg_temp.check_test(names[12],r->>'status'='identityConflict',r::text);
 r:=public.acquisition_get_v2('hero',2);
 PERFORM pg_temp.check_test(names[13],r->>'status'='envelope' AND r->>'payloadCanonical'=(SELECT canonical FROM acquisition_fixtures WHERE name='op1') AND r->>'serverRevision'='1',r::text);
 r:=public.acquisition_get_v2('hero',99);
 PERFORM pg_temp.check_test(names[14],r->>'status'='identityConflict',r::text);
 PERFORM set_config('request.jwt.claim.sub','bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb',true);
 r:=public.acquisition_get_v2('hero',2);
 PERFORM pg_temp.check_test(names[15],r->>'status'='missing',r::text);
 PERFORM set_config('request.jwt.claim.sub','aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',true);
 r:=pg_temp.push_fixture('server2');
 PERFORM pg_temp.check_test(names[16],r->>'status'='ack' AND r->>'serverRevision'='2',r::text);
 r:=pg_temp.push_fixture('choose-local');
 PERFORM pg_temp.check_test(names[17],r->>'status'='ack' AND r->>'serverRevision'='3' AND
  r->>'payloadCanonical'=(SELECT canonical FROM acquisition_fixtures WHERE name='choose-local'),r::text);
 r:=pg_temp.push_fixture('choose-local');
 PERFORM pg_temp.check_test(names[18],r->>'status'='ack' AND r->>'serverRevision'='3',r::text);
 SELECT body INTO f FROM acquisition_fixtures WHERE name='second-choice';
 r:=public.acquisition_sync_v2('hero',3,f->'p_operation_ids',(SELECT body->'p_envelope' FROM acquisition_fixtures WHERE name='bad-missing-proof'),2,f->'p_resolution');
 PERFORM pg_temp.check_test(names[19],r->>'status'='identityConflict',r::text);
 e:=(SELECT body->'p_envelope' FROM acquisition_fixtures WHERE name='bad-proof-id');
 r:=public.acquisition_sync_v2('hero',3,f->'p_operation_ids',e,2,f->'p_resolution');
 PERFORM pg_temp.check_test(names[20],r->>'status'='identityConflict',r::text);
 e:=(SELECT body->'p_envelope' FROM acquisition_fixtures WHERE name='bad-cycle');
 r:=public.acquisition_sync_v2('hero',3,f->'p_operation_ids',e,2,f->'p_resolution');
 PERFORM pg_temp.check_test(names[21],r->>'status'='identityConflict',r::text);
 r:=public.acquisition_sync_v2('hero',3,f->'p_operation_ids',f->'p_envelope',2,jsonb_set(f->'p_resolution','{archiveId}','"forged"'));
 PERFORM pg_temp.check_test(names[22],r->>'status'='identityConflict',r::text);
 SELECT body INTO f FROM acquisition_fixtures WHERE name='after-adoption';
 e:=(SELECT body->'p_envelope' FROM acquisition_fixtures WHERE name='bad-prefix');
 r:=public.acquisition_sync_v2('hero',3,f->'p_operation_ids',e,2,NULL);
 PERFORM pg_temp.check_test(names[23],r->>'status'='identityConflict',r::text);
 r:=pg_temp.push_fixture('remote-after-adoption');
 PERFORM pg_temp.check_test(names[24],r->>'status'='ack' AND r->>'serverRevision'='4',r::text);
 r:=pg_temp.push_fixture('second-choice');
 PERFORM pg_temp.check_test(names[25],r->>'status'='ack' AND r->>'serverRevision'='5',r::text);
 r:=pg_temp.push_fixture('copy');
 PERFORM pg_temp.check_test(names[26],r->>'status'='ack' AND r->>'serverRevision'='1' AND r->>'payloadCanonical'=(SELECT canonical FROM acquisition_fixtures WHERE name='copy'),r::text);
 r:=public.acquisition_delete_v2('hero',4,2);
 PERFORM pg_temp.check_test('V2 delete stale revision returns exact immutable conflict',r->>'status'='conflict' AND r->>'serverRevision'='5' AND r->>'serverCanonical'=(SELECT canonical FROM acquisition_fixtures WHERE name='second-choice'),r::text);
 r:=public.acquisition_delete_v2('hero',NULL,2);
 PERFORM pg_temp.check_test('V2 delete null revision never skips CAS',r->>'status'='identityConflict',r::text);
 r:=public.acquisition_delete_v2('hero',5,1);
 PERFORM pg_temp.check_test('V2 delete old writer refused',r->>'status'='identityConflict',r::text);
 r:=public.acquisition_delete_v2('hero',5,2);
 PERFORM pg_temp.check_test(names[27],r->>'status'='deleted' AND public.acquisition_get_v2('hero',2)->>'status'='deleted',r::text);
 PERFORM pg_temp.blocked(names[28],$q$SELECT public.save_web_character('hero','{"id":"hero"}','{}',0,'old')$q$);
 SELECT body INTO f FROM acquisition_fixtures WHERE name='op2';
 r:=public.acquisition_sync_v2('hero',6,f->'p_operation_ids',f->'p_envelope',2,NULL);
 PERFORM pg_temp.check_test(names[29],r->>'status'='identityConflict',r::text);
 PERFORM pg_temp.blocked(names[30],$q$WITH x AS (DELETE FROM acquisition_private.characters RETURNING 1) SELECT to_jsonb(count(*)) FROM x$q$);
 PERFORM set_config('request.jwt.claim.sub','bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb',true);
 r:=pg_temp.push_fixture('op1');
 PERFORM pg_temp.check_test('operation uniqueness is scoped to owner and character',r->>'status'='ack' AND r->>'serverRevision'='1',r::text);
 PERFORM set_config('request.jwt.claim.sub','aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',true);
 r:=public.acquisition_get_v2('hero',2);
 PERFORM pg_temp.check_test('other owner recreation does not remove A tombstone',r->>'status'='deleted' AND r->>'serverRevision'='6',r::text);
 PERFORM pg_temp.check_test('direct table privileges cannot truncate or bypass receipts',
  NOT has_table_privilege(current_user,'public.web_characters','INSERT,UPDATE,DELETE,TRUNCATE') AND
  NOT has_table_privilege(current_user,'public.user_state','INSERT,UPDATE,DELETE,TRUNCATE') AND
  NOT has_table_privilege(current_user,'public.sync_history','INSERT,UPDATE,DELETE,TRUNCATE') AND
  NOT has_table_privilege(current_user,'public.web_state','INSERT,UPDATE,DELETE,TRUNCATE') AND
  NOT has_table_privilege(current_user,'public.profiles','INSERT,UPDATE,DELETE,TRUNCATE'));
 PERFORM pg_temp.check_test('anonymous cannot execute any new public RPC',
  NOT has_function_privilege('anon','public.acquisition_sync_v2(text,bigint,jsonb,jsonb,integer,jsonb)','EXECUTE') AND
  NOT has_function_privilege('anon','public.acquisition_get_v2(text,integer)','EXECUTE') AND
  NOT has_function_privilege('anon','public.acquisition_delete_v2(text,bigint,integer)','EXECUTE') AND
  NOT has_function_privilege('anon','public.campaign_get_dashboard_v2(uuid,integer)','EXECUTE'));
END $$;
