create table if not exists public.campaigns (
  id uuid primary key default gen_random_uuid(),
  owner_user_id uuid not null references auth.users(id) on delete cascade,
  name text not null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint campaigns_name_length check (char_length(btrim(name)) between 2 and 80)
);

create table if not exists public.campaign_members (
  campaign_id uuid not null references public.campaigns(id) on delete cascade,
  user_id uuid not null references auth.users(id) on delete cascade,
  character_id text not null,
  joined_at timestamptz not null default now(),
  primary key (campaign_id, user_id),
  constraint campaign_members_character_owner_fk
    foreign key (user_id, character_id)
    references public.web_characters(user_id, character_id)
    on delete cascade
);

create table if not exists public.campaign_invites (
  id uuid primary key default gen_random_uuid(),
  campaign_id uuid not null references public.campaigns(id) on delete cascade,
  token text not null unique,
  created_by uuid not null references auth.users(id) on delete cascade,
  created_at timestamptz not null default now(),
  expires_at timestamptz not null,
  revoked_at timestamptz,
  constraint campaign_invites_token_length check (char_length(token) between 8 and 64)
);

create index if not exists campaigns_owner_idx on public.campaigns(owner_user_id);
create index if not exists campaign_members_user_idx on public.campaign_members(user_id);
create index if not exists campaign_invites_campaign_idx on public.campaign_invites(campaign_id);
create index if not exists campaign_invites_token_idx on public.campaign_invites(token);

alter table public.campaigns enable row level security;
alter table public.campaign_members enable row level security;
alter table public.campaign_invites enable row level security;

revoke all on public.campaigns from anon, authenticated;
revoke all on public.campaign_members from anon, authenticated;
revoke all on public.campaign_invites from anon, authenticated;

drop policy if exists campaigns_select_owner on public.campaigns;
create policy campaigns_select_owner on public.campaigns
  for select to authenticated
  using ((select auth.uid()) = owner_user_id);

drop policy if exists campaign_members_select_self on public.campaign_members;
create policy campaign_members_select_self on public.campaign_members
  for select to authenticated
  using ((select auth.uid()) = user_id);

create or replace function public.campaign_create(p_name text)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_uid uuid := auth.uid();
  v_name text := btrim(coalesce(p_name, ''));
  v_campaign public.campaigns%rowtype;
begin
  if v_uid is null then raise exception 'authentication required'; end if;
  if char_length(v_name) < 2 or char_length(v_name) > 80 then
    raise exception 'campaign name must contain 2 to 80 characters';
  end if;

  insert into public.campaigns(owner_user_id, name)
  values (v_uid, v_name)
  returning * into v_campaign;

  return jsonb_build_object(
    'id', v_campaign.id,
    'name', v_campaign.name,
    'isOwner', true,
    'linkedCharacterId', null,
    'memberCount', 0,
    'createdAt', v_campaign.created_at
  );
end;
$$;

create or replace function public.campaign_list()
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_uid uuid := auth.uid();
  v_result jsonb;
begin
  if v_uid is null then raise exception 'authentication required'; end if;

  select coalesce(jsonb_agg(q.item order by q.created_at desc), '[]'::jsonb)
  into v_result
  from (
    select
      c.created_at,
      jsonb_build_object(
        'id', c.id,
        'name', c.name,
        'isOwner', c.owner_user_id = v_uid,
        'linkedCharacterId', m.character_id,
        'memberCount', (
          select count(*) from public.campaign_members cm where cm.campaign_id = c.id
        ),
        'createdAt', c.created_at
      ) as item
    from public.campaigns c
    left join public.campaign_members m
      on m.campaign_id = c.id and m.user_id = v_uid
    where c.owner_user_id = v_uid or m.user_id = v_uid
  ) q;

  return v_result;
end;
$$;

create or replace function public.campaign_create_invite(
  p_campaign_id uuid,
  p_expires_hours integer default 168
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_uid uuid := auth.uid();
  v_token text;
  v_expires timestamptz;
  v_invite_id uuid;
begin
  if v_uid is null then raise exception 'authentication required'; end if;
  if not exists (
    select 1 from public.campaigns
    where id = p_campaign_id and owner_user_id = v_uid
  ) then
    raise exception 'gm access required';
  end if;
  if p_expires_hours < 1 or p_expires_hours > 720 then
    raise exception 'invite lifetime must be between 1 and 720 hours';
  end if;

  v_expires := now() + make_interval(hours => p_expires_hours);

  loop
    v_token := upper(substr(replace(gen_random_uuid()::text, '-', ''), 1, 10));
    begin
      insert into public.campaign_invites(campaign_id, token, created_by, expires_at)
      values (p_campaign_id, v_token, v_uid, v_expires)
      returning id into v_invite_id;
      exit;
    exception when unique_violation then
      null;
    end;
  end loop;

  return jsonb_build_object(
    'id', v_invite_id,
    'campaignId', p_campaign_id,
    'token', v_token,
    'expiresAt', v_expires
  );
end;
$$;

create or replace function public.campaign_join(
  p_token text,
  p_character_id text
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_uid uuid := auth.uid();
  v_invite public.campaign_invites%rowtype;
  v_campaign public.campaigns%rowtype;
  v_token text := upper(btrim(coalesce(p_token, '')));
begin
  if v_uid is null then raise exception 'authentication required'; end if;
  if v_token = '' then raise exception 'invite code required'; end if;
  if coalesce(btrim(p_character_id), '') = '' then raise exception 'character required'; end if;

  select * into v_invite
  from public.campaign_invites
  where token = v_token
    and revoked_at is null
    and expires_at > now()
  limit 1;

  if not found then raise exception 'invite is invalid or expired'; end if;

  select * into v_campaign
  from public.campaigns
  where id = v_invite.campaign_id;

  if not found then raise exception 'campaign not found'; end if;
  if v_campaign.owner_user_id = v_uid then raise exception 'campaign owner is already the gm'; end if;

  if not exists (
    select 1 from public.web_characters
    where user_id = v_uid and character_id = p_character_id
  ) then
    raise exception 'character does not belong to current user';
  end if;

  insert into public.campaign_members(campaign_id, user_id, character_id)
  values (v_campaign.id, v_uid, p_character_id)
  on conflict (campaign_id, user_id) do update
    set character_id = excluded.character_id;

  return jsonb_build_object(
    'id', v_campaign.id,
    'name', v_campaign.name,
    'isOwner', false,
    'linkedCharacterId', p_character_id,
    'createdAt', v_campaign.created_at
  );
end;
$$;

create or replace function public.campaign_set_character(
  p_campaign_id uuid,
  p_character_id text
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_uid uuid := auth.uid();
begin
  if v_uid is null then raise exception 'authentication required'; end if;

  if not exists (
    select 1 from public.web_characters
    where user_id = v_uid and character_id = p_character_id
  ) then
    raise exception 'character does not belong to current user';
  end if;

  update public.campaign_members
  set character_id = p_character_id
  where campaign_id = p_campaign_id and user_id = v_uid;

  if not found then raise exception 'campaign membership not found'; end if;

  return jsonb_build_object('status', 'ok', 'characterId', p_character_id);
end;
$$;

create or replace function public.campaign_leave(p_campaign_id uuid)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_uid uuid := auth.uid();
begin
  if v_uid is null then raise exception 'authentication required'; end if;
  if exists (
    select 1 from public.campaigns where id = p_campaign_id and owner_user_id = v_uid
  ) then
    raise exception 'gm cannot leave owned campaign';
  end if;

  delete from public.campaign_members
  where campaign_id = p_campaign_id and user_id = v_uid;

  return jsonb_build_object('status', 'ok');
end;
$$;

create or replace function public.campaign_remove_member(
  p_campaign_id uuid,
  p_user_id uuid
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_uid uuid := auth.uid();
begin
  if v_uid is null then raise exception 'authentication required'; end if;
  if not exists (
    select 1 from public.campaigns
    where id = p_campaign_id and owner_user_id = v_uid
  ) then
    raise exception 'gm access required';
  end if;

  delete from public.campaign_members
  where campaign_id = p_campaign_id and user_id = p_user_id;

  return jsonb_build_object('status', 'ok');
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

revoke all on function public.campaign_create(text) from public, anon;
revoke all on function public.campaign_list() from public, anon;
revoke all on function public.campaign_create_invite(uuid, integer) from public, anon;
revoke all on function public.campaign_join(text, text) from public, anon;
revoke all on function public.campaign_set_character(uuid, text) from public, anon;
revoke all on function public.campaign_leave(uuid) from public, anon;
revoke all on function public.campaign_remove_member(uuid, uuid) from public, anon;
revoke all on function public.campaign_get_dashboard(uuid) from public, anon;

grant execute on function public.campaign_create(text) to authenticated;
grant execute on function public.campaign_list() to authenticated;
grant execute on function public.campaign_create_invite(uuid, integer) to authenticated;
grant execute on function public.campaign_join(text, text) to authenticated;
grant execute on function public.campaign_set_character(uuid, text) to authenticated;
grant execute on function public.campaign_leave(uuid) to authenticated;
grant execute on function public.campaign_remove_member(uuid, uuid) to authenticated;
grant execute on function public.campaign_get_dashboard(uuid) to authenticated;
