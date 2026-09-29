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

revoke all on function public.native_link_device(text, jsonb, jsonb, text) from public, anon;
revoke all on function public.native_sync_snapshot(text, jsonb, text[], text[], jsonb, jsonb, bigint, text) from public, anon;
revoke all on function public.native_force_character(text, jsonb, jsonb, bigint, text, boolean) from public, anon;
grant execute on function public.native_link_device(text, jsonb, jsonb, text) to authenticated;
grant execute on function public.native_sync_snapshot(text, jsonb, text[], text[], jsonb, jsonb, bigint, text) to authenticated;
grant execute on function public.native_force_character(text, jsonb, jsonb, bigint, text, boolean) to authenticated;
