create table if not exists public.communities (
    id uuid primary key default gen_random_uuid(),
    name text not null unique,
    guide_label text not null default 'Equipo de guías',
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz
);

drop trigger if exists trg_communities_updated_at on public.communities;
create trigger trg_communities_updated_at before update on public.communities
for each row execute function public.set_updated_at();

alter table public.accounts
add column if not exists community_id uuid references public.communities(id) on delete set null;

create index if not exists idx_accounts_community on public.accounts(community_id);

create table if not exists public.community_admins (
    id uuid primary key default gen_random_uuid(),
    community_id uuid not null references public.communities(id) on delete cascade,
    display_name text not null,
    email text,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz
);

drop trigger if exists trg_community_admins_updated_at on public.community_admins;
create trigger trg_community_admins_updated_at before update on public.community_admins
for each row execute function public.set_updated_at();

create index if not exists idx_community_admins_community on public.community_admins(community_id, updated_at);

alter table public.activation_codes
add column if not exists intended_app_role text;

alter table public.activation_codes
add column if not exists intended_display_name text;

alter table public.activation_codes
add column if not exists community_admin_id uuid references public.community_admins(id) on delete set null;

update public.activation_codes
set intended_app_role = coalesce(intended_app_role, 'user')
where intended_app_role is null;

alter table public.activation_codes
alter column intended_app_role set default 'user';

do $$
begin
    if not exists (
        select 1
        from pg_constraint
        where conname = 'activation_codes_intended_app_role_check'
    ) then
        alter table public.activation_codes
        add constraint activation_codes_intended_app_role_check
        check (intended_app_role in ('user', 'admin'));
    end if;
end $$;

alter table public.devices
add column if not exists community_admin_id uuid references public.community_admins(id) on delete set null;

do $$
declare
    default_community_id uuid;
begin
    insert into public.communities (name, guide_label)
    values ('Comunidad Primero Año', 'Equipo de guías')
    on conflict (name) do update
    set updated_at = now()
    returning id into default_community_id;

    if default_community_id is null then
        select id
        into default_community_id
        from public.communities
        where name = 'Comunidad Primero Año'
        limit 1;
    end if;

    update public.accounts
    set community_id = default_community_id,
        name = 'Comunidad Primero Año'
    where community_id is null
      and deleted_at is null;
end $$;

-- Remove the old DEV "license 1-100" pool as available credentials. Used rows stay as history.
update public.activation_codes ac
set deleted_at = now()
where ac.used_at is null
  and ac.deleted_at is null
  and exists (
      select 1
      from generate_series(1, 100) code(value)
      where ac.code_hash = crypt(code.value::text, ac.code_hash)
  );

create or replace function public.device_token_matches_role(
    target_account_id uuid,
    required_app_role text
)
returns boolean
language plpgsql
security definer
stable
set search_path = public, extensions
as $$
declare
    token text;
begin
    token := public.request_device_token();
    if token is null then
        return false;
    end if;

    return exists (
        select 1
        from public.devices
        where account_id = target_account_id
          and deleted_at is null
          and app_role = required_app_role
          and device_token_hash is not null
          and device_token_hash = crypt(token, device_token_hash)
    );
end;
$$;

create or replace function public.admin_device_token_matches(target_account_id uuid)
returns boolean
language sql
security definer
stable
set search_path = public
as $$
    select public.device_token_matches_role(target_account_id, 'admin');
$$;

revoke all on function public.device_token_matches_role(uuid, text) from public;
revoke all on function public.admin_device_token_matches(uuid) from public;
grant execute on function public.device_token_matches_role(uuid, text) to anon, authenticated;
grant execute on function public.admin_device_token_matches(uuid) to anon, authenticated;

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

create or replace function public.create_admin_pairing_code(
    admin_display_name text,
    admin_email text default null,
    ttl_minutes integer default 60
)
returns table (
    activation_code text,
    expires_at timestamptz
)
language plpgsql
security definer
set search_path = public, extensions
as $$
declare
    owner_account public.accounts%rowtype;
    new_admin_id uuid;
    raw_code text;
    expiration timestamptz;
    safe_admin_name text;
begin
    if auth.uid() is null then
        raise exception 'Authentication required';
    end if;

    safe_admin_name := nullif(trim(admin_display_name), '');
    if safe_admin_name is null then
        raise exception 'Admin name required';
    end if;

    select *
    into owner_account
    from public.accounts
    where owner_user_id = auth.uid()
      and deleted_at is null
    order by created_at asc
    limit 1;

    if owner_account.id is null then
        raise exception 'Account not found';
    end if;

    insert into public.community_admins (
        community_id,
        display_name,
        email
    )
    values (
        owner_account.community_id,
        safe_admin_name,
        nullif(trim(admin_email), '')
    )
    returning id into new_admin_id;

    raw_code := upper(substr(replace(gen_random_uuid()::text, '-', ''), 1, 8));
    expiration := now() + make_interval(mins => greatest(5, least(coalesce(ttl_minutes, 60), 1440)));

    insert into public.activation_codes (
        account_id,
        code_hash,
        intended_app_role,
        intended_display_name,
        community_admin_id,
        expires_at
    )
    values (
        owner_account.id,
        crypt(raw_code, gen_salt('bf')),
        'admin',
        safe_admin_name,
        new_admin_id,
        expiration
    );

    return query select raw_code, expiration;
end;
$$;

revoke all on function public.create_admin_pairing_code(text, text, integer) from public;
grant execute on function public.create_admin_pairing_code(text, text, integer) to authenticated;

drop function if exists public.pair_device_with_code(text, text, integer, text);

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

alter table public.communities enable row level security;
alter table public.community_admins enable row level security;

drop policy if exists "accounts_device_token_select" on public.accounts;
create policy "accounts_device_token_select" on public.accounts
for select
using (public.device_token_matches(id));

drop policy if exists "communities_account_owner_select" on public.communities;
create policy "communities_account_owner_select" on public.communities
for select
using (
    exists (
        select 1
        from public.accounts
        where accounts.community_id = communities.id
          and accounts.deleted_at is null
          and (
              accounts.owner_user_id = auth.uid()
              or public.device_token_matches(accounts.id)
          )
    )
);

drop policy if exists "communities_account_owner_all" on public.communities;
create policy "communities_account_owner_all" on public.communities
for all
using (
    exists (
        select 1
        from public.accounts
        where accounts.community_id = communities.id
          and accounts.owner_user_id = auth.uid()
          and accounts.deleted_at is null
    )
)
with check (
    exists (
        select 1
        from public.accounts
        where accounts.community_id = communities.id
          and accounts.owner_user_id = auth.uid()
          and accounts.deleted_at is null
    )
);

drop policy if exists "community_admins_account_owner_select" on public.community_admins;
create policy "community_admins_account_owner_select" on public.community_admins
for select
using (
    exists (
        select 1
        from public.accounts
        where accounts.community_id = community_admins.community_id
          and accounts.deleted_at is null
          and (
              accounts.owner_user_id = auth.uid()
              or public.device_token_matches(accounts.id)
          )
    )
);

drop policy if exists "community_admins_account_owner_all" on public.community_admins;
create policy "community_admins_account_owner_all" on public.community_admins
for all
using (
    exists (
        select 1
        from public.accounts
        where accounts.community_id = community_admins.community_id
          and accounts.owner_user_id = auth.uid()
          and accounts.deleted_at is null
    )
)
with check (
    exists (
        select 1
        from public.accounts
        where accounts.community_id = community_admins.community_id
          and accounts.owner_user_id = auth.uid()
          and accounts.deleted_at is null
    )
);

drop policy if exists "policies_admin_device_token_all" on public.policies;
create policy "policies_admin_device_token_all" on public.policies
for all
using (public.admin_device_token_matches(account_id))
with check (public.admin_device_token_matches(account_id));

drop policy if exists "policy_rules_admin_device_token_all" on public.policy_rules;
create policy "policy_rules_admin_device_token_all" on public.policy_rules
for all
using (public.admin_device_token_matches(account_id))
with check (public.admin_device_token_matches(account_id));

drop policy if exists "daily_limits_admin_device_token_all" on public.daily_limits;
create policy "daily_limits_admin_device_token_all" on public.daily_limits
for all
using (public.admin_device_token_matches(account_id))
with check (public.admin_device_token_matches(account_id));

drop policy if exists "extra_time_grants_admin_device_token_all" on public.extra_time_grants;
create policy "extra_time_grants_admin_device_token_all" on public.extra_time_grants
for all
using (public.admin_device_token_matches(account_id))
with check (public.admin_device_token_matches(account_id));

grant select on public.accounts to anon, authenticated;
grant select on public.communities to anon, authenticated;
grant select on public.community_admins to anon, authenticated;
grant insert, update on public.community_admins to authenticated;
grant select, insert, update on public.policies to anon;
grant select, insert, update on public.policy_rules to anon;
grant select, insert, update on public.daily_limits to anon;
grant select, insert, update on public.extra_time_grants to anon;
