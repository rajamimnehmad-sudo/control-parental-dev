create or replace function public.cancel_own_removal_authorization()
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

    select device.id as device_id, device.account_id
    into current_device
    from public.devices device
    where device.deleted_at is null
      and device.device_token_hash is not null
      and device.device_token_hash = extensions.crypt(request_token, device.device_token_hash)
    order by device.created_at desc
    limit 1;

    if current_device.account_id is null then
        raise exception 'Device not authorized';
    end if;

    update public.device_protection_controls control
    set authorization_scope = 'none',
        authorization_expires_at = null,
        command_revision = control.command_revision + 1,
        updated_at = now()
    where control.device_id = current_device.device_id
      and control.account_id = current_device.account_id
      and control.authorization_scope = 'removal';

    return found;
end;
$$;

revoke all on function public.cancel_own_removal_authorization() from public, anon, authenticated;
grant execute on function public.cancel_own_removal_authorization() to anon, authenticated;
