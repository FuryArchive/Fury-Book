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

