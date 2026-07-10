alter table public.devices
    add column if not exists applied_policy_id uuid references public.policies(id) on delete set null,
    add column if not exists applied_policy_revision bigint,
    add column if not exists policy_applied_at timestamptz;

grant update (
    last_seen_at,
    updated_at,
    applied_policy_id,
    applied_policy_revision,
    policy_applied_at
) on public.devices to anon;

create index if not exists idx_devices_applied_policy_revision
    on public.devices(id, applied_policy_revision);
