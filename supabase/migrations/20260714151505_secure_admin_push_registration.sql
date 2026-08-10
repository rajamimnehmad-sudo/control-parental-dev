drop policy if exists "device_push_tokens_admin_device_token_upsert"
on public.device_push_tokens;

revoke insert, update on table public.device_push_tokens from anon, authenticated;

create or replace function public.register_admin_push_token(
    p_fcm_token text
)
returns void
language plpgsql
security definer
set search_path = public, extensions
as $$
declare
    request_token text;
    admin_device record;
    normalized_fcm_token text;
begin
    request_token := public.request_device_token();
    normalized_fcm_token := trim(p_fcm_token);

    if request_token is null then
        raise exception 'Device token not authorized';
    end if;

    if normalized_fcm_token = ''
       or length(normalized_fcm_token) < 20
       or length(normalized_fcm_token) > 4096 then
        raise exception 'Invalid FCM token';
    end if;

    select devices.id, devices.account_id
    into admin_device
    from public.devices
    where devices.deleted_at is null
      and devices.app_role = 'admin'
      and devices.device_token_hash is not null
      and devices.device_token_hash = crypt(request_token, devices.device_token_hash)
    order by devices.created_at desc
    limit 1;

    if admin_device.id is null then
        raise exception 'Admin device not authorized';
    end if;

    insert into public.device_push_tokens (
        id,
        account_id,
        device_id,
        app_role,
        platform,
        fcm_token,
        updated_at,
        deleted_at
    )
    values (
        admin_device.id,
        admin_device.account_id,
        admin_device.id,
        'admin',
        'android',
        normalized_fcm_token,
        now(),
        null
    )
    on conflict (id) do update
    set account_id = excluded.account_id,
        device_id = excluded.device_id,
        app_role = excluded.app_role,
        platform = excluded.platform,
        fcm_token = excluded.fcm_token,
        updated_at = excluded.updated_at,
        deleted_at = null;
end;
$$;

revoke all on function public.register_admin_push_token(text) from public;
grant execute on function public.register_admin_push_token(text) to anon, authenticated;
