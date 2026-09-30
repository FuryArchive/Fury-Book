create index if not exists campaign_invites_created_by_idx
  on public.campaign_invites(created_by);

create index if not exists campaign_members_user_character_idx
  on public.campaign_members(user_id, character_id);

drop policy if exists campaign_invites_select_creator on public.campaign_invites;
create policy campaign_invites_select_creator on public.campaign_invites
  for select to authenticated
  using ((select auth.uid()) = created_by);
