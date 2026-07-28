create or replace function public.super_admin_list_admin_contacts(
    target_community_id uuid
)
returns table (
    admin_id uuid,
    phone_e164 text
)
language plpgsql
security definer
set search_path = ''
as $$
begin
    if not public.is_super_admin() then
        raise exception 'Not authorized';
    end if;
    return query
    select admin.id, admin.phone_e164
    from public.community_admins admin
    where admin.community_id = target_community_id and admin.deleted_at is null;
end;
$$;

revoke all on function public.super_admin_list_admin_contacts(uuid) from public;
grant execute on function public.super_admin_list_admin_contacts(uuid) to authenticated;
