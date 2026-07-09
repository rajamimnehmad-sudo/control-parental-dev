create or replace function public.super_admin_delete_community(target_community_id uuid)
returns integer
language plpgsql
security definer
set search_path = public
as $$
declare
    deleted_count integer;
    delete_suffix text;
begin
    perform public.require_super_admin();

    if not exists (
        select 1
        from public.communities
        where id = target_community_id
          and deleted_at is null
    ) then
        raise exception 'Comunidad no encontrada';
    end if;

    delete_suffix := ' [borrada ' || to_char(now(), 'YYYY-MM-DD HH24:MI:SS') || ']';

    update public.device_apps
    set deleted_at = now(),
        updated_at = now()
    where account_id in (
        select id from public.accounts where community_id = target_community_id
    )
      and deleted_at is null;

    update public.device_activations
    set deleted_at = now(),
        updated_at = now(),
        revoked_at = coalesce(revoked_at, now())
    where account_id in (
        select id from public.accounts where community_id = target_community_id
    )
      and deleted_at is null;

    update public.activation_codes
    set deleted_at = now(),
        updated_at = now()
    where account_id in (
        select id from public.accounts where community_id = target_community_id
    )
      and deleted_at is null;

    update public.policy_rules
    set deleted_at = now(),
        updated_at = now()
    where account_id in (
        select id from public.accounts where community_id = target_community_id
    )
      and deleted_at is null;

    update public.daily_limits
    set deleted_at = now(),
        updated_at = now()
    where account_id in (
        select id from public.accounts where community_id = target_community_id
    )
      and deleted_at is null;

    update public.policies
    set deleted_at = now(),
        updated_at = now(),
        active = false
    where account_id in (
        select id from public.accounts where community_id = target_community_id
    )
      and deleted_at is null;

    update public.access_requests
    set deleted_at = now(),
        updated_at = now()
    where account_id in (
        select id from public.accounts where community_id = target_community_id
    )
      and deleted_at is null;

    update public.extra_time_grants
    set deleted_at = now(),
        updated_at = now()
    where account_id in (
        select id from public.accounts where community_id = target_community_id
    )
      and deleted_at is null;

    update public.devices
    set deleted_at = now(),
        updated_at = now()
    where account_id in (
        select id from public.accounts where community_id = target_community_id
    )
      and deleted_at is null;

    update public.community_admins
    set deleted_at = now(),
        updated_at = now()
    where community_id = target_community_id
      and deleted_at is null;

    update public.community_licenses
    set deleted_at = now(),
        updated_at = now(),
        status = 'expired'
    where community_id = target_community_id
      and deleted_at is null;

    update public.accounts
    set deleted_at = now(),
        updated_at = now()
    where community_id = target_community_id
      and deleted_at is null;

    update public.communities
    set deleted_at = now(),
        updated_at = now(),
        name = name || delete_suffix
    where id = target_community_id
      and deleted_at is null;

    get diagnostics deleted_count = row_count;
    return deleted_count;
end;
$$;

revoke all on function public.super_admin_delete_community(uuid) from public, anon;
grant execute on function public.super_admin_delete_community(uuid) to authenticated;
