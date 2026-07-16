create or replace function public.register_device_push_token(p_fcm_token text)
returns void
language plpgsql
security definer
set search_path = public, extensions
as $$
declare
    request_token text;
    matched_device record;
    normalized_fcm_token text;
begin
    request_token := public.request_device_token();
    normalized_fcm_token := trim(p_fcm_token);
    if request_token is null then
        raise exception 'Device token not authorized';
    end if;
    if normalized_fcm_token = '' or length(normalized_fcm_token) < 20 or length(normalized_fcm_token) > 4096 then
        raise exception 'Invalid FCM token';
    end if;

    select device.id, device.account_id, device.app_role
    into matched_device
    from public.devices device
    where device.deleted_at is null
      and device.app_role in ('admin', 'user')
      and device.device_token_hash is not null
      and device.device_token_hash = crypt(request_token, device.device_token_hash)
    order by device.created_at desc
    limit 1;

    if matched_device.id is null then
        raise exception 'Device not authorized';
    end if;

    insert into public.device_push_tokens (
        id, account_id, device_id, app_role, platform, fcm_token, updated_at, deleted_at
    ) values (
        matched_device.id, matched_device.account_id, matched_device.id,
        matched_device.app_role, 'android', normalized_fcm_token, now(), null
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

revoke all on function public.register_device_push_token(text) from public;
grant execute on function public.register_device_push_token(text) to anon, authenticated;
