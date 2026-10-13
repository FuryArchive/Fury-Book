-- Local/staging prerequisite only. This migration does not enable production cloudLedgerV2.
-- Exact wire text is carried inside the JSONB DTO. Never cast the inner envelope to JSONB:
-- JSONB loses numeric lexemes and rejects valid UTF-16 lone-surrogate escapes.
CREATE SCHEMA acquisition_private AUTHORIZATION postgres;
REVOKE ALL ON SCHEMA acquisition_private FROM PUBLIC, anon, authenticated, service_role;
CREATE TYPE acquisition_private.token AS (canonical text, next_position integer, sort_key bytea);

CREATE FUNCTION acquisition_private.require(p_ok boolean, p_reason text) RETURNS void
LANGUAGE plpgsql IMMUTABLE SET search_path='' AS $$ BEGIN
 IF p_ok IS DISTINCT FROM true THEN RAISE EXCEPTION USING ERRCODE='22023', MESSAGE=p_reason; END IF;
END $$;
CREATE FUNCTION acquisition_private.unit_quote(p integer) RETURNS text
LANGUAGE sql IMMUTABLE STRICT SET search_path='' AS $$ SELECT CASE p
 WHEN 34 THEN '\"' WHEN 92 THEN '\\' WHEN 8 THEN '\b' WHEN 12 THEN '\f'
 WHEN 10 THEN '\n' WHEN 13 THEN '\r' WHEN 9 THEN '\t'
 ELSE CASE WHEN p<32 THEN '\u'||lpad(to_hex(p),4,'0')
 WHEN p BETWEEN 55296 AND 57343 THEN '\u'||upper(lpad(to_hex(p),4,'0')) ELSE chr(p) END END $$;
-- Strict lexical canonicalizer matching T15: UTF-16 key order/escaping, original number tokens,
-- no duplicate decoded keys, bounded nesting. It never materializes arbitrary strings as UTF-8.
CREATE FUNCTION acquisition_private.parse_chars(p_chars text[], p_position integer DEFAULT 1, p_depth integer DEFAULT 0)
RETURNS acquisition_private.token LANGUAGE plpgsql IMMUTABLE STRICT SET search_path='' AS $$
DECLARE i integer:=p_position; n integer:=cardinality(p_chars); c text; out text:=''; k acquisition_private.token;
 v acquisition_private.token; keys bytea[]:=ARRAY[]::bytea[]; vals text[]:=ARRAY[]::text[];
 names text[]:=ARRAY[]::text[]; b bytea:=''::bytea; cp integer; lo integer; escaped boolean; digits text; j integer; candidate text;
BEGIN
 PERFORM acquisition_private.require(p_depth<=64,'Wire nesting limit exceeded');
 WHILE i<=n AND p_chars[i] IN (' ',chr(9),chr(10),chr(13)) LOOP i:=i+1; END LOOP;
 c:=p_chars[i];
 IF c='"' THEN
  i:=i+1; out:='"';
  LOOP
   PERFORM acquisition_private.require(i<=n,'Unterminated JSON string');
   c:=p_chars[i]; i:=i+1;
   IF c='"' THEN RETURN (out||'"',i,b)::acquisition_private.token; END IF;
   escaped:=c='\';
   IF escaped THEN
    c:=p_chars[i]; i:=i+1;
    IF c='u' THEN
     digits:=array_to_string(p_chars[i:i+3],''); PERFORM acquisition_private.require(digits~'^[0-9A-Fa-f]{4}$','Invalid unicode escape');
     cp:=('x'||digits)::bit(16)::integer; i:=i+4;
    ELSE
     cp:=CASE c WHEN '"' THEN 34 WHEN '\' THEN 92 WHEN '/' THEN 47 WHEN 'b' THEN 8
      WHEN 'f' THEN 12 WHEN 'n' THEN 10 WHEN 'r' THEN 13 WHEN 't' THEN 9 ELSE NULL END;
     PERFORM acquisition_private.require(cp IS NOT NULL,'Invalid JSON escape');
    END IF;
   ELSE
    cp:=ascii(c); PERFORM acquisition_private.require(cp>=32,'Unescaped JSON control');
   END IF;
   IF cp>65535 THEN
    lo:=56320+((cp-65536)%1024); cp:=55296+((cp-65536)/1024);
    out:=out||acquisition_private.unit_quote(cp)||acquisition_private.unit_quote(lo);
    b:=b||decode(lpad(to_hex(cp),4,'0')||lpad(to_hex(lo),4,'0'),'hex');
   ELSE
    out:=out||acquisition_private.unit_quote(cp); b:=b||decode(lpad(to_hex(cp),4,'0'),'hex');
   END IF;
  END LOOP;
 ELSIF c='{' THEN
  i:=i+1;
  WHILE i<=n AND p_chars[i] IN (' ',chr(9),chr(10),chr(13)) LOOP i:=i+1; END LOOP;
  IF p_chars[i]='}' THEN RETURN ('{}',i+1,NULL)::acquisition_private.token; END IF;
  LOOP
   PERFORM acquisition_private.require(p_chars[i]='"','Object requires string key');
   k:=acquisition_private.parse_chars(p_chars,i,p_depth+1); i:=k.next_position;
   PERFORM acquisition_private.require(NOT k.sort_key=ANY(keys),'Duplicate decoded JSON key');
   keys:=array_append(keys,k.sort_key); names:=array_append(names,k.canonical);
   WHILE i<=n AND p_chars[i] IN (' ',chr(9),chr(10),chr(13)) LOOP i:=i+1; END LOOP;
   PERFORM acquisition_private.require(p_chars[i]=':','Object requires colon');
   v:=acquisition_private.parse_chars(p_chars,i+1,p_depth+1); i:=v.next_position; vals:=array_append(vals,v.canonical);
   WHILE i<=n AND p_chars[i] IN (' ',chr(9),chr(10),chr(13)) LOOP i:=i+1; END LOOP;
   c:=p_chars[i]; i:=i+1;
   IF c='}' THEN EXIT; END IF;
   PERFORM acquisition_private.require(c=',','Object requires comma');
   WHILE i<=n AND p_chars[i] IN (' ',chr(9),chr(10),chr(13)) LOOP i:=i+1; END LOOP;
  END LOOP;
  SELECT '{'||string_agg(names[x]||':'||vals[x],',' ORDER BY keys[x])||'}' INTO out FROM generate_subscripts(keys,1) x;
  RETURN (out,i,NULL)::acquisition_private.token;
 ELSIF c='[' THEN
  i:=i+1; out:='[';
  WHILE i<=n AND p_chars[i] IN (' ',chr(9),chr(10),chr(13)) LOOP i:=i+1; END LOOP;
  IF p_chars[i]=']' THEN RETURN ('[]',i+1,NULL)::acquisition_private.token; END IF;
  LOOP
   v:=acquisition_private.parse_chars(p_chars,i,p_depth+1); out:=out||CASE WHEN out='[' THEN '' ELSE ',' END||v.canonical; i:=v.next_position;
   WHILE i<=n AND p_chars[i] IN (' ',chr(9),chr(10),chr(13)) LOOP i:=i+1; END LOOP;
   c:=p_chars[i]; i:=i+1;
   IF c=']' THEN RETURN (out||']',i,NULL)::acquisition_private.token; END IF;
   PERFORM acquisition_private.require(c=',','Array requires comma');
  END LOOP;
 ELSE
  j:=i;
  IF p_chars[i] IN ('t','f','n') THEN
   candidate:=array_to_string(p_chars[i:least(n,i+4)],'');
  ELSE
   WHILE j<=n AND p_chars[j]~'^[0-9eE+.\-]$' LOOP j:=j+1; END LOOP;
   candidate:=array_to_string(p_chars[i:j-1],'');
  END IF;
  out:=substring(candidate FROM '^(-?(0|[1-9][0-9]*)(\.[0-9]+)?([eE][+-]?[0-9]+)?|true|false|null)');
  PERFORM acquisition_private.require(out IS NOT NULL,'Invalid JSON value');
  RETURN (out,i+length(out),NULL)::acquisition_private.token;
 END IF;
END $$;
CREATE FUNCTION acquisition_private.parse(p_raw text,p_position integer DEFAULT 1,p_depth integer DEFAULT 0)
RETURNS acquisition_private.token LANGUAGE sql IMMUTABLE STRICT SET search_path='' AS $$
 SELECT acquisition_private.parse_chars(regexp_split_to_array(p_raw,''),p_position,p_depth) $$;
CREATE FUNCTION acquisition_private.canonical(p_raw text) RETURNS text
LANGUAGE plpgsql IMMUTABLE STRICT SET search_path='' AS $$ DECLARE t acquisition_private.token; BEGIN
 t:=acquisition_private.parse(p_raw);
 PERFORM acquisition_private.require(substr(p_raw,t.next_position)~'^[ \t\r\n]*$','Trailing JSON content');
 RETURN t.canonical;
END $$;
CREATE FUNCTION acquisition_private.wire_json(p_raw text) RETURNS json
LANGUAGE plpgsql IMMUTABLE STRICT SET search_path='' AS $$ BEGIN
 PERFORM acquisition_private.require(octet_length(p_raw)<=8388608,'Wire payload exceeds bounded server limit');
 PERFORM acquisition_private.require(p_raw=acquisition_private.canonical(p_raw),'Expected exact canonical wire');
 RETURN p_raw::json;
END $$;
-- PostgreSQL json field/key access still rejects nested lone surrogates while json's
-- type/array functions preserve them. Use native access where supported; strict lexical
-- fallback retains raw values, never substitutes Unicode or serializes number values.
CREATE FUNCTION acquisition_private.object_members(p json) RETURNS TABLE(key text,value json)
LANGUAGE plpgsql IMMUTABLE SET search_path='' AS $$
DECLARE raw text:=p::text; chars text[]; i integer:=2; k acquisition_private.token; v acquisition_private.token; start integer;
BEGIN
 BEGIN RETURN QUERY SELECT x.key,x.value FROM pg_catalog.json_each(p) x; RETURN;
 EXCEPTION WHEN invalid_text_representation OR untranslatable_character THEN NULL; END;
 PERFORM acquisition_private.require(json_typeof(p)='object','Expected object');
 chars:=regexp_split_to_array(raw,''); IF chars[i]='}' THEN RETURN; END IF;
 LOOP
  k:=acquisition_private.parse_chars(chars,i); i:=k.next_position;
  PERFORM acquisition_private.require(chars[i]=':','Object field colon'); start:=i+1;
  v:=acquisition_private.parse_chars(chars,start); i:=v.next_position;
  key:=k.canonical::json#>>'{}'; value:=array_to_string(chars[start:i-1],'')::json; RETURN NEXT;
  IF chars[i]='}' THEN RETURN; END IF;
  PERFORM acquisition_private.require(chars[i]=',','Object field comma'); i:=i+1;
 END LOOP;
END $$;
CREATE FUNCTION acquisition_private.object_raw_members(p json) RETURNS TABLE(key json,value json)
LANGUAGE plpgsql IMMUTABLE SET search_path='' AS $$
DECLARE chars text[]:=regexp_split_to_array(p::text,''); i integer:=2; k acquisition_private.token; v acquisition_private.token; start integer;
BEGIN
 PERFORM acquisition_private.require(json_typeof(p)='object','Expected object');
 IF chars[i]='}' THEN RETURN; END IF;
 LOOP
  k:=acquisition_private.parse_chars(chars,i); i:=k.next_position;
  PERFORM acquisition_private.require(chars[i]=':','Object field colon'); start:=i+1;
  v:=acquisition_private.parse_chars(chars,start); i:=v.next_position;
  key:=k.canonical::json; value:=array_to_string(chars[start:i-1],'')::json; RETURN NEXT;
  IF chars[i]='}' THEN RETURN; END IF;
  PERFORM acquisition_private.require(chars[i]=',','Object field comma'); i:=i+1;
 END LOOP;
END $$;
CREATE FUNCTION acquisition_private.field_raw(p json,p_key json) RETURNS json
LANGUAGE sql IMMUTABLE STRICT SET search_path='' AS $$
 SELECT value FROM acquisition_private.object_raw_members(p) WHERE key::text=p_key::text $$;
CREATE FUNCTION acquisition_private.field(p json,p_key text) RETURNS json
LANGUAGE plpgsql IMMUTABLE STRICT SET search_path='' AS $$ DECLARE v json; BEGIN
 BEGIN RETURN p OPERATOR(pg_catalog.->) p_key; EXCEPTION WHEN invalid_text_representation OR untranslatable_character THEN NULL; END;
 SELECT x.value INTO v FROM acquisition_private.object_raw_members(p) x WHERE x.key::text=to_json(p_key)::text; RETURN v;
END $$;
CREATE FUNCTION acquisition_private.element(p json,p_index integer) RETURNS json
LANGUAGE plpgsql IMMUTABLE STRICT SET search_path='' AS $$ DECLARE v json; idx integer:=p_index; BEGIN
 BEGIN RETURN p OPERATOR(pg_catalog.->) p_index; EXCEPTION WHEN invalid_text_representation OR untranslatable_character THEN NULL; END;
 IF idx<0 THEN idx:=json_array_length(p)+idx; END IF; IF idx<0 THEN RETURN NULL; END IF;
 SELECT x.value INTO v FROM json_array_elements(p) WITH ORDINALITY x(value,ord) WHERE ord=idx+1; RETURN v;
END $$;
CREATE FUNCTION acquisition_private.field_text(p json,p_key text) RETURNS text LANGUAGE sql IMMUTABLE STRICT SET search_path='' AS $$
 SELECT acquisition_private.field(p,p_key)#>>'{}' $$;
CREATE FUNCTION acquisition_private.element_text(p json,p_index integer) RETURNS text LANGUAGE sql IMMUTABLE STRICT SET search_path='' AS $$
 SELECT acquisition_private.element(p,p_index)#>>'{}' $$;
CREATE FUNCTION acquisition_private.field(p jsonb,p_key text) RETURNS jsonb LANGUAGE sql IMMUTABLE STRICT SET search_path='' AS $$ SELECT p OPERATOR(pg_catalog.->) p_key $$;
CREATE FUNCTION acquisition_private.element(p jsonb,p_index integer) RETURNS jsonb LANGUAGE sql IMMUTABLE STRICT SET search_path='' AS $$ SELECT p OPERATOR(pg_catalog.->) p_index $$;
CREATE FUNCTION acquisition_private.field_text(p jsonb,p_key text) RETURNS text LANGUAGE sql IMMUTABLE STRICT SET search_path='' AS $$ SELECT p OPERATOR(pg_catalog.->>) p_key $$;
CREATE FUNCTION acquisition_private.element_text(p jsonb,p_index integer) RETURNS text LANGUAGE sql IMMUTABLE STRICT SET search_path='' AS $$ SELECT p OPERATOR(pg_catalog.->>) p_index $$;
CREATE OPERATOR acquisition_private.-> (LEFTARG=json,RIGHTARG=text,FUNCTION=acquisition_private.field);
CREATE OPERATOR acquisition_private.-> (LEFTARG=json,RIGHTARG=integer,FUNCTION=acquisition_private.element);
CREATE OPERATOR acquisition_private.->> (LEFTARG=json,RIGHTARG=text,FUNCTION=acquisition_private.field_text);
CREATE OPERATOR acquisition_private.->> (LEFTARG=json,RIGHTARG=integer,FUNCTION=acquisition_private.element_text);
CREATE OPERATOR acquisition_private.-> (LEFTARG=jsonb,RIGHTARG=text,FUNCTION=acquisition_private.field);
CREATE OPERATOR acquisition_private.-> (LEFTARG=jsonb,RIGHTARG=integer,FUNCTION=acquisition_private.element);
CREATE OPERATOR acquisition_private.->> (LEFTARG=jsonb,RIGHTARG=text,FUNCTION=acquisition_private.field_text);
CREATE OPERATOR acquisition_private.->> (LEFTARG=jsonb,RIGHTARG=integer,FUNCTION=acquisition_private.element_text);

CREATE FUNCTION acquisition_private.keys(p json,p_required text[],p_optional text[] DEFAULT ARRAY[]::text[]) RETURNS void
LANGUAGE plpgsql IMMUTABLE SET search_path='' AS $$ DECLARE k text; BEGIN
 PERFORM acquisition_private.require(json_typeof(p)='object','Expected object');
 FOR k IN SELECT key FROM acquisition_private.object_members(p) LOOP
  PERFORM acquisition_private.require(k=ANY(p_required||p_optional),'Unknown required field: '||k);
 END LOOP;
 FOREACH k IN ARRAY p_required LOOP PERFORM acquisition_private.require(p OPERATOR(acquisition_private.->) k IS NOT NULL,'Missing required field: '||k); END LOOP;
END $$;
CREATE FUNCTION acquisition_private.text(p json) RETURNS text LANGUAGE plpgsql IMMUTABLE SET search_path='' AS $$ BEGIN
 PERFORM acquisition_private.require(json_typeof(p)='string','Expected string'); RETURN p#>>'{}';
END $$;
CREATE FUNCTION acquisition_private.trim_string(p json) RETURNS json LANGUAGE plpgsql IMMUTABLE SET search_path='' AS $$
DECLARE units bytea; first_unit integer:=0; last_unit integer; i integer; u integer; result text:='"';
 whitespace integer[]:=ARRAY[9,10,11,12,13,28,29,30,31,32,160,5760,8192,8193,8194,8195,8196,8197,8198,8199,8200,8201,8202,8232,8233,8239,8287,12288];
BEGIN
 PERFORM acquisition_private.require(json_typeof(p)='string','Expected string');
 units:=(acquisition_private.parse(p::text)).sort_key; last_unit:=octet_length(units)/2-1;
 WHILE first_unit<=last_unit LOOP
  u:=get_byte(units,first_unit*2)*256+get_byte(units,first_unit*2+1); EXIT WHEN NOT u=ANY(whitespace); first_unit:=first_unit+1;
 END LOOP;
 WHILE last_unit>=first_unit LOOP
  u:=get_byte(units,last_unit*2)*256+get_byte(units,last_unit*2+1); EXIT WHEN NOT u=ANY(whitespace); last_unit:=last_unit-1;
 END LOOP;
 IF first_unit<=last_unit THEN FOR i IN first_unit..last_unit LOOP
  result:=result||acquisition_private.unit_quote(get_byte(units,i*2)*256+get_byte(units,i*2+1));
 END LOOP; END IF;
 RETURN (result||'"')::json;
END $$;
CREATE FUNCTION acquisition_private.nonblank(p json) RETURNS void LANGUAGE plpgsql IMMUTABLE SET search_path='' AS $$ BEGIN
 PERFORM acquisition_private.require(acquisition_private.trim_string(p)::text<>'""','Expected nonblank identity');
END $$;
CREATE FUNCTION acquisition_private.decimal(p json,p_signed boolean DEFAULT false) RETURNS bigint
LANGUAGE plpgsql IMMUTABLE SET search_path='' AS $$ DECLARE t text:=acquisition_private.text(p); BEGIN
 PERFORM acquisition_private.require(t~CASE WHEN p_signed THEN '^-?(0|[1-9][0-9]*)$' ELSE '^(0|[1-9][0-9]*)$' END AND t<>'-0','Expected canonical decimal string'); RETURN t::bigint;
END $$;
CREATE FUNCTION acquisition_private.amount(p json) RETURNS bigint[] LANGUAGE plpgsql IMMUTABLE SET search_path='' AS $$ BEGIN
 PERFORM acquisition_private.keys(p,ARRAY['xp','ap']);
 RETURN ARRAY[acquisition_private.decimal(p OPERATOR(acquisition_private.->) 'xp',true),acquisition_private.decimal(p OPERATOR(acquisition_private.->) 'ap',true)];
END $$;
CREATE FUNCTION acquisition_private.utf16(p text) RETURNS bytea LANGUAGE sql IMMUTABLE STRICT SET search_path='' AS $$
 SELECT decode(coalesce(string_agg(CASE WHEN ascii(c)>65535 THEN
  lpad(to_hex(55296+((ascii(c)-65536)/1024)),4,'0')||lpad(to_hex(56320+((ascii(c)-65536)%1024)),4,'0')
  ELSE lpad(to_hex(ascii(c)),4,'0') END,'' ORDER BY ord),''),'hex')
 FROM regexp_split_to_table(p,'') WITH ORDINALITY t(c,ord) WHERE c<>'' $$;
CREATE FUNCTION acquisition_private.fingerprint(p text) RETURNS text LANGUAGE sql IMMUTABLE STRICT SET search_path='' AS $$
 SELECT 'sha256-utf16be:'||encode(sha256(acquisition_private.utf16(p)),'hex') $$;
CREATE FUNCTION acquisition_private.frame(p text) RETURNS text LANGUAGE sql IMMUTABLE STRICT SET search_path='' AS $$
 SELECT (octet_length(acquisition_private.utf16(p))/2)::text||':'||p $$;
CREATE FUNCTION acquisition_private.archive_id(p_id text,p_revision bigint,p_local text,p_server text) RETURNS text
LANGUAGE sql IMMUTABLE STRICT SET search_path='' AS $$ SELECT 'conflict:'||acquisition_private.fingerprint(
 acquisition_private.frame(p_id)||acquisition_private.frame(p_revision::text)||acquisition_private.frame(p_local)||acquisition_private.frame(p_server)) $$;

CREATE FUNCTION acquisition_private.string_frame(p json) RETURNS bytea
LANGUAGE plpgsql IMMUTABLE SET search_path='' AS $$ DECLARE units bytea; BEGIN
 PERFORM acquisition_private.require(json_typeof(p)='string','Expected captured source string');
 units:=(acquisition_private.parse(p::text)).sort_key;
 RETURN acquisition_private.utf16((octet_length(units)/2)::text||':')||units;
END $$;
-- Lexical UTF-16 operations for historical free labels and derived nested identities.
-- Root RPC/database IDs still use the explicit PostgreSQL-text representability boundary.
CREATE FUNCTION acquisition_private.string_from_units(p bytea) RETURNS json
LANGUAGE plpgsql IMMUTABLE STRICT SET search_path='' AS $$ DECLARE i integer:=0; result text:='"'; BEGIN
 WHILE i<octet_length(p) LOOP
  result:=result||acquisition_private.unit_quote(get_byte(p,i)*256+get_byte(p,i+1)); i:=i+2;
 END LOOP; RETURN (result||'"')::json;
END $$;
CREATE FUNCTION acquisition_private.prefix_string(p_prefix text,p_value json) RETURNS json
LANGUAGE plpgsql IMMUTABLE STRICT SET search_path='' AS $$ BEGIN
 PERFORM acquisition_private.require(json_typeof(p_value)='string','Expected historical label');
 RETURN acquisition_private.string_from_units(acquisition_private.utf16(p_prefix)||(acquisition_private.parse(p_value::text)).sort_key);
END $$;
CREATE FUNCTION acquisition_private.lower_string(p json) RETURNS json
LANGUAGE plpgsql IMMUTABLE STRICT SET search_path='' AS $$
DECLARE units bytea:=(acquisition_private.parse(p::text)).sort_key; result bytea:=''::bytea; chunk text:=''; i integer:=0; u integer; lo integer;
BEGIN
 PERFORM acquisition_private.require(json_typeof(p)='string','Expected historical label');
 -- NUL and unpaired surrogates are non-case-ignorable boundaries. Lowercase each
 -- representable span with the captured ICU locale, retaining opaque units verbatim.
 WHILE i<octet_length(units) LOOP
  u:=get_byte(units,i)*256+get_byte(units,i+1); i:=i+2;
  IF u BETWEEN 55296 AND 56319 AND i<octet_length(units) THEN
   lo:=get_byte(units,i)*256+get_byte(units,i+1);
   IF lo BETWEEN 56320 AND 57343 THEN chunk:=chunk||chr(65536+(u-55296)*1024+lo-56320); i:=i+2; CONTINUE; END IF;
  END IF;
  IF u=0 OR u BETWEEN 55296 AND 57343 THEN
   result:=result||acquisition_private.utf16(lower(chunk))||decode(lpad(to_hex(u),4,'0'),'hex'); chunk:='';
  ELSE chunk:=chunk||chr(u); END IF;
 END LOOP;
 RETURN acquisition_private.string_from_units(result||acquisition_private.utf16(lower(chunk)));
END $$;
CREATE FUNCTION acquisition_private.captured_json(p json) RETURNS json
LANGUAGE plpgsql IMMUTABLE STRICT SET search_path='' AS $$
DECLARE units bytea:=(acquisition_private.parse(p::text)).sort_key; i integer:=0; u integer; raw text:=''; in_string boolean:=false; escaped boolean:=false;
BEGIN
 PERFORM acquisition_private.require(json_typeof(p)='string','Expected captured source string');
 -- JSON's old reader admits literal UTF-16 surrogate units in strings. Escape only
 -- their semantic parse view; never alter the captured raw string or its fingerprint.
 WHILE i<octet_length(units) LOOP
  u:=get_byte(units,i)*256+get_byte(units,i+1); i:=i+2;
  PERFORM acquisition_private.require(u<>0,'Unescaped JSON control');
  IF u BETWEEN 55296 AND 57343 THEN
   PERFORM acquisition_private.require(in_string AND NOT escaped,'Invalid captured JSON surrogate token');
   raw:=raw||acquisition_private.unit_quote(u);
  ELSE
   raw:=raw||chr(u);
   IF in_string THEN
    IF escaped THEN escaped:=false;
    ELSIF u=92 THEN escaped:=true;
    ELSIF u=34 THEN in_string:=false; END IF;
   ELSIF u=34 THEN in_string:=true; END IF;
  END IF;
 END LOOP;
 RETURN acquisition_private.canonical(raw)::json;
END $$;
CREATE TABLE acquisition_private.characters (
 user_id uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
 character_id text NOT NULL, min_writer_version integer NOT NULL DEFAULT 2 CHECK(min_writer_version=2),
 server_revision bigint NOT NULL CHECK(server_revision>=1), envelope_canonical text NOT NULL,
 deleted boolean NOT NULL DEFAULT false, PRIMARY KEY(user_id,character_id)
);
CREATE TABLE acquisition_private.operations (
 user_id uuid NOT NULL, character_id text NOT NULL, operation_id text NOT NULL,
 entry_canonical text NOT NULL, receipt_identity text NOT NULL,
 payload_canonical text, resolution jsonb, acknowledgement jsonb,
 PRIMARY KEY(user_id,character_id,operation_id),
 FOREIGN KEY(user_id,character_id) REFERENCES acquisition_private.characters(user_id,character_id) ON DELETE CASCADE
);
CREATE TABLE acquisition_private.archives (
 user_id uuid NOT NULL, character_id text NOT NULL, archive_id text NOT NULL,
 server_revision bigint NOT NULL, local_canonical text NOT NULL, server_canonical text NOT NULL,
 PRIMARY KEY(user_id,character_id,archive_id),
 FOREIGN KEY(user_id,character_id) REFERENCES acquisition_private.characters(user_id,character_id) ON DELETE CASCADE
);
ALTER TABLE acquisition_private.characters ENABLE ROW LEVEL SECURITY;
ALTER TABLE acquisition_private.operations ENABLE ROW LEVEL SECURITY;
ALTER TABLE acquisition_private.archives ENABLE ROW LEVEL SECURITY;
-- Baseline default ACLs are broad. Explicitly remove them, including service_role and PUBLIC.
REVOKE ALL ON ALL TABLES IN SCHEMA acquisition_private FROM PUBLIC,anon,authenticated,service_role;
CREATE FUNCTION acquisition_private.set_field(p json,p_key text,p_value json) RETURNS json
LANGUAGE sql IMMUTABLE SET search_path='' AS $$
 SELECT acquisition_private.canonical('{'||string_agg(to_json(k)::text||':'||v::text,',')||'}')::json
 FROM (SELECT key k,value v FROM acquisition_private.object_members(p) WHERE key<>p_key UNION ALL SELECT p_key,p_value) x $$;
CREATE FUNCTION acquisition_private.remove_field(p json,p_key text) RETURNS json
LANGUAGE sql IMMUTABLE SET search_path='' AS $$
 SELECT acquisition_private.canonical('{'||coalesce(string_agg(to_json(key)::text||':'||value::text,','),'')||'}')::json FROM acquisition_private.object_members(p) WHERE key<>p_key $$;
CREATE FUNCTION acquisition_private.projection(p json,p_financial boolean DEFAULT false) RETURNS text
LANGUAGE sql IMMUTABLE SET search_path='' AS $$ SELECT acquisition_private.canonical(
 CASE WHEN p_financial THEN json_build_object('characterSnapshot',p OPERATOR(acquisition_private.->) 'characterSnapshot','instances',p OPERATOR(acquisition_private.->) 'instances')::text
 ELSE json_build_object('characterSnapshot',p OPERATOR(acquisition_private.->) 'characterSnapshot','instances',p OPERATOR(acquisition_private.->) 'instances','extras',p OPERATOR(acquisition_private.->) 'extras','creationPhase',p OPERATOR(acquisition_private.->) 'creationPhase')::text END) $$;
CREATE FUNCTION acquisition_private.durable_projection(p json) RETURNS text
LANGUAGE sql IMMUTABLE SET search_path='' AS $$ SELECT acquisition_private.set_field(p,'extras',acquisition_private.remove_field(p OPERATOR(acquisition_private.->) 'extras','portraitUri'))::text $$;
CREATE FUNCTION acquisition_private.members(p json) RETURNS text[] LANGUAGE plpgsql IMMUTABLE SET search_path='' AS $$
DECLARE x json; ids text[]:=ARRAY[]::text[]; id text; BEGIN
 PERFORM acquisition_private.require(json_typeof(p)='array','Expected proof membership array');
 FOR x IN SELECT value FROM json_array_elements(p) LOOP
  id:=acquisition_private.text(x); PERFORM acquisition_private.nonblank(x);
  PERFORM acquisition_private.require(NOT id=ANY(ids) AND (cardinality(ids)=0 OR ids[cardinality(ids)] COLLATE "C"<id COLLATE "C"),'Duplicate/unsorted proof membership');
  ids:=array_append(ids,id);
 END LOOP; RETURN ids;
END $$;
CREATE FUNCTION acquisition_private.pool_ids(p json) RETURNS text[] LANGUAGE sql IMMUTABLE SET search_path='' AS $$
 SELECT coalesce(array_agg(value OPERATOR(acquisition_private.->>) 'proofId' ORDER BY ord),ARRAY[]::text[]) FROM json_array_elements(p) WITH ORDINALITY x(value,ord) $$;
CREATE FUNCTION acquisition_private.proof_closure(p_id text,p_pool json,p_path text[] DEFAULT ARRAY[]::text[]) RETURNS void
LANGUAGE plpgsql IMMUTABLE SET search_path='' AS $$ DECLARE rec json; id text; BEGIN
 PERFORM acquisition_private.require(NOT p_id=ANY(p_path) AND cardinality(p_path)<64,'Cyclic or excessive sync proof closure');
 SELECT value INTO rec FROM json_array_elements(p_pool) WHERE value OPERATOR(acquisition_private.->>) 'proofId'=p_id;
 PERFORM acquisition_private.require(rec IS NOT NULL,'Missing prior proof membership');
 FOREACH id IN ARRAY acquisition_private.members(rec OPERATOR(acquisition_private.->) 'proofIds') LOOP
  PERFORM acquisition_private.proof_closure(id,p_pool,array_append(p_path,p_id));
 END LOOP;
END $$;
CREATE FUNCTION acquisition_private.proof_wire(p_id text,p_pool json,p_path text[] DEFAULT ARRAY[]::text[],p_validate boolean DEFAULT true) RETURNS text
LANGUAGE plpgsql IMMUTABLE SET search_path='' AS $$
DECLARE rec json; flat text; ids text[]; expanded json; root json;
BEGIN
 PERFORM acquisition_private.require(NOT p_id=ANY(p_path) AND cardinality(p_path)<64,'Cyclic or excessive sync proof closure');
 SELECT value INTO rec FROM json_array_elements(p_pool) WHERE value OPERATOR(acquisition_private.->>) 'proofId'=p_id;
 PERFORM acquisition_private.require(rec IS NOT NULL,'Missing referenced proof');
 PERFORM acquisition_private.keys(rec,ARRAY['proofId','payloadCanonical','proofIds']);
 flat:=acquisition_private.text(rec OPERATOR(acquisition_private.->) 'payloadCanonical'); root:=acquisition_private.wire_json(flat);
 PERFORM acquisition_private.require(root OPERATOR(acquisition_private.->) 'syncProofs' IS NULL,'Recursive sync proof pool');
 ids:=acquisition_private.members(rec OPERATOR(acquisition_private.->) 'proofIds');
 PERFORM acquisition_private.require(p_id='sync-proof:'||acquisition_private.fingerprint(acquisition_private.canonical(
  json_build_object('payloadCanonical',flat,'proofIds',rec OPERATOR(acquisition_private.->) 'proofIds')::text)),'Proof content identity mismatch');
 IF p_validate THEN
  PERFORM acquisition_private.proof_closure(p_id,p_pool,p_path);
  PERFORM acquisition_private.validate_wire(flat,p_pool,ids,array_append(p_path,p_id));
 END IF;
 IF cardinality(ids)=0 THEN RETURN flat; END IF;
 SELECT json_agg(value ORDER BY ord) INTO expanded
 FROM unnest(ids) WITH ORDINALITY names(id,ord) JOIN LATERAL json_array_elements(p_pool) x(value) ON value OPERATOR(acquisition_private.->>) 'proofId'=id;
 PERFORM acquisition_private.require(json_array_length(expanded)=cardinality(ids),'Missing prior proof membership');
 RETURN acquisition_private.set_field(root,'syncProofs',expanded)::text;
END $$;
CREATE FUNCTION acquisition_private.branches(p json,p_pool json,p_members text[],p_path text[]) RETURNS text[]
LANGUAGE plpgsql IMMUTABLE SET search_path='' AS $$ DECLARE schema text:=p OPERATOR(acquisition_private.->>) 'schema'; ids text[]; id text; result text[]; BEGIN
 IF schema='sync-adoption-v2' THEN
  PERFORM acquisition_private.keys(p,ARRAY['schema','selectedProofId','displacedProofId','serverProofId','expectedServerRevision','choice','archiveId']);
  ids:=ARRAY[acquisition_private.text(p OPERATOR(acquisition_private.->) 'selectedProofId'),acquisition_private.text(p OPERATOR(acquisition_private.->) 'displacedProofId'),acquisition_private.text(p OPERATOR(acquisition_private.->) 'serverProofId')];
  FOREACH id IN ARRAY ids LOOP
   PERFORM acquisition_private.require(id=ANY(p_members),'Branch proof outside captured membership');
   result:=array_append(result,acquisition_private.proof_wire(id,p_pool,p_path,cardinality(p_path)>0));
  END LOOP;
 ELSIF schema='sync-adoption-v1' THEN
  PERFORM acquisition_private.keys(p,ARRAY['schema','selectedCanonical','displacedCanonical','serverCanonical','expectedServerRevision','choice','archiveId']);
  result:=ARRAY[acquisition_private.text(p OPERATOR(acquisition_private.->) 'selectedCanonical'),acquisition_private.text(p OPERATOR(acquisition_private.->) 'displacedCanonical'),acquisition_private.text(p OPERATOR(acquisition_private.->) 'serverCanonical')];
  FOREACH id IN ARRAY result LOOP PERFORM acquisition_private.validate_wire(id,NULL,NULL,p_path); END LOOP;
 ELSE RAISE EXCEPTION USING ERRCODE='22023',MESSAGE='Unsupported adoption proof schema'; END IF;
 RETURN result;
END $$;

-- Shape checks preserve manual values and raw UTF-16 strings; they are not gameplay/price rules.
CREATE FUNCTION acquisition_private.types(p json,p_strings text[] DEFAULT ARRAY[]::text[],p_numbers text[] DEFAULT ARRAY[]::text[],p_booleans text[] DEFAULT ARRAY[]::text[],p_arrays text[] DEFAULT ARRAY[]::text[]) RETURNS void
LANGUAGE plpgsql IMMUTABLE SET search_path='' AS $$ DECLARE k text; BEGIN
 FOREACH k IN ARRAY p_strings LOOP PERFORM acquisition_private.require(json_typeof(p OPERATOR(acquisition_private.->) k)='string','Expected string field: '||k); END LOOP;
 FOREACH k IN ARRAY p_numbers LOOP
  PERFORM acquisition_private.require(json_typeof(p OPERATOR(acquisition_private.->) k)='number' AND (p OPERATOR(acquisition_private.->>) k)~'^-?(0|[1-9][0-9]*)$' AND (p OPERATOR(acquisition_private.->>) k)<>'-0' AND (p OPERATOR(acquisition_private.->>) k)::bigint BETWEEN -2147483648 AND 2147483647,'Expected canonical Int32 field: '||k);
 END LOOP;
 FOREACH k IN ARRAY p_booleans LOOP PERFORM acquisition_private.require(json_typeof(p OPERATOR(acquisition_private.->) k)='boolean','Expected Boolean field: '||k); END LOOP;
 FOREACH k IN ARRAY p_arrays LOOP PERFORM acquisition_private.require(json_typeof(p OPERATOR(acquisition_private.->) k)='array','Expected array field: '||k); END LOOP;
END $$;
CREATE FUNCTION acquisition_private.string_list(p json) RETURNS void LANGUAGE plpgsql IMMUTABLE SET search_path='' AS $$ DECLARE x json; BEGIN
 PERFORM acquisition_private.require(json_typeof(p)='array','Expected string array');
 FOR x IN SELECT value FROM json_array_elements(p) LOOP PERFORM acquisition_private.require(json_typeof(x)='string','Expected string array member'); END LOOP;
END $$;
CREATE FUNCTION acquisition_private.string_map(p json) RETURNS void LANGUAGE plpgsql IMMUTABLE SET search_path='' AS $$
DECLARE raw text:=p::text; i integer:=2; k acquisition_private.token; v acquisition_private.token; c text;
BEGIN
 PERFORM acquisition_private.require(json_typeof(p)='object','Expected string map');
 IF raw='{}' THEN RETURN; END IF;
 LOOP
  k:=acquisition_private.parse(raw,i); i:=k.next_position;
  PERFORM acquisition_private.require(substr(raw,i,1)=':','String map colon');
  v:=acquisition_private.parse(raw,i+1); i:=v.next_position;
  PERFORM acquisition_private.require(substr(v.canonical,1,1)='"','Expected string map value');
  c:=substr(raw,i,1); i:=i+1; IF c='}' THEN RETURN; END IF;
  PERFORM acquisition_private.require(c=',','String map comma');
 END LOOP;
END $$;
CREATE FUNCTION acquisition_private.parameter_map(p json) RETURNS void LANGUAGE plpgsql IMMUTABLE SET search_path='' AS $$ DECLARE x record; BEGIN
 PERFORM acquisition_private.string_map(p);
 FOR x IN SELECT * FROM acquisition_private.object_raw_members(p) LOOP
  PERFORM acquisition_private.nonblank(x.key); PERFORM acquisition_private.nonblank(x.value);
 END LOOP;
END $$;
CREATE FUNCTION acquisition_private.enum_list(p json,p_values text[],p_unique_sorted boolean DEFAULT false) RETURNS void
LANGUAGE plpgsql IMMUTABLE SET search_path='' AS $$ DECLARE x json; prior text; current_value text; BEGIN
 PERFORM acquisition_private.string_list(p);
 FOR x IN SELECT value FROM json_array_elements(p) LOOP
  current_value:=acquisition_private.text(x);
  PERFORM acquisition_private.require(current_value=ANY(p_values),'Unknown enum value');
  IF p_unique_sorted THEN PERFORM acquisition_private.require(prior IS NULL OR prior COLLATE "C"<current_value COLLATE "C",'Duplicate/unsorted enum list'); END IF;
  prior:=current_value;
 END LOOP;
END $$;
CREATE FUNCTION acquisition_private.snapshot_shape(p json) RETURNS void LANGUAGE plpgsql IMMUTABLE SET search_path='' AS $$
DECLARE c json:=p OPERATOR(acquisition_private.->) 'characters' OPERATOR(acquisition_private.->) 0; x json; y json; k text; id json; seen_ids text[];
BEGIN
 PERFORM acquisition_private.types(c,ARRAY['id','name','concept'],ARRAY['experience','creationExperience','xpAdjustment','size','legs','hpCurrent','enduranceCurrent','manaCurrent','manaMaximum','chiCurrent','chiBonusRanks'],ARRAY['creationComplete','manaEnabled','chiEnabled'],
  ARRAY['customResources','skills','hiddenSkillIds','disabledSkillEffectIds','development','developmentOverrides','customDevelopmentEntries']);
 FOREACH k IN ARRAY ARRAY['abilityPointsOverride','healthMaximumOverride','enduranceMaximumOverride','manaMaximumOverride'] LOOP
  IF c OPERATOR(acquisition_private.->) k IS NOT NULL THEN PERFORM acquisition_private.types(c,ARRAY[]::text[],ARRAY[k]); END IF;
 END LOOP;
 -- These arrays decode through keyed maps, sets or distinct/filter operations. Reject
 -- loss rather than sorting arrays, clamping manual values or normalizing identities.
 FOREACH k IN ARRAY ARRAY['skills','development','developmentOverrides','customDevelopmentEntries','hiddenSkillIds','disabledSkillEffectIds'] LOOP
  seen_ids:=ARRAY[]::text[];
  FOR x IN SELECT value FROM json_array_elements(c OPERATOR(acquisition_private.->) k) LOOP
   id:=CASE WHEN k IN ('hiddenSkillIds','disabledSkillEffectIds') THEN x ELSE x OPERATOR(acquisition_private.->) 'id' END;
   PERFORM acquisition_private.nonblank(id);
   IF k IN ('development','developmentOverrides','customDevelopmentEntries','disabledSkillEffectIds') THEN
    PERFORM acquisition_private.require(acquisition_private.trim_string(id)::text=id::text,'Snapshot identity would be trimmed: '||k);
   END IF;
   PERFORM acquisition_private.require(NOT id::text=ANY(seen_ids),'Duplicate snapshot identity: '||k); seen_ids:=array_append(seen_ids,id::text);
  END LOOP;
 END LOOP;
 PERFORM acquisition_private.keys(c OPERATOR(acquisition_private.->) 'attributes',ARRAY['CHARISMA','CONSTITUTION','DEXTERITY','INTELLIGENCE','PERCEPTION','SPEED','STRENGTH','WILL']);
 FOR x IN SELECT value FROM acquisition_private.object_members(c OPERATOR(acquisition_private.->) 'attributes') LOOP
  PERFORM acquisition_private.keys(x,ARRAY['base','bonus']); PERFORM acquisition_private.types(x,ARRAY[]::text[],ARRAY['base','bonus']);
 END LOOP;
 FOR x IN SELECT value FROM json_array_elements(c OPERATOR(acquisition_private.->) 'customResources') LOOP
  PERFORM acquisition_private.keys(x,ARRAY['uid','name','current','maximum']); PERFORM acquisition_private.types(x,ARRAY['uid','name'],ARRAY['current','maximum']);
  PERFORM acquisition_private.nonblank(x OPERATOR(acquisition_private.->) 'uid');
 END LOOP;
 FOR x IN SELECT value FROM json_array_elements(c OPERATOR(acquisition_private.->) 'skills') LOOP
  PERFORM acquisition_private.keys(x,ARRAY['id','name','description','rank','attributes','modifier','formulaNote'],ARRAY['definitionId','categoryOverride','untrainedOverride','auto6Override','auto12Override']);
  PERFORM acquisition_private.types(x,ARRAY['id','name','description','formulaNote'],ARRAY['rank','modifier'],ARRAY[]::text[],ARRAY['attributes']);
  PERFORM acquisition_private.enum_list(x OPERATOR(acquisition_private.->) 'attributes',ARRAY['CHARISMA','CONSTITUTION','DEXTERITY','INTELLIGENCE','PERCEPTION','SPEED','STRENGTH','WILL']);
  IF x OPERATOR(acquisition_private.->) 'categoryOverride' IS NOT NULL THEN PERFORM acquisition_private.require(x OPERATOR(acquisition_private.->>) 'categoryOverride' IN ('COMBAT','PHYSICAL','FIELD','SOCIAL','KNOWLEDGE','TECHNICAL','CUSTOM'),'Unknown skill category'); END IF;
  IF x OPERATOR(acquisition_private.->) 'untrainedOverride' IS NOT NULL THEN PERFORM acquisition_private.require(x OPERATOR(acquisition_private.->>) 'untrainedOverride' IN ('YES','YES_MINUS_2','NO','UNSPECIFIED'),'Unknown untrained rule'); END IF;
  FOREACH k IN ARRAY ARRAY['definitionId','categoryOverride','untrainedOverride','auto6Override','auto12Override'] LOOP
   IF x OPERATOR(acquisition_private.->) k IS NOT NULL THEN PERFORM acquisition_private.nonblank(x OPERATOR(acquisition_private.->) k); END IF;
  END LOOP;
 END LOOP;
 PERFORM acquisition_private.string_list(c OPERATOR(acquisition_private.->) 'hiddenSkillIds'); PERFORM acquisition_private.string_list(c OPERATOR(acquisition_private.->) 'disabledSkillEffectIds');
 FOR x IN SELECT value FROM json_array_elements(c OPERATOR(acquisition_private.->) 'development') LOOP
  PERFORM acquisition_private.keys(x,ARRAY['id','rank','option']); PERFORM acquisition_private.types(x,ARRAY['id'],ARRAY['rank','option']);
 END LOOP;
 FOR x IN SELECT value FROM json_array_elements(c OPERATOR(acquisition_private.->) 'developmentOverrides') UNION ALL SELECT value FROM json_array_elements(c OPERATOR(acquisition_private.->) 'customDevelopmentEntries') LOOP
  PERFORM acquisition_private.keys(x,ARRAY['id','name','section','category','cost','costType','maxRank','requirements','benefit','notes','tags','abilityOptions','incomplete','repeatable','perfectRoot','mechanicsConflict','conflictNote'],ARRAY['accessId']);
  PERFORM acquisition_private.types(x,ARRAY['id','name','section','category','costType','requirements','benefit','notes','mechanicsConflict','conflictNote'],ARRAY['cost','maxRank'],ARRAY['incomplete','repeatable','perfectRoot'],ARRAY['tags','abilityOptions']);
  PERFORM acquisition_private.require(x OPERATOR(acquisition_private.->>) 'costType' IN ('XP','ABILITY'),'Unknown development cost type');
  IF x OPERATOR(acquisition_private.->) 'accessId' IS NOT NULL THEN
   PERFORM acquisition_private.nonblank(x OPERATOR(acquisition_private.->) 'accessId');
   PERFORM acquisition_private.require(acquisition_private.trim_string(x OPERATOR(acquisition_private.->) 'accessId')::text=(x OPERATOR(acquisition_private.->) 'accessId')::text,'Snapshot access ID would be trimmed');
  END IF;
  PERFORM acquisition_private.string_list(x OPERATOR(acquisition_private.->) 'tags');
  FOR y IN SELECT value FROM json_array_elements(x OPERATOR(acquisition_private.->) 'abilityOptions') LOOP
   PERFORM acquisition_private.keys(y,ARRAY['source','value']); PERFORM acquisition_private.types(y,ARRAY['source'],ARRAY['value']);
  END LOOP;
 END LOOP;
 PERFORM acquisition_private.keys(c OPERATOR(acquisition_private.->) 'magic',ARRAY['manaRank','power','schools','spells']);
 PERFORM acquisition_private.types(c OPERATOR(acquisition_private.->) 'magic',ARRAY[]::text[],ARRAY['manaRank','power'],ARRAY[]::text[],ARRAY['schools','spells']);
 FOR x IN SELECT value FROM json_array_elements(c OPERATOR(acquisition_private.->) 'magic' OPERATOR(acquisition_private.->) 'schools') LOOP
  PERFORM acquisition_private.keys(x,ARRAY['name','rank','note']); PERFORM acquisition_private.types(x,ARRAY['name','note'],ARRAY['rank']);
 END LOOP;
 FOR x IN SELECT value FROM json_array_elements(c OPERATOR(acquisition_private.->) 'magic' OPERATOR(acquisition_private.->) 'spells') LOOP
  PERFORM acquisition_private.keys(x,ARRAY['uid','name','school','cost','manaText','time','range','area','action','duration','description','enhancement','learned','incomplete','conflictNote','custom'],ARRAY['catalogId','xpOverride']);
  PERFORM acquisition_private.types(x,ARRAY['uid','name','school','manaText','time','range','area','action','duration','description','enhancement','conflictNote'],ARRAY['cost'],ARRAY['learned','incomplete','custom']);
  PERFORM acquisition_private.nonblank(x OPERATOR(acquisition_private.->) 'uid');
  IF x OPERATOR(acquisition_private.->) 'catalogId' IS NOT NULL THEN PERFORM acquisition_private.nonblank(x OPERATOR(acquisition_private.->) 'catalogId'); END IF;
  IF x OPERATOR(acquisition_private.->) 'xpOverride' IS NOT NULL THEN PERFORM acquisition_private.types(x,ARRAY[]::text[],ARRAY['xpOverride']); END IF;
 END LOOP;
 PERFORM acquisition_private.keys(c OPERATOR(acquisition_private.->) 'gear',ARRAY['loadAutomatic','loadManual','items']);
 PERFORM acquisition_private.types(c OPERATOR(acquisition_private.->) 'gear',ARRAY[]::text[],ARRAY[]::text[],ARRAY['loadAutomatic'],ARRAY['items']);
 PERFORM acquisition_private.require(json_typeof(c OPERATOR(acquisition_private.->) 'gear' OPERATOR(acquisition_private.->) 'loadManual')='number','Expected raw Double loadManual');
 PERFORM (c OPERATOR(acquisition_private.->) 'gear' OPERATOR(acquisition_private.->>) 'loadManual')::double precision;
 FOR x IN SELECT value FROM json_array_elements(c OPERATOR(acquisition_private.->) 'gear' OPERATOR(acquisition_private.->) 'items') LOOP
  PERFORM acquisition_private.keys(x,ARRAY['uid','name','quantity','load','carried','description','category','section','custom','fields'],ARRAY['catalogId']);
  PERFORM acquisition_private.types(x,ARRAY['uid','name','description','category','section'],ARRAY['quantity'],ARRAY['carried','custom']);
  PERFORM acquisition_private.nonblank(x OPERATOR(acquisition_private.->) 'uid');
  IF x OPERATOR(acquisition_private.->) 'catalogId' IS NOT NULL THEN PERFORM acquisition_private.nonblank(x OPERATOR(acquisition_private.->) 'catalogId'); END IF;
  PERFORM acquisition_private.require(json_typeof(x OPERATOR(acquisition_private.->) 'load')='number','Expected raw Double gear load'); PERFORM (x OPERATOR(acquisition_private.->>) 'load')::double precision;
  PERFORM acquisition_private.string_map(x OPERATOR(acquisition_private.->) 'fields');
 END LOOP;
END $$;

-- Legacy extras are compact form-encoded records inside JSON strings, not opaque
-- permission to acknowledge data which the unchanged shared decoder would discard.
CREATE FUNCTION acquisition_private.form_token(p text) RETURNS bytea LANGUAGE plpgsql IMMUTABLE SET search_path='' AS $$
DECLARE i integer:=1; b integer; data bytea:=''::bytea; j integer; n integer; next integer; count integer;
BEGIN
 PERFORM acquisition_private.require(p~'^([A-Za-z0-9_.*+\-]|%[0-9A-F]{2})*$','Malformed form token');
 WHILE i<=length(p) LOOP
  IF substr(p,i,1)='%' THEN
   b:=('x'||substr(p,i+1,2))::bit(8)::integer; i:=i+3;
   PERFORM acquisition_private.require(NOT (b=32 OR b BETWEEN 65 AND 90 OR b BETWEEN 97 AND 122 OR b BETWEEN 48 AND 57 OR b IN (45,95,46,42)),'Noncanonical form escape');
  ELSE b:=CASE WHEN substr(p,i,1)='+' THEN 32 ELSE ascii(substr(p,i,1)) END; i:=i+1; END IF;
  data:=data||decode(lpad(to_hex(b),2,'0'),'hex');
 END LOOP;
 -- Validate UTF8 as bytes; U+0000 is legitimate non-identity content and stays encoded.
 j:=0; n:=octet_length(data);
 WHILE j<n LOOP
  b:=get_byte(data,j);
  IF b<128 THEN j:=j+1; CONTINUE; END IF;
  count:=CASE WHEN b BETWEEN 194 AND 223 THEN 1 WHEN b BETWEEN 224 AND 239 THEN 2 WHEN b BETWEEN 240 AND 244 THEN 3 ELSE -1 END;
  PERFORM acquisition_private.require(count>0 AND j+count<n,'Invalid UTF8 form token');
  next:=get_byte(data,j+1);
  PERFORM acquisition_private.require(next BETWEEN 128 AND 191 AND (b<>224 OR next>=160) AND (b<>237 OR next<=159) AND (b<>240 OR next>=144) AND (b<>244 OR next<=143),'Invalid UTF8 form token');
  FOR i IN 2..count LOOP PERFORM acquisition_private.require(get_byte(data,j+i) BETWEEN 128 AND 191,'Invalid UTF8 continuation'); END LOOP;
  j:=j+count+1;
 END LOOP;
 RETURN data;
END $$;
CREATE FUNCTION acquisition_private.form_nonblank(p text) RETURNS boolean LANGUAGE plpgsql IMMUTABLE SET search_path='' AS $$
DECLARE data bytea:=acquisition_private.form_token(p); j integer; whitespace text:=chr(9)||chr(10)||chr(11)||chr(12)||chr(13)||chr(28)||chr(29)||chr(30)||chr(31)||chr(32)||chr(160)||chr(5760)||chr(8192)||chr(8193)||chr(8194)||chr(8195)||chr(8196)||chr(8197)||chr(8198)||chr(8199)||chr(8200)||chr(8201)||chr(8202)||chr(8232)||chr(8233)||chr(8239)||chr(8287)||chr(12288);
BEGIN
 IF octet_length(data)=0 THEN RETURN false; END IF;
 FOR j IN 0..octet_length(data)-1 LOOP IF get_byte(data,j)=0 THEN RETURN true; END IF; END LOOP;
 RETURN btrim(convert_from(data,'UTF8'),whitespace)<>'';
END $$;
CREATE FUNCTION acquisition_private.extra_records(p json,p_kind text) RETURNS void LANGUAGE plpgsql IMMUTABLE SET search_path='' AS $$
DECLARE raw text:=acquisition_private.text(p); record text; fields text[]; item text; seen text[]:=ARRAY[]::text[]; prior text;
 conditions text[]:=ARRAY['HELPLESS','PARALYZED','UNCONSCIOUS','BLINDNESS','DAZED','DAZZLED','DEAFNESS','ENTANGLED','WEAKNESS','EXHAUSTION','TIRED','INVISIBILITY','NAUSEA','PRONE','SHOCK','FEAR','PANIC','SICKNESS','STAGGERED','STUNNED','POISONED','INSPIRED','DEMORALIZED'];
BEGIN
 IF raw='' THEN RETURN; END IF;
 FOREACH record IN ARRAY string_to_array(raw,chr(30)) LOOP
  fields:=string_to_array(record,chr(31));
  PERFORM acquisition_private.require(cardinality(fields)=CASE WHEN p_kind='override' THEN 3 ELSE 4 END,'Malformed legacy extras record');
  PERFORM acquisition_private.require(acquisition_private.form_nonblank(fields[1]),'Blank legacy extras identity');
  PERFORM acquisition_private.form_token(fields[2]);
  IF p_kind='override' THEN
   PERFORM acquisition_private.require(fields[1]=ANY(conditions) AND (prior IS NULL OR prior COLLATE "C"<fields[1] COLLATE "C"),'Unknown/duplicate/unsorted condition override');
   PERFORM acquisition_private.require(fields[2]='' OR acquisition_private.form_nonblank(fields[2]),'Blank override would normalize');
   PERFORM acquisition_private.form_token(fields[3]); prior:=fields[1];
  ELSIF p_kind='custom' THEN
   PERFORM acquisition_private.require(acquisition_private.form_nonblank(fields[2]) AND NOT fields[1]=ANY(seen) AND fields[4] IN ('0','1'),'Invalid custom condition');
   seen:=array_append(seen,fields[1]); PERFORM acquisition_private.form_token(fields[3]);
  ELSE
   PERFORM acquisition_private.require(acquisition_private.form_nonblank(fields[2]) AND fields[3] IN ('0','1'),'Invalid sheet group');
   seen:=ARRAY[]::text[];
   IF fields[4]<>'' THEN FOREACH item IN ARRAY string_to_array(fields[4],chr(29)) LOOP
    PERFORM acquisition_private.require(acquisition_private.form_nonblank(item) AND NOT item=ANY(seen),'Blank/duplicate group item'); seen:=array_append(seen,item);
   END LOOP; END IF;
  END IF;
 END LOOP;
END $$;

-- The captured legacy backup uses the existing compatibility decoder, not the lossless
-- schema-11 projection decoder. Keep historical numeric strings/aliases/defaults here.
CREATE FUNCTION acquisition_private.legacy_int(p json,p_default integer DEFAULT 0) RETURNS integer
LANGUAGE plpgsql IMMUTABLE SET search_path='' AS $$
DECLARE n numeric; units bytea; i integer:=0; u integer; z integer; digit integer; negative boolean:=false; magnitude bigint:=0; limit_value bigint:=2147483647;
 zeros integer[]:=ARRAY[48,1632,1776,1984,2406,2534,2662,2790,2918,3046,3174,3302,3430,3558,3664,3792,3872,4160,4240,6112,6160,6470,6608,6784,6800,6992,7088,7232,7248,42528,43216,43264,43472,43504,43600,44016,65296];
BEGIN
 IF p IS NULL THEN RETURN p_default; END IF;
 PERFORM acquisition_private.require(json_typeof(p) IN ('number','string'),'Invalid legacy integer');
 IF json_typeof(p)='number' THEN
  n:=p::text::numeric; PERFORM acquisition_private.require(n=trunc(n) AND n BETWEEN -2147483648 AND 2147483647,'Invalid legacy integer'); RETURN n::integer;
 END IF;
 -- Pinned JVM String.toIntOrNull uses Character.digit(Char,10), ASCII signs and
 -- checked Int32 accumulation. All 37 BMP decimal digit ranges were enumerated by
 -- the actual public producer; supplementary/superscript digits are not Char digits.
 units:=(acquisition_private.parse(p::text)).sort_key;
 PERFORM acquisition_private.require(octet_length(units)>0,'Invalid legacy integer string');
 u:=get_byte(units,0)*256+get_byte(units,1);
 IF u IN (43,45) THEN negative:=u=45; i:=2; END IF;
 IF negative THEN limit_value:=2147483648; END IF;
 PERFORM acquisition_private.require(i<octet_length(units),'Invalid legacy integer string');
 WHILE i<octet_length(units) LOOP
  u:=get_byte(units,i)*256+get_byte(units,i+1); i:=i+2; digit:=NULL;
  FOREACH z IN ARRAY zeros LOOP IF u BETWEEN z AND z+9 THEN digit:=u-z; EXIT; END IF; END LOOP;
  PERFORM acquisition_private.require(digit IS NOT NULL,'Invalid legacy integer string');
  PERFORM acquisition_private.require(magnitude<=(limit_value-digit)/10,'Invalid legacy integer');
  magnitude:=magnitude*10+digit;
 END LOOP;
 RETURN (CASE WHEN negative THEN -magnitude ELSE magnitude END)::integer;
END $$;
CREATE FUNCTION acquisition_private.legacy_bool(p json,p_default boolean DEFAULT false) RETURNS boolean
LANGUAGE plpgsql IMMUTABLE SET search_path='' AS $$ BEGIN
 IF p IS NULL THEN RETURN p_default; END IF;
 PERFORM acquisition_private.require(json_typeof(p) IN ('boolean','string') AND p#>>'{}' IN ('true','false'),'Invalid legacy Boolean'); RETURN (p#>>'{}')::boolean;
END $$;
-- Only the old snapshot schema selector uses the compatibility decoder's Double-to-Int
-- conversion. Ownership integers above retain validateLegacySnapshot's exact admission.
CREATE FUNCTION acquisition_private.legacy_schema(p json) RETURNS integer LANGUAGE plpgsql IMMUTABLE SET search_path='' AS $$ DECLARE n double precision; BEGIN
 IF json_typeof(p)='number' THEN
  n:=p::text::double precision;
  PERFORM acquisition_private.require(n>=1 AND n<12,'Invalid legacy snapshot schema'); RETURN floor(n)::integer;
 END IF;
 RETURN acquisition_private.legacy_int(p,0);
END $$;
CREATE FUNCTION acquisition_private.legacy_text(p json,p_default text DEFAULT '') RETURNS text
LANGUAGE sql IMMUTABLE SET search_path='' AS $$ SELECT CASE WHEN json_typeof(p) IN ('string','number','boolean') THEN p#>>'{}' ELSE p_default END $$;
CREATE FUNCTION acquisition_private.legacy_label(p json,p_default text DEFAULT '') RETURNS json
LANGUAGE sql IMMUTABLE SET search_path='' AS $$
 SELECT CASE WHEN json_typeof(p)='string' THEN p WHEN json_typeof(p) IN ('number','boolean') THEN to_json(p::text) ELSE to_json(p_default) END $$;
CREATE FUNCTION acquisition_private.legacy_rows(p json) RETURNS SETOF json LANGUAGE plpgsql IMMUTABLE SET search_path='' AS $$ DECLARE x json; BEGIN
 IF p IS NULL THEN RETURN; END IF;
 PERFORM acquisition_private.require(json_typeof(p)='array','Invalid legacy array');
 FOR x IN SELECT value FROM json_array_elements(p) LOOP PERFORM acquisition_private.require(json_typeof(x)='object','Invalid legacy row'); RETURN NEXT x; END LOOP;
END $$;
CREATE FUNCTION acquisition_private.legacy_fields(p json,p_ints text[] DEFAULT ARRAY[]::text[],p_bools text[] DEFAULT ARRAY[]::text[],p_texts text[] DEFAULT ARRAY[]::text[],p_nullable_ints text[] DEFAULT ARRAY[]::text[],p_nullable_texts text[] DEFAULT ARRAY[]::text[],p_finite text[] DEFAULT ARRAY[]::text[]) RETURNS void
LANGUAGE plpgsql IMMUTABLE SET search_path='' AS $$ DECLARE k text; v json; n double precision; BEGIN
 FOREACH k IN ARRAY p_ints LOOP IF p OPERATOR(acquisition_private.->) k IS NOT NULL THEN PERFORM acquisition_private.legacy_int(p OPERATOR(acquisition_private.->) k); END IF; END LOOP;
 FOREACH k IN ARRAY p_bools LOOP IF p OPERATOR(acquisition_private.->) k IS NOT NULL THEN PERFORM acquisition_private.legacy_bool(p OPERATOR(acquisition_private.->) k); END IF; END LOOP;
 FOREACH k IN ARRAY p_texts LOOP IF p OPERATOR(acquisition_private.->) k IS NOT NULL THEN PERFORM acquisition_private.types(p,ARRAY[k]); END IF; END LOOP;
 FOREACH k IN ARRAY p_nullable_ints LOOP v:=p OPERATOR(acquisition_private.->) k; IF v IS NOT NULL AND v::text<>'null' THEN PERFORM acquisition_private.legacy_int(v); END IF; END LOOP;
 FOREACH k IN ARRAY p_nullable_texts LOOP v:=p OPERATOR(acquisition_private.->) k; IF v IS NOT NULL AND v::text<>'null' THEN PERFORM acquisition_private.types(p,ARRAY[k]); END IF; END LOOP;
 FOREACH k IN ARRAY p_finite LOOP
  v:=p OPERATOR(acquisition_private.->) k; IF v IS NOT NULL THEN
   PERFORM acquisition_private.require(json_typeof(v) IN ('number','string'),'Invalid legacy number'); n:=(v#>>'{}')::double precision;
   PERFORM acquisition_private.require(n NOT IN ('NaN'::double precision,'Infinity'::double precision,'-Infinity'::double precision),'Invalid legacy number');
  END IF;
 END LOOP;
END $$;
CREATE FUNCTION acquisition_private.legacy_unique(p json,p_key text,p_required boolean,p_trim boolean DEFAULT false) RETURNS void
LANGUAGE plpgsql IMMUTABLE SET search_path='' AS $$ DECLARE x json; v json; seen text[]:=ARRAY[]::text[]; BEGIN
 FOR x IN SELECT * FROM acquisition_private.legacy_rows(p) LOOP
  v:=x OPERATOR(acquisition_private.->) p_key;
  IF p_required THEN PERFORM acquisition_private.nonblank(v);
  ELSIF v IS NULL OR v::text='null' THEN CONTINUE;
  ELSE PERFORM acquisition_private.types(x,ARRAY[p_key]); END IF;
  IF p_trim THEN v:=acquisition_private.trim_string(v); END IF;
  IF acquisition_private.trim_string(v)::text='""' THEN CONTINUE; END IF;
  PERFORM acquisition_private.require(NOT v::text=ANY(seen),'Duplicate legacy identity'); seen:=array_append(seen,v::text);
 END LOOP;
END $$;
CREATE FUNCTION acquisition_private.legacy_backup(p_origin json) RETURNS json
LANGUAGE plpgsql IMMUTABLE SET search_path='' AS $$
DECLARE root json:=acquisition_private.captured_json(p_origin OPERATOR(acquisition_private.->) 'raw');
 c json; x json; y json; z json; selected json; k text; n integer:=0; schema integer:=acquisition_private.legacy_schema(root OPERATOR(acquisition_private.->) 'schema');
BEGIN
 PERFORM acquisition_private.require(json_typeof(root)='object' AND root OPERATOR(acquisition_private.->) 'formatVersion' IS NULL AND root OPERATOR(acquisition_private.->) 'format' IS NULL AND schema=acquisition_private.legacy_int(p_origin OPERATOR(acquisition_private.->) 'schema'),'Invalid legacy snapshot routing/schema');
 PERFORM acquisition_private.require(root OPERATOR(acquisition_private.->) 'characters' IS NOT NULL AND json_array_length(root OPERATOR(acquisition_private.->) 'characters')>0,'Empty legacy snapshot');
 PERFORM acquisition_private.legacy_unique(root OPERATOR(acquisition_private.->) 'characters','id',true);
 IF root OPERATOR(acquisition_private.->) 'activeCharacterId' IS NOT NULL THEN PERFORM acquisition_private.types(root,ARRAY['activeCharacterId']); END IF;
 FOR c IN SELECT * FROM acquisition_private.legacy_rows(root OPERATOR(acquisition_private.->) 'characters') LOOP
  PERFORM acquisition_private.legacy_fields(c,ARRAY['experience','creationExperience','xpAdjustment','chiBonusRanks'],ARRAY['creationComplete','manaEnabled','chiEnabled'],ARRAY[]::text[],ARRAY['abilityPointsOverride']);
  x:=c OPERATOR(acquisition_private.->) 'attributes';
  IF x IS NOT NULL THEN
   PERFORM acquisition_private.require(json_typeof(x)='object','Invalid legacy attributes');
   FOR y IN SELECT value FROM acquisition_private.object_raw_members(x) LOOP PERFORM acquisition_private.require(json_typeof(y)='object','Invalid legacy attribute'); PERFORM acquisition_private.legacy_fields(y,ARRAY['base','bonus']); END LOOP;
  END IF;
  PERFORM acquisition_private.legacy_unique(c OPERATOR(acquisition_private.->) 'skills','id',true);
  FOR x IN SELECT * FROM acquisition_private.legacy_rows(c OPERATOR(acquisition_private.->) 'skills') LOOP PERFORM acquisition_private.legacy_fields(x,ARRAY['rank','modifier'],ARRAY[]::text[],ARRAY[]::text[],ARRAY[]::text[],ARRAY['definitionId']); END LOOP;
  PERFORM acquisition_private.legacy_unique(c OPERATOR(acquisition_private.->) 'development','id',true,true);
  FOR x IN SELECT * FROM acquisition_private.legacy_rows(c OPERATOR(acquisition_private.->) 'development') LOOP PERFORM acquisition_private.legacy_fields(x,ARRAY['rank','option']); END LOOP;
  FOREACH k IN ARRAY ARRAY['developmentOverrides','customDevelopmentEntries'] LOOP
   PERFORM acquisition_private.legacy_unique(c OPERATOR(acquisition_private.->) k,'id',true,true);
   FOR x IN SELECT * FROM acquisition_private.legacy_rows(c OPERATOR(acquisition_private.->) k) LOOP
    PERFORM acquisition_private.legacy_fields(x,ARRAY['cost','maxRank'],ARRAY['incomplete','repeatable','perfectRoot'],ARRAY['costType']);
    IF x OPERATOR(acquisition_private.->) 'costType' IS NOT NULL THEN PERFORM acquisition_private.require(x OPERATOR(acquisition_private.->>) 'costType' IN ('XP','ABILITY'),'Invalid legacy cost type'); END IF;
    FOR y IN SELECT * FROM acquisition_private.legacy_rows(x OPERATOR(acquisition_private.->) 'abilityOptions') LOOP PERFORM acquisition_private.legacy_fields(y,ARRAY['value']); END LOOP;
   END LOOP;
  END LOOP;
  x:=c OPERATOR(acquisition_private.->) 'magic';
  IF x IS NOT NULL THEN
   PERFORM acquisition_private.require(json_typeof(x)='object','Invalid legacy magic'); PERFORM acquisition_private.legacy_fields(x,ARRAY['manaRank','power']);
   FOR y IN SELECT * FROM acquisition_private.legacy_rows(x OPERATOR(acquisition_private.->) 'schools') LOOP PERFORM acquisition_private.legacy_fields(y,ARRAY['rank','level'],ARRAY[]::text[],ARRAY['name']); END LOOP;
   PERFORM acquisition_private.legacy_unique(x OPERATOR(acquisition_private.->) 'spells','uid',false);
   FOR y IN SELECT * FROM acquisition_private.legacy_rows(x OPERATOR(acquisition_private.->) 'spells') LOOP PERFORM acquisition_private.legacy_fields(y,ARRAY['cost'],ARRAY['learned','custom','incomplete'],ARRAY[]::text[],ARRAY['xpOverride'],ARRAY['catalogId']); END LOOP;
  END IF;
  x:=c OPERATOR(acquisition_private.->) 'gear';
  IF x IS NOT NULL THEN
   PERFORM acquisition_private.require(json_typeof(x)='object','Invalid legacy gear'); PERFORM acquisition_private.legacy_fields(x,ARRAY[]::text[],ARRAY['loadAutomatic'],ARRAY[]::text[],ARRAY[]::text[],ARRAY[]::text[],ARRAY['loadManual']);
   PERFORM acquisition_private.legacy_unique(x OPERATOR(acquisition_private.->) 'items','uid',false);
   FOR y IN SELECT * FROM acquisition_private.legacy_rows(x OPERATOR(acquisition_private.->) 'items') LOOP PERFORM acquisition_private.legacy_fields(y,ARRAY['quantity','qty'],ARRAY['carried','custom'],ARRAY[]::text[],ARRAY[]::text[],ARRAY['catalogId'],ARRAY['load']); END LOOP;
  END IF;
  PERFORM acquisition_private.legacy_unique(c OPERATOR(acquisition_private.->) 'customResources','uid',false);
  FOR y IN SELECT * FROM acquisition_private.legacy_rows(c OPERATOR(acquisition_private.->) 'customResources') LOOP PERFORM acquisition_private.legacy_fields(y,ARRAY['current','maximum']); END LOOP;
  IF (c OPERATOR(acquisition_private.->) 'id')::text=(p_origin OPERATOR(acquisition_private.->) 'characterId')::text THEN selected:=c; n:=n+1; END IF;
 END LOOP;
 PERFORM acquisition_private.require(n=1,'Legacy backup character scope mismatch');
 x:=selected OPERATOR(acquisition_private.->) 'ruleset';
 IF json_typeof(x)='object' THEN
  PERFORM acquisition_private.require((acquisition_private.trim_string(to_json(acquisition_private.legacy_text(x OPERATOR(acquisition_private.->) 'id')))::text='""' OR acquisition_private.legacy_text(x OPERATOR(acquisition_private.->) 'id')='dubl') AND (acquisition_private.trim_string(to_json(acquisition_private.legacy_text(x OPERATOR(acquisition_private.->) 'version')))::text='""' OR acquisition_private.legacy_text(x OPERATOR(acquisition_private.->) 'version')='3.69'),'Wrong legacy ruleset');
 END IF;
 RETURN root;
END $$;
CREATE FUNCTION acquisition_private.legacy_school(p json) RETURNS json LANGUAGE plpgsql IMMUTABLE SET search_path='' AS $$
DECLARE clean text:=replace(acquisition_private.lower_string(acquisition_private.trim_string(p))::text,'ё','е'); name text;
 names text[]:=ARRAY['Боевая магия','Воплощение','Друид','Магия крови','Молитва','Некромантия','Ограждение','Призыв','Природа','Прорицание','Разрушение','Разум','Трансмутация'];
BEGIN
 clean:=CASE clean WHEN '"ограждения"' THEN '"ограждение"' WHEN '"молитвы"' THEN '"молитва"' WHEN '"некромант"' THEN '"некромантия"' WHEN '"друидичество"' THEN '"друид"' WHEN '"друидическая магия"' THEN '"друид"' WHEN '"боевая"' THEN '"боевая магия"' ELSE clean END;
 FOREACH name IN ARRAY names LOOP IF replace(to_json(lower(name))::text,'ё','е')=clean THEN RETURN to_json(name); END IF; END LOOP;
 RETURN NULL;
END $$;
CREATE FUNCTION acquisition_private.ownership_record(p_key json,p_kind text,p_rank integer,p_candidate json,p_custom boolean DEFAULT false) RETURNS json
LANGUAGE sql IMMUTABLE SET search_path='' AS $$ SELECT acquisition_private.canonical(json_build_object('ownershipKey',p_key,'kind',p_kind,'rank',p_rank,'candidateEntryId',p_candidate,'custom',p_custom)::text)::json $$;
CREATE FUNCTION acquisition_private.legacy_records(p_origin json,p_migration_id text,p_backup json) RETURNS json
LANGUAGE plpgsql IMMUTABLE SET search_path='' AS $$
DECLARE c json; x json; y json; selected json; result text:='['; rank integer; key json; candidate json; custom boolean; uid json; sequence integer:=0; schools json:='[]'; rows json; mana integer; old_mana integer:=0; spell_uids text[]:=ARRAY[]::text[]; school_names text[]:=ARRAY['Боевая магия','Воплощение','Друид','Магия крови','Молитва','Некромантия','Ограждение','Призыв','Природа','Прорицание','Разрушение','Разум','Трансмутация'];
BEGIN
 -- Stable generated nested IDs consume the decoder's sequence for every character, in
 -- custom-resource, spell, gear order. Only the selected character contributes ownership.
 FOR c IN SELECT * FROM acquisition_private.legacy_rows(p_backup OPERATOR(acquisition_private.->) 'characters') LOOP
  FOR x IN SELECT * FROM acquisition_private.legacy_rows(c OPERATOR(acquisition_private.->) 'customResources') LOOP
   IF acquisition_private.trim_string(acquisition_private.legacy_label(x OPERATOR(acquisition_private.->) 'uid'))::text='""' THEN sequence:=sequence+1; END IF;
  END LOOP;
  FOR x IN SELECT * FROM acquisition_private.legacy_rows(c OPERATOR(acquisition_private.->) 'magic' OPERATOR(acquisition_private.->) 'spells') LOOP
   uid:=acquisition_private.legacy_label(x OPERATOR(acquisition_private.->) 'uid');
   IF acquisition_private.trim_string(uid)::text='""' THEN uid:=to_json('legacy-generated:'||p_migration_id||':'||sequence); sequence:=sequence+1; END IF;
   IF (c OPERATOR(acquisition_private.->) 'id')::text=(p_origin OPERATOR(acquisition_private.->) 'characterId')::text THEN
    IF uid::text=ANY(spell_uids) THEN CONTINUE; END IF; spell_uids:=array_append(spell_uids,uid::text);
    IF NOT acquisition_private.legacy_bool(x OPERATOR(acquisition_private.->) 'learned',true) THEN CONTINUE; END IF;
    candidate:=acquisition_private.legacy_label(x OPERATOR(acquisition_private.->) 'catalogId'); IF acquisition_private.trim_string(candidate)::text='""' THEN candidate:=NULL; END IF;
    rows:=coalesce(rows,'[]'::json); rows:=(substr(rows::text,1,length(rows::text)-1)||CASE WHEN rows::text='[]' THEN '' ELSE ',' END||acquisition_private.ownership_record(acquisition_private.prefix_string('spell:',uid),'SPELL',1,candidate,acquisition_private.legacy_bool(x OPERATOR(acquisition_private.->) 'custom'))::text||']')::json;
   END IF;
  END LOOP;
  FOR x IN SELECT * FROM acquisition_private.legacy_rows(c OPERATOR(acquisition_private.->) 'gear' OPERATOR(acquisition_private.->) 'items') LOOP
   IF acquisition_private.trim_string(acquisition_private.legacy_label(x OPERATOR(acquisition_private.->) 'uid'))::text='""' THEN sequence:=sequence+1; END IF;
  END LOOP;
  IF (c OPERATOR(acquisition_private.->) 'id')::text=(p_origin OPERATOR(acquisition_private.->) 'characterId')::text THEN selected:=c; END IF;
 END LOOP;
 FOR x IN SELECT value FROM acquisition_private.legacy_rows(selected OPERATOR(acquisition_private.->) 'development') value ORDER BY (acquisition_private.parse(acquisition_private.trim_string(value OPERATOR(acquisition_private.->) 'id')::text)).sort_key LOOP
  key:=acquisition_private.trim_string(x OPERATOR(acquisition_private.->) 'id'); rank:=greatest(acquisition_private.legacy_int(x OPERATOR(acquisition_private.->) 'rank'),0);
  IF key::text='"feat_bf7b04eb68961725"' THEN old_mana:=least(rank,5); CONTINUE; END IF;
  IF rank=0 THEN CONTINUE; END IF;
  custom:=EXISTS(SELECT 1 FROM acquisition_private.legacy_rows(selected OPERATOR(acquisition_private.->) 'customDevelopmentEntries') t WHERE acquisition_private.trim_string(t OPERATOR(acquisition_private.->) 'id')::text=key::text);
  result:=result||CASE WHEN result='[' THEN '' ELSE ',' END||acquisition_private.ownership_record(acquisition_private.prefix_string('development:',key),'DEVELOPMENT',rank,key,custom)::text;
 END LOOP;
 FOR x IN SELECT value FROM acquisition_private.legacy_rows(selected OPERATOR(acquisition_private.->) 'skills') value ORDER BY (acquisition_private.parse((value OPERATOR(acquisition_private.->) 'id')::text)).sort_key LOOP
  rank:=greatest(0,least(acquisition_private.legacy_int(x OPERATOR(acquisition_private.->) 'rank'),10)); IF rank=0 THEN CONTINUE; END IF;
  candidate:=acquisition_private.legacy_label(x OPERATOR(acquisition_private.->) 'definitionId'); IF acquisition_private.trim_string(candidate)::text='""' THEN candidate:=NULL; END IF;
  result:=result||CASE WHEN result='[' THEN '' ELSE ',' END||acquisition_private.ownership_record(acquisition_private.prefix_string('skill:',x OPERATOR(acquisition_private.->) 'id'),'SKILL',rank,candidate)::text;
 END LOOP;
 mana:=greatest(old_mana,greatest(0,least(acquisition_private.legacy_int(selected OPERATOR(acquisition_private.->) 'magic' OPERATOR(acquisition_private.->) 'manaRank'),5)));
 IF mana>0 THEN result:=result||CASE WHEN result='[' THEN '' ELSE ',' END||acquisition_private.ownership_record('"mana"','MANA',mana,'"mana:rank"')::text; END IF;
 rank:=greatest(0,least(acquisition_private.legacy_int(selected OPERATOR(acquisition_private.->) 'chiBonusRanks'),10));
 IF rank>0 THEN result:=result||CASE WHEN result='[' THEN '' ELSE ',' END||acquisition_private.ownership_record('"chi-bonus"','CHI_BONUS',rank,'"chi:bonus-ranks"')::text; END IF;
 -- School normalization merges aliases by greatest rank, retaining first ties and the
 -- established catalog ordering. This is original ownership extraction, never repricing.
 FOR x IN SELECT * FROM acquisition_private.legacy_rows(selected OPERATOR(acquisition_private.->) 'magic' OPERATOR(acquisition_private.->) 'schools') LOOP
  key:=coalesce(acquisition_private.legacy_school(acquisition_private.legacy_label(x OPERATOR(acquisition_private.->) 'name','Школа')),acquisition_private.trim_string(acquisition_private.legacy_label(x OPERATOR(acquisition_private.->) 'name','Школа')));
  IF key::text='""' THEN CONTINUE; END IF;
  rank:=greatest(acquisition_private.legacy_int(x OPERATOR(acquisition_private.->) 'rank',acquisition_private.legacy_int(x OPERATOR(acquisition_private.->) 'level')),0);
  y:=acquisition_private.canonical(json_build_object('name',key,'rank',rank)::text)::json;
  IF NOT EXISTS(SELECT 1 FROM json_array_elements(schools) s WHERE acquisition_private.lower_string(s.value OPERATOR(acquisition_private.->) 'name')::text=acquisition_private.lower_string(key)::text) THEN schools:=(substr(schools::text,1,length(schools::text)-1)||CASE WHEN schools::text='[]' THEN '' ELSE ',' END||y::text||']')::json;
  ELSE SELECT json_agg(CASE WHEN acquisition_private.lower_string(s.value OPERATOR(acquisition_private.->) 'name')::text=acquisition_private.lower_string(key)::text AND (s.value OPERATOR(acquisition_private.->>) 'rank')::integer<rank THEN y ELSE s.value END ORDER BY ord) INTO schools FROM json_array_elements(schools) WITH ORDINALITY s(value,ord); END IF;
 END LOOP;
 FOR x IN SELECT value FROM json_array_elements(schools) ORDER BY coalesce((SELECT ord FROM unnest(school_names) WITH ORDINALITY names(name,ord) WHERE to_json(name)::text=(value OPERATOR(acquisition_private.->) 'name')::text),14),(acquisition_private.parse(acquisition_private.lower_string(value OPERATOR(acquisition_private.->) 'name')::text)).sort_key LOOP
  rank:=(x OPERATOR(acquisition_private.->>) 'rank')::integer; IF rank=0 THEN CONTINUE; END IF; key:=x OPERATOR(acquisition_private.->) 'name';
  result:=result||CASE WHEN result='[' THEN '' ELSE ',' END||acquisition_private.ownership_record(acquisition_private.prefix_string('school:'||(octet_length((acquisition_private.parse(key::text)).sort_key)/2)::text||':',key),'SCHOOL',rank,NULL)::text;
 END LOOP;
 FOR x IN SELECT value FROM json_array_elements(coalesce(rows,'[]'::json)) LOOP result:=result||CASE WHEN result='[' THEN '' ELSE ',' END||x::text; END LOOP;
 RETURN (result||']')::json;
END $$;

CREATE FUNCTION acquisition_private.legacy_school_ref(p json) RETURNS void LANGUAGE plpgsql IMMUTABLE SET search_path='' AS $$ BEGIN
 PERFORM acquisition_private.keys(p,ARRAY['packId','entryId']);
 PERFORM acquisition_private.require((p OPERATOR(acquisition_private.->) 'packId')::text='"dubl-3.69"' AND p OPERATOR(acquisition_private.->>) 'entryId' IN ('magic-school:боевая магия','magic-school:воплощение','magic-school:друид','magic-school:магия крови','magic-school:молитва','magic-school:некромантия','magic-school:ограждение','magic-school:призыв','magic-school:природа','magic-school:прорицание','magic-school:разрушение','magic-school:разум','magic-school:трансмутация'),'Invalid resolved school identity');
END $$;
CREATE FUNCTION acquisition_private.holding(p_instances json,p_id json) RETURNS json LANGUAGE sql IMMUTABLE SET search_path='' AS $$
 SELECT value FROM json_array_elements(p_instances) WHERE (value OPERATOR(acquisition_private.->) 'instanceId')::text=p_id::text $$;
CREATE FUNCTION acquisition_private.without_holding(p_instances json,p_id json) RETURNS text LANGUAGE sql IMMUTABLE SET search_path='' AS $$
 SELECT acquisition_private.canonical(coalesce(json_agg(value ORDER BY ord),'[]'::json)::text) FROM json_array_elements(p_instances) WITH ORDINALITY x(value,ord) WHERE (value OPERATOR(acquisition_private.->) 'instanceId')::text<>p_id::text $$;
CREATE FUNCTION acquisition_private.instance_shapes(p_instances json) RETURNS void LANGUAGE plpgsql IMMUTABLE SET search_path='' AS $$
DECLARE x json; o json; k text; seen text[]:=ARRAY[]::text[]; BEGIN
 PERFORM acquisition_private.require(json_typeof(p_instances)='array','Expected recorded instances');
 FOR x IN SELECT value FROM json_array_elements(p_instances) LOOP
  PERFORM acquisition_private.keys(x,ARRAY['instanceId','definition','rank','parameters','abilityOptionId','origin']); PERFORM acquisition_private.nonblank(x OPERATOR(acquisition_private.->) 'instanceId');
  PERFORM acquisition_private.require(NOT (x OPERATOR(acquisition_private.->) 'instanceId')::text=ANY(seen),'Duplicate recorded instance'); seen:=array_append(seen,(x OPERATOR(acquisition_private.->) 'instanceId')::text);
  PERFORM acquisition_private.keys(x OPERATOR(acquisition_private.->) 'definition',ARRAY['packId','entryId']); PERFORM acquisition_private.nonblank(x OPERATOR(acquisition_private.->) 'definition' OPERATOR(acquisition_private.->) 'packId'); PERFORM acquisition_private.nonblank(x OPERATOR(acquisition_private.->) 'definition' OPERATOR(acquisition_private.->) 'entryId');
  PERFORM acquisition_private.types(x,ARRAY[]::text[],ARRAY['rank']); PERFORM acquisition_private.require((x OPERATOR(acquisition_private.->>) 'rank')::integer>0,'Invalid recorded rank'); PERFORM acquisition_private.parameter_map(x OPERATOR(acquisition_private.->) 'parameters');
  PERFORM acquisition_private.require(json_typeof(x OPERATOR(acquisition_private.->) 'abilityOptionId') IN ('string','null'),'Invalid recorded option'); IF (x OPERATOR(acquisition_private.->) 'abilityOptionId')::text<>'null' THEN PERFORM acquisition_private.nonblank(x OPERATOR(acquisition_private.->) 'abilityOptionId'); END IF;
  o:=x OPERATOR(acquisition_private.->) 'origin';
  IF o OPERATOR(acquisition_private.->>) 'type'='New' THEN PERFORM acquisition_private.keys(o,ARRAY['type']);
  ELSIF o OPERATOR(acquisition_private.->>) 'type'='Legacy' THEN
   PERFORM acquisition_private.keys(o,ARRAY['type','characterId','schema','sourceRevision','sourceFingerprint','ownershipKey']); FOREACH k IN ARRAY ARRAY['type','characterId','schema','sourceRevision','sourceFingerprint','ownershipKey'] LOOP PERFORM acquisition_private.nonblank(o OPERATOR(acquisition_private.->) k); END LOOP;
  ELSE RAISE EXCEPTION 'Invalid recorded origin'; END IF;
 END LOOP;
END $$;
CREATE FUNCTION acquisition_private.recorded_holding_mutation(p_root json,p_entry json,p_current json,p_identity json,p_reverse boolean DEFAULT false) RETURNS json
LANGUAGE plpgsql IMMUTABLE SET search_path='' AS $$
DECLARE receipt json; request json; payload json; proof json; before_projection json; after_projection json; patch json; patch_projection json; before_holding json; after_holding json; record json; resolution json; item record; id json:=p_identity OPERATOR(acquisition_private.->) 'instanceId'; kind text:=p_entry OPERATOR(acquisition_private.->>) 'type'; BEGIN
 SELECT value INTO receipt FROM json_array_elements(p_root OPERATOR(acquisition_private.->) 'receipts') WHERE value OPERATOR(acquisition_private.->>) 'operationId'=p_entry OPERATOR(acquisition_private.->>) 'operationId';
 request:=acquisition_private.wire_json(receipt OPERATOR(acquisition_private.->>) 'requestCanonical'); PERFORM acquisition_private.keys(request,ARRAY['type','characterId','expectedRevision','reason','payload']);
 PERFORM acquisition_private.require(request OPERATOR(acquisition_private.->>) 'type'='CanonicalMutation' AND (request OPERATOR(acquisition_private.->) 'characterId')::text=(p_root OPERATOR(acquisition_private.->) 'characterId')::text AND (request OPERATOR(acquisition_private.->) 'expectedRevision')::text=(receipt OPERATOR(acquisition_private.->) 'beforeRevision')::text,'Copied mutation command scope differs'); PERFORM acquisition_private.nonblank(request OPERATOR(acquisition_private.->) 'reason');
 payload:=request OPERATOR(acquisition_private.->) 'payload'; PERFORM acquisition_private.require(payload OPERATOR(acquisition_private.->>) 'kind'=kind,'Copied mutation payload differs');
 proof:=acquisition_private.wire_json(p_entry OPERATOR(acquisition_private.->>) 'evidenceCanonical'); PERFORM acquisition_private.keys(proof,ARRAY['schema','command','before','after']); PERFORM acquisition_private.require(proof OPERATOR(acquisition_private.->>) 'schema'='mutation-evidence-v1' AND (proof OPERATOR(acquisition_private.->) 'command')::text=request::text,'Copied mutation proof differs');
 before_projection:=proof OPERATOR(acquisition_private.->) 'before'; after_projection:=proof OPERATOR(acquisition_private.->) 'after';
 PERFORM acquisition_private.keys(before_projection,ARRAY['characterSnapshot','instances','extras','creationPhase']); PERFORM acquisition_private.keys(after_projection,ARRAY['characterSnapshot','instances','extras','creationPhase']);
 PERFORM acquisition_private.instance_shapes(before_projection OPERATOR(acquisition_private.->) 'instances'); PERFORM acquisition_private.instance_shapes(after_projection OPERATOR(acquisition_private.->) 'instances');
 before_holding:=acquisition_private.holding(before_projection OPERATOR(acquisition_private.->) 'instances',id); after_holding:=acquisition_private.holding(after_projection OPERATOR(acquisition_private.->) 'instances',id);
 IF kind='ManualCorrection' THEN
  PERFORM acquisition_private.keys(payload,ARRAY['kind','patch','delta']); PERFORM acquisition_private.require(acquisition_private.amount(payload OPERATOR(acquisition_private.->) 'delta')=acquisition_private.amount(p_entry OPERATOR(acquisition_private.->) 'amountDelta'),'Copied correction amount differs');
  patch:=payload OPERATOR(acquisition_private.->) 'patch'; PERFORM acquisition_private.keys(patch,ARRAY['projection','replacesInstances','replacesExtras']); PERFORM acquisition_private.types(patch,ARRAY[]::text[],ARRAY[]::text[],ARRAY['replacesInstances','replacesExtras']);
  patch_projection:=patch OPERATOR(acquisition_private.->) 'projection'; PERFORM acquisition_private.keys(patch_projection,ARRAY['characterSnapshot','instances','extras','creationPhase']);
  PERFORM acquisition_private.require((after_projection OPERATOR(acquisition_private.->) 'characterSnapshot')::text=(patch_projection OPERATOR(acquisition_private.->) 'characterSnapshot')::text AND (after_projection OPERATOR(acquisition_private.->) 'creationPhase')::text=(patch_projection OPERATOR(acquisition_private.->) 'creationPhase')::text AND (before_projection OPERATOR(acquisition_private.->) 'creationPhase')::text=(after_projection OPERATOR(acquisition_private.->) 'creationPhase')::text,'Copied correction projection differs');
  PERFORM acquisition_private.require((after_projection OPERATOR(acquisition_private.->) 'instances')::text=(CASE WHEN patch OPERATOR(acquisition_private.->>) 'replacesInstances'='true' THEN patch_projection ELSE before_projection END OPERATOR(acquisition_private.->) 'instances')::text AND (after_projection OPERATOR(acquisition_private.->) 'extras')::text=(CASE WHEN patch OPERATOR(acquisition_private.->>) 'replacesExtras'='true' THEN patch_projection ELSE before_projection END OPERATOR(acquisition_private.->) 'extras')::text,'Copied correction differs from captured patch');
 ELSIF kind='ConfirmLegacyParameter' THEN
  PERFORM acquisition_private.keys(payload,ARRAY['kind','instanceId','parameters','resolution']); PERFORM acquisition_private.require(acquisition_private.amount(p_entry OPERATOR(acquisition_private.->) 'amountDelta')=ARRAY[0::bigint,0::bigint],'Copied confirmation amount differs');
  IF (payload OPERATOR(acquisition_private.->) 'instanceId')::text=id::text THEN
   PERFORM acquisition_private.require(acquisition_private.remove_field(before_projection,'instances')::text=acquisition_private.remove_field(after_projection,'instances')::text AND acquisition_private.without_holding(before_projection OPERATOR(acquisition_private.->) 'instances',id)=acquisition_private.without_holding(after_projection OPERATOR(acquisition_private.->) 'instances',id) AND after_holding IS NOT NULL AND (after_holding OPERATOR(acquisition_private.->) 'parameters')::text=(payload OPERATOR(acquisition_private.->) 'parameters')::text,'Copied confirmation projection differs');
   IF before_holding IS NOT NULL THEN
    PERFORM acquisition_private.require((payload OPERATOR(acquisition_private.->) 'resolution')::text='null' AND acquisition_private.remove_field(before_holding,'parameters')::text=acquisition_private.remove_field(after_holding,'parameters')::text,'Copied confirmation changed identity/rank/option');
    FOR item IN SELECT * FROM acquisition_private.object_raw_members(before_holding OPERATOR(acquisition_private.->) 'parameters') LOOP PERFORM acquisition_private.require(acquisition_private.field_raw(after_holding OPERATOR(acquisition_private.->) 'parameters',item.key)::text=item.value::text,'Copied confirmation replaced a known parameter'); END LOOP;
   ELSE
    SELECT value INTO record FROM json_array_elements(p_root OPERATOR(acquisition_private.->) 'ledger' OPERATOR(acquisition_private.->) 'opening' OPERATOR(acquisition_private.->) 'legacyMigration' OPERATOR(acquisition_private.->) 'ownershipRecords') WHERE (value OPERATOR(acquisition_private.->) 'ownershipKey')::text=(p_identity OPERATOR(acquisition_private.->) 'origin' OPERATOR(acquisition_private.->) 'ownershipKey')::text;
    resolution:=payload OPERATOR(acquisition_private.->) 'resolution'; PERFORM acquisition_private.keys(resolution,ARRAY['ownershipKey','packId','entryId','rank']);
    PERFORM acquisition_private.require((resolution OPERATOR(acquisition_private.->) 'ownershipKey')::text=(record OPERATOR(acquisition_private.->) 'ownershipKey')::text AND (resolution OPERATOR(acquisition_private.->) 'packId')::text=(p_identity OPERATOR(acquisition_private.->) 'definition' OPERATOR(acquisition_private.->) 'packId')::text AND (resolution OPERATOR(acquisition_private.->) 'entryId')::text=(p_identity OPERATOR(acquisition_private.->) 'definition' OPERATOR(acquisition_private.->) 'entryId')::text AND (resolution OPERATOR(acquisition_private.->) 'rank')::text=(record OPERATOR(acquisition_private.->) 'rank')::text AND (after_holding OPERATOR(acquisition_private.->) 'rank')::text=(record OPERATOR(acquisition_private.->) 'rank')::text,'Copied confirmation differs from original ownership');
   END IF;
  ELSE PERFORM acquisition_private.require(before_holding::text IS NOT DISTINCT FROM after_holding::text,'Unrelated confirmation changed copied holding'); END IF;
 ELSE RAISE EXCEPTION 'Invalid copied holding mutation'; END IF;
 IF p_reverse THEN proof:=before_holding; before_holding:=after_holding; after_holding:=proof; END IF;
 PERFORM acquisition_private.require(before_holding::text IS NOT DISTINCT FROM p_current::text,'Copied mutation before holding differs');
 PERFORM acquisition_private.require(after_holding IS NULL OR ((after_holding OPERATOR(acquisition_private.->) 'instanceId')::text=(p_identity OPERATOR(acquisition_private.->) 'instanceId')::text AND (after_holding OPERATOR(acquisition_private.->) 'definition')::text=(p_identity OPERATOR(acquisition_private.->) 'definition')::text AND (after_holding OPERATOR(acquisition_private.->) 'origin')::text=(p_identity OPERATOR(acquisition_private.->) 'origin')::text),'Copied mutation changed holding identity');
 PERFORM acquisition_private.require(p_reverse OR kind<>'ManualCorrection' OR p_current IS NOT NULL OR after_holding IS NULL,'Correction cannot recreate a removed holding');
 RETURN after_holding;
END $$;
CREATE FUNCTION acquisition_private.copied_holding_chain(p_root json,p_initial json,p_kind text,p_start integer) RETURNS void
LANGUAGE plpgsql IMMUTABLE SET search_path='' AS $$ DECLARE expected json:=p_initial; e json; change json; original json; id json:=p_initial OPERATOR(acquisition_private.->) 'instanceId'; BEGIN
 FOR e IN SELECT value FROM json_array_elements(p_root OPERATOR(acquisition_private.->) 'ledger' OPERATOR(acquisition_private.->) 'entries') WITH ORDINALITY x(value,ord) WHERE ord>p_start ORDER BY ord LOOP
  IF EXISTS(SELECT 1 FROM json_array_elements(e OPERATOR(acquisition_private.->) 'changes') c WHERE (c.value OPERATOR(acquisition_private.->) 'instanceId')::text=id::text) THEN
   PERFORM acquisition_private.require(e OPERATOR(acquisition_private.->>) 'type' IN ('Purchase','Undo'),'Unrecorded copied holding change');
   FOR change IN SELECT value FROM json_array_elements(e OPERATOR(acquisition_private.->) 'changes') WHERE (value OPERATOR(acquisition_private.->) 'instanceId')::text=id::text LOOP
    PERFORM acquisition_private.require(expected IS NOT NULL AND change OPERATOR(acquisition_private.->>) 'kind'=p_kind AND (change OPERATOR(acquisition_private.->) 'definition')::text=(expected OPERATOR(acquisition_private.->) 'definition')::text AND (change OPERATOR(acquisition_private.->) 'parameters')::text=(expected OPERATOR(acquisition_private.->) 'parameters')::text AND (change OPERATOR(acquisition_private.->) 'optionId')::text=(expected OPERATOR(acquisition_private.->) 'abilityOptionId')::text AND (change OPERATOR(acquisition_private.->) 'fromRank')::text=(expected OPERATOR(acquisition_private.->) 'rank')::text AND (change OPERATOR(acquisition_private.->>) 'toRank')::integer>0,'Paid copied change lacks exact preceding holding');
    expected:=acquisition_private.set_field(expected,'rank',change OPERATOR(acquisition_private.->) 'toRank');
   END LOOP;
  ELSIF e OPERATOR(acquisition_private.->>) 'type' IN ('ManualCorrection','ConfirmLegacyParameter') THEN expected:=acquisition_private.recorded_holding_mutation(p_root,e,expected,p_initial);
  ELSIF e OPERATOR(acquisition_private.->>) 'type'='Undo' THEN
   SELECT value INTO original FROM json_array_elements(p_root OPERATOR(acquisition_private.->) 'ledger' OPERATOR(acquisition_private.->) 'entries') WHERE value OPERATOR(acquisition_private.->>) 'operationId'=e OPERATOR(acquisition_private.->>) 'reversesId';
   IF original OPERATOR(acquisition_private.->>) 'type' IN ('ManualCorrection','ConfirmLegacyParameter') THEN expected:=acquisition_private.recorded_holding_mutation(p_root,original,expected,p_initial,true); END IF;
  END IF;
 END LOOP;
 PERFORM acquisition_private.require(acquisition_private.holding(p_root OPERATOR(acquisition_private.->) 'instances',id)::text IS NOT DISTINCT FROM expected::text,'Copied holding removed or changed without exact recorded proof');
END $$;

CREATE FUNCTION acquisition_private.legacy_instance(p_holding json,p_opening json) RETURNS void LANGUAGE plpgsql IMMUTABLE SET search_path='' AS $$
DECLARE holding json:=p_holding; opening json:=p_opening; origin json:=p_opening OPERATOR(acquisition_private.->) 'legacyMigration' OPERATOR(acquisition_private.->) 'origin'; ownership json:=p_opening OPERATOR(acquisition_private.->) 'legacyMigration' OPERATOR(acquisition_private.->) 'ownershipRecords'; captured json; rec json; identity json;
BEGIN
 PERFORM acquisition_private.instance_shapes(('['||p_holding::text||']')::json);
   captured:=holding OPERATOR(acquisition_private.->) 'origin';
   PERFORM acquisition_private.require((captured OPERATOR(acquisition_private.->) 'characterId')::text=(origin OPERATOR(acquisition_private.->) 'characterId')::text AND captured OPERATOR(acquisition_private.->>) 'schema'=origin OPERATOR(acquisition_private.->>) 'schema' AND (captured OPERATOR(acquisition_private.->) 'sourceRevision')::text=(origin OPERATOR(acquisition_private.->) 'sourceRevision')::text AND (captured OPERATOR(acquisition_private.->) 'sourceFingerprint')::text=(opening OPERATOR(acquisition_private.->) 'sourceFingerprint')::text,'Legacy instance provenance mismatch');
   SELECT value INTO rec FROM json_array_elements(ownership) o WHERE (o.value OPERATOR(acquisition_private.->) 'ownershipKey')::text=(captured OPERATOR(acquisition_private.->) 'ownershipKey')::text;
   PERFORM acquisition_private.require(rec IS NOT NULL,'Legacy instance lacks original ownership evidence');
   PERFORM acquisition_private.require((rec OPERATOR(acquisition_private.->) 'candidateEntryId')::text='null' OR (rec OPERATOR(acquisition_private.->) 'candidateEntryId')::text=(holding OPERATOR(acquisition_private.->) 'definition' OPERATOR(acquisition_private.->) 'entryId')::text,'Legacy instance differs from original candidate ID');
   IF rec OPERATOR(acquisition_private.->>) 'kind'='SCHOOL' THEN PERFORM acquisition_private.legacy_school_ref(holding OPERATOR(acquisition_private.->) 'definition'); END IF;
   IF acquisition_private.field_raw(origin OPERATOR(acquisition_private.->) 'ownershipMappings',captured OPERATOR(acquisition_private.->) 'ownershipKey') IS NOT NULL THEN
    PERFORM acquisition_private.require((acquisition_private.field_raw(origin OPERATOR(acquisition_private.->) 'ownershipMappings',captured OPERATOR(acquisition_private.->) 'ownershipKey'))::text=(holding OPERATOR(acquisition_private.->) 'definition')::text,'Legacy mapping differs from instance');
   END IF;
   identity:=acquisition_private.string_from_units(acquisition_private.utf16('legacy:')||acquisition_private.string_frame(captured OPERATOR(acquisition_private.->) 'characterId')||acquisition_private.string_frame(captured OPERATOR(acquisition_private.->) 'schema')||acquisition_private.string_frame(captured OPERATOR(acquisition_private.->) 'sourceRevision')||acquisition_private.string_frame(captured OPERATOR(acquisition_private.->) 'sourceFingerprint')||acquisition_private.string_frame(captured OPERATOR(acquisition_private.->) 'ownershipKey')||acquisition_private.string_frame(holding OPERATOR(acquisition_private.->) 'definition' OPERATOR(acquisition_private.->) 'packId')||acquisition_private.string_frame(holding OPERATOR(acquisition_private.->) 'definition' OPERATOR(acquisition_private.->) 'entryId'));
   PERFORM acquisition_private.require((holding OPERATOR(acquisition_private.->) 'instanceId')::text=identity::text,'Legacy instance identity mismatch');
END $$;

CREATE FUNCTION acquisition_private.validate_wire(p_raw text,p_pool json DEFAULT NULL,p_members text[] DEFAULT NULL,p_path text[] DEFAULT ARRAY[]::text[]) RETURNS void
LANGUAGE plpgsql IMMUTABLE SET search_path='' AS $$
DECLARE root json:=acquisition_private.wire_json(p_raw); pool json; members text[]; rec json; proof json;
 opening json; entries json; receipts json; e json; r json; request json; evidence json; snapshot json; ch json;
 historic json; source json; before_copy json; source_id text; dest text; import_id text; inherited boolean;
 n integer; i integer; revision bigint:=0; before_revision bigint; after_revision bigint; amounts bigint[]; delta bigint[]; cost bigint[]; remaining bigint[];
 seen text[]:=ARRAY[]::text[]; reversed text[]:=ARRAY[]::text[]; op text; kind text; reverse_id text; original json;
 branches text[]; selected json; displaced json; server json; expected bigint; prior_entries json:='[]'; prefix json;
 member_id text; last_member text; projection text; legacy json; origin json; fingerprint text; copy_ancestry json:='[]';
 backup json; ownership json; mapping record; holding json; captured json; current_holding json; identity text; holding_kind text; admitted_holdings text[]:=ARRAY[]::text[];
BEGIN
 PERFORM acquisition_private.require(octet_length(p_raw)<=8388608,'Wire payload exceeds bounded server limit');
 PERFORM acquisition_private.keys(root,ARRAY['formatVersion','characterId','characterSnapshot','extras','instances','ledger','receipts','revision','creationPhase'],ARRAY['copySources','syncProofs']);
 PERFORM acquisition_private.require((root OPERATOR(acquisition_private.->) 'formatVersion')::text='1','Unsupported acquisition format');
 PERFORM acquisition_private.nonblank(root OPERATOR(acquisition_private.->) 'characterId');
 -- This identity must fit the RPC/database text key. Free-text values below retain
 -- their lexical JSON representation, including supported NUL/surrogate escapes.
 PERFORM acquisition_private.text(root OPERATOR(acquisition_private.->) 'characterId');
 PERFORM acquisition_private.decimal(root OPERATOR(acquisition_private.->) 'revision');
 PERFORM acquisition_private.require(root OPERATOR(acquisition_private.->>) 'creationPhase' IN ('Creating','Finalized'),'Unsupported creation phase');
 snapshot:=root OPERATOR(acquisition_private.->) 'characterSnapshot'; PERFORM acquisition_private.keys(snapshot,ARRAY['schema','activeCharacterId','characters']);
 PERFORM acquisition_private.require((snapshot OPERATOR(acquisition_private.->) 'schema')::text='11' AND json_typeof(snapshot OPERATOR(acquisition_private.->) 'characters')='array' AND json_array_length(snapshot OPERATOR(acquisition_private.->) 'characters')=1,'Unsupported embedded character snapshot');
 ch:=snapshot OPERATOR(acquisition_private.->) 'characters' OPERATOR(acquisition_private.->) 0;
 PERFORM acquisition_private.require((snapshot OPERATOR(acquisition_private.->) 'activeCharacterId')::text=(root OPERATOR(acquisition_private.->) 'characterId')::text AND (ch OPERATOR(acquisition_private.->) 'id')::text=(root OPERATOR(acquisition_private.->) 'characterId')::text,'Character scope mismatch');
 PERFORM acquisition_private.require((ch OPERATOR(acquisition_private.->) 'ruleset')::text=acquisition_private.canonical('{"id":"dubl","version":"3.69"}') AND
  (ch OPERATOR(acquisition_private.->) 'creationComplete')::text=CASE WHEN root OPERATOR(acquisition_private.->>) 'creationPhase'='Finalized' THEN 'true' ELSE 'false' END,'Character ruleset/creation phase mismatch');
 PERFORM acquisition_private.keys(ch,ARRAY['id','ruleset','name','concept','experience','creationExperience','creationComplete','xpAdjustment','size','legs','hpCurrent','enduranceCurrent','manaEnabled','manaCurrent','manaMaximum','chiEnabled','chiCurrent','chiBonusRanks','customResources','attributes','skills','hiddenSkillIds','disabledSkillEffectIds','development','developmentOverrides','customDevelopmentEntries','magic','gear'],
  ARRAY['abilityPointsOverride','healthMaximumOverride','enduranceMaximumOverride','manaMaximumOverride']);
 PERFORM acquisition_private.snapshot_shape(snapshot);
 PERFORM acquisition_private.keys(root OPERATOR(acquisition_private.->) 'extras',ARRAY['activeConditions','conditionOverrides','customConditions','developmentGroups','hiddenResourceIds','noteEntries','notes','preferredSkillAttributes','skillGroups']);
 PERFORM acquisition_private.types(root OPERATOR(acquisition_private.->) 'extras',ARRAY['conditionOverrides','customConditions','developmentGroups','notes','skillGroups'],ARRAY[]::text[],ARRAY[]::text[],ARRAY['activeConditions','hiddenResourceIds','noteEntries']);
 PERFORM acquisition_private.enum_list(root OPERATOR(acquisition_private.->) 'extras' OPERATOR(acquisition_private.->) 'activeConditions',ARRAY['HELPLESS','PARALYZED','UNCONSCIOUS','BLINDNESS','DAZED','DAZZLED','DEAFNESS','ENTANGLED','WEAKNESS','EXHAUSTION','TIRED','INVISIBILITY','NAUSEA','PRONE','SHOCK','FEAR','PANIC','SICKNESS','STAGGERED','STUNNED','POISONED','INSPIRED','DEMORALIZED'],true);
 PERFORM acquisition_private.enum_list(root OPERATOR(acquisition_private.->) 'extras' OPERATOR(acquisition_private.->) 'hiddenResourceIds',ARRAY['HEALTH','ENDURANCE','MANA','CHI'],true);
 PERFORM acquisition_private.string_map(root OPERATOR(acquisition_private.->) 'extras' OPERATOR(acquisition_private.->) 'preferredSkillAttributes');
 FOR rec IN SELECT value FROM acquisition_private.object_members(root OPERATOR(acquisition_private.->) 'extras' OPERATOR(acquisition_private.->) 'preferredSkillAttributes') LOOP
  PERFORM acquisition_private.require(acquisition_private.text(rec) IN ('CHARISMA','CONSTITUTION','DEXTERITY','INTELLIGENCE','PERCEPTION','SPEED','STRENGTH','WILL'),'Unknown preferred skill attribute');
 END LOOP;
 PERFORM acquisition_private.extra_records(root OPERATOR(acquisition_private.->) 'extras' OPERATOR(acquisition_private.->) 'conditionOverrides','override');
 PERFORM acquisition_private.extra_records(root OPERATOR(acquisition_private.->) 'extras' OPERATOR(acquisition_private.->) 'customConditions','custom');
 PERFORM acquisition_private.extra_records(root OPERATOR(acquisition_private.->) 'extras' OPERATOR(acquisition_private.->) 'skillGroups','group');
 PERFORM acquisition_private.extra_records(root OPERATOR(acquisition_private.->) 'extras' OPERATOR(acquisition_private.->) 'developmentGroups','group');
 FOR rec IN SELECT value FROM json_array_elements(root OPERATOR(acquisition_private.->) 'extras' OPERATOR(acquisition_private.->) 'noteEntries') LOOP
  PERFORM acquisition_private.keys(rec,ARRAY['id','title','body']);
  PERFORM acquisition_private.require(json_typeof(rec OPERATOR(acquisition_private.->) 'id')='string' AND json_typeof(rec OPERATOR(acquisition_private.->) 'title')='string' AND json_typeof(rec OPERATOR(acquisition_private.->) 'body')='string','Malformed note');
 END LOOP;
 PERFORM acquisition_private.require(json_typeof(root OPERATOR(acquisition_private.->) 'instances')='array','Expected instances');
 FOR rec IN SELECT value FROM json_array_elements(root OPERATOR(acquisition_private.->) 'instances') LOOP
  PERFORM acquisition_private.keys(rec,ARRAY['instanceId','definition','rank','parameters','abilityOptionId','origin']);
  PERFORM acquisition_private.nonblank(rec OPERATOR(acquisition_private.->) 'instanceId');
  PERFORM acquisition_private.keys(rec OPERATOR(acquisition_private.->) 'definition',ARRAY['packId','entryId']);
  PERFORM acquisition_private.nonblank(rec OPERATOR(acquisition_private.->) 'definition' OPERATOR(acquisition_private.->) 'packId'); PERFORM acquisition_private.nonblank(rec OPERATOR(acquisition_private.->) 'definition' OPERATOR(acquisition_private.->) 'entryId');
  PERFORM acquisition_private.require(json_typeof(rec OPERATOR(acquisition_private.->) 'rank')='number' AND (rec OPERATOR(acquisition_private.->>) 'rank')~'^[1-9][0-9]*$' AND (rec OPERATOR(acquisition_private.->>) 'rank')::bigint<=2147483647,'Invalid instance rank');
  PERFORM acquisition_private.parameter_map(rec OPERATOR(acquisition_private.->) 'parameters');
  PERFORM acquisition_private.require(json_typeof(rec OPERATOR(acquisition_private.->) 'origin')='object' AND json_typeof(rec OPERATOR(acquisition_private.->) 'abilityOptionId') IN ('string','null'),'Malformed instance');
  IF (rec OPERATOR(acquisition_private.->) 'abilityOptionId')::text<>'null' THEN PERFORM acquisition_private.nonblank(rec OPERATOR(acquisition_private.->) 'abilityOptionId'); END IF;
  origin:=rec OPERATOR(acquisition_private.->) 'origin';
  IF origin OPERATOR(acquisition_private.->>) 'type'='New' THEN PERFORM acquisition_private.keys(origin,ARRAY['type']);
  ELSIF origin OPERATOR(acquisition_private.->>) 'type'='Legacy' THEN
   PERFORM acquisition_private.keys(origin,ARRAY['type','characterId','schema','sourceRevision','sourceFingerprint','ownershipKey']);
   FOREACH member_id IN ARRAY ARRAY['type','characterId','schema','sourceRevision','sourceFingerprint','ownershipKey'] LOOP PERFORM acquisition_private.nonblank(origin OPERATOR(acquisition_private.->) member_id); END LOOP;
  ELSE RAISE EXCEPTION USING ERRCODE='22023',MESSAGE='Unsupported instance origin'; END IF;
 END LOOP;
 PERFORM acquisition_private.require((SELECT count(*)=count(DISTINCT (value OPERATOR(acquisition_private.->) 'instanceId')::text) FROM json_array_elements(root OPERATOR(acquisition_private.->) 'instances')),'Duplicate instance ID');
 pool:=coalesce(p_pool,root OPERATOR(acquisition_private.->) 'syncProofs','[]'::json);
 PERFORM acquisition_private.require(json_typeof(pool)='array' AND json_array_length(pool)<=512,'Invalid/excessive proof pool');
 members:=coalesce(p_members,acquisition_private.pool_ids(coalesce(root OPERATOR(acquisition_private.->) 'syncProofs','[]'::json)));
 IF p_pool IS NULL THEN
  last_member:=NULL;
  FOR rec IN SELECT value FROM json_array_elements(pool) LOOP
   member_id:=acquisition_private.text(rec OPERATOR(acquisition_private.->) 'proofId');
   PERFORM acquisition_private.require(last_member IS NULL OR last_member COLLATE "C"<member_id COLLATE "C",'Duplicate/unsorted proof pool');
   PERFORM acquisition_private.proof_wire(member_id,pool,p_path); last_member:=member_id;
  END LOOP;
 ELSE
  FOREACH member_id IN ARRAY members LOOP
   PERFORM acquisition_private.require(EXISTS(SELECT 1 FROM json_array_elements(pool) x WHERE x.value OPERATOR(acquisition_private.->>) 'proofId'=member_id),'Missing prior proof');
  END LOOP;
 END IF;
 PERFORM acquisition_private.keys(root OPERATOR(acquisition_private.->) 'ledger',ARRAY['opening','entries']);
 opening:=root OPERATOR(acquisition_private.->) 'ledger' OPERATOR(acquisition_private.->) 'opening';
 PERFORM acquisition_private.keys(opening,ARRAY['migrationId','sourceFingerprint','balance','legacyStage','provenance','reason'],ARRAY['legacyMigration']);
 PERFORM acquisition_private.nonblank(opening OPERATOR(acquisition_private.->) 'migrationId'); PERFORM acquisition_private.nonblank(opening OPERATOR(acquisition_private.->) 'sourceFingerprint');
 PERFORM acquisition_private.require(json_typeof(opening OPERATOR(acquisition_private.->) 'reason') IN ('string','null'),'Expected nullable opening reason');
 PERFORM acquisition_private.require(opening OPERATOR(acquisition_private.->>) 'legacyStage' IN ('Creating','Finalized') AND opening OPERATOR(acquisition_private.->>) 'provenance' IN ('LegacyCalculated','UserConfirmed','NewCharacter'),'Invalid opening provenance');
 IF opening OPERATOR(acquisition_private.->>) 'provenance'='UserConfirmed' THEN PERFORM acquisition_private.nonblank(opening OPERATOR(acquisition_private.->) 'reason'); END IF;
 legacy:=opening OPERATOR(acquisition_private.->) 'legacyMigration';
 IF legacy IS NOT NULL THEN
  PERFORM acquisition_private.keys(legacy,ARRAY['origin','calculatorBaseline','catalogIdentity','historyUnknown','ownershipRecords']);
  origin:=legacy OPERATOR(acquisition_private.->) 'origin'; PERFORM acquisition_private.keys(origin,ARRAY['characterId','schema','sourceRevision','raw','activePackIds','ownershipMappings'],ARRAY['extrasSource']);
  PERFORM acquisition_private.types(origin,ARRAY['characterId','sourceRevision','raw'],ARRAY['schema'],ARRAY[]::text[],ARRAY['activePackIds']);
  -- Hash the raw JSON string's UTF-16 code units without decoding lone-surrogate content into UTF-8.
  IF origin OPERATOR(acquisition_private.->) 'extrasSource' IS NULL THEN
   fingerprint:='sha256-utf16be:'||encode(sha256((acquisition_private.parse((origin OPERATOR(acquisition_private.->) 'raw')::text)).sort_key),'hex');
  ELSE
   PERFORM acquisition_private.keys(origin OPERATOR(acquisition_private.->) 'extrasSource',ARRAY['raw']);
   PERFORM acquisition_private.require(json_typeof(origin OPERATOR(acquisition_private.->) 'extrasSource' OPERATOR(acquisition_private.->) 'raw') IN ('string','null'),'Expected nullable legacy extras source');
   fingerprint:='sha256-utf16be:'||encode(sha256(acquisition_private.string_frame('"characters.json"')||acquisition_private.string_frame(origin OPERATOR(acquisition_private.->) 'raw')||
    acquisition_private.string_frame('"sheet-extras.json"')||acquisition_private.string_frame(CASE WHEN (origin OPERATOR(acquisition_private.->) 'extrasSource' OPERATOR(acquisition_private.->) 'raw')::text='null' THEN '"absent"' ELSE '"present"' END::json)||
    acquisition_private.string_frame(CASE WHEN (origin OPERATOR(acquisition_private.->) 'extrasSource' OPERATOR(acquisition_private.->) 'raw')::text='null' THEN '""'::json ELSE origin OPERATOR(acquisition_private.->) 'extrasSource' OPERATOR(acquisition_private.->) 'raw' END)),'hex');
  END IF;
  PERFORM acquisition_private.require(opening OPERATOR(acquisition_private.->>) 'sourceFingerprint'=fingerprint AND (legacy OPERATOR(acquisition_private.->) 'historyUnknown')::text='true' AND opening OPERATOR(acquisition_private.->>) 'provenance'<>'NewCharacter','Legacy opening/source identity differs');
  PERFORM acquisition_private.require((legacy OPERATOR(acquisition_private.->) 'calculatorBaseline')::text='"f47a5256a66ddc5e1b7b5c1e204e57279fc7227f"','Legacy calculator baseline differs');
  PERFORM acquisition_private.nonblank(origin OPERATOR(acquisition_private.->) 'characterId'); PERFORM acquisition_private.nonblank(origin OPERATOR(acquisition_private.->) 'sourceRevision');
  PERFORM acquisition_private.require(json_typeof(origin OPERATOR(acquisition_private.->) 'schema')='number' AND (origin OPERATOR(acquisition_private.->>) 'schema')~'^[0-9]+$' AND (origin OPERATOR(acquisition_private.->>) 'schema')::integer BETWEEN 1 AND 11,'Invalid legacy source schema');
  PERFORM acquisition_private.string_list(origin OPERATOR(acquisition_private.->) 'activePackIds');
  PERFORM acquisition_private.require(json_array_length(origin OPERATOR(acquisition_private.->) 'activePackIds')>0 AND
   (SELECT count(*)=count(DISTINCT value::text) FROM json_array_elements(origin OPERATOR(acquisition_private.->) 'activePackIds')),'Invalid legacy active packs');
  FOR rec IN SELECT value FROM json_array_elements(origin OPERATOR(acquisition_private.->) 'activePackIds') LOOP PERFORM acquisition_private.nonblank(rec); END LOOP;
  PERFORM acquisition_private.require(opening OPERATOR(acquisition_private.->>) 'migrationId'='legacy-v11:sha256-utf16be:'||encode(sha256(acquisition_private.string_frame(origin OPERATOR(acquisition_private.->) 'characterId')||acquisition_private.string_frame(to_json(origin OPERATOR(acquisition_private.->>) 'schema'))||acquisition_private.string_frame(origin OPERATOR(acquisition_private.->) 'sourceRevision')||acquisition_private.string_frame(to_json(fingerprint))),'hex'),'Legacy migration identity differs');
  IF opening OPERATOR(acquisition_private.->>) 'provenance'='LegacyCalculated' THEN
   PERFORM acquisition_private.require((origin OPERATOR(acquisition_private.->) 'activePackIds')::text IN ('["dubl-3.69"]','["dubl-3.69","dubl-chi-3.69"]') AND
    legacy OPERATOR(acquisition_private.->>) 'catalogIdentity'='f47a5256a66ddc5e1b7b5c1e204e57279fc7227f:ee62b7d86dc16a49943fe586ede5cafe18470b82779a5b7bd1680ced2e90c0a0:'||(SELECT string_agg(value#>>'{}',',' ORDER BY ord) FROM json_array_elements(origin OPERATOR(acquisition_private.->) 'activePackIds') WITH ORDINALITY x(value,ord)),'Calculated migration catalog identity differs');
  ELSE PERFORM acquisition_private.require((legacy OPERATOR(acquisition_private.->) 'catalogIdentity')::text='null','Confirmed migration cannot claim calculated catalog'); END IF;
  PERFORM acquisition_private.require(json_typeof(legacy OPERATOR(acquisition_private.->) 'ownershipRecords')='array' AND json_typeof(origin OPERATOR(acquisition_private.->) 'ownershipMappings')='object','Invalid legacy ownership evidence');
  FOR rec IN SELECT value FROM json_array_elements(legacy OPERATOR(acquisition_private.->) 'ownershipRecords') LOOP
   PERFORM acquisition_private.keys(rec,ARRAY['ownershipKey','kind','rank','candidateEntryId','custom']);
   PERFORM acquisition_private.types(rec,ARRAY['ownershipKey','kind'],ARRAY['rank'],ARRAY['custom']);
   PERFORM acquisition_private.nonblank(rec OPERATOR(acquisition_private.->) 'ownershipKey');
   PERFORM acquisition_private.require(rec OPERATOR(acquisition_private.->>) 'kind' IN ('DEVELOPMENT','ATTRIBUTE','SKILL','MANA','SCHOOL','SPELL','CHI_BONUS') AND (rec OPERATOR(acquisition_private.->>) 'rank')::integer>0 AND json_typeof(rec OPERATOR(acquisition_private.->) 'candidateEntryId') IN ('string','null'),'Invalid legacy holding');
  END LOOP;
  backup:=acquisition_private.legacy_backup(origin);
  ownership:=acquisition_private.legacy_records(origin,opening OPERATOR(acquisition_private.->>) 'migrationId',backup);
  PERFORM acquisition_private.require(ownership::text=(legacy OPERATOR(acquisition_private.->) 'ownershipRecords')::text,'Legacy ownership evidence differs from original backup');
  SELECT value INTO captured FROM json_array_elements(backup OPERATOR(acquisition_private.->) 'characters') WHERE (value OPERATOR(acquisition_private.->) 'id')::text=(origin OPERATOR(acquisition_private.->) 'characterId')::text;
  PERFORM acquisition_private.require(acquisition_private.legacy_bool(captured OPERATOR(acquisition_private.->) 'creationComplete',(origin OPERATOR(acquisition_private.->>) 'schema')::integer<5)=(opening OPERATOR(acquisition_private.->>) 'legacyStage'='Finalized'),'Legacy stage differs from original backup');
  FOR mapping IN SELECT * FROM acquisition_private.object_raw_members(origin OPERATOR(acquisition_private.->) 'ownershipMappings') LOOP
   PERFORM acquisition_private.require(EXISTS(SELECT 1 FROM json_array_elements(ownership) o WHERE (o.value OPERATOR(acquisition_private.->) 'ownershipKey')::text=mapping.key::text AND o.value OPERATOR(acquisition_private.->>) 'kind'='SCHOOL'),'Invalid explicit legacy school mapping');
   PERFORM acquisition_private.legacy_school_ref(mapping.value);
  END LOOP;
  PERFORM acquisition_private.require((SELECT count(*)=count(DISTINCT value::text) FROM acquisition_private.object_raw_members(origin OPERATOR(acquisition_private.->) 'ownershipMappings')),'Ambiguous duplicate school mapping');
  FOR holding IN SELECT value FROM json_array_elements(root OPERATOR(acquisition_private.->) 'instances') WHERE value OPERATOR(acquisition_private.->) 'origin' OPERATOR(acquisition_private.->>) 'type'='Legacy' LOOP
   PERFORM acquisition_private.legacy_instance(holding,opening);
  END LOOP;
 END IF;
 amounts:=acquisition_private.amount(opening OPERATOR(acquisition_private.->) 'balance');
 entries:=root OPERATOR(acquisition_private.->) 'ledger' OPERATOR(acquisition_private.->) 'entries'; receipts:=root OPERATOR(acquisition_private.->) 'receipts';
 PERFORM acquisition_private.require(json_typeof(entries)='array' AND json_typeof(receipts)='array','Expected ledger and receipts');
 n:=json_array_length(entries);
 PERFORM acquisition_private.require(n<=2048 AND json_array_length(receipts)=n,'Ledger/receipt completeness mismatch');
 -- Validate flat copy ancestry using the same proof pool, then pin each zero-cost import boundary.
 IF root OPERATOR(acquisition_private.->) 'copySources' IS NOT NULL THEN
  PERFORM acquisition_private.require(json_typeof(root OPERATOR(acquisition_private.->) 'copySources')='array','Invalid copy ancestry');
  FOR source IN SELECT value FROM json_array_elements(root OPERATOR(acquisition_private.->) 'copySources') LOOP
   PERFORM acquisition_private.keys(source,ARRAY['sourceCharacterId','destinationCharacterId','importOperationId','sourceCanonical']);
   source_id:=acquisition_private.text(source OPERATOR(acquisition_private.->) 'sourceCharacterId');
   PERFORM acquisition_private.require(dest IS NULL OR dest=source_id,'Broken copy ancestry');
   historic:=acquisition_private.wire_json(acquisition_private.text(source OPERATOR(acquisition_private.->) 'sourceCanonical'));
   PERFORM acquisition_private.require(historic OPERATOR(acquisition_private.->) 'copySources' IS NULL AND historic OPERATOR(acquisition_private.->) 'syncProofs' IS NULL AND historic OPERATOR(acquisition_private.->>) 'characterId'=source_id,'Nested/mismatched copy ancestry');
   PERFORM acquisition_private.validate_wire(CASE WHEN copy_ancestry::text='[]' THEN historic::text ELSE acquisition_private.set_field(historic,'copySources',copy_ancestry)::text END,pool,members,p_path);
   copy_ancestry:=(substr(copy_ancestry::text,1,length(copy_ancestry::text)-1)||CASE WHEN copy_ancestry::text='[]' THEN '' ELSE ',' END||source::text||']')::json;
   before_copy:=historic; dest:=acquisition_private.text(source OPERATOR(acquisition_private.->) 'destinationCharacterId'); import_id:=acquisition_private.text(source OPERATOR(acquisition_private.->) 'importOperationId');
   PERFORM acquisition_private.require(dest<>source_id AND btrim(import_id)<>'','Invalid copy identity');
  END LOOP;
  PERFORM acquisition_private.require(dest IS NULL OR dest=root OPERATOR(acquisition_private.->>) 'characterId','Copy destination scope mismatch');
 END IF;
 IF historic IS NOT NULL THEN
  PERFORM acquisition_private.require((historic OPERATOR(acquisition_private.->) 'ledger' OPERATOR(acquisition_private.->) 'opening')::text=opening::text,'Copied opening differs');
  i:=json_array_length(historic OPERATOR(acquisition_private.->) 'ledger' OPERATOR(acquisition_private.->) 'entries');
  PERFORM acquisition_private.require(n>i,'Copy requires separate import operation');
  FOR rec IN SELECT value FROM json_array_elements(historic OPERATOR(acquisition_private.->) 'ledger' OPERATOR(acquisition_private.->) 'entries') WITH ORDINALITY h(value,ord) LOOP
   PERFORM acquisition_private.require((entries OPERATOR(acquisition_private.->) (cardinality(seen)))::text=rec::text,'Copied ledger differs'); seen:=array_append(seen,rec OPERATOR(acquisition_private.->>) 'operationId');
  END LOOP;
  seen:=ARRAY[]::text[];
  e:=entries OPERATOR(acquisition_private.->) i;
  PERFORM acquisition_private.require(e OPERATOR(acquisition_private.->>) 'type'='Import' AND e OPERATOR(acquisition_private.->>) 'operationId'=import_id,'Copy import identity differs');
  PERFORM acquisition_private.require(acquisition_private.amount(e OPERATOR(acquisition_private.->) 'amountDelta')=ARRAY[0::bigint,0::bigint] AND (e OPERATOR(acquisition_private.->) 'changes')::text='[]','Copy import must be zero cost with no changes');
  SELECT value INTO r FROM json_array_elements(receipts) WHERE value OPERATOR(acquisition_private.->>) 'operationId'=import_id;
  PERFORM acquisition_private.require(r OPERATOR(acquisition_private.->>) 'requestCanonical'=acquisition_private.canonical(json_build_object('type','Import','characterId',root OPERATOR(acquisition_private.->>) 'characterId','sourceCharacterId',source_id,'sourceFingerprint',acquisition_private.fingerprint(acquisition_private.text(source OPERATOR(acquisition_private.->) 'sourceCanonical')))::text),'Import receipt scope/provenance differs');
  fingerprint:=acquisition_private.fingerprint(acquisition_private.text(source OPERATOR(acquisition_private.->) 'sourceCanonical'));
  evidence:=acquisition_private.wire_json(e OPERATOR(acquisition_private.->>) 'evidenceCanonical');
  PERFORM acquisition_private.keys(evidence,ARRAY['schema','sourceCharacterId','sourceFingerprint']);
  PERFORM acquisition_private.require(evidence OPERATOR(acquisition_private.->>) 'schema'='copy-import-v1' AND evidence OPERATOR(acquisition_private.->>) 'sourceCharacterId'=source_id AND evidence OPERATOR(acquisition_private.->>) 'sourceFingerprint'=fingerprint,'Copy import source fingerprint differs');
 END IF;
 FOR i IN 0..n-1 LOOP
  e:=entries OPERATOR(acquisition_private.->) i;
  PERFORM acquisition_private.keys(e,ARRAY['operationId','type','changes','amountDelta','ruleVersions','reversesId','creationPhase','evidenceCanonical']);
  op:=acquisition_private.text(e OPERATOR(acquisition_private.->) 'operationId'); PERFORM acquisition_private.nonblank(e OPERATOR(acquisition_private.->) 'operationId');
  PERFORM acquisition_private.require(NOT op=ANY(seen),'Duplicate ledger operation identity');
  SELECT value INTO r FROM json_array_elements(receipts) WHERE (value OPERATOR(acquisition_private.->) 'operationId')::text=(e OPERATOR(acquisition_private.->) 'operationId')::text;
  PERFORM acquisition_private.require(r IS NOT NULL AND (SELECT count(*) FROM json_array_elements(receipts) WHERE (value OPERATOR(acquisition_private.->) 'operationId')::text=(e OPERATOR(acquisition_private.->) 'operationId')::text)=1,'Missing/duplicate receipt identity');
  PERFORM acquisition_private.keys(r,ARRAY['operationId','requestCanonical','result','status','beforeRevision','afterRevision']);
  PERFORM acquisition_private.keys(r OPERATOR(acquisition_private.->) 'result',ARRAY['cost','remaining','changes']);
  PERFORM acquisition_private.require(r OPERATOR(acquisition_private.->>) 'status' IN ('Applied','Undone'),'Unsupported receipt status');
  request:=acquisition_private.wire_json(acquisition_private.text(r OPERATOR(acquisition_private.->) 'requestCanonical'));
  inherited:=historic IS NOT NULL AND i<json_array_length(historic OPERATOR(acquisition_private.->) 'ledger' OPERATOR(acquisition_private.->) 'entries');
  PERFORM acquisition_private.require((request OPERATOR(acquisition_private.->) 'characterId')::text=(root OPERATOR(acquisition_private.->) 'characterId')::text OR inherited,'Receipt character scope mismatch');
  IF inherited THEN
   original:=historic OPERATOR(acquisition_private.->) 'receipts' OPERATOR(acquisition_private.->) i;
   PERFORM acquisition_private.require(acquisition_private.remove_field(r,'status')::text=acquisition_private.remove_field(original,'status')::text,'Copied receipt identity differs');
  END IF;
  kind:=acquisition_private.text(e OPERATOR(acquisition_private.->) 'type');
  PERFORM acquisition_private.require(kind IN ('Purchase','Undo','Grant','ManualCorrection','NonFinancial','CompleteCreation','ConfirmLegacyParameter','Import','SyncAdoption'),'Unsupported ledger entry type');
  PERFORM acquisition_private.require(e OPERATOR(acquisition_private.->>) 'creationPhase' IN ('Creating','Finalized') AND json_typeof(e OPERATOR(acquisition_private.->) 'changes')='array','Invalid entry phase/changes');
  FOR rec IN SELECT value FROM json_array_elements(e OPERATOR(acquisition_private.->) 'changes') LOOP
   PERFORM acquisition_private.keys(rec,ARRAY['kind','definition','instanceId','fromRank','toRank','parameters','optionId']);
   PERFORM acquisition_private.nonblank(rec OPERATOR(acquisition_private.->) 'instanceId'); PERFORM acquisition_private.keys(rec OPERATOR(acquisition_private.->) 'definition',ARRAY['packId','entryId']);
   PERFORM acquisition_private.require(rec OPERATOR(acquisition_private.->>) 'kind' IN ('DEVELOPMENT','ATTRIBUTE','SKILL','MANA','SCHOOL','SPELL','CHI_BONUS'),'Unknown change kind');
   PERFORM acquisition_private.types(rec,ARRAY['kind','instanceId'],ARRAY['fromRank','toRank']);
   PERFORM acquisition_private.nonblank(rec OPERATOR(acquisition_private.->) 'definition' OPERATOR(acquisition_private.->) 'packId'); PERFORM acquisition_private.nonblank(rec OPERATOR(acquisition_private.->) 'definition' OPERATOR(acquisition_private.->) 'entryId');
   PERFORM acquisition_private.parameter_map(rec OPERATOR(acquisition_private.->) 'parameters');
   PERFORM acquisition_private.require(json_typeof(rec OPERATOR(acquisition_private.->) 'optionId') IN ('string','null'),'Invalid change option');
   IF (rec OPERATOR(acquisition_private.->) 'optionId')::text<>'null' THEN PERFORM acquisition_private.nonblank(rec OPERATOR(acquisition_private.->) 'optionId'); END IF;
  END LOOP;
  PERFORM acquisition_private.keys(e OPERATOR(acquisition_private.->) 'ruleVersions',ARRAY['catalogRevision','resolutionRevision']);
  PERFORM acquisition_private.nonblank(e OPERATOR(acquisition_private.->) 'ruleVersions' OPERATOR(acquisition_private.->) 'catalogRevision'); PERFORM acquisition_private.nonblank(e OPERATOR(acquisition_private.->) 'ruleVersions' OPERATOR(acquisition_private.->) 'resolutionRevision');
  before_revision:=acquisition_private.decimal(r OPERATOR(acquisition_private.->) 'beforeRevision'); after_revision:=acquisition_private.decimal(r OPERATOR(acquisition_private.->) 'afterRevision');
  evidence:=acquisition_private.wire_json(acquisition_private.text(e OPERATOR(acquisition_private.->) 'evidenceCanonical'));
  delta:=acquisition_private.amount(e OPERATOR(acquisition_private.->) 'amountDelta'); cost:=acquisition_private.amount(r OPERATOR(acquisition_private.->) 'result' OPERATOR(acquisition_private.->) 'cost'); remaining:=acquisition_private.amount(r OPERATOR(acquisition_private.->) 'result' OPERATOR(acquisition_private.->) 'remaining');
  IF kind='SyncAdoption' AND NOT inherited THEN
   branches:=acquisition_private.branches(evidence,pool,members,p_path); selected:=branches[1]::json; displaced:=branches[2]::json; server:=branches[3]::json;
   expected:=acquisition_private.decimal(evidence OPERATOR(acquisition_private.->) 'expectedServerRevision');
   PERFORM acquisition_private.require(evidence OPERATOR(acquisition_private.->>) 'choice' IN ('Local','Server') AND branches[1]=CASE WHEN evidence OPERATOR(acquisition_private.->>) 'choice'='Local' THEN branches[2] ELSE branches[3] END,'Adoption choice differs from selected proof');
   PERFORM acquisition_private.require((selected OPERATOR(acquisition_private.->) 'characterId')::text=(root OPERATOR(acquisition_private.->) 'characterId')::text AND (displaced OPERATOR(acquisition_private.->) 'characterId')::text=(root OPERATOR(acquisition_private.->) 'characterId')::text AND (server OPERATOR(acquisition_private.->) 'characterId')::text=(root OPERATOR(acquisition_private.->) 'characterId')::text,'Adoption character scope differs');
   PERFORM acquisition_private.require(evidence OPERATOR(acquisition_private.->>) 'archiveId'=acquisition_private.archive_id(root OPERATOR(acquisition_private.->>) 'characterId',expected,branches[2],branches[3]),'Adoption archive identity mismatch');
   SELECT coalesce(json_agg(value ORDER BY ord),'[]'::json) INTO prefix FROM json_array_elements(entries) WITH ORDINALITY x(value,ord) WHERE ord<=i;
   PERFORM acquisition_private.require((selected OPERATOR(acquisition_private.->) 'ledger' OPERATOR(acquisition_private.->) 'opening')::text=opening::text AND (selected OPERATOR(acquisition_private.->) 'ledger' OPERATOR(acquisition_private.->) 'entries')::text=acquisition_private.canonical(prefix::text)
    AND acquisition_private.decimal(selected OPERATOR(acquisition_private.->) 'revision')=revision,'Adoption financial prefix differs from selected branch');
   FOR rec IN SELECT value FROM json_array_elements(selected OPERATOR(acquisition_private.->) 'receipts') LOOP
    SELECT value INTO original FROM json_array_elements(receipts) WHERE (value OPERATOR(acquisition_private.->) 'operationId')::text=(rec OPERATOR(acquisition_private.->) 'operationId')::text;
    PERFORM acquisition_private.require(acquisition_private.remove_field(rec,'status')::text=acquisition_private.remove_field(original,'status')::text,'Adoption receipt prefix differs');
   END LOOP;
   PERFORM acquisition_private.require(before_revision=greatest(revision,acquisition_private.decimal(displaced OPERATOR(acquisition_private.->) 'revision')) AND delta=ARRAY[0::bigint,0::bigint] AND (e OPERATOR(acquisition_private.->) 'changes')::text='[]','Invalid adoption revision/financial boundary');
   PERFORM acquisition_private.require(acquisition_private.canonical(request::text)=acquisition_private.canonical(json_build_object('type','SyncAdoption','characterId',root OPERATOR(acquisition_private.->>) 'characterId','expectedRevision',displaced OPERATOR(acquisition_private.->>) 'revision','expectedServerRevision',expected::text,'choice',evidence OPERATOR(acquisition_private.->>) 'choice','archiveId',evidence OPERATOR(acquisition_private.->>) 'archiveId')::text),'Adoption receipt differs from confirmed selection');
   IF i=n-1 THEN PERFORM acquisition_private.require(acquisition_private.projection(root)=acquisition_private.projection(selected),'Adoption whole projection differs'); END IF;
  ELSIF kind='SyncAdoption' AND inherited THEN
   PERFORM acquisition_private.require(before_revision>=revision,'Copied adoption revision regressed');
  ELSE
   PERFORM acquisition_private.require(before_revision=revision,'Broken receipt revision chain');
  END IF;
  PERFORM acquisition_private.require(before_revision<9223372036854775807 AND after_revision=before_revision+1,'Broken receipt revision increment');
  revision:=after_revision; amounts:=ARRAY[amounts[1]+delta[1],amounts[2]+delta[2]];
  PERFORM acquisition_private.require(cost=ARRAY[-delta[1],-delta[2]] AND remaining=amounts AND (r OPERATOR(acquisition_private.->) 'result' OPERATOR(acquisition_private.->) 'changes')::text=(e OPERATOR(acquisition_private.->) 'changes')::text,'Receipt financial result mismatch');
  IF kind='Undo' THEN
   reverse_id:=acquisition_private.text(e OPERATOR(acquisition_private.->) 'reversesId');
   SELECT value INTO original FROM json_array_elements(entries) WHERE value OPERATOR(acquisition_private.->>) 'operationId'=reverse_id;
   PERFORM acquisition_private.require(reverse_id=ANY(seen) AND NOT reverse_id=ANY(reversed) AND original OPERATOR(acquisition_private.->>) 'type'<>'Undo','Invalid duplicate/chained reversal');
   cost:=acquisition_private.amount(original OPERATOR(acquisition_private.->) 'amountDelta');
   PERFORM acquisition_private.require(delta=ARRAY[-cost[1],-cost[2]],'Invalid reversal amount');
   PERFORM acquisition_private.require((SELECT value OPERATOR(acquisition_private.->>) 'status' FROM json_array_elements(receipts) WHERE value OPERATOR(acquisition_private.->>) 'operationId'=reverse_id)='Undone','Missing undone receipt status');
   reversed:=array_append(reversed,reverse_id);
  ELSE PERFORM acquisition_private.require((e OPERATOR(acquisition_private.->) 'reversesId')::text='null','Only Undo can reverse an operation'); END IF;
  IF kind='Import' THEN PERFORM acquisition_private.require(inherited OR historic IS NOT NULL AND op=import_id,'Import lacks captured source provenance'); END IF;
  -- Recorded mutation command/projection identity is evidence, never current-price recomputation.
  IF kind IN ('Grant','ManualCorrection','NonFinancial','CompleteCreation','ConfirmLegacyParameter') AND NOT inherited AND NOT (kind='NonFinancial' AND request OPERATOR(acquisition_private.->>) 'type'='ConfirmAcquisition') THEN
   PERFORM acquisition_private.require(evidence OPERATOR(acquisition_private.->>) 'schema'='mutation-evidence-v1','Missing mutation proof');
   PERFORM acquisition_private.keys(request,ARRAY['type','characterId','expectedRevision','reason','payload']);
   proof:=request OPERATOR(acquisition_private.->) 'payload';
   PERFORM acquisition_private.require(proof OPERATOR(acquisition_private.->>) 'kind'=kind OR kind='NonFinancial' AND proof OPERATOR(acquisition_private.->>) 'kind'='CompleteCreation','Mutation payload kind differs');
   CASE proof OPERATOR(acquisition_private.->>) 'kind'
    WHEN 'Grant' THEN PERFORM acquisition_private.keys(proof,ARRAY['kind','delta']);
    WHEN 'ManualCorrection' THEN PERFORM acquisition_private.keys(proof,ARRAY['kind','patch','delta']); PERFORM acquisition_private.require(acquisition_private.amount(proof OPERATOR(acquisition_private.->) 'delta')=delta,'Correction amount differs');
    WHEN 'NonFinancial' THEN PERFORM acquisition_private.keys(proof,ARRAY['kind','patch']);
    WHEN 'CompleteCreation' THEN PERFORM acquisition_private.keys(proof,ARRAY['kind']);
    WHEN 'ConfirmLegacyParameter' THEN
     PERFORM acquisition_private.keys(proof,ARRAY['kind','instanceId','parameters','resolution']);
     PERFORM acquisition_private.nonblank(proof OPERATOR(acquisition_private.->) 'instanceId'); PERFORM acquisition_private.parameter_map(proof OPERATOR(acquisition_private.->) 'parameters');
     IF (proof OPERATOR(acquisition_private.->) 'resolution')::text<>'null' THEN
      PERFORM acquisition_private.keys(proof OPERATOR(acquisition_private.->) 'resolution',ARRAY['ownershipKey','packId','entryId','rank']);
      PERFORM acquisition_private.types(proof OPERATOR(acquisition_private.->) 'resolution',ARRAY['ownershipKey','packId','entryId'],ARRAY['rank']);
     END IF;
    ELSE RAISE EXCEPTION USING ERRCODE='22023',MESSAGE='Unsupported mutation payload';
   END CASE;
   IF proof OPERATOR(acquisition_private.->) 'patch' IS NOT NULL THEN
    PERFORM acquisition_private.keys(proof OPERATOR(acquisition_private.->) 'patch',ARRAY['projection','replacesInstances','replacesExtras']);
    PERFORM acquisition_private.types(proof OPERATOR(acquisition_private.->) 'patch',ARRAY[]::text[],ARRAY[]::text[],ARRAY['replacesInstances','replacesExtras']);
    PERFORM acquisition_private.keys(proof OPERATOR(acquisition_private.->) 'patch' OPERATOR(acquisition_private.->) 'projection',ARRAY['characterSnapshot','instances','extras','creationPhase']);
   END IF;
   PERFORM acquisition_private.require(json_typeof(request OPERATOR(acquisition_private.->) 'reason') IN ('string','null'),'Invalid mutation reason');
   IF kind IN ('Grant','ManualCorrection','ConfirmLegacyParameter') THEN PERFORM acquisition_private.nonblank(request OPERATOR(acquisition_private.->) 'reason'); END IF;
   PERFORM acquisition_private.keys(evidence,ARRAY['schema','command','before','after']);
   PERFORM acquisition_private.require((evidence OPERATOR(acquisition_private.->) 'command')::text=request::text AND request OPERATOR(acquisition_private.->>) 'type'='CanonicalMutation' AND acquisition_private.decimal(request OPERATOR(acquisition_private.->) 'expectedRevision')=before_revision,'Mutation receipt/evidence differs');
   PERFORM acquisition_private.keys(evidence OPERATOR(acquisition_private.->) 'before',ARRAY['characterSnapshot','instances','extras','creationPhase']);
   PERFORM acquisition_private.keys(evidence OPERATOR(acquisition_private.->) 'after',ARRAY['characterSnapshot','instances','extras','creationPhase']);
   IF kind='Grant' THEN
    PERFORM acquisition_private.require(acquisition_private.amount(request OPERATOR(acquisition_private.->) 'payload' OPERATOR(acquisition_private.->) 'delta')=delta AND (evidence OPERATOR(acquisition_private.->) 'before')::text=(evidence OPERATOR(acquisition_private.->) 'after')::text,'Grant changed projection/amount');
   ELSIF kind IN ('NonFinancial','CompleteCreation','ConfirmLegacyParameter') THEN PERFORM acquisition_private.require(delta=ARRAY[0::bigint,0::bigint],'Nonfinancial mutation spent money'); END IF;
   IF i=n-1 THEN PERFORM acquisition_private.require(acquisition_private.projection(root)=acquisition_private.durable_projection(evidence OPERATOR(acquisition_private.->) 'after'),'Mutation tip projection differs'); END IF;
  END IF;
  seen:=array_append(seen,op);
 END LOOP;
 PERFORM acquisition_private.require(revision=acquisition_private.decimal(root OPERATOR(acquisition_private.->) 'revision'),'Truncated/future receipt chain');
 FOR r IN SELECT value FROM json_array_elements(receipts) LOOP
  PERFORM acquisition_private.require((r OPERATOR(acquisition_private.->>) 'status'='Undone')=(r OPERATOR(acquisition_private.->>) 'operationId'=ANY(reversed)),'Undone receipt lacks exact reversal');
 END LOOP;
 -- Original backup scope, inherited holdings and copied confirmations remain independent
 -- admission conditions after financial/receipt validation, including omissions from root.
 IF legacy IS NOT NULL THEN
  PERFORM acquisition_private.require((legacy OPERATOR(acquisition_private.->) 'origin' OPERATOR(acquisition_private.->) 'characterId')::text=(root OPERATOR(acquisition_private.->) 'characterId')::text OR historic IS NOT NULL,'Legacy backup scope mismatch');
 END IF;
 IF historic IS NOT NULL THEN
  FOR captured IN SELECT value FROM json_array_elements(historic OPERATOR(acquisition_private.->) 'instances') WHERE value OPERATOR(acquisition_private.->) 'origin' OPERATOR(acquisition_private.->>) 'type'='Legacy' LOOP
   SELECT value OPERATOR(acquisition_private.->>) 'kind' INTO holding_kind FROM json_array_elements(historic OPERATOR(acquisition_private.->) 'ledger' OPERATOR(acquisition_private.->) 'opening' OPERATOR(acquisition_private.->) 'legacyMigration' OPERATOR(acquisition_private.->) 'ownershipRecords') WHERE (value OPERATOR(acquisition_private.->) 'ownershipKey')::text=(captured OPERATOR(acquisition_private.->) 'origin' OPERATOR(acquisition_private.->) 'ownershipKey')::text;
   PERFORM acquisition_private.require(holding_kind IS NOT NULL,'Copied holding lacks original ownership');
   PERFORM acquisition_private.copied_holding_chain(root,captured,holding_kind,json_array_length(historic OPERATOR(acquisition_private.->) 'ledger' OPERATOR(acquisition_private.->) 'entries')+1);
   admitted_holdings:=array_append(admitted_holdings,(captured OPERATOR(acquisition_private.->) 'instanceId')::text);
  END LOOP;
  FOR e,i IN SELECT value,ord::integer FROM json_array_elements(entries) WITH ORDINALITY x(value,ord) WHERE ord>json_array_length(historic OPERATOR(acquisition_private.->) 'ledger' OPERATOR(acquisition_private.->) 'entries')+1 AND value OPERATOR(acquisition_private.->>) 'type'='ConfirmLegacyParameter' LOOP
   evidence:=acquisition_private.wire_json(e OPERATOR(acquisition_private.->>) 'evidenceCanonical');
   FOR captured IN SELECT value FROM json_array_elements(evidence OPERATOR(acquisition_private.->) 'after' OPERATOR(acquisition_private.->) 'instances') WHERE value OPERATOR(acquisition_private.->) 'origin' OPERATOR(acquisition_private.->>) 'type'='Legacy' AND (value OPERATOR(acquisition_private.->) 'origin' OPERATOR(acquisition_private.->) 'characterId')::text<>(root OPERATOR(acquisition_private.->) 'characterId')::text AND acquisition_private.holding(evidence OPERATOR(acquisition_private.->) 'before' OPERATOR(acquisition_private.->) 'instances',value OPERATOR(acquisition_private.->) 'instanceId') IS NULL LOOP
    PERFORM acquisition_private.legacy_instance(captured,opening);
    SELECT value INTO rec FROM json_array_elements(ownership) WHERE (value OPERATOR(acquisition_private.->) 'ownershipKey')::text=(captured OPERATOR(acquisition_private.->) 'origin' OPERATOR(acquisition_private.->) 'ownershipKey')::text;
    PERFORM acquisition_private.require((rec OPERATOR(acquisition_private.->) 'custom')::text='false' AND (rec OPERATOR(acquisition_private.->) 'rank')::text=(captured OPERATOR(acquisition_private.->) 'rank')::text AND (e OPERATOR(acquisition_private.->) 'changes')::text='[]','Invalid copied confirmation baseline');
    PERFORM acquisition_private.require(acquisition_private.recorded_holding_mutation(root,e,NULL,captured)::text=captured::text,'Copied confirmation differs from captured holding');
    PERFORM acquisition_private.copied_holding_chain(root,captured,rec OPERATOR(acquisition_private.->>) 'kind',i);
    admitted_holdings:=array_append(admitted_holdings,(captured OPERATOR(acquisition_private.->) 'instanceId')::text);
   END LOOP;
  END LOOP;
 END IF;
 FOR holding IN SELECT value FROM json_array_elements(root OPERATOR(acquisition_private.->) 'instances') WHERE value OPERATOR(acquisition_private.->) 'origin' OPERATOR(acquisition_private.->>) 'type'='Legacy' LOOP
  PERFORM acquisition_private.require((holding OPERATOR(acquisition_private.->) 'origin' OPERATOR(acquisition_private.->) 'characterId')::text=(root OPERATOR(acquisition_private.->) 'characterId')::text OR (holding OPERATOR(acquisition_private.->) 'instanceId')::text=ANY(admitted_holdings),'Legacy instance scope mismatch');
 END LOOP;

END $$;
CREATE FUNCTION acquisition_private.guard_character_row() RETURNS trigger
LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE u uuid; id text; floor acquisition_private.characters%rowtype;
BEGIN
 u:=CASE WHEN TG_OP='DELETE' THEN OLD.user_id ELSE NEW.user_id END;
 id:=CASE WHEN TG_OP='DELETE' THEN OLD.character_id ELSE NEW.character_id END;
 IF TG_OP='UPDATE' THEN
  PERFORM acquisition_private.require(NEW.user_id=OLD.user_id AND NEW.character_id=OLD.character_id,'Character ownership/identity cannot be reassigned');
 END IF;
 PERFORM pg_advisory_xact_lock(hashtextextended(u::text||':'||id,0));
 SELECT * INTO floor FROM acquisition_private.characters WHERE user_id=u AND character_id=id;
 IF FOUND THEN
  IF TG_OP='DELETE' THEN
   PERFORM acquisition_private.require(floor.deleted,'Migrated character delete requires acquisition_delete_v2');
  ELSE
   PERFORM acquisition_private.require(NOT floor.deleted AND NEW.revision=floor.server_revision AND
    NEW.data=jsonb_build_object('characterId',id,'formatVersion',1,'minReaderVersion',2) AND NEW.extras='{}'::jsonb,
    'Migrated character requires acquisition_sync_v2; ledger/floor cannot be downgraded');
  END IF;
 ELSIF TG_OP<>'DELETE' THEN
  PERFORM acquisition_private.require(NOT (NEW.data ?| ARRAY['formatVersion','characterSnapshot','ledger','payloadCanonical']) AND
   NEW.data OPERATOR(acquisition_private.->>) 'id'=id,'New-format character requires validated acquisition_sync_v2');
 END IF;
 RETURN CASE WHEN TG_OP='DELETE' THEN OLD ELSE NEW END;
END $$;
CREATE TRIGGER acquisition_character_format_guard BEFORE INSERT OR UPDATE OR DELETE ON public.web_characters
 FOR EACH ROW EXECUTE FUNCTION acquisition_private.guard_character_row();
-- Close all directly granted application-table mutation/read bypasses. RPCs retain owner checks.
REVOKE ALL ON public.web_characters,public.user_state,public.sync_history FROM anon,authenticated;
REVOKE INSERT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER ON public.profiles,public.web_state FROM authenticated;
REVOKE ALL ON SEQUENCE public.sync_history_id_seq FROM anon,authenticated;

CREATE FUNCTION public.acquisition_sync_v2(p_character_id text,p_expected_revision bigint,p_operation_ids jsonb,
 p_envelope jsonb,p_writer_version integer,p_resolution jsonb DEFAULT NULL) RETURNS jsonb
LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE uid uuid:=auth.uid(); raw text; root json; floor acquisition_private.characters%rowtype; current_row public.web_characters%rowtype;
 prior json; prior_count integer; i integer; e json; r json; op text; tip json; proof json; branches text[];
 operation acquisition_private.operations%rowtype; reply jsonb; revision bigint; archive text; existing_archive acquisition_private.archives%rowtype;
 receipt_identity text; source json; origin json; err text;
BEGIN
 IF uid IS NULL THEN RAISE EXCEPTION 'authentication required'; END IF;
 BEGIN
  PERFORM acquisition_private.require(p_character_id IS NOT NULL AND btrim(p_character_id)<>'' AND p_expected_revision IS NOT NULL AND p_expected_revision>=0,'Character/expected server revision required');
  PERFORM acquisition_private.require(p_writer_version=2,'Unsupported acquisition writer version');
  PERFORM acquisition_private.require(jsonb_typeof(p_envelope)='object' AND (SELECT count(*) FROM jsonb_object_keys(p_envelope))=1 AND jsonb_typeof(p_envelope OPERATOR(acquisition_private.->) 'payloadCanonical')='string','Expected exact one-field payloadCanonical wrapper');
  raw:=p_envelope OPERATOR(acquisition_private.->>) 'payloadCanonical'; PERFORM acquisition_private.validate_wire(raw); root:=raw::json;
  PERFORM acquisition_private.require(acquisition_private.text(root OPERATOR(acquisition_private.->) 'characterId')=p_character_id,'Envelope character identity differs');
  PERFORM acquisition_private.require(jsonb_typeof(p_operation_ids)='array' AND jsonb_array_length(p_operation_ids)=1 AND jsonb_typeof(p_operation_ids OPERATOR(acquisition_private.->) 0)='string','Exactly one saved tip operation required');
  op:=p_operation_ids OPERATOR(acquisition_private.->>) 0; tip:=root OPERATOR(acquisition_private.->) 'ledger' OPERATOR(acquisition_private.->) 'entries' OPERATOR(acquisition_private.->) (json_array_length(root OPERATOR(acquisition_private.->) 'ledger' OPERATOR(acquisition_private.->) 'entries')-1);
  PERFORM acquisition_private.require(tip OPERATOR(acquisition_private.->>) 'operationId'=op AND root OPERATOR(acquisition_private.->) 'receipts' OPERATOR(acquisition_private.->) (json_array_length(root OPERATOR(acquisition_private.->) 'receipts')-1) OPERATOR(acquisition_private.->>) 'operationId'=op AND
   acquisition_private.decimal(root OPERATOR(acquisition_private.->) 'receipts' OPERATOR(acquisition_private.->) (json_array_length(root OPERATOR(acquisition_private.->) 'receipts')-1) OPERATOR(acquisition_private.->) 'afterRevision')=acquisition_private.decimal(root OPERATOR(acquisition_private.->) 'revision'),'Request does not identify the saved receipt tip');
  IF p_resolution IS NULL THEN PERFORM acquisition_private.require(tip OPERATOR(acquisition_private.->>) 'type'<>'SyncAdoption','Ordinary request cannot carry adoption');
  ELSE
   PERFORM acquisition_private.require(tip OPERATOR(acquisition_private.->>) 'type'='SyncAdoption' AND jsonb_typeof(p_resolution)='object' AND
    (SELECT array_agg(key ORDER BY key) FROM jsonb_object_keys(p_resolution) key)=ARRAY['archiveId','choice','expectedServerRevision'],'Resolution requires exact dedicated choice wrapper');
   proof:=acquisition_private.wire_json(tip OPERATOR(acquisition_private.->>) 'evidenceCanonical');
   PERFORM acquisition_private.require(jsonb_typeof(p_resolution OPERATOR(acquisition_private.->) 'choice')='string' AND jsonb_typeof(p_resolution OPERATOR(acquisition_private.->) 'archiveId')='string' AND
    jsonb_typeof(p_resolution OPERATOR(acquisition_private.->) 'expectedServerRevision')='string' AND proof OPERATOR(acquisition_private.->>) 'choice'=p_resolution OPERATOR(acquisition_private.->>) 'choice' AND proof OPERATOR(acquisition_private.->>) 'archiveId'=p_resolution OPERATOR(acquisition_private.->>) 'archiveId' AND
    proof OPERATOR(acquisition_private.->>) 'expectedServerRevision'=p_resolution OPERATOR(acquisition_private.->>) 'expectedServerRevision' AND acquisition_private.decimal(proof OPERATOR(acquisition_private.->) 'expectedServerRevision')=p_expected_revision,'Resolution differs from archived confirmed choice');
  END IF;
 EXCEPTION WHEN OTHERS THEN GET STACKED DIAGNOSTICS err=MESSAGE_TEXT;
  RETURN jsonb_build_object('status','identityConflict','reason',err);
 END;
 PERFORM pg_advisory_xact_lock(hashtextextended(uid::text||':'||p_character_id,0));
 SELECT * INTO floor FROM acquisition_private.characters WHERE user_id=uid AND character_id=p_character_id FOR UPDATE;
 IF FOUND AND floor.deleted THEN RETURN jsonb_build_object('status','identityConflict','reason','Character is durably deleted; use a new copy identity'); END IF;
 SELECT * INTO operation FROM acquisition_private.operations WHERE user_id=uid AND character_id=p_character_id AND operation_id=op;
 IF FOUND AND operation.acknowledgement IS NOT NULL THEN
  IF operation.payload_canonical=raw AND operation.resolution IS NOT DISTINCT FROM p_resolution THEN RETURN operation.acknowledgement; END IF;
  RETURN jsonb_build_object('status','identityConflict','reason','Operation ID reused with different payload');
 END IF;
 IF floor.user_id IS NOT NULL AND floor.server_revision<>p_expected_revision THEN
  RETURN jsonb_build_object('status','conflict','serverCanonical',floor.envelope_canonical,'serverRevision',floor.server_revision::text);
 END IF;
 SELECT * INTO current_row FROM public.web_characters WHERE user_id=uid AND character_id=p_character_id FOR UPDATE;
 IF floor.user_id IS NULL THEN
  IF p_resolution IS NOT NULL THEN RETURN jsonb_build_object('status','identityConflict','reason','Resolution requires an existing server envelope'); END IF;
  IF coalesce(current_row.revision,0)<>p_expected_revision THEN RETURN jsonb_build_object('status','identityConflict','reason','Legacy/missing server revision differs; preserve local branch and migrate current source'); END IF;
  IF current_row.user_id IS NOT NULL THEN
   BEGIN
    origin:=root OPERATOR(acquisition_private.->) 'ledger' OPERATOR(acquisition_private.->) 'opening' OPERATOR(acquisition_private.->) 'legacyMigration' OPERATOR(acquisition_private.->) 'origin';
    PERFORM acquisition_private.require(origin IS NOT NULL AND origin OPERATOR(acquisition_private.->>) 'characterId'=p_character_id,'Existing legacy character requires its captured migration source');
    SELECT value INTO source FROM json_array_elements((origin OPERATOR(acquisition_private.->>) 'raw')::json OPERATOR(acquisition_private.->) 'characters') WHERE value OPERATOR(acquisition_private.->>) 'id'=p_character_id;
    PERFORM acquisition_private.require(source::jsonb=current_row.data,'Legacy migration source differs from current server character');
   EXCEPTION WHEN OTHERS THEN GET STACKED DIAGNOSTICS err=MESSAGE_TEXT; RETURN jsonb_build_object('status','identityConflict','reason',err); END;
  END IF;
  revision:=p_expected_revision+1;
 ELSE revision:=floor.server_revision+1; END IF;
 BEGIN
  IF floor.user_id IS NOT NULL AND p_resolution IS NULL THEN
   prior:=floor.envelope_canonical::json; prior_count:=json_array_length(prior OPERATOR(acquisition_private.->) 'ledger' OPERATOR(acquisition_private.->) 'entries');
   PERFORM acquisition_private.require((prior OPERATOR(acquisition_private.->) 'ledger' OPERATOR(acquisition_private.->) 'opening')::text=(root OPERATOR(acquisition_private.->) 'ledger' OPERATOR(acquisition_private.->) 'opening')::text AND json_array_length(root OPERATOR(acquisition_private.->) 'ledger' OPERATOR(acquisition_private.->) 'entries')>prior_count,'Ordinary write must extend the immutable server ledger');
   FOR i IN 0..prior_count-1 LOOP
    PERFORM acquisition_private.require((prior OPERATOR(acquisition_private.->) 'ledger' OPERATOR(acquisition_private.->) 'entries' OPERATOR(acquisition_private.->) i)::text=(root OPERATOR(acquisition_private.->) 'ledger' OPERATOR(acquisition_private.->) 'entries' OPERATOR(acquisition_private.->) i)::text,'Immutable server ledger prefix differs');
   END LOOP;
   -- Previously captured pools and ancestry may only be extended; IDs never change meaning.
   FOR e IN SELECT value FROM json_array_elements(coalesce(prior OPERATOR(acquisition_private.->) 'syncProofs','[]'::json)) LOOP
    PERFORM acquisition_private.require(EXISTS(SELECT 1 FROM json_array_elements(coalesce(root OPERATOR(acquisition_private.->) 'syncProofs','[]'::json)) p WHERE p.value::text=e::text),'Existing proof pool member removed/changed');
   END LOOP;
   PERFORM acquisition_private.require(coalesce((prior OPERATOR(acquisition_private.->) 'copySources')::text,'[]')=coalesce((root OPERATOR(acquisition_private.->) 'copySources')::text,'[]'),'Ordinary copy ancestry changed');
  ELSIF p_resolution IS NOT NULL THEN
   branches:=acquisition_private.branches(proof,coalesce(root OPERATOR(acquisition_private.->) 'syncProofs','[]'::json),acquisition_private.pool_ids(coalesce(root OPERATOR(acquisition_private.->) 'syncProofs','[]'::json)),ARRAY[]::text[]);
   PERFORM acquisition_private.require(branches[3]=floor.envelope_canonical,'Resolution server proof differs from current immutable server branch');
   archive:=proof OPERATOR(acquisition_private.->>) 'archiveId';
   SELECT * INTO existing_archive FROM acquisition_private.archives WHERE user_id=uid AND character_id=p_character_id AND archive_id=archive;
   PERFORM acquisition_private.require(existing_archive.user_id IS NULL OR (existing_archive.server_revision=p_expected_revision AND existing_archive.local_canonical=branches[2] AND existing_archive.server_canonical=branches[3]),'Archive ID reused with different branches');
  END IF;
  FOR e IN SELECT value FROM json_array_elements(root OPERATOR(acquisition_private.->) 'ledger' OPERATOR(acquisition_private.->) 'entries') LOOP
   SELECT value INTO r FROM json_array_elements(root OPERATOR(acquisition_private.->) 'receipts') WHERE value OPERATOR(acquisition_private.->>) 'operationId'=e OPERATOR(acquisition_private.->>) 'operationId';
   receipt_identity:=acquisition_private.remove_field(r,'status')::text;
   SELECT * INTO operation FROM acquisition_private.operations WHERE user_id=uid AND character_id=p_character_id AND operation_id=e OPERATOR(acquisition_private.->>) 'operationId';
   PERFORM acquisition_private.require(operation.user_id IS NULL OR operation.entry_canonical=e::text AND operation.receipt_identity=receipt_identity,'Historical operation ID reused with different receipt/body');
  END LOOP;
  INSERT INTO acquisition_private.characters(user_id,character_id,server_revision,envelope_canonical)
   VALUES(uid,p_character_id,revision,raw) ON CONFLICT(user_id,character_id) DO UPDATE SET server_revision=excluded.server_revision,envelope_canonical=excluded.envelope_canonical;
  IF p_resolution IS NOT NULL THEN
   INSERT INTO acquisition_private.archives VALUES(uid,p_character_id,archive,p_expected_revision,branches[2],branches[3]) ON CONFLICT DO NOTHING;
  END IF;
  FOR e IN SELECT value FROM json_array_elements(root OPERATOR(acquisition_private.->) 'ledger' OPERATOR(acquisition_private.->) 'entries') LOOP
   SELECT value INTO r FROM json_array_elements(root OPERATOR(acquisition_private.->) 'receipts') WHERE value OPERATOR(acquisition_private.->>) 'operationId'=e OPERATOR(acquisition_private.->>) 'operationId';
   INSERT INTO acquisition_private.operations(user_id,character_id,operation_id,entry_canonical,receipt_identity)
    VALUES(uid,p_character_id,e OPERATOR(acquisition_private.->>) 'operationId',e::text,acquisition_private.remove_field(r,'status')::text) ON CONFLICT DO NOTHING;
  END LOOP;
  reply:=jsonb_build_object('status','ack','operationId',op,'payloadCanonical',raw,'serverRevision',revision::text);
  UPDATE acquisition_private.operations SET payload_canonical=raw,resolution=p_resolution,acknowledgement=reply
   WHERE user_id=uid AND character_id=p_character_id AND operation_id=op;
  INSERT INTO public.web_characters(user_id,character_id,data,extras,revision,updated_by)
   VALUES(uid,p_character_id,jsonb_build_object('characterId',p_character_id,'formatVersion',1,'minReaderVersion',2),'{}',revision,'Acquisition V2')
   ON CONFLICT(user_id,character_id) DO UPDATE SET data=excluded.data,extras=excluded.extras,revision=excluded.revision,updated_at=now(),updated_by=excluded.updated_by;
  INSERT INTO public.sync_history(user_id,character_id,revision,action,device,data,extras)
   VALUES(uid,p_character_id,revision,CASE WHEN current_row.user_id IS NULL THEN 'create' ELSE 'save' END,'Acquisition V2',jsonb_build_object('characterId',p_character_id,'formatVersion',1,'minReaderVersion',2),'{}');
 EXCEPTION WHEN OTHERS THEN GET STACKED DIAGNOSTICS err=MESSAGE_TEXT; RETURN jsonb_build_object('status','identityConflict','reason',err);
 END;
 RETURN reply;
END $$;
CREATE FUNCTION public.acquisition_get_v2(p_character_id text,p_reader_version integer) RETURNS jsonb
LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$ DECLARE uid uuid:=auth.uid(); floor acquisition_private.characters%rowtype; BEGIN
 IF uid IS NULL THEN RAISE EXCEPTION 'authentication required'; END IF;
 IF p_reader_version IS DISTINCT FROM 2 OR p_character_id IS NULL OR btrim(p_character_id)='' THEN RETURN jsonb_build_object('status','identityConflict','reason','Exact reader version 2 and character ID required'); END IF;
 SELECT * INTO floor FROM acquisition_private.characters WHERE user_id=uid AND character_id=p_character_id;
 IF NOT FOUND THEN RETURN jsonb_build_object('status','missing'); END IF;
 RETURN jsonb_build_object('status',CASE WHEN floor.deleted THEN 'deleted' ELSE 'envelope' END,'payloadCanonical',floor.envelope_canonical,'serverRevision',floor.server_revision::text,'minWriterVersion',floor.min_writer_version);
END $$;
CREATE FUNCTION public.acquisition_delete_v2(p_character_id text,p_expected_revision bigint,p_writer_version integer) RETURNS jsonb
LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$ DECLARE uid uuid:=auth.uid(); floor acquisition_private.characters%rowtype; BEGIN
 IF uid IS NULL THEN RAISE EXCEPTION 'authentication required'; END IF;
 IF p_writer_version IS DISTINCT FROM 2 OR p_expected_revision IS NULL OR p_expected_revision<0 OR coalesce(btrim(p_character_id),'')='' THEN RETURN jsonb_build_object('status','identityConflict','reason','Exact writer version and revision required'); END IF;
 PERFORM pg_advisory_xact_lock(hashtextextended(uid::text||':'||p_character_id,0));
 SELECT * INTO floor FROM acquisition_private.characters WHERE user_id=uid AND character_id=p_character_id FOR UPDATE;
 IF NOT FOUND THEN RETURN jsonb_build_object('status','missing'); END IF;
 IF floor.deleted THEN RETURN jsonb_build_object('status','deleted','serverRevision',floor.server_revision::text); END IF;
 IF floor.server_revision<>p_expected_revision THEN RETURN jsonb_build_object('status','conflict','serverCanonical',floor.envelope_canonical,'serverRevision',floor.server_revision::text); END IF;
 UPDATE acquisition_private.characters SET deleted=true,server_revision=server_revision+1 WHERE user_id=uid AND character_id=p_character_id;
 DELETE FROM public.web_characters WHERE user_id=uid AND character_id=p_character_id;
 INSERT INTO public.sync_history(user_id,character_id,revision,action,device,data,extras) VALUES(uid,p_character_id,floor.server_revision+1,'delete','Acquisition V2',jsonb_build_object('characterId',p_character_id,'formatVersion',1,'minReaderVersion',2),'{}');
 RETURN jsonb_build_object('status','deleted','serverRevision',(floor.server_revision+1)::text);
END $$;

-- Replacements copied from immutable deployed-matching bodies; only explicit guards are added.
create or replace function public.get_web_bootstrap()
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_uid uuid := auth.uid();
  v_result jsonb;
begin
  if v_uid is null then raise exception 'authentication required'; end if;
  if exists(select 1 from acquisition_private.characters where user_id=v_uid) then raise exception 'acquisition reader version 2 required'; end if;

  select jsonb_build_object(
    'profile', coalesce((
      select jsonb_build_object('nickname', p.nickname, 'updatedAt', p.updated_at)
      from public.profiles p where p.user_id = v_uid
    ), '{}'::jsonb),
    'state', coalesce((
      select jsonb_build_object(
        'activeCharacterId', s.active_character_id,
        'packState', s.pack_state,
        'revision', s.revision,
        'updatedAt', s.updated_at,
        'updatedBy', s.updated_by
      ) from public.web_state s where s.user_id = v_uid
    ), '{}'::jsonb),
    'characters', coalesce((
      select jsonb_agg(jsonb_build_object(
        'id', c.character_id,
        'data', c.data,
        'extras', c.extras,
        'revision', c.revision,
        'updatedAt', c.updated_at,
        'updatedBy', c.updated_by
      ) order by c.created_at, c.character_id)
      from public.web_characters c where c.user_id = v_uid
    ), '[]'::jsonb),
    'history', coalesce((
      select jsonb_agg(x.item order by x.changed_at desc)
      from (
        select h.changed_at, jsonb_build_object(
          'characterId', h.character_id,
          'characterName', coalesce(h.data ->> 'name', 'Персонаж'),
          'revision', h.revision,
          'action', h.action,
          'device', h.device,
          'changedAt', h.changed_at
        ) as item
        from public.sync_history h
        where h.user_id = v_uid
        order by h.changed_at desc
        limit 12
      ) x
    ), '[]'::jsonb)
  ) into v_result;

  return v_result;
end;
$$;

create or replace function public.save_web_character(
  p_character_id text,
  p_data jsonb,
  p_extras jsonb,
  p_expected_revision bigint,
  p_device text,
  p_action text default 'save'
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_uid uuid := auth.uid();
  v_current public.web_characters%rowtype;
  v_new_revision bigint;
  v_now timestamptz := now();
  v_action text;
begin
  if v_uid is null then raise exception 'authentication required'; end if;
  if p_expected_revision is null or p_expected_revision<0 then raise exception 'expected revision required'; end if;
  if exists(select 1 from acquisition_private.characters where user_id=v_uid and character_id=p_character_id) then raise exception 'acquisition writer version 2 required'; end if;
  if coalesce(btrim(p_character_id), '') = '' then raise exception 'character id required'; end if;
  v_action := case when p_action = 'force' then 'force' else 'save' end;

  select * into v_current
  from public.web_characters
  where user_id = v_uid and character_id = p_character_id
  for update;

  if not found then
    if p_expected_revision <> 0 then
      return jsonb_build_object('status', 'conflict', 'serverRevision', 0, 'serverMissing', true);
    end if;
    v_new_revision := 1;
    insert into public.web_characters(user_id, character_id, data, extras, revision, updated_at, updated_by)
    values (v_uid, p_character_id, p_data, coalesce(p_extras, '{}'::jsonb), v_new_revision, v_now, left(coalesce(p_device, 'Web'), 120));
    insert into public.sync_history(user_id, character_id, revision, action, device, changed_at, data, extras)
    values (v_uid, p_character_id, v_new_revision, 'create', left(coalesce(p_device, 'Web'), 120), v_now, p_data, coalesce(p_extras, '{}'::jsonb));
    return jsonb_build_object('status', 'ok', 'revision', v_new_revision, 'updatedAt', v_now, 'updatedBy', left(coalesce(p_device, 'Web'), 120));
  end if;

  if v_current.revision <> p_expected_revision then
    return jsonb_build_object(
      'status', 'conflict',
      'serverRevision', v_current.revision,
      'serverData', v_current.data,
      'serverExtras', v_current.extras,
      'updatedAt', v_current.updated_at,
      'updatedBy', v_current.updated_by
    );
  end if;

  v_new_revision := v_current.revision + 1;
  update public.web_characters
  set data = p_data,
      extras = coalesce(p_extras, '{}'::jsonb),
      revision = v_new_revision,
      updated_at = v_now,
      updated_by = left(coalesce(p_device, 'Web'), 120)
  where user_id = v_uid and character_id = p_character_id;

  insert into public.sync_history(user_id, character_id, revision, action, device, changed_at, data, extras)
  values (v_uid, p_character_id, v_new_revision, v_action, left(coalesce(p_device, 'Web'), 120), v_now, p_data, coalesce(p_extras, '{}'::jsonb));

  return jsonb_build_object('status', 'ok', 'revision', v_new_revision, 'updatedAt', v_now, 'updatedBy', left(coalesce(p_device, 'Web'), 120));
end;
$$;

create or replace function public.delete_web_character(
  p_character_id text,
  p_expected_revision bigint,
  p_device text
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_uid uuid := auth.uid();
  v_current public.web_characters%rowtype;
  v_now timestamptz := now();
begin
  if v_uid is null then raise exception 'authentication required'; end if;
  if p_expected_revision is null or p_expected_revision<0 then raise exception 'expected revision required'; end if;
  if exists(select 1 from acquisition_private.characters where user_id=v_uid and character_id=p_character_id) then raise exception 'acquisition delete version 2 required'; end if;

  select * into v_current
  from public.web_characters
  where user_id = v_uid and character_id = p_character_id
  for update;

  if not found then return jsonb_build_object('status', 'ok', 'deleted', false); end if;
  if v_current.revision <> p_expected_revision then
    return jsonb_build_object(
      'status', 'conflict',
      'serverRevision', v_current.revision,
      'serverData', v_current.data,
      'serverExtras', v_current.extras,
      'updatedAt', v_current.updated_at,
      'updatedBy', v_current.updated_by
    );
  end if;

  insert into public.sync_history(user_id, character_id, revision, action, device, changed_at, data, extras)
  values (v_uid, p_character_id, v_current.revision + 1, 'delete', left(coalesce(p_device, 'Web'), 120), v_now, v_current.data, v_current.extras);

  delete from public.web_characters where user_id = v_uid and character_id = p_character_id;
  return jsonb_build_object('status', 'ok', 'deleted', true, 'updatedAt', v_now, 'updatedBy', left(coalesce(p_device, 'Web'), 120));
end;
$$;

create or replace function public.save_web_state(
  p_active_character_id text,
  p_pack_state jsonb,
  p_expected_revision bigint,
  p_device text
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_uid uuid := auth.uid();
  v_current public.web_state%rowtype;
  v_new_revision bigint;
  v_now timestamptz := now();
begin
  if v_uid is null then raise exception 'authentication required'; end if;
  if p_expected_revision is null or p_expected_revision<0 then raise exception 'expected revision required'; end if;
  if exists(select 1 from acquisition_private.characters where user_id=v_uid and character_id=p_active_character_id) then raise exception 'acquisition writer version 2 required'; end if;

  select * into v_current from public.web_state where user_id = v_uid for update;
  if not found then
    if p_expected_revision <> 0 then
      return jsonb_build_object('status', 'conflict', 'serverRevision', 0, 'serverMissing', true);
    end if;
    insert into public.web_state(user_id, active_character_id, pack_state, revision, updated_at, updated_by)
    values (v_uid, p_active_character_id, coalesce(p_pack_state, '{}'::jsonb), 1, v_now, left(coalesce(p_device, 'Web'), 120));
    return jsonb_build_object('status', 'ok', 'revision', 1, 'updatedAt', v_now, 'updatedBy', left(coalesce(p_device, 'Web'), 120));
  end if;

  if v_current.revision <> p_expected_revision then
    return jsonb_build_object(
      'status', 'conflict',
      'serverRevision', v_current.revision,
      'activeCharacterId', v_current.active_character_id,
      'packState', v_current.pack_state,
      'updatedAt', v_current.updated_at,
      'updatedBy', v_current.updated_by
    );
  end if;

  v_new_revision := v_current.revision + 1;
  update public.web_state
  set active_character_id = p_active_character_id,
      pack_state = coalesce(p_pack_state, '{}'::jsonb),
      revision = v_new_revision,
      updated_at = v_now,
      updated_by = left(coalesce(p_device, 'Web'), 120)
  where user_id = v_uid;

  return jsonb_build_object('status', 'ok', 'revision', v_new_revision, 'updatedAt', v_now, 'updatedBy', left(coalesce(p_device, 'Web'), 120));
end;
$$;

create or replace function public.native_force_character(
  p_character_id text,
  p_data jsonb,
  p_extras jsonb,
  p_server_revision bigint,
  p_device text,
  p_delete boolean default false
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_uid uuid := auth.uid();
  v_existing public.web_characters%rowtype;
  v_now timestamptz := now();
  v_new_revision bigint;
begin
  if v_uid is null then raise exception 'authentication required'; end if;
  if p_server_revision is null or p_server_revision<0 then raise exception 'expected revision required'; end if;
  if exists(select 1 from acquisition_private.characters where user_id=v_uid and character_id=p_character_id) then raise exception 'acquisition writer version 2 required'; end if;

  select * into v_existing
  from public.web_characters
  where user_id = v_uid and character_id = p_character_id
  for update;

  if not found then
    return jsonb_build_object('status', 'missing');
  end if;

  if v_existing.revision <> p_server_revision then
    return jsonb_build_object(
      'status', 'conflict',
      'serverRevision', v_existing.revision,
      'serverData', v_existing.data,
      'serverExtras', v_existing.extras,
      'updatedAt', v_existing.updated_at,
      'updatedBy', v_existing.updated_by
    );
  end if;

  if p_delete then
    insert into public.sync_history(user_id, character_id, revision, action, device, changed_at, data, extras)
    values (v_uid, p_character_id, v_existing.revision + 1, 'delete', left(coalesce(p_device, 'Native'), 120), v_now, v_existing.data, v_existing.extras);
    delete from public.web_characters where user_id = v_uid and character_id = p_character_id;
    return jsonb_build_object('status', 'ok', 'deleted', true);
  end if;

  v_new_revision := v_existing.revision + 1;
  update public.web_characters
  set data = p_data,
      extras = coalesce(p_extras, '{}'::jsonb),
      revision = v_new_revision,
      updated_at = v_now,
      updated_by = left(coalesce(p_device, 'Native'), 120)
  where user_id = v_uid and character_id = p_character_id;

  insert into public.sync_history(user_id, character_id, revision, action, device, changed_at, data, extras)
  values (v_uid, p_character_id, v_new_revision, 'force', left(coalesce(p_device, 'Native'), 120), v_now, p_data, coalesce(p_extras, '{}'::jsonb));

  return jsonb_build_object('status', 'ok', 'revision', v_new_revision);
end;
$$;

create or replace function public.native_link_device(
  p_snapshot text,
  p_extras jsonb,
  p_pack_state jsonb,
  p_device text
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_uid uuid := auth.uid();
  v_snapshot jsonb;
  v_local jsonb;
  v_local_extras jsonb;
  v_existing public.web_characters%rowtype;
  v_id text;
  v_new_id text;
  v_clone jsonb;
  v_name text;
  v_now timestamptz := now();
  v_active text;
  v_active_mapped text;
begin
  if v_uid is null then raise exception 'authentication required'; end if;
  if exists(select 1 from acquisition_private.characters where user_id=v_uid) then raise exception 'acquisition writer version 2 required'; end if;
  v_snapshot := p_snapshot::jsonb;
  v_active := v_snapshot ->> 'activeCharacterId';
  v_active_mapped := v_active;

  for v_local in select value from jsonb_array_elements(coalesce(v_snapshot -> 'characters', '[]'::jsonb))
  loop
    v_id := coalesce(v_local ->> 'id', '');
    if v_id = '' then continue; end if;
    v_local_extras := coalesce(p_extras -> 'characters' -> v_id, '{}'::jsonb);

    select * into v_existing
    from public.web_characters
    where user_id = v_uid and character_id = v_id
    for update;

    if not found then
      insert into public.web_characters(user_id, character_id, data, extras, revision, updated_at, updated_by)
      values (v_uid, v_id, v_local, v_local_extras, 1, v_now, left(coalesce(p_device, 'Native'), 120));

      insert into public.sync_history(user_id, character_id, revision, action, device, changed_at, data, extras)
      values (v_uid, v_id, 1, 'create', left(coalesce(p_device, 'Native'), 120), v_now, v_local, v_local_extras);
    elsif v_existing.data = v_local and v_existing.extras = v_local_extras then
      null;
    else
      v_new_id := gen_random_uuid()::text;
      v_name := coalesce(nullif(v_local ->> 'name', ''), 'Персонаж') || ' (локальная копия)';
      v_clone := jsonb_set(
        jsonb_set(v_local, '{id}', to_jsonb(v_new_id), true),
        '{name}', to_jsonb(v_name), true
      );

      insert into public.web_characters(user_id, character_id, data, extras, revision, updated_at, updated_by)
      values (v_uid, v_new_id, v_clone, v_local_extras, 1, v_now, left(coalesce(p_device, 'Native'), 120));

      insert into public.sync_history(user_id, character_id, revision, action, device, changed_at, data, extras)
      values (v_uid, v_new_id, 1, 'create', left(coalesce(p_device, 'Native'), 120), v_now, v_clone, v_local_extras);

      if v_id = v_active then v_active_mapped := v_new_id; end if;
    end if;
  end loop;

  insert into public.web_state(user_id, active_character_id, pack_state, revision, updated_at, updated_by)
  values (
    v_uid,
    v_active_mapped,
    coalesce(p_pack_state, '{}'::jsonb),
    1,
    v_now,
    left(coalesce(p_device, 'Native'), 120)
  )
  on conflict (user_id) do nothing;

  return public.get_web_bootstrap();
end;
$$;

create or replace function public.native_sync_snapshot(
  p_snapshot text,
  p_extras jsonb,
  p_dirty_ids text[],
  p_deleted_ids text[],
  p_known_revisions jsonb,
  p_pack_state jsonb,
  p_known_state_revision bigint,
  p_device text
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_uid uuid := auth.uid();
  v_snapshot jsonb := p_snapshot::jsonb;
  v_id text;
  v_local jsonb;
  v_local_extras jsonb;
  v_existing public.web_characters%rowtype;
  v_expected bigint;
  v_new_revision bigint;
  v_now timestamptz;
  v_conflicts jsonb := '[]'::jsonb;
  v_state public.web_state%rowtype;
  v_active text := v_snapshot ->> 'activeCharacterId';
begin
  if v_uid is null then raise exception 'authentication required'; end if;
  if exists(select 1 from acquisition_private.characters where user_id=v_uid) then raise exception 'acquisition writer version 2 required'; end if;

  foreach v_id in array coalesce(p_dirty_ids, array[]::text[])
  loop
    select value into v_local
    from jsonb_array_elements(coalesce(v_snapshot -> 'characters', '[]'::jsonb))
    where value ->> 'id' = v_id
    limit 1;

    if v_local is null then continue; end if;
    v_local_extras := coalesce(p_extras -> 'characters' -> v_id, '{}'::jsonb);
    v_expected := coalesce((p_known_revisions ->> v_id)::bigint, 0);
    v_now := now();

    select * into v_existing
    from public.web_characters
    where user_id = v_uid and character_id = v_id
    for update;

    if not found then
      if v_expected <> 0 then
        v_conflicts := v_conflicts || jsonb_build_array(jsonb_build_object(
          'characterId', v_id,
          'serverRevision', 0,
          'serverMissing', true
        ));
      else
        insert into public.web_characters(user_id, character_id, data, extras, revision, updated_at, updated_by)
        values (v_uid, v_id, v_local, v_local_extras, 1, v_now, left(coalesce(p_device, 'Native'), 120));
        insert into public.sync_history(user_id, character_id, revision, action, device, changed_at, data, extras)
        values (v_uid, v_id, 1, 'create', left(coalesce(p_device, 'Native'), 120), v_now, v_local, v_local_extras);
      end if;
    elsif v_existing.revision <> v_expected then
      v_conflicts := v_conflicts || jsonb_build_array(jsonb_build_object(
        'characterId', v_id,
        'serverRevision', v_existing.revision,
        'serverData', v_existing.data,
        'serverExtras', v_existing.extras,
        'updatedAt', v_existing.updated_at,
        'updatedBy', v_existing.updated_by
      ));
    else
      v_new_revision := v_existing.revision + 1;
      update public.web_characters
      set data = v_local,
          extras = v_local_extras,
          revision = v_new_revision,
          updated_at = v_now,
          updated_by = left(coalesce(p_device, 'Native'), 120)
      where user_id = v_uid and character_id = v_id;

      insert into public.sync_history(user_id, character_id, revision, action, device, changed_at, data, extras)
      values (v_uid, v_id, v_new_revision, 'save', left(coalesce(p_device, 'Native'), 120), v_now, v_local, v_local_extras);
    end if;
  end loop;

  foreach v_id in array coalesce(p_deleted_ids, array[]::text[])
  loop
    v_expected := coalesce((p_known_revisions ->> v_id)::bigint, 0);
    v_now := now();

    select * into v_existing
    from public.web_characters
    where user_id = v_uid and character_id = v_id
    for update;

    if not found then
      null;
    elsif v_existing.revision <> v_expected then
      v_conflicts := v_conflicts || jsonb_build_array(jsonb_build_object(
        'characterId', v_id,
        'serverRevision', v_existing.revision,
        'serverData', v_existing.data,
        'serverExtras', v_existing.extras,
        'updatedAt', v_existing.updated_at,
        'updatedBy', v_existing.updated_by,
        'deleteRequested', true
      ));
    else
      insert into public.sync_history(user_id, character_id, revision, action, device, changed_at, data, extras)
      values (v_uid, v_id, v_existing.revision + 1, 'delete', left(coalesce(p_device, 'Native'), 120), v_now, v_existing.data, v_existing.extras);

      delete from public.web_characters
      where user_id = v_uid and character_id = v_id;
    end if;
  end loop;

  select * into v_state
  from public.web_state
  where user_id = v_uid
  for update;

  v_now := now();
  if not found then
    insert into public.web_state(user_id, active_character_id, pack_state, revision, updated_at, updated_by)
    values (v_uid, v_active, coalesce(p_pack_state, '{}'::jsonb), 1, v_now, left(coalesce(p_device, 'Native'), 120));
  elsif v_state.revision = p_known_state_revision then
    update public.web_state
    set active_character_id = v_active,
        pack_state = coalesce(p_pack_state, '{}'::jsonb),
        revision = v_state.revision + 1,
        updated_at = v_now,
        updated_by = left(coalesce(p_device, 'Native'), 120)
    where user_id = v_uid;
  end if;

  return jsonb_build_object(
    'bootstrap', public.get_web_bootstrap(),
    'conflicts', v_conflicts
  );
end;
$$;

create or replace function public.campaign_get_dashboard(p_campaign_id uuid)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_uid uuid := auth.uid();
  v_campaign public.campaigns%rowtype;
  v_players jsonb;
begin
  if v_uid is null then raise exception 'authentication required'; end if;

  select * into v_campaign
  from public.campaigns
  where id = p_campaign_id and owner_user_id = v_uid;

  if not found then raise exception 'gm access required'; end if;
  if exists(select 1 from public.campaign_members m join acquisition_private.characters f on f.user_id=m.user_id and f.character_id=m.character_id where m.campaign_id=p_campaign_id) then raise exception 'acquisition campaign reader version 2 required'; end if;


  select coalesce(jsonb_agg(jsonb_build_object(
    'userId', m.user_id,
    'nickname', coalesce(p.nickname, 'Player'),
    'characterId', m.character_id,
    'data', wc.data,
    'extras', wc.extras,
    'revision', wc.revision,
    'updatedAt', wc.updated_at,
    'updatedBy', wc.updated_by,
    'joinedAt', m.joined_at
  ) order by m.joined_at, m.user_id), '[]'::jsonb)
  into v_players
  from public.campaign_members m
  join public.web_characters wc
    on wc.user_id = m.user_id and wc.character_id = m.character_id
  left join public.profiles p on p.user_id = m.user_id
  where m.campaign_id = p_campaign_id;

  return jsonb_build_object(
    'id', v_campaign.id,
    'name', v_campaign.name,
    'ownerUserId', v_campaign.owner_user_id,
    'createdAt', v_campaign.created_at,
    'players', v_players
  );
end;
$$;

CREATE FUNCTION public.campaign_get_dashboard_v2(p_campaign_id uuid,p_reader_version integer) RETURNS jsonb
LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$ DECLARE uid uuid:=auth.uid(); c public.campaigns%rowtype; players jsonb; BEGIN
 IF uid IS NULL THEN RAISE EXCEPTION 'authentication required'; END IF;
 SELECT * INTO c FROM public.campaigns WHERE id=p_campaign_id AND owner_user_id=uid;
 IF NOT FOUND THEN RAISE EXCEPTION 'gm access required'; END IF;
 IF p_reader_version IS DISTINCT FROM 2 THEN RETURN jsonb_build_object('status','identityConflict','reason','Exact reader version 2 required'); END IF;
 SELECT coalesce(jsonb_agg(jsonb_build_object('userId',m.user_id,'nickname',p.nickname,'characterId',m.character_id,
  'format',CASE WHEN f.user_id IS NULL THEN 'legacy' ELSE 'acquisition-v2' END,'payloadCanonical',f.envelope_canonical,
  'data',CASE WHEN f.user_id IS NULL THEN wc.data ELSE NULL END,'extras',CASE WHEN f.user_id IS NULL THEN wc.extras ELSE NULL END,
  'revision',wc.revision::text,'joinedAt',m.joined_at) ORDER BY m.joined_at,m.user_id),'[]') INTO players
 FROM public.campaign_members m JOIN public.web_characters wc ON wc.user_id=m.user_id AND wc.character_id=m.character_id
 LEFT JOIN public.profiles p ON p.user_id=m.user_id LEFT JOIN acquisition_private.characters f ON f.user_id=m.user_id AND f.character_id=m.character_id
 WHERE m.campaign_id=p_campaign_id;
 RETURN jsonb_build_object('id',c.id,'name',c.name,'ownerUserId',c.owner_user_id,'createdAt',c.created_at,'players',players);
END $$;
-- Every new helper/table is private, with no inherited default execute/table privileges.
DO $$ DECLARE x record; BEGIN
 FOR x IN SELECT p.oid::regprocedure signature FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace WHERE n.nspname='acquisition_private' LOOP
  EXECUTE format('ALTER FUNCTION %s OWNER TO postgres',x.signature);
  EXECUTE format('REVOKE ALL ON FUNCTION %s FROM PUBLIC,anon,authenticated,service_role',x.signature);
 END LOOP;
 FOR x IN SELECT o.oid::regoperator signature FROM pg_operator o JOIN pg_namespace n ON n.oid=o.oprnamespace WHERE n.nspname='acquisition_private' LOOP
  EXECUTE format('ALTER OPERATOR %s OWNER TO postgres',x.signature);
 END LOOP;
END $$;
ALTER TABLE acquisition_private.characters OWNER TO postgres;
ALTER TABLE acquisition_private.operations OWNER TO postgres;
ALTER TABLE acquisition_private.archives OWNER TO postgres;
ALTER FUNCTION public.acquisition_sync_v2(text,bigint,jsonb,jsonb,integer,jsonb) OWNER TO postgres;
ALTER FUNCTION public.acquisition_get_v2(text,integer) OWNER TO postgres;
ALTER FUNCTION public.acquisition_delete_v2(text,bigint,integer) OWNER TO postgres;
ALTER FUNCTION public.campaign_get_dashboard_v2(uuid,integer) OWNER TO postgres;
REVOKE ALL ON FUNCTION public.acquisition_sync_v2(text,bigint,jsonb,jsonb,integer,jsonb),public.acquisition_get_v2(text,integer),public.acquisition_delete_v2(text,bigint,integer),public.campaign_get_dashboard_v2(uuid,integer) FROM PUBLIC,anon,authenticated,service_role;
GRANT EXECUTE ON FUNCTION public.acquisition_sync_v2(text,bigint,jsonb,jsonb,integer,jsonb),public.acquisition_get_v2(text,integer),public.acquisition_delete_v2(text,bigint,integer),public.campaign_get_dashboard_v2(uuid,integer) TO authenticated;
