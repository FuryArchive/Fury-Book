create table if not exists public.user_state (
  user_id uuid primary key references auth.users(id) on delete cascade,
  snapshot text not null,
  extras text not null default '{}',
  pack_state jsonb not null default '{}'::jsonb,
  revision bigint not null default 1,
  updated_at timestamptz not null default now()
);

alter table public.user_state enable row level security;

grant select, insert, update, delete
on public.user_state
to authenticated;

drop policy if exists "user_state_select_own" on public.user_state;
create policy "user_state_select_own"
on public.user_state
for select
to authenticated
using ((select auth.uid()) = user_id);

drop policy if exists "user_state_insert_own" on public.user_state;
create policy "user_state_insert_own"
on public.user_state
for insert
to authenticated
with check ((select auth.uid()) = user_id);

drop policy if exists "user_state_update_own" on public.user_state;
create policy "user_state_update_own"
on public.user_state
for update
to authenticated
using ((select auth.uid()) = user_id)
with check ((select auth.uid()) = user_id);

drop policy if exists "user_state_delete_own" on public.user_state;
create policy "user_state_delete_own"
on public.user_state
for delete
to authenticated
using ((select auth.uid()) = user_id);
