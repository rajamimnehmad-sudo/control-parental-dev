create or replace function public.set_updated_at()
returns trigger
language plpgsql
set search_path = public
as $$
begin
    new.updated_at = now();
    return new;
end;
$$;

create or replace function public.request_device_token()
returns text
language plpgsql
stable
set search_path = public
as $$
declare
    headers json;
begin
    begin
        headers := current_setting('request.headers', true)::json;
    exception when others then
        return null;
    end;

    return nullif(headers ->> 'x-device-token', '');
end;
$$;

revoke execute on function public.is_super_admin() from public, anon;
grant execute on function public.is_super_admin() to authenticated;

revoke execute on function public.require_super_admin() from public, anon, authenticated;
revoke execute on function public.license_allows_activation(uuid, text) from public, anon, authenticated;

revoke execute on function public.super_admin_list_communities() from public, anon;
grant execute on function public.super_admin_list_communities() to authenticated;

revoke execute on function public.super_admin_create_community(
    text,
    text,
    text,
    timestamptz,
    integer,
    integer,
    integer,
    text
) from public, anon;
grant execute on function public.super_admin_create_community(
    text,
    text,
    text,
    timestamptz,
    integer,
    integer,
    integer,
    text
) to authenticated;

revoke execute on function public.super_admin_get_community_detail(uuid) from public, anon;
grant execute on function public.super_admin_get_community_detail(uuid) to authenticated;

revoke execute on function public.super_admin_upsert_license(
    uuid,
    text,
    text,
    timestamptz,
    timestamptz,
    integer,
    integer,
    integer,
    text
) from public, anon;
grant execute on function public.super_admin_upsert_license(
    uuid,
    text,
    text,
    timestamptz,
    timestamptz,
    integer,
    integer,
    integer,
    text
) to authenticated;

revoke execute on function public.super_admin_list_community_admins(uuid) from public, anon;
grant execute on function public.super_admin_list_community_admins(uuid) to authenticated;

revoke execute on function public.super_admin_create_admin_pairing_code(
    uuid,
    text,
    text,
    integer
) from public, anon;
grant execute on function public.super_admin_create_admin_pairing_code(
    uuid,
    text,
    text,
    integer
) to authenticated;

revoke execute on function public.super_admin_list_community_devices(uuid) from public, anon;
grant execute on function public.super_admin_list_community_devices(uuid) to authenticated;

revoke execute on function public.create_device_pairing_code(integer) from public;
grant execute on function public.create_device_pairing_code(integer) to anon, authenticated;

revoke execute on function public.pair_device_with_code(text, text, integer, text) from public;
grant execute on function public.pair_device_with_code(text, text, integer, text) to anon, authenticated;
