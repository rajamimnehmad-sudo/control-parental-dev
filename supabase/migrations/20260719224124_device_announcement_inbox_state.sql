create table if not exists public.device_announcement_receipts (
    device_id uuid not null references public.devices(id) on delete cascade,
    announcement_id uuid not null references public.super_admin_announcements(id) on delete restrict,
    read_at timestamptz,
    dismissed_at timestamptz,
    updated_at timestamptz not null default now(),
    primary key (device_id, announcement_id)
);

create index if not exists idx_device_announcement_receipts_device_visible
on public.device_announcement_receipts(device_id, updated_at desc)
where dismissed_at is null;

create index if not exists idx_device_announcement_receipts_announcement
on public.device_announcement_receipts(announcement_id);

alter table public.device_announcement_receipts enable row level security;

revoke all on table public.device_announcement_receipts from public, anon, authenticated;

create or replace function public.list_device_announcements_v2(max_rows integer default 50)
returns table (
    announcement_id uuid,
    title text,
    body text,
    created_at timestamptz,
    expires_at timestamptz,
    is_read boolean
)
language plpgsql
security definer
stable
set search_path = ''
as $$
declare
    request_token text;
    current_device record;
begin
    request_token := public.request_device_token();
    if request_token is null then
        raise exception 'Device token not authorized';
    end if;

    select device.id as device_id, device.account_id, device.app_role, account.community_id
    into current_device
    from public.devices device
    join public.accounts account on account.id = device.account_id
    where device.deleted_at is null
      and device.device_token_hash is not null
      and device.device_token_hash = extensions.crypt(request_token, device.device_token_hash)
    order by device.created_at desc
    limit 1;

    if current_device.account_id is null then
        raise exception 'Device not authorized';
    end if;

    return query
    select
        announcement.id,
        announcement.title,
        announcement.body,
        announcement.created_at,
        announcement.expires_at,
        receipt.read_at is not null
    from public.super_admin_announcements announcement
    left join public.device_announcement_receipts receipt
      on receipt.device_id = current_device.device_id
     and receipt.announcement_id = announcement.id
    where announcement.community_id = current_device.community_id
      and announcement.target_role in ('all', current_device.app_role)
      and announcement.archived_at is null
      and (announcement.expires_at is null or announcement.expires_at > now())
      and receipt.dismissed_at is null
    order by announcement.created_at desc
    limit greatest(1, least(coalesce(max_rows, 50), 100));
end;
$$;

create or replace function public.mark_device_announcements_read()
returns integer
language plpgsql
security definer
set search_path = ''
as $$
declare
    request_token text;
    current_device record;
    affected integer;
begin
    request_token := public.request_device_token();
    if request_token is null then
        raise exception 'Device token not authorized';
    end if;

    select device.id as device_id, device.account_id, device.app_role, account.community_id
    into current_device
    from public.devices device
    join public.accounts account on account.id = device.account_id
    where device.deleted_at is null
      and device.device_token_hash is not null
      and device.device_token_hash = extensions.crypt(request_token, device.device_token_hash)
    order by device.created_at desc
    limit 1;

    if current_device.account_id is null then
        raise exception 'Device not authorized';
    end if;

    insert into public.device_announcement_receipts (
        device_id,
        announcement_id,
        read_at,
        updated_at
    )
    select current_device.device_id, announcement.id, now(), now()
    from public.super_admin_announcements announcement
    where announcement.community_id = current_device.community_id
      and announcement.target_role in ('all', current_device.app_role)
      and announcement.archived_at is null
      and (announcement.expires_at is null or announcement.expires_at > now())
    on conflict (device_id, announcement_id) do update
    set read_at = coalesce(public.device_announcement_receipts.read_at, excluded.read_at),
        updated_at = excluded.updated_at;

    get diagnostics affected = row_count;
    return affected;
end;
$$;

create or replace function public.dismiss_device_announcement(target_announcement_id uuid)
returns boolean
language plpgsql
security definer
set search_path = ''
as $$
declare
    request_token text;
    current_device record;
begin
    request_token := public.request_device_token();
    if request_token is null then
        raise exception 'Device token not authorized';
    end if;

    select device.id as device_id, device.account_id, device.app_role, account.community_id
    into current_device
    from public.devices device
    join public.accounts account on account.id = device.account_id
    where device.deleted_at is null
      and device.device_token_hash is not null
      and device.device_token_hash = extensions.crypt(request_token, device.device_token_hash)
    order by device.created_at desc
    limit 1;

    if current_device.account_id is null then
        raise exception 'Device not authorized';
    end if;

    if not exists (
        select 1
        from public.super_admin_announcements announcement
        where announcement.id = target_announcement_id
          and announcement.community_id = current_device.community_id
          and announcement.target_role in ('all', current_device.app_role)
          and announcement.archived_at is null
          and (announcement.expires_at is null or announcement.expires_at > now())
    ) then
        return false;
    end if;

    insert into public.device_announcement_receipts (
        device_id,
        announcement_id,
        read_at,
        dismissed_at,
        updated_at
    ) values (
        current_device.device_id,
        target_announcement_id,
        now(),
        now(),
        now()
    )
    on conflict (device_id, announcement_id) do update
    set read_at = coalesce(public.device_announcement_receipts.read_at, excluded.read_at),
        dismissed_at = excluded.dismissed_at,
        updated_at = excluded.updated_at;

    return true;
end;
$$;

create or replace function public.restore_device_announcement(target_announcement_id uuid)
returns boolean
language plpgsql
security definer
set search_path = ''
as $$
declare
    request_token text;
    current_device record;
begin
    request_token := public.request_device_token();
    if request_token is null then
        raise exception 'Device token not authorized';
    end if;

    select device.id as device_id, device.account_id, device.app_role, account.community_id
    into current_device
    from public.devices device
    join public.accounts account on account.id = device.account_id
    where device.deleted_at is null
      and device.device_token_hash is not null
      and device.device_token_hash = extensions.crypt(request_token, device.device_token_hash)
    order by device.created_at desc
    limit 1;

    if current_device.account_id is null then
        raise exception 'Device not authorized';
    end if;

    update public.device_announcement_receipts receipt
    set dismissed_at = null,
        updated_at = now()
    where receipt.device_id = current_device.device_id
      and receipt.announcement_id = target_announcement_id
      and receipt.dismissed_at is not null
      and exists (
          select 1
          from public.super_admin_announcements announcement
          where announcement.id = receipt.announcement_id
            and announcement.community_id = current_device.community_id
            and announcement.target_role in ('all', current_device.app_role)
            and announcement.archived_at is null
            and (announcement.expires_at is null or announcement.expires_at > now())
      );

    return found;
end;
$$;

revoke all on function public.list_device_announcements_v2(integer) from public, anon, authenticated;
revoke all on function public.mark_device_announcements_read() from public, anon, authenticated;
revoke all on function public.dismiss_device_announcement(uuid) from public, anon, authenticated;
revoke all on function public.restore_device_announcement(uuid) from public, anon, authenticated;

grant execute on function public.list_device_announcements_v2(integer) to anon, authenticated;
grant execute on function public.mark_device_announcements_read() to anon, authenticated;
grant execute on function public.dismiss_device_announcement(uuid) to anon, authenticated;
grant execute on function public.restore_device_announcement(uuid) to anon, authenticated;
