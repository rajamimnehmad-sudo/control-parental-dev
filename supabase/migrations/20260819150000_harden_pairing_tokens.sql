-- Harden pairing tokens: higher entropy codes, index-backed lookup, and safer pairing flow.

alter table public.activation_codes
add column if not exists code_lookup_hash text;

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
stable
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
stable
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
    legacy_transition_cutoff timestamptz := timestamp '2026-08-19 15:00:00+00';
    -- Legacy bcrypt fallback is transition-only:
    -- accept legacy tokens only if created before hardening rollout.
    -- Remove this branch in a follow-up migration after 2026-09-30.
begin
    normalized_code := public.normalize_pairing_code(pairing_code);
    if normalized_code = '' then
        return null;
    end if;

    normalized_lookup_hash := public.pairing_code_lookup_hash(normalized_code);

    select code.*
    into matched_code
    from public.activation_codes code
    where code.code_lookup_hash = normalized_lookup_hash
      and code.code_hash is not null
      and code.used_at is null
      and code.deleted_at is null
      and code.expires_at > now()
    order by code.created_at desc
    limit 1;

    if matched_code.id is not null then
        return matched_code;
    end if;

    if length(normalized_code) <= 8 then
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

revoke all on function public.create_device_pairing_code(integer) from public;
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
    expiration := now() + make_interval(mins => greatest(5, least(coalesce(ttl_minutes, 15), 60)));

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

revoke all on function public.create_admin_pairing_code(text, text, integer) from public;
grant execute on function public.create_admin_pairing_code(text, text, integer) to authenticated;

create or replace function public.super_admin_create_admin_pairing_code(
    target_community_id uuid,
    admin_display_name text,
    admin_email text default null,
    ttl_minutes integer default 60
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
    expiration := now() + make_interval(mins => greatest(5, least(coalesce(ttl_minutes, 60), 120)));

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

revoke all on function public.super_admin_create_admin_pairing_code(uuid, text, text, integer) from public;
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

create or replace function public.pair_device_with_code_new_or_restore_internal(
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
set search_path = ''
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

    matched_code := public.find_activation_code_by_pairing_code(pairing_code);
    if matched_code.id is null then
        raise exception 'Pairing code not found';
    end if;

    if matched_code.relink_device_id is not null then
        raise exception 'Relink code requires relink flow';
    end if;

    raw_device_token := encode(extensions.gen_random_bytes(24), 'hex');

    resolved_app_role := coalesce(matched_code.intended_app_role, device_app_role);
    if resolved_app_role not in ('user', 'admin') then
        raise exception 'Invalid device role';
    end if;

    if resolved_app_role <> device_app_role then
        raise exception 'Pairing code role mismatch';
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
    into matched_code
    from public.activation_codes
    where id = matched_code.id
      and used_at is null
      and deleted_at is null
      and expires_at > now()
    for update;

    if matched_code.id is null then
        raise exception 'Pairing code not available';
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
        extensions.crypt(raw_device_token, extensions.gen_salt('bf')),
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

revoke all on function public.pair_device_with_code_new_or_restore_internal(text, text, integer, text)
from public, anon, authenticated;

create or replace function public.pair_admin_device_with_password_new_internal(
    pairing_code text,
    admin_email text,
    admin_password text,
    device_display_name text,
    device_app_version_code integer
)
returns table (
    account_id uuid,
    device_id uuid,
    activation_id uuid,
    device_token text
)
language plpgsql
security definer
set search_path = ''
as $$
declare
    matched_code public.activation_codes%rowtype;
    matched_account public.accounts%rowtype;
    matched_admin public.community_admins%rowtype;
    existing_user auth.users%rowtype;
    admin_user_id uuid;
    inserted_device_id uuid;
    inserted_activation_id uuid;
    safe_email text;
    safe_display_name text;
    raw_device_token text;
begin
    safe_email := lower(nullif(trim(admin_email), ''));
    safe_display_name := coalesce(nullif(trim(device_display_name), ''), 'Administrador');

    if safe_email is null or safe_email !~ '^[^@\s]+@[^@\s]+\.[^@\s]+$' then
        raise exception 'Email invalido';
    end if;

    if admin_password is null or length(admin_password) < 8 then
        raise exception 'La contraseña debe tener al menos 8 caracteres';
    end if;

    matched_code := public.find_activation_code_by_pairing_code(pairing_code);
    if matched_code.id is null then
        raise exception 'Token de administrador inválido';
    end if;

    if matched_code.relink_device_id is not null then
        raise exception 'Token de administrador requerido para flujo de relink';
    end if;

    if matched_code.intended_app_role is distinct from 'admin' then
        raise exception 'Token de administrador inválido';
    end if;

    select *
    into matched_code
    from public.activation_codes
    where id = matched_code.id
      and used_at is null
      and deleted_at is null
      and expires_at > now()
    for update;

    if matched_code.id is null then
        raise exception 'Token de administrador inválido';
    end if;

    if matched_code.community_admin_id is null then
        raise exception 'Administrador no encontrado';
    end if;

    select *
    into matched_account
    from public.accounts
    where id = matched_code.account_id
      and deleted_at is null
    limit 1;

    if matched_account.id is null then
        raise exception 'Comunidad no encontrada';
    end if;

    select *
    into matched_admin
    from public.community_admins
    where id = matched_code.community_admin_id
      and deleted_at is null
    limit 1;

    if matched_admin.id is null then
        raise exception 'Administrador no encontrado';
    end if;

    if matched_admin.auth_user_id is not null then
        raise exception 'Este administrador ya fue activado';
    end if;

    if not public.license_allows_activation(matched_code.account_id, 'admin') then
        raise exception 'La licencia no permite activar más administradores';
    end if;

    select *
    into existing_user
    from auth.users
    where lower(email) = safe_email
      and deleted_at is null
    limit 1;

    if existing_user.id is not null then
        if existing_user.encrypted_password is null
           or existing_user.encrypted_password <> extensions.crypt(admin_password, existing_user.encrypted_password) then
            raise exception 'Email o contraseña invalidos';
        end if;

        if exists (
            select 1
            from public.community_admins
            where auth_user_id = existing_user.id
              and id <> matched_admin.id
              and deleted_at is null
        ) then
            raise exception 'Ese email ya pertenece a otro administrador';
        end if;

        admin_user_id := existing_user.id;
    else
        admin_user_id := extensions.gen_random_uuid();

        insert into auth.users (
            instance_id,
            id,
            aud,
            role,
            email,
            encrypted_password,
            email_confirmed_at,
            invited_at,
            confirmation_token,
            confirmation_sent_at,
            recovery_token,
            recovery_sent_at,
            email_change_token_new,
            email_change,
            email_change_sent_at,
            last_sign_in_at,
            raw_app_meta_data,
            raw_user_meta_data,
            is_super_admin,
            created_at,
            updated_at,
            phone,
            phone_confirmed_at,
            phone_change,
            phone_change_token,
            phone_change_sent_at,
            email_change_token_current,
            email_change_confirm_status,
            banned_until,
            reauthentication_token,
            reauthentication_sent_at,
            is_sso_user,
            deleted_at,
            is_anonymous
        ) values (
            '00000000-0000-0000-0000-000000000000'::uuid,
            admin_user_id,
            'authenticated',
            'authenticated',
            safe_email,
            extensions.crypt(admin_password, extensions.gen_salt('bf')),
            now(),
            null,
            '',
            null,
            '',
            null,
            '',
            '',
            null,
            null,
            jsonb_build_object('provider', 'email', 'providers', array['email']),
            jsonb_build_object('name', safe_display_name, 'email_verified', true),
            false,
            now(),
            now(),
            null,
            null,
            '',
            '',
            null,
            '',
            0,
            null,
            '',
            null,
            false,
            null,
            false
        );

        insert into auth.identities (
            provider_id,
            user_id,
            identity_data,
            provider,
            last_sign_in_at,
            created_at,
            updated_at
        ) values (
            admin_user_id::text,
            admin_user_id,
            jsonb_build_object(
                'sub', admin_user_id::text,
                'email', safe_email,
                'email_verified', true,
                'phone_verified', false
            ),
            'email',
            null,
            now(),
            now()
        ) on conflict (provider_id, provider) do nothing;
    end if;

    update public.community_admins
    set auth_user_id = admin_user_id,
        email = safe_email,
        display_name = coalesce(nullif(trim(display_name), ''), safe_display_name)
    where id = matched_admin.id
      and deleted_at is null
      and auth_user_id is null;

    if not found then
        raise exception 'No se pudo vincular el administrador';
    end if;

    raw_device_token := encode(extensions.gen_random_bytes(24), 'hex');

    insert into public.devices (
        account_id,
        platform,
        display_name,
        app_role,
        app_version_code,
        last_seen_at,
        device_token_hash,
        community_admin_id
    ) values (
        matched_code.account_id,
        'android',
        safe_display_name,
        'admin',
        device_app_version_code,
        now(),
        extensions.crypt(raw_device_token, extensions.gen_salt('bf')),
        matched_code.community_admin_id
    ) returning id into inserted_device_id;

    insert into public.device_activations (
        account_id,
        device_id,
        activated_by_user_id,
        activation_code_id
    ) values (
        matched_code.account_id,
        inserted_device_id,
        admin_user_id,
        matched_code.id
    ) returning id into inserted_activation_id;

    update public.activation_codes
    set used_at = now(),
        consumed_device_id = inserted_device_id
    where id = matched_code.id;

    return query select matched_code.account_id, inserted_device_id, inserted_activation_id, raw_device_token;
end;
$$;

revoke all on function public.pair_admin_device_with_password_new_internal(text, text, text, text, integer)
from public, anon, authenticated;

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
    device_token text,
    relink_pending boolean
)
language plpgsql
security definer
set search_path = ''
as $$
declare
    normalized_code text;
    matched_code public.activation_codes%rowtype;
    target_device public.devices%rowtype;
    new_activation_id uuid;
    raw_device_token text;
    relink_expiration timestamptz;
begin
    normalized_code := public.normalize_pairing_code(pairing_code);

    matched_code := public.find_activation_code_by_pairing_code(normalized_code);
    if matched_code.id is null then
        raise exception 'Invalid pairing code';
    end if;

    if matched_code.relink_device_id is null then
        return query
        select paired.account_id, paired.device_id, paired.activation_id, paired.device_token, false
        from public.pair_device_with_code_new_or_restore_internal(
            normalized_code,
            device_display_name,
            device_app_version_code,
            device_app_role
        ) paired;
        return;
    end if;

    select code.*
    into matched_code
    from public.activation_codes code
    where code.id = matched_code.id
    for update;

    if matched_code.deleted_at is not null or matched_code.used_at is not null or matched_code.expires_at <= now() then
        raise exception 'Relink code invalid, expired or already used';
    end if;
    if matched_code.intended_app_role is distinct from device_app_role then
        raise exception 'Relink code role mismatch';
    end if;

    select device.*
    into target_device
    from public.devices device
    where device.id = matched_code.relink_device_id
      and device.account_id = matched_code.account_id
      and device.app_role = device_app_role
      and device.deleted_at is null
    for update;

    if target_device.id is null then
        raise exception 'Relink target not available';
    end if;
    if not public.license_allows_relink(target_device.account_id, device_app_role) then
        raise exception 'Community license does not allow this relink';
    end if;

    perform public.revoke_open_device_relinks(target_device.id);

    raw_device_token := encode(extensions.gen_random_bytes(24), 'hex');
    relink_expiration := least(matched_code.expires_at, now() + interval '30 minutes');

    insert into public.device_activations (
        account_id,
        device_id,
        activated_by_user_id,
        activation_code_id
    )
    select target_device.account_id, target_device.id, account.owner_user_id, matched_code.id
    from public.accounts account
    where account.id = target_device.account_id
      and account.deleted_at is null
    returning id into new_activation_id;

    insert into public.device_relink_sessions (
        activation_code_id,
        account_id,
        device_id,
        activation_id,
        pending_device_token_hash,
        expires_at
    ) values (
        matched_code.id,
        target_device.account_id,
        target_device.id,
        new_activation_id,
        extensions.crypt(raw_device_token, extensions.gen_salt('bf')),
        relink_expiration
    );

    update public.activation_codes
    set used_at = now(), consumed_device_id = target_device.id, updated_at = now()
    where id = matched_code.id;

    return query select target_device.account_id, target_device.id, new_activation_id, raw_device_token, true;
end;
$$;

revoke all on function public.pair_device_with_code(text, text, integer, text) from public, anon, authenticated;
grant execute on function public.pair_device_with_code(text, text, integer, text) to anon, authenticated;

create or replace function public.pair_admin_device_with_password(
    pairing_code text,
    admin_email text,
    admin_password text,
    device_display_name text,
    device_app_version_code integer
)
returns table (
    account_id uuid,
    device_id uuid,
    activation_id uuid,
    device_token text,
    relink_pending boolean
)
language plpgsql
security definer
set search_path = ''
as $$
declare
    normalized_code text;
    matched_code public.activation_codes%rowtype;
    target_device public.devices%rowtype;
    target_admin public.community_admins%rowtype;
    new_activation_id uuid;
    raw_device_token text;
    relink_expiration timestamptz;
begin
    normalized_code := public.normalize_pairing_code(pairing_code);

    matched_code := public.find_activation_code_by_pairing_code(normalized_code);
    if matched_code.id is null then
        raise exception 'Invalid pairing code';
    end if;

    if matched_code.relink_device_id is null then
        return query
        select paired.account_id, paired.device_id, paired.activation_id, paired.device_token, false
        from public.pair_admin_device_with_password_new_internal(
            normalized_code,
            admin_email,
            admin_password,
            device_display_name,
            device_app_version_code
        ) paired;
        return;
    end if;

    if auth.uid() is null then
        raise exception 'Admin authentication required for relink';
    end if;

    select code.*
    into matched_code
    from public.activation_codes code
    where code.id = matched_code.id
    for update;

    if matched_code.deleted_at is not null or matched_code.used_at is not null or matched_code.expires_at <= now() then
        raise exception 'Relink code invalid, expired or already used';
    end if;
    if matched_code.intended_app_role is distinct from 'admin' then
        raise exception 'Relink code role mismatch';
    end if;

    select device.*
    into target_device
    from public.devices device
    where device.id = matched_code.relink_device_id
      and device.account_id = matched_code.account_id
      and device.app_role = 'admin'
      and device.deleted_at is null
    for update;

    select admin.*
    into target_admin
    from public.community_admins admin
    where admin.id = target_device.community_admin_id
      and admin.auth_user_id = auth.uid()
      and lower(admin.email) = lower(trim(admin_email))
      and admin.deleted_at is null;

    if target_device.id is null or target_admin.id is null then
        raise exception 'Admin relink target not authorized';
    end if;
    if not public.license_allows_relink(target_device.account_id, 'admin') then
        raise exception 'Community license does not allow this relink';
    end if;

    perform public.revoke_open_device_relinks(target_device.id);

    raw_device_token := encode(extensions.gen_random_bytes(24), 'hex');
    relink_expiration := least(matched_code.expires_at, now() + interval '30 minutes');

    insert into public.device_activations (
        account_id,
        device_id,
        activated_by_user_id,
        activation_code_id
    ) values (
        target_device.account_id,
        target_device.id,
        auth.uid(),
        matched_code.id
    ) returning id into new_activation_id;

    insert into public.device_relink_sessions (
        activation_code_id,
        account_id,
        device_id,
        activation_id,
        pending_device_token_hash,
        expires_at
    ) values (
        matched_code.id,
        target_device.account_id,
        target_device.id,
        new_activation_id,
        extensions.crypt(raw_device_token, extensions.gen_salt('bf')),
        relink_expiration
    );

    update public.activation_codes
    set used_at = now(), consumed_device_id = target_device.id, updated_at = now()
    where id = matched_code.id;

    return query select target_device.account_id, target_device.id, new_activation_id, raw_device_token, true;
end;
$$;

revoke all on function public.pair_admin_device_with_password(text, text, text, text, integer)
from public, anon, authenticated;
grant execute on function public.pair_admin_device_with_password(text, text, text, text, integer)
to anon, authenticated;
