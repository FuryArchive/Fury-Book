revoke all on table public.user_state from anon;
revoke truncate, references, trigger on table public.user_state from authenticated;
grant select, insert, update, delete on table public.user_state to authenticated;
