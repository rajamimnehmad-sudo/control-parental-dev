-- Development-only all-in-one setup for the Supabase MVP.
-- Paste this file into Supabase SQL Editor after creating the Auth user.
--
-- Required manual value:
-- Replace every DEV_AUTH_USER_ID_HERE below with the Supabase Auth User UID.
--
-- This file combines, in order:
-- 1. schema
-- 2. RLS
-- 3. minimal development test data
--
-- Never paste a Service Role Key in Android, Gradle or .env.

create schema if not exists extensions;
create extension if not exists "pgcrypto" with schema extensions;

create table if not exists public.accounts (
    id uuid primary key default gen_random_uuid(),
    owner_user_id uuid not null references auth.users(id) on delete cascade,
    name text not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz
);

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

alter table public.accounts enable row level security;
alter table public.devices enable row level security;
alter table public.activation_codes enable row level security;
alter table public.device_activations enable row level security;
alter table public.policies enable row level security;
alter table public.policy_rules enable row level security;
alter table public.daily_limits enable row level security;
alter table public.access_requests enable row level security;
alter table public.extra_time_grants enable row level security;

drop policy if exists "accounts_owner_all" on public.accounts;
create policy "accounts_owner_all" on public.accounts
for all
using (owner_user_id = auth.uid())
with check (owner_user_id = auth.uid());

drop policy if exists "devices_account_owner_all" on public.devices;
create policy "devices_account_owner_all" on public.devices
for all
using (
    exists (
        select 1 from public.accounts
        where accounts.id = devices.account_id
        and accounts.owner_user_id = auth.uid()
    )
)
with check (
    exists (
        select 1 from public.accounts
        where accounts.id = devices.account_id
        and accounts.owner_user_id = auth.uid()
    )
);

drop policy if exists "activation_codes_account_owner_all" on public.activation_codes;
create policy "activation_codes_account_owner_all" on public.activation_codes
for all
using (
    exists (
        select 1 from public.accounts
        where accounts.id = activation_codes.account_id
        and accounts.owner_user_id = auth.uid()
    )
)
with check (
    exists (
        select 1 from public.accounts
        where accounts.id = activation_codes.account_id
        and accounts.owner_user_id = auth.uid()
    )
);

drop policy if exists "device_activations_account_owner_all" on public.device_activations;
create policy "device_activations_account_owner_all" on public.device_activations
for all
using (
    exists (
        select 1 from public.accounts
        where accounts.id = device_activations.account_id
        and accounts.owner_user_id = auth.uid()
    )
)
with check (
    exists (
        select 1 from public.accounts
        where accounts.id = device_activations.account_id
        and accounts.owner_user_id = auth.uid()
    )
);

drop policy if exists "policies_account_owner_all" on public.policies;
create policy "policies_account_owner_all" on public.policies
for all
using (
    exists (
        select 1 from public.accounts
        where accounts.id = policies.account_id
        and accounts.owner_user_id = auth.uid()
    )
)
with check (
    exists (
        select 1 from public.accounts
        where accounts.id = policies.account_id
        and accounts.owner_user_id = auth.uid()
    )
);

drop policy if exists "policy_rules_account_owner_all" on public.policy_rules;
create policy "policy_rules_account_owner_all" on public.policy_rules
for all
using (
    exists (
        select 1 from public.accounts
        where accounts.id = policy_rules.account_id
        and accounts.owner_user_id = auth.uid()
    )
)
with check (
    exists (
        select 1 from public.accounts
        where accounts.id = policy_rules.account_id
        and accounts.owner_user_id = auth.uid()
    )
);

drop policy if exists "daily_limits_account_owner_all" on public.daily_limits;
create policy "daily_limits_account_owner_all" on public.daily_limits
for all
using (
    exists (
        select 1 from public.accounts
        where accounts.id = daily_limits.account_id
        and accounts.owner_user_id = auth.uid()
    )
)
with check (
    exists (
        select 1 from public.accounts
        where accounts.id = daily_limits.account_id
        and accounts.owner_user_id = auth.uid()
    )
);

drop policy if exists "access_requests_account_owner_all" on public.access_requests;
create policy "access_requests_account_owner_all" on public.access_requests
for all
using (
    exists (
        select 1 from public.accounts
        where accounts.id = access_requests.account_id
        and accounts.owner_user_id = auth.uid()
    )
)
with check (
    exists (
        select 1 from public.accounts
        where accounts.id = access_requests.account_id
        and accounts.owner_user_id = auth.uid()
    )
);

drop policy if exists "extra_time_grants_account_owner_all" on public.extra_time_grants;
create policy "extra_time_grants_account_owner_all" on public.extra_time_grants
for all
using (
    exists (
        select 1 from public.accounts
        where accounts.id = extra_time_grants.account_id
        and accounts.owner_user_id = auth.uid()
    )
)
with check (
    exists (
        select 1 from public.accounts
        where accounts.id = extra_time_grants.account_id
        and accounts.owner_user_id = auth.uid()
    )
);

do $$
declare
    dev_owner_user_id_text text := 'DEV_AUTH_USER_ID_HERE';
    dev_owner_user_id uuid;
    dev_account_id uuid;
    dev_policy_id uuid;
    dev_code text;
begin
    if dev_owner_user_id_text = 'DEV_AUTH_USER_ID_HERE' then
        raise exception 'Replace DEV_AUTH_USER_ID_HERE with the Supabase Auth User UID before running this file.';
    end if;

    dev_owner_user_id := dev_owner_user_id_text::uuid;

    if not exists (
        select 1
        from auth.users
        where id = dev_owner_user_id
    ) then
        raise exception 'No auth.users row exists for %. Create the Auth user first, then rerun this SQL.', dev_owner_user_id;
    end if;

    select id
    into dev_account_id
    from public.accounts
    where owner_user_id = dev_owner_user_id
      and name = 'Dev E2E Account'
      and deleted_at is null
    order by created_at
    limit 1;

    if dev_account_id is null then
        insert into public.accounts (owner_user_id, name)
        values (dev_owner_user_id, 'Dev E2E Account')
        returning id into dev_account_id;
    end if;

    select id
    into dev_policy_id
    from public.policies
    where account_id = dev_account_id
      and active = true
      and deleted_at is null
    order by created_at
    limit 1;

    if dev_policy_id is null then
        insert into public.policies (account_id, version, active)
        values (dev_account_id, 1, true)
        returning id into dev_policy_id;
    end if;

    if not exists (
        select 1
        from public.policy_rules
        where account_id = dev_account_id
          and policy_id = dev_policy_id
          and scope = 'Global'
          and target = '*'
          and action = 'RequestAuthorization'
          and deleted_at is null
    ) then
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
            dev_account_id,
            dev_policy_id,
            'Global',
            '*',
            'RequestAuthorization',
            0,
            true
        );
    end if;

    if not exists (
        select 1
        from public.daily_limits
        where account_id = dev_account_id
          and policy_id = dev_policy_id
          and target_type = 'Global'
          and target = '*'
          and deleted_at is null
    ) then
        insert into public.daily_limits (
            account_id,
            policy_id,
            target_type,
            target,
            limit_minutes,
            enabled
        )
        values (
            dev_account_id,
            dev_policy_id,
            'Global',
            '*',
            120,
            true
        );
    end if;

    if not exists (
        select 1
        from public.activation_codes
        where account_id = dev_account_id
          and used_at is null
          and deleted_at is null
          and expires_at > now()
          and code_hash = crypt('TEST-USER-CODE', code_hash)
    ) then
        insert into public.activation_codes (account_id, code_hash, expires_at)
        values (
            dev_account_id,
            crypt('TEST-USER-CODE', gen_salt('bf')),
            now() + interval '14 days'
        );
    end if;

    if not exists (
        select 1
        from public.activation_codes
        where account_id = dev_account_id
          and used_at is null
          and deleted_at is null
          and expires_at > now()
          and code_hash = crypt('TEST-ADMIN-CODE', code_hash)
    ) then
        insert into public.activation_codes (account_id, code_hash, expires_at)
        values (
            dev_account_id,
            crypt('TEST-ADMIN-CODE', gen_salt('bf')),
            now() + interval '14 days'
        );
    end if;

    for dev_code in
        select generate_series(1, 100)::text
    loop
        if not exists (
            select 1
            from public.activation_codes
            where account_id = dev_account_id
              and deleted_at is null
              and code_hash = extensions.crypt(dev_code, code_hash)
        ) then
            insert into public.activation_codes (
                account_id,
                code_hash,
                expires_at,
                used_at,
                deleted_at
            )
            values (
                dev_account_id,
                extensions.crypt(dev_code, extensions.gen_salt('bf')),
                now() + interval '5 years',
                null,
                null
            );
        end if;
    end loop;

    raise notice 'Dev E2E account_id: %', dev_account_id;
    raise notice 'Activation code for App Usuario: TEST-USER-CODE';
    raise notice 'Activation code for App Admin: TEST-ADMIN-CODE';
    raise notice 'DEV activation codes for App Usuario: 1-50';
    raise notice 'DEV activation codes for App Admin: 51-100';
    raise notice 'Use the same Auth email/password in both apps.';
end $$;
