create or replace function public.send_community_license_invalidation(target_community_id uuid)
returns bigint
language plpgsql
security definer
set search_path = ''
as $$
declare
    notified_devices bigint;
begin
    -- Broadcast is only an invalidation hint. It deliberately contains no
    -- license state; each device must fetch its authoritative entitlement
    -- through get_device_license_entitlement with its own device token.
    perform realtime.send(
        jsonb_build_object('device_id', devices.id),
        'license_changed',
        'policy:' || devices.id::text,
        false
    )
    from public.devices
    join public.accounts
      on accounts.id = devices.account_id
     and accounts.deleted_at is null
    where accounts.community_id = target_community_id
      and devices.deleted_at is null;

    get diagnostics notified_devices = row_count;
    return notified_devices;
end;
$$;

revoke all on function public.send_community_license_invalidation(uuid) from public, anon, authenticated;

create or replace function public.broadcast_community_license_invalidation()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
    target_community_id uuid;
begin
    if tg_op = 'UPDATE'
       and old.status is not distinct from new.status
       and old.starts_at is not distinct from new.starts_at
       and old.expires_at is not distinct from new.expires_at
       and old.deleted_at is not distinct from new.deleted_at
       and old.dag_entitled is not distinct from new.dag_entitled then
        return new;
    end if;

    target_community_id := case when tg_op = 'DELETE' then old.community_id else new.community_id end;
    perform public.send_community_license_invalidation(target_community_id);

    if tg_op = 'DELETE' then
        return old;
    end if;
    return new;
end;
$$;

revoke all on function public.broadcast_community_license_invalidation() from public, anon, authenticated;

drop trigger if exists trg_community_licenses_broadcast_invalidation on public.community_licenses;
create trigger trg_community_licenses_broadcast_invalidation
after insert or update or delete on public.community_licenses
for each row execute function public.broadcast_community_license_invalidation();

create or replace function public.process_due_community_license_transitions(
    reference_time timestamptz default now()
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    due_license record;
    started_count bigint := 0;
    expired_count bigint;
begin
    -- A scheduled license remains stored as active. Notify around its server-
    -- evaluated start so clients replace their previous scheduled snapshot.
    for due_license in
        select community_id
        from public.community_licenses
        where status = 'active'
          and deleted_at is null
          and starts_at > reference_time - interval '2 minutes'
          and starts_at <= reference_time
          and (expires_at is null or expires_at > reference_time)
    loop
        perform public.send_community_license_invalidation(due_license.community_id);
        started_count := started_count + 1;
    end loop;

    update public.community_licenses
    set status = 'expired'
    where status = 'active'
      and deleted_at is null
      and expires_at is not null
      and expires_at <= reference_time;

    get diagnostics expired_count = row_count;
    return jsonb_build_object(
        'started_licenses', started_count,
        'expired_licenses', expired_count
    );
end;
$$;

revoke all on function public.process_due_community_license_transitions(timestamptz)
from public, anon, authenticated;

-- Persist natural expiration and announce scheduled activation using server
-- time, so connected devices do not trust their wall clock for either edge.
select public.process_due_community_license_transitions();

create extension if not exists pg_cron with schema pg_catalog;

do $$
declare
    existing_job bigint;
begin
    for existing_job in
        select jobid
        from cron.job
        where jobname = 'license-transitions-every-minute'
    loop
        perform cron.unschedule(existing_job);
    end loop;

    perform cron.schedule(
        'license-transitions-every-minute',
        '* * * * *',
        'select public.process_due_community_license_transitions();'
    );
end;
$$;
