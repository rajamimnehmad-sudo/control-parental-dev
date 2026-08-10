alter table public.devices
    add column if not exists manufacturer text,
    add column if not exists model text,
    add column if not exists android_version text,
    add column if not exists android_sdk integer;

alter table public.community_admins
    add column if not exists phone_e164 text;

create table if not exists public.app_ratings (
    id uuid primary key default gen_random_uuid(),
    account_id uuid not null references public.accounts(id) on delete cascade,
    device_id uuid not null references public.devices(id) on delete cascade,
    community_id uuid references public.communities(id) on delete set null,
    app_role text not null check (app_role in ('user', 'admin')),
    stars smallint not null check (stars between 1 and 5),
    comment text,
    app_version_code integer not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint app_ratings_device_unique unique (device_id),
    constraint app_ratings_comment_length check (
        comment is null or char_length(comment) between 1 and 1000
    )
);

drop trigger if exists trg_app_ratings_updated_at on public.app_ratings;
create trigger trg_app_ratings_updated_at
before update on public.app_ratings
for each row execute function public.set_updated_at();

create index if not exists idx_app_ratings_role_updated
on public.app_ratings(app_role, updated_at desc);

alter table public.app_ratings enable row level security;
revoke all on table public.app_ratings from public, anon, authenticated;

create or replace function public.report_own_device_metadata(
    p_device_id uuid,
    p_manufacturer text,
    p_model text,
    p_android_version text,
    p_android_sdk integer
)
returns void
language plpgsql
security definer
set search_path = ''
as $$
begin
    if not public.device_token_matches_device(p_device_id) then
        raise exception 'Device not authorized';
    end if;
    update public.devices
    set manufacturer = nullif(trim(p_manufacturer), ''),
        model = nullif(trim(p_model), ''),
        android_version = nullif(trim(p_android_version), ''),
        android_sdk = p_android_sdk,
        updated_at = now()
    where id = p_device_id and deleted_at is null;
end;
$$;

create or replace function public.submit_own_app_rating(
    p_device_id uuid,
    p_stars integer,
    p_comment text,
    p_app_version_code integer
)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
    current_device record;
    normalized_comment text;
begin
    if not public.device_token_matches_device(p_device_id) then
        raise exception 'Device not authorized';
    end if;
    if p_stars < 1 or p_stars > 5 then
        raise exception 'Rating must be between 1 and 5';
    end if;
    normalized_comment := nullif(trim(p_comment), '');
    if normalized_comment is not null and char_length(normalized_comment) > 1000 then
        raise exception 'Comment is too long';
    end if;
    select device.account_id, device.app_role, account.community_id
    into current_device
    from public.devices device
    join public.accounts account on account.id = device.account_id
    where device.id = p_device_id and device.deleted_at is null;

    insert into public.app_ratings (
        account_id, device_id, community_id, app_role, stars, comment, app_version_code
    ) values (
        current_device.account_id, p_device_id, current_device.community_id,
        current_device.app_role, p_stars, normalized_comment, p_app_version_code
    )
    on conflict (device_id) do update
    set stars = excluded.stars,
        comment = excluded.comment,
        app_version_code = excluded.app_version_code,
        app_role = excluded.app_role,
        community_id = excluded.community_id,
        updated_at = now();
end;
$$;

create or replace function public.update_own_admin_contact(
    p_device_id uuid,
    p_phone_e164 text
)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
    target_admin_id uuid;
    normalized_phone text;
begin
    if not public.device_token_matches_device(p_device_id) then
        raise exception 'Device not authorized';
    end if;
    select community_admin_id into target_admin_id
    from public.devices
    where id = p_device_id and app_role = 'admin' and deleted_at is null;
    if target_admin_id is null then
        raise exception 'Admin device not found';
    end if;
    normalized_phone := nullif(regexp_replace(p_phone_e164, '[^0-9+]', '', 'g'), '');
    if normalized_phone is not null and normalized_phone !~ '^\+[1-9][0-9]{7,14}$' then
        raise exception 'Phone must use international format';
    end if;
    update public.community_admins
    set phone_e164 = normalized_phone, updated_at = now()
    where id = target_admin_id and deleted_at is null;
end;
$$;

create or replace function public.super_admin_list_app_ratings(max_rows integer default 500)
returns table (
    rating_id uuid,
    community_id uuid,
    community_name text,
    device_id uuid,
    device_name text,
    app_role text,
    stars smallint,
    comment text,
    app_version_code integer,
    manufacturer text,
    model text,
    android_version text,
    updated_at timestamptz
)
language plpgsql
security definer
set search_path = ''
as $$
begin
    if not public.is_super_admin() then
        raise exception 'Not authorized';
    end if;
    return query
    select rating.id, rating.community_id, community.name, rating.device_id,
           device.display_name, rating.app_role, rating.stars, rating.comment,
           rating.app_version_code, device.manufacturer, device.model,
           device.android_version, rating.updated_at
    from public.app_ratings rating
    join public.devices device on device.id = rating.device_id
    left join public.communities community on community.id = rating.community_id
    where device.deleted_at is null
    order by rating.updated_at desc
    limit greatest(1, least(coalesce(max_rows, 500), 1000));
end;
$$;

create or replace function public.super_admin_list_device_metadata(
    target_community_id uuid
)
returns table (
    device_id uuid,
    manufacturer text,
    model text,
    android_version text,
    android_sdk integer
)
language plpgsql
security definer
set search_path = ''
as $$
begin
    if not public.is_super_admin() then
        raise exception 'Not authorized';
    end if;
    return query
    select device.id, device.manufacturer, device.model, device.android_version, device.android_sdk
    from public.devices device
    join public.accounts account on account.id = device.account_id
    where account.community_id = target_community_id and device.deleted_at is null;
end;
$$;

revoke all on function public.report_own_device_metadata(uuid, text, text, text, integer) from public;
revoke all on function public.submit_own_app_rating(uuid, integer, text, integer) from public;
revoke all on function public.update_own_admin_contact(uuid, text) from public;
revoke all on function public.super_admin_list_app_ratings(integer) from public;
revoke all on function public.super_admin_list_device_metadata(uuid) from public;
grant execute on function public.report_own_device_metadata(uuid, text, text, text, integer) to anon, authenticated;
grant execute on function public.submit_own_app_rating(uuid, integer, text, integer) to anon, authenticated;
grant execute on function public.update_own_admin_contact(uuid, text) to anon, authenticated;
grant execute on function public.super_admin_list_app_ratings(integer) to authenticated;
grant execute on function public.super_admin_list_device_metadata(uuid) to authenticated;
