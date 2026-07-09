alter table public.devices
add column if not exists vpn_state text not null default 'Unknown',
add column if not exists accessibility_state text not null default 'Unknown',
add column if not exists protection_alert text,
add column if not exists protection_updated_at timestamptz;

alter table public.devices
drop constraint if exists devices_vpn_state_check;

alter table public.devices
add constraint devices_vpn_state_check
check (vpn_state in ('Enabled', 'Disabled', 'Warning', 'Unknown'));

alter table public.devices
drop constraint if exists devices_accessibility_state_check;

alter table public.devices
add constraint devices_accessibility_state_check
check (accessibility_state in ('Enabled', 'Disabled', 'Warning', 'Unknown'));

grant update (
    last_seen_at,
    updated_at,
    vpn_state,
    accessibility_state,
    protection_alert,
    protection_updated_at
) on public.devices to anon;
