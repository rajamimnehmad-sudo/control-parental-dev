create or replace function public.is_current_user_super_admin()
returns boolean
language sql
security definer
stable
set search_path = public
as $$
    select public.is_super_admin();
$$;

revoke all on function public.is_current_user_super_admin() from public, anon;
grant execute on function public.is_current_user_super_admin() to authenticated;
