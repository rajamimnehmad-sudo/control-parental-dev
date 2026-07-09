create or replace function public.super_admin_delete_community_admin(
    target_community_id uuid,
    target_admin_id uuid
)
returns integer
language plpgsql
security definer
set search_path = public
as $$
declare
    deleted_count integer;
begin
    perform public.require_super_admin();

    if not exists (
        select 1
        from public.community_admins
        where id = target_admin_id
          and community_id = target_community_id
          and deleted_at is null
    ) then
        raise exception 'Administrador no encontrado';
    end if;

    update public.activation_codes
    set deleted_at = now(),
        updated_at = now()
    where community_admin_id = target_admin_id
      and deleted_at is null;

    update public.devices
    set deleted_at = now(),
        updated_at = now()
    where community_admin_id = target_admin_id
      and deleted_at is null;

    update public.community_admins
    set deleted_at = now(),
        updated_at = now()
    where id = target_admin_id
      and community_id = target_community_id
      and deleted_at is null;

    get diagnostics deleted_count = row_count;
    return deleted_count;
end;
$$;

revoke all on function public.super_admin_delete_community_admin(uuid, uuid) from public, anon;
grant execute on function public.super_admin_delete_community_admin(uuid, uuid) to authenticated;
