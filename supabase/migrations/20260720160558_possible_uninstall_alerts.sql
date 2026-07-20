alter table public.protection_alert_events
drop constraint if exists protection_alert_events_type_check;

alter table public.protection_alert_events
add constraint protection_alert_events_type_check
check (alert_type in (
    'web_disabled',
    'apps_disabled',
    'admin_disabled',
    'incomplete',
    'tamper_attempt',
    'maintenance_requested',
    'device_offline',
    'possible_uninstall'
));

create or replace function public.generate_possible_uninstall_alerts(
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
        'possible_uninstall',
        'ALERTA MÁXIMA',
        device.display_name ||
            ' dejó de comunicarse después de quedar sin protección contra desinstalación. ' ||
            'Verificá si App Usuario sigue instalada y restablecé la protección.'
    from public.devices device
    join public.accounts account
      on account.id = device.account_id
     and account.deleted_at is null
    where device.deleted_at is null
      and device.app_role = 'user'
      and device.device_admin_state = 'Disabled'
      and device.last_seen_at is not null
      and device.last_seen_at <= reference_time - interval '30 minutes'
      and not exists (
          select 1
          from public.protection_alert_events event
          where event.device_id = device.id
            and event.alert_type = 'possible_uninstall'
            and event.created_at >= device.last_seen_at
      );

    get diagnostics inserted_count = row_count;
    return inserted_count;
end;
$$;

revoke all on function public.generate_possible_uninstall_alerts(timestamptz)
from public, anon, authenticated;

create extension if not exists pg_cron with schema pg_catalog;

do $$
declare
    existing_job bigint;
begin
    for existing_job in
        select jobid
        from cron.job
        where jobname = 'possible-uninstall-alerts-15m'
    loop
        perform cron.unschedule(existing_job);
    end loop;

    perform cron.schedule(
        'possible-uninstall-alerts-15m',
        '2,17,32,47 * * * *',
        'select public.generate_possible_uninstall_alerts();'
    );
end;
$$;
