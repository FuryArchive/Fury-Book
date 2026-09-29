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

