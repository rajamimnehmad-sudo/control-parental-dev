alter table public.devices
    add column if not exists contact_email text,
    add column if not exists contact_phone_e164 text;

create or replace function public.get_own_app_rating_status(
    p_device_id uuid
)
returns table (
    last_submitted_at timestamptz,
    next_available_at timestamptz
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
    select rating.updated_at,
           case when rating.updated_at is null then null else rating.updated_at + interval '7 days' end
    from public.devices device
    left join public.app_ratings rating on rating.device_id = device.id
    where device.id = p_device_id
      and device.deleted_at is null;
end;
$$;

create or replace function public.get_own_user_contact(
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
    select device.contact_email, device.contact_phone_e164
    from public.devices device
    where device.id = p_device_id
      and device.app_role = 'user'
      and device.deleted_at is null;
end;
$$;

create or replace function public.update_own_user_contact(
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
    normalized_email text;
    normalized_phone text;
begin
    if not public.device_token_matches_device(p_device_id) then
        raise exception 'Device not authorized';
    end if;
    normalized_email := nullif(lower(trim(p_contact_email)), '');
    if normalized_email is not null and normalized_email !~ '^[^@[:space:]]+@[^@[:space:]]+[.][^@[:space:]]+$' then
        raise exception 'Email de contacto invalido';
    end if;
    normalized_phone := nullif(regexp_replace(p_phone_e164, '[^0-9+]', '', 'g'), '');
    if normalized_phone is not null and normalized_phone !~ '^\+[1-9][0-9]{7,14}$' then
        raise exception 'Phone must use international format';
    end if;
    update public.devices
    set contact_email = normalized_email,
        contact_phone_e164 = normalized_phone,
        updated_at = now()
    where id = p_device_id
      and app_role = 'user'
      and deleted_at is null;
end;
$$;

revoke all on function public.get_own_app_rating_status(uuid) from public;
revoke all on function public.get_own_user_contact(uuid) from public;
revoke all on function public.update_own_user_contact(uuid, text, text) from public;
grant execute on function public.get_own_app_rating_status(uuid) to anon, authenticated;
grant execute on function public.get_own_user_contact(uuid) to anon, authenticated;
grant execute on function public.update_own_user_contact(uuid, text, text) to anon, authenticated;
