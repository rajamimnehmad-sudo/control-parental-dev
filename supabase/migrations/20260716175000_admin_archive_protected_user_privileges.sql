revoke all on function public.admin_archive_protected_user(uuid) from public, anon, authenticated;
grant execute on function public.admin_archive_protected_user(uuid) to authenticated;
