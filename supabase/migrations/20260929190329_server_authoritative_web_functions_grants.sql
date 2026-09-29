create or replace function public.set_profile_nickname(p_nickname text)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_uid uuid := auth.uid();
  v_nickname text := btrim(coalesce(p_nickname, ''));
  v_now timestamptz := now();
begin
  if v_uid is null then raise exception 'authentication required'; end if;
  if char_length(v_nickname) < 2 or char_length(v_nickname) > 32 then
    raise exception 'nickname must contain 2 to 32 characters';
  end if;

  insert into public.profiles(user_id, nickname, updated_at)
  values (v_uid, v_nickname, v_now)
  on conflict (user_id) do update set nickname = excluded.nickname, updated_at = excluded.updated_at;
  return jsonb_build_object('nickname', v_nickname, 'updatedAt', v_now);
end;
$$;

revoke all on function public.get_web_bootstrap() from public;
revoke all on function public.save_web_character(text, jsonb, jsonb, bigint, text, text) from public;
revoke all on function public.delete_web_character(text, bigint, text) from public;
revoke all on function public.save_web_state(text, jsonb, bigint, text) from public;
revoke all on function public.set_profile_nickname(text) from public;

grant execute on function public.get_web_bootstrap() to authenticated;
grant execute on function public.save_web_character(text, jsonb, jsonb, bigint, text, text) to authenticated;
grant execute on function public.delete_web_character(text, bigint, text) to authenticated;
grant execute on function public.save_web_state(text, jsonb, bigint, text) to authenticated;
grant execute on function public.set_profile_nickname(text) to authenticated;
