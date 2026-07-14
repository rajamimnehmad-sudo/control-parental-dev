alter table public.devices
add column if not exists device_admin_state text not null default 'Unknown';

alter table public.devices
drop constraint if exists devices_device_admin_state_check;

alter table public.devices
add constraint devices_device_admin_state_check
check (device_admin_state in ('Enabled', 'Disabled', 'Warning', 'Unknown'));

grant update (device_admin_state) on public.devices to anon;

create table if not exists public.device_protection_controls (
    device_id uuid primary key references public.devices(id) on delete cascade,
    account_id uuid not null references public.accounts(id) on delete cascade,
    armed boolean not null default false,
    authorization_scope text not null default 'none',
    authorization_expires_at timestamptz,
    command_revision bigint not null default 0,
    applied_revision bigint not null default 0,
    applied_at timestamptz,
    recovery_salt text,
    recovery_verifier text,
    recovery_revision bigint not null default 0,
    recovery_consumed_revision bigint not null default 0,
    updated_at timestamptz not null default now(),
    constraint device_protection_controls_scope_check
        check (authorization_scope in ('none', 'settings', 'removal')),
    constraint device_protection_controls_authorization_check
        check (
            (authorization_scope = 'none' and authorization_expires_at is null)
            or
            (authorization_scope <> 'none' and authorization_expires_at is not null)
        ),
    constraint device_protection_controls_revision_check
        check (
            command_revision >= 0
            and applied_revision >= 0
            and recovery_revision >= 0
            and recovery_consumed_revision >= 0
            and applied_revision <= command_revision
            and recovery_consumed_revision <= recovery_revision
        )
);

create index if not exists device_protection_controls_account_id_idx
on public.device_protection_controls(account_id);

insert into public.device_protection_controls (device_id, account_id)
select id, account_id
from public.devices
where deleted_at is null
  and app_role = 'user'
on conflict (device_id) do nothing;

create or replace function public.ensure_device_protection_control()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
    if new.deleted_at is null and new.app_role = 'user' then
        insert into public.device_protection_controls (device_id, account_id)
        values (new.id, new.account_id)
        on conflict (device_id) do update
        set account_id = excluded.account_id,
            updated_at = now();
    end if;

    return new;
end;
$$;

revoke all on function public.ensure_device_protection_control() from public, anon, authenticated;

drop trigger if exists ensure_device_protection_control_trigger on public.devices;
create trigger ensure_device_protection_control_trigger
after insert or update of account_id, app_role, deleted_at on public.devices
for each row
execute function public.ensure_device_protection_control();

alter table public.device_protection_controls enable row level security;

drop policy if exists "device_protection_controls_select" on public.device_protection_controls;
create policy "device_protection_controls_select" on public.device_protection_controls
for select
to anon, authenticated
using (
    public.device_token_matches_device(device_id)
    or public.admin_device_token_matches(account_id)
);

drop policy if exists "device_protection_controls_admin_insert" on public.device_protection_controls;
create policy "device_protection_controls_admin_insert" on public.device_protection_controls
for insert
to anon, authenticated
with check (
    public.admin_device_token_matches(account_id)
    and exists (
        select 1
        from public.devices
        where devices.id = device_id
          and devices.account_id = device_protection_controls.account_id
          and devices.app_role = 'user'
          and devices.deleted_at is null
    )
);

drop policy if exists "device_protection_controls_admin_update" on public.device_protection_controls;
create policy "device_protection_controls_admin_update" on public.device_protection_controls
for update
to anon, authenticated
using (public.admin_device_token_matches(account_id))
with check (public.admin_device_token_matches(account_id));

revoke all on table public.device_protection_controls from public;
grant select, insert, update on table public.device_protection_controls to anon, authenticated;

create or replace function public.ack_device_protection_control(
    p_device_id uuid,
    p_command_revision bigint,
    p_recovery_consumed_revision bigint default null
)
returns void
language plpgsql
security definer
set search_path = public
as $$
begin
    if not public.device_token_matches_device(p_device_id) then
        raise exception 'invalid device authorization';
    end if;

    update public.device_protection_controls
    set applied_revision = greatest(applied_revision, least(command_revision, p_command_revision)),
        applied_at = now(),
        recovery_consumed_revision = case
            when p_recovery_consumed_revision is null then recovery_consumed_revision
            else greatest(
                recovery_consumed_revision,
                least(recovery_revision, p_recovery_consumed_revision)
            )
        end,
        updated_at = now()
    where device_id = p_device_id;
end;
$$;

revoke all on function public.ack_device_protection_control(uuid, bigint, bigint) from public;
grant execute on function public.ack_device_protection_control(uuid, bigint, bigint) to anon, authenticated;

alter table public.protection_alert_events
drop constraint if exists protection_alert_events_type_check;

alter table public.protection_alert_events
add constraint protection_alert_events_type_check
check (
    alert_type in (
        'web_disabled',
        'apps_disabled',
        'incomplete',
        'admin_disabled',
        'tamper_attempt',
        'maintenance_requested'
    )
);

create or replace function public.create_reinforced_protection_alert_event(
    p_device_id uuid,
    p_alert_type text
)
returns table (
    event_id uuid,
    account_id uuid,
    device_name text,
    title text,
    body text,
    should_send boolean
)
language plpgsql
security definer
set search_path = public, extensions
as $$
declare
    target_device record;
    resolved_title text;
    resolved_body text;
    inserted_id uuid;
    recent_id uuid;
begin
    if p_alert_type not in (
        'web_disabled',
        'apps_disabled',
        'incomplete',
        'admin_disabled',
        'tamper_attempt',
        'maintenance_requested'
    ) then
        raise exception 'Invalid alert type';
    end if;

    if not public.device_token_matches_device(p_device_id) then
        raise exception 'Device token not authorized';
    end if;

    select device.id, device.account_id, device.display_name
    into target_device
    from public.devices as device
    where device.id = p_device_id
      and device.deleted_at is null
    limit 1;

    if target_device.id is null then
        raise exception 'Device not found';
    end if;

    select id
    into recent_id
    from public.protection_alert_events
    where device_id = p_device_id
      and alert_type = p_alert_type
      and created_at >= now() - interval '5 minutes'
    order by created_at desc
    limit 1;

    resolved_title :=
        case p_alert_type
            when 'tamper_attempt' then 'Intento de cambio bloqueado'
            when 'maintenance_requested' then 'Pedido de mantenimiento'
            else 'Protección incompleta'
        end;
    resolved_body :=
        case p_alert_type
            when 'web_disabled' then 'Protección web desactivada en el dispositivo de ' || target_device.display_name || '.'
            when 'apps_disabled' then 'Protección de apps desactivada en el dispositivo de ' || target_device.display_name || '.'
            when 'admin_disabled' then 'Protección contra desinstalación desactivada en el dispositivo de ' || target_device.display_name || '.'
            when 'tamper_attempt' then 'Se bloqueó un intento de cambiar la protección de ' || target_device.display_name || '.'
            when 'maintenance_requested' then target_device.display_name || ' pidió acceso temporal a ajustes protegidos.'
            else 'Protección incompleta en el dispositivo de ' || target_device.display_name || '.'
        end;

    if recent_id is not null then
        return query select recent_id, target_device.account_id, target_device.display_name,
            resolved_title, resolved_body, false;
        return;
    end if;

    insert into public.protection_alert_events (
        account_id,
        device_id,
        alert_type,
        title,
        body
    ) values (
        target_device.account_id,
        p_device_id,
        p_alert_type,
        resolved_title,
        resolved_body
    ) returning id into inserted_id;

    return query select inserted_id, target_device.account_id, target_device.display_name,
        resolved_title, resolved_body, true;
end;
$$;

revoke all on function public.create_reinforced_protection_alert_event(uuid, text) from public;
grant execute on function public.create_reinforced_protection_alert_event(uuid, text) to anon, authenticated;
