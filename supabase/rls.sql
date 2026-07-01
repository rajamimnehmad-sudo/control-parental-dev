-- Row Level Security for the MVP schema.
-- Clients use only the anon key plus authenticated user sessions.

alter table public.accounts enable row level security;
alter table public.devices enable row level security;
alter table public.activation_codes enable row level security;
alter table public.device_activations enable row level security;
alter table public.policies enable row level security;
alter table public.policy_rules enable row level security;
alter table public.daily_limits enable row level security;
alter table public.access_requests enable row level security;
alter table public.extra_time_grants enable row level security;

drop policy if exists "accounts_owner_all" on public.accounts;
create policy "accounts_owner_all" on public.accounts
for all
using (owner_user_id = auth.uid())
with check (owner_user_id = auth.uid());

drop policy if exists "devices_account_owner_all" on public.devices;
create policy "devices_account_owner_all" on public.devices
for all
using (
    exists (
        select 1 from public.accounts
        where accounts.id = devices.account_id
        and accounts.owner_user_id = auth.uid()
    )
)
with check (
    exists (
        select 1 from public.accounts
        where accounts.id = devices.account_id
        and accounts.owner_user_id = auth.uid()
    )
);

drop policy if exists "activation_codes_account_owner_all" on public.activation_codes;
create policy "activation_codes_account_owner_all" on public.activation_codes
for all
using (
    exists (
        select 1 from public.accounts
        where accounts.id = activation_codes.account_id
        and accounts.owner_user_id = auth.uid()
    )
)
with check (
    exists (
        select 1 from public.accounts
        where accounts.id = activation_codes.account_id
        and accounts.owner_user_id = auth.uid()
    )
);

drop policy if exists "device_activations_account_owner_all" on public.device_activations;
create policy "device_activations_account_owner_all" on public.device_activations
for all
using (
    exists (
        select 1 from public.accounts
        where accounts.id = device_activations.account_id
        and accounts.owner_user_id = auth.uid()
    )
)
with check (
    exists (
        select 1 from public.accounts
        where accounts.id = device_activations.account_id
        and accounts.owner_user_id = auth.uid()
    )
);

drop policy if exists "policies_account_owner_all" on public.policies;
create policy "policies_account_owner_all" on public.policies
for all
using (
    exists (
        select 1 from public.accounts
        where accounts.id = policies.account_id
        and accounts.owner_user_id = auth.uid()
    )
)
with check (
    exists (
        select 1 from public.accounts
        where accounts.id = policies.account_id
        and accounts.owner_user_id = auth.uid()
    )
);

drop policy if exists "policy_rules_account_owner_all" on public.policy_rules;
create policy "policy_rules_account_owner_all" on public.policy_rules
for all
using (
    exists (
        select 1 from public.accounts
        where accounts.id = policy_rules.account_id
        and accounts.owner_user_id = auth.uid()
    )
)
with check (
    exists (
        select 1 from public.accounts
        where accounts.id = policy_rules.account_id
        and accounts.owner_user_id = auth.uid()
    )
);

drop policy if exists "daily_limits_account_owner_all" on public.daily_limits;
create policy "daily_limits_account_owner_all" on public.daily_limits
for all
using (
    exists (
        select 1 from public.accounts
        where accounts.id = daily_limits.account_id
        and accounts.owner_user_id = auth.uid()
    )
)
with check (
    exists (
        select 1 from public.accounts
        where accounts.id = daily_limits.account_id
        and accounts.owner_user_id = auth.uid()
    )
);

drop policy if exists "access_requests_account_owner_all" on public.access_requests;
create policy "access_requests_account_owner_all" on public.access_requests
for all
using (
    exists (
        select 1 from public.accounts
        where accounts.id = access_requests.account_id
        and accounts.owner_user_id = auth.uid()
    )
)
with check (
    exists (
        select 1 from public.accounts
        where accounts.id = access_requests.account_id
        and accounts.owner_user_id = auth.uid()
    )
);

drop policy if exists "extra_time_grants_account_owner_all" on public.extra_time_grants;
create policy "extra_time_grants_account_owner_all" on public.extra_time_grants
for all
using (
    exists (
        select 1 from public.accounts
        where accounts.id = extra_time_grants.account_id
        and accounts.owner_user_id = auth.uid()
    )
)
with check (
    exists (
        select 1 from public.accounts
        where accounts.id = extra_time_grants.account_id
        and accounts.owner_user_id = auth.uid()
    )
);
