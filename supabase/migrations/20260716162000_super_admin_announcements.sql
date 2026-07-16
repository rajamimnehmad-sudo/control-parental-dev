create table if not exists public.super_admin_announcements (
    id uuid primary key default gen_random_uuid(),
    community_id uuid not null references public.communities(id) on delete restrict,
    target_role text not null check (target_role in ('admin', 'user', 'all')),
    title text not null check (char_length(trim(title)) between 2 and 80),
    body text not null check (char_length(trim(body)) between 2 and 500),
    created_by uuid not null references auth.users(id) on delete restrict,
    created_at timestamptz not null default now(),
    expires_at timestamptz,
    constraint super_admin_announcements_expiry_check
        check (expires_at is null or expires_at > created_at)
);

alter table public.super_admin_announcements enable row level security;

revoke all on table public.super_admin_announcements from public, anon, authenticated;

create or replace function public.super_admin_create_announcement(
    target_community_id uuid,
    target_app_role text,
    announcement_title text,
    announcement_body text,
    announcement_expires_at timestamptz default null
)
returns table (announcement_id uuid)
language plpgsql
security definer
set search_path = public
as $$
declare
    created_id uuid;
begin
    if not public.is_current_user_super_admin() then
        raise exception 'Super Admin required';
    end if;
    if target_app_role not in ('admin', 'user', 'all') then
        raise exception 'Invalid target role';
    end if;
    if not exists (select 1 from public.communities where id = target_community_id) then
        raise exception 'Community not found';
    end if;
    if announcement_expires_at is not null and announcement_expires_at <= now() then
        raise exception 'Expiration must be in the future';
    end if;

    insert into public.super_admin_announcements (
        community_id,
        target_role,
        title,
        body,
        created_by,
        expires_at
    ) values (
        target_community_id,
        target_app_role,
        trim(announcement_title),
        trim(announcement_body),
        auth.uid(),
        announcement_expires_at
    ) returning id into created_id;

    return query select created_id;
end;
$$;

create or replace function public.super_admin_list_announcements(max_rows integer default 100)
returns table (
    announcement_id uuid,
    community_id uuid,
    community_name text,
    target_role text,
    title text,
    body text,
    created_at timestamptz,
    expires_at timestamptz
)
language plpgsql
security definer
set search_path = public
as $$
begin
    if not public.is_current_user_super_admin() then
        raise exception 'Super Admin required';
    end if;
    return query
    select
        announcement.id,
        announcement.community_id,
        community.name,
        announcement.target_role,
        announcement.title,
        announcement.body,
        announcement.created_at,
        announcement.expires_at
    from public.super_admin_announcements announcement
    join public.communities community on community.id = announcement.community_id
    order by announcement.created_at desc
    limit greatest(1, least(coalesce(max_rows, 100), 200));
end;
$$;

create or replace function public.super_admin_get_announcement_for_delivery(target_announcement_id uuid)
returns table (
    announcement_id uuid,
    community_id uuid,
    target_role text,
    title text,
    body text
)
language plpgsql
security definer
set search_path = public
as $$
begin
    if not public.is_current_user_super_admin() then
        raise exception 'Super Admin required';
    end if;
    return query
    select announcement.id, announcement.community_id, announcement.target_role,
           announcement.title, announcement.body
    from public.super_admin_announcements announcement
    where announcement.id = target_announcement_id
      and (announcement.expires_at is null or announcement.expires_at > now());
end;
$$;

create or replace function public.list_device_announcements(max_rows integer default 50)
returns table (
    announcement_id uuid,
    title text,
    body text,
    created_at timestamptz,
    expires_at timestamptz
)
language plpgsql
security definer
set search_path = public, extensions
as $$
declare
    request_token text;
    current_device record;
begin
    request_token := public.request_device_token();
    if request_token is null then
        raise exception 'Device token not authorized';
    end if;

    select device.account_id, device.app_role, account.community_id
    into current_device
    from public.devices device
    join public.accounts account on account.id = device.account_id
    where device.deleted_at is null
      and device.device_token_hash is not null
      and device.device_token_hash = crypt(request_token, device.device_token_hash)
    order by device.created_at desc
    limit 1;

    if current_device.account_id is null then
        raise exception 'Device not authorized';
    end if;

    return query
    select announcement.id, announcement.title, announcement.body,
           announcement.created_at, announcement.expires_at
    from public.super_admin_announcements announcement
    where announcement.community_id = current_device.community_id
      and announcement.target_role in ('all', current_device.app_role)
      and (announcement.expires_at is null or announcement.expires_at > now())
    order by announcement.created_at desc
    limit greatest(1, least(coalesce(max_rows, 50), 100));
end;
$$;

revoke all on function public.super_admin_create_announcement(uuid, text, text, text, timestamptz) from public;
revoke all on function public.super_admin_list_announcements(integer) from public;
revoke all on function public.super_admin_get_announcement_for_delivery(uuid) from public;
revoke all on function public.list_device_announcements(integer) from public;

grant execute on function public.super_admin_create_announcement(uuid, text, text, text, timestamptz) to authenticated;
grant execute on function public.super_admin_list_announcements(integer) to authenticated;
grant execute on function public.super_admin_get_announcement_for_delivery(uuid) to authenticated;
grant execute on function public.list_device_announcements(integer) to anon, authenticated;
