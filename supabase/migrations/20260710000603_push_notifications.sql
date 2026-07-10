create table if not exists public.device_push_tokens (
    id uuid primary key references public.devices(id) on delete cascade,
    account_id uuid not null references public.accounts(id) on delete cascade,
    device_id uuid not null references public.devices(id) on delete cascade,
    app_role text not null,
    platform text not null default 'android',
    fcm_token text not null,
    updated_at timestamptz not null default now(),
    deleted_at timestamptz,
    constraint device_push_tokens_role_check check (app_role in ('admin', 'user')),
    constraint device_push_tokens_device_unique unique (device_id)
);

create index if not exists idx_device_push_tokens_account_role
on public.device_push_tokens(account_id, app_role)
where deleted_at is null;

alter table public.device_push_tokens enable row level security;

drop policy if exists "device_push_tokens_admin_device_token_select" on public.device_push_tokens;
create policy "device_push_tokens_admin_device_token_select" on public.device_push_tokens
for select
to anon
using (public.admin_device_token_matches(account_id));

drop policy if exists "device_push_tokens_admin_device_token_upsert" on public.device_push_tokens;
create policy "device_push_tokens_admin_device_token_upsert" on public.device_push_tokens
for all
to anon
using (
    public.device_token_matches_device(device_id)
    and public.admin_device_token_matches(account_id)
)
with check (
    public.device_token_matches_device(device_id)
    and public.admin_device_token_matches(account_id)
    and app_role = 'admin'
);

grant select, insert, update on public.device_push_tokens to anon;

create table if not exists public.protection_alert_events (
    id uuid primary key default gen_random_uuid(),
    account_id uuid not null references public.accounts(id) on delete cascade,
    device_id uuid not null references public.devices(id) on delete cascade,
    alert_type text not null,
    title text not null,
    body text not null,
    created_at timestamptz not null default now(),
    constraint protection_alert_events_type_check check (alert_type in ('web_disabled', 'apps_disabled', 'incomplete'))
);

create index if not exists idx_protection_alert_events_account_created
on public.protection_alert_events(account_id, created_at desc);

alter table public.protection_alert_events enable row level security;

drop policy if exists "protection_alert_events_admin_device_token_select" on public.protection_alert_events;
create policy "protection_alert_events_admin_device_token_select" on public.protection_alert_events
for select
to anon
using (public.admin_device_token_matches(account_id));

grant select on public.protection_alert_events to anon;

create or replace function public.create_protection_alert_event(
    p_device_id uuid,
    p_alert_type text
)
returns table (
    event_id uuid,
    account_id uuid,
    device_name text,
    title text,
    body text
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
begin
    if p_alert_type not in ('web_disabled', 'apps_disabled', 'incomplete') then
        raise exception 'Invalid alert type';
    end if;

    if not public.device_token_matches_device(p_device_id) then
        raise exception 'Device token not authorized';
    end if;

    select id, account_id, display_name
    into target_device
    from public.devices
    where id = p_device_id
      and deleted_at is null
    limit 1;

    if target_device.id is null then
        raise exception 'Device not found';
    end if;

    resolved_title := 'Protección incompleta';
    resolved_body :=
        case p_alert_type
            when 'web_disabled' then 'Protección web desactivada en el dispositivo de ' || target_device.display_name || '.'
            when 'apps_disabled' then 'Protección de apps desactivada en el dispositivo de ' || target_device.display_name || '.'
            else 'Protección incompleta en el dispositivo de ' || target_device.display_name || '.'
        end;

    insert into public.protection_alert_events (
        account_id,
        device_id,
        alert_type,
        title,
        body
    )
    values (
        target_device.account_id,
        p_device_id,
        p_alert_type,
        resolved_title,
        resolved_body
    )
    returning id into inserted_id;

    return query
    select inserted_id,
           target_device.account_id,
           target_device.display_name,
           resolved_title,
           resolved_body;
end;
$$;

revoke all on function public.create_protection_alert_event(uuid, text) from public;
grant execute on function public.create_protection_alert_event(uuid, text) to anon;
