alter table public.community_licenses
add constraint community_licenses_valid_period_check
check (expires_at is null or expires_at > starts_at);

create or replace function public.effective_community_license_status(
    stored_status text,
    starts_at timestamptz,
    expires_at timestamptz,
    evaluated_at timestamptz default now()
)
returns text
language sql
immutable
set search_path = public
as $$
    select case
        when stored_status is null then 'expired'
        when stored_status <> 'active' then stored_status
        when starts_at > evaluated_at then 'scheduled'
        when expires_at is not null and expires_at <= evaluated_at then 'expired'
        else 'active'
    end;
$$;

revoke all on function public.effective_community_license_status(text, timestamptz, timestamptz, timestamptz) from public, anon, authenticated;

create or replace function public.super_admin_list_communities()
returns table (
    community_id uuid,
    account_id uuid,
    name text,
    guide_label text,
    license_status text,
    plan_name text,
    expires_at timestamptz,
    max_admins integer,
    max_user_devices integer,
    max_admin_devices integer,
    admin_count bigint,
    user_device_count bigint,
    admin_device_count bigint,
    created_at timestamptz,
    updated_at timestamptz
)
language plpgsql
security definer
stable
set search_path = public
as $$
begin
    perform public.require_super_admin();

    return query
    select
        communities.id,
        (
            select accounts.id
            from public.accounts
            where accounts.community_id = communities.id
              and accounts.deleted_at is null
            order by accounts.created_at asc
            limit 1
        ) as account_id,
        communities.name,
        communities.guide_label,
        public.effective_community_license_status(
            licenses.status,
            licenses.starts_at,
            licenses.expires_at
        ) as license_status,
        coalesce(licenses.plan_name, 'Sin licencia') as plan_name,
        licenses.expires_at,
        licenses.max_admins,
        licenses.max_user_devices,
        licenses.max_admin_devices,
        (
            select count(*)
            from public.community_admins
            where community_admins.community_id = communities.id
              and community_admins.deleted_at is null
        ) as admin_count,
        (
            select count(*)
            from public.devices
            join public.accounts on accounts.id = devices.account_id
            where accounts.community_id = communities.id
              and accounts.deleted_at is null
              and devices.deleted_at is null
              and devices.app_role = 'user'
        ) as user_device_count,
        (
            select count(*)
            from public.devices
            join public.accounts on accounts.id = devices.account_id
            where accounts.community_id = communities.id
              and accounts.deleted_at is null
              and devices.deleted_at is null
              and devices.app_role = 'admin'
        ) as admin_device_count,
        communities.created_at,
        communities.updated_at
    from public.communities
    left join public.community_licenses licenses
      on licenses.community_id = communities.id
     and licenses.deleted_at is null
    where communities.deleted_at is null
    order by communities.updated_at desc, communities.name asc;
end;
$$;

revoke all on function public.super_admin_list_communities() from public;
grant execute on function public.super_admin_list_communities() to authenticated;

create or replace function public.super_admin_get_community_detail(target_community_id uuid)
returns table (
    community_id uuid,
    account_id uuid,
    name text,
    guide_label text,
    license_status text,
    plan_name text,
    starts_at timestamptz,
    expires_at timestamptz,
    max_admins integer,
    max_user_devices integer,
    max_admin_devices integer,
    internal_notes text,
    admin_count bigint,
    user_device_count bigint,
    admin_device_count bigint,
    created_at timestamptz,
    updated_at timestamptz
)
language plpgsql
security definer
stable
set search_path = public
as $$
begin
    perform public.require_super_admin();

    return query
    select
        communities.id,
        (
            select accounts.id
            from public.accounts
            where accounts.community_id = communities.id
              and accounts.deleted_at is null
            order by accounts.created_at asc
            limit 1
        ) as account_id,
        communities.name,
        communities.guide_label,
        public.effective_community_license_status(
            licenses.status,
            licenses.starts_at,
            licenses.expires_at
        ) as license_status,
        coalesce(licenses.plan_name, 'Sin licencia') as plan_name,
        licenses.starts_at,
        licenses.expires_at,
        licenses.max_admins,
        licenses.max_user_devices,
        licenses.max_admin_devices,
        licenses.internal_notes,
        (
            select count(*)
            from public.community_admins
            where community_admins.community_id = communities.id
              and community_admins.deleted_at is null
        ) as admin_count,
        (
            select count(*)
            from public.devices
            join public.accounts on accounts.id = devices.account_id
            where accounts.community_id = communities.id
              and accounts.deleted_at is null
              and devices.deleted_at is null
              and devices.app_role = 'user'
        ) as user_device_count,
        (
            select count(*)
            from public.devices
            join public.accounts on accounts.id = devices.account_id
            where accounts.community_id = communities.id
              and accounts.deleted_at is null
              and devices.deleted_at is null
              and devices.app_role = 'admin'
        ) as admin_device_count,
        communities.created_at,
        communities.updated_at
    from public.communities
    left join public.community_licenses licenses
      on licenses.community_id = communities.id
     and licenses.deleted_at is null
    where communities.id = target_community_id
      and communities.deleted_at is null;
end;
$$;

revoke all on function public.super_admin_get_community_detail(uuid) from public;
grant execute on function public.super_admin_get_community_detail(uuid) to authenticated;
