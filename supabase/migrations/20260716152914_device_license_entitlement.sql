create or replace function public.get_device_license_entitlement(p_device_id uuid)
returns jsonb
language plpgsql
security definer
stable
set search_path = public
as $$
declare
    entitlement jsonb;
begin
    if not public.device_token_matches_device(p_device_id) then
        raise exception 'Invalid device authorization';
    end if;

    select jsonb_build_object(
        'status', public.effective_community_license_status(
            licenses.status,
            licenses.starts_at,
            licenses.expires_at
        ),
        'starts_at', licenses.starts_at,
        'expires_at', licenses.expires_at,
        'evaluated_at', now()
    )
    into entitlement
    from public.devices
    join public.accounts
      on accounts.id = devices.account_id
     and accounts.deleted_at is null
    left join public.community_licenses licenses
      on licenses.community_id = accounts.community_id
     and licenses.deleted_at is null
    where devices.id = p_device_id
      and devices.deleted_at is null;

    if entitlement is null then
        raise exception 'Device license not found';
    end if;

    return entitlement;
end;
$$;

revoke all on function public.get_device_license_entitlement(uuid) from public;
grant execute on function public.get_device_license_entitlement(uuid) to anon, authenticated;
