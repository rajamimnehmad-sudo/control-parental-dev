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
    matched_archive public.protected_user_deletion_audit%rowtype;
    restoring_admin record;
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

    select code.*
    into matched_code
    from public.activation_codes code
    where code.code_hash = crypt(normalized_pairing_code, code.code_hash)
    order by code.created_at desc
    limit 1
    for update;

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

    safe_display_name := coalesce(
        nullif(trim(matched_code.intended_display_name), ''),
        nullif(trim(device_display_name), '')
    );
    if safe_display_name is null then
        raise exception 'Device name required';
    end if;

    select account.*
    into matched_account
    from public.accounts account
    where account.id = matched_code.account_id
      and account.deleted_at is null
    limit 1;
    if matched_account.id is null then
        raise exception 'Account not found';
    end if;

    if not public.license_allows_activation(matched_code.account_id, resolved_app_role) then
        raise exception 'Community license does not allow this activation';
    end if;

    if matched_code.restore_archive_id is null then
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
            resolved_app_role,
            device_app_version_code,
            now(),
            crypt(raw_device_token, gen_salt('bf')),
            matched_code.community_admin_id
        ) returning id into inserted_device_id;
    else
        select archive.*
        into matched_archive
        from public.protected_user_deletion_audit archive
        where archive.id = matched_code.restore_archive_id
          and archive.account_id = matched_code.account_id
          and archive.restored_at is null
        for update;
        if matched_archive.id is null then
            raise exception 'Archived protected user not available';
        end if;
        if matched_archive.snapshot is null or matched_archive.snapshot_version <> 1 then
            raise exception 'Archived protected user requires manual review';
        end if;

        select admin.id, admin.auth_user_id
        into restoring_admin
        from public.community_admins admin
        where admin.id = matched_code.community_admin_id
          and admin.community_id = matched_archive.community_id
          and admin.deleted_at is null
        limit 1;
        if restoring_admin.id is null then
            raise exception 'Restoring admin not available';
        end if;

        update public.devices as device
        set deleted_at = null,
            platform = 'android',
            display_name = safe_display_name,
            app_role = 'user',
            app_version_code = device_app_version_code,
            last_seen_at = now(),
            updated_at = now(),
            device_token_hash = crypt(raw_device_token, gen_salt('bf')),
            vpn_state = 'Unknown',
            accessibility_state = 'Unknown',
            device_admin_state = 'Unknown',
            protection_alert = null,
            protection_updated_at = null,
            applied_policy_id = null,
            applied_policy_revision = null,
            policy_applied_at = null
        where device.id = matched_archive.device_id
          and device.account_id = matched_archive.account_id
          and device.deleted_at is not null
        returning device.id into inserted_device_id;
        if inserted_device_id is null then
            raise exception 'Archived device not available';
        end if;

        update public.policies policy
        set deleted_at = null,
            active = (item.value ->> 'active')::boolean,
            updated_at = now()
        from jsonb_array_elements(coalesce(matched_archive.snapshot -> 'policies', '[]'::jsonb)) item(value)
        where policy.id = (item.value ->> 'id')::uuid
          and policy.device_id = inserted_device_id
          and policy.account_id = matched_archive.account_id;

        update public.policy_rules rule
        set deleted_at = null,
            enabled = (item.value ->> 'enabled')::boolean,
            updated_at = now()
        from jsonb_array_elements(coalesce(matched_archive.snapshot -> 'policy_rules', '[]'::jsonb)) item(value)
        where rule.id = (item.value ->> 'id')::uuid
          and rule.account_id = matched_archive.account_id
          and exists (
              select 1 from public.policies policy
              where policy.id = rule.policy_id and policy.device_id = inserted_device_id
          );

        update public.daily_limits daily_limit
        set deleted_at = null,
            enabled = (item.value ->> 'enabled')::boolean,
            updated_at = now()
        from jsonb_array_elements(coalesce(matched_archive.snapshot -> 'daily_limits', '[]'::jsonb)) item(value)
        where daily_limit.id = (item.value ->> 'id')::uuid
          and daily_limit.account_id = matched_archive.account_id
          and exists (
              select 1 from public.policies policy
              where policy.id = daily_limit.policy_id and policy.device_id = inserted_device_id
          );

        update public.app_groups app_group
        set deleted_at = null,
            enabled = (item.value ->> 'enabled')::boolean,
            updated_at = now()
        from jsonb_array_elements(coalesce(matched_archive.snapshot -> 'app_groups', '[]'::jsonb)) item(value)
        where app_group.id = (item.value ->> 'id')::uuid
          and app_group.device_id = inserted_device_id
          and app_group.account_id = matched_archive.account_id;

        update public.app_group_apps group_app
        set deleted_at = null,
            enabled = (item.value ->> 'enabled')::boolean,
            updated_at = now()
        from jsonb_array_elements(coalesce(matched_archive.snapshot -> 'app_group_apps', '[]'::jsonb)) item(value)
        where group_app.id = (item.value ->> 'id')::uuid
          and group_app.device_id = inserted_device_id
          and group_app.account_id = matched_archive.account_id;

        update public.device_apps device_app
        set deleted_at = null,
            updated_at = now()
        from jsonb_array_elements(coalesce(matched_archive.snapshot -> 'device_apps', '[]'::jsonb)) item(value)
        where device_app.id = (item.value ->> 'id')::uuid
          and device_app.device_id = inserted_device_id
          and device_app.account_id = matched_archive.account_id;

        update public.protected_user_group_members member
        set deleted_at = null,
            updated_at = now()
        from jsonb_array_elements(
            coalesce(matched_archive.snapshot -> 'protected_user_group_members', '[]'::jsonb)
        ) item(value)
        where member.id = (item.value ->> 'id')::uuid
          and member.device_id = inserted_device_id
          and member.account_id = matched_archive.account_id;

        update public.device_protection_controls as control
        set armed = coalesce(
                (matched_archive.snapshot -> 'protection_control' ->> 'armed')::boolean,
                control.armed
            ),
            authorization_scope = 'none',
            authorization_expires_at = null,
            command_revision = control.command_revision + 1,
            updated_at = now()
        where control.device_id = inserted_device_id
          and control.account_id = matched_archive.account_id;

        update public.protected_user_deletion_audit
        set restored_at = now(),
            restored_by_admin_id = restoring_admin.id,
            restored_by_auth_user_id = restoring_admin.auth_user_id,
            restore_activation_code_id = matched_code.id
        where id = matched_archive.id;
    end if;

    insert into public.device_activations (
        account_id,
        device_id,
        activated_by_user_id,
        activation_code_id
    ) values (
        matched_code.account_id,
        inserted_device_id,
        matched_account.owner_user_id,
        matched_code.id
    ) returning id into inserted_activation_id;

    update public.activation_codes
    set used_at = now(),
        consumed_device_id = inserted_device_id,
        updated_at = now()
    where id = matched_code.id;

    return query select matched_code.account_id, inserted_device_id, inserted_activation_id, raw_device_token;
end;
$$;

revoke all on function public.pair_device_with_code(text, text, integer, text) from public, anon, authenticated;
grant execute on function public.pair_device_with_code(text, text, integer, text) to anon, authenticated;
