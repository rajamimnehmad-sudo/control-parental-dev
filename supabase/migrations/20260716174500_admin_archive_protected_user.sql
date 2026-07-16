create table if not exists public.protected_user_deletion_audit (
    id uuid primary key default gen_random_uuid(),
    community_id uuid not null references public.communities(id) on delete restrict,
    account_id uuid not null references public.accounts(id) on delete restrict,
    device_id uuid not null,
    device_display_name text not null,
    deleted_by_admin_id uuid not null references public.community_admins(id) on delete restrict,
    deleted_by_auth_user_id uuid not null references auth.users(id) on delete restrict,
    affected_rows integer not null check (affected_rows >= 1),
    created_at timestamptz not null default now()
);

alter table public.protected_user_deletion_audit enable row level security;
revoke all on table public.protected_user_deletion_audit from public, anon, authenticated;

create or replace function public.admin_archive_protected_user(target_device_id uuid)
returns table (audit_id uuid, affected_rows integer)
language plpgsql
security definer
set search_path = public
as $$
declare
    current_admin record;
    target_device record;
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

    insert into public.protected_user_deletion_audit (
        community_id, account_id, device_id, device_display_name,
        deleted_by_admin_id, deleted_by_auth_user_id, affected_rows
    ) values (
        current_admin.community_id, target_device.account_id, target_device.id, target_device.display_name,
        current_admin.id, auth.uid(), total_changed
    ) returning id into created_audit_id;

    return query select created_audit_id, total_changed;
end;
$$;

revoke all on function public.admin_archive_protected_user(uuid) from public;
grant execute on function public.admin_archive_protected_user(uuid) to authenticated;
