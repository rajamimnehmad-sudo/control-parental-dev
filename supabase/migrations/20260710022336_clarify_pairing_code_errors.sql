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
    normalized_pairing_code text;
begin
    if device_app_role not in ('user', 'admin') then
        raise exception 'Invalid device role';
    end if;

    normalized_pairing_code := upper(regexp_replace(coalesce(pairing_code, ''), '[^A-Za-z0-9]', '', 'g'));
    if normalized_pairing_code = '' then
        raise exception 'Invalid pairing code';
    end if;

    raw_device_token := encode(gen_random_bytes(24), 'hex');

    select *
    into matched_code
    from public.activation_codes
    where code_hash = crypt(normalized_pairing_code, code_hash)
    order by created_at desc
    limit 1;

    if matched_code.id is null then
        raise exception 'Pairing code not found';
    end if;

    if matched_code.deleted_at is not null then
        raise exception 'Pairing code deleted';
    end if;

    if matched_code.used_at is not null then
        raise exception 'Pairing code already used';
    end if;

    if matched_code.expires_at <= now() then
        raise exception 'Pairing code expired';
    end if;

    resolved_app_role := coalesce(matched_code.intended_app_role, device_app_role);
    if resolved_app_role not in ('user', 'admin') or resolved_app_role <> device_app_role then
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
