create table if not exists public.super_admins (
    user_id uuid primary key references auth.users(id) on delete cascade,
    display_name text not null,
    enabled boolean not null default true,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz
);

drop trigger if exists trg_super_admins_updated_at on public.super_admins;
create trigger trg_super_admins_updated_at before update on public.super_admins
for each row execute function public.set_updated_at();

create or replace function public.is_super_admin()
returns boolean
language sql
security definer
stable
set search_path = public
as $$
    select coalesce(
        exists (
            select 1
            from public.super_admins
            where user_id = (select auth.uid())
              and enabled = true
              and deleted_at is null
        ),
        false
    );
$$;

revoke all on function public.is_super_admin() from public;
grant execute on function public.is_super_admin() to authenticated;

create table if not exists public.community_licenses (
    id uuid primary key default gen_random_uuid(),
    community_id uuid not null references public.communities(id) on delete cascade,
    status text not null default 'active'
        check (status in ('active', 'suspended', 'expired')),
    plan_name text not null default 'DEV',
    starts_at timestamptz not null default now(),
    expires_at timestamptz,
    max_admins integer not null default 10 check (max_admins > 0),
    max_user_devices integer not null default 250 check (max_user_devices > 0),
    max_admin_devices integer not null default 10 check (max_admin_devices > 0),
    internal_notes text,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz,
    unique (community_id)
);

drop trigger if exists trg_community_licenses_updated_at on public.community_licenses;
create trigger trg_community_licenses_updated_at before update on public.community_licenses
for each row execute function public.set_updated_at();

create index if not exists idx_community_licenses_status on public.community_licenses(status, expires_at);

insert into public.community_licenses (community_id, status, plan_name)
select communities.id, 'active', 'DEV'
from public.communities
where communities.deleted_at is null
on conflict (community_id) do nothing;

alter table public.super_admins enable row level security;
alter table public.community_licenses enable row level security;

drop policy if exists "super_admins_self_select" on public.super_admins;
create policy "super_admins_self_select" on public.super_admins
for select
to authenticated
using ((select auth.uid()) = user_id and enabled = true and deleted_at is null);

drop policy if exists "community_licenses_super_admin_select" on public.community_licenses;
create policy "community_licenses_super_admin_select" on public.community_licenses
for select
to authenticated
using (public.is_super_admin());

create or replace function public.require_super_admin()
returns void
language plpgsql
security definer
stable
set search_path = public
as $$
begin
    if not public.is_super_admin() then
        raise exception 'Super admin access required';
    end if;
end;
$$;

revoke all on function public.require_super_admin() from public;

create or replace function public.license_allows_activation(
    target_account_id uuid,
    target_app_role text
)
returns boolean
language plpgsql
security definer
stable
set search_path = public
as $$
declare
    target_community_id uuid;
    active_license public.community_licenses%rowtype;
    active_device_count integer;
begin
    if target_app_role not in ('user', 'admin') then
        return false;
    end if;

    select community_id
    into target_community_id
    from public.accounts
    where id = target_account_id
      and deleted_at is null
    limit 1;

    if target_community_id is null then
        return false;
    end if;

    select *
    into active_license
    from public.community_licenses
    where community_id = target_community_id
      and deleted_at is null
    limit 1;

    if active_license.id is null then
        return false;
    end if;

    if active_license.status <> 'active' then
        return false;
    end if;

    if active_license.starts_at > now() then
        return false;
    end if;

    if active_license.expires_at is not null and active_license.expires_at <= now() then
        return false;
    end if;

    select count(*)
    into active_device_count
    from public.devices
    join public.accounts on accounts.id = devices.account_id
    where accounts.community_id = target_community_id
      and accounts.deleted_at is null
      and devices.deleted_at is null
      and devices.app_role = target_app_role;

    if target_app_role = 'admin' then
        return active_device_count < active_license.max_admin_devices;
    end if;

    return active_device_count < active_license.max_user_devices;
end;
$$;

revoke all on function public.license_allows_activation(uuid, text) from public;

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
        coalesce(licenses.status, 'expired') as license_status,
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

create or replace function public.super_admin_create_community(
    community_name text,
    community_guide_label text default 'Equipo de guías',
    license_plan_name text default 'DEV',
    license_expires_at timestamptz default null,
    license_max_admins integer default 10,
    license_max_user_devices integer default 250,
    license_max_admin_devices integer default 10,
    license_internal_notes text default null
)
returns table (
    community_id uuid,
    account_id uuid
)
language plpgsql
security definer
set search_path = public, extensions
as $$
declare
    safe_name text;
    safe_guide_label text;
    inserted_community_id uuid;
    inserted_account_id uuid;
    inserted_policy_id uuid;
begin
    perform public.require_super_admin();

    safe_name := nullif(trim(community_name), '');
    if safe_name is null then
        raise exception 'Community name required';
    end if;

    safe_guide_label := coalesce(nullif(trim(community_guide_label), ''), 'Equipo de guías');

    insert into public.communities (name, guide_label)
    values (safe_name, safe_guide_label)
    returning id into inserted_community_id;

    insert into public.accounts (owner_user_id, name, community_id)
    values ((select auth.uid()), safe_name, inserted_community_id)
    returning id into inserted_account_id;

    insert into public.policies (account_id, version, active)
    values (inserted_account_id, 1, true)
    returning id into inserted_policy_id;

    insert into public.policy_rules (
        account_id,
        policy_id,
        scope,
        target,
        action,
        priority,
        enabled
    )
    values (
        inserted_account_id,
        inserted_policy_id,
        'Global',
        '*',
        'RequestAuthorization',
        0,
        true
    );

    insert into public.daily_limits (
        account_id,
        policy_id,
        target_type,
        target,
        limit_minutes,
        enabled
    )
    values (
        inserted_account_id,
        inserted_policy_id,
        'Global',
        '*',
        120,
        true
    );

    insert into public.community_licenses (
        community_id,
        status,
        plan_name,
        expires_at,
        max_admins,
        max_user_devices,
        max_admin_devices,
        internal_notes
    )
    values (
        inserted_community_id,
        'active',
        coalesce(nullif(trim(license_plan_name), ''), 'DEV'),
        license_expires_at,
        greatest(1, coalesce(license_max_admins, 10)),
        greatest(1, coalesce(license_max_user_devices, 250)),
        greatest(1, coalesce(license_max_admin_devices, 10)),
        nullif(trim(license_internal_notes), '')
    );

    return query select inserted_community_id, inserted_account_id;
end;
$$;

revoke all on function public.super_admin_create_community(text, text, text, timestamptz, integer, integer, integer, text) from public;
grant execute on function public.super_admin_create_community(text, text, text, timestamptz, integer, integer, integer, text) to authenticated;

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
        coalesce(licenses.status, 'expired') as license_status,
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

create or replace function public.super_admin_upsert_license(
    target_community_id uuid,
    license_status text,
    license_plan_name text,
    license_starts_at timestamptz,
    license_expires_at timestamptz,
    license_max_admins integer,
    license_max_user_devices integer,
    license_max_admin_devices integer,
    license_internal_notes text default null
)
returns void
language plpgsql
security definer
set search_path = public
as $$
begin
    perform public.require_super_admin();

    if license_status not in ('active', 'suspended', 'expired') then
        raise exception 'Invalid license status';
    end if;

    if not exists (
        select 1
        from public.communities
        where id = target_community_id
          and deleted_at is null
    ) then
        raise exception 'Community not found';
    end if;

    insert into public.community_licenses (
        community_id,
        status,
        plan_name,
        starts_at,
        expires_at,
        max_admins,
        max_user_devices,
        max_admin_devices,
        internal_notes,
        deleted_at
    )
    values (
        target_community_id,
        license_status,
        coalesce(nullif(trim(license_plan_name), ''), 'DEV'),
        coalesce(license_starts_at, now()),
        license_expires_at,
        greatest(1, coalesce(license_max_admins, 10)),
        greatest(1, coalesce(license_max_user_devices, 250)),
        greatest(1, coalesce(license_max_admin_devices, 10)),
        nullif(trim(license_internal_notes), ''),
        null
    )
    on conflict (community_id) do update
    set status = excluded.status,
        plan_name = excluded.plan_name,
        starts_at = excluded.starts_at,
        expires_at = excluded.expires_at,
        max_admins = excluded.max_admins,
        max_user_devices = excluded.max_user_devices,
        max_admin_devices = excluded.max_admin_devices,
        internal_notes = excluded.internal_notes,
        deleted_at = null;
end;
$$;

revoke all on function public.super_admin_upsert_license(uuid, text, text, timestamptz, timestamptz, integer, integer, integer, text) from public;
grant execute on function public.super_admin_upsert_license(uuid, text, text, timestamptz, timestamptz, integer, integer, integer, text) to authenticated;

create or replace function public.super_admin_list_community_admins(target_community_id uuid)
returns table (
    admin_id uuid,
    display_name text,
    email text,
    created_at timestamptz,
    updated_at timestamptz,
    activated_device_id uuid,
    activated_device_name text,
    activated_at timestamptz,
    last_seen_at timestamptz,
    pending_token_expires_at timestamptz
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
        admins.id,
        admins.display_name,
        admins.email,
        admins.created_at,
        admins.updated_at,
        devices.id,
        devices.display_name,
        devices.activated_at,
        devices.last_seen_at,
        (
            select min(activation_codes.expires_at)
            from public.activation_codes
            where activation_codes.community_admin_id = admins.id
              and activation_codes.used_at is null
              and activation_codes.deleted_at is null
              and activation_codes.expires_at > now()
        ) as pending_token_expires_at
    from public.community_admins admins
    left join public.devices devices
      on devices.community_admin_id = admins.id
     and devices.deleted_at is null
     and devices.app_role = 'admin'
    where admins.community_id = target_community_id
      and admins.deleted_at is null
    order by admins.updated_at desc, admins.display_name asc;
end;
$$;

revoke all on function public.super_admin_list_community_admins(uuid) from public;
grant execute on function public.super_admin_list_community_admins(uuid) to authenticated;

create or replace function public.super_admin_create_admin_pairing_code(
    target_community_id uuid,
    admin_display_name text,
    admin_email text default null,
    ttl_minutes integer default 1440
)
returns table (
    community_admin_id uuid,
    activation_code text,
    expires_at timestamptz
)
language plpgsql
security definer
set search_path = public, extensions
as $$
declare
    owner_account_id uuid;
    safe_admin_name text;
    new_admin_id uuid;
    raw_code text;
    expiration timestamptz;
    active_license public.community_licenses%rowtype;
    active_admin_count integer;
begin
    perform public.require_super_admin();

    safe_admin_name := nullif(trim(admin_display_name), '');
    if safe_admin_name is null then
        raise exception 'Admin name required';
    end if;

    select *
    into active_license
    from public.community_licenses
    where community_id = target_community_id
      and deleted_at is null
    limit 1;

    if active_license.id is null
       or active_license.status <> 'active'
       or active_license.starts_at > now()
       or (active_license.expires_at is not null and active_license.expires_at <= now()) then
        raise exception 'Community license is not active';
    end if;

    select count(*)
    into active_admin_count
    from public.community_admins
    where community_id = target_community_id
      and deleted_at is null;

    if active_admin_count >= active_license.max_admins then
        raise exception 'Admin license limit reached';
    end if;

    select id
    into owner_account_id
    from public.accounts
    where community_id = target_community_id
      and deleted_at is null
    order by created_at asc
    limit 1;

    if owner_account_id is null then
        raise exception 'Community account not found';
    end if;

    insert into public.community_admins (
        community_id,
        display_name,
        email
    )
    values (
        target_community_id,
        safe_admin_name,
        nullif(trim(admin_email), '')
    )
    returning id into new_admin_id;

    raw_code := upper(substr(replace(gen_random_uuid()::text, '-', ''), 1, 8));
    expiration := now() + make_interval(mins => greatest(5, least(coalesce(ttl_minutes, 1440), 10080)));

    insert into public.activation_codes (
        account_id,
        code_hash,
        intended_app_role,
        intended_display_name,
        community_admin_id,
        expires_at
    )
    values (
        owner_account_id,
        crypt(raw_code, gen_salt('bf')),
        'admin',
        safe_admin_name,
        new_admin_id,
        expiration
    );

    return query select new_admin_id, raw_code, expiration;
end;
$$;

revoke all on function public.super_admin_create_admin_pairing_code(uuid, text, text, integer) from public;
grant execute on function public.super_admin_create_admin_pairing_code(uuid, text, text, integer) to authenticated;

create or replace function public.super_admin_list_community_devices(target_community_id uuid)
returns table (
    device_id uuid,
    account_id uuid,
    display_name text,
    app_role text,
    app_version_code integer,
    community_admin_id uuid,
    community_admin_name text,
    activated_at timestamptz,
    last_seen_at timestamptz,
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
        devices.id,
        devices.account_id,
        devices.display_name,
        devices.app_role,
        devices.app_version_code,
        devices.community_admin_id,
        admins.display_name,
        devices.activated_at,
        devices.last_seen_at,
        devices.updated_at
    from public.devices
    join public.accounts on accounts.id = devices.account_id
    left join public.community_admins admins on admins.id = devices.community_admin_id
    where accounts.community_id = target_community_id
      and accounts.deleted_at is null
      and devices.deleted_at is null
    order by devices.last_seen_at desc nulls last, devices.updated_at desc;
end;
$$;

revoke all on function public.super_admin_list_community_devices(uuid) from public;
grant execute on function public.super_admin_list_community_devices(uuid) to authenticated;

create or replace function public.create_device_pairing_code(ttl_minutes integer default 15)
returns table (
    activation_code text,
    expires_at timestamptz
)
language plpgsql
security definer
set search_path = public, extensions
as $$
declare
    owner_account_id uuid;
    device_token text;
    raw_code text;
    expiration timestamptz;
begin
    if auth.uid() is not null then
        select id
        into owner_account_id
        from public.accounts
        where owner_user_id = auth.uid()
          and deleted_at is null
        order by created_at asc
        limit 1;
    end if;

    if owner_account_id is null then
        device_token := public.request_device_token();
        select devices.account_id
        into owner_account_id
        from public.devices
        where devices.deleted_at is null
          and devices.app_role = 'admin'
          and devices.device_token_hash is not null
          and devices.device_token_hash = crypt(device_token, devices.device_token_hash)
        order by devices.created_at asc
        limit 1;
    end if;

    if owner_account_id is null then
        raise exception 'Admin device not found';
    end if;

    if not public.license_allows_activation(owner_account_id, 'user') then
        raise exception 'Community license does not allow more user devices';
    end if;

    raw_code := upper(substr(replace(gen_random_uuid()::text, '-', ''), 1, 6));
    expiration := now() + make_interval(mins => greatest(1, least(coalesce(ttl_minutes, 15), 60)));

    insert into public.activation_codes (
        account_id,
        code_hash,
        intended_app_role,
        expires_at
    )
    values (
        owner_account_id,
        crypt(raw_code, gen_salt('bf')),
        'user',
        expiration
    );

    return query select raw_code, expiration;
end;
$$;

revoke all on function public.create_device_pairing_code(integer) from public;
grant execute on function public.create_device_pairing_code(integer) to anon, authenticated;

create or replace function public.pair_device_with_code(
    pairing_code text,
    device_display_name text,
    device_app_version_code integer,
    device_app_role text default 'user'
)
returns table (
    account_id uuid,
    device_id uuid,
    activation_id uuid,
    device_token text
)
language plpgsql
security definer
set search_path = public, extensions
as $$
declare
    matched_code public.activation_codes%rowtype;
    matched_account public.accounts%rowtype;
    inserted_device_id uuid;
    inserted_activation_id uuid;
    safe_display_name text;
    resolved_app_role text;
    raw_device_token text;
begin
    if device_app_role not in ('user', 'admin') then
        raise exception 'Invalid device role';
    end if;

    raw_device_token := encode(gen_random_bytes(24), 'hex');

    select *
    into matched_code
    from public.activation_codes
    where used_at is null
      and deleted_at is null
      and expires_at > now()
      and code_hash = crypt(upper(trim(pairing_code)), code_hash)
    order by created_at asc
    limit 1;

    if matched_code.id is null then
        raise exception 'Invalid pairing code';
    end if;

    resolved_app_role := coalesce(matched_code.intended_app_role, device_app_role);
    if resolved_app_role not in ('user', 'admin') then
        raise exception 'Invalid device role';
    end if;

    safe_display_name :=
        coalesce(
            nullif(trim(matched_code.intended_display_name), ''),
            nullif(trim(device_display_name), '')
        );
    if safe_display_name is null then
        raise exception 'Device name required';
    end if;

    select *
    into matched_account
    from public.accounts
    where id = matched_code.account_id
      and deleted_at is null
    limit 1;

    if matched_account.id is null then
        raise exception 'Account not found';
    end if;

    if not public.license_allows_activation(matched_code.account_id, resolved_app_role) then
        raise exception 'Community license does not allow this activation';
    end if;

    insert into public.devices (
        account_id,
        platform,
        display_name,
        app_role,
        app_version_code,
        last_seen_at,
        device_token_hash,
        community_admin_id
    )
    values (
        matched_code.account_id,
        'android',
        safe_display_name,
        resolved_app_role,
        device_app_version_code,
        now(),
        crypt(raw_device_token, gen_salt('bf')),
        matched_code.community_admin_id
    )
    returning id into inserted_device_id;

    insert into public.device_activations (
        account_id,
        device_id,
        activated_by_user_id,
        activation_code_id
    )
    values (
        matched_code.account_id,
        inserted_device_id,
        matched_account.owner_user_id,
        matched_code.id
    )
    returning id into inserted_activation_id;

    update public.activation_codes
    set used_at = now(),
        consumed_device_id = inserted_device_id
    where id = matched_code.id;

    return query select matched_code.account_id, inserted_device_id, inserted_activation_id, raw_device_token;
end;
$$;

revoke all on function public.pair_device_with_code(text, text, integer, text) from public;
grant execute on function public.pair_device_with_code(text, text, integer, text) to anon, authenticated;

grant select on public.super_admins to authenticated;
grant select on public.community_licenses to authenticated;
