-- Harden pairing tokens: higher entropy codes, index-backed lookup, and safer pairing flow.
-- Rollback: restore the previous RPC definitions if application compatibility requires it.
-- The nullable hash column, rollout marker and partial index are safe to retain during rollback.

alter table public.activation_codes
add column if not exists code_lookup_hash text;

create table if not exists public.pairing_hardening_rollout (
    singleton boolean primary key default true check (singleton),
    legacy_created_before timestamptz not null
);

alter table public.pairing_hardening_rollout enable row level security;

insert into public.pairing_hardening_rollout (singleton, legacy_created_before)
values (true, transaction_timestamp())
on conflict (singleton) do nothing;

revoke all on table public.pairing_hardening_rollout from public, anon, authenticated;

create unique index if not exists activation_codes_code_lookup_hash_uq
on public.activation_codes(code_lookup_hash)
where code_lookup_hash is not null;

create or replace function public.normalize_pairing_code(raw_code text)
returns text
language sql
immutable
set search_path = ''
as $$
    select upper(regexp_replace(coalesce(raw_code, ''), '[^A-Za-z0-9]', '', 'g'));
$$;

revoke all on function public.normalize_pairing_code(text) from public, anon, authenticated;

create or replace function public.pairing_code_lookup_hash(normalized_code text)
returns text
language sql
immutable
set search_path = ''
as $$
    select encode(extensions.digest(upper(coalesce(normalized_code, '')), 'sha256'), 'hex');
$$;

revoke all on function public.pairing_code_lookup_hash(text) from public, anon, authenticated;

create or replace function public.generate_pairing_token(p_bits integer)
returns text
language plpgsql
volatile
set search_path = ''
as $$
declare
    effective_bits integer := greatest(128, coalesce(p_bits, 128));
    raw_bytes bytea;
    token_chars integer;
begin
    -- Hex gives 4 bits per character and stays uppercase/alphanumeric.
    token_chars := ((effective_bits + 3) / 4);
    raw_bytes := extensions.gen_random_bytes((token_chars + 1) / 2);
    return upper(substr(encode(raw_bytes, 'hex'), 1, token_chars));
end;
$$;

revoke all on function public.generate_pairing_token(integer) from public, anon, authenticated;

create or replace function public.generate_unique_pairing_token(p_bits integer)
returns text
language plpgsql
volatile
set search_path = ''
as $$
declare
    candidate text;
    candidate_hash text;
    attempts integer := 0;
begin
    loop
        candidate := public.generate_pairing_token(p_bits);
        candidate_hash := public.pairing_code_lookup_hash(candidate);
        attempts := attempts + 1;

        if not exists (
            select 1
            from public.activation_codes
            where code_lookup_hash = candidate_hash
        ) then
            return candidate;
        end if;

        if attempts >= 16 then
            raise exception 'Could not generate a unique pairing token';
        end if;
    end loop;
end;
$$;

revoke all on function public.generate_unique_pairing_token(integer) from public, anon, authenticated;

create or replace function public.find_activation_code_by_pairing_code(pairing_code text)
returns public.activation_codes%rowtype
language plpgsql
stable
set search_path = ''
as $$
declare
    normalized_code text;
    normalized_lookup_hash text;
    matched_code public.activation_codes%rowtype;
    legacy_transition_cutoff timestamptz;
    -- Legacy bcrypt fallback is transition-only:
    -- accept legacy tokens only if created before hardening rollout.
    -- Remove this branch in a follow-up migration after 2026-09-30.
begin
    select rollout.legacy_created_before
    into strict legacy_transition_cutoff
    from public.pairing_hardening_rollout rollout
    where rollout.singleton;

    normalized_code := public.normalize_pairing_code(pairing_code);
    if normalized_code = '' then
        return null;
    end if;

    normalized_lookup_hash := public.pairing_code_lookup_hash(normalized_code);

    select code.*
    into matched_code
    from public.activation_codes code
    where length(normalized_code) = 32
      and code.code_lookup_hash = normalized_lookup_hash
      and code.code_hash is not null
      and code.used_at is null
      and code.deleted_at is null
      and code.expires_at > now()
    order by code.created_at desc
    limit 1;

    if matched_code.id is not null then
        return matched_code;
    end if;

    if length(normalized_code) in (6, 8) then
        select code.*
        into matched_code
        from public.activation_codes code
        where code.code_hash = extensions.crypt(normalized_code, code.code_hash)
          and code.code_lookup_hash is null
          and code.created_at <= legacy_transition_cutoff
          and code.used_at is null
          and code.deleted_at is null
          and code.expires_at > now()
        order by code.created_at desc
        limit 1;
    end if;

    return matched_code;
end;
$$;

revoke all on function public.find_activation_code_by_pairing_code(text) from public, anon, authenticated;

create or replace function public.create_device_pairing_code(ttl_minutes integer default 15)
returns table (
    activation_code text,
    expires_at timestamptz
)
language plpgsql
security definer
set search_path = ''
as $$
declare
    owner_account_id uuid;
    creator_admin_id uuid;
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

    device_token := public.request_device_token();
    if device_token is not null then
        select devices.account_id,
               devices.community_admin_id
        into owner_account_id,
             creator_admin_id
        from public.devices
        where devices.deleted_at is null
          and devices.app_role = 'admin'
          and devices.device_token_hash is not null
          and devices.device_token_hash = extensions.crypt(device_token, devices.device_token_hash)
        order by devices.created_at asc
        limit 1;
    end if;

    if owner_account_id is null then
        raise exception 'Admin device not found';
    end if;

    raw_code := public.generate_unique_pairing_token(128);
    expiration := now() + make_interval(mins => greatest(10, least(coalesce(ttl_minutes, 10), 15)));

    insert into public.activation_codes (
        account_id,
        code_hash,
        code_lookup_hash,
        intended_app_role,
        community_admin_id,
        expires_at
    )
    values (
        owner_account_id,
        extensions.crypt(raw_code, extensions.gen_salt('bf')),
        public.pairing_code_lookup_hash(raw_code),
        'user',
        creator_admin_id,
        expiration
    );

    return query select raw_code, expiration;
end;
$$;

revoke all on function public.create_device_pairing_code(integer) from public, anon, authenticated;
grant execute on function public.create_device_pairing_code(integer) to anon, authenticated;

create or replace function public.create_admin_pairing_code(
    admin_display_name text,
    admin_email text default null,
    ttl_minutes integer default 15
)
returns table (
    activation_code text,
    expires_at timestamptz
)
language plpgsql
security definer
set search_path = ''
as $$
declare
    owner_account public.accounts%rowtype;
    new_admin_id uuid;
    raw_code text;
    safe_admin_name text;
    expiration timestamptz;
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

    raw_code := public.generate_unique_pairing_token(128);
    expiration := now() + make_interval(mins => greatest(5, least(coalesce(ttl_minutes, 15), 30)));

    insert into public.activation_codes (
        account_id,
        code_hash,
        code_lookup_hash,
        intended_app_role,
        intended_display_name,
        community_admin_id,
        expires_at
    )
    values (
        owner_account.id,
        extensions.crypt(raw_code, extensions.gen_salt('bf')),
        public.pairing_code_lookup_hash(raw_code),
        'admin',
        safe_admin_name,
        new_admin_id,
        expiration
    );

    return query select raw_code, expiration;
end;
$$;

revoke all on function public.create_admin_pairing_code(text, text, integer) from public, anon, authenticated;
grant execute on function public.create_admin_pairing_code(text, text, integer) to authenticated;

create or replace function public.super_admin_create_admin_pairing_code(
    target_community_id uuid,
    admin_display_name text,
    admin_email text default null,
    ttl_minutes integer default 30
)
returns table (
    community_admin_id uuid,
    activation_code text,
    expires_at timestamptz
)
language plpgsql
security definer
set search_path = ''
as $$
declare
    owner_account_id uuid;
    safe_admin_name text;
    active_license public.community_licenses%rowtype;
    active_admin_count integer;
    new_admin_id uuid;
    raw_code text;
    expiration timestamptz;
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

    raw_code := public.generate_unique_pairing_token(128);
    expiration := now() + make_interval(mins => greatest(5, least(coalesce(ttl_minutes, 30), 60)));

    insert into public.activation_codes (
        account_id,
        code_hash,
        code_lookup_hash,
        intended_app_role,
        intended_display_name,
        community_admin_id,
        expires_at
    )
    values (
        owner_account_id,
        extensions.crypt(raw_code, extensions.gen_salt('bf')),
        public.pairing_code_lookup_hash(raw_code),
        'admin',
        safe_admin_name,
        new_admin_id,
        expiration
    );

    return query select new_admin_id, raw_code, expiration;
end;
$$;

revoke all on function public.super_admin_create_admin_pairing_code(uuid, text, text, integer)
from public, anon, authenticated;
grant execute on function public.super_admin_create_admin_pairing_code(uuid, text, text, integer) to authenticated;

create or replace function public.admin_create_device_relink_code(
    target_device_id uuid,
    ttl_minutes integer default 30
)
returns table (
    activation_code text,
    expires_at timestamptz
)
language plpgsql
security definer
set search_path = ''
as $$
declare
    requesting_admin record;
    target_device record;
    raw_code text;
    expiration timestamptz;
begin
    select device.account_id, device.community_admin_id
    into requesting_admin
    from public.devices device
    where device.app_role = 'admin'
      and device.deleted_at is null
      and public.device_token_matches_device(device.id)
    order by device.created_at asc
    limit 1;

    if requesting_admin.account_id is null then
        raise exception 'Admin device not authorized';
    end if;

    select device.id, device.account_id, device.display_name, device.app_role, device.community_admin_id
    into target_device
    from public.devices device
    where device.id = target_device_id
      and device.account_id = requesting_admin.account_id
      and device.app_role = 'user'
      and device.deleted_at is null
    for update;

    if target_device.id is null then
        raise exception 'Protected user not found';
    end if;

    perform public.revoke_open_device_relinks(target_device.id);

    update public.activation_codes code
    set deleted_at = now(), updated_at = now()
    where code.relink_device_id = target_device.id
      and code.used_at is null
      and code.deleted_at is null;

    raw_code := public.generate_unique_pairing_token(128);
    expiration := now() + make_interval(mins => greatest(1, least(coalesce(ttl_minutes, 30), 30)));

    insert into public.activation_codes (
        account_id,
        code_hash,
        code_lookup_hash,
        intended_app_role,
        intended_display_name,
        community_admin_id,
        relink_device_id,
        expires_at
    )
    values (
        target_device.account_id,
        extensions.crypt(raw_code, extensions.gen_salt('bf')),
        public.pairing_code_lookup_hash(raw_code),
        target_device.app_role,
        target_device.display_name,
        requesting_admin.community_admin_id,
        target_device.id,
        expiration
    );

    return query select raw_code, expiration;
end;
$$;

revoke all on function public.admin_create_device_relink_code(uuid, integer) from public, anon, authenticated;
-- Device-token-only admins authenticate this RPC with the anon JWT plus x-device-token.
-- The function itself resolves and verifies that token before reading the target device.
grant execute on function public.admin_create_device_relink_code(uuid, integer) to anon, authenticated;

create or replace function public.super_admin_create_device_relink_code(
    target_device_id uuid,
    ttl_minutes integer default 30
)
returns table (
    activation_code text,
    expires_at timestamptz
)
language plpgsql
security definer
set search_path = ''
as $$
declare
    target_device record;
    raw_code text;
    expiration timestamptz;
begin
    perform public.require_super_admin();

    select device.id, device.account_id, device.display_name, device.app_role, device.community_admin_id
    into target_device
    from public.devices device
    where device.id = target_device_id
      and device.app_role in ('user', 'admin')
      and device.deleted_at is null
    for update;

    if target_device.id is null then
        raise exception 'Device not found';
    end if;

    perform public.revoke_open_device_relinks(target_device.id);

    update public.activation_codes code
    set deleted_at = now(), updated_at = now()
    where code.relink_device_id = target_device.id
      and code.used_at is null
      and code.deleted_at is null;

    raw_code := public.generate_unique_pairing_token(128);
    expiration := now() + make_interval(mins => greatest(1, least(coalesce(ttl_minutes, 30), 30)));

    insert into public.activation_codes (
        account_id,
        code_hash,
        code_lookup_hash,
        intended_app_role,
        intended_display_name,
        community_admin_id,
        relink_device_id,
        expires_at
    )
    values (
        target_device.account_id,
        extensions.crypt(raw_code, extensions.gen_salt('bf')),
        public.pairing_code_lookup_hash(raw_code),
        target_device.app_role,
        target_device.display_name,
        target_device.community_admin_id,
        target_device.id,
        expiration
    );

    return query select raw_code, expiration;
end;
$$;

revoke all on function public.super_admin_create_device_relink_code(uuid, integer) from public, anon, authenticated;
grant execute on function public.super_admin_create_device_relink_code(uuid, integer) to authenticated;
