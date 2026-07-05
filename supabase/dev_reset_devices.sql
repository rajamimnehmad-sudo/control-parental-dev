-- DEV ONLY.
-- Reset linked user devices and device-owned data for one account.
--
-- Usage in Supabase SQL editor:
--   1. Replace the UUID below with the DEV account_id.
--   2. Run the whole script.
--
-- Affected tables:
--   - device_apps: hard-deleted for deleted devices.
--   - extra_time_grants: hard-deleted when tied to access_requests from deleted devices.
--   - access_requests: hard-deleted for deleted devices.
--   - device_activations: hard-deleted for deleted devices.
--   - activation_codes: hard-deleted for the account so stale pairing tokens disappear.
--   - policies: hard-deleted only when policies.device_id belongs to a deleted device.
--   - policy_rules: deleted by cascade from deleted device-scoped policies.
--   - daily_limits: deleted by cascade from deleted device-scoped policies.
--   - devices: hard-deleted for user devices in the account.
--
-- Not touched:
--   - accounts and global users/profiles.
--   - global account policies where policies.device_id is null.
--   - historical grants with request_id already null, because they cannot be safely tied
--     back to one device.

begin;

do $$
declare
    target_account_id uuid := '00000000-0000-0000-0000-000000000000';
begin
    if target_account_id = '00000000-0000-0000-0000-000000000000'::uuid then
        raise exception 'Replace target_account_id before running this DEV reset.';
    end if;

    create temp table target_devices on commit drop as
    select id
    from public.devices
    where account_id = target_account_id
      and coalesce(app_role, 'user') <> 'admin';

    create temp table target_requests on commit drop as
    select id
    from public.access_requests
    where account_id = target_account_id
      and device_id in (select id from target_devices);

    create temp table target_policies on commit drop as
    select id
    from public.policies
    where account_id = target_account_id
      and device_id in (select id from target_devices);

    delete from public.device_apps
    where account_id = target_account_id
      and device_id in (select id from target_devices);

    delete from public.extra_time_grants
    where account_id = target_account_id
      and request_id in (select id from target_requests);

    delete from public.access_requests
    where account_id = target_account_id
      and id in (select id from target_requests);

    delete from public.device_activations
    where account_id = target_account_id
      and device_id in (select id from target_devices);

    delete from public.activation_codes
    where account_id = target_account_id;

    delete from public.policies
    where account_id = target_account_id
      and id in (select id from target_policies);

    delete from public.devices
    where account_id = target_account_id
      and id in (select id from target_devices);
end $$;

commit;
