alter table public.protection_alert_events
add column if not exists archived_at timestamptz,
add column if not exists archived_by uuid references auth.users(id);

alter table public.super_admin_announcements
add column if not exists archived_at timestamptz,
add column if not exists archived_by uuid references auth.users(id);

create index if not exists idx_protection_alert_events_visible_created
on public.protection_alert_events(created_at desc)
where archived_at is null;

create index if not exists idx_super_admin_announcements_visible_created
on public.super_admin_announcements(created_at desc)
where archived_at is null;

create or replace function public.super_admin_archive_protection_alerts(target_device_id uuid)
returns integer
language plpgsql
security definer
set search_path = ''
as $$
declare affected integer;
begin
    perform public.require_super_admin();
    update public.protection_alert_events
    set archived_at = now(), archived_by = auth.uid()
    where device_id = target_device_id and archived_at is null;
    get diagnostics affected = row_count;
    return affected;
end;
$$;

create or replace function public.super_admin_archive_announcement(target_announcement_id uuid)
returns boolean
language plpgsql
security definer
set search_path = ''
as $$
begin
    perform public.require_super_admin();
    update public.super_admin_announcements
    set archived_at = now(), archived_by = auth.uid()
    where id = target_announcement_id and archived_at is null;
    return found;
end;
$$;

revoke all on function public.super_admin_archive_protection_alerts(uuid) from public, anon, authenticated;
revoke all on function public.super_admin_archive_announcement(uuid) from public, anon, authenticated;
grant execute on function public.super_admin_archive_protection_alerts(uuid) to authenticated;
grant execute on function public.super_admin_archive_announcement(uuid) to authenticated;

create or replace function public.super_admin_list_protection_alerts(max_rows integer default 100)
returns table (event_id uuid, community_id uuid, community_name text, device_id uuid, device_name text, alert_type text, title text, body text, created_at timestamptz)
language plpgsql security definer stable set search_path = public
as $$
begin
    perform public.require_super_admin();
    return query
    select events.id, accounts.community_id, communities.name, events.device_id,
           devices.display_name, events.alert_type, events.title, events.body, events.created_at
    from public.protection_alert_events events
    join public.accounts accounts on accounts.id = events.account_id and accounts.deleted_at is null
    join public.communities communities on communities.id = accounts.community_id and communities.deleted_at is null
    join public.devices devices on devices.id = events.device_id and devices.deleted_at is null
    where events.archived_at is null
    order by events.created_at desc
    limit greatest(1, least(coalesce(max_rows, 100), 500));
end;
$$;

create or replace function public.super_admin_list_announcements(max_rows integer default 100)
returns table (announcement_id uuid, community_id uuid, community_name text, target_role text, title text, body text, created_at timestamptz, expires_at timestamptz)
language plpgsql security definer set search_path = public
as $$
begin
    perform public.require_super_admin();
    return query
    select announcement.id, announcement.community_id, community.name, announcement.target_role,
           announcement.title, announcement.body, announcement.created_at, announcement.expires_at
    from public.super_admin_announcements announcement
    join public.communities community on community.id = announcement.community_id
    where announcement.archived_at is null
    order by announcement.created_at desc
    limit greatest(1, least(coalesce(max_rows, 100), 200));
end;
$$;

create or replace function public.super_admin_get_announcement_for_delivery(target_announcement_id uuid)
returns table (announcement_id uuid, community_id uuid, target_role text, title text, body text)
language plpgsql security definer set search_path = public
as $$
begin
    perform public.require_super_admin();
    return query select announcement.id, announcement.community_id, announcement.target_role, announcement.title, announcement.body
    from public.super_admin_announcements announcement
    where announcement.id = target_announcement_id and announcement.archived_at is null
      and (announcement.expires_at is null or announcement.expires_at > now());
end;
$$;

create or replace function public.list_device_announcements(max_rows integer default 50)
returns table (announcement_id uuid, title text, body text, created_at timestamptz, expires_at timestamptz)
language plpgsql security definer set search_path = public, extensions
as $$
declare request_token text; current_device record;
begin
    request_token := public.request_device_token();
    if request_token is null then raise exception 'Device token not authorized'; end if;
    select device.account_id, device.app_role, account.community_id into current_device
    from public.devices device join public.accounts account on account.id = device.account_id
    where device.deleted_at is null and device.device_token_hash is not null
      and device.device_token_hash = crypt(request_token, device.device_token_hash)
    order by device.created_at desc limit 1;
    if current_device.account_id is null then raise exception 'Device not authorized'; end if;
    return query select announcement.id, announcement.title, announcement.body, announcement.created_at, announcement.expires_at
    from public.super_admin_announcements announcement
    where announcement.community_id = current_device.community_id
      and announcement.target_role in ('all', current_device.app_role)
      and announcement.archived_at is null
      and (announcement.expires_at is null or announcement.expires_at > now())
    order by announcement.created_at desc limit greatest(1, least(coalesce(max_rows, 50), 100));
end;
$$;

revoke all on function public.super_admin_list_protection_alerts(integer) from public, anon, authenticated;
revoke all on function public.super_admin_list_announcements(integer) from public, anon, authenticated;
revoke all on function public.super_admin_get_announcement_for_delivery(uuid) from public, anon, authenticated;
revoke all on function public.list_device_announcements(integer) from public, anon, authenticated;
grant execute on function public.super_admin_list_protection_alerts(integer) to authenticated;
grant execute on function public.super_admin_list_announcements(integer) to authenticated;
grant execute on function public.super_admin_get_announcement_for_delivery(uuid) to authenticated;
grant execute on function public.list_device_announcements(integer) to anon, authenticated;
