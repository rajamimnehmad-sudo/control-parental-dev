create or replace function public.create_device_pairing_code(ttl_minutes integer default 180)
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
          and devices.device_token_hash = crypt(device_token, devices.device_token_hash)
        order by devices.created_at asc
        limit 1;
    end if;

    if owner_account_id is null then
        raise exception 'Admin device not found';
    end if;

    raw_code := upper(substr(replace(gen_random_uuid()::text, '-', ''), 1, 6));
    expiration := now() + make_interval(mins => greatest(1, least(coalesce(ttl_minutes, 180), 180)));

    insert into public.activation_codes (
        account_id,
        code_hash,
        intended_app_role,
        community_admin_id,
        expires_at
    )
    values (
        owner_account_id,
        crypt(raw_code, gen_salt('bf')),
        'user',
        creator_admin_id,
        expiration
    );

    return query select raw_code, expiration;
end;
$$;

revoke all on function public.create_device_pairing_code(integer) from public;
grant execute on function public.create_device_pairing_code(integer) to anon, authenticated;

drop function if exists public.super_admin_list_protected_users(uuid);

create or replace function public.super_admin_list_protected_users(target_community_id uuid)
returns table (
    protected_user_id uuid,
    display_name text,
    status text,
    activation_code_id uuid,
    device_id uuid,
    app_version_code integer,
    creator_admin_id uuid,
    creator_admin_name text,
    creator_admin_email text,
    token_created_at timestamptz,
    token_expires_at timestamptz,
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
        devices.id as protected_user_id,
        devices.display_name,
        'activated'::text as status,
        null::uuid as activation_code_id,
        devices.id as device_id,
        devices.app_version_code,
        admins.id as creator_admin_id,
        admins.display_name as creator_admin_name,
        admins.email as creator_admin_email,
        null::timestamptz as token_created_at,
        null::timestamptz as token_expires_at,
        devices.activated_at,
        devices.last_seen_at,
        devices.updated_at
    from public.devices
    join public.accounts on accounts.id = devices.account_id
    left join public.community_admins admins on admins.id = devices.community_admin_id
    where accounts.community_id = target_community_id
      and accounts.deleted_at is null
      and devices.deleted_at is null
      and devices.app_role = 'user'

    union all

    select
        activation_codes.id as protected_user_id,
        coalesce(nullif(trim(activation_codes.intended_display_name), ''), 'Usuario pendiente') as display_name,
        case
            when activation_codes.expires_at <= now() then 'expired'
            else 'pending'
        end as status,
        activation_codes.id as activation_code_id,
        null::uuid as device_id,
        null::integer as app_version_code,
        admins.id as creator_admin_id,
        admins.display_name as creator_admin_name,
        admins.email as creator_admin_email,
        activation_codes.created_at as token_created_at,
        activation_codes.expires_at as token_expires_at,
        null::timestamptz as activated_at,
        null::timestamptz as last_seen_at,
        activation_codes.updated_at
    from public.activation_codes
    join public.accounts on accounts.id = activation_codes.account_id
    left join public.community_admins admins on admins.id = activation_codes.community_admin_id
    where accounts.community_id = target_community_id
      and accounts.deleted_at is null
      and activation_codes.deleted_at is null
      and activation_codes.used_at is null
      and activation_codes.consumed_device_id is null
      and coalesce(activation_codes.intended_app_role, 'user') = 'user'

    order by updated_at desc nulls last, token_created_at desc nulls last;
end;
$$;

revoke execute on function public.super_admin_list_protected_users(uuid) from public, anon;
grant execute on function public.super_admin_list_protected_users(uuid) to authenticated;

create or replace function public.super_admin_delete_protected_user(
    target_community_id uuid,
    target_protected_user_id uuid
)
returns integer
language plpgsql
security definer
set search_path = public
as $$
declare
    target_account_id uuid;
    target_device_id uuid;
    target_activation_code_id uuid;
    deleted_count integer := 0;
    affected integer := 0;
begin
    perform public.require_super_admin();

    select devices.id,
           devices.account_id
    into target_device_id,
         target_account_id
    from public.devices
    join public.accounts on accounts.id = devices.account_id
    where accounts.community_id = target_community_id
      and accounts.deleted_at is null
      and devices.id = target_protected_user_id
      and devices.app_role = 'user'
      and devices.deleted_at is null
    limit 1;

    if target_device_id is null then
        select activation_codes.id,
               activation_codes.account_id
        into target_activation_code_id,
             target_account_id
        from public.activation_codes
        join public.accounts on accounts.id = activation_codes.account_id
        where accounts.community_id = target_community_id
          and accounts.deleted_at is null
          and activation_codes.id = target_protected_user_id
          and coalesce(activation_codes.intended_app_role, 'user') = 'user'
          and activation_codes.deleted_at is null
        limit 1;
    end if;

    if target_device_id is null and target_activation_code_id is null then
        raise exception 'Usuario protegido no encontrado';
    end if;

    if target_device_id is not null then
        update public.device_apps
        set deleted_at = now(),
            updated_at = now()
        where device_id = target_device_id
          and deleted_at is null;
        get diagnostics affected = row_count;
        deleted_count := deleted_count + affected;

        update public.access_requests
        set deleted_at = now(),
            updated_at = now()
        where device_id = target_device_id
          and deleted_at is null;
        get diagnostics affected = row_count;
        deleted_count := deleted_count + affected;

        update public.extra_time_grants
        set deleted_at = now(),
            updated_at = now()
        where request_id in (
            select id
            from public.access_requests
            where device_id = target_device_id
        )
          and deleted_at is null;
        get diagnostics affected = row_count;
        deleted_count := deleted_count + affected;

        update public.policy_rules
        set deleted_at = now(),
            updated_at = now()
        where policy_id in (
            select id
            from public.policies
            where device_id = target_device_id
        )
          and deleted_at is null;
        get diagnostics affected = row_count;
        deleted_count := deleted_count + affected;

        update public.daily_limits
        set deleted_at = now(),
            updated_at = now()
        where policy_id in (
            select id
            from public.policies
            where device_id = target_device_id
        )
          and deleted_at is null;
        get diagnostics affected = row_count;
        deleted_count := deleted_count + affected;

        update public.policies
        set deleted_at = now(),
            updated_at = now(),
            active = false
        where device_id = target_device_id
          and deleted_at is null;
        get diagnostics affected = row_count;
        deleted_count := deleted_count + affected;

        update public.device_activations
        set deleted_at = now(),
            updated_at = now(),
            revoked_at = coalesce(revoked_at, now())
        where device_id = target_device_id
          and deleted_at is null;
        get diagnostics affected = row_count;
        deleted_count := deleted_count + affected;

        update public.activation_codes
        set deleted_at = now(),
            updated_at = now()
        where consumed_device_id = target_device_id
          and deleted_at is null;
        get diagnostics affected = row_count;
        deleted_count := deleted_count + affected;

        update public.devices
        set deleted_at = now(),
            updated_at = now()
        where id = target_device_id
          and account_id = target_account_id
          and app_role = 'user'
          and deleted_at is null;
        get diagnostics affected = row_count;
        deleted_count := deleted_count + affected;
    else
        update public.activation_codes
        set deleted_at = now(),
            updated_at = now()
        where id = target_activation_code_id
          and account_id = target_account_id
          and coalesce(intended_app_role, 'user') = 'user'
          and deleted_at is null;
        get diagnostics affected = row_count;
        deleted_count := deleted_count + affected;
    end if;

    return deleted_count;
end;
$$;

revoke all on function public.super_admin_delete_protected_user(uuid, uuid) from public, anon;
grant execute on function public.super_admin_delete_protected_user(uuid, uuid) to authenticated;
