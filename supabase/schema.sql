-- Community Content Filter MVP schema.
-- Run this file before rls.sql and seed.sql.

create schema if not exists extensions;
create extension if not exists "pgcrypto" with schema extensions;

-- Accounts group administrators, protected users and devices.
create table if not exists public.accounts (
    id uuid primary key default gen_random_uuid(),
    owner_user_id uuid not null references auth.users(id) on delete cascade,
    name text not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz
);

-- Devices registered under an account. Android is the only active platform in MVP.
create table if not exists public.devices (
    id uuid primary key default gen_random_uuid(),
    account_id uuid not null references public.accounts(id) on delete cascade,
    platform text not null check (platform in ('android')),
    display_name text not null,
    app_role text not null check (app_role in ('user', 'admin')),
    app_version_code integer not null default 1,
    activated_at timestamptz not null default now(),
    last_seen_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz
);

-- Activation codes are one-time account-bound codes. Android never receives privileged keys.
create table if not exists public.activation_codes (
    id uuid primary key default gen_random_uuid(),
    account_id uuid not null references public.accounts(id) on delete cascade,
    code_hash text not null,
    consumed_device_id uuid references public.devices(id) on delete set null,
    expires_at timestamptz not null,
    used_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz
);

-- Device activations record the binding between an authenticated account and device.
create table if not exists public.device_activations (
    id uuid primary key default gen_random_uuid(),
    account_id uuid not null references public.accounts(id) on delete cascade,
    device_id uuid not null references public.devices(id) on delete cascade,
    activated_by_user_id uuid not null references auth.users(id) on delete cascade,
    activation_code_id uuid references public.activation_codes(id) on delete set null,
    activated_at timestamptz not null default now(),
    revoked_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz,
    unique (device_id)
);

-- Policies are versioned containers for rules and limits.
create table if not exists public.policies (
    id uuid primary key default gen_random_uuid(),
    account_id uuid not null references public.accounts(id) on delete cascade,
    device_id uuid references public.devices(id) on delete cascade,
    version bigint not null default 1,
    active boolean not null default true,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz
);

-- Policy rules define app/domain/category/global allow, block, warn or request behavior.
create table if not exists public.policy_rules (
    id uuid primary key default gen_random_uuid(),
    account_id uuid not null references public.accounts(id) on delete cascade,
    policy_id uuid not null references public.policies(id) on delete cascade,
    scope text not null check (scope in ('App', 'Domain', 'Category', 'Global')),
    target text not null,
    action text not null check (action in ('Allow', 'Block', 'Warn', 'RequestAuthorization')),
    priority integer not null default 0,
    enabled boolean not null default true,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz
);

-- Daily limits store local time caps for app/category/domain/global targets.
create table if not exists public.daily_limits (
    id uuid primary key default gen_random_uuid(),
    account_id uuid not null references public.accounts(id) on delete cascade,
    policy_id uuid not null references public.policies(id) on delete cascade,
    target_type text not null check (target_type in ('App', 'Domain', 'Category', 'Global')),
    target text not null,
    limit_minutes integer not null check (limit_minutes > 0),
    enabled boolean not null default true,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz
);

-- Access requests are created offline by app-user and approved/rejected by app-admin.
create table if not exists public.access_requests (
    id uuid primary key,
    account_id uuid not null references public.accounts(id) on delete cascade,
    device_id uuid references public.devices(id) on delete set null,
    request_type text not null default 'APP_ACCESS'
        check (request_type in ('APP_ACCESS', 'DOMAIN_ACCESS', 'EXTRA_TIME', 'OTHER')),
    target_type text not null check (target_type in ('App', 'Domain', 'Category', 'Global')),
    target text not null,
    target_package_name text,
    target_domain text,
    reason text not null,
    requested_minutes integer,
    status text not null check (status in ('PendingLocal', 'PendingRemote', 'Approved', 'Rejected', 'Expired')),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    expires_at timestamptz,
    deleted_at timestamptz
);

-- Extra-time grants are approvals with a time window and target.
create table if not exists public.extra_time_grants (
    id uuid primary key default gen_random_uuid(),
    account_id uuid not null references public.accounts(id) on delete cascade,
    request_id uuid references public.access_requests(id) on delete set null,
    target_type text not null check (target_type in ('App', 'Domain', 'Category', 'Global')),
    target text not null,
    granted_minutes integer not null check (granted_minutes > 0),
    valid_until timestamptz not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz
);

create index if not exists idx_accounts_owner on public.accounts(owner_user_id);
create index if not exists idx_devices_account_updated on public.devices(account_id, updated_at);
create index if not exists idx_activation_codes_account on public.activation_codes(account_id, expires_at);
create index if not exists idx_device_activations_account on public.device_activations(account_id, updated_at);
create index if not exists idx_policies_account_updated on public.policies(account_id, updated_at);
create index if not exists idx_policy_rules_policy_updated on public.policy_rules(policy_id, updated_at);
create index if not exists idx_daily_limits_policy_updated on public.daily_limits(policy_id, updated_at);
create index if not exists idx_access_requests_account_status_updated on public.access_requests(account_id, status, updated_at);
create index if not exists idx_extra_time_grants_account_updated on public.extra_time_grants(account_id, updated_at);

create or replace function public.set_updated_at()
returns trigger
language plpgsql
as $$
begin
    new.updated_at = now();
    return new;
end;
$$;

drop trigger if exists trg_accounts_updated_at on public.accounts;
create trigger trg_accounts_updated_at before update on public.accounts
for each row execute function public.set_updated_at();

drop trigger if exists trg_devices_updated_at on public.devices;
create trigger trg_devices_updated_at before update on public.devices
for each row execute function public.set_updated_at();

drop trigger if exists trg_activation_codes_updated_at on public.activation_codes;
create trigger trg_activation_codes_updated_at before update on public.activation_codes
for each row execute function public.set_updated_at();

drop trigger if exists trg_device_activations_updated_at on public.device_activations;
create trigger trg_device_activations_updated_at before update on public.device_activations
for each row execute function public.set_updated_at();

drop trigger if exists trg_policies_updated_at on public.policies;
create trigger trg_policies_updated_at before update on public.policies
for each row execute function public.set_updated_at();

drop trigger if exists trg_policy_rules_updated_at on public.policy_rules;
create trigger trg_policy_rules_updated_at before update on public.policy_rules
for each row execute function public.set_updated_at();

drop trigger if exists trg_daily_limits_updated_at on public.daily_limits;
create trigger trg_daily_limits_updated_at before update on public.daily_limits
for each row execute function public.set_updated_at();

drop trigger if exists trg_access_requests_updated_at on public.access_requests;
create trigger trg_access_requests_updated_at before update on public.access_requests
for each row execute function public.set_updated_at();

drop trigger if exists trg_extra_time_grants_updated_at on public.extra_time_grants;
create trigger trg_extra_time_grants_updated_at before update on public.extra_time_grants
for each row execute function public.set_updated_at();

drop function if exists public.activate_device(text, text, integer);
drop function if exists public.activate_device(text, text, integer, text);

create or replace function public.activate_device(
    activation_code text,
    device_display_name text,
    device_app_version_code integer,
    device_app_role text default 'user'
)
returns table (
    account_id uuid,
    device_id uuid,
    activation_id uuid
)
language plpgsql
security definer
set search_path = public, extensions
as $$
declare
    matched_code public.activation_codes%rowtype;
    inserted_device_id uuid;
    inserted_activation_id uuid;
begin
    if device_app_role not in ('user', 'admin') then
        raise exception 'Invalid device role';
    end if;

    select *
    into matched_code
    from public.activation_codes
    where used_at is null
      and deleted_at is null
      and expires_at > now()
      and code_hash = crypt(activation_code, code_hash)
      and exists (
          select 1
          from public.accounts
          where accounts.id = activation_codes.account_id
            and accounts.owner_user_id = auth.uid()
      )
    limit 1;

    if matched_code.id is null then
        raise exception 'Invalid activation code';
    end if;

    insert into public.devices (
        account_id,
        platform,
        display_name,
        app_role,
        app_version_code,
        last_seen_at
    )
    values (
        matched_code.account_id,
        'android',
        device_display_name,
        device_app_role,
        device_app_version_code,
        now()
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
        auth.uid(),
        matched_code.id
    )
    returning id into inserted_activation_id;

    update public.activation_codes
    set used_at = now(),
        consumed_device_id = inserted_device_id
    where id = matched_code.id;

    return query select matched_code.account_id, inserted_device_id, inserted_activation_id;
end;
$$;

revoke all on function public.activate_device(text, text, integer, text) from public;
grant execute on function public.activate_device(text, text, integer, text) to authenticated;

do $$
declare
    realtime_table text;
begin
    foreach realtime_table in array array[
        'devices',
        'policies',
        'policy_rules',
        'daily_limits',
        'access_requests',
        'extra_time_grants'
    ]
    loop
        if not exists (
            select 1
            from pg_publication_tables
            where pubname = 'supabase_realtime'
            and schemaname = 'public'
            and tablename = realtime_table
        ) then
            execute format('alter publication supabase_realtime add table public.%I', realtime_table);
        end if;
    end loop;
end $$;
