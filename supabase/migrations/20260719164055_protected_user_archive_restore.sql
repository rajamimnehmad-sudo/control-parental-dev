alter table public.protected_user_deletion_audit
    add column if not exists snapshot jsonb,
    add column if not exists snapshot_version smallint,
    add column if not exists restored_at timestamptz,
    add column if not exists restored_by_admin_id uuid references public.community_admins(id) on delete restrict,
    add column if not exists restored_by_auth_user_id uuid references auth.users(id) on delete restrict,
    add column if not exists restore_activation_code_id uuid references public.activation_codes(id) on delete restrict;

alter table public.activation_codes
    add column if not exists restore_archive_id uuid references public.protected_user_deletion_audit(id) on delete restrict;

do $$
begin
    if not exists (
        select 1
        from pg_constraint
        where conname = 'protected_user_archive_snapshot_version_check'
          and conrelid = 'public.protected_user_deletion_audit'::regclass
    ) then
        alter table public.protected_user_deletion_audit
            add constraint protected_user_archive_snapshot_version_check
            check (
                (snapshot is null and snapshot_version is null)
                or (snapshot is not null and snapshot_version = 1)
            );
    end if;
end;
$$;

create unique index if not exists protected_user_archive_open_device_idx
    on public.protected_user_deletion_audit (device_id)
    where restored_at is null;

create index if not exists protected_user_archive_community_created_idx
    on public.protected_user_deletion_audit (community_id, created_at desc)
    where restored_at is null;

create index if not exists protected_user_archive_restored_by_admin_idx
    on public.protected_user_deletion_audit (restored_by_admin_id)
    where restored_by_admin_id is not null;

create index if not exists protected_user_archive_restored_by_auth_user_idx
    on public.protected_user_deletion_audit (restored_by_auth_user_id)
    where restored_by_auth_user_id is not null;

create index if not exists protected_user_archive_restore_activation_code_idx
    on public.protected_user_deletion_audit (restore_activation_code_id)
    where restore_activation_code_id is not null;

create index if not exists activation_codes_restore_archive_idx
    on public.activation_codes (restore_archive_id)
    where restore_archive_id is not null;

create or replace function public.admin_archive_protected_user(target_device_id uuid)
returns table (audit_id uuid, affected_rows integer)
language plpgsql
security definer
set search_path = public
as $$
declare
    current_admin record;
    target_device record;
    archive_snapshot jsonb;
    changed integer := 0;
    total_changed integer := 0;
    created_audit_id uuid;
begin
    if auth.uid() is null then
        raise exception 'Authentication required';
    end if;

    select admin.id, admin.community_id
    into current_admin
    from public.community_admins admin
    where admin.auth_user_id = auth.uid()
      and admin.deleted_at is null
    order by admin.id
    limit 1;
    if current_admin.id is null then
        raise exception 'Active community admin required';
    end if;

    select device.id, device.account_id, device.display_name
    into target_device
    from public.devices device
    join public.accounts account on account.id = device.account_id
    where device.id = target_device_id
      and device.app_role = 'user'
      and device.deleted_at is null
      and account.deleted_at is null
      and account.community_id = current_admin.community_id
    for update of device;
    if target_device.id is null then
        raise exception 'Protected user not found';
    end if;

    archive_snapshot := jsonb_build_object(
        'policies', coalesce((
            select jsonb_agg(jsonb_build_object('id', policy.id, 'active', policy.active) order by policy.id)
            from public.policies policy
            where policy.device_id = target_device.id and policy.deleted_at is null
        ), '[]'::jsonb),
        'policy_rules', coalesce((
            select jsonb_agg(jsonb_build_object('id', rule.id, 'enabled', rule.enabled) order by rule.id)
            from public.policy_rules rule
            where rule.policy_id in (
                select policy.id from public.policies policy
                where policy.device_id = target_device.id and policy.deleted_at is null
            ) and rule.deleted_at is null
        ), '[]'::jsonb),
        'daily_limits', coalesce((
            select jsonb_agg(jsonb_build_object('id', daily_limit.id, 'enabled', daily_limit.enabled) order by daily_limit.id)
            from public.daily_limits daily_limit
            where daily_limit.policy_id in (
                select policy.id from public.policies policy
                where policy.device_id = target_device.id and policy.deleted_at is null
            ) and daily_limit.deleted_at is null
        ), '[]'::jsonb),
        'app_groups', coalesce((
            select jsonb_agg(jsonb_build_object('id', app_group.id, 'enabled', app_group.enabled) order by app_group.id)
            from public.app_groups app_group
            where app_group.device_id = target_device.id and app_group.deleted_at is null
        ), '[]'::jsonb),
        'app_group_apps', coalesce((
            select jsonb_agg(jsonb_build_object('id', group_app.id, 'enabled', group_app.enabled) order by group_app.id)
            from public.app_group_apps group_app
            where group_app.device_id = target_device.id and group_app.deleted_at is null
        ), '[]'::jsonb),
        'device_apps', coalesce((
            select jsonb_agg(jsonb_build_object('id', device_app.id) order by device_app.id)
            from public.device_apps device_app
            where device_app.device_id = target_device.id and device_app.deleted_at is null
        ), '[]'::jsonb),
        'protected_user_group_members', coalesce((
            select jsonb_agg(jsonb_build_object('id', member.id) order by member.id)
            from public.protected_user_group_members member
            where member.device_id = target_device.id and member.deleted_at is null
        ), '[]'::jsonb),
        'protection_control', coalesce((
            select jsonb_build_object('armed', control.armed)
            from public.device_protection_controls control
            where control.device_id = target_device.id
        ), '{}'::jsonb)
    );

    insert into public.protected_user_deletion_audit (
        community_id,
        account_id,
        device_id,
        device_display_name,
        deleted_by_admin_id,
        deleted_by_auth_user_id,
        affected_rows,
        snapshot,
        snapshot_version
    ) values (
        current_admin.community_id,
        target_device.account_id,
        target_device.id,
        target_device.display_name,
        current_admin.id,
        auth.uid(),
        1,
        archive_snapshot,
        1
    ) returning id into created_audit_id;

    update public.device_protection_controls
    set authorization_scope = 'none',
        authorization_expires_at = null,
        command_revision = command_revision + 1,
        updated_at = now()
    where device_id = target_device.id;
    get diagnostics changed = row_count; total_changed := total_changed + changed;

    update public.device_apps set deleted_at = now(), updated_at = now()
    where device_id = target_device.id and deleted_at is null;
    get diagnostics changed = row_count; total_changed := total_changed + changed;

    update public.access_requests set deleted_at = now(), updated_at = now()
    where device_id = target_device.id and deleted_at is null;
    get diagnostics changed = row_count; total_changed := total_changed + changed;

    update public.app_group_apps set deleted_at = now(), updated_at = now()
    where device_id = target_device.id and deleted_at is null;
    get diagnostics changed = row_count; total_changed := total_changed + changed;

    update public.app_groups set deleted_at = now(), updated_at = now(), enabled = false
    where device_id = target_device.id and deleted_at is null;
    get diagnostics changed = row_count; total_changed := total_changed + changed;

    update public.protected_user_group_members set deleted_at = now(), updated_at = now()
    where device_id = target_device.id and deleted_at is null;
    get diagnostics changed = row_count; total_changed := total_changed + changed;

    update public.policy_rules set deleted_at = now(), updated_at = now(), enabled = false
    where policy_id in (select id from public.policies where device_id = target_device.id)
      and deleted_at is null;
    get diagnostics changed = row_count; total_changed := total_changed + changed;

    update public.daily_limits set deleted_at = now(), updated_at = now(), enabled = false
    where policy_id in (select id from public.policies where device_id = target_device.id)
      and deleted_at is null;
    get diagnostics changed = row_count; total_changed := total_changed + changed;

    update public.policies set deleted_at = now(), updated_at = now(), active = false
    where device_id = target_device.id and deleted_at is null;
    get diagnostics changed = row_count; total_changed := total_changed + changed;

    update public.device_activations
    set deleted_at = now(), updated_at = now(), revoked_at = coalesce(revoked_at, now())
    where device_id = target_device.id and deleted_at is null;
    get diagnostics changed = row_count; total_changed := total_changed + changed;

    update public.activation_codes set deleted_at = now(), updated_at = now()
    where consumed_device_id = target_device.id and deleted_at is null;
    get diagnostics changed = row_count; total_changed := total_changed + changed;

    update public.device_push_tokens set deleted_at = now(), updated_at = now()
    where device_id = target_device.id and deleted_at is null;
    get diagnostics changed = row_count; total_changed := total_changed + changed;

    update public.devices
    set deleted_at = now(), updated_at = now(), last_seen_at = now(), device_token_hash = null
    where id = target_device.id and deleted_at is null;
    get diagnostics changed = row_count; total_changed := total_changed + changed;

    update public.protected_user_deletion_audit
    set affected_rows = total_changed
    where id = created_audit_id;

    return query select created_audit_id, total_changed;
end;
$$;

revoke all on function public.admin_archive_protected_user(uuid) from public, anon, authenticated;
grant execute on function public.admin_archive_protected_user(uuid) to authenticated;

create or replace function public.admin_list_archived_protected_users()
returns table (
    archive_id uuid,
    device_id uuid,
    display_name text,
    archived_at timestamptz,
    can_restore boolean
)
language plpgsql
security definer
set search_path = public
as $$
declare
    current_admin record;
begin
    if auth.uid() is null then
        raise exception 'Authentication required';
    end if;

    select admin.id, admin.community_id
    into current_admin
    from public.community_admins admin
    where admin.auth_user_id = auth.uid()
      and admin.deleted_at is null
    order by admin.id
    limit 1;
    if current_admin.id is null then
        raise exception 'Active community admin required';
    end if;

    return query
    select audit.id,
           audit.device_id,
           audit.device_display_name,
           audit.created_at,
           audit.snapshot is not null and audit.snapshot_version = 1
    from public.protected_user_deletion_audit audit
    where audit.community_id = current_admin.community_id
      and audit.restored_at is null
    order by audit.created_at desc, audit.id;
end;
$$;

revoke all on function public.admin_list_archived_protected_users() from public, anon, authenticated;
grant execute on function public.admin_list_archived_protected_users() to authenticated;

create or replace function public.admin_create_archived_user_restore_code(
    target_archive_id uuid,
    ttl_minutes integer default 180
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
    current_admin record;
    target_archive public.protected_user_deletion_audit%rowtype;
    raw_code text;
    expiration timestamptz;
begin
    if auth.uid() is null then
        raise exception 'Authentication required';
    end if;

    select admin.id, admin.community_id
    into current_admin
    from public.community_admins admin
    where admin.auth_user_id = auth.uid()
      and admin.deleted_at is null
    order by admin.id
    limit 1;
    if current_admin.id is null then
        raise exception 'Active community admin required';
    end if;

    select audit.*
    into target_archive
    from public.protected_user_deletion_audit audit
    where audit.id = target_archive_id
      and audit.community_id = current_admin.community_id
      and audit.restored_at is null
    for update;
    if target_archive.id is null then
        raise exception 'Archived protected user not found';
    end if;
    if target_archive.snapshot is null or target_archive.snapshot_version <> 1 then
        raise exception 'Archived protected user requires manual review';
    end if;

    update public.activation_codes
    set deleted_at = now(), updated_at = now()
    where restore_archive_id = target_archive.id
      and used_at is null
      and deleted_at is null;

    raw_code := upper(substr(replace(gen_random_uuid()::text, '-', ''), 1, 6));
    expiration := now() + make_interval(mins => greatest(1, least(coalesce(ttl_minutes, 180), 180)));

    insert into public.activation_codes (
        account_id,
        code_hash,
        intended_app_role,
        intended_display_name,
        community_admin_id,
        expires_at,
        restore_archive_id
    ) values (
        target_archive.account_id,
        crypt(raw_code, gen_salt('bf')),
        'user',
        target_archive.device_display_name,
        current_admin.id,
        expiration,
        target_archive.id
    );

    return query select raw_code, expiration;
end;
$$;

revoke all on function public.admin_create_archived_user_restore_code(uuid, integer) from public, anon, authenticated;
grant execute on function public.admin_create_archived_user_restore_code(uuid, integer) to authenticated;

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
