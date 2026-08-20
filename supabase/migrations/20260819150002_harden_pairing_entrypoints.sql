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
