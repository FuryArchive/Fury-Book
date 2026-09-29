revoke execute on function public.get_web_bootstrap() from anon;
revoke execute on function public.save_web_character(text, jsonb, jsonb, bigint, text, text) from anon;
revoke execute on function public.delete_web_character(text, bigint, text) from anon;
revoke execute on function public.save_web_state(text, jsonb, bigint, text) from anon;
revoke execute on function public.set_profile_nickname(text) from anon;
revoke execute on function public.handle_new_fury_user() from anon, authenticated;
