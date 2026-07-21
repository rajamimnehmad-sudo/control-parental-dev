create or replace function public.generate_device_offline_alerts(
    reference_time timestamptz default now()
)
returns integer
language plpgsql
security definer
set search_path = ''
as $$
declare
    inserted_count integer;
begin
    insert into public.protection_alert_events (
        account_id,
        device_id,
        alert_type,
        title,
        body
    )
    select
        device.account_id,
        device.id,
        'device_offline',
        'Teléfono sin comunicación',
        device.display_name || ' no se comunica desde ' ||
            to_char(device.last_seen_at at time zone 'America/Argentina/Buenos_Aires', 'DD/MM/YYYY HH24:MI') || '.'
    from public.devices device
    join public.accounts account
      on account.id = device.account_id
     and account.deleted_at is null
    where device.deleted_at is null
      and device.app_role = 'user'
      and device.last_seen_at is not null
      and device.last_seen_at <= reference_time - interval '100 hours'
      and not exists (
          select 1
          from public.protection_alert_events event
          where event.device_id = device.id
            and event.alert_type = 'device_offline'
            and event.created_at >= device.last_seen_at
      );

    get diagnostics inserted_count = row_count;
    return inserted_count;
end;
$$;

revoke all on function public.generate_device_offline_alerts(timestamptz)
from public, anon, authenticated;
