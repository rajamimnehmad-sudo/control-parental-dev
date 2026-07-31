alter table public.community_admins
    add column if not exists contact_email text;

create or replace function public.get_own_admin_contact(
    p_device_id uuid
)
returns table (
    contact_email text,
    phone_e164 text
)
language plpgsql
security definer
set search_path = ''
as $$
begin
    if not public.device_token_matches_device(p_device_id) then
        raise exception 'Device not authorized';
    end if;
    return query
    select admin.contact_email, admin.phone_e164
    from public.devices device
    join public.community_admins admin on admin.id = device.community_admin_id
    where device.id = p_device_id
      and device.app_role = 'admin'
      and device.deleted_at is null
      and admin.deleted_at is null;
end;
$$;

create or replace function public.update_own_admin_contact_v2(
    p_device_id uuid,
    p_contact_email text,
    p_phone_e164 text
)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
    target_admin_id uuid;
    normalized_email text;
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

    normalized_email := nullif(lower(trim(p_contact_email)), '');
    if normalized_email is not null and normalized_email !~ '^[^@[:space:]]+@[^@[:space:]]+[.][^@[:space:]]+$' then
        raise exception 'Email de contacto invalido';
    end if;
    normalized_phone := nullif(regexp_replace(p_phone_e164, '[^0-9+]', '', 'g'), '');
    if normalized_phone is not null and normalized_phone !~ '^\+[1-9][0-9]{7,14}$' then
        raise exception 'Phone must use international format';
    end if;

    update public.community_admins
    set contact_email = normalized_email,
        phone_e164 = normalized_phone,
        updated_at = now()
    where id = target_admin_id and deleted_at is null;
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
    last_rating_at timestamptz;
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
    select updated_at
    into last_rating_at
    from public.app_ratings
    where device_id = p_device_id;
    if last_rating_at is not null and last_rating_at > now() - interval '7 days' then
        raise exception 'Rating can be submitted once every 7 days';
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

revoke all on function public.get_own_admin_contact(uuid) from public;
revoke all on function public.update_own_admin_contact_v2(uuid, text, text) from public;
revoke all on function public.submit_own_app_rating(uuid, integer, text, integer) from public;
grant execute on function public.get_own_admin_contact(uuid) to anon, authenticated;
grant execute on function public.update_own_admin_contact_v2(uuid, text, text) to anon, authenticated;
grant execute on function public.submit_own_app_rating(uuid, integer, text, integer) to anon, authenticated;
