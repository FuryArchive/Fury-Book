-- Rollback-scoped helpers and synthetic owners for SQL assertions.
CREATE TEMP TABLE acquisition_results (name text PRIMARY KEY, passed boolean NOT NULL, detail text);
GRANT INSERT, SELECT ON acquisition_results TO authenticated;
CREATE FUNCTION pg_temp.check_test(p_name text, p_pass boolean, p_detail text DEFAULT NULL) RETURNS void
LANGUAGE plpgsql AS $$ BEGIN
 INSERT INTO acquisition_results VALUES (p_name, coalesce(p_pass, false), left(p_detail,400));
 RAISE NOTICE '%: %', CASE WHEN coalesce(p_pass,false) THEN 'PASS' ELSE 'FAIL' END, p_name;
END $$;
-- Each attempted writer gets its own subtransaction, so a RED acceptance cannot spoil other cases.
CREATE FUNCTION pg_temp.attempt(p_sql text) RETURNS jsonb LANGUAGE plpgsql AS $$
DECLARE v_result jsonb; v_error text; v_state text;
BEGIN
 BEGIN
  EXECUTE p_sql INTO v_result;
  RAISE EXCEPTION USING ERRCODE = 'ZX001', MESSAGE = 'test-only rollback of successful attempt';
 EXCEPTION WHEN SQLSTATE 'ZX001' THEN NULL;
 WHEN OTHERS THEN GET STACKED DIAGNOSTICS v_error = MESSAGE_TEXT, v_state = RETURNED_SQLSTATE;
 END;
 RETURN jsonb_build_object('result',v_result,'error',v_error,'sqlstate',v_state);
END $$;
CREATE FUNCTION pg_temp.blocked(p_name text, p_sql text) RETURNS void LANGUAGE plpgsql AS $$
DECLARE v jsonb := pg_temp.attempt(p_sql);
BEGIN
 PERFORM pg_temp.check_test(p_name, (v->>'sqlstate' IN ('42501','P0001','22023','23514')) OR
  v->'result'->>'status' = 'identityConflict', v::text);
END $$;
INSERT INTO auth.users (id,email,raw_user_meta_data) VALUES
 ('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa','acquisition-a@example.invalid','{"nickname":"Fixture A"}'),
 ('bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb','acquisition-b@example.invalid','{"nickname":"Fixture B"}');
